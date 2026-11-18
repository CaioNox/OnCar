package br.com.oncar.web.api;

import br.com.oncar.service.SessoesAtivasService;
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
    private final SessoesAtivasService sessoesAtivas;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SessaoApiController(AuthenticationManager authenticationManager, UsuarioService usuarioService,
                               SessoesAtivasService sessoesAtivas) {
        this.authenticationManager = authenticationManager;
        this.usuarioService = usuarioService;
        this.sessoesAtivas = sessoesAtivas;
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
            // Protecao contra fixacao de sessao: o formLogin troca o ID sozinho, mas este
            // login manual nao. Sem isto, um JSESSIONID plantado no navegador da vitima
            // antes do login continuaria valido depois dele, ja autenticado. Descartar a
            // sessao anterior (em vez de so trocar o ID) tambem evita herdar atributos de
            // outro usuario que tenha entrado no mesmo navegador sem sair.
            HttpSession anterior = request.getSession(false);
            if (anterior != null) {
                anterior.invalidate();
            }
            HttpSession sessao = request.getSession(true);
            contextRepository.saveContext(contexto, request, response);
            // este login nao passa pelo formLogin: registra a sessao para que ela possa ser
            // encerrada quando o acesso do usuario mudar (ver SessoesAtivasService)
            sessoesAtivas.registrar(sessao.getId(), autenticacao.getPrincipal());

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
