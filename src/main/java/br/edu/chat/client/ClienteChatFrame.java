package br.edu.chat.client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.BoxView;
import javax.swing.text.StyledEditorKit;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;
import br.edu.chat.config.ConfiguracaoTls;

/** Janela Swing do cliente TCP, independente das regras de servidor e persistência. */
public final class ClienteChatFrame extends JFrame {

    private static final Color FUNDO_AZUL_MARINHO = new Color(0x10, 0x1D, 0x33);
    private static final Color AZUL_SECUNDARIO = new Color(0x1E, 0x33, 0x52);
    private static final Color AZUL_PRINCIPAL = new Color(0x3B, 0x82, 0xF6);
    private static final Color TEXTO_CLARO = new Color(0xF1, 0xF5, 0xF9);
    private static final Color TEXTO_SECUNDARIO = new Color(0xC5, 0xD2, 0xE3);
    private static final Color BORDA = new Color(0x3B, 0x54, 0x78);
    private static final Color FUNDO_DESABILITADO = new Color(0x18, 0x2A, 0x45);
    private static final Color STATUS_CONECTADO = new Color(0x22, 0xC5, 0x5E);
    private static final Color STATUS_DESCONECTADO = new Color(0xEF, 0x44, 0x44);
    private static final Color STATUS_EM_ANDAMENTO = new Color(0xFA, 0xCC, 0x15);
    private static final Font FONTE_PADRAO = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font FONTE_BOTAO = new Font("SansSerif", Font.BOLD, 13);
    private static final Object ATRIBUTO_REMETENTE = new Object();
    private static final Object ATRIBUTO_CABECALHO = new Object();
    private static final Object ATRIBUTO_CONTEUDO = new Object();
    private static final Object ATRIBUTO_MENSAGEM_PROPRIA = new Object();
    private static final Object ATRIBUTO_ID_MENSAGEM = new Object();
    private static final String CARTAO_LOGIN = "LOGIN";
    private static final String CARTAO_CHAT = "CHAT";
    private static final Border BORDA_CAMPO = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDA),
            BorderFactory.createEmptyBorder(6, 8, 6, 8));

    private final JTextField campoHost = new JTextField("localhost", 15);
    private final JTextField campoPorta = new JTextField("5000", 6);
    private final JTextField campoLogin = new JTextField(12);
    private final JTextField campoNome = new JTextField(12);
    private final JPasswordField campoSenha = new JPasswordField(12);
    private final JCheckBox modoCadastro = new JCheckBox("Cadastrar");
    private final JTextPane areaHistorico = new JTextPane();
    private final JTextField campoMensagem = new JTextField();
    private final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JButton botaoDesconectar = new JButton("Desconectar");
    private final JLabel rotuloEstado = new JLabel("Desconectado");
    private final IndicadorStatusIcon iconeStatus = new IndicadorStatusIcon(
            STATUS_DESCONECTADO);
    private final JLabel indicadorEstado = new JLabel(iconeStatus);

    private volatile ClienteChat cliente;
    private volatile long geracaoSessao;
    private volatile boolean conexaoEmAndamento;
    private volatile boolean encerramentoIntencional;
    private volatile boolean janelaEmFechamento;
    private final ConfiguracaoTls configuracaoTls;
    private final CardLayout layoutCartoes = new CardLayout();
    private final JPanel painelCartoes = new JPanel(layoutCartoes);

    public ClienteChatFrame() {
        this(null);
    }

    ClienteChatFrame(ConfiguracaoTls configuracaoTls) {
        super("Chat Multiusuário");
        this.configuracaoTls = configuracaoTls;
        configurarJanela();
        construirComponentes();
        configurarAcoes();
        atualizarEstadoControles();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setResizable(true);
        setMinimumSize(new Dimension(760, 520));
        getContentPane().setBackground(FUNDO_AZUL_MARINHO);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                fecharJanela();
            }
        });
    }

    private void construirComponentes() {
        JLabel tituloLogin = new JLabel("Chat Multiusuário");
        tituloLogin.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 22f));
        tituloLogin.setForeground(TEXTO_CLARO);
        tituloLogin.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtituloLogin = new JLabel("Acesse sua conta para conversar");
        subtituloLogin.setFont(FONTE_PADRAO.deriveFont(Font.PLAIN, 13f));
        subtituloLogin.setForeground(TEXTO_SECUNDARIO);
        subtituloLogin.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel painelTituloLogin = new JPanel();
        painelTituloLogin.setLayout(new javax.swing.BoxLayout(
                painelTituloLogin, javax.swing.BoxLayout.Y_AXIS));
        painelTituloLogin.setBackground(AZUL_SECUNDARIO);
        painelTituloLogin.add(tituloLogin);
        painelTituloLogin.add(javax.swing.Box.createVerticalStrut(4));
        painelTituloLogin.add(subtituloLogin);

        JPanel painelAutenticacao = new JPanel();
        painelAutenticacao.setLayout(new javax.swing.BoxLayout(
                painelAutenticacao, javax.swing.BoxLayout.Y_AXIS));
        painelAutenticacao.setBackground(AZUL_SECUNDARIO);
        adicionarCampoVertical(painelAutenticacao, "Login:", campoLogin);
        adicionarCampoVertical(painelAutenticacao, "Nome:", campoNome);
        adicionarCampoVertical(painelAutenticacao, "Senha:", campoSenha);
        modoCadastro.setFont(FONTE_PADRAO);
        modoCadastro.setForeground(TEXTO_CLARO);
        modoCadastro.setBackground(AZUL_SECUNDARIO);
        modoCadastro.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelAutenticacao.add(modoCadastro);

        JPanel painelCamposConexao = new JPanel();
        painelCamposConexao.setLayout(new javax.swing.BoxLayout(
                painelCamposConexao, javax.swing.BoxLayout.Y_AXIS));
        painelCamposConexao.setBackground(AZUL_SECUNDARIO);
        painelCamposConexao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        adicionarCampoVertical(painelCamposConexao, "Host:", campoHost);
        adicionarCampoVertical(painelCamposConexao, "Porta:", campoPorta);
        painelCamposConexao.setVisible(false);

        JToggleButton botaoConfiguracoes = new JToggleButton(
                "Configurações de conexão  ▸");
        botaoConfiguracoes.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 12f));
        botaoConfiguracoes.setForeground(TEXTO_SECUNDARIO);
        botaoConfiguracoes.setBackground(AZUL_SECUNDARIO);
        botaoConfiguracoes.setBorderPainted(false);
        botaoConfiguracoes.setFocusPainted(false);
        botaoConfiguracoes.setContentAreaFilled(false);
        botaoConfiguracoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        botaoConfiguracoes.addActionListener(evento -> {
            boolean expandido = botaoConfiguracoes.isSelected();
            painelCamposConexao.setVisible(expandido);
            botaoConfiguracoes.setText(expandido
                    ? "Configurações de conexão  ▾"
                    : "Configurações de conexão  ▸");
            painelCamposConexao.getParent().revalidate();
            painelCamposConexao.getParent().getParent().revalidate();
            painelCamposConexao.getParent().getParent().repaint();
        });

        JPanel painelBotoesLogin = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelBotoesLogin.setBackground(AZUL_SECUNDARIO);
        painelBotoesLogin.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        estilizarBotao(botaoConectar);
        painelBotoesLogin.add(botaoConectar);

        JPanel painelLogin = new JPanel(new BorderLayout(0, 8));
        painelLogin.setBackground(FUNDO_AZUL_MARINHO);
        painelLogin.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        painelLogin.setPreferredSize(new Dimension(460, 430));

        JPanel painelFormulario = new JPanel();
        painelFormulario.setLayout(new javax.swing.BoxLayout(
                painelFormulario, javax.swing.BoxLayout.Y_AXIS));
        painelFormulario.setBackground(AZUL_SECUNDARIO);
        painelFormulario.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)));
        painelFormulario.add(painelTituloLogin);
        painelFormulario.add(javax.swing.Box.createVerticalStrut(24));
        painelFormulario.add(painelAutenticacao);
        painelFormulario.add(javax.swing.Box.createVerticalStrut(14));
        painelFormulario.add(botaoConfiguracoes);
        painelFormulario.add(javax.swing.Box.createVerticalStrut(8));
        painelFormulario.add(painelCamposConexao);
        painelFormulario.add(javax.swing.Box.createVerticalStrut(18));
        painelFormulario.add(painelBotoesLogin);

        JPanel painelLoginCentral = new JPanel(new GridBagLayout());
        painelLoginCentral.setBackground(FUNDO_AZUL_MARINHO);
        GridBagConstraints restricaoLogin = new GridBagConstraints();
        restricaoLogin.gridx = 0;
        restricaoLogin.gridy = 0;
        restricaoLogin.weightx = 1;
        restricaoLogin.weighty = 1;
        restricaoLogin.anchor = GridBagConstraints.CENTER;
        painelLoginCentral.add(painelFormulario, restricaoLogin);

        JScrollPane painelLoginRolagem = new JScrollPane(
                painelLoginCentral,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        painelLoginRolagem.setBorder(null);
        painelLoginRolagem.getViewport().setBackground(FUNDO_AZUL_MARINHO);
        painelLogin.add(painelLoginRolagem, BorderLayout.CENTER);

        JLabel tituloChat = new JLabel("Chat Multiusuário");
        tituloChat.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 16f));
        tituloChat.setForeground(TEXTO_CLARO);

        JPanel painelStatusChat = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelStatusChat.setBackground(AZUL_SECUNDARIO);
        painelStatusChat.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        estilizarBotao(botaoDesconectar);
        indicadorEstado.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2));
        rotuloEstado.setFont(FONTE_PADRAO);
        rotuloEstado.setForeground(TEXTO_CLARO);
        painelStatusChat.add(indicadorEstado);
        painelStatusChat.add(rotuloEstado);
        painelStatusChat.add(botaoDesconectar);

        JPanel painelCabecalhoChat = new JPanel(new BorderLayout(0, 8));
        painelCabecalhoChat.setBackground(AZUL_SECUNDARIO);
        painelCabecalhoChat.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        painelCabecalhoChat.add(tituloChat, BorderLayout.NORTH);
        painelCabecalhoChat.add(painelStatusChat, BorderLayout.SOUTH);

        estilizarCampo(campoMensagem);
        areaHistorico.setEditorKit(new MensagemEditorKit());
        areaHistorico.setEditable(false);
        areaHistorico.setFont(FONTE_PADRAO);
        areaHistorico.setForeground(TEXTO_CLARO);
        areaHistorico.setBackground(AZUL_SECUNDARIO);
        areaHistorico.setCaretColor(TEXTO_CLARO);
        areaHistorico.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        areaHistorico.setPreferredSize(new Dimension(500, 300));

        JScrollPane painelHistorico = new JScrollPane(areaHistorico);
        painelHistorico.setBorder(BorderFactory.createLineBorder(BORDA));
        painelHistorico.getViewport().setBackground(AZUL_SECUNDARIO);

        JPanel painelEnvio = new JPanel(new BorderLayout(4, 4));
        painelEnvio.setBackground(FUNDO_AZUL_MARINHO);
        painelEnvio.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        estilizarBotao(botaoEnviar);
        painelEnvio.add(campoMensagem, BorderLayout.CENTER);
        painelEnvio.add(botaoEnviar, BorderLayout.EAST);

        JPanel painelChat = new JPanel(new BorderLayout(0, 8));
        painelChat.setBackground(FUNDO_AZUL_MARINHO);
        painelChat.add(painelCabecalhoChat, BorderLayout.NORTH);
        painelChat.add(painelHistorico, BorderLayout.CENTER);
        painelChat.add(painelEnvio, BorderLayout.SOUTH);

        painelCartoes.setBackground(FUNDO_AZUL_MARINHO);
        painelCartoes.add(painelLogin, CARTAO_LOGIN);
        painelCartoes.add(painelChat, CARTAO_CHAT);
        layoutCartoes.show(painelCartoes, CARTAO_LOGIN);

        add(painelCartoes, BorderLayout.CENTER);
        pack();
        setLocationByPlatform(true);
    }

    private static void adicionarCampo(JPanel painel, GridBagConstraints restricao,
            int coluna, String rotulo, JTextField campo) {
        restricao.gridx = coluna * 2;
        restricao.gridy = 0;
        JLabel etiqueta = new JLabel(rotulo);
        etiqueta.setFont(FONTE_PADRAO);
        etiqueta.setForeground(TEXTO_CLARO);
        painel.add(etiqueta, restricao);
        restricao.gridx = coluna * 2 + 1;
        estilizarCampo(campo);
        painel.add(campo, restricao);
    }

    private static void adicionarCampoVertical(JPanel painel, String rotulo,
            JTextField campo) {
        JPanel grupo = new JPanel(new BorderLayout(0, 4));
        grupo.setBackground(AZUL_SECUNDARIO);
        grupo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel etiqueta = new JLabel(rotulo);
        etiqueta.setFont(FONTE_PADRAO);
        etiqueta.setForeground(TEXTO_CLARO);
        grupo.add(etiqueta, BorderLayout.NORTH);

        estilizarCampo(campo);
        grupo.add(campo, BorderLayout.CENTER);
        grupo.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                grupo.getPreferredSize().height));

        painel.add(grupo);
        painel.add(javax.swing.Box.createVerticalStrut(8));
    }

    private static void estilizarCampo(JTextField campo) {
        campo.setFont(FONTE_PADRAO);
        campo.setForeground(TEXTO_CLARO);
        campo.setBackground(AZUL_SECUNDARIO);
        campo.setCaretColor(TEXTO_CLARO);
        campo.setDisabledTextColor(TEXTO_SECUNDARIO);
        campo.setBorder(BORDA_CAMPO);
        campo.setSelectionColor(AZUL_PRINCIPAL);
        campo.setSelectedTextColor(TEXTO_CLARO);
    }

    private static void estilizarBotao(JButton botao) {
        botao.setFont(FONTE_BOTAO);
        botao.setForeground(TEXTO_CLARO);
        botao.setBackground(AZUL_PRINCIPAL);
        botao.setFocusPainted(false);
        botao.setBorderPainted(true);
        botao.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AZUL_PRINCIPAL.darker()),
                BorderFactory.createEmptyBorder(7, 13, 7, 13)));
        botao.setMargin(new Insets(0, 0, 0, 0));
        botao.setOpaque(true);
        botao.addPropertyChangeListener("enabled",
                evento -> atualizarContrasteBotao(botao));
        atualizarContrasteBotao(botao);
    }

    private static void atualizarContrasteBotao(JButton botao) {
        if (botao.isEnabled()) {
            botao.setBackground(AZUL_PRINCIPAL);
            botao.setForeground(TEXTO_CLARO);
            botao.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AZUL_PRINCIPAL.darker()),
                    BorderFactory.createEmptyBorder(7, 13, 7, 13)));
        } else {
            botao.setBackground(FUNDO_DESABILITADO);
            botao.setForeground(TEXTO_SECUNDARIO);
            botao.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDA),
                    BorderFactory.createEmptyBorder(7, 13, 7, 13)));
        }
    }

    private void atualizarEstado(String estado) {
        rotuloEstado.setText(estado);
        if ("Conectado".equals(estado)) {
            iconeStatus.setCor(STATUS_CONECTADO);
        } else if ("Conectando...".equals(estado)
                || "Desconectando...".equals(estado)) {
            iconeStatus.setCor(STATUS_EM_ANDAMENTO);
        } else {
            iconeStatus.setCor(STATUS_DESCONECTADO);
        }
        indicadorEstado.repaint();
    }

    private void configurarAcoes() {
        botaoConectar.addActionListener(evento -> conectar());
        botaoEnviar.addActionListener(evento -> enviar());
        botaoDesconectar.addActionListener(evento -> desconectar());
        campoMensagem.addActionListener(evento -> enviar());
        modoCadastro.addActionListener(evento -> atualizarEstadoControles());
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
            String login = campoLogin.getText();
            ClienteChatApresentacao.validarNome(login);
            boolean cadastrar = modoCadastro.isSelected();
            String nome = cadastrar ? campoNome.getText() : login;
            if (cadastrar) {
                ClienteChatApresentacao.validarNome(nome);
            }
            char[] senha = campoSenha.getPassword();
            if (senha.length == 0) {
                throw new IllegalArgumentException("A senha nÃ£o pode ser vazia.");
            }

            areaHistorico.setText("");
            conexaoEmAndamento = true;
            encerramentoIntencional = false;
            atualizarEstadoControles();
            atualizarEstado("Conectando...");
            long geracao = ++geracaoSessao;

            new SwingWorker<ClienteChat, Void>() {
                @Override
                protected ClienteChat doInBackground() throws Exception {
                    ClienteChat novoCliente = new ClienteChat(
                            host, porta, nome,
                            configuracaoTls != null ? configuracaoTls : carregarConfiguracaoTls(),
                            mensagem -> receberMensagem(geracao, mensagem),
                            () -> clienteDesconectou(geracao));
                    if (!sessaoAtual(geracao)) {
                        novoCliente.fechar();
                        return novoCliente;
                    }
                    cliente = novoCliente;
                    try {
                        if (cadastrar) {
                            novoCliente.cadastrarV2(login, nome, senha);
                        } else {
                            novoCliente.conectarV2(login, senha);
                        }
                    } catch (Exception exception) {
                        if (sessaoAtual(geracao)) {
                            cliente = null;
                        } else {
                            novoCliente.fechar();
                        }
                        throw exception;
                    } finally {
                        Arrays.fill(senha, '\0');
                    }
                    if (!sessaoAtual(geracao)) {
                        novoCliente.fechar();
                    }
                    return novoCliente;
                }

                @Override
                protected void done() {
                    try {
                        ClienteChat clienteAutenticado = get();
                        if (!sessaoAtual(geracao)) {
                            clienteAutenticado.fechar();
                            return;
                        }
                        String nomeCanonico = clienteAutenticado.getNomeAutenticado()
                                .orElseThrow(() -> new IllegalStateException(
                                        "O servidor nao retornou o nome canonico."));
                        ClienteChatApresentacao.validarNome(nomeCanonico);
                        cliente = clienteAutenticado;
                        campoNome.setText(nomeCanonico);
                        atualizarEstado("Conectado");
                        mostrarCartao(CARTAO_CHAT);
                    } catch (InterruptedException exception) {
                        if (!sessaoAtual(geracao)) {
                            return;
                        }
                        Thread.currentThread().interrupt();
                        atualizarEstado("Desconectado");
                        exibirErro("A conexão foi interrompida.");
                    } catch (ExecutionException exception) {
                        if (!sessaoAtual(geracao)) {
                            return;
                        }
                        atualizarEstado("Desconectado");
                        exibirErro("Não foi possível conectar: " + causa(exception).getMessage());
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        if (!sessaoAtual(geracao)) {
                            return;
                        }
                        atualizarEstado("Desconectado");
                        exibirErro("Falha na identidade retornada pelo servidor.");
                    } finally {
                        if (sessaoAtual(geracao)) {
                            conexaoEmAndamento = false;
                            atualizarEstadoControles();
                        }
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

        String nome = clienteAtual.getNomeAutenticado().orElse(null);
        if (nome == null) {
            exibirErro("Não há identidade autenticada para enviar a mensagem.");
            return;
        }
        campoMensagem.setEnabled(false);
        botaoEnviar.setEnabled(false);
        long geracao = geracaoSessao;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                clienteAtual.enviar(conteudo);
                return null;
            }

            @Override
            protected void done() {
                if (!sessaoAtual(geracao)) {
                    return;
                }
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

    private static ConfiguracaoTls carregarConfiguracaoTls() {
        char[] senhaKeystore = obrigatoria("CHAT_TLS_KEYSTORE_PASSWORD").toCharArray();
        char[] senhaTruststore = obrigatoria("CHAT_TLS_TRUSTSTORE_PASSWORD").toCharArray();
        try {
            return new ConfiguracaoTls(
                    Path.of(obrigatoria("CHAT_TLS_KEYSTORE")), senhaKeystore,
                    Path.of(obrigatoria("CHAT_TLS_TRUSTSTORE")), senhaTruststore);
        } finally {
            Arrays.fill(senhaKeystore, '\0');
            Arrays.fill(senhaTruststore, '\0');
        }
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Configuração TLS ausente: " + nome);
        }
        return valor;
    }

    private void desconectar() {
        ClienteChat clienteAtual = cliente;
        if (clienteAtual == null) {
            return;
        }

        long geracaoEncerrada = ++geracaoSessao;
        encerramentoIntencional = true;
        cliente = null;
        atualizarEstado("Desconectando...");
        atualizarEstadoControles();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                clienteAtual.fechar();
                return null;
            }

            @Override
            protected void done() {
                if (!sessaoAtual(geracaoEncerrada)) {
                    return;
                }
                atualizarEstado("Desconectado");
                atualizarEstadoControles();
                mostrarCartao(CARTAO_LOGIN);
            }
        }.execute();
    }

    private void fecharJanela() {
        if (janelaEmFechamento) {
            return;
        }
        janelaEmFechamento = true;
        encerramentoIntencional = true;
        long geracaoEncerrada = ++geracaoSessao;
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
                if (sessaoAtual(geracaoEncerrada)) {
                    dispose();
                }
            }
        }.execute();
    }

    private void receberMensagem(long geracao, Mensagem mensagem) {
        if (!sessaoAtual(geracao)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!sessaoAtual(geracao)) {
                return;
            }
            exibirMensagem(mensagem, mensagemEhPropria(mensagem));
        });
    }

    private void clienteDesconectou(long geracao) {
        if (!sessaoAtual(geracao)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!sessaoAtual(geracao)) {
                return;
            }
            ++geracaoSessao;
            cliente = null;
            conexaoEmAndamento = false;
            atualizarEstado("Desconectado");
            atualizarEstadoControles();
            mostrarCartao(CARTAO_LOGIN);
            if (!encerramentoIntencional && !janelaEmFechamento) {
                exibirErro("A conexão com o servidor foi encerrada.");
            }
        });
    }

    private boolean sessaoAtual(long geracao) {
        return geracaoSessao == geracao;
    }

    private void exibirMensagem(Mensagem mensagem) {
        exibirMensagem(mensagem, mensagemEhPropria(mensagem));
    }

    private boolean mensagemEhPropria(Mensagem mensagem) {
        ClienteChat clienteAtual = cliente;
        if (clienteAtual == null) {
            return false;
        }
        String nomeAutenticado = clienteAtual.getNomeAutenticado().orElse(null);
        return nomeAutenticado != null
                && mensagem.getRemetente().getNome().equals(nomeAutenticado);
    }

    private static MensagemPartes identificarCabecalhoEConteudo(String texto,
            String remetente) {
        if (texto == null || remetente == null) {
            return new MensagemPartes("", texto == null ? "" : texto);
        }

        int fimHorario = texto.indexOf(']');
        if (fimHorario < 3 || texto.charAt(0) != '[') {
            return new MensagemPartes("", texto);
        }

        String horario = texto.substring(1, fimHorario);
        if (!horario.matches("\\d{2}(?::\\d{2})?")) {
            return new MensagemPartes("", texto);
        }

        String prefixo = texto.substring(0, fimHorario + 1) + " ";
        String separador = prefixo + remetente + ": ";
        if (!texto.startsWith(separador)) {
            return new MensagemPartes("", texto);
        }

        return new MensagemPartes(prefixo + remetente,
                texto.substring(separador.length()));
    }

    private void exibirMensagem(Mensagem mensagem, boolean propria) {
        String textoFormatado = ClienteChatApresentacao.formatar(mensagem);
        String texto = textoFormatado + System.lineSeparator();
        String idMensagem = UUID.randomUUID().toString();
        MensagemPartes partes = identificarCabecalhoEConteudo(textoFormatado,
                mensagem.getRemetente().getNome());
        SimpleAttributeSet atributosTexto = criarAtributosTexto(propria);
        atributosTexto.addAttribute(ATRIBUTO_REMETENTE, mensagem.getRemetente().getNome());
        atributosTexto.addAttribute(ATRIBUTO_CABECALHO, partes.cabecalho());
        atributosTexto.addAttribute(ATRIBUTO_CONTEUDO, partes.conteudo());
        atributosTexto.addAttribute(ATRIBUTO_MENSAGEM_PROPRIA, propria);
        atributosTexto.addAttribute(ATRIBUTO_ID_MENSAGEM, idMensagem);
        StyledDocument documento = areaHistorico.getStyledDocument();
        try {
            int inicio = documento.getLength();
            documento.insertString(inicio, texto, atributosTexto);
            SimpleAttributeSet atributosParagrafo = criarAtributosParagrafo(propria);
            atributosParagrafo.addAttribute(ATRIBUTO_ID_MENSAGEM, idMensagem);
            documento.setParagraphAttributes(inicio, textoFormatado.length(),
                    atributosParagrafo, false);
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Não foi possível exibir a mensagem.", exception);
        }
        areaHistorico.setCaretPosition(documento.getLength());
    }

    private SimpleAttributeSet criarAtributosTexto(boolean propria) {
        SimpleAttributeSet atributos = new SimpleAttributeSet();
        StyleConstants.setForeground(atributos, TEXTO_CLARO);
        StyleConstants.setFontFamily(atributos, FONTE_PADRAO.getFamily());
        StyleConstants.setFontSize(atributos, FONTE_PADRAO.getSize());
        return atributos;
    }

    private SimpleAttributeSet criarAtributosParagrafo(boolean propria) {
        SimpleAttributeSet atributos = new SimpleAttributeSet();
        StyleConstants.setAlignment(atributos,
                propria ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
        StyleConstants.setBackground(atributos,
                propria ? AZUL_PRINCIPAL : AZUL_SECUNDARIO);
        StyleConstants.setSpaceAbove(atributos, 5);
        StyleConstants.setSpaceBelow(atributos, 5);
        return atributos;
    }

    private void atualizarEstadoControles() {
        boolean conectado = cliente != null;
        botaoConectar.setEnabled(!conectado && !conexaoEmAndamento && !janelaEmFechamento);
        botaoEnviar.setEnabled(conectado && !janelaEmFechamento);
        botaoDesconectar.setEnabled(conectado && !janelaEmFechamento);
        campoHost.setEnabled(!conectado && !conexaoEmAndamento);
        campoPorta.setEnabled(!conectado && !conexaoEmAndamento);
        campoLogin.setEnabled(!conectado && !conexaoEmAndamento);
        campoNome.setEnabled(!conectado && !conexaoEmAndamento
                && modoCadastro.isSelected());
        campoSenha.setEnabled(!conectado && !conexaoEmAndamento);
        modoCadastro.setEnabled(!conectado && !conexaoEmAndamento);
        campoMensagem.setEnabled(conectado && !janelaEmFechamento);
    }

    private void mostrarCartao(String nomeCartao) {
        if (SwingUtilities.isEventDispatchThread()) {
            layoutCartoes.show(painelCartoes, nomeCartao);
            return;
        }

        SwingUtilities.invokeLater(() -> layoutCartoes.show(painelCartoes, nomeCartao));
    }

    private void exibirErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Chat", JOptionPane.ERROR_MESSAGE);
    }

    private static Throwable causa(ExecutionException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }

    private static final class MensagemEditorKit extends StyledEditorKit {

        private final ViewFactory fabrica = new MensagemViewFactory();

        @Override
        public ViewFactory getViewFactory() {
            return fabrica;
        }
    }

    private static final class MensagemViewFactory implements ViewFactory {

        @Override
        public View create(Element elemento) {
            String nome = elemento.getName();
            if (AbstractDocument.ContentElementName.equals(nome)) {
                return new MensagemConteudoView(elemento);
            }
            if (AbstractDocument.ParagraphElementName.equals(nome)) {
                return new MensagemParagraphView(elemento);
            }
            if (AbstractDocument.SectionElementName.equals(nome)) {
                return new BoxView(elemento, View.Y_AXIS);
            }
            if (StyleConstants.ComponentElementName.equals(nome)) {
                return new ComponentView(elemento);
            }
            if (StyleConstants.IconElementName.equals(nome)) {
                return new IconView(elemento);
            }
            return new LabelView(elemento);
        }
    }

    private static final class MensagemConteudoView extends LabelView {

        private static final int MARGEM_HORIZONTAL = 24;
        private static final int MARGEM_VERTICAL = 16;
        private static final int TAMANHO_FONTE_CABECALHO = 12;
        private static final int LARGURA_MAXIMA = 520;
        private static final int LARGURA_NAO_CALCULADA = -1;

        private MensagemDimensoes dimensoesDoFluxo;
        private int larguraDasDimensoesDoFluxo = LARGURA_NAO_CALCULADA;

        private MensagemConteudoView(Element elemento) {
            super(elemento);
        }

        private DadosMensagem obterDadosMensagem() {
            AttributeSet atributos = getElement().getAttributes();
            return new DadosMensagem(
                    (String) atributos.getAttribute(ATRIBUTO_CABECALHO),
                    (String) atributos.getAttribute(ATRIBUTO_CONTEUDO),
                    Boolean.TRUE.equals(atributos.getAttribute(ATRIBUTO_MENSAGEM_PROPRIA)),
                    (String) atributos.getAttribute(ATRIBUTO_ID_MENSAGEM));
        }

        private MensagemDimensoes calcularDimensoes(float larguraDisponivel) {
            int larguraInternaDisponivel = Math.max(1,
                    Math.round(larguraDisponivel) - MARGEM_HORIZONTAL);
            return calcularDimensoesComLarguraInterna(larguraInternaDisponivel);
        }

        private MensagemDimensoes calcularDimensoesNoFluxo(float larguraFluxo) {
            return calcularDimensoesComLarguraInterna(Math.max(1,
                    Math.round(larguraFluxo)));
        }

        private void atualizarDimensoesDoFluxo(float larguraFluxo) {
            DadosMensagem dados = obterDadosMensagem();
            if (dados.cabecalho() == null || dados.conteudo() == null) {
                dimensoesDoFluxo = null;
                larguraDasDimensoesDoFluxo = LARGURA_NAO_CALCULADA;
                return;
            }
            int larguraNormalizada = normalizarLargura(larguraFluxo);
            if (larguraDasDimensoesDoFluxo == larguraNormalizada) {
                return;
            }
            dimensoesDoFluxo = calcularDimensoesNoFluxo(larguraNormalizada);
            larguraDasDimensoesDoFluxo = larguraNormalizada;
        }

        private MensagemDimensoes obterDimensoesDoFluxo() {
            if (dimensoesDoFluxo == null) {
                return calcularDimensoesNoFluxo(LARGURA_MAXIMA - MARGEM_HORIZONTAL);
            }
            return dimensoesDoFluxo;
        }

        @Override
        public float getPreferredSpan(int eixo) {
            if (eixo == View.Y_AXIS) {
                DadosMensagem dados = obterDadosMensagem();
                if (dados.cabecalho() == null || dados.conteudo() == null) {
                    return super.getPreferredSpan(View.Y_AXIS);
                }
                if (ehFragmentoDeQuebra()) {
                    return super.getPreferredSpan(View.Y_AXIS);
                }
                return alturaCompartilhada(obterDimensoesDoFluxo());
            }
            return super.getPreferredSpan(eixo);
        }

        private boolean ehFragmentoDeQuebra() {
            return getStartOffset() != getElement().getStartOffset()
                    || getEndOffset() != getElement().getEndOffset();
        }

        private float alturaCompartilhada(MensagemDimensoes dimensoes) {
            String idMensagem = obterDadosMensagem().idMensagem();
            if (idMensagem == null) {
                return dimensoes.altura();
            }
            List<Element> paragrafos = paragrafosDaMensagem(idMensagem);
            if (paragrafos.size() <= 1) {
                return dimensoes.altura();
            }
            Element paragrafoAtual = getElement().getParentElement();
            int indice = paragrafos.indexOf(paragrafoAtual);
            if (indice < 0) {
                return dimensoes.altura();
            }
            List<List<String>> linhasPorParagrafo =
                    dimensoes.linhas().conteudoPorParagrafo();
            if (linhasPorParagrafo.size() != paragrafos.size()) {
                return dimensoes.altura();
            }
            int quantidadeLinhasDoParagrafo = linhasPorParagrafo.get(indice).size();
            int altura = quantidadeLinhasDoParagrafo
                    * (dimensoes.linhasConteudo() == 0
                            ? 0
                            : dimensoes.alturaConteudo() / dimensoes.linhasConteudo());
            if (indice == 0) {
                altura += dimensoes.alturaCabecalho() + MARGEM_VERTICAL;
            }
            return altura;
        }

        private List<Element> paragrafosDaMensagem(String idMensagem) {
            List<Element> resultado = new ArrayList<>();
            Element raiz = getDocument().getDefaultRootElement();
            for (int indice = 0; indice < raiz.getElementCount(); indice++) {
                Element paragrafo = raiz.getElement(indice);
                if (idMensagem.equals(idDoParagrafo(paragrafo))) {
                    resultado.add(paragrafo);
                }
            }
            return resultado;
        }

        private static String idDoParagrafo(Element paragrafo) {
            Object id = paragrafo.getAttributes().getAttribute(ATRIBUTO_ID_MENSAGEM);
            if (id instanceof String) {
                return (String) id;
            }
            for (int indice = 0; indice < paragrafo.getElementCount(); indice++) {
                Object idDoTrecho = paragrafo.getElement(indice).getAttributes()
                        .getAttribute(ATRIBUTO_ID_MENSAGEM);
                if (idDoTrecho instanceof String) {
                    return (String) idDoTrecho;
                }
            }
            return null;
        }

        private static int normalizarLargura(float larguraFluxo) {
            return Math.max(1, Math.round(larguraFluxo));
        }

        private MensagemDimensoes calcularDimensoesComLarguraInterna(
                int larguraInternaDisponivel) {
            DadosMensagem dados = obterDadosMensagem();
            Font fonteConteudo = getFont();
            Font fonteCabecalho = fonteConteudo.deriveFont(Font.BOLD,
                    TAMANHO_FONTE_CABECALHO);
            FontMetrics metricasConteudo = obterMetricas(fonteConteudo);
            FontMetrics metricasCabecalho = obterMetricas(fonteCabecalho);

            int larguraNatural = Math.max(
                    maiorLarguraDeLinha(dados.cabecalho(), metricasCabecalho),
                    maiorLarguraDeLinha(dados.conteudo(), metricasConteudo));
            int larguraBolha = Math.min(LARGURA_MAXIMA,
                    Math.min(larguraInternaDisponivel + MARGEM_HORIZONTAL,
                            Math.max(1, larguraNatural + MARGEM_HORIZONTAL)));
            int larguraTexto = Math.max(1, larguraBolha - MARGEM_HORIZONTAL);
            List<String> linhasConteudo = quebrarLinhas(dados.conteudo(),
                    metricasConteudo, larguraTexto);
            List<List<String>> linhasConteudoPorParagrafo =
                    quebrarLinhasPorParagrafo(dados.conteudo(), metricasConteudo,
                            larguraTexto);
            if (quantidadeDeLinhas(linhasConteudoPorParagrafo) != linhasConteudo.size()) {
                throw new IllegalStateException(
                        "As linhas agrupadas não correspondem às linhas do conteúdo.");
            }
            int quantidadeLinhasConteudo = linhasConteudo.size();
            LinhasVisuais linhas = new LinhasVisuais(
                    quebrarLinhas(dados.cabecalho(), metricasCabecalho, larguraTexto),
                    linhasConteudo, linhasConteudoPorParagrafo);
            int linhasCabecalho = linhas.cabecalho().size();
            int alturaCabecalho = linhasCabecalho * metricasCabecalho.getHeight();
            int alturaConteudo = quantidadeLinhasConteudo * metricasConteudo.getHeight();
            return new MensagemDimensoes(larguraBolha,
                    alturaCabecalho + alturaConteudo + MARGEM_VERTICAL,
                    linhasCabecalho, quantidadeLinhasConteudo,
                    alturaCabecalho, alturaConteudo,
                    linhas);
        }

        private static FontMetrics obterMetricas(Font fonte) {
            return new java.awt.Canvas().getFontMetrics(fonte);
        }

        private static int maiorLarguraDeLinha(String texto, FontMetrics metricas) {
            int maior = 0;
            for (String linha : texto.split("\\R", -1)) {
                maior = Math.max(maior, metricas.stringWidth(linha));
            }
            return maior;
        }

        private static List<String> quebrarLinhas(String texto, FontMetrics metricas,
                int larguraDisponivel) {
            List<String> linhas = new ArrayList<>();
            for (String linha : texto.split("\\R", -1)) {
                quebrarLinha(linha, metricas, larguraDisponivel, linhas);
            }
            return List.copyOf(linhas);
        }

        private static List<List<String>> quebrarLinhasPorParagrafo(String texto,
                FontMetrics metricas, int larguraDisponivel) {
            List<List<String>> linhasPorParagrafo = new ArrayList<>();
            for (String paragrafo : texto.split("\\R", -1)) {
                linhasPorParagrafo.add(quebrarLinhas(paragrafo, metricas,
                        larguraDisponivel));
            }
            return List.copyOf(linhasPorParagrafo);
        }

        private static int quantidadeDeLinhas(List<List<String>> linhasPorParagrafo) {
            int quantidade = 0;
            for (List<String> linhas : linhasPorParagrafo) {
                quantidade += linhas.size();
            }
            return quantidade;
        }

        private static void quebrarLinha(String texto, FontMetrics metricas,
                int larguraDisponivel, List<String> linhas) {
            if (texto.isEmpty()) {
                linhas.add("");
                return;
            }
            StringBuilder linhaVisual = new StringBuilder();
            int larguraAtual = 0;
            for (int indice = 0; indice < texto.length(); indice++) {
                int larguraCaractere = metricas.charWidth(texto.charAt(indice));
                if (larguraAtual > 0
                        && larguraAtual + larguraCaractere > larguraDisponivel) {
                    linhas.add(linhaVisual.toString());
                    linhaVisual.setLength(0);
                    larguraAtual = 0;
                }
                linhaVisual.append(texto.charAt(indice));
                larguraAtual += larguraCaractere;
            }
            linhas.add(linhaVisual.toString());
        }
    }

    private record MensagemPartes(String cabecalho, String conteudo) {
    }

    private record DadosMensagem(String cabecalho, String conteudo, boolean propria,
            String idMensagem) {
    }

    private record LinhasVisuais(List<String> cabecalho, List<String> conteudo,
            List<List<String>> conteudoPorParagrafo) {
        private LinhasVisuais {
            cabecalho = List.copyOf(cabecalho);
            conteudo = List.copyOf(conteudo);
            List<List<String>> copia = new ArrayList<>();
            for (List<String> linhas : conteudoPorParagrafo) {
                copia.add(List.copyOf(linhas));
            }
            conteudoPorParagrafo = List.copyOf(copia);
        }

        private LinhasVisuais(List<String> cabecalho, List<String> conteudo) {
            this(cabecalho, conteudo, List.of());
        }
    }

    private record MensagemDimensoes(int largura, int altura,
            int linhasCabecalho, int linhasConteudo,
            int alturaCabecalho, int alturaConteudo, LinhasVisuais linhas) {
    }

    private static final class MensagemParagraphView extends ParagraphView {

        private static final int LARGURA_MAXIMA = 520;
        private static final int LARGURA_MINIMA = 96;
        private static final int MARGEM_HORIZONTAL = 24;
        private static final int RAIO = 16;

        private MensagemParagraphView(Element elemento) {
            super(elemento);
            setInsets((short) 8, (short) 12, (short) 8, (short) 12);
        }

        @Override
        public int getFlowSpan(int indice) {
            int larguraDisponivel = super.getFlowSpan(indice);
            int larguraFluxo = Math.max(1, Math.min(larguraDisponivel,
                    LARGURA_MAXIMA - MARGEM_HORIZONTAL));
            atualizarDimensoesDasViews(this, larguraFluxo);
            return larguraFluxo;
        }

        private static void atualizarDimensoesDasViews(View view, float larguraFluxo) {
            if (view instanceof MensagemConteudoView conteudo) {
                conteudo.atualizarDimensoesDoFluxo(larguraFluxo);
                return;
            }
            for (int filho = 0; filho < view.getViewCount(); filho++) {
                atualizarDimensoesDasViews(view.getView(filho), larguraFluxo);
            }
        }

        @Override
        public float getPreferredSpan(int eixo) {
            float preferencia = super.getPreferredSpan(eixo);
            if (eixo == View.Y_AXIS) {
                return preferencia + alturaReservadaParaCabecalho();
            }
            if (eixo == View.X_AXIS) {
                return Math.min(preferencia, LARGURA_MAXIMA);
            }
            return preferencia;
        }

        private float alturaReservadaParaCabecalho() {
            MensagemConteudoView conteudo = encontrarMensagemConteudoView(this);
            if (conteudo == null) {
                return 0;
            }
            DadosMensagem dados = conteudo.obterDadosMensagem();
            if (dados.idMensagem() == null || dados.cabecalho() == null
                    || dados.conteudo() == null || pinturaManualSimples(conteudo)) {
                return 0;
            }
            List<Element> paragrafos = conteudo.paragrafosDaMensagem(
                    dados.idMensagem());
            if (paragrafos.isEmpty() || paragrafos.get(0) != getElement()) {
                return 0;
            }
            return conteudo.obterDimensoesDoFluxo().alturaCabecalho();
        }

        @Override
        public void paint(Graphics grafico, Shape alocacao) {
            if (paragrafoSemConteudo()) {
                return;
            }
            MensagemConteudoView conteudo = encontrarMensagemConteudoView(this);
            if (pinturaManualSimples(conteudo)) {
                pintarMensagemSimples(grafico, alocacao, conteudo);
                return;
            }
            Rectangle balao = calcularRetanguloDoBalaoLongo(alocacao);

            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color cor = StyleConstants.getBackground(getAttributes());
                if (cor == null) {
                    cor = AZUL_SECUNDARIO;
                }
                grafico2d.setColor(cor);
                grafico2d.fillRoundRect(balao.x, balao.y, balao.width,
                        balao.height, RAIO, RAIO);
            } finally {
                grafico2d.dispose();
            }

            super.paint(grafico, alocacao);
        }

        private Rectangle calcularRetanguloDoBalaoLongo(Shape alocacao) {
            Rectangle area = alocacao.getBounds();
            int larguraInterna = Math.max(1,
                    area.width - getLeftInset() - getRightInset());
            int larguraNatural = Math.round(super.getPreferredSpan(View.X_AXIS));
            int larguraBolha = Math.max(LARGURA_MINIMA,
                    Math.min(LARGURA_MAXIMA, larguraNatural));
            larguraBolha = Math.min(larguraBolha, larguraInterna);
            int xInterno = area.x + getLeftInset();
            boolean alinhadoDireita = StyleConstants.getAlignment(getAttributes())
                    == StyleConstants.ALIGN_RIGHT;
            int x = alinhadoDireita
                    ? xInterno + larguraInterna - larguraBolha
                    : xInterno;
            int alturaInterna = Math.max(1,
                    area.height - getTopInset() - getBottomInset());
            return new Rectangle(x, area.y + getTopInset(), larguraBolha,
                    alturaInterna);
        }

        private static MensagemConteudoView encontrarMensagemConteudoView(View view) {
            if (view instanceof MensagemConteudoView conteudo) {
                return conteudo;
            }
            for (int indice = 0; indice < view.getViewCount(); indice++) {
                MensagemConteudoView encontrada = encontrarMensagemConteudoView(
                        view.getView(indice));
                if (encontrada != null) {
                    return encontrada;
                }
            }
            return null;
        }

        private boolean pinturaManualSimples(MensagemConteudoView conteudo) {
            if (conteudo == null) {
                return false;
            }
            DadosMensagem dados = conteudo.obterDadosMensagem();
            if (dados.idMensagem() == null) {
                return false;
            }
            List<Element> paragrafos = conteudo.paragrafosDaMensagem(dados.idMensagem());
            if (paragrafos.size() != 1
                    || paragrafos.get(0) != getElement()) {
                return false;
            }
            MensagemDimensoes dimensoes = conteudo.obterDimensoesDoFluxo();
            LinhasVisuais linhas = dimensoes.linhas();
            return linhas.cabecalho().size() == 1
                    && linhas.conteudo().size() == 1
                    && linhas.conteudoPorParagrafo().size() == 1
                    && linhas.conteudoPorParagrafo().get(0).size() == 1
                    && !dados.cabecalho().matches(".*\\R.*")
                    && !dados.conteudo().matches(".*\\R.*");
        }

        private void pintarMensagemSimples(Graphics grafico, Shape alocacao,
                MensagemConteudoView conteudo) {
            MensagemDimensoes dimensoes = conteudo.obterDimensoesDoFluxo();
            DadosMensagem dados = conteudo.obterDadosMensagem();
            Rectangle balao = calcularRetanguloDoBalao(alocacao, dimensoes);
            boolean alinhadoDireita = StyleConstants.getAlignment(getAttributes())
                    == StyleConstants.ALIGN_RIGHT;
            int margemHorizontal = MensagemConteudoView.MARGEM_HORIZONTAL / 2;
            Font fonteConteudo = conteudo.getFont();
            Font fonteCabecalho = fonteConteudo.deriveFont(Font.BOLD,
                    MensagemConteudoView.TAMANHO_FONTE_CABECALHO);
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                FontMetrics metricasCabecalho = grafico2d.getFontMetrics(fonteCabecalho);
                FontMetrics metricasConteudo = grafico2d.getFontMetrics(fonteConteudo);
                Color cor = StyleConstants.getBackground(getAttributes());
                if (cor == null) {
                    cor = AZUL_SECUNDARIO;
                }
                grafico2d.setColor(cor);
                grafico2d.fillRoundRect(balao.x, balao.y, balao.width,
                        balao.height,
                        RAIO, RAIO);

                Color corTexto = StyleConstants.getForeground(conteudo.getAttributes());
                if (corTexto == null) {
                    corTexto = TEXTO_CLARO;
                }
                grafico2d.setColor(corTexto);
                int inicioTexto = balao.y
                        + MensagemConteudoView.MARGEM_VERTICAL / 2;
                desenharLinha(grafico2d, dados.cabecalho(), fonteCabecalho,
                        metricasCabecalho, balao.x, balao.width, margemHorizontal,
                        inicioTexto + metricasCabecalho.getAscent(), alinhadoDireita);
                desenharLinha(grafico2d, dados.conteudo(), fonteConteudo,
                        metricasConteudo, balao.x, balao.width, margemHorizontal,
                        inicioTexto + dimensoes.alturaCabecalho()
                                + metricasConteudo.getAscent(), alinhadoDireita);
            } finally {
                grafico2d.dispose();
            }
        }

        private Rectangle calcularRetanguloDoBalao(Shape alocacao,
                MensagemDimensoes dimensoes) {
            Rectangle area = alocacao.getBounds();
            int larguraInterna = Math.max(1,
                    area.width - getLeftInset() - getRightInset());
            int larguraBolha = Math.max(LARGURA_MINIMA,
                    Math.min(LARGURA_MAXIMA, dimensoes.largura()));
            larguraBolha = Math.min(larguraBolha, larguraInterna);
            int xInterno = area.x + getLeftInset();
            boolean alinhadoDireita = StyleConstants.getAlignment(getAttributes())
                    == StyleConstants.ALIGN_RIGHT;
            int x = alinhadoDireita
                    ? xInterno + larguraInterna - larguraBolha
                    : xInterno;
            int alturaInterna = Math.max(1,
                    area.height - getTopInset() - getBottomInset());
            return new Rectangle(x, area.y + getTopInset(), larguraBolha,
                    alturaInterna);
        }

        private static void desenharLinha(Graphics2D grafico, String texto, Font fonte,
                FontMetrics metricas, int x, int largura, int margem, int linhaBase,
                boolean alinhadoDireita) {
            grafico.setFont(fonte);
            int larguraTexto = metricas.stringWidth(texto);
            int inicio = alinhadoDireita
                    ? x + largura - margem - larguraTexto
                    : x + margem;
            grafico.drawString(texto, inicio, linhaBase);
        }

        private boolean paragrafoSemConteudo() {
            try {
                String texto = getDocument().getText(getStartOffset(),
                        getEndOffset() - getStartOffset());
                return texto.replace("\r", "").replace("\n", "").isEmpty();
            } catch (BadLocationException exception) {
                return true;
            }
        }
    }

    private static final class IndicadorStatusIcon implements javax.swing.Icon {

        private static final int TAMANHO = 10;
        private Color cor;

        private IndicadorStatusIcon(Color cor) {
            this.cor = cor;
        }

        private void setCor(Color cor) {
            this.cor = cor;
        }

        @Override
        public int getIconWidth() {
            return TAMANHO;
        }

        @Override
        public int getIconHeight() {
            return TAMANHO;
        }

        @Override
        public void paintIcon(Component componente, Graphics grafico,
                int x, int y) {
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                grafico2d.setColor(cor);
                grafico2d.fillOval(x, y, TAMANHO, TAMANHO);
            } finally {
                grafico2d.dispose();
            }
        }
    }
}
