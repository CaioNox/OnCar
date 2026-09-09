package br.com.oncar.web.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpos de requisicao aceitos pela API REST.
 */
public final class RequisicaoDtos {

    private RequisicaoDtos() {
    }

    public record Login(
            @NotBlank(message = "Informe o e-mail") @Email(message = "Informe um e-mail valido") String email,
            @NotBlank(message = "Informe a senha") String senha) {
    }

    /** RF08: entrada de mercadoria. */
    public record Entrada(
            @NotNull(message = "Selecione o produto") Long produtoId,
            @NotNull(message = "Informe a quantidade recebida")
            @Positive(message = "A quantidade deve ser maior que zero") BigDecimal quantidade,
            @NotNull(message = "Informe o custo unitario da compra")
            @PositiveOrZero(message = "O custo unitario nao pode ser negativo") BigDecimal custoUnitario,
            Long fornecedorId,
            String documento,
            LocalDate competencia) {
    }

    /** RF09: saida de estoque. */
    public record Saida(
            @NotNull(message = "Selecione o produto") Long produtoId,
            @NotNull(message = "Informe a quantidade")
            @Positive(message = "A quantidade deve ser maior que zero") BigDecimal quantidade,
            @NotBlank(message = "Selecione o motivo da saida") String motivo,
            Long clienteId,
            String documento,
            LocalDate competencia) {
    }

    /** RF11 e RN006: ajuste de inventario. */
    public record Ajuste(
            @NotNull(message = "Selecione o produto") Long produtoId,
            @NotNull(message = "Informe o saldo apurado na contagem")
            @PositiveOrZero(message = "O saldo apurado nao pode ser negativo") BigDecimal saldoApurado,
            @Size(min = 10, max = 500, message = "A justificativa deve conter no minimo 10 caracteres")
            String justificativa,
            LocalDate competencia) {
    }

    /** RN008: estorno de movimentacao. */
    public record Estorno(
            @Size(min = 10, max = 500, message = "A justificativa deve conter no minimo 10 caracteres")
            String justificativa) {
    }
}
