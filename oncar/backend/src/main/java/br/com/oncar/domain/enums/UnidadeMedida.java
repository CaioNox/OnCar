package br.com.oncar.domain.enums;

public enum UnidadeMedida {

    UNIDADE("UN"),
    LITRO("L"),
    QUILOGRAMA("KG"),
    METRO("M"),
    PECA("PC"),
    CAIXA("CX");

    private final String sigla;

    UnidadeMedida(String sigla) {
        this.sigla = sigla;
    }

    public String getSigla() {
        return sigla;
    }
}
