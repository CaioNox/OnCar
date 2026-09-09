package br.com.oncar.domain.enums;

public enum CategoriaProduto {

    PECA("Peca"),
    LUBRIFICANTE("Lubrificante"),
    FILTRO("Filtro"),
    INSUMO("Insumo"),
    FERRAMENTA("Ferramenta"),
    ACESSORIO("Acessorio"),
    OUTRO("Outro");

    private final String descricao;

    CategoriaProduto(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
