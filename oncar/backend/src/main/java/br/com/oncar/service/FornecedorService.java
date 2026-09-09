package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.repository.FornecedorRepository;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de fornecedores (RF05), com inativacao no lugar da exclusao (RN009).
 */
@Service
public class FornecedorService {

    private final FornecedorRepository fornecedorRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final AuditoriaService auditoriaService;

    public FornecedorService(FornecedorRepository fornecedorRepository,
                             MovimentacaoRepository movimentacaoRepository,
                             AuditoriaService auditoriaService) {
        this.fornecedorRepository = fornecedorRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<Fornecedor> listar() {
        return fornecedorRepository.findAllByOrderByRazaoSocialAsc();
    }

    @Transactional(readOnly = true)
    public List<Fornecedor> listarAtivos() {
        return fornecedorRepository.findByAtivoTrueOrderByRazaoSocialAsc();
    }

    @Transactional(readOnly = true)
    public Fornecedor buscarPorId(Long id) {
        return fornecedorRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Fornecedor nao encontrado."));
    }

    @Transactional
    public Fornecedor cadastrar(Fornecedor fornecedor) {
        validarCnpjUnico(fornecedor.getCnpj(), null);
        Fornecedor salvo = fornecedorRepository.save(fornecedor);
        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Fornecedor", salvo.getId(),
                "Fornecedor " + salvo.getRazaoSocial() + " cadastrado");
        return salvo;
    }

    @Transactional
    public Fornecedor atualizar(Long id, Fornecedor dados) {
        Fornecedor fornecedor = buscarPorId(id);
        validarCnpjUnico(dados.getCnpj(), id);
        fornecedor.setRazaoSocial(dados.getRazaoSocial());
        fornecedor.setCnpj(dados.getCnpj());
        fornecedor.setContato(dados.getContato());
        fornecedor.setTelefone(dados.getTelefone());
        fornecedor.setEmail(dados.getEmail());
        fornecedor.setPrazoMedioEntrega(dados.getPrazoMedioEntrega());
        Fornecedor salvo = fornecedorRepository.save(fornecedor);
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Fornecedor", salvo.getId(),
                "Fornecedor " + salvo.getRazaoSocial() + " alterado");
        return salvo;
    }

    @Transactional
    public void inativar(Long id) {
        Fornecedor fornecedor = buscarPorId(id);
        fornecedor.setAtivo(false);
        fornecedorRepository.save(fornecedor);
        auditoriaService.registrar(AcaoAuditoria.INATIVACAO, "Fornecedor", id,
                "Fornecedor " + fornecedor.getRazaoSocial() + " inativado");
    }

    @Transactional
    public void reativar(Long id) {
        Fornecedor fornecedor = buscarPorId(id);
        fornecedor.setAtivo(true);
        fornecedorRepository.save(fornecedor);
        auditoriaService.registrar(AcaoAuditoria.REATIVACAO, "Fornecedor", id,
                "Fornecedor " + fornecedor.getRazaoSocial() + " reativado");
    }

    /** RN009: fornecedor com entradas registradas nao pode ser excluido. */
    @Transactional
    public void excluir(Long id) {
        Fornecedor fornecedor = buscarPorId(id);
        if (movimentacaoRepository.existsByFornecedorId(id)) {
            throw new RegraNegocioException("O fornecedor " + fornecedor.getRazaoSocial()
                    + " possui movimentacoes registradas e nao pode ser excluido. Inative o cadastro.");
        }
        fornecedorRepository.delete(fornecedor);
        auditoriaService.registrar(AcaoAuditoria.EXCLUSAO, "Fornecedor", id,
                "Fornecedor " + fornecedor.getRazaoSocial() + " excluido");
    }

    private void validarCnpjUnico(String cnpj, Long idAtual) {
        fornecedorRepository.findByCnpj(cnpj)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new RegraNegocioException("O CNPJ " + cnpj + " ja esta cadastrado para "
                            + existente.getRazaoSocial() + ".");
                });
    }
}
