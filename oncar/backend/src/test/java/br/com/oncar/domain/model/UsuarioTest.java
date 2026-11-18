package br.com.oncar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    @DisplayName("o e-mail e gravado em minusculas e sem espacos, para o login nao depender da digitacao")
    void normalizaEmail() {
        Usuario usuario = new Usuario();

        usuario.setEmail("  Dono@Oficina.COM.br ");

        assertThat(usuario.getEmail()).isEqualTo("dono@oficina.com.br");
    }

    @Test
    @DisplayName("e-mail nulo continua nulo")
    void emailNulo() {
        Usuario usuario = new Usuario();

        usuario.setEmail(null);

        assertThat(usuario.getEmail()).isNull();
    }

    @Test
    @DisplayName("o bloqueio vale so enquanto o prazo nao vence")
    void bloqueio() {
        Usuario usuario = new Usuario();
        assertThat(usuario.isBloqueado()).as("sem bloqueio").isFalse();

        usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(1));
        assertThat(usuario.isBloqueado()).as("prazo futuro").isTrue();

        usuario.setBloqueadoAte(LocalDateTime.now().minusSeconds(1));
        assertThat(usuario.isBloqueado()).as("prazo vencido").isFalse();
    }

    @Test
    @DisplayName("um usuario novo nasce ativo e sem tentativas falhas")
    void padroes() {
        Usuario usuario = new Usuario();

        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getTentativasFalhas()).isZero();
        assertThat(usuario.getCriadoEm()).isNotNull();
    }
}
