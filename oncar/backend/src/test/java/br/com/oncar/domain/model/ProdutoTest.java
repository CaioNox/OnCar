package br.com.oncar.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProdutoTest {

    @Test
    @DisplayName("o SKU e gravado sem espacos nas pontas e normalizado em maiusculas para a unicidade")
    void normalizaSku() {
        Produto produto = new Produto();

        produto.setSku("  fl-001 ");

        assertThat(produto.getSku()).isEqualTo("fl-001");
        assertThat(produto.getSkuNormalizado()).isEqualTo("FL-001");
    }

    @Test
    @DisplayName("SKU nulo continua nulo nos dois campos")
    void skuNulo() {
        Produto produto = new Produto();

        produto.setSku(null);

        assertThat(produto.getSku()).isNull();
        assertThat(produto.getSkuNormalizado()).isNull();
        assertThat(Produto.normalizarSku(null)).isNull();
    }

    @ParameterizedTest(name = "saldo {0}, minimo {1} -> {2}")
    @CsvSource({"4, 5, true", "5, 5, true", "5.000, 5, true", "5.001, 5, false", "0, 0, true"})
    @DisplayName("o produto entra na reposicao ao atingir o minimo, nao so ao passar dele")
    void abaixoDoMinimo(String saldo, String minimo, boolean abaixo) {
        Produto produto = new Produto();
        produto.setSaldo(new BigDecimal(saldo));
        produto.setEstoqueMinimo(new BigDecimal(minimo));

        assertThat(produto.isAbaixoDoMinimo()).isEqualTo(abaixo);
    }

    @Test
    @DisplayName("sem saldo ou minimo nao ha alerta de reposicao")
    void abaixoDoMinimoComNulos() {
        Produto produto = new Produto();
        produto.setSaldo(null);
        assertThat(produto.isAbaixoDoMinimo()).isFalse();

        produto.setSaldo(BigDecimal.ONE);
        produto.setEstoqueMinimo(null);
        assertThat(produto.isAbaixoDoMinimo()).isFalse();
    }

    @Test
    @DisplayName("o valor em estoque e saldo vezes custo medio, e zero quando falta um deles")
    void valorEmEstoque() {
        Produto produto = new Produto();
        produto.setSaldo(new BigDecimal("2.5"));
        produto.setPrecoCusto(new BigDecimal("10.2"));
        assertThat(produto.getValorEmEstoque()).isEqualByComparingTo("25.5");

        produto.setPrecoCusto(null);
        assertThat(produto.getValorEmEstoque()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("um produto novo nasce ativo e zerado")
    void padroes() {
        Produto produto = new Produto();

        assertThat(produto.isAtivo()).isTrue();
        assertThat(produto.getSaldo()).isEqualByComparingTo("0");
        assertThat(produto.getPrecoCusto()).isEqualByComparingTo("0");
    }
}
