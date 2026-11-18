package br.com.oncar.service.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.service.relatorio.Relatorios.ResumoMensal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    private static final YearMonth JANEIRO = YearMonth.of(2026, 1);

    @Test
    @DisplayName("mes inicial ou final ausente e recusado")
    void mesAusente() {
        assertThatThrownBy(() -> new Periodo(null, JANEIRO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mes inicial e o mes final");
        assertThatThrownBy(() -> new Periodo(JANEIRO, null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("periodo invertido e recusado")
    void invertido() {
        assertThatThrownBy(() -> new Periodo(JANEIRO, JANEIRO.minusMonths(1)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("anterior ou igual");
    }

    @Test
    @DisplayName("24 meses e o maximo aceito; 25 e recusado")
    void limiteDeVinteEQuatroMeses() {
        assertThatCode(() -> new Periodo(JANEIRO, JANEIRO.plusMonths(23))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Periodo(JANEIRO, JANEIRO.plusMonths(24)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("no maximo 24 meses");
    }

    @Test
    @DisplayName("os meses vao do inicial ao final inclusive, atravessando o ano")
    void mesesDoPeriodo() {
        Periodo periodo = new Periodo(YearMonth.of(2025, 11), YearMonth.of(2026, 2));

        assertThat(periodo.meses()).containsExactly(YearMonth.of(2025, 11), YearMonth.of(2025, 12),
                YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    @DisplayName("primeiro e ultimo dia respeitam o fim de fevereiro em ano bissexto")
    void limitesDoPeriodo() {
        Periodo periodo = Periodo.doMes(YearMonth.of(2028, 2));

        assertThat(periodo.primeiroDia()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(periodo.ultimoDia()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(periodo.abertura()).isEqualTo(LocalDateTime.of(2028, 2, 1, 0, 0));
        assertThat(periodo.encerramento()).as("limite exclusivo").isEqualTo(LocalDateTime.of(2028, 3, 1, 0, 0));
    }

    @Test
    @DisplayName("sem meses informados vale o mes corrente")
    void informadoSemMeses() {
        assertThat(Periodo.informado(null, null)).isEqualTo(Periodo.doMes(YearMonth.now()));
    }

    @Test
    @DisplayName("com so um dos meses informado, o periodo e aquele mes")
    void informadoComUmMes() {
        assertThat(Periodo.informado(JANEIRO, null)).isEqualTo(Periodo.doMes(JANEIRO));
        assertThat(Periodo.informado(null, JANEIRO)).isEqualTo(Periodo.doMes(JANEIRO));
    }

    @Test
    @DisplayName("a descricao usa o nome do mes em portugues com inicial maiuscula")
    void descricao() {
        assertThat(Periodo.doMes(YearMonth.of(2026, 3)).descricao()).isEqualTo("Março/2026");
        assertThat(new Periodo(JANEIRO, YearMonth.of(2026, 2)).descricao())
                .isEqualTo("Janeiro/2026 a Fevereiro/2026");
    }

    @Test
    @DisplayName("o consolidado de um periodo (mes nulo) e chamado de total do periodo")
    void nomeDoConsolidado() {
        ResumoMensal consolidado = new ResumoMensal(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(consolidado.nomeDoMes()).isEqualTo("Total do periodo");
    }
}
