package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    DashboardService service = new DashboardService(mongo, clock);
    LocalDate dia = LocalDate.of(2026, 11, 6);

    @BeforeEach void preparar() { when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of()); }

    static Pedido pedido(StatusPedido status, String valor, LocalDate dia) {
        Pedido p = new Pedido();
        p.setStatus(status);
        p.setValorTotal(valor == null ? null : new BigDecimal(valor));
        p.setDataHoraEntrada(dia.atTime(12, 0));
        p.setItens(List.of(new ItemPedido("pizza", "Pizza", 2, new BigDecimal("25.00"))));
        return p;
    }

    @Test void calculaReceitaLiquidaTicketStatusERankingSemCancelados() {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, "45", dia), pedido(StatusPedido.FINALIZADO, "50", dia),
                pedido(StatusPedido.CANCELADO, "100", dia), pedido(StatusPedido.RECEBIDO, "30", dia)));
        var r = service.consultar(dia.minusDays(1), dia, null, "DIA", 5);
        assertThat(r.indicadores().pedidos()).isEqualTo(4);
        assertThat(r.indicadores().faturamento()).isEqualByComparingTo("95");
        assertThat(r.indicadores().ticketMedio()).isEqualByComparingTo("47.50");
        assertThat(r.indicadores().emAndamento()).isEqualTo(1);
        assertThat(r.indicadores().cancelados()).isEqualTo(1);
        assertThat(r.produtos().getFirst().quantidade()).isEqualTo(4);
        assertThat(r.vendas()).hasSize(2);
        assertThat(r.vendas().getFirst().quantidade()).isZero();
        assertThat(r.vendas().getLast().quantidade()).isEqualTo(2);
    }

    @Test void vazioTemZerosESerieCompletaComPeriodoPadrao() {
        var r = service.consultar(null, null, null, "DIA", 5);
        assertThat(r.inicio()).isEqualTo(dia.minusDays(29));
        assertThat(r.vendas()).hasSize(30);
        assertThat(r.indicadores().ticketMedio()).isEqualByComparingTo("0");
        assertThat(r.situacoes()).hasSize(7).allMatch(s -> s.quantidade() == 0);
    }

    @Test void filtraStatusAgrupaPorMesEOrdenaEmpatesPorId() {
        Pedido a = pedido(StatusPedido.FINALIZADO, "20", dia.minusMonths(1));
        a.setItens(List.of(new ItemPedido("b", "B", 2, BigDecimal.TEN), new ItemPedido("a", "A", 2, BigDecimal.TEN)));
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(a, pedido(StatusPedido.CANCELADO, "20", dia)));
        var r = service.consultar(dia.minusMonths(1), dia, StatusPedido.FINALIZADO, "MES", 1);
        assertThat(r.indicadores().pedidos()).isEqualTo(1);
        assertThat(r.vendas()).hasSize(2);
        assertThat(r.vendas().getFirst().periodo()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.produtos()).hasSize(1);
        assertThat(r.produtos().getFirst().produtoId()).isEqualTo("a");
    }

    @Test void incompletosNaoInventamReceitaEItensInvalidosSaoSinalizados() {
        Pedido incompleto = pedido(StatusPedido.FINALIZADO, null, dia);
        Pedido semItens = pedido(StatusPedido.FINALIZADO, "10", dia);
        semItens.setItens(null);
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(incompleto, semItens));
        var r = service.consultar(dia, dia, null, "DIA", 5);
        assertThat(r.pedidosInconsistentes()).isEqualTo(2);
        assertThat(r.indicadores().vendasConcluidas()).isEqualTo(1);
        assertThat(r.indicadores().faturamento()).isEqualByComparingTo("10");
        assertThat(r.produtos()).isEmpty();
    }

    @Test void consultaUsaLimiteExclusivoDoDiaSeguinteEProjecao() {
        service.consultar(dia, dia, null, "DIA", 5);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(Pedido.class));
        var filtro = captor.getValue().getQueryObject().get("dataHoraEntrada", org.bson.Document.class);
        assertThat(filtro.get("$gte")).isEqualTo(dia.atStartOfDay());
        assertThat(filtro.get("$lt")).isEqualTo(dia.plusDays(1).atStartOfDay());
        assertThat(captor.getValue().getFieldsObject()).doesNotContainKeys("clienteId", "observacao");
    }

    @Test void rejeitaFiltrosInvalidosAntesDeConsultarBanco() {
        assertThatThrownBy(() -> service.consultar(dia, dia.minusDays(1), null, "DIA", 5)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(dia.minusDays(366), dia, null, "DIA", 5)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(dia, dia.plusDays(1), null, "DIA", 5)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(dia, dia, null, "ANO", 5)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(dia, dia, null, "DIA", 0)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.consultar(dia, dia, null, "DIA", 21)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(mongo);
    }
}
