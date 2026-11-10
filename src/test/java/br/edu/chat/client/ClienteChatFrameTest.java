package br.edu.chat.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.text.JTextComponent;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import br.edu.chat.model.Mensagem;
import br.edu.chat.config.ConfiguracaoTls;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioCredencialRepository;
import br.edu.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.edu.chat.persistence.jdbc.JdbcCadastroUsuario;
import br.edu.chat.server.Servidor;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.SenhaHasher;

class ClienteChatFrameTest {

    private static final String SENHA_TLS = "teste-integracao-tls";

    private Thread.UncaughtExceptionHandler handlerAnterior;
    private final AtomicReference<Throwable> excecaoEdt = new AtomicReference<>();

    @BeforeEach
    void instalarVerificadorDaEdt() {
        handlerAnterior = Thread.getDefaultUncaughtExceptionHandler();
        excecaoEdt.set(null);
        Thread.setDefaultUncaughtExceptionHandler((thread, excecao) -> {
            if (thread.getName().startsWith("AWT-EventQueue-")) {
                excecaoEdt.compareAndSet(null, excecao);
            }
            if (handlerAnterior != null) {
                handlerAnterior.uncaughtException(thread, excecao);
            }
        });
    }

    @AfterEach
    void restaurarVerificadorDaEdt() {
        Thread.setDefaultUncaughtExceptionHandler(handlerAnterior);
        assertNull(excecaoEdt.get(),
                "Exceção não capturada na EDT durante o teste: " + excecaoEdt.get());
    }

    @Test
    void deveAssociarMetadadosAosTrechosIndividuaisSemAlterarTextoOuParagrafos()
            throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem propria = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "Olá | parte: dois", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem recebida = new Mensagem(new br.edu.chat.model.Usuario("Equipe: Azul"),
                "recebida: acentuação | preservada",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, propria, true);
            adicionarMensagem(frame, recebida, false);

