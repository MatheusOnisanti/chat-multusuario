package br.edu.chat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocket;

import br.edu.chat.config.ConfiguracaoTls;
import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;
import br.edu.chat.server.RespostaAutenticacao;

/** Cliente TLS independente da interface grÃ¡fica. */
public final class ClienteChat implements AutoCloseable {

    private static final int TIMEOUT_HANDSHAKE_MILLIS = 5000;

    private final String host;
    private final int porta;
    private final Usuario usuario;
    private final Consumer<Mensagem> aoReceber;
    private final Runnable aoDesconectar;
    private final ConfiguracaoTls configuracaoTls;
    private final Object lockEscrita = new Object();
    private final Object lockEstado = new Object();
    private final AtomicBoolean desconexaoNotificada = new AtomicBoolean();

    private volatile boolean fechado;
    private volatile boolean autenticado;
    private volatile String nomeAutenticado;
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;
    private Thread threadRecebimento;

    ClienteChat(String host, int porta, String nome, Consumer<Mensagem> aoReceber) {
        this(host, porta, nome, aoReceber, () -> { });
    }

    ClienteChat(String host, int porta, String nome,
            Consumer<Mensagem> aoReceber, Runnable aoDesconectar) {
        this.host = Objects.requireNonNull(host, "O host nÃ£o pode ser nulo.");
        validarPorta(porta);
        this.porta = porta;
        this.usuario = criarUsuario(nome);
        this.configuracaoTls = null;
        this.aoReceber = Objects.requireNonNull(aoReceber, "O callback nÃ£o pode ser nulo.");
        this.aoDesconectar = Objects.requireNonNull(
                aoDesconectar, "O callback de desconexÃ£o nÃ£o pode ser nulo.");
    }

    public ClienteChat(String host, int porta, String nome,
            ConfiguracaoTls configuracaoTls, Consumer<Mensagem> aoReceber,
            Runnable aoDesconectar) {
        this.host = Objects.requireNonNull(host, "O host nÃ£o pode ser nulo.");
        validarPorta(porta);
        this.porta = porta;
        this.usuario = criarUsuario(nome);
        this.configuracaoTls = Objects.requireNonNull(
                configuracaoTls, "A configuraÃ§Ã£o TLS nÃ£o pode ser nula.");
        this.aoReceber = Objects.requireNonNull(aoReceber, "O callback nÃ£o pode ser nulo.");
        this.aoDesconectar = Objects.requireNonNull(
                aoDesconectar, "O callback de desconexÃ£o nÃ£o pode ser nulo.");
    }

    ClienteChat(Socket socket, String nome, Consumer<Mensagem> aoReceber) {
        this(socket, nome, aoReceber, () -> { });
    }

    ClienteChat(Socket socket, String nome, Consumer<Mensagem> aoReceber,
            Runnable aoDesconectar) {
        this.host = null;
        this.porta = -1;
        this.usuario = criarUsuario(nome);
        this.aoReceber = Objects.requireNonNull(aoReceber, "O callback nÃ£o pode ser nulo.");
        this.aoDesconectar = Objects.requireNonNull(
                aoDesconectar, "O callback de desconexÃ£o nÃ£o pode ser nulo.");
        this.configuracaoTls = null;
        this.socket = Objects.requireNonNull(socket, "O socket nÃ£o pode ser nulo.");
    }

    public void conectar(String login, char[] senha) throws IOException {
        conectarComando(comandoAutenticacao(login, senha), false);
    }

    public void conectarV2(String login, char[] senha) throws IOException {
        conectarComando(comandoAutenticacaoV2(login, senha), true);
    }

    /** Retorna o nome canonico recebido na resposta V2, quando disponivel. */
    public Optional<String> getNomeAutenticado() {
        return Optional.ofNullable(nomeAutenticado);
    }

    /** MantÃ©m a forma de chamada existente, mas nunca abre uma conexÃ£o sem autenticaÃ§Ã£o. */
    public void conectar() throws IOException {
        if (autenticado) {
            return;
        }
        throw new IllegalStateException("Login e senha sÃ£o obrigatÃ³rios.");
    }

