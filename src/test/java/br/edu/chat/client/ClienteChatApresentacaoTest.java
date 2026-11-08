package br.edu.chat.client;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClienteChatApresentacaoTest {

    @Test
    void deveAceitarHostValido() {
        ClienteChatApresentacao.validarHost("localhost");
    }

    @Test
    void deveRejeitarHostNuloOuVazio() {
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarHost(null));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarHost("  "));
    }

    @Test
    void deveAceitarPortasNosLimites() {
        ClienteChatApresentacao.validarPorta(1);
        ClienteChatApresentacao.validarPorta(65535);
    }

    @Test
    void deveRejeitarPortasForaDoIntervalo() {
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarPorta(0));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarPorta(65536));
    }

    @Test
    void deveRejeitarNomeInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarNome(null));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarNome(" "));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarNome("Ana|Bia"));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarNome("Ana\nBia"));
    }

    @Test
    void deveRejeitarConteudoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarConteudo(null));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarConteudo("  "));
        assertThrows(IllegalArgumentException.class,
                () -> ClienteChatApresentacao.validarConteudo("linha\nquebrada"));
    }

    @Test
    void deveAceitarSeparadorDentroDoConteudo() {
        ClienteChatApresentacao.validarConteudo("parte 1 | parte 2");
    }

    @Test
    void deveFormatarMensagemComHoraRemetenteEConteudo() {
        Mensagem mensagem = new Mensagem(
                new Usuario("Ana"),
                "Olá | turma",
                LocalDateTime.of(2026, 11, 7, 9, 35));

        assertEquals("[09:35] Ana: Olá | turma", ClienteChatApresentacao.formatar(mensagem));
    }
}
