package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DetalhesUsuarioServiceTest {

    private static final String EMAIL = "mecanico@teste.com.br";

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private DetalhesUsuarioService detalhesUsuarioService;

    private Usuario usuario;

    @BeforeEach
    void prepararUsuario() {
        usuario = new Usuario();
        usuario.setEmail(EMAIL);
        usuario.setSenha("$2a$10$hash");
        usuario.setPerfil(Perfil.MECANICO);
    }

    @Test
    @DisplayName("usuario ativo vira credencial com o hash e a permissao do perfil")
    void usuarioAtivo() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        UserDetails detalhes = detalhesUsuarioService.loadUserByUsername(EMAIL);

        assertThat(detalhes.getUsername()).isEqualTo(EMAIL);
        assertThat(detalhes.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(detalhes.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MECANICO");
        assertThat(detalhes.isEnabled()).isTrue();
        assertThat(detalhes.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("usuario inativo fica desabilitado")
    void usuarioInativo() {
        usuario.setAtivo(false);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThat(detalhesUsuarioService.loadUserByUsername(EMAIL).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("bloqueio ainda vigente trava a conta")
    void bloqueioVigente() {
        usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThat(detalhesUsuarioService.loadUserByUsername(EMAIL).isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("bloqueio ja vencido nao trava mais a conta")
    void bloqueioVencido() {
        usuario.setBloqueadoAte(LocalDateTime.now().minusSeconds(1));
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

        assertThat(detalhesUsuarioService.loadUserByUsername(EMAIL).isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("e-mail desconhecido recebe a mesma mensagem generica de senha errada")
    void emailDesconhecido() {
        when(usuarioRepository.findByEmailIgnoreCase("x@teste.com.br")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> detalhesUsuarioService.loadUserByUsername("x@teste.com.br"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario ou senha invalidos.");
    }
}
