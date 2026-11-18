package br.com.oncar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovimentacaoTest {

    @Test
    @DisplayName("o valor total e quantidade vezes custo unitario")
    void valorTotal() {
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setQuantidade(new BigDecimal("2.500"));
        movimentacao.setCustoUnitario(new BigDecimal("12.3456"));

        assertThat(movimentacao.getValorTotal()).isEqualByComparingTo("30.864");
    }

    @Test
    @DisplayName("sem quantidade ou sem custo o valor total e zero")
    void valorTotalComNulos() {
        Movimentacao semCusto = new Movimentacao();
        semCusto.setQuantidade(BigDecimal.ONE);
        Movimentacao semQuantidade = new Movimentacao();
        semQuantidade.setCustoUnitario(BigDecimal.ONE);

        assertThat(semCusto.getValorTotal()).isEqualByComparingTo("0");
        assertThat(semQuantidade.getValorTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("o mes de competencia vem da data de competencia, e e nulo sem ela")
    void mesDeCompetencia() {
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setCompetencia(LocalDate.of(2026, 12, 31));
        assertThat(movimentacao.getMesCompetencia()).isEqualTo(YearMonth.of(2026, 12));

        movimentacao.setCompetencia(null);
        assertThat(movimentacao.getMesCompetencia()).isNull();
    }

    @Test
    @DisplayName("o mes de competencia da despesa segue a mesma regra")
    void mesDeCompetenciaDaDespesa() {
        Despesa despesa = new Despesa();
        assertThat(despesa.getMesCompetencia()).isNull();

        despesa.setCompetencia(LocalDate.of(2026, 1, 1));
        assertThat(despesa.getMesCompetencia()).isEqualTo(YearMonth.of(2026, 1));
    }
}
