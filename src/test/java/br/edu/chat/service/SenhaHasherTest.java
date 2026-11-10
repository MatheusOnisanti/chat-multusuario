package br.edu.chat.service;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaHasherTest {

    @Test
    void senhaCorretaDeveSerValidada() {
        SenhaHasher hasher = new SenhaHasher();
        CredencialSenha credencial = hasher.gerar("senha-segura".toCharArray());

        assertTrue(hasher.verificar("senha-segura".toCharArray(), credencial));
    }

    @Test
    void senhaIncorretaDeveSerRejeitada() {
        SenhaHasher hasher = new SenhaHasher();
        CredencialSenha credencial = hasher.gerar("senha-segura".toCharArray());

        assertFalse(hasher.verificar("senha-incorreta".toCharArray(), credencial));
    }

    @Test
    void saltsDiferentesDevemProduzirHashesDiferentes() {
        SenhaHasher hasher = new SenhaHasher();

        CredencialSenha primeira = hasher.gerar("mesma-senha".toCharArray());
        CredencialSenha segunda = hasher.gerar("mesma-senha".toCharArray());

        assertFalse(Arrays.equals(primeira.getSalt(), segunda.getSalt()));
        assertFalse(Arrays.equals(primeira.getHash(), segunda.getHash()));
    }

    @Test
    void hashAdulteradoDeveSerRejeitado() {
        SenhaHasher hasher = new SenhaHasher();
        CredencialSenha original = hasher.gerar("senha-segura".toCharArray());
        byte[] hashAdulterado = original.getHash();
        hashAdulterado[0] ^= 1;
        CredencialSenha adulterada = new CredencialSenha(hashAdulterado,
                original.getSalt(), original.getIteracoes(), original.getAlgoritmo());

        assertFalse(hasher.verificar("senha-segura".toCharArray(), adulterada));
    }

    @Test
    void senhaNulaOuVaziaDeveSerRejeitada() {
        SenhaHasher hasher = new SenhaHasher();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> hasher.gerar(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> hasher.gerar(new char[0])),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> hasher.verificar(null, credencial(hasher))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> hasher.verificar(new char[0], credencial(hasher)))
        );
    }

    @Test
    void credenciaisComParametrosInvalidosDevemSerRejeitadas() {
        byte[] hash = new byte[SenhaHasher.TAMANHO_HASH];
        byte[] salt = new byte[SenhaHasher.TAMANHO_SALT];

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CredencialSenha(null, salt, 1, SenhaHasher.ALGORITMO)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CredencialSenha(hash, new byte[0], 1,
                                SenhaHasher.ALGORITMO)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CredencialSenha(hash, salt, 0, SenhaHasher.ALGORITMO)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CredencialSenha(hash, salt, 1, "")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SenhaHasher().verificar("senha".toCharArray(),
                                new CredencialSenha(hash, salt, 1, "algoritmo-invalido")))
        );
    }

    @Test
    void dadosRetornadosNaoDevemPermitirAlterarCredencialInterna() {
        SenhaHasher hasher = new SenhaHasher();
        CredencialSenha credencial = hasher.gerar("senha-segura".toCharArray());
        byte[] hashObtido = credencial.getHash();
        byte[] saltObtido = credencial.getSalt();
        hashObtido[0] ^= 1;
        saltObtido[0] ^= 1;

        assertTrue(hasher.verificar("senha-segura".toCharArray(), credencial));
    }

    @Test
    void algoritmoEIteracoesDevemSerPreservados() {
        SenhaHasher hasher = new SenhaHasher();
        CredencialSenha credencial = hasher.gerar("senha-segura".toCharArray());

        assertEquals(SenhaHasher.ALGORITMO, credencial.getAlgoritmo());
        assertEquals(SenhaHasher.ITERACOES_PADRAO, credencial.getIteracoes());
        assertEquals(SenhaHasher.TAMANHO_HASH, credencial.getHash().length);
        assertEquals(SenhaHasher.TAMANHO_SALT, credencial.getSalt().length);
        assertNotEquals(0, credencial.getIteracoes());
        assertArrayEquals(credencial.getHash(), credencial.getHash());
    }

    private static CredencialSenha credencial(SenhaHasher hasher) {
        return hasher.gerar("senha-segura".toCharArray());
    }
}
