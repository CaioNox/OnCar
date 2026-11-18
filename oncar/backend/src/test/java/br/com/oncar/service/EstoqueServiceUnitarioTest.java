package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.TipoMovimentacao;
import br.com.oncar.domain.enums.UnidadeMedida;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Regras do estoque isoladas do banco: repositorios, usuarios e auditoria sao simulados.
 * Os testes de integracao (EstoqueServiceTest) cobrem o mesmo servico com o H2.
 */
@ExtendWith(MockitoExtension.class)
class EstoqueServiceUnitarioTest {

    private static final String EMAIL = "gerente@teste.com.br";
    private static final long PRODUTO_ID = 1L;

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private EstoqueService estoqueService;

    private Produto produto;
    private Usuario usuario;

    @BeforeEach
    void prepararProduto() {
        produto = new Produto();
        produto.setId(PRODUTO_ID);
        produto.setSku("FL-001");
        produto.setDescricao("Filtro de oleo");
        produto.setCategoria(CategoriaProduto.FILTRO);
        produto.setUnidadeMedida(UnidadeMedida.UNIDADE);
        produto.setPrecoVenda(new BigDecimal("50.00"));
        usuario = new Usuario();
        usuario.setId(9L);
        usuario.setEmail(EMAIL);
    }

    private void comSaldo(String saldo, String custoMedio) {
        produto.setSaldo(new BigDecimal(saldo));
        produto.setPrecoCusto(new BigDecimal(custoMedio));
    }

    private void produtoExiste() {
        when(produtoRepository.findById(PRODUTO_ID)).thenReturn(Optional.of(produto));
    }

