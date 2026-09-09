package br.com.oncar.web.controller;

import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Veiculo;
import br.com.oncar.service.ClienteService;
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

/**
 * Cadastro de clientes e veiculos (RF06).
 */
@Controller
@RequestMapping("/clientes")
public class ClienteController {

    private static final int SLOTS_VEICULOS_VAZIOS = 2;

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("clientes", clienteService.listar());
        return "cliente/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        Cliente cliente = new Cliente();
        acrescentarSlots(cliente);
        model.addAttribute("cliente", cliente);
        return "cliente/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Cliente cliente = clienteService.buscarPorId(id);
        acrescentarSlots(cliente);
        model.addAttribute("cliente", cliente);
        return "cliente/form";
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("cliente") Cliente cliente, BindingResult resultado,
                         RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            acrescentarSlots(cliente);
            return "cliente/form";
        }
        if (cliente.getId() == null) {
            clienteService.cadastrar(cliente);
            atributos.addFlashAttribute("sucesso", "Cliente cadastrado com sucesso.");
        } else {
            clienteService.atualizar(cliente.getId(), cliente);
            atributos.addFlashAttribute("sucesso", "Cliente atualizado com sucesso.");
        }
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes atributos) {
        clienteService.inativar(id);
        atributos.addFlashAttribute("sucesso", "Cliente inativado.");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, RedirectAttributes atributos) {
        clienteService.reativar(id);
        atributos.addFlashAttribute("sucesso", "Cliente reativado.");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        clienteService.excluir(id);
        atributos.addFlashAttribute("sucesso", "Cliente excluido.");
        return "redirect:/clientes";
    }

    /** Linhas em branco para o cadastro de novos veiculos direto no formulario. */
    private void acrescentarSlots(Cliente cliente) {
        for (int i = 0; i < SLOTS_VEICULOS_VAZIOS; i++) {
            cliente.getVeiculos().add(new Veiculo());
        }
    }
}
