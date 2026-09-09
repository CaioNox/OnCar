package br.com.oncar.web.api;

import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.service.ClienteService;
import br.com.oncar.service.EstoqueService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.web.api.dto.RequisicaoDtos;
import br.com.oncar.web.api.dto.RespostaDtos;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Movimentacoes de estoque pela API REST: entrada (RF08), saida (RF09),
 * ajuste (RF11) e estorno (RN008). As regras ficam no servico de estoque -
 * aqui apenas traduzimos a requisicao.
 */
@RestController
@RequestMapping("/api/movimentacoes")
public class MovimentacaoApiController {

    private final EstoqueService estoqueService;
    private final FornecedorService fornecedorService;
    private final ClienteService clienteService;

    public MovimentacaoApiController(EstoqueService estoqueService, FornecedorService fornecedorService,
                                     ClienteService clienteService) {
        this.estoqueService = estoqueService;
        this.fornecedorService = fornecedorService;
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<RespostaDtos.MovimentacaoResumo> listar() {
        return estoqueService.listarRecentes().stream()
                .map(RespostaDtos.MovimentacaoResumo::de)
                .toList();
    }

    @PostMapping("/entrada")
    public RespostaDtos.MovimentacaoResumo registrarEntrada(@Valid @RequestBody RequisicaoDtos.Entrada requisicao,
                                                            Principal principal) {
        Fornecedor fornecedor = requisicao.fornecedorId() == null ? null
                : fornecedorService.buscarPorId(requisicao.fornecedorId());
        return RespostaDtos.MovimentacaoResumo.de(estoqueService.registrarEntrada(
                requisicao.produtoId(), requisicao.quantidade(), requisicao.custoUnitario(), fornecedor,
                requisicao.documento(), requisicao.competencia(), principal.getName()));
    }

    @PostMapping("/saida")
    public RespostaDtos.MovimentacaoResumo registrarSaida(@Valid @RequestBody RequisicaoDtos.Saida requisicao,
                                                          Principal principal) {
        Cliente cliente = requisicao.clienteId() == null ? null
                : clienteService.buscarPorId(requisicao.clienteId());
        return RespostaDtos.MovimentacaoResumo.de(estoqueService.registrarSaida(
                requisicao.produtoId(), requisicao.quantidade(), motivoDe(requisicao.motivo()), cliente,
                requisicao.documento(), requisicao.competencia(), principal.getName()));
    }

    @PostMapping("/ajuste")
    public RespostaDtos.MovimentacaoResumo ajustar(@Valid @RequestBody RequisicaoDtos.Ajuste requisicao,
                                                   Principal principal) {
        return RespostaDtos.MovimentacaoResumo.de(estoqueService.ajustarInventario(
                requisicao.produtoId(), requisicao.saldoApurado(), requisicao.justificativa(),
                requisicao.competencia(), principal.getName()));
    }

    @PostMapping("/{id}/estorno")
    public RespostaDtos.MovimentacaoResumo estornar(@PathVariable Long id,
                                                    @Valid @RequestBody RequisicaoDtos.Estorno requisicao,
                                                    Principal principal) {
        return RespostaDtos.MovimentacaoResumo.de(
                estoqueService.estornar(id, requisicao.justificativa(), principal.getName()));
    }

    /** Motivos de saida aceitos pelo RF09. */
    @GetMapping("/motivos")
    public List<RespostaDtos.Opcao> motivos() {
        return java.util.Arrays.stream(MotivoMovimentacao.values())
                .filter(MotivoMovimentacao::ehSaida)
                .map(motivo -> new RespostaDtos.Opcao(motivo.name(), motivo.getDescricao()))
                .toList();
    }

    private MotivoMovimentacao motivoDe(String valor) {
        try {
            return MotivoMovimentacao.valueOf(valor);
        } catch (IllegalArgumentException excecao) {
            throw new RegraNegocioException("Selecione um motivo de saida valido.");
        }
    }
}
