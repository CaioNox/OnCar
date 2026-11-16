package br.com.oncar.web.controller;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.DespesaService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.web.form.DespesaForm;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/despesas")
public class DespesaController {

    private final DespesaService despesaService;
    private final FornecedorService fornecedorService;

    public DespesaController(DespesaService despesaService, FornecedorService fornecedorService) {
        this.despesaService = despesaService;
        this.fornecedorService = fornecedorService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) YearMonth inicio,
                         @RequestParam(required = false) YearMonth fim, Model model) {
        Periodo periodo = Periodo.informado(inicio, fim);
        List<Despesa> despesas = despesaService.listarPorCompetencia(periodo);

        BigDecimal total = despesas.stream().map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<CategoriaDespesa, BigDecimal> porCategoria = new LinkedHashMap<>();
        despesas.forEach(despesa -> porCategoria.merge(despesa.getCategoria(), despesa.getValor(), BigDecimal::add));
        List<TotalDaCategoria> totaisPorCategoria = porCategoria.entrySet().stream()
                .map(item -> new TotalDaCategoria(item.getKey().getDescricao(), item.getValue(),
                        percentual(item.getValue(), total)))
                .sorted(Comparator.comparing(TotalDaCategoria::valor).reversed())
                .toList();

        model.addAttribute("periodo", periodo);
        model.addAttribute("despesas", despesas);
        model.addAttribute("totaisPorCategoria", totaisPorCategoria);
        model.addAttribute("total", total);
        return "despesa/lista";
    }

    /** Participacao de uma categoria no total do periodo, para a barra da tela. */
    public record TotalDaCategoria(String descricao, BigDecimal valor, int percentual) {
    }

    private static int percentual(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        return formulario(new DespesaForm(), model);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return formulario(DespesaForm.de(despesaService.buscarPorId(id)), model);
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("despesaForm") DespesaForm form, BindingResult resultado,
                         Principal principal, Model model, RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            prepararFormulario(form, model);
            return "despesa/form";
        }
        Despesa salva;
        try {
            if (form.getId() == null) {
                salva = despesaService.lancar(form.paraDados(), principal.getName());
                atributos.addFlashAttribute("sucesso", "Despesa lancada.");
            } else {
                salva = despesaService.atualizar(form.getId(), form.paraDados());
                atributos.addFlashAttribute("sucesso", "Despesa atualizada.");
            }
        } catch (RegraNegocioException excecao) {
            // volta ao formulario preenchido em vez de perder o que foi digitado
            model.addAttribute("erro", excecao.getMessage());
            prepararFormulario(form, model);
            return "despesa/form";
        }
        return "redirect:/despesas?inicio=" + salva.getMesCompetencia();
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        YearMonth mes = despesaService.buscarPorId(id).getMesCompetencia();
        despesaService.excluir(id);
        atributos.addFlashAttribute("sucesso", "Despesa excluida.");
        return "redirect:/despesas?inicio=" + mes;
    }

    private String formulario(DespesaForm form, Model model) {
        model.addAttribute("despesaForm", form);
        prepararFormulario(form, model);
        return "despesa/form";
    }

    /** Fornecedores ativos e, na edicao, o fornecedor que ja estava vinculado mesmo se inativo. */
    private void prepararFormulario(DespesaForm form, Model model) {
        List<Fornecedor> fornecedores = new ArrayList<>(fornecedorService.listarAtivos());
        if (form.getFornecedorId() != null
                && fornecedores.stream().noneMatch(fornecedor -> fornecedor.getId().equals(form.getFornecedorId()))) {
            fornecedores.add(fornecedorService.buscarPorId(form.getFornecedorId()));
        }
        model.addAttribute("fornecedores", fornecedores);
        model.addAttribute("categorias", CategoriaDespesa.values());
    }
}
