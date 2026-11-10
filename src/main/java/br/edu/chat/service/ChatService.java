package br.edu.chat.service;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.edu.chat.model.Mensagem;
import br.edu.chat.persistence.MensagemRepository;
import br.edu.chat.persistence.PersistenciaException;

/** Centraliza a distribuição de mensagens entre as conexões registradas. */
public final class ChatService {

    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    static final String HISTORY_INTERNAL_FAILURE = "HISTORY_INTERNAL_FAILURE";

    private final ClientRegistry clientRegistry;
    private final MensagemRepository mensagemRepository;
    private final Object lockHistorico = new Object();

    public ChatService() {
        this(new ClientRegistry(), null);
    }

    public ChatService(ClientRegistry clientRegistry) {
        this(clientRegistry, null);
    }

    public ChatService(ClientRegistry clientRegistry, MensagemRepository mensagemRepository) {
        this.clientRegistry = Objects.requireNonNull(clientRegistry, "O registro de clientes não pode ser nulo.");
        this.mensagemRepository = mensagemRepository;
    }

    public void registrar(ConexaoCliente cliente) {
        clientRegistry.registrar(Objects.requireNonNull(cliente, "A conexão do cliente não pode ser nula."));
    }

    public void remover(ConexaoCliente cliente) {
        clientRegistry.remover(cliente);
    }

    /**
     * Registra uma conexão depois de recuperar e enviar seu histórico.
     * O lock coordena esta operação com o salvamento e o broadcast de novas mensagens.
     * A estratégia é síncrona: um cliente lento pode atrasar temporariamente os demais.
     */
    public void conectar(ConexaoCliente cliente) {
        Objects.requireNonNull(cliente, "A conexão do cliente não pode ser nula.");

        synchronized (lockHistorico) {
            List<Mensagem> historico = List.of();
            boolean historicoDisponivel = mensagemRepository != null;

            if (historicoDisponivel) {
                try {
                    historico = mensagemRepository.listarHistorico();
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING, HISTORY_INTERNAL_FAILURE);
                }
            }

            clientRegistry.registrar(cliente);
            if (!historicoDisponivel || historico.isEmpty()) {
                return;
            }

            for (Mensagem mensagem : historico) {
                try {
                    if (!cliente.enviar(mensagem)) {
                        removerEEncerrar(cliente);
                        return;
                    }
                } catch (RuntimeException exception) {
                    removerEEncerrar(cliente);
                    return;
                }
            }
        }
    }

    public void distribuir(Mensagem mensagem, ConexaoCliente origem) {
        Objects.requireNonNull(mensagem, "A mensagem não pode ser nula.");

        synchronized (lockHistorico) {
            if (mensagemRepository != null) {
                try {
                    mensagemRepository.salvar(mensagem);
                } catch (PersistenciaException exception) {
                    LOGGER.log(Level.WARNING, HISTORY_INTERNAL_FAILURE);
                    return;
                }
            }

            for (ConexaoCliente cliente : clientRegistry.snapshot()) {
                if (cliente == origem) {
                    continue;
                }

                boolean enviado;
                try {
                    enviado = cliente.enviar(mensagem);
                } catch (RuntimeException ignored) {
                    enviado = false;
                }

                if (!enviado) {
                    removerEEncerrar(cliente);
                }
            }
        }
    }

    private void removerEEncerrar(ConexaoCliente cliente) {
        clientRegistry.remover(cliente);
        try {
            cliente.encerrar();
        } catch (RuntimeException ignored) {
            // A falha de encerramento de um cliente não interrompe o serviço.
        }
    }
}