    public void cadastrar(String login, String nome, char[] senha) throws IOException {
        validarNome(nome);
        conectarComando(comandoCadastro(login, nome, senha), false);
    }

    public void cadastrarV2(String login, String nome, char[] senha) throws IOException {
        validarNome(nome);
        conectarComando(comandoCadastroV2(login, nome, senha), true);
    }

    private void conectarComando(String comando, boolean respostaVersionada) throws IOException {
        synchronized (lockEstado) {
            if (threadRecebimento != null || autenticado) {
                throw new IllegalStateException("O cliente jÃ¡ foi conectado.");
            }
            if (fechado) {
                throw new IllegalStateException("O cliente jÃ¡ foi fechado.");
            }

            Socket socketNovo = socket;
            BufferedReader entradaNova = null;
            try {
                if (socketNovo == null) {
                    if (configuracaoTls == null) {
                        throw new IllegalStateException("A configuraÃ§Ã£o TLS Ã© obrigatÃ³ria.");
                    }
                    socketNovo = configuracaoTls.conectar(host, porta);
                    SSLSocket socketTls = (SSLSocket) socketNovo;
                    socketTls.setSoTimeout(TIMEOUT_HANDSHAKE_MILLIS);
                    socketTls.startHandshake();
                    socketTls.setSoTimeout(0);
                }

                entradaNova = new BufferedReader(new InputStreamReader(
                        socketNovo.getInputStream(), StandardCharsets.UTF_8));
                saida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                        socketNovo.getOutputStream(), StandardCharsets.UTF_8)));
                saida.println(comando);
                saida.flush();
                if (saida.checkError()) {
                    throw new IOException("Falha ao enviar a autenticaÃ§Ã£o.");
                }

                List<String> linhasAntecedentes = new ArrayList<>();
                String respostaTexto;
                RespostaAutenticacao resposta;
                while (true) {
                    respostaTexto = entradaNova.readLine();
                    if (respostaTexto == null) {
                        throw new IOException("O servidor encerrou antes da autenticacao.");
                    }
                    try {
                        if (respostaVersionada) {
                            try {
                                resposta = RespostaAutenticacao.interpretarVersionada(respostaTexto);
                            } catch (IllegalArgumentException excecaoV2) {
                                resposta = RespostaAutenticacao.interpretar(respostaTexto);
                                if (resposta.isSucesso()) {
                                    throw new IOException(
                                            "O servidor nao retornou uma resposta V2.");
                                }
                            }
                        } else {
                            resposta = RespostaAutenticacao.interpretar(respostaTexto);
                        }
                        break;
                    } catch (IllegalArgumentException exception) {
                        linhasAntecedentes.add(respostaTexto);
                    }
                }
                if (!resposta.isSucesso()) {
                    throw new IOException("AutenticaÃ§Ã£o rejeitada.");
                }

                if (respostaVersionada && resposta.getNomeAutenticado().isEmpty()) {
                    throw new IOException("A resposta V2 nao possui identidade canonica.");
                }

