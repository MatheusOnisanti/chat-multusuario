package br.edu.chat.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Deriva e verifica senhas usando PBKDF2 com salt aleatório. */
public final class SenhaHasher {

    public static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    public static final int ITERACOES_PADRAO = 600_000;
    public static final int TAMANHO_MINIMO_SALT = 16;
    public static final int TAMANHO_SALT = 16;
    public static final int TAMANHO_HASH = 32;

    private final SecureRandom geradorAleatorio;

    public SenhaHasher() {
        this.geradorAleatorio = new SecureRandom();
    }

    /**
     * Gera um hash PBKDF2-HMAC-SHA256 com 600.000 iterações.
     * O custo é deliberadamente configurado em uma constante única para permitir
     * ajuste futuro conforme o ambiente, sem reduzir a proteção apenas para acelerar testes.
     */
    public CredencialSenha gerar(char[] senha) {
        validarSenha(senha);
        byte[] salt = new byte[TAMANHO_SALT];
        geradorAleatorio.nextBytes(salt);
        byte[] hash = derivar(senha, salt, ITERACOES_PADRAO);
        return new CredencialSenha(hash, salt, ITERACOES_PADRAO, ALGORITMO);
    }

    public boolean verificar(char[] senha, CredencialSenha credencial) {
        validarSenha(senha);
        if (credencial == null) {
            throw new IllegalArgumentException("A credencial não pode ser nula.");
        }
        validarCredencial(credencial);
        byte[] hashCalculado = derivar(senha, credencial.getSalt(),
                credencial.getIteracoes());
        return MessageDigest.isEqual(hashCalculado, credencial.getHash());
    }

    private static byte[] derivar(char[] senha, byte[] salt, int iteracoes) {
        PBEKeySpec especificacao = new PBEKeySpec(senha, salt, iteracoes,
                TAMANHO_HASH * Byte.SIZE);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO);
            return fabrica.generateSecret(especificacao).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Não foi possível derivar a credencial da senha.", exception);
        } finally {
            especificacao.clearPassword();
        }
    }

    private static void validarSenha(char[] senha) {
        if (senha == null || senha.length == 0) {
            throw new IllegalArgumentException("A senha não pode ser nula ou vazia.");
        }
    }

    private static void validarCredencial(CredencialSenha credencial) {
        if (!ALGORITMO.equals(credencial.getAlgoritmo())) {
            throw new IllegalArgumentException("Algoritmo de credencial não suportado.");
        }
        if (credencial.getHash().length != TAMANHO_HASH) {
            throw new IllegalArgumentException("O hash deve possuir 32 bytes.");
        }
        if (credencial.getSalt().length < TAMANHO_MINIMO_SALT
                || credencial.getIteracoes() <= 0) {
            throw new IllegalArgumentException("Parâmetros da credencial inválidos.");
        }
    }
}
