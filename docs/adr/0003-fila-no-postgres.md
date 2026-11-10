# 0003. Fila no próprio Postgres, com SKIP LOCKED

- Status: aceito
- Data: 2026-11-10

## Contexto

O outbox precisa de alguém que o consuma: ler mensagens prontas, entregar, registrar o resultado. Com mais de uma instância da API, duas não podem pegar a mesma mensagem ao mesmo tempo. A demo roda numa VPS pequena, sem orçamento para um broker gerenciado.

## Decisão

A própria tabela `outbox` é a fila. O relay reserva um lote com uma única instrução:

```sql
update outbox set status = 'IN_FLIGHT', locked_until = :lease, attempts = attempts + 1
 where id in (select id from outbox where <pronta> order by next_attempt_at
               limit :n for update skip locked)
returning ...
```

- `FOR UPDATE SKIP LOCKED` faz instâncias concorrentes pularem linhas que outra já está reservando, sem esperar.
- `locked_until` é um lease: se o processo morrer com a mensagem reservada, ela volta a ficar disponível quando o lease expira.
- Índice parcial `where status in ('PENDING','IN_FLIGHT')`, para a consulta não crescer com o histórico.

## Alternativas

- **SQS / RabbitMQ / Kafka**: o caminho certo com volume alto ou muitos consumidores. Aqui seria mais um serviço para operar e pagar, e ainda precisaríamos do outbox para publicar com segurança. No trabalho uso SQS; o `OutboxStore` é a porta onde ele entraria.
- **`@Scheduled` com `select` seguido de `update`**: corrida entre instâncias.

## Consequências

- Zero infraestrutura extra, e a fila é consultável com SQL, o que facilita depurar e alimentar o painel.
- Escala até alguns milhares de mensagens por segundo, muito além do necessário aqui. Acima disso, um broker dedicado.
- Polling a cada 500 ms gera consultas mesmo sem mensagens. Barato com o índice parcial; `LISTEN/NOTIFY` eliminaria isso se fosse preciso.
