package br.com.oncar.web.api.dto;

import br.com.oncar.domain.model.LogAuditoria;
import br.com.oncar.domain.model.Cliente;
import br.com.oncar.domain.model.Fornecedor;
import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.model.Usuario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RespostaDtos {

    private RespostaDtos() {
    }

    public record UsuarioSessao(Long id, String nome, String email, String perfil, String perfilDescricao) {

        public static UsuarioSessao de(Usuario usuario) {
            return new UsuarioSessao(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                    usuario.getPerfil().name(), usuario.getPerfil().getDescricao());
        }
    }

    public record ProdutoResumo(Long id, String sku, String descricao, String categoria, String unidade,
                                String marca, BigDecimal saldo, BigDecimal estoqueMinimo, BigDecimal precoVenda,
                                BigDecimal precoCusto, boolean abaixoDoMinimo, boolean ativo,
                                String fornecedorPadrao) {

        public static ProdutoResumo de(Produto produto, boolean exibirCusto) {
            return new ProdutoResumo(
                    produto.getId(),
                    produto.getSku(),
                    produto.getDescricao(),
                    produto.getCategoria().getDescricao(),
                    produto.getUnidadeMedida().getSigla(),
                    produto.getMarca(),
                    produto.getSaldo(),
                    produto.getEstoqueMinimo(),
                    produto.getPrecoVenda(),
                    exibirCusto ? produto.getPrecoCusto() : null,
                    produto.isAbaixoDoMinimo(),
                    produto.isAtivo(),
                    produto.getFornecedorPadrao() == null ? null : produto.getFornecedorPadrao().getRazaoSocial());
        }
    }

    public record MovimentacaoResumo(Long id, Long produtoId, String produtoSku, String produtoDescricao,
                                     String tipo, String tipoDescricao, String motivo, BigDecimal quantidade,
                                     BigDecimal saldoResultante, String documento, String justificativa,
                                     String usuario, LocalDateTime dataHora, LocalDate competencia,
                                     boolean estornada, boolean estornavel) {

        public static MovimentacaoResumo de(Movimentacao movimentacao) {
            boolean ehEstorno = "ESTORNO".equals(movimentacao.getTipo().name());
            return new MovimentacaoResumo(
                    movimentacao.getId(),
                    movimentacao.getProduto().getId(),
                    movimentacao.getProduto().getSku(),
                    movimentacao.getProduto().getDescricao(),
                    movimentacao.getTipo().name(),
                    movimentacao.getTipo().getDescricao(),
                    movimentacao.getMotivo().getDescricao(),
                    movimentacao.getQuantidade(),
                    movimentacao.getSaldoResultante(),
                    movimentacao.getDocumento(),
                    movimentacao.getJustificativa(),
                    movimentacao.getUsuario().getNome(),
                    movimentacao.getDataHora(),
                    movimentacao.getCompetencia(),
                    movimentacao.isEstornada(),
                    !movimentacao.isEstornada() && !ehEstorno);
        }
    }

    public record Painel(int produtosAtivos, int itensAbaixoDoMinimo, BigDecimal valorTotalEmEstoque,
                         List<ProdutoResumo> listaDeReposicao, List<MovimentacaoResumo> ultimasMovimentacoes) {
    }

    public record FornecedorResumo(Long id, String razaoSocial, String cnpj, boolean ativo) {

        public static FornecedorResumo de(Fornecedor fornecedor) {
            return new FornecedorResumo(fornecedor.getId(), fornecedor.getRazaoSocial(),
                    fornecedor.getCnpj(), fornecedor.isAtivo());
        }
    }

    public record ClienteResumo(Long id, String nome, String documento, boolean ativo) {

        public static ClienteResumo de(Cliente cliente) {
            return new ClienteResumo(cliente.getId(), cliente.getNome(), cliente.getDocumento(),
                    cliente.isAtivo());
        }
    }

    public record RegistroAuditoria(Long id, String usuario, String acao, String entidade, String registroId,
                                    String detalhe, LocalDateTime dataHora) {

        public static RegistroAuditoria de(LogAuditoria log) {
            return new RegistroAuditoria(log.getId(), log.getUsuario(), log.getAcao().getDescricao(),
                    log.getEntidade(), log.getRegistroId(), log.getDetalhe(), log.getDataHora());
        }
    }

    public record Opcao(String valor, String descricao) {
    }

    public record Erro(String mensagem, List<String> detalhes) {

        public static Erro de(String mensagem) {
            return new Erro(mensagem, List.of());
        }
    }
}
