package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.LogAuditoria;
import br.com.oncar.repository.LogAuditoriaRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro do log de auditoria (RF20).
 *
 * <p>Grava em transacao propria para que o log de uma tentativa rejeitada
 * sobreviva ao rollback da operacao de negocio.</p>
 */
@Service
public class AuditoriaService {

    private static final String USUARIO_SISTEMA = "sistema";

    private final LogAuditoriaRepository logAuditoriaRepository;

    public AuditoriaService(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(AcaoAuditoria acao, String entidade, Object registroId, String detalhe) {
        registrarComoUsuario(usuarioAutenticado(), acao, entidade, registroId, detalhe);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarComoUsuario(String usuario, AcaoAuditoria acao, String entidade,
                                     Object registroId, String detalhe) {
        LogAuditoria log = new LogAuditoria(
                usuario,
                acao,
                entidade,
                registroId == null ? null : String.valueOf(registroId),
                detalhe);
        logAuditoriaRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<LogAuditoria> listarRecentes() {
        return logAuditoriaRepository.findTop200ByOrderByDataHoraDescIdDesc();
    }

    public String usuarioAutenticado() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || "anonymousUser".equals(autenticacao.getPrincipal())) {
            return USUARIO_SISTEMA;
        }
        return autenticacao.getName();
    }
}
