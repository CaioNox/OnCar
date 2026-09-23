package br.com.oncar.web.api;

import br.com.oncar.service.exception.RegistroNaoEncontradoException;
import br.com.oncar.service.exception.RegraNegocioException;
import br.com.oncar.web.api.dto.RespostaDtos;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "br.com.oncar.web.api")
public class TratadorDeErrosApi {

    @ExceptionHandler(RegistroNaoEncontradoException.class)
    public ResponseEntity<RespostaDtos.Erro> naoEncontrado(RegistroNaoEncontradoException excecao) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RespostaDtos.Erro.de(excecao.getMessage()));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<RespostaDtos.Erro> regraDeNegocio(RegraNegocioException excecao) {
        return ResponseEntity.badRequest().body(RespostaDtos.Erro.de(excecao.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaDtos.Erro> dadosInvalidos(MethodArgumentNotValidException excecao) {
        List<String> detalhes = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new RespostaDtos.Erro(
                "Confira os dados informados antes de continuar.", detalhes));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespostaDtos.Erro> acessoNegado(AccessDeniedException excecao) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(RespostaDtos.Erro.de(
                "O seu perfil de acesso nao permite esta operacao."));
    }
}
