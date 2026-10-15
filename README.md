# PediuPatrão 🍕 — Controle de acesso, gestão de pedidos e auditoria

Evolução do sistema PediuPatrão (Atividade Prática M1 – Engenharia de Software).
**Tecnologias:** Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf.

## Como executar

**Pré-requisitos:** JDK 21, MongoDB Community Server rodando em `localhost:27017` e IntelliJ IDEA (ou Maven).

1. Abra a pasta do projeto no IntelliJ (*File ▸ Open ▸ pom.xml ▸ Open as Project*) e aguarde o Maven baixar as dependências.
2. Inicie o MongoDB (no Windows ele já sobe como serviço após a instalação).
3. Execute a classe `PizzariaPedidosApplication` (ou `./mvnw spring-boot:run`).
4. Acesse <http://localhost:8080>.

Outro servidor MongoDB? Defina as variáveis de ambiente `MONGODB_URI` e `MONGODB_DATABASE`
(padrões: `mongodb://localhost:27017` e `pediupatrao`).

Testes: `./mvnw test`.

### Usuários criados automaticamente (`pediupatrao.seed-demo=true`)

| Usuário        | Senha          | Perfil     |
|----------------|----------------|------------|
| `admin`        | `teste123`     | ADMIN      |
| `gerente01`    | `gerente123`   | GERENTE    |
| `atendente01`  | `atendente123` | ATENDENTE  |

São senhas de demonstração: em uso real, desligue `pediupatrao.seed-demo` e troque as senhas.
Usuários antigos sem perfil válido são migrados na inicialização (`admin` → ADMIN; demais → ATENDENTE).
Também são criados 5 produtos de exemplo se a coleção estiver vazia.

## Requisitos implementados

### RF01 — Usuários e permissões
- Três perfis (`ADMIN`, `GERENTE`, `ATENDENTE`); `UsuarioDetailsService` atribui a *authority* do perfil real
  (antes todos recebiam `USER`). Perfil ausente/inválido ⇒ conta desabilitada.
- Autorização no **backend**: `SecurityConfig` (regras por URL **e** método HTTP) + `@PreAuthorize` nos serviços
  (`@EnableMethodSecurity`). Ocultar botões é só conveniência visual.
- `/api/usuarios` deixou de ser público: agora é exclusivo do ADMIN, e a API **nunca devolve a senha**.
- Administração segura de perfis: só ADMIN cadastra/altera/exclui usuários; perfil validado contra o enum;
  ADMIN não altera o **próprio** perfil, não exclui a própria conta, não remove o **último** admin;
  username é imutável; senha mínima de 6 caracteres (BCrypt).
- **CSRF ativado** (antes estava desligado). Formulários Thymeleaf recebem o token automaticamente; os `fetch`
  de JavaScript enviam o token (script no `layout.html`). Clientes de script que mandam `Authorization: Basic`
  (curl/Postman) não dependem de cookie de sessão e dispensam o token.
- Tentativas indevidas: API responde `401`/`403` em JSON, telas respondem com a página de erro 403, e cada
  acesso negado é registrado em log (`WARN`).

### Matriz de permissões

| Recurso | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Usuários e perfis | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar / alterar itens de pedido | ❌ | ✅ | ✅ |
| Avançar status do pedido | ❌ | ✅ | ❌ |
| **Aplicar desconto / cancelar pedido** | ❌ | ✅ | ❌ |
| Consultar clientes | ❌ | ✅ | ✅ |
| Cadastrar / atualizar clientes | ❌ | ❌ | ✅ |
| Excluir clientes | ❌ | ✅ | ❌ |
| Consultar auditoria (somente leitura) | ✅ | ✅ | ❌ |
| Produtos: consultar / alterar | ✅/✅ | ✅/✅ | ✅/❌ |

> Decisões de interpretação (o enunciado não detalha): somente o GERENTE avança o status ("gerenciar pedidos");
> o ATENDENTE "acompanha" o andamento; exclusão de cliente fica com o GERENTE. Para mudar, basta ajustar
> `SecurityConfig` e os `@PreAuthorize`.

