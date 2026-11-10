package br.edu.chat.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.CadastroUsuario;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.service.CredencialSenha;

/** Cadastra um usuário e sua credencial em uma única transação JDBC. */
public final class JdbcCadastroUsuario implements CadastroUsuario {

    private final ConnectionFactory connectionFactory;
    private final JdbcUsuarioRepository usuarioRepository;
    private final JdbcUsuarioCredencialRepository credencialRepository;

    public JdbcCadastroUsuario(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory,
                "A fábrica de conexões não pode ser nula.");
        this.usuarioRepository = new JdbcUsuarioRepository(connectionFactory);
        this.credencialRepository = new JdbcUsuarioCredencialRepository(connectionFactory);
    }

    @Override
    public void cadastrar(Usuario usuario, CredencialSenha credencial) {
        Objects.requireNonNull(usuario, "O usuário não pode ser nulo.");
        Objects.requireNonNull(credencial, "A credencial não pode ser nula.");
        if (!usuario.possuiIdentidadePersistente() || usuario.getId() == null) {
            throw new IllegalArgumentException(
                    "Somente usuários com identidade persistente podem ser cadastrados.");
        }

        Connection conexao = null;
        try {
            conexao = connectionFactory.abrir();
            conexao.setAutoCommit(false);
            usuarioRepository.cadastrar(conexao, usuario);
            credencialRepository.salvar(conexao, usuario.getId(), credencial);
            conexao.commit();
        } catch (SQLException | RuntimeException exception) {
            rollback(conexao, exception);
            if (exception instanceof PersistenciaException persistenciaException) {
                throw persistenciaException;
            }
            throw new PersistenciaException(
                    "Não foi possível cadastrar usuário e credencial.", exception);
        } finally {
            fechar(conexao);
        }
    }

    private static void rollback(Connection conexao, Exception causa) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.rollback();
        } catch (SQLException rollbackException) {
            causa.addSuppressed(rollbackException);
        }
    }

    private static void fechar(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.close();
        } catch (SQLException closeException) {
            throw new PersistenciaException(
                    "Não foi possível fechar a conexão do cadastro.", closeException);
        }
    }
}