                socket = socketNovo;
                entrada = entradaNova;
                nomeAutenticado = resposta.getNomeAutenticado().orElse(null);
                autenticado = true;
                Socket socketAtual = socketNovo;
                threadRecebimento = new Thread(
                        () -> receberMensagens(socketAtual, linhasAntecedentes),
                        "cliente-chat-recebimento");
                threadRecebimento.start();
            } catch (IOException | RuntimeException exception) {
                fecharSocket(socketNovo);
                socket = null;
                entrada = null;
                saida = null;
                nomeAutenticado = null;
                autenticado = false;
                if (exception instanceof IOException ioException) {
                    throw ioException;
                }
                throw exception;
            }
        }
    }

    public void enviar(String conteudo) {
        validarConteudo(conteudo);

        synchronized (lockEscrita) {
            if (fechado || !autenticado || socket == null || threadRecebimento == null) {
                throw new IllegalStateException("O cliente nÃ£o estÃ¡ autenticado.");
            }

            saida.println(comandoMensagem(conteudo));
            saida.flush();
            if (saida.checkError()) {
                fechar();
                throw new IllegalStateException("Falha ao enviar mensagem.");
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
            autenticado = false;
            nomeAutenticado = null;
            socketAtual = socket;
            threadAtual = threadRecebimento;
            socket = null;
            entrada = null;
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

    private void receberMensagens(Socket socketAtual, List<String> linhasAntecedentes) {
        try (BufferedReader entradaAtual = entrada) {
            String linha;
            for (String linhaAnterior : linhasAntecedentes) {
                processarMensagemRecebida(linhaAnterior);
            }
            while (!fechado && (linha = entradaAtual.readLine()) != null) {
                processarMensagemRecebida(linha);
            }
        } catch (IOException ignored) {
            // EOF, desconexÃ£o e falhas de comunicaÃ§Ã£o encerram somente este cliente.
        } finally {
            synchronized (lockEstado) {
                fechado = true;
                autenticado = false;
                nomeAutenticado = null;
                if (socket == socketAtual) {
                    socket = null;
                    entrada = null;
                    threadRecebimento = null;
                }
            fecharSocket(socketAtual);
            notificarDesconexao();
        }
    }

    }

    private void processarMensagemRecebida(String linha) {
                Optional<Mensagem> mensagem = MensagemRecebidaParser.interpretar(linha);
                if (mensagem.isPresent()) {
                    try {
                        aoReceber.accept(mensagem.orElseThrow());
                    } catch (RuntimeException ignored) {
                        // Uma falha do callback nÃ£o encerra a comunicaÃ§Ã£o do cliente.
                    }
                }
    }

    private void notificarDesconexao() {
        if (desconexaoNotificada.compareAndSet(false, true)) {
            try {
                aoDesconectar.run();
            } catch (RuntimeException ignored) {
                // Uma falha no callback nÃ£o deve afetar o encerramento do cliente.
            }
        }
    }

    private static String comandoAutenticacao(String login, char[] senha) {
        return comandoAutenticacao("AUTENTICAR", login, senha);
    }

    private static String comandoAutenticacaoV2(String login, char[] senha) {
        return comandoAutenticacao("AUTENTICAR_V2", login, senha);
    }

    private static String comandoAutenticacao(String tipo, String login, char[] senha) {
        validarLogin(login);
        validarSenha(senha);
        return tipo + "|" + codificar(login) + "|" + codificar(new String(senha));
    }

    private static String comandoCadastro(String login, String nome, char[] senha) {
        return comandoCadastro("CADASTRAR", login, nome, senha);
    }

    private static String comandoCadastroV2(String login, String nome, char[] senha) {
        return comandoCadastro("CADASTRAR_V2", login, nome, senha);
    }

    private static String comandoCadastro(String tipo, String login, String nome, char[] senha) {
        validarLogin(login);
        validarSenha(senha);
        return tipo + "|" + codificar(login) + "|" + codificar(nome)
                + "|" + codificar(new String(senha));
    }

    private static String comandoMensagem(String conteudo) {
        return "MENSAGEM|" + codificar(conteudo);
    }

    private static String codificar(String valor) {
        return Base64.getEncoder().encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private static Usuario criarUsuario(String nome) {
        validarNome(nome);
        return new Usuario(nome);
    }

    private static void validarLogin(String login) {
        if (login == null || login.isBlank() || login.indexOf('|') >= 0
                || contemQuebraDeLinha(login)) {
            throw new IllegalArgumentException("O login deve ser vÃ¡lido.");
        }
    }

    private static void validarSenha(char[] senha) {
        if (senha == null || senha.length == 0) {
            throw new IllegalArgumentException("A senha nÃ£o pode ser vazia.");
        }
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank() || nome.indexOf('|') >= 0
                || contemQuebraDeLinha(nome)) {
            throw new IllegalArgumentException("O nome deve ser vÃ¡lido e nÃ£o conter | ou quebras de linha.");
        }
    }

    private static void validarConteudo(String conteudo) {
        if (conteudo == null || conteudo.isBlank() || contemQuebraDeLinha(conteudo)) {
            throw new IllegalArgumentException("O conteÃºdo deve ser vÃ¡lido e nÃ£o conter quebras de linha.");
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
                // O encerramento Ã© idempotente e nÃ£o deve propagar falhas de fechamento.
            }
        }
    }
}
