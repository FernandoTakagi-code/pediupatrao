package com.umc.pediupatrao;

import com.umc.pediupatrao.entity.Perfil;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilitário de testes unitários: define o usuário "logado" sem subir o Spring. */
public final class TestUsuarios {

    private TestUsuarios() {
    }

    public static void autenticar(String username, Perfil perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority(perfil.authority()))));
    }

    public static void limpar() {
        SecurityContextHolder.clearContext();
    }
}