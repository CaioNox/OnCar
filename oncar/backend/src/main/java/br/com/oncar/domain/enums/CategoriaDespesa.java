package br.com.oncar.domain.enums;

public enum CategoriaDespesa {

    ALUGUEL("Aluguel"),
    ENERGIA("Energia eletrica"),
    AGUA("Agua e esgoto"),
    TELEFONE_INTERNET("Telefone e internet"),
    FERRAMENTAS("Ferramentas e equipamentos"),
    SERVICOS_TERCEIROS("Servicos de terceiros"),
    IMPOSTOS_TAXAS("Impostos e taxas"),
    SALARIOS("Salarios e encargos"),
    OUTRAS("Outras despesas");

    private final String descricao;

    CategoriaDespesa(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
