package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in: requer MongoDB local. Cada execução usa e remove SOMENTE um banco com UUID próprio. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "pediupatrao.seed-demo=false")
@EnabledIfEnvironmentVariable(named = "MONGODB_INTEGRATION", matches = "true")
class DashboardMongoIntegrationTest {
    private static final String DATABASE = "pediupatrao_dashboard_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired MongoTemplate mongo;
    @Autowired MockMvc mvc;

    @DynamicPropertySource static void banco(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/?serverSelectionTimeoutMS=5000");
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @BeforeEach void limparPedidos() { mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Pedido.class); }

    @AfterAll static void limparBanco(@Autowired MongoTemplate mongo) {
        if (!mongo.getDb().getName().equals(DATABASE)) throw new IllegalStateException("Banco de teste inesperado");
        mongo.getDb().drop();
    }

    @Test void mongoRealRespeitaLimitesConverteLegadosEAlimentaHttp() throws Exception {
        LocalDate dia = LocalDate.of(2026, 1, 15);
        Pedido primeiro = DashboardServiceTest.pedido(StatusPedido.FINALIZADO, "45", dia);
        primeiro.setDataHoraEntrada(dia.atStartOfDay());
        mongo.insert(primeiro);
        Pedido ultimo = DashboardServiceTest.pedido(StatusPedido.FINALIZADO, "55", dia);
        ultimo.setDataHoraEntrada(dia.atTime(23, 59, 59));
        mongo.insert(ultimo);
        mongo.insert(DashboardServiceTest.pedido(StatusPedido.FINALIZADO, "999", dia.plusDays(1)));
        mongo.insert(DashboardServiceTest.pedido(StatusPedido.FINALIZADO, "999", dia.minusDays(1)));
        mongo.insert(DashboardServiceTest.pedido(StatusPedido.CANCELADO, "80", dia));
        // Documento legado com nome antigo de status: a conversão deve ocorrer antes do filtro.
        Document legado = new Document("status", "ENTREGUE")
                .append("dataHoraEntrada", mongo.getConverter().convertToMongoType(dia.atStartOfDay()))
                .append("valorTotal", "20.00").append("itens", null);
        mongo.getCollection("pedidos").insertOne(legado);
        mvc.perform(get("/api/dashboard").param("inicio", dia.toString()).param("fim", dia.toString())
                        .param("status", "FINALIZADO").with(user("gestor").roles("GERENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.pedidos").value(3))
                .andExpect(jsonPath("$.indicadores.faturamento").value(120.00))
                .andExpect(jsonPath("$.indicadores.ticketMedio").value(40.00))
                .andExpect(jsonPath("$.produtos[0].quantidade").value(4))
                .andExpect(jsonPath("$.pedidosInconsistentes").value(1));
    }

    @Test void templateDashboardRenderizaComMenuEControles() throws Exception {
        mvc.perform(get("/dashboard").with(user("gestor").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("layout"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dashboard-filters")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/dashboard.js")));
    }
}
