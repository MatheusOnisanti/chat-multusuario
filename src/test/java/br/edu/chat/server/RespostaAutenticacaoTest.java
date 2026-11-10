package br.edu.chat.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespostaAutenticacaoTest {

    @Test
    void deveSerializarTodasAsRespostas() {
        assertEquals("OK|CADASTRADO", resposta(RespostaAutenticacao.Codigo.CADASTRADO).serializar());
        assertEquals("OK|AUTENTICADO", resposta(RespostaAutenticacao.Codigo.AUTENTICADO).serializar());
        assertEquals("ERRO|AUTENTICACAO", resposta(RespostaAutenticacao.Codigo.AUTENTICACAO).serializar());
        assertEquals("ERRO|CADASTRO", resposta(RespostaAutenticacao.Codigo.CADASTRO).serializar());
        assertEquals("ERRO|REQUISICAO_INVALIDA",
                resposta(RespostaAutenticacao.Codigo.REQUISICAO_INVALIDA).serializar());
        assertEquals("ERRO|NAO_AUTENTICADO",
                resposta(RespostaAutenticacao.Codigo.NAO_AUTENTICADO).serializar());
        assertEquals("ERRO|COMANDO_NAO_PERMITIDO",
                resposta(RespostaAutenticacao.Codigo.COMANDO_NAO_PERMITIDO).serializar());
    }

    @Test
    void deveInterpretarRespostasValidas() {
        for (RespostaAutenticacao.Codigo codigo : RespostaAutenticacao.Codigo.values()) {
            RespostaAutenticacao resposta = RespostaAutenticacao.interpretar(
                    resposta(codigo).serializar());

            assertEquals(codigo, resposta.getCodigo());
            assertEquals(codigo == RespostaAutenticacao.Codigo.CADASTRADO
                    || codigo == RespostaAutenticacao.Codigo.AUTENTICADO,
                    resposta.isSucesso());
        }
    }

    @Test
    void deveSerImutavelERejeitarCodigoNulo() {
        RespostaAutenticacao resposta = resposta(RespostaAutenticacao.Codigo.AUTENTICADO);

        assertEquals(RespostaAutenticacao.Codigo.AUTENTICADO, resposta.getCodigo());
        assertEquals("OK|AUTENTICADO", resposta.toString());
        assertThrows(NullPointerException.class, () -> RespostaAutenticacao.de(null));
    }

    @Test
    void deveRejeitarRespostasDesconhecidasOuMalformadas() {
        assertInvalida("");
        assertInvalida("OK");
        assertInvalida("OK|CADASTRADO|EXTRA");
        assertInvalida("ERRO||AUTENTICACAO");
        assertInvalida("DESCONHECIDO|AUTENTICADO");
    }

    @Test
    void deveRejeitarQuebrasDeLinha() {
        assertInvalida("OK|AUTENTICADO\n");
        assertInvalida("ERRO|AUTENTICACAO\r");
    }

    @Test
    void respostasNaoDevemConterInformacoesSensíveis() {
        for (RespostaAutenticacao.Codigo codigo : RespostaAutenticacao.Codigo.values()) {
            String texto = resposta(codigo).serializar().toLowerCase();

            assertFalse(texto.contains("senha"));
            assertFalse(texto.contains("hash"));
            assertFalse(texto.contains("salt"));
            assertTrue(texto.startsWith("ok|") || texto.startsWith("erro|"));
        }
    }

    @Test
    void deveSerializarEInterpretarRespostaVersionadaComNomeUnicode() {
        String nome = "Matheus | \u00e7\u00e3o";
        RespostaAutenticacao original = RespostaAutenticacao.deComNome(
                RespostaAutenticacao.Codigo.AUTENTICADO, nome);

        String serializado = original.serializarVersionada();
        RespostaAutenticacao interpretada = RespostaAutenticacao.interpretarVersionada(
                serializado);

        assertTrue(serializado.startsWith("OK|V2|AUTENTICADO|"));
        assertEquals(RespostaAutenticacao.Codigo.AUTENTICADO, interpretada.getCodigo());
        assertEquals(nome, interpretada.getNomeAutenticado().orElseThrow());
    }

    @Test
    void devePreservarRespostaLegadaSemNome() {
        RespostaAutenticacao resposta = resposta(RespostaAutenticacao.Codigo.AUTENTICADO);

        assertEquals("OK|AUTENTICADO", resposta.serializar());
        assertTrue(resposta.getNomeAutenticado().isEmpty());
        assertThrows(IllegalStateException.class, resposta::serializarVersionada);
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.interpretar("OK|V2|AUTENTICADO|TWF0aGV1cw=="));
    }

    @Test
    void deveRejeitarNomeInvalidoNaRespostaVersionada() {
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.deComNome(
                        RespostaAutenticacao.Codigo.AUTENTICADO, " "));
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.deComNome(
                        RespostaAutenticacao.Codigo.AUTENTICADO, "Nome\ninvalido"));
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.interpretarVersionada(
                        "OK|V2|AUTENTICADO|%%%"));
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.interpretarVersionada(
                        "OK|V2|AUTENTICADO|"));
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.interpretarVersionada(
                        "OK|V2|ERRO|TWF0aGV1cw=="));
    }

    private static RespostaAutenticacao resposta(RespostaAutenticacao.Codigo codigo) {
        return RespostaAutenticacao.de(codigo);
    }

    private static void assertInvalida(String texto) {
        assertThrows(IllegalArgumentException.class,
                () -> RespostaAutenticacao.interpretar(texto));
    }
}
