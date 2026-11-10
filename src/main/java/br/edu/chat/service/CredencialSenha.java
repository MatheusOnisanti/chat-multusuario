package br.edu.chat.service;

/** Representa uma credencial derivada sem armazenar a senha original. */
public final class CredencialSenha {

    private final byte[] hash;
    private final byte[] salt;
    private final int iteracoes;
    private final String algoritmo;

    public CredencialSenha(byte[] hash, byte[] salt, int iteracoes, String algoritmo) {
        validarHash(hash);
        validarSalt(salt);
        if (iteracoes <= 0) {
            throw new IllegalArgumentException("O número de iterações deve ser positivo.");
        }
        if (algoritmo == null || algoritmo.isBlank()) {
            throw new IllegalArgumentException("O algoritmo não pode ser nulo ou vazio.");
        }
        this.hash = hash.clone();
        this.salt = salt.clone();
        this.iteracoes = iteracoes;
        this.algoritmo = algoritmo;
    }

    public byte[] getHash() {
        return hash.clone();
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public int getIteracoes() {
        return iteracoes;
    }

    public String getAlgoritmo() {
        return algoritmo;
    }

    private static void validarHash(byte[] hash) {
        if (hash == null || hash.length == 0) {
            throw new IllegalArgumentException("O hash não pode ser nulo ou vazio.");
        }
    }

    private static void validarSalt(byte[] salt) {
        if (salt == null || salt.length < SenhaHasher.TAMANHO_MINIMO_SALT) {
            throw new IllegalArgumentException(
                    "O salt deve possuir pelo menos "
                            + SenhaHasher.TAMANHO_MINIMO_SALT + " bytes.");
        }
    }
}
