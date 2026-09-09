package br.com.oncar.domain.enums;

/**
 * Perfis de acesso previstos no item 4.2 da documentacao e na RN007.
 * A constante {@link #ROLE_PREFIX} mantem os nomes alinhados as authorities do Spring Security.
 */
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
