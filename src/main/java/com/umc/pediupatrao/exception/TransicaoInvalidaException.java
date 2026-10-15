package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

/** Operação incompatível com o estado atual do pedido (HTTP 409). */
public class TransicaoInvalidaException extends RegraNegocioException {

    public TransicaoInvalidaException(String mensagem) {
        super(HttpStatus.CONFLICT, mensagem);
    }
}