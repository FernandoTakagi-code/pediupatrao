package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Carga inicial:
 *  - migra usuários antigos (sem perfil válido) para o novo modelo de perfis;
 *  - garante o usuário "admin" (ADMIN);
 *  - cria usuários de demonstração (gerente01 / atendente01) e alguns produtos, se habilitado.
 * As senhas de demonstração são apenas para ambiente de desenvolvimento/avaliação.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo,
                                   PasswordEncoder encoder,
                                   @Value("${pediupatrao.seed-demo:true}") boolean seedDemo) {
        return args -> {
            migrarPerfisAntigos(repo);

            if (repo.findByUsername("admin").isEmpty()) {
                criar(repo, encoder, "admin", "teste123", Perfil.ADMIN);
                log.info("Usuário admin criado.");
            }

            if (seedDemo) {
                if (repo.findByUsername("gerente01").isEmpty()) {
                    criar(repo, encoder, "gerente01", "gerente123", Perfil.GERENTE);
                    log.info("Usuário de demonstração gerente01 criado.");
                }
                if (repo.findByUsername("atendente01").isEmpty()) {
                    criar(repo, encoder, "atendente01", "atendente123", Perfil.ATENDENTE);
                    log.info("Usuário de demonstração atendente01 criado.");
                }
                if (produtoRepo.count() == 0) {
                    produto(produtoRepo, "Pizza Calabresa", "Pizza", 45.00);
                    produto(produtoRepo, "Pizza Margherita", "Pizza", 42.50);
                    produto(produtoRepo, "Pizza Quatro Queijos", "Pizza", 52.00);
                    produto(produtoRepo, "Esfiha de Carne", "Esfiha", 6.50);
                    produto(produtoRepo, "Refrigerante 2L", "Bebida", 12.00);
                    log.info("Produtos de demonstração criados.");
                }
            }
        };
    }

    /** Usuários antigos tinham role nula/USER: "admin" vira ADMIN e os demais ATENDENTE (menor privilégio útil). */
    private void migrarPerfisAntigos(UsuarioRepository repo) {
        for (Usuario u : repo.findAll()) {
            if (Perfil.de(u.getRole()).isEmpty()) {
                u.setRole("admin".equals(u.getUsername()) ? Perfil.ADMIN.name() : Perfil.ATENDENTE.name());
                repo.save(u);
                log.info("Perfil do usuário '{}' migrado para {}.", u.getUsername(), u.getRole());
            }
        }
    }

    private void criar(UsuarioRepository repo, PasswordEncoder encoder, String username, String senha, Perfil perfil) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword(encoder.encode(senha));
        u.setRole(perfil.name());
        repo.save(u);
    }

    private void produto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        repo.save(p);
    }
}