# Demonstração local — 10/11/2026

Carga fictícia criada pelas APIs de clientes e pedidos, usando os perfis existentes e as transições de status do sistema. Não foram alteradas as datas dos pedidos diretamente no banco.

- Cinco clientes fictícios; os cinco produtos de demonstração existentes foram reutilizados.
- 16 pedidos: nove FINALIZADO, dois CANCELADO e um de cada RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA e RETIRADO.
- Três pedidos finalizados com desconto de 10%.
- Faturamento: R$ 678,85; ticket médio: R$ 75,43; pedidos inconsistentes: zero.
- Filtro CANCELADO, de 10/11/2026 a 10/11/2026: dois pedidos, nenhuma venda e receita zero.
- Agrupamento MES, de 01/11/2026 a 10/11/2026: nove vendas e R$ 678,85 em novembro.

Os totais foram conferidos pelo endpoint `GET /api/dashboard`. A [resposta agregada](resposta-api-2026-11-10.json) não contém dados de clientes nem credenciais. Os registros persistem apenas no banco local e os indicadores podem mudar com novos pedidos.

Este registro complementa o [relatório de testes de 06/11/2026](testes.txt). Os 65 testes daquele relatório não foram reexecutados nesta revisão documental; as capturas e a resposta antiga correspondem à carga anterior de seis pedidos.
