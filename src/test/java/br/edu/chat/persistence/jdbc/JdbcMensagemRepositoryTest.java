package br.edu.chat.persistence.jdbc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMensagemRepositoryTest {

    @Test
    void deveSalvarERecuperarMensagem() {
        JdbcMensagemRepository repository = novoRepository();
        Mensagem esperada = mensagem("Ana", "Olá | turma", LocalDateTime.of(2026, 11, 7, 21, 0));

        repository.salvar(esperada);

        List<Mensagem> resultado = repository.listarHistorico();
        assertEquals(1, resultado.size());
        assertMensagemIgual(esperada, resultado.get(0));
    }

    @Test
    void deveOrdenarHistoricoPorHorarioEId() {
        JdbcMensagemRepository repository = novoRepository();
        LocalDateTime horario = LocalDateTime.of(2026, 11, 7, 21, 0);
        Mensagem segunda = mensagem("Bia", "segunda", horario.plusMinutes(1));
        Mensagem primeira = mensagem("Ana", "primeira", horario);
        Mensagem terceira = mensagem("Caio", "terceira", horario);

        repository.salvar(segunda);
        repository.salvar(primeira);
        repository.salvar(terceira);

        List<Mensagem> resultado = repository.listarHistorico();
        assertEquals(3, resultado.size());
        assertMensagemIgual(primeira, resultado.get(0));
        assertMensagemIgual(terceira, resultado.get(1));
        assertMensagemIgual(segunda, resultado.get(2));
    }

    @Test
    void deveSuportarInsercoesConcorrentes() throws Exception {
        JdbcMensagemRepository repository = novoRepository();
        int quantidade = 8;
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        try {
            for (int i = 0; i < quantidade; i++) {
                int indice = i;
                executor.submit(() -> {
                    try {
                        inicio.await();
                        repository.salvar(mensagem("Usuario" + indice, "mensagem" + indice,
                                LocalDateTime.of(2026, 11, 7, 21, 0).plusNanos(indice)));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            inicio.countDown();
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }

        assertEquals(quantidade, repository.listarHistorico().size());
    }

    private static JdbcMensagemRepository novoRepository() {
        String nome = "repository_test_" + UUID.randomUUID().toString().replace('-', '_');
        return new JdbcMensagemRepository(ConnectionFactory.emMemoria(nome));
    }

    private static Mensagem mensagem(String nome, String conteudo, LocalDateTime horario) {
        return new Mensagem(new Usuario(nome), conteudo, horario);
    }

    private static void assertMensagemIgual(Mensagem esperada, Mensagem atual) {
        assertEquals(esperada.getRemetente().getNome(), atual.getRemetente().getNome());
        assertEquals(esperada.getConteudo(), atual.getConteudo());
        assertEquals(esperada.getHorario(), atual.getHorario());
    }
}
