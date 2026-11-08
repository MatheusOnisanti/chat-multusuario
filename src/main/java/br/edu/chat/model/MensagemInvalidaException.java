package br.edu.chat.model;

/** Indica que os dados necessários para criar uma mensagem são inválidos. */
public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
