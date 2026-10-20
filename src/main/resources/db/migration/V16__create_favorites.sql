create table favorites (
    id uuid primary key,
    user_id uuid not null references users (id) on delete restrict,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_by uuid references users (id) on delete set null,
    updated_at timestamptz not null default current_timestamp,
    constraint favorites_user_restaurant_unique unique (user_id, restaurant_id)
);

create index favorites_user_created_at_idx
    on favorites (user_id, created_at desc, id desc);
