package br.com.oncar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Registro das sessoes autenticadas, usado para encerrar o acesso de um usuario no
 * momento em que ele e inativado, muda de perfil ou tem a senha trocada.
 *
 * O registro fica em memoria, o que basta para uma unica instancia da aplicacao. Com
 * mais de uma instancia atras de um balanceador, troque por Spring Session (JDBC ou
 * Redis) para que todas enxerguem as mesmas sessoes.
 */
@Configuration
public class SessoesConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa o registro quando o Tomcat destroi uma sessao (logout ou expiracao por inatividade). */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
