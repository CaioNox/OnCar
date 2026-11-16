package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DespesaService {

    private static final int TAMANHO_MINIMO_DESCRICAO = 3;
    private static final int TAMANHO_MAXIMO_DESCRICAO = 120;
    private static final int TAMANHO_MAXIMO_DOCUMENTO = 60;
    private static final int TAMANHO_MAXIMO_OBSERVACAO = 500;
    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");

    private final DespesaRepository despesaRepository;
    private final FornecedorService fornecedorService;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public DespesaService(DespesaRepository despesaRepository, FornecedorService fornecedorService,
                          UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.despesaRepository = despesaRepository;
        this.fornecedorService = fornecedorService;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /** Dados informados pelo usuario ao lancar ou corrigir uma despesa. */
    public record DadosDespesa(String descricao, CategoriaDespesa categoria, BigDecimal valor,
                               LocalDate competencia, Long fornecedorId, String documento,
                               String observacao) {
    }

    @Transactional(readOnly = true)
    public List<Despesa> listarPorCompetencia(Periodo periodo) {
        return despesaRepository.buscarPorCompetencia(periodo.primeiroDia(), periodo.ultimoDia());
    }

    @Transactional(readOnly = true)
    public Despesa buscarPorId(Long id) {
        return despesaRepository.buscarCompleta(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Despesa nao encontrada."));
    }

    @Transactional
    public Despesa lancar(DadosDespesa dados, String emailUsuario) {
        validar(dados);
        Despesa despesa = new Despesa();
        preencher(despesa, dados, null);
        despesa.setUsuario(usuarioService.buscarPorEmail(emailUsuario));
        Despesa salva = despesaRepository.save(despesa);
        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Despesa", salva.getId(),
                "Despesa " + resumo(salva) + " lancada");
        return salva;
    }

    @Transactional
    public Despesa atualizar(Long id, DadosDespesa dados) {
        Despesa despesa = buscarPorId(id);
        validar(dados);
        String anterior = resumo(despesa);
        preencher(despesa, dados, despesa.getFornecedor());
        Despesa salva = despesaRepository.save(despesa);
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Despesa", salva.getId(),
                "Despesa " + anterior + " alterada para " + resumo(salva));
        return salva;
    }

    @Transactional
    public void excluir(Long id) {
        Despesa despesa = buscarPorId(id);
        despesaRepository.delete(despesa);
        auditoriaService.registrar(AcaoAuditoria.EXCLUSAO, "Despesa", id,
                "Despesa " + resumo(despesa) + " excluida");
    }

    private void validar(DadosDespesa dados) {
        String descricao = dados.descricao() == null ? "" : dados.descricao().trim();
        if (descricao.length() < TAMANHO_MINIMO_DESCRICAO || descricao.length() > TAMANHO_MAXIMO_DESCRICAO) {
            throw new RegraNegocioException("Descreva a despesa com " + TAMANHO_MINIMO_DESCRICAO + " a "
                    + TAMANHO_MAXIMO_DESCRICAO + " caracteres. Ex.: Conta de energia de outubro.");
        }
        if (dados.categoria() == null) {
            throw new RegraNegocioException("Selecione a categoria da despesa.");
        }
        if (dados.valor() == null || dados.valor().signum() <= 0) {
            throw new RegraNegocioException("Informe um valor maior que zero para a despesa.");
        }
        if (dados.valor().stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Informe o valor da despesa com no maximo dois centavos.");
        }
        if (dados.competencia() == null) {
            throw new RegraNegocioException("Informe a data de competencia da despesa.");
        }
        // a despesa pertence ao mes em que o gasto ocorreu; meses que ainda nao comecaram
        // ficariam fora de qualquer apuracao fechada
        if (YearMonth.from(dados.competencia()).isAfter(YearMonth.now())) {
            throw new RegraNegocioException("A competencia nao pode ser de um mes futuro. "
                    + "Lance a despesa quando o mes de referencia comecar.");
        }
        if (tamanho(dados.documento()) > TAMANHO_MAXIMO_DOCUMENTO) {
            throw new RegraNegocioException("O documento deve conter no maximo "
                    + TAMANHO_MAXIMO_DOCUMENTO + " caracteres.");
        }
        if (tamanho(dados.observacao()) > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraNegocioException("A observacao deve conter no maximo "
                    + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
    }

    private void preencher(Despesa despesa, DadosDespesa dados, Fornecedor fornecedorAtual) {
        despesa.setDescricao(dados.descricao().trim());
        despesa.setCategoria(dados.categoria());
        despesa.setValor(dados.valor().setScale(2, RoundingMode.HALF_UP));
        despesa.setCompetencia(dados.competencia());
        despesa.setDocumento(textoOuNulo(dados.documento()));
        despesa.setObservacao(textoOuNulo(dados.observacao()));
        despesa.setFornecedor(fornecedorDe(dados.fornecedorId(), fornecedorAtual));
    }

    /**
     * Fornecedores inativos nao sao oferecidos em novos lancamentos, mas a correcao de
     * uma despesa antiga pode manter o fornecedor que ja estava vinculado a ela.
     */
    private Fornecedor fornecedorDe(Long fornecedorId, Fornecedor fornecedorAtual) {
        if (fornecedorId == null) {
            return null;
        }
        if (fornecedorAtual != null && fornecedorId.equals(fornecedorAtual.getId())) {
            return fornecedorAtual;
        }
        Fornecedor fornecedor = fornecedorService.buscarPorId(fornecedorId);
        if (!fornecedor.isAtivo()) {
            throw new RegraNegocioException("O fornecedor " + fornecedor.getRazaoSocial()
                    + " esta inativo. Reative o cadastro ou escolha outro fornecedor.");
        }
        return fornecedor;
    }

    private String resumo(Despesa despesa) {
        return "\"" + despesa.getDescricao() + "\" (" + despesa.getCategoria().getDescricao() + ", "
                + NumberFormat.getCurrencyInstance(PORTUGUES).format(despesa.getValor()) + ", competencia "
                + despesa.getMesCompetencia() + ")";
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static int tamanho(String valor) {
        return valor == null ? 0 : valor.trim().length();
    }
}
