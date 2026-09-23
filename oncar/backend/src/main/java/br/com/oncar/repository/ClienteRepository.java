package br.com.oncar.repository;

import br.com.oncar.domain.model.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByDocumento(String documento);

    @Query("""
            select distinct c from Cliente c
            left join fetch c.veiculos
            order by c.nome
            """)
    List<Cliente> listarComVeiculos();

    List<Cliente> findByAtivoTrueOrderByNomeAsc();
}
