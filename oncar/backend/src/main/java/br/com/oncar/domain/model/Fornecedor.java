package br.com.oncar.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "fornecedor")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe a razao social")
    @Size(min = 3, max = 120, message = "A razao social deve conter entre 3 e 120 caracteres")
    @Column(name = "razao_social", nullable = false, length = 120)
    private String razaoSocial;

    @NotBlank(message = "Informe o CNPJ")
    @Size(min = 14, max = 18, message = "Informe um CNPJ valido")
    @Column(name = "cnpj", nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(name = "contato", length = 100)
    private String contato;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Email(message = "Informe um e-mail valido")
    @Column(name = "email", length = 150)
    private String email;

    @PositiveOrZero(message = "O prazo medio de entrega nao pode ser negativo")
    @Column(name = "prazo_medio_entrega")
    private Integer prazoMedioEntrega;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public void setRazaoSocial(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getContato() {
        return contato;
    }

    public void setContato(String contato) {
        this.contato = contato;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getPrazoMedioEntrega() {
        return prazoMedioEntrega;
    }

    public void setPrazoMedioEntrega(Integer prazoMedioEntrega) {
        this.prazoMedioEntrega = prazoMedioEntrega;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
