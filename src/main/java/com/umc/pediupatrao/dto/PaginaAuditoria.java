package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;

public record PaginaAuditoria(List<RegistroAuditoria> registros, int pagina, int totalPaginas, long total) {
}