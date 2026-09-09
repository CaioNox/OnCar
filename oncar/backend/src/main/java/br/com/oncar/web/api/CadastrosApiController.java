package br.com.oncar.web.api;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.service.AuditoriaService;
import br.com.oncar.service.ClienteService;
import br.com.oncar.service.FornecedorService;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listas de apoio consumidas pelos formularios do front-end (RF05, RF06 e RF20).
 */
@RestController
@RequestMapping("/api")
public class CadastrosApiController {

    private final FornecedorService fornecedorService;
    private final ClienteService clienteService;
    private final AuditoriaService auditoriaService;

    public CadastrosApiController(FornecedorService fornecedorService, ClienteService clienteService,
                                  AuditoriaService auditoriaService) {
        this.fornecedorService = fornecedorService;
        this.clienteService = clienteService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/fornecedores")
    public List<RespostaDtos.FornecedorResumo> fornecedores() {
        return fornecedorService.listarAtivos().stream()
                .map(RespostaDtos.FornecedorResumo::de)
                .toList();
    }

    @GetMapping("/clientes")
    public List<RespostaDtos.ClienteResumo> clientes() {
        return clienteService.listarAtivos().stream()
                .map(RespostaDtos.ClienteResumo::de)
                .toList();
    }

    @GetMapping("/categorias")
    public List<RespostaDtos.Opcao> categorias() {
        return Arrays.stream(CategoriaProduto.values())
                .map(categoria -> new RespostaDtos.Opcao(categoria.name(), categoria.getDescricao()))
                .toList();
    }

    /** RF20: consulta do log de auditoria, restrita ao Proprietario (RN007). */
    @GetMapping("/auditoria")
    public List<RespostaDtos.RegistroAuditoria> auditoria() {
        return auditoriaService.listarRecentes().stream()
                .map(RespostaDtos.RegistroAuditoria::de)
                .toList();
    }
}
