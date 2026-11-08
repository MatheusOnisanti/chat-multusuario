package br.edu.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    @Test
    void deveExcluirOrigemPorIdentidadeDaConexao() {
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro);
        ConexaoFalsa origem = new ConexaoFalsa();
        ConexaoFalsa destino = new ConexaoFalsa();
        Mensagem mensagem = mensagem("Ana", "Olá");
        registro.registrar(origem);
        registro.registrar(destino);

        service.distribuir(mensagem, origem);

        assertEquals(0, origem.mensagens.size());
        assertEquals(1, destino.mensagens.size());
    }

    @Test
    void falhaDeUmClienteNaoDeveInterromperOsDemais() {
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro);
        ConexaoFalsa origem = new ConexaoFalsa();
        ConexaoFalsa falha = new ConexaoFalsa();
        falha.falharEnvio.set(true);
        ConexaoFalsa destino = new ConexaoFalsa();
        registro.registrar(origem);
        registro.registrar(falha);
        registro.registrar(destino);

        service.distribuir(mensagem("Ana", "Olá"), origem);

        assertTrue(falha.encerrada.get());
        assertFalse(registro.snapshot().contains(falha));
        assertEquals(1, destino.mensagens.size());
    }

    @Test
    void deveSuportarMensagensSimultaneasDeConexoesDiferentes() throws Exception {
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro);
        ConexaoFalsa origemUm = new ConexaoFalsa();
        ConexaoFalsa origemDois = new ConexaoFalsa();
        ConexaoFalsa destino = new ConexaoFalsa();
        registro.registrar(origemUm);
        registro.registrar(origemDois);
        registro.registrar(destino);

        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> distribuirAposInicio(
                    inicio, service, mensagem("Ana", "A"), origemUm));
            executor.submit(() -> distribuirAposInicio(
                    inicio, service, mensagem("Ana", "B"), origemDois));
            inicio.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }

        assertEquals(2, destino.mensagens.size());
    }

    @Test
    void clientesComMesmoNomeContinuamSendoConexoesDistintas() {
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro);
        ConexaoFalsa origemUm = new ConexaoFalsa();
        ConexaoFalsa origemDois = new ConexaoFalsa();
        ConexaoFalsa destino = new ConexaoFalsa();
        registro.registrar(origemUm);
        registro.registrar(origemDois);
        registro.registrar(destino);

        service.distribuir(mensagem("Ana", "A"), origemUm);
        service.distribuir(mensagem("Ana", "B"), origemDois);

        assertEquals(1, origemUm.mensagens.size());
        assertEquals(1, origemDois.mensagens.size());
        assertEquals(2, destino.mensagens.size());
    }

    private static void distribuirAposInicio(
            CountDownLatch inicio,
            ChatService service,
            Mensagem mensagem,
            ConexaoFalsa origem) {
        try {
            inicio.await();
            service.distribuir(mensagem, origem);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static Mensagem mensagem(String nome, String conteudo) {
        return new Mensagem(new Usuario(nome), conteudo, LocalDateTime.of(2026, 11, 7, 21, 0));
    }

    private static final class ConexaoFalsa implements ConexaoCliente {
        private final List<Mensagem> mensagens = new ArrayList<>();
        private final AtomicBoolean falharEnvio = new AtomicBoolean();
        private final AtomicBoolean encerrada = new AtomicBoolean();

        @Override
        public synchronized boolean enviar(Mensagem mensagem) {
            if (falharEnvio.get()) {
                return false;
            }
            mensagens.add(mensagem);
            return true;
        }

        @Override
        public void encerrar() {
            encerrada.set(true);
        }
    }
}
