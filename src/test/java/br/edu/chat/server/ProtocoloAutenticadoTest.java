package br.edu.chat.server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocoloAutenticadoTest {

    @Test
    void deveInterpretarCadastroValido() {
        ProtocoloAutenticado.Comando comando = interpretar(
                "CADASTRAR", "login", "Nome", "senha-segura");

        ProtocoloAutenticado.Cadastro cadastro =
                (ProtocoloAutenticado.Cadastro) comando;
        assertEquals("login", cadastro.getLogin());
        assertEquals("Nome", cadastro.getNome());
        assertArrayEquals("senha-segura".toCharArray(), cadastro.getSenha());
    }

    @Test
    void deveInterpretarAutenticacaoValida() {
        ProtocoloAutenticado.Autenticacao autenticacao =
                (ProtocoloAutenticado.Autenticacao) interpretar(
                        "AUTENTICAR", "login", "senha");

        assertEquals("login", autenticacao.getLogin());
        assertArrayEquals("senha".toCharArray(), autenticacao.getSenha());
    }

    @Test
    void deveInterpretarCadastroV2ValidoComUnicodeESeparadores() {
        ProtocoloAutenticado.CadastroV2 cadastro =
                (ProtocoloAutenticado.CadastroV2) interpretar(
                        "CADASTRAR_V2", "usu\u00e1rio|especial", "Nome | \ud83d\ude0a",
                        "senha|segura");

        assertEquals("usu\u00e1rio|especial", cadastro.getLogin());
        assertEquals("Nome | \ud83d\ude0a", cadastro.getNome());
        assertArrayEquals("senha|segura".toCharArray(), cadastro.getSenha());
    }

    @Test
    void deveInterpretarAutenticacaoV2Valida() {
        ProtocoloAutenticado.AutenticacaoV2 autenticacao =
                (ProtocoloAutenticado.AutenticacaoV2) interpretar(
                        "AUTENTICAR_V2", "login", "senha");

        assertEquals("login", autenticacao.getLogin());
        assertArrayEquals("senha".toCharArray(), autenticacao.getSenha());
    }

    @Test
    void deveInterpretarMensagemValida() {
        ProtocoloAutenticado.Mensagem mensagem =
                (ProtocoloAutenticado.Mensagem) interpretar(
                        "MENSAGEM", "conteúdo da mensagem");

        assertEquals("conteúdo da mensagem", mensagem.getConteudo());
    }

    @Test
    void devePreservarSeparadoresEUnicode() {
        ProtocoloAutenticado.Cadastro cadastro =
                (ProtocoloAutenticado.Cadastro) interpretar(
                        "CADASTRAR", "usuário|especial", "Nome | 😊", "senha|segura");
        ProtocoloAutenticado.Mensagem mensagem =
                (ProtocoloAutenticado.Mensagem) interpretar(
                        "MENSAGEM", "Olá | mundo 😊");

        assertEquals("usuário|especial", cadastro.getLogin());
        assertEquals("Nome | 😊", cadastro.getNome());
        assertArrayEquals("senha|segura".toCharArray(), cadastro.getSenha());
        assertEquals("Olá | mundo 😊", mensagem.getConteudo());
    }

    @Test
    void deveRejeitarComandoDesconhecidoEQuantidadeIncorreta() {
        assertInvalido("DESCONHECIDO|x");
        assertInvalido("MENSAGEM");
        assertInvalido("AUTENTICAR|x|y|z");
        assertInvalido("CADASTRAR|x|y");
        assertInvalido("AUTENTICAR_V2|x|y|z");
        assertInvalido("CADASTRAR_V2|x|y");
    }

    @Test
    void deveRejeitarBase64Invalido() {
        assertInvalido("MENSAGEM|nao-base64!");
        assertInvalido("AUTENTICAR|bG9naW4|c2VuaGE=");
        assertInvalido("AUTENTICAR_V2|bG9naW4|c2VuaGE=");
        assertInvalido("CADASTRAR_V2|bG9naW4=|Tm9tZQ==|nao-base64!");
    }

    @Test
    void deveRejeitarUtf8Malformado() {
        byte[] dados = "MENSAGEM|".getBytes(StandardCharsets.US_ASCII);
        byte[] malformado = new byte[dados.length + 2];
        System.arraycopy(dados, 0, malformado, 0, dados.length);
        malformado[dados.length] = (byte) 0xC3;
        malformado[dados.length + 1] = 0x28;

        assertThrows(IllegalArgumentException.class,
                () -> ProtocoloAutenticado.interpretar(malformado));
    }

    @Test
    void deveRejeitarCamposVazios() {
        assertInvalido("MENSAGEM|");
        assertInvalido("AUTENTICAR||c2VuaGE=");
        assertInvalido("CADASTRAR|bG9naW4=||c2VuaGE=");
        assertInvalido("CADASTRAR|bG9naW4=|Tm9tZQ==|");
        assertInvalido("AUTENTICAR_V2||c2VuaGE=");
        assertInvalido("CADASTRAR_V2|bG9naW4=||c2VuaGE=");
        assertInvalido("CADASTRAR_V2|bG9naW4=|Tm9tZQ==|");
    }

    @Test
    void deveRejeitarEntradasAcimaDosLimites() {
        String campoGrande = Base64.getEncoder().encodeToString(
                "x".repeat(ProtocoloAutenticado.MAX_CONTEUDO + 1)
                        .getBytes(StandardCharsets.UTF_8));
        assertInvalido("MENSAGEM|" + campoGrande);

        byte[] comandoGrande = new byte[ProtocoloAutenticado.MAX_COMANDO_BYTES + 1];
        java.util.Arrays.fill(comandoGrande, (byte) 'x');
        assertThrows(IllegalArgumentException.class,
                () -> ProtocoloAutenticado.interpretar(comandoGrande));
    }

    @Test
    void deveRejeitarQuebrasDeLinhaNosCampos() {
        assertInvalido("AUTENTICAR_V2", "login", "senha\rquebrada");
        assertInvalido("CADASTRAR_V2", "login", "nome\ninvalid", "senha");
        assertInvalido("MENSAGEM", "linha\nquebrada");
        assertInvalido("AUTENTICAR", "login", "senha\rquebrada");
        assertInvalido("CADASTRAR", "login", "nome\ninválido", "senha");
    }

    @Test
    void errosNaoDevemExporCredenciais() {
        String senha = "senha-super-secreta";
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> ProtocoloAutenticado.interpretar(
                        "AUTENTICAR|@@@|" + codificar(senha)));

        assertEquals("Comando autenticado inválido.", excecao.getMessage());
        assertTrue(!excecao.getMessage().contains(senha));
    }

    private static ProtocoloAutenticado.Comando interpretar(String tipo,
            String... valores) {
        StringBuilder linha = new StringBuilder(tipo);
        for (String valor : valores) {
            linha.append('|').append(codificar(valor));
        }
        return ProtocoloAutenticado.interpretar(linha.toString());
    }

    private static void assertInvalido(String linha) {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocoloAutenticado.interpretar(linha));
    }

    private static void assertInvalido(String tipo, String... valores) {
        StringBuilder linha = new StringBuilder(tipo);
        for (String valor : valores) {
            linha.append('|').append(codificar(valor));
        }
        assertInvalido(linha.toString());
    }

    private static String codificar(String valor) {
        return Base64.getEncoder().encodeToString(
                valor.getBytes(StandardCharsets.UTF_8));
    }
}
