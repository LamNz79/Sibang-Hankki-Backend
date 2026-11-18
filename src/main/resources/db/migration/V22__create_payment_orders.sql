create table payment_orders (
    id uuid primary key,
    reservation_id uuid not null references reservations(id) on delete restrict,
    provider varchar(30) not null,
    merchant_reference varchar(100) not null unique,
    amount bigint not null,
    currency char(3) not null,
    status varchar(30) not null,
    provider_transaction_id varchar(100),
    response_code varchar(10),
    expires_at timestamptz not null,
    paid_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint payment_orders_provider_check check (provider in ('VNPAY')),
    constraint payment_orders_amount_check check (amount > 0),
    constraint payment_orders_currency_check check (currency = 'VND'),
    constraint payment_orders_status_check check (status in ('PENDING', 'PAID', 'FAILED', 'EXPIRED'))
);

create unique index payment_orders_one_pending_per_reservation
    on payment_orders(reservation_id)
    where status = 'PENDING';

create index payment_orders_reservation_created_idx
    on payment_orders(reservation_id, created_at desc);
