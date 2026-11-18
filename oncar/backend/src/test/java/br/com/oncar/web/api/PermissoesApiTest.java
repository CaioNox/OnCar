package br.com.oncar.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oncar.domain.enums.Perfil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissoesApiTest {

    private final PermissoesApi permissoes = new PermissoesApi();

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "{0}: custos={1}, financeiro={2}")
    @CsvSource({
            "PROPRIETARIO,    true,  true",
            "GERENTE_ESTOQUE, true,  false",
            "ATENDENTE,       false, false",
            "MECANICO,        false, false"})
    @DisplayName("custos ficam com proprietario e gerente; o financeiro, so com o proprietario")
    void permissoesPorPerfil(Perfil perfil, boolean custos, boolean financeiro) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "x@teste.com.br", null, List.of(new SimpleGrantedAuthority(perfil.getAuthority()))));

        assertThat(permissoes.podeVerCustos()).isEqualTo(custos);
        assertThat(permissoes.podeVerFinanceiro()).isEqualTo(financeiro);
    }

    @Test
    @DisplayName("sem autenticacao nada e liberado")
    void semAutenticacao() {
        assertThat(permissoes.podeVerCustos()).isFalse();
        assertThat(permissoes.podeVerFinanceiro()).isFalse();
    }

    @Test
    @DisplayName("a permissao precisa do prefixo ROLE_; o nome cru do perfil nao libera nada")
    void exigePrefixoRole() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "x@teste.com.br", null, List.of(new SimpleGrantedAuthority("PROPRIETARIO"))));

        assertThat(permissoes.podeVerFinanceiro()).isFalse();
    }
}
