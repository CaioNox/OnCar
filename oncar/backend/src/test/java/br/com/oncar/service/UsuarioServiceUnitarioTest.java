package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.SenhaHistorico;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.SenhaHistoricoRepository;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * O codificador de senha e simulado com uma regra previsivel ("hash:" + senha), para que
 * os testes nao dependam do custo nem do sal aleatorio do BCrypt.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceUnitarioTest {

    private static final String EMAIL = "atendente@teste.com.br";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SenhaHistoricoRepository senhaHistoricoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private DespesaRepository despesaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private SessoesAtivasService sessoesAtivas;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;

    @BeforeEach
    void prepararUsuario() {
        usuario = new Usuario();
        usuario.setId(4L);
        usuario.setNome("Atendente");
        usuario.setEmail(EMAIL);
        usuario.setPerfil(Perfil.ATENDENTE);
        usuario.setSenha("hash:senhaAtual1");
    }

    private void codificadorPrevisivel() {
        when(passwordEncoder.encode(anyString())).thenAnswer(chamada -> "hash:" + chamada.getArgument(0));
    }

    private void comparacaoPrevisivel() {
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenAnswer(chamada -> ("hash:" + chamada.getArgument(0)).equals(chamada.getArgument(1)));
    }

    private static SenhaHistorico historico(String senhaEmTexto) {
        return new SenhaHistorico(null, "hash:" + senhaEmTexto);
    }

    @Nested
    @DisplayName("politica de senha")
    class Politica {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abcdefg1", "1234567a", "Senha 2026!", "oncar2026"})
        @DisplayName("aceita oito ou mais caracteres com ao menos uma letra e um numero")
        void senhasValidas(String senha) {
            assertThatCode(() -> usuarioService.validarPolitica(senha)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"abcdef1", "abcdefgh", "12345678", "        ", "!@#$%^&*"})
        @DisplayName("recusa senha curta, so com letras, so com numeros, vazia ou nula")
        void senhasInvalidas(String senha) {
            assertThatThrownBy(() -> usuarioService.validarPolitica(senha))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("no minimo 8 caracteres");
        }
    }

    @Nested
    @DisplayName("cadastro")
    class Cadastro {

        @Test
        @DisplayName("e-mail repetido e recusado antes de codificar a senha")
        void emailRepetido() {
            when(usuarioRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> usuarioService.cadastrar(usuario, "oncar2026"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Ja existe um usuario");
            verifyNoInteractions(passwordEncoder);
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("senha fora da politica impede o cadastro")
        void senhaFraca() {
            when(usuarioRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);

            assertThatThrownBy(() -> usuarioService.cadastrar(usuario, "curta"))
                    .isInstanceOf(RegraNegocioException.class);
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("grava so o hash, inicia o historico de senhas e audita")
        void cadastra() {
            when(usuarioRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
            codificadorPrevisivel();
            when(usuarioRepository.save(usuario)).thenReturn(usuario);

            Usuario salvo = usuarioService.cadastrar(usuario, "oncar2026");

            assertThat(salvo.getSenha()).isEqualTo("hash:oncar2026");
            assertThat(salvo.getCriadoEm()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
            ArgumentCaptor<SenhaHistorico> historico = ArgumentCaptor.forClass(SenhaHistorico.class);
            verify(senhaHistoricoRepository).save(historico.capture());
            assertThat(historico.getValue().getSenha()).isEqualTo("hash:oncar2026");
            verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Usuario"), eq(4L), anyString());
        }
    }

    @Nested
    @DisplayName("alteracao de cadastro e sessoes abertas")
    class Alteracao {

        private Usuario dados(String email, Perfil perfil) {
            Usuario dados = new Usuario();
            dados.setNome("Nome Novo");
            dados.setEmail(email);
            dados.setPerfil(perfil);
            return dados;
        }

        @BeforeEach
        void usuarioExiste() {
            when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
        }

        @Test
        @DisplayName("e-mail de outro usuario e recusado")
        void emailDeOutro() {
            Usuario outro = new Usuario();
            outro.setId(5L);
            when(usuarioRepository.findByEmailIgnoreCase("outro@teste.com.br")).thenReturn(Optional.of(outro));

            assertThatThrownBy(() -> usuarioService.atualizar(4L, dados("outro@teste.com.br", Perfil.ATENDENTE)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("ja pertence a outro usuario");
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("mudar so o nome mantem as sessoes abertas")
        void soNome() {
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
            when(usuarioRepository.save(usuario)).thenReturn(usuario);

            usuarioService.atualizar(4L, dados(EMAIL, Perfil.ATENDENTE));

            assertThat(usuario.getNome()).isEqualTo("Nome Novo");
            verifyNoInteractions(sessoesAtivas);
        }

        @Test
        @DisplayName("mudar o perfil encerra as sessoes do usuario")
        void mudaPerfil() {
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
            when(usuarioRepository.save(usuario)).thenReturn(usuario);

            usuarioService.atualizar(4L, dados(EMAIL, Perfil.MECANICO));

            verify(sessoesAtivas).encerrarSessoesDe(EMAIL);
        }

        @Test
        @DisplayName("mudar o e-mail encerra as sessoes abertas com o e-mail antigo")
        void mudaEmail() {
            when(usuarioRepository.findByEmailIgnoreCase("novo@teste.com.br")).thenReturn(Optional.empty());
            when(usuarioRepository.save(usuario)).thenReturn(usuario);

            usuarioService.atualizar(4L, dados("novo@teste.com.br", Perfil.ATENDENTE));

            assertThat(usuario.getEmail()).isEqualTo("novo@teste.com.br");
            verify(sessoesAtivas).encerrarSessoesDe(EMAIL);
        }
    }

    @Nested
    @DisplayName("situacao e exclusao")
    class Situacao {

        @BeforeEach
        void usuarioExiste() {
            when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
        }

        @Test
        @DisplayName("inativar encerra as sessoes do usuario")
        void inativa() {
            usuarioService.inativar(4L);

            assertThat(usuario.isAtivo()).isFalse();
            verify(sessoesAtivas).encerrarSessoesDe(EMAIL);
        }

        @Test
        @DisplayName("reativar zera tentativas e desbloqueia a conta")
        void reativa() {
            usuario.setAtivo(false);
            usuario.setTentativasFalhas(3);
            usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(10));

            usuarioService.reativar(4L);

            assertThat(usuario.isAtivo()).isTrue();
            assertThat(usuario.getTentativasFalhas()).isZero();
            assertThat(usuario.getBloqueadoAte()).isNull();
        }

        @Test
        @DisplayName("usuario com movimentacao nao e excluido")
        void exclusaoComMovimentacao() {
            when(movimentacaoRepository.existsByUsuarioId(4L)).thenReturn(true);

            assertThatThrownBy(() -> usuarioService.excluir(4L))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Inative o cadastro");
            verify(usuarioRepository, never()).delete(any());
            verifyNoInteractions(sessoesAtivas);
        }

        @Test
        @DisplayName("usuario com despesa lancada nao e excluido")
        void exclusaoComDespesa() {
            when(movimentacaoRepository.existsByUsuarioId(4L)).thenReturn(false);
            when(despesaRepository.existsByUsuarioId(4L)).thenReturn(true);

            assertThatThrownBy(() -> usuarioService.excluir(4L)).isInstanceOf(RegraNegocioException.class);
            verify(usuarioRepository, never()).delete(any());
        }

        @Test
        @DisplayName("usuario sem lancamentos e excluido com o historico de senhas e perde as sessoes")
        void exclui() {
            List<SenhaHistorico> senhas = List.of(historico("antiga123"));
            when(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario)).thenReturn(senhas);

            usuarioService.excluir(4L);

            verify(senhaHistoricoRepository).deleteAll(senhas);
            verify(usuarioRepository).delete(usuario);
            verify(sessoesAtivas).encerrarSessoesDe(EMAIL);
        }
    }

    @Test
    @DisplayName("id inexistente e recusado sem encerrar sessao alguma")
    void idInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.inativar(99L))
                .isInstanceOf(RegistroNaoEncontradoException.class);
        verifyNoInteractions(sessoesAtivas);
    }

    @Nested
    @DisplayName("troca de senha pelo proprio usuario")
    class TrocaDeSenha {

        @BeforeEach
        void usuarioExiste() {
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
            comparacaoPrevisivel();
        }

        @Test
        @DisplayName("senha atual incorreta e recusada")
        void senhaAtualIncorreta() {
            assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL, "errada123", "nova2026x", "nova2026x"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("senha atual");
        }

        @Test
        @DisplayName("confirmacao diferente e recusada")
        void confirmacaoDiferente() {
            assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL, "senhaAtual1", "nova2026x", "nova2026y"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("confirmacao");
        }

        @Test
        @DisplayName("repetir uma das tres ultimas senhas e recusado")
        void repeteUmaDasTresUltimas() {
            when(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario)).thenReturn(List.of(
                    historico("senhaAtual1"), historico("segunda22"), historico("terceira3")));

            assertThatThrownBy(() -> usuarioService.alterarSenha(EMAIL, "senhaAtual1", "terceira3", "terceira3"))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("3 ultimas senhas");
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("a quarta senha mais antiga ja pode ser reutilizada")
        void quartaSenhaPodeVoltar() {
            when(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario)).thenReturn(List.of(
                    historico("senhaAtual1"), historico("segunda22"), historico("terceira3"),
                    historico("quarta444")));
            codificadorPrevisivel();

            usuarioService.alterarSenha(EMAIL, "senhaAtual1", "quarta444", "quarta444");

            assertThat(usuario.getSenha()).isEqualTo("hash:quarta444");
            verify(usuarioRepository).save(usuario);
            verify(senhaHistoricoRepository).save(any(SenhaHistorico.class));
        }
    }

    @Test
    @DisplayName("a redefinicao pelo proprietario desbloqueia a conta e derruba as sessoes")
    void redefinicaoDeSenha() {
        when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
        when(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario)).thenReturn(List.of());
        codificadorPrevisivel();
        usuario.setTentativasFalhas(4);
        usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(5));

        usuarioService.redefinirSenha(4L, "provisoria9");

        assertThat(usuario.getSenha()).isEqualTo("hash:provisoria9");
        assertThat(usuario.getTentativasFalhas()).isZero();
        assertThat(usuario.getBloqueadoAte()).isNull();
        verify(sessoesAtivas).encerrarSessoesDe(EMAIL);
    }

    @Nested
    @DisplayName("tentativas de login")
    class TentativasDeLogin {

        @Test
        @DisplayName("e-mail desconhecido nao grava nada nem audita")
        void emailDesconhecido() {
            when(usuarioRepository.findByEmailIgnoreCase("ninguem@teste.com.br")).thenReturn(Optional.empty());

            usuarioService.registrarFalhaDeLogin("ninguem@teste.com.br");

            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(auditoriaService);
        }

        @Test
        @DisplayName("a quarta falha seguida ainda nao bloqueia")
        void quartaFalha() {
            usuario.setTentativasFalhas(3);
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

            usuarioService.registrarFalhaDeLogin(EMAIL);

            assertThat(usuario.getTentativasFalhas()).isEqualTo(4);
            assertThat(usuario.getBloqueadoAte()).isNull();
            verify(auditoriaService).registrarComoUsuario(eq(EMAIL), eq(AcaoAuditoria.LOGIN_FALHA), eq("Usuario"),
                    eq(4L), anyString());
        }

        @Test
        @DisplayName("a quinta falha bloqueia por 15 minutos e reinicia a contagem")
        void quintaFalhaBloqueia() {
            usuario.setTentativasFalhas(4);
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

            usuarioService.registrarFalhaDeLogin(EMAIL);

            assertThat(usuario.getBloqueadoAte())
                    .isCloseTo(LocalDateTime.now().plusMinutes(15), within(5, ChronoUnit.SECONDS));
            assertThat(usuario.isBloqueado()).isTrue();
            assertThat(usuario.getTentativasFalhas()).isZero();
            verify(auditoriaService).registrarComoUsuario(eq(EMAIL), eq(AcaoAuditoria.BLOQUEIO_CONTA),
                    eq("Usuario"), eq(4L), anyString());
            verify(usuarioRepository).save(usuario);
        }

        @Test
        @DisplayName("o login com sucesso zera as falhas, desbloqueia e audita")
        void loginComSucesso() {
            usuario.setTentativasFalhas(2);
            usuario.setBloqueadoAte(LocalDateTime.now().minusMinutes(1));
            when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));

            usuarioService.registrarLoginComSucesso(EMAIL);

            assertThat(usuario.getTentativasFalhas()).isZero();
            assertThat(usuario.getBloqueadoAte()).isNull();
            verify(auditoriaService).registrarComoUsuario(eq(EMAIL), eq(AcaoAuditoria.LOGIN), eq("Usuario"),
                    eq(4L), anyString());
        }
    }
}