    private void gravacaoDevolveOProprioLancamento() {
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);
        when(movimentacaoRepository.save(any(Movimentacao.class))).thenAnswer(chamada -> {
            Movimentacao movimentacao = chamada.getArgument(0);
            if (movimentacao.getId() == null) {
                movimentacao.setId(100L);
            }
            return movimentacao;
        });
    }

    @Nested
    @DisplayName("custo medio ponderado")
    class CustoMedioPonderado {

        @Test
        @DisplayName("pondera o custo atual pelo saldo e o da compra pela quantidade")
        void calculaMediaPonderada() {
            // (10 x 20 + 30 x 40) / 40 = 35
            BigDecimal custo = estoqueService.calcularCustoMedioPonderado(new BigDecimal("10"),
                    new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("40"));

            assertThat(custo).isEqualByComparingTo("35").hasScaleOf(4);
        }

        @Test
        @DisplayName("arredonda para quatro casas com HALF_UP")
        void arredondaEmQuatroCasas() {
            // (1 x 0 + 2 x 1) / 3 = 0,66666... -> 0,6667
            BigDecimal custo = estoqueService.calcularCustoMedioPonderado(BigDecimal.ONE, BigDecimal.ZERO,
                    new BigDecimal("2"), BigDecimal.ONE);

            assertThat(custo).isEqualTo(new BigDecimal("0.6667"));
        }

        @Test
        @DisplayName("saldo negativo nao entra na media: vale so o custo da compra")
        void saldoNegativoEhTratadoComoZero() {
            BigDecimal custo = estoqueService.calcularCustoMedioPonderado(new BigDecimal("-5"),
                    new BigDecimal("99"), new BigDecimal("4"), new BigDecimal("12.5"));

            assertThat(custo).isEqualByComparingTo("12.5");
        }

        @Test
        @DisplayName("custos nulos sao tratados como zero")
        void custosNulosValemZero() {
            assertThat(estoqueService.calcularCustoMedioPonderado(new BigDecimal("10"), null,
                    new BigDecimal("10"), new BigDecimal("30"))).isEqualByComparingTo("15");
            assertThat(estoqueService.calcularCustoMedioPonderado(new BigDecimal("10"), new BigDecimal("30"),
                    new BigDecimal("10"), null)).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("sem saldo nem quantidade nao divide por zero e devolve o custo da compra")
        void denominadorZeroDevolveCustoDaCompra() {
            BigDecimal custo = estoqueService.calcularCustoMedioPonderado(BigDecimal.ZERO, new BigDecimal("7"),
                    BigDecimal.ZERO, new BigDecimal("3.5"));

            assertThat(custo).isEqualTo(new BigDecimal("3.5000"));
        }

        @Test
        @DisplayName("a reversao desfaz exatamente uma entrada anterior")
        void reversaoDesfazEntrada() {
            BigDecimal depoisDaEntrada = estoqueService.calcularCustoMedioPonderado(new BigDecimal("10"),
                    new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("30"));

            BigDecimal revertido = estoqueService.reverterCustoMedioPonderado(new BigDecimal("20"),
                    depoisDaEntrada, new BigDecimal("10"), new BigDecimal("30"));

            assertThat(revertido).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("a reversao que zeraria o saldo mantem o custo atual")
        void reversaoSemSaldoRemanescenteMantemCusto() {
            BigDecimal custo = estoqueService.reverterCustoMedioPonderado(new BigDecimal("10"),
                    new BigDecimal("25"), new BigDecimal("10"), new BigDecimal("25"));

            assertThat(custo).isEqualByComparingTo("25");
        }

        @Test
        @DisplayName("a reversao que daria valor negativo mantem o custo atual")
        void reversaoComValorNegativoMantemCusto() {
            // 20 x 10 - 10 x 50 = -300
            BigDecimal custo = estoqueService.reverterCustoMedioPonderado(new BigDecimal("20"),
                    new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("50"));

            assertThat(custo).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("a reversao trata custos nulos como zero")
        void reversaoComCustosNulos() {
            assertThat(estoqueService.reverterCustoMedioPonderado(new BigDecimal("20"), null,
                    new BigDecimal("10"), new BigDecimal("5"))).isEqualByComparingTo("0");
            assertThat(estoqueService.reverterCustoMedioPonderado(new BigDecimal("20"), new BigDecimal("8"),
                    new BigDecimal("10"), null)).isEqualByComparingTo("16");
        }
    }

    @Nested
    @DisplayName("entrada de mercadoria")
    class Entrada {

        @Test
        @DisplayName("produto inexistente e recusado sem gravar nada")
        void produtoInexistente() {
            when(produtoRepository.findById(PRODUTO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estoqueService.registrarEntrada(PRODUTO_ID, BigDecimal.ONE, BigDecimal.ONE,
                    null, null, null, EMAIL))
                    .isInstanceOf(RegistroNaoEncontradoException.class)
                    .hasMessage("Produto nao encontrado.");
            verify(movimentacaoRepository, never()).save(any());
            verifyNoInteractions(auditoriaService);
        }

        @ParameterizedTest(name = "quantidade {0}")
        @NullSource
        @ValueSource(strings = {"0", "-1", "-0.001"})
        @DisplayName("quantidade nula, zero ou negativa e recusada")
        void quantidadeInvalida(String quantidade) {
            produtoExiste();
            BigDecimal valor = quantidade == null ? null : new BigDecimal(quantidade);

            assertThatThrownBy(() -> estoqueService.registrarEntrada(PRODUTO_ID, valor, BigDecimal.ONE,
                    null, null, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("quantidade maior que zero");
            verify(produtoRepository, never()).save(any());
        }

        @ParameterizedTest(name = "custo {0}")
        @NullSource
        @ValueSource(strings = {"-0.01"})
        @DisplayName("custo unitario ausente ou negativo e recusado")
        void custoInvalido(String custo) {
            produtoExiste();
            BigDecimal valor = custo == null ? null : new BigDecimal(custo);

            assertThatThrownBy(() -> estoqueService.registrarEntrada(PRODUTO_ID, BigDecimal.TEN, valor,
                    null, null, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("custo unitario");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("custo zero e aceito (bonificacao) e dilui o custo medio")
        void custoZeroEhAceito() {
            comSaldo("10", "20");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            estoqueService.registrarEntrada(PRODUTO_ID, new BigDecimal("10"), BigDecimal.ZERO,
                    null, null, null, EMAIL);

            assertThat(produto.getPrecoCusto()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("atualiza saldo e custo, grava o lancamento completo e audita")
        void registraEntrada() {
            comSaldo("4", "10");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();
            Fornecedor fornecedor = new Fornecedor();
            LocalDate competencia = LocalDate.of(2026, 3, 15);

            Movimentacao entrada = estoqueService.registrarEntrada(PRODUTO_ID, new BigDecimal("6"),
                    new BigDecimal("20"), fornecedor, "NF-e 10", competencia, EMAIL);

            // (4 x 10 + 6 x 20) / 10 = 16
            assertThat(produto.getSaldo()).isEqualTo(new BigDecimal("10.000"));
            assertThat(produto.getPrecoCusto()).isEqualByComparingTo("16");
            verify(produtoRepository).save(produto);

            assertThat(entrada.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
            assertThat(entrada.getMotivo()).isEqualTo(MotivoMovimentacao.COMPRA_FORNECEDOR);
            assertThat(entrada.getQuantidade()).isEqualTo(new BigDecimal("6.000"));
            assertThat(entrada.getCustoUnitario()).isEqualTo(new BigDecimal("20.0000"));
            assertThat(entrada.getSaldoAnterior()).isEqualTo(new BigDecimal("4.000"));
            assertThat(entrada.getSaldoResultante()).isEqualTo(new BigDecimal("10.000"));
            assertThat(entrada.getCustoMedioResultante()).isEqualByComparingTo("16");
            assertThat(entrada.getPrecoVendaUnitario()).as("entrada nao gera receita").isNull();
            assertThat(entrada.getFornecedor()).isSameAs(fornecedor);
            assertThat(entrada.getDocumento()).isEqualTo("NF-e 10");
            assertThat(entrada.getCompetencia()).isEqualTo(competencia);
            assertThat(entrada.getUsuario()).isSameAs(usuario);

            verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Movimentacao"), eq(100L),
                    anyString());
        }

        @Test
        @DisplayName("sem competencia informada vale a data de hoje")
        void competenciaPadraoEhHoje() {
            comSaldo("0", "0");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            Movimentacao entrada = estoqueService.registrarEntrada(PRODUTO_ID, BigDecimal.ONE, BigDecimal.ONE,
                    null, null, null, EMAIL);

            assertThat(entrada.getCompetencia()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    @DisplayName("saida de mercadoria")
    class Saida {

        @Test
        @DisplayName("motivo ausente e recusado")
        void motivoAusente() {
            produtoExiste();

            assertThatThrownBy(() -> estoqueService.registrarSaida(PRODUTO_ID, BigDecimal.ONE, null,
                    null, null, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("motivo da saida");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = MotivoMovimentacao.class,
                names = {"COMPRA_FORNECEDOR", "DEVOLUCAO_CLIENTE", "AJUSTE_INVENTARIO", "ESTORNO_MOVIMENTACAO"})
        @DisplayName("motivo que nao e de saida e recusado")
        void motivoQueNaoEhDeSaida(MotivoMovimentacao motivo) {
            comSaldo("10", "5");
            produtoExiste();

            assertThatThrownBy(() -> estoqueService.registrarSaida(PRODUTO_ID, BigDecimal.ONE, motivo,
                    null, null, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("motivo da saida");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("quantidade acima do saldo e recusada sem baixar nada")
        void saldoInsuficiente() {
            comSaldo("3", "5");
            produtoExiste();

            assertThatThrownBy(() -> estoqueService.registrarSaida(PRODUTO_ID, new BigDecimal("3.001"),
                    MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Saldo insuficiente para o produto FL-001")
                    .hasMessageContaining("Nenhum item foi baixado");
            assertThat(produto.getSaldo()).isEqualByComparingTo("3");
            verify(produtoRepository, never()).save(any());
            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("baixar exatamente o saldo disponivel e permitido e zera o estoque")
        void saidaDoSaldoInteiro() {
            comSaldo("3", "5");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            Movimentacao saida = estoqueService.registrarSaida(PRODUTO_ID, new BigDecimal("3"),
                    MotivoMovimentacao.PERDA_AVARIA, null, null, null, EMAIL);

            assertThat(produto.getSaldo()).isEqualTo(new BigDecimal("0.000"));
            assertThat(saida.getSaldoResultante()).isEqualTo(new BigDecimal("0.000"));
        }

        @Test
        @DisplayName("grava preco de venda e custo medio do momento e mantem o custo do produto")
        void registraSaida() {
            comSaldo("10", "12.5");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();
            Cliente cliente = new Cliente();

            Movimentacao saida = estoqueService.registrarSaida(PRODUTO_ID, new BigDecimal("4"),
                    MotivoMovimentacao.ORDEM_SERVICO, cliente, "OS 77", null, EMAIL);

            assertThat(produto.getSaldo()).isEqualByComparingTo("6");
            assertThat(produto.getPrecoCusto()).as("saida nao muda o custo medio").isEqualByComparingTo("12.5");
            assertThat(saida.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
            assertThat(saida.getMotivo()).isEqualTo(MotivoMovimentacao.ORDEM_SERVICO);
            assertThat(saida.getCustoUnitario()).isEqualByComparingTo("12.5");
            assertThat(saida.getPrecoVendaUnitario()).isEqualByComparingTo("50.00");
            assertThat(saida.getCliente()).isSameAs(cliente);
            verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Movimentacao"), eq(100L),
                    anyString());
        }
    }

    @Nested
    @DisplayName("ajuste de inventario")
    class Ajuste {

        @ParameterizedTest(name = "saldo apurado {0}")
        @NullSource
        @ValueSource(strings = {"-1"})
        @DisplayName("saldo apurado ausente ou negativo e recusado")
        void saldoApuradoInvalido(String saldo) {
            produtoExiste();
            BigDecimal valor = saldo == null ? null : new BigDecimal(saldo);

            assertThatThrownBy(() -> estoqueService.ajustarInventario(PRODUTO_ID, valor,
                    "Contagem fisica de marco", null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("saldo apurado");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullSource
        @ValueSource(strings = {"", "123456789", "   curta     "})
        @DisplayName("justificativa com menos de dez caracteres uteis e recusada")
        void justificativaCurta(String justificativa) {
            comSaldo("10", "5");
            produtoExiste();

            assertThatThrownBy(() -> estoqueService.ajustarInventario(PRODUTO_ID, BigDecimal.ONE,
                    justificativa, null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("no minimo 10 caracteres");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("justificativa com exatamente dez caracteres e aceita")
        void justificativaNoLimite() {
            comSaldo("10", "5");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            Movimentacao ajuste = estoqueService.ajustarInventario(PRODUTO_ID, new BigDecimal("9"),
                    "1234567890", null, EMAIL);

            assertThat(ajuste.getJustificativa()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("saldo apurado igual ao atual (em qualquer escala) nao gera ajuste")
        void saldoIgualNaoGeraAjuste() {
            comSaldo("10.000", "5");
            produtoExiste();

            assertThatThrownBy(() -> estoqueService.ajustarInventario(PRODUTO_ID, new BigDecimal("10"),
                    "Contagem fisica de marco", null, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Nao ha ajuste a registrar");
            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("ajuste para mais registra a diferenca positiva e audita como alteracao")
        void ajusteParaMais() {
            comSaldo("4", "5");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            Movimentacao ajuste = estoqueService.ajustarInventario(PRODUTO_ID, new BigDecimal("6.5"),
                    "Contagem fisica de marco", null, EMAIL);

            assertThat(produto.getSaldo()).isEqualTo(new BigDecimal("6.500"));
            assertThat(ajuste.getTipo()).isEqualTo(TipoMovimentacao.AJUSTE);
            assertThat(ajuste.getMotivo()).isEqualTo(MotivoMovimentacao.AJUSTE_INVENTARIO);
            assertThat(ajuste.getQuantidade()).isEqualTo(new BigDecimal("2.500"));
            assertThat(ajuste.getSaldoAnterior()).isEqualByComparingTo("4");
            assertThat(ajuste.getSaldoResultante()).isEqualByComparingTo("6.5");
            verify(auditoriaService).registrar(eq(AcaoAuditoria.ALTERACAO), eq("Movimentacao"), eq(100L),
                    anyString());
        }

        @Test
        @DisplayName("ajuste para zero e permitido e grava a quantidade sempre positiva")
        void ajusteParaZero() {
            comSaldo("4", "5");
            produtoExiste();
            gravacaoDevolveOProprioLancamento();

            Movimentacao ajuste = estoqueService.ajustarInventario(PRODUTO_ID, BigDecimal.ZERO,
                    "Pecas extraviadas no deposito", null, EMAIL);

            assertThat(ajuste.getQuantidade()).isEqualByComparingTo("4");
            assertThat(produto.getSaldo()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("estorno")
    class Estorno {

        private static final String JUSTIFICATIVA = "Lancamento feito em duplicidade";

        private Movimentacao lancamento(TipoMovimentacao tipo, String quantidade, String custo) {
            Movimentacao movimentacao = new Movimentacao();
            movimentacao.setId(50L);
            movimentacao.setProduto(produto);
            movimentacao.setTipo(tipo);
            movimentacao.setQuantidade(new BigDecimal(quantidade));
            movimentacao.setCustoUnitario(new BigDecimal(custo));
            movimentacao.setDocumento("DOC-1");
            movimentacao.setCompetencia(LocalDate.of(2026, 2, 10));
            when(movimentacaoRepository.findById(50L)).thenReturn(Optional.of(movimentacao));
            return movimentacao;
        }

        @Test
        @DisplayName("movimentacao inexistente e recusada")
        void movimentacaoInexistente() {
            when(movimentacaoRepository.findById(50L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL))
                    .isInstanceOf(RegistroNaoEncontradoException.class);
        }

        @Test
        @DisplayName("movimentacao ja estornada nao e estornada de novo")
        void jaEstornada() {
            lancamento(TipoMovimentacao.ENTRADA, "1", "1").setEstornada(true);

            assertThatThrownBy(() -> estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("ja foi estornada");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("um estorno nao pode ser estornado")
        void estornoDeEstorno() {
            lancamento(TipoMovimentacao.ESTORNO, "1", "1");

            assertThatThrownBy(() -> estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Um estorno nao pode ser estornado");
        }

        @Test
        @DisplayName("a justificativa e validada antes de qualquer alteracao")
        void justificativaCurta() {
            lancamento(TipoMovimentacao.ENTRADA, "1", "1");

            assertThatThrownBy(() -> estoqueService.estornar(50L, "curta", EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("justificativa");
            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("entrada cujo saldo ja foi consumido nao e estornada")
        void entradaComSaldoConsumido() {
            comSaldo("4", "20");
            lancamento(TipoMovimentacao.ENTRADA, "10", "20");

            assertThatThrownBy(() -> estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Registre um ajuste de inventario");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("estorno de entrada retira a quantidade e reverte o custo medio")
        void estornaEntrada() {
            // saldo 20 a 25: 10 a 20 + 10 a 30; estornar a segunda volta para 10 a 20
            comSaldo("20", "25");
            Movimentacao original = lancamento(TipoMovimentacao.ENTRADA, "10", "30");
            Fornecedor fornecedor = new Fornecedor();
            original.setFornecedor(fornecedor);
            gravacaoDevolveOProprioLancamento();

            Movimentacao estorno = estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL);

            assertThat(produto.getSaldo()).isEqualByComparingTo("10");
            assertThat(produto.getPrecoCusto()).isEqualByComparingTo("20");
            assertThat(original.isEstornada()).isTrue();
            assertThat(estorno.getTipo()).isEqualTo(TipoMovimentacao.ESTORNO);
            assertThat(estorno.getMotivo()).isEqualTo(MotivoMovimentacao.ESTORNO_MOVIMENTACAO);
            assertThat(estorno.getMovimentacaoOrigem()).isSameAs(original);
            assertThat(estorno.getQuantidade()).isEqualByComparingTo("10");
            assertThat(estorno.getSaldoAnterior()).isEqualByComparingTo("20");
            assertThat(estorno.getSaldoResultante()).isEqualByComparingTo("10");
            assertThat(estorno.getJustificativa()).isEqualTo(JUSTIFICATIVA);
            assertThat(estorno.getFornecedor()).isSameAs(fornecedor);
            assertThat(estorno.getDocumento()).isEqualTo("DOC-1");
            assertThat(estorno.getCompetencia()).as("o estorno herda a competencia do original")
                    .isEqualTo(LocalDate.of(2026, 2, 10));
            verify(movimentacaoRepository).save(original);
        }

        @Test
        @DisplayName("estorno de saida devolve a quantidade ao saldo pelo custo da saida")
        void estornaSaida() {
            comSaldo("6", "10");
            Movimentacao original = lancamento(TipoMovimentacao.SAIDA, "4", "10");
            Cliente cliente = new Cliente();
            original.setCliente(cliente);
            gravacaoDevolveOProprioLancamento();

            Movimentacao estorno = estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL);

            assertThat(produto.getSaldo()).isEqualByComparingTo("10");
            assertThat(produto.getPrecoCusto()).isEqualByComparingTo("10");
            assertThat(estorno.getCliente()).isSameAs(cliente);
            assertThat(estorno.getQuantidade()).isEqualByComparingTo("4");
        }

        @Test
        @DisplayName("estorno de ajuste restaura o saldo anterior ao ajuste")
        void estornaAjuste() {
            comSaldo("7", "10");
            Movimentacao original = lancamento(TipoMovimentacao.AJUSTE, "3", "10");
            original.setSaldoAnterior(new BigDecimal("10"));
            original.setSaldoResultante(new BigDecimal("7"));
            gravacaoDevolveOProprioLancamento();

            Movimentacao estorno = estoqueService.estornar(50L, JUSTIFICATIVA, EMAIL);

            assertThat(produto.getSaldo()).isEqualTo(new BigDecimal("10.000"));
            assertThat(estorno.getQuantidade()).isEqualByComparingTo("3");
            verify(auditoriaService).registrar(eq(AcaoAuditoria.ALTERACAO), eq("Movimentacao"), eq(100L),
                    anyString());
        }
    }

    @Test
    @DisplayName("as movimentacoes recentes sao limitadas as 50 ultimas")
    void recentesLimitadasA50() {
        when(movimentacaoRepository.buscarRecentes(any(Pageable.class))).thenReturn(List.of());

        estoqueService.listarRecentes();

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(movimentacaoRepository).buscarRecentes(pagina.capture());
        assertThat(pagina.getValue()).isEqualTo(PageRequest.of(0, 50));
    }

    @Test
    @DisplayName("o extrato de produto inexistente e recusado")
    void extratoDeProdutoInexistente() {
        when(produtoRepository.findById(PRODUTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listarExtrato(PRODUTO_ID))
                .isInstanceOf(RegistroNaoEncontradoException.class);
        verify(movimentacaoRepository, never()).buscarExtrato(any());
    }
}
