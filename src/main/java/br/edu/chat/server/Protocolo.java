package br.edu.chat.server;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.MensagemInvalidaException;
import br.edu.chat.model.Usuario;

/** Interpreta e serializa mensagens no protocolo textual do chat. */
public final class Protocolo {

    private Protocolo() {
    }

    /**
     * Interpreta uma linha no formato {@code remetente|conteúdo}.
     * O conteúdo é preservado a partir do primeiro separador.
     */
    public static Optional<Mensagem> interpretar(String linha) {
        if (linha == null) {
            return Optional.empty();
        }

        int separador = linha.indexOf('|');
        if (separador < 0) {
            return Optional.empty();
        }

        String nome = linha.substring(0, separador);
        String conteudo = linha.substring(separador + 1);

        try {
            Usuario remetente = new Usuario(nome);
            return Optional.of(new Mensagem(remetente, conteudo, LocalDateTime.now()));
        } catch (IllegalArgumentException | MensagemInvalidaException exception) {
            return Optional.empty();
        }
    }

    /** Serializa uma mensagem no formato {@code remetente|horário|conteúdo}. */
    public static String serializar(Mensagem mensagem) {
        Objects.requireNonNull(mensagem, "A mensagem não pode ser nula.");
        return mensagem.getRemetente().getNome()
                + "|" + mensagem.getHorario()
                + "|" + mensagem.getConteudo();
    }
}
