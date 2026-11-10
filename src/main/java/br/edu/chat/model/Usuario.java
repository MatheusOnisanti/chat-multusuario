package br.edu.chat.model;

import java.util.UUID;

/** Representa um usuário identificado pelo nome no chat. */
public final class Usuario {

    private final UUID id;
    private final String login;
    private final String nome;

    public Usuario(String nome) {
        validarNome(nome);
        this.id = null;
        this.login = null;
        this.nome = nome;
    }

    public Usuario(UUID id, String login, String nome) {
        if (id == null) {
            throw new IllegalArgumentException("O identificador do usuário não pode ser nulo.");
        }
        validarLogin(login);
        validarNome(nome);
        this.id = id;
        this.login = login;
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getNome() {
        return nome;
    }

    public boolean possuiIdentidadePersistente() {
        return id != null;
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário não pode ser nulo ou vazio.");
        }
    }

    private static void validarLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("O login não pode ser nulo ou vazio.");
        }
        if (login.indexOf('|') >= 0 || login.indexOf('\r') >= 0
                || login.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "O login não pode conter | ou quebras de linha.");
        }
    }
}
