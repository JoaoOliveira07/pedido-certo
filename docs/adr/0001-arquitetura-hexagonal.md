# 0001. Arquitetura hexagonal, verificada com ArchUnit

- Status: aceito
- Data: 2026-11-10

## Contexto

O valor deste projeto está nas regras de integração: idempotência, outbox, retentativas. Essas regras precisam ser testáveis sem banco e sem rede, e o transporte até o ERP precisa poder mudar (simulado hoje; HTTP ou fila num caso real) sem tocar nelas.

## Decisão

Três camadas, com dependências apontando só para dentro:

- `domain`: Java puro. O agregado `Order`, a `RetryPolicy` e os eventos.
- `application`: casos de uso (`port.in`), as portas que eles precisam (`port.out`) e as implementações (`service`).
- `adapter`: web, agendamento, JDBC, ERP simulado. Cada adapter implementa ou chama uma porta.

As regras são testes (`ArchitectureTest`): domínio sem Spring, Jackson ou Jakarta; aplicação sem nada de web, JDBC ou adapters; adapters sem depender uns dos outros.

## Alternativas

- **Camadas tradicionais (controller → service → repository)**: mais simples, mas o serviço acaba conhecendo JPA, DTOs HTTP e detalhes do transporte, e os testes do fluxo passam a exigir banco.
- **Módulos Maven separados por camada**: a separação vira erro de compilação, o que é ainda mais forte, mas pesa demais num projeto deste tamanho. O ArchUnit dá quase a mesma garantia com um módulo só.

## Consequências

- O fluxo inteiro, com falhas de rede, é testado em milissegundos com adapters em memória (`OrderFlowTest`).
- Trocar o ERP simulado por um real é escrever outro `ErpGateway`.
- Concessão consciente: os serviços de aplicação usam `@Service` e `@Transactional` do Spring. Eliminar isso exigiria declarar cada bean à mão e um decorator de transação, custo alto para ganho pequeno. A regra do ArchUnit permite só esses pacotes.
