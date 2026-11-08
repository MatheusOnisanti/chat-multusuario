package br.edu.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuarioTest {

    @Test
    void nomeValido_deveCriarUsuario() {
        Usuario usuario = new Usuario("Ana");

        assertNotNull(usuario);
        assertEquals("Ana", usuario.getNome());
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
}
