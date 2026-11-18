package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.TipoMovimentacao;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.service.relatorio.Relatorios.IndicadoresFinanceiros;
import br.com.oncar.service.relatorio.Relatorios.LinhaPosicao;
import br.com.oncar.service.relatorio.Relatorios.LinhaSaida;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.service.relatorio.Relatorios.ProdutoGiro;
import br.com.oncar.service.relatorio.Relatorios.RelatorioEntradas;
import br.com.oncar.service.relatorio.Relatorios.RelatorioGastos;
import br.com.oncar.service.relatorio.Relatorios.RelatorioPosicao;
import br.com.oncar.service.relatorio.Relatorios.RelatorioSaidas;
import br.com.oncar.service.relatorio.Relatorios.ResumoMensal;
import br.com.oncar.service.relatorio.Relatorios.TotalPorGrupo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Apuracao dos relatorios sobre lancamentos montados em memoria. As consultas
 * (filtro de estornos, competencia) sao responsabilidade dos repositorios e ficam
 * nos testes de integracao; aqui se verifica so o calculo.
 */
@ExtendWith(MockitoExtension.class)
class RelatorioServiceUnitarioTest {

    private static final YearMonth MES = YearMonth.of(2026, 5);
    private static final Periodo PERIODO = Periodo.doMes(MES);

    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private DespesaRepository despesaRepository;

    @InjectMocks
    private RelatorioService relatorioService;

    private final Usuario usuario = usuario();
    private long proximoId = 1;

