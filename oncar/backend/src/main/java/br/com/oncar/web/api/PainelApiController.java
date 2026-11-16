package br.com.oncar.web.api;

import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.service.RelatorioService;
import br.com.oncar.service.relatorio.Relatorios;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/painel")
public class PainelApiController {

    private static final int MOVIMENTACOES_NO_PAINEL = 10;
    /** Meses do grafico de receita x despesa, incluindo o corrente. */
    private static final int MESES_NO_HISTORICO = 6;

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final RelatorioService relatorioService;
    private final PermissoesApi permissoes;

    public PainelApiController(ProdutoService produtoService, EstoqueService estoqueService,
                               RelatorioService relatorioService, PermissoesApi permissoes) {
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
        this.relatorioService = relatorioService;
        this.permissoes = permissoes;
    }

    @GetMapping
    public RespostaDtos.Painel indicadores() {
        boolean exibirCusto = permissoes.podeVerCustos();
        boolean exibirFinanceiro = permissoes.podeVerFinanceiro();
        YearMonth mesAtual = YearMonth.now();

        List<RespostaDtos.ProdutoResumo> reposicao = produtoService.listarAbaixoDoMinimo().stream()
                .map(produto -> RespostaDtos.ProdutoResumo.de(produto, exibirCusto))
                .toList();
        List<RespostaDtos.MovimentacaoResumo> movimentacoes = estoqueService.listarRecentes().stream()
                .limit(MOVIMENTACOES_NO_PAINEL)
                .map(RespostaDtos.MovimentacaoResumo::de)
                .toList();
        BigDecimal valorEmEstoque = exibirCusto ? produtoService.calcularValorTotalEmEstoque() : null;
        Relatorios.IndicadoresFinanceiros financeiro = exibirFinanceiro
                ? relatorioService.indicadoresFinanceiros(mesAtual, MESES_NO_HISTORICO) : null;

        return new RespostaDtos.Painel(produtoService.listarAtivos().size(), reposicao.size(),
                valorEmEstoque, reposicao, movimentacoes, financeiro,
                relatorioService.maiorGiro(mesAtual, exibirFinanceiro));
    }
}
