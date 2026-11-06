# Apresentação do dashboard

O arquivo `apresentacao-dashboard.mp4` é uma apresentação legendada, sem narração, feita com capturas reais do dashboard. Duração planejada: 84 segundos (7 cenas de 12 segundos), abaixo do limite de três minutos. Os dados são fictícios, criados pelas APIs do sistema em banco de validação separado.

## Roteiro para apresentar ou narrar

| Tempo | Demonstração | Explicação |
|---|---|---|
| 00:00–00:12 | Visão geral e indicadores | O responsável acompanha faturamento, volume de pedidos, ticket e operação. Apenas pedidos finalizados geram receita. O total já inclui os descontos. |
| 00:12–00:24 | Gráficos e produtos mais vendidos | Série de receita por período, distribuição por status e ranking por unidades. Preservamos o nome registrado no pedido, mesmo após alteração do catálogo. |
| 00:24–00:36 | Filtro de cancelados | O mesmo filtro afeta todo o painel. Cancelados aparecem na operação e não geram faturamento ou ranking. |
| 00:36–00:48 | Agrupamento mensal e tabela | É possível agrupar por dia ou mês e consultar uma tabela acessível com os valores exatos. Períodos sem vendas aparecem com zero. |
| 00:48–01:00 | Validação de período | O backend rejeita intervalo invertido, futuro ou maior que 366 dias. A tela informa o problema e oculta resultados anteriores. |
| 01:00–01:12 | Layout móvel e atualização | Layout responsivo, atualização manual e opção de atualização a cada 60 segundos, pausada em aba oculta. |
| 01:12–01:24 | Decisões e evidências | Java 21, Spring Boot, MongoDB e Thymeleaf já existentes. API consolidada, BigDecimal, índice por data, ADMIN/GERENTE, 65 testes aprovados incluindo banco real. |

## Preparar uma gravação interativa própria

Execute a aplicação no IntelliJ, entre como GERENTE, abra Dashboard, mostre os filtros e o botão Atualizar agora. Siga a sequência acima e finalize mostrando `docs/dashboard.md` e o resultado de `MONGODB_INTEGRATION=true` com `mvnw.cmd verify`. Não exponha senhas, documentos pessoais nem dados reais na gravação.
