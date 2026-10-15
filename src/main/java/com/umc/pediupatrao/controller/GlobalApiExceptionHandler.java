package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.exception.RegraNegocioException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Tratamento de erros das APIs REST. AccessDeniedException NÃO é tratada aqui de propósito:
 * ela segue para o Spring Security, que responde 403 (JSON) via AccessDeniedHandler.
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class GlobalApiExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> regraNegocio(RegraNegocioException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("erro", ex.getMessage(), "status", ex.getStatus().value()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", "Requisição inválida: verifique o formato dos dados enviados.", "status", 400));
    }
}