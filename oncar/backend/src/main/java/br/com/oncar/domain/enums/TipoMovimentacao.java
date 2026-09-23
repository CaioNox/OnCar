package br.com.oncar.domain.enums;

public enum TipoMovimentacao {

    ENTRADA("Entrada", 1),
    SAIDA("Saida", -1),
    AJUSTE("Ajuste de inventario", 0),
    ESTORNO("Estorno", 0);

    private final String descricao;
    private final int sinal;

    TipoMovimentacao(String descricao, int sinal) {
        this.descricao = descricao;
        this.sinal = sinal;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getSinal() {
        return sinal;
    }
}
