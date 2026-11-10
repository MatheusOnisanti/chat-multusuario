package br.edu.chat.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioTest {

    @Test
    void nomeValido_deveCriarUsuario() {
        Usuario usuario = new Usuario("Ana");

        assertNotNull(usuario);
        assertNull(usuario.getId());
        assertNull(usuario.getLogin());
        assertEquals("Ana", usuario.getNome());
        assertFalse(usuario.possuiIdentidadePersistente());
    }

    @Test
    void identidadePersistente_devePreservarIdLoginENome() {
        UUID id = UUID.randomUUID();

        Usuario usuario = new Usuario(id, "ana.silva", "Ana");

        assertEquals(id, usuario.getId());
        assertEquals("ana.silva", usuario.getLogin());
        assertEquals("Ana", usuario.getNome());
        assertTrue(usuario.possuiIdentidadePersistente());
    }

    @Test
    void nomeNulo_deveLancarIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(null));
    }

    @Test
    void nomeVazioOuApenasEspacos_deveLancarIllegalArgumentException() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Usuario("")),
                () -> assertThrows(IllegalArgumentException.class, () -> new Usuario("   "))
        );
    }

    @Test
    void identidadePersistente_deveRejeitarIdLoginENomeInvalidos() {
        UUID id = UUID.randomUUID();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(null, "ana", "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, null, "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "", "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "   ", "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "ana|silva", "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "ana\nsilva", "Ana")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "ana", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Usuario(id, "ana", "   "))
        );
    }
}
