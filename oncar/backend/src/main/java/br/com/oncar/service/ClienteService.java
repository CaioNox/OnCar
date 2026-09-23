package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Veiculo;
import br.com.oncar.repository.ClienteRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository,
                          MovimentacaoRepository movimentacaoRepository,
                          AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<Cliente> listar() {
        return clienteRepository.listarComVeiculos();
    }

    @Transactional(readOnly = true)
    public List<Cliente> listarAtivos() {
        return clienteRepository.findByAtivoTrueOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Cliente buscarPorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Cliente nao encontrado."));
        cliente.getVeiculos().size();
        return cliente;
    }

    @Transactional
    public Cliente cadastrar(Cliente cliente) {
        validarDocumentoUnico(cliente.getDocumento(), null);
        vincularVeiculos(cliente);
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Cliente", salvo.getId(),
                "Cliente " + salvo.getNome() + " cadastrado");
        return salvo;
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente dados) {
        Cliente cliente = buscarPorId(id);
        validarDocumentoUnico(dados.getDocumento(), id);
        cliente.setNome(dados.getNome());
        cliente.setDocumento(dados.getDocumento());
        cliente.setContato(dados.getContato());
        cliente.setTelefone(dados.getTelefone());
        cliente.setEmail(dados.getEmail());
        cliente.getVeiculos().clear();
        dados.getVeiculos().stream()
                .filter(veiculo -> veiculo.getPlaca() != null && !veiculo.getPlaca().isBlank())
                .forEach(cliente::adicionarVeiculo);
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Cliente", salvo.getId(),
                "Cliente " + salvo.getNome() + " alterado");
        return salvo;
    }

    @Transactional
    public void inativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setAtivo(false);
        clienteRepository.save(cliente);
        auditoriaService.registrar(AcaoAuditoria.INATIVACAO, "Cliente", id,
                "Cliente " + cliente.getNome() + " inativado");
    }

    @Transactional
    public void reativar(Long id) {
        Cliente cliente = buscarPorId(id);
        cliente.setAtivo(true);
        clienteRepository.save(cliente);
        auditoriaService.registrar(AcaoAuditoria.REATIVACAO, "Cliente", id,
                "Cliente " + cliente.getNome() + " reativado");
    }

    @Transactional
    public void excluir(Long id) {
        Cliente cliente = buscarPorId(id);
        if (movimentacaoRepository.existsByClienteId(id)) {
            throw new RegraNegocioException("O cliente " + cliente.getNome()
                    + " possui movimentacoes registradas e nao pode ser excluido. Inative o cadastro.");
        }
        clienteRepository.delete(cliente);
        auditoriaService.registrar(AcaoAuditoria.EXCLUSAO, "Cliente", id,
                "Cliente " + cliente.getNome() + " excluido");
    }

    private void vincularVeiculos(Cliente cliente) {
        List<Veiculo> informados = List.copyOf(cliente.getVeiculos());
        cliente.getVeiculos().clear();
        informados.stream()
                .filter(veiculo -> veiculo.getPlaca() != null && !veiculo.getPlaca().isBlank())
                .forEach(cliente::adicionarVeiculo);
    }

    private void validarDocumentoUnico(String documento, Long idAtual) {
        clienteRepository.findByDocumento(documento)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new RegraNegocioException("O documento " + documento
                            + " ja esta cadastrado para o cliente " + existente.getNome() + ".");
                });
    }
}
