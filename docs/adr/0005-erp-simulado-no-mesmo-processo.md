# 0005. ERP simulado no mesmo processo, em outro schema

- Status: aceito
- Data: 2026-11-10

## Contexto

A demo precisa de um "ERP" que receba pedidos e de uma rede entre os dois que falhe de formas controláveis. Precisa caber numa VPS pequena junto com o resto.

## Decisão

- O ERP é um componente da própria API (`adapter.out.erp.SimulatedErp`), com tabelas no schema `erp`, sem chaves estrangeiras para a loja, e transação própria (`REQUIRES_NEW`).
- A rede é `SimulatedErpGateway`: antes, durante e depois da chamada ao ERP ele sorteia timeout, entrega dupla e perda do ack, conforme a configuração de caos.
- O caos e os assinantes do SSE ficam em memória.

## Alternativas

- **Um segundo serviço para o ERP**: mais realista e mostraria HTTP entre serviços, mas dobra deploy, memória e configuração para ensinar a mesma coisa.

## Consequências

- A fronteira continua clara no código: a aplicação só conhece a porta `ErpGateway`. Extrair o ERP para outro serviço é escrever um `HttpErpGateway` e mover `SimulatedErp` para outro projeto.
- **Uma instância só.** Com várias, cada uma teria seu próprio caos e seus próprios assinantes SSE. O relay já funciona com várias (ADR 0003); para o resto, o caminho seria guardar o caos no banco e espalhar os avisos do SSE com `LISTEN/NOTIFY`.
- O caos é global: quem mexe na rede muda a demo para todos que estão olhando. O painel avisa isso.
