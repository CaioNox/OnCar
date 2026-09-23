package br.com.oncar.web.api;

import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/painel")
public class PainelApiController {

    private static final int MOVIMENTACOES_NO_PAINEL = 10;

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final PermissoesApi permissoes;

    public PainelApiController(ProdutoService produtoService, EstoqueService estoqueService,
                               PermissoesApi permissoes) {
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
        this.permissoes = permissoes;
    }

    @GetMapping
    public RespostaDtos.Painel indicadores() {
        boolean exibirCusto = permissoes.podeVerCustos();
        List<RespostaDtos.ProdutoResumo> reposicao = produtoService.listarAbaixoDoMinimo().stream()
                .map(produto -> RespostaDtos.ProdutoResumo.de(produto, exibirCusto))
                .toList();
        List<RespostaDtos.MovimentacaoResumo> movimentacoes = estoqueService.listarRecentes().stream()
                .limit(MOVIMENTACOES_NO_PAINEL)
                .map(RespostaDtos.MovimentacaoResumo::de)
                .toList();
        BigDecimal valorEmEstoque = exibirCusto ? produtoService.calcularValorTotalEmEstoque() : null;

        return new RespostaDtos.Painel(produtoService.listarAtivos().size(), reposicao.size(),
                valorEmEstoque, reposicao, movimentacoes);
    }
}
