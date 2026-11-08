package br.edu.chat.server;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

import br.edu.chat.model.Mensagem;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ConexaoCliente;

/** Atende uma conexão TCP individual em sua própria thread. */
public final class ClienteHandler implements Runnable, ConexaoCliente {

    private final Socket socket;
    private final ChatService chatService;
    private final Consumer<ClienteHandler> aoFinalizar;
    private final Object lockEscrita = new Object();
    private volatile boolean emExecucao = true;
    private PrintWriter saida;

    public ClienteHandler(Socket socket, Consumer<ClienteHandler> aoFinalizar) {
        this(socket, new ChatService(), aoFinalizar);
    }

    public ClienteHandler(Socket socket, ChatService chatService, Consumer<ClienteHandler> aoFinalizar) {
        this.socket = Objects.requireNonNull(socket, "O socket do cliente não pode ser nulo.");
        this.chatService = Objects.requireNonNull(chatService, "O serviço de chat não pode ser nulo.");
        this.aoFinalizar = Objects.requireNonNull(aoFinalizar, "A ação de finalização não pode ser nula.");
    }

    @Override
    public void run() {
        chatService.conectar(this);

        try (Socket socketAtual = socket;
             BufferedReader entrada = new BufferedReader(
                     new InputStreamReader(socketAtual.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while (emExecucao && (linha = entrada.readLine()) != null) {
                Protocolo.interpretar(linha)
                        .ifPresent(mensagem -> chatService.distribuir(mensagem, this));
            }
        } catch (IOException exception) {
            // Desconexões e erros de comunicação encerram somente este handler.
        } finally {
            emExecucao = false;
            chatService.remover(this);
            aoFinalizar.accept(this);
        }
    }

    @Override
    public boolean enviar(Mensagem mensagem) {
        synchronized (lockEscrita) {
            if (!emExecucao) {
                return false;
            }

            try {
                if (saida == null) {
                    saida = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8)));
                }

                saida.println(Protocolo.serializar(mensagem));
                saida.flush();
                return !saida.checkError();
            } catch (IOException exception) {
                return false;
            }
        }
    }

    /** Solicita o encerramento da conexão e desbloqueia a leitura do socket. */
    @Override
    public void encerrar() {
        emExecucao = false;
        try {
            socket.close();
        } catch (IOException exception) {
            // O encerramento já foi solicitado; não deve derrubar o servidor.
        }
    }
}
