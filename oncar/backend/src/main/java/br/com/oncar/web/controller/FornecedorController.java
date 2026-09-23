package br.com.oncar.web.controller;

import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/fornecedores")
public class FornecedorController {

    private final FornecedorService fornecedorService;

    public FornecedorController(FornecedorService fornecedorService) {
        this.fornecedorService = fornecedorService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("fornecedores", fornecedorService.listar());
        return "fornecedor/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("fornecedor", new Fornecedor());
        return "fornecedor/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("fornecedor", fornecedorService.buscarPorId(id));
        return "fornecedor/form";
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("fornecedor") Fornecedor fornecedor,
                         BindingResult resultado, RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            return "fornecedor/form";
        }
        if (fornecedor.getId() == null) {
            fornecedorService.cadastrar(fornecedor);
            atributos.addFlashAttribute("sucesso", "Fornecedor cadastrado com sucesso.");
        } else {
            fornecedorService.atualizar(fornecedor.getId(), fornecedor);
            atributos.addFlashAttribute("sucesso", "Fornecedor atualizado com sucesso.");
        }
        return "redirect:/fornecedores";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes atributos) {
        fornecedorService.inativar(id);
        atributos.addFlashAttribute("sucesso", "Fornecedor inativado.");
        return "redirect:/fornecedores";
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, RedirectAttributes atributos) {
        fornecedorService.reativar(id);
        atributos.addFlashAttribute("sucesso", "Fornecedor reativado.");
        return "redirect:/fornecedores";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        fornecedorService.excluir(id);
        atributos.addFlashAttribute("sucesso", "Fornecedor excluido.");
        return "redirect:/fornecedores";
    }
}
