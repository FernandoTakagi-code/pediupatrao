package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria imutável: não possui setters e a coleção só é acessada
 * para escrita pelo AuditoriaService (insert). Nunca contém senhas ou tokens.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private final String id;
    private final LocalDateTime dataHora;
    private final String usuario;
    private final String perfil;
    private final TipoOperacao operacao;
    private final String entidade;
    private final String entidadeId;
    private final List<AlteracaoCampo> alteracoes;
    private final String justificativa;

    public RegistroAuditoria(String id, LocalDateTime dataHora, String usuario, String perfil,
                             TipoOperacao operacao, String entidade, String entidadeId,
                             List<AlteracaoCampo> alteracoes, String justificativa) {
        this.id = id;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public TipoOperacao getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }
}