package br.com.oncar.web.api;

import br.com.oncar.domain.enums.Perfil;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PermissoesApi {

    public boolean podeVerCustos() {
        return possuiAlgumPerfil(Perfil.PROPRIETARIO, Perfil.GERENTE_ESTOQUE);
    }

    /** Receita, despesas e resultado consolidados ficam com o proprietario. */
    public boolean podeVerFinanceiro() {
        return possuiAlgumPerfil(Perfil.PROPRIETARIO);
    }

    private boolean possuiAlgumPerfil(Perfil... perfis) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null) {
            return false;
        }
        Set<String> permitidos = Arrays.stream(perfis).map(Perfil::getAuthority).collect(Collectors.toSet());
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permitidos::contains);
    }
}
