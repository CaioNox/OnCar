package br.com.oncar.repository;

import br.com.oncar.domain.model.Despesa;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    @Query("""
            select d from Despesa d
            join fetch d.usuario
            left join fetch d.fornecedor
            where d.competencia >= :inicio and d.competencia <= :fim
            order by d.competencia desc, d.id desc
            """)
    List<Despesa> buscarPorCompetencia(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select d from Despesa d
            join fetch d.usuario
            left join fetch d.fornecedor
            where d.id = :id
            """)
    Optional<Despesa> buscarCompleta(@Param("id") Long id);

    @Query("select count(d) > 0 from Despesa d where d.fornecedor.id = :fornecedorId")
    boolean existsByFornecedorId(@Param("fornecedorId") Long fornecedorId);

    @Query("select count(d) > 0 from Despesa d where d.usuario.id = :usuarioId")
    boolean existsByUsuarioId(@Param("usuarioId") Long usuarioId);
}
