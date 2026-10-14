package com.umc.pediupatrao.entity;

import java.util.Optional;

/**
 * Perfis de acesso do sistema. O nome do enum é gravado em Usuario.role.
 */
public enum Perfil {
    ADMIN,
    GERENTE,
    ATENDENTE;

    /** Authority usada pelo Spring Security (hasRole('GERENTE') => ROLE_GERENTE). */
    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<Perfil> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Perfil.valueOf(valor.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}