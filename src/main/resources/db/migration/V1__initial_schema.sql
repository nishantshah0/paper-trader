create table users (
    id         bigint generated always as identity primary key,
    username   varchar(32) not null unique,
    created_at timestamptz not null default now()
);

-- one account per user; starting_cash lets the leaderboard rank by return
create table accounts (
    id            bigint generated always as identity primary key,
    user_id       bigint not null unique references users (id),
    cash          numeric(18, 2) not null check (cash >= 0),
    starting_cash numeric(18, 2) not null check (starting_cash > 0),
    created_at    timestamptz not null default now()
);

create table orders (
    id          bigint generated always as identity primary key,
    account_id  bigint not null references accounts (id),
    symbol      varchar(10) not null,
    side        varchar(4) not null check (side in ('BUY', 'SELL')),
    order_type  varchar(6) not null check (order_type in ('MARKET', 'LIMIT')),
    quantity    integer not null check (quantity > 0),
    limit_price numeric(18, 4) check (limit_price > 0),
    status      varchar(9) not null check (status in ('OPEN', 'FILLED', 'CANCELLED', 'REJECTED')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    -- limit orders need a price, market orders must not have one
    constraint orders_limit_price_matches_type check (
        (order_type = 'LIMIT' and limit_price is not null)
        or (order_type = 'MARKET' and limit_price is null)
    )
);

-- open orders get reloaded into the in-memory book on startup
create index orders_open_by_symbol_idx on orders (symbol) where status = 'OPEN';
create index orders_by_account_idx on orders (account_id, created_at desc);

create table trades (
    id          bigint generated always as identity primary key,
    order_id    bigint not null references orders (id),
    account_id  bigint not null references accounts (id),
    symbol      varchar(10) not null,
    side        varchar(4) not null check (side in ('BUY', 'SELL')),
    quantity    integer not null check (quantity > 0),
    price       numeric(18, 4) not null check (price > 0),
    executed_at timestamptz not null default now()
);

create index trades_by_account_idx on trades (account_id, executed_at desc);

-- long only for now, so quantity never goes negative
create table positions (
    id           bigint generated always as identity primary key,
    account_id   bigint not null references accounts (id),
    symbol       varchar(10) not null,
    quantity     integer not null check (quantity >= 0),
    avg_cost     numeric(18, 4) not null check (avg_cost >= 0),
    realized_pnl numeric(18, 2) not null default 0,
    updated_at   timestamptz not null default now(),
    unique (account_id, symbol)
);
