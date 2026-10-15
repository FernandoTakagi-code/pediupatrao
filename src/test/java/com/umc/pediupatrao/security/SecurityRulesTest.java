package com.umc.pediupatrao.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.springframework.data.domain.Page;
/**
 * Prova que as restrições estão no BACKEND (filtros do Spring Security): as chamadas HTTP diretas
 * à API são negadas independentemente de qualquer botão escondido na tela.
 */
@WebMvcTest(controllers = {PedidoController.class, ClienteController.class,
        UsuarioController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class SecurityRulesTest {

    private static final String JSON_CANCELAMENTO = "{\"justificativa\":\"Cliente desistiu\"}";
    private static final String JSON_DESCONTO = "{\"percentual\":10}";

    @Autowired MockMvc mvc;

    @MockitoBean PedidoService pedidoService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    // ---------------------------------------------------------------- sem login

    @Test
    void anonimoRecebe401NasApis() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
        // /api/usuarios era público no projeto original; agora exige login
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void anonimoNasTelasEhRedirecionadoParaLogin() throws Exception {
        mvc.perform(get("/pedidos").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/usuarios").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
    }

    // ---------------------------------------------------------------- RF04: cancelar / desconto só GERENTE

    @Test
    void atendenteNaoCancelaNemDaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").exists());
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void adminTambemNaoCancelaNemDaDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isForbidden());
        // idem pelas rotas das telas
        mvc.perform(post("/pedidos/p1/cancelar").with(user("admin").roles("ADMIN")).with(csrf())
                .param("justificativa", "teste teste")).andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/desconto").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .param("percentual", "10")).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void gerentePodeCancelarEDarDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(user("gerente01").roles("GERENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isOk());
        verify(pedidoService).cancelar("p1", "Cliente desistiu");

        mvc.perform(post("/api/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_DESCONTO))
                .andExpect(status().isOk());
        verify(pedidoService).aplicarDesconto("p1", new BigDecimal("10"));
    }

    // ---------------------------------------------------------------- RF03: status só GERENTE

    @Test
    void somenteGerenteAlteraStatus() throws Exception {
        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO")
                .with(user("atendente01").roles("ATENDENTE")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);

        mvc.perform(put("/api/pedidos/p1/status").param("status", "PRONTO")
                .with(user("gerente01").roles("GERENTE")).with(csrf())).andExpect(status().isOk());
        verify(pedidoService).atualizarStatus("p1", "PRONTO");
    }

    // ---------------------------------------------------------------- pedidos: quem registra / consulta

    @Test
    void adminConsultaMasNaoRegistraPedidos() throws Exception {
        mvc.perform(get("/api/pedidos").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"c1\",\"itens\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteRegistraEConsultaPedidos() throws Exception {
        mvc.perform(post("/api/pedidos").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"c1\",\"itens\":[{\"produtoId\":\"p\",\"quantidade\":1}],\"valorTotal\":0.01}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/pedidos").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- RF05: auditoria

    @Test
    void auditoriaSomenteAdminEGerenteEApenasLeitura() throws Exception {
        when(auditoriaService.consultar(any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/auditoria").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());

        // nenhuma escrita/exclusão é aceita, nem pelo ADMIN ou GERENTE
        for (String perfil : new String[] {"ADMIN", "GERENTE"}) {
            mvc.perform(delete("/api/auditoria/abc").with(user("u").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/auditoria/abc").with(user("u").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/auditoria").with(user("u").roles(perfil)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- RF01: usuários só ADMIN

    @Test
    void gerenciarUsuariosEPerfisSomenteAdmin() throws Exception {
        when(usuarioService.listarTodos()).thenReturn(List.of());

        mvc.perform(get("/api/usuarios").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user("gerente01").roles("GERENTE"))).andExpect(status().isForbidden());
        // tentativa de autoelevação: atendente tenta se promover a ADMIN
        mvc.perform(put("/api/usuarios/u1").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/salvar").with(user("gerente01").roles("GERENTE")).with(csrf())
                        .param("username", "novo").param("password", "123456").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);

        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- clientes

    @Test
    void permissoesDeClientes() throws Exception {
        mvc.perform(get("/api/clientes").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());

        String json = "{\"nome\":\"Maria\",\"telefone\":\"(11) 99999-0000\"}";
        mvc.perform(post("/api/clientes").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
        mvc.perform(post("/api/clientes").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/clientes/c1").with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/c1").with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void sessaoSemTokenCsrfEhBloqueada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(user("gerente01").roles("GERENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    void clienteDeScriptComBasicAuthFuncionaEPerfilContinuaValendo() throws Exception {
        String hash = new BCryptPasswordEncoder().encode("gerente123");
        when(usuarioDetailsService.loadUserByUsername("gerente01"))
                .thenReturn(User.withUsername("gerente01").password(hash).authorities("ROLE_GERENTE").build());
        when(usuarioDetailsService.loadUserByUsername("atendente01"))
                .thenReturn(User.withUsername("atendente01").password(hash).authorities("ROLE_ATENDENTE").build());

        mvc.perform(post("/api/pedidos/p1/cancelamento").with(httpBasic("gerente01", "gerente123"))
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO)).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(httpBasic("atendente01", "gerente123"))
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/cancelamento").with(httpBasic("gerente01", "senha-errada"))
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CANCELAMENTO)).andExpect(status().isUnauthorized());
    }
}