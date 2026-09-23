package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.TipoMovimentacao;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EstoqueServiceTest extends TesteIntegracao {

    @Autowired
    private EstoqueService estoqueService;

    private Produto produto;

    @BeforeEach
    void prepararCenario() {
        criarGerente();
        produto = criarProduto("FL-001", new BigDecimal("5"));
    }

    @Test
    @DisplayName("RN003: a entrada soma ao saldo e o lancamento registra o saldo resultante")
    void entradaAtualizaSaldo() {
        Movimentacao movimentacao = estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"),
                new BigDecimal("20.00"), null, "NF-e 1", null, EMAIL_GERENTE);

        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("10");
        assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(movimentacao.getSaldoAnterior()).isEqualByComparingTo("0");
        assertThat(movimentacao.getSaldoResultante()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("RN004: o custo medio ponderado e recalculado a cada entrada")
    void entradaRecalculaCustoMedioPonderado() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("30.00"),
                null, null, null, EMAIL_GERENTE);

        // (10 x 20 + 10 x 30) / 20 = 25
        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getPrecoCusto())
                .isEqualByComparingTo("25.0000");
    }

    @Test
    @DisplayName("RN002: saida maior que o saldo e integralmente rejeitada")
    void saidaAcimaDoSaldoEhRejeitada() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);

        assertThatThrownBy(() -> estoqueService.registrarSaida(produto.getId(), new BigDecimal("15"),
                MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL_GERENTE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("10");
        assertThat(movimentacaoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RN003: a saida subtrai do saldo no mesmo instante da confirmacao")
    void saidaAtualizaSaldo() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);
        estoqueService.registrarSaida(produto.getId(), new BigDecimal("4"),
                MotivoMovimentacao.ORDEM_SERVICO, null, "OS 100", null, EMAIL_GERENTE);

        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("RN006: o ajuste exige justificativa com no minimo dez caracteres")
    void ajusteExigeJustificativa() {
        assertThatThrownBy(() -> estoqueService.ajustarInventario(produto.getId(), new BigDecimal("3"),
                "erro", null, EMAIL_GERENTE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("justificativa");

        assertThat(movimentacaoRepository.count()).isZero();
    }

    @Test
    @DisplayName("RN006: o ajuste corrige o saldo e registra saldo anterior e apurado")
    void ajusteCorrigeSaldo() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);

        Movimentacao ajuste = estoqueService.ajustarInventario(produto.getId(), new BigDecimal("8"),
                "Contagem fisica de janeiro", null, EMAIL_GERENTE);

        assertThat(ajuste.getTipo()).isEqualTo(TipoMovimentacao.AJUSTE);
        assertThat(ajuste.getSaldoAnterior()).isEqualByComparingTo("10");
        assertThat(ajuste.getSaldoResultante()).isEqualByComparingTo("8");
        assertThat(ajuste.getQuantidade()).isEqualByComparingTo("2");
        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("RN008: o estorno gera lancamento contrario e desfaz o efeito no saldo e no custo")
    void estornoDesfazEntrada() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);
        Movimentacao segunda = estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"),
                new BigDecimal("30.00"), null, null, null, EMAIL_GERENTE);

        Movimentacao estorno = estoqueService.estornar(segunda.getId(),
                "Nota fiscal lancada em duplicidade", EMAIL_GERENTE);

        assertThat(estorno.getTipo()).isEqualTo(TipoMovimentacao.ESTORNO);
        assertThat(estorno.getMovimentacaoOrigem().getId()).isEqualTo(segunda.getId());
        assertThat(movimentacaoRepository.findById(segunda.getId()).orElseThrow().isEstornada()).isTrue();

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertThat(atualizado.getSaldo()).isEqualByComparingTo("10");
        assertThat(atualizado.getPrecoCusto()).isEqualByComparingTo("20.0000");
    }

    @Test
    @DisplayName("RN008: uma movimentacao ja estornada nao pode ser estornada novamente")
    void estornoDuplicadoEhRejeitado() {
        Movimentacao entrada = estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"),
                new BigDecimal("20.00"), null, null, null, EMAIL_GERENTE);
        estoqueService.estornar(entrada.getId(), "Lancamento equivocado", EMAIL_GERENTE);

        assertThatThrownBy(() -> estoqueService.estornar(entrada.getId(),
                "Nova tentativa de estorno", EMAIL_GERENTE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ja foi estornada");
    }

    @Test
    @DisplayName("RN008: o estorno tambem exige justificativa")
    void estornoExigeJustificativa() {
        Movimentacao entrada = estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"),
                new BigDecimal("20.00"), null, null, null, EMAIL_GERENTE);

        assertThatThrownBy(() -> estoqueService.estornar(entrada.getId(), "curto", EMAIL_GERENTE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    @DisplayName("RN002: o estorno de entrada e recusado quando o saldo ja foi consumido")
    void estornoDeEntradaSemSaldoEhRejeitado() {
        Movimentacao entrada = estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"),
                new BigDecimal("20.00"), null, null, null, EMAIL_GERENTE);
        estoqueService.registrarSaida(produto.getId(), new BigDecimal("6"),
                MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL_GERENTE);

        assertThatThrownBy(() -> estoqueService.estornar(entrada.getId(),
                "Devolucao integral ao fornecedor", EMAIL_GERENTE))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ajuste de inventario");
    }

    @Test
    @DisplayName("RF13: o extrato lista as movimentacoes do produto")
    void extratoListaMovimentacoesDoProduto() {
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("20.00"),
                null, null, null, EMAIL_GERENTE);
        estoqueService.registrarSaida(produto.getId(), new BigDecimal("2"),
                MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL_GERENTE);

        assertThat(estoqueService.listarExtrato(produto.getId())).hasSize(2);
    }
}
