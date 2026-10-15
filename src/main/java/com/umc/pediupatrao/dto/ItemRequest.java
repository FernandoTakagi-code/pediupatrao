package com.umc.pediupatrao.dto;

/** Item solicitado: apenas produto e quantidade. Preço e subtotal são definidos no servidor. */
public record ItemRequest(String produtoId, Integer quantidade) {
}