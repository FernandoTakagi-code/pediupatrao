package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.util.ArrayList;
import java.util.List;

/** Dados de um pedido já preparados para a tela (nome do cliente e ações possíveis). */
public class PedidoView {

    private final Pedido pedido;
    private final String clienteNome;
    private final List<StatusPedido> proximos;
    private final boolean antesDaSaida;

    public PedidoView(Pedido pedido, String clienteNome) {
        this.pedido = pedido;
        this.clienteNome = clienteNome;
        StatusPedido s = pedido.getStatus();
        this.proximos = s == null ? new ArrayList<>() : new ArrayList<>(s.proximos());
        this.antesDaSaida = s != null && s.isAntesDaSaida();
    }

    public Pedido getPedido() { return pedido; }
    public String getClienteNome() { return clienteNome; }
    public List<StatusPedido> getProximos() { return proximos; }
    /** true se ainda é possível aplicar desconto ou cancelar (antes da saída). */
    public boolean isAntesDaSaida() { return antesDaSaida; }

    /** Classe CSS (Bootstrap) do selo de status. */
    public String getBadgeClasse() {
        StatusPedido s = pedido.getStatus();
        if (s == null) {
            return "bg-secondary";
        }
        switch (s) {
            case FINALIZADO:
                return "bg-success";
            case CANCELADO:
                return "bg-danger";
            case EM_PREPARACAO:
                return "bg-warning text-dark";
            case PRONTO:
                return "bg-info text-dark";
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return "bg-primary";
            default:
                return "bg-secondary";
        }
    }
}