package br.com.oncar.web.controller;

import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.web.form.UsuarioForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gestao de usuarios e perfis (RF02 e RF03), restrita ao Proprietario (RN007).
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listar());
        return "usuario/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("usuarioForm", new UsuarioForm());
        model.addAttribute("perfis", Perfil.values());
        return "usuario/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioService.buscarPorId(id);
        UsuarioForm form = new UsuarioForm();
        form.setId(usuario.getId());
        form.setNome(usuario.getNome());
        form.setEmail(usuario.getEmail());
        form.setPerfil(usuario.getPerfil());
        model.addAttribute("usuarioForm", form);
        model.addAttribute("perfis", Perfil.values());
        return "usuario/form";
    }

    @PostMapping("/salvar")
    public String salvar(@Valid @ModelAttribute("usuarioForm") UsuarioForm form, BindingResult resultado,
                         Model model, RedirectAttributes atributos) {
        if (resultado.hasErrors()) {
            model.addAttribute("perfis", Perfil.values());
            return "usuario/form";
        }
        Usuario usuario = new Usuario();
        usuario.setNome(form.getNome());
        usuario.setEmail(form.getEmail());
        usuario.setPerfil(form.getPerfil());
        if (form.isNovo()) {
            usuarioService.cadastrar(usuario, form.getSenha());
            atributos.addFlashAttribute("sucesso", "Usuario cadastrado com sucesso.");
        } else {
            usuarioService.atualizar(form.getId(), usuario);
            atributos.addFlashAttribute("sucesso", "Usuario atualizado com sucesso.");
        }
        return "redirect:/usuarios";
    }

    /** RF03: redefinicao da senha de um usuario pelo Proprietario. */
    @PostMapping("/{id}/redefinir-senha")
    public String redefinirSenha(@PathVariable Long id, @RequestParam String novaSenha,
                                 RedirectAttributes atributos) {
        usuarioService.redefinirSenha(id, novaSenha);
        atributos.addFlashAttribute("sucesso", "Senha redefinida. Oriente o usuario a troca-la no proximo acesso.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes atributos) {
        usuarioService.inativar(id);
        atributos.addFlashAttribute("sucesso", "Usuario inativado.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/reativar")
    public String reativar(@PathVariable Long id, RedirectAttributes atributos) {
        usuarioService.reativar(id);
        atributos.addFlashAttribute("sucesso", "Usuario reativado.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes atributos) {
        usuarioService.excluir(id);
        atributos.addFlashAttribute("sucesso", "Usuario excluido.");
        return "redirect:/usuarios";
    }
}
