# Apresentação do dashboard

[Assistir ou baixar o vídeo](apresentacao-dashboard.mp4).

Gravação fornecida em 10/11/2026. Arquivo MP4, resolução 1280 × 720, duração de aproximadamente 1min18s e faixa de áudio estéreo. A duração está dentro do limite de três minutos da atividade. Caso o GitHub não reproduza o arquivo diretamente, use a opção de download.

## Guia para a demonstração

Esta sequência serve como apoio para apresentar o projeto; não é uma transcrição nem uma marcação de cenas do vídeo.

1. Acesse `/dashboard` com perfil GERENTE e apresente os indicadores gerais.
2. Mostre os gráficos de faturamento e situação dos pedidos e o ranking de produtos por unidades vendidas.
3. Consulte 10/11/2026 com situação CANCELADO: dois pedidos, faturamento e ticket zerados. Retorne para todos os status.
4. Alterne o agrupamento entre DIA e MES e mostre a tabela de valores.
5. Mostre a atualização manual e a opção automática a cada 60 segundos.
6. Explique as decisões: reaproveitamento de Java 21/Spring Boot/MongoDB/Thymeleaf, endpoint consolidado `GET /api/dashboard`, regras no servidor, `BigDecimal` para valores monetários e acesso por ADMIN/GERENTE.
7. Apresente `docs/dashboard.md` e as evidências de testes de 06/11/2026.

## Dados fictícios disponíveis

A carga local de 10/11/2026 possui cinco clientes identificados com o prefixo `Cliente Demo` e 16 pedidos identificados nas observações com `DEMO-APRESENTACAO-20261110-`. Nove estão finalizados, dois cancelados e cinco em andamento, um em cada etapa operacional. Três pedidos finalizados receberam desconto de 10%.

Na validação dessa carga, o faturamento foi R$ 678,85 e o ticket médio R$ 75,43. Todos os pedidos foram criados em 10/11/2026, por isso as vendas ficam concentradas nesse dia no gráfico diário. A carga está no MongoDB local: os dados não são inseridos automaticamente ao clonar o repositório. Veja [o registro da demonstração](evidencias/demonstracao-2026-11-10.md).
