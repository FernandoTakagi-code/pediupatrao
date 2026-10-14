package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

/**
 * Repositório SOMENTE LEITURA da auditoria: estende Repository (e não MongoRepository),
 * portanto não expõe save/delete. A gravação é feita apenas por AuditoriaService (insert).
 */
public interface AuditoriaRepository
        extends Repository<RegistroAuditoria, String>, QueryByExampleExecutor<RegistroAuditoria> {
}