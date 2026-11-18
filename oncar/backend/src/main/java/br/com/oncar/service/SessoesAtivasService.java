package br.com.oncar.service;

import java.util.Objects;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Encerra sessoes ja abertas quando o acesso de um usuario muda. O Spring Security le
 * perfil e situacao do usuario apenas no login; sem isto, quem foi inativado ou
 * rebaixado continuaria usando a permissao antiga enquanto mantivesse a aba aberta.
 *
 * A sessao marcada como encerrada e derrubada pelo ConcurrentSessionFilter na proxima
 * requisicao: as telas vao para /login?encerrada e a API responde 401.
 */
@Service
public class SessoesAtivasService {

    private final SessionRegistry sessionRegistry;

    public SessoesAtivasService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /** Inclui no registro uma sessao autenticada fora do formLogin (login pela API). */
    public void registrar(String idDaSessao, Object principal) {
        sessionRegistry.registerNewSession(idDaSessao, principal);
    }

    /** Encerra todas as sessoes do usuario. */
    public void encerrarSessoesDe(String email) {
        encerrar(email, null);
    }

    /** Encerra as sessoes do usuario, exceto a informada (a de quem fez a alteracao). */
    public void encerrarOutrasSessoesDe(String email, String idDaSessaoMantida) {
        encerrar(email, idDaSessaoMantida);
    }

    /**
     * Dentro de uma transacao, so encerra depois do commit: se a alteracao for desfeita,
     * ninguem e desconectado a toa, e quem entrar de novo ja encontra os dados gravados.
     */
    private void encerrar(String email, String idDaSessaoMantida) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    expirar(email, idDaSessaoMantida);
                }
            });
        } else {
            expirar(email, idDaSessaoMantida);
        }
    }

    private void expirar(String email, String idDaSessaoMantida) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> pertenceAo(principal, email))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .filter(sessao -> !Objects.equals(sessao.getSessionId(), idDaSessaoMantida))
                .forEach(SessionInformation::expireNow);
    }

    private static boolean pertenceAo(Object principal, String email) {
        String nome = principal instanceof UserDetails usuario ? usuario.getUsername() : String.valueOf(principal);
        return nome.equalsIgnoreCase(email.trim());
    }
}
