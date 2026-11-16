package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DespesaServiceTest extends TesteIntegracao {

    @Autowired
    private DespesaService despesaService;

    @Autowired
    private FornecedorService fornecedorService;

    @BeforeEach
    void prepararCenario() {
        criarProprietario();
    }

    @Test
    @DisplayName("a despesa e lancada com categoria, competencia e usuario, e fica na auditoria")
    void lancaDespesa() {
        Despesa despesa = despesaService.lancar(dados("Conta de energia", CategoriaDespesa.ENERGIA,
                "412.3", LocalDate.now()), EMAIL_PROPRIETARIO);

        assertThat(despesa.getId()).isNotNull();
        assertThat(despesa.getValor()).isEqualByComparingTo("412.30");
        assertThat(despesa.getUsuario().getEmail()).isEqualTo(EMAIL_PROPRIETARIO);
        assertThat(logAuditoriaRepository.findAll())
                .anyMatch(log -> log.getAcao() == AcaoAuditoria.INCLUSAO && "Despesa".equals(log.getEntidade()));
    }

    @Test
    @DisplayName("valor zero, descricao curta e categoria ausente sao rejeitados")
    void validaCamposObrigatorios() {
        assertThatThrownBy(() -> despesaService.lancar(dados("Aluguel", CategoriaDespesa.ALUGUEL, "0",
                LocalDate.now()), EMAIL_PROPRIETARIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> despesaService.lancar(dados("Al", CategoriaDespesa.ALUGUEL, "10",
                LocalDate.now()), EMAIL_PROPRIETARIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Descreva a despesa");
        assertThatThrownBy(() -> despesaService.lancar(dados("Aluguel", null, "10", LocalDate.now()),
                EMAIL_PROPRIETARIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("categoria");
        assertThat(despesaRepository.count()).isZero();
    }

    @Test
    @DisplayName("a competencia nao pode ser de um mes que ainda nao comecou")
    void rejeitaCompetenciaFutura() {
        LocalDate proximoMes = YearMonth.now().plusMonths(1).atDay(1);

        assertThatThrownBy(() -> despesaService.lancar(dados("Aluguel adiantado", CategoriaDespesa.ALUGUEL,
                "2500", proximoMes), EMAIL_PROPRIETARIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("mes futuro");
    }

    @Test
    @DisplayName("a listagem considera o mes de competencia, nao a data de digitacao")
    void listaPorCompetencia() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        despesaService.lancar(dados("Aluguel do mes passado", CategoriaDespesa.ALUGUEL, "2500",
                mesPassado.atDay(5)), EMAIL_PROPRIETARIO);
        despesaService.lancar(dados("Aluguel deste mes", CategoriaDespesa.ALUGUEL, "2500",
                LocalDate.now()), EMAIL_PROPRIETARIO);

        assertThat(despesaService.listarPorCompetencia(Periodo.doMes(mesPassado)))
                .extracting(Despesa::getDescricao)
                .containsExactly("Aluguel do mes passado");
        assertThat(despesaService.listarPorCompetencia(new Periodo(mesPassado, YearMonth.now()))).hasSize(2);
    }

    @Test
    @DisplayName("fornecedor inativo nao entra em nova despesa, mas a correcao preserva o vinculo antigo")
    void fornecedorInativo() {
        Fornecedor fornecedor = cadastrarFornecedor();
        Despesa despesa = despesaService.lancar(dadosComFornecedor(fornecedor.getId()), EMAIL_PROPRIETARIO);
        fornecedorService.inativar(fornecedor.getId());

        assertThatThrownBy(() -> despesaService.lancar(dadosComFornecedor(fornecedor.getId()), EMAIL_PROPRIETARIO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativo");

        Despesa corrigida = despesaService.atualizar(despesa.getId(), new DespesaService.DadosDespesa(
                "Manutencao do elevador corrigida", CategoriaDespesa.SERVICOS_TERCEIROS, new BigDecimal("350"),
                LocalDate.now(), fornecedor.getId(), "NFS 88", null));
        assertThat(corrigida.getFornecedor().getId()).isEqualTo(fornecedor.getId());
        assertThat(corrigida.getValor()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("a exclusao remove a despesa e fica registrada na auditoria")
    void excluiDespesa() {
        Despesa despesa = despesaService.lancar(dados("Taxa do alvara", CategoriaDespesa.IMPOSTOS_TAXAS, "180",
                LocalDate.now()), EMAIL_PROPRIETARIO);

        despesaService.excluir(despesa.getId());

        assertThat(despesaRepository.count()).isZero();
        assertThat(logAuditoriaRepository.findAll())
                .anyMatch(log -> log.getAcao() == AcaoAuditoria.EXCLUSAO && "Despesa".equals(log.getEntidade()));
    }

    @Test
    @DisplayName("fornecedor com despesa vinculada nao pode ser excluido, apenas inativado")
    void fornecedorComDespesaNaoEhExcluido() {
        Fornecedor fornecedor = cadastrarFornecedor();
        despesaService.lancar(dadosComFornecedor(fornecedor.getId()), EMAIL_PROPRIETARIO);

        assertThatThrownBy(() -> fornecedorService.excluir(fornecedor.getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Inative o cadastro");
    }

    private DespesaService.DadosDespesa dados(String descricao, CategoriaDespesa categoria, String valor,
                                              LocalDate competencia) {
        return new DespesaService.DadosDespesa(descricao, categoria, new BigDecimal(valor), competencia,
                null, null, null);
    }

    private DespesaService.DadosDespesa dadosComFornecedor(Long fornecedorId) {
        return new DespesaService.DadosDespesa("Manutencao do elevador", CategoriaDespesa.SERVICOS_TERCEIROS,
                new BigDecimal("320"), LocalDate.now(), fornecedorId, null, null);
    }

    private Fornecedor cadastrarFornecedor() {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setRazaoSocial("Elevadores Brasilia Ltda");
        fornecedor.setCnpj("98.765.432/0001-10");
        return fornecedorService.cadastrar(fornecedor);
    }
}
