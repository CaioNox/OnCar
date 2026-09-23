package br.com.oncar.domain.enums;

public enum AcaoAuditoria {

    INCLUSAO("Inclusao"),
    ALTERACAO("Alteracao"),
    EXCLUSAO("Exclusao"),
    INATIVACAO("Inativacao"),
    REATIVACAO("Reativacao"),
    LOGIN("Login"),
    LOGIN_FALHA("Falha de login"),
    BLOQUEIO_CONTA("Bloqueio de conta"),
    ACESSO_NEGADO("Acesso negado"),
    MARGEM_NEGATIVA("Confirmacao de margem negativa");

    private final String descricao;

    AcaoAuditoria(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
