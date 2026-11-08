package br.edu.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.MensagemRepository;
import br.edu.chat.persistence.PersistenciaException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServicePersistenciaTest {

    @Test
    void deveSalvarAntesDeTransmitir() {
        List<String> eventos = new ArrayList<>();
        OrdemRepository repository = new OrdemRepository(eventos);
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro, repository);
        ConexaoFalsa destino = new ConexaoFalsa(eventos);
        registro.registrar(destino);

        service.distribuir(mensagem("Ana", "Olá"), new ConexaoFalsa());

        assertEquals(List.of("salvar", "enviar"), repository.eventos);
    }

    @Test
    void falhaAoSalvarNaoDeveTransmitir() {
        MensagemRepository repository = new MensagemRepository() {
            @Override
            public void salvar(Mensagem mensagem) {
                throw new PersistenciaException("falha", new IllegalStateException());
            }

            @Override
            public List<Mensagem> listarHistorico() {
                return List.of();
            }
        };
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro, repository);
        ConexaoFalsa destino = new ConexaoFalsa();
        registro.registrar(destino);

        service.distribuir(mensagem("Ana", "Olá"), new ConexaoFalsa());

        assertTrue(destino.mensagens.isEmpty());
        assertFalse(destino.encerrada);
    }

    @Test
    void deveEnviarHistoricoAoConectar() {
        Mensagem antiga = mensagem("Ana", "antiga");
        MensagemRepository repository = new RepositoryFalso(List.of(antiga));
        ChatService service = new ChatService(new ClientRegistry(), repository);
        ConexaoFalsa cliente = new ConexaoFalsa();

        service.conectar(cliente);

        assertEquals(List.of(antiga), cliente.mensagens);
    }

    @Test
    void falhaAoConsultarHistoricoNaoDeveImpedirConexao() {
        MensagemRepository repository = new RepositoryFalsoFalho();
        ClientRegistry registro = new ClientRegistry();
        ChatService service = new ChatService(registro, repository);
        ConexaoFalsa cliente = new ConexaoFalsa();

        service.conectar(cliente);

        assertTrue(registro.snapshot().contains(cliente));
        assertTrue(cliente.mensagens.isEmpty());
    }

    private static Mensagem mensagem(String nome, String conteudo) {
        return new Mensagem(new Usuario(nome), conteudo, LocalDateTime.of(2026, 11, 7, 21, 0));
    }

    private static final class ConexaoFalsa implements ConexaoCliente {
        private final List<Mensagem> mensagens = new ArrayList<>();
        private final List<String> eventos;
        private boolean encerrada;

        private ConexaoFalsa() {
            this(new ArrayList<>());
        }

        private ConexaoFalsa(List<String> eventos) {
            this.eventos = eventos;
        }

        @Override
        public boolean enviar(Mensagem mensagem) {
            mensagens.add(mensagem);
            eventos.add("enviar");
            return true;
        }

        @Override
        public void encerrar() {
            encerrada = true;
        }
    }

    private static final class RepositoryFalso implements MensagemRepository {
        private final List<Mensagem> mensagens;

        private RepositoryFalso(List<Mensagem> mensagens) {
            this.mensagens = mensagens;
        }

        @Override
        public void salvar(Mensagem mensagem) {
        }

        @Override
        public List<Mensagem> listarHistorico() {
            return mensagens;
        }
    }

    private static final class RepositoryFalsoFalho implements MensagemRepository {
        @Override
        public void salvar(Mensagem mensagem) {
        }

        @Override
        public List<Mensagem> listarHistorico() {
            throw new PersistenciaException("falha", new IllegalStateException());
        }
    }

    private static final class OrdemRepository implements MensagemRepository {
        private final List<String> eventos;

        private OrdemRepository(List<String> eventos) {
            this.eventos = eventos;
        }

        @Override
        public void salvar(Mensagem mensagem) {
            eventos.add("salvar");
        }

        @Override
        public List<Mensagem> listarHistorico() {
            return List.of();
        }
    }
}
