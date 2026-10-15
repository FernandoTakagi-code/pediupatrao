package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ItemRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.data.domain.Page;
/**
 * Controlador das telas (Thymeleaf). As regras de negócio ficam nos serviços; aqui as
 * violações (RegraNegocioException) viram mensagens de erro para o usuário. O acesso por
 * perfil é imposto pelo SecurityConfig, e não por esta classe nem pelo HTML.
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private final AuditoriaService auditoriaService;
    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;

    public HomeController(PedidoService pedidoService, ClienteService clienteService,
                          ProdutoService produtoService, UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = new ArrayList<>();
        usuarioService.listarTodos().forEach(usuarios::add);
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
                                    RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null); // nunca envia o hash ao formulário
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute Usuario usuario,
                                   RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        Map<String, String> nomesClientes = new HashMap<>();
        for (Cliente c : clienteService.listarClientes()) {
            nomesClientes.put(c.getId(), c.getNome());
        }
        List<PedidoView> views = new ArrayList<>();
        for (Pedido p : pedidoService.listarPedidos()) {
            views.add(new PedidoView(p, nomesClientes.getOrDefault(p.getClienteId(), "(cliente removido)")));
        }
        List<Produto> ativos = new ArrayList<>();
        for (Produto p : produtoService.listarProdutos()) {
            if (Boolean.TRUE.equals(p.getAtivo())) {
                ativos.add(p);
            }
        }
        List<Cliente> clientes = clienteService.listarClientes();
        clientes.sort((a, b) -> String.valueOf(a.getNome()).compareToIgnoreCase(String.valueOf(b.getNome())));

        model.addAttribute("pedidos", views);
        model.addAttribute("produtos", ativos);
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @PostMapping("/pedidos/novo")
    public String novoPedido(@RequestParam String clienteId,
                             @RequestParam(required = false) List<String> produtoId,
                             @RequestParam(required = false) List<Integer> quantidade,
                             @RequestParam(required = false) String observacao,
                             RedirectAttributes redirectAttributes) {
        try {
            List<ItemRequest> itens = new ArrayList<>();
            if (produtoId != null) {
                for (int i = 0; i < produtoId.size(); i++) {
                    Integer qtd = quantidade != null && i < quantidade.size() ? quantidade.get(i) : null;
                    itens.add(new ItemRequest(produtoId.get(i), qtd));
                }
            }
            Pedido salvo = pedidoService.criarPedido(new PedidoRequest(clienteId, itens, observacao));
            redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado! Total: R$ " + salvo.getValorTotal());
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String status,
                                RedirectAttributes redirectAttributes) {
        try {
            Pedido p = pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Status atualizado para " + p.getStatus().getDescricao() + ".");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam String percentual,
                                  RedirectAttributes redirectAttributes) {
        try {
            BigDecimal valor = new BigDecimal(percentual.trim().replace(',', '.'));
            Pedido p = pedidoService.aplicarDesconto(id, valor);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Desconto aplicado. Novo total: R$ " + p.getValorTotal());
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("erro", "Percentual de desconto inválido.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos";
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelarPedido(@PathVariable String id, @RequestParam(required = false) String justificativa,
                                 RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelar(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos";
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        try {
            cliente.setId(null);
            clienteService.salvar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
                                    RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente,
                                   RedirectAttributes redirectAttributes) {
        try {
            cliente.setId(id);
            clienteService.salvar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        } catch (RegraNegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/clientes";
    }
    // ========================
    // AUDITORIA (ADMIN e GERENTE, somente leitura)
    // ========================
    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidade,
                            @RequestParam(required = false) String entidadeId,
                            @RequestParam(required = false) String usuario,
                            @RequestParam(defaultValue = "0") int pagina,
                            Model model) {
        Page<RegistroAuditoria> p = auditoriaService.consultar(entidade, entidadeId, usuario, pagina, 30);
        model.addAttribute("registros", p.getContent());
        model.addAttribute("pagina", p.getNumber());
        model.addAttribute("totalPaginas", p.getTotalPages());
        model.addAttribute("total", p.getTotalElements());
        model.addAttribute("fEntidade", entidade);
        model.addAttribute("fEntidadeId", entidadeId);
        model.addAttribute("fUsuario", usuario);
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }
    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}