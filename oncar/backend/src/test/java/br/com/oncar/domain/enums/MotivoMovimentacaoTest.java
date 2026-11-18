package br.com.oncar.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A baixa de estoque aceita so motivos de saida (EstoqueService.registrarSaida), e o
 * front-end compara as descricoes literais (lib/sinais.ts): mudar um motivo de grupo ou
 * renomear a descricao quebra regras fora deste enum.
 */
class MotivoMovimentacaoTest {

    @Test
    @DisplayName("os motivos de saida sao exatamente venda, ordem de servico, devolucao ao fornecedor e perda")
    void motivosDeSaida() {
        assertThat(Arrays.stream(MotivoMovimentacao.values()).filter(MotivoMovimentacao::ehSaida))
                .containsExactlyInAnyOrder(MotivoMovimentacao.VENDA_BALCAO, MotivoMovimentacao.ORDEM_SERVICO,
                        MotivoMovimentacao.DEVOLUCAO_FORNECEDOR, MotivoMovimentacao.PERDA_AVARIA);
    }

    @Test
    @DisplayName("os motivos de entrada sao compra e devolucao de cliente")
    void motivosDeEntrada() {
        assertThat(Arrays.stream(MotivoMovimentacao.values()).filter(MotivoMovimentacao::ehEntrada))
                .containsExactlyInAnyOrder(MotivoMovimentacao.COMPRA_FORNECEDOR,
                        MotivoMovimentacao.DEVOLUCAO_CLIENTE);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MotivoMovimentacao.class)
    @DisplayName("nenhum motivo e de entrada e de saida ao mesmo tempo")
    void grupoUnico(MotivoMovimentacao motivo) {
        assertThat(motivo.ehEntrada() && motivo.ehSaida()).isFalse();
        assertThat(motivo.ehEntrada()).isEqualTo(motivo.getTipo() == TipoMovimentacao.ENTRADA);
    }

    @Test
    @DisplayName("as descricoes que o front-end compara continuam as mesmas")
    void descricoesUsadasPeloFrontEnd() {
        assertThat(MotivoMovimentacao.COMPRA_FORNECEDOR.getDescricao()).isEqualTo("Compra de fornecedor");
        assertThat(MotivoMovimentacao.DEVOLUCAO_CLIENTE.getDescricao()).isEqualTo("Devolucao de cliente");
        assertThat(MotivoMovimentacao.VENDA_BALCAO.getDescricao()).isEqualTo("Venda de balcao");
        assertThat(MotivoMovimentacao.ORDEM_SERVICO.getDescricao()).isEqualTo("Aplicacao em ordem de servico");
        assertThat(MotivoMovimentacao.DEVOLUCAO_FORNECEDOR.getDescricao()).isEqualTo("Devolucao ao fornecedor");
        assertThat(MotivoMovimentacao.PERDA_AVARIA.getDescricao()).isEqualTo("Perda ou avaria");
    }
}
