package br.edu.chat.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.edu.chat.model.Usuario;
import br.edu.chat.persistence.CadastroUsuario;
import br.edu.chat.persistence.UsuarioCredencialRepository;
import br.edu.chat.persistence.UsuarioRepository;

/** Coordena cadastro de contas e autenticação por login e senha. */
public final class AutenticacaoService {

    private final CadastroUsuario cadastroUsuario;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioCredencialRepository credencialRepository;
    private final SenhaHasher senhaHasher;

    public AutenticacaoService(CadastroUsuario cadastroUsuario,
            UsuarioRepository usuarioRepository,
            UsuarioCredencialRepository credencialRepository,
            SenhaHasher senhaHasher) {
        this.cadastroUsuario = Objects.requireNonNull(cadastroUsuario,
                "O cadastro de usuário não pode ser nulo.");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository,
                "O repositório de usuários não pode ser nulo.");
        this.credencialRepository = Objects.requireNonNull(credencialRepository,
                "O repositório de credenciais não pode ser nulo.");
        this.senhaHasher = Objects.requireNonNull(senhaHasher,
                "O hasher de senha não pode ser nulo.");
    }

    public Usuario cadastrar(String login, String nome, char[] senha) {
        Usuario usuario = new Usuario(UUID.randomUUID(), login, nome);
        CredencialSenha credencial = senhaHasher.gerar(senha);
        cadastroUsuario.cadastrar(usuario, credencial);
        return usuario;
    }

    public Optional<Usuario> autenticar(String login, char[] senha) {
        Objects.requireNonNull(login, "O login não pode ser nulo.");
        Optional<Usuario> usuario = usuarioRepository.buscarPorLogin(login);
        if (usuario.isEmpty()) {
            return Optional.empty();
        }

        Usuario encontrado = usuario.orElseThrow();
        Optional<CredencialSenha> credencial = credencialRepository
                .buscarPorUsuarioId(encontrado.getId());
        if (credencial.isEmpty()) {
            return Optional.empty();
        }

        if (!senhaHasher.verificar(senha, credencial.orElseThrow())) {
            return Optional.empty();
        }
        return Optional.of(encontrado);
    }
}
