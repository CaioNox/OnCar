package br.com.oncar.service.relatorio;

import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resultados dos relatorios gerenciais e dos indicadores do painel. */
public final class Relatorios {

    private Relatorios() {
    }

    /** Intervalo de meses de competencia, do primeiro ao ultimo inclusive. */
    public record Periodo(YearMonth inicio, YearMonth fim) {

        public static final int MAXIMO_DE_MESES = 24;
        private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");

        public Periodo {
            if (inicio == null || fim == null) {
                throw new RegraNegocioException("Informe o mes inicial e o mes final do periodo.");
            }
            if (inicio.isAfter(fim)) {
                throw new RegraNegocioException("O mes inicial deve ser anterior ou igual ao mes final.");
            }
            if (inicio.plusMonths(MAXIMO_DE_MESES).isBefore(fim.plusMonths(1))) {
                throw new RegraNegocioException("Escolha um periodo de no maximo " + MAXIMO_DE_MESES
                        + " meses.");
            }
        }

        public static Periodo doMes(YearMonth mes) {
            return new Periodo(mes, mes);
        }

        /** Completa o periodo pedido pela tela: sem meses vale o corrente; com um so, aquele mes. */
        public static Periodo informado(YearMonth inicio, YearMonth fim) {
            YearMonth primeiro = inicio != null ? inicio : (fim != null ? fim : YearMonth.now());
            return new Periodo(primeiro, fim != null ? fim : primeiro);
        }

        public LocalDate primeiroDia() {
            return inicio.atDay(1);
        }

        public LocalDate ultimoDia() {
            return fim.atEndOfMonth();
        }

        public LocalDateTime abertura() {
            return primeiroDia().atStartOfDay();
        }

        /** Primeiro instante depois do periodo; limite exclusivo para datas de lancamento. */
        public LocalDateTime encerramento() {
            return fim.plusMonths(1).atDay(1).atStartOfDay();
        }

        public List<YearMonth> meses() {
            List<YearMonth> meses = new ArrayList<>();
            for (YearMonth mes = inicio; !mes.isAfter(fim); mes = mes.plusMonths(1)) {
                meses.add(mes);
            }
            return meses;
        }

        public String descricao() {
            return inicio.equals(fim) ? nomeDoMes(inicio) : nomeDoMes(inicio) + " a " + nomeDoMes(fim);
        }

        public static String nomeDoMes(YearMonth mes) {
            String nome = mes.getMonth().getDisplayName(TextStyle.FULL, PORTUGUES);
            return Character.toUpperCase(nome.charAt(0)) + nome.substring(1) + "/" + mes.getYear();
        }
    }

    /** Soma de um agrupamento (fornecedor, categoria, motivo...). */
    public record TotalPorGrupo(String grupo, int lancamentos, BigDecimal quantidade, BigDecimal valor) {
    }

    public record LinhaEntrada(Long movimentacaoId, LocalDate competencia, LocalDateTime dataHora,
                               Long produtoId, String sku, String produto, String categoria,
                               String fornecedor, String documento, BigDecimal quantidade, String unidade,
                               BigDecimal custoUnitario, BigDecimal valorTotal, String usuario) {
    }

    public record RelatorioEntradas(Periodo periodo, List<LinhaEntrada> linhas, BigDecimal valorTotal,
                                    List<TotalPorGrupo> porFornecedor, List<TotalPorGrupo> porCategoria) {
    }

    /** `valorVenda` fica nulo nas saidas que nao geram receita (devolucao, perda). */
    public record LinhaSaida(Long movimentacaoId, LocalDate competencia, LocalDateTime dataHora,
                             Long produtoId, String sku, String produto, String categoria, String motivo,
                             String cliente, String documento, BigDecimal quantidade, String unidade,
                             BigDecimal custoTotal, BigDecimal valorVenda, String usuario) {
    }

    /**
     * `custoTotal` soma todas as saidas; `custoDasVendas` so as que geraram receita
     * (venda de balcao e ordem de servico), base da margem bruta.
     */
    public record RelatorioSaidas(Periodo periodo, List<LinhaSaida> linhas, BigDecimal custoTotal,
                                  BigDecimal receita, BigDecimal custoDasVendas, BigDecimal margemBruta,
                                  List<TotalPorGrupo> porMotivo, List<TotalPorGrupo> porCategoria) {
    }

    public record LinhaDespesa(Long despesaId, LocalDate competencia, String descricao, String categoria,
                               String fornecedor, String documento, BigDecimal valor, String usuario) {
    }

    /**
     * Apuracao pelo regime de competencia. A margem usa o custo medio ponderado gravado
     * em cada saida; a despesa total soma esse custo, as perdas e as despesas operacionais.
     * Compras sao investimento em estoque e aparecem a parte, sem reduzir o resultado.
     * `mes` fica nulo no consolidado de um periodo.
     */
    public record ResumoMensal(YearMonth mes, BigDecimal receita, BigDecimal custoDasVendas,
                               BigDecimal margemBruta, BigDecimal perdas, BigDecimal despesasOperacionais,
                               BigDecimal despesaTotal, BigDecimal resultado, BigDecimal compras) {

        public String nomeDoMes() {
            return mes == null ? "Total do periodo" : Periodo.nomeDoMes(mes);
        }
    }

    public record RelatorioGastos(Periodo periodo, List<LinhaDespesa> despesas, BigDecimal totalDespesas,
                                  List<TotalPorGrupo> despesasPorCategoria, BigDecimal totalCompras,
                                  List<TotalPorGrupo> comprasPorFornecedor, BigDecimal totalGasto,
                                  List<ResumoMensal> meses, ResumoMensal consolidado) {
    }

    public record LinhaPosicao(Long produtoId, String sku, String produto, String categoria, String unidade,
                               String fornecedor, BigDecimal saldoInicial, BigDecimal entradas,
                               BigDecimal saidas, BigDecimal ajustes, BigDecimal saldoFinal,
                               BigDecimal estoqueMinimo, BigDecimal custoMedio, BigDecimal valorFinal,
                               boolean abaixoDoMinimo, boolean ativo) {
    }

    public record RelatorioPosicao(Periodo periodo, List<LinhaPosicao> linhas, BigDecimal valorTotal,
                                   int produtosAbaixoDoMinimo) {
    }

    /** `receita` fica nula para quem nao tem acesso ao financeiro. */
    public record ProdutoGiro(Long produtoId, String sku, String descricao, String unidade,
                              BigDecimal quantidade, int saidas, BigDecimal receita) {
    }

    /** Mes corrente e a serie dos ultimos meses, para o grafico de receita x despesa. */
    public record IndicadoresFinanceiros(ResumoMensal mesAtual, List<ResumoMensal> historico) {
    }
}
