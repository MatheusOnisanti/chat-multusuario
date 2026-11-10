package br.edu.chat.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.net.SocketException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracaoTlsTest {

    private static final String SENHA_TESTE = "teste-handshake-tls";
    private static final int TIMEOUT_SEGUNDOS = 10;

    @TempDir
    Path diretorioTemporario;

    private Path keytool;

    @BeforeEach
    void localizarKeytool() {
        keytool = localizarKeytoolDoJdk();
    }

    @Test
    void deveRejeitarConfiguracaoNula() {
        char[] senha = "senha".toCharArray();
        assertThrows(NullPointerException.class,
                () -> new ConfiguracaoTls(null, senha, Path.of("truststore"), senha));
        assertThrows(NullPointerException.class,
                () -> new ConfiguracaoTls(Path.of("keystore"), senha, null, senha));
        assertThrows(NullPointerException.class,
                () -> new ConfiguracaoTls(Path.of("keystore"), null,
                        Path.of("truststore"), senha));
        assertThrows(NullPointerException.class,
                () -> new ConfiguracaoTls(Path.of("keystore"), senha,
                        Path.of("truststore"), null));
    }

    @Test
    void deveFalharQuandoArquivosNaoExistirem() {
        assertThrows(IllegalStateException.class,
                () -> new ConfiguracaoTls(Path.of("keystore-inexistente"),
                        "senha".toCharArray(), Path.of("truststore-inexistente"),
                        "senha".toCharArray()));
    }

    @Test
    void deveFalharAoConectarEmPortaFechada() throws Exception {
        ConfiguracaoTls configuracao = configuracaoDeTeste();
        int porta;
        try (java.net.ServerSocket servidor = new java.net.ServerSocket(0)) {
            porta = servidor.getLocalPort();
        }

        assertThrows(IOException.class, () -> configuracao.conectar("127.0.0.1", porta));
    }

    @Test
    void devePreservarSenhaDoChamador() {
        char[] senhaKeystore = "chave".toCharArray();
        char[] senhaTruststore = "confianca".toCharArray();
        char[] copiaKeystore = senhaKeystore.clone();
        char[] copiaTruststore = senhaTruststore.clone();

        assertThrows(IllegalStateException.class,
                () -> new ConfiguracaoTls(Path.of("keystore-inexistente"),
                        senhaKeystore, Path.of("truststore-inexistente"), senhaTruststore));

        assertArrayEquals(copiaKeystore, senhaKeystore);
        assertArrayEquals(copiaTruststore, senhaTruststore);
    }

    @Test
    void deveConfigurarProtocolosTlsPermitidosNoServidorECliente() throws Exception {
        ConfiguracaoTls configuracao = configuracaoDeTeste();
        try (SSLServerSocket servidor = configuracao.criarServidor(0);
             SSLSocket cliente = configuracao.criarCliente()) {
            assertProtocolosPermitidos(servidor.getEnabledProtocols());
            assertProtocolosPermitidos(cliente.getEnabledProtocols());
        }
    }

    @Test
    void deveHabilitarIdentificacaoDeEndpointHttpsNoCliente() throws Exception {
        try (SSLSocket cliente = configuracaoDeTeste().criarCliente()) {
            assertEquals("HTTPS", cliente.getSSLParameters()
                    .getEndpointIdentificationAlgorithm());
        }
    }

    @Test
    void naoDeveHabilitarTrustManagerPermissivo() throws Exception {
        try (SSLSocket cliente = configuracaoDeTeste().criarCliente()) {
            assertTrue(cliente.getSSLParameters()
                    .getEndpointIdentificationAlgorithm().equals("HTTPS"));
            assertFalse(Arrays.asList(cliente.getSupportedProtocols()).isEmpty());
        }
    }

    @Test
    void deveRealizarHandshakeETransportarPayloadComLocalhost() throws Exception {
        MaterialTls material = gerarMaterial("localhost", "dns:localhost,ip:127.0.0.1");
        assertHandshakeComPayload(material, "localhost");
    }

    @Test
    void deveRealizarHandshakeETransportarPayloadComEnderecoIp() throws Exception {
        MaterialTls material = gerarMaterial("localhost", "dns:localhost,ip:127.0.0.1");
        assertHandshakeComPayload(material, "127.0.0.1");
    }

    @Test
    void deveRejeitarCertificadoNaoConfiavel() throws Exception {
        MaterialTls servidor = gerarMaterial("localhost", "dns:localhost,ip:127.0.0.1");
        Path truststoreIncorreto = gerarTruststoreComCertificadoDiferente();
        ConfiguracaoTls configuracaoServidor = criarConfiguracao(servidor.keystore(),
                servidor.truststore());
        ConfiguracaoTls configuracaoCliente = criarConfiguracao(servidor.keystore(),
                truststoreIncorreto);

        try (SSLServerSocket socketServidor = configuracaoServidor.criarServidor(0)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
            Future<Void> servidorAceitou = executor.submit(() -> {
                aceitarHandshakeComFalha(socketServidor);
                return null;
            });
            assertHandshakeFalho(configuracaoCliente, socketServidor.getLocalPort(), "localhost");
            aguardarTarefa(servidorAceitou);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void deveRejeitarHostnameIncompativel() throws Exception {
        MaterialTls material = gerarMaterial("nao-corresponde.test", "dns:nao-corresponde.test");
        ConfiguracaoTls configuracaoServidor = criarConfiguracao(material.keystore(),
                material.truststore());
        ConfiguracaoTls configuracaoCliente = criarConfiguracao(material.keystore(),
                material.truststore());

        try (SSLServerSocket socketServidor = configuracaoServidor.criarServidor(0)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
            Future<Void> servidorAceitou = executor.submit(() -> {
                aceitarHandshakeComFalha(socketServidor);
                return null;
            });
            assertHandshakeFalho(configuracaoCliente, socketServidor.getLocalPort(), "localhost");
            aguardarTarefa(servidorAceitou);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void assertHandshakeComPayload(MaterialTls material, String host) throws Exception {
        ConfiguracaoTls configuracaoServidor = criarConfiguracao(material.keystore(),
                material.truststore());
        ConfiguracaoTls configuracaoCliente = criarConfiguracao(material.keystore(),
                material.truststore());

        try (SSLServerSocket socketServidor = configuracaoServidor.criarServidor(0)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
            socketServidor.setSoTimeout(TIMEOUT_SEGUNDOS * 1000);
            Future<Integer> payloadRecebido = executor.submit(() -> {
                try (SSLSocket socketAceito = (SSLSocket) socketServidor.accept()) {
                    socketAceito.setSoTimeout(TIMEOUT_SEGUNDOS * 1000);
                    socketAceito.startHandshake();
                    return socketAceito.getInputStream().read();
                }
            });

            try (SSLSocket socketCliente = configuracaoCliente.conectar(host,
                    socketServidor.getLocalPort())) {
                socketCliente.setSoTimeout(TIMEOUT_SEGUNDOS * 1000);
                socketCliente.startHandshake();
                try (OutputStream saida = socketCliente.getOutputStream()) {
                    saida.write(0x5A);
                    saida.flush();
                }
            }

            assertEquals(0x5A, payloadRecebido.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void assertHandshakeFalho(ConfiguracaoTls configuracaoCliente, int porta,
            String host) throws Exception {
        try (SSLSocket socketCliente = configuracaoCliente.conectar(host, porta)) {
            socketCliente.setSoTimeout(TIMEOUT_SEGUNDOS * 1000);
            assertThrows(SSLException.class, socketCliente::startHandshake);
        }
    }

    private static void aceitarHandshakeComFalha(SSLServerSocket servidor) throws IOException {
        try (SSLSocket socket = (SSLSocket) servidor.accept()) {
            socket.setSoTimeout(TIMEOUT_SEGUNDOS * 1000);
            boolean handshakeRejeitado = false;
            try {
                socket.startHandshake();
            } catch (SSLException | SocketException exception) {
                handshakeRejeitado = true;
            }
            if (!handshakeRejeitado) {
                throw new AssertionError("O servidor concluiu um handshake que deveria falhar.");
            }
        }
    }

    private static void aguardarTarefa(Future<?> tarefa) throws Exception {
        try {
            tarefa.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            tarefa.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            throw new AssertionError("A tarefa do servidor falhou.", exception.getCause());
        }
    }

    private ConfiguracaoTls criarConfiguracao(Path keystore, Path truststore) {
        char[] senhaKeystore = SENHA_TESTE.toCharArray();
        char[] senhaTruststore = SENHA_TESTE.toCharArray();
        try {
            return new ConfiguracaoTls(keystore, senhaKeystore, truststore, senhaTruststore);
        } finally {
            Arrays.fill(senhaKeystore, '\0');
            Arrays.fill(senhaTruststore, '\0');
        }
    }

    private MaterialTls gerarMaterial(String nomeComum, String san) throws Exception {
        Path keystore = diretorioTemporario.resolve("servidor-" + nomeComum + ".p12");
        Path certificado = diretorioTemporario.resolve("servidor-" + nomeComum + ".crt");
        Path truststore = diretorioTemporario.resolve("confianca-" + nomeComum + ".p12");
        executarKeytool(List.of("-genkeypair", "-alias", "servidor", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TESTE, "-keypass", SENHA_TESTE,
                "-dname", "CN=" + nomeComum, "-ext", "SAN=" + san));
        executarKeytool(List.of("-exportcert", "-alias", "servidor", "-keystore",
                keystore.toString(), "-storetype", "PKCS12", "-storepass", SENHA_TESTE,
                "-rfc", "-file", certificado.toString()));
        executarKeytool(List.of("-importcert", "-alias", "servidor", "-file",
                certificado.toString(), "-keystore", truststore.toString(), "-storetype",
                "PKCS12", "-storepass", SENHA_TESTE, "-noprompt"));
        return new MaterialTls(keystore, truststore);
    }

    private Path gerarTruststoreComCertificadoDiferente() throws Exception {
        Path keystore = diretorioTemporario.resolve("outro.p12");
        Path certificado = diretorioTemporario.resolve("outro.crt");
        Path truststore = diretorioTemporario.resolve("confianca-incorreta.p12");
        executarKeytool(List.of("-genkeypair", "-alias", "outro", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TESTE, "-keypass", SENHA_TESTE,
                "-dname", "CN=outro.test", "-ext", "SAN=dns:outro.test"));
        executarKeytool(List.of("-exportcert", "-alias", "outro", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TESTE, "-rfc", "-file",
                certificado.toString()));
        executarKeytool(List.of("-importcert", "-alias", "outro", "-file", certificado.toString(),
                "-keystore", truststore.toString(), "-storetype", "PKCS12", "-storepass",
                SENHA_TESTE, "-noprompt"));
        return truststore;
    }

    private void executarKeytool(List<String> argumentos) throws Exception {
        ProcessBuilder processo = new ProcessBuilder();
        processo.command().add(keytool.toString());
        processo.command().addAll(argumentos);
        processo.redirectError(ProcessBuilder.Redirect.DISCARD);
        processo.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process iniciado = processo.start();
        try {
            if (!iniciado.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                iniciado.destroyForcibly();
                throw new AssertionError("keytool excedeu o tempo limite.");
            }
            assertEquals(0, iniciado.exitValue(), "keytool falhou sem expor sua saída.");
        } finally {
            iniciado.destroyForcibly();
        }
    }

    private static Path localizarKeytoolDoJdk() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path executavel = javaHome.resolve("bin").resolve(nomeKeytool());
        if (!Files.isExecutable(executavel)) {
            executavel = javaHome.getParent().resolve("bin").resolve(nomeKeytool());
        }
        if (!Files.isExecutable(executavel)) {
            throw new IllegalStateException("keytool não está disponível no JDK utilizado nos testes.");
        }
        return executavel;
    }

    private static String nomeKeytool() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? "keytool.exe" : "keytool";
    }

    private record MaterialTls(Path keystore, Path truststore) {
    }

    private static ConfiguracaoTls configuracaoDeTeste() throws Exception {
        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(null, null, new SecureRandom());
        return new ConfiguracaoTls(contexto);
    }

    private static void assertProtocolosPermitidos(String[] protocolos) {
        assertTrue(protocolos.length > 0);
        assertTrue(Arrays.stream(protocolos)
                .allMatch(protocolo -> protocolo.equals("TLSv1.2")
                        || protocolo.equals("TLSv1.3")));
    }
}
