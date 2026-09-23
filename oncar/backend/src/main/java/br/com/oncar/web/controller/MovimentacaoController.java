package br.com.oncar.web.controller;

import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.ClienteService;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.ProdutoService;
import br.com.oncar.web.form.AjusteForm;
import br.com.oncar.web.form.EntradaForm;
import br.com.oncar.web.form.SaidaForm;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Arrays;
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
@RequestMapping("/movimentacoes")
public class MovimentacaoController {

    private final EstoqueService estoqueService;
    private final ProdutoService produtoService;
    private final FornecedorService fornecedorService;
    private final ClienteService clienteService;

    public MovimentacaoController(EstoqueService estoqueService, ProdutoService produtoService,
                                  FornecedorService fornecedorService, ClienteService clienteService) {
        this.estoqueService = estoqueService;
        this.produtoService = produtoService;
        this.fornecedorService = fornecedorService;
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("movimentacoes", estoqueService.listarRecentes());
        return "movimentacao/lista";
    }

    @GetMapping("/entrada")
    public String formularioEntrada(Model model) {
        model.addAttribute("entradaForm", new EntradaForm());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("fornecedores", fornecedorService.listarAtivos());
        return "movimentacao/entrada";
    }

    @PostMapping("/entrada")
    public String registrarEntrada(@Valid @ModelAttribute("entradaForm") EntradaForm form,
                                   BindingResult resultado, Principal principal, Model model,
                                   RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            model.addAttribute("produtos", produtoService.listarAtivos());
            model.addAttribute("fornecedores", fornecedorService.listarAtivos());
            return "movimentacao/entrada";
        }
        Fornecedor fornecedor = form.getFornecedorId() == null ? null
                : fornecedorService.buscarPorId(form.getFornecedorId());
        estoqueService.registrarEntrada(form.getProdutoId(), form.getQuantidade(), form.getCustoUnitario(),
                fornecedor, form.getDocumento(), form.getCompetencia(), principal.getName());
        atributos.addFlashAttribute("sucesso", "Entrada registrada e saldo atualizado.");
        return "redirect:/movimentacoes";
    }

    @GetMapping("/saida")
    public String formularioSaida(Model model) {
        model.addAttribute("saidaForm", new SaidaForm());
        prepararSaida(model);
        return "movimentacao/saida";
    }

    @PostMapping("/saida")
    public String registrarSaida(@Valid @ModelAttribute("saidaForm") SaidaForm form,
                                 BindingResult resultado, Principal principal, Model model,
                                 RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            prepararSaida(model);
            return "movimentacao/saida";
        }
        Cliente cliente = form.getClienteId() == null ? null
                : clienteService.buscarPorId(form.getClienteId());
        estoqueService.registrarSaida(form.getProdutoId(), form.getQuantidade(), form.getMotivo(),
                cliente, form.getDocumento(), form.getCompetencia(), principal.getName());
        atributos.addFlashAttribute("sucesso", "Saida registrada e saldo atualizado.");
        return "redirect:/movimentacoes";
    }

    @GetMapping("/ajuste")
    public String formularioAjuste(Model model) {
        model.addAttribute("ajusteForm", new AjusteForm());
        model.addAttribute("produtos", produtoService.listarAtivos());
        return "movimentacao/ajuste";
    }

    @PostMapping("/ajuste")
    public String registrarAjuste(@Valid @ModelAttribute("ajusteForm") AjusteForm form,
                                  BindingResult resultado, Principal principal, Model model,
                                  RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            model.addAttribute("produtos", produtoService.listarAtivos());
            return "movimentacao/ajuste";
        }
        estoqueService.ajustarInventario(form.getProdutoId(), form.getSaldoApurado(),
                form.getJustificativa(), form.getCompetencia(), principal.getName());
        atributos.addFlashAttribute("sucesso", "Ajuste de inventario registrado.");
        return "redirect:/movimentacoes";
    }

    @PostMapping("/{id}/estorno")
    public String estornar(@PathVariable Long id, @RequestParam String justificativa,
                           Principal principal, RedirectAttributes atributos) {
        estoqueService.estornar(id, justificativa, principal.getName());
        atributos.addFlashAttribute("sucesso", "Movimentacao estornada.");
        return "redirect:/movimentacoes";
    }

    private void prepararSaida(Model model) {
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("clientes", clienteService.listarAtivos());
        model.addAttribute("motivos", Arrays.stream(MotivoMovimentacao.values())
                .filter(MotivoMovimentacao::ehSaida)
                .toList());
    }
}
