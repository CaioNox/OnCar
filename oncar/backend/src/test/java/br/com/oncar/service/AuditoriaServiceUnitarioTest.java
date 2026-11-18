package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.LogAuditoria;
import br.com.oncar.repository.LogAuditoriaRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceUnitarioTest {

    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    private LogAuditoria registroGravado() {
        ArgumentCaptor<LogAuditoria> captor = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("sem autenticacao a acao e atribuida ao sistema")
    void semAutenticacao() {
        assertThat(auditoriaService.usuarioAutenticado()).isEqualTo("sistema");
    }

    @Test
    @DisplayName("visitante anonimo e registrado como sistema")
    void anonimo() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("chave",
                "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(auditoriaService.usuarioAutenticado()).isEqualTo("sistema");
    }

    @Test
    @DisplayName("credencial ainda nao autenticada e registrada como sistema")
    void naoAutenticado() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguem@teste.com.br", "senha"));

        assertThat(auditoriaService.usuarioAutenticado()).isEqualTo("sistema");
    }

    @Test
    @DisplayName("usuario autenticado e identificado pelo nome de login")
    void autenticado() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "dono@teste.com.br", null, List.of(new SimpleGrantedAuthority("ROLE_PROPRIETARIO"))));

        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Produto", 15L, "Produto cadastrado");

        LogAuditoria log = registroGravado();
        assertThat(log.getUsuario()).isEqualTo("dono@teste.com.br");
        assertThat(log.getAcao()).isEqualTo(AcaoAuditoria.INCLUSAO);
        assertThat(log.getEntidade()).isEqualTo("Produto");
        assertThat(log.getRegistroId()).isEqualTo("15");
        assertThat(log.getDetalhe()).isEqualTo("Produto cadastrado");
    }

    @Test
    @DisplayName("registro sem id fica com o identificador nulo, e nao com o texto \"null\"")
    void registroSemId() {
        auditoriaService.registrarComoUsuario("x@teste.com.br", AcaoAuditoria.LOGIN_FALHA, "Usuario", null,
                "Tentativa");

        LogAuditoria log = registroGravado();
        assertThat(log.getRegistroId()).isNull();
        assertThat(log.getUsuario()).isEqualTo("x@teste.com.br");
    }
}
