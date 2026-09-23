package br.com.oncar.web.controller;

import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.ProdutoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;

    public HomeController(ProdutoService produtoService, EstoqueService estoqueService) {
        this.produtoService = produtoService;
        this.estoqueService = estoqueService;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("produtosAbaixoDoMinimo", produtoService.listarAbaixoDoMinimo());
        model.addAttribute("ultimasMovimentacoes", estoqueService.listarRecentes());
        model.addAttribute("valorTotalEmEstoque", produtoService.calcularValorTotalEmEstoque());
        model.addAttribute("totalProdutosAtivos", produtoService.listarAtivos().size());
        return "home/inicio";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "erro/acesso-negado";
    }
}
