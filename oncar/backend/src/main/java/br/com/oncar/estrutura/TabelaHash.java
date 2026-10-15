package br.com.oncar.estrutura;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabela hash com encadeamento separado (separate chaining).
 * Cada posicao do vetor guarda uma lista encadeada de nos; colisoes sao
 * resolvidas inserindo na lista do mesmo indice. Quando o fator de carga
 * passa de 0,75 o vetor dobra de tamanho e as chaves sofrem rehash.
 * Nao e thread-safe: quem compartilhar a instancia deve sincronizar.
 */
public class TabelaHash<K, V> {

    private static final int CAPACIDADE_INICIAL = 16;
    private static final double FATOR_DE_CARGA = 0.75;

    private static final class No<K, V> {
        final K chave;
        V valor;
        No<K, V> proximo;

        No(K chave, V valor, No<K, V> proximo) {
            this.chave = chave;
            this.valor = valor;
            this.proximo = proximo;
        }
    }

    private No<K, V>[] baldes;
    private int tamanho;

    public TabelaHash() {
        this(CAPACIDADE_INICIAL);
    }

    public TabelaHash(int capacidadeInicial) {
        if (capacidadeInicial <= 0) {
            throw new IllegalArgumentException("A capacidade inicial deve ser positiva");
        }
        this.baldes = novoVetor(capacidadeInicial);
    }

    /** Insere ou substitui o valor da chave. Retorna o valor anterior, ou null. */
    public V put(K chave, V valor) {
        exigirChave(chave);
        int indice = indice(chave, baldes.length);
        for (No<K, V> no = baldes[indice]; no != null; no = no.proximo) {
            if (no.chave.equals(chave)) {
                V anterior = no.valor;
                no.valor = valor;
                return anterior;
            }
        }
        baldes[indice] = new No<>(chave, valor, baldes[indice]);
        tamanho++;
        if (tamanho > baldes.length * FATOR_DE_CARGA) {
            redimensionar();
        }
        return null;
    }

    public V get(K chave) {
        if (chave == null) {
            return null;
        }
        for (No<K, V> no = baldes[indice(chave, baldes.length)]; no != null; no = no.proximo) {
            if (no.chave.equals(chave)) {
                return no.valor;
            }
        }
        return null;
    }

    public boolean containsKey(K chave) {
        if (chave == null) {
            return false;
        }
        for (No<K, V> no = baldes[indice(chave, baldes.length)]; no != null; no = no.proximo) {
            if (no.chave.equals(chave)) {
                return true;
            }
        }
        return false;
    }

    /** Remove a chave. Retorna o valor removido, ou null se nao existia. */
    public V remove(K chave) {
        if (chave == null) {
            return null;
        }
        int indice = indice(chave, baldes.length);
        No<K, V> anterior = null;
        for (No<K, V> no = baldes[indice]; no != null; anterior = no, no = no.proximo) {
            if (no.chave.equals(chave)) {
                if (anterior == null) {
                    baldes[indice] = no.proximo;
                } else {
                    anterior.proximo = no.proximo;
                }
                tamanho--;
                return no.valor;
            }
        }
        return null;
    }

    public void clear() {
        baldes = novoVetor(CAPACIDADE_INICIAL);
        tamanho = 0;
    }

    public int size() {
        return tamanho;
    }

    public boolean isEmpty() {
        return tamanho == 0;
    }

    public int capacidade() {
        return baldes.length;
    }

    public List<V> valores() {
        List<V> valores = new ArrayList<>(tamanho);
        for (No<K, V> balde : baldes) {
            for (No<K, V> no = balde; no != null; no = no.proximo) {
                valores.add(no.valor);
            }
        }
        return valores;
    }

    private void redimensionar() {
        No<K, V>[] antigos = baldes;
        No<K, V>[] novos = novoVetor(antigos.length * 2);
        for (No<K, V> balde : antigos) {
            No<K, V> no = balde;
            while (no != null) {
                No<K, V> proximo = no.proximo;
                int indice = indice(no.chave, novos.length);
                no.proximo = novos[indice];
                novos[indice] = no;
                no = proximo;
            }
        }
        baldes = novos;
    }

    private static int indice(Object chave, int capacidade) {
        int h = chave.hashCode();
        h ^= (h >>> 16);
        return (h & 0x7fffffff) % capacidade;
    }

    private static void exigirChave(Object chave) {
        if (chave == null) {
            throw new IllegalArgumentException("A chave nao pode ser nula");
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> No<K, V>[] novoVetor(int capacidade) {
        return (No<K, V>[]) new No[capacidade];
    }
}
