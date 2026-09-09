package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.LogAuditoria;
import br.com.oncar.domain.model.Produto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RF20: as operacoes de inclusao, alteracao e exclusao alimentam o log de auditoria.
 */
class AuditoriaServiceTest extends TesteIntegracao {

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Test
    @DisplayName("RF20: cadastro e movimentacao geram registros identificando usuario e registro afetado")
    void operacoesGeramLogDeAuditoria() {
        criarGerente();
        Produto produto = criarProduto("FL-950", new BigDecimal("2"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("5"), new BigDecimal("10.00"),
                null, "NF-e 55", null, EMAIL_GERENTE);

        List<LogAuditoria> registros = auditoriaService.listarRecentes();

        assertThat(registros)
                .extracting(LogAuditoria::getEntidade)
                .contains("Usuario", "Produto", "Movimentacao");
        assertThat(registros)
                .filteredOn(registro -> "Movimentacao".equals(registro.getEntidade()))
                .allSatisfy(registro -> {
                    assertThat(registro.getAcao()).isEqualTo(AcaoAuditoria.INCLUSAO);
                    assertThat(registro.getRegistroId()).isNotBlank();
                    assertThat(registro.getDataHora()).isNotNull();
                });
    }
}
