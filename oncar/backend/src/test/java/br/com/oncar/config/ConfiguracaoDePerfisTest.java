package br.com.oncar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

/**
 * Protege os padroes de seguranca dos arquivos de configuracao: estes valores nao
 * aparecem em nenhum teste de tela, entao uma regressao passaria despercebida.
 */
class ConfiguracaoDePerfisTest {

    @Test
    @DisplayName("sem perfil informado a aplicacao sobe como producao, nunca com a base de demonstracao")
    void perfilPadraoEProducao() throws IOException {
        Properties base = carregar("application.properties");

        assertThat(base.getProperty("spring.profiles.active")).isNull();
        assertThat(base.getProperty("spring.profiles.default")).isEqualTo("prod");
    }

    @Test
    @DisplayName("o console web do H2 fica desligado")
    void consoleDoH2Desligado() throws IOException {
        assertThat(carregar("application.properties").getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(carregar("application-dev.properties").getProperty("spring.h2.console.enabled")).isNull();
        assertThat(carregar("application-prod.properties").getProperty("spring.h2.console.enabled"))
                .isIn(null, "false");
    }

    @Test
    @DisplayName("o perfil dev, que tem senha de demonstracao publica, so atende na propria maquina")
    void perfilDevSoNaPropriaMaquina() throws IOException {
        assertThat(carregar("application-dev.properties").getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("em producao a senha do banco vem so da variavel de ambiente, sem valor padrao")
    void senhaDoBancoSemPadraoEmProducao() throws IOException {
        assertThat(carregar("application-prod.properties").getProperty("spring.datasource.password"))
                .isEqualTo("${ONCAR_DB_PASSWORD}");
    }

    @Test
    @DisplayName("producao recusa subir quando a variavel da senha do banco nao existe")
    void producaoSemSenhaNaoSobe() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("spring.datasource.password", "${ONCAR_DB_PASSWORD}");

        assertThatThrownBy(() -> VerificacaoDeProducao.verificarSenhaDoBanco(ambiente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ONCAR_DB_PASSWORD");
    }

    @Test
    @DisplayName("producao recusa subir com a senha do banco em branco")
    void producaoComSenhaEmBrancoNaoSobe() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("spring.datasource.password", "${ONCAR_DB_PASSWORD}")
                .withProperty("ONCAR_DB_PASSWORD", "  ");

        assertThatThrownBy(() -> VerificacaoDeProducao.verificarSenhaDoBanco(ambiente))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("producao sobe quando a senha do banco foi informada")
    void producaoComSenhaSobe() {
        MockEnvironment ambiente = new MockEnvironment()
                .withProperty("spring.datasource.password", "${ONCAR_DB_PASSWORD}")
                .withProperty("ONCAR_DB_PASSWORD", "s3nha-do-servidor");

        assertThatCode(() -> VerificacaoDeProducao.verificarSenhaDoBanco(ambiente)).doesNotThrowAnyException();
    }

    private static Properties carregar(String arquivo) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(arquivo));
    }
}
