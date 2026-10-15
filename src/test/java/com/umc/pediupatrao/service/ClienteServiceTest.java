package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock ClienteRepository clienteRepository;
    @Mock AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, auditoriaService);
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("c1");
            }
            return c;
        });
    }

    private Cliente cliente(String id, String logradouro, String telefone) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setNome("Maria");
        c.setTelefone(telefone);
        c.setLogradouro(logradouro);
        c.setNumero("10");
        c.setCidade("São Paulo");
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<AlteracaoCampo> alteracoesAuditadas(TipoOperacao tipo, String id) {
        ArgumentCaptor<List<AlteracaoCampo>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(tipo), eq("CLIENTE"), eq(id), captor.capture(), isNull());
        return captor.getValue();
    }

    @Test
    void alteracaoDeEnderecoGeraAuditoriaComValorAnteriorENovo() {
        lenient().when(clienteRepository.findById("c1"))
                .thenReturn(Optional.of(cliente("c1", "Rua das Flores", "(11) 99999-1234")));

        service.salvar(cliente("c1", "Av. Brasil", "(11) 99999-1234"));

        List<AlteracaoCampo> alt = alteracoesAuditadas(TipoOperacao.ALTERACAO_CLIENTE, "c1");
        assertEquals(1, alt.size()); // somente o campo que mudou
        assertEquals("logradouro", alt.get(0).getCampo());
        assertEquals("Rua das Flores", alt.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alt.get(0).getValorNovo());
    }

    @Test
    void alteracaoDeTelefoneEAuditadaMascarandoODadoPessoal() {
        lenient().when(clienteRepository.findById("c1"))
                .thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 98888-1111")));

        service.salvar(cliente("c1", "Rua A", "(11) 97777-2222"));

        List<AlteracaoCampo> alt = alteracoesAuditadas(TipoOperacao.ALTERACAO_CLIENTE, "c1");
        assertEquals(1, alt.size());
        assertEquals("telefone", alt.get(0).getCampo());
        assertEquals("*******1111", alt.get(0).getValorAnterior());
        assertEquals("*******2222", alt.get(0).getValorNovo());
        assertFalse(alt.get(0).getValorNovo().contains("97777"));
    }

    @Test
    void semMudancaNaoGeraRegistroDeAuditoria() {
        lenient().when(clienteRepository.findById("c1"))
                .thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 98888-1111")));

        service.salvar(cliente("c1", "Rua A", "(11) 98888-1111"));

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void cadastroNovoEAuditadoComoCriacao() {
        Cliente novo = cliente(null, "Rua B", "(11) 95555-0000");
        service.salvar(novo);

        List<AlteracaoCampo> alt = alteracoesAuditadas(TipoOperacao.CRIACAO_CLIENTE, "c1");
        assertTrue(alt.stream().anyMatch(a -> a.getCampo().equals("logradouro") && a.getValorAnterior() == null
                && "Rua B".equals(a.getValorNovo())));
    }

    @Test
    void preservaEnderecoLegadoQuandoFormularioNaoEnvia() {
        Cliente existente = cliente("c1", "Rua A", "(11) 98888-1111");
        existente.setEndereco("Endereço antigo");
        lenient().when(clienteRepository.findById("c1")).thenReturn(Optional.of(existente));

        Cliente salvo = service.salvar(cliente("c1", "Rua A", "(11) 98888-1111"));

        assertEquals("Endereço antigo", salvo.getEndereco());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void exigeNomeETelefone() {
        Cliente semNome = cliente(null, "Rua", "(11) 90000-0000");
        semNome.setNome("  ");
        assertThrows(RegraNegocioException.class, () -> service.salvar(semNome));
        assertThrows(RegraNegocioException.class, () -> service.salvar(cliente(null, "Rua", " ")));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void exclusaoEAuditada() {
        lenient().when(clienteRepository.findById("c1"))
                .thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 98888-1111")));

        service.excluir("c1");

        verify(clienteRepository).deleteById("c1");
        List<AlteracaoCampo> alt = alteracoesAuditadas(TipoOperacao.EXCLUSAO_CLIENTE, "c1");
        assertTrue(alt.stream().allMatch(a -> a.getValorNovo() == null));
    }
}