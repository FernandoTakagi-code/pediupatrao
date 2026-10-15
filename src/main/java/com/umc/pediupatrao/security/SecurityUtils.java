package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário autenticado (nome e perfil) a partir do contexto do Spring Security. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    private static Authentication autenticacao() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return a;
    }

    /** Nome do usuário autenticado ou null se não houver. */
    public static String usernameOuNull() {
        Authentication a = autenticacao();
        return a == null ? null : a.getName();
    }

    /** Perfil do usuário autenticado ou null se não houver / desconhecido. */
    public static Perfil perfilOuNull() {
        Authentication a = autenticacao();
        if (a == null) {
            return null;
        }
        for (GrantedAuthority ga : a.getAuthorities()) {
            for (Perfil p : Perfil.values()) {
                if (p.authority().equals(ga.getAuthority())) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Usuário autenticado obrigatório (lança exceção se não houver). */
    public static String username() {
        String u = usernameOuNull();
        if (u == null) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }
        return u;
    }

    public static Perfil perfil() {
        Perfil p = perfilOuNull();
        if (p == null) {
            throw new AuthenticationCredentialsNotFoundException("Perfil do usuário não identificado.");
        }
        return p;
    }
}