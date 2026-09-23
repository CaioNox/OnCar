package br.com.oncar.web.api;

import br.com.oncar.domain.enums.Perfil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PermissoesApi {

    public boolean podeVerCustos() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) {
            return false;
        }
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(Perfil.PROPRIETARIO.getAuthority())
                        || authority.equals(Perfil.GERENTE_ESTOQUE.getAuthority()));
    }
}
