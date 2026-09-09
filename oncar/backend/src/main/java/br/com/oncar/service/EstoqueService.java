package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.TipoMovimentacao;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nucleo do controle de estoque: entradas (RF08), saidas (RF09), atualizacao
 * automatica do saldo (RF10), ajuste de inventario (RF11), extrato (RF13) e estorno.
 *
 * <p>Cada operacao grava a movimentacao e atualiza o saldo do produto dentro de
 * uma unica transacao (RN003); qualquer falha desfaz as duas etapas.</p>
 */
@Service
public class EstoqueService {

    private static final int ESCALA_QUANTIDADE = 3;
    private static final int ESCALA_CUSTO = 4;
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;
    private static final int LIMITE_MOVIMENTACOES_RECENTES = 50;

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public EstoqueService(ProdutoRepository produtoRepository,
                          MovimentacaoRepository movimentacaoRepository,
                          UsuarioService usuarioService,
                          AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * RF08: entrada de mercadoria. Alem de somar a quantidade ao saldo, recalcula
     * o custo medio ponderado do produto conforme a RN004.
     */
    @Transactional
    public Movimentacao registrarEntrada(Long produtoId, BigDecimal quantidade, BigDecimal custoUnitario,
                                         Fornecedor fornecedor, String documento, LocalDate competencia,
                                         String emailUsuario) {
        Produto produto = carregarProduto(produtoId);
        validarQuantidade(quantidade);
        if (custoUnitario == null || custoUnitario.signum() < 0) {
            throw new RegraNegocioException("Informe o custo unitario da compra.");
        }

        BigDecimal saldoAnterior = produto.getSaldo();
        BigDecimal saldoResultante = normalizarQuantidade(saldoAnterior.add(quantidade));
        BigDecimal novoCusto = calcularCustoMedioPonderado(saldoAnterior, produto.getPrecoCusto(),
                quantidade, custoUnitario);

        produto.setSaldo(saldoResultante);
        produto.setPrecoCusto(novoCusto);
        produtoRepository.save(produto);

        Movimentacao movimentacao = novaMovimentacao(produto, TipoMovimentacao.ENTRADA,
                MotivoMovimentacao.COMPRA_FORNECEDOR, quantidade, custoUnitario,
                saldoAnterior, saldoResultante, documento, null, competencia, emailUsuario);
        movimentacao.setFornecedor(fornecedor);
        Movimentacao salva = movimentacaoRepository.save(movimentacao);

        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Movimentacao", salva.getId(),
                "Entrada de " + quantidade + " " + produto.getUnidadeMedida().getSigla()
                        + " no produto " + produto.getSku() + " (novo saldo " + saldoResultante + ")");
        return salva;
    }

