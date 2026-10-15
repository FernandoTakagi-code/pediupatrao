package com.umc.pediupatrao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Autorização por perfil no BACKEND (ADMIN, GERENTE, ATENDENTE). Esconder botões nas telas é
 * apenas conveniência: toda rota abaixo é protegida aqui e, nas operações críticas,
 * também por @PreAuthorize nos serviços.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /** Requisições da API (/api/**) recebem 401/403 em JSON em vez de redirecionamento para o login. */
    private static final RequestMatcher REQUISICAO_API = request -> request.getRequestURI().startsWith("/api/");

    /**
     * CSRF fica ATIVO para o navegador (sessão + formulários). Clientes de script/teste que
     * enviam explicitamente "Authorization: Basic ..." (curl, Postman) não dependem de cookie
     * de sessão e por isso não ficam sujeitos a CSRF.
     */
    private static final RequestMatcher REQUISICAO_COM_BASIC_AUTH = request -> {
        String header = request.getHeader("Authorization");
        return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(REQUISICAO_COM_BASIC_AUTH))
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), REQUISICAO_API)
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        // públicos
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // usuários e perfis: somente ADMIN (API e telas)
                        .requestMatchers("/api/usuarios", "/api/usuarios/**", "/usuarios", "/usuarios/**", "/admin").hasRole(ADMIN)

                        // auditoria: consulta somente leitura para ADMIN e GERENTE; qualquer escrita é negada
                        .requestMatchers(HttpMethod.GET, "/api/auditoria", "/api/auditoria/**", "/auditoria", "/auditoria/**")
                        .hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/api/auditoria", "/api/auditoria/**", "/auditoria", "/auditoria/**").denyAll()

                        // pedidos - operações exclusivas do GERENTE
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/pedidos/*/status", "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole(GERENTE)
                        // pedidos - registro e alteração de itens
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/pedidos/novo").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*").hasAnyRole(GERENTE, ATENDENTE)
                        // pedidos - consulta
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/*", "/pedidos").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/api/pedidos", "/api/pedidos/**", "/pedidos", "/pedidos/**").denyAll()

                        // clientes: ATENDENTE cadastra/atualiza; GERENTE consulta e exclui; ADMIN não acessa
                        .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/*", "/clientes").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/clientes", "/clientes/salvar", "/clientes/editar/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/*").hasRole(GERENTE)
                        .requestMatchers("/api/clientes", "/api/clientes/**", "/clientes", "/clientes/**").denyAll()

                        // produtos: todos consultam; ADMIN e GERENTE alteram
                        .requestMatchers(HttpMethod.GET, "/api/produtos", "/api/produtos/*", "/produtos").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/api/produtos", "/api/produtos/**", "/produtos", "/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        // demais rotas (home, configurações) exigem apenas login
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }

    /** Registra a tentativa de acesso indevido e responde 403 (JSON na API, página de erro nas telas). */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandlerImpl paginaDeErro = new AccessDeniedHandlerImpl();
        ObjectMapper mapper = new ObjectMapper();
        return (request, response, ex) -> {
            String usuario = request.getUserPrincipal() == null ? "anônimo" : request.getUserPrincipal().getName();
            log.warn("Acesso negado: usuário={} método={} uri={}", usuario, request.getMethod(), request.getRequestURI());
            if (REQUISICAO_API.matches(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                mapper.writeValue(response.getWriter(), Map.of(
                        "erro", "Acesso negado: seu perfil não tem permissão para esta operação.",
                        "status", 403));
            } else {
                paginaDeErro.handle(request, response, ex);
            }
        };
    }
}