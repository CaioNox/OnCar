package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Usa o SessionRegistryImpl do Spring Security de verdade: e um registro em memoria,
 * sem I/O, e simula-lo esconderia justamente o comportamento que importa (expirar a sessao).
 */
class SessoesAtivasServiceTest {

    private static final String EMAIL = "gerente@teste.com.br";

    private SessionRegistryImpl registro;
    private SessoesAtivasService sessoes;

    @BeforeEach
    void montar() {
        registro = new SessionRegistryImpl();
        sessoes = new SessoesAtivasService(registro);
    }

    @AfterEach
    void encerrarSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static UserDetails usuario(String email) {
        return User.withUsername(email).password("x").roles("GERENTE_ESTOQUE").build();
    }

    private boolean expirada(String idDaSessao) {
        return registro.getSessionInformation(idDaSessao).isExpired();
    }

    @Test
    @DisplayName("encerra todas as sessoes do usuario, nos dois formatos de principal")
    void encerraTodasAsSessoes() {
        sessoes.registrar("tela", usuario(EMAIL));
        sessoes.registrar("api", EMAIL);

        sessoes.encerrarSessoesDe(EMAIL);

        assertThat(expirada("tela")).isTrue();
        assertThat(expirada("api")).isTrue();
    }

    @Test
    @DisplayName("o e-mail e comparado sem diferenciar caixa e sem os espacos das pontas")
    void comparaEmailSemCaixa() {
        sessoes.registrar("tela", usuario("Gerente@Teste.com.br"));

        sessoes.encerrarSessoesDe("  GERENTE@teste.com.br ");

        assertThat(expirada("tela")).isTrue();
    }

    @Test
    @DisplayName("as sessoes de outros usuarios nao sao afetadas")
    void naoAfetaOutros() {
        sessoes.registrar("minha", usuario(EMAIL));
        sessoes.registrar("alheia", usuario("atendente@teste.com.br"));

        sessoes.encerrarSessoesDe(EMAIL);

        assertThat(expirada("minha")).isTrue();
        assertThat(expirada("alheia")).isFalse();
    }

    @Test
    @DisplayName("encerrar as outras sessoes preserva a sessao de quem fez a alteracao")
    void preservaSessaoAtual() {
        sessoes.registrar("este-navegador", usuario(EMAIL));
        sessoes.registrar("celular", usuario(EMAIL));

        sessoes.encerrarOutrasSessoesDe(EMAIL, "este-navegador");

        assertThat(expirada("este-navegador")).isFalse();
        assertThat(expirada("celular")).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("e-mail ausente nao encerra sessao nenhuma")
    void emailAusente(String email) {
        sessoes.registrar("tela", usuario(EMAIL));

        sessoes.encerrarSessoesDe(email);

        assertThat(expirada("tela")).isFalse();
    }

    @Test
    @DisplayName("dentro de uma transacao so encerra depois do commit")
    void esperaOCommit() {
        sessoes.registrar("tela", usuario(EMAIL));
        TransactionSynchronizationManager.initSynchronization();

        sessoes.encerrarSessoesDe(EMAIL);
        assertThat(expirada("tela")).as("antes do commit").isFalse();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(expirada("tela")).as("depois do commit").isTrue();
    }

    @Test
    @DisplayName("transacao desfeita nao derruba ninguem")
    void rollbackNaoEncerra() {
        sessoes.registrar("tela", usuario(EMAIL));
        TransactionSynchronizationManager.initSynchronization();

        sessoes.encerrarSessoesDe(EMAIL);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacao -> sincronizacao.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(expirada("tela")).isFalse();
    }
}
