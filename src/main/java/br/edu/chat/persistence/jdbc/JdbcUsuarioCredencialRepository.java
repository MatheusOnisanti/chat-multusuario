package br.edu.chat.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.persistence.UsuarioCredencialRepository;
import br.edu.chat.service.CredencialSenha;

/** Implementa a persistência JDBC das credenciais dos usuários. */
public final class JdbcUsuarioCredencialRepository implements UsuarioCredencialRepository {

    private static final String CRIAR_TABELA = """
            CREATE TABLE IF NOT EXISTS usuario_credenciais (
                usuario_id UUID PRIMARY KEY,
                senha_hash VARBINARY(32) NOT NULL,
                senha_salt VARBINARY(16) NOT NULL,
                senha_iteracoes INT NOT NULL,
                algoritmo VARCHAR(80) NOT NULL,
                CONSTRAINT fk_usuario_credenciais_usuario
                    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
            )
            """;
    private static final String INSERIR = """
            INSERT INTO usuario_credenciais
                (usuario_id, senha_hash, senha_salt, senha_iteracoes, algoritmo)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String CONSULTAR_POR_USUARIO = """
            SELECT senha_hash, senha_salt, senha_iteracoes, algoritmo
            FROM usuario_credenciais
            WHERE usuario_id = ?
            """;

    private final ConnectionFactory connectionFactory;

    public JdbcUsuarioCredencialRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory,
                "A fábrica de conexões não pode ser nula.");
        inicializarTabela();
    }

    @Override
    public void salvar(UUID usuarioId, CredencialSenha credencial) {
        Objects.requireNonNull(usuarioId,
                "O identificador do usuário não pode ser nulo.");
        Objects.requireNonNull(credencial, "A credencial não pode ser nula.");

        try (Connection conexao = connectionFactory.abrir()) {
            salvar(conexao, usuarioId, credencial);
        } catch (SQLException exception) {
            throw new PersistenciaException(
                    "Não foi possível salvar a credencial do usuário.", exception);
        }
    }

    void salvar(Connection conexao, UUID usuarioId, CredencialSenha credencial)
            throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(INSERIR)) {
            comando.setObject(1, usuarioId);
            comando.setBytes(2, credencial.getHash());
            comando.setBytes(3, credencial.getSalt());
            comando.setInt(4, credencial.getIteracoes());
            comando.setString(5, credencial.getAlgoritmo());
            comando.executeUpdate();
        }
    }

    @Override
    public Optional<CredencialSenha> buscarPorUsuarioId(UUID usuarioId) {
        Objects.requireNonNull(usuarioId,
                "O identificador do usuário não pode ser nulo.");

        try (Connection conexao = connectionFactory.abrir();
             PreparedStatement comando = conexao.prepareStatement(CONSULTAR_POR_USUARIO)) {
            comando.setObject(1, usuarioId);
            try (ResultSet resultados = comando.executeQuery()) {
                if (resultados.next()) {
                    return Optional.of(credencial(resultados));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenciaException(
                    "Não foi possível consultar a credencial do usuário.", exception);
        }
    }

    private static CredencialSenha credencial(ResultSet resultados) throws SQLException {
        return new CredencialSenha(
                resultados.getBytes("senha_hash"),
                resultados.getBytes("senha_salt"),
                resultados.getInt("senha_iteracoes"),
                resultados.getString("algoritmo"));
    }

    private void inicializarTabela() {
        try (Connection conexao = connectionFactory.abrir();
             Statement comando = conexao.createStatement()) {
            comando.execute(CRIAR_TABELA);
        } catch (SQLException exception) {
            throw new PersistenciaException(
                    "Não foi possível inicializar a tabela de credenciais dos usuários.",
                    exception);
        }
    }
}
