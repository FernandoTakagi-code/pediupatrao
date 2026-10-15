package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.security.SecurityUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expõe usuário e perfil às telas apenas para adaptar a interface (menu, botões).
 * A segurança real continua no backend (SecurityConfig e @PreAuthorize).
 */
@ControllerAdvice(assignableTypes = HomeController.class)
public class ViewModelAdvice {

    @ModelAttribute("usuarioAtual")
    public String usuarioAtual() {
        return SecurityUtils.usernameOuNull();
    }

    @ModelAttribute("perfilAtual")
    public String perfilAtual() {
        Perfil p = SecurityUtils.perfilOuNull();
        return p == null ? null : p.name();
    }

    @ModelAttribute("ehAdmin")
    public boolean ehAdmin() {
        return SecurityUtils.perfilOuNull() == Perfil.ADMIN;
    }

    @ModelAttribute("ehGerente")
    public boolean ehGerente() {
        return SecurityUtils.perfilOuNull() == Perfil.GERENTE;
    }

    @ModelAttribute("ehAtendente")
    public boolean ehAtendente() {
        return SecurityUtils.perfilOuNull() == Perfil.ATENDENTE;
    }
}