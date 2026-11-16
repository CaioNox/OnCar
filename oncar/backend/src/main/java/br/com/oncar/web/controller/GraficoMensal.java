package br.com.oncar.web.controller;

import br.com.oncar.service.relatorio.Relatorios.ProdutoGiro;
import br.com.oncar.service.relatorio.Relatorios.ResumoMensal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Geometria dos graficos do painel em Thymeleaf: as alturas e larguras saem prontas
 * em percentual, sobre uma escala arredondada, para a tela so desenhar.
 */
public final class GraficoMensal {

    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final int MARCAS_NO_EIXO = 4;

    private GraficoMensal() {
    }

    public record Coluna(String mes, String mesCompleto, BigDecimal receita, BigDecimal despesa,
                         int alturaReceita, int alturaDespesa, boolean atual) {
    }

    public record Marca(String rotulo, int posicao) {
    }

    public record ReceitaDespesa(List<Coluna> colunas, List<Marca> eixo, boolean vazio) {
    }

    public record BarraDeGiro(ProdutoGiro produto, int largura) {
    }

    public static ReceitaDespesa receitaDespesa(List<ResumoMensal> meses) {
        BigDecimal maior = meses.stream()
                .flatMap(mes -> Stream.of(mes.receita(), mes.despesaTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        BigDecimal topo = escalaArredondada(maior);

        List<Coluna> colunas = new ArrayList<>();
        for (int indice = 0; indice < meses.size(); indice++) {
            ResumoMensal mes = meses.get(indice);
            String abreviado = mes.mes().getMonth().getDisplayName(TextStyle.SHORT, PORTUGUES).replace(".", "")
                    + "/" + String.valueOf(mes.mes().getYear()).substring(2);
            colunas.add(new Coluna(abreviado, mes.nomeDoMes(), mes.receita(), mes.despesaTotal(),
                    percentual(mes.receita(), topo), percentual(mes.despesaTotal(), topo),
                    indice == meses.size() - 1));
        }

        List<Marca> eixo = new ArrayList<>();
        for (int passo = MARCAS_NO_EIXO; passo >= 0; passo--) {
            BigDecimal valor = topo.multiply(BigDecimal.valueOf(passo))
                    .divide(BigDecimal.valueOf(MARCAS_NO_EIXO), 2, RoundingMode.HALF_UP);
            eixo.add(new Marca(compacto(valor), passo * 100 / MARCAS_NO_EIXO));
        }
        return new ReceitaDespesa(colunas, eixo, maior.signum() == 0);
    }

    public static List<BarraDeGiro> giro(List<ProdutoGiro> produtos) {
        BigDecimal maior = produtos.stream().map(ProdutoGiro::quantidade).reduce(BigDecimal.ZERO, BigDecimal::max);
        return produtos.stream()
                .map(produto -> new BarraDeGiro(produto, Math.max(percentual(produto.quantidade(), maior), 2)))
                .toList();
    }

    /**
     * Topo do eixo: o passo entre as marcas e 1, 2, 2,5 ou 5 vezes uma potencia de dez,
     * para todas as marcas cairem em numeros redondos (1 mil, 2 mil...).
     */
    static BigDecimal escalaArredondada(BigDecimal maior) {
        if (maior.signum() <= 0) {
            return BigDecimal.valueOf(1000);
        }
        double passoMinimo = maior.doubleValue() / MARCAS_NO_EIXO;
        double potencia = Math.pow(10, Math.floor(Math.log10(passoMinimo)));
        double passo = 10 * potencia;
        for (double fator : new double[] {1, 2, 2.5, 5}) {
            if (fator * potencia >= passoMinimo) {
                passo = fator * potencia;
                break;
            }
        }
        return BigDecimal.valueOf(passo * MARCAS_NO_EIXO);
    }

    /** Rotulo curto para o eixo: 0, 500, 1,5 mil, 2 mi. */
    static String compacto(BigDecimal valor) {
        double numero = valor.doubleValue();
        NumberFormat formato = NumberFormat.getNumberInstance(PORTUGUES);
        formato.setMaximumFractionDigits(1);
        if (numero >= 1_000_000) {
            return formato.format(numero / 1_000_000) + " mi";
        }
        if (numero >= 1_000) {
            return formato.format(numero / 1_000) + " mil";
        }
        return formato.format(numero);
    }

    private static int percentual(BigDecimal parte, BigDecimal total) {
        if (total.signum() <= 0 || parte == null) {
            return 0;
        }
        return parte.multiply(CEM).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