            AtomicReference<ResultadoMetadados> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                StyledDocument documento = area.getStyledDocument();
                String textoEsperado = ClienteChatApresentacao.formatar(propria)
                        + "\r\r\n"
                        + ClienteChatApresentacao.formatar(recebida)
                        + "\r\r\n";
                int inicioPropria = area.getText().indexOf("Olá | parte: dois");
                int inicioRecebida = area.getText().indexOf("recebida: acentuação | preservada");
                resultado.set(new ResultadoMetadados(
                        area.getText(), textoEsperado,
                        atributos(documento, inicioPropria),
                        atributos(documento, inicioRecebida),
                        documento.getDefaultRootElement().getElementCount()));
            });

            assertEquals(resultado.get().textoEsperado(), resultado.get().textoAtual());
            assertEquals(1, contar(resultado.get().textoAtual(), "Olá | parte: dois"));
            assertEquals(1, contar(resultado.get().textoAtual(),
                    "recebida: acentuação | preservada"));
            assertEquals("Ana", atributo(resultado.get().propria(), "ATRIBUTO_REMETENTE"));
            assertEquals("[09:35] Ana", atributo(resultado.get().propria(), "ATRIBUTO_CABECALHO"));
            assertEquals("Olá | parte: dois", atributo(resultado.get().propria(), "ATRIBUTO_CONTEUDO"));
            assertEquals(Boolean.TRUE, atributo(resultado.get().propria(), "ATRIBUTO_MENSAGEM_PROPRIA"));
            assertEquals("Equipe: Azul", atributo(resultado.get().recebida(), "ATRIBUTO_REMETENTE"));
            assertEquals("recebida: acentuação | preservada",
                    atributo(resultado.get().recebida(), "ATRIBUTO_CONTEUDO"));
            assertEquals(Boolean.FALSE, atributo(resultado.get().recebida(), "ATRIBUTO_MENSAGEM_PROPRIA"));
            assertEquals(3, resultado.get().quantidadeParagrafos());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveDisponibilizarMetadadosNaMensagemConteudoViewAposAlterarNome()
            throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem propria = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem própria", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem recebida = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "mensagem recebida", java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, propria, true);
            adicionarMensagem(frame, recebida, false);
            executarNaEdt(() -> campo(frame, "campoNome", JTextField.class).setText("Carlos"));

            AtomicReference<ResultadoViews> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                StyledDocument documento = area.getStyledDocument();
                int inicioPropria = area.getText().indexOf("mensagem própria");
                int inicioRecebida = area.getText().indexOf("mensagem recebida");
                View raiz = area.getUI().getRootView(area);
                resultado.set(new ResultadoViews(
                        encontrarView(raiz, inicioPropria),
                        encontrarView(raiz, inicioRecebida),
                        area.getText(),
                        documento.getDefaultRootElement().getElementCount()));
            });

            assertNotNull(resultado.get().propria());
            assertNotNull(resultado.get().recebida());
            assertEquals("MensagemConteudoView",
                    resultado.get().propria().getClass().getSimpleName());
            assertEquals("MensagemConteudoView",
                    resultado.get().recebida().getClass().getSimpleName());
            assertEquals("Ana", atributo(resultado.get().propria().getElement().getAttributes(),
                    "ATRIBUTO_REMETENTE"));
            assertEquals("[09:35] Ana", atributo(
                    resultado.get().propria().getElement().getAttributes(), "ATRIBUTO_CABECALHO"));
            assertEquals("mensagem própria", atributo(
                    resultado.get().propria().getElement().getAttributes(), "ATRIBUTO_CONTEUDO"));
            assertEquals(Boolean.TRUE, atributo(
                    resultado.get().propria().getElement().getAttributes(),
                    "ATRIBUTO_MENSAGEM_PROPRIA"));
            assertEquals("Bia", atributo(resultado.get().recebida().getElement().getAttributes(),
                    "ATRIBUTO_REMETENTE"));
            assertEquals("mensagem recebida", atributo(
                    resultado.get().recebida().getElement().getAttributes(), "ATRIBUTO_CONTEUDO"));
            assertEquals(Boolean.FALSE, atributo(
                    resultado.get().recebida().getElement().getAttributes(),
                    "ATRIBUTO_MENSAGEM_PROPRIA"));
            assertEquals(1, contar(resultado.get().texto(), "mensagem própria"));
            assertEquals(1, contar(resultado.get().texto(), "mensagem recebida"));
            assertEquals(3, resultado.get().quantidadeParagrafos());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveCriarModeloImutavelIndependenteParaCadaMensagem() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem propria = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "conteúdo próprio | detalhe: um",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem recebida = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "conteúdo recebido | detalhe: dois",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, propria, true);
            adicionarMensagem(frame, recebida, false);

            AtomicReference<ResultadoModelos> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                int inicioPropria = area.getText().indexOf("conteúdo próprio");
                int inicioRecebida = area.getText().indexOf("conteúdo recebido");
                View raiz = area.getUI().getRootView(area);
                try {
                    resultado.set(new ResultadoModelos(
                            obterDadosModelo(encontrarView(raiz, inicioPropria)),
                            obterDadosModelo(encontrarView(raiz, inicioRecebida)),
                            area.getText()));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertEquals("[09:35] Ana", modeloParte(resultado.get().propria(), "cabecalho"));
            assertEquals("conteúdo próprio | detalhe: um",
                    modeloParte(resultado.get().propria(), "conteudo"));
            assertEquals(Boolean.TRUE, modeloParte(resultado.get().propria(), "propria"));
            assertEquals("[09:36] Bia", modeloParte(resultado.get().recebida(), "cabecalho"));
            assertEquals("conteúdo recebido | detalhe: dois",
                    modeloParte(resultado.get().recebida(), "conteudo"));
            assertEquals(Boolean.FALSE, modeloParte(resultado.get().recebida(), "propria"));
            assertEquals(1, contar(resultado.get().texto(), "conteúdo próprio | detalhe: um"));
            assertEquals(1, contar(resultado.get().texto(), "conteúdo recebido | detalhe: dois"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveAtribuirIdentidadeUnicaAMensagensIdenticasEpreservarQuebras() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem primeira = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mesmo conteÃºdo\ncom quebra", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem segunda = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mesmo conteÃºdo\ncom quebra", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, primeira, true);
            adicionarMensagem(frame, segunda, true);

            AtomicReference<ResultadoIdentidades> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                try {
                    JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                StyledDocument documento = area.getStyledDocument();
                String texto = area.getText();
                int primeiroIndice = texto.indexOf("mesmo conteÃºdo");
                int segundoIndice = texto.indexOf("mesmo conteÃºdo", primeiroIndice + 1);
                Object primeiroId = atributo(documento, primeiroIndice, "ATRIBUTO_ID_MENSAGEM");
                Object primeiroIdQuebra = atributo(documento,
                        texto.indexOf("com quebra", primeiroIndice), "ATRIBUTO_ID_MENSAGEM");
                Object segundoId = atributo(documento, segundoIndice, "ATRIBUTO_ID_MENSAGEM");
                AttributeSet paragrafoPrimeiro = documento.getDefaultRootElement()
                        .getElement(0).getAttributes();
                AttributeSet paragrafoFinal = documento.getDefaultRootElement()
                        .getElement(documento.getDefaultRootElement().getElementCount() - 1)
                        .getAttributes();
                    resultado.set(new ResultadoIdentidades(texto, primeiroId, primeiroIdQuebra,
                            segundoId, paragrafoPrimeiro, paragrafoFinal,
                            documento.getDefaultRootElement().getElementCount()));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertEquals(ClienteChatApresentacao.formatar(primeira).replace("\n", "\r\n")
                    + "\r\r\n"
                    + ClienteChatApresentacao.formatar(segunda).replace("\n", "\r\n")
                    + "\r\r\n",
                    resultado.get().texto());
            assertNotNull(resultado.get().primeiroId());
            assertEquals(resultado.get().primeiroId(), resultado.get().primeiroIdQuebra());
            assertNotNull(resultado.get().segundoId());
            assertNotEquals(resultado.get().primeiroId(), resultado.get().segundoId());
            assertNotNull(resultado.get().atributosParagrafo().getAttribute(
                    chaveAtributo("ATRIBUTO_ID_MENSAGEM")));
            assertEquals(5, resultado.get().quantidadeParagrafos());
            assertNull(resultado.get().atributosParagrafoFinal().getAttribute(
                    chaveAtributo("ATRIBUTO_ID_MENSAGEM")));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveDistribuirAlturaDaMensagemEntreParagrafosDoMesmoId() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "primeira linha\n\nterceira linha com conteúdo longo",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<ResultadoGeometriaCompartilhada> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                try {
                    JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                    StyledDocument documento = area.getStyledDocument();
                    int inicio = area.getText().indexOf("primeira linha");
                    Object id = atributo(documento, inicio, "ATRIBUTO_ID_MENSAGEM");
                    List<View> views = encontrarViewsComId(area.getUI().getRootView(area), id);
                    List<Float> alturasEstreitas = medirAlturas(views, 180);
                    Object dimensoesEstreitas = obterDimensoes(views.get(0));
                    List<Float> alturasAmplas = medirAlturas(views, 420);
                    Object dimensoesAmplas = obterDimensoes(views.get(0));
                    resultado.set(new ResultadoGeometriaCompartilhada(
                            views.size(), soma(alturasEstreitas), soma(alturasAmplas),
                            dimensao(dimensoesEstreitas, "altura"),
                            dimensao(dimensoesAmplas, "altura"), area.getText(),
                            documento.getDefaultRootElement().getElementCount()));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertTrue(resultado.get().quantidadeViews() >= 3);
            assertEquals(resultado.get().alturaTotalEstreita(),
                    resultado.get().somaAlturasEstreitas());
            assertEquals(resultado.get().alturaTotalAmpla(),
                    resultado.get().somaAlturasAmplas());
            assertTrue(resultado.get().somaAlturasEstreitas()
                    >= resultado.get().somaAlturasAmplas());
            assertEquals(ClienteChatApresentacao.formatar(mensagem).replace("\n", "\r\n")
                    + "\r\r\n", resultado.get().texto());
            assertEquals(4, resultado.get().quantidadeParagrafos());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveCalcularDimensoesDoCabecalhoEConteudoComLargurasVariaveis()
            throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "Mensagem longa com acentuação | detalhe: "
                        + "texto repetido para testar quebra automática e espaços",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<ResultadoDimensoes> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                int inicio = area.getText().indexOf("Mensagem longa");
                View view = encontrarView(area.getUI().getRootView(area), inicio);
                try {
                    resultado.set(new ResultadoDimensoes(
                            calcularDimensoes(view, 700), calcularDimensoes(view, 260),
                            area.getText()));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            Object largo = resultado.get().largo();
            Object estreito = resultado.get().estreito();
            assertTrue(dimensao(largo, "largura") > 0);
            assertTrue(dimensao(largo, "altura") > 0);
            assertTrue(dimensao(largo, "alturaCabecalho") > 0);
            assertTrue(dimensao(largo, "alturaConteudo") > 0);
            assertTrue(dimensao(estreito, "linhasConteudo")
                    >= dimensao(largo, "linhasConteudo"));
            assertEquals(1, contar(resultado.get().texto(), "Mensagem longa"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveIntegrarDimensoesComLarguraEfetivaDoFluxo() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem curta = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "curta | teste: espaço",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem longa = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "longa com acentuação | teste: conteúdo "
                        + "repetido para ocupar várias linhas no fluxo",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, curta, true);
            adicionarMensagem(frame, longa, false);

            AtomicReference<ResultadoFluxo> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View raiz = area.getUI().getRootView(area);
                View viewCurta = encontrarView(raiz, area.getText().indexOf("curta | teste"));
                View viewLonga = encontrarView(raiz, area.getText().indexOf("longa com"));
                try {
                    resultado.set(new ResultadoFluxo(
                            dimensoesDoFluxo(viewCurta), dimensoesDoFluxo(viewLonga),
                            area.getText()));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertTrue(dimensao(resultado.get().curta(), "largura") > 0);
            assertTrue(dimensao(resultado.get().curta(), "altura") > 0);
            assertTrue(dimensao(resultado.get().longa(), "largura") > 0);
            assertTrue(dimensao(resultado.get().longa(), "largura") <= 520);
            assertTrue(dimensao(resultado.get().longa(), "linhasConteudo") >= 1);
            assertEquals(1, contar(resultado.get().texto(), "curta | teste: espaço"));
            assertEquals(1, contar(resultado.get().texto(), "longa com acentuação"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveReutilizarDimensoesQuandoLarguraForAmesma() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem para testar cache",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                View view = encontrarView(campo(frame, "areaHistorico", JTextPane.class)
                        .getUI().getRootView(campo(frame, "areaHistorico", JTextPane.class)),
                        campo(frame, "areaHistorico", JTextPane.class).getText()
                                .indexOf("mensagem para testar cache"));
                try {
                    atualizarDimensoes(view, 280);
                    Object primeira = obterDimensoes(view);
                    atualizarDimensoes(view, 280);
                    Object segunda = obterDimensoes(view);
                    resultado.set(new Object[] { primeira, segunda });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertNotNull(resultado.get()[0]);
            assertSame(resultado.get()[0], resultado.get()[1]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveCalcularNovasDimensoesQuandoLarguraMudar() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem para testar largura diferente",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("mensagem para testar largura diferente"));
                try {
                    atualizarDimensoes(view, 280);
                    Object primeira = obterDimensoes(view);
                    atualizarDimensoes(view, 160);
                    Object segunda = obterDimensoes(view);
                    resultado.set(new Object[] { primeira, segunda });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertNotSame(resultado.get()[0], resultado.get()[1]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveUsarFallbackSemRegistrarLarguraDefinitiva() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem antes do layout",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("mensagem antes do layout"));
                try {
                    Object dimensoes = obterDimensoes(view);
                    resultado.set(new Object[] { dimensoes, larguraDoCache(view) });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertNotNull(resultado.get()[0]);
            assertEquals(-1, resultado.get()[1]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveSubstituirFallbackQuandoLarguraRealFicarDisponivel() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem depois do layout",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("mensagem depois do layout"));
                try {
                    Object fallback = obterDimensoes(view);
                    atualizarDimensoes(view, 240);
                    Object definitivo = obterDimensoes(view);
                    resultado.set(new Object[] { fallback, definitivo, larguraDoCache(view) });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertNotNull(resultado.get()[0]);
            assertNotNull(resultado.get()[1]);
            assertNotSame(resultado.get()[0], resultado.get()[1]);
            assertEquals(240, resultado.get()[2]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void devePreservarMensagensPropriasERecebidasAoAtualizarCache() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem propria = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem própria", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem recebida = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "mensagem recebida", java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, propria, true);
            adicionarMensagem(frame, recebida, false);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View raiz = area.getUI().getRootView(area);
                View viewPropria = encontrarView(raiz, area.getText().indexOf("mensagem própria"));
                View viewRecebida = encontrarView(raiz, area.getText().indexOf("mensagem recebida"));
                try {
                    atualizarDimensoes(viewPropria, 240);
                    atualizarDimensoes(viewRecebida, 240);
                    resultado.set(new Object[] {
                            obterDadosModelo(viewPropria), obterDadosModelo(viewRecebida) });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertEquals(Boolean.TRUE, modeloParte(resultado.get()[0], "propria"));
            assertEquals(Boolean.FALSE, modeloParte(resultado.get()[1], "propria"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveUsarAlturaDasDimensoesParaMensagemComMetadadosValidos() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem com metadados válidos",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("mensagem com metadados válidos"));
                try {
                    atualizarDimensoes(view, 240);
                    resultado.set(new Object[] {
                            view.getPreferredSpan(View.Y_AXIS), obterDimensoes(view) });
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertEquals((float) dimensao(resultado.get()[1], "altura"),
                    resultado.get()[0]);
            assertTrue((Float) resultado.get()[0] > 0);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void devePreservarAlturaNaturalQuandoMetadadosDaMensagemEstaoIncompletos()
            throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem com metadados temporariamente incompletos",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Float> altura = new AtomicReference<>();
            executarNaEdt(() -> {
                try {
                    JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                    StyledDocument documento = area.getStyledDocument();
                    int inicio = area.getText().indexOf("mensagem com metadados");
                    SimpleAttributeSet semMetadados = new SimpleAttributeSet(
                            documento.getCharacterElement(inicio).getAttributes());
                    semMetadados.removeAttribute(chaveAtributo("ATRIBUTO_CABECALHO"));
                    semMetadados.removeAttribute(chaveAtributo("ATRIBUTO_CONTEUDO"));
                    documento.setCharacterAttributes(inicio,
                            "mensagem com metadados temporariamente incompletos".length(),
                            semMetadados, true);
                    forcarLayout(area, 240, 600);
                    View view = encontrarView(area.getUI().getRootView(area), inicio);
                    altura.set(view.getPreferredSpan(View.Y_AXIS));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            assertNotNull(altura.get());
            assertTrue(altura.get() > 0);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveTratarParagrafoFinalSemMetadadosNosDoisCaminhosDeDimensao() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "mensagem antes do parágrafo final",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                StyledDocument documento = area.getStyledDocument();
                View raiz = area.getUI().getRootView(area);
                Element elementoFinal = documento.getDefaultRootElement().getElement(
                        documento.getDefaultRootElement().getElementCount() - 1);
                View paragrafoFinal = encontrarViewDoElemento(raiz, elementoFinal);
                View conteudoFinal = encontrarMensagemConteudoView(paragrafoFinal,
                        elementoFinal);
                assertNotNull(conteudoFinal,
                        "MensagemConteudoView não encontrada no parágrafo final vazio.");
                atualizarDimensoes(conteudoFinal, 240);
                resultado.set(new Object[] {
                        conteudoFinal.getPreferredSpan(View.Y_AXIS),
                        larguraDoCache(conteudoFinal),
                        area.getText(),
                        documento.getDefaultRootElement().getElementCount() });
            });

            assertTrue((Float) resultado.get()[0] > 0);
            assertEquals(-1, resultado.get()[1]);
            assertEquals(1, contar((String) resultado.get()[2],
                    "mensagem antes do parágrafo final"));
            assertEquals(2, resultado.get()[3]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveCalcularDimensoesComJTextPaneAmploSemJFrame() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        ResultadoRedimensionamento resultado = medirRedimensionamento(area, 700);

        assertEquals(resultado.larguraFluxo(), resultado.larguraCache());
        assertTrue(resultado.altura() > 0);
        assertTrue(resultado.linhasConteudo() >= 1);
        assertEquals(StyleConstants.ALIGN_RIGHT, resultado.alinhamentoPropria());
        assertEquals(StyleConstants.ALIGN_LEFT, resultado.alinhamentoRecebida());
        assertEquals(3, resultado.quantidadeParagrafos());
        assertEquals(1, contar(resultado.texto(), "mensagem própria de teste"));
        assertEquals(1, contar(resultado.texto(), "mensagem recebida longa"));
        assertEquals(-1, resultado.larguraCacheParagrafoFinal());
        assertTrue(resultado.alturaParagrafoFinal() > 0);
    }

    @Test
    void deveAtualizarDimensoesDeAmploParaEstreito() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        ResultadoRedimensionamento amplo = medirRedimensionamento(area, 700);
        ResultadoRedimensionamento estreito = medirRedimensionamento(area, 220);

        assertTrue(estreito.larguraFluxo() < amplo.larguraFluxo());
        assertEquals(estreito.larguraFluxo(), estreito.larguraCache());
        assertTrue(estreito.altura() >= amplo.altura());
        assertTrue(estreito.linhasConteudo() >= amplo.linhasConteudo());
        assertEquals(amplo.texto(), estreito.texto());
        assertEquals(amplo.quantidadeParagrafos(), estreito.quantidadeParagrafos());
        assertEquals(amplo.alinhamentoPropria(), estreito.alinhamentoPropria());
        assertEquals(amplo.alinhamentoRecebida(), estreito.alinhamentoRecebida());
        assertEquals(-1, estreito.larguraCacheParagrafoFinal());
    }

    @Test
    void deveAtualizarDimensoesDeEstreitoParaAmplo() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        ResultadoRedimensionamento estreito = medirRedimensionamento(area, 220);
        ResultadoRedimensionamento amplo = medirRedimensionamento(area, 700);

        assertTrue(amplo.larguraFluxo() > estreito.larguraFluxo());
        assertEquals(amplo.larguraFluxo(), amplo.larguraCache());
        assertTrue(amplo.altura() <= estreito.altura());
        assertTrue(amplo.linhasConteudo() <= estreito.linhasConteudo());
        assertEquals(estreito.texto(), amplo.texto());
        assertEquals(estreito.quantidadeParagrafos(), amplo.quantidadeParagrafos());
        assertEquals(estreito.alinhamentoPropria(), amplo.alinhamentoPropria());
        assertEquals(estreito.alinhamentoRecebida(), amplo.alinhamentoRecebida());
        assertEquals(-1, amplo.larguraCacheParagrafoFinal());
    }

    @Test
    void deveArmazenarLinhasVisuaisDoCabecalhoEConteudoSemAlterarTexto()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        LinhasCalculadas propria = medirLinhasVisuais(area, 700,
                "mensagem própria de teste");
        LinhasCalculadas recebida = medirLinhasVisuais(area, 700,
                "mensagem recebida longa");

        assertEquals(propria.cabecalhoOriginal(), String.join("\n", propria.cabecalho()));
        assertEquals(propria.conteudoOriginal(), String.join("", propria.conteudo()));
        assertEquals(recebida.cabecalhoOriginal(), String.join("\n", recebida.cabecalho()));
        assertEquals(recebida.conteudoOriginal(), String.join("", recebida.conteudo()));
        assertEquals(propria.cabecalho().size(), propria.linhasCabecalho());
        assertEquals(propria.conteudo().size(), propria.linhasConteudo());
        assertEquals(recebida.cabecalho().size(), recebida.linhasCabecalho());
        assertEquals(recebida.conteudo().size(), recebida.linhasConteudo());
        assertEquals(propria.linhasCabecalho() * propria.alturaFonteCabecalho(),
                propria.alturaCabecalho());
        assertEquals(propria.linhasConteudo() * propria.alturaFonteConteudo(),
                propria.alturaConteudo());
        assertEquals(recebida.linhasCabecalho() * recebida.alturaFonteCabecalho(),
                recebida.alturaCabecalho());
        assertEquals(recebida.linhasConteudo() * recebida.alturaFonteConteudo(),
                recebida.alturaConteudo());
        assertTrue(recebida.linhasConteudo() > 1);
        assertThrows(UnsupportedOperationException.class,
                () -> propria.cabecalho().add("alteração"));
        assertThrows(UnsupportedOperationException.class,
                () -> recebida.conteudo().add("alteração"));
        assertEquals(3, propria.quantidadeParagrafos());
        assertEquals(area.getText(), recebida.textoDocumento());
    }

    @Test
    void devePreservarQuebrasExplicitasCaracteresEspeciaisERecalcularLinhasPorLargura()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();
        String conteudoEspecial = "  acentua\u00e7\u00e3o | parte: dois\n\nlinha final";
        executarNaEdt(() -> {
            try {
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Equipe: Azul"),
                                conteudoEspecial,
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 37)), false);
            } catch (ReflectiveOperationException | IOException exception) {
                throw new IllegalStateException(exception);
            }
        });

        LinhasCalculadas largo = medirLinhasVisuais(area, 700, "acentua\u00e7\u00e3o | parte");
        LinhasCalculadas estreito = medirLinhasVisuais(area, 220, "acentua\u00e7\u00e3o | parte");
        LinhasCalculadas amploNovamente = medirLinhasVisuais(area, 700,
                "acentua\u00e7\u00e3o | parte");

        assertEquals(conteudoEspecial, String.join("\n", largo.conteudo()));
        assertEquals(conteudoEspecial.replaceAll("\\R", ""),
                String.join("", estreito.conteudo()));
        assertTrue(estreito.conteudo().contains(""));
        assertEquals(estreito.cabecalhoOriginal(), String.join("\n", estreito.cabecalho()));
        assertEquals(estreito.cabecalho().size(), estreito.linhasCabecalho());
        assertEquals(estreito.conteudo().size(), estreito.linhasConteudo());
        assertTrue(estreito.linhasConteudo() >= largo.linhasConteudo());
        assertEquals(largo.linhasConteudo(), amploNovamente.linhasConteudo());
        assertEquals(largo.conteudo(), amploNovamente.conteudo());
        assertEquals(largo.larguraFluxo(), amploNovamente.larguraFluxo());
        assertEquals(6, estreito.quantidadeParagrafos());
        assertEquals(area.getText(), estreito.textoDocumento());
        executarNaEdt(() -> {
            try {
                verificarParagrafosDaMensagemEspecial(area, conteudoEspecial);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void deveAgruparLinhasPorParagrafoPreservandoVaziosEVariacaoDeLargura()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();
        String conteudo = "curto\n\nconteúdo longo para quebrar em várias linhas "
                + "quando a largura for reduzida\n";
        executarNaEdt(() -> {
            try {
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Equipe"), conteudo,
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 38)),
                        false);
            } catch (ReflectiveOperationException | IOException exception) {
                throw new IllegalStateException(exception);
            }
        });

        List<List<String>> linhasAmplas = medirLinhasPorParagrafo(area, 700, "curto");
        List<List<String>> linhasEstreitas = medirLinhasPorParagrafo(area, 220, "curto");
        LinhasCalculadas linhasCalculadas = medirLinhasVisuais(area, 220, "curto");

        assertEquals(4, linhasAmplas.size());
        assertEquals(4, linhasEstreitas.size());
        assertEquals(List.of(""), linhasAmplas.get(1));
        assertEquals(List.of(""), linhasEstreitas.get(1));
        assertTrue(linhasEstreitas.get(2).size() >= linhasAmplas.get(2).size());
        assertEquals(linhasCalculadas.linhasConteudo(),
                linhasEstreitas.stream().mapToInt(List::size).sum());
        assertEquals(linhasCalculadas.conteudo(), achatar(linhasEstreitas));
    }

    @Test
    void devePreservarParagrafoVazioCriadoPorQuebraExplicitaFinalSemIncluirParagrafoAutomatico()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();
        String conteudo = "primeiro\nsegundo\n";
        executarNaEdt(() -> {
            try {
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Equipe"), conteudo,
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 39)),
                        false);
            } catch (ReflectiveOperationException | IOException exception) {
                throw new IllegalStateException(exception);
            }
        });

        List<List<String>> linhas = medirLinhasPorParagrafo(area, 700, "primeiro");

        assertEquals(3, linhas.size());
        assertEquals(List.of("primeiro"), linhas.get(0));
        assertEquals(List.of("segundo"), linhas.get(1));
        assertEquals(List.of(""), linhas.get(2));
        assertEquals(6, area.getStyledDocument().getDefaultRootElement().getElementCount());
        assertEquals(conteudo.replace("\n", ""), String.join("", achatar(linhas)));
    }

    @Test
    void deveCompararLinhasManuaisComRowsDoSwingEmLargurasVariaveis() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();
        String conteudo = "curto\n\nconteúdo longo para provocar quebra automática "
                + "em largura estreita\n";
        executarNaEdt(() -> {
            try {
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Equipe"), conteudo,
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 40)),
                        false);
            } catch (ReflectiveOperationException | IOException exception) {
                throw new IllegalStateException(exception);
            }
        });

        compararLinhasManuaisComRows(area, 700, "curto");
        compararLinhasManuaisComRows(area, 220, "curto");
    }

    @Test
    void deveDiagnosticarElegibilidadeDaPinturaManualLongaPorLarguraEAlinhamento()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> {
            JTextPane area = new JTextPane();
            try {
                Class<?> tipoKit = Class.forName(
                        "br.edu.chat.client.ClienteChatFrame$MensagemEditorKit");
                Constructor<?> construtor = tipoKit.getDeclaredConstructor();
                construtor.setAccessible(true);
                area.setEditorKit((StyledEditorKit) construtor.newInstance());
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                                "mensagem própria longa para verificar a correspondência "
                                        + "das quebras automáticas em duas larguras",
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 41)), true);
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                                "mensagem recebida longa para verificar a correspondência "
                                        + "das quebras automáticas em duas larguras",
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 42)), false);
            } catch (ReflectiveOperationException | IOException exception) {
                throw new IllegalStateException(exception);
            }
            referencia.set(area);
        });
        JTextPane area = referencia.get();
        List<DiagnosticoElegibilidade> diagnosticos = new java.util.ArrayList<>();
        for (int largura : List.of(700, 220)) {
            diagnosticos.add(diagnosticarElegibilidade(area, largura,
                    "mensagem própria longa"));
            diagnosticos.add(diagnosticarElegibilidade(area, largura,
                    "mensagem recebida longa"));
        }

        assertEquals(4, diagnosticos.size());
        assertTrue(diagnosticos.stream().allMatch(
                diagnostico -> diagnostico.conteudoReconstruidoCompleto()));
        assertTrue(diagnosticos.stream().allMatch(
                diagnostico -> diagnostico.quantidadeRowsCabecalho() >= 1));
        assertTrue(diagnosticos.stream().allMatch(
                diagnostico -> diagnostico.quantidadeRowsConteudo() >= 1));
        System.out.println("Diagnóstico de elegibilidade da pintura longa:");
        diagnosticos.forEach(System.out::println);
    }

    @Test
    void deveCaracterizarPinturaDosBaloesProprioERecebidoSemJanela() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        ResultadoPintura resultado = pintarPainel(area, 700, 600);

        assertNotNull(resultado.corPropria());
        assertNotNull(resultado.corRecebida());
        assertTrue(resultado.corPropria().minX() > resultado.corRecebida().minX());
        assertTrue(resultado.corPropria().maxX() > resultado.corRecebida().maxX());
        assertEquals(new Color(0x3B, 0x82, 0xF6), resultado.corPropria().cor());
        assertEquals(new Color(0x1E, 0x33, 0x52), resultado.corRecebida().cor());
        assertTrue(resultado.viewsPropria() >= 1);
        assertTrue(resultado.viewsRecebida() >= 1);
        assertTrue(resultado.linhasRecebida() >= 1);
        assertEquals(3, resultado.quantidadeParagrafos());
        assertEquals(1, contar(resultado.texto(), "mensagem própria de teste"));
        assertEquals(1, contar(resultado.texto(), "mensagem recebida longa"));
        assertTrue(resultado.metadadosValidos());
    }

    @Test
    void deveCaracterizarPinturaEmLargurasAmplaEEstreitaSemBalaoFinal()
            throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        ResultadoPintura ampla = pintarPainel(area, 700, 600);
        ResultadoPintura estreita = pintarPainel(area, 220, 600);

        assertTrue(estreita.alturaRecebida() >= ampla.alturaRecebida());
        assertTrue(estreita.linhasRecebida() >= ampla.linhasRecebida());
        assertTrue(estreita.viewsRecebida() >= 1);
        assertTrue(estreita.linhasRecebida() >= 1);
        assertNull(estreita.corParagrafoFinal());
        assertEquals(ampla.texto(), estreita.texto());
        assertEquals(ampla.quantidadeParagrafos(), estreita.quantidadeParagrafos());
        assertTrue(estreita.metadadosValidos());
    }

    @Test
    void deveDisponibilizarDadosDoCabecalhoEConteudoParaPintura() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(criarPainelComMensagens()));
        JTextPane area = referencia.get();

        executarNaEdt(() -> {
            forcarLayout(area, 700, 600);
            StyledDocument documento = area.getStyledDocument();
            Element raiz = documento.getDefaultRootElement();
            View raizView = area.getUI().getRootView(area);
            int mensagensEncontradas = 0;

            for (int indice = 0; indice < raiz.getElementCount(); indice++) {
                Element paragrafo = raiz.getElement(indice);
                Object id = atributo(paragrafo.getAttributes(), "ATRIBUTO_ID_MENSAGEM");
                if (id == null) {
                    continue;
                }

                View paragrafoView = encontrarViewDoElemento(raizView, paragrafo);
                View conteudoView = encontrarMensagemConteudoView(paragrafoView, paragrafo);
                assertNotNull(conteudoView);

                try {
                    Object dados = obterDadosModelo(conteudoView);
                    Object dimensoes = dimensoesDoFluxo(conteudoView);
                    assertEquals(id, modeloParte(dados, "idMensagem"));
                    assertNotNull(modeloParte(dados, "cabecalho"));
                    assertNotNull(modeloParte(dados, "conteudo"));
                    assertTrue(dimensao(dimensoes, "linhasCabecalho") >= 1);
                    assertTrue(dimensao(dimensoes, "linhasConteudo") >= 1);
                    assertTrue(dimensao(dimensoes, "alturaCabecalho") > 0);
                    assertTrue(dimensao(dimensoes, "alturaConteudo") > 0);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
                mensagensEncontradas++;
            }

            assertEquals(2, mensagensEncontradas);
            Element paragrafoFinal = raiz.getElement(raiz.getElementCount() - 1);
            assertNull(atributo(paragrafoFinal.getAttributes(), "ATRIBUTO_ID_MENSAGEM"));
        });
    }

    @Test
    void devePreservarAgrupamentoAoPintarMensagemComMultiplosParagrafos() throws Exception {
        AtomicReference<JTextPane> referencia = new AtomicReference<>();
        executarNaEdt(() -> {
            JTextPane area = criarPainelComMensagens();
            try {
                inserirMensagemNoDocumento(area,
                        new Mensagem(new br.edu.chat.model.Usuario("Equipe"),
                                "primeiro parágrafo\n\nterceiro parágrafo",
                                java.time.LocalDateTime.of(2026, 11, 8, 9, 37)),
                        false);
            } catch (IOException | ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            referencia.set(area);
        });
        JTextPane area = referencia.get();

        String textoAntes = area.getText();
        List<Object> idsAntes = idsDosParagrafos(area.getStyledDocument());
        assertEquals(5, idsAntes.size());

        // Regra de regressão: a pintura não deve alterar o modelo do documento.
        pintarDocumento(area, 700, 700);

        StyledDocument documento = area.getStyledDocument();
        assertEquals(textoAntes, area.getText());
        assertEquals(idsAntes, idsDosParagrafos(documento));
        assertEquals(5, idsDosParagrafos(documento).size());

        Element raiz = documento.getDefaultRootElement();
        Element paragrafoFinal = raiz.getElement(raiz.getElementCount() - 1);
        assertNull(atributo(paragrafoFinal.getAttributes(), "ATRIBUTO_ID_MENSAGEM"));
    }

    @Test
    void deveInformarAlturaVerticalPositivaPelasDimensoes() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "conteúdo com altura calculada",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Float> altura = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("conteúdo com altura calculada"));
                altura.set(view.getPreferredSpan(View.Y_AXIS));
            });

            assertTrue(altura.get() > 0);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveAumentarAlturaParaConteudoMaisLongoNaMesmaLargura() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem curta = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "curto", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem longa = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "conteúdo muito longo para ocupar mais linhas na mesma largura disponível",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, curta, true);
            adicionarMensagem(frame, longa, false);
            AtomicReference<float[]> alturas = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                forcarLayout(area, 180, 600);
                View raiz = area.getUI().getRootView(area);
                View viewCurta = encontrarView(raiz, area.getText().indexOf("curto"));
                View viewLonga = encontrarView(raiz, area.getText().indexOf("conteúdo muito longo"));
                ParagraphView paragrafoCurto = encontrarMensagemParagraphView(viewCurta);
                ParagraphView paragrafoLongo = encontrarMensagemParagraphView(viewLonga);
                if (paragrafoCurto == null || paragrafoLongo == null) {
                    throw new IllegalStateException("Parágrafos das mensagens não encontrados.");
                }
                paragrafoCurto.getFlowSpan(0);
                paragrafoLongo.getFlowSpan(0);
                alturas.set(new float[] {
                        paragrafoCurto.getPreferredSpan(View.Y_AXIS),
                        paragrafoLongo.getPreferredSpan(View.Y_AXIS) });
            });

            assertTrue(alturas.get()[1] >= alturas.get()[0]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveDiagnosticarReservaDeAlturaDoCabecalhoNaPrimeiraRow() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "conteúdo longo para provocar várias linhas automáticas e medir a reserva "
                        + "do cabeçalho sem alterar as Rows seguintes",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 41));
        try {
            adicionarMensagem(frame, mensagem, false);
            AtomicReference<DiagnosticoReservaAltura> diagnostico = new AtomicReference<>();
            executarNaEdt(() -> {
                try {
                    JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                    forcarLayout(area, 180, 700);
                    StyledDocument documento = area.getStyledDocument();
                    View raiz = area.getUI().getRootView(area);
                    View conteudo = encontrarView(raiz,
                            area.getText().indexOf("conteúdo longo para provocar"));
                    ParagraphView paragrafo = encontrarMensagemParagraphView(conteudo);
                    if (conteudo == null || paragrafo == null) {
                        throw new IllegalStateException("View longa não encontrada.");
                    }
                    paragrafo.getFlowSpan(0);
                    List<LinhaRow> rows = coletarRows(paragrafo, documento);
                    Object dimensoes = obterDimensoes(conteudo);
                    List<Float> alturasRows = new java.util.ArrayList<>();
                    for (int indice = 0; indice < rows.size(); indice++) {
                        alturasRows.add(alturaDaRow(paragrafo, indice));
                    }
                    diagnostico.set(new DiagnosticoReservaAltura(
                            rows, alturasRows, paragrafo.getPreferredSpan(View.Y_AXIS),
                            dimensao(dimensoes, "alturaCabecalho")));
                } catch (ReflectiveOperationException | BadLocationException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            DiagnosticoReservaAltura resultado = diagnostico.get();
            assertTrue(resultado.rows().size() >= 2);
            assertEquals(resultado.rows().size(), resultado.alturasRows().size());
            assertTrue(resultado.alturaTotalParagrafo() > 0);
            assertTrue(resultado.alturaEstimadaCabecalho() > 0);
            assertTrue(resultado.alturasRows().stream().allMatch(altura -> altura > 0));
            float somaAlturasRows = soma(resultado.alturasRows());
            assertTrue(resultado.alturaTotalParagrafo()
                    >= somaAlturasRows + resultado.alturaEstimadaCabecalho());
            assertTrue(resultado.alturaTotalParagrafo() - somaAlturasRows
                    < resultado.alturaEstimadaCabecalho() * resultado.rows().size());
            assertTrue(resultado.rows().get(0).inicio()
                    < resultado.rows().get(resultado.rows().size() - 1).inicio());
            System.out.println("Diagnóstico de reserva do cabeçalho: rows="
                    + resultado.rows().size() + ", alturas=" + resultado.alturasRows()
                    + ", alturaTotal=" + resultado.alturaTotalParagrafo()
                    + ", alturaCabecalho=" + resultado.alturaEstimadaCabecalho());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveRefletirLargurasEstreitasEAmplasNaAlturaPreferida() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "conteúdo longo que deve quebrar quando a largura for estreita",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<float[]> alturas = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("conteúdo longo que deve quebrar"));
                atualizarDimensoes(view, 140);
                float estreita = view.getPreferredSpan(View.Y_AXIS);
                atualizarDimensoes(view, 420);
                float ampla = view.getPreferredSpan(View.Y_AXIS);
                alturas.set(new float[] { estreita, ampla });
            });

            assertTrue(alturas.get()[0] >= alturas.get()[1]);
            assertTrue(alturas.get()[0] > 0);
            assertTrue(alturas.get()[1] > 0);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void devePreservarPreferenciaHorizontalAoCalcularAlturaVertical() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "texto para verificar os dois eixos",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<float[]> preferencias = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("texto para verificar os dois eixos"));
                atualizarDimensoes(view, 240);
                preferencias.set(new float[] {
                        view.getPreferredSpan(View.X_AXIS),
                        view.getPreferredSpan(View.Y_AXIS) });
            });

            assertTrue(preferencias.get()[0] > 0);
            assertTrue(preferencias.get()[1] > 0);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveUsarFallbackVerticalSemRegistrarLargura() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem mensagem = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "texto antes da largura real",
                java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        try {
            adicionarMensagem(frame, mensagem, true);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View view = encontrarView(area.getUI().getRootView(area),
                        area.getText().indexOf("texto antes da largura real"));
                resultado.set(new Object[] {
                        view.getPreferredSpan(View.Y_AXIS), larguraDoCache(view) });
            });

            assertTrue((Float) resultado.get()[0] > 0);
            assertEquals(-1, resultado.get()[1]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void devePreservarTextoEParagrafosAoCalcularPreferenciaVertical() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        Mensagem primeira = new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                "primeiro parágrafo lógico", java.time.LocalDateTime.of(2026, 11, 8, 9, 35));
        Mensagem segunda = new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                "segundo parágrafo lógico", java.time.LocalDateTime.of(2026, 11, 8, 9, 36));
        try {
            adicionarMensagem(frame, primeira, true);
            adicionarMensagem(frame, segunda, false);
            AtomicReference<Object[]> resultado = new AtomicReference<>();
            executarNaEdt(() -> {
                JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
                View raiz = area.getUI().getRootView(area);
                View viewPrimeira = encontrarView(raiz,
                        area.getText().indexOf("primeiro parágrafo lógico"));
                View viewSegunda = encontrarView(raiz,
                        area.getText().indexOf("segundo parágrafo lógico"));
                atualizarDimensoes(viewPrimeira, 240);
                atualizarDimensoes(viewSegunda, 240);
                viewPrimeira.getPreferredSpan(View.Y_AXIS);
                viewSegunda.getPreferredSpan(View.Y_AXIS);
                resultado.set(new Object[] {
                        area.getText(),
                        area.getStyledDocument().getDefaultRootElement().getElementCount() });
            });

            assertEquals(1, contar((String) resultado.get()[0], "primeiro parágrafo lógico"));
            assertEquals(1, contar((String) resultado.get()[0], "segundo parágrafo lógico"));
            assertEquals(3, resultado.get()[1]);
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveSepararCabecalhoEConteudoDeMensagemFormatada() throws Exception {
        Object partes = identificarPartes("[09:35] Ana: Olá | turma", "Ana");

        assertEquals("[09:35] Ana", parte(partes, "cabecalho"));
        assertEquals("Olá | turma", parte(partes, "conteudo"));
    }

    @Test
    void deveAceitarHorarioSomenteComHora() throws Exception {
        Object partes = identificarPartes("[09] Ana: mensagem", "Ana");

        assertEquals("[09] Ana", parte(partes, "cabecalho"));
        assertEquals("mensagem", parte(partes, "conteudo"));
    }

    @Test
    void devePreservarOcorrenciasDeDoisPontosNoConteudo() throws Exception {
        Object partes = identificarPartes("[09:35] Ana: parte 1: parte 2", "Ana");

        assertEquals("[09:35] Ana", parte(partes, "cabecalho"));
        assertEquals("parte 1: parte 2", parte(partes, "conteudo"));
    }

    @Test
    void deveUsarNomeConhecidoQuandoEleContemDoisPontos() throws Exception {
        Object partes = identificarPartes("[09:35] Equipe: Azul: mensagem", "Equipe: Azul");

        assertEquals("[09:35] Equipe: Azul", parte(partes, "cabecalho"));
        assertEquals("mensagem", parte(partes, "conteudo"));
    }

    @Test
    void deveManterTextoInteiroQuandoSeparacaoForAmbigua() throws Exception {
        Object partes = identificarPartes("[09:35] Ana: mensagem", "Bia");

        assertEquals("", parte(partes, "cabecalho"));
        assertEquals("[09:35] Ana: mensagem", parte(partes, "conteudo"));
    }

    @Test
    void deveIniciarComControlesNoEstadoDesconectado() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            JButton conectar = campo(frame, "botaoConectar", JButton.class);
            JButton enviar = campo(frame, "botaoEnviar", JButton.class);
            JButton desconectar = campo(frame, "botaoDesconectar", JButton.class);
            JCheckBox modoCadastro = campo(frame, "modoCadastro", JCheckBox.class);
            JTextField nome = campo(frame, "campoNome", JTextField.class);

            assertTrue(conectar.isEnabled());
            assertFalse(enviar.isEnabled());
            assertFalse(desconectar.isEnabled());
            assertFalse(nome.isEnabled());
            assertFalse(modoCadastro.isSelected());

            clicarModoCadastro(frame);
            assertTrue(nome.isEnabled());
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveUsarNomeCanonicoNoLoginV2() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            executarNaEdt(() -> campo(frame, "campoNome", JTextField.class)
                    .setText("Nome digitado ignorado"));

            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            assertEquals("Ana", texto(frame, "campoNome"));
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveUsarNomeInformadoNoCadastroV2() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        String login = "cadastro" + UUID.randomUUID().toString().replace('-', 'a');
        try {
            preencherConexao(frame, contexto.porta, "Usuario Novo");
            executarNaEdt(() -> {
                campo(frame, "campoLogin", JTextField.class).setText(login);
                campo(frame, "campoNome", JTextField.class).setText("Usuário Novo");
            });
            clicarModoCadastro(frame);
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            assertEquals("Usuário Novo", texto(frame, "campoNome"));
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveConectarEImpedirConexoesDuplicadas() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");

            assertFalse(campo(frame, "botaoConectar", JButton.class).isEnabled());
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));
            assertEquals(1, handlersAtivos(contexto.servidor));

            clicar(frame, "botaoConectar");
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            assertFalse(campo(frame, "botaoConectar", JButton.class).isEnabled());
            assertTrue(campo(frame, "botaoEnviar", JButton.class).isEnabled());
            assertTrue(campo(frame, "botaoDesconectar", JButton.class).isEnabled());
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveExibirHistoricoEMensagemPropriaUmaUnicaVez() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Preparador",
                contexto.configuracao, mensagem -> { }, () -> { });
        try {
            preparador.conectar("preparador", "senha-teste".toCharArray());
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            preparador.enviar("mensagem histórica");
            aguardar(() -> contexto.repository.listarHistorico().stream()
                    .anyMatch(mensagem -> mensagem.getConteudo().equals("mensagem histórica")));
            preparador.fechar();

            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("mensagem histórica"));
            aguardar(() -> botaoHabilitado(frame, "botaoEnviar"));

            preencherMensagem(frame, "minha mensagem | preservada");
            clicar(frame, "botaoEnviar");
            aguardar(() -> contar(texto(frame, "areaHistorico"), "minha mensagem | preservada") == 1);

            assertEquals(1, contar(texto(frame, "areaHistorico"), "minha mensagem | preservada"));
            assertTrue(campo(frame, "botaoEnviar", JButton.class).isEnabled());
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveExibirHistoricoUmaUnicaVezAposDesconectarEReconectar() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Preparador",
                contexto.configuracao, mensagem -> { }, () -> { });
        try {
            preparador.conectar("preparador", "senha-teste".toCharArray());
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            preparador.enviar("histórico da reconexão");
            aguardar(() -> contexto.repository.listarHistorico().stream()
                    .anyMatch(mensagem -> mensagem.getConteudo().equals("histórico da reconexão")));
            preparador.fechar();

            preencherConexao(frame, contexto.porta, "Victor");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("histórico da reconexão"));
            aguardar(() -> botaoHabilitado(frame, "botaoDesconectar"));

            clicar(frame, "botaoDesconectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Desconectado"));
            aguardar(() -> botaoHabilitado(frame, "botaoConectar"));

            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico").contains("histórico da reconexão"));
            aguardar(() -> botaoHabilitado(frame, "botaoDesconectar"));

            assertEquals(1, contar(texto(frame, "areaHistorico"), "histórico da reconexão"));
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveAlinharMensagensPropriasERecebidas() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Bruno",
                contexto.configuracao, mensagem -> { }, () -> { });
        try {
            preparador.conectar("bruno", "senha-teste".toCharArray());
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);

            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            preparador.enviar("mensagem recebida | acentuação");
            aguardar(() -> texto(frame, "areaHistorico")
                    .contains("mensagem recebida | acentuação"));

            preencherMensagem(frame, "mensagem própria | preservada");
            clicar(frame, "botaoEnviar");
            aguardar(() -> texto(frame, "areaHistorico")
                    .contains("mensagem própria | preservada"));

            assertEquals(StyleConstants.ALIGN_LEFT,
                    alinhamentoDoTrecho(frame, "mensagem recebida | acentuação"));
            assertEquals(StyleConstants.ALIGN_RIGHT,
                    alinhamentoDoTrecho(frame, "mensagem própria | preservada"));
            assertEquals(1, contar(texto(frame, "areaHistorico"), "mensagem própria | preservada"));
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveEnviarMensagemComNomeCanonicoMesmoComNomeDigitadoDiferente() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));
            executarNaEdt(() -> campo(frame, "campoNome", JTextField.class)
                    .setText("Nome digitado diferente"));

            preencherMensagem(frame, "mensagem com identidade canonica");
            clicar(frame, "botaoEnviar");
            aguardar(() -> texto(frame, "areaHistorico")
                    .contains("Ana: mensagem com identidade canonica"));

            assertEquals(StyleConstants.ALIGN_RIGHT,
                    alinhamentoDoTrecho(frame, "mensagem com identidade canonica"));
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveRespeitarMaiusculasEMinusculasNaIdentidadeAutenticada() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            assertTrue(mensagemEhPropria(frame, new Mensagem(
                    new br.edu.chat.model.Usuario("Ana"), "exata",
                    java.time.LocalDateTime.now())));
            assertFalse(mensagemEhPropria(frame, new Mensagem(
                    new br.edu.chat.model.Usuario("ana"), "diferente",
                    java.time.LocalDateTime.now())));
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveUsarNovaIdentidadeCanonicaAposReconexao() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            clicar(frame, "botaoDesconectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Desconectado"));

            preencherConexao(frame, contexto.porta, "Bia");
            executarNaEdt(() -> campo(frame, "campoNome", JTextField.class).setText("Ana"));
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            preencherMensagem(frame, "mensagem apos reconexao");
            clicar(frame, "botaoEnviar");
            aguardar(() -> texto(frame, "areaHistorico")
                    .contains("Bia: mensagem apos reconexao"));
            assertEquals(StyleConstants.ALIGN_RIGHT,
                    alinhamentoDoTrecho(frame, "mensagem apos reconexao"));
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveRetornarFalsoQuandoNaoHouverIdentidadeAutenticada() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            assertFalse(mensagemEhPropria(frame, new Mensagem(
                    new br.edu.chat.model.Usuario("Ana"), "sem sessão",
                    java.time.LocalDateTime.now())));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveIgnorarMensagemAtrasadaDeSessaoAntiga() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            definirGeracao(frame, 2);
            executarNaEdt(() -> campo(frame, "rotuloEstado", javax.swing.JLabel.class)
                    .setText("Conectado"));
            invocarRecebimento(frame, 1, new Mensagem(
                    new br.edu.chat.model.Usuario("Ana"), "mensagem antiga",
                    java.time.LocalDateTime.now()));
            executarNaEdt(() -> { });

            assertFalse(texto(frame, "areaHistorico").contains("mensagem antiga"));
            assertEquals("Conectado", texto(frame, "rotuloEstado"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveIgnorarDesconexaoAtrasadaDaSessaoAntiga() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            definirGeracao(frame, 2);
            executarNaEdt(() -> campo(frame, "rotuloEstado", javax.swing.JLabel.class)
                    .setText("Conectado"));
            invocarDesconexao(frame, 1);
            executarNaEdt(() -> { });

            assertEquals("Conectado", texto(frame, "rotuloEstado"));
            assertEquals(2, geracao(frame));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveInvalidarGeracaoApenasUmaVezParaCallbacksDuplicados() throws Exception {
        requerAmbienteGrafico();
        ClienteChatFrame frame = criarFrame();
        try {
            definirGeracao(frame, 1);
            invocarDesconexao(frame, 1);
            executarNaEdt(() -> { });
            assertEquals(2, geracao(frame));
            assertEquals("Desconectado", texto(frame, "rotuloEstado"));

            invocarDesconexao(frame, 1);
            executarNaEdt(() -> { });
            assertEquals(2, geracao(frame));
            assertEquals("Desconectado", texto(frame, "rotuloEstado"));
        } finally {
            fecharFrame(frame);
        }
    }

    @Test
    void deveAlinharComoPropriaMensagemRecuperadaDoHistorico() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        ClienteChat preparador = new ClienteChat("127.0.0.1", contexto.porta, "Ana",
                contexto.configuracao, mensagem -> { }, () -> { });
        try {
            preparador.conectar("ana", "senha-teste".toCharArray());
            aguardar(() -> handlersAtivos(contexto.servidor) == 1);
            preparador.enviar("histórico próprio | persistido");
            aguardar(() -> contexto.repository.listarHistorico().stream()
                    .anyMatch(mensagem -> mensagem.getConteudo()
                            .equals("histórico próprio | persistido")));
            preparador.fechar();

            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "areaHistorico")
                    .contains("histórico próprio | persistido"));

            assertEquals(StyleConstants.ALIGN_RIGHT,
                    alinhamentoDoTrecho(frame, "histórico próprio | persistido"));
            assertEquals(1, contar(texto(frame, "areaHistorico"),
                    "histórico próprio | persistido"));
        } finally {
            preparador.fechar();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveAtualizarEstadoAposDesconexaoInesperada() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        Timer fechamentoDialogo = fecharDialogosAutomaticamente();
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            contexto.servidor.encerrar();
            aguardar(() -> texto(frame, "rotuloEstado").equals("Desconectado"));

            assertTrue(campo(frame, "botaoConectar", JButton.class).isEnabled());
            assertFalse(campo(frame, "botaoEnviar", JButton.class).isEnabled());
            assertFalse(campo(frame, "botaoDesconectar", JButton.class).isEnabled());
        } finally {
            fechamentoDialogo.stop();
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    @Test
    void deveFecharJanelaERecursosComSeguranca() throws Exception {
        requerAmbienteGrafico();
        Contexto contexto = iniciarServidor();
        ClienteChatFrame frame = criarFrame(contexto.configuracao);
        try {
            preencherConexao(frame, contexto.porta, "Ana");
            clicar(frame, "botaoConectar");
            aguardar(() -> texto(frame, "rotuloEstado").equals("Conectado"));

            executarNaEdt(() -> frame.dispatchEvent(new WindowEvent(
                    frame, WindowEvent.WINDOW_CLOSING)));
            aguardar(() -> !frame.isDisplayable());
            aguardar(() -> handlersAtivos(contexto.servidor) == 0);

            assertFalse(frame.isDisplayable());
        } finally {
            fecharFrame(frame);
            contexto.fechar();
        }
    }

    private static void requerAmbienteGrafico() {
        assumeFalse(GraphicsEnvironment.isHeadless(),
                "Teste Swing ignorado em ambiente headless.");
    }

    private static ClienteChatFrame criarFrame() throws Exception {
        return criarFrame(null);
    }

    private static ClienteChatFrame criarFrame(ConfiguracaoTls configuracaoTls) throws Exception {
        AtomicReference<ClienteChatFrame> referencia = new AtomicReference<>();
        executarNaEdt(() -> referencia.set(new ClienteChatFrame(configuracaoTls)));
        return referencia.get();
    }

    private static void fecharFrame(ClienteChatFrame frame) throws Exception {
        if (frame != null) {
            executarNaEdt(frame::dispose);
        }
    }

    private static void preencherConexao(ClienteChatFrame frame, int porta, String nome)
            throws Exception {
        executarNaEdt(() -> {
            campo(frame, "campoPorta", JTextField.class).setText(Integer.toString(porta));
            campo(frame, "campoLogin", JTextField.class).setText(nome.toLowerCase());
            campo(frame, "campoNome", JTextField.class).setText(nome);
            campo(frame, "campoSenha", JPasswordField.class).setText("senha-teste");
        });
    }

    private static void preencherMensagem(ClienteChatFrame frame, String mensagem)
            throws Exception {
        executarNaEdt(() -> campo(frame, "campoMensagem", JTextField.class).setText(mensagem));
    }

    private static void clicar(ClienteChatFrame frame, String nomeCampo) throws Exception {
        executarNaEdt(() -> campo(frame, nomeCampo, JButton.class).doClick());
    }

    private static void clicarModoCadastro(ClienteChatFrame frame) throws Exception {
        executarNaEdt(() -> campo(frame, "modoCadastro", JCheckBox.class).doClick());
    }

    private static void definirGeracao(ClienteChatFrame frame, long geracao)
            throws Exception {
        executarNaEdt(() -> {
            try {
                Field campo = ClienteChatFrame.class.getDeclaredField("geracaoSessao");
                campo.setAccessible(true);
                campo.setLong(frame, geracao);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static long geracao(ClienteChatFrame frame) throws Exception {
        AtomicReference<Long> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                Field campo = ClienteChatFrame.class.getDeclaredField("geracaoSessao");
                campo.setAccessible(true);
                resultado.set(campo.getLong(frame));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static void invocarRecebimento(ClienteChatFrame frame, long geracao,
            Mensagem mensagem) throws Exception {
        executarNaEdt(() -> {
            try {
                Method metodo = ClienteChatFrame.class.getDeclaredMethod(
                        "receberMensagem", long.class, Mensagem.class);
                metodo.setAccessible(true);
                metodo.invoke(frame, geracao, mensagem);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static void invocarDesconexao(ClienteChatFrame frame, long geracao)
            throws Exception {
        executarNaEdt(() -> {
            try {
                Method metodo = ClienteChatFrame.class.getDeclaredMethod(
                        "clienteDesconectou", long.class);
                metodo.setAccessible(true);
                metodo.invoke(frame, geracao);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static boolean mensagemEhPropria(ClienteChatFrame frame, Mensagem mensagem)
            throws Exception {
        AtomicReference<Boolean> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                Method metodo = ClienteChatFrame.class.getDeclaredMethod(
                        "mensagemEhPropria", Mensagem.class);
                metodo.setAccessible(true);
                resultado.set((Boolean) metodo.invoke(frame, mensagem));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static JTextPane criarPainelComMensagens() {
        try {
            JTextPane area = new JTextPane();
            Class<?> tipoKit = Class.forName(
                    "br.edu.chat.client.ClienteChatFrame$MensagemEditorKit");
            Constructor<?> construtor = tipoKit.getDeclaredConstructor();
            construtor.setAccessible(true);
            area.setEditorKit((StyledEditorKit) construtor.newInstance());
            inserirMensagemNoDocumento(area,
                    new Mensagem(new br.edu.chat.model.Usuario("Ana"),
                            "mensagem própria de teste",
                            java.time.LocalDateTime.of(2026, 11, 8, 9, 35)), true);
            inserirMensagemNoDocumento(area,
                    new Mensagem(new br.edu.chat.model.Usuario("Bia"),
                            "mensagem recebida longa com conteúdo suficiente para quebrar automaticamente em largura estreita",
                            java.time.LocalDateTime.of(2026, 11, 8, 9, 36)), false);
            return area;
        } catch (ReflectiveOperationException | IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void inserirMensagemNoDocumento(JTextPane area, Mensagem mensagem,
            boolean propria) throws IOException, ReflectiveOperationException {
        String textoFormatado = ClienteChatApresentacao.formatar(mensagem);
        int fimHorario = textoFormatado.indexOf("] ") + 2;
        String cabecalho = textoFormatado.substring(0, fimHorario)
                + mensagem.getRemetente().getNome();
        String separador = cabecalho + ": ";
        if (!textoFormatado.startsWith(separador)) {
            throw new IOException("Formato de mensagem de teste inesperado.");
        }
        String conteudo = textoFormatado.substring(separador.length());
        SimpleAttributeSet atributos = new SimpleAttributeSet();
        atributos.addAttribute(chaveAtributo("ATRIBUTO_REMETENTE"),
                mensagem.getRemetente().getNome());
        atributos.addAttribute(chaveAtributo("ATRIBUTO_CABECALHO"), cabecalho);
        atributos.addAttribute(chaveAtributo("ATRIBUTO_CONTEUDO"), conteudo);
        atributos.addAttribute(chaveAtributo("ATRIBUTO_MENSAGEM_PROPRIA"), propria);
        atributos.addAttribute(chaveAtributo("ATRIBUTO_ID_MENSAGEM"), UUID.randomUUID().toString());
        StyleConstants.setFontFamily(atributos, "SansSerif");
        StyleConstants.setFontSize(atributos, 14);
        StyledDocument documento = area.getStyledDocument();
        try {
            int inicio = documento.getLength();
            documento.insertString(inicio, textoFormatado + System.lineSeparator(), atributos);
            SimpleAttributeSet paragrafo = new SimpleAttributeSet();
            StyleConstants.setAlignment(paragrafo,
                    propria ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
            StyleConstants.setBackground(paragrafo,
                    propria ? new Color(0x3B, 0x82, 0xF6)
                            : new Color(0x1E, 0x33, 0x52));
            paragrafo.addAttribute(chaveAtributo("ATRIBUTO_ID_MENSAGEM"),
                    atributos.getAttribute(chaveAtributo("ATRIBUTO_ID_MENSAGEM")));
            documento.setParagraphAttributes(inicio, textoFormatado.length()
                    , paragrafo, false);
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IOException("Não foi possível inserir a mensagem de teste.", exception);
        }
    }

    private static Object chaveAtributo(String nome) throws ReflectiveOperationException {
        Field campo = ClienteChatFrame.class.getDeclaredField(nome);
        campo.setAccessible(true);
        return campo.get(null);
    }

    private static ResultadoRedimensionamento medirRedimensionamento(JTextPane area,
            int largura) throws Exception {
        AtomicReference<ResultadoRedimensionamento> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, 600);
                StyledDocument documento = area.getStyledDocument();
                View raiz = area.getUI().getRootView(area);
                View propria = encontrarView(raiz, area.getText().indexOf("mensagem própria de teste"));
                View recebida = encontrarView(raiz, area.getText().indexOf("mensagem recebida longa"));
                ParagraphView paragrafo = encontrarMensagemParagraphView(recebida);
                if (propria == null || recebida == null || paragrafo == null) {
                    throw new IllegalStateException("Views de mensagem não encontradas após o layout.");
                }
                int larguraFluxo = paragrafo.getFlowSpan(0);
                Object dimensoes = obterDimensoes(recebida);
                Element elementoFinal = documento.getDefaultRootElement().getElement(
                        documento.getDefaultRootElement().getElementCount() - 1);
                View paragrafoFinal = encontrarViewDoElemento(raiz, elementoFinal);
                View conteudoFinal = encontrarMensagemConteudoView(paragrafoFinal,
                        elementoFinal);
                if (conteudoFinal == null) {
                    throw new IllegalStateException(
                            "MensagemConteudoView do parágrafo final não encontrada.");
                }
                resultado.set(new ResultadoRedimensionamento(
                        larguraFluxo,
                        larguraDoCache(recebida),
                        recebida.getPreferredSpan(View.Y_AXIS),
                        dimensao(dimensoes, "linhasConteudo"),
                        alinhamentoDoTrecho(area, "mensagem própria de teste"),
                        alinhamentoDoTrecho(area, "mensagem recebida longa"),
                        conteudoFinal.getPreferredSpan(View.Y_AXIS),
                        larguraDoCache(conteudoFinal),
                        area.getText(),
                        documento.getDefaultRootElement().getElementCount()));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static LinhasCalculadas medirLinhasVisuais(JTextPane area, int largura,
            String trecho) throws Exception {
        AtomicReference<LinhasCalculadas> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, 600);
                StyledDocument documento = area.getStyledDocument();
                View raiz = area.getUI().getRootView(area);
                View view = encontrarView(raiz, area.getText().indexOf(trecho));
                ParagraphView paragrafo = encontrarMensagemParagraphView(view);
                if (view == null || paragrafo == null) {
                    throw new IllegalStateException("View de mensagem não encontrada.");
                }
                int larguraFluxo = paragrafo.getFlowSpan(0);
                Object dimensoes = obterDimensoes(view);
                Object linhas = linhasDeDimensoes(dimensoes);
                List<String> cabecalho = listaDeLinhas(linhas, "cabecalho");
                List<String> conteudo = listaDeLinhas(linhas, "conteudo");
                Object dados = obterDadosModelo(view);
                FontMetrics metricasConteudo = new Canvas().getFontMetrics(
                        new Font("SansSerif", Font.PLAIN, 14));
                FontMetrics metricasCabecalho = new Canvas().getFontMetrics(
                        new Font("SansSerif", Font.BOLD, 12));
                resultado.set(new LinhasCalculadas(
                        cabecalho,
                        conteudo,
                        dimensao(dimensoes, "linhasCabecalho"),
                        dimensao(dimensoes, "linhasConteudo"),
                        dimensao(dimensoes, "alturaCabecalho"),
                        dimensao(dimensoes, "alturaConteudo"),
                        metricasCabecalho.getHeight(),
                        metricasConteudo.getHeight(),
                        larguraFluxo,
                        (String) modeloParte(dados, "cabecalho"),
                        (String) modeloParte(dados, "conteudo"),
                        area.getText(),
                        documento.getDefaultRootElement().getElementCount()));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static void verificarParagrafosDaMensagemEspecial(JTextPane area,
            String conteudoEsperado) throws Exception {
        StyledDocument documento = area.getStyledDocument();
        Element raiz = documento.getDefaultRootElement();
        String primeiraLinha = conteudoEsperado.substring(
                0, conteudoEsperado.indexOf('\n')).trim();
        int inicioMensagem = area.getText().indexOf(primeiraLinha);
        int primeiroParagrafo = -1;
        for (int indice = 0; indice < raiz.getElementCount(); indice++) {
            Element paragrafo = raiz.getElement(indice);
            if (inicioMensagem >= paragrafo.getStartOffset()
                    && inicioMensagem < paragrafo.getEndOffset()) {
                primeiroParagrafo = indice;
                break;
            }
        }
        assertTrue(primeiroParagrafo >= 0);
        assertEquals(raiz.getElementCount() - 1, primeiroParagrafo + 3);

        for (int indice = primeiroParagrafo; indice < primeiroParagrafo + 3; indice++) {
            Element paragrafo = raiz.getElement(indice);
            assertEquals(StyleConstants.ALIGN_LEFT,
                    StyleConstants.getAlignment(paragrafo.getAttributes()));
            assertEquals(new Color(0x1E, 0x33, 0x52),
                    StyleConstants.getBackground(paragrafo.getAttributes()));
            int inicio = Math.min(paragrafo.getStartOffset(), documento.getLength());
            int fim = Math.min(paragrafo.getEndOffset(), documento.getLength());
            String textoParagrafo = fim > inicio
                    ? documento.getText(inicio, fim - inicio) : "";
            if (!textoParagrafo.replace("\r", "").replace("\n", "").isEmpty()) {
                AttributeSet atributosTexto = atributos(documento, inicio);
                assertEquals("Equipe: Azul",
                        atributo(atributosTexto, "ATRIBUTO_REMETENTE"));
                assertEquals(conteudoEsperado,
                        atributo(atributosTexto, "ATRIBUTO_CONTEUDO"));
            }
        }

        Element paragrafoFinal = raiz.getElement(raiz.getElementCount() - 1);
        assertTrue(paragrafoFinal.getStartOffset() >= documento.getLength());
    }

    private static Object linhasDeDimensoes(Object dimensoes)
            throws ReflectiveOperationException {
        Method metodo = dimensoes.getClass().getDeclaredMethod("linhas");
        metodo.setAccessible(true);
        return metodo.invoke(dimensoes);
    }

    @SuppressWarnings("unchecked")
    private static List<String> listaDeLinhas(Object linhas, String nome)
            throws ReflectiveOperationException {
        Method metodo = linhas.getClass().getDeclaredMethod(nome);
        metodo.setAccessible(true);
        return (List<String>) metodo.invoke(linhas);
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> listaDeLinhasPorParagrafo(Object linhas)
            throws ReflectiveOperationException {
        Method metodo = linhas.getClass().getDeclaredMethod("conteudoPorParagrafo");
        metodo.setAccessible(true);
        return (List<List<String>>) metodo.invoke(linhas);
    }

    private static List<List<String>> medirLinhasPorParagrafo(JTextPane area, int largura,
            String trecho) throws Exception {
        AtomicReference<List<List<String>>> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, 700);
                View raiz = area.getUI().getRootView(area);
                View view = encontrarView(raiz, area.getText().indexOf(trecho));
                ParagraphView paragrafo = encontrarMensagemParagraphView(view);
                if (view == null || paragrafo == null) {
                    throw new IllegalStateException("View de mensagem não encontrada.");
                }
                paragrafo.getFlowSpan(0);
                Object dimensoes = obterDimensoes(view);
                Object linhas = linhasDeDimensoes(dimensoes);
                resultado.set(listaDeLinhasPorParagrafo(linhas));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static List<String> achatar(List<List<String>> linhasPorParagrafo) {
        List<String> resultado = new java.util.ArrayList<>();
        for (List<String> linhas : linhasPorParagrafo) {
            resultado.addAll(linhas);
        }
        return List.copyOf(resultado);
    }

    private static void compararLinhasManuaisComRows(JTextPane area, int largura,
            String trecho) throws Exception {
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, 700);
                StyledDocument documento = area.getStyledDocument();
                View raizView = area.getUI().getRootView(area);
                String textoDocumento = documento.getText(0, documento.getLength());
                int inicioMensagem = textoDocumento.indexOf(trecho);
                View conteudoView = encontrarView(raizView, inicioMensagem);
                ParagraphView paragrafoMensagem = encontrarMensagemParagraphView(conteudoView);
                if (conteudoView == null || paragrafoMensagem == null) {
                    throw new IllegalStateException("View da mensagem não encontrada.");
                }

                Object id = atributo(documento, inicioMensagem, "ATRIBUTO_ID_MENSAGEM");
                paragrafoMensagem.getFlowSpan(0);
                Object dimensoes = obterDimensoes(conteudoView);
                Object linhas = linhasDeDimensoes(dimensoes);
                List<List<String>> linhasManuais = listaDeLinhasPorParagrafo(linhas);
                List<Element> paragrafos = paragrafosComId(documento, id);
                String conteudoLogico = (String) atributo(documento, inicioMensagem,
                        "ATRIBUTO_CONTEUDO");
                int inicioConteudo = inicioMensagem;
                int fimConteudo = inicioConteudo + conteudoLogico.length();
                Element paragrafoFinal = documento.getDefaultRootElement().getElement(
                        documento.getDefaultRootElement().getElementCount() - 1);
                boolean grupoVazioTerminalSemUuid = linhasManuais.size() == paragrafos.size() + 1
                        && List.of("").equals(linhasManuais.get(linhasManuais.size() - 1))
                        && atributo(paragrafoFinal.getAttributes(), "ATRIBUTO_ID_MENSAGEM") == null;

                if (!grupoVazioTerminalSemUuid) {
                    assertEquals(linhasManuais.size(), paragrafos.size(),
                            diagnostico(largura, id, -1, null, null,
                                    "quantidade de parágrafos"));
                }
                List<LinhaRow> rowsDaMensagem = new java.util.ArrayList<>();
                for (int indice = 0; indice < paragrafos.size(); indice++) {
                    Element paragrafo = paragrafos.get(indice);
                    View paragrafoView = encontrarViewDoElemento(raizView, paragrafo);
                    rowsDaMensagem.addAll(coletarRows(paragrafoView, documento));
                }
                List<LinhaRow> rowsDeConteudo = intersecoesComIntervalo(rowsDaMensagem,
                        documento, inicioConteudo, fimConteudo);
                String conteudoReconstruido = reconstruirTexto(rowsDeConteudo);
                assertEquals(normalizarTexto(conteudoLogico), conteudoReconstruido,
                        diagnosticoConteudo(largura, id, linhasManuais, rowsDeConteudo,
                                conteudoLogico, conteudoReconstruido));

                assertNull(atributo(paragrafoFinal.getAttributes(), "ATRIBUTO_ID_MENSAGEM"));
            } catch (ReflectiveOperationException | BadLocationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static DiagnosticoElegibilidade diagnosticarElegibilidade(JTextPane area,
            int largura, String trecho) throws Exception {
        AtomicReference<DiagnosticoElegibilidade> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, 700);
                StyledDocument documento = area.getStyledDocument();
                View raizView = area.getUI().getRootView(area);
                String textoDocumento = documento.getText(0, documento.getLength());
                int inicioTrecho = textoDocumento.indexOf(trecho);
                View conteudoView = encontrarView(raizView, inicioTrecho);
                ParagraphView paragrafoView = encontrarMensagemParagraphView(conteudoView);
                if (conteudoView == null || paragrafoView == null) {
                    throw new IllegalStateException("View da mensagem não encontrada.");
                }

                Element paragrafo = documento.getParagraphElement(inicioTrecho);
                Object id = atributo(paragrafo.getAttributes(), "ATRIBUTO_ID_MENSAGEM");
                String cabecalho = (String) atributo(documento, inicioTrecho,
                        "ATRIBUTO_CABECALHO");
                String conteudo = (String) atributo(documento, inicioTrecho,
                        "ATRIBUTO_CONTEUDO");
                int inicioConteudo = paragrafo.getStartOffset() + cabecalho.length() + 2;
                int fimConteudo = inicioConteudo + conteudo.length();

                paragrafoView.getFlowSpan(0);
                Object dimensoes = obterDimensoes(conteudoView);
                Object linhas = linhasDeDimensoes(dimensoes);
                List<List<String>> linhasManuais = listaDeLinhasPorParagrafo(linhas);
                List<Element> paragrafos = paragrafosComId(documento, id);
                assertEquals(1, paragrafos.size(),
                        "Mensagem longa do diagnóstico deve ter um único parágrafo.");
                List<LinhaRow> rows = coletarRows(paragrafoView, documento);
                List<String> linhasRows = new java.util.ArrayList<>();
                int quantidadeRowsCabecalho = 0;
                int quantidadeRowsConteudo = 0;
                boolean rowMistaCabecalhoConteudo = false;
                for (LinhaRow row : rows) {
                    int inicioCabecalho = Math.max(row.inicio(), paragrafo.getStartOffset());
                    int fimCabecalho = Math.min(row.fim(), inicioConteudo);
                    int inicioLinhaConteudo = Math.max(row.inicio(), inicioConteudo);
                    int fimLinhaConteudo = Math.min(row.fim(), fimConteudo);
                    boolean possuiCabecalho = inicioCabecalho < fimCabecalho;
                    boolean possuiConteudo = inicioLinhaConteudo < fimLinhaConteudo;
                    if (possuiCabecalho) {
                        quantidadeRowsCabecalho++;
                    }
                    if (possuiConteudo) {
                        quantidadeRowsConteudo++;
                        linhasRows.add(documento.getText(inicioLinhaConteudo,
                                fimLinhaConteudo - inicioLinhaConteudo)
                                .replace("\r", "").replace("\n", ""));
                    }
                    rowMistaCabecalhoConteudo |= possuiCabecalho && possuiConteudo;
                }

                String reconstruido = reconstruirTexto(
                        intersecoesComIntervalo(rows, documento, inicioConteudo, fimConteudo));
                assertEquals(normalizarTexto(conteudo), reconstruido,
                        diagnosticoConteudo(largura, id, linhasManuais,
                                rows, conteudo, reconstruido));
                Element paragrafoFinal = documento.getDefaultRootElement().getElement(
                        documento.getDefaultRootElement().getElementCount() - 1);
                assertNull(atributo(paragrafoFinal.getAttributes(), "ATRIBUTO_ID_MENSAGEM"));

                List<String> linhasManuaisDoParagrafo = linhasManuais.get(0);
                resultado.set(new DiagnosticoElegibilidade(
                        trecho, largura, StyleConstants.getAlignment(
                                paragrafo.getAttributes()), quantidadeRowsCabecalho,
                        quantidadeRowsConteudo, rowMistaCabecalhoConteudo,
                        linhasManuaisDoParagrafo, linhasRows,
                        linhasManuaisDoParagrafo.equals(linhasRows),
                        reconstruido.equals(normalizarTexto(conteudo))));
            } catch (ReflectiveOperationException | BadLocationException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return resultado.get();
    }

    private static List<Element> paragrafosComId(StyledDocument documento, Object id) {
        List<Element> resultado = new java.util.ArrayList<>();
        Element raiz = documento.getDefaultRootElement();
        for (int indice = 0; indice < raiz.getElementCount(); indice++) {
            Element paragrafo = raiz.getElement(indice);
            if (id.equals(atributo(paragrafo.getAttributes(), "ATRIBUTO_ID_MENSAGEM"))) {
                resultado.add(paragrafo);
            }
        }
        return resultado;
    }

    private static List<LinhaRow> coletarRows(View view, StyledDocument documento)
            throws BadLocationException {
        List<LinhaRow> resultado = new java.util.ArrayList<>();
        coletarRows(view, documento, resultado);
        return resultado;
    }

    private static List<LinhaRow> intersecoesComIntervalo(List<LinhaRow> rows,
            StyledDocument documento, int inicioConteudo, int fimConteudo)
            throws BadLocationException {
        List<LinhaRow> resultado = new java.util.ArrayList<>();
        for (LinhaRow row : rows) {
            int inicio = Math.max(row.inicio(), inicioConteudo);
            int fim = Math.min(row.fim(), fimConteudo);
            if (inicio < fim) {
                String texto = documento.getText(inicio, fim - inicio);
                resultado.add(new LinhaRow(inicio, fim, texto));
            }
        }
        return resultado;
    }

    private static String reconstruirTexto(List<LinhaRow> rows) {
        StringBuilder texto = new StringBuilder();
        for (LinhaRow row : rows) {
            texto.append(normalizarTexto(row.texto()));
        }
        return texto.toString();
    }

    private static String normalizarTexto(String texto) {
        return texto.replace("\r", "").replace("\n", "");
    }

    private static String diagnosticoConteudo(int largura, Object id,
            List<List<String>> linhasManuais, List<LinhaRow> rows,
            String esperado, String reconstruido) {
        return "Divergência no conteúdo reconstruído | largura=" + largura
                + " | uuid=" + id
                + " | esperado=" + normalizarTexto(esperado)
                + " | reconstruído=" + reconstruido
                + " | linhasManuais=" + linhasManuais
                + " | rowsConteudo=" + rows;
    }

    private static void coletarRows(View view, StyledDocument documento,
            List<LinhaRow> resultado) throws BadLocationException {
        if (view == null) {
            return;
        }
        if ("Row".equals(view.getClass().getSimpleName())) {
            int inicio = view.getStartOffset();
            int fim = view.getEndOffset();
            String texto = documento.getText(inicio, fim - inicio)
                    .replace("\r", "")
                    .replace("\n", "");
            resultado.add(new LinhaRow(inicio, fim, texto));
            return;
        }
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            coletarRows(view.getView(indice), documento, resultado);
        }
    }

    private static String diagnostico(int largura, Object id, int indice,
            List<String> esperadas, List<LinhaRow> rows, String motivo) {
        return "Divergência de " + motivo + " | largura=" + largura
                + " | uuid=" + id + " | parágrafo=" + indice
                + " | esperadas=" + esperadas
                + " | rows=" + rows;
    }

    private static ResultadoPintura pintarPainel(JTextPane area, int largura, int altura)
            throws Exception {
        AtomicReference<ResultadoPintura> resultado = new AtomicReference<>();
        executarNaEdt(() -> {
            forcarLayout(area, largura, altura);
            StyledDocument documento = area.getStyledDocument();
            View raiz = area.getUI().getRootView(area);
            View propria = encontrarView(raiz,
                    area.getText().indexOf("mensagem própria de teste"));
            View recebida = encontrarView(raiz,
                    area.getText().indexOf("mensagem recebida longa"));
            Element elementoFinal = documento.getDefaultRootElement().getElement(
                    documento.getDefaultRootElement().getElementCount() - 1);
            View paragrafoFinal = encontrarViewDoElemento(raiz, elementoFinal);
            ParagraphView paragrafoPropria = encontrarMensagemParagraphView(propria);
            ParagraphView paragrafoRecebida = encontrarMensagemParagraphView(recebida);
            if (propria == null || recebida == null || paragrafoPropria == null
                    || paragrafoRecebida == null || paragrafoFinal == null) {
                throw new IllegalStateException("Views necessárias não encontradas.");
            }

            BufferedImage imagem = new BufferedImage(largura, altura,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D grafico = imagem.createGraphics();
            try {
                grafico.setColor(Color.MAGENTA);
                grafico.fillRect(0, 0, largura, altura);
                grafico.setClip(new Rectangle(0, 0, largura, altura));
                raiz.paint(grafico, new Rectangle(0, 0, largura, altura));
            } finally {
                grafico.dispose();
            }

            Rectangle alocacaoPropria = alocacaoDaView(raiz, paragrafoPropria,
                    new Rectangle(0, 0, largura, altura));
            Rectangle alocacaoRecebida = alocacaoDaView(raiz, paragrafoRecebida,
                    new Rectangle(0, 0, largura, altura));
            Rectangle alocacaoFinal = alocacaoDaView(raiz, paragrafoFinal,
                    new Rectangle(0, 0, largura, altura));
            if (alocacaoPropria == null || alocacaoRecebida == null
                    || alocacaoFinal == null) {
                throw new IllegalStateException("Alocações das Views não encontradas.");
            }
            resultado.set(new ResultadoPintura(
                    pixelsDaCor(imagem, alocacaoPropria, new Color(0x3B, 0x82, 0xF6)),
                    pixelsDaCor(imagem, alocacaoRecebida, new Color(0x1E, 0x33, 0x52)),
                    pixelsDaCor(imagem, alocacaoFinal, new Color(0x3B, 0x82, 0xF6)),
                    contarViews(propria, "MensagemConteudoView"),
                    contarViews(recebida, "MensagemConteudoView"),
                    contarViews(paragrafoRecebida, "Row"),
                    recebida.getPreferredSpan(View.Y_AXIS),
                    area.getText(),
                    documento.getDefaultRootElement().getElementCount(),
                    metadadosValidos(propria) && metadadosValidos(recebida)));
        });
        return resultado.get();
    }

    private static void pintarDocumento(JTextPane area, int largura, int altura)
            throws Exception {
        AtomicReference<Throwable> falha = new AtomicReference<>();
        executarNaEdt(() -> {
            try {
                forcarLayout(area, largura, altura);
                BufferedImage imagem = new BufferedImage(largura, altura,
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D grafico = imagem.createGraphics();
                try {
                    grafico.setClip(new Rectangle(0, 0, largura, altura));
                    area.getUI().getRootView(area).paint(grafico,
                            new Rectangle(0, 0, largura, altura));
                } finally {
                    grafico.dispose();
                }
            } catch (Throwable exception) {
                falha.set(exception);
            }
        });
        if (falha.get() != null) {
            throw new IllegalStateException("Falha ao pintar o documento.", falha.get());
        }
    }

    private static List<Object> idsDosParagrafos(StyledDocument documento) {
        List<Object> ids = new java.util.ArrayList<>();
        Element raiz = documento.getDefaultRootElement();
        for (int indice = 0; indice < raiz.getElementCount(); indice++) {
            Object id = atributo(raiz.getElement(indice).getAttributes(),
                    "ATRIBUTO_ID_MENSAGEM");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static void forcarLayout(JTextPane area, int largura, int altura) {
        area.setSize(new Dimension(largura, altura));
        area.revalidate();
        area.doLayout();
        area.getPreferredSize();
        View raiz = area.getUI().getRootView(area);
        raiz.setSize(largura, altura);
        raiz.getPreferredSpan(View.Y_AXIS);
    }

    private static ParagraphView encontrarMensagemParagraphView(View view) {
        for (View atual = view; atual != null; atual = atual.getParent()) {
            if (atual instanceof ParagraphView
                    && "MensagemParagraphView".equals(atual.getClass().getSimpleName())) {
                return (ParagraphView) atual;
            }
        }
        return null;
    }

    private static float alturaDaRow(ParagraphView paragrafo, int indice) {
        return paragrafo.getView(indice).getPreferredSpan(View.Y_AXIS);
    }

    private static Rectangle alocacaoDaView(View atual, View alvo, Rectangle alocacao) {
        if (atual == alvo) {
            return alocacao;
        }
        for (int indice = 0; indice < atual.getViewCount(); indice++) {
            Shape forma = atual.getChildAllocation(indice, alocacao);
            if (forma != null) {
                Rectangle encontrada = alocacaoDaView(atual.getView(indice), alvo,
                        forma.getBounds());
                if (encontrada != null) {
                    return encontrada;
                }
            }
        }
        return null;
    }

    private static ResultadoPixels pixelsDaCor(BufferedImage imagem, Rectangle area,
            Color cor) {
        if (area == null) {
            return null;
        }
        int inicioX = Math.max(0, area.x);
        int fimX = Math.min(imagem.getWidth(), area.x + area.width);
        int inicioY = Math.max(0, area.y);
        int fimY = Math.min(imagem.getHeight(), area.y + area.height);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int rgb = cor.getRGB();
        for (int y = inicioY; y < fimY; y++) {
            for (int x = inicioX; x < fimX; x++) {
                if (imagem.getRGB(x, y) == rgb) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (minX == Integer.MAX_VALUE) {
            return null;
        }
        return new ResultadoPixels(cor, minX, minY, maxX, maxY);
    }

    private static int contarViews(View view, String nomeSimples) {
        int quantidade = nomeSimples.equals(view.getClass().getSimpleName()) ? 1 : 0;
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            quantidade += contarViews(view.getView(indice), nomeSimples);
        }
        return quantidade;
    }

    private static boolean metadadosValidos(View view) {
        if ("MensagemConteudoView".equals(view.getClass().getSimpleName())) {
            try {
                Object dados = obterDadosModelo(view);
                return modeloParte(dados, "cabecalho") != null
                        && modeloParte(dados, "conteudo") != null;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            if (!metadadosValidos(view.getView(indice))) {
                return false;
            }
        }
        return true;
    }

    private static boolean botaoHabilitado(ClienteChatFrame frame, String nomeCampo)
            throws Exception {
        AtomicReference<Boolean> habilitado = new AtomicReference<>();
        executarNaEdt(() -> habilitado.set(campo(frame, nomeCampo, JButton.class).isEnabled()));
        return habilitado.get();
    }

    private static String texto(ClienteChatFrame frame, String nomeCampo) throws Exception {
        AtomicReference<String> texto = new AtomicReference<>();
        executarNaEdt(() -> {
            Object componente = campo(frame, nomeCampo);
            if (componente instanceof JTextComponent campoTexto) {
                texto.set(campoTexto.getText());
            } else {
                texto.set(((javax.swing.JLabel) componente).getText());
            }
        });
        return texto.get();
    }

    private static int contar(String texto, String trecho) {
        int quantidade = 0;
        int indice = 0;
        while ((indice = texto.indexOf(trecho, indice)) >= 0) {
            quantidade++;
            indice += trecho.length();
        }
        return quantidade;
    }

    private static Object identificarPartes(String texto, String remetente)
            throws Exception {
        Method metodo = ClienteChatFrame.class.getDeclaredMethod(
                "identificarCabecalhoEConteudo", String.class, String.class);
        metodo.setAccessible(true);
        return metodo.invoke(null, texto, remetente);
    }

    private static void adicionarMensagem(ClienteChatFrame frame, Mensagem mensagem,
            boolean propria) throws Exception {
        executarNaEdt(() -> {
            try {
                Method metodo = ClienteChatFrame.class.getDeclaredMethod(
                        "exibirMensagem", Mensagem.class, boolean.class);
                metodo.setAccessible(true);
                metodo.invoke(frame, mensagem, propria);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static AttributeSet atributos(StyledDocument documento, int indice) {
        return documento.getCharacterElement(indice).getAttributes();
    }

    private static Object atributo(StyledDocument documento, int indice, String nomeCampo) {
        return atributo(atributos(documento, indice), nomeCampo);
    }

    private static Object atributo(AttributeSet atributos, String nomeCampo) {
        try {
            Field campo = ClienteChatFrame.class.getDeclaredField(nomeCampo);
            campo.setAccessible(true);
            return atributos.getAttribute(campo.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Atributo não encontrado: " + nomeCampo, exception);
        }
    }

    private static View encontrarView(View view, int offset) {
        Element elemento = view.getElement();
        if (elemento != null
                && AbstractDocument.ContentElementName.equals(elemento.getName())
                && offset >= elemento.getStartOffset()
                && offset < elemento.getEndOffset()) {
            return view;
        }
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            View encontrada = encontrarView(view.getView(indice), offset);
            if (encontrada != null) {
                return encontrada;
            }
        }
        return null;
    }

    private static View encontrarViewDoElemento(View view, Element elementoAlvo) {
        if (view.getElement() == elementoAlvo) {
            return view;
        }
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            View encontrada = encontrarViewDoElemento(view.getView(indice), elementoAlvo);
            if (encontrada != null) {
                return encontrada;
            }
        }
        return null;
    }

    private static View encontrarMensagemConteudoView(View view, Element elementoAlvo) {
        if (view == null) {
            return null;
        }
        if ("MensagemConteudoView".equals(view.getClass().getSimpleName())
                && view.getStartOffset() >= elementoAlvo.getStartOffset()
                && view.getEndOffset() <= elementoAlvo.getEndOffset()) {
            return view;
        }
        for (int indice = 0; indice < view.getViewCount(); indice++) {
            View encontrada = encontrarMensagemConteudoView(view.getView(indice), elementoAlvo);
            if (encontrada != null) {
                return encontrada;
            }
        }
        return null;
    }

    private static List<View> encontrarViewsComId(View view, Object id) throws Exception {
        List<View> resultado = new java.util.ArrayList<>();
        if (view != null && "MensagemConteudoView".equals(view.getClass().getSimpleName())
                && id.equals(atributo(view.getElement().getAttributes(),
                        "ATRIBUTO_ID_MENSAGEM"))) {
            resultado.add(view);
        }
        if (view != null) {
            for (int indice = 0; indice < view.getViewCount(); indice++) {
                resultado.addAll(encontrarViewsComId(view.getView(indice), id));
            }
        }
        return resultado;
    }

    private static List<Float> medirAlturas(List<View> views, int largura)
            throws ReflectiveOperationException {
        List<Float> alturas = new java.util.ArrayList<>();
        for (View view : views) {
            atualizarDimensoes(view, largura);
            alturas.add(view.getPreferredSpan(View.Y_AXIS));
        }
        return alturas;
    }

    private static float soma(List<Float> alturas) {
        float soma = 0;
        for (float altura : alturas) {
            soma += altura;
        }
        return soma;
    }

    private static Object obterDadosModelo(View view) throws ReflectiveOperationException {
        Method metodo = view.getClass().getDeclaredMethod("obterDadosMensagem");
        metodo.setAccessible(true);
        return metodo.invoke(view);
    }

    private static Object calcularDimensoes(View view, int largura)
            throws ReflectiveOperationException {
        Method metodo = view.getClass().getDeclaredMethod("calcularDimensoes", float.class);
        metodo.setAccessible(true);
        return metodo.invoke(view, (float) largura);
    }

    private static void atualizarDimensoes(View view, int largura) {
        try {
            Method metodo = view.getClass().getDeclaredMethod(
                    "atualizarDimensoesDoFluxo", float.class);
            metodo.setAccessible(true);
            metodo.invoke(view, (float) largura);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object obterDimensoes(View view) throws ReflectiveOperationException {
        Method metodo = view.getClass().getDeclaredMethod("obterDimensoesDoFluxo");
        metodo.setAccessible(true);
        return metodo.invoke(view);
    }

    private static int larguraDoCache(View view) {
        try {
            Field campo = view.getClass().getDeclaredField("larguraDasDimensoesDoFluxo");
            campo.setAccessible(true);
            return campo.getInt(view);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object dimensoesDoFluxo(View view)
            throws ReflectiveOperationException {
        if (view.getParent() instanceof ParagraphView paragrafo) {
            paragrafo.getFlowSpan(0);
        }
        Method metodo = view.getClass().getDeclaredMethod("obterDimensoesDoFluxo");
        metodo.setAccessible(true);
        return metodo.invoke(view);
    }

    private static int dimensao(Object dimensoes, String nome)
            throws ReflectiveOperationException {
        Method metodo = dimensoes.getClass().getDeclaredMethod(nome);
        metodo.setAccessible(true);
        return (int) metodo.invoke(dimensoes);
    }

    private static Object modeloParte(Object modelo, String nome)
            throws ReflectiveOperationException {
        Method metodo = modelo.getClass().getDeclaredMethod(nome);
        metodo.setAccessible(true);
        return metodo.invoke(modelo);
    }

    private record ResultadoMetadados(String textoAtual, String textoEsperado,
            AttributeSet propria, AttributeSet recebida, int quantidadeParagrafos) {
    }

    private record ResultadoViews(View propria, View recebida, String texto,
            int quantidadeParagrafos) {
    }

    private record ResultadoModelos(Object propria, Object recebida, String texto) {
    }

    private record ResultadoIdentidades(String texto, Object primeiroId,
            Object primeiroIdQuebra, Object segundoId, AttributeSet atributosParagrafo,
            AttributeSet atributosParagrafoFinal,
            int quantidadeParagrafos) {
    }

    private record ResultadoGeometriaCompartilhada(int quantidadeViews,
            float somaAlturasEstreitas, float somaAlturasAmplas,
            float alturaTotalEstreita, float alturaTotalAmpla, String texto,
            int quantidadeParagrafos) {
    }

    private record ResultadoDimensoes(Object largo, Object estreito, String texto) {
    }

    private record ResultadoFluxo(Object curta, Object longa, String texto) {
    }

    private record ResultadoRedimensionamento(int larguraFluxo, int larguraCache,
            float altura, int linhasConteudo, int alinhamentoPropria,
            int alinhamentoRecebida, float alturaParagrafoFinal,
            int larguraCacheParagrafoFinal, String texto, int quantidadeParagrafos) {
    }

    private record ResultadoPixels(Color cor, int minX, int minY, int maxX, int maxY) {
    }

    private record ResultadoPintura(ResultadoPixels corPropria,
            ResultadoPixels corRecebida, ResultadoPixels corParagrafoFinal,
            int viewsPropria, int viewsRecebida, int linhasRecebida,
            float alturaRecebida, String texto, int quantidadeParagrafos,
            boolean metadadosValidos) {
    }

    private record DiagnosticoReservaAltura(List<LinhaRow> rows,
            List<Float> alturasRows, float alturaTotalParagrafo,
            int alturaEstimadaCabecalho) {
    }

    private record DiagnosticoElegibilidade(String trecho, int largura,
            int alinhamento, int quantidadeRowsCabecalho, int quantidadeRowsConteudo,
            boolean rowMistaCabecalhoConteudo, List<String> linhasManuais,
            List<String> linhasRows, boolean quebrasExatas,
            boolean conteudoReconstruidoCompleto) {
    }

    private record LinhaRow(int inicio, int fim, String texto) {
    }

    private record LinhasCalculadas(List<String> cabecalho, List<String> conteudo,
            int linhasCabecalho, int linhasConteudo, int alturaCabecalho,
            int alturaConteudo, int alturaFonteCabecalho, int alturaFonteConteudo,
            int larguraFluxo, String cabecalhoOriginal, String conteudoOriginal,
            String textoDocumento, int quantidadeParagrafos) {
    }

    private static String parte(Object partes, String nome) throws Exception {
        Method metodo = partes.getClass().getDeclaredMethod(nome);
        metodo.setAccessible(true);
        return (String) metodo.invoke(partes);
    }

    private static int alinhamentoDoTrecho(ClienteChatFrame frame, String trecho)
            throws Exception {
        AtomicReference<Integer> alinhamento = new AtomicReference<>();
        executarNaEdt(() -> {
            JTextPane area = campo(frame, "areaHistorico", JTextPane.class);
            alinhamento.set(alinhamentoDoTrecho(area, trecho));
        });
        return alinhamento.get();
    }

    private static int alinhamentoDoTrecho(JTextPane area, String trecho) {
        int inicio = area.getText().indexOf(trecho);
        assertTrue(inicio >= 0, "Trecho não encontrado no histórico: " + trecho);
        Element paragrafo = area.getStyledDocument().getParagraphElement(inicio);
        return StyleConstants.getAlignment(paragrafo.getAttributes());
    }

    private static void executarNaEdt(Runnable tarefa) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            tarefa.run();
        } else {
            SwingUtilities.invokeAndWait(tarefa);
        }
    }

    private static void aguardar(Condicao condicao) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.yield();
        }
        assertTrue(condicao.getAsBoolean(), "Condição não atingida dentro do limite.");
    }

    @FunctionalInterface
    private interface Condicao {
        boolean getAsBoolean() throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static <T> T campo(Object objeto, String nome, Class<T> tipo) {
        return tipo.cast(campo(objeto, nome));
    }

    private static Object campo(Object objeto, String nome) {
        try {
            Field campo = objeto.getClass().getDeclaredField(nome);
            campo.setAccessible(true);
            return campo.get(objeto);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Campo não encontrado: " + nome, exception);
        }
    }

    private static Timer fecharDialogosAutomaticamente() throws Exception {
        AtomicReference<Timer> referencia = new AtomicReference<>();
        executarNaEdt(() -> {
            Timer timer = new Timer(25, evento -> {
                for (Window janela : Window.getWindows()) {
                    if (janela instanceof Dialog dialogo && dialogo.isShowing()) {
                        dialogo.dispose();
                    }
                }
            });
            timer.start();
            referencia.set(timer);
        });
        return referencia.get();
    }

    private static Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String banco = "frame_teste_" + UUID.randomUUID().toString().replace('-', '_');
        ConnectionFactory conexoes = ConnectionFactory.emMemoria(banco);
        JdbcMensagemRepository repository = new JdbcMensagemRepository(conexoes);
        ChatService service = new ChatService(new ClientRegistry(), repository);
        JdbcUsuarioRepository usuarios = new JdbcUsuarioRepository(conexoes);
        JdbcUsuarioCredencialRepository credenciais = new JdbcUsuarioCredencialRepository(conexoes);
        AutenticacaoService autenticacao = new AutenticacaoService(
                new JdbcCadastroUsuario(conexoes), usuarios, credenciais, new SenhaHasher());
        for (String nome : List.of("Ana", "Bia", "Caio", "Preparador", "Victor", "Bruno")) {
            autenticacao.cadastrar(nome.toLowerCase(), nome, "senha-teste".toCharArray());
        }
        Path diretorio = Files.createTempDirectory("frame-tls-");
        Path keytool = localizarKeytool();
        MaterialTls material = gerarMaterial(diretorio, keytool);
        ConfiguracaoTls configuracao = criarConfiguracao(material);
        Servidor servidor = new Servidor(porta, service, configuracao, autenticacao);
        AtomicReference<Throwable> falha = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                servidor.iniciar();
            } catch (IOException exception) {
                falha.set(exception);
            }
        }, "servidor-frame-teste");
        thread.start();
        aguardarPorta(porta, falha);
        return new Contexto(servidor, thread, porta, repository, configuracao);
    }

    private static void aguardarPorta(int porta, AtomicReference<Throwable> falha) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < limite) {
            if (falha.get() != null) {
                throw new AssertionError("Servidor não iniciou.", falha.get());
            }
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", porta)) {
                return;
            } catch (IOException exception) {
                Thread.yield();
            }
        }
        throw new IOException("Servidor não iniciou dentro do limite.");
    }

    private static int handlersAtivos(Servidor servidor) throws Exception {
        Field campo = Servidor.class.getDeclaredField("handlersAtivos");
        campo.setAccessible(true);
        return ((java.util.Set<?>) campo.get(servidor)).size();
    }

    private static int encontrarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ConfiguracaoTls criarConfiguracao(MaterialTls material) {
        char[] senhaKeystore = SENHA_TLS.toCharArray();
        char[] senhaTruststore = SENHA_TLS.toCharArray();
        try {
            return new ConfiguracaoTls(material.keystore(), senhaKeystore,
                    material.truststore(), senhaTruststore);
        } finally {
            Arrays.fill(senhaKeystore, '\0');
            Arrays.fill(senhaTruststore, '\0');
        }
    }

    private static MaterialTls gerarMaterial(Path diretorio, Path keytool) throws Exception {
        Path keystore = diretorio.resolve("servidor.p12");
        Path certificado = diretorio.resolve("servidor.crt");
        Path truststore = diretorio.resolve("confianca.p12");
        executarKeytool(keytool, List.of("-genkeypair", "-alias", "servidor", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "1", "-keystore", keystore.toString(),
                "-storetype", "PKCS12", "-storepass", SENHA_TLS, "-keypass", SENHA_TLS,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1"));
        executarKeytool(keytool, List.of("-exportcert", "-alias", "servidor", "-keystore",
                keystore.toString(), "-storetype", "PKCS12", "-storepass", SENHA_TLS,
                "-rfc", "-file", certificado.toString()));
        executarKeytool(keytool, List.of("-importcert", "-alias", "servidor", "-file",
                certificado.toString(), "-keystore", truststore.toString(), "-storetype",
                "PKCS12", "-storepass", SENHA_TLS, "-noprompt"));
        return new MaterialTls(keystore, truststore);
    }

    private static void executarKeytool(Path keytool, List<String> argumentos) throws Exception {
        ProcessBuilder processo = new ProcessBuilder();
        processo.command().add(keytool.toString());
        processo.command().addAll(argumentos);
        processo.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processo.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process iniciado = processo.start();
        try {
            assertTrue(iniciado.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, iniciado.exitValue());
        } finally {
            iniciado.destroyForcibly();
        }
    }

    private static Path localizarKeytool() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String nome = System.getProperty("os.name").toLowerCase().contains("win")
                ? "keytool.exe" : "keytool";
        Path executavel = javaHome.resolve("bin").resolve(nome);
        if (!Files.isExecutable(executavel)) {
            executavel = javaHome.getParent().resolve("bin").resolve(nome);
        }
        if (!Files.isExecutable(executavel)) {
            throw new IllegalStateException("keytool não está disponível.");
        }
        return executavel;
    }

    private record MaterialTls(Path keystore, Path truststore) {
    }

    private record Contexto(Servidor servidor, Thread threadServidor, int porta,
            JdbcMensagemRepository repository, ConfiguracaoTls configuracao) {
        private void fechar() throws Exception {
            servidor.encerrar();
            threadServidor.join(2000);
            assertFalse(threadServidor.isAlive());
        }
    }
}
