package br.com.oncar.web.controller;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final FornecedorService fornecedorService;
    private final EstoqueService estoqueService;

    public ProdutoController(ProdutoService produtoService, FornecedorService fornecedorService,
                             EstoqueService estoqueService) {
        this.produtoService = produtoService;
        this.fornecedorService = fornecedorService;
        this.estoqueService = estoqueService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String termo,
                         @RequestParam(required = false) CategoriaProduto categoria,
                         @RequestParam(required = false) Long fornecedorId,
                         @RequestParam(defaultValue = "true") boolean somenteAtivos,
                         Model model) {
        model.addAttribute("produtos", produtoService.pesquisar(termo, categoria, fornecedorId, somenteAtivos));
        model.addAttribute("termo", termo);
        model.addAttribute("categoriaSelecionada", categoria);
        model.addAttribute("fornecedorSelecionado", fornecedorId);
        model.addAttribute("somenteAtivos", somenteAtivos);
        model.addAttribute("categorias", CategoriaProduto.values());
        model.addAttribute("fornecedores", fornecedorService.listarAtivos());
        return "produto/lista";
    }

    @GetMapping("/reposicao")
    public String reposicao(Model model) {
        model.addAttribute("produtos", produtoService.listarAbaixoDoMinimo());
        return "produto/reposicao";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("produto", new Produto());
        prepararFormulario(model);
        return "produto/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id));
        prepararFormulario(model);
        return "produto/form";
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("produto") Produto produto, BindingResult resultado,
                         @RequestParam(defaultValue = "false") boolean confirmaMargemNegativa,
                         Model model, RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            prepararFormulario(model);
            return "produto/form";
        }
        if (produto.getId() == null) {
            produtoService.cadastrar(produto, confirmaMargemNegativa);
            atributos.addFlashAttribute("sucesso", "Produto cadastrado com sucesso.");
        } else {
            produtoService.atualizar(produto.getId(), produto, confirmaMargemNegativa);
            atributos.addFlashAttribute("sucesso", "Produto atualizado com sucesso.");
        }
        return "redirect:/produtos";
    }

    @GetMapping("/{id}/extrato")
    public String extrato(@PathVariable Long id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id));
        model.addAttribute("movimentacoes", estoqueService.listarExtrato(id));
        return "produto/extrato";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes atributos) {
        produtoService.inativar(id);
        atributos.addFlashAttribute("sucesso", "Produto inativado.");
        return "redirect:/produtos?somenteAtivos=false";
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, RedirectAttributes atributos) {
        produtoService.reativar(id);
        atributos.addFlashAttribute("sucesso", "Produto reativado.");
        return "redirect:/produtos?somenteAtivos=false";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        produtoService.excluir(id);
        atributos.addFlashAttribute("sucesso", "Produto excluido.");
        return "redirect:/produtos";
    }

    private void prepararFormulario(Model model) {
        model.addAttribute("categorias", CategoriaProduto.values());
        model.addAttribute("unidades", UnidadeMedida.values());
        model.addAttribute("fornecedores", fornecedorService.listarAtivos());
    }
}
