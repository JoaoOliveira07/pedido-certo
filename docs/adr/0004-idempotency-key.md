# 0004. Idempotency-Key obrigatória, com hash do corpo

- Status: aceito
- Data: 2026-11-10

## Contexto

`POST /api/orders` não é idempotente por natureza. Clique duplo, timeout com retry automático do cliente ou uma resposta perdida no caminho geram pedidos duplicados.

## Decisão

- O header `Idempotency-Key` é **obrigatório** na criação (8 a 100 caracteres seguros).
- A chave é gravada em `idempotency_keys` junto com o **SHA-256 do corpo**, na mesma transação que cria o pedido.
- A reserva é `insert … on conflict (key) do nothing`. Se outra transação estiver inserindo a mesma chave, o Postgres faz esta esperar; quando a outra confirma, esta não insere nada e devolve o pedido original. Se a outra desfaz, esta insere. Não existe janela para dois pedidos.
- Mesma chave e mesmo corpo: `200` com o pedido original e `Idempotent-Replayed: true`. Mesma chave com outro corpo: `422`, porque é erro do cliente e não deve ser aceito em silêncio.

## Alternativas

- **Chave opcional**: clientes esquecem, e o problema volta justamente nos casos que mais importam.
- **Deduplicar pelo conteúdo** (mesmo cliente e itens em X minutos): bloqueia pedidos legítimos repetidos e não tem uma regra certa de janela.
- **Guardar a resposta HTTP inteira** (como o Stripe): mais fiel, mas devolver o estado atual do pedido é mais útil aqui e evita armazenar corpos.

## Consequências

- Um teste com 8 requisições simultâneas e a mesma chave prova a garantia (`OrderFlowIT`).
- As chaves somem junto com o pedido (`on delete cascade`). Em produção seria preciso uma retenção própria, tipicamente 24 horas a alguns dias.
