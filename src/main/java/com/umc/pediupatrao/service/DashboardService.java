package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardResponse;
import com.umc.pediupatrao.dto.DashboardResponse.*;
import com.umc.pediupatrao.entity.*;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Consulta somente o período solicitado; nenhum dado pessoal é enviado ao dashboard. */
@Service
public class DashboardService {
    private final MongoTemplate mongo;
    private final Clock clock;

    public DashboardService(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public DashboardResponse consultar(LocalDate inicio, LocalDate fim, StatusPedido status,
                                       String agrupamento, int limite) {
        LocalDate hoje = LocalDate.now(clock);
        if (fim == null) fim = hoje;
        if (inicio == null) inicio = fim.minusDays(29);
        if (inicio.isAfter(fim) || ChronoUnit.DAYS.between(inicio, fim) > 365
                || fim.isAfter(hoje)) {
            throw new RegraNegocioException("Informe um período de 1 a 366 dias, sem datas futuras.");
        }
        if (!Set.of("DIA", "MES").contains(agrupamento)) {
            throw new RegraNegocioException("Agrupamento deve ser DIA ou MES.");
        }
        if (limite < 1 || limite > 20) throw new RegraNegocioException("Limite deve ser de 1 a 20.");

        // Intervalo semiaberto preserva todos os horários do último dia.
        Query query = Query.query(Criteria.where("dataHoraEntrada").gte(inicio.atStartOfDay())
                .lt(fim.plusDays(1).atStartOfDay()));
        query.fields().include("dataHoraEntrada").include("status").include("valorTotal").include("itens");
        // Filtrar status após a conversão mantém compatibilidade com ENTREGUE/EM_PREPARO legados.
        List<Pedido> pedidos = mongo.find(query, Pedido.class).stream()
                .filter(p -> status == null || p.getStatus() == status).toList();
        Map<LocalDate, Venda> serie = new TreeMap<>();
        for (LocalDate d = periodo(inicio, agrupamento); !d.isAfter(fim);
             d = "MES".equals(agrupamento) ? d.plusMonths(1) : d.plusDays(1)) {
            serie.put(d, new Venda(d, 0, dinheiro(BigDecimal.ZERO)));
        }
        Map<StatusPedido, Long> situacoes = new EnumMap<>(StatusPedido.class);
        Map<String, ProdutoVendido> ranking = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        long concluidas = 0, inconsistentes = 0;
        for (Pedido p : pedidos) {
            if (p.getStatus() != null) situacoes.merge(p.getStatus(), 1L, Long::sum);
            boolean invalido = p.getStatus() == null || p.getValorTotal() == null
                    || p.getValorTotal().signum() < 0;
            if (invalido) inconsistentes++;
            if (p.getStatus() != StatusPedido.FINALIZADO || invalido) continue;
            concluidas++;
            total = total.add(p.getValorTotal());
            LocalDate dia = periodo(p.getDataHoraEntrada().toLocalDate(), agrupamento);
            Venda anterior = serie.get(dia);
            serie.put(dia, new Venda(dia, anterior.quantidade() + 1,
                    dinheiro(anterior.faturamento().add(p.getValorTotal()))));
            boolean itemInvalido = false;
            for (ItemPedido item : p.getItens()) {
                if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()
                        || item.getQuantidade() <= 0) {
                    itemInvalido = true;
                    continue;
                }
                String nome = item.getNomeProduto() == null ? "Produto sem nome" : item.getNomeProduto();
                ranking.merge(item.getProdutoId(), new ProdutoVendido(item.getProdutoId(), nome, item.getQuantidade()),
                        (a, b) -> new ProdutoVendido(a.produtoId(),
                                a.nome().compareTo(b.nome()) <= 0 ? a.nome() : b.nome(), a.quantidade() + b.quantidade()));
            }
            if (itemInvalido || p.getItens().isEmpty()) inconsistentes++;
        }
        long cancelados = situacoes.getOrDefault(StatusPedido.CANCELADO, 0L);
        long finalizados = situacoes.getOrDefault(StatusPedido.FINALIZADO, 0L);
        var indicadores = new Indicadores(pedidos.size(), concluidas, dinheiro(total),
                concluidas == 0 ? dinheiro(BigDecimal.ZERO) : total.divide(BigDecimal.valueOf(concluidas), 2, RoundingMode.HALF_UP),
                cancelados, pedidos.size() - cancelados - finalizados - pedidos.stream().filter(p -> p.getStatus() == null).count());
        var distribuicao = Arrays.stream(StatusPedido.values())
                .map(s -> new Situacao(s, s.getDescricao(), situacoes.getOrDefault(s, 0L))).toList();
        var produtos = ranking.values().stream().sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                .thenComparing(ProdutoVendido::produtoId)).limit(limite).toList();
        return new DashboardResponse(inicio, fim, agrupamento, LocalDateTime.now(clock), indicadores,
                List.copyOf(serie.values()), distribuicao, produtos, inconsistentes);
    }

    private static LocalDate periodo(LocalDate data, String agrupamento) {
        return "MES".equals(agrupamento) ? data.withDayOfMonth(1) : data;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
