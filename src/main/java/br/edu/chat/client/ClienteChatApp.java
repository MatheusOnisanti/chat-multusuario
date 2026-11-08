package br.edu.chat.client;

import javax.swing.SwingUtilities;

/** Ponto de entrada da aplicação gráfica Swing do cliente. */
public final class ClienteChatApp {

    private ClienteChatApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClienteChatFrame janela = new ClienteChatFrame();
            janela.setVisible(true);
        });
    }
}
