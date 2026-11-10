package br.edu.chat.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import br.edu.chat.config.ConfiguracaoTls;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcCadastroUsuario;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioCredencialRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.service.SenhaHasher;

/** Ponto de entrada para iniciar o servidor TCP na porta padrão do projeto. */
public final class ServidorApp {

    private static final int PORTA_PADRAO = 5000;

    private ServidorApp() {
    }

    public static void main(String[] args) throws IOException {
        char[] senhaKeystore = obrigatoria("CHAT_TLS_KEYSTORE_PASSWORD").toCharArray();
        char[] senhaTruststore = obrigatoria("CHAT_TLS_TRUSTSTORE_PASSWORD").toCharArray();
        try {
            ConfiguracaoTls configuracaoTls = new ConfiguracaoTls(
                    Path.of(obrigatoria("CHAT_TLS_KEYSTORE")), senhaKeystore,
                    Path.of(obrigatoria("CHAT_TLS_TRUSTSTORE")), senhaTruststore);
            ConnectionFactory conexoes = ConnectionFactory.producao();
            JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(conexoes);
            JdbcUsuarioCredencialRepository credenciais = new JdbcUsuarioCredencialRepository(conexoes);
            AutenticacaoService autenticacao = new AutenticacaoService(
                    new JdbcCadastroUsuario(conexoes), usuarios, credenciais, new SenhaHasher());
            ChatService chat = new ChatService(new ClientRegistry(),
                    new JdbcMensagemRepository(conexoes));
            new Servidor(PORTA_PADRAO, chat, configuracaoTls, autenticacao).iniciar();
        } finally {
            Arrays.fill(senhaKeystore, '\0');
            Arrays.fill(senhaTruststore, '\0');
        }
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Configuração TLS ausente: " + nome);
        }
        return valor;
    }
}
