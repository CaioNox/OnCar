package br.com.oncar.web.form;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.service.DespesaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class DespesaForm {

    private Long id;

    @NotBlank(message = "Descreva a despesa")
    @Size(min = 3, max = 120, message = "A descricao deve conter entre 3 e 120 caracteres")
    private String descricao;

    @NotNull(message = "Selecione a categoria da despesa")
    private CategoriaDespesa categoria;

    @NotNull(message = "Informe o valor da despesa")
    @Positive(message = "O valor deve ser maior que zero")
    private BigDecimal valor;

    @NotNull(message = "Informe a data de competencia")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate competencia = LocalDate.now();

    private Long fornecedorId;

    @Size(max = 60, message = "O documento deve conter no maximo 60 caracteres")
    private String documento;

    @Size(max = 500, message = "A observacao deve conter no maximo 500 caracteres")
    private String observacao;

    public static DespesaForm de(Despesa despesa) {
        DespesaForm form = new DespesaForm();
        form.setId(despesa.getId());
        form.setDescricao(despesa.getDescricao());
        form.setCategoria(despesa.getCategoria());
        form.setValor(despesa.getValor());
        form.setCompetencia(despesa.getCompetencia());
        form.setFornecedorId(despesa.getFornecedor() == null ? null : despesa.getFornecedor().getId());
        form.setDocumento(despesa.getDocumento());
        form.setObservacao(despesa.getObservacao());
        return form;
    }

    public DespesaService.DadosDespesa paraDados() {
        return new DespesaService.DadosDespesa(descricao, categoria, valor, competencia, fornecedorId,
                documento, observacao);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public CategoriaDespesa getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaDespesa categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public void setCompetencia(LocalDate competencia) {
        this.competencia = competencia;
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

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
