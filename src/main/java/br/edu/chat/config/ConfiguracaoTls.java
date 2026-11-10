package br.edu.chat.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Configura TLS sem iniciar servidores ou conexões permanentes. */
public final class ConfiguracaoTls {

    private static final String TIPO_KEYSTORE = "PKCS12";
    public static final String TLS_13 = "TLSv1.3";
    public static final String TLS_12 = "TLSv1.2";
    public static final String IDENTIFICACAO_ENDPOINT = "HTTPS";
    private static final int TIMEOUT_CONEXAO_MILLIS = 5000;

    private final SSLContext contexto;

    /**
     * Carrega o material TLS a partir dos arquivos explicitamente informados.
     * As senhas recebidas não são armazenadas; cópias temporárias são limpas
     * depois do carregamento do keystore e do truststore.
     */
    public ConfiguracaoTls(Path caminhoKeystore, char[] senhaKeystore,
            Path caminhoTruststore, char[] senhaTruststore) {
        Objects.requireNonNull(caminhoKeystore, "O caminho do keystore não pode ser nulo.");
        Objects.requireNonNull(caminhoTruststore,
                "O caminho do truststore não pode ser nulo.");
        Objects.requireNonNull(senhaKeystore, "A senha do keystore não pode ser nula.");
        Objects.requireNonNull(senhaTruststore,
                "A senha do truststore não pode ser nula.");
        contexto = carregar(caminhoKeystore, senhaKeystore,
                caminhoTruststore, senhaTruststore);
    }

    ConfiguracaoTls(SSLContext contexto) {
        this.contexto = Objects.requireNonNull(contexto, "O contexto TLS não pode ser nulo.");
    }

    public SSLServerSocket criarServidor(int porta) throws IOException {
        SSLServerSocketFactory fabrica = contexto.getServerSocketFactory();
        SSLServerSocket servidor = (SSLServerSocket) fabrica.createServerSocket(porta);
        servidor.setEnabledProtocols(protocolosPermitidos(servidor.getSupportedProtocols()));
        return servidor;
    }

    public SSLSocket criarCliente() throws IOException {
        SSLSocketFactory fabrica = contexto.getSocketFactory();
        SSLSocket cliente = (SSLSocket) fabrica.createSocket();
        cliente.setEnabledProtocols(protocolosPermitidos(cliente.getSupportedProtocols()));
        SSLParameters parametros = cliente.getSSLParameters();
        parametros.setEndpointIdentificationAlgorithm(IDENTIFICACAO_ENDPOINT);
        cliente.setSSLParameters(parametros);
        return cliente;
    }

    public SSLSocket conectar(String host, int porta) throws IOException {
        Objects.requireNonNull(host, "O host não pode ser nulo.");
        SSLSocket cliente = (SSLSocket) contexto.getSocketFactory().createSocket();
        cliente.setEnabledProtocols(protocolosPermitidos(cliente.getSupportedProtocols()));
        SSLParameters parametros = cliente.getSSLParameters();
        parametros.setEndpointIdentificationAlgorithm(IDENTIFICACAO_ENDPOINT);
        cliente.setSSLParameters(parametros);
        try {
            cliente.connect(new InetSocketAddress(host, porta), TIMEOUT_CONEXAO_MILLIS);
            return cliente;
        } catch (IOException exception) {
            try {
                cliente.close();
            } catch (IOException fechamento) {
                exception.addSuppressed(fechamento);
            }
            throw exception;
        }
    }

    private static SSLContext carregar(Path caminhoKeystore, char[] senhaKeystore,
            Path caminhoTruststore, char[] senhaTruststore) {
        char[] copiaSenhaKeystore = senhaKeystore.clone();
        char[] copiaSenhaTruststore = senhaTruststore.clone();
        try {
            KeyStore keystore = carregarKeyStore(caminhoKeystore, copiaSenhaKeystore);
            KeyStore truststore = carregarKeyStore(caminhoTruststore, copiaSenhaTruststore);
            KeyManagerFactory fabricaChaves = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory fabricaConfianca = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            fabricaChaves.init(keystore, copiaSenhaKeystore);
            fabricaConfianca.init(truststore);

            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(fabricaChaves.getKeyManagers(),
                    fabricaConfianca.getTrustManagers(), new SecureRandom());
            return contexto;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Não foi possível carregar a configuração TLS.", exception);
        } finally {
            Arrays.fill(copiaSenhaKeystore, '\0');
            Arrays.fill(copiaSenhaTruststore, '\0');
        }
    }

    private static KeyStore carregarKeyStore(Path caminho, char[] senha) throws IOException,
            GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(TIPO_KEYSTORE);
        try (InputStream entrada = Files.newInputStream(caminho)) {
            keyStore.load(entrada, senha);
        }
        return keyStore;
    }

    private static String[] protocolosPermitidos(String[] suportados) {
        List<String> protocolos = new ArrayList<>();
        if (Arrays.asList(suportados).contains(TLS_13)) {
            protocolos.add(TLS_13);
        }
        if (Arrays.asList(suportados).contains(TLS_12)) {
            protocolos.add(TLS_12);
        }
        if (protocolos.isEmpty()) {
            throw new IllegalStateException("A JVM não oferece TLS 1.2 ou superior.");
        }
        return protocolos.toArray(String[]::new);
    }
}
