package br.com.oncar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OnCarApplicationTests {

    @Test
    @DisplayName("O contexto da aplicacao sobe com todas as camadas configuradas")
    void contextoCarrega() {
    }
}
