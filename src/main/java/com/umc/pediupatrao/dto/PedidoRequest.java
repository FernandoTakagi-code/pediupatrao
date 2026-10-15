package com.umc.pediupatrao.dto;

import java.util.List;

/** Dados aceitos para criar/alterar um pedido. Total, desconto e status NÃO são aceitos do cliente HTTP. */
public record PedidoRequest(String clienteId, List<ItemRequest> itens, String observacao) {
}