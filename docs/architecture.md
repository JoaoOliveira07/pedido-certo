# Arquitetura

## A vida de um pedido

```mermaid
sequenceDiagram
    autonumber
    participant P as Painel
    participant A as API
    participant DB as Postgres (loja)
    participant R as Relay do outbox
    participant E as ERP simulado

    P->>A: POST /api/orders (Idempotency-Key)
    A->>DB: reserva a chave + insere pedido (1 transação)
    A-->>P: 201 Created
    Note over P,A: a mesma chave de novo → 200 com o pedido original

    P->>A: POST /api/demo/orders/{id}/pay
    A->>A: PSP simulado assina o webhook (HMAC)
    A->>DB: pedido PAGO + mensagem no outbox (1 transação)

    loop a cada 500 ms
        R->>DB: reserva mensagens prontas (FOR UPDATE SKIP LOCKED)
        R->>E: entrega (pode falhar, duplicar ou perder o ack)
        E->>E: inbox: já vi este id? devolve o mesmo número
        alt ERP respondeu
            R->>DB: mensagem entregue + pedido INTEGRADO
        else timeout ou ack perdido
            R->>DB: agenda nova tentativa (backoff com jitter)
        else tentativas esgotadas
            R->>DB: mensagem DEAD + pedido FALHA_INTEGRACAO
        end
    end

    A-->>P: SSE "order" depois de cada commit
```

## Estados do pedido

```mermaid
stateDiagram-v2
    [*] --> AGUARDANDO_PAGAMENTO
    AGUARDANDO_PAGAMENTO --> PAGO: webhook do PIX
    PAGO --> INTEGRADO: ERP confirmou
    PAGO --> FALHA_INTEGRACAO: tentativas esgotadas
    FALHA_INTEGRACAO --> PAGO: reprocessar
    INTEGRADO --> [*]
```

As transições ficam no agregado `Order`. As operações que sistemas externos podem repetir (`markPaid`, `markIntegrated`) são idempotentes: uma repetição devolve `false` em vez de lançar erro.

## Onde cada garantia mora

| Garantia | Código | Teste que prova |
|---|---|---|
| Uma chave, um pedido, mesmo em paralelo | `JdbcIdempotencyStore.tryReserve` (`on conflict do nothing` sobre a PK) | `OrderFlowIT.concurrentRequestsWithTheSameKeyCreateASingleOrder` |
| Não existe pedido pago sem mensagem | `ConfirmPaymentService.confirm` (`@Transactional`) | `OrderFlowTest.repeatedWebhookWritesASingleOutboxMessage` |
| Duas instâncias não pegam a mesma mensagem | `JdbcOutboxStore.claimDue` (`SKIP LOCKED` + lease) | `OrderFlowTest.leaseExpiryRedeliversAMessageWhoseResultWasNeverRecorded` |
| Reentrega não duplica no ERP | `SimulatedErp.receive` (inbox + `unique(order_id)`) | `OrderFlowIT.duplicatedDeliveriesAndLostAcksNeverDuplicateTheErpOrder` |
| Falha permanente não some em silêncio | `DeliveryOutcomes.failed` (DLQ + status) | `OrderFlowIT.exhaustedDeliveriesGoToTheDeadLetterQueueAndCanBeRetried` |
| Webhook não pode ser forjado | `PixSignature` (HMAC-SHA256, comparação em tempo constante) | `OrderFlowIT.webhookWithAnInvalidSignatureIsRejected` |

## Transações

- **Criação e pagamento:** uma transação cada, curta.
- **Entrega ao ERP:** de propósito **sem** transação em volta da chamada de rede. A reserva da mensagem é um `UPDATE … RETURNING` em autocommit; o resultado é gravado depois, em outra transação curta (`DeliveryOutcomes`). Segurar uma conexão do pool enquanto se espera a rede é a forma mais comum de derrubar uma API sob carga.
- **ERP simulado:** `REQUIRES_NEW`, porque representa outro sistema, que confirma por conta própria.
- **Eventos para o painel:** publicados em `afterCommit`, então ninguém vê um estado que ainda pode sofrer rollback.

## Observabilidade

- `GET /actuator/health/readiness` para o deploy e o healthcheck do Docker.
- `GET /actuator/prometheus` com métricas da JVM, do pool e do HTTP (bloqueado no Caddy, acessível só pela rede interna).
- Linha do tempo por pedido (`order_timeline`), que é o que o painel mostra: cada tentativa, falha e duplicata descartada, com o motivo.
