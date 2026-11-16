package br.com.oncar.service;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.TipoMovimentacao;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.service.relatorio.Relatorios.IndicadoresFinanceiros;
import br.com.oncar.service.relatorio.Relatorios.LinhaDespesa;
import br.com.oncar.service.relatorio.Relatorios.LinhaEntrada;
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
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatorios mensais e indicadores do painel gerencial. Tudo que e financeiro e
 * apurado pelo mes de competencia do lancamento, e nao pela data de digitacao;
 * a posicao de estoque, por ser fisica, segue a data em que cada lancamento ocorreu.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    private static final int ESCALA_VALOR = 2;
    private static final int PRODUTOS_DE_MAIOR_GIRO = 5;
    private static final String SEM_FORNECEDOR = "Sem fornecedor";

    /** Saidas que geram receita; devolucao ao fornecedor e perda nao sao venda. */
    private static final Set<MotivoMovimentacao> MOTIVOS_DE_RECEITA =
            EnumSet.of(MotivoMovimentacao.VENDA_BALCAO, MotivoMovimentacao.ORDEM_SERVICO);

    private final MovimentacaoRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;
    private final DespesaRepository despesaRepository;

    public RelatorioService(MovimentacaoRepository movimentacaoRepository, ProdutoRepository produtoRepository,
                            DespesaRepository despesaRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.produtoRepository = produtoRepository;
        this.despesaRepository = despesaRepository;
    }

    public RelatorioEntradas entradas(Periodo periodo, CategoriaProduto categoria, Long fornecedorId,
                                      Long usuarioId) {
        List<LinhaEntrada> linhas = lancamentos(TipoMovimentacao.ENTRADA, periodo).stream()
                .filter(daCategoria(categoria))
                .filter(movimentacao -> fornecedorId == null || (movimentacao.getFornecedor() != null
                        && fornecedorId.equals(movimentacao.getFornecedor().getId())))
                .filter(doUsuario(usuarioId))
                .map(movimentacao -> new LinhaEntrada(
                        movimentacao.getId(),
                        movimentacao.getCompetencia(),
                        movimentacao.getDataHora(),
                        movimentacao.getProduto().getId(),
                        movimentacao.getProduto().getSku(),
                        movimentacao.getProduto().getDescricao(),
                        movimentacao.getProduto().getCategoria().getDescricao(),
                        movimentacao.getFornecedor() == null ? null : movimentacao.getFornecedor().getRazaoSocial(),
                        movimentacao.getDocumento(),
                        movimentacao.getQuantidade(),
                        movimentacao.getProduto().getUnidadeMedida().getSigla(),
                        movimentacao.getCustoUnitario(),
                        custo(movimentacao),
                        movimentacao.getUsuario().getNome()))
                .toList();

        return new RelatorioEntradas(periodo, linhas,
                somar(linhas, LinhaEntrada::valorTotal),
                agrupar(linhas, linha -> linha.fornecedor() == null ? SEM_FORNECEDOR : linha.fornecedor(),
                        LinhaEntrada::quantidade, LinhaEntrada::valorTotal),
                agrupar(linhas, LinhaEntrada::categoria, LinhaEntrada::quantidade, LinhaEntrada::valorTotal));
    }

    /** Nas saidas, o filtro de fornecedor considera o fornecedor padrao da peca. */
    public RelatorioSaidas saidas(Periodo periodo, CategoriaProduto categoria, Long fornecedorId, Long usuarioId) {
        List<Movimentacao> saidas = lancamentos(TipoMovimentacao.SAIDA, periodo).stream()
                .filter(daCategoria(categoria))
                .filter(movimentacao -> fornecedorId == null
                        || (movimentacao.getProduto().getFornecedorPadrao() != null
                        && fornecedorId.equals(movimentacao.getProduto().getFornecedorPadrao().getId())))
                .filter(doUsuario(usuarioId))
                .toList();

        List<LinhaSaida> linhas = saidas.stream()
                .map(movimentacao -> new LinhaSaida(
                        movimentacao.getId(),
                        movimentacao.getCompetencia(),
                        movimentacao.getDataHora(),
                        movimentacao.getProduto().getId(),
                        movimentacao.getProduto().getSku(),
                        movimentacao.getProduto().getDescricao(),
                        movimentacao.getProduto().getCategoria().getDescricao(),
                        movimentacao.getMotivo().getDescricao(),
                        movimentacao.getCliente() == null ? null : movimentacao.getCliente().getNome(),
                        movimentacao.getDocumento(),
                        movimentacao.getQuantidade(),
                        movimentacao.getProduto().getUnidadeMedida().getSigla(),
                        custo(movimentacao),
                        geraReceita(movimentacao) ? receita(movimentacao) : null,
                        movimentacao.getUsuario().getNome()))
                .toList();

        BigDecimal receita = somar(saidas.stream().filter(this::geraReceita).toList(), this::receita);
        BigDecimal custoDasVendas = somar(saidas.stream().filter(this::geraReceita).toList(), this::custo);
        return new RelatorioSaidas(periodo, linhas,
                somar(linhas, LinhaSaida::custoTotal),
                receita,
                custoDasVendas,
                receita.subtract(custoDasVendas),
                agrupar(linhas, LinhaSaida::motivo, LinhaSaida::quantidade, LinhaSaida::custoTotal),
                agrupar(linhas, LinhaSaida::categoria, LinhaSaida::quantidade, LinhaSaida::custoTotal));
    }

    /**
     * Despesas operacionais e compras de mercadoria do periodo. O filtro de categoria
     * vale para as despesas; com ele ativo, as compras ficam de fora por nao pertencerem
     * a nenhuma categoria de despesa. A apuracao mensal ignora os filtros: e o resultado
     * da oficina inteira.
     */
    public RelatorioGastos gastos(Periodo periodo, CategoriaDespesa categoria, Long fornecedorId, Long usuarioId) {
        List<Despesa> todasAsDespesas = despesas(periodo);
        List<Movimentacao> todasAsCompras = compras(lancamentos(TipoMovimentacao.ENTRADA, periodo));

        List<LinhaDespesa> despesas = todasAsDespesas.stream()
                .filter(despesa -> categoria == null || despesa.getCategoria() == categoria)
                .filter(despesa -> fornecedorId == null || (despesa.getFornecedor() != null
                        && fornecedorId.equals(despesa.getFornecedor().getId())))
                .filter(despesa -> usuarioId == null || usuarioId.equals(despesa.getUsuario().getId()))
                .map(despesa -> new LinhaDespesa(
                        despesa.getId(),
                        despesa.getCompetencia(),
                        despesa.getDescricao(),
                        despesa.getCategoria().getDescricao(),
                        despesa.getFornecedor() == null ? null : despesa.getFornecedor().getRazaoSocial(),
                        despesa.getDocumento(),
                        despesa.getValor(),
                        despesa.getUsuario().getNome()))
                .toList();

        List<Movimentacao> compras = categoria != null ? List.of() : todasAsCompras.stream()
                .filter(movimentacao -> fornecedorId == null || (movimentacao.getFornecedor() != null
                        && fornecedorId.equals(movimentacao.getFornecedor().getId())))
                .filter(doUsuario(usuarioId))
                .toList();

        BigDecimal totalDespesas = somar(despesas, LinhaDespesa::valor);
        BigDecimal totalCompras = somar(compras, this::custo);
        List<ResumoMensal> meses = apurar(periodo, lancamentos(TipoMovimentacao.SAIDA, periodo),
                todasAsCompras, todasAsDespesas);

        return new RelatorioGastos(periodo, despesas, totalDespesas,
                agrupar(despesas, LinhaDespesa::categoria, linha -> BigDecimal.ZERO, LinhaDespesa::valor),
                totalCompras,
                agrupar(compras, movimentacao -> movimentacao.getFornecedor() == null ? SEM_FORNECEDOR
                        : movimentacao.getFornecedor().getRazaoSocial(), Movimentacao::getQuantidade, this::custo),
                totalDespesas.add(totalCompras),
                meses,
                consolidar(meses));
    }

    /**
     * Posicao fisica de cada produto no periodo: saldo ao abrir, o que entrou, saiu ou
     * foi ajustado, e o saldo ao fechar, valorizado pelo custo medio vigente no fechamento.
     */
    public RelatorioPosicao posicao(Periodo periodo, CategoriaProduto categoria, Long fornecedorId) {
        Map<Long, Movimentacao> naAbertura = ultimoPorProduto(
                movimentacaoRepository.buscarUltimosAntesDe(periodo.abertura()));
        Map<Long, Movimentacao> noEncerramento = ultimoPorProduto(
                movimentacaoRepository.buscarUltimosAntesDe(periodo.encerramento()));
        Map<Long, List<Movimentacao>> noPeriodo = movimentacaoRepository
                .buscarPorDataDeLancamento(periodo.abertura(), periodo.encerramento()).stream()
                .collect(Collectors.groupingBy(movimentacao -> movimentacao.getProduto().getId()));
        boolean incluiMesAtual = !periodo.fim().isBefore(YearMonth.now());

        List<LinhaPosicao> linhas = new ArrayList<>();
        for (Produto produto : produtoRepository.listarComFornecedor()) {
            if ((categoria != null && produto.getCategoria() != categoria)
                    || (fornecedorId != null && (produto.getFornecedorPadrao() == null
                    || !fornecedorId.equals(produto.getFornecedorPadrao().getId())))) {
                continue;
            }
            Movimentacao fechamento = noEncerramento.get(produto.getId());
            // produto sem historico ate o fim do periodo so aparece se estiver ativo hoje
            // e o periodo chegar ao mes corrente: em meses passados ele ainda nao existia
            if (fechamento == null && !(produto.isAtivo() && incluiMesAtual)) {
                continue;
            }
            Movimentacao abertura = naAbertura.get(produto.getId());
            List<Movimentacao> movimentos = noPeriodo.getOrDefault(produto.getId(), List.of());

            BigDecimal saldoInicial = abertura == null ? zeroQuantidade() : abertura.getSaldoResultante();
            BigDecimal saldoFinal = fechamento == null ? produto.getSaldo() : fechamento.getSaldoResultante();
            BigDecimal custoMedio = fechamento == null || fechamento.getCustoMedioResultante() == null
                    ? produto.getPrecoCusto() : fechamento.getCustoMedioResultante();

            linhas.add(new LinhaPosicao(
                    produto.getId(),
                    produto.getSku(),
                    produto.getDescricao(),
                    produto.getCategoria().getDescricao(),
                    produto.getUnidadeMedida().getSigla(),
                    produto.getFornecedorPadrao() == null ? null : produto.getFornecedorPadrao().getRazaoSocial(),
                    saldoInicial,
                    variacao(movimentos, TipoMovimentacao.ENTRADA),
                    variacao(movimentos, TipoMovimentacao.SAIDA).negate(),
                    variacao(movimentos, TipoMovimentacao.AJUSTE).add(variacao(movimentos, TipoMovimentacao.ESTORNO)),
                    saldoFinal,
                    produto.getEstoqueMinimo(),
                    custoMedio,
                    valor(saldoFinal.multiply(custoMedio)),
                    produto.isAtivo() && saldoFinal.compareTo(produto.getEstoqueMinimo()) <= 0,
                    produto.isAtivo()));
        }

        return new RelatorioPosicao(periodo, linhas, somar(linhas, LinhaPosicao::valorFinal),
                (int) linhas.stream().filter(LinhaPosicao::abaixoDoMinimo).count());
    }

    /** Receita e despesa do mes corrente e dos meses anteriores, para o painel. */
    public IndicadoresFinanceiros indicadoresFinanceiros(YearMonth mesAtual, int mesesNoHistorico) {
        Periodo periodo = new Periodo(mesAtual.minusMonths(mesesNoHistorico - 1L), mesAtual);
        List<ResumoMensal> meses = apurar(periodo, lancamentos(TipoMovimentacao.SAIDA, periodo),
                compras(lancamentos(TipoMovimentacao.ENTRADA, periodo)), despesas(periodo));
        return new IndicadoresFinanceiros(meses.get(meses.size() - 1), meses);
    }

    /**
     * Pecas que mais sairam no mes por venda ou ordem de servico, em quantidade.
     * A receita so e preenchida para quem pode ver o financeiro.
     */
    public List<ProdutoGiro> maiorGiro(YearMonth mes, boolean comReceita) {
        Map<Produto, List<Movimentacao>> porProduto = lancamentos(TipoMovimentacao.SAIDA, Periodo.doMes(mes))
                .stream()
                .filter(this::geraReceita)
                .collect(Collectors.groupingBy(Movimentacao::getProduto, LinkedHashMap::new, Collectors.toList()));

        return porProduto.entrySet().stream()
                .map(entrada -> {
                    Produto produto = entrada.getKey();
                    List<Movimentacao> saidas = entrada.getValue();
                    return new ProdutoGiro(
                            produto.getId(),
                            produto.getSku(),
                            produto.getDescricao(),
                            produto.getUnidadeMedida().getSigla(),
                            somarQuantidade(saidas),
                            saidas.size(),
                            comReceita ? somar(saidas, this::receita) : null);
                })
                .sorted(Comparator.comparing(ProdutoGiro::quantidade).reversed()
                        .thenComparing(ProdutoGiro::saidas, Comparator.reverseOrder())
                        .thenComparing(ProdutoGiro::sku))
                .limit(PRODUTOS_DE_MAIOR_GIRO)
                .toList();
    }

    private List<ResumoMensal> apurar(Periodo periodo, List<Movimentacao> saidas, List<Movimentacao> compras,
                                      List<Despesa> despesas) {
        Map<YearMonth, List<Movimentacao>> saidasPorMes = porMes(saidas, Movimentacao::getMesCompetencia);
        Map<YearMonth, List<Movimentacao>> comprasPorMes = porMes(compras, Movimentacao::getMesCompetencia);
        Map<YearMonth, List<Despesa>> despesasPorMes = porMes(despesas, Despesa::getMesCompetencia);

        return periodo.meses().stream()
                .map(mes -> {
                    List<Movimentacao> saidasDoMes = saidasPorMes.getOrDefault(mes, List.of());
                    List<Movimentacao> vendas = saidasDoMes.stream().filter(this::geraReceita).toList();
                    List<Movimentacao> perdas = saidasDoMes.stream()
                            .filter(movimentacao -> movimentacao.getMotivo() == MotivoMovimentacao.PERDA_AVARIA)
                            .toList();
                    return resumo(mes,
                            somar(vendas, this::receita),
                            somar(vendas, this::custo),
                            somar(perdas, this::custo),
                            somar(despesasPorMes.getOrDefault(mes, List.of()), Despesa::getValor),
                            somar(comprasPorMes.getOrDefault(mes, List.of()), this::custo));
                })
                .toList();
    }

    private ResumoMensal consolidar(List<ResumoMensal> meses) {
        return resumo(null,
                somar(meses, ResumoMensal::receita),
                somar(meses, ResumoMensal::custoDasVendas),
                somar(meses, ResumoMensal::perdas),
                somar(meses, ResumoMensal::despesasOperacionais),
                somar(meses, ResumoMensal::compras));
    }

    private static ResumoMensal resumo(YearMonth mes, BigDecimal receita, BigDecimal custoDasVendas,
                                       BigDecimal perdas, BigDecimal despesasOperacionais, BigDecimal compras) {
        BigDecimal despesaTotal = custoDasVendas.add(perdas).add(despesasOperacionais);
        return new ResumoMensal(mes, receita, custoDasVendas, receita.subtract(custoDasVendas), perdas,
                despesasOperacionais, despesaTotal, receita.subtract(despesaTotal), compras);
    }

    private List<Movimentacao> lancamentos(TipoMovimentacao tipo, Periodo periodo) {
        return movimentacaoRepository.buscarLancamentosPorCompetencia(tipo, periodo.primeiroDia(),
                periodo.ultimoDia());
    }

    private List<Despesa> despesas(Periodo periodo) {
        return despesaRepository.buscarPorCompetencia(periodo.primeiroDia(), periodo.ultimoDia());
    }

    private static List<Movimentacao> compras(List<Movimentacao> entradas) {
        return entradas.stream()
                .filter(movimentacao -> movimentacao.getMotivo() == MotivoMovimentacao.COMPRA_FORNECEDOR)
                .toList();
    }

    private boolean geraReceita(Movimentacao movimentacao) {
        return MOTIVOS_DE_RECEITA.contains(movimentacao.getMotivo());
    }

    /** Preco gravado na saida; lancamentos anteriores a essa gravacao usam o preco atual. */
    private BigDecimal receita(Movimentacao movimentacao) {
        BigDecimal preco = movimentacao.getPrecoVendaUnitario() != null
                ? movimentacao.getPrecoVendaUnitario() : movimentacao.getProduto().getPrecoVenda();
        return valor(movimentacao.getQuantidade().multiply(preco));
    }

    /** Quantidade vezes o custo do lancamento: custo da compra na entrada, custo medio na saida. */
    private BigDecimal custo(Movimentacao movimentacao) {
        return valor(movimentacao.getValorTotal());
    }

    private static Predicate<Movimentacao> daCategoria(CategoriaProduto categoria) {
        return movimentacao -> categoria == null || movimentacao.getProduto().getCategoria() == categoria;
    }

    private static Predicate<Movimentacao> doUsuario(Long usuarioId) {
        return movimentacao -> usuarioId == null || usuarioId.equals(movimentacao.getUsuario().getId());
    }

    /** Variacao assinada do saldo causada pelos lancamentos de um tipo. */
    private static BigDecimal variacao(List<Movimentacao> movimentos, TipoMovimentacao tipo) {
        return movimentos.stream()
                .filter(movimentacao -> movimentacao.getTipo() == tipo)
                .map(movimentacao -> movimentacao.getSaldoResultante().subtract(movimentacao.getSaldoAnterior()))
                .reduce(zeroQuantidade(), BigDecimal::add);
    }

    private static Map<Long, Movimentacao> ultimoPorProduto(List<Movimentacao> movimentacoes) {
        Comparator<Movimentacao> ordem = Comparator.comparing(Movimentacao::getDataHora)
                .thenComparing(Movimentacao::getId);
        Map<Long, Movimentacao> ultimos = new HashMap<>();
        for (Movimentacao movimentacao : movimentacoes) {
            ultimos.merge(movimentacao.getProduto().getId(), movimentacao,
                    (atual, outra) -> ordem.compare(atual, outra) >= 0 ? atual : outra);
        }
        return ultimos;
    }

    private static <T> Map<YearMonth, List<T>> porMes(List<T> itens, Function<T, YearMonth> mes) {
        return itens.stream().collect(Collectors.groupingBy(mes));
    }

    /** Totais por grupo, do maior valor para o menor. */
    private static <T> List<TotalPorGrupo> agrupar(List<T> itens, Function<T, String> grupo,
                                                   Function<T, BigDecimal> quantidade,
                                                   Function<T, BigDecimal> valor) {
        Map<String, List<T>> porGrupo = itens.stream()
                .collect(Collectors.groupingBy(grupo, LinkedHashMap::new, Collectors.toList()));
        return porGrupo.entrySet().stream()
                .map(entrada -> new TotalPorGrupo(entrada.getKey(), entrada.getValue().size(),
                        entrada.getValue().stream().map(quantidade).reduce(zeroQuantidade(), BigDecimal::add),
                        somar(entrada.getValue(), valor)))
                .sorted(Comparator.comparing(TotalPorGrupo::valor).reversed()
                        .thenComparing(TotalPorGrupo::grupo))
                .toList();
    }

    private static <T> BigDecimal somar(List<T> itens, Function<T, BigDecimal> valor) {
        return itens.stream()
                .map(valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
    }

    private static BigDecimal somarQuantidade(List<Movimentacao> movimentacoes) {
        return movimentacoes.stream()
                .map(Movimentacao::getQuantidade)
                .reduce(zeroQuantidade(), BigDecimal::add);
    }

    private static BigDecimal valor(BigDecimal valor) {
        return valor.setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroQuantidade() {
        return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    }
}
