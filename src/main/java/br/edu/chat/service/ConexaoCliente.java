package br.edu.chat.service;

import br.edu.chat.model.Mensagem;

/** Contrato de comunicação com um cliente, independente da tecnologia de rede. */
public interface ConexaoCliente {

    /**
     * Tenta enviar uma mensagem ao cliente.
     *
     * @return {@code true} quando o envio foi concluído; {@code false} quando falhou
     */
    boolean enviar(Mensagem mensagem);

    /** Solicita o encerramento da conexão do cliente. */
    void encerrar();
}
