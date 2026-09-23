package br.com.oncar.service;

import br.com.oncar.domain.enums.AcaoAuditoria;
import br.com.oncar.domain.model.SenhaHistorico;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.MovimentacaoRepository;
import br.com.oncar.repository.SenhaHistoricoRepository;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Pattern SENHA_VALIDA = Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d).{8,}$");
    private static final int SENHAS_ANTERIORES_BLOQUEADAS = 3;
    private static final int MAXIMO_TENTATIVAS = 5;
    private static final int MINUTOS_BLOQUEIO = 15;

    private final UsuarioRepository usuarioRepository;
    private final SenhaHistoricoRepository senhaHistoricoRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          SenhaHistoricoRepository senhaHistoricoRepository,
                          MovimentacaoRepository movimentacaoRepository,
                          PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.senhaHistoricoRepository = senhaHistoricoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarioRepository.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuario nao encontrado."));
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuario nao encontrado."));
    }

    @Transactional
    public Usuario cadastrar(Usuario usuario, String senhaEmTexto) {
        if (usuarioRepository.existsByEmailIgnoreCase(usuario.getEmail())) {
            throw new RegraNegocioException(
                    "Ja existe um usuario cadastrado com o e-mail " + usuario.getEmail() + ".");
        }
        validarPolitica(senhaEmTexto);
        usuario.setSenha(passwordEncoder.encode(senhaEmTexto));
        usuario.setCriadoEm(LocalDateTime.now());
        Usuario salvo = usuarioRepository.save(usuario);
        senhaHistoricoRepository.save(new SenhaHistorico(salvo, salvo.getSenha()));
        auditoriaService.registrar(AcaoAuditoria.INCLUSAO, "Usuario", salvo.getId(),
                "Usuario " + salvo.getEmail() + " criado com perfil " + salvo.getPerfil());
        return salvo;
    }

    @Transactional
    public Usuario atualizar(Long id, Usuario dados) {
        Usuario usuario = buscarPorId(id);
        usuarioRepository.findByEmailIgnoreCase(dados.getEmail())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new RegraNegocioException(
                            "O e-mail " + dados.getEmail() + " ja pertence a outro usuario.");
                });
        usuario.setNome(dados.getNome());
        usuario.setEmail(dados.getEmail());
        usuario.setPerfil(dados.getPerfil());
        Usuario salvo = usuarioRepository.save(usuario);
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Usuario", salvo.getId(),
                "Dados do usuario " + salvo.getEmail() + " alterados");
        return salvo;
    }

    @Transactional
    public void inativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(AcaoAuditoria.INATIVACAO, "Usuario", id,
                "Usuario " + usuario.getEmail() + " inativado");
    }

    @Transactional
    public void reativar(Long id) {
        Usuario usuario = buscarPorId(id);
        usuario.setAtivo(true);
        usuario.setTentativasFalhas(0);
        usuario.setBloqueadoAte(null);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(AcaoAuditoria.REATIVACAO, "Usuario", id,
                "Usuario " + usuario.getEmail() + " reativado");
    }

    @Transactional
    public void excluir(Long id) {
        Usuario usuario = buscarPorId(id);
        if (movimentacaoRepository.existsByUsuarioId(id)) {
            throw new RegraNegocioException("O usuario " + usuario.getEmail()
                    + " possui movimentacoes registradas e nao pode ser excluido. Inative o cadastro.");
        }
        senhaHistoricoRepository.deleteAll(senhaHistoricoRepository.findByUsuarioOrderByCriadoEmDesc(usuario));
        usuarioRepository.delete(usuario);
        auditoriaService.registrar(AcaoAuditoria.EXCLUSAO, "Usuario", id,
                "Usuario " + usuario.getEmail() + " excluido");
    }

    @Transactional
    public void alterarSenha(String email, String senhaAtual, String novaSenha, String confirmacao) {
        Usuario usuario = buscarPorEmail(email);
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
            throw new RegraNegocioException("A senha atual informada esta incorreta.");
        }
        if (!novaSenha.equals(confirmacao)) {
            throw new RegraNegocioException("A confirmacao nao confere com a nova senha.");
        }
        validarPolitica(novaSenha);
        validarReuso(usuario, novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        senhaHistoricoRepository.save(new SenhaHistorico(usuario, usuario.getSenha()));
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Usuario", usuario.getId(),
                "Senha alterada pelo proprio usuario");
    }

    @Transactional
    public void redefinirSenha(Long id, String novaSenha) {
        Usuario usuario = buscarPorId(id);
        validarPolitica(novaSenha);
        validarReuso(usuario, novaSenha);
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTentativasFalhas(0);
        usuario.setBloqueadoAte(null);
        usuarioRepository.save(usuario);
        senhaHistoricoRepository.save(new SenhaHistorico(usuario, usuario.getSenha()));
        auditoriaService.registrar(AcaoAuditoria.ALTERACAO, "Usuario", id,
                "Senha do usuario " + usuario.getEmail() + " redefinida");
    }

    @Transactional
    public void registrarFalhaDeLogin(String email) {
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(usuario -> {
            usuario.setTentativasFalhas(usuario.getTentativasFalhas() + 1);
            if (usuario.getTentativasFalhas() >= MAXIMO_TENTATIVAS) {
                usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEIO));
                usuario.setTentativasFalhas(0);
                auditoriaService.registrarComoUsuario(usuario.getEmail(), AcaoAuditoria.BLOQUEIO_CONTA,
                        "Usuario", usuario.getId(),
                        "Conta bloqueada por " + MINUTOS_BLOQUEIO + " minutos apos "
                                + MAXIMO_TENTATIVAS + " tentativas malsucedidas");
            } else {
                auditoriaService.registrarComoUsuario(usuario.getEmail(), AcaoAuditoria.LOGIN_FALHA,
                        "Usuario", usuario.getId(),
                        "Tentativa de login malsucedida (" + usuario.getTentativasFalhas() + ")");
            }
            usuarioRepository.save(usuario);
        });
    }

    @Transactional
    public void registrarLoginComSucesso(String email) {
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(usuario -> {
            usuario.setTentativasFalhas(0);
            usuario.setBloqueadoAte(null);
            usuarioRepository.save(usuario);
            auditoriaService.registrarComoUsuario(usuario.getEmail(), AcaoAuditoria.LOGIN,
                    "Usuario", usuario.getId(), "Autenticacao realizada");
        });
    }

    void validarPolitica(String senha) {
        if (senha == null || !SENHA_VALIDA.matcher(senha).matches()) {
            throw new RegraNegocioException(
                    "A senha deve ter no minimo 8 caracteres e conter ao menos uma letra e um numero.");
        }
    }

    private void validarReuso(Usuario usuario, String novaSenha) {
        List<SenhaHistorico> ultimas = senhaHistoricoRepository
                .findByUsuarioOrderByCriadoEmDesc(usuario);
        ultimas.stream()
                .limit(SENHAS_ANTERIORES_BLOQUEADAS)
                .filter(historico -> passwordEncoder.matches(novaSenha, historico.getSenha()))
                .findAny()
                .ifPresent(historico -> {
                    throw new RegraNegocioException(
                            "A nova senha nao pode repetir nenhuma das "
                                    + SENHAS_ANTERIORES_BLOQUEADAS + " ultimas senhas utilizadas.");
                });
    }
}
