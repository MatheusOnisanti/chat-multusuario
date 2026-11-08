package br.edu.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.service.ChatService;

/** Servidor TCP responsável por aceitar conexões de clientes. */
public final class Servidor {

    private final int porta;
    private final ChatService chatService;
    private final Set<ClienteHandler> handlersAtivos = ConcurrentHashMap.newKeySet();
    private volatile boolean emExecucao;
    private ServerSocket serverSocket;

    public Servidor(int porta) {
        this(porta, new ChatService(
                new br.edu.chat.service.ClientRegistry(),
                new JdbcMensagemRepository(ConnectionFactory.producao())));
    }

    public Servidor(int porta, ChatService chatService) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }

        this.porta = porta;
        this.chatService = chatService;
    }

    /**
     * Inicia o servidor e permanece bloqueado aguardando conexões.
     *
     * @throws IOException se não for possível abrir ou utilizar a porta
     */
    public void iniciar() throws IOException {
        ServerSocket servidorLocal;

        synchronized (this) {
            if (emExecucao) {
                throw new IllegalStateException("O servidor já está em execução.");
            }

            servidorLocal = new ServerSocket(porta);
            serverSocket = servidorLocal;
            emExecucao = true;
        }

        try {
            while (emExecucao) {
                Socket socket;
                try {
                    socket = servidorLocal.accept();
                } catch (SocketException exception) {
                    if (emExecucao) {
                        throw exception;
                    }
                    break;
                }

                synchronized (this) {
                    if (!emExecucao) {
                        socket.close();
                        continue;
                    }

                    ClienteHandler handler = new ClienteHandler(socket, chatService, handlersAtivos::remove);
                    handlersAtivos.add(handler);
                    new Thread(handler).start();
                }
            }
        } finally {
            synchronized (this) {
                emExecucao = false;
                if (serverSocket == servidorLocal) {
                    serverSocket = null;
                }
            }
            servidorLocal.close();
            handlersAtivos.forEach(ClienteHandler::encerrar);
        }
    }

    /** Interrompe o laço de aceitação e libera o ServerSocket. */
    public void encerrar() throws IOException {
        ServerSocket servidorLocal;

        synchronized (this) {
            emExecucao = false;
            servidorLocal = serverSocket;
        }

        if (servidorLocal != null) {
            servidorLocal.close();
        }

        handlersAtivos.forEach(ClienteHandler::encerrar);
    }
}
