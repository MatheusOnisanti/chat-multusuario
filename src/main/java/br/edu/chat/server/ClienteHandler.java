package br.edu.chat.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ConexaoCliente;

/** Atende uma conexão autenticada em sua própria thread. */
public final class ClienteHandler implements Runnable, ConexaoCliente {

    static final String AUTH_CREDENTIALS_REJECTED = "AUTH_CREDENTIALS_REJECTED";
    static final String AUTH_INTERNAL_FAILURE = "AUTH_INTERNAL_FAILURE";
    private static final Logger LOGGER = Logger.getLogger(ClienteHandler.class.getName());

    private final Socket socket;
    private final ChatService chatService;
    private final AutenticacaoService autenticacaoService;
    private final Consumer<ClienteHandler> aoFinalizar;
    private final Object lockEscrita = new Object();
    private volatile boolean emExecucao = true;
    private Estado estado = Estado.NAO_AUTENTICADO;
    private Usuario usuarioAutenticado;
    private PrintWriter saida;

    public ClienteHandler(Socket socket, ChatService chatService,
            AutenticacaoService autenticacaoService,
            Consumer<ClienteHandler> aoFinalizar) {
        this.socket = Objects.requireNonNull(socket, "O socket do cliente não pode ser nulo.");
        this.chatService = Objects.requireNonNull(chatService,
                "O serviço de chat não pode ser nulo.");
        this.autenticacaoService = Objects.requireNonNull(autenticacaoService,
                "O serviço de autenticação não pode ser nulo.");
        this.aoFinalizar = Objects.requireNonNull(aoFinalizar,
                "A ação de finalização não pode ser nula.");
    }

