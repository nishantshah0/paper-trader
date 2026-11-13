-- Orders can now fill in pieces, against other users' resting orders or the house.

alter table orders add column filled_quantity integer not null default 0;
alter table orders add column avg_fill_price numeric(18, 4);

-- every order filled so far filled in one trade
update orders set filled_quantity = quantity where status = 'FILLED';
update orders o set avg_fill_price = t.price from trades t where t.order_id = o.id;

-- the database backstop against overfilling, replacing "one trade per order"
alter table orders add constraint orders_filled_within_quantity
    check (filled_quantity >= 0 and filled_quantity <= quantity);
alter table orders add constraint orders_filled_means_fully_filled
    check (status <> 'FILLED' or filled_quantity = quantity);

drop index trades_one_fill_per_order_idx;
create index trades_by_order_idx on trades (order_id);

-- the other side of a fill: another user's order, or null for the house
alter table trades add column counterparty_order_id bigint references orders (id);
