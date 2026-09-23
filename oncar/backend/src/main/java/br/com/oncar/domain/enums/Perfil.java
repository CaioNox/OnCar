package br.com.oncar.domain.enums;

public enum Perfil {

    PROPRIETARIO("Proprietario"),
    GERENTE_ESTOQUE("Gerente de Estoque"),
    ATENDENTE("Atendente"),
    MECANICO("Mecanico");

    public static final String ROLE_PREFIX = "ROLE_";

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getAuthority() {
        return ROLE_PREFIX + name();
    }
}
