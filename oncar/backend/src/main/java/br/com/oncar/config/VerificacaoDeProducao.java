package br.com.oncar.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Recusa subir em producao sem a senha do banco. Sem isto, o Spring Boot aceitaria o
 * texto literal "${ONCAR_DB_PASSWORD}" como senha e so falharia la na frente, com um
 * erro de conexao que nao aponta a causa.
 *
 * Roda como BeanFactoryPostProcessor, antes de qualquer bean ser criado (inclusive o
 * DataSource), para que a mensagem seja a primeira coisa que aparece no log.
 */
@Configuration
@Profile("prod")
public class VerificacaoDeProducao {

    static final String MENSAGEM = "A senha do PostgreSQL nao foi definida. Informe a variavel de ambiente "
            + "ONCAR_DB_PASSWORD (no Docker, no arquivo .env ao lado do docker-compose.yml) e suba a "
            + "aplicacao de novo.";

    @Bean
    static BeanFactoryPostProcessor exigirSenhaDoBanco(Environment ambiente) {
        return fabrica -> verificarSenhaDoBanco(ambiente);
    }

    static void verificarSenhaDoBanco(Environment ambiente) {
        String senha;
        try {
            senha = ambiente.getProperty("spring.datasource.password");
        } catch (IllegalArgumentException placeholderSemValor) {
            senha = null;
        }
        if (senha == null || senha.isBlank() || senha.contains("${")) {
            throw new IllegalStateException(MENSAGEM);
        }
    }
}
