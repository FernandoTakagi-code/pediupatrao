package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente (HTTP 404). */
public class RecursoNaoEncontradoException extends RegraNegocioException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, mensagem);
    }
}