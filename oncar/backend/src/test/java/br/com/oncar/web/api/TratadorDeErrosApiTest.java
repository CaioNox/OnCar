package br.com.oncar.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.web.api.dto.RespostaDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class TratadorDeErrosApiTest {

    private final TratadorDeErrosApi tratador = new TratadorDeErrosApi();

    private static MethodParameter parametroQualquer() throws NoSuchMethodException {
        return new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
    }

    @Test
    @DisplayName("registro nao encontrado vira 404 com a mensagem da regra")
    void naoEncontrado() {
        ResponseEntity<RespostaDtos.Erro> resposta =
                tratador.naoEncontrado(new RegistroNaoEncontradoException("Produto nao encontrado."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().mensagem()).isEqualTo("Produto nao encontrado.");
        assertThat(resposta.getBody().detalhes()).isEmpty();
    }

    @Test
    @DisplayName("regra de negocio violada vira 400 com a mensagem da regra")
    void regraDeNegocio() {
        ResponseEntity<RespostaDtos.Erro> resposta =
                tratador.regraDeNegocio(new RegraNegocioException("Saldo insuficiente."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().mensagem()).isEqualTo("Saldo insuficiente.");
    }

    @Test
    @DisplayName("campos invalidos viram 400 com a mensagem de cada campo, na ordem")
    void camposInvalidos() throws NoSuchMethodException {
        BeanPropertyBindingResult erros = new BeanPropertyBindingResult(new Object(), "form");
        erros.addError(new FieldError("form", "quantidade", "Informe a quantidade"));
        erros.addError(new FieldError("form", "justificativa", "Justifique o ajuste"));

        ResponseEntity<RespostaDtos.Erro> resposta =
                tratador.dadosInvalidos(new MethodArgumentNotValidException(parametroQualquer(), erros));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().mensagem()).isEqualTo("Confira os dados informados antes de continuar.");
        assertThat(resposta.getBody().detalhes()).containsExactly("Informe a quantidade", "Justifique o ajuste");
    }

    @Test
    @DisplayName("parametro de tipo errado vira 400 citando o nome do parametro")
    void parametroDeTipoErrado() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException excecao = new MethodArgumentTypeMismatchException("abc", Long.class,
                "fornecedorId", parametroQualquer(), new NumberFormatException("abc"));

        ResponseEntity<RespostaDtos.Erro> resposta = tratador.parametroInvalido(excecao);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().mensagem()).contains("\"fornecedorId\"").contains("nao e valido");
    }

    @Test
    @DisplayName("acesso negado vira 403 sem expor a mensagem interna do Spring")
    void acessoNegado() {
        ResponseEntity<RespostaDtos.Erro> resposta =
                tratador.acessoNegado(new AccessDeniedException("Access Denied: detalhe interno"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().mensagem()).isEqualTo("O seu perfil de acesso nao permite esta operacao.");
    }
}
