package br.edu.chat.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.CadastroUsuario;
import br.edu.chat.persistence.PersistenciaException;
import br.edu.chat.persistence.UsuarioCredencialRepository;
import br.edu.chat.persistence.UsuarioRepository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutenticacaoServiceTest {

    @Test
    void deveCadastrarComUuidEDadosCorretos() {
        FalsoCadastro cadastro = new FalsoCadastro();
        AutenticacaoService service = novoService(cadastro, new FalsosRepositorios());

        Usuario usuario = service.cadastrar("ana", "Ana", "segredo".toCharArray());

        assertTrue(usuario.possuiIdentidadePersistente());
        assertEquals("ana", usuario.getLogin());
        assertEquals("Ana", usuario.getNome());
        assertSame(usuario, cadastro.usuario);
    }

    @Test
    void deveChamarCadastroTransacionalComUsuarioECredencial() {
        FalsoCadastro cadastro = new FalsoCadastro();
        AutenticacaoService service = novoService(cadastro, new FalsosRepositorios());

        service.cadastrar("bia", "Bia", "segredo".toCharArray());

        assertEquals(1, cadastro.quantidadeChamadas);
        assertSame(cadastro.usuario.getId(), cadastro.credencialId());
        assertTrue(new SenhaHasher().verificar("segredo".toCharArray(), cadastro.credencial));
    }

    @Test
    void deveAutenticarComSenhaCorreta() {
        FalsosRepositorios repositorios = new FalsosRepositorios();
        Usuario usuario = usuario("carol", "Carol");
        repositorios.usuarios.adicionar(usuario);
        repositorios.credenciais.adicionar(usuario.getId(),
                new SenhaHasher().gerar("segredo".toCharArray()));
        AutenticacaoService service = novoService(new FalsoCadastro(), repositorios);

        Optional<Usuario> resultado = service.autenticar("carol", "segredo".toCharArray());

        assertTrue(resultado.isPresent());
        assertSame(usuario, resultado.orElseThrow());
    }

    @Test
    void deveRejeitarLoginInexistenteSenhaIncorretaECredencialAusente() {
        FalsosRepositorios repositorios = new FalsosRepositorios();
        Usuario usuario = usuario("davi", "Davi");
        repositorios.usuarios.adicionar(usuario);
        AutenticacaoService service = novoService(new FalsoCadastro(), repositorios);

        assertFalse(service.autenticar("inexistente", "segredo".toCharArray()).isPresent());
        assertFalse(service.autenticar("davi", "segredo".toCharArray()).isPresent());

        repositorios.credenciais.adicionar(usuario.getId(),
                new SenhaHasher().gerar("correta".toCharArray()));
        assertFalse(service.autenticar("davi", "incorreta".toCharArray()).isPresent());
    }

    @Test
    void devePropagarLoginDuplicadoEFalhaDePersistencia() {
        FalsosRepositorios repositorios = new FalsosRepositorios();
        FalsoCadastro cadastro = new FalsoCadastro();
        cadastro.falha = new PersistenciaException("falha", new IllegalStateException());
        AutenticacaoService service = novoService(cadastro, repositorios);

        assertThrows(PersistenciaException.class,
                () -> service.cadastrar("duplicado", "Nome", "segredo".toCharArray()));
        assertEquals(1, cadastro.quantidadeChamadas);
    }

    @Test
    void devePropagarExcecaoInesperadaDoRepositorioDeUsuarios() {
        FalsosRepositorios repositorios = new FalsosRepositorios();
        String detalheInterno = "detalhe-interno-nao-publico";
        repositorios.usuarios.falha = new IllegalStateException(detalheInterno);
        AutenticacaoService service = novoService(new FalsoCadastro(), repositorios);

        IllegalStateException excecao = assertThrows(IllegalStateException.class,
                () -> service.autenticar("ana", "segredo".toCharArray()));

        assertEquals(detalheInterno, excecao.getMessage());
    }

    @Test
    void devePreservarArrayDeSenhaRecebido() {
        FalsoCadastro cadastro = new FalsoCadastro();
        AutenticacaoService service = novoService(cadastro, new FalsosRepositorios());
        char[] senha = "segredo".toCharArray();
        char[] original = senha.clone();

        service.cadastrar("elisa", "Elisa", senha);

        assertArrayEquals(original, senha);
    }

    @Test
    void naoDevePersistirQuandoArgumentosForemInvalidos() {
        FalsoCadastro cadastro = new FalsoCadastro();
        AutenticacaoService service = novoService(cadastro, new FalsosRepositorios());

        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar("", "Nome", "segredo".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar("login", "", "segredo".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar("login", "Nome", new char[0]));
        assertEquals(0, cadastro.quantidadeChamadas);
    }

    private static AutenticacaoService novoService(FalsoCadastro cadastro,
            FalsosRepositorios repositorios) {
        return new AutenticacaoService(cadastro, repositorios.usuarios,
                repositorios.credenciais, new SenhaHasher());
    }

    private static Usuario usuario(String login, String nome) {
        return new Usuario(UUID.randomUUID(), login, nome);
    }

    private static final class FalsoCadastro implements CadastroUsuario {
        private Usuario usuario;
        private CredencialSenha credencial;
        private RuntimeException falha;
        private int quantidadeChamadas;

        @Override
        public void cadastrar(Usuario usuario, CredencialSenha credencial) {
            quantidadeChamadas++;
            if (falha != null) {
                throw falha;
            }
            this.usuario = usuario;
            this.credencial = credencial;
        }

        private UUID credencialId() {
            return usuario.getId();
        }
    }

    private static final class FalsosRepositorios {
        private final FalsoUsuarios usuarios = new FalsoUsuarios();
        private final FalsoCredenciais credenciais = new FalsoCredenciais();
    }

    private static final class FalsoUsuarios implements UsuarioRepository {
        private final Map<String, Usuario> usuarios = new HashMap<>();
        private RuntimeException falha;

        private void adicionar(Usuario usuario) {
            usuarios.put(usuario.getLogin(), usuario);
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
            if (falha != null) {
                throw falha;
            }
            return Optional.ofNullable(usuarios.get(login));
        }
    }

    private static final class FalsoCredenciais implements UsuarioCredencialRepository {
        private final Map<UUID, CredencialSenha> credenciais = new HashMap<>();

        private void adicionar(UUID usuarioId, CredencialSenha credencial) {
            credenciais.put(usuarioId, credencial);
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
}