    private static Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Gerente");
        return usuario;
    }

    private static Produto produto(long id, String sku, String precoVenda) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setSku(sku);
        produto.setDescricao("Produto " + sku);
        produto.setCategoria(CategoriaProduto.PECA);
        produto.setUnidadeMedida(UnidadeMedida.UNIDADE);
        produto.setPrecoVenda(new BigDecimal(precoVenda));
        return produto;
    }

    private Movimentacao lancamento(Produto produto, MotivoMovimentacao motivo, String quantidade, String custo) {
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setId(proximoId++);
        movimentacao.setProduto(produto);
        movimentacao.setTipo(motivo.getTipo());
        movimentacao.setMotivo(motivo);
        movimentacao.setQuantidade(new BigDecimal(quantidade));
        movimentacao.setCustoUnitario(new BigDecimal(custo));
        movimentacao.setCompetencia(MES.atDay(10));
        movimentacao.setUsuario(usuario);
        return movimentacao;
    }

    private Movimentacao venda(Produto produto, MotivoMovimentacao motivo, String quantidade, String custo,
                               String precoGravado) {
        Movimentacao saida = lancamento(produto, motivo, quantidade, custo);
        saida.setPrecoVendaUnitario(precoGravado == null ? null : new BigDecimal(precoGravado));
        return saida;
    }

    private void lancamentosDoTipo(TipoMovimentacao tipo, Movimentacao... movimentacoes) {
        when(movimentacaoRepository.buscarLancamentosPorCompetencia(eq(tipo), any(), any()))
                .thenReturn(List.of(movimentacoes));
    }

    private static Despesa despesa(CategoriaDespesa categoria, String valor) {
        Despesa despesa = new Despesa();
        despesa.setDescricao(categoria.getDescricao());
        despesa.setCategoria(categoria);
        despesa.setValor(new BigDecimal(valor));
        despesa.setCompetencia(MES.atDay(5));
        despesa.setUsuario(usuario());
        return despesa;
    }

    @Nested
    @DisplayName("saidas")
    class Saidas {

        @Test
        @DisplayName("so venda de balcao e ordem de servico geram receita; perda entra so no custo")
        void receitaSoDeVendas() {
            Produto produto = produto(1, "P-1", "50");
            lancamentosDoTipo(TipoMovimentacao.SAIDA,
                    venda(produto, MotivoMovimentacao.VENDA_BALCAO, "2", "30", "45"),
                    venda(produto, MotivoMovimentacao.ORDEM_SERVICO, "1", "30", "45"),
                    venda(produto, MotivoMovimentacao.PERDA_AVARIA, "1", "30", null));

            RelatorioSaidas relatorio = relatorioService.saidas(PERIODO, null, null, null);

            assertThat(relatorio.receita()).isEqualTo(new BigDecimal("135.00"));
            assertThat(relatorio.custoDasVendas()).isEqualTo(new BigDecimal("90.00"));
            assertThat(relatorio.margemBruta()).isEqualTo(new BigDecimal("45.00"));
            assertThat(relatorio.custoTotal()).as("inclui a perda").isEqualTo(new BigDecimal("120.00"));
            assertThat(relatorio.linhas()).extracting(LinhaSaida::valorVenda)
                    .containsExactly(new BigDecimal("90.00"), new BigDecimal("45.00"), null);
        }

        @Test
        @DisplayName("saida sem preco gravado (lancamento antigo) usa o preco de venda atual do produto")
        void semPrecoGravadoUsaPrecoAtual() {
            lancamentosDoTipo(TipoMovimentacao.SAIDA,
                    venda(produto(1, "P-1", "80"), MotivoMovimentacao.VENDA_BALCAO, "3", "10", null));

            assertThat(relatorioService.saidas(PERIODO, null, null, null).receita())
                    .isEqualTo(new BigDecimal("240.00"));
        }

        @Test
        @DisplayName("os grupos vem do maior valor para o menor e, no empate, em ordem alfabetica")
        void ordemDosGrupos() {
            Produto produto = produto(1, "P-1", "50");
            lancamentosDoTipo(TipoMovimentacao.SAIDA,
                    venda(produto, MotivoMovimentacao.PERDA_AVARIA, "1", "30", null),
                    venda(produto, MotivoMovimentacao.ORDEM_SERVICO, "1", "30", "45"),
                    venda(produto, MotivoMovimentacao.VENDA_BALCAO, "2", "30", "45"));

            List<TotalPorGrupo> porMotivo = relatorioService.saidas(PERIODO, null, null, null).porMotivo();

            assertThat(porMotivo).extracting(TotalPorGrupo::grupo, TotalPorGrupo::valor).containsExactly(
                    tuple("Venda de balcao", new BigDecimal("60.00")),
                    tuple("Aplicacao em ordem de servico", new BigDecimal("30.00")),
                    tuple("Perda ou avaria", new BigDecimal("30.00")));
        }

        @Test
        @DisplayName("o filtro de fornecedor considera o fornecedor padrao da peca")
        void filtroPorFornecedorPadrao() {
            Fornecedor fornecedor = new Fornecedor();
            fornecedor.setId(7L);
            Produto doFornecedor = produto(1, "P-1", "10");
            doFornecedor.setFornecedorPadrao(fornecedor);
            lancamentosDoTipo(TipoMovimentacao.SAIDA,
                    venda(doFornecedor, MotivoMovimentacao.VENDA_BALCAO, "1", "5", "10"),
                    venda(produto(2, "P-2", "10"), MotivoMovimentacao.VENDA_BALCAO, "1", "5", "10"));

            assertThat(relatorioService.saidas(PERIODO, null, 7L, null).linhas())
                    .extracting(LinhaSaida::sku).containsExactly("P-1");
        }
    }

    @Test
    @DisplayName("entradas sem fornecedor ficam no grupo \"Sem fornecedor\" e saem do filtro por fornecedor")
    void entradasPorFornecedor() {
        Fornecedor alfa = new Fornecedor();
        alfa.setId(1L);
        alfa.setRazaoSocial("Alfa Pecas");
        Produto produto = produto(1, "P-1", "10");
        Movimentacao comFornecedor = lancamento(produto, MotivoMovimentacao.COMPRA_FORNECEDOR, "10", "2");
        comFornecedor.setFornecedor(alfa);
        lancamentosDoTipo(TipoMovimentacao.ENTRADA, comFornecedor,
                lancamento(produto, MotivoMovimentacao.COMPRA_FORNECEDOR, "1", "5"));

        RelatorioEntradas todas = relatorioService.entradas(PERIODO, null, null, null);
        RelatorioEntradas daAlfa = relatorioService.entradas(PERIODO, null, 1L, null);

        assertThat(todas.valorTotal()).isEqualTo(new BigDecimal("25.00"));
        assertThat(todas.porFornecedor()).extracting(TotalPorGrupo::grupo)
                .containsExactly("Alfa Pecas", "Sem fornecedor");
        assertThat(daAlfa.linhas()).hasSize(1);
        assertThat(daAlfa.valorTotal()).isEqualTo(new BigDecimal("20.00"));
    }

    @Nested
    @DisplayName("gastos e apuracao do mes")
    class Gastos {

        private void cenario() {
            Produto produto = produto(1, "P-1", "300");
            lancamentosDoTipo(TipoMovimentacao.ENTRADA,
                    lancamento(produto, MotivoMovimentacao.COMPRA_FORNECEDOR, "10", "2"),
                    lancamento(produto, MotivoMovimentacao.DEVOLUCAO_CLIENTE, "1", "50"));
            lancamentosDoTipo(TipoMovimentacao.SAIDA,
                    venda(produto, MotivoMovimentacao.VENDA_BALCAO, "1", "50", "300"),
                    venda(produto, MotivoMovimentacao.PERDA_AVARIA, "1", "10", null));
            when(despesaRepository.buscarPorCompetencia(PERIODO.primeiroDia(), PERIODO.ultimoDia()))
                    .thenReturn(List.of(despesa(CategoriaDespesa.ENERGIA, "100"),
                            despesa(CategoriaDespesa.ALUGUEL, "500")));
        }

        @Test
        @DisplayName("devolucao de cliente nao e compra; o resultado desconta custo, perda e despesas")
        void apuracaoCompleta() {
            cenario();

            RelatorioGastos gastos = relatorioService.gastos(PERIODO, null, null, null);

            assertThat(gastos.totalDespesas()).isEqualTo(new BigDecimal("600.00"));
            assertThat(gastos.totalCompras()).isEqualTo(new BigDecimal("20.00"));
            assertThat(gastos.totalGasto()).isEqualTo(new BigDecimal("620.00"));
            assertThat(gastos.despesasPorCategoria()).extracting(TotalPorGrupo::grupo)
                    .containsExactly("Aluguel", "Energia eletrica");

            ResumoMensal mes = gastos.meses().get(0);
            assertThat(mes.mes()).isEqualTo(MES);
            assertThat(mes.receita()).isEqualByComparingTo("300");
            assertThat(mes.custoDasVendas()).isEqualByComparingTo("50");
            assertThat(mes.margemBruta()).isEqualByComparingTo("250");
            assertThat(mes.perdas()).isEqualByComparingTo("10");
            assertThat(mes.despesasOperacionais()).isEqualByComparingTo("600");
            assertThat(mes.despesaTotal()).isEqualByComparingTo("660");
            assertThat(mes.resultado()).isEqualByComparingTo("-360");
            assertThat(mes.compras()).as("compra e estoque, nao despesa").isEqualByComparingTo("20");
            assertThat(gastos.consolidado().mes()).isNull();
            assertThat(gastos.consolidado().resultado()).isEqualByComparingTo("-360");
        }

        @Test
        @DisplayName("filtrar por categoria de despesa tira as compras, mas a apuracao continua completa")
        void filtroDeCategoria() {
            cenario();

            RelatorioGastos gastos = relatorioService.gastos(PERIODO, CategoriaDespesa.ENERGIA, null, null);

            assertThat(gastos.totalDespesas()).isEqualTo(new BigDecimal("100.00"));
            assertThat(gastos.totalCompras()).isEqualTo(new BigDecimal("0.00"));
            assertThat(gastos.comprasPorFornecedor()).isEmpty();
            assertThat(gastos.meses().get(0).despesasOperacionais()).isEqualByComparingTo("600");
        }
    }

    @Nested
    @DisplayName("posicao de estoque")
    class Posicao {

        private Movimentacao comSaldo(Produto produto, LocalDateTime quando, String antes, String depois) {
            Movimentacao movimentacao = lancamento(produto,
                    new BigDecimal(depois).compareTo(new BigDecimal(antes)) >= 0
                            ? MotivoMovimentacao.COMPRA_FORNECEDOR : MotivoMovimentacao.VENDA_BALCAO,
                    new BigDecimal(depois).subtract(new BigDecimal(antes)).abs().toPlainString(), "5");
            movimentacao.setDataHora(quando);
            movimentacao.setSaldoAnterior(new BigDecimal(antes));
            movimentacao.setSaldoResultante(new BigDecimal(depois));
            movimentacao.setCustoMedioResultante(new BigDecimal("5"));
            return movimentacao;
        }

        @Test
        @DisplayName("saldo inicial + entradas - saidas fecha o saldo final, valorizado pelo custo do fechamento")
        void fechaOSaldo() {
            Periodo passado = Periodo.doMes(YearMonth.now().minusMonths(3));
            LocalDateTime antes = passado.abertura().minusDays(2);
            LocalDateTime durante = passado.abertura().plusDays(3);

            Produto filtro = produto(1, "A-1", "10");
            filtro.setEstoqueMinimo(new BigDecimal("8"));
            Produto semHistorico = produto(2, "B-2", "10");
            Produto inativo = produto(3, "C-3", "10");
            inativo.setAtivo(false);
            inativo.setEstoqueMinimo(new BigDecimal("5"));

            Movimentacao abertura = comSaldo(filtro, antes, "0", "3");
            Movimentacao entrada = comSaldo(filtro, durante, "3", "10");
            Movimentacao saida = comSaldo(filtro, durante.plusHours(1), "10", "8");
            Movimentacao inativoAntes = comSaldo(inativo, antes, "2", "0");

            when(produtoRepository.listarComFornecedor()).thenReturn(List.of(filtro, semHistorico, inativo));
            when(movimentacaoRepository.buscarUltimosAntesDe(passado.abertura()))
                    .thenReturn(List.of(abertura, inativoAntes));
            // fora de ordem de proposito: vale o lancamento mais recente de cada produto
            when(movimentacaoRepository.buscarUltimosAntesDe(passado.encerramento()))
                    .thenReturn(List.of(saida, abertura, entrada, inativoAntes));
            when(movimentacaoRepository.buscarPorDataDeLancamento(passado.abertura(), passado.encerramento()))
                    .thenReturn(List.of(entrada, saida));

            RelatorioPosicao posicao = relatorioService.posicao(passado, null, null);

            assertThat(posicao.linhas()).extracting(LinhaPosicao::sku)
                    .as("produto sem historico nao existia no mes passado").containsExactly("A-1", "C-3");
            LinhaPosicao linha = posicao.linhas().get(0);
            assertThat(linha.saldoInicial()).isEqualByComparingTo("3");
            assertThat(linha.entradas()).isEqualByComparingTo("7");
            assertThat(linha.saidas()).isEqualByComparingTo("2");
            assertThat(linha.ajustes()).isEqualByComparingTo("0");
            assertThat(linha.saldoFinal()).isEqualByComparingTo("8");
            assertThat(linha.valorFinal()).isEqualTo(new BigDecimal("40.00"));
            assertThat(linha.abaixoDoMinimo()).as("saldo igual ao minimo").isTrue();
            assertThat(posicao.linhas().get(1).abaixoDoMinimo()).as("inativo nao pede reposicao").isFalse();
            assertThat(posicao.produtosAbaixoDoMinimo()).isEqualTo(1);
            assertThat(posicao.valorTotal()).isEqualTo(new BigDecimal("40.00"));
        }

        @Test
        @DisplayName("no mes corrente, produto ativo sem historico aparece com saldo e custo do cadastro")
        void produtoNovoNoMesCorrente() {
            Produto novo = produto(2, "B-2", "10");
            novo.setSaldo(new BigDecimal("4"));
            novo.setPrecoCusto(new BigDecimal("2.5"));
            when(produtoRepository.listarComFornecedor()).thenReturn(List.of(novo));

            RelatorioPosicao posicao = relatorioService.posicao(Periodo.doMes(YearMonth.now()), null, null);

            assertThat(posicao.linhas()).singleElement().satisfies(linha -> {
                assertThat(linha.saldoInicial()).isEqualByComparingTo("0");
                assertThat(linha.saldoFinal()).isEqualByComparingTo("4");
                assertThat(linha.valorFinal()).isEqualTo(new BigDecimal("10.00"));
            });
        }

        @Test
        @DisplayName("os filtros de categoria e fornecedor tiram os produtos que nao se encaixam")
        void filtros() {
            Produto peca = produto(1, "A-1", "10");
            Produto oleo = produto(2, "B-2", "10");
            oleo.setCategoria(CategoriaProduto.LUBRIFICANTE);
            when(produtoRepository.listarComFornecedor()).thenReturn(List.of(peca, oleo));
            Periodo atual = Periodo.doMes(YearMonth.now());

            assertThat(relatorioService.posicao(atual, CategoriaProduto.LUBRIFICANTE, null).linhas())
                    .extracting(LinhaPosicao::sku).containsExactly("B-2");
            assertThat(relatorioService.posicao(atual, null, 99L).linhas()).as("ninguem tem o fornecedor 99")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("o maior giro lista cinco pecas por quantidade, desempata por numero de saidas e pelo SKU")
    void maiorGiro() {
        Produto c3 = produto(3, "C-3", "10");
        Produto d2 = produto(2, "D-2", "10");
        Produto b1 = produto(1, "B-1", "10");
        Produto a7 = produto(7, "A-7", "10");
        Produto e6 = produto(6, "E-6", "10");
        Produto f4 = produto(4, "F-4", "10");
        lancamentosDoTipo(TipoMovimentacao.SAIDA,
                venda(b1, MotivoMovimentacao.VENDA_BALCAO, "5", "1", "10"),
                venda(d2, MotivoMovimentacao.VENDA_BALCAO, "2", "1", "10"),
                venda(d2, MotivoMovimentacao.ORDEM_SERVICO, "3", "1", "10"),
                venda(c3, MotivoMovimentacao.VENDA_BALCAO, "10", "1", "10"),
                venda(a7, MotivoMovimentacao.VENDA_BALCAO, "5", "1", "10"),
                venda(e6, MotivoMovimentacao.VENDA_BALCAO, "3", "1", "10"),
                venda(f4, MotivoMovimentacao.VENDA_BALCAO, "1", "1", "10"),
                venda(f4, MotivoMovimentacao.PERDA_AVARIA, "100", "1", null));

        List<ProdutoGiro> semReceita = relatorioService.maiorGiro(MES, false);
        List<ProdutoGiro> comReceita = relatorioService.maiorGiro(MES, true);

        assertThat(semReceita).extracting(ProdutoGiro::sku).containsExactly("C-3", "D-2", "A-7", "B-1", "E-6");
        assertThat(semReceita.get(1).saidas()).isEqualTo(2);
        assertThat(semReceita).extracting(ProdutoGiro::receita).containsOnlyNulls();
        assertThat(comReceita.get(0).receita()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("os indicadores trazem um resumo por mes do historico, terminando no mes atual")
    void indicadoresFinanceiros() {
        YearMonth junho = YearMonth.of(2026, 6);

        IndicadoresFinanceiros indicadores = relatorioService.indicadoresFinanceiros(junho, 6);

        assertThat(indicadores.historico()).extracting(ResumoMensal::mes)
                .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3),
                        YearMonth.of(2026, 4), YearMonth.of(2026, 5), junho);
        assertThat(indicadores.mesAtual().mes()).isEqualTo(junho);
        assertThat(indicadores.mesAtual().resultado()).isEqualTo(new BigDecimal("0.00"));
        verify(despesaRepository).buscarPorCompetencia(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    }
}
