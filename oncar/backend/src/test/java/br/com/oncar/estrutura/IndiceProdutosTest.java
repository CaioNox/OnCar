package br.com.oncar.estrutura;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndiceProdutosTest {

    @Test
    @DisplayName("indexa, localiza, reindexa e remove pelo SKU normalizado")
    void cicloDoIndice() {
        IndiceProdutos indice = new IndiceProdutos();

        indice.indexar("FL-001", 1L);
        indice.indexar("FL-001", 2L);
        assertThat(indice.buscarId("FL-001")).isEqualTo(2L);
        assertThat(indice.tamanho()).isEqualTo(1);

        indice.remover("FL-001");
        assertThat(indice.buscarId("FL-001")).isNull();
        assertThat(indice.tamanho()).isZero();
    }

    @Test
    @DisplayName("SKU nulo nao e encontrado e remover chave ausente nao falha")
    void chavesAusentes() {
        IndiceProdutos indice = new IndiceProdutos();

        assertThat(indice.buscarId(null)).isNull();
        indice.remover("NAO-EXISTE");
        indice.remover(null);
        assertThat(indice.tamanho()).isZero();
    }

    @Test
    @DisplayName("o acesso concorrente de varias requisicoes nao perde nenhuma indexacao")
    void acessoConcorrente() throws InterruptedException {
        IndiceProdutos indice = new IndiceProdutos();
        List<Thread> threads = new ArrayList<>();
        for (int bloco = 0; bloco < 8; bloco++) {
            int inicio = bloco * 500;
            Thread thread = new Thread(() -> {
                for (int i = inicio; i < inicio + 500; i++) {
                    indice.indexar("SKU-" + i, (long) i);
                }
            });
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(indice.tamanho()).isEqualTo(4000);
        assertThat(indice.buscarId("SKU-3999")).isEqualTo(3999L);
    }
}
