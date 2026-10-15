package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API de usuários: exclusiva do ADMIN (SecurityConfig + @PreAuthorize no serviço). Nunca devolve senha. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarTodos() {
        List<UsuarioResponse> lista = new ArrayList<>();
        usuarioService.listarTodos().forEach(u -> lista.add(UsuarioResponse.de(u)));
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(u -> ResponseEntity.ok(UsuarioResponse.de(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criarUsuario(@RequestBody UsuarioRequest request) {
        Usuario entrada = new Usuario();
        entrada.setUsername(request.username());
        entrada.setPassword(request.password());
        entrada.setRole(request.role());
        Usuario salvo = usuarioService.salvarUsuario(entrada);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> atualizarUsuario(@PathVariable String id,
                                                            @RequestBody UsuarioRequest request) {
        Usuario dados = new Usuario();
        dados.setUsername(request.username());
        dados.setPassword(request.password());
        dados.setRole(request.role());
        return ResponseEntity.ok(UsuarioResponse.de(usuarioService.atualizarUsuario(id, dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.deletarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}