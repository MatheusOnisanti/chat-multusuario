package br.edu.chat.server;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Resposta estrita para o fluxo de cadastro e autenticacao.
 *
 * <p>O cadastro bem-sucedido tambem autenticara a conexao quando essa resposta
 * for integrada ao {@link ClienteHandler}. Depois da autenticacao, o comando
 * {@code MENSAGEM} sera permitido; antes disso, ele devera ser rejeitado.</p>
 */
public final class RespostaAutenticacao {

    private static final String VERSAO_IDENTIDADE = "V2";

    public enum Codigo {
        CADASTRADO("OK|CADASTRADO", true),
        AUTENTICADO("OK|AUTENTICADO", true),
        AUTENTICACAO("ERRO|AUTENTICACAO", false),
        CADASTRO("ERRO|CADASTRO", false),
        REQUISICAO_INVALIDA("ERRO|REQUISICAO_INVALIDA", false),
        NAO_AUTENTICADO("ERRO|NAO_AUTENTICADO", false),
        COMANDO_NAO_PERMITIDO("ERRO|COMANDO_NAO_PERMITIDO", false);

        private final String texto;
        private final boolean sucesso;

        Codigo(String texto, boolean sucesso) {
            this.texto = texto;
            this.sucesso = sucesso;
        }

        private String texto() {
            return texto;
        }

        private boolean sucesso() {
            return sucesso;
        }
    }

    private final Codigo codigo;
    private final String nomeAutenticado;

    private RespostaAutenticacao(Codigo codigo) {
        this(codigo, null);
    }

    private RespostaAutenticacao(Codigo codigo, String nomeAutenticado) {
        this.codigo = Objects.requireNonNull(codigo, "O codigo nao pode ser nulo.");
        this.nomeAutenticado = nomeAutenticado;
    }

    public static RespostaAutenticacao de(Codigo codigo) {
        return new RespostaAutenticacao(codigo);
    }

    /** Cria a forma versionada que transporta a identidade canonica. */
    public static RespostaAutenticacao deComNome(Codigo codigo, String nome) {
        if (codigo != Codigo.CADASTRADO && codigo != Codigo.AUTENTICADO) {
            throw new IllegalArgumentException(
                    "Somente respostas de sucesso podem transportar identidade.");
        }
        validarNome(nome);
        return new RespostaAutenticacao(codigo, nome);
    }

    public static RespostaAutenticacao interpretar(String texto) {
        Objects.requireNonNull(texto, "A resposta nao pode ser nula.");
        if (texto.indexOf('\r') >= 0 || texto.indexOf('\n') >= 0) {
            throw erro();
        }

        for (Codigo codigo : Codigo.values()) {
            if (codigo.texto().equals(texto)) {
                return new RespostaAutenticacao(codigo);
            }
        }
        throw erro();
    }

    /** Interpreta somente a representacao versionada, sem alterar o parser legado. */
    public static RespostaAutenticacao interpretarVersionada(String texto) {
        Objects.requireNonNull(texto, "A resposta nao pode ser nula.");
        if (texto.indexOf('\r') >= 0 || texto.indexOf('\n') >= 0) {
            throw erro();
        }

        String[] campos = texto.split("\\|", -1);
        if (campos.length != 4 || !"OK".equals(campos[0])
                || !VERSAO_IDENTIDADE.equals(campos[1])) {
            throw erro();
        }

        Codigo codigo;
        try {
            codigo = Codigo.valueOf(campos[2]);
        } catch (IllegalArgumentException exception) {
            throw erro();
        }
        if (codigo != Codigo.CADASTRADO && codigo != Codigo.AUTENTICADO) {
            throw erro();
        }

        return new RespostaAutenticacao(codigo, decodificarNome(campos[3]));
    }

    public Codigo getCodigo() {
        return codigo;
    }

    public boolean isSucesso() {
        return codigo.sucesso();
    }

    public Optional<String> getNomeAutenticado() {
        return Optional.ofNullable(nomeAutenticado);
    }

    public String serializar() {
        return codigo.texto();
    }

    /** Serializa somente a representacao versionada com identidade. */
    public String serializarVersionada() {
        if (nomeAutenticado == null) {
            throw new IllegalStateException("A resposta nao possui identidade autenticada.");
        }
        String nomeCodificado = Base64.getEncoder().encodeToString(
                nomeAutenticado.getBytes(StandardCharsets.UTF_8));
        return "OK|" + VERSAO_IDENTIDADE + "|" + codigo.name() + "|" + nomeCodificado;
    }

    @Override
    public String toString() {
        return serializar();
    }

    private static IllegalArgumentException erro() {
        return new IllegalArgumentException("Resposta de autenticacao invalida.");
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()
                || nome.indexOf('\r') >= 0 || nome.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("O nome autenticado e invalido.");
        }
    }

    private static String decodificarNome(String valor) {
        if (valor.isEmpty()) {
            throw erro();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(valor);
            CharBuffer caracteres = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            String nome = caracteres.toString();
            validarNome(nome);
            return nome;
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw erro();
        }
    }
}
