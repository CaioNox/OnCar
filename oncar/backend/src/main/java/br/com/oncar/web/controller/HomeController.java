package br.com.oncar.web.controller;

import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.service.RelatorioService;
import br.com.oncar.service.relatorio.Relatorios.IndicadoresFinanceiros;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.web.api.PermissoesApi;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Meses do grafico de receita x despesa, incluindo o corrente. */
    private static final int MESES_NO_HISTORICO = 6;

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final RelatorioService relatorioService;
    private final PermissoesApi permissoes;

    public HomeController(ProdutoService produtoService, EstoqueService estoqueService,
                          RelatorioService relatorioService, PermissoesApi permissoes) {
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
        this.relatorioService = relatorioService;
        this.permissoes = permissoes;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        YearMonth mesAtual = YearMonth.now();
        boolean exibirFinanceiro = permissoes.podeVerFinanceiro();

        model.addAttribute("produtosAbaixoDoMinimo", produtoService.listarAbaixoDoMinimo());
        model.addAttribute("ultimasMovimentacoes", estoqueService.listarRecentes());
        model.addAttribute("valorTotalEmEstoque", produtoService.calcularValorTotalEmEstoque());
        model.addAttribute("totalProdutosAtivos", produtoService.listarAtivos().size());
        model.addAttribute("nomeDoMes", Periodo.nomeDoMes(mesAtual));
        model.addAttribute("maiorGiro", GraficoMensal.giro(relatorioService.maiorGiro(mesAtual, exibirFinanceiro)));
        // receita e despesa so sao calculadas para quem pode ve-las
        if (exibirFinanceiro) {
            IndicadoresFinanceiros financeiro = relatorioService.indicadoresFinanceiros(mesAtual, MESES_NO_HISTORICO);
            model.addAttribute("financeiro", financeiro);
            model.addAttribute("grafico", GraficoMensal.receitaDespesa(financeiro.historico()));
        }
        return "home/inicio";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "erro/acesso-negado";
    }
}
