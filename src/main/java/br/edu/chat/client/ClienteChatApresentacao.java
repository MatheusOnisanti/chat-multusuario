package br.edu.chat.client;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

import br.edu.chat.model.Mensagem;

/** Valida entradas e formata mensagens sem depender de Swing ou rede. */
public final class ClienteChatApresentacao {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ClienteChatApresentacao() {
    }

    public static void validarHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("O host não pode ser nulo ou vazio.");
        }
    }

    public static void validarPorta(int porta) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }
    }

    public static void validarNome(String nome) {
        if (nome == null || nome.isBlank() || contemSeparadorOuQuebraDeLinha(nome)) {
            throw new IllegalArgumentException(
                    "O nome deve ser válido e não conter | ou quebras de linha.");
        }
    }

    public static void validarConteudo(String conteudo) {
        if (conteudo == null || conteudo.isBlank() || contemQuebraDeLinha(conteudo)) {
            throw new IllegalArgumentException(
                    "O conteúdo deve ser válido e não conter quebras de linha.");
        }
    }

    public static String formatar(Mensagem mensagem) {
        Objects.requireNonNull(mensagem, "A mensagem não pode ser nula.");
        return "[" + mensagem.getHorario().format(FORMATO_HORA) + "] "
                + mensagem.getRemetente().getNome() + ": "
                + mensagem.getConteudo();
    }

    private static boolean contemSeparadorOuQuebraDeLinha(String texto) {
        return texto.indexOf('|') >= 0 || contemQuebraDeLinha(texto);
    }

    private static boolean contemQuebraDeLinha(String texto) {
        return texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0;
    }
}
