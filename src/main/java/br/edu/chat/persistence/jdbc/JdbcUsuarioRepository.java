package br.edu.chat.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.persistence.UsuarioRepository;

/** Implementa a persistência de usuários usando JDBC e H2. */
public final class JdbcUsuarioRepository implements UsuarioRepository {

    private static final String CRIAR_TABELA = """
            CREATE TABLE IF NOT EXISTS usuarios (
                id UUID PRIMARY KEY,
                login VARCHAR(120) NOT NULL UNIQUE,
                nome VARCHAR(120) NOT NULL,
                criado_em TIMESTAMP NOT NULL
            )
            """;
    private static final String INSERIR = """
            INSERT INTO usuarios (id, login, nome, criado_em)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
    private static final String CONSULTAR_POR_ID = """
            SELECT id, login, nome
            FROM usuarios
            WHERE id = ?
            """;
    private static final String CONSULTAR_POR_LOGIN = """
            SELECT id, login, nome
            FROM usuarios
            WHERE login = ?
            """;

    private final ConnectionFactory connectionFactory;

    public JdbcUsuarioRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory,
                "A fábrica de conexões não pode ser nula.");
        inicializarTabela();
    }

    @Override
    public void cadastrar(Usuario usuario) {
        Objects.requireNonNull(usuario, "O usuário não pode ser nulo.");
        if (!usuario.possuiIdentidadePersistente()) {
            throw new IllegalArgumentException(
                    "Somente usuários com identidade persistente podem ser cadastrados.");
        }

        try (Connection conexao = connectionFactory.abrir()) {
            cadastrar(conexao, usuario);
        } catch (SQLException exception) {
            throw new PersistenciaException("Não foi possível cadastrar o usuário.", exception);
        }
    }

    void cadastrar(Connection conexao, Usuario usuario) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(INSERIR)) {
            comando.setObject(1, usuario.getId());
            comando.setString(2, usuario.getLogin());
            comando.setString(3, usuario.getNome());
            comando.executeUpdate();
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        Objects.requireNonNull(id, "O identificador do usuário não pode ser nulo.");

        try (Connection conexao = connectionFactory.abrir();
             PreparedStatement comando = conexao.prepareStatement(CONSULTAR_POR_ID)) {
            comando.setObject(1, id);
            try (ResultSet resultados = comando.executeQuery()) {
                if (resultados.next()) {
                    return Optional.of(usuario(resultados));
                }
                return Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            if (exception instanceof PersistenciaException persistenciaException) {
                throw persistenciaException;
            }
            throw new PersistenciaException("Não foi possível consultar o usuário por ID.",
                    exception);
        }
    }

    @Override
    public Optional<Usuario> buscarPorLogin(String login) {
        Objects.requireNonNull(login, "O login não pode ser nulo.");

        try (Connection conexao = connectionFactory.abrir();
             PreparedStatement comando = conexao.prepareStatement(CONSULTAR_POR_LOGIN)) {
            comando.setString(1, login);
            try (ResultSet resultados = comando.executeQuery()) {
                if (resultados.next()) {
                    return Optional.of(usuario(resultados));
                }
                return Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            if (exception instanceof PersistenciaException persistenciaException) {
                throw persistenciaException;
            }
            throw new PersistenciaException(
                    "Não foi possível consultar o usuário por login.", exception);
        }
    }

    private static Usuario usuario(ResultSet resultados) throws SQLException {
        return new Usuario(
                resultados.getObject("id", UUID.class),
                resultados.getString("login"),
                resultados.getString("nome"));
    }

    private void inicializarTabela() {
        try (Connection conexao = connectionFactory.abrir();
             Statement comando = conexao.createStatement()) {
            comando.execute(CRIAR_TABELA);
        } catch (SQLException exception) {
            throw new PersistenciaException(
                    "Não foi possível inicializar a tabela de usuários.", exception);
        }
    }
}
