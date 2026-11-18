package br.com.oncar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.service.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;

@ExtendWith(MockitoExtension.class)
class PrimeiroAcessoTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioService usuarioService;

    private CommandLineRunner rotina(String email, String senha) {
        return new PrimeiroAcesso().criarPrimeiroProprietario(usuarioRepository, usuarioService,
                "Dona da Oficina", email, senha);
    }

    @Test
    @DisplayName("com usuarios ja cadastrados as variaveis sao ignoradas")
    void baseJaPovoada() throws Exception {
        when(usuarioRepository.count()).thenReturn(1L);

        rotina("dona@oficina.com.br", "oficina2026").run();

        verifyNoInteractions(usuarioService);
    }

    @ParameterizedTest(name = "e-mail \"{0}\", senha \"{1}\"")
    @CsvSource({"'', oficina2026", "dona@oficina.com.br, ''", "'   ', '   '"})
    @DisplayName("base vazia sem e-mail ou senha apenas avisa, sem criar ninguem")
    void variaveisAusentes(String email, String senha) throws Exception {
        when(usuarioRepository.count()).thenReturn(0L);

        rotina(email, senha).run();

        verifyNoInteractions(usuarioService);
    }

    @Test
    @DisplayName("base vazia cria o proprietario com o e-mail sem espacos e a senha informada")
    void criaProprietario() throws Exception {
        when(usuarioRepository.count()).thenReturn(0L);

        rotina("  Dona@Oficina.com.br ", "oficina2026").run();

        ArgumentCaptor<Usuario> criado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService).cadastrar(criado.capture(), eq("oficina2026"));
        assertThat(criado.getValue().getEmail()).isEqualTo("dona@oficina.com.br");
        assertThat(criado.getValue().getPerfil()).isEqualTo(Perfil.PROPRIETARIO);
        assertThat(criado.getValue().getNome()).isEqualTo("Dona da Oficina");
    }

    @Test
    @DisplayName("senha fora da politica impede a subida com mensagem que aponta as variaveis")
    void senhaRecusada() {
        when(usuarioRepository.count()).thenReturn(0L);
        RegraNegocioException causa = new RegraNegocioException("A senha deve ter no minimo 8 caracteres.");
        when(usuarioService.cadastrar(any(Usuario.class), eq("123"))).thenThrow(causa);

        assertThatThrownBy(() -> rotina("dona@oficina.com.br", "123").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ONCAR_ADMIN_EMAIL/ONCAR_ADMIN_SENHA")
                .hasMessageContaining("no minimo 8 caracteres")
                .hasCause(causa);
    }
}
