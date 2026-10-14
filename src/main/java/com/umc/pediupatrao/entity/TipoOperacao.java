package com.umc.pediupatrao.entity;

/** Tipos de operação registradas na auditoria. */
public enum TipoOperacao {
    CRIACAO_PEDIDO("Criação de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS_PEDIDO("Mudança de status do pedido"),
    DESCONTO_PEDIDO("Desconto em pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),
    CRIACAO_CLIENTE("Cadastro de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),
    CRIACAO_USUARIO("Criação de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}