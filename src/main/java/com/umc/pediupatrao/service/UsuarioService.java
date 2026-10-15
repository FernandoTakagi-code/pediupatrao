package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.SecurityUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Gestão de usuários e perfis. Apenas ADMIN pode cadastrar, alterar perfis ou excluir usuários.
 * Proteções: nome de usuário imutável, sem autoelevação/alteração do próprio perfil,
 * sem exclusão da própria conta e sem remover o último ADMIN.
 */
@Service
public class UsuarioService {

    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    static final int TAMANHO_MINIMO_SENHA = 6;
    private static final String ENTIDADE = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    /** Cria um usuário (senha criptografada com BCrypt). */
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario salvarUsuario(Usuario usuario) {
        String username = usuario.getUsername() == null ? "" : usuario.getUsername().trim();
        if (!USERNAME_VALIDO.matcher(username).matches()) {
            throw new RegraNegocioException(
                    "Username inválido: use de 3 a 40 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }
        Perfil perfil = Perfil.de(usuario.getRole())
                .orElseThrow(() -> new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE."));
        validarSenha(usuario.getPassword());

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setRole(perfil.name());
        novo.setPassword(passwordEncoder.encode(usuario.getPassword()));
        Usuario salvo = usuarioRepository.save(novo);

        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("username", salvo.getUsername());
        depois.put("perfil", salvo.getRole());
        auditoriaService.registrar(TipoOperacao.CRIACAO_USUARIO, ENTIDADE, salvo.getId(),
                AlteracaoCampo.diferencas(new LinkedHashMap<>(), depois), null);
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deletarUsuario(String id) {
        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (alvo.getUsername().equals(SecurityUtils.username())) {
            throw new RegraNegocioException("Você não pode excluir a própria conta.");
        }
        if (Perfil.ADMIN.name().equals(alvo.getRole()) && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("Não é possível excluir o último administrador.");
        }
        usuarioRepository.deleteById(id);

        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("username", alvo.getUsername());
        antes.put("perfil", alvo.getRole());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, ENTIDADE, id,
                AlteracaoCampo.diferencas(antes, new LinkedHashMap<>()), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /** Atualiza perfil e/ou senha. O username não pode ser alterado. */
    @PreAuthorize("hasRole('ADMIN')")
    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (dados.getUsername() != null && !dados.getUsername().isBlank()
                && !dados.getUsername().trim().equals(existente.getUsername())) {
            throw new RegraNegocioException("O username não pode ser alterado.");
        }

        boolean proprioUsuario = existente.getUsername().equals(SecurityUtils.username());
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("perfil", existente.getRole());

        if (dados.getRole() != null && !dados.getRole().isBlank()) {
            Perfil novoPerfil = Perfil.de(dados.getRole())
                    .orElseThrow(() -> new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE."));
            if (!novoPerfil.name().equals(existente.getRole())) {
                if (proprioUsuario) {
                    throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
                }
                if (Perfil.ADMIN.name().equals(existente.getRole())
                        && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
                    throw new RegraNegocioException("Não é possível rebaixar o último administrador.");
                }
                existente.setRole(novoPerfil.name());
            }
        }

        boolean senhaRedefinida = dados.getPassword() != null && !dados.getPassword().isBlank();
        if (senhaRedefinida) {
            validarSenha(dados.getPassword());
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }

        Usuario salvo = usuarioRepository.save(existente);

        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("perfil", salvo.getRole());
        // A senha nunca é registrada (nem em claro, nem hash); só o perfil entra na comparação.
        java.util.List<AlteracaoCampo> mudancas = AlteracaoCampo.diferencas(antes, depois);
        if (!mudancas.isEmpty() || senhaRedefinida) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, ENTIDADE, salvo.getId(), mudancas,
                    senhaRedefinida ? "Senha redefinida (valor não registrado)." : null);
        }
        return salvo;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException(
                    "A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}