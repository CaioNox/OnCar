package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro e consulta de produtos (RF04 e RF07), com as regras de unicidade
 * do SKU (RN001), inativacao (RN009) e consistencia entre preco e custo (RN010).
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository,
                          MovimentacaoRepository movimentacaoRepository,
                          AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<Produto> listar() {
        return produtoRepository.findAllByOrderByDescricaoAsc();
    }

    @Transactional(readOnly = true)
    public List<Produto> listarAtivos() {
        return produtoRepository.findByAtivoTrueOrderByDescricaoAsc();
    }

    /** RF12 e RN005: itens que atingiram o estoque minimo. */
    @Transactional(readOnly = true)
    public List<Produto> listarAbaixoDoMinimo() {
        return produtoRepository.findAbaixoDoMinimo();
    }

    /** RF07: pesquisa por descricao, SKU, categoria, marca ou fornecedor. */
    @Transactional(readOnly = true)
    public List<Produto> pesquisar(String termo, CategoriaProduto categoria, Long fornecedorId,
                                   boolean somenteAtivos) {
        String termoLimpo = (termo == null || termo.isBlank()) ? null : termo.trim();
        return produtoRepository.pesquisar(termoLimpo, categoria, fornecedorId, somenteAtivos);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.buscarComFornecedor(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Produto nao encontrado."));
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularValorTotalEmEstoque() {
        BigDecimal total = produtoRepository.calcularValorTotalEmEstoque();
        return total == null ? BigDecimal.ZERO : total;
    }

    /** RF04: inclusao de produto. O saldo inicial e sempre zero (RN003). */
    @Transactional
    public Produto cadastrar(Produto produto, boolean confirmaMargemNegativa) {
        validarSkuUnico(produto.getSku(), null);
        validarMargem(produto, confirmaMargemNegativa);
        produto.setSaldo(BigDecimal.ZERO);
        produto.setPrecoCusto(produto.getPrecoCusto() == null ? BigDecimal.ZERO : produto.getPrecoCusto());
        Produto salvo = produtoRepository.save(produto);
        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Produto", salvo.getId(),
                "Produto " + salvo.getSku() + " - " + salvo.getDescricao() + " cadastrado");
        return salvo;
    }

    /** RF04: edicao dos dados cadastrais. Saldo e custo medio nao sao editaveis (RN003 e RN004). */
    @Transactional
    public Produto atualizar(Long id, Produto dados, boolean confirmaMargemNegativa) {
        Produto produto = buscarPorId(id);
        validarSkuUnico(dados.getSku(), id);
        produto.setSku(dados.getSku());
        produto.setDescricao(dados.getDescricao());
        produto.setCategoria(dados.getCategoria());
        produto.setUnidadeMedida(dados.getUnidadeMedida());
        produto.setMarca(dados.getMarca());
        produto.setAplicacao(dados.getAplicacao());
        produto.setPrecoVenda(dados.getPrecoVenda());
        produto.setEstoqueMinimo(dados.getEstoqueMinimo());
        produto.setLocalizacao(dados.getLocalizacao());
        produto.setFornecedorPadrao(dados.getFornecedorPadrao());
        validarMargem(produto, confirmaMargemNegativa);
        Produto salvo = produtoRepository.save(produto);
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Produto", salvo.getId(),
                "Produto " + salvo.getSku() + " alterado");
        return salvo;
    }

    /** RN009: produto com movimentacoes e inativado, nunca excluido. */
    @Transactional
    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
        auditoriaService.registrar(AcaoAuditoria.INATIVACAO, "Produto", id,
                "Produto " + produto.getSku() + " inativado");
    }

    @Transactional
    public void reativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(true);
        produtoRepository.save(produto);
        auditoriaService.registrar(AcaoAuditoria.REATIVACAO, "Produto", id,
                "Produto " + produto.getSku() + " reativado");
    }

    /** RN009: exclusao permitida apenas para produtos sem historico. */
    @Transactional
    public void excluir(Long id) {
        Produto produto = buscarPorId(id);
        if (movimentacaoRepository.existsByProduto(produto)) {
            throw new RegraNegocioException("O produto " + produto.getSku()
                    + " possui movimentacoes registradas e nao pode ser excluido. Inative o cadastro.");
        }
        produtoRepository.delete(produto);
        auditoriaService.registrar(AcaoAuditoria.EXCLUSAO, "Produto", id,
                "Produto " + produto.getSku() + " excluido");
    }

    /**
     * RN001: o SKU e unico na oficina, desconsiderando caixa e espacos nas
     * extremidades, e a verificacao alcanca tambem os produtos inativos.
     */
    private void validarSkuUnico(String sku, Long idAtual) {
        String normalizado = Produto.normalizarSku(sku);
        produtoRepository.findBySkuNormalizado(normalizado)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new RegraNegocioException("O SKU " + sku + " ja esta em uso pelo produto "
                            + existente.getDescricao() + " (codigo " + existente.getId() + ")"
                            + (existente.isAtivo() ? "." : ", atualmente inativo."));
                });
    }

    /**
     * RN010: preco de venda inferior ao custo medio e bloqueado para o Atendente e,
     * para Proprietario e Gerente de Estoque, exige confirmacao explicita registrada
     * no log de auditoria.
     */
    private void validarMargem(Produto produto, boolean confirmaMargemNegativa) {
        BigDecimal custo = produto.getPrecoCusto();
        BigDecimal venda = produto.getPrecoVenda();
        if (custo == null || venda == null || custo.signum() == 0 || venda.compareTo(custo) >= 0) {
            return;
        }
        if (!podeConfirmarMargemNegativa()) {
            throw new RegraNegocioException("O preco de venda nao pode ser inferior ao custo medio de "
                    + custo + ". Procure o gerente de estoque para autorizar a alteracao.");
        }
        if (!confirmaMargemNegativa) {
            throw new RegraNegocioException("O preco de venda informado e inferior ao custo medio de "
                    + custo + ". Marque a confirmacao de margem negativa para prosseguir.");
        }
        auditoriaService.registrar(AcaoAuditoria.MARGEM_NEGATIVA, "Produto", produto.getId(),
                "Margem negativa confirmada: custo medio " + custo + " e preco de venda " + venda);
    }

    private boolean podeConfirmarMargemNegativa() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) {
            return true;
        }
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(Perfil.PROPRIETARIO.getAuthority())
                        || authority.equals(Perfil.GERENTE_ESTOQUE.getAuthority()));
    }
}
