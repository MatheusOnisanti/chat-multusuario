package br.edu.chat.server;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.config.ConfiguracaoTls;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLServerSocket;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.persistence.jdbc.JdbcCadastroUsuario;
import br.edu.chat.persistence.jdbc.JdbcUsuarioCredencialRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.SenhaHasher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServidorIntegracaoTest {

    private static final String SENHA_TLS = "teste-integracao-tls";
    private Path diretorioTls;
    private Path keytool;

    @org.junit.jupiter.api.BeforeEach
    void prepararTls() {
        diretorioTls = criarDiretorioTemporario();
        keytool = localizarKeytool();
    }

    @Test
    void servidorDeveAceitarDuasConexoesSimultaneas() throws Exception {
        Contexto contexto = iniciarServidor();
        try (SSLSocket clienteUm = conectar(contexto);
             SSLSocket clienteDois = conectar(contexto)) {
            aguardarQuantidadeDeHandlers(contexto.servidor, 2);

            assertTrue(contexto.threadServidor.isAlive());
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void umaConexaoNaoDeveInterferirNaOutra() throws Exception {
        Contexto contexto = iniciarServidor();
        SSLSocket clienteUm = conectar(contexto);
        SSLSocket clienteDois = conectar(contexto);
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
        SSLSocket clienteUm = conectar(contexto);
        SSLSocket clienteDois = conectar(contexto);
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
        try (SSLSocket cliente = conectar(contexto)) {
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
        try (SSLSocket origem = conectar(contexto);
             SSLSocket destinoUm = conectar(contexto);
             SSLSocket destinoDois = conectar(contexto)) {
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
        try (SSLSocket origemUm = conectar(contexto);
             SSLSocket origemDois = conectar(contexto);
             SSLSocket destino = conectar(contexto)) {
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

    @Test
    void servidorDeveAceitarConexaoValidaAposFalhaDeHandshake() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket clienteInvalido = new Socket("127.0.0.1", contexto.porta())) {
            clienteInvalido.setSoLinger(true, 0);
            clienteInvalido.close();

            try (SSLSocket clienteValido = conectar(contexto)) {
                aguardarQuantidadeDeHandlers(contexto.servidor, 1);
                assertEquals(1, handlersAtivos(contexto.servidor).size());
                assertTrue(contexto.threadServidor.isAlive());
            }
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void encerrarDeveFecharHandshakePendenteSemAguardarTimeout() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket clientePendente = new Socket("127.0.0.1", contexto.porta())) {
            aguardarQuantidadeDeHandshakesPendentes(contexto.servidor, 1);

            contexto.servidor.encerrar();

            assertTimeoutAndJoin(contexto.threadServidor);
            assertFalse(contexto.threadServidor.isAlive());
            assertEquals(0, handlersAtivos(contexto.servidor).size());
            aguardarFechamento(clientePendente);
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void encerrarDeveFinalizarExecutorEHandshakesEnfileirados() throws Exception {
        Contexto contexto = iniciarServidor();
        List<Socket> clientes = new ArrayList<>();
        ThreadPoolExecutor executor = null;
        try {
            for (int indice = 0; indice < 4; indice++) {
                clientes.add(new Socket("127.0.0.1", contexto.porta()));
            }
            aguardarQuantidadeDeHandshakesPendentes(contexto.servidor, 4);
            executor = executorHandshakes(contexto.servidor);

            contexto.servidor.encerrar();

            assertTrue(executor.isTerminated());
            assertEquals(0, handshakesPendentes(contexto.servidor));
            for (Socket cliente : clientes) {
                aguardarFechamento(cliente);
            }
        } finally {
            for (Socket cliente : clientes) {
                cliente.close();
            }
            encerrar(contexto);
        }
    }

    @Test
    void handshakePendenteNaoDeveImpedirConexaoValida() throws Exception {
        Contexto contexto = iniciarServidor();
        try (Socket clientePendente = new Socket("127.0.0.1", contexto.porta())) {
            aguardarQuantidadeDeHandshakesPendentes(contexto.servidor, 1);

            try (SSLSocket clienteValido = conectar(contexto)) {
                aguardarQuantidadeDeHandlers(contexto.servidor, 1);
                assertEquals(1, handlersAtivos(contexto.servidor).size());
            }
        } finally {
            encerrar(contexto);
        }
    }

    @Test
    void excessoDeHandshakesDeveSerRejeitadoEFechado() throws Exception {
        Contexto contexto = iniciarServidor();
        List<Socket> clientes = new ArrayList<>();
        try {
            for (int indice = 0; indice < 4; indice++) {
                clientes.add(new Socket("127.0.0.1", contexto.porta()));
            }
            aguardarQuantidadeDeHandshakesPendentes(contexto.servidor, 4);

            Socket excedente = new Socket("127.0.0.1", contexto.porta());
            clientes.add(excedente);
            aguardarFechamento(excedente);
            assertEquals(0, handlersAtivos(contexto.servidor).size());
        } finally {
            for (Socket cliente : clientes) {
                cliente.close();
            }
            encerrar(contexto);
        }
    }

    private Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String nomeBanco = "servidor_test_" + java.util.UUID.randomUUID().toString().replace('-', '_');
        ChatService service = new ChatService(
                new ClientRegistry(),
                new JdbcMensagemRepository(ConnectionFactory.emMemoria(nomeBanco)));
        ConnectionFactory conexoes = ConnectionFactory.emMemoria(nomeBanco);
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(conexoes);
        JdbcUsuarioCredencialRepository credenciais = new JdbcUsuarioCredencialRepository(conexoes);
        AutenticacaoService autenticacao = new AutenticacaoService(
                new JdbcCadastroUsuario(conexoes), usuarios, credenciais, new SenhaHasher());
        autenticacao.cadastrar("ana", "Ana", "senha".toCharArray());
        autenticacao.cadastrar("bia", "Bia", "senha".toCharArray());
        MaterialTls material = gerarMaterial("localhost", "dns:localhost,ip:127.0.0.1");
        ConfiguracaoTls configuracao = criarConfiguracao(material);
        Servidor servidor = new Servidor(porta, service, configuracao, autenticacao);
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
        aguardarServidorPronto(servidor, threadServidor);
        return new Contexto(servidor, threadServidor, porta, configuracao);
    }

    private static SSLSocket conectar(Contexto contexto) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        IOException ultimoErro = null;

        while (System.nanoTime() < limite) {
            try {
                SSLSocket socket = contexto.configuracao().conectar(
                        "127.0.0.1", contexto.porta());
                socket.startHandshake();
                enviarComando(socket, comando("AUTENTICAR", "ana", "senha"));
                BufferedReader resposta = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                if (!"OK|AUTENTICADO".equals(resposta.readLine())) {
                    socket.close();
                    throw new IOException("Falha na autenticação do cliente de teste.");
                }
                return socket;
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
        enviarComando(socket, comando("MENSAGEM", linha));
    }

    private static void enviarComando(Socket socket, String linha) throws IOException {
        PrintWriter saida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        saida.println(linha);
        assertFalse(saida.checkError());
    }

    private static String comando(String tipo, String... valores) {
        StringBuilder resultado = new StringBuilder(tipo);
        for (String valor : valores) {
            resultado.append('|').append(Base64.getEncoder().encodeToString(
                    valor.getBytes(StandardCharsets.UTF_8)));
        }
        return resultado.toString();
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

    private static void aguardarQuantidadeDeHandshakesPendentes(Servidor servidor, int quantidade) {
        assertTimeout(
                Duration.ofSeconds(3),
                () -> {
                    while (handshakesPendentes(servidor) < quantidade) {
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

    @SuppressWarnings("unchecked")
    private static int handshakesPendentes(Servidor servidor) throws Exception {
        Field campo = Servidor.class.getDeclaredField("handshakesPendentes");
        campo.setAccessible(true);
        return ((Set<SSLSocket>) campo.get(servidor)).size();
    }

    private static ThreadPoolExecutor executorHandshakes(Servidor servidor) throws Exception {
        Field campo = Servidor.class.getDeclaredField("executorHandshakes");
        campo.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) campo.get(servidor);
        if (executor == null) {
            throw new AssertionError("O executor de handshakes não foi inicializado.");
        }
        return executor;
    }

    private static void aguardarServidorPronto(Servidor servidor, Thread threadServidor)
            throws Exception {
        assertTimeout(
                Duration.ofSeconds(3),
                () -> {
                    while (true) {
                        if (!threadServidor.isAlive()) {
                            throw new AssertionError("A thread do servidor terminou antes da prontidão.");
                        }

                        SSLServerSocket socket;
                        synchronized (servidor) {
                            Field campo = Servidor.class.getDeclaredField("serverSocket");
                            campo.setAccessible(true);
                            socket = (SSLServerSocket) campo.get(servidor);
                        }

                        if (socket != null && socket.isBound() && !socket.isClosed()) {
                            return;
                        }
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    }
                }
        );
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

    private ConfiguracaoTls criarConfiguracao(MaterialTls material) {
        char[] senhaKeystore = SENHA_TLS.toCharArray();
        char[] senhaTruststore = SENHA_TLS.toCharArray();
        try {
            return new ConfiguracaoTls(material.keystore(), senhaKeystore,
                    material.truststore(), senhaTruststore);
        } finally {
            Arrays.fill(senhaKeystore, '\0');
            Arrays.fill(senhaTruststore, '\0');
        }
    }

    private MaterialTls gerarMaterial(String nomeComum, String san) throws Exception {
        Path keystore = diretorioTls.resolve("servidor.p12");
        Path certificado = diretorioTls.resolve("servidor.crt");
        Path truststore = diretorioTls.resolve("confianca.p12");
        executarKeytool(List.of("-genkeypair", "-alias", "servidor", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TLS, "-keypass", SENHA_TLS,
                "-dname", "CN=" + nomeComum, "-ext", "SAN=" + san));
        executarKeytool(List.of("-exportcert", "-alias", "servidor", "-keystore",
                keystore.toString(), "-storetype", "PKCS12", "-storepass", SENHA_TLS,
                "-rfc", "-file", certificado.toString()));
        executarKeytool(List.of("-importcert", "-alias", "servidor", "-file",
                certificado.toString(), "-keystore", truststore.toString(), "-storetype",
                "PKCS12", "-storepass", SENHA_TLS, "-noprompt"));
        return new MaterialTls(keystore, truststore);
    }

    private void executarKeytool(List<String> argumentos) throws Exception {
        ProcessBuilder processo = new ProcessBuilder();
        processo.command().add(keytool.toString());
        processo.command().addAll(argumentos);
        processo.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processo.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process iniciado = processo.start();
        try {
            assertTrue(iniciado.waitFor(10, TimeUnit.SECONDS), "keytool excedeu o tempo limite.");
            assertEquals(0, iniciado.exitValue(), "keytool falhou.");
        } finally {
            iniciado.destroyForcibly();
        }
    }

    private static Path criarDiretorioTemporario() {
        try {
            return Files.createTempDirectory("servidor-tls-");
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível criar diretório TLS temporário.", exception);
        }
    }

    private static Path localizarKeytool() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String nome = System.getProperty("os.name").toLowerCase().contains("win")
                ? "keytool.exe" : "keytool";
        Path executavel = javaHome.resolve("bin").resolve(nome);
        if (!Files.isExecutable(executavel)) {
            executavel = javaHome.getParent().resolve("bin").resolve(nome);
        }
        if (!Files.isExecutable(executavel)) {
            throw new IllegalStateException("keytool não está disponível.");
        }
        return executavel;
    }

    private record MaterialTls(Path keystore, Path truststore) {
    }

    private record Contexto(Servidor servidor, Thread threadServidor, int porta,
            ConfiguracaoTls configuracao) {
    }
}
