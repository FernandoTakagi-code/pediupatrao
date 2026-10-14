package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Serviço de auditoria. Só existem duas operações: registrar (insert) e consultar (leitura).
 * Não há atualização nem exclusão de registros.
 */
@Service
public class AuditoriaService {

    /** Rede de segurança: campos com estes nomes jamais são gravados na auditoria. */
    private static final Set<String> CAMPOS_PROIBIDOS = Set.of("password", "senha", "token", "hash", "credencial");

    private final MongoTemplate mongoTemplate;
    private final AuditoriaRepository auditoriaRepository;
    private final Clock clock;

    public AuditoriaService(MongoTemplate mongoTemplate, AuditoriaRepository auditoriaRepository, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.auditoriaRepository = auditoriaRepository;
        this.clock = clock;
    }

    /** Registra um evento usando o usuário/perfil autenticados no momento da operação. */
    public RegistroAuditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
                                       List<AlteracaoCampo> alteracoes, String justificativa) {
        List<AlteracaoCampo> seguras = new ArrayList<>();
        if (alteracoes != null) {
            for (AlteracaoCampo a : alteracoes) {
                if (!CAMPOS_PROIBIDOS.contains(a.getCampo().toLowerCase(Locale.ROOT))) {
                    seguras.add(a);
                }
            }
        }
        RegistroAuditoria registro = new RegistroAuditoria(
                new ObjectId().toHexString(),
                LocalDateTime.now(clock),
                SecurityUtils.username(),
                SecurityUtils.perfil().name(),
                operacao,
                entidade,
                entidadeId,
                seguras,
                justificativa);
        return mongoTemplate.insert(registro);
    }

    /** Consulta paginada (mais recentes primeiro). Somente ADMIN e GERENTE. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<RegistroAuditoria> consultar(String entidade, String entidadeId, String usuario,
                                             int pagina, int tamanho) {
        RegistroAuditoria filtro = new RegistroAuditoria(null, null, vazioParaNull(usuario), null, null,
                vazioParaNull(entidade), vazioParaNull(entidadeId), null, null);
        Example<RegistroAuditoria> exemplo = Example.of(filtro, ExampleMatcher.matching().withIgnoreNullValues());
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamanho, 1), 200);
        return auditoriaRepository.findAll(exemplo, PageRequest.of(p, t, Sort.by(Sort.Direction.DESC, "dataHora")));
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Mascara telefone na auditoria (proteção de dado pessoal): mantém só os 4 últimos dígitos. */
    public static String mascararTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 4) + digitos.substring(digitos.length() - 4);
    }
}