package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.service.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Politica de senhas, bloqueio de conta e cadastro de usuarios: RN014 e RN009.
 */
class UsuarioServiceTest extends TesteIntegracao {

    @Test
    @DisplayName("RN014: senha curta ou sem numero e recusada")
    void senhaForaDaPoliticaEhRecusada() {
        Usuario usuario = new Usuario();
        usuario.setNome("Novo Usuario");
        usuario.setEmail("novo@teste.com.br");
        usuario.setPerfil(Perfil.ATENDENTE);

        assertThatThrownBy(() -> usuarioService.cadastrar(usuario, "curta1"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("8 caracteres");

        assertThatThrownBy(() -> usuarioService.cadastrar(usuario, "somenteletras"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("uma letra e um numero");
    }

    @Test
    @DisplayName("RN014: a senha e armazenada apenas como hash BCrypt")
    void senhaEhArmazenadaComoHash() {
        Usuario gerente = criarGerente();

        assertThat(gerente.getSenha()).isNotEqualTo("oncar2026").startsWith("$2");
    }

    @Test
    @DisplayName("RN014: a nova senha nao pode repetir as tres ultimas utilizadas")
    void novaSenhaNaoPodeRepetirAsTresUltimas() {
        criarGerente();
        usuarioService.alterarSenha(EMAIL_GERENTE, "oncar2026", "oficina2026", "oficina2026");
        usuarioService.alterarSenha(EMAIL_GERENTE, "oficina2026", "estoque2026", "estoque2026");

        assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL_GERENTE, "estoque2026",
                "oncar2026", "oncar2026"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("3 ultimas senhas");
    }

    @Test
    @DisplayName("RF03: a troca de senha exige a senha atual correta e a confirmacao")
    void trocaDeSenhaValidaSenhaAtualEConfirmacao() {
        criarGerente();

        assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL_GERENTE, "errada1234",
                "oficina2026", "oficina2026"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("senha atual");

        assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL_GERENTE, "oncar2026",
                "oficina2026", "outra2026"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("confirmacao");
    }

    @Test
    @DisplayName("RN014: cinco tentativas malsucedidas bloqueiam a conta por 15 minutos")
    void cincoTentativasBloqueiamAConta() {
        criarGerente();

        for (int tentativa = 0; tentativa < 4; tentativa++) {
            usuarioService.registrarFalhaDeLogin(EMAIL_GERENTE);
        }
        assertThat(usuarioService.buscarPorEmail(EMAIL_GERENTE).isBloqueado()).isFalse();

        usuarioService.registrarFalhaDeLogin(EMAIL_GERENTE);

        Usuario bloqueado = usuarioService.buscarPorEmail(EMAIL_GERENTE);
        assertThat(bloqueado.isBloqueado()).isTrue();
        assertThat(bloqueado.getBloqueadoAte()).isNotNull();
    }

    @Test
    @DisplayName("RN014: o login com sucesso zera as tentativas acumuladas")
    void loginComSucessoZeraTentativas() {
        criarGerente();
        usuarioService.registrarFalhaDeLogin(EMAIL_GERENTE);
        usuarioService.registrarFalhaDeLogin(EMAIL_GERENTE);

        usuarioService.registrarLoginComSucesso(EMAIL_GERENTE);

        assertThat(usuarioService.buscarPorEmail(EMAIL_GERENTE).getTentativasFalhas()).isZero();
    }

    @Test
    @DisplayName("RF02: o e-mail do usuario nao pode se repetir")
    void emailDuplicadoEhRecusado() {
        criarGerente();

        Usuario outro = new Usuario();
        outro.setNome("Outro Usuario");
        outro.setEmail(EMAIL_GERENTE.toUpperCase());
        outro.setPerfil(Perfil.ATENDENTE);

        assertThatThrownBy(() -> usuarioService.cadastrar(outro, "oncar2026"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Ja existe um usuario");
    }

    @Test
    @DisplayName("RN009: a inativacao preserva o cadastro do usuario")
    void inativacaoPreservaCadastro() {
        Usuario gerente = criarGerente();

        usuarioService.inativar(gerente.getId());

        assertThat(usuarioService.buscarPorId(gerente.getId()).isAtivo()).isFalse();
    }
}
