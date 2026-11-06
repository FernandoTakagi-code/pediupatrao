package com.umc.pediupatrao.security;

import com.umc.pediupatrao.config.*;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class, AppConfig.class})
class DashboardHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean MongoTemplate mongo;
    @MockitoBean UsuarioDetailsService usuarios;

    @Test void permiteGestaoEDevolveContratoSemCache() throws Exception {
        when(mongo.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());
        for (String perfil : List.of("ADMIN", "GERENTE")) {
            mvc.perform(get("/api/dashboard").with(user("gestor").roles(perfil)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.indicadores.pedidos").value(0))
                    .andExpect(jsonPath("$.vendas.length()").value(30));
        }
    }

    @Test void bloqueiaAnonimoAtendenteEEscrita() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard").with(user("a").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/dashboard").with(user("g").roles("GERENTE")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(mongo);
    }

    @Test void validaBindingERegrasDoPeriodo() throws Exception {
        for (String query : List.of("inicio=abc", "status=INVALIDO", "limite=abc", "agrupamento=ANO", "limite=21",
                "inicio=2026-01-05&fim=2026-01-01", "inicio=2020-01-01&fim=2026-01-01")) {
            mvc.perform(get("/api/dashboard?" + query).with(user("g").roles("GERENTE")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").exists());
        }
        verifyNoInteractions(mongo);
    }
}
