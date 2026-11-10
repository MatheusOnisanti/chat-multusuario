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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.server.RespostaAutenticacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatTest {

    @Test
    void deveEnviarNoFormatoDoProtocolo() throws Exception {
        SocketDeTeste socket = new SocketDeTeste("OK|AUTENTICADO\n", new LeituraBloqueante());
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });

        try {
            cliente.conectar("ana", "senha".toCharArray());
            cliente.enviar("Ol\u00e1 | turma");

            assertTrue(aguardarTexto(socket, "MENSAGEM|T2zDoSB8IHR1cm1h"));
            try (BufferedReader leitor = new BufferedReader(
                    new StringReader(socket.saida.toString(StandardCharsets.UTF_8)))) {
                assertEquals("AUTENTICAR|YW5h|c2VuaGE=", leitor.readLine());
                assertEquals("MENSAGEM|T2zDoSB8IHR1cm1h", leitor.readLine());
            }
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void deveReceberMensagensEmThreadIndependente() throws Exception {
        EntradaControlada entrada = new EntradaControlada(
                "OK|AUTENTICADO\n",
                "Ana|2026-11-07T21:00|hist\u00f3rico\n");
        SocketDeTeste socket = new SocketDeTeste(entrada);
        CountDownLatch recebida = new CountDownLatch(1);
        List<Mensagem> mensagens = new ArrayList<>();
        ClienteChat cliente = new ClienteChat(socket, "Bia", mensagem -> {
            mensagens.add(mensagem);
            recebida.countDown();
        });

        try {
            cliente.conectar("bia", "senha".toCharArray());
            entrada.liberarMensagens();
            assertTrue(recebida.await(1, TimeUnit.SECONDS));
            assertEquals("hist\u00f3rico", mensagens.get(0).getConteudo());
            assertFalse(Thread.currentThread().getName()
                    .equals(entrada.threadMensagens.get()));
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

        SocketDeTeste socket = new SocketDeTeste("OK|AUTENTICADO\n");
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });
        try {
            cliente.conectar("ana", "senha".toCharArray());
            assertThrows(IllegalArgumentException.class, () -> cliente.enviar(" "));
            assertThrows(IllegalArgumentException.class, () -> cliente.enviar("linha\nquebrada"));
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void fecharDeveSerIdempotente() throws Exception {
        SocketDeTeste socket = new SocketDeTeste("OK|AUTENTICADO\n");
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { });
        cliente.conectar("ana", "senha".toCharArray());
        cliente.fechar();
        cliente.fechar();
        assertTrue(socket.fechado);
    }

    @Test
    void excecaoDoCallbackNaoDeveEncerrarRecebimento() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(
                "OK|AUTENTICADO\nAna|2026-11-07T21:00|primeira\n"
                        + "Ana|2026-11-07T21:01|segunda\n");
        CountDownLatch segundaRecebida = new CountDownLatch(1);
        ClienteChat cliente = new ClienteChat(socket, "Bia", mensagem -> {
            if ("primeira".equals(mensagem.getConteudo())) {
                throw new IllegalStateException("falha do callback");
            }
            segundaRecebida.countDown();
        });

        try {
            cliente.conectar("bia", "senha".toCharArray());
            assertTrue(segundaRecebida.await(1, TimeUnit.SECONDS));
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void deveArmazenarNomeCanonicoDaRespostaV2NaAutenticacao() throws Exception {
        String resposta = RespostaAutenticacao.deComNome(
                RespostaAutenticacao.Codigo.AUTENTICADO, "Matheus").serializarVersionada();
        SocketDeTeste socket = new SocketDeTeste(
                resposta + "\n", new LeituraBloqueante());
        ClienteChat cliente = new ClienteChat(socket, "Nome digitado", mensagem -> { });

        try {
            cliente.conectarV2("blaze", "senha-teste".toCharArray());

            assertTrue(socket.saida.toString(StandardCharsets.UTF_8)
                    .startsWith("AUTENTICAR_V2|YmxhemU=|c2VuaGEtdGVzdGU="));
            assertEquals("Matheus", cliente.getNomeAutenticado().orElseThrow());
            cliente.fechar();
            assertTrue(cliente.getNomeAutenticado().isEmpty());
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void deveArmazenarNomeCanonicoDaRespostaV2NoCadastroComUnicode() throws Exception {
        String nome = "Matheus \u00e7\u00e3o";
        String resposta = RespostaAutenticacao.deComNome(
                RespostaAutenticacao.Codigo.CADASTRADO, nome).serializarVersionada();
        SocketDeTeste socket = new SocketDeTeste(
                resposta + "\n", new LeituraBloqueante());
        ClienteChat cliente = new ClienteChat(socket, "Nome local diferente", mensagem -> { });

        try {
            cliente.cadastrarV2("blaze", nome, "senha-teste".toCharArray());

            assertTrue(socket.saida.toString(StandardCharsets.UTF_8)
                    .startsWith("CADASTRAR_V2|YmxhemU=|"));
            assertEquals(nome, cliente.getNomeAutenticado().orElseThrow());
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void respostaLegadaDeveContinuarAutenticandoSemInventarIdentidade() throws Exception {
        SocketDeTeste socket = new SocketDeTeste("OK|AUTENTICADO\n");
        ClienteChat cliente = new ClienteChat(socket, "Nome local", mensagem -> { });

        try {
            cliente.conectar("blaze", "senha-teste".toCharArray());

            assertTrue(cliente.getNomeAutenticado().isEmpty());
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void respostaLegadaDeSucessoNaoDeveSerAceitaPorSolicitacaoV2() {
        SocketDeTeste socket = new SocketDeTeste("OK|AUTENTICADO\n");
        ClienteChat cliente = new ClienteChat(socket, "Nome local", mensagem -> { });

        try {
            assertThrows(IOException.class,
                    () -> cliente.conectarV2("blaze", "senha-teste".toCharArray()));
            assertTrue(cliente.getNomeAutenticado().isEmpty());
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void erroLegadoDeveContinuarSendoAceitoComoFalhaV2SemIdentidade() {
        SocketDeTeste socket = new SocketDeTeste("ERRO|AUTENTICACAO\n");
        ClienteChat cliente = new ClienteChat(socket, "Nome local", mensagem -> { });

        try {
            assertThrows(IOException.class,
                    () -> cliente.conectarV2("blaze", "senha-teste".toCharArray()));
            assertTrue(cliente.getNomeAutenticado().isEmpty());
        } finally {
            cliente.fechar();
        }
    }

    @Test
    void respostaV2InvalidaNaoDeveSerAceitaComoAutenticacao() {
        SocketDeTeste socket = new SocketDeTeste("OK|V2|AUTENTICADO|%%%\n");
        ClienteChat cliente = new ClienteChat(socket, "Nome local", mensagem -> { });

        try {
            assertThrows(IOException.class,
                    () -> cliente.conectarV2("blaze", "senha-teste".toCharArray()));
            assertTrue(cliente.getNomeAutenticado().isEmpty());
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

        private SocketDeTeste(String recebido) {
            this.entrada = new ByteArrayInputStream(recebido.getBytes(StandardCharsets.UTF_8));
        }

        private SocketDeTeste(EntradaControlada entrada) {
            this.entrada = entrada;
        }

        private SocketDeTeste(String inicial, InputStream entrada) {
            this.entrada = new java.io.SequenceInputStream(
                    new ByteArrayInputStream(inicial.getBytes(StandardCharsets.UTF_8)), entrada);
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

    private static final class EntradaControlada extends InputStream {
        private final byte[] autenticacao;
        private final byte[] mensagens;
        private final CountDownLatch liberar = new CountDownLatch(1);
        private final AtomicReference<String> threadMensagens = new AtomicReference<>();
        private int indiceAutenticacao;
        private int indiceMensagens;

        private EntradaControlada(String autenticacao, String mensagens) {
            this.autenticacao = autenticacao.getBytes(StandardCharsets.UTF_8);
            this.mensagens = mensagens.getBytes(StandardCharsets.UTF_8);
        }

        private void liberarMensagens() {
            liberar.countDown();
        }

        @Override
        public int read() throws IOException {
            if (indiceAutenticacao < autenticacao.length) {
                return autenticacao[indiceAutenticacao++] & 0xff;
            }
            try {
                liberar.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Leitura interrompida.", exception);
            }
            if (indiceMensagens >= mensagens.length) {
                return -1;
            }
            threadMensagens.compareAndSet(null, Thread.currentThread().getName());
            return mensagens[indiceMensagens++] & 0xff;
        }

        @Override
        public int read(byte[] destino, int deslocamento, int quantidade)
                throws IOException {
            if (destino == null) {
                throw new NullPointerException("O destino nÃ£o pode ser nulo.");
            }
            if (deslocamento < 0 || quantidade < 0
                    || deslocamento > destino.length - quantidade) {
                throw new IndexOutOfBoundsException("Intervalo de leitura invÃ¡lido.");
            }
            if (quantidade == 0) {
                return 0;
            }
            if (indiceAutenticacao < autenticacao.length) {
                int quantidadeDisponivel = Math.min(quantidade,
                        autenticacao.length - indiceAutenticacao);
                System.arraycopy(autenticacao, indiceAutenticacao,
                        destino, deslocamento, quantidadeDisponivel);
                indiceAutenticacao += quantidadeDisponivel;
                return quantidadeDisponivel;
            }
            liberarMensagensSeNecessario();
            if (indiceMensagens >= mensagens.length) {
                return -1;
            }
            int quantidadeDisponivel = Math.min(quantidade,
                    mensagens.length - indiceMensagens);
            System.arraycopy(mensagens, indiceMensagens,
                    destino, deslocamento, quantidadeDisponivel);
            indiceMensagens += quantidadeDisponivel;
            threadMensagens.compareAndSet(null, Thread.currentThread().getName());
            return quantidadeDisponivel;
        }

        private void liberarMensagensSeNecessario() throws IOException {
            try {
                liberar.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Leitura interrompida.", exception);
            }
        }

        @Override
        public void close() {
            liberar.countDown();
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
