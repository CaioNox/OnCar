package br.com.oncar.web.api;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.RelatorioService;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.service.relatorio.Relatorios;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.time.YearMonth;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatorios por periodo de competencia (`inicio` e `fim` no formato AAAA-MM).
 * Sem periodo, vale o mes corrente; com so um dos lados, o periodo e de um mes.
 */
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioApiController {

    private final RelatorioService relatorioService;
    private final FornecedorService fornecedorService;
    private final UsuarioService usuarioService;

    public RelatorioApiController(RelatorioService relatorioService, FornecedorService fornecedorService,
                                  UsuarioService usuarioService) {
        this.relatorioService = relatorioService;
        this.fornecedorService = fornecedorService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/filtros")
    public RespostaDtos.FiltrosDeRelatorio filtros() {
        return new RespostaDtos.FiltrosDeRelatorio(
                Arrays.stream(CategoriaProduto.values())
                        .map(categoria -> new RespostaDtos.Opcao(categoria.name(), categoria.getDescricao()))
                        .toList(),
                Arrays.stream(CategoriaDespesa.values())
                        .map(categoria -> new RespostaDtos.Opcao(categoria.name(), categoria.getDescricao()))
                        .toList(),
                fornecedorService.listar().stream().map(RespostaDtos.FornecedorResumo::de).toList(),
                usuarioService.listar().stream()
                        .map(usuario -> new RespostaDtos.Opcao(String.valueOf(usuario.getId()), usuario.getNome()))
                        .toList());
    }

    @GetMapping("/entradas")
    public Relatorios.RelatorioEntradas entradas(@RequestParam(required = false) YearMonth inicio,
                                                 @RequestParam(required = false) YearMonth fim,
                                                 @RequestParam(required = false) CategoriaProduto categoria,
                                                 @RequestParam(required = false) Long fornecedorId,
                                                 @RequestParam(required = false) Long usuarioId) {
        return relatorioService.entradas(periodo(inicio, fim), categoria, fornecedorId, usuarioId);
    }

    @GetMapping("/saidas")
    public Relatorios.RelatorioSaidas saidas(@RequestParam(required = false) YearMonth inicio,
                                             @RequestParam(required = false) YearMonth fim,
                                             @RequestParam(required = false) CategoriaProduto categoria,
                                             @RequestParam(required = false) Long fornecedorId,
                                             @RequestParam(required = false) Long usuarioId) {
        return relatorioService.saidas(periodo(inicio, fim), categoria, fornecedorId, usuarioId);
    }

    @GetMapping("/gastos")
    public Relatorios.RelatorioGastos gastos(@RequestParam(required = false) YearMonth inicio,
                                             @RequestParam(required = false) YearMonth fim,
                                             @RequestParam(required = false) CategoriaDespesa categoria,
                                             @RequestParam(required = false) Long fornecedorId,
                                             @RequestParam(required = false) Long usuarioId) {
        return relatorioService.gastos(periodo(inicio, fim), categoria, fornecedorId, usuarioId);
    }

    @GetMapping("/posicao")
    public Relatorios.RelatorioPosicao posicao(@RequestParam(required = false) YearMonth inicio,
                                               @RequestParam(required = false) YearMonth fim,
                                               @RequestParam(required = false) CategoriaProduto categoria,
                                               @RequestParam(required = false) Long fornecedorId) {
        return relatorioService.posicao(periodo(inicio, fim), categoria, fornecedorId);
    }

    private static Relatorios.Periodo periodo(YearMonth inicio, YearMonth fim) {
        return Relatorios.Periodo.informado(inicio, fim);
    }
}
