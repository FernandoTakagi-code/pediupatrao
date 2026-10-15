package com.umc.pediupatrao.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Segunda camada de proteção: mesmo chamando o serviço diretamente (sem passar pelas rotas),
 * @PreAuthorize impede que perfis não autorizados cancelem, deem desconto ou mudem status.
 */
@SpringJUnitConfig(PedidoServiceMethodSecurityTest.Config.class)
class PedidoServiceMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        PedidoService pedidoService() {
            return new PedidoService(mock(PedidoRepository.class), mock(ClienteRepository.class),
                    mock(ProdutoRepository.class), mock(AuditoriaService.class), Clock.systemUTC());
        }
    }

    @Autowired PedidoService service;

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoPodeCancelarDescontarNemMudarStatus() {
        assertThrows(AccessDeniedException.class, () -> service.cancelar("p1", "motivo válido"));
        assertThrows(AccessDeniedException.class, () -> service.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(AccessDeniedException.class, () -> service.avancarStatus("p1", StatusPedido.EM_PREPARACAO));
        assertThrows(AccessDeniedException.class, () -> service.atualizarStatus("p1", "EM_PREPARACAO"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeCancelarDescontarNemRegistrarPedido() {
        assertThrows(AccessDeniedException.class, () -> service.cancelar("p1", "motivo válido"));
        assertThrows(AccessDeniedException.class, () -> service.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(AccessDeniedException.class, () -> service.criarPedido(new PedidoRequest("c1", List.of(), null)));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteChegaAteARegraDeNegocio() {
        // autorizado: o erro agora é de negócio (pedido inexistente), não de permissão
        assertThrows(RecursoNaoEncontradoException.class, () -> service.cancelar("inexistente", "motivo válido"));
    }
}