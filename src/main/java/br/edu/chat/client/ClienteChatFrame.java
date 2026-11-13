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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
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
    private static final Color GRAFITE_1 = new Color(0x14, 0x18, 0x22);
    private static final Color GRAFITE_2 = new Color(0x1B, 0x21, 0x2D);
    private static final Color GRAFITE_3 = new Color(0x24, 0x2B, 0x39);
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
    private final JTextField campoMensagem = new CampoMensagem("Mensagem em #geral");
    private final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JButton botaoDesconectar = new JButton("Desconectar");
    private final JLabel rotuloEstado = new JLabel("Desconectado");
    private final JLabel rotuloPerfilNome = new JLabel("Visitante");
    private final JLabel rotuloPerfilLogin = new JLabel("Nao autenticado");
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
        construirComponentesRedeSocial();
        configurarAcoes();
        atualizarEstadoControles();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setResizable(true);
        setMinimumSize(new Dimension(900, 600));
        getContentPane().setBackground(FUNDO_AZUL_MARINHO);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evento) {
                fecharJanela();
            }
        });
    }

    /**
     * Reorganiza somente a apresentacao do cartao de chat. O cartao de login
     * continua sendo construido pela implementacao existente, assim como os
     * campos e os listeners usados pelo fluxo de autenticacao.
     */
    private void construirComponentesRedeSocial() {
        construirComponentes();
        Component painelLogin = painelCartoes.getComponent(0);

        JPanel barraComunidades = new JPanel();
        barraComunidades.setLayout(new javax.swing.BoxLayout(
                barraComunidades, javax.swing.BoxLayout.Y_AXIS));
        barraComunidades.setBackground(GRAFITE_3);
        barraComunidades.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        barraComunidades.add(criarBotaoComunidade("CM"));
        barraComunidades.add(javax.swing.Box.createVerticalStrut(10));
        barraComunidades.add(criarBotaoComunidade("+"));
        barraComunidades.add(javax.swing.Box.createVerticalGlue());
        barraComunidades.add(criarBotaoComunidade("?"));

        JPanel painelCanais = new JPanel();
        painelCanais.setLayout(new javax.swing.BoxLayout(
                painelCanais, javax.swing.BoxLayout.Y_AXIS));
        painelCanais.setBackground(GRAFITE_2);
        painelCanais.setBorder(BorderFactory.createEmptyBorder(18, 14, 14, 14));
        painelCanais.setPreferredSize(new Dimension(190, 0));
        JLabel nomeComunidade = criarRotulo("Comunidade local", 14, true, TEXTO_CLARO);
        nomeComunidade.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelCanais.add(nomeComunidade);
        JLabel avisoDemo = criarRotulo("Navegacao demonstrativa", 10, false, TEXTO_SECUNDARIO);
        avisoDemo.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelCanais.add(javax.swing.Box.createVerticalStrut(4));
        painelCanais.add(avisoDemo);
        painelCanais.add(javax.swing.Box.createVerticalStrut(22));
        painelCanais.add(criarTituloSecao("CANAIS DE TEXTO"));
        painelCanais.add(criarItemCanal("#  geral", true));
        painelCanais.add(criarItemCanal("#  ideias  (demo)", false));
        painelCanais.add(criarItemCanal("#  avisos  (demo)", false));
        painelCanais.add(javax.swing.Box.createVerticalGlue());
        painelCanais.add(criarBlocoPerfilCompacto());

        JLabel tituloCanal = criarRotulo("# geral", 17, true, TEXTO_CLARO);
        JLabel subtituloCanal = criarRotulo(
                "Chat global conectado • outros canais sao demonstrativos",
                11, false, TEXTO_SECUNDARIO);
        JPanel identificacaoCanal = new JPanel();
        identificacaoCanal.setLayout(new javax.swing.BoxLayout(
                identificacaoCanal, javax.swing.BoxLayout.Y_AXIS));
        identificacaoCanal.setOpaque(false);
        identificacaoCanal.add(tituloCanal);
        identificacaoCanal.add(javax.swing.Box.createVerticalStrut(3));
        identificacaoCanal.add(subtituloCanal);

        JPanel painelStatusChat = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        painelStatusChat.setOpaque(false);
        estilizarBotao(botaoDesconectar);
        indicadorEstado.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        rotuloEstado.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 12f));
        rotuloEstado.setForeground(TEXTO_CLARO);
        painelStatusChat.add(indicadorEstado);
        painelStatusChat.add(rotuloEstado);
        painelStatusChat.add(botaoDesconectar);

        JPanel painelCabecalhoChat = new JPanel(new BorderLayout(12, 0));
        painelCabecalhoChat.setBackground(GRAFITE_2);
        painelCabecalhoChat.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDA),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        painelCabecalhoChat.add(identificacaoCanal, BorderLayout.CENTER);
        painelCabecalhoChat.add(painelStatusChat, BorderLayout.EAST);

        estilizarCampo(campoMensagem);
        campoMensagem.setBorder(new BordaArredondada(BORDA, 18,
                BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        campoMensagem.setToolTipText("Mensagem em #geral");
        areaHistorico.setEditorKit(new MensagemEditorKit());
        areaHistorico.setEditable(false);
        areaHistorico.setFont(FONTE_PADRAO);
        areaHistorico.setForeground(TEXTO_CLARO);
        areaHistorico.setBackground(GRAFITE_1);
        areaHistorico.setCaretColor(TEXTO_CLARO);
        areaHistorico.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JScrollPane painelHistorico = new JScrollPane(areaHistorico);
        painelHistorico.setBorder(null);
        painelHistorico.getViewport().setBackground(GRAFITE_1);
        JScrollBar barraHistorico = painelHistorico.getVerticalScrollBar();
        barraHistorico.setUI(new TemaScrollBarUI());
        barraHistorico.setBackground(GRAFITE_2);
        barraHistorico.setUnitIncrement(16);
        barraHistorico.setBlockIncrement(96);

        JPanel painelEnvio = new JPanel(new BorderLayout(8, 4));
        painelEnvio.setBackground(GRAFITE_1);
        painelEnvio.setBorder(BorderFactory.createEmptyBorder(10, 16, 16, 16));
        estilizarBotao(botaoEnviar);
        painelEnvio.add(campoMensagem, BorderLayout.CENTER);
        painelEnvio.add(botaoEnviar, BorderLayout.EAST);

        JPanel painelChat = new JPanel(new BorderLayout());
        painelChat.setBackground(GRAFITE_1);
        painelChat.add(painelCabecalhoChat, BorderLayout.NORTH);
        painelChat.add(painelHistorico, BorderLayout.CENTER);
        painelChat.add(painelEnvio, BorderLayout.SOUTH);

        JPanel painelPerfil = criarPainelMembros();
        JPanel centro = new JPanel(new BorderLayout(1, 0));
        centro.setBackground(BORDA);
        centro.add(painelCanais, BorderLayout.WEST);
        centro.add(painelChat, BorderLayout.CENTER);

        JPanel estruturaChat = new JPanel(new BorderLayout(1, 0));
        estruturaChat.setBackground(BORDA);
        estruturaChat.add(barraComunidades, BorderLayout.WEST);
        estruturaChat.add(centro, BorderLayout.CENTER);
        estruturaChat.add(painelPerfil, BorderLayout.EAST);

        painelCartoes.removeAll();
        painelCartoes.add(painelLogin, CARTAO_LOGIN);
        painelCartoes.add(estruturaChat, CARTAO_CHAT);
        layoutCartoes.show(painelCartoes, CARTAO_LOGIN);
        revalidate();
        repaint();
        pack();
        setLocationByPlatform(true);
    }

    private JPanel criarPainelPerfil() {
        JPanel painel = new JPanel();
        painel.setLayout(new javax.swing.BoxLayout(painel, javax.swing.BoxLayout.Y_AXIS));
        painel.setBackground(GRAFITE_2);
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDA),
                BorderFactory.createEmptyBorder(18, 16, 16, 16)));
        painel.setPreferredSize(new Dimension(190, 0));

        JLabel titulo = criarRotulo("SEU PERFIL", 11, true, TEXTO_SECUNDARIO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(titulo);
        painel.add(javax.swing.Box.createVerticalStrut(18));
        JLabel avatar = criarRotulo("●", 42, true, AZUL_PRINCIPAL);
        avatar.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(avatar);
        painel.add(javax.swing.Box.createVerticalStrut(10));
        rotuloPerfilNome.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 15f));
        rotuloPerfilNome.setForeground(TEXTO_CLARO);
        rotuloPerfilNome.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(rotuloPerfilNome);
        rotuloPerfilLogin.setFont(FONTE_PADRAO.deriveFont(Font.PLAIN, 11f));
        rotuloPerfilLogin.setForeground(TEXTO_SECUNDARIO);
        rotuloPerfilLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(javax.swing.Box.createVerticalStrut(3));
        painel.add(rotuloPerfilLogin);
        painel.add(javax.swing.Box.createVerticalStrut(18));
        JLabel status = criarRotulo("●  identidade autenticada", 11, false, STATUS_CONECTADO);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(status);
        painel.add(javax.swing.Box.createVerticalGlue());
        JLabel nota = criarRotulo("Comunidades e canais reais serao adicionados em uma etapa futura.",
                10, false, TEXTO_SECUNDARIO);
        nota.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(nota);
        return painel;
    }

    private JPanel criarBlocoPerfilCompacto() {
        JPanel painel = new JPanel(new BorderLayout(8, 0));
        painel.setBackground(GRAFITE_3);
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDA),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));
        JLabel avatar = criarRotulo("●", 20, true, AZUL_PRINCIPAL);
        painel.add(avatar, BorderLayout.WEST);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new javax.swing.BoxLayout(textos, javax.swing.BoxLayout.Y_AXIS));
        rotuloPerfilNome.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 12f));
        rotuloPerfilNome.setForeground(TEXTO_CLARO);
        rotuloPerfilLogin.setFont(FONTE_PADRAO.deriveFont(Font.PLAIN, 10f));
        rotuloPerfilLogin.setForeground(TEXTO_SECUNDARIO);
        textos.add(rotuloPerfilNome);
        textos.add(rotuloPerfilLogin);
        painel.add(textos, BorderLayout.CENTER);
        painel.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        return painel;
    }

    private JPanel criarPainelMembros() {
        JPanel painel = new JPanel();
        painel.setLayout(new javax.swing.BoxLayout(painel, javax.swing.BoxLayout.Y_AXIS));
        painel.setBackground(GRAFITE_2);
        painel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDA),
                BorderFactory.createEmptyBorder(18, 14, 14, 14)));
        painel.setPreferredSize(new Dimension(190, 0));

        JLabel titulo = criarRotulo("MEMBROS", 11, true, TEXTO_SECUNDARIO);
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(titulo);
        painel.add(javax.swing.Box.createVerticalStrut(10));
        painel.add(criarMembroDemonstrativo("●", "Voce", "online", STATUS_CONECTADO));
        painel.add(criarMembroDemonstrativo("●", "Ana", "online", STATUS_CONECTADO));
        painel.add(criarMembroDemonstrativo("●", "Bia", "demonstracao", STATUS_EM_ANDAMENTO));
        painel.add(criarMembroDemonstrativo("●", "Equipe Azul", "offline", TEXTO_SECUNDARIO));
        painel.add(javax.swing.Box.createVerticalStrut(10));
        JLabel nota = criarRotulo("Membros demonstrativos", 10, false, TEXTO_SECUNDARIO);
        nota.setAlignmentX(Component.LEFT_ALIGNMENT);
        painel.add(nota);
        return painel;
    }

    private static JPanel criarMembroDemonstrativo(String marcador, String nome,
            String estado, Color corEstado) {
        JPanel linha = new JPanel(new BorderLayout(8, 0));
        linha.setOpaque(false);
        linha.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        JLabel avatar = criarRotulo(marcador, 18, true, corEstado);
        linha.add(avatar, BorderLayout.WEST);
        JPanel textos = new JPanel();
        textos.setOpaque(false);
        textos.setLayout(new javax.swing.BoxLayout(textos, javax.swing.BoxLayout.Y_AXIS));
        textos.add(criarRotulo(nome, 11, true, TEXTO_CLARO));
        textos.add(criarRotulo(estado, 9, false, TEXTO_SECUNDARIO));
        linha.add(textos, BorderLayout.CENTER);
        linha.setAlignmentX(Component.LEFT_ALIGNMENT);
        return linha;
    }

    private static JButton criarBotaoComunidade(String texto) {
        JButton botao = new BotaoComunidade(texto, texto.equals("CM"));
        botao.setAlignmentX(Component.CENTER_ALIGNMENT);
        botao.setPreferredSize(new Dimension(48, 48));
        botao.setMaximumSize(new Dimension(48, 48));
        botao.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 13f));
        botao.setForeground(TEXTO_CLARO);
        botao.setBackground(texto.equals("CM") ? AZUL_PRINCIPAL : GRAFITE_2);
        botao.setFocusPainted(false);
        botao.setBorder(new BordaCircular(BORDA, texto.equals("CM")));
        return botao;
    }

    private static JLabel criarTituloSecao(String texto) {
        JLabel label = criarRotulo(texto, 10, true, TEXTO_SECUNDARIO);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel criarItemCanal(String texto, boolean ativo) {
        JLabel label = criarRotulo(texto, 13, ativo, ativo ? TEXTO_CLARO : TEXTO_SECUNDARIO);
        label.setOpaque(true);
        label.setBackground(ativo ? AZUL_PRINCIPAL.darker() : GRAFITE_2);
        label.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return label;
    }

    private static JLabel criarRotulo(String texto, int tamanho, boolean negrito, Color cor) {
        JLabel label = new JLabel(texto);
        label.setFont(FONTE_PADRAO.deriveFont(negrito ? Font.BOLD : Font.PLAIN, (float) tamanho));
        label.setForeground(cor);
        return label;
    }

    private static final class CampoMensagem extends JTextField {

        private final String placeholder;

        private CampoMensagem(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics grafico) {
            super.paintComponent(grafico);
            if (!isFocusOwner() && getText().isEmpty()) {
                Graphics2D grafico2d = (Graphics2D) grafico.create();
                try {
                    grafico2d.setColor(TEXTO_SECUNDARIO);
                    grafico2d.setFont(getFont());
                    Insets margens = getInsets();
                    grafico2d.drawString(placeholder, margens.left,
                            (getHeight() - grafico2d.getFontMetrics().getHeight()) / 2
                                    + grafico2d.getFontMetrics().getAscent());
                } finally {
                    grafico2d.dispose();
                }
            }
        }
    }

    private static final class BordaArredondada extends javax.swing.border.AbstractBorder {

        private final Color cor;
        private final int raio;
        private final Border margens;

        private BordaArredondada(Color cor, int raio, Border margens) {
            this.cor = cor;
            this.raio = raio;
            this.margens = margens;
        }

        @Override
        public Insets getBorderInsets(Component componente) {
            Insets insets = margens.getBorderInsets(componente);
            return new Insets(insets.top, insets.left, insets.bottom, insets.right);
        }

        @Override
        public void paintBorder(Component componente, Graphics grafico, int x, int y,
                int largura, int altura) {
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                grafico2d.setColor(cor);
                grafico2d.drawRoundRect(x, y, largura - 1, altura - 1, raio, raio);
            } finally {
                grafico2d.dispose();
            }
        }
    }

    private static final class BotaoComunidade extends JButton {

        private final boolean selecionado;

        private BotaoComunidade(String texto, boolean selecionado) {
            super(texto);
            this.selecionado = selecionado;
            setContentAreaFilled(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics grafico) {
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                grafico2d.setColor(getBackground());
                grafico2d.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                super.paintComponent(grafico2d);
            } finally {
                grafico2d.dispose();
            }
        }
    }

    private static final class BordaCircular extends javax.swing.border.AbstractBorder {

        private final Color cor;
        private final boolean selecionado;

        private BordaCircular(Color cor, boolean selecionado) {
            this.cor = cor;
            this.selecionado = selecionado;
        }

        @Override
        public Insets getBorderInsets(Component componente) {
            return new Insets(2, 2, 2, 2);
        }

        @Override
        public void paintBorder(Component componente, Graphics grafico, int x, int y,
                int largura, int altura) {
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                grafico2d.setColor(selecionado ? TEXTO_CLARO : cor);
                grafico2d.setStroke(new java.awt.BasicStroke(selecionado ? 3f : 1f));
                grafico2d.drawOval(x + 1, y + 1, largura - 3, altura - 3);
            } finally {
                grafico2d.dispose();
            }
        }
    }

    private static final class TemaScrollBarUI extends BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            thumbColor = GRAFITE_3;
            thumbHighlightColor = GRAFITE_3.brighter();
            thumbDarkShadowColor = GRAFITE_1;
            trackColor = GRAFITE_1;
            trackHighlightColor = GRAFITE_1;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return criarBotaoVazio();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return criarBotaoVazio();
        }

        private static JButton criarBotaoVazio() {
            JButton botao = new JButton();
            botao.setPreferredSize(new Dimension(0, 0));
            botao.setMinimumSize(new Dimension(0, 0));
            botao.setMaximumSize(new Dimension(0, 0));
            botao.setBorder(null);
            botao.setOpaque(false);
            return botao;
        }
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
        painelTituloLogin.setOpaque(false);
        painelTituloLogin.add(tituloLogin);
        painelTituloLogin.add(javax.swing.Box.createVerticalStrut(4));
        painelTituloLogin.add(subtituloLogin);

        JPanel painelAutenticacao = new JPanel();
        painelAutenticacao.setLayout(new javax.swing.BoxLayout(
                painelAutenticacao, javax.swing.BoxLayout.Y_AXIS));
        painelAutenticacao.setBackground(GRAFITE_2);
        adicionarCampoVertical(painelAutenticacao, "Login:", campoLogin);
        adicionarCampoVertical(painelAutenticacao, "Nome:", campoNome);
        adicionarCampoVertical(painelAutenticacao, "Senha:", campoSenha);
        modoCadastro.setFont(FONTE_PADRAO);
        modoCadastro.setForeground(TEXTO_CLARO);
        modoCadastro.setBackground(GRAFITE_2);
        modoCadastro.setOpaque(false);
        modoCadastro.setFocusPainted(false);
        modoCadastro.setBorder(new BordaArredondada(BORDA, 12,
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        modoCadastro.setBorderPainted(true);
        modoCadastro.setPreferredSize(new Dimension(132, 32));
        modoCadastro.setMaximumSize(new Dimension(132, 32));
        modoCadastro.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelAutenticacao.add(modoCadastro);

        JPanel painelCamposConexao = new JPanel();
        painelCamposConexao.setLayout(new javax.swing.BoxLayout(
                painelCamposConexao, javax.swing.BoxLayout.Y_AXIS));
        painelCamposConexao.setBackground(GRAFITE_3);
        painelCamposConexao.setBorder(new BordaArredondada(BORDA, 14,
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        adicionarCampoVertical(painelCamposConexao, "Host:", campoHost);
        adicionarCampoVertical(painelCamposConexao, "Porta:", campoPorta);
        painelCamposConexao.setVisible(false);

        JToggleButton botaoConfiguracoes = new JToggleButton(
                "Configurações de conexão  ▸");
        botaoConfiguracoes.setFont(FONTE_PADRAO.deriveFont(Font.BOLD, 12f));
        botaoConfiguracoes.setForeground(TEXTO_SECUNDARIO);
        botaoConfiguracoes.setBackground(GRAFITE_2);
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
        painelBotoesLogin.setBackground(GRAFITE_2);
        painelBotoesLogin.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        estilizarBotao(botaoConectar);
        botaoConectar.setPreferredSize(new Dimension(180, 40));
        botaoConectar.setMinimumSize(new Dimension(180, 40));
        painelBotoesLogin.add(botaoConectar);

        JPanel painelLogin = new JPanel(new BorderLayout(0, 8));
        painelLogin.setBackground(FUNDO_AZUL_MARINHO);
        painelLogin.setBorder(null);
        painelLogin.setPreferredSize(new Dimension(520, 480));

        JPanel painelFormulario = new JPanel();
        painelFormulario.setLayout(new javax.swing.BoxLayout(
                painelFormulario, javax.swing.BoxLayout.Y_AXIS));
        painelFormulario.setBackground(GRAFITE_2);
        painelFormulario.setBorder(new BordaArredondada(BORDA, 22,
                BorderFactory.createEmptyBorder(26, 32, 26, 32)));
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
         grupo.setBackground(GRAFITE_2);
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
        campo.setBackground(GRAFITE_3);
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
            botao.setBorder(new BordaArredondada(AZUL_PRINCIPAL.darker(), 12,
                    BorderFactory.createEmptyBorder(7, 13, 7, 13)));
        } else {
            botao.setBackground(FUNDO_DESABILITADO);
            botao.setForeground(TEXTO_SECUNDARIO);
            botao.setBorder(new BordaArredondada(BORDA, 12,
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
                        rotuloPerfilNome.setText(nomeCanonico);
                        rotuloPerfilLogin.setText("@" + campoLogin.getText().trim());
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
                rotuloPerfilNome.setText("Visitante");
                rotuloPerfilLogin.setText("Nao autenticado");
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
            rotuloPerfilNome.setText("Visitante");
            rotuloPerfilLogin.setText("Nao autenticado");
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
        // Mantem os metadados legados para compatibilidade; a pintura nova nao usa baloes.
        StyleConstants.setBackground(atributos,
                propria ? AZUL_PRINCIPAL : AZUL_SECUNDARIO);
        StyleConstants.setSpaceAbove(atributos, 7);
        StyleConstants.setSpaceBelow(atributos, 7);
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

        private static final int MARGEM_HORIZONTAL = 58;
        private static final int MARGEM_VERTICAL = 18;
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
                    (String) atributos.getAttribute(ATRIBUTO_REMETENTE),
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

    private record DadosMensagem(String remetente, String cabecalho, String conteudo, boolean propria,
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
        private static final int MARGEM_HORIZONTAL = 58;
        private static final int RAIO = 16;
        private static final int TAMANHO_AVATAR = 30;

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
            if (conteudo == null) {
                return;
            }
            pintarMensagemDiscord(grafico, alocacao, conteudo);
        }

        private void pintarMensagemDiscord(Graphics grafico, Shape alocacao,
                MensagemConteudoView conteudo) {
            DadosMensagem dados = conteudo.obterDadosMensagem();
            MensagemDimensoes dimensoes = conteudo.obterDimensoesDoFluxo();
            List<Element> paragrafos = conteudo.paragrafosDaMensagem(dados.idMensagem());
            int indiceParagrafo = Math.max(0, paragrafos.indexOf(getElement()));
            List<List<String>> linhasPorParagrafo = dimensoes.linhas().conteudoPorParagrafo();
            List<String> linhas = indiceParagrafo < linhasPorParagrafo.size()
                    ? linhasPorParagrafo.get(indiceParagrafo) : List.of(dados.conteudo());
            Rectangle area = alocacao.getBounds();
            int xTexto = area.x + getLeftInset() + TAMANHO_AVATAR + 12;
            int yTopo = area.y + getTopInset();
            Graphics2D grafico2d = (Graphics2D) grafico.create();
            try {
                grafico2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Font fonteConteudo = conteudo.getFont();
                Font fonteNome = fonteConteudo.deriveFont(Font.BOLD, 13f);
                Font fonteHorario = fonteConteudo.deriveFont(Font.PLAIN, 11f);
                FontMetrics metricasConteudo = grafico2d.getFontMetrics(fonteConteudo);
                FontMetrics metricasNome = grafico2d.getFontMetrics(fonteNome);

                if (indiceParagrafo == 0) {
                    int avatarX = area.x + getLeftInset();
                    int avatarY = yTopo + 2;
                    grafico2d.setColor(dados.propria() ? AZUL_PRINCIPAL : AZUL_SECUNDARIO);
                    grafico2d.fillOval(avatarX, avatarY, TAMANHO_AVATAR, TAMANHO_AVATAR);
                    grafico2d.setColor(TEXTO_CLARO);
                    String inicial = dados.remetente() == null || dados.remetente().isBlank()
                            ? "?" : dados.remetente().substring(0, 1).toUpperCase();
                    grafico2d.setFont(fonteNome);
                    FontMetrics metricasAvatar = grafico2d.getFontMetrics();
                    grafico2d.drawString(inicial,
                            avatarX + (TAMANHO_AVATAR - metricasAvatar.stringWidth(inicial)) / 2,
                            avatarY + (TAMANHO_AVATAR + metricasAvatar.getAscent()
                                    - metricasAvatar.getDescent()) / 2);

                    String cabecalho = dados.cabecalho() == null ? "" : dados.cabecalho();
                    int fimHorario = cabecalho.indexOf(']');
                    String horario = fimHorario >= 0 ? cabecalho.substring(1, fimHorario) : "";
                    String nome = dados.remetente() == null ? "Usuario" : dados.remetente();
                    grafico2d.setColor(TEXTO_CLARO);
                    grafico2d.setFont(fonteNome);
                    grafico2d.drawString(nome, xTexto, yTopo + metricasNome.getAscent());
                    int xHorario = xTexto + metricasNome.stringWidth(nome) + 8;
                    grafico2d.setColor(TEXTO_SECUNDARIO);
                    grafico2d.setFont(fonteHorario);
                    grafico2d.drawString(horario, xHorario,
                            yTopo + metricasNome.getAscent());
                    yTopo += dimensoes.alturaCabecalho();
                }

                grafico2d.setColor(TEXTO_CLARO);
                grafico2d.setFont(fonteConteudo);
                int linhaBase = yTopo + metricasConteudo.getAscent();
                for (String linha : linhas) {
                    grafico2d.drawString(linha, xTexto, linhaBase);
                    linhaBase += metricasConteudo.getHeight();
                }
            } finally {
                grafico2d.dispose();
            }
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
