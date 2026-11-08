package br.edu.chat.client;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.persistence.jdbc.ConnectionFactory;
import br.edu.chat.persistence.jdbc.JdbcMensagemRepository;
import br.edu.chat.server.Servidor;
import br.edu.chat.service.ChatService;
import br.edu.chat.service.ClientRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatIntegracaoTest {

    @Test
    void deveConectarClienteAoServidorReal() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            aguardarHandlers(contexto, 1);
            assertTrue(contexto.threadServidor.isAlive());
        } finally {
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveEnviarMensagemAoServidor() throws Exception {
        Contexto contexto = iniciarServidor();
        CountDownLatch recebida = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> recebida.countDown());
        try {
            origem.conectar();
            destino.conectar();
            origem.enviar("mensagem enviada");
            assertTrue(recebida.await(2, TimeUnit.SECONDS));
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveDistribuirBroadcastAOutroCliente() throws Exception {
        Contexto contexto = iniciarServidor();
        AtomicReference<Mensagem> recebida = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> {
            recebida.set(mensagem);
            chegou.countDown();
        });
        try {
            origem.conectar();
            destino.conectar();
            aguardarHandlers(contexto, 2);

            origem.enviar("broadcast");

            assertTrue(chegou.await(2, TimeUnit.SECONDS));
            assertEquals("broadcast", recebida.get().getConteudo());
            assertEquals("Ana", recebida.get().getRemetente().getNome());
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveRecuperarHistoricoAoConectarNovoCliente() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat novoCliente = null;
        CountDownLatch historicoRecebido = new CountDownLatch(1);
        List<Mensagem> mensagens = new ArrayList<>();
        try {
            origem.conectar();
            origem.enviar("mensagem histórica");
            origem.fechar();

            novoCliente = novoCliente(contexto, "Bia", mensagem -> {
                mensagens.add(mensagem);
                historicoRecebido.countDown();
            });
            novoCliente.conectar();

            assertTrue(historicoRecebido.await(2, TimeUnit.SECONDS));
            assertEquals("mensagem histórica", mensagens.get(0).getConteudo());
        } finally {
            fechar(contexto, origem, novoCliente);
        }
    }

    @Test
    void devePreservarCaractereSeparadorNoConteudo() throws Exception {
        Contexto contexto = iniciarServidor();
        AtomicReference<Mensagem> recebida = new AtomicReference<>();
        CountDownLatch chegou = new CountDownLatch(1);
        ClienteChat origem = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Bia", mensagem -> {
            recebida.set(mensagem);
            chegou.countDown();
        });
        try {
            origem.conectar();
            destino.conectar();
            origem.enviar("parte 1 | parte 2");

            assertTrue(chegou.await(2, TimeUnit.SECONDS));
            assertEquals("parte 1 | parte 2", recebida.get().getConteudo());
        } finally {
            fechar(contexto, origem, destino);
        }
    }

    @Test
    void deveDistribuirMensagensSimultaneas() throws Exception {
        Contexto contexto = iniciarServidor();
        CountDownLatch recebidas = new CountDownLatch(2);
        List<String> conteudos = new ArrayList<>();
        ClienteChat origemUm = novoCliente(contexto, "Ana", mensagem -> { });
        ClienteChat origemDois = novoCliente(contexto, "Bia", mensagem -> { });
        ClienteChat destino = novoCliente(contexto, "Caio", mensagem -> {
            synchronized (conteudos) {
                conteudos.add(mensagem.getConteudo());
            }
            recebidas.countDown();
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            origemUm.conectar();
            origemDois.conectar();
            destino.conectar();
            CountDownLatch inicio = new CountDownLatch(1);
            executor.submit(() -> enviarAposInicio(inicio, origemUm, "A"));
            executor.submit(() -> enviarAposInicio(inicio, origemDois, "B"));
            inicio.countDown();

            assertTrue(recebidas.await(3, TimeUnit.SECONDS));
            synchronized (conteudos) {
                assertTrue(conteudos.contains("A"));
                assertTrue(conteudos.contains("B"));
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            fechar(contexto, origemUm, origemDois, destino);
        }
    }

    @Test
    void deveEncerrarThreadDoServidorComSeguranca() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            contexto.servidor.encerrar();
            assertTimeout(Duration.ofSeconds(3), () -> contexto.threadServidor.join(2000));
            assertFalse(contexto.threadServidor.isAlive());
        } finally {
            fechar(contexto, cliente);
        }
    }

    @Test
    void deveEncerrarClientesQuandoServidorForEncerrado() throws Exception {
        Contexto contexto = iniciarServidor();
        ClienteChat cliente = novoCliente(contexto, "Ana", mensagem -> { });
        try {
            cliente.conectar();
            aguardarHandlers(contexto, 1);
            contexto.servidor.encerrar();

            assertTimeout(Duration.ofSeconds(3), () -> {
                while (true) {
                    try {
                        cliente.enviar("não deve ser enviado");
                    } catch (IllegalStateException exception) {
                        return;
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
            });
        } finally {
            fechar(contexto, cliente);
        }
    }

    private static Contexto iniciarServidor() throws Exception {
        int porta = encontrarPortaLivre();
        String banco = "cliente_integracao_" + UUID.randomUUID().toString().replace('-', '_');
        ChatService service = new ChatService(
                new ClientRegistry(),
                new JdbcMensagemRepository(ConnectionFactory.emMemoria(banco)));
        Servidor servidor = new Servidor(porta, service);
        AtomicReference<Throwable> falha = new AtomicReference<>();
        Thread threadServidor = new Thread(() -> {
            try {
                servidor.iniciar();
            } catch (IOException exception) {
                falha.set(exception);
            }
        }, "servidor-integracao-cliente");
        threadServidor.start();
        aguardarPorta(porta, falha);
        return new Contexto(servidor, threadServidor, porta);
    }

    private static ClienteChat novoCliente(Contexto contexto, String nome,
            java.util.function.Consumer<Mensagem> callback) {
        return new ClienteChat("127.0.0.1", contexto.porta, nome, callback);
    }

    private static void enviarAposInicio(CountDownLatch inicio, ClienteChat cliente, String conteudo) {
        try {
            inicio.await();
            cliente.enviar(conteudo);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void aguardarPorta(int porta, AtomicReference<Throwable> falha) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (falha.get() != null) {
                throw new AssertionError("O servidor não iniciou.", falha.get());
            }
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", porta)) {
                return;
            } catch (IOException exception) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        throw new IOException("Não foi possível iniciar o servidor de integração.");
    }

    private static void aguardarHandlers(Contexto contexto, int quantidade) throws Exception {
        assertTimeout(Duration.ofSeconds(3), () -> {
            while (handlersAtivos(contexto.servidor) < quantidade) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        });
    }

    private static int handlersAtivos(Servidor servidor) throws Exception {
        var campo = Servidor.class.getDeclaredField("handlersAtivos");
        campo.setAccessible(true);
        return ((java.util.Set<?>) campo.get(servidor)).size();
    }

    private static int encontrarPortaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void fechar(Contexto contexto, ClienteChat... clientes) throws Exception {
        for (ClienteChat cliente : clientes) {
            if (cliente != null) {
                cliente.fechar();
            }
        }
        contexto.servidor.encerrar();
        contexto.threadServidor.join(2000);
    }

    private static void assertTimeout(Duration duracao, org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertTimeout(duracao, executable);
    }

    private record Contexto(Servidor servidor, Thread threadServidor, int porta) {
    }
}
