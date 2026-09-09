package br.com.oncar.domain.enums;

import static br.com.oncar.domain.enums.TipoMovimentacao.AJUSTE;
import static br.com.oncar.domain.enums.TipoMovimentacao.ENTRADA;
import static br.com.oncar.domain.enums.TipoMovimentacao.ESTORNO;
import static br.com.oncar.domain.enums.TipoMovimentacao.SAIDA;

/**
 * Motivo obrigatorio de cada movimentacao (RF08 e RF09).
 */
public enum MotivoMovimentacao {

    COMPRA_FORNECEDOR("Compra de fornecedor", ENTRADA),
    DEVOLUCAO_CLIENTE("Devolucao de cliente", ENTRADA),
    VENDA_BALCAO("Venda de balcao", SAIDA),
    ORDEM_SERVICO("Aplicacao em ordem de servico", SAIDA),
    DEVOLUCAO_FORNECEDOR("Devolucao ao fornecedor", SAIDA),
    PERDA_AVARIA("Perda ou avaria", SAIDA),
    AJUSTE_INVENTARIO("Ajuste de inventario", AJUSTE),
    ESTORNO_MOVIMENTACAO("Estorno de movimentacao", ESTORNO);

    private final String descricao;
    private final TipoMovimentacao tipo;

    MotivoMovimentacao(String descricao, TipoMovimentacao tipo) {
        this.descricao = descricao;
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public boolean ehEntrada() {
        return tipo == ENTRADA;
    }

    public boolean ehSaida() {
        return tipo == SAIDA;
    }
}
