package br.edu.chat.persistence.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Cria conexões JDBC sem expor detalhes de banco ao domínio. */
public final class ConnectionFactory {

    private final String url;
    private final String usuario;
    private final String senha;

    public ConnectionFactory(String url, String usuario, String senha) {
        this.url = Objects.requireNonNull(url, "A URL do banco não pode ser nula.");
        this.usuario = Objects.requireNonNull(usuario, "O usuário do banco não pode ser nulo.");
        this.senha = Objects.requireNonNull(senha, "A senha do banco não pode ser nula.");
    }

    public static ConnectionFactory producao() {
        return new ConnectionFactory("jdbc:h2:file:./data/chat", "sa", "");
    }

    public static ConnectionFactory emMemoria(String nome) {
        Objects.requireNonNull(nome, "O nome do banco em memória não pode ser nulo.");
        return new ConnectionFactory("jdbc:h2:mem:" + nome + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    public Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }
}
