package br.com.oncar.domain.model;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.UnidadeMedida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe o SKU do produto")
    @Size(max = 40, message = "O SKU deve conter no maximo 40 caracteres")
    @Column(name = "sku", nullable = false, length = 40)
    private String sku;

    @Column(name = "sku_normalizado", nullable = false, unique = true, length = 40)
    private String skuNormalizado;

    @NotBlank(message = "Informe a descricao do produto")
    @Size(min = 3, max = 120, message = "A descricao deve conter entre 3 e 120 caracteres")
    @Column(name = "descricao", nullable = false, length = 120)
    private String descricao;

    @NotNull(message = "Selecione a categoria")
    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    private CategoriaProduto categoria;

    @NotNull(message = "Selecione a unidade de medida")
    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", nullable = false, length = 20)
    private UnidadeMedida unidadeMedida;

    @Column(name = "marca", length = 60)
    private String marca;

    @Column(name = "aplicacao", length = 120)
    private String aplicacao;

    @NotNull(message = "Informe o preco de venda")
    @PositiveOrZero(message = "O preco de venda nao pode ser negativo")
    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda = BigDecimal.ZERO;

    @Column(name = "preco_custo", nullable = false, precision = 12, scale = 4)
    private BigDecimal precoCusto = BigDecimal.ZERO;

    @NotNull(message = "Informe o estoque minimo")
    @PositiveOrZero(message = "O estoque minimo nao pode ser negativo")
    @Column(name = "estoque_minimo", nullable = false, precision = 12, scale = 3)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    @Column(name = "saldo", nullable = false, precision = 12, scale = 3)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(name = "localizacao", length = 60)
    private String localizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedorPadrao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public boolean isAbaixoDoMinimo() {
        return saldo != null && estoqueMinimo != null && saldo.compareTo(estoqueMinimo) <= 0;
    }

    public BigDecimal getValorEmEstoque() {
        if (saldo == null || precoCusto == null) {
            return BigDecimal.ZERO;
        }
        return saldo.multiply(precoCusto);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku == null ? null : sku.trim();
        this.skuNormalizado = normalizarSku(sku);
    }

    public static String normalizarSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase();
    }

    public String getSkuNormalizado() {
        return skuNormalizado;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public CategoriaProduto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaProduto categoria) {
        this.categoria = categoria;
    }

    public UnidadeMedida getUnidadeMedida() {
        return unidadeMedida;
    }

    public void setUnidadeMedida(UnidadeMedida unidadeMedida) {
        this.unidadeMedida = unidadeMedida;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getAplicacao() {
        return aplicacao;
    }

    public void setAplicacao(String aplicacao) {
        this.aplicacao = aplicacao;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public void setPrecoVenda(BigDecimal precoVenda) {
        this.precoVenda = precoVenda;
    }

    public BigDecimal getPrecoCusto() {
        return precoCusto;
    }

    public void setPrecoCusto(BigDecimal precoCusto) {
        this.precoCusto = precoCusto;
    }

    public BigDecimal getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public void setEstoqueMinimo(BigDecimal estoqueMinimo) {
        this.estoqueMinimo = estoqueMinimo;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public void setLocalizacao(String localizacao) {
        this.localizacao = localizacao;
    }

    public Fornecedor getFornecedorPadrao() {
        return fornecedorPadrao;
    }

    public void setFornecedorPadrao(Fornecedor fornecedorPadrao) {
        this.fornecedorPadrao = fornecedorPadrao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
