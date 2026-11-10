-- Lado da loja --------------------------------------------------------------

create table orders (
    id            uuid         primary key,
    customer_name varchar(120) not null,
    status        varchar(30)  not null,
    total_cents   bigint       not null check (total_cents > 0),
    pix_txid      varchar(35)  not null unique,
    erp_number    bigint,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       integer      not null default 0
);

create index orders_created_at on orders (created_at desc);

create table order_items (
    order_id         uuid         not null references orders (id) on delete cascade,
    line             integer      not null,
    sku              varchar(40)  not null,
    description      varchar(120) not null,
    quantity         integer      not null check (quantity > 0),
    unit_price_cents bigint       not null check (unit_price_cents > 0),
    primary key (order_id, line)
);

create table idempotency_keys (
    key          varchar(100) primary key,
    request_hash char(64)     not null,
    order_id     uuid         references orders (id) on delete cascade,
    created_at   timestamptz  not null default now()
);

-- Outbox transacional: gravado na mesma transação que muda o pedido.
create table outbox (
    id              uuid        primary key,
    aggregate_id    uuid        not null references orders (id) on delete cascade,
    type            varchar(50) not null,
    payload         jsonb       not null,
    status          varchar(20) not null check (status in ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'DEAD')),
    attempts        integer     not null default 0,
    next_attempt_at timestamptz not null,
    locked_until    timestamptz,
    last_error      text,
    created_at      timestamptz not null,
    delivered_at    timestamptz
);

-- Índice parcial: o relay só olha para o que ainda precisa ser entregue.
create index outbox_due on outbox (next_attempt_at) where status in ('PENDING', 'IN_FLIGHT');
create index outbox_aggregate on outbox (aggregate_id);

create table order_timeline (
    id       bigserial   primary key,
    order_id uuid        not null references orders (id) on delete cascade,
    at       timestamptz not null,
    kind     varchar(40) not null,
    detail   text        not null
);

create index order_timeline_order on order_timeline (order_id, id);

-- Lado do ERP simulado -------------------------------------------------------
-- Fica em outro schema para deixar claro que é "outro sistema". Não há FK para a loja.

create schema erp;

create table erp.orders (
    erp_number  bigserial   primary key,
    order_id    uuid        not null unique, -- segunda linha de defesa contra duplicata
    total_cents bigint      not null,
    created_at  timestamptz not null default now()
);

-- Inbox do consumidor idempotente: cada mensagem é processada uma vez só.
create table erp.inbox (
    message_id  uuid        primary key,
    erp_number  bigint      not null references erp.orders (erp_number) on delete cascade,
    duplicates  integer     not null default 0,
    received_at timestamptz not null default now()
);

alter sequence erp.orders_erp_number_seq restart with 1001;