    /**
     * RF09: saida por venda de balcao, ordem de servico, devolucao ao fornecedor
     * ou perda/avaria. A operacao e integralmente rejeitada se o saldo for
     * insuficiente (RN002).
     */
    @Transactional
    public Movimentacao registrarSaida(Long produtoId, BigDecimal quantidade, MotivoMovimentacao motivo,
                                       Cliente cliente, String documento, LocalDate competencia,
                                       String emailUsuario) {
        Produto produto = carregarProduto(produtoId);
        validarQuantidade(quantidade);
        if (motivo == null || !motivo.ehSaida()) {
            throw new RegraNegocioException("Selecione o motivo da saida.");
        }

        BigDecimal saldoAnterior = produto.getSaldo();
        if (quantidade.compareTo(saldoAnterior) > 0) {
            throw new RegraNegocioException("Saldo insuficiente para o produto " + produto.getSku()
                    + ": disponivel " + saldoAnterior + " " + produto.getUnidadeMedida().getSigla()
                    + " e solicitado " + quantidade + ". Nenhum item foi baixado.");
        }

        BigDecimal saldoResultante = normalizarQuantidade(saldoAnterior.subtract(quantidade));
        produto.setSaldo(saldoResultante);
        produtoRepository.save(produto);

        Movimentacao movimentacao = novaMovimentacao(produto, TipoMovimentacao.SAIDA, motivo,
                quantidade, produto.getPrecoCusto(), saldoAnterior, saldoResultante,
                documento, null, competencia, emailUsuario);
        movimentacao.setCliente(cliente);
        Movimentacao salva = movimentacaoRepository.save(movimentacao);

        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Movimentacao", salva.getId(),
                "Saida de " + quantidade + " " + produto.getUnidadeMedida().getSigla()
                        + " do produto " + produto.getSku() + " por " + motivo.getDescricao()
                        + " (novo saldo " + saldoResultante + ")");
        return salva;
    }

    /**
     * RF11 e RN006: ajuste de inventario apos contagem fisica. Exige o saldo
     * apurado e uma justificativa com no minimo dez caracteres.
     */
    @Transactional
    public Movimentacao ajustarInventario(Long produtoId, BigDecimal saldoApurado, String justificativa,
                                          LocalDate competencia, String emailUsuario) {
        Produto produto = carregarProduto(produtoId);
        if (saldoApurado == null || saldoApurado.signum() < 0) {
            throw new RegraNegocioException("Informe o saldo apurado na contagem fisica.");
        }
        validarJustificativa(justificativa);

        BigDecimal saldoAnterior = produto.getSaldo();
        BigDecimal saldoResultante = normalizarQuantidade(saldoApurado);
        if (saldoResultante.compareTo(saldoAnterior) == 0) {
            throw new RegraNegocioException("O saldo apurado e igual ao saldo atual do produto. "
                    + "Nao ha ajuste a registrar.");
        }

        produto.setSaldo(saldoResultante);
        produtoRepository.save(produto);

        BigDecimal diferenca = saldoResultante.subtract(saldoAnterior);
        Movimentacao movimentacao = novaMovimentacao(produto, TipoMovimentacao.AJUSTE,
                MotivoMovimentacao.AJUSTE_INVENTARIO, diferenca.abs(), produto.getPrecoCusto(),
                saldoAnterior, saldoResultante, null, justificativa, competencia, emailUsuario);
        Movimentacao salva = movimentacaoRepository.save(movimentacao);

        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Movimentacao", salva.getId(),
                "Ajuste de inventario no produto " + produto.getSku() + ": saldo " + saldoAnterior
                        + " corrigido para " + saldoResultante);
        return salva;
    }

    /**
     * RN008: movimentacoes confirmadas nao sao editadas nem excluidas. A correcao
     * de um lancamento equivocado gera um novo lancamento de sinal contrario,
     * referenciando o documento original e exigindo justificativa.
     */
    @Transactional
    public Movimentacao estornar(Long movimentacaoId, String justificativa, String emailUsuario) {
        Movimentacao original = movimentacaoRepository.findById(movimentacaoId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Movimentacao nao encontrada."));
        validarJustificativa(justificativa);
        if (original.isEstornada()) {
            throw new RegraNegocioException("Esta movimentacao ja foi estornada.");
        }
        if (original.getTipo() == TipoMovimentacao.ESTORNO) {
            throw new RegraNegocioException("Um estorno nao pode ser estornado. "
                    + "Registre uma nova movimentacao, se necessario.");
        }

        Produto produto = original.getProduto();
        BigDecimal saldoAnterior = produto.getSaldo();
        BigDecimal quantidade = original.getQuantidade();
        BigDecimal saldoResultante;

        switch (original.getTipo()) {
            case ENTRADA -> {
                if (quantidade.compareTo(saldoAnterior) > 0) {
                    throw new RegraNegocioException("Nao e possivel estornar a entrada: o saldo atual ("
                            + saldoAnterior + ") e menor que a quantidade lancada (" + quantidade
                            + "). Registre um ajuste de inventario.");
                }
                saldoResultante = normalizarQuantidade(saldoAnterior.subtract(quantidade));
                produto.setPrecoCusto(reverterCustoMedioPonderado(saldoAnterior, produto.getPrecoCusto(),
                        quantidade, original.getCustoUnitario()));
            }
            case SAIDA -> {
                saldoResultante = normalizarQuantidade(saldoAnterior.add(quantidade));
                produto.setPrecoCusto(calcularCustoMedioPonderado(saldoAnterior, produto.getPrecoCusto(),
                        quantidade, original.getCustoUnitario()));
            }
            case AJUSTE -> saldoResultante = normalizarQuantidade(original.getSaldoAnterior());
            default -> throw new RegraNegocioException("Tipo de movimentacao nao suporta estorno.");
        }

        produto.setSaldo(saldoResultante);
        produtoRepository.save(produto);

        original.setEstornada(true);
        movimentacaoRepository.save(original);

        Movimentacao estorno = novaMovimentacao(produto, TipoMovimentacao.ESTORNO,
                MotivoMovimentacao.ESTORNO_MOVIMENTACAO, saldoResultante.subtract(saldoAnterior).abs(),
                original.getCustoUnitario(), saldoAnterior, saldoResultante, original.getDocumento(),
                justificativa, original.getCompetencia(), emailUsuario);
        estorno.setMovimentacaoOrigem(original);
        estorno.setFornecedor(original.getFornecedor());
        estorno.setCliente(original.getCliente());
        Movimentacao salva = movimentacaoRepository.save(estorno);

        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Movimentacao", salva.getId(),
                "Estorno da movimentacao " + original.getId() + " do produto " + produto.getSku()
                        + " (saldo " + saldoAnterior + " para " + saldoResultante + ")");
        return salva;
    }

    /** RF13: extrato (kardex) do produto. */
    @Transactional(readOnly = true)
    public List<Movimentacao> listarExtrato(Long produtoId) {
        return movimentacaoRepository.buscarExtrato(carregarProduto(produtoId));
    }

    @Transactional(readOnly = true)
    public List<Movimentacao> listarRecentes() {
        return movimentacaoRepository.buscarRecentes(PageRequest.of(0, LIMITE_MOVIMENTACOES_RECENTES));
    }

    @Transactional(readOnly = true)
    public List<Movimentacao> listarPorCompetencia(LocalDate inicio, LocalDate fim) {
        return movimentacaoRepository.buscarPorCompetencia(inicio, fim);
    }

    /**
     * RN004: CMP = (saldo anterior x custo anterior + quantidade recebida x custo da compra)
     * / (saldo anterior + quantidade recebida).
     */
    BigDecimal calcularCustoMedioPonderado(BigDecimal saldoAnterior, BigDecimal custoAnterior,
                                           BigDecimal quantidade, BigDecimal custoCompra) {
        BigDecimal custoAtual = custoAnterior == null ? BigDecimal.ZERO : custoAnterior;
        BigDecimal custoRecebido = custoCompra == null ? BigDecimal.ZERO : custoCompra;
        BigDecimal saldoBase = saldoAnterior.max(BigDecimal.ZERO);
        BigDecimal denominador = saldoBase.add(quantidade);
        if (denominador.signum() <= 0) {
            return custoRecebido.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
        }
        return saldoBase.multiply(custoAtual)
                .add(quantidade.multiply(custoRecebido))
                .divide(denominador, ESCALA_CUSTO, RoundingMode.HALF_UP);
    }

    /**
     * Desfaz o efeito de uma entrada sobre o custo medio (usado no estorno).
     * Sem saldo remanescente, o custo anterior deixa de ser apuravel e e mantido.
     */
    BigDecimal reverterCustoMedioPonderado(BigDecimal saldoAtual, BigDecimal custoAtual,
                                           BigDecimal quantidade, BigDecimal custoEntrada) {
        BigDecimal custo = custoAtual == null ? BigDecimal.ZERO : custoAtual;
        BigDecimal custoLancado = custoEntrada == null ? BigDecimal.ZERO : custoEntrada;
        BigDecimal denominador = saldoAtual.subtract(quantidade);
        if (denominador.signum() <= 0) {
            return custo;
        }
        BigDecimal valorRemanescente = saldoAtual.multiply(custo)
                .subtract(quantidade.multiply(custoLancado));
        if (valorRemanescente.signum() < 0) {
            return custo;
        }
        return valorRemanescente.divide(denominador, ESCALA_CUSTO, RoundingMode.HALF_UP);
    }

    private Movimentacao novaMovimentacao(Produto produto, TipoMovimentacao tipo, MotivoMovimentacao motivo,
                                          BigDecimal quantidade, BigDecimal custoUnitario,
                                          BigDecimal saldoAnterior, BigDecimal saldoResultante,
                                          String documento, String justificativa, LocalDate competencia,
                                          String emailUsuario) {
        Usuario usuario = usuarioService.buscarPorEmail(emailUsuario);
        Movimentacao movimentacao = new Movimentacao();
        movimentacao.setProduto(produto);
        movimentacao.setTipo(tipo);
        movimentacao.setMotivo(motivo);
        movimentacao.setQuantidade(normalizarQuantidade(quantidade));
        movimentacao.setCustoUnitario(custoUnitario == null ? BigDecimal.ZERO
                : custoUnitario.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP));
        movimentacao.setSaldoAnterior(normalizarQuantidade(saldoAnterior));
        movimentacao.setSaldoResultante(normalizarQuantidade(saldoResultante));
        movimentacao.setDocumento(documento);
        movimentacao.setJustificativa(justificativa);
        movimentacao.setCompetencia(competencia == null ? LocalDate.now() : competencia);
        movimentacao.setUsuario(usuario);
        return movimentacao;
    }

    private Produto carregarProduto(Long produtoId) {
        return produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Produto nao encontrado."));
    }

    private void validarQuantidade(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new RegraNegocioException("Informe uma quantidade maior que zero.");
        }
    }

    /** RN006 e RN008: justificativa textual obrigatoria com no minimo dez caracteres. */
    private void validarJustificativa(String justificativa) {
        if (justificativa == null || justificativa.trim().length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe uma justificativa com no minimo "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres.");
        }
    }

    private BigDecimal normalizarQuantidade(BigDecimal valor) {
        return valor.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
    }
}
