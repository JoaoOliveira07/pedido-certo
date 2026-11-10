# 0006. Spring JDBC com SQL explícito em vez de JPA

- Status: aceito
- Data: 2026-11-10

## Contexto

As partes críticas deste projeto são SQL específico do Postgres: `on conflict do nothing`, `for update skip locked`, `update … returning`, índices parciais. Também é importante saber exatamente quando cada escrita acontece, porque a correção depende de quais escritas estão na mesma transação.

## Decisão

Usar `JdbcClient` (Spring 6.1+) com SQL escrito à mão nos adapters de persistência. O agregado é reconstruído por `Order.restore(...)`. Concorrência otimista pela coluna `version`, verificada no `update … where version = :v`.

## Alternativas

- **JPA/Hibernate**: produtivo para CRUD, mas as consultas importantes acabariam como SQL nativo, o domínio ganharia anotações e o momento real das escritas ficaria escondido atrás do flush.
- **jOOQ**: SQL tipado, ótima opção; exige geração de código a partir do schema, mais uma etapa no build.

## Consequências

- O SQL está à vista de quem revisa, junto do comentário que explica por que ele é assim.
- O mapeamento é manual. Com poucas tabelas isso é pouco código; os testes de integração pegam erros de coluna.
- Itens dos pedidos são carregados numa consulta só por página (`where order_id in (...)`), evitando N+1.
