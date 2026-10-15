package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de pedidos. As permissões por perfil estão no SecurityConfig e em @PreAuthorize no serviço.
 * O corpo da requisição nunca é convertido direto em Pedido: total, status e responsável são do servidor.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody PedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(request));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPedido(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public Pedido alterarPedido(@PathVariable String id, @RequestBody PedidoRequest request) {
        return pedidoService.alterarPedido(id, request);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoRequest request) {
        return pedidoService.aplicarDesconto(id, request.percentual());
    }

    @PostMapping("/{id}/cancelamento")
    public Pedido cancelar(@PathVariable String id, @RequestBody CancelamentoRequest request) {
        return pedidoService.cancelar(id, request.justificativa());
    }
}