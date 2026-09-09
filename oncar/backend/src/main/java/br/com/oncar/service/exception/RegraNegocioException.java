package br.com.oncar.service.exception;

/**
 * Erro de regra de negocio apresentado ao usuario em portugues e em linguagem
 * nao tecnica, indicando a acao corretiva esperada (RNF13).
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
