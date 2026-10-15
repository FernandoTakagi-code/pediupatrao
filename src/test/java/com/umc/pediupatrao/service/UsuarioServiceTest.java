package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.TestUsuarios;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock AuditoriaService auditoriaService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, encoder, auditoriaService);
        TestUsuarios.autenticar("admin", Perfil.ADMIN);
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("novo");
            }
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        TestUsuarios.limpar();
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setPassword("hash");
        return u;
    }

    @Test
    void criaUsuarioComSenhaCriptografadaESemSenhaNaAuditoria() {
        Usuario entrada = new Usuario();
        entrada.setUsername("joao.silva");
        entrada.setPassword("segredo123");
        entrada.setRole("GERENTE");
        lenient().when(usuarioRepository.findByUsername("joao.silva")).thenReturn(Optional.empty());

        Usuario salvo = service.salvarUsuario(entrada);

        assertNotEquals("segredo123", salvo.getPassword());
        assertEquals(true, encoder.matches("segredo123", salvo.getPassword()));
        assertEquals("GERENTE", salvo.getRole());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.CRIACAO_USUARIO), eq("USUARIO"), anyString(),
                captor.capture(), any());
        captor.getValue().forEach(a -> {
            assertNotEquals("segredo123", a.getValorNovo());
            assertNotEquals(salvo.getPassword(), a.getValorNovo());
        });
    }

    @Test
    void rejeitaPerfilInvalidoComoUSEROuSuperAdmin() {
        for (String perfilInvalido : new String[] {"USER", "SUPERADMIN", "", null, "ROLE_ADMIN"}) {
            Usuario u = new Usuario();
            u.setUsername("fulano");
            u.setPassword("segredo123");
            u.setRole(perfilInvalido);
            assertThrows(RegraNegocioException.class, () -> service.salvarUsuario(u));
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void rejeitaUsernameDuplicadoESenhaCurta() {
        lenient().when(usuarioRepository.findByUsername("existente")).thenReturn(Optional.of(usuario("1", "existente", "ATENDENTE")));
        Usuario dup = new Usuario();
        dup.setUsername("existente");
        dup.setPassword("segredo123");
        dup.setRole("ATENDENTE");
        assertThrows(RegraNegocioException.class, () -> service.salvarUsuario(dup));

        Usuario curta = new Usuario();
        curta.setUsername("novo.usuario");
        curta.setPassword("123");
        curta.setRole("ATENDENTE");
        assertThrows(RegraNegocioException.class, () -> service.salvarUsuario(curta));
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        lenient().when(usuarioRepository.findById("1")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN")));
        Usuario dados = usuario(null, "admin", "ATENDENTE");
        dados.setPassword(null);
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("1", dados));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void alteraPerfilDeOutroUsuarioEAudita() {
        Usuario alvo = usuario("2", "maria", "ATENDENTE");
        lenient().when(usuarioRepository.findById("2")).thenReturn(Optional.of(alvo));
        Usuario dados = new Usuario();
        dados.setRole("GERENTE");

        service.atualizarUsuario("2", dados);

        assertEquals("GERENTE", alvo.getRole());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_USUARIO), eq("USUARIO"), eq("2"),
                captor.capture(), any());
        assertEquals("ATENDENTE", captor.getValue().get(0).getValorAnterior());
        assertEquals("GERENTE", captor.getValue().get(0).getValorNovo());
    }

    @Test
    void usernameNaoPodeSerAlterado() {
        lenient().when(usuarioRepository.findById("2")).thenReturn(Optional.of(usuario("2", "maria", "ATENDENTE")));
        Usuario dados = new Usuario();
        dados.setUsername("outro.nome");
        assertThrows(RegraNegocioException.class, () -> service.atualizarUsuario("2", dados));
    }

    @Test
    void naoExcluiAPropriaContaNemOUltimoAdmin() {
        lenient().when(usuarioRepository.findById("1")).thenReturn(Optional.of(usuario("1", "admin", "ADMIN")));
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("1")); // própria conta

        lenient().when(usuarioRepository.findById("9")).thenReturn(Optional.of(usuario("9", "outro.admin", "ADMIN")));
        lenient().when(usuarioRepository.countByRole("ADMIN")).thenReturn(1L);
        assertThrows(RegraNegocioException.class, () -> service.deletarUsuario("9")); // último admin

        verify(usuarioRepository, never()).deleteById(anyString());
    }
}