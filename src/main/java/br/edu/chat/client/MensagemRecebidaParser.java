package br.edu.chat.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.MensagemInvalidaException;
import br.edu.chat.model.Usuario;

/** Interpreta mensagens recebidas do servidor no formato textual do chat. */
public final class MensagemRecebidaParser {

    private MensagemRecebidaParser() {
    }

    /**
     * Interpreta {@code remetente|horário|conteúdo}, preservando separadores no conteúdo.
     */
    public static Optional<Mensagem> interpretar(String linha) {
        if (linha == null) {
            return Optional.empty();
        }

        int primeiroSeparador = linha.indexOf('|');
        if (primeiroSeparador < 0) {
            return Optional.empty();
        }

        int segundoSeparador = linha.indexOf('|', primeiroSeparador + 1);
        if (segundoSeparador < 0) {
            return Optional.empty();
        }

        String nome = linha.substring(0, primeiroSeparador);
        String horarioTexto = linha.substring(primeiroSeparador + 1, segundoSeparador);
        String conteudo = linha.substring(segundoSeparador + 1);

        try {
            return Optional.of(new Mensagem(
                    new Usuario(nome),
                    conteudo,
                    LocalDateTime.parse(horarioTexto)));
        } catch (IllegalArgumentException | MensagemInvalidaException | DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
