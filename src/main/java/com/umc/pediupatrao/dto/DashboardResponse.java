package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(LocalDate inicio, LocalDate fim, String agrupamento,
        LocalDateTime atualizadoEm, Indicadores indicadores, List<Venda> vendas,
        List<Situacao> situacoes, List<ProdutoVendido> produtos, long pedidosInconsistentes) {
    public record Indicadores(long pedidos, long vendasConcluidas, BigDecimal faturamento,
                             BigDecimal ticketMedio, long cancelados, long emAndamento) {}
    public record Venda(LocalDate periodo, long quantidade, BigDecimal faturamento) {}
    public record Situacao(StatusPedido status, String descricao, long quantidade) {}
    public record ProdutoVendido(String produtoId, String nome, long quantidade) {}
}
