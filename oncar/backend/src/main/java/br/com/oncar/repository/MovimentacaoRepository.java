package br.com.oncar.repository;

import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * As consultas de listagem carregam produto e usuario junto com a movimentacao,
 * porque a aplicacao nao mantem a sessao do JPA aberta durante a renderizacao.
 */
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /** RF13: extrato (kardex) do produto. */
    @Query("""
            select m from Movimentacao m
            join fetch m.produto
            join fetch m.usuario
            where m.produto = :produto
            order by m.dataHora desc, m.id desc
            """)
    List<Movimentacao> buscarExtrato(@Param("produto") Produto produto);

    @Query("""
            select m from Movimentacao m
            join fetch m.produto
            join fetch m.usuario
            order by m.dataHora desc, m.id desc
            """)
    List<Movimentacao> buscarRecentes(Pageable paginacao);

    boolean existsByProduto(Produto produto);

    @Query("select count(m) > 0 from Movimentacao m where m.fornecedor.id = :fornecedorId")
    boolean existsByFornecedorId(@Param("fornecedorId") Long fornecedorId);

    @Query("select count(m) > 0 from Movimentacao m where m.cliente.id = :clienteId")
    boolean existsByClienteId(@Param("clienteId") Long clienteId);

    @Query("select count(m) > 0 from Movimentacao m where m.usuario.id = :usuarioId")
    boolean existsByUsuarioId(@Param("usuarioId") Long usuarioId);

    /** RN012: os relatorios consideram o mes de competencia do lancamento. */
    @Query("""
            select m from Movimentacao m
            join fetch m.produto
            join fetch m.usuario
            where (:inicio is null or m.competencia >= :inicio)
              and (:fim is null or m.competencia <= :fim)
            order by m.dataHora desc, m.id desc
            """)
    List<Movimentacao> buscarPorCompetencia(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
