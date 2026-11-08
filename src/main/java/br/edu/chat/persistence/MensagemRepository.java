package br.edu.chat.persistence;

import java.util.List;

import br.edu.chat.model.Mensagem;

/** Contrato de armazenamento e consulta das mensagens do chat. */
public interface MensagemRepository {

    void salvar(Mensagem mensagem);

    List<Mensagem> listarHistorico();
}
