package br.edu.chat.persistence.jdbc;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.service.CredencialSenha;
import br.edu.chat.service.SenhaHasher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCadastroUsuarioTest {

    @Test
    void deveCadastrarUsuarioECredencialNaMesmaTransacao() {
        Contexto contexto = novoContexto();
        Usuario usuario = novoUsuario();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        contexto.cadastro.cadastrar(usuario, credencial);

        assertTrue(contexto.usuarios.buscarPorId(usuario.getId()).isPresent());
        assertTrue(contexto.credenciais.buscarPorUsuarioId(usuario.getId()).isPresent());
    }

    @Test
    void deveRecuperarPosteriormenteUsuarioECredencial() {
        Contexto contexto = novoContexto();
        Usuario esperado = novoUsuario();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        contexto.cadastro.cadastrar(esperado, credencial);

        Usuario atual = contexto.usuarios.buscarPorLogin(esperado.getLogin()).orElseThrow();
        CredencialSenha credencialAtual = contexto.credenciais
                .buscarPorUsuarioId(esperado.getId()).orElseThrow();
        assertEquals(esperado.getId(), atual.getId());
        assertEquals(esperado.getNome(), atual.getNome());
        assertTrue(contexto.hasher.verificar("segredo".toCharArray(), credencialAtual));
    }

    @Test
    void deveExecutarRollbackQuandoInsercaoDaCredencialFalhar() {
        Contexto contexto = novoContexto();
        Usuario usuario = novoUsuario();
        CredencialSenha credencial = new CredencialSenha(
                new byte[32], new byte[16], 1, "x".repeat(81));

        assertThrows(PersistenciaException.class,
                () -> contexto.cadastro.cadastrar(usuario, credencial));

        assertFalse(contexto.usuarios.buscarPorId(usuario.getId()).isPresent());
        assertFalse(contexto.credenciais.buscarPorUsuarioId(usuario.getId()).isPresent());
    }

    @Test
    void deveRejeitarUsuarioLegadoEArgumentosInvalidos() {
        Contexto contexto = novoContexto();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        assertThrows(IllegalArgumentException.class,
                () -> contexto.cadastro.cadastrar(new Usuario("Legado"), credencial));
        assertThrows(NullPointerException.class,
                () -> contexto.cadastro.cadastrar(null, credencial));
        assertThrows(NullPointerException.class,
                () -> contexto.cadastro.cadastrar(novoUsuario(), null));
    }

    @Test
    void devePreservarMetodosPublicosDosRepositorios() {
        Contexto contexto = novoContexto();
        Usuario usuario = novoUsuario();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        contexto.usuarios.cadastrar(usuario);
        contexto.credenciais.salvar(usuario.getId(), credencial);

        assertTrue(contexto.usuarios.buscarPorId(usuario.getId()).isPresent());
        assertTrue(contexto.credenciais.buscarPorUsuarioId(usuario.getId()).isPresent());
    }

    @Test
    void deveIsolarBancosH2EmMemoria() {
        Contexto primeiro = novoContexto();
        Contexto segundo = novoContexto();
        Usuario usuario = novoUsuario();
        CredencialSenha credencial = primeiro.hasher.gerar("segredo".toCharArray());

        primeiro.cadastro.cadastrar(usuario, credencial);

        assertTrue(primeiro.usuarios.buscarPorId(usuario.getId()).isPresent());
        assertFalse(segundo.usuarios.buscarPorId(usuario.getId()).isPresent());
    }

    private static Contexto novoContexto() {
        ConnectionFactory factory = ConnectionFactory.emMemoria(
                "cadastro_usuario_test_"
                        + UUID.randomUUID().toString().replace('-', '_'));
        return new Contexto(new JdbcCadastroUsuario(factory),
                new JdbcUsuarioRepository(factory),
                new JdbcUsuarioCredencialRepository(factory), new SenhaHasher());
    }

    private static Usuario novoUsuario() {
        return new Usuario(UUID.randomUUID(),
                "login_" + UUID.randomUUID().toString().replace('-', '_'), "Nome");
    }

    private record Contexto(JdbcCadastroUsuario cadastro,
            JdbcUsuarioRepository usuarios,
            JdbcUsuarioCredencialRepository credenciais,
            SenhaHasher hasher) {
    }
}
