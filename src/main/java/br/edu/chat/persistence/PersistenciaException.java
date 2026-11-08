package br.edu.chat.persistence;

/** Representa uma falha controlada ao acessar a persistência. */
public class PersistenciaException extends RuntimeException {

    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
