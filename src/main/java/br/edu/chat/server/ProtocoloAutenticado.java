package br.edu.chat.server;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Representa os comandos do protocolo autenticado sem depender de rede.
 *
 * <p>O Base64 resolve apenas a ambiguidade dos separadores. Ele não fornece
 * confidencialidade; senhas só poderão trafegar quando houver TLS.</p>
 */
public final class ProtocoloAutenticado {

    public static final int MAX_COMANDO_BYTES = 32_768;
    public static final int MAX_CAMPO_CODIFICADO = 24_576;
    public static final int MAX_LOGIN = 120;
    public static final int MAX_NOME = 120;
    public static final int MAX_SENHA = 256;
    public static final int MAX_CONTEUDO = 4_096;

    private ProtocoloAutenticado() {
    }

    public static Comando interpretar(byte[] dados) {
        Objects.requireNonNull(dados, "Os dados do comando não podem ser nulos.");
        if (dados.length == 0 || dados.length > MAX_COMANDO_BYTES) {
            throw erro();
        }

        String linha = decodificarUtf8(dados);
        return interpretarLinha(linha);
    }

    public static Comando interpretar(String linha) {
        Objects.requireNonNull(linha, "O comando não pode ser nulo.");
        if (linha.isEmpty()
                || linha.getBytes(StandardCharsets.UTF_8).length > MAX_COMANDO_BYTES) {
            throw erro();
        }
        return interpretarLinha(linha);
    }

    private static Comando interpretarLinha(String linha) {
        if (linha.indexOf('\r') >= 0 || linha.indexOf('\n') >= 0) {
            throw erro();
        }

        String[] campos = linha.split("\\|", -1);
        return switch (campos[0]) {
            case "CADASTRAR" -> cadastrar(campos);
            case "AUTENTICAR" -> autenticar(campos);
            case "CADASTRAR_V2" -> cadastrarV2(campos);
            case "AUTENTICAR_V2" -> autenticarV2(campos);
            case "MENSAGEM" -> mensagem(campos);
            default -> throw erro();
        };
    }

    private static Cadastro cadastrar(String[] campos) {
        exigirQuantidade(campos, 4);
        return new Cadastro(
                decodificarCampo(campos[1], MAX_LOGIN, false),
                decodificarCampo(campos[2], MAX_NOME, false),
                decodificarSenha(campos[3]));
    }

    private static Autenticacao autenticar(String[] campos) {
        exigirQuantidade(campos, 3);
        return new Autenticacao(
                decodificarCampo(campos[1], MAX_LOGIN, false),
                decodificarSenha(campos[2]));
    }

    private static CadastroV2 cadastrarV2(String[] campos) {
        exigirQuantidade(campos, 4);
        return new CadastroV2(
                decodificarCampo(campos[1], MAX_LOGIN, false),
                decodificarCampo(campos[2], MAX_NOME, false),
                decodificarSenha(campos[3]));
    }

    private static AutenticacaoV2 autenticarV2(String[] campos) {
        exigirQuantidade(campos, 3);
        return new AutenticacaoV2(
                decodificarCampo(campos[1], MAX_LOGIN, false),
                decodificarSenha(campos[2]));
    }

    private static Mensagem mensagem(String[] campos) {
        exigirQuantidade(campos, 2);
        return new Mensagem(decodificarCampo(campos[1], MAX_CONTEUDO, false));
    }

    private static void exigirQuantidade(String[] campos, int esperada) {
        if (campos.length != esperada) {
            throw erro();
        }
    }

    private static String decodificarCampo(String campo, int limite, boolean permitirQuebra) {
        if (campo == null || campo.isEmpty() || campo.length() > MAX_CAMPO_CODIFICADO
                || !base64Estrito(campo)) {
            throw erro();
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(campo);
        } catch (IllegalArgumentException exception) {
            throw erro();
        }

        String valor = decodificarUtf8(bytes);
        if (valor.isEmpty() || valor.length() > limite
                || (!permitirQuebra && contemQuebraDeLinha(valor))) {
            throw erro();
        }
        return valor;
    }

    private static char[] decodificarSenha(String campo) {
        return decodificarCampo(campo, MAX_SENHA, false).toCharArray();
    }

    private static boolean base64Estrito(String campo) {
        if (!campo.matches("[A-Za-z0-9+/]*={0,2}")) {
            return false;
        }
        try {
            byte[] decodificado = Base64.getDecoder().decode(campo);
            return Base64.getEncoder().encodeToString(decodificado).equals(campo);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String decodificarUtf8(byte[] bytes) {
        try {
            CharBuffer caracteres = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return caracteres.toString();
        } catch (CharacterCodingException exception) {
            throw erro();
        }
    }

    private static boolean contemQuebraDeLinha(String valor) {
        return valor.indexOf('\r') >= 0 || valor.indexOf('\n') >= 0;
    }

    private static IllegalArgumentException erro() {
        return new IllegalArgumentException("Comando autenticado inválido.");
    }

    public sealed interface Comando permits Cadastro, Autenticacao,
            CadastroV2, AutenticacaoV2, Mensagem {
    }

    public static final class Cadastro implements Comando {
        private final String login;
        private final String nome;
        private final char[] senha;

        private Cadastro(String login, String nome, char[] senha) {
            this.login = login;
            this.nome = nome;
            this.senha = senha.clone();
        }

        public String getLogin() {
            return login;
        }

        public String getNome() {
            return nome;
        }

        public char[] getSenha() {
            return senha.clone();
        }
    }

    public static final class Autenticacao implements Comando {
        private final String login;
        private final char[] senha;

        private Autenticacao(String login, char[] senha) {
            this.login = login;
            this.senha = senha.clone();
        }

        public String getLogin() {
            return login;
        }

        public char[] getSenha() {
            return senha.clone();
        }
    }

    public static final class CadastroV2 implements Comando {
        private final String login;
        private final String nome;
        private final char[] senha;

        private CadastroV2(String login, String nome, char[] senha) {
            this.login = login;
            this.nome = nome;
            this.senha = senha.clone();
        }

        public String getLogin() {
            return login;
        }

        public String getNome() {
            return nome;
        }

        public char[] getSenha() {
            return senha.clone();
        }
    }

    public static final class AutenticacaoV2 implements Comando {
        private final String login;
        private final char[] senha;

        private AutenticacaoV2(String login, char[] senha) {
            this.login = login;
            this.senha = senha.clone();
        }

        public String getLogin() {
            return login;
        }

        public char[] getSenha() {
            return senha.clone();
        }
    }

    public static final class Mensagem implements Comando {
        private final String conteudo;

        private Mensagem(String conteudo) {
            this.conteudo = conteudo;
        }

        public String getConteudo() {
            return conteudo;
        }
    }
}
