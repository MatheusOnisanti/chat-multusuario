package br.edu.chat.model;

/** Representa um usuário identificado pelo nome no chat. */
public final class Usuario {

    private final String nome;

    public Usuario(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário não pode ser nulo ou vazio.");
        }

        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
