package br.edu.chat.persistence;

import java.util.Optional;
import java.util.UUID;

import br.edu.chat.model.Usuario;

/** Contrato de armazenamento e consulta de usuários persistentes. */
public interface UsuarioRepository {

    void cadastrar(Usuario usuario);

    Optional<Usuario> buscarPorId(UUID id);

    Optional<Usuario> buscarPorLogin(String login);
}
