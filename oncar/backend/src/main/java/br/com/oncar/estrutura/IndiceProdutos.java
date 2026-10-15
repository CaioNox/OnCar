package br.com.oncar.estrutura;

import org.springframework.stereotype.Component;

/**
 * Indice de produtos em memoria baseado em {@link TabelaHash}:
 * SKU normalizado -> id do produto. Permite localizar um produto pelo SKU
 * em O(1) medio, sem consultar o banco. Acesso sincronizado por ser um bean singleton.
 */
@Component
public class IndiceProdutos {

    private final TabelaHash<String, Long> idPorSku = new TabelaHash<>();

    public synchronized Long buscarId(String skuNormalizado) {
        return idPorSku.get(skuNormalizado);
    }

    public synchronized void indexar(String skuNormalizado, Long id) {
        idPorSku.put(skuNormalizado, id);
    }

    public synchronized void remover(String skuNormalizado) {
        idPorSku.remove(skuNormalizado);
    }

    public synchronized int tamanho() {
        return idPorSku.size();
    }
}
