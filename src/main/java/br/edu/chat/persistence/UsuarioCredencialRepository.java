package br.edu.chat.persistence;

import java.util.Optional;
import java.util.UUID;

import br.edu.chat.service.CredencialSenha;

/** Contrato de armazenamento das credenciais derivadas dos usuários. */
public interface UsuarioCredencialRepository {

    void salvar(UUID usuarioId, CredencialSenha credencial);

    Optional<CredencialSenha> buscarPorUsuarioId(UUID usuarioId);
}
