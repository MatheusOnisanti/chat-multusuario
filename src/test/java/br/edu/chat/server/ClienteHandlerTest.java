package br.edu.chat.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.CadastroUsuario;
import br.edu.chat.persistence.MensagemRepository;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.persistence.UsuarioCredencialRepository;
import br.edu.chat.service.AutenticacaoService;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;
import br.edu.chat.service.ConexaoCliente;
import br.edu.chat.service.CredencialSenha;
import br.edu.chat.service.SenhaHasher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteHandlerTest {

    @Test
    void encerrarDeveInterromperLeituraBloqueante() throws Exception {
        LeituraBloqueante leitura = new LeituraBloqueante();
        SocketDeTeste socket = new SocketDeTeste(leitura);
        CountDownLatch finalizado = new CountDownLatch(1);
        ClienteHandler handler = novoHandler(socket, ignorado -> finalizado.countDown());
        Thread thread = new Thread(handler);

        try {
            thread.start();
            assertTrue(leitura.leituraIniciada.await(1, TimeUnit.SECONDS));

            handler.encerrar();

            assertTimeoutAndJoin(thread);
            assertTrue(finalizado.await(1, TimeUnit.SECONDS));
        } finally {
            handler.encerrar();
            thread.join(1000);
        }
    }

    @Test
    void socketDeveSerFechadoAposTerminoNormal() {
        SocketDeTeste socket = new SocketDeTeste(new ByteArrayInputStream(new byte[0]));
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        handler.run();

        assertTrue(socket.fechado);
    }

    @Test
    void callbackDeFinalizacaoDeveSerExecutado() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new ByteArrayInputStream(new byte[0]));
        CountDownLatch finalizado = new CountDownLatch(1);
        ClienteHandler handler = novoHandler(socket, ignorado -> finalizado.countDown());

        handler.run();

        assertTrue(finalizado.await(1, TimeUnit.SECONDS));
    }

    @Test
    void callbackDeveSerExecutadoQuandoOcorreIOException() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("falha simulada");
            }
        });
        CountDownLatch finalizado = new CountDownLatch(1);
        ClienteHandler handler = novoHandler(socket, ignorado -> finalizado.countDown());

        handler.run();

        assertTrue(finalizado.await(1, TimeUnit.SECONDS));
        assertTrue(socket.fechado);
    }

    @Test
    void bytesRecebidosDevemSerRejeitadosSemProtocoloLegado() throws IOException {
        byte[] bytes = "dados sem protocolo".getBytes(StandardCharsets.UTF_8);
        SocketDeTeste socket = new SocketDeTeste(new ByteArrayInputStream(bytes));
        AtomicInteger chamadasCallback = new AtomicInteger();
        ClienteHandler handler = novoHandler(socket, ignorado -> chamadasCallback.incrementAndGet());

        handler.run();

        assertResposta(socket, RespostaAutenticacao.Codigo.REQUISICAO_INVALIDA);
        assertEquals(1, chamadasCallback.get());
        assertTrue(socket.fechado);
    }

    @Test
    void mensagemAntesDoLoginDeveSerRejeitadaSemRegistrarCliente() {
        ClientRegistry registro = new ClientRegistry();
        SocketDeTeste socket = socketComando(comando("MENSAGEM", "antes do login"));
        ClienteHandler handler = novoHandler(socket, new ChatService(registro), ignorado -> { });

        handler.run();

        assertEquals(0, registro.snapshot().size());
        assertResposta(socket, RespostaAutenticacao.Codigo.NAO_AUTENTICADO);
    }

    @Test
    void cadastroBemSucedidoDeveAutenticarEEnviarHistorico() {
        Mensagem antiga = new Mensagem(new Usuario("legado"), "histórico", LocalDateTime.now());
        ClientRegistry registro = new ClientRegistry();
        MensagemRepository historico = new RepositorioFalso(antiga);
        SocketDeTeste socket = socketComando(
                comando("CADASTRAR", "novo", "Novo", "senha-nova"));
        ClienteHandler handler = novoHandler(socket,
                new ChatService(registro, historico), ignorado -> { });

        handler.run();

        String resposta = socket.saida.toString(StandardCharsets.UTF_8);
        String respostaNormalizada = normalizarQuebraDeLinha(resposta);
        String sucesso = "OK|CADASTRADO\n";
        assertTrue(respostaNormalizada.startsWith(sucesso));
        assertTrue(respostaNormalizada.indexOf(sucesso)
                < respostaNormalizada.indexOf("|hist\u00F3rico"));
        assertTrue(resposta.contains("|histórico"));
        assertEquals(0, registro.snapshot().size());
    }

    @Test
    void autenticacaoValidaDeveLiberarHistorico() {
        Mensagem antiga = new Mensagem(new Usuario("legado"), "histórico", LocalDateTime.now());
        ClientRegistry registro = new ClientRegistry();
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR", "ana", "senha"));
        ClienteHandler handler = novoHandler(socket,
                new ChatService(registro, new RepositorioFalso(antiga)), ignorado -> { });

        handler.run();

        String resposta = socket.saida.toString(StandardCharsets.UTF_8);
        String respostaNormalizada = normalizarQuebraDeLinha(resposta);
        String sucesso = "OK|AUTENTICADO\n";
        assertTrue(respostaNormalizada.startsWith(sucesso));
        assertTrue(respostaNormalizada.indexOf(sucesso)
                < respostaNormalizada.indexOf("|hist\u00F3rico"));
        assertTrue(resposta.contains("|histórico"));
    }

    @Test
    void autenticacaoV2DeveRetornarNomeCanonico() {
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "ana", "senha"));
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        handler.run();

        RespostaAutenticacao interpretada = RespostaAutenticacao.interpretarVersionada(
                normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8)).trim());
        assertEquals(RespostaAutenticacao.Codigo.AUTENTICADO, interpretada.getCodigo());
        assertEquals("Ana", interpretada.getNomeAutenticado().orElseThrow());
    }

    @Test
    void cadastroV2DeveRetornarNomeCadastrado() {
        String nome = "Novo \u00e7\u00e3o";
        SocketDeTeste socket = socketComando(
                comando("CADASTRAR_V2", "novo", nome, "senha-nova"));
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        handler.run();

        RespostaAutenticacao interpretada = RespostaAutenticacao.interpretarVersionada(
                normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8)).trim());
        assertEquals(RespostaAutenticacao.Codigo.CADASTRADO, interpretada.getCodigo());
        assertEquals(nome, interpretada.getNomeAutenticado().orElseThrow());
    }

    @Test
    void credencialIncorretaNaoDeveAutenticar() {
        ClientRegistry registro = new ClientRegistry();
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR", "ana", "incorreta"));
        ClienteHandler handler = novoHandler(socket, new ChatService(registro), ignorado -> { });

        handler.run();

        assertEquals(0, registro.snapshot().size());
        assertResposta(socket, RespostaAutenticacao.Codigo.AUTENTICACAO);
    }

    @Test
    void credencialIncorretaV2NaoDeveRetornarNome() {
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "ana", "incorreta"));
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        try (CapturadorDeLogs logs = capturarLogs(ClienteHandler.class.getName())) {
            handler.run();

            assertResposta(socket, RespostaAutenticacao.Codigo.AUTENTICACAO);
            assertTrue(logs.contem(ClienteHandler.AUTH_CREDENTIALS_REJECTED));
            assertFalse(logs.contem("incorreta"));
        }
    }

    @Test
    void loginInexistenteV2NaoDeveRetornarNomeNemDetalhes() {
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "login-inexistente", "senha"));
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        try (CapturadorDeLogs logs = capturarLogs(ClienteHandler.class.getName())) {
            handler.run();

            String resposta = normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8));
            assertResposta(socket, RespostaAutenticacao.Codigo.AUTENTICACAO);
            assertTrue(logs.contem(ClienteHandler.AUTH_CREDENTIALS_REJECTED));
            assertFalse(logs.contem("login-inexistente"));
            assertFalse(logs.contem("senha"));
            assertFalse(resposta.contains("login-inexistente"));
            assertFalse(resposta.contains("senha"));
        }
    }

    @Test
    void credencialAusenteV2DeveSerRejeitadaSemDetalhes() {
        Map<String, Usuario> usuarios = new HashMap<>();
        Map<UUID, CredencialSenha> credenciais = new HashMap<>();
        Usuario usuario = new Usuario(UUID.randomUUID(), "sem-credencial", "Sem Credencial");
        usuarios.put(usuario.getLogin(), usuario);
        AutenticacaoService autenticacao = new AutenticacaoService(
                (ignorado, credencial) -> { },
                new UsuariosFalsos(usuarios), new CredenciaisFalsas(credenciais), new SenhaHasher());
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "sem-credencial", "senha"));
        ClienteHandler handler = novoHandler(socket, new ChatService(), autenticacao,
                ignorado -> { });

        try (CapturadorDeLogs logs = capturarLogs(ClienteHandler.class.getName())) {
            handler.run();

            assertResposta(socket, RespostaAutenticacao.Codigo.AUTENTICACAO);
            assertTrue(logs.contem(ClienteHandler.AUTH_CREDENTIALS_REJECTED));
            String resposta = normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8));
            assertFalse(logs.contem("sem-credencial"));
            assertFalse(logs.contem("senha"));
            assertFalse(resposta.contains("Sem Credencial"));
            assertFalse(resposta.contains("senha"));
        }
    }

    @Test
    void excecaoInesperadaNoRepositorioNaoDeveExporDetalhes() {
        String detalheInterno = "falha-interna-nao-publica";
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "ana", "senha"));
        AutenticacaoService autenticacao = new AutenticacaoService(
                (ignorado, credencial) -> { },
                new UsuariosFalsosComFalha(new IllegalStateException(detalheInterno)),
                new CredenciaisFalsas(new HashMap<>()), new SenhaHasher());
        ClienteHandler handler = novoHandler(socket, new ChatService(), autenticacao,
                ignorado -> { });

        try (CapturadorDeLogs logs = capturarLogs(ClienteHandler.class.getName())) {
            handler.run();

            assertResposta(socket, RespostaAutenticacao.Codigo.AUTENTICACAO);
            assertTrue(logs.contem(ClienteHandler.AUTH_INTERNAL_FAILURE));
            String resposta = normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8));
            assertFalse(logs.contem(detalheInterno));
            assertFalse(logs.contem("IllegalStateException"));
            assertFalse(resposta.contains(detalheInterno));
            assertFalse(resposta.contains("IllegalStateException"));
        }
    }

    @Test
    void falhaNoHistoricoAposSucessoNaoDeveExporDetalhes() {
        ClientRegistry registro = new ClientRegistry();
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "ana", "senha"));
        ClienteHandler handler = novoHandler(socket,
                new ChatService(registro, new RepositorioHistoricoFalho()), ignorado -> { });

        try (CapturadorDeLogs logs = capturarLogs(ChatService.class.getName())) {
            handler.run();

            String resposta = normalizarQuebraDeLinha(socket.saida.toString(StandardCharsets.UTF_8));
            RespostaAutenticacao interpretada = RespostaAutenticacao.interpretarVersionada(
                    resposta.trim());
            assertEquals(RespostaAutenticacao.Codigo.AUTENTICADO, interpretada.getCodigo());
            assertEquals("Ana", interpretada.getNomeAutenticado().orElseThrow());
            assertTrue(logs.contem("HISTORY_INTERNAL_FAILURE"));
            assertFalse(logs.contem("historico-interno-nao-publico"));
            assertFalse(logs.contem("PersistenciaException"));
            assertFalse(resposta.contains("historico-interno-nao-publico"));
            assertFalse(resposta.contains("PersistenciaException"));
        }
    }

    @Test
    void remetenteDeveSerAIdentidadeAutenticada() {
        ClientRegistry registro = new ClientRegistry();
        ConexaoFalsa observador = new ConexaoFalsa();
        registro.registrar(observador);
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR", "ana", "senha") + "\n"
                        + comando("MENSAGEM", "mensagem autenticada"));
        ClienteHandler handler = novoHandler(socket, new ChatService(registro), ignorado -> { });

        handler.run();

        assertEquals(1, observador.mensagens.size());
        assertEquals("ana", observador.mensagens.get(0).getRemetente().getLogin());
        assertEquals("Ana", observador.mensagens.get(0).getRemetente().getNome());
    }

    @Test
    void remetenteV2DeveSerAIdentidadeAutenticada() {
        ClientRegistry registro = new ClientRegistry();
        ConexaoFalsa observador = new ConexaoFalsa();
        registro.registrar(observador);
        SocketDeTeste socket = socketComando(
                comando("AUTENTICAR_V2", "ana", "senha") + "\n"
                        + comando("MENSAGEM", "mensagem autenticada v2"));
        ClienteHandler handler = novoHandler(socket, new ChatService(registro), ignorado -> { });

        handler.run();

        assertEquals(1, observador.mensagens.size());
        assertEquals("ana", observador.mensagens.get(0).getRemetente().getLogin());
        assertEquals("Ana", observador.mensagens.get(0).getRemetente().getNome());
    }

    @Test
    void comandoMalformadoDeveReceberRespostaGenerica() {
        SocketDeTeste socket = socketComando("DESCONHECIDO|x");
        ClienteHandler handler = novoHandler(socket, ignorado -> { });

        handler.run();

        assertResposta(socket, RespostaAutenticacao.Codigo.REQUISICAO_INVALIDA);
    }

    private static void assertResposta(SocketDeTeste socket,
            RespostaAutenticacao.Codigo codigo) {
        String resposta = normalizarQuebraDeLinha(
                socket.saida.toString(StandardCharsets.UTF_8));
        assertEquals(RespostaAutenticacao.de(codigo).serializar() + "\n", resposta);
    }

    private static String normalizarQuebraDeLinha(String texto) {
        return texto.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static ClienteHandler novoHandler(SocketDeTeste socket,
            ConsumerDeFinalizacao callback) {
        return novoHandler(socket, new ChatService(), callback);
    }

    private static ClienteHandler novoHandler(SocketDeTeste socket, ChatService chatService,
            ConsumerDeFinalizacao callback) {
        return new ClienteHandler(socket, chatService, autenticacao(), callback::aceitar);
    }

    private static ClienteHandler novoHandler(SocketDeTeste socket, ChatService chatService,
            AutenticacaoService autenticacao, ConsumerDeFinalizacao callback) {
        return new ClienteHandler(socket, chatService, autenticacao, callback::aceitar);
    }

    private static AutenticacaoService autenticacao() {
        Map<String, Usuario> usuarios = new HashMap<>();
        Map<UUID, CredencialSenha> credenciais = new HashMap<>();
        Usuario ana = new Usuario(UUID.randomUUID(), "ana", "Ana");
        usuarios.put(ana.getLogin(), ana);
        credenciais.put(ana.getId(), new SenhaHasher().gerar("senha".toCharArray()));

        CadastroUsuario cadastro = (usuario, credencial) -> {
            usuarios.put(usuario.getLogin(), usuario);
            credenciais.put(usuario.getId(), credencial);
        };
        return new AutenticacaoService(cadastro,
                new UsuariosFalsos(usuarios), new CredenciaisFalsas(credenciais), new SenhaHasher());
    }

    private static SocketDeTeste socketComando(String comando) {
        return new SocketDeTeste(new ByteArrayInputStream(
                (comando + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static String comando(String tipo, String... valores) {
        StringBuilder resultado = new StringBuilder(tipo);
        for (String valor : valores) {
            resultado.append('|').append(Base64.getEncoder().encodeToString(
                    valor.getBytes(StandardCharsets.UTF_8)));
        }
        return resultado.toString();
    }

    private static void assertTimeoutAndJoin(Thread thread) {
        org.junit.jupiter.api.Assertions.assertTimeout(
                Duration.ofSeconds(2), () -> thread.join(1000));
        assertFalse(thread.isAlive());
    }

    private static CapturadorDeLogs capturarLogs(String nomeLogger) {
        return new CapturadorDeLogs(Logger.getLogger(nomeLogger));
    }

    private static final class CapturadorDeLogs extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level nivelAnterior;
        private final boolean usarPaisAnterior;
        private final java.util.List<String> mensagens = new java.util.ArrayList<>();

        private CapturadorDeLogs(Logger logger) {
            this.logger = logger;
            this.nivelAnterior = logger.getLevel();
            this.usarPaisAnterior = logger.getUseParentHandlers();
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                mensagens.add(String.valueOf(record.getMessage()));
            }
        }

        private boolean contem(String texto) {
            return mensagens.stream().anyMatch(mensagem -> mensagem.contains(texto));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(nivelAnterior);
            logger.setUseParentHandlers(usarPaisAnterior);
        }
    }

    @FunctionalInterface
    private interface ConsumerDeFinalizacao {
        void aceitar(ClienteHandler handler);
    }

    private static final class UsuariosFalsos implements br.edu.chat.persistence.UsuarioRepository {
        private final Map<String, Usuario> usuarios;

        private UsuariosFalsos(Map<String, Usuario> usuarios) {
            this.usuarios = usuarios;
        }

        @Override
        public void cadastrar(Usuario usuario) {
            usuarios.put(usuario.getLogin(), usuario);
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID id) {
            return usuarios.values().stream()
                    .filter(usuario -> usuario.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorLogin(String login) {
            return Optional.ofNullable(usuarios.get(login));
        }
    }

    private static final class UsuariosFalsosComFalha
            implements br.edu.chat.persistence.UsuarioRepository {
        private final RuntimeException falha;

        private UsuariosFalsosComFalha(RuntimeException falha) {
            this.falha = falha;
        }

        @Override
        public void cadastrar(Usuario usuario) {
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID id) {
            throw falha;
        }

        @Override
        public Optional<Usuario> buscarPorLogin(String login) {
            throw falha;
        }
    }

    private static final class CredenciaisFalsas implements UsuarioCredencialRepository {
        private final Map<UUID, CredencialSenha> credenciais;

        private CredenciaisFalsas(Map<UUID, CredencialSenha> credenciais) {
            this.credenciais = credenciais;
        }

        @Override
        public void salvar(UUID usuarioId, CredencialSenha credencial) {
            credenciais.put(usuarioId, credencial);
        }

        @Override
        public Optional<CredencialSenha> buscarPorUsuarioId(UUID usuarioId) {
            return Optional.ofNullable(credenciais.get(usuarioId));
        }
    }

    private static final class RepositorioFalso implements MensagemRepository {
        private final Mensagem mensagem;

        private RepositorioFalso(Mensagem mensagem) {
            this.mensagem = mensagem;
        }

        @Override
        public void salvar(Mensagem mensagem) {
        }

        @Override
        public java.util.List<Mensagem> listarHistorico() {
            return java.util.List.of(mensagem);
        }
    }

    private static final class RepositorioHistoricoFalho implements MensagemRepository {
        @Override
        public void salvar(Mensagem mensagem) {
        }

        @Override
        public java.util.List<Mensagem> listarHistorico() {
            throw new PersistenciaException(
                    "historico-interno-nao-publico", new IllegalStateException());
        }
    }

    private static final class ConexaoFalsa implements ConexaoCliente {
        private final java.util.List<Mensagem> mensagens = new java.util.ArrayList<>();

        @Override
        public boolean enviar(Mensagem mensagem) {
            mensagens.add(mensagem);
            return true;
        }

        @Override
        public void encerrar() {
        }
    }

    private static final class SocketDeTeste extends Socket {
        private final InputStream entrada;
        private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
        private boolean fechado;

        private SocketDeTeste(InputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public InputStream getInputStream() {
            return entrada;
        }

        @Override
        public OutputStream getOutputStream() {
            return saida;
        }

        @Override
        public synchronized void close() throws IOException {
            fechado = true;
            entrada.close();
        }
    }

    private static final class LeituraBloqueante extends InputStream {
        private final CountDownLatch leituraIniciada = new CountDownLatch(1);
        private final CountDownLatch encerrada = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            leituraIniciada.countDown();
            try {
                encerrada.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Leitura interrompida", exception);
            }
            throw new IOException("Leitura encerrada");
        }

        @Override
        public void close() {
            encerrada.countDown();
        }
    }
}
