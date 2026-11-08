package br.edu.chat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

/** Cliente TCP independente de interface gráfica. */
public final class ClienteChat implements AutoCloseable {

    private final String host;
    private final int porta;
    private final Usuario usuario;
    private final Consumer<Mensagem> aoReceber;
    private final Runnable aoDesconectar;
    private final Object lockEscrita = new Object();
    private final Object lockEstado = new Object();
    private final AtomicBoolean desconexaoNotificada = new AtomicBoolean();

    private volatile boolean fechado;
    private Socket socket;
    private PrintWriter saida;
    private Thread threadRecebimento;

    public ClienteChat(String host, int porta, String nome, Consumer<Mensagem> aoReceber) {
        this(host, porta, nome, aoReceber, () -> { });
    }

    public ClienteChat(String host, int porta, String nome,
            Consumer<Mensagem> aoReceber, Runnable aoDesconectar) {
        this.host = Objects.requireNonNull(host, "O host não pode ser nulo.");
        validarPorta(porta);
        this.porta = porta;
        this.usuario = criarUsuario(nome);
        this.aoReceber = Objects.requireNonNull(aoReceber, "O callback não pode ser nulo.");
        this.aoDesconectar = Objects.requireNonNull(
                aoDesconectar, "O callback de desconexão não pode ser nulo.");
    }

    ClienteChat(Socket socket, String nome, Consumer<Mensagem> aoReceber) {
        this(socket, nome, aoReceber, () -> { });
    }

    ClienteChat(Socket socket, String nome, Consumer<Mensagem> aoReceber,
            Runnable aoDesconectar) {
        this.host = null;
        this.porta = -1;
        this.usuario = criarUsuario(nome);
        this.aoReceber = Objects.requireNonNull(aoReceber, "O callback não pode ser nulo.");
        this.aoDesconectar = Objects.requireNonNull(
                aoDesconectar, "O callback de desconexão não pode ser nulo.");
        this.socket = Objects.requireNonNull(socket, "O socket não pode ser nulo.");
    }

    public void conectar() throws IOException {
        synchronized (lockEstado) {
            if (threadRecebimento != null) {
                throw new IllegalStateException("O cliente já foi conectado.");
            }
            if (fechado) {
                throw new IllegalStateException("O cliente já foi fechado.");
            }
            if (socket == null) {
                socket = new Socket(host, porta);
            }
            Socket socketAtual = socket;
            threadRecebimento = new Thread(
                    () -> receberMensagens(socketAtual),
                    "cliente-chat-recebimento");
            threadRecebimento.start();
        }
    }

    public void enviar(String conteudo) {
        validarConteudo(conteudo);

        synchronized (lockEscrita) {
            if (fechado || socket == null || threadRecebimento == null) {
                throw new IllegalStateException("O cliente não está conectado.");
            }

            try {
                if (saida == null) {
                    saida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8)));
                }
                saida.println(usuario.getNome() + "|" + conteudo);
                saida.flush();
                if (saida.checkError()) {
                    fechar();
                    throw new IOException("Falha ao enviar mensagem.");
                }
            } catch (IOException exception) {
                fechar();
                throw new IllegalStateException("Não foi possível enviar a mensagem.", exception);
            }
        }
    }

    @Override
    public void close() {
        fechar();
    }

    public void fechar() {
        Socket socketAtual;
        Thread threadAtual;

        synchronized (lockEstado) {
            if (fechado && socket == null && threadRecebimento == null) {
                return;
            }
            fechado = true;
            socketAtual = socket;
            threadAtual = threadRecebimento;
            socket = null;
            threadRecebimento = null;
        }

        fecharSocket(socketAtual);
        if (threadAtual != null && threadAtual != Thread.currentThread()) {
            try {
                threadAtual.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void receberMensagens(Socket socketAtual) {
        try (InputStream entradaBruta = socketAtual.getInputStream();
             BufferedReader entrada = new BufferedReader(new InputStreamReader(
                     entradaBruta, StandardCharsets.UTF_8))) {
            String linha;
            while (!fechado && (linha = entrada.readLine()) != null) {
                Optional<Mensagem> mensagem = MensagemRecebidaParser.interpretar(linha);
                if (mensagem.isPresent()) {
                    try {
                        aoReceber.accept(mensagem.orElseThrow());
                    } catch (RuntimeException ignored) {
                        // Uma falha do callback não encerra a comunicação do cliente.
                    }
                }
            }
        } catch (IOException ignored) {
            // EOF, desconexão e falhas de comunicação encerram somente este cliente.
        } finally {
            synchronized (lockEstado) {
                fechado = true;
                if (socket == socketAtual) {
                    socket = null;
                    threadRecebimento = null;
                }
            }
            fecharSocket(socketAtual);
            notificarDesconexao();
        }
    }

    private void notificarDesconexao() {
        if (desconexaoNotificada.compareAndSet(false, true)) {
            try {
                aoDesconectar.run();
            } catch (RuntimeException ignored) {
                // Uma falha no callback não deve afetar o encerramento do cliente.
            }
        }
    }

    private static Usuario criarUsuario(String nome) {
        validarNome(nome);
        return new Usuario(nome);
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank() || nome.indexOf('|') >= 0 || contemQuebraDeLinha(nome)) {
            throw new IllegalArgumentException("O nome deve ser válido e não conter | ou quebras de linha.");
        }
    }

    private static void validarConteudo(String conteudo) {
        if (conteudo == null || conteudo.isBlank() || contemQuebraDeLinha(conteudo)) {
            throw new IllegalArgumentException("O conteúdo deve ser válido e não conter quebras de linha.");
        }
    }

    private static boolean contemQuebraDeLinha(String texto) {
        return texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0;
    }

    private static void validarPorta(int porta) {
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
        }
    }

    private static void fecharSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // O encerramento é idempotente e não deve propagar falhas de fechamento.
            }
        }
    }
}
