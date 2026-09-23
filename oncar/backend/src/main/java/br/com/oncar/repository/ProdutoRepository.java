package br.com.oncar.repository;

import br.com.oncar.domain.enums.CategoriaProduto;
import br.com.oncar.domain.model.Produto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findBySkuNormalizado(String skuNormalizado);

    @Query("select p from Produto p left join fetch p.fornecedorPadrao where p.id = :id")
    Optional<Produto> buscarComFornecedor(@Param("id") Long id);

    List<Produto> findAllByOrderByDescricaoAsc();

    List<Produto> findByAtivoTrueOrderByDescricaoAsc();

    @Query("""
            select p from Produto p
            left join fetch p.fornecedorPadrao
            where p.ativo = true and p.saldo <= p.estoqueMinimo
            order by p.descricao
            """)
    List<Produto> findAbaixoDoMinimo();

    @Query("""
            select p from Produto p
            left join p.fornecedorPadrao f
            where (:termo is null
                   or lower(p.descricao) like lower(concat('%', :termo, '%'))
                   or lower(p.sku) like lower(concat('%', :termo, '%'))
                   or lower(coalesce(p.marca, '')) like lower(concat('%', :termo, '%'))
                   or lower(coalesce(f.razaoSocial, '')) like lower(concat('%', :termo, '%')))
              and (:categoria is null or p.categoria = :categoria)
              and (:fornecedorId is null or f.id = :fornecedorId)
              and (:somenteAtivos = false or p.ativo = true)
            order by p.descricao
            """)
    List<Produto> pesquisar(@Param("termo") String termo,
                            @Param("categoria") CategoriaProduto categoria,
                            @Param("fornecedorId") Long fornecedorId,
                            @Param("somenteAtivos") boolean somenteAtivos);

    @Query("select coalesce(sum(p.saldo * p.precoCusto), 0) from Produto p where p.ativo = true")
    java.math.BigDecimal calcularValorTotalEmEstoque();
}
