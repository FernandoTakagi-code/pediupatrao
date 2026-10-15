package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.PaginaAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria (ADMIN e GERENTE). Só existe GET: não há endpoint para criar,
 * alterar ou excluir registros (e o SecurityConfig nega qualquer outro método).
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public PaginaAuditoria consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        Page<RegistroAuditoria> p = auditoriaService.consultar(entidade, entidadeId, usuario, pagina, tamanho);
        return new PaginaAuditoria(p.getContent(), p.getNumber(), p.getTotalPages(), p.getTotalElements());
    }
}