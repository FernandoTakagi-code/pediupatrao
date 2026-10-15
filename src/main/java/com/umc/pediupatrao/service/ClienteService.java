package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    private static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    /** Exclui o cliente e registra a exclusão (com os dados removidos) na auditoria. */
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                protegerTelefone(AlteracaoCampo.diferencas(retrato(existente), new LinkedHashMap<>())), null);
    }

    /** Salva um cliente novo (sem id) ou atualiza um existente, auditando os campos alterados. */
    public Cliente salvar(Cliente cliente) {
        normalizar(cliente);
        if (cliente.getNome() == null) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (cliente.getTelefone() == null) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }

        if (cliente.getId() == null) {
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(TipoOperacao.CRIACAO_CLIENTE, ENTIDADE, salvo.getId(),
                    protegerTelefone(AlteracaoCampo.diferencas(new LinkedHashMap<>(), retrato(salvo))), null);
            return salvo;
        }

        Cliente existente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        // O formulário não envia o campo legado "endereco": preserva o valor existente.
        if (cliente.getEndereco() == null) {
            cliente.setEndereco(existente.getEndereco());
        }
        Map<String, String> antes = retrato(existente);
        Cliente salvo = clienteRepository.save(cliente);
        List<AlteracaoCampo> mudancas = protegerTelefone(AlteracaoCampo.diferencas(antes, retrato(salvo)));
        if (!mudancas.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), mudancas, null);
        }
        return salvo;
    }

    // ---------- auxiliares ----------

    private static Map<String, String> retrato(Cliente c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", c.getNome());
        m.put("telefone", c.getTelefone());
        m.put("cep", c.getCep());
        m.put("logradouro", c.getLogradouro());
        m.put("numero", c.getNumero());
        m.put("complemento", c.getComplemento());
        m.put("bairro", c.getBairro());
        m.put("cidade", c.getCidade());
        m.put("estado", c.getEstado());
        m.put("endereco", c.getEndereco());
        // Mapas aceitam null, mas removemos nulos para não gerar "null -> null".
        m.values().removeIf(v -> v == null);
        return m;
    }

    /** O telefone é comparado em claro, mas só é gravado mascarado na auditoria. */
    private static List<AlteracaoCampo> protegerTelefone(List<AlteracaoCampo> alteracoes) {
        List<AlteracaoCampo> resultado = new ArrayList<>();
        for (AlteracaoCampo a : alteracoes) {
            if ("telefone".equals(a.getCampo())) {
                resultado.add(new AlteracaoCampo("telefone",
                        AuditoriaService.mascararTelefone(a.getValorAnterior()),
                        AuditoriaService.mascararTelefone(a.getValorNovo())));
            } else {
                resultado.add(a);
            }
        }
        return resultado;
    }

    private static void normalizar(Cliente c) {
        c.setNome(limpar(c.getNome()));
        c.setTelefone(limpar(c.getTelefone()));
        c.setCep(limpar(c.getCep()));
        c.setLogradouro(limpar(c.getLogradouro()));
        c.setNumero(limpar(c.getNumero()));
        c.setComplemento(limpar(c.getComplemento()));
        c.setBairro(limpar(c.getBairro()));
        c.setCidade(limpar(c.getCidade()));
        c.setEstado(limpar(c.getEstado()));
        c.setEndereco(limpar(c.getEndereco()));
    }

    private static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}