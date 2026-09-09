package br.com.oncar.repository;

import br.com.oncar.domain.model.SenhaHistorico;
import br.com.oncar.domain.model.Usuario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenhaHistoricoRepository extends JpaRepository<SenhaHistorico, Long> {

    List<SenhaHistorico> findByUsuarioOrderByCriadoEmDesc(Usuario usuario);
}
