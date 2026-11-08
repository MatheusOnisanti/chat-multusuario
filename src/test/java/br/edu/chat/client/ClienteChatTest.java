package br.edu.chat.client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatTest {

    @Test
    void deveEnviarNoFormatoDoProtocolo() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new LeituraBloqueante());
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });

        try {
            cliente.conectar();
            cliente.enviar("Olá | turma");

            assertTrue(aguardarTexto(socket, "Ana|Olá | turma"));
            try (BufferedReader leitor = new BufferedReader(
                    new StringReader(socket.saida.toString(StandardCharsets.UTF_8)))) {
                assertEquals("Ana|Olá | turma", leitor.readLine());
            }
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void deveReceberMensagensEmThreadIndependente() throws Exception {
        SocketDeTeste socket = new SocketDeTeste("Ana|2026-11-07T21:00|histórico\n");
        CountDownLatch recebida = new CountDownLatch(1);
        List<Mensagem> mensagens = new ArrayList<>();
        ClienteChat cliente = new ClienteChat(socket, "Bia", mensagem -> {
            mensagens.add(mensagem);
            recebida.countDown();
        });

        try {
            cliente.conectar();

            assertTrue(recebida.await(1, TimeUnit.SECONDS));
            assertEquals("histórico", mensagens.get(0).getConteudo());
            assertFalse(Thread.currentThread().getName().equals(socket.nomeThreadRecebimento));
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void deveValidarNomeEConteudo() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new ClienteChat("localhost", 1234, "Ana|Bia", mensagem -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new ClienteChat("localhost", 1234, "Ana\nBia", mensagem -> { }));

        SocketDeTeste socket = new SocketDeTeste("");
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            assertThrows(IllegalArgumentException.class, () -> cliente.enviar(" "));
            assertThrows(IllegalArgumentException.class, () -> cliente.enviar("linha\nquebrada"));
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void fecharDeveSerIdempotente() throws Exception {
        SocketDeTeste socket = new SocketDeTeste("");
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });
        cliente.conectar();

        cliente.fechar();
        cliente.fechar();

        assertTrue(socket.fechado);
    }

    @Test
    void excecaoDoCallbackNaoDeveEncerrarRecebimento() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(
                "Ana|2026-11-07T21:00|primeira\nAna|2026-11-07T21:01|segunda\n");
        CountDownLatch segundaRecebida = new CountDownLatch(1);
        ClienteChat cliente = new ClienteChat(socket, "Bia", mensagem -> {
            if ("primeira".equals(mensagem.getConteudo())) {
                throw new IllegalStateException("falha do callback");
            }
            segundaRecebida.countDown();
        });

        try {
            cliente.conectar();
            assertTrue(segundaRecebida.await(1, TimeUnit.SECONDS));
        } finally {
            cliente.fechar();
        }
    }

    private static boolean aguardarTexto(SocketDeTeste socket, String esperado) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!socket.saida.toString(StandardCharsets.UTF_8).contains(esperado)
                && System.nanoTime() < limite) {
            Thread.yield();
        }
        return socket.saida.toString(StandardCharsets.UTF_8).contains(esperado);
    }

    private static final class SocketDeTeste extends Socket {
        private final InputStream entrada;
        private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
        private boolean fechado;
        private String nomeThreadRecebimento;

        private SocketDeTeste(String recebido) {
            this.entrada = new ByteArrayInputStream(recebido.getBytes(StandardCharsets.UTF_8));
        }

        private SocketDeTeste(InputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public InputStream getInputStream() {
            nomeThreadRecebimento = Thread.currentThread().getName();
            return entrada;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return saida;
        }

        @Override
        public synchronized void close() throws IOException {
            fechado = true;
            entrada.close();
        }
    }

    private static final class LeituraBloqueante extends InputStream {
        private final CountDownLatch encerrada = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            try {
                encerrada.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Leitura encerrada");
        }

        @Override
        public void close() {
            encerrada.countDown();
        }
    }
}
