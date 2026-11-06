package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardResponse;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<DashboardResponse> consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "DIA") String agrupamento,
            @RequestParam(defaultValue = "5") int limite) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.consultar(inicio, fim, status, agrupamento, limite));
    }
}
