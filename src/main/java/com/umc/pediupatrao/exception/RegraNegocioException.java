package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio. Carrega o status HTTP adequado para a API. */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(String mensagem) {
        this(HttpStatus.BAD_REQUEST, mensagem);
    }

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}