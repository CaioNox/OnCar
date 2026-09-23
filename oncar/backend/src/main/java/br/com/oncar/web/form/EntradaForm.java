package br.com.oncar.web.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class EntradaForm {

    @NotNull(message = "Selecione o produto")
    private Long produtoId;

    @NotNull(message = "Informe a quantidade recebida")
    @Positive(message = "A quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "Informe o custo unitario da compra")
    @PositiveOrZero(message = "O custo unitario nao pode ser negativo")
    private BigDecimal custoUnitario;

    private Long fornecedorId;

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

    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public void setCustoUnitario(BigDecimal custoUnitario) {
        this.custoUnitario = custoUnitario;
    }

    public Long getFornecedorId() {
        return fornecedorId;
    }

    public void setFornecedorId(Long fornecedorId) {
        this.fornecedorId = fornecedorId;
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
