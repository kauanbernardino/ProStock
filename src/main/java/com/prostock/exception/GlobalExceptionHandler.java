package com.prostock.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Recurso não encontrado", ex.getMessage()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErrorResponse> handleRegraDeNegocio(RegraDeNegocioException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Regra de negócio violada", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleCredenciaisInvalidas(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Credenciais inválidas", "E-mail ou senha incorretos"));
    }

    /**
     * Login de usuário inativado pelo administrador. Sem este handler a
     * exceção caía no genérico e a tela mostrava "erro inesperado".
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleUsuarioInativo(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Usuário inativo",
                        "Este usuário está inativo. Procure o administrador do sistema."));
    }

    /**
     * Dois usuários alteraram o mesmo produto ao mesmo tempo (ex.: duas vendas
     * da última unidade). A operação que chegou depois foi desfeita por
     * inteiro; basta tentar de novo, já com o estoque atualizado.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflitoDeConcorrencia(OptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflito de atualização",
                        "O estoque foi alterado por outra operação neste mesmo instante. "
                                + "Nada foi gravado; confira o saldo e tente novamente."));
    }

    /**
     * Rede de segurança para restrições do banco (chave estrangeira, UNIQUE,
     * CHECK) que escaparem das validações dos services: vira 409 com mensagem
     * clara em vez de 500 genérico.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Operação não permitida",
                        "O registro está em uso por outros dados do sistema ou viola uma regra do banco."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Dados inválidos", "Verifique os campos enviados", detalhes));
    }

    /**
     * URL sem rota correspondente (ex.: endpoint digitado errado). O Spring
     * levanta essa exceção quando não acha nem um controller nem um recurso
     * estático pra atender — precisa de handler próprio, senão cairia no
     * "Erro interno" genérico abaixo, o que tornaria um simples 404 em 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRotaNaoEncontrada(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Rota não encontrada", "Endpoint inexistente: " + ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(405, "Método não permitido", ex.getMessage()));
    }

    /**
     * Rede de segurança: qualquer exceção não tratada acima (ex.: falha de
     * infraestrutura, bug não previsto) cai aqui em vez de vazar para a
     * página de erro padrão do Spring. O detalhe técnico vai só pro log do
     * servidor — o cliente recebe uma mensagem genérica, no mesmo formato
     * usado pelos demais erros da API.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenerico(Exception ex) {
        log.error("Erro não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Erro interno", "Ocorreu um erro inesperado. Tente novamente."));
    }
}
