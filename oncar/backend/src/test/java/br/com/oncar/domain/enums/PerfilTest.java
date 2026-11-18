package br.com.oncar.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PerfilTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Perfil.class)
    @DisplayName("a permissao de cada perfil leva o prefixo ROLE_ exigido pelo hasRole do Spring Security")
    void autoridadeDoPerfil(Perfil perfil) {
        assertThat(perfil.getAuthority()).isEqualTo("ROLE_" + perfil.name());
    }
}
