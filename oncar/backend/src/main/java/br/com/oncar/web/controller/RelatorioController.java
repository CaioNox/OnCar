package br.com.oncar.web.controller;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.RelatorioService;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import br.com.oncar.web.api.dto.RespostaDtos.Opcao;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Relatorios mensais com filtros por periodo de competencia, categoria, fornecedor e usuario. */
@Controller
@RequestMapping("/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final FornecedorService fornecedorService;
    private final UsuarioService usuarioService;

    public RelatorioController(RelatorioService relatorioService, FornecedorService fornecedorService,
                               UsuarioService usuarioService) {
        this.relatorioService = relatorioService;
        this.fornecedorService = fornecedorService;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String inicio() {
        return "redirect:/relatorios/entradas";
    }

    @GetMapping("/entradas")
    public String entradas(@RequestParam(required = false) YearMonth inicio,
                           @RequestParam(required = false) YearMonth fim,
                           @RequestParam(required = false) CategoriaProduto categoria,
                           @RequestParam(required = false) Long fornecedorId,
                           @RequestParam(required = false) Long usuarioId, Model model) {
        Periodo periodo = Periodo.informado(inicio, fim);
        prepararFiltros(model, "entradas", periodo, opcoesDeProduto(), categoria, fornecedorId, usuarioId, true);
        model.addAttribute("relatorio", relatorioService.entradas(periodo, categoria, fornecedorId, usuarioId));
        return "relatorio/entradas";
    }

    @GetMapping("/saidas")
    public String saidas(@RequestParam(required = false) YearMonth inicio,
                         @RequestParam(required = false) YearMonth fim,
                         @RequestParam(required = false) CategoriaProduto categoria,
                         @RequestParam(required = false) Long fornecedorId,
                         @RequestParam(required = false) Long usuarioId, Model model) {
        Periodo periodo = Periodo.informado(inicio, fim);
        prepararFiltros(model, "saidas", periodo, opcoesDeProduto(), categoria, fornecedorId, usuarioId, true);
        model.addAttribute("relatorio", relatorioService.saidas(periodo, categoria, fornecedorId, usuarioId));
        return "relatorio/saidas";
    }

    @GetMapping("/gastos")
    public String gastos(@RequestParam(required = false) YearMonth inicio,
                         @RequestParam(required = false) YearMonth fim,
                         @RequestParam(required = false) CategoriaDespesa categoria,
                         @RequestParam(required = false) Long fornecedorId,
                         @RequestParam(required = false) Long usuarioId, Model model) {
        Periodo periodo = Periodo.informado(inicio, fim);
        List<Opcao> categorias = Arrays.stream(CategoriaDespesa.values())
                .map(opcao -> new Opcao(opcao.name(), opcao.getDescricao()))
                .toList();
        prepararFiltros(model, "gastos", periodo, categorias, categoria, fornecedorId, usuarioId, true);
        model.addAttribute("relatorio", relatorioService.gastos(periodo, categoria, fornecedorId, usuarioId));
        return "relatorio/gastos";
    }

    @GetMapping("/posicao")
    public String posicao(@RequestParam(required = false) YearMonth inicio,
                          @RequestParam(required = false) YearMonth fim,
                          @RequestParam(required = false) CategoriaProduto categoria,
                          @RequestParam(required = false) Long fornecedorId, Model model) {
        Periodo periodo = Periodo.informado(inicio, fim);
        prepararFiltros(model, "posicao", periodo, opcoesDeProduto(), categoria, fornecedorId, null, false);
        model.addAttribute("relatorio", relatorioService.posicao(periodo, categoria, fornecedorId));
        return "relatorio/posicao";
    }

    private void prepararFiltros(Model model, String relatorioAtivo, Periodo periodo, List<Opcao> categorias,
                                 Enum<?> categoria, Long fornecedorId, Long usuarioId, boolean filtraUsuario) {
        model.addAttribute("relatorioAtivo", relatorioAtivo);
        model.addAttribute("periodo", periodo);
        model.addAttribute("categorias", categorias);
        model.addAttribute("fornecedores", fornecedorService.listar());
        model.addAttribute("usuarios", filtraUsuario ? usuarioService.listar() : List.of());
        model.addAttribute("filtraUsuario", filtraUsuario);
        model.addAttribute("filtroCategoria", categoria == null ? "" : categoria.name());
        model.addAttribute("filtroFornecedor", fornecedorId);
        model.addAttribute("filtroUsuario", usuarioId);
    }

    private static List<Opcao> opcoesDeProduto() {
        return Arrays.stream(CategoriaProduto.values())
                .map(opcao -> new Opcao(opcao.name(), opcao.getDescricao()))
                .toList();
    }
}
