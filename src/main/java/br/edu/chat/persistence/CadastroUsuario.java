package br.edu.chat.persistence;

import br.edu.chat.model.Usuario;
import br.edu.chat.service.CredencialSenha;

/** Contrato para cadastro atômico de usuário e credencial. */
public interface CadastroUsuario {

    void cadastrar(Usuario usuario, CredencialSenha credencial);
}
