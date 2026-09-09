package br.com.oncar.repository;

import br.com.oncar.domain.model.LogAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    List<LogAuditoria> findTop200ByOrderByDataHoraDescIdDesc();
}
