package br.com.oncar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.enums.UnidadeMedida;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.estrutura.IndiceProdutos;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.ProdutoRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * O indice de SKU e a classe real (estrutura em memoria, sem I/O); repositorios e
 * auditoria sao simulados.
 */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceUnitarioTest {

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MovimentacaoRepository movimentacaoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private IndiceProdutos indice;
    private ProdutoService produtoService;

    @BeforeEach
    void montarServico() {
        indice = new IndiceProdutos();
        produtoService = new ProdutoService(produtoRepository, movimentacaoRepository, auditoriaService, indice);
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    private static Produto produto(Long id, String sku) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setSku(sku);
        produto.setDescricao("Pastilha de freio " + sku);
        produto.setCategoria(CategoriaProduto.PECA);
        produto.setUnidadeMedida(UnidadeMedida.PECA);
        produto.setPrecoVenda(new BigDecimal("80.00"));
        return produto;
    }

    private static void autenticadoComo(Perfil perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario@teste.com.br", null, List.of(new SimpleGrantedAuthority(perfil.getAuthority()))));
    }

    private void gravacaoAtribuiId(long id) {
        when(produtoRepository.save(any(Produto.class))).thenAnswer(chamada -> {
            Produto salvo = chamada.getArgument(0);
            if (salvo.getId() == null) {
                salvo.setId(id);
            }
            return salvo;
        });
    }

    @Nested
    @DisplayName("consulta")
    class Consulta {

        @Test
        @DisplayName("a pesquisa sem termo envia texto vazio, nunca nulo")
        void pesquisaSemTermo() {
            produtoService.pesquisar(null, null, null, true);

            verify(produtoRepository).pesquisar("", null, null, true);
        }

        @Test
        @DisplayName("a pesquisa remove os espacos das extremidades do termo")
        void pesquisaAparaTermo() {
            produtoService.pesquisar("  filtro  ", CategoriaProduto.FILTRO, 3L, false);

            verify(produtoRepository).pesquisar("filtro", CategoriaProduto.FILTRO, 3L, false);
        }

        @Test
        @DisplayName("produto inexistente pelo id e recusado")
        void buscarPorIdInexistente() {
            when(produtoRepository.buscarComFornecedor(7L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.buscarPorId(7L))
                    .isInstanceOf(RegistroNaoEncontradoException.class);
        }

        @Test
        @DisplayName("o valor em estoque sem produtos e zero, e nao nulo")
        void valorTotalSemProdutos() {
            when(produtoRepository.calcularValorTotalEmEstoque()).thenReturn(null);

            assertThat(produtoService.calcularValorTotalEmEstoque()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("busca pelo SKU com o indice em memoria")
    class BuscaPorSku {

        @Test
        @DisplayName("normaliza o SKU, consulta o banco na primeira vez e indexa o resultado")
        void primeiraBuscaIndexa() {
            Produto existente = produto(5L, "PF-10");
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.of(existente));

            assertThat(produtoService.buscarPorSku("  pf-10 ")).isSameAs(existente);
            assertThat(indice.buscarId("PF-10")).isEqualTo(5L);
        }

        @Test
        @DisplayName("com o SKU indexado, confere o id no banco sem nova busca por SKU")
        void usaIndice() {
            Produto existente = produto(5L, "PF-10");
            indice.indexar("PF-10", 5L);
            when(produtoRepository.findById(5L)).thenReturn(Optional.of(existente));

            assertThat(produtoService.buscarPorSku("PF-10")).isSameAs(existente);
            verify(produtoRepository, never()).findBySkuNormalizado(anyString());
        }

        @Test
        @DisplayName("indice defasado (o id agora tem outro SKU) e corrigido pelo banco")
        void indiceDefasado() {
            indice.indexar("PF-10", 5L);
            when(produtoRepository.findById(5L)).thenReturn(Optional.of(produto(5L, "OUTRO-SKU")));
            Produto correto = produto(8L, "PF-10");
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.of(correto));

            assertThat(produtoService.buscarPorSku("PF-10")).isSameAs(correto);
            assertThat(indice.buscarId("PF-10")).isEqualTo(8L);
        }

        @Test
        @DisplayName("SKU desconhecido e recusado com o codigo pesquisado na mensagem")
        void skuDesconhecido() {
            when(produtoRepository.findBySkuNormalizado("XX-1")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.buscarPorSku("xx-1"))
                    .isInstanceOf(RegistroNaoEncontradoException.class)
                    .hasMessageContaining("xx-1");
            assertThat(indice.tamanho()).isZero();
        }
    }

    @Nested
    @DisplayName("cadastro e alteracao")
    class Cadastro {

        @Test
        @DisplayName("produto novo comeca com saldo zero e custo zero, entra no indice e e auditado")
        void cadastraProduto() {
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());
            gravacaoAtribuiId(11L);
            Produto novo = produto(null, "pf-10");
            novo.setSaldo(new BigDecimal("99"));
            novo.setPrecoCusto(null);

            Produto salvo = produtoService.cadastrar(novo, false);

            assertThat(salvo.getSaldo()).isEqualByComparingTo("0");
            assertThat(salvo.getPrecoCusto()).isEqualByComparingTo("0");
            assertThat(indice.buscarId("PF-10")).isEqualTo(11L);
            verify(auditoriaService).registrar(eq(AcaoAuditoria.INCLUSAO), eq("Produto"), eq(11L), anyString());
        }

        @Test
        @DisplayName("SKU em uso por produto inativo e recusado avisando que ele esta inativo")
        void skuDeProdutoInativo() {
            Produto existente = produto(3L, "PF-10");
            existente.setAtivo(false);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> produtoService.cadastrar(produto(null, "PF-10"), false))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("ja esta em uso")
                    .hasMessageEndingWith("atualmente inativo.");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("a alteracao pode manter o proprio SKU")
        void alteracaoMantemProprioSku() {
            Produto atual = produto(3L, "PF-10");
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(atual));
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.of(atual));
            gravacaoAtribuiId(3L);

            assertThatCode(() -> produtoService.atualizar(3L, produto(null, "pf-10"), false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("trocar o SKU tira o codigo antigo do indice e indexa o novo")
        void trocaDeSkuReindexa() {
            Produto atual = produto(3L, "PF-10");
            indice.indexar("PF-10", 3L);
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(atual));
            when(produtoRepository.findBySkuNormalizado("PF-20")).thenReturn(Optional.empty());
            gravacaoAtribuiId(3L);
            Produto dados = produto(null, "PF-20");
            dados.setDescricao("Descricao nova");
            dados.setEstoqueMinimo(new BigDecimal("4"));

            Produto salvo = produtoService.atualizar(3L, dados, false);

            assertThat(salvo.getDescricao()).isEqualTo("Descricao nova");
            assertThat(salvo.getEstoqueMinimo()).isEqualByComparingTo("4");
            assertThat(indice.buscarId("PF-10")).isNull();
            assertThat(indice.buscarId("PF-20")).isEqualTo(3L);
        }

        @Test
        @DisplayName("a alteracao para um SKU de outro produto e recusada")
        void alteracaoParaSkuDeOutro() {
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(produto(3L, "PF-10")));
            when(produtoRepository.findBySkuNormalizado("PF-20")).thenReturn(Optional.of(produto(4L, "PF-20")));

            assertThatThrownBy(() -> produtoService.atualizar(3L, produto(null, "PF-20"), false))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("codigo 4");
        }
    }

    @Nested
    @DisplayName("margem negativa")
    class MargemNegativa {

        private Produto abaixoDoCusto() {
            Produto produto = produto(null, "PF-10");
            produto.setPrecoCusto(new BigDecimal("100"));
            produto.setPrecoVenda(new BigDecimal("90"));
            return produto;
        }

        @Test
        @DisplayName("o atendente nao grava preco abaixo do custo nem confirmando")
        void atendenteNaoConfirma() {
            autenticadoComo(Perfil.ATENDENTE);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.cadastrar(abaixoDoCusto(), true))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Procure o gerente de estoque");
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("o gerente precisa marcar a confirmacao")
        void gerenteSemConfirmacao() {
            autenticadoComo(Perfil.GERENTE_ESTOQUE);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.cadastrar(abaixoDoCusto(), false))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Marque a confirmacao");
        }

        @Test
        @DisplayName("o proprietario que confirma grava e a confirmacao fica na auditoria")
        void proprietarioConfirma() {
            autenticadoComo(Perfil.PROPRIETARIO);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());
            gravacaoAtribuiId(12L);

            produtoService.cadastrar(abaixoDoCusto(), true);

            verify(auditoriaService).registrar(eq(AcaoAuditoria.MARGEM_NEGATIVA), eq("Produto"), any(),
                    anyString());
            verify(produtoRepository).save(any(Produto.class));
        }

        @Test
        @DisplayName("sem usuario autenticado (rotina interna) a confirmacao basta")
        void semAutenticacaoComConfirmacao() {
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());
            gravacaoAtribuiId(12L);

            assertThatCode(() -> produtoService.cadastrar(abaixoDoCusto(), true)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("preco igual ao custo nao e margem negativa")
        void precoIgualAoCusto() {
            autenticadoComo(Perfil.ATENDENTE);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());
            gravacaoAtribuiId(12L);
            Produto produto = abaixoDoCusto();
            produto.setPrecoVenda(new BigDecimal("100.00"));

            assertThatCode(() -> produtoService.cadastrar(produto, false)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("produto sem custo apurado nao passa pela regra de margem")
        void semCustoNaoValidaMargem() {
            autenticadoComo(Perfil.ATENDENTE);
            when(produtoRepository.findBySkuNormalizado("PF-10")).thenReturn(Optional.empty());
            gravacaoAtribuiId(12L);
            Produto produto = abaixoDoCusto();
            produto.setPrecoCusto(BigDecimal.ZERO);

            assertThatCode(() -> produtoService.cadastrar(produto, false)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("situacao e exclusao")
    class Situacao {

        @Test
        @DisplayName("produto com movimentacao nao e excluido e continua no indice")
        void exclusaoComMovimentacao() {
            Produto existente = produto(3L, "PF-10");
            indice.indexar("PF-10", 3L);
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(existente));
            when(movimentacaoRepository.existsByProduto(existente)).thenReturn(true);

            assertThatThrownBy(() -> produtoService.excluir(3L))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessageContaining("Inative o cadastro");
            verify(produtoRepository, never()).delete(any());
            assertThat(indice.buscarId("PF-10")).isEqualTo(3L);
        }

        @Test
        @DisplayName("produto sem historico e excluido, sai do indice e e auditado")
        void exclusaoSemHistorico() {
            Produto existente = produto(3L, "PF-10");
            indice.indexar("PF-10", 3L);
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(existente));
            when(movimentacaoRepository.existsByProduto(existente)).thenReturn(false);

            produtoService.excluir(3L);

            verify(produtoRepository).delete(existente);
            assertThat(indice.buscarId("PF-10")).isNull();
            verify(auditoriaService).registrar(eq(AcaoAuditoria.EXCLUSAO), eq("Produto"), eq(3L), anyString());
        }

        @Test
        @DisplayName("inativar e reativar alternam a situacao e sao auditados")
        void inativaEReativa() {
            Produto existente = produto(3L, "PF-10");
            when(produtoRepository.buscarComFornecedor(3L)).thenReturn(Optional.of(existente));

            produtoService.inativar(3L);
            assertThat(existente.isAtivo()).isFalse();
            verify(auditoriaService).registrar(eq(AcaoAuditoria.INATIVACAO), eq("Produto"), eq(3L), anyString());

            produtoService.reativar(3L);
            assertThat(existente.isAtivo()).isTrue();
            verify(auditoriaService).registrar(eq(AcaoAuditoria.REATIVACAO), eq("Produto"), eq(3L), anyString());
        }
    }
}
