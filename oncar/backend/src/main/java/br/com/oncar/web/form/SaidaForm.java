package br.com.oncar.web.form;

import br.com.oncar.domain.enums.MotivoMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class SaidaForm {

    @NotNull(message = "Selecione o produto")
    private Long produtoId;

    @NotNull(message = "Informe a quantidade")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "Selecione o motivo da saida")
    private MotivoMovimentacao motivo;

    private Long clienteId;

    private String documento;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate competencia = LocalDate.now();

    public Long getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Long produtoId) {
        this.produtoId = produtoId;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public MotivoMovimentacao getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoMovimentacao motivo) {
        this.motivo = motivo;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public void setCompetencia(LocalDate competencia) {
        this.competencia = competencia;
    }
}
