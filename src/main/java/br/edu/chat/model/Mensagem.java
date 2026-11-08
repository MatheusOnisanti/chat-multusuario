package br.edu.chat.model;

import java.time.LocalDateTime;

/** Representa uma mensagem enviada por um usuário em determinado horário. */
public final class Mensagem {

    private final Usuario remetente;
    private final String conteudo;
    private final LocalDateTime horario;

    public Mensagem(Usuario remetente, String conteudo, LocalDateTime horario) {
        if (remetente == null) {
            throw new MensagemInvalidaException("O remetente da mensagem não pode ser nulo.");
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new MensagemInvalidaException("O conteúdo da mensagem não pode ser nulo ou vazio.");
        }
        if (horario == null) {
            throw new MensagemInvalidaException("O horário da mensagem não pode ser nulo.");
        }

        this.remetente = remetente;
        this.conteudo = conteudo;
        this.horario = horario;
    }

    public Usuario getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }
}
