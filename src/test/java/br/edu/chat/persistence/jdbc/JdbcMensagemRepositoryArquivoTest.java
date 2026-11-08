package br.edu.chat.persistence.jdbc;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.chat.model.Mensagem;
import br.edu.chat.model.Usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JdbcMensagemRepositoryArquivoTest {

    @TempDir
    Path diretorioTemporario;

    @Test
    void devePreservarMensagensAoReabrirOBancoEmArquivoTemporario() {
        Path caminhoBanco = diretorioTemporario.resolve("historico").toAbsolutePath();
        ConnectionFactory primeiraFactory = factoryPara(caminhoBanco);
        JdbcMensagemRepository primeiroRepository = new JdbcMensagemRepository(primeiraFactory);
        Mensagem primeira = mensagem("Ana", "Olá | arquivo", LocalDateTime.of(2026, 11, 7, 21, 0));
        Mensagem segunda = mensagem("Bia", "Mensagem persistida", LocalDateTime.of(2026, 11, 7, 21, 1));

        primeiroRepository.salvar(primeira);
        primeiroRepository.salvar(segunda);
        List<Mensagem> antesDaReabertura = primeiroRepository.listarHistorico();

        ConnectionFactory segundaFactory = factoryPara(caminhoBanco);
        JdbcMensagemRepository segundoRepository = new JdbcMensagemRepository(segundaFactory);
        List<Mensagem> depoisDaReabertura = segundoRepository.listarHistorico();

        assertEquals(2, antesDaReabertura.size());
        assertEquals(2, depoisDaReabertura.size());
        assertMensagem(primeira, depoisDaReabertura.get(0));
        assertMensagem(segunda, depoisDaReabertura.get(1));
        assertFalse(caminhoBanco.resolveSibling("historico.mv.db").toFile().length() == 0);
    }

    private ConnectionFactory factoryPara(Path caminhoBanco) {
        return new ConnectionFactory("jdbc:h2:file:" + caminhoBanco, "sa", "");
    }

    private static Mensagem mensagem(String nome, String conteudo, LocalDateTime horario) {
        return new Mensagem(new Usuario(nome), conteudo, horario);
    }

    private static void assertMensagem(Mensagem esperada, Mensagem atual) {
        assertEquals(esperada.getRemetente().getNome(), atual.getRemetente().getNome());
        assertEquals(esperada.getConteudo(), atual.getConteudo());
        assertEquals(esperada.getHorario(), atual.getHorario());
        assertNotEquals(esperada, atual);
    }
}
