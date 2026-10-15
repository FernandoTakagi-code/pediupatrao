package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.TestUsuarios;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock AuditoriaRepository auditoriaRepository;

    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new AuditoriaService(mongoTemplate, auditoriaRepository, clock);
        TestUsuarios.autenticar("atendente01", Perfil.ATENDENTE);
        lenient().when(mongoTemplate.insert(any(RegistroAuditoria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TestUsuarios.limpar();
    }

    @Test
    void registraUsuarioPerfilDataHoraEntidadeECampos() {
        service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1",
                List.of(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil")), null);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        RegistroAuditoria r = captor.getValue();

        assertEquals("atendente01", r.getUsuario());
        assertEquals("ATENDENTE", r.getPerfil());
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 35), r.getDataHora()); // exemplo do enunciado: 23/09/2026 14:35
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, r.getOperacao());
        assertEquals("CLIENTE", r.getEntidade());
        assertEquals("c1", r.getEntidadeId());
        assertEquals("logradouro", r.getAlteracoes().get(0).getCampo());
        assertEquals("Rua das Flores", r.getAlteracoes().get(0).getValorAnterior());
        assertEquals("Av. Brasil", r.getAlteracoes().get(0).getValorNovo());
        assertNull(r.getJustificativa());
    }

    @Test
    void nuncaGravaCamposDeCredencial() {
        service.registrar(TipoOperacao.ALTERACAO_USUARIO, "USUARIO", "u1", List.of(
                new AlteracaoCampo("password", "hash-antigo", "hash-novo"),
                new AlteracaoCampo("Senha", "a", "b"),
                new AlteracaoCampo("token", "t1", "t2"),
                new AlteracaoCampo("perfil", "ATENDENTE", "GERENTE")), null);

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(mongoTemplate).insert(captor.capture());
        List<AlteracaoCampo> gravados = captor.getValue().getAlteracoes();
        assertEquals(1, gravados.size());
        assertEquals("perfil", gravados.get(0).getCampo());
    }

    @Test
    void mascaraTelefoneMantendoSomenteOsQuatroUltimosDigitos() {
        assertEquals("*******4321", AuditoriaService.mascararTelefone("(11) 98765-4321"));
        assertEquals("****", AuditoriaService.mascararTelefone("123"));
        assertNull(AuditoriaService.mascararTelefone(null));
        assertTrue(!AuditoriaService.mascararTelefone("(11) 98765-4321").contains("98765"));
    }
}