package br.com.oncar.web.api;

import br.com.oncar.service.UsuarioService;
import br.com.oncar.web.api.dto.RequisicaoDtos;
import br.com.oncar.web.api.dto.RespostaDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessao")
public class SessaoApiController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioService usuarioService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SessaoApiController(AuthenticationManager authenticationManager, UsuarioService usuarioService) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<?> entrar(@Valid @RequestBody RequisicaoDtos.Login credenciais,
                                    HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication autenticacao = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(credenciais.email(), credenciais.senha()));

            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacao);
            SecurityContextHolder.setContext(contexto);
            request.getSession(true);
            contextRepository.saveContext(contexto, request, response);

            usuarioService.registrarLoginComSucesso(autenticacao.getName());
            return ResponseEntity.ok(RespostaDtos.UsuarioSessao.de(
                    usuarioService.buscarPorEmail(autenticacao.getName())));
        } catch (LockedException excecao) {
            return ResponseEntity.status(423).body(RespostaDtos.Erro.de(
                    "Conta bloqueada por 15 minutos apos cinco tentativas malsucedidas. "
                            + "Aguarde para tentar de novo."));
        } catch (AuthenticationException excecao) {
            usuarioService.registrarFalhaDeLogin(credenciais.email());
            return ResponseEntity.status(401).body(RespostaDtos.Erro.de(
                    "E-mail ou senha invalidos. Confira os dados e tente novamente."));
        }
    }

    @GetMapping
    public RespostaDtos.UsuarioSessao usuarioAutenticado(Principal principal) {
        return RespostaDtos.UsuarioSessao.de(usuarioService.buscarPorEmail(principal.getName()));
    }

    @DeleteMapping
    public ResponseEntity<Void> sair(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
