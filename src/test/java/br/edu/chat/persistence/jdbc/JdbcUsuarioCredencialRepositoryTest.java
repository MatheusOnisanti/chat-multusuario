package br.edu.chat.persistence.jdbc;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.service.CredencialSenha;
import br.edu.chat.service.SenhaHasher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUsuarioCredencialRepositoryTest {

    @Test
    void deveSalvarERecuperarCredencial() {
        Contexto contexto = novoContexto();
        CredencialSenha esperada = contexto.hasher.gerar("segredo".toCharArray());

        contexto.repository.salvar(contexto.usuario.getId(), esperada);

        CredencialSenha atual = contexto.repository
                .buscarPorUsuarioId(contexto.usuario.getId()).orElseThrow();
        assertCredencial(esperada, atual);
    }

    @Test
    void devePreservarHashSaltIteracoesEAlgoritmo() {
        Contexto contexto = novoContexto();
        CredencialSenha esperada = new CredencialSenha(
                new byte[32], new byte[16], 123_456, "PBKDF2WithHmacSHA256");

        contexto.repository.salvar(contexto.usuario.getId(), esperada);

        CredencialSenha atual = contexto.repository
                .buscarPorUsuarioId(contexto.usuario.getId()).orElseThrow();
        assertCredencial(esperada, atual);
    }

    @Test
    void deveRetornarVazioParaUsuarioSemCredencial() {
        Contexto contexto = novoContexto();

        assertFalse(contexto.repository
                .buscarPorUsuarioId(contexto.usuario.getId()).isPresent());
    }

    @Test
    void deveRejeitarUuidOuCredencialNulos() {
        Contexto contexto = novoContexto();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        assertThrows(NullPointerException.class,
                () -> contexto.repository.salvar(null, credencial));
        assertThrows(NullPointerException.class,
                () -> contexto.repository.salvar(contexto.usuario.getId(), null));
        assertThrows(NullPointerException.class,
                () -> contexto.repository.buscarPorUsuarioId(null));
    }

    @Test
    void deveRejeitarCredencialDeUsuarioInexistente() {
        Contexto contexto = novoContexto();
        CredencialSenha credencial = contexto.hasher.gerar("segredo".toCharArray());

        assertThrows(PersistenciaException.class,
                () -> contexto.repository.salvar(UUID.randomUUID(), credencial));
    }

    @Test
    void deveRejeitarCredencialDuplicadaParaMesmoUsuario() {
        Contexto contexto = novoContexto();
        CredencialSenha primeira = contexto.hasher.gerar("primeira".toCharArray());
        CredencialSenha segunda = contexto.hasher.gerar("segunda".toCharArray());

        contexto.repository.salvar(contexto.usuario.getId(), primeira);

        assertThrows(PersistenciaException.class,
                () -> contexto.repository.salvar(contexto.usuario.getId(), segunda));
    }

    @Test
    void deveIsolarBancosEmMemoriaComNomesDiferentes() {
        Contexto primeiro = novoContexto();
        Contexto segundo = novoContexto();
        CredencialSenha credencial = primeiro.hasher.gerar("segredo".toCharArray());

        primeiro.repository.salvar(primeiro.usuario.getId(), credencial);

        assertTrue(primeiro.repository.buscarPorUsuarioId(primeiro.usuario.getId()).isPresent());
        assertFalse(segundo.repository.buscarPorUsuarioId(segundo.usuario.getId()).isPresent());
    }

    private static Contexto novoContexto() {
        ConnectionFactory factory = ConnectionFactory.emMemoria(
                "usuario_credencial_repository_test_"
                        + UUID.randomUUID().toString().replace('-', '_'));
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(factory);
        Usuario usuario = new Usuario(UUID.randomUUID(),
                "login_" + UUID.randomUUID().toString().replace('-', '_'), "Nome");
        usuarios.cadastrar(usuario);
        return new Contexto(new JdbcUsuarioCredencialRepository(factory),
                usuario, new SenhaHasher());
    }

    private static void assertCredencial(CredencialSenha esperada,
            CredencialSenha atual) {
        assertArrayEquals(esperada.getHash(), atual.getHash());
        assertArrayEquals(esperada.getSalt(), atual.getSalt());
        assertEquals(esperada.getIteracoes(), atual.getIteracoes());
        assertEquals(esperada.getAlgoritmo(), atual.getAlgoritmo());
    }

    private record Contexto(JdbcUsuarioCredencialRepository repository,
            Usuario usuario, SenhaHasher hasher) {
    }
}
