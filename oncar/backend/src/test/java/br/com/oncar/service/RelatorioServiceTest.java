package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.LinhaPosicao;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.service.relatorio.Relatorios.ProdutoGiro;
import br.com.oncar.service.relatorio.Relatorios.RelatorioEntradas;
import br.com.oncar.service.relatorio.Relatorios.RelatorioGastos;
import br.com.oncar.service.relatorio.Relatorios.RelatorioSaidas;
import br.com.oncar.service.relatorio.Relatorios.ResumoMensal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RelatorioServiceTest extends TesteIntegracao {

    private static final YearMonth MES_ATUAL = YearMonth.now();
    private static final YearMonth MES_PASSADO = MES_ATUAL.minusMonths(1);

    @Autowired
    private RelatorioService relatorioService;

    @Autowired
    private EstoqueService estoqueService;

    @Autowired
    private DespesaService despesaService;

    @Autowired
    private FornecedorService fornecedorService;

    private Usuario gerente;
    private Produto filtro;

    @BeforeEach
    void prepararCenario() {
        gerente = criarGerente();
        criarProprietario();
        // preco de venda 100,00 (definido em criarProduto)
        filtro = criarProduto("FL-REL", new BigDecimal("2"));
    }

    @Test
    @DisplayName("a receita usa o preco gravado na saida, mesmo que o cadastro mude depois")
    void receitaUsaPrecoDaSaida() {
        entrada(filtro, "10", "40.00", null, LocalDate.now());
        saida(filtro, "3", MotivoMovimentacao.VENDA_BALCAO, LocalDate.now());

        Produto alterado = produtoRepository.findById(filtro.getId()).orElseThrow();
        alterado.setPrecoVenda(new BigDecimal("150.00"));
        produtoService.atualizar(alterado.getId(), alterado, false);

        RelatorioSaidas relatorio = relatorioService.saidas(Periodo.doMes(MES_ATUAL), null, null, null);
        // 3 x 100 = 300 de receita; 3 x 40 (custo medio) = 120 de custo
        assertThat(relatorio.receita()).isEqualByComparingTo("300.00");
        assertThat(relatorio.custoDasVendas()).isEqualByComparingTo("120.00");
        assertThat(relatorio.margemBruta()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("perdas e devolucoes entram no custo das saidas, mas nao geram receita")
    void perdaNaoGeraReceita() {
        entrada(filtro, "10", "40.00", null, LocalDate.now());
        saida(filtro, "2", MotivoMovimentacao.ORDEM_SERVICO, LocalDate.now());
        saida(filtro, "1", MotivoMovimentacao.PERDA_AVARIA, LocalDate.now());

        RelatorioSaidas relatorio = relatorioService.saidas(Periodo.doMes(MES_ATUAL), null, null, null);

        assertThat(relatorio.receita()).isEqualByComparingTo("200.00");
        assertThat(relatorio.custoTotal()).isEqualByComparingTo("120.00");
        assertThat(relatorio.custoDasVendas()).isEqualByComparingTo("80.00");
        assertThat(relatorio.linhas())
                .filteredOn(linha -> linha.motivo().equals(MotivoMovimentacao.PERDA_AVARIA.getDescricao()))
                .singleElement()
                .satisfies(linha -> assertThat(linha.valorVenda()).isNull());
    }

    @Test
    @DisplayName("o lancamento estornado e o seu estorno ficam fora dos relatorios")
    void estornoSaiDoRelatorio() {
        entrada(filtro, "10", "40.00", null, LocalDate.now());
        Movimentacao venda = saida(filtro, "4", MotivoMovimentacao.VENDA_BALCAO, LocalDate.now());
        estoqueService.estornar(venda.getId(), "Venda lancada em duplicidade", EMAIL_GERENTE);

        RelatorioSaidas relatorio = relatorioService.saidas(Periodo.doMes(MES_ATUAL), null, null, null);

        assertThat(relatorio.linhas()).isEmpty();
        assertThat(relatorio.receita()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("os relatorios separam os meses pela competencia do lancamento")
    void separaPorCompetencia() {
        entrada(filtro, "10", "40.00", null, MES_PASSADO.atDay(10));
        entrada(filtro, "5", "40.00", null, LocalDate.now());

        assertThat(relatorioService.entradas(Periodo.doMes(MES_PASSADO), null, null, null).valorTotal())
                .isEqualByComparingTo("400.00");
        assertThat(relatorioService.entradas(Periodo.doMes(MES_ATUAL), null, null, null).valorTotal())
                .isEqualByComparingTo("200.00");
        assertThat(relatorioService.entradas(new Periodo(MES_PASSADO, MES_ATUAL), null, null, null).linhas())
                .hasSize(2);
    }

    @Test
    @DisplayName("as entradas filtram por fornecedor, categoria e usuario")
    void entradasFiltram() {
        Fornecedor distribuidora = cadastrarFornecedor("Distribuidora Filtros Ltda", "11.111.111/0001-11");
        Fornecedor atacado = cadastrarFornecedor("Atacado de Pecas Ltda", "22.222.222/0001-22");
        entrada(filtro, "10", "40.00", distribuidora, LocalDate.now());
        entrada(filtro, "4", "45.00", atacado, LocalDate.now());

        RelatorioEntradas daDistribuidora = relatorioService.entradas(Periodo.doMes(MES_ATUAL), null,
                distribuidora.getId(), null);
        assertThat(daDistribuidora.linhas()).hasSize(1);
        assertThat(daDistribuidora.valorTotal()).isEqualByComparingTo("400.00");

        RelatorioEntradas todas = relatorioService.entradas(Periodo.doMes(MES_ATUAL), null, null, null);
        assertThat(todas.porFornecedor()).extracting(grupo -> grupo.grupo())
                .containsExactly("Distribuidora Filtros Ltda", "Atacado de Pecas Ltda");

        assertThat(relatorioService.entradas(Periodo.doMes(MES_ATUAL), CategoriaProduto.LUBRIFICANTE, null, null)
                .linhas()).isEmpty();
        assertThat(relatorioService.entradas(Periodo.doMes(MES_ATUAL), null, null, gerente.getId() + 999)
                .linhas()).isEmpty();
        assertThat(relatorioService.entradas(Periodo.doMes(MES_ATUAL), null, null, gerente.getId())
                .linhas()).hasSize(2);
    }

    @Test
    @DisplayName("o relatorio de gastos soma despesas e compras e apura o resultado do mes")
    void gastosEApuracao() {
        entrada(filtro, "10", "40.00", null, LocalDate.now());
        saida(filtro, "5", MotivoMovimentacao.VENDA_BALCAO, LocalDate.now());
        saida(filtro, "1", MotivoMovimentacao.PERDA_AVARIA, LocalDate.now());
        despesa("Aluguel", CategoriaDespesa.ALUGUEL, "150.00", LocalDate.now());
        despesa("Energia", CategoriaDespesa.ENERGIA, "50.00", LocalDate.now());

        RelatorioGastos gastos = relatorioService.gastos(Periodo.doMes(MES_ATUAL), null, null, null);

        assertThat(gastos.totalDespesas()).isEqualByComparingTo("200.00");
        assertThat(gastos.totalCompras()).isEqualByComparingTo("400.00");
        assertThat(gastos.totalGasto()).isEqualByComparingTo("600.00");

        ResumoMensal mes = gastos.meses().get(0);
        // receita 500; custo das vendas 200; perda 40; despesas 200 => despesa total 440 e resultado 60
        assertThat(mes.receita()).isEqualByComparingTo("500.00");
        assertThat(mes.custoDasVendas()).isEqualByComparingTo("200.00");
        assertThat(mes.perdas()).isEqualByComparingTo("40.00");
        assertThat(mes.despesaTotal()).isEqualByComparingTo("440.00");
        assertThat(mes.resultado()).isEqualByComparingTo("60.00");
        assertThat(mes.compras()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("filtrar gastos por categoria de despesa deixa as compras de fora e mantem a apuracao")
    void gastosPorCategoria() {
        entrada(filtro, "10", "40.00", null, LocalDate.now());
        despesa("Aluguel", CategoriaDespesa.ALUGUEL, "150.00", LocalDate.now());
        despesa("Energia", CategoriaDespesa.ENERGIA, "50.00", LocalDate.now());

        RelatorioGastos energia = relatorioService.gastos(Periodo.doMes(MES_ATUAL), CategoriaDespesa.ENERGIA,
                null, null);

        assertThat(energia.despesas()).singleElement().satisfies(linha -> assertThat(linha.descricao())
                .isEqualTo("Energia"));
        assertThat(energia.totalCompras()).isEqualByComparingTo("0");
        assertThat(energia.consolidado().despesasOperacionais()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("os indicadores do painel trazem o mes atual e os anteriores")
    void indicadoresDoPainel() {
        entrada(filtro, "10", "40.00", null, MES_PASSADO.atDay(1));
        saida(filtro, "2", MotivoMovimentacao.VENDA_BALCAO, MES_PASSADO.atDay(2));
        despesa("Aluguel", CategoriaDespesa.ALUGUEL, "300.00", LocalDate.now());

        var indicadores = relatorioService.indicadoresFinanceiros(MES_ATUAL, 6);

        assertThat(indicadores.historico()).hasSize(6);
        assertThat(indicadores.historico().get(4).receita()).isEqualByComparingTo("200.00");
        assertThat(indicadores.mesAtual().mes()).isEqualTo(MES_ATUAL);
        assertThat(indicadores.mesAtual().receita()).isEqualByComparingTo("0");
        assertThat(indicadores.mesAtual().resultado()).isEqualByComparingTo("-300.00");
    }

    @Test
    @DisplayName("o maior giro ordena pela quantidade vendida e ignora perdas")
    void maiorGiro() {
        Produto oleo = criarProduto("OL-REL", new BigDecimal("1"));
        entrada(filtro, "20", "40.00", null, LocalDate.now());
        entrada(oleo, "20", "30.00", null, LocalDate.now());
        saida(filtro, "3", MotivoMovimentacao.VENDA_BALCAO, LocalDate.now());
        saida(oleo, "5", MotivoMovimentacao.ORDEM_SERVICO, LocalDate.now());
        saida(oleo, "1", MotivoMovimentacao.ORDEM_SERVICO, LocalDate.now());
        saida(filtro, "10", MotivoMovimentacao.PERDA_AVARIA, LocalDate.now());

        List<ProdutoGiro> giro = relatorioService.maiorGiro(MES_ATUAL, true);

        assertThat(giro).extracting(ProdutoGiro::sku).containsExactly("OL-REL", "FL-REL");
        assertThat(giro.get(0).quantidade()).isEqualByComparingTo("6");
        assertThat(giro.get(0).saidas()).isEqualTo(2);
        assertThat(giro.get(0).receita()).isEqualByComparingTo("600.00");
        assertThat(relatorioService.maiorGiro(MES_ATUAL, false).get(0).receita()).isNull();
    }

    @Test
    @DisplayName("a posicao fecha o saldo: inicial + entradas - saidas + ajustes = final")
    void posicaoDeEstoque() {
        Movimentacao antiga = entrada(filtro, "10", "40.00", null, MES_PASSADO.atDay(5));
        // a contagem fisica segue a data do lancamento; o lancamento antigo vai para o mes passado
        antiga.setDataHora(MES_PASSADO.atDay(5).atTime(9, 0));
        movimentacaoRepository.save(antiga);

        saida(filtro, "3", MotivoMovimentacao.VENDA_BALCAO, LocalDate.now());
        estoqueService.ajustarInventario(filtro.getId(), new BigDecimal("6"), "Contagem encontrou uma peca a menos",
                null, EMAIL_GERENTE);

        LinhaPosicao atual = relatorioService.posicao(Periodo.doMes(MES_ATUAL), null, null).linhas().get(0);
        assertThat(atual.saldoInicial()).isEqualByComparingTo("10");
        assertThat(atual.saidas()).isEqualByComparingTo("3");
        assertThat(atual.ajustes()).isEqualByComparingTo("-1");
        assertThat(atual.saldoFinal()).isEqualByComparingTo("6");
        assertThat(atual.valorFinal()).isEqualByComparingTo("240.00");
        assertThat(atual.saldoInicial().add(atual.entradas()).subtract(atual.saidas()).add(atual.ajustes()))
                .isEqualByComparingTo(atual.saldoFinal());

        LinhaPosicao passado = relatorioService.posicao(Periodo.doMes(MES_PASSADO), null, null).linhas().get(0);
        assertThat(passado.saldoInicial()).isEqualByComparingTo("0");
        assertThat(passado.entradas()).isEqualByComparingTo("10");
        assertThat(passado.saldoFinal()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("periodos invertidos ou longos demais sao recusados com mensagem clara")
    void validaPeriodo() {
        assertThatThrownBy(() -> new Periodo(MES_ATUAL, MES_PASSADO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("anterior ou igual");
        assertThatThrownBy(() -> new Periodo(MES_ATUAL.minusMonths(24), MES_ATUAL))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("24 meses");
        assertThat(new Periodo(MES_ATUAL.minusMonths(23), MES_ATUAL).meses()).hasSize(24);
    }

    private Movimentacao entrada(Produto produto, String quantidade, String custo, Fornecedor fornecedor,
                                 LocalDate competencia) {
        return estoqueService.registrarEntrada(produto.getId(), new BigDecimal(quantidade), new BigDecimal(custo),
                fornecedor, null, competencia, EMAIL_GERENTE);
    }

    private Movimentacao saida(Produto produto, String quantidade, MotivoMovimentacao motivo, LocalDate competencia) {
        return estoqueService.registrarSaida(produto.getId(), new BigDecimal(quantidade), motivo, null, null,
                competencia, EMAIL_GERENTE);
    }

    private void despesa(String descricao, CategoriaDespesa categoria, String valor, LocalDate competencia) {
        despesaService.lancar(new DespesaService.DadosDespesa(descricao, categoria, new BigDecimal(valor),
                competencia, null, null, null), EMAIL_PROPRIETARIO);
    }

    private Fornecedor cadastrarFornecedor(String razaoSocial, String cnpj) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setRazaoSocial(razaoSocial);
        fornecedor.setCnpj(cnpj);
        return fornecedorService.cadastrar(fornecedor);
    }
}
