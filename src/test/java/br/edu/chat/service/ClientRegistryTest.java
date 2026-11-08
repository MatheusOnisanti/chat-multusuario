package br.edu.chat.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRegistryTest {

    @Test
    void deveRegistrarERemoverConexoes() {
        ClientRegistry registro = new ClientRegistry();
        ConexaoFalsa cliente = new ConexaoFalsa();

        registro.registrar(cliente);
        assertTrue(registro.snapshot().contains(cliente));

        registro.remover(cliente);
        assertFalse(registro.snapshot().contains(cliente));
    }

    private static final class ConexaoFalsa implements ConexaoCliente {
        private final AtomicBoolean encerrada = new AtomicBoolean();

        @Override
        public boolean enviar(br.edu.chat.model.Mensagem mensagem) {
            return !encerrada.get();
        }

        @Override
        public void encerrar() {
            encerrada.set(true);
        }
    }
}