### RF02 — Pedido
`Pedido` agora tem itens (produto, nome, quantidade, preço unitário, subtotal), subtotal, desconto (%) e valor do
desconto, **valor total**, status, responsável pelo registro, data/hora de entrada, responsável e data/hora de saída,
dados de cancelamento e histórico de status. O corpo das requisições (`PedidoRequest`) só aceita **cliente + produto +
quantidade (+ observação)**: preço, total, status e responsável vêm do servidor. Validações: cliente existente,
produto existente e ativo, quantidade 1–100, 1–50 itens. Dá para alterar itens enquanto o pedido está `RECEBIDO`
(`PUT /api/pedidos/{id}`), recalculando o total.

### RF03 — Fluxo e transições
`RECEBIDO → EM_PREPARACAO → PRONTO → (SAIU_PARA_ENTREGA | RETIRADO) → FINALIZADO`. Qualquer outra transição
retorna `409` (ex.: finalizar um pedido em preparação). A saída grava **data/hora e usuário responsável**.
`CANCELADO` só pela operação de cancelamento; depois dele nada mais pode ser alterado.

### RF04 — Cancelamento e desconto (exclusivos do GERENTE)
- Cancelamento: justificativa obrigatória (mín. 5 caracteres), só antes da saída; mantém itens, valores e histórico.
- Desconto: percentual maior que 0 e **no máximo 20%**, só antes da saída; o novo desconto *substitui* o anterior
  (não acumula) e o total é recalculado no servidor.
- Ambos são auditados com usuário, data/hora e valores anteriores/novos.

### RF05 — Auditoria
- Coleção `auditoria` no MongoDB (`RegistroAuditoria`): usuário e perfil, data/hora, operação, entidade e id,
  lista de campos alterados (anterior → novo) e justificativa.
- Auditados: criação e alteração de pedidos, mudanças de status, descontos, cancelamentos, cadastro/alteração/exclusão
  de clientes (endereço, telefone etc.) e gestão de usuários/perfis.
- Consulta apenas por ADMIN/GERENTE: tela `/auditoria` e `GET /api/auditoria?entidade=&entidadeId=&usuario=&pagina=&tamanho=`.
- Integridade: o repositório (`AuditoriaRepository`) é **somente leitura** (não herda `save`/`delete`), a entidade é
  imutável, a escrita é apenas `insert` pelo `AuditoriaService`, não há endpoints de alteração/exclusão e o
  `SecurityConfig` nega qualquer método diferente de `GET` nessas rotas.
- Proteção de dados: senhas, hashes e tokens nunca são gravados (nem na auditoria nem em log); o **telefone é
  gravado mascarado** (só os 4 últimos dígitos).

## Principais arquivos alterados / criados

| Área | Arquivos |
|---|---|
| Segurança | `config/SecurityConfig`, `service/UsuarioDetailsService`, `security/SecurityUtils`, `entity/Perfil` |
| Pedidos | `entity/Pedido`, `ItemPedido`, `StatusPedido`, `EventoStatus`, `service/PedidoService`, `controller/PedidoController`, `dto/*` |
| Auditoria | `entity/RegistroAuditoria`, `AlteracaoCampo`, `TipoOperacao`, `repository/AuditoriaRepository`, `service/AuditoriaService`, `controller/AuditoriaController` |
| Clientes / usuários | `service/ClienteService`, `service/UsuarioService`, controladores REST e `HomeController` |
| Infra | `config/AppConfig` (relógio, leitura de status antigos), `config/DataInitializer`, `exception/*`, `controller/GlobalApiExceptionHandler` |
| Telas | `pedidos.html`, `auditoria.html`, `fragments/sidebar.html`, `layout.html`, `usuarios/*`, `clientes/lista.html`, `login.html` |
| Testes | `src/test/...` (regras de negócio, auditoria, usuários, regras de segurança HTTP e `@PreAuthorize`) |

Também foi corrigido um XSS na lista de clientes (valores eram inseridos via `innerHTML` sem escape).

