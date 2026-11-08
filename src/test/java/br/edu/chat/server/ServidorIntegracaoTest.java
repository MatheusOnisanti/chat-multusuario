package br.edu.chat.server;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServidorIntegracaoTest {

    @Test
    void servidorDeveAceitarDuasConexoesSimultaneas() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket clienteUm = conectar(contexto.porta);
             Socket clienteDois = conectar(contexto.porta)) {
            aguardarQuantidadeDeHandlers(contexto.servidor, 2);

            assertTrue(contexto.threadServidor.isAlive());
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void umaConexaoNaoDeveInterferirNaOutra() throws Exception {
        Contexto contexto = iniciarServidor();
        Socket clienteUm = conectar(contexto.porta);
        Socket clienteDois = conectar(contexto.porta);
        try {
            aguardarQuantidadeDeHandlers(contexto.servidor, 2);

            clienteDois.close();
            aguardarQuantidadeDeHandlers(contexto.servidor, 1);

            clienteUm.getOutputStream().write(1);
            clienteUm.getOutputStream().flush();
            assertTrue(contexto.threadServidor.isAlive());
        } finally {
            clienteUm.close();
            clienteDois.close();
            encerrar(contexto);
        }
    }

    @Test
    void encerrarDeveFecharServidorEConexoesAtivas() throws Exception {
        Contexto contexto = iniciarServidor();
        Socket clienteUm = conectar(contexto.porta);
        Socket clienteDois = conectar(contexto.porta);
        try {
            aguardarQuantidadeDeHandlers(contexto.servidor, 2);

            contexto.servidor.encerrar();
            aguardarFechamento(clienteUm);
            aguardarFechamento(clienteDois);
        } finally {
            clienteUm.close();
            clienteDois.close();
            encerrar(contexto);
        }
    }

    @Test
    void threadDeIniciarDeveTerminarAposEncerramento() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket cliente = conectar(contexto.porta)) {
            contexto.servidor.encerrar();

            assertTimeoutAndJoin(contexto.threadServidor);
            assertFalse(contexto.threadServidor.isAlive());
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void broadcastDeveEnviarMensagemAosDemaisClientes() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket origem = conectar(contexto.porta);
             Socket destinoUm = conectar(contexto.porta);
             Socket destinoDois = conectar(contexto.porta)) {
            aguardarQuantidadeDeHandlers(contexto.servidor, 3);
            aguardarQuantidadeDeClientesRegistrados(contexto.servidor, 3);
            destinoUm.setSoTimeout(2000);
            destinoDois.setSoTimeout(2000);

            enviar(origem, "Ana|Olá | turma!");

            assertTrue(new BufferedReader(new InputStreamReader(
                    destinoUm.getInputStream(), StandardCharsets.UTF_8)).readLine()
                    .endsWith("|Olá | turma!"));
            assertTrue(new BufferedReader(new InputStreamReader(
                    destinoDois.getInputStream(), StandardCharsets.UTF_8)).readLine()
                    .endsWith("|Olá | turma!"));
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void mensagensSimultaneasDeClientesDiferentesDevemSerDistribuidas() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket origemUm = conectar(contexto.porta);
             Socket origemDois = conectar(contexto.porta);
             Socket destino = conectar(contexto.porta)) {
            aguardarQuantidadeDeHandlers(contexto.servidor, 3);
            aguardarQuantidadeDeClientesRegistrados(contexto.servidor, 3);
            destino.setSoTimeout(2000);

            CountDownLatch inicio = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                executor.submit(() -> enviarAposInicio(inicio, origemUm, "Ana|mensagem A"));
                executor.submit(() -> enviarAposInicio(inicio, origemDois, "Bia|mensagem B"));
                inicio.countDown();
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            }

            BufferedReader entrada = new BufferedReader(new InputStreamReader(
                    destino.getInputStream(), StandardCharsets.UTF_8));
            String primeira = entrada.readLine();
            String segunda = entrada.readLine();
            assertTrue(primeira.contains("mensagem A") || segunda.contains("mensagem A"));
            assertTrue(primeira.contains("mensagem B") || segunda.contains("mensagem B"));
        } finally {
            encerrar(contexto);
        }
    }

    private static Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String nomeBanco = "servidor_test_" + java.util.UUID.randomUUID().toString().replace('-', '_');
        ChatService service = new ChatService(
                new ClientRegistry(),
                new JdbcMensagemRepository(ConnectionFactory.emMemoria(nomeBanco)));
        Servidor servidor = new Servidor(porta, service);
        CountDownLatch iniciado = new CountDownLatch(1);
        Thread threadServidor = new Thread(() -> {
            iniciado.countDown();
            try {
                servidor.iniciar();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        threadServidor.start();
        assertTrue(iniciado.await(1, TimeUnit.SECONDS));
        return new Contexto(servidor, threadServidor, porta);
    }

    private static Socket conectar(int porta) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        IOException ultimoErro = null;

        while (System.nanoTime() < limite) {
            try {
                return new Socket("127.0.0.1", porta);
            } catch (IOException exception) {
                ultimoErro = exception;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }

        throw new IOException("Não foi possível conectar ao servidor", ultimoErro);
    }

    private static int encontrarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void enviar(Socket socket, String linha) throws IOException {
        PrintWriter saida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        saida.println(linha);
        assertFalse(saida.checkError());
    }

    private static void enviarAposInicio(CountDownLatch inicio, Socket socket, String linha) {
        try {
            inicio.await();
            enviar(socket, linha);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void aguardarQuantidadeDeHandlers(Servidor servidor, int quantidade) {
        assertTimeout(
                Duration.ofSeconds(3),
                () -> {
                    while (handlersAtivos(servidor).size() < quantidade) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    }
                }
        );
    }

    private static void aguardarQuantidadeDeClientesRegistrados(Servidor servidor, int quantidade) {
        assertTimeout(
                Duration.ofSeconds(3),
                () -> {
                    while (clientRegistry(servidor).snapshot().size() < quantidade) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    }
                }
        );
    }

    private static void aguardarFechamento(Socket socket) {
        assertTimeout(
                Duration.ofSeconds(3),
                () -> {
                    socket.setSoTimeout(100);
                    while (!socket.isClosed()) {
                        try {
                            if (socket.getInputStream().read() == -1) {
                                return;
                            }
                        } catch (SocketTimeoutException exception) {
                            // Continua aguardando o fechamento remoto.
                        } catch (IOException exception) {
                            return;
                        }
                    }
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static Set<ClienteHandler> handlersAtivos(Servidor servidor) throws Exception {
        Field campo = Servidor.class.getDeclaredField("handlersAtivos");
        campo.setAccessible(true);
        return (Set<ClienteHandler>) campo.get(servidor);
    }

    private static ClientRegistry clientRegistry(Servidor servidor) throws Exception {
        Field campoService = Servidor.class.getDeclaredField("chatService");
        campoService.setAccessible(true);
        ChatService service = (ChatService) campoService.get(servidor);

        Field campoRegistry = ChatService.class.getDeclaredField("clientRegistry");
        campoRegistry.setAccessible(true);
        return (ClientRegistry) campoRegistry.get(service);
    }

    private static void assertTimeout(Duration duracao, org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertTimeout(duracao, executable);
    }

    private static void assertTimeoutAndJoin(Thread thread) {
        assertTimeout(Duration.ofSeconds(3), () -> thread.join(2000));
    }

    private static void encerrar(Contexto contexto) throws Exception {
        contexto.servidor.encerrar();
        contexto.threadServidor.join(2000);
    }

    private record Contexto(Servidor servidor, Thread threadServidor, int porta) {
    }
}
