package br.edu.chat.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

/** Janela Swing do cliente TCP, independente das regras de servidor e persistência. */
public final class ClienteChatFrame extends JFrame {

    private final JTextField campoHost = new JTextField("localhost", 15);
    private final JTextField campoPorta = new JTextField("5000", 6);
    private final JTextField campoNome = new JTextField(12);
    private final JTextArea areaHistorico = new JTextArea(18, 55);
    private final JTextField campoMensagem = new JTextField();
    private final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JButton botaoDesconectar = new JButton("Desconectar");
    private final JLabel rotuloEstado = new JLabel("Desconectado");

    private volatile ClienteChat cliente;
    private volatile boolean conexaoEmAndamento;
    private volatile boolean encerramentoIntencional;
    private volatile boolean janelaEmFechamento;

    public ClienteChatFrame() {
        super("Chat Multiusuário");
        configurarJanela();
        construirComponentes();
        configurarAcoes();
        atualizarEstadoControles();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setResizable(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                fecharJanela();
            }
        });
    }

    private void construirComponentes() {
        JPanel painelConexao = new JPanel(new GridBagLayout());
        GridBagConstraints restricao = new GridBagConstraints();
        restricao.insets = new Insets(4, 4, 4, 4);
        restricao.anchor = GridBagConstraints.WEST;

        adicionarCampo(painelConexao, restricao, 0, "Host:", campoHost);
        adicionarCampo(painelConexao, restricao, 1, "Porta:", campoPorta);
        adicionarCampo(painelConexao, restricao, 2, "Nome:", campoNome);

        JPanel painelBotoesConexao = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelBotoesConexao.add(botaoConectar);
        painelBotoesConexao.add(botaoDesconectar);
        painelBotoesConexao.add(rotuloEstado);

        JPanel painelSuperior = new JPanel(new BorderLayout());
        painelSuperior.add(painelConexao, BorderLayout.CENTER);
        painelSuperior.add(painelBotoesConexao, BorderLayout.SOUTH);

        areaHistorico.setEditable(false);
        areaHistorico.setLineWrap(true);
        areaHistorico.setWrapStyleWord(true);

        JPanel painelEnvio = new JPanel(new BorderLayout(4, 4));
        painelEnvio.add(campoMensagem, BorderLayout.CENTER);
        painelEnvio.add(botaoEnviar, BorderLayout.EAST);

        add(painelSuperior, BorderLayout.NORTH);
        add(new JScrollPane(areaHistorico), BorderLayout.CENTER);
        add(painelEnvio, BorderLayout.SOUTH);
        pack();
        setLocationByPlatform(true);
    }

    private static void adicionarCampo(JPanel painel, GridBagConstraints restricao,
            int coluna, String rotulo, JTextField campo) {
        restricao.gridx = coluna * 2;
        restricao.gridy = 0;
        painel.add(new JLabel(rotulo), restricao);
        restricao.gridx = coluna * 2 + 1;
        painel.add(campo, restricao);
    }

    private void configurarAcoes() {
        botaoConectar.addActionListener(evento -> conectar());
        botaoEnviar.addActionListener(evento -> enviar());
        botaoDesconectar.addActionListener(evento -> desconectar());
        campoMensagem.addActionListener(evento -> enviar());
    }

    private void conectar() {
        if (cliente != null || conexaoEmAndamento) {
            return;
        }

        try {
            String host = campoHost.getText();
            ClienteChatApresentacao.validarHost(host);
            int porta = Integer.parseInt(campoPorta.getText().trim());
            ClienteChatApresentacao.validarPorta(porta);
            String nome = campoNome.getText();
            ClienteChatApresentacao.validarNome(nome);

            areaHistorico.setText("");
            conexaoEmAndamento = true;
            encerramentoIntencional = false;
            atualizarEstadoControles();
            rotuloEstado.setText("Conectando...");

            new SwingWorker<ClienteChat, Void>() {
                @Override
                protected ClienteChat doInBackground() throws Exception {
                    ClienteChat novoCliente = new ClienteChat(
                            host, porta, nome,
                            ClienteChatFrame.this::receberMensagem,
                            ClienteChatFrame.this::clienteDesconectou);
                    novoCliente.conectar();
                    return novoCliente;
                }

                @Override
                protected void done() {
                    try {
                        cliente = get();
                        rotuloEstado.setText("Conectado");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        rotuloEstado.setText("Desconectado");
                        exibirErro("A conexão foi interrompida.");
                    } catch (ExecutionException exception) {
                        rotuloEstado.setText("Desconectado");
                        exibirErro("Não foi possível conectar: " + causa(exception).getMessage());
                    } finally {
                        conexaoEmAndamento = false;
                        atualizarEstadoControles();
                    }
                }
            }.execute();
        } catch (IllegalArgumentException exception) {
            exibirErro(exception.getMessage());
        }
    }

    private void enviar() {
        ClienteChat clienteAtual = cliente;
        if (clienteAtual == null) {
            return;
        }

        String conteudo = campoMensagem.getText();
        try {
            ClienteChatApresentacao.validarConteudo(conteudo);
        } catch (IllegalArgumentException exception) {
            exibirErro(exception.getMessage());
            return;
        }

        String nome = campoNome.getText();
        campoMensagem.setEnabled(false);
        botaoEnviar.setEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                clienteAtual.enviar(conteudo);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    Mensagem propria = new Mensagem(
                            new Usuario(nome), conteudo, LocalDateTime.now());
                    exibirMensagem(propria);
                    campoMensagem.setText("");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exibirErro("O envio foi interrompido.");
                } catch (ExecutionException exception) {
                    exibirErro("Não foi possível enviar: " + causa(exception).getMessage());
                } finally {
                    atualizarEstadoControles();
                }
            }
        }.execute();
    }

    private void desconectar() {
        ClienteChat clienteAtual = cliente;
        if (clienteAtual == null) {
            return;
        }

        encerramentoIntencional = true;
        cliente = null;
        rotuloEstado.setText("Desconectando...");
        atualizarEstadoControles();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                clienteAtual.fechar();
                return null;
            }

            @Override
            protected void done() {
                rotuloEstado.setText("Desconectado");
                atualizarEstadoControles();
            }
        }.execute();
    }

    private void fecharJanela() {
        if (janelaEmFechamento) {
            return;
        }
        janelaEmFechamento = true;
        encerramentoIntencional = true;
        ClienteChat clienteAtual = cliente;
        cliente = null;
        atualizarEstadoControles();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                if (clienteAtual != null) {
                    clienteAtual.fechar();
                }
                return null;
            }

            @Override
            protected void done() {
                dispose();
            }
        }.execute();
    }

    private void receberMensagem(Mensagem mensagem) {
        SwingUtilities.invokeLater(() -> exibirMensagem(mensagem));
    }

    private void clienteDesconectou() {
        SwingUtilities.invokeLater(() -> {
            cliente = null;
            conexaoEmAndamento = false;
            rotuloEstado.setText("Desconectado");
            atualizarEstadoControles();
            if (!encerramentoIntencional && !janelaEmFechamento) {
                exibirErro("A conexão com o servidor foi encerrada.");
            }
        });
    }

    private void exibirMensagem(Mensagem mensagem) {
        areaHistorico.append(ClienteChatApresentacao.formatar(mensagem));
        areaHistorico.append(System.lineSeparator());
        areaHistorico.setCaretPosition(areaHistorico.getDocument().getLength());
    }

    private void atualizarEstadoControles() {
        boolean conectado = cliente != null;
        botaoConectar.setEnabled(!conectado && !conexaoEmAndamento && !janelaEmFechamento);
        botaoEnviar.setEnabled(conectado && !janelaEmFechamento);
        botaoDesconectar.setEnabled(conectado && !janelaEmFechamento);
        campoHost.setEnabled(!conectado && !conexaoEmAndamento);
        campoPorta.setEnabled(!conectado && !conexaoEmAndamento);
        campoNome.setEnabled(!conectado && !conexaoEmAndamento);
        campoMensagem.setEnabled(conectado && !janelaEmFechamento);
    }

    private void exibirErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Chat", JOptionPane.ERROR_MESSAGE);
    }

    private static Throwable causa(ExecutionException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }
}
