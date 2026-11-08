package br.edu.chat.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteHandlerTest {

    @Test
    void encerrarDeveInterromperLeituraBloqueante() throws Exception {
        LeituraBloqueante leitura = new LeituraBloqueante();
        SocketDeTeste socket = new SocketDeTeste(leitura);
        CountDownLatch finalizado = new CountDownLatch(1);
        ClienteHandler handler = new ClienteHandler(socket, ignorado -> finalizado.countDown());
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
        ClienteHandler handler = new ClienteHandler(socket, ignorado -> { });

        handler.run();

        assertTrue(socket.fechado);
    }

    @Test
    void callbackDeFinalizacaoDeveSerExecutado() throws Exception {
        SocketDeTeste socket = new SocketDeTeste(new ByteArrayInputStream(new byte[0]));
        CountDownLatch finalizado = new CountDownLatch(1);
        ClienteHandler handler = new ClienteHandler(socket, ignorado -> finalizado.countDown());

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
        ClienteHandler handler = new ClienteHandler(socket, ignorado -> finalizado.countDown());

        handler.run();

        assertTrue(finalizado.await(1, TimeUnit.SECONDS));
        assertTrue(socket.fechado);
    }

    @Test
    void bytesRecebidosDevemSerDescartadosSemInterpretacao() throws IOException {
        byte[] bytes = "dados sem protocolo".getBytes();
        SocketDeTeste socket = new SocketDeTeste(new ByteArrayInputStream(bytes));
        AtomicInteger chamadasCallback = new AtomicInteger();
        ClienteHandler handler = new ClienteHandler(socket, ignorado -> chamadasCallback.incrementAndGet());

        handler.run();

        assertEquals(0, socket.entrada.available());
        assertEquals(1, chamadasCallback.get());
        assertTrue(socket.fechado);
    }

    private static void assertTimeoutAndJoin(Thread thread) {
        org.junit.jupiter.api.Assertions.assertTimeout(
                Duration.ofSeconds(2),
                () -> thread.join(1000)
        );
        assertFalse(thread.isAlive());
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
