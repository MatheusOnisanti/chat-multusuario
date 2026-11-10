package br.edu.chat.client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioCredencialRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.edu.chat.persistence.jdbc.JdbcCadastroUsuario;
import br.edu.chat.server.Servidor;
import br.edu.chat.config.ConfiguracaoTls;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.SenhaHasher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatIntegracaoTest {

    private static final String SENHA_TLS = "teste-integracao-tls";

    @Test
    void deveConectarClienteAoServidorReal() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            aguardarHandlers(contexto, 1);
            assertTrue(contexto.threadServidor.isAlive());
        } finally {
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveCadastrarV2DesconectarEAutenticarNovamenteComNomeCanonico() throws Exception {
        Contexto contexto = iniciarServidor();
        String login = "v2_reconexao_" + UUID.randomUUID().toString().replace('-', '_');
        String nomeCanonico = "Usuário V2 · Reconexão";
        char[] senha = "senha-v2-reconexao".toCharArray();
        ClienteChat primeiro = novoClienteSemAutenticacao(contexto, "Nome local");
        ClienteChat segundo = null;
        try {
            primeiro.cadastrarV2(login, nomeCanonico, senha);
            assertEquals(nomeCanonico, primeiro.getNomeAutenticado().orElseThrow());

            primeiro.fechar();

            segundo = novoClienteSemAutenticacao(contexto, "Outro nome local");
            segundo.conectarV2(login, senha);

            assertEquals(nomeCanonico, segundo.getNomeAutenticado().orElseThrow());
        } finally {
            Arrays.fill(senha, '\0');
            fechar(contexto, primeiro, segundo);
        }
    }

    @Test
    void deveRejeitarSenhaIncorretaAposCadastroV2() throws Exception {
        Contexto contexto = iniciarServidor();
        String login = "v2_senha_" + UUID.randomUUID().toString().replace('-', '_');
        char[] senhaCorreta = "senha-v2-correta".toCharArray();
        char[] senhaIncorreta = "senha-v2-incorreta".toCharArray();
        ClienteChat cadastro = novoClienteSemAutenticacao(contexto, "Cadastro V2");
        ClienteChat tentativa = null;
        try {
            cadastro.cadastrarV2(login, "Usuário Senha", senhaCorreta);
            cadastro.fechar();

            tentativa = novoClienteSemAutenticacao(contexto, "Tentativa");
            ClienteChat tentativaAtual = tentativa;
            assertThrows(IOException.class,
                    () -> tentativaAtual.conectarV2(login, senhaIncorreta));
        } finally {
            Arrays.fill(senhaCorreta, '\0');
            Arrays.fill(senhaIncorreta, '\0');
            fechar(contexto, cadastro, tentativa);
        }
    }

    @Test
    void deveRejeitarLoginInexistenteNoFluxoV2() throws Exception {
        Contexto contexto = iniciarServidor();
        char[] senha = "senha-v2-inexistente".toCharArray();
        ClienteChat cliente = novoClienteSemAutenticacao(contexto, "Login inexistente");
        try {
            assertThrows(IOException.class,
                    () -> cliente.conectarV2(
                            "login_inexistente_" + UUID.randomUUID(), senha));
        } finally {
            Arrays.fill(senha, '\0');
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveRejeitarCadastroV2ComLoginDuplicado() throws Exception {
        Contexto contexto = iniciarServidor();
        String login = "v2_duplicado_" + UUID.randomUUID().toString().replace('-', '_');
        char[] primeiraSenha = "senha-v2-primeira".toCharArray();
        char[] segundaSenha = "senha-v2-segunda".toCharArray();
        ClienteChat primeiro = novoClienteSemAutenticacao(contexto, "Primeiro");
        ClienteChat segundo = null;
        try {
            primeiro.cadastrarV2(login, "Ana duplicada", primeiraSenha);
            primeiro.fechar();

            segundo = novoClienteSemAutenticacao(contexto, "Segundo");
            ClienteChat segundoAtual = segundo;
            assertThrows(IOException.class,
                    () -> segundoAtual.cadastrarV2(login, "Outra Ana", segundaSenha));
        } finally {
            Arrays.fill(primeiraSenha, '\0');
            Arrays.fill(segundaSenha, '\0');
            fechar(contexto, primeiro, segundo);
        }
    }

    @Test
    void deveEnviarMensagemAoServidor() throws Exception {
        Contexto contexto = iniciarServidor();
        CountDownLatch recebida = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> recebida.countDown());
        try {
            origem.conectar();
            destino.conectar();
            origem.enviar("mensagem enviada");
            assertTrue(recebida.await(2, TimeUnit.SECONDS));
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveDistribuirBroadcastAOutroCliente() throws Exception {
        Contexto contexto = iniciarServidor();
        AtomicReference<Mensagem> recebida = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> {
            recebida.set(mensagem);
            chegou.countDown();
        });
        try {
            origem.conectar();
            destino.conectar();
            aguardarHandlers(contexto, 2);

            origem.enviar("broadcast");

            assertTrue(chegou.await(2, TimeUnit.SECONDS));
            assertEquals("broadcast", recebida.get().getConteudo());
            assertEquals("Ana", recebida.get().getRemetente().getNome());
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveRecuperarHistoricoAoConectarNovoCliente() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat observador = null;
        ClienteChat novoCliente = null;
        CountDownLatch mensagemPersistida = new CountDownLatch(1);
        CountDownLatch historicoRecebido = new CountDownLatch(1);
        List<Mensagem> mensagens = new ArrayList<>();
        try {
            origem.conectar();
            observador = novoCliente(contexto, "Caio", mensagem -> mensagemPersistida.countDown());
            observador.conectar();
            origem.enviar("mensagem histórica");

            assertTrue(mensagemPersistida.await(2, TimeUnit.SECONDS));
            origem.fechar();
            observador.fechar();

            novoCliente = novoCliente(contexto, "Bia", mensagem -> {
                mensagens.add(mensagem);
                historicoRecebido.countDown();
            });
            novoCliente.conectar();

            assertTrue(historicoRecebido.await(2, TimeUnit.SECONDS));
            assertEquals("mensagem histórica", mensagens.get(0).getConteudo());
        } finally {
            fechar(contexto, origem, observador, novoCliente);
        }
    }

    @Test
    void devePreservarCaractereSeparadorNoConteudo() throws Exception {
        Contexto contexto = iniciarServidor();
        AtomicReference<Mensagem> recebida = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> {
            recebida.set(mensagem);
            chegou.countDown();
        });
        try {
            origem.conectar();
            destino.conectar();
            origem.enviar("parte 1 | parte 2");

            assertTrue(chegou.await(2, TimeUnit.SECONDS));
            assertEquals("parte 1 | parte 2", recebida.get().getConteudo());
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveDistribuirMensagensSimultaneas() throws Exception {
        Contexto contexto = iniciarServidor();
        CountDownLatch recebidas = new CountDownLatch(2);
        List<String> conteudos = new ArrayList<>();
        ClienteChat origemUm = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat origemDois = novoCliente(contexto, "Bia", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Caio", mensagem -> {
            synchronized (conteudos) {
                conteudos.add(mensagem.getConteudo());
            }
            recebidas.countDown();
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            origemUm.conectar();
            origemDois.conectar();
            destino.conectar();
            CountDownLatch inicio = new CountDownLatch(1);
            executor.submit(() -> enviarAposInicio(inicio, origemUm, "A"));
            executor.submit(() -> enviarAposInicio(inicio, origemDois, "B"));
            inicio.countDown();

            assertTrue(recebidas.await(3, TimeUnit.SECONDS));
            synchronized (conteudos) {
                assertTrue(conteudos.contains("A"));
                assertTrue(conteudos.contains("B"));
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            fechar(contexto, origemUm, origemDois, destino);
        }
    }

    @Test
    void deveEncerrarThreadDoServidorComSeguranca() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            contexto.servidor.encerrar();
            assertTimeout(Duration.ofSeconds(3), () -> contexto.threadServidor.join(2000));
            assertFalse(contexto.threadServidor.isAlive());
        } finally {
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveEncerrarClientesQuandoServidorForEncerrado() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            aguardarHandlers(contexto, 1);
            contexto.servidor.encerrar();

            assertTimeout(Duration.ofSeconds(3), () -> {
                while (true) {
                    try {
                        cliente.enviar("não deve ser enviado");
                    } catch (IllegalStateException exception) {
                        return;
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
            });
        } finally {
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveInterromperHandshakeQuandoServidorNaoResponde() throws Exception {
        Path diretorio = Files.createTempDirectory("cliente-timeout-tls-");
        Path keytool = localizarKeytool();
        ConfiguracaoTls configuracao = criarConfiguracao(gerarMaterial(diretorio, keytool));
        CountDownLatch aceitou = new CountDownLatch(1);
        CountDownLatch encerrou = new CountDownLatch(1);
        AtomicReference<Throwable> falhaServidor = new AtomicReference<>();

        try (ServerSocket servidor = new ServerSocket(0)) {
            Thread observador = new Thread(() -> {
                try (Socket socket = servidor.accept()) {
                    aceitou.countDown();
                    socket.getInputStream().read();
                    encerrou.countDown();
                } catch (IOException exception) {
                    falhaServidor.set(exception);
                }
            }, "servidor-tls-sem-handshake");
            observador.start();

            ClienteChat cliente = new ClienteChat(
                    "127.0.0.1", servidor.getLocalPort(), "Ana", configuracao,
                    mensagem -> { }, () -> { });
            long inicio = System.nanoTime();
            try {
                assertThrows(IOException.class,
                        () -> cliente.conectar("ana", "senha-teste".toCharArray()));
                assertThrows(IllegalStateException.class, () -> cliente.enviar("não deve ser enviado"));
            } finally {
                cliente.fechar();
            }

            long duracao = System.nanoTime() - inicio;
            assertTrue(duracao < TimeUnit.SECONDS.toNanos(7));
            assertTrue(aceitou.await(1, TimeUnit.SECONDS));
            assertTrue(encerrou.await(2, TimeUnit.SECONDS));
            assertTrue(falhaServidor.get() == null, () ->
                    "O servidor de teste falhou: " + falhaServidor.get());
            observador.join(2000);
            assertFalse(observador.isAlive());
        }
    }

    private static Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String banco = "cliente_integracao_" + UUID.randomUUID().toString().replace('-', '_');
        ConnectionFactory conexoes = ConnectionFactory.emMemoria(banco);
        ChatService service = new ChatService(
                new ClientRegistry(),
                new JdbcMensagemRepository(conexoes));
        Path diretorio = Files.createTempDirectory("cliente-tls-");
        Path keytool = localizarKeytool();
        MaterialTls material = gerarMaterial(diretorio, keytool);
        ConfiguracaoTls configuracao = criarConfiguracao(material);
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(
                conexoes);
        JdbcUsuarioCredencialRepository credenciais = new JdbcUsuarioCredencialRepository(
                conexoes);
        AutenticacaoService autenticacao = new AutenticacaoService(
                new JdbcCadastroUsuario(conexoes),
                usuarios, credenciais, new SenhaHasher());
        cadastrarUsuarios(autenticacao);
        Servidor servidor = new Servidor(porta, service, configuracao, autenticacao);
        AtomicReference<Throwable> falha = new AtomicReference<>();
        Thread threadServidor = new Thread(() -> {
            try {
                servidor.iniciar();
            } catch (IOException exception) {
                falha.set(exception);
            }
        }, "servidor-integracao-cliente");
        threadServidor.start();
        aguardarPorta(porta, falha);
        return new Contexto(servidor, threadServidor, porta, configuracao);
    }

    private static ClienteChat novoCliente(Contexto contexto, String nome,
            java.util.function.Consumer<Mensagem> callback) {
        ClienteChat cliente = new ClienteChat("127.0.0.1", contexto.porta, nome,
                contexto.configuracao, callback, () -> { });
        try {
            cliente.conectar(nome.toLowerCase(), "senha-teste".toCharArray());
            return cliente;
        } catch (IOException exception) {
            cliente.fechar();
            throw new IllegalStateException("NÃ£o foi possÃ­vel autenticar cliente de teste.", exception);
        }
    }

    private static ClienteChat novoClienteSemAutenticacao(Contexto contexto, String nome) {
        return new ClienteChat("127.0.0.1", contexto.porta, nome,
                contexto.configuracao, mensagem -> { }, () -> { });
    }

    private static void cadastrarUsuarios(AutenticacaoService autenticacao) {
        for (String nome : List.of("Ana", "Bia", "Caio", "Preparador", "Victor", "Bruno")) {
            autenticacao.cadastrar(nome.toLowerCase(), nome, "senha-teste".toCharArray());
        }
    }

    private static void enviarAposInicio(CountDownLatch inicio, ClienteChat cliente, String conteudo) {
        try {
            inicio.await();
            cliente.enviar(conteudo);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void aguardarPorta(int porta, AtomicReference<Throwable> falha) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (falha.get() != null) {
                throw new AssertionError("O servidor não iniciou.", falha.get());
            }
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", porta)) {
                return;
            } catch (IOException exception) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        throw new IOException("Não foi possível iniciar o servidor de integração.");
    }

    private static void aguardarHandlers(Contexto contexto, int quantidade) throws Exception {
        assertTimeout(Duration.ofSeconds(3), () -> {
            while (handlersAtivos(contexto.servidor) < quantidade) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        });
    }

    private static int handlersAtivos(Servidor servidor) throws Exception {
        var campo = Servidor.class.getDeclaredField("handlersAtivos");
        campo.setAccessible(true);
        return ((java.util.Set<?>) campo.get(servidor)).size();
    }

    private static int encontrarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void fechar(Contexto contexto, ClienteChat... clientes) throws Exception {
        for (ClienteChat cliente : clientes) {
            if (cliente != null) {
                cliente.fechar();
            }
        }
        contexto.servidor.encerrar();
        contexto.threadServidor.join(2000);
    }

    private static void assertTimeout(Duration duracao, org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertTimeout(duracao, executable);
    }

    private static ConfiguracaoTls criarConfiguracao(MaterialTls material) {
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

    private static MaterialTls gerarMaterial(Path diretorio, Path keytool) throws Exception {
        Path keystore = diretorio.resolve("servidor.p12");
        Path certificado = diretorio.resolve("servidor.crt");
        Path truststore = diretorio.resolve("confianca.p12");
        executarKeytool(keytool, List.of("-genkeypair", "-alias", "servidor", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TLS, "-keypass", SENHA_TLS,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1"));
        executarKeytool(keytool, List.of("-exportcert", "-alias", "servidor", "-keystore",
                keystore.toString(), "-storetype", "PKCS12", "-storepass", SENHA_TLS,
                "-rfc", "-file", certificado.toString()));
        executarKeytool(keytool, List.of("-importcert", "-alias", "servidor", "-file",
                certificado.toString(), "-keystore", truststore.toString(), "-storetype",
                "PKCS12", "-storepass", SENHA_TLS, "-noprompt"));
        return new MaterialTls(keystore, truststore);
    }

    private static void executarKeytool(Path keytool, List<String> argumentos) throws Exception {
        ProcessBuilder processo = new ProcessBuilder();
        processo.command().add(keytool.toString());
        processo.command().addAll(argumentos);
        processo.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processo.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process iniciado = processo.start();
        try {
            assertTrue(iniciado.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, iniciado.exitValue());
        } finally {
            iniciado.destroyForcibly();
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
