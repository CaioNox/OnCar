package br.com.oncar.config;

import br.com.oncar.domain.enums.Perfil;
import br.com.oncar.domain.model.Usuario;
import br.com.oncar.repository.UsuarioRepository;
import br.com.oncar.service.UsuarioService;
import br.com.oncar.service.exception.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Em producao a base comeca vazia e so o proprietario cadastra usuarios. Por isso o
 * primeiro proprietario vem das variaveis ONCAR_ADMIN_EMAIL e ONCAR_ADMIN_SENHA, e so
 * e criado enquanto nao existir nenhum usuario; depois disso as variaveis sao ignoradas.
 */
@Configuration
@Profile("prod")
public class PrimeiroAcesso {

    private static final Logger LOG = LoggerFactory.getLogger(PrimeiroAcesso.class);

    @Bean
    public CommandLineRunner criarPrimeiroProprietario(UsuarioRepository usuarioRepository,
                                                       UsuarioService usuarioService,
                                                       @Value("${oncar.admin.nome:Proprietario}") String nome,
                                                       @Value("${oncar.admin.email:}") String email,
                                                       @Value("${oncar.admin.senha:}") String senha) {
        return argumentos -> {
            if (usuarioRepository.count() > 0) {
                return;
            }
            if (email.isBlank() || senha.isBlank()) {
                LOG.warn("Nenhum usuario cadastrado e ONCAR_ADMIN_EMAIL/ONCAR_ADMIN_SENHA nao foram definidos: "
                        + "ninguem conseguira entrar. Defina as duas variaveis e reinicie a aplicacao.");
                return;
            }
            Usuario proprietario = new Usuario();
            proprietario.setNome(nome);
            proprietario.setEmail(email.trim());
            proprietario.setPerfil(Perfil.PROPRIETARIO);
            try {
                usuarioService.cadastrar(proprietario, senha);
            } catch (RegraNegocioException excecao) {
                // sobe com erro claro em vez de ficar no ar sem nenhum acesso possivel
                throw new IllegalStateException("Nao foi possivel criar o primeiro proprietario a partir de "
                        + "ONCAR_ADMIN_EMAIL/ONCAR_ADMIN_SENHA: " + excecao.getMessage(), excecao);
            }
            LOG.info("Primeiro proprietario criado: {}. Troque a senha em Alterar senha apos o primeiro acesso.",
                    proprietario.getEmail());
        };
    }
}
