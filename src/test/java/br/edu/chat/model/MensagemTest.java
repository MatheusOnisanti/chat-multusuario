package br.edu.chat.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensagemTest {

    @Test
    void mensagemValida_devePreservarRemetenteConteudoEHorario() {
        Usuario remetente = new Usuario("Ana");
        String conteudo = "Olá, turma!";
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 20, 30);

        Mensagem mensagem = new Mensagem(remetente, conteudo, horario);

        assertNotNull(mensagem);
        assertEquals(remetente, mensagem.getRemetente());
        assertEquals(conteudo, mensagem.getConteudo());
        assertEquals(horario, mensagem.getHorario());
    }

    @Test
    void conteudoNulo_deveLancarMensagemInvalidaException() {
        Usuario remetente = new Usuario("Ana");
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 20, 30);

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(remetente, null, horario)
        );
    }

    @Test
    void conteudoVazioOuApenasEspacos_deveLancarMensagemInvalidaException() {
        Usuario remetente = new Usuario("Ana");
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 20, 30);

        assertAll(
                () -> assertThrows(
                        MensagemInvalidaException.class,
                        () -> new Mensagem(remetente, "", horario)
                ),
                () -> assertThrows(
                        MensagemInvalidaException.class,
                        () -> new Mensagem(remetente, "   ", horario)
                )
        );
    }

    @Test
    void remetenteNulo_deveLancarMensagemInvalidaException() {
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 20, 30);

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(null, "Olá, turma!", horario)
        );
    }

    @Test
    void horarioNulo_deveLancarMensagemInvalidaException() {
        Usuario remetente = new Usuario("Ana");

        assertThrows(
                MensagemInvalidaException.class,
                () -> new Mensagem(remetente, "Olá, turma!", null)
        );
    }
}
