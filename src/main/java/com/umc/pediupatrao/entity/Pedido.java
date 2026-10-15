package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pedido da pizzaria. Todos os valores monetários (subtotal, desconto e total) e os
 * campos de responsável/data são calculados e preenchidos pelo servidor (PedidoService);
 * nenhum controlador faz bind direto desta entidade a partir do corpo da requisição.
 */
@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens = new ArrayList<>();
    private String observacao;

    private BigDecimal subtotal;
    private BigDecimal descontoPercentual;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;

    private StatusPedido status;

    // Entrada
    private String responsavelRegistro;
    private LocalDateTime dataHoraEntrada;

    // Saída (entrega ou retirada)
    private String responsavelSaida;
    private LocalDateTime dataHoraSaida;

    // Cancelamento
    private String motivoCancelamento;
    private String canceladoPor;
    private LocalDateTime dataHoraCancelamento;

    private List<EventoStatus> historico = new ArrayList<>();

    // Campos legados (modelo antigo de pedido). Mantidos apenas para ler documentos antigos.
    private String tipoPizza;
    private String sabor;
    private int quantidade;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens == null ? new ArrayList<>() : itens; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(BigDecimal descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public String getResponsavelRegistro() { return responsavelRegistro; }
    public void setResponsavelRegistro(String responsavelRegistro) { this.responsavelRegistro = responsavelRegistro; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }

    public String getCanceladoPor() { return canceladoPor; }
    public void setCanceladoPor(String canceladoPor) { this.canceladoPor = canceladoPor; }

    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }

    public List<EventoStatus> getHistorico() { return historico; }
    public void setHistorico(List<EventoStatus> historico) { this.historico = historico == null ? new ArrayList<>() : historico; }

    public String getTipoPizza() { return tipoPizza; }
    public void setTipoPizza(String tipoPizza) { this.tipoPizza = tipoPizza; }

    public String getSabor() { return sabor; }
    public void setSabor(String sabor) { this.sabor = sabor; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
}