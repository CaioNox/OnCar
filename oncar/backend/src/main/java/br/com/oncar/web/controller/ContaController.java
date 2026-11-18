package br.com.oncar.web.controller;

import br.com.oncar.service.SessoesAtivasService;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.web.form.AlterarSenhaForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/conta")
public class ContaController {

    private final UsuarioService usuarioService;
    private final SessoesAtivasService sessoesAtivas;

    public ContaController(UsuarioService usuarioService, SessoesAtivasService sessoesAtivas) {
        this.usuarioService = usuarioService;
        this.sessoesAtivas = sessoesAtivas;
    }

    @GetMapping("/senha")
    public String formulario(Model model) {
        model.addAttribute("alterarSenhaForm", new AlterarSenhaForm());
        return "conta/senha";
    }

    @PostMapping("/senha")
    public String alterar(@Valid @ModelAttribute("alterarSenhaForm") AlterarSenhaForm form,
                          BindingResult resultado, Principal principal, HttpServletRequest request,
                          RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            return "conta/senha";
        }
        usuarioService.alterarSenha(principal.getName(), form.getSenhaAtual(),
                form.getNovaSenha(), form.getConfirmacao());
        // a senha antiga pode ter vazado: derruba os outros navegadores e mantem este
        sessoesAtivas.encerrarOutrasSessoesDe(principal.getName(), request.getSession().getId());
        atributos.addFlashAttribute("sucesso",
                "Senha alterada com sucesso. As sessoes abertas em outros dispositivos foram encerradas.");
        return "redirect:/";
    }
}