    @Override
    public void run() {
        try (Socket socketAtual = socket;
                BufferedReader entrada = new BufferedReader(
                        new InputStreamReader(socketAtual.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while (emExecucao && (linha = entrada.readLine()) != null) {
                processar(linha);
            }
        } catch (IOException exception) {
            // Desconexões e erros de comunicação encerram somente este handler.
        } finally {
            emExecucao = false;
            estado = Estado.ENCERRADO;
            if (usuarioAutenticado != null) {
                chatService.remover(this);
            }
            aoFinalizar.accept(this);
        }
    }

    private void processar(String linha) {
        ProtocoloAutenticado.Comando comando;
        try {
            comando = ProtocoloAutenticado.interpretar(linha);
        } catch (IllegalArgumentException exception) {
            enviarResposta(RespostaAutenticacao.Codigo.REQUISICAO_INVALIDA);
            return;
        }

        if (comando instanceof ProtocoloAutenticado.Cadastro cadastro) {
            processarCadastro(cadastro);
        } else if (comando instanceof ProtocoloAutenticado.CadastroV2 cadastroV2) {
            processarCadastro(cadastroV2);
        } else if (comando instanceof ProtocoloAutenticado.Autenticacao autenticacao) {
            processarAutenticacao(autenticacao);
        } else if (comando instanceof ProtocoloAutenticado.AutenticacaoV2 autenticacaoV2) {
            processarAutenticacao(autenticacaoV2);
        } else if (comando instanceof ProtocoloAutenticado.Mensagem mensagem) {
            processarMensagem(mensagem);
        }
    }

    private void processarCadastro(ProtocoloAutenticado.Cadastro cadastro) {
        processarCadastro(cadastro.getLogin(), cadastro.getNome(), cadastro.getSenha(), false);
    }

    private void processarCadastro(ProtocoloAutenticado.CadastroV2 cadastro) {
        processarCadastro(cadastro.getLogin(), cadastro.getNome(), cadastro.getSenha(), true);
    }

    private void processarCadastro(String login, String nome, char[] senha,
            boolean respostaVersionada) {
        if (estado != Estado.NAO_AUTENTICADO) {
            enviarResposta(RespostaAutenticacao.Codigo.COMANDO_NAO_PERMITIDO);
            return;
        }

        try {
            Usuario usuario = autenticacaoService.cadastrar(
                    login, nome, senha);
            autenticar(usuario);
            if (respostaVersionada) {
                enviarRespostaV2(RespostaAutenticacao.Codigo.CADASTRADO,
                        usuario.getNome());
            } else {
                enviarResposta(RespostaAutenticacao.Codigo.CADASTRADO);
            }
            chatService.conectar(this);
        } catch (RuntimeException exception) {
            enviarResposta(RespostaAutenticacao.Codigo.CADASTRO);
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    private void processarAutenticacao(ProtocoloAutenticado.Autenticacao autenticacao) {
        processarAutenticacao(autenticacao.getLogin(), autenticacao.getSenha(), false);
    }

    private void processarAutenticacao(ProtocoloAutenticado.AutenticacaoV2 autenticacao) {
        processarAutenticacao(autenticacao.getLogin(), autenticacao.getSenha(), true);
    }

    private void processarAutenticacao(String login, char[] senha,
            boolean respostaVersionada) {
        if (estado != Estado.NAO_AUTENTICADO) {
            enviarResposta(RespostaAutenticacao.Codigo.COMANDO_NAO_PERMITIDO);
            return;
        }

        try {
            Optional<Usuario> usuario = autenticacaoService.autenticar(
                    login, senha);
            if (usuario.isEmpty()) {
                LOGGER.log(Level.INFO, AUTH_CREDENTIALS_REJECTED);
                enviarResposta(RespostaAutenticacao.Codigo.AUTENTICACAO);
                return;
            }

            Usuario usuarioAutenticado = usuario.orElseThrow();
            autenticar(usuarioAutenticado);
            if (respostaVersionada) {
                enviarRespostaV2(RespostaAutenticacao.Codigo.AUTENTICADO,
                        usuarioAutenticado.getNome());
            } else {
                enviarResposta(RespostaAutenticacao.Codigo.AUTENTICADO);
            }
            chatService.conectar(this);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, AUTH_INTERNAL_FAILURE);
            enviarResposta(RespostaAutenticacao.Codigo.AUTENTICACAO);
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    private void autenticar(Usuario usuario) {
        usuarioAutenticado = Objects.requireNonNull(usuario,
                "O usuário autenticado não pode ser nulo.");
        estado = Estado.AUTENTICADO;
    }

    private void processarMensagem(ProtocoloAutenticado.Mensagem mensagem) {
        if (estado != Estado.AUTENTICADO || usuarioAutenticado == null) {
            enviarResposta(RespostaAutenticacao.Codigo.NAO_AUTENTICADO);
            return;
        }

        Mensagem mensagemServidor = new Mensagem(
                usuarioAutenticado, mensagem.getConteudo(), LocalDateTime.now());
        chatService.distribuir(mensagemServidor, this);
    }

    private void enviarResposta(RespostaAutenticacao.Codigo codigo) {
        enviarRespostaTexto(RespostaAutenticacao.de(codigo).serializar());
    }

    private void enviarRespostaV2(RespostaAutenticacao.Codigo codigo, String nome) {
        RespostaAutenticacao resposta = RespostaAutenticacao.deComNome(codigo, nome);
        enviarRespostaTexto(resposta.serializarVersionada());
    }

    private void enviarRespostaTexto(String texto) {
        synchronized (lockEscrita) {
            if (!emExecucao) {
                return;
            }
            if (saida == null) {
                try {
                    saida = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8)));
                } catch (IOException exception) {
                    return;
                }
            }
            saida.println(texto);
            saida.flush();
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
        estado = Estado.ENCERRADO;
        try {
            socket.close();
        } catch (IOException exception) {
            // O encerramento já foi solicitado; não deve derrubar o servidor.
        }
    }

    private enum Estado {
        NAO_AUTENTICADO,
        AUTENTICADO,
        ENCERRADO
    }
}
