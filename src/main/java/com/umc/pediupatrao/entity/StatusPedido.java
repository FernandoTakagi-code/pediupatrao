package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados do pedido e fluxo obrigatório:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> (SAIU_PARA_ENTREGA | RETIRADO) -> FINALIZADO.
 * CANCELADO é excepcional (somente GERENTE, antes da saída) e é tratado em operação própria.
 */
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Próximos estados permitidos no fluxo normal (não inclui CANCELADO). */
    public Set<StatusPedido> proximos() {
        switch (this) {
            case RECEBIDO:
                return EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO:
                return EnumSet.of(PRONTO);
            case PRONTO:
                return EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return EnumSet.of(FINALIZADO);
            default:
                return EnumSet.noneOf(StatusPedido.class);
        }
    }

    /** Estados que representam a saída do pedido (exigem data/hora e responsável). */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Cancelamento e desconto só são possíveis antes da saída. */
    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Compatibilidade com documentos antigos gravados com outros nomes de status. */
    public static StatusPedido deTextoLegado(String valor) {
        if (valor == null) {
            return RECEBIDO;
        }
        String v = valor.trim().toUpperCase();
        switch (v) {
            case "EM_PREPARO":
                return EM_PREPARACAO;
            case "ENTREGUE":
                return FINALIZADO;
            default:
                try {
                    return StatusPedido.valueOf(v);
                } catch (IllegalArgumentException e) {
                    return RECEBIDO;
                }
        }
    }
}