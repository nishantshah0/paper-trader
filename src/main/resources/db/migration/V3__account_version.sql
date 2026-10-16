-- optimistic lock on balances, alongside the row lock that order handling already takes
alter table accounts add column version bigint not null default 0;
