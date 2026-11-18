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

    @Test
    void consultasComChaveNulaNaoFalham() {
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        tabela.put("A", 1);

        assertThat(tabela.containsKey(null)).isFalse();
        assertThat(tabela.remove(null)).isNull();
        assertThat(tabela.size()).isEqualTo(1);
    }

    @Test
    void rejeitaCapacidadeInicialNaoPositiva() {
        assertThatThrownBy(() -> new TabelaHash<String, Integer>(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TabelaHash<String, Integer>(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeDoMeioDoFimEDoInicioDaMesmaCadeia() {
        // "AaAa", "AaBB" e "BBBB" tem o mesmo hashCode: caem no mesmo balde em qualquer capacidade.
        // A insercao e pela cabeca, entao a cadeia fica BBBB -> AaBB -> AaAa.
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        tabela.put("AaAa", 1);
        tabela.put("AaBB", 2);
        tabela.put("BBBB", 3);

        assertThat(tabela.remove("AaBB")).as("meio").isEqualTo(2);
        assertThat(tabela.get("AaAa")).isEqualTo(1);
        assertThat(tabela.get("BBBB")).isEqualTo(3);
        assertThat(tabela.remove("AaAa")).as("fim").isEqualTo(1);
        assertThat(tabela.remove("BBBB")).as("inicio").isEqualTo(3);
        assertThat(tabela.isEmpty()).isTrue();
        assertThat(tabela.valores()).isEmpty();
    }

    @Test
    void chavesComMesmoHashSaoDistinguidasPorEquals() {
        // "Aa" e "BB" tem o mesmo hashCode em Java
        TabelaHash<String, Integer> tabela = new TabelaHash<>();
        tabela.put("Aa", 1);
        tabela.put("BB", 2);

        assertThat(tabela.get("Aa")).isEqualTo(1);
        assertThat(tabela.get("BB")).isEqualTo(2);
        assertThat(tabela.remove("Aa")).isEqualTo(1);
        assertThat(tabela.get("BB")).isEqualTo(2);
        assertThat(tabela.size()).isEqualTo(1);
    }

    @Test
    void limparVoltaACapacidadeInicial() {
        TabelaHash<Integer, Integer> tabela = new TabelaHash<>();
        int capacidadeInicial = tabela.capacidade();
        for (int i = 0; i < 100; i++) {
            tabela.put(i, i);
        }

        tabela.clear();

        assertThat(tabela.size()).isZero();
        assertThat(tabela.capacidade()).isEqualTo(capacidadeInicial);
        assertThat(tabela.get(5)).isNull();
    }

    @Test
    void substituirValorNaoAlteraOTamanho() {
        TabelaHash<String, Integer> tabela = new TabelaHash<>(4);
        for (int i = 0; i < 10; i++) {
            tabela.put("A", i);
        }

        assertThat(tabela.size()).isEqualTo(1);
        assertThat(tabela.capacidade()).as("substituicao nao conta para o fator de carga").isEqualTo(4);
    }
}
