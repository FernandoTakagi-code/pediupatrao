package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/** Linha do histórico de status do pedido (quem mudou, para qual status e quando). */
public class EventoStatus {

    private StatusPedido status;
    private String usuario;
    private LocalDateTime dataHora;

    public EventoStatus() {
    }

    public EventoStatus(StatusPedido status, String usuario, LocalDateTime dataHora) {
        this.status = status;
        this.usuario = usuario;
        this.dataHora = dataHora;
    }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
}