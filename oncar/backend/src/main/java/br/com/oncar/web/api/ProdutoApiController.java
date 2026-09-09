package br.com.oncar.web.api;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de produtos e do extrato pela API REST (RF04, RF07, RF12 e RF13).
 */
@RestController
@RequestMapping("/api/produtos")
public class ProdutoApiController {

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final PermissoesApi permissoes;

    public ProdutoApiController(ProdutoService produtoService, EstoqueService estoqueService,
                                PermissoesApi permissoes) {
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
        this.permissoes = permissoes;
    }

    @GetMapping
    public List<RespostaDtos.ProdutoResumo> pesquisar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) CategoriaProduto categoria,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(defaultValue = "true") boolean somenteAtivos) {
        return converter(produtoService.pesquisar(termo, categoria, fornecedorId, somenteAtivos));
    }

    /** RF12 e RN005: itens que atingiram o estoque minimo. */
    @GetMapping("/reposicao")
    public List<RespostaDtos.ProdutoResumo> reposicao() {
        return converter(produtoService.listarAbaixoDoMinimo());
    }

    @GetMapping("/{id}")
    public RespostaDtos.ProdutoResumo detalhar(@PathVariable Long id) {
        return RespostaDtos.ProdutoResumo.de(produtoService.buscarPorId(id), permissoes.podeVerCustos());
    }

    /** RF13: extrato (kardex) do produto. */
    @GetMapping("/{id}/extrato")
    public List<RespostaDtos.MovimentacaoResumo> extrato(@PathVariable Long id) {
        return estoqueService.listarExtrato(id).stream()
                .map(RespostaDtos.MovimentacaoResumo::de)
                .toList();
    }

    private List<RespostaDtos.ProdutoResumo> converter(List<Produto> produtos) {
        boolean exibirCusto = permissoes.podeVerCustos();
        return produtos.stream()
                .map(produto -> RespostaDtos.ProdutoResumo.de(produto, exibirCusto))
                .toList();
    }
}
