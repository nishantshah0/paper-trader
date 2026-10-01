alter table orders add column idempotency_key varchar(128);
create unique index orders_idempotency_idx on orders (account_id, idempotency_key) where idempotency_key is not null;
-- This simulator fills an order completely; one durable fill per order.
create unique index trades_one_fill_per_order_idx on trades (order_id);
