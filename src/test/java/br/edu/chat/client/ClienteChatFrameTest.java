package br.edu.chat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.server.Servidor;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;

class ClienteChatFrameTest {

    @Test
    void deveIniciarComControlesNoEstadoDesconectado() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            JButton conectar = campo(frame, "botaoConectar", JButton.class);
            JButton enviar = campo(frame, "botaoEnviar", JButton.class);
            JButton desconectar = campo(frame, "botaoDesconectar", JButton.class);

            assertTrue(conectar.isEnabled());
            assertFalse(enviar.isEnabled());
            assertFalse(desconectar.isEnabled());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveConectarEImpedirConexoesDuplicadas() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame();
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");

            assertFalse(campo(frame, "botaoConectar", JButton.class).isEnabled());
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));
            assertEquals(1, handlersAtivos(contexto.servidor));

            clicar(frame, "botaoConectar");
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            assertFalse(campo(frame, "botaoConectar", JButton.class).isEnabled());
            assertTrue(campo(frame, "botaoEnviar", JButton.class).isEnabled());
            assertTrue(campo(frame, "botaoDesconectar", JButton.class).isEnabled());
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveExibirHistoricoEMensagemPropriaUmaUnicaVez() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame();
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Preparador",
                mensagem -> { });
        try {
            preparador.conectar();
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            preparador.enviar("mensagem histórica");
            aguardar(() -> contexto.repository.listarHistorico().stream()
                    .anyMatch(mensagem -> mensagem.getConteudo().equals("mensagem histórica")));
            preparador.fechar();

            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("mensagem histórica"));
            aguardar(() -> botaoHabilitado(frame, "botaoEnviar"));

            preencherMensagem(frame, "minha mensagem | preservada");
            clicar(frame, "botaoEnviar");
            aguardar(() -> contar(texto(frame, "areaHistorico"), "minha mensagem | preservada") == 1);

            assertEquals(1, contar(texto(frame, "areaHistorico"), "minha mensagem | preservada"));
            assertTrue(campo(frame, "botaoEnviar", JButton.class).isEnabled());
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveExibirHistoricoUmaUnicaVezAposDesconectarEReconectar() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame();
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Preparador",
                mensagem -> { });
        try {
            preparador.conectar();
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            preparador.enviar("histórico da reconexão");
            aguardar(() -> contexto.repository.listarHistorico().stream()
                    .anyMatch(mensagem -> mensagem.getConteudo().equals("histórico da reconexão")));
            preparador.fechar();

            preencherConexao(frame, contexto.porta, "Victor");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("histórico da reconexão"));
            aguardar(() -> botaoHabilitado(frame, "botaoDesconectar"));

            clicar(frame, "botaoDesconectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Desconectado"));
            aguardar(() -> botaoHabilitado(frame, "botaoConectar"));

            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("histórico da reconexão"));
            aguardar(() -> botaoHabilitado(frame, "botaoDesconectar"));

            assertEquals(1, contar(texto(frame, "areaHistorico"), "histórico da reconexão"));
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveAtualizarEstadoAposDesconexaoInesperada() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame();
        Timer fechamentoDialogo = fecharDialogosAutomaticamente();
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            contexto.servidor.encerrar();
            aguardar(() -> texto(frame, "rotuloEstado").equals("Desconectado"));

            assertTrue(campo(frame, "botaoConectar", JButton.class).isEnabled());
            assertFalse(campo(frame, "botaoEnviar", JButton.class).isEnabled());
            assertFalse(campo(frame, "botaoDesconectar", JButton.class).isEnabled());
        } finally {
            fechamentoDialogo.stop();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveFecharJanelaERecursosComSeguranca() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame();
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            executarNaEdt(() -> frame.dispatchEvent(new WindowEvent(
                    frame, WindowEvent.WINDOW_CLOSING)));
            aguardar(() -> !frame.isDisplayable());
            aguardar(() -> handlersAtivos(contexto.servidor) == 0);

            assertFalse(frame.isDisplayable());
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    private static void requerAmbienteGrafico() {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Teste Swing ignorado em ambiente headless.");
    }

    private static ClienteChatFrame criarFrame() throws Exception {
        AtomicReference<ClienteChatFrame> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(new ClienteChatFrame()));
        return referencia.get();
    }

    private static void fecharFrame(ClienteChatFrame frame) throws Exception {
        if (frame != null) {
            executarNaEdt(frame::dispose);
        }
    }

    private static void preencherConexao(ClienteChatFrame frame, int porta, String nome)
            throws Exception {
        executarNaEdt(() -> {
            campo(frame, "campoPorta", JTextField.class).setText(Integer.toString(porta));
            campo(frame, "campoNome", JTextField.class).setText(nome);
        });
    }

    private static void preencherMensagem(ClienteChatFrame frame, String mensagem)
            throws Exception {
        executarNaEdt(() -> campo(frame, "campoMensagem", JTextField.class).setText(mensagem));
    }

    private static void clicar(ClienteChatFrame frame, String nomeCampo) throws Exception {
        executarNaEdt(() -> campo(frame, nomeCampo, JButton.class).doClick());
    }

    private static boolean botaoHabilitado(ClienteChatFrame frame, String nomeCampo)
            throws Exception {
        AtomicReference<Boolean> habilitado = new AtomicReference<>();
        executarNaEdt(() -> habilitado.set(campo(frame, nomeCampo, JButton.class).isEnabled()));
        return habilitado.get();
    }

    private static String texto(ClienteChatFrame frame, String nomeCampo) throws Exception {
        AtomicReference<String> texto = new AtomicReference<>();
        executarNaEdt(() -> {
            Object componente = campo(frame, nomeCampo);
            if (componente instanceof JTextArea area) {
                texto.set(area.getText());
            } else {
                texto.set(((javax.swing.JLabel) componente).getText());
            }
        });
        return texto.get();
    }

    private static int contar(String texto, String trecho) {
        int quantidade = 0;
        int indice = 0;
        while ((indice = texto.indexOf(trecho, indice)) >= 0) {
            quantidade++;
            indice += trecho.length();
        }
        return quantidade;
    }

    private static void executarNaEdt(Runnable tarefa) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            tarefa.run();
        } else {
            SwingUtilities.invokeAndWait(tarefa);
        }
    }

    private static void aguardar(Condicao condicao) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.yield();
        }
        assertTrue(condicao.getAsBoolean(), "Condição não atingida dentro do limite.");
    }

    @FunctionalInterface
    private interface Condicao {
        boolean getAsBoolean() throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object objeto, String nome, Class<T> tipo) {
        return tipo.cast(campo(objeto, nome));
    }

    private static Object campo(Object objeto, String nome) {
        try {
            Field campo = objeto.getClass().getDeclaredField(nome);
            campo.setAccessible(true);
            return campo.get(objeto);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Campo não encontrado: " + nome, exception);
        }
    }

    private static Timer fecharDialogosAutomaticamente() throws Exception {
        AtomicReference<Timer> referencia = new AtomicReference<>();
        executarNaEdt(() -> {
            Timer timer = new Timer(25, evento -> {
                for (Window janela : Window.getWindows()) {
                    if (janela instanceof Dialog dialogo && dialogo.isShowing()) {
                        dialogo.dispose();
                    }
                }
            });
            timer.start();
            referencia.set(timer);
        });
        return referencia.get();
    }

    private static Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String banco = "frame_teste_" + UUID.randomUUID().toString().replace('-', '_');
        JdbcMensagemRepository repository = new JdbcMensagemRepository(
                ConnectionFactory.emMemoria(banco));
        ChatService service = new ChatService(new ClientRegistry(), repository);
        Servidor servidor = new Servidor(porta, service);
        AtomicReference<Throwable> falha = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                servidor.iniciar();
            } catch (IOException exception) {
                falha.set(exception);
            }
        }, "servidor-frame-teste");
        thread.start();
        aguardarPorta(porta, falha);
        return new Contexto(servidor, thread, porta, repository);
    }

    private static void aguardarPorta(int porta, AtomicReference<Throwable> falha) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < limite) {
            if (falha.get() != null) {
                throw new AssertionError("Servidor não iniciou.", falha.get());
            }
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", porta)) {
                return;
            } catch (IOException exception) {
                Thread.yield();
            }
        }
        throw new IOException("Servidor não iniciou dentro do limite.");
    }

    private static int handlersAtivos(Servidor servidor) throws Exception {
        Field campo = Servidor.class.getDeclaredField("handlersAtivos");
        campo.setAccessible(true);
        return ((java.util.Set<?>) campo.get(servidor)).size();
    }

    private static int encontrarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private record Contexto(Servidor servidor, Thread threadServidor, int porta,
            JdbcMensagemRepository repository) {
        private void fechar() throws Exception {
            servidor.encerrar();
            threadServidor.join(2000);
            assertFalse(threadServidor.isAlive());
        }
    }
}
