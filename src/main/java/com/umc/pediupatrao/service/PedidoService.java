package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.EventoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.exception.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negócio dos pedidos. A autorização por perfil é aplicada aqui (além das regras de URL
 * do SecurityConfig) com @PreAuthorize, de modo que a restrição vale mesmo fora do fluxo web.
 */
@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");
    static final int MAX_ITENS = 50;
    static final int MAX_QUANTIDADE_POR_ITEM = 100;
    static final int TAMANHO_MINIMO_JUSTIFICATIVA = 5;
    private static final String ENTIDADE = "PEDIDO";
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, AuditoriaService auditoriaService, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consultas

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return obter(id);
    }

    // ------------------------------------------------------------------ RF02: criação

    /** Registra um pedido novo: status RECEBIDO, responsável e data/hora de entrada vêm do servidor. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        if (request.clienteId() == null || request.clienteId().isBlank()
                || !clienteRepository.existsById(request.clienteId())) {
            throw new RegraNegocioException("Cliente inválido ou não encontrado.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(request.clienteId());
        pedido.setObservacao(limpar(request.observacao()));
        pedido.setItens(montarItens(request.itens()));
        pedido.setDescontoPercentual(BigDecimal.ZERO.setScale(2));
        recalcularValores(pedido);

        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = SecurityUtils.username();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setResponsavelRegistro(usuario);
        pedido.setDataHoraEntrada(agora);
        pedido.getHistorico().add(new EventoStatus(StatusPedido.RECEBIDO, usuario, agora));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CRIACAO_PEDIDO, ENTIDADE, salvo.getId(),
                AlteracaoCampo.diferencas(new LinkedHashMap<>(), retrato(salvo)), null);
        return salvo;
    }

    /** Altera cliente/itens/observação. Só é possível enquanto o pedido está RECEBIDO. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarPedido(String id, PedidoRequest request) {
        Pedido pedido = obter(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new TransicaoInvalidaException(
                    "Só é possível alterar itens de pedidos com status RECEBIDO (atual: " + pedido.getStatus() + ").");
        }
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        Map<String, String> antes = retrato(pedido);

        if (request.clienteId() != null && !request.clienteId().isBlank()) {
            if (!clienteRepository.existsById(request.clienteId())) {
                throw new RegraNegocioException("Cliente inválido ou não encontrado.");
            }
            pedido.setClienteId(request.clienteId());
        }
        pedido.setObservacao(limpar(request.observacao()));
        pedido.setItens(montarItens(request.itens()));
        recalcularValores(pedido); // mantém o desconto percentual já autorizado, recalculando os valores

        Pedido salvo = pedidoRepository.save(pedido);
        List<AlteracaoCampo> mudancas = AlteracaoCampo.diferencas(antes, retrato(salvo));
        if (!mudancas.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, salvo.getId(), mudancas, null);
        }
        return salvo;
    }

    // ------------------------------------------------------------------ RF03: transições

    /** Compatibilidade com a assinatura antiga (status como texto). */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String status) {
        StatusPedido novo;
        try {
            novo = StatusPedido.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("Status inválido: " + status);
        }
        return avancarStatus(id, novo);
    }

    /** Avança o pedido no fluxo obrigatório, registrando saída (data/hora e responsável) quando aplicável. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido avancarStatus(String id, StatusPedido novo) {
        Pedido pedido = obter(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Pedido cancelado não pode ter o status alterado.");
        }
        if (novo == null) {
            throw new RegraNegocioException("Status não informado.");
        }
        if (novo == StatusPedido.CANCELADO) {
            throw new RegraNegocioException(
                    "Para cancelar um pedido use a operação de cancelamento, que exige justificativa.");
        }
        if (!atual.proximos().contains(novo)) {
            throw new TransicaoInvalidaException("Transição inválida: " + atual + " -> " + novo + ".");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = SecurityUtils.username();
        Map<String, String> antes = retrato(pedido);

        pedido.setStatus(novo);
        if (novo.isSaida()) {
            pedido.setResponsavelSaida(usuario);
            pedido.setDataHoraSaida(agora);
        }
        pedido.getHistorico().add(new EventoStatus(novo, usuario, agora));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS_PEDIDO, ENTIDADE, salvo.getId(),
                AlteracaoCampo.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    // ------------------------------------------------------------------ RF04: desconto e cancelamento

    /** Aplica desconto percentual (máx. 20%) antes da saída. O novo desconto substitui o anterior. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        Pedido pedido = obter(id);
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Informe um percentual de desconto maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto é limitado a "
                    + DESCONTO_MAXIMO_PERCENTUAL.toPlainString() + "% do valor do pedido.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("Pedido cancelado não pode receber desconto.");
        }
        if (pedido.getStatus() == null || !pedido.getStatus().isAntesDaSaida()) {
            throw new TransicaoInvalidaException(
                    "O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }

        Map<String, String> antes = retrato(pedido);
        pedido.setDescontoPercentual(percentual.setScale(2, RoundingMode.HALF_UP));
        recalcularValores(pedido);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, salvo.getId(),
                AlteracaoCampo.diferencas(antes, retrato(salvo)), null);
        return salvo;
    }

    /** Cancela o pedido (justificativa obrigatória), preservando todos os dados históricos. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = obter(id);
        String motivo = limpar(justificativa);
        if (motivo == null || motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Justificativa obrigatória (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres) para cancelar o pedido.");
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new TransicaoInvalidaException("O pedido já está cancelado.");
        }
        if (pedido.getStatus() == null || !pedido.getStatus().isAntesDaSaida()) {
            throw new TransicaoInvalidaException(
                    "Pedidos que já saíram para entrega/retirada (ou finalizados) não podem ser cancelados.");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        String usuario = SecurityUtils.username();
        Map<String, String> antes = retrato(pedido);

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setMotivoCancelamento(motivo);
        pedido.setCanceladoPor(usuario);
        pedido.setDataHoraCancelamento(agora);
        pedido.getHistorico().add(new EventoStatus(StatusPedido.CANCELADO, usuario, agora));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, salvo.getId(),
                AlteracaoCampo.diferencas(antes, retrato(salvo)), motivo);
        return salvo;
    }

    // ------------------------------------------------------------------ auxiliares

    private Pedido obter(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    /** Monta os itens consultando preço e nome no cadastro de produtos: o preço nunca vem da requisição. */
    private List<ItemPedido> montarItens(List<ItemRequest> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa ter ao menos um item.");
        }
        if (solicitados.size() > MAX_ITENS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAX_ITENS + " itens.");
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemRequest req : solicitados) {
            if (req == null || req.produtoId() == null || req.produtoId().isBlank()) {
                throw new RegraNegocioException("Produto não informado em um dos itens.");
            }
            Integer qtd = req.quantidade();
            if (qtd == null || qtd < 1 || qtd > MAX_QUANTIDADE_POR_ITEM) {
                throw new RegraNegocioException(
                        "Quantidade inválida (use de 1 a " + MAX_QUANTIDADE_POR_ITEM + ").");
            }
            Produto produto = produtoRepository.findById(req.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + req.produtoId()));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("Produto inativo: " + produto.getNome());
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("Produto sem preço válido: " + produto.getNome());
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), qtd, preco));
        }
        return itens;
    }

    /** Calcula subtotal, valor do desconto e total a partir dos itens e do percentual autorizado. */
    static void recalcularValores(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            subtotal = subtotal.add(item.getSubtotal());
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = pedido.getDescontoPercentual() == null
                ? BigDecimal.ZERO : pedido.getDescontoPercentual();
        BigDecimal desconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    /** Retrato dos campos auditáveis do pedido (campo -> valor em texto). */
    private static Map<String, String> retrato(Pedido p) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clienteId", p.getClienteId());
        m.put("status", p.getStatus() == null ? null : p.getStatus().name());
        m.put("itens", p.getItens().isEmpty() ? null
                : p.getItens().stream().map(ItemPedido::resumo).collect(Collectors.joining("; ")));
        m.put("observacao", p.getObservacao());
        m.put("subtotal", texto(p.getSubtotal()));
        m.put("descontoPercentual", texto(p.getDescontoPercentual()));
        m.put("valorDesconto", texto(p.getValorDesconto()));
        m.put("valorTotal", texto(p.getValorTotal()));
        m.put("responsavelSaida", p.getResponsavelSaida());
        m.put("dataHoraSaida", p.getDataHoraSaida() == null ? null : p.getDataHoraSaida().toString());
        m.put("motivoCancelamento", p.getMotivoCancelamento());
        m.values().removeIf(v -> v == null);
        return m;
    }

    private static String texto(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}