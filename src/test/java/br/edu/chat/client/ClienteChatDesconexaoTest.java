package br.edu.chat.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteChatDesconexaoTest {

    @Test
    void deveExecutarCallbackUmaVezAoReceberEof() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        });
        CountDownLatch desconectado = new CountDownLatch(1);
        AtomicInteger chamadas = new AtomicInteger();
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            chamadas.incrementAndGet();
            desconectado.countDown();
        });

        cliente.conectar();

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        cliente.fechar();
        assertEquals(1, chamadas.get());
    }

    @Test
    void deveExecutarCallbackUmaVezAoFecharRepetidamente() throws Exception {
        LeituraBloqueante leitura = new LeituraBloqueante();
        SocketDeTeste socket = new SocketDeTeste(leitura);
        CountDownLatch desconectado = new CountDownLatch(1);
        AtomicInteger chamadas = new AtomicInteger();
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            chamadas.incrementAndGet();
            desconectado.countDown();
        });

        cliente.conectar();
        cliente.fechar();
        cliente.fechar();

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        assertEquals(1, chamadas.get());
    }

    @Test
    void falhaDoCallbackNaoDeveImpedirOEncerramento() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        });
        CountDownLatch desconectado = new CountDownLatch(1);
        ClienteChat cliente = new ClienteChat(socket, "Ana", mensagem -> { }, () -> {
            desconectado.countDown();
            throw new IllegalStateException("falha simulada");
        });

        cliente.conectar();

        assertTrue(desconectado.await(1, TimeUnit.SECONDS));
        assertTrue(socket.fechado);
    }

    private static final class SocketDeTeste extends Socket {
        private final InputStream entrada;
        private boolean fechado;

        private SocketDeTeste(InputStream entrada) {
            this.entrada = entrada;
        }

        @Override
        public InputStream getInputStream() {
            return entrada;
        }

        @Override
        public synchronized void close() throws IOException {
            fechado = true;
            entrada.close();
        }
    }

    private static final class LeituraBloqueante extends InputStream {
        private final CountDownLatch encerrada = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            try {
                encerrada.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Leitura encerrada");
        }

        @Override
        public void close() {
            encerrada.countDown();
        }
    }
}
