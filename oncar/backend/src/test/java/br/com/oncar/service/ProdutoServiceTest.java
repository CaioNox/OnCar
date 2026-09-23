package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oncar.TesteIntegracao;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.MotivoMovimentacao;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

class ProdutoServiceTest extends TesteIntegracao {

    @Autowired
    private EstoqueService estoqueService;

    @BeforeEach
    void prepararCenario() {
        criarGerente();
    }

    @Test
    @DisplayName("RN001: o SKU e unico desconsiderando caixa e espacos nas extremidades")
    void skuDuplicadoEhRejeitado() {
        criarProduto("FL-100", new BigDecimal("2"));

        Produto duplicado = new Produto();
        duplicado.setSku("  fl-100 ");
        duplicado.setDescricao("Outro filtro");
        duplicado.setCategoria(CategoriaProduto.FILTRO);
        duplicado.setUnidadeMedida(UnidadeMedida.UNIDADE);
        duplicado.setPrecoVenda(new BigDecimal("50.00"));
        duplicado.setEstoqueMinimo(BigDecimal.ONE);

        assertThatThrownBy(() -> produtoService.cadastrar(duplicado, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ja esta em uso");
    }

    @Test
    @DisplayName("RN001: a unicidade do SKU alcanca tambem os produtos inativos")
    void skuDeProdutoInativoContinuaReservado() {
        Produto produto = criarProduto("FL-200", new BigDecimal("2"));
        produtoService.inativar(produto.getId());

        Produto novo = new Produto();
        novo.setSku("FL-200");
        novo.setDescricao("Filtro novo");
        novo.setCategoria(CategoriaProduto.FILTRO);
        novo.setUnidadeMedida(UnidadeMedida.UNIDADE);
        novo.setPrecoVenda(new BigDecimal("50.00"));
        novo.setEstoqueMinimo(BigDecimal.ONE);

        assertThatThrownBy(() -> produtoService.cadastrar(novo, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("RN003: o saldo inicial de um produto novo e sempre zero")
    void produtoNovoNasceComSaldoZero() {
        Produto produto = criarProduto("FL-300", new BigDecimal("2"));

        assertThat(produto.getSaldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("RN005: o produto entra na lista de reposicao ao atingir o estoque minimo")
    void produtoAbaixoDoMinimoEntraNaListaDeReposicao() {
        Produto produto = criarProduto("FL-400", new BigDecimal("5"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("10.00"),
                null, null, null, EMAIL_GERENTE);

        assertThat(produtoService.listarAbaixoDoMinimo()).isEmpty();

        estoqueService.registrarSaida(produto.getId(), new BigDecimal("5"),
                MotivoMovimentacao.VENDA_BALCAO, null, null, null, EMAIL_GERENTE);

        assertThat(produtoService.listarAbaixoDoMinimo())
                .extracting(Produto::getSku)
                .containsExactly("FL-400");
    }

    @Test
    @DisplayName("RN005: o alerta sai da lista assim que uma entrada eleva o saldo")
    void entradaRetiraProdutoDaListaDeReposicao() {
        Produto produto = criarProduto("FL-450", new BigDecimal("5"));
        assertThat(produtoService.listarAbaixoDoMinimo()).hasSize(1);

        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("10.00"),
                null, null, null, EMAIL_GERENTE);

        assertThat(produtoService.listarAbaixoDoMinimo()).isEmpty();
    }

    @Test
    @DisplayName("RN009: produto com movimentacao nao pode ser excluido, apenas inativado")
    void produtoComMovimentacaoNaoPodeSerExcluido() {
        Produto produto = criarProduto("FL-500", new BigDecimal("2"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("3"), new BigDecimal("10.00"),
                null, null, null, EMAIL_GERENTE);

        assertThatThrownBy(() -> produtoService.excluir(produto.getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Inative o cadastro");

        produtoService.inativar(produto.getId());
        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().isAtivo()).isFalse();
    }

    @Test
    @DisplayName("RN009: produto sem historico pode ser excluido")
    void produtoSemHistoricoPodeSerExcluido() {
        Produto produto = criarProduto("FL-600", new BigDecimal("2"));

        produtoService.excluir(produto.getId());

        assertThat(produtoRepository.findById(produto.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "gerente@teste.com.br", roles = "GERENTE_ESTOQUE")
    @DisplayName("RN010: preco abaixo do custo medio exige confirmacao explicita do gerente")
    void precoAbaixoDoCustoExigeConfirmacao() {
        Produto produto = criarProduto("FL-700", new BigDecimal("2"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("50.00"),
                null, null, null, EMAIL_GERENTE);

        Produto dados = dadosDe(produto, new BigDecimal("30.00"));

        assertThatThrownBy(() -> produtoService.atualizar(produto.getId(), dados, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("margem negativa");

        Produto salvo = produtoService.atualizar(produto.getId(), dados, true);
        assertThat(salvo.getPrecoVenda()).isEqualByComparingTo("30.00");
    }

    @Test
    @WithMockUser(username = "atendente@teste.com.br", roles = "ATENDENTE")
    @DisplayName("RN010: o atendente nao pode gravar preco de venda abaixo do custo medio")
    void atendenteNaoConfirmaMargemNegativa() {
        Produto produto = criarProduto("FL-800", new BigDecimal("2"));
        estoqueService.registrarEntrada(produto.getId(), new BigDecimal("10"), new BigDecimal("50.00"),
                null, null, null, EMAIL_GERENTE);

        Produto dados = dadosDe(produto, new BigDecimal("30.00"));

        assertThatThrownBy(() -> produtoService.atualizar(produto.getId(), dados, true))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Procure o gerente de estoque");
    }

    @Test
    @DisplayName("RF07: a pesquisa localiza o produto por descricao, SKU ou marca")
    void pesquisaLocalizaProduto() {
        criarProduto("FL-900", new BigDecimal("2"));

        assertThat(produtoService.pesquisar("FL-900", null, null, true)).hasSize(1);
        assertThat(produtoService.pesquisar("filtro", null, null, true)).hasSize(1);
        assertThat(produtoService.pesquisar(null, CategoriaProduto.FILTRO, null, true)).hasSize(1);
        assertThat(produtoService.pesquisar("inexistente", null, null, true)).isEmpty();
    }

    private Produto dadosDe(Produto produto, BigDecimal precoVenda) {
        Produto dados = new Produto();
        dados.setSku(produto.getSku());
        dados.setDescricao(produto.getDescricao());
        dados.setCategoria(produto.getCategoria());
        dados.setUnidadeMedida(produto.getUnidadeMedida());
        dados.setPrecoVenda(precoVenda);
        dados.setEstoqueMinimo(produto.getEstoqueMinimo());
        return dados;
    }
}
