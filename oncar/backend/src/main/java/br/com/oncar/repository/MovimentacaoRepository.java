package br.com.oncar.repository;

import br.com.oncar.domain.model.Movimentacao;
import br.com.oncar.domain.model.Produto;
import br.com.oncar.domain.enums.TipoMovimentacao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

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

    @Query("""
            select m from Movimentacao m
            join fetch m.produto
            join fetch m.usuario
            where (:inicio is null or m.competencia >= :inicio)
              and (:fim is null or m.competencia <= :fim)
            order by m.dataHora desc, m.id desc
            """)
    List<Movimentacao> buscarPorCompetencia(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Lancamentos validos de um tipo por competencia; estornados e estornos se anulam e ficam de fora. */
    @Query("""
            select m from Movimentacao m
            join fetch m.produto p
            left join fetch p.fornecedorPadrao
            join fetch m.usuario
            left join fetch m.fornecedor
            left join fetch m.cliente
            where m.tipo = :tipo
              and m.estornada = false
              and m.competencia >= :inicio and m.competencia <= :fim
            order by m.competencia, m.dataHora, m.id
            """)
    List<Movimentacao> buscarLancamentosPorCompetencia(@Param("tipo") TipoMovimentacao tipo,
                                                       @Param("inicio") LocalDate inicio,
                                                       @Param("fim") LocalDate fim);

    /** Tudo o que mexeu no saldo fisico dentro de um intervalo de datas de lancamento. */
    @Query("""
            select m from Movimentacao m
            where m.dataHora >= :inicio and m.dataHora < :fim
            order by m.dataHora, m.id
            """)
    List<Movimentacao> buscarPorDataDeLancamento(@Param("inicio") LocalDateTime inicio,
                                                 @Param("fim") LocalDateTime fim);

    /**
     * Ultimo lancamento de cada produto antes do limite: o saldo resultante dele e a
     * posicao do produto naquele instante. Empates no mesmo instante sao resolvidos pelo id.
     */
    @Query("""
            select m from Movimentacao m
            where m.dataHora < :limite
              and m.dataHora = (select max(m2.dataHora) from Movimentacao m2
                                where m2.produto = m.produto and m2.dataHora < :limite)
            """)
    List<Movimentacao> buscarUltimosAntesDe(@Param("limite") LocalDateTime limite);
}
