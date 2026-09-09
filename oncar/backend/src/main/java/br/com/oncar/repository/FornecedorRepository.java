package br.com.oncar.repository;

import br.com.oncar.domain.model.Fornecedor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    Optional<Fornecedor> findByCnpj(String cnpj);

    List<Fornecedor> findAllByOrderByRazaoSocialAsc();

    List<Fornecedor> findByAtivoTrueOrderByRazaoSocialAsc();
}
