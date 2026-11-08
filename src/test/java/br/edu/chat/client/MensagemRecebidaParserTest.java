package br.edu.chat.client;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MensagemRecebidaParserTest {

    @Test
    void deveInterpretarMensagemRecebida() {
        var resultado = MensagemRecebidaParser.interpretar(
                "Ana|2026-11-07T21:00|Olá, turma!");

        assertTrue(resultado.isPresent());
        Mensagem mensagem = resultado.orElseThrow();
        assertEquals("Ana", mensagem.getRemetente().getNome());
        assertEquals("Olá, turma!", mensagem.getConteudo());
        assertEquals(LocalDateTime.of(2026, 11, 7, 21, 0), mensagem.getHorario());
    }

    @Test
    void devePreservarSeparadoresNoConteudo() {
        var resultado = MensagemRecebidaParser.interpretar(
                "Ana|2026-11-07T21:00|parte 1|parte 2");

        assertTrue(resultado.isPresent());
        assertEquals("parte 1|parte 2", resultado.orElseThrow().getConteudo());
    }

    @Test
    void deveRejeitarLinhaInvalida() {
        assertFalse(MensagemRecebidaParser.interpretar("Ana|sem-horario|texto").isPresent());
        assertFalse(MensagemRecebidaParser.interpretar("linha sem separadores").isPresent());
        assertFalse(MensagemRecebidaParser.interpretar("Ana|2026-11-07T21:00|").isPresent());
    }

    @Test
    void deveRejeitarLinhaNula() {
        assertFalse(MensagemRecebidaParser.interpretar(null).isPresent());
    }
}
