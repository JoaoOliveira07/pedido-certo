# 0002. Outbox transacional + consumidor idempotente

- Status: aceito
- Data: 2026-11-10

## Contexto

Quando o PIX é confirmado, duas coisas precisam acontecer: o pedido vira `PAGO` no banco da loja e o ERP fica sabendo. Fazer as duas em sequência abre duas janelas de falha:

1. Grava no banco e o processo cai antes de avisar o ERP: pedido pago que nunca chega.
2. Avisa o ERP e o processo cai antes de gravar: ERP com pedido que a loja não considera pago.

Transação distribuída (2PC/XA) entre o banco e o ERP não é uma opção realista: ERPs e filas raramente participam, e quando participam o custo operacional é alto.

Além disso, nenhuma rede entrega "exatamente uma vez". Se a resposta do ERP se perde, quem enviou não tem como saber se o ERP recebeu.

## Decisão

- **Outbox transacional.** A mudança de status e a mensagem para o ERP são gravadas na **mesma transação**, na tabela `outbox`. Um relay lê o outbox e entrega ao ERP.
- **Entrega pelo menos uma vez.** Na dúvida (timeout, ack perdido, processo morto no meio), a mensagem é enviada de novo, com backoff exponencial e jitter. Depois de `max-attempts`, vai para a DLQ (`status = DEAD`) e o pedido fica em `FALHA_INTEGRACAO`, visível e reprocessável.
- **Consumidor idempotente.** O ERP guarda o id de cada mensagem recebida (`erp.inbox`). Se a mesma mensagem chegar de novo, devolve o número que já tinha criado. A restrição `unique(order_id)` em `erp.orders` é uma segunda linha de defesa.

O efeito final é o de "exatamente uma vez", construído com peças que existem de verdade.

## Alternativas

- **Publicar direto na fila depois do commit**: volta a janela 1.
- **CDC (Debezium lendo o WAL)**: elimina o polling, mas acrescenta Kafka Connect e um broker para operar. Faz sentido com muitos serviços, não com um.
- **Confiar em deduplicação do broker (SQS FIFO, Kafka idempotent producer)**: resolve duplicatas entre produtor e broker, não entre broker e consumidor final. O consumidor continua precisando ser idempotente.

## Consequências

- Latência de entrega igual ao intervalo do relay (500 ms por padrão).
- O banco da loja carrega a tabela `outbox`; mensagens entregues são apagadas junto com o pedido pela retenção da demo. Em produção seria preciso um expurgo próprio.
- A idempotência do consumidor vira **contrato**: qualquer ERP real ligado aqui precisa aceitar um id de mensagem e deduplicar por ele.
