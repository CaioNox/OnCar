package br.com.oncar.web.api;

import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.service.DespesaService;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios;
import br.com.oncar.web.api.dto.RequisicaoDtos;
import br.com.oncar.web.api.dto.RespostaDtos;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/despesas")
public class DespesaApiController {

    private final DespesaService despesaService;

    public DespesaApiController(DespesaService despesaService) {
        this.despesaService = despesaService;
    }

    /** Sem periodo informado, devolve as despesas do mes corrente. */
    @GetMapping
    public List<RespostaDtos.DespesaResumo> listar(@RequestParam(required = false) YearMonth inicio,
                                                   @RequestParam(required = false) YearMonth fim) {
        return despesaService.listarPorCompetencia(Relatorios.Periodo.informado(inicio, fim)).stream()
                .map(RespostaDtos.DespesaResumo::de)
                .toList();
    }

    @GetMapping("/{id}")
    public RespostaDtos.DespesaResumo detalhar(@PathVariable Long id) {
        return RespostaDtos.DespesaResumo.de(despesaService.buscarPorId(id));
    }

    @PostMapping
    public RespostaDtos.DespesaResumo lancar(@Valid @RequestBody RequisicaoDtos.Despesa requisicao,
                                             Principal principal) {
        return RespostaDtos.DespesaResumo.de(despesaService.lancar(dadosDe(requisicao), principal.getName()));
    }

    @PutMapping("/{id}")
    public RespostaDtos.DespesaResumo atualizar(@PathVariable Long id,
                                                @Valid @RequestBody RequisicaoDtos.Despesa requisicao) {
        return RespostaDtos.DespesaResumo.de(despesaService.atualizar(id, dadosDe(requisicao)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        despesaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categorias")
    public List<RespostaDtos.Opcao> categorias() {
        return Arrays.stream(CategoriaDespesa.values())
                .map(categoria -> new RespostaDtos.Opcao(categoria.name(), categoria.getDescricao()))
                .toList();
    }

    private DespesaService.DadosDespesa dadosDe(RequisicaoDtos.Despesa requisicao) {
        return new DespesaService.DadosDespesa(requisicao.descricao(), categoriaDe(requisicao.categoria()),
                requisicao.valor(), requisicao.competencia(), requisicao.fornecedorId(),
                requisicao.documento(), requisicao.observacao());
    }

    private CategoriaDespesa categoriaDe(String valor) {
        try {
            return CategoriaDespesa.valueOf(valor);
        } catch (IllegalArgumentException excecao) {
            throw new RegraNegocioException("Selecione uma categoria de despesa valida.");
        }
    }
}
