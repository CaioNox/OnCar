package br.com.oncar.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.oncar.service.relatorio.Relatorios.ProdutoGiro;
import br.com.oncar.service.relatorio.Relatorios.ResumoMensal;
import br.com.oncar.web.controller.GraficoMensal.BarraDeGiro;
import br.com.oncar.web.controller.GraficoMensal.Coluna;
import br.com.oncar.web.controller.GraficoMensal.Marca;
import br.com.oncar.web.controller.GraficoMensal.ReceitaDespesa;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GraficoMensalTest {

    private static ResumoMensal mes(YearMonth mes, String receita, String despesa) {
        BigDecimal zero = BigDecimal.ZERO;
        return new ResumoMensal(mes, new BigDecimal(receita), zero, zero, zero, zero, new BigDecimal(despesa),
                zero, zero);
    }

    private static ProdutoGiro giro(String sku, String quantidade) {
        return new ProdutoGiro(1L, sku, "Produto " + sku, "UN", new BigDecimal(quantidade), 1, null);
    }

    @ParameterizedTest(name = "maior {0} -> topo {1}")
    @CsvSource({"0, 1000", "-50, 1000", "90, 100", "3800, 4000", "4000, 4000", "4001, 8000", "950000, 1000000"})
    @DisplayName("o topo do eixo e um numero redondo que comporta o maior valor")
    void escalaArredondada(String maior, String topo) {
        BigDecimal escala = GraficoMensal.escalaArredondada(new BigDecimal(maior));

        assertThat(escala).isEqualByComparingTo(topo);
        assertThat(escala).isGreaterThanOrEqualTo(new BigDecimal(maior));
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource({"0, 0", "500, 500", "999, 999", "1000, 1 mil", "1500, '1,5 mil'", "12345, '12,3 mil'",
            "2000000, 2 mi", "2500000, '2,5 mi'"})
    @DisplayName("os rotulos do eixo usam mil e mi")
    void rotuloCompacto(String valor, String rotulo) {
        assertThat(GraficoMensal.compacto(new BigDecimal(valor))).isEqualTo(rotulo);
    }

    @Test
    @DisplayName("as alturas sao percentuais do topo e so a ultima coluna e o mes atual")
    void colunas() {
        ReceitaDespesa grafico = GraficoMensal.receitaDespesa(List.of(
                mes(YearMonth.of(2026, 1), "1000", "500"),
                mes(YearMonth.of(2026, 2), "2000", "3000")));

        assertThat(grafico.vazio()).isFalse();
        assertThat(grafico.colunas()).extracting(Coluna::mes, Coluna::alturaReceita, Coluna::alturaDespesa,
                Coluna::atual).containsExactly(
                tuple("jan/26", 25, 13, false),
                tuple("fev/26", 50, 75, true));
        assertThat(grafico.colunas().get(0).mesCompleto()).isEqualTo("Janeiro/2026");
        assertThat(grafico.eixo()).extracting(Marca::rotulo, Marca::posicao).containsExactly(
                tuple("4 mil", 100), tuple("3 mil", 75), tuple("2 mil", 50), tuple("1 mil", 25), tuple("0", 0));
    }

    @Test
    @DisplayName("sem movimento o grafico fica vazio, com escala padrao e colunas zeradas")
    void semMovimento() {
        ReceitaDespesa grafico = GraficoMensal.receitaDespesa(List.of(mes(YearMonth.of(2026, 1), "0", "0")));

        assertThat(grafico.vazio()).isTrue();
        assertThat(grafico.colunas().get(0).alturaReceita()).isZero();
        assertThat(grafico.eixo().get(0).rotulo()).isEqualTo("1 mil");
    }

    @Test
    @DisplayName("sem meses nao ha colunas, mas o eixo continua desenhado")
    void semMeses() {
        ReceitaDespesa grafico = GraficoMensal.receitaDespesa(List.of());

        assertThat(grafico.colunas()).isEmpty();
        assertThat(grafico.eixo()).hasSize(5);
        assertThat(grafico.vazio()).isTrue();
    }

    @Test
    @DisplayName("a barra de giro e proporcional a maior e nunca some (minimo de 2%)")
    void barrasDeGiro() {
        List<BarraDeGiro> barras = GraficoMensal.giro(List.of(giro("A", "10"), giro("B", "1"), giro("C", "0.1")));

        assertThat(barras).extracting(BarraDeGiro::largura).containsExactly(100, 10, 2);
        assertThat(GraficoMensal.giro(List.of())).isEmpty();
    }
}
