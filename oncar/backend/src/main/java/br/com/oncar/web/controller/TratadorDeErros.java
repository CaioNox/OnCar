package br.com.oncar.web.controller;

import br.com.oncar.service.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * RNF13: converte as violacoes de regra de negocio em mensagens em portugues,
 * apresentadas na propria tela de origem.
 */
@ControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RegraNegocioException.class)
    public String tratarRegraDeNegocio(RegraNegocioException excecao, HttpServletRequest request,
                                       RedirectAttributes atributos) {
        atributos.addFlashAttribute("erro", excecao.getMessage());
        return "redirect:" + telaDeOrigem(request);
    }

    /**
     * Devolve o caminho interno da tela que originou a operacao. Enderecos externos
     * sao descartados para que o cabecalho Referer nao redirecione para fora da aplicacao.
     */
    private String telaDeOrigem(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            URI origem = new URI(referer);
            if (origem.getHost() != null && !origem.getHost().equalsIgnoreCase(request.getServerName())) {
                return "/";
            }
            String caminho = origem.getPath();
            if (caminho == null || caminho.isBlank()) {
                return "/";
            }
            return origem.getQuery() == null ? caminho : caminho + "?" + origem.getQuery();
        } catch (URISyntaxException excecao) {
            return "/";
        }
    }
}
