package br.com.oncar.web.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class AjusteForm {

    @NotNull(message = "Selecione o produto")
    private Long produtoId;

    @NotNull(message = "Informe o saldo apurado na contagem")
    @PositiveOrZero(message = "O saldo apurado nao pode ser negativo")
    private BigDecimal saldoApurado;

    @Size(min = 10, max = 500, message = "A justificativa deve conter no minimo 10 caracteres")
    private String justificativa;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate competencia = LocalDate.now();

    public Long getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Long produtoId) {
        this.produtoId = produtoId;
    }

    public BigDecimal getSaldoApurado() {
        return saldoApurado;
    }

    public void setSaldoApurado(BigDecimal saldoApurado) {
        this.saldoApurado = saldoApurado;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public void setCompetencia(LocalDate competencia) {
        this.competencia = competencia;
    }
}
