package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock UsuarioRepository usuarioRepository;

    private Usuario usuario(String role) {
        Usuario u = new Usuario();
        u.setUsername("fulano");
        u.setPassword("hash");
        u.setRole(role);
        return u;
    }

    @Test
    void cadaPerfilVirraSuaPropriaAuthority() {
        UsuarioDetailsService service = new UsuarioDetailsService(usuarioRepository);
        for (String perfil : new String[] {"ADMIN", "GERENTE", "ATENDENTE"}) {
            when(usuarioRepository.findByUsername("fulano")).thenReturn(Optional.of(usuario(perfil)));
            UserDetails d = service.loadUserByUsername("fulano");
            assertEquals(1, d.getAuthorities().size());
            assertEquals("ROLE_" + perfil, d.getAuthorities().iterator().next().getAuthority());
            assertTrue(d.isEnabled());
        }
    }

    @Test
    void perfilAusenteOuInvalidoDesabilitaAConta() {
        UsuarioDetailsService service = new UsuarioDetailsService(usuarioRepository);
        for (String perfil : new String[] {null, "USER", "ROOT"}) {
            when(usuarioRepository.findByUsername("fulano")).thenReturn(Optional.of(usuario(perfil)));
            UserDetails d = service.loadUserByUsername("fulano");
            assertFalse(d.isEnabled());
            assertTrue(d.getAuthorities().isEmpty());
        }
    }

    @Test
    void usuarioInexistente() {
        UsuarioDetailsService service = new UsuarioDetailsService(usuarioRepository);
        when(usuarioRepository.findByUsername("x")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x"));
    }
}