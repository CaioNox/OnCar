package br.com.oncar.estrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TabelaHashTest {

    @Test
    void insereBuscaESubstitui() {
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        assertThat(tabela.put("A", 1)).isNull();
        assertThat(tabela.put("A", 2)).isEqualTo(1);
        assertThat(tabela.get("A")).isEqualTo(2);
        assertThat(tabela.size()).isEqualTo(1);
        assertThat(tabela.get("B")).isNull();
        assertThat(tabela.containsKey("A")).isTrue();
    }

    @Test
    void removeChave() {
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        tabela.put("A", 1);
        assertThat(tabela.remove("A")).isEqualTo(1);
        assertThat(tabela.remove("A")).isNull();
        assertThat(tabela.isEmpty()).isTrue();
    }

    @Test
    void trataColisoesComCapacidadeUm() {
        TabelaHash<Integer, String> tabela = new TabelaHash<>(1);
        for (int i = 0; i < 5; i++) {
            tabela.put(i, "v" + i);
        }
        assertThat(tabela.get(3)).isEqualTo("v3");
        tabela.remove(2);
        assertThat(tabela.get(2)).isNull();
        assertThat(tabela.get(4)).isEqualTo("v4");
    }

    @Test
    void redimensionaMantendoTodasAsChaves() {
        TabelaHash<Integer, Integer> tabela = new TabelaHash<>();
        int capacidadeInicial = tabela.capacidade();
        for (int i = 0; i < 1000; i++) {
            tabela.put(i, i * 2);
        }
        assertThat(tabela.capacidade()).isGreaterThan(capacidadeInicial);
        assertThat(tabela.size()).isEqualTo(1000);
        for (int i = 0; i < 1000; i++) {
            assertThat(tabela.get(i)).isEqualTo(i * 2);
        }
        assertThat(tabela.valores()).hasSize(1000);
    }

    @Test
    void rejeitaChaveNulaNaInsercao() {
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        assertThatThrownBy(() -> tabela.put(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(tabela.get(null)).isNull();
    }
}
