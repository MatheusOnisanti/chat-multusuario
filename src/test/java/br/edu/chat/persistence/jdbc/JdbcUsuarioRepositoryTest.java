package br.edu.chat.persistence.jdbc;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.PersistenciaException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUsuarioRepositoryTest {

    @Test
    void deveCadastrarEConsultarPorUuid() {
        JdbcUsuarioRepository repository = novoRepository();
        Usuario esperado = usuario("ana.silva", "Ana");

        repository.cadastrar(esperado);

        Optional<Usuario> resultado = repository.buscarPorId(esperado.getId());
        assertTrue(resultado.isPresent());
        assertUsuario(esperado, resultado.orElseThrow());
    }

    @Test
    void deveCadastrarEConsultarPorLogin() {
        JdbcUsuarioRepository repository = novoRepository();
        Usuario esperado = usuario("bia.souza", "Bia");

        repository.cadastrar(esperado);

        Optional<Usuario> resultado = repository.buscarPorLogin(esperado.getLogin());
        assertTrue(resultado.isPresent());
        assertUsuario(esperado, resultado.orElseThrow());
    }

    @Test
    void deveRetornarVazioParaConsultasInexistentes() {
        JdbcUsuarioRepository repository = novoRepository();

        assertFalse(repository.buscarPorId(UUID.randomUUID()).isPresent());
        assertFalse(repository.buscarPorLogin("inexistente").isPresent());
    }

    @Test
    void deveRejeitarUsuarioLegado() {
        JdbcUsuarioRepository repository = novoRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.cadastrar(new Usuario("Ana")));
    }

    @Test
    void deveRejeitarLoginDuplicado() {
        JdbcUsuarioRepository repository = novoRepository();
        repository.cadastrar(usuario("ana", "Ana"));

        assertThrows(PersistenciaException.class,
                () -> repository.cadastrar(usuario("ana", "Outra Ana")));
    }

    @Test
    void devePreservarDadosCadastrados() {
        JdbcUsuarioRepository repository = novoRepository();
        UUID id = UUID.randomUUID();
        Usuario esperado = new Usuario(id, "carol.silva", "Carol Silva");

        repository.cadastrar(esperado);

        Usuario atual = repository.buscarPorLogin("carol.silva").orElseThrow();
        assertEquals(id, atual.getId());
        assertEquals("carol.silva", atual.getLogin());
        assertEquals("Carol Silva", atual.getNome());
        assertTrue(atual.possuiIdentidadePersistente());
    }

    @Test
    void deveIsolarBancosEmMemoriaComNomesDiferentes() {
        JdbcUsuarioRepository primeiro = novoRepository();
        JdbcUsuarioRepository segundo = novoRepository();
        Usuario usuarioPrimeiro = usuario("mesmo.login", "Primeiro");
        Usuario usuarioSegundo = usuario("mesmo.login", "Segundo");

        primeiro.cadastrar(usuarioPrimeiro);
        segundo.cadastrar(usuarioSegundo);

        assertEquals("Primeiro", primeiro.buscarPorLogin("mesmo.login")
                .orElseThrow().getNome());
        assertEquals("Segundo", segundo.buscarPorLogin("mesmo.login")
                .orElseThrow().getNome());
    }

    private static JdbcUsuarioRepository novoRepository() {
        String nome = "usuario_repository_test_"
                + UUID.randomUUID().toString().replace('-', '_');
        return new JdbcUsuarioRepository(ConnectionFactory.emMemoria(nome));
    }

    private static Usuario usuario(String login, String nome) {
        return new Usuario(UUID.randomUUID(), login, nome);
    }

    private static void assertUsuario(Usuario esperado, Usuario atual) {
        assertEquals(esperado.getId(), atual.getId());
        assertEquals(esperado.getLogin(), atual.getLogin());
        assertEquals(esperado.getNome(), atual.getNome());
        assertTrue(atual.possuiIdentidadePersistente());
    }
}
