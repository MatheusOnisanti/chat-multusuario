package br.edu.chat.server;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocoloTest {

    @Test
    void deveInterpretarRemetenteEConteudoPreservandoSeparadores() {
        Mensagem mensagem = Protocolo.interpretar("Ana|texto com | no conteúdo").orElseThrow();

        assertNotNull(mensagem);
        assertEquals("Ana", mensagem.getRemetente().getNome());
        assertEquals("texto com | no conteúdo", mensagem.getConteudo());
        assertNotNull(mensagem.getHorario());
    }

    @Test
    void deveDescartarLinhasInvalidas() {
        assertAll(
                () -> assertFalse(Protocolo.interpretar("sem separador").isPresent()),
                () -> assertFalse(Protocolo.interpretar("   |conteúdo").isPresent()),
                () -> assertFalse(Protocolo.interpretar("Ana|   ").isPresent()),
                () -> assertFalse(Protocolo.interpretar(null).isPresent())
        );
    }

    @Test
    void deveSerializarRemetenteHorarioEConteudo() {
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 21, 30);
        Mensagem mensagem = new Mensagem(new Usuario("Ana"), "texto | preservado", horario);

        String linha = Protocolo.serializar(mensagem);

        assertTrue(linha.startsWith("Ana|2026-11-07T21:30|"));
        assertTrue(linha.endsWith("texto | preservado"));
    }
}
