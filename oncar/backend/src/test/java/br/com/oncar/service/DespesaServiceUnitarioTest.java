package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaDespesa;
import br.com.oncar.domain.model.Despesa;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.DespesaRepository;
import br.com.oncar.service.DespesaService.DadosDespesa;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.service.relatorio.Relatorios.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DespesaServiceUnitarioTest {

    private static final String EMAIL = "dono@teste.com.br";
    private static final LocalDate HOJE = LocalDate.now();

    @Mock
    private DespesaRepository despesaRepository;
    @Mock
    private FornecedorService fornecedorService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private DespesaService despesaService;

    private final Usuario usuario = new Usuario();

    private static DadosDespesa dados(String descricao, String valor) {
        return new DadosDespesa(descricao, CategoriaDespesa.ENERGIA, valor == null ? null : new BigDecimal(valor),
                HOJE, null, null, null);
    }

    private static DadosDespesa comCategoria(CategoriaDespesa categoria) {
        return new DadosDespesa("Conta de energia", categoria, BigDecimal.TEN, HOJE, null, null, null);
    }

    private static DadosDespesa comCompetencia(LocalDate competencia) {
        return new DadosDespesa("Conta de energia", CategoriaDespesa.ENERGIA, BigDecimal.TEN, competencia,
                null, null, null);
    }

    private static DadosDespesa comTextos(String documento, String observacao) {
        return new DadosDespesa("Conta de energia", CategoriaDespesa.ENERGIA, BigDecimal.TEN, HOJE, null,
                documento, observacao);
    }

    private static DadosDespesa comFornecedor(Long fornecedorId) {
        return new DadosDespesa("Conta de energia", CategoriaDespesa.ENERGIA, BigDecimal.TEN, HOJE, fornecedorId,
                null, null);
    }

    private static Fornecedor fornecedor(long id, boolean ativo) {
        Fornecedor fornecedor = new Fornecedor();
        fornecedor.setId(id);
        fornecedor.setRazaoSocial("Distribuidora " + id);
        fornecedor.setAtivo(ativo);
        return fornecedor;
    }

    /** Stubs do caminho feliz do lancamento; lenient porque os testes de recusa param antes. */
    @BeforeEach
    void lancamentoGravaComId() {
        lenient().when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);
        lenient().when(despesaRepository.save(any(Despesa.class))).thenAnswer(chamada -> {
            Despesa despesa = chamada.getArgument(0);
            despesa.setId(30L);
            return despesa;
        });
    }

    private void lancamentoRecusado(DadosDespesa dados, String trechoDaMensagem) {
        assertThatThrownBy(() -> despesaService.lancar(dados, EMAIL))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining(trechoDaMensagem);
        verify(despesaRepository, never()).save(any());
    }

    @Nested
    @DisplayName("descricao")
    class Descricao {

        @ParameterizedTest(name = "\"{0}\"")
        @NullSource
        @ValueSource(strings = {"", "ab", "   ab   "})
        @DisplayName("menos de tres caracteres uteis e recusado")
        void curtaDemais(String descricao) {
            lancamentoRecusado(dados(descricao, "10"), "3 a 120 caracteres");
        }

        @Test
        @DisplayName("mais de 120 caracteres e recusado")
        void longaDemais() {
            lancamentoRecusado(dados("x".repeat(121), "10"), "3 a 120 caracteres");
        }

        @Test
        @DisplayName("exatamente 3 e 120 caracteres sao aceitos")
        void limitesAceitos() {
            assertThatCode(() -> despesaService.lancar(dados("abc", "10"), EMAIL)).doesNotThrowAnyException();
            assertThatCode(() -> despesaService.lancar(dados("x".repeat(120), "10"), EMAIL))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("categoria ausente e recusada")
    void categoriaAusente() {
        lancamentoRecusado(comCategoria(null), "categoria");
    }

    @Nested
    @DisplayName("valor")
    class Valor {

        @ParameterizedTest(name = "{0}")
        @NullSource
        @ValueSource(strings = {"0", "0.00", "-15.90"})
        @DisplayName("nulo, zero ou negativo e recusado")
        void valorNaoPositivo(String valor) {
            lancamentoRecusado(dados("Conta de energia", valor), "maior que zero");
        }

        @Test
        @DisplayName("mais de dois centavos e recusado")
        void tresCasasDecimais() {
            lancamentoRecusado(dados("Conta de energia", "10.001"), "dois centavos");
        }

        @Test
        @DisplayName("zeros a direita alem dos centavos nao contam como casas decimais")
        void zerosADireitaSaoAceitos() {
            Despesa despesa = despesaService.lancar(dados("Conta de energia", "10.5000"), EMAIL);

            assertThat(despesa.getValor()).isEqualTo(new BigDecimal("10.50"));
        }

        @Test
        @DisplayName("o menor valor aceito e um centavo")
        void umCentavo() {
            assertThat(despesaService.lancar(dados("Conta de energia", "0.01"), EMAIL).getValor())
                    .isEqualTo(new BigDecimal("0.01"));
        }
    }

    @Nested
    @DisplayName("competencia")
    class Competencia {

        @Test
        @DisplayName("ausente e recusada")
        void ausente() {
            lancamentoRecusado(comCompetencia(null), "data de competencia");
        }

        @Test
        @DisplayName("o primeiro dia do mes seguinte ja e mes futuro e e recusado")
        void mesFuturo() {
            lancamentoRecusado(comCompetencia(YearMonth.now().plusMonths(1).atDay(1)), "mes futuro");
        }

        @Test
        @DisplayName("o ultimo dia do mes corrente e aceito, mesmo que ainda nao tenha chegado")
        void ultimoDiaDoMesCorrente() {
            assertThatCode(() -> despesaService.lancar(comCompetencia(YearMonth.now().atEndOfMonth()), EMAIL))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("meses anteriores sao aceitos")
        void mesAnterior() {
            assertThatCode(() -> despesaService.lancar(comCompetencia(HOJE.minusYears(1)), EMAIL))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("documento e observacao")
    class Textos {

        @Test
        @DisplayName("documento acima de 60 caracteres e recusado")
        void documentoLongo() {
            lancamentoRecusado(comTextos("d".repeat(61), null), "documento");
        }

        @Test
        @DisplayName("observacao acima de 500 caracteres e recusada")
        void observacaoLonga() {
            lancamentoRecusado(comTextos(null, "o".repeat(501)), "observacao");
        }

        @Test
        @DisplayName("espacos nas pontas nao contam no limite e sao removidos ao gravar")
        void espacosNasPontas() {
            Despesa despesa = despesaService.lancar(
                    comTextos("  " + "d".repeat(60) + "  ", "  paga no caixa  "), EMAIL);

            assertThat(despesa.getDocumento()).hasSize(60);
            assertThat(despesa.getObservacao()).isEqualTo("paga no caixa");
        }

        @Test
        @DisplayName("documento e observacao em branco sao gravados como nulos")
        void emBrancoViraNulo() {
            Despesa despesa = despesaService.lancar(comTextos("   ", ""), EMAIL);

            assertThat(despesa.getDocumento()).isNull();
            assertThat(despesa.getObservacao()).isNull();
        }
    }

    @Nested
    @DisplayName("fornecedor vinculado")
    class FornecedorVinculado {

        @Test
        @DisplayName("fornecedor inativo nao entra em nova despesa")
        void inativoNoLancamento() {
            when(fornecedorService.buscarPorId(8L)).thenReturn(fornecedor(8L, false));

            lancamentoRecusado(comFornecedor(8L), "esta inativo");
        }

        @Test
        @DisplayName("fornecedor ativo e vinculado")
        void ativoNoLancamento() {
            Fornecedor ativo = fornecedor(8L, true);
            when(fornecedorService.buscarPorId(8L)).thenReturn(ativo);

            assertThat(despesaService.lancar(comFornecedor(8L), EMAIL).getFornecedor()).isSameAs(ativo);
        }

        @Test
        @DisplayName("a correcao mantem o fornecedor ja vinculado, mesmo inativo, sem consulta-lo de novo")
        void correcaoMantemFornecedorInativo() {
            Fornecedor inativo = fornecedor(8L, false);
            Despesa existente = new Despesa();
            existente.setId(30L);
            existente.setDescricao("Conta antiga");
            existente.setCategoria(CategoriaDespesa.ENERGIA);
            existente.setValor(BigDecimal.ONE);
            existente.setCompetencia(HOJE);
            existente.setFornecedor(inativo);
            when(despesaRepository.buscarCompleta(30L)).thenReturn(Optional.of(existente));

            Despesa corrigida = despesaService.atualizar(30L, comFornecedor(8L));

            assertThat(corrigida.getFornecedor()).isSameAs(inativo);
            assertThat(corrigida.getDescricao()).isEqualTo("Conta de energia");
            verify(fornecedorService, never()).buscarPorId(anyLong());
            verify(auditoriaService).registrar(eq(AcaoAuditoria.ALTERACAO), eq("Despesa"), eq(30L),
                    anyString());
        }
    }

    @Test
    @DisplayName("o lancamento apara a descricao, liga o usuario e audita")
    void lancaDespesa() {
        Despesa despesa = despesaService.lancar(dados("  Conta de energia  ", "120"), EMAIL);

        assertThat(despesa.getDescricao()).isEqualTo("Conta de energia");
        assertThat(despesa.getValor()).isEqualTo(new BigDecimal("120.00"));
        assertThat(despesa.getUsuario()).isSameAs(usuario);
        verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Despesa"), eq(30L), anyString());
    }

    @Test
    @DisplayName("excluir despesa inexistente e recusado")
    void exclusaoDeInexistente() {
        when(despesaRepository.buscarCompleta(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> despesaService.excluir(77L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(despesaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a listagem consulta do primeiro dia do mes inicial ao ultimo do mes final")
    void listagemUsaLimitesDoPeriodo() {
        despesaService.listarPorCompetencia(new Periodo(YearMonth.of(2026, 1), YearMonth.of(2026, 2)));

        verify(despesaRepository).buscarPorCompetencia(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));
    }
}
