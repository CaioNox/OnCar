package br.com.oncar.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FormatosTest {

    private final Formatos formatos = new Formatos();

    /** O JDK separa "R$" do numero com espaco nao separavel; o teste compara o texto visivel. */
    private static String visivel(String texto) {
        return texto.replace(' ', ' ');
    }

    @Test
    @DisplayName("valor ausente aparece como traco em todos os formatos")
    void valorAusente() {
        assertThat(formatos.moeda(null)).isEqualTo("-");
        assertThat(formatos.quantidade(null)).isEqualTo("-");
        assertThat(formatos.variacao(null)).isEqualTo("0");
    }

    @Test
    @DisplayName("moeda em reais, com milhar por ponto e centavos por virgula")
    void moeda() {
        assertThat(visivel(formatos.moeda(new BigDecimal("1234.5")))).isEqualTo("R$ 1.234,50");
        assertThat(visivel(formatos.moeda(new BigDecimal("-10")))).contains("10,00").contains("-");
    }

    /**
     * Registro de comportamento: a tela arredonda meio centavo pelo padrao do NumberFormat
     * (HALF_EVEN), enquanto os servicos gravam com HALF_UP. Na pratica os valores ja chegam
     * com dois centavos, entao a diferenca nao aparece; o teste evita que ela passe despercebida.
     */
    @Test
    @DisplayName("meio centavo e arredondado para o par (HALF_EVEN do NumberFormat)")
    void meioCentavo() {
        assertThat(visivel(formatos.moeda(new BigDecimal("0.005")))).isEqualTo("R$ 0,00");
        assertThat(visivel(formatos.moeda(new BigDecimal("0.015")))).isEqualTo("R$ 0,02");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"5.000, 5", "2.500, '2,5'", "0.125, '0,125'", "1234.5, '1.234,5'", "0.0004, 0"})
    @DisplayName("quantidade sem zeros sobrando e com no maximo tres casas")
    void quantidade(String valor, String esperado) {
        assertThat(formatos.quantidade(new BigDecimal(valor))).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"2.5, '+2,5'", "-3.000, -3", "0.000, 0"})
    @DisplayName("variacao mostra o sinal explicito, e zero sem sinal")
    void variacao(String valor, String esperado) {
        assertThat(formatos.variacao(new BigDecimal(valor))).isEqualTo(esperado);
    }
}
