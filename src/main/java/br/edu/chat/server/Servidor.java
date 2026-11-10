package br.edu.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import br.edu.chat.config.ConfiguracaoTls;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.ChatService;

/** Servidor TCP responsável por aceitar conexões de clientes. */
public final class Servidor {

    private static final Logger LOGGER = Logger.getLogger(Servidor.class.getName());
    private static final long TEMPO_MAXIMO_ENCERRAMENTO_EXECUTOR_SEGUNDOS = 2L;

    private final int porta;
    private final ChatService chatService;
    private final AutenticacaoService autenticacaoService;
    private final Set<ClienteHandler> handlersAtivos = ConcurrentHashMap.newKeySet();
    private final Set<SSLSocket> handshakesPendentes = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<ThreadPoolExecutor> executorHandshakeAtual = new ThreadLocal<>();
    private volatile boolean emExecucao;
    private SSLServerSocket serverSocket;
    private ThreadPoolExecutor executorHandshakes;

    public Servidor(int porta, ConfiguracaoTls configuracaoTls,
            AutenticacaoService autenticacaoService) {
        this(porta, new ChatService(
                new br.edu.chat.service.ClientRegistry(),
                new JdbcMensagemRepository(ConnectionFactory.producao())), configuracaoTls,
                autenticacaoService);
    }

    public Servidor(int porta, ChatService chatService, ConfiguracaoTls configuracaoTls,
            AutenticacaoService autenticacaoService) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }

        this.porta = porta;
        this.chatService = chatService;
        this.autenticacaoService = java.util.Objects.requireNonNull(
                autenticacaoService, "O serviço de autenticação não pode ser nulo.");
        this.configuracaoTls = java.util.Objects.requireNonNull(
                configuracaoTls, "A configuração TLS não pode ser nula.");
    }

    private final ConfiguracaoTls configuracaoTls;

    /**
     * Inicia o servidor e permanece bloqueado aguardando conexões.
     *
     * @throws IOException se não for possível abrir ou utilizar a porta
     */
    public void iniciar() throws IOException {
        SSLServerSocket servidorLocal;
        ThreadPoolExecutor executorLocal;

        synchronized (this) {
            if (emExecucao) {
                throw new IllegalStateException("O servidor já está em execução.");
            }

            servidorLocal = configuracaoTls.criarServidor(porta);
            executorLocal = criarExecutorHandshakes();
            serverSocket = servidorLocal;
            executorHandshakes = executorLocal;
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

                SSLSocket socketTls = (SSLSocket) socket;
                submeterHandshake(socketTls, executorLocal);
            }
        } finally {
            synchronized (this) {
                emExecucao = false;
                if (serverSocket == servidorLocal) {
                    serverSocket = null;
                }
                if (executorHandshakes == executorLocal) {
                    executorHandshakes = null;
                }
            }
            try {
                servidorLocal.close();
            } finally {
                encerrarExecutorHandshakes(executorLocal);
                handlersAtivos.forEach(ClienteHandler::encerrar);
            }
        }
    }

    /** Interrompe o laço de aceitação e libera o ServerSocket. */
    public void encerrar() throws IOException {
        SSLServerSocket servidorLocal;
        ThreadPoolExecutor executorLocal;

        synchronized (this) {
            emExecucao = false;
            servidorLocal = serverSocket;
            executorLocal = executorHandshakes;
            executorHandshakes = null;
        }

        try {
            if (servidorLocal != null) {
                servidorLocal.close();
            }
        } finally {
            if (executorLocal != null) {
                encerrarExecutorHandshakes(executorLocal);
            }
            handlersAtivos.forEach(ClienteHandler::encerrar);
        }
    }

    private void encerrarExecutorHandshakes(ThreadPoolExecutor executor) {
        executor.shutdownNow();
        handshakesPendentes.forEach(socket -> fecharSocketSilenciosamente(socket));

        if (executorHandshakeAtual.get() == executor) {
            LOGGER.warning("O encerramento do executor foi solicitado pela própria tarefa de handshake.");
            handshakesPendentes.clear();
            return;
        }

        try {
            if (!executor.awaitTermination(
                    TEMPO_MAXIMO_ENCERRAMENTO_EXECUTOR_SEGUNDOS, TimeUnit.SECONDS)) {
                LOGGER.warning("O executor de handshakes TLS não terminou no prazo previsto.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Aguardando o encerramento do executor de handshakes TLS foi interrompido.");
        }
        handshakesPendentes.clear();
    }

    private static ThreadPoolExecutor criarExecutorHandshakes() {
        return new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private void submeterHandshake(SSLSocket socket, ThreadPoolExecutor executor) {
        boolean fecharSocket = false;
        synchronized (this) {
            if (!emExecucao || executorHandshakes != executor) {
                fecharSocket = true;
            } else {
                handshakesPendentes.add(socket);
                try {
                    executor.execute(() -> processarHandshake(socket, executor));
                } catch (RejectedExecutionException exception) {
                    handshakesPendentes.remove(socket);
                    fecharSocket = true;
                }
            }
        }

        if (fecharSocket) {
            fecharSocketSilenciosamente(socket);
        }
    }

    private void processarHandshake(SSLSocket socket, ThreadPoolExecutor executor) {
        executorHandshakeAtual.set(executor);
        try {
            socket.setSoTimeout(5000);
            socket.startHandshake();
            socket.setSoTimeout(0);
            registrarHandlerAposHandshake(socket, executor);
        } catch (IOException exception) {
            fecharSocketSilenciosamente(socket, exception);
        } catch (RuntimeException exception) {
            fecharSocketSilenciosamente(socket);
        } finally {
            handshakesPendentes.remove(socket);
            executorHandshakeAtual.remove();
        }
    }

    private void registrarHandlerAposHandshake(SSLSocket socket, ThreadPoolExecutor executor) {
        boolean fecharSocket = false;
        synchronized (this) {
            if (!emExecucao || executorHandshakes != executor) {
                fecharSocket = true;
            } else {
                ClienteHandler handler = new ClienteHandler(socket, chatService,
                        autenticacaoService, handlersAtivos::remove);
                handlersAtivos.add(handler);
                new Thread(handler).start();
            }
        }

        if (fecharSocket) {
            fecharSocketSilenciosamente(socket);
        }
    }

    private static void fecharSocketSilenciosamente(SSLSocket socket) {
        fecharSocketSilenciosamente(socket, null);
    }

    private static void fecharSocketSilenciosamente(SSLSocket socket, IOException causa) {
        try {
            socket.close();
        } catch (IOException fechamento) {
            if (causa != null) {
                causa.addSuppressed(fechamento);
            }
        }
    }
}
