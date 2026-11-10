package br.edu.chat.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatDesconexaoTest {

    @Test
    void deveExecutarCallbackUmaVezAoReceberEof() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        });
        CountDownLatch desconectado = new CountDownLatch(1);
        AtomicInteger chamadas = new AtomicInteger();
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            chamadas.incrementAndGet();
            desconectado.countDown();
        });

        socket.prepararResposta();
        cliente.conectar("ana", "senha".toCharArray());

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        cliente.fechar();
        assertEquals(1, chamadas.get());
    }

    @Test
    void deveExecutarCallbackUmaVezAoFecharRepetidamente() throws Exception {
        LeituraBloqueante leitura = new LeituraBloqueante();
        SocketDeTeste socket = new SocketDeTeste(leitura);
        CountDownLatch desconectado = new CountDownLatch(1);
        AtomicInteger chamadas = new AtomicInteger();
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            chamadas.incrementAndGet();
            desconectado.countDown();
        });

        socket.prepararResposta();
        cliente.conectar("ana", "senha".toCharArray());
        cliente.fechar();
        cliente.fechar();

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        assertEquals(1, chamadas.get());
    }

    @Test
    void falhaDoCallbackNaoDeveImpedirOEncerramento() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        });
        CountDownLatch desconectado = new CountDownLatch(1);
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            desconectado.countDown();
            throw new IllegalStateException("falha simulada");
        });

        socket.prepararResposta();
        cliente.conectar("ana", "senha".toCharArray());

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        assertTrue(socket.fechado);
    }

    private static final class SocketDeTeste extends Socket {
        private final InputStream entrada;
        private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
        private boolean fechado;

        private SocketDeTeste(InputStream entrada) {
            this.entrada = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream("OK|AUTENTICADO\n"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)), entrada);
        }

        private void prepararResposta() {
            // A resposta jÃ¡ faz parte do fluxo de entrada antes da autenticaÃ§Ã£o.
        }

        @Override
        public InputStream getInputStream() {
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
