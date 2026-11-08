package br.edu.chat.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mantém o registro thread-safe das conexões disponíveis para o chat. */
public final class ClientRegistry {

    private final Set<ConexaoCliente> clientes = ConcurrentHashMap.newKeySet();

    public void registrar(ConexaoCliente cliente) {
        clientes.add(cliente);
    }

    public void remover(ConexaoCliente cliente) {
        clientes.remove(cliente);
    }

    public Set<ConexaoCliente> snapshot() {
        return Set.copyOf(clientes);
    }
}
