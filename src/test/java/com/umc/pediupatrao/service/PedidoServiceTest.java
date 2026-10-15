package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.TestUsuarios;
import com.umc.pediupatrao.dto.ItemRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoriaService;

    private PedidoService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T22:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService, clock);
        TestUsuarios.autenticar("gerente01", Perfil.GERENTE);
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("p1");
            }
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        TestUsuarios.limpar();
    }

    // ---------- helpers ----------

    private Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    /** Cria (via serviço) um pedido de 2 x R$ 50,00 = R$ 100,00 e o devolve já "persistido" no mock. */
    private Pedido pedidoCriado() {
        lenient().when(clienteRepository.existsById("c1")).thenReturn(true);
        lenient().when(produtoRepository.findById("pz")).thenReturn(Optional.of(produto("pz", "Calabresa", 50.0, true)));
        Pedido p = service.criarPedido(new PedidoRequest("c1", List.of(new ItemRequest("pz", 2)), null));
        lenient().when(pedidoRepository.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    private Pedido pedidoNoStatus(StatusPedido status) {
        Pedido p = pedidoCriado();
        p.setStatus(status);
        return p;
    }

    // ---------- RF02: criação ----------

    @Test
    void criarPedidoCalculaTotalNoServidorEPreencheResponsavelEEntrada() {
        Pedido p = pedidoCriado();

        assertEquals(StatusPedido.RECEBIDO, p.getStatus());
        assertEquals("gerente01", p.getResponsavelRegistro());
        assertEquals(LocalDateTime.of(2026, 10, 14, 19, 0), p.getDataHoraEntrada());
        assertEquals(new BigDecimal("100.00"), p.getSubtotal());
        assertEquals(new BigDecimal("100.00"), p.getValorTotal());
        assertEquals(1, p.getItens().size());
        assertEquals(new BigDecimal("50.00"), p.getItens().get(0).getPrecoUnitario());
        assertEquals(1, p.getHistorico().size());

        verify(auditoriaService).registrar(eq(TipoOperacao.CRIACAO_PEDIDO), eq("PEDIDO"), eq("p1"), any(), isNull());
    }

    @Test
    void criarPedidoRejeitaClienteInexistente() {
        lenient().when(clienteRepository.existsById("x")).thenReturn(false);
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("x", List.of(new ItemRequest("pz", 1)), null)));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void criarPedidoRejeitaQuantidadeInvalidaProdutoInativoEPedidoVazio() {
        lenient().when(clienteRepository.existsById("c1")).thenReturn(true);
        lenient().when(produtoRepository.findById("pz")).thenReturn(Optional.of(produto("pz", "Calabresa", 50.0, true)));
        lenient().when(produtoRepository.findById("off")).thenReturn(Optional.of(produto("off", "Fora", 10.0, false)));

        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of(new ItemRequest("pz", 0)), null)));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of(new ItemRequest("pz", -3)), null)));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of(new ItemRequest("off", 1)), null)));
        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(new PedidoRequest("c1", List.of(), null)));
        verify(pedidoRepository, never()).save(any());
    }

    // ---------- RF03: transições ----------

    @Test
    void fluxoCompletoComSaidaRegistrandoResponsavelEDataHora() {
        Pedido p = pedidoCriado();

        service.avancarStatus("p1", StatusPedido.EM_PREPARACAO);
        service.avancarStatus("p1", StatusPedido.PRONTO);
        assertNull(p.getDataHoraSaida());

        TestUsuarios.autenticar("gerente02", Perfil.GERENTE);
        service.avancarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA);
        assertEquals("gerente02", p.getResponsavelSaida());
        assertEquals(LocalDateTime.of(2026, 10, 14, 19, 0), p.getDataHoraSaida());

        service.avancarStatus("p1", StatusPedido.FINALIZADO);
        assertEquals(StatusPedido.FINALIZADO, p.getStatus());
        assertEquals(5, p.getHistorico().size()); // RECEBIDO + 4 mudanças
    }

    @Test
    void retiradaTambemRegistraSaida() {
        Pedido p = pedidoNoStatus(StatusPedido.PRONTO);
        service.avancarStatus("p1", StatusPedido.RETIRADO);
        assertNotNull(p.getDataHoraSaida());
        assertEquals("gerente01", p.getResponsavelSaida());
    }

    @Test
    void bloqueiaTransicoesInvalidas() {
        pedidoCriado();
        // RECEBIDO -> PRONTO / FINALIZADO / SAIU (pulando etapas)
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.PRONTO));
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.FINALIZADO));
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.SAIU_PARA_ENTREGA));
        // mesmo estado
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.RECEBIDO));
    }

    @Test
    void naoFinalizaPedidoEmPreparacaoNemVoltaStatus() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.FINALIZADO));
        assertThrows(TransicaoInvalidaException.class, () -> service.avancarStatus("p1", StatusPedido.RECEBIDO));
    }

    @Test
    void statusCancelamentoNaoPassaPeloEndpointDeStatus() {
        pedidoCriado();
        assertThrows(RegraNegocioException.class, () -> service.avancarStatus("p1", StatusPedido.CANCELADO));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "CANCELADO"));
        assertThrows(RegraNegocioException.class, () -> service.atualizarStatus("p1", "INEXISTENTE"));
    }

    @Test
    void pedidoCanceladoNaoAceitaNenhumaAlteracao() {
        pedidoNoStatus(StatusPedido.CANCELADO);
        for (StatusPedido s : StatusPedido.values()) {
            assertThrows(RegraNegocioException.class, () -> service.avancarStatus("p1", s));
        }
        assertThrows(TransicaoInvalidaException.class, () -> service.aplicarDesconto("p1", new BigDecimal("5")));
        assertThrows(TransicaoInvalidaException.class, () -> service.cancelar("p1", "tentando de novo"));
    }

    // ---------- RF04: desconto ----------

    @Test
    void descontoValidoRecalculaTotalEAudita() {
        Pedido p = pedidoCriado();

        service.aplicarDesconto("p1", new BigDecimal("10"));

        assertEquals(new BigDecimal("10.00"), p.getDescontoPercentual());
        assertEquals(new BigDecimal("10.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), p.getValorTotal());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("PEDIDO"), eq("p1"),
                captor.capture(), isNull());
        AlteracaoCampo total = captor.getValue().stream()
                .filter(a -> a.getCampo().equals("valorTotal")).findFirst().orElseThrow();
        assertEquals("100.00", total.getValorAnterior());
        assertEquals("90.00", total.getValorNovo());
    }

    @Test
    void descontoLimitadoA20PorCento() {
        Pedido p = pedidoCriado();
        service.aplicarDesconto("p1", new BigDecimal("20"));
        assertEquals(new BigDecimal("80.00"), p.getValorTotal());

        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("20.01")));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("50")));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", BigDecimal.ZERO));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", new BigDecimal("-5")));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto("p1", null));
        assertEquals(new BigDecimal("80.00"), p.getValorTotal()); // nada mudou nas tentativas inválidas
    }

    @Test
    void descontoNaoAcumulaSubstituiOAnterior() {
        Pedido p = pedidoCriado();
        service.aplicarDesconto("p1", new BigDecimal("15"));
        service.aplicarDesconto("p1", new BigDecimal("5"));
        assertEquals(new BigDecimal("95.00"), p.getValorTotal());
    }

    @Test
    void descontoSomenteAntesDaSaida() {
        for (StatusPedido s : List.of(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO, StatusPedido.FINALIZADO)) {
            pedidoNoStatus(s);
            assertThrows(TransicaoInvalidaException.class, () -> service.aplicarDesconto("p1", new BigDecimal("10")));
        }
        for (StatusPedido s : List.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO)) {
            pedidoNoStatus(s);
            service.aplicarDesconto("p1", new BigDecimal("10")); // não lança
        }
    }

    // ---------- RF04: cancelamento ----------

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoCriado();
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", null));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "   "));
        assertThrows(RegraNegocioException.class, () -> service.cancelar("p1", "ab"));
    }

    @Test
    void cancelamentoPreservaDadosEAuditaComJustificativa() {
        Pedido p = pedidoNoStatus(StatusPedido.PRONTO);

        service.cancelar("p1", "  Cliente desistiu  ");

        assertEquals(StatusPedido.CANCELADO, p.getStatus());
        assertEquals("Cliente desistiu", p.getMotivoCancelamento());
        assertEquals("gerente01", p.getCanceladoPor());
        assertNotNull(p.getDataHoraCancelamento());
        // dados históricos preservados
        assertEquals(1, p.getItens().size());
        assertEquals(new BigDecimal("100.00"), p.getValorTotal());
        assertEquals("gerente01", p.getResponsavelRegistro());

        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("PEDIDO"), eq("p1"),
                any(), eq("Cliente desistiu"));
    }

    @Test
    void naoCancelaDepoisDaSaida() {
        for (StatusPedido s : List.of(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO, StatusPedido.FINALIZADO)) {
            Pedido p = pedidoNoStatus(s);
            assertThrows(TransicaoInvalidaException.class, () -> service.cancelar("p1", "tarde demais"));
            assertEquals(s, p.getStatus());
        }
    }

    // ---------- alteração de itens ----------

    @Test
    void alteraItensSomenteEmRecebidoEMantemDescontoRecalculando() {
        Pedido p = pedidoCriado();
        service.aplicarDesconto("p1", new BigDecimal("10"));

        service.alterarPedido("p1", new PedidoRequest(null, List.of(new ItemRequest("pz", 4)), "sem cebola"));
        assertEquals(new BigDecimal("200.00"), p.getSubtotal());
        assertEquals(new BigDecimal("180.00"), p.getValorTotal()); // 10% continua valendo
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_PEDIDO), eq("PEDIDO"), eq("p1"), any(), isNull());

        p.setStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(TransicaoInvalidaException.class, () -> service.alterarPedido("p1",
                new PedidoRequest(null, List.of(new ItemRequest("pz", 1)), null)));
    }

    @Test
    void recalcularValoresArredondaCorretamente() {
        Pedido p = new Pedido();
        p.setItens(List.of(new com.umc.pediupatrao.entity.ItemPedido("a", "Item", 3, new BigDecimal("33.33"))));
        p.setDescontoPercentual(new BigDecimal("7.50"));
        PedidoService.recalcularValores(p);
        assertEquals(new BigDecimal("99.99"), p.getSubtotal());
        assertEquals(new BigDecimal("7.50"), p.getValorDesconto());
        assertEquals(new BigDecimal("92.49"), p.getValorTotal());
        assertTrue(p.getValorTotal().compareTo(p.getSubtotal()) < 0);
    }
}