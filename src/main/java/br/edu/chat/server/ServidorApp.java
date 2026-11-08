package br.edu.chat.server;

import java.io.IOException;

/** Ponto de entrada para iniciar o servidor TCP na porta padrão do projeto. */
public final class ServidorApp {

    private static final int PORTA_PADRAO = 5000;

    private ServidorApp() {
    }

    public static void main(String[] args) throws IOException {
        new Servidor(PORTA_PADRAO).iniciar();
    }
}
