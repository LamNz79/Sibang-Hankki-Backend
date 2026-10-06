create table menu_categories (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    name varchar(50) not null,
    display_order integer not null default 0,
    deleted_at timestamptz,
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_by uuid references users (id) on delete set null,
    updated_at timestamptz not null default current_timestamp,
    constraint menu_categories_display_order_check check (display_order >= 0)
);

create unique index menu_categories_active_name_idx
    on menu_categories (restaurant_id, lower(name))
    where deleted_at is null;

create table menus (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    category_id uuid references menu_categories (id) on delete restrict,
    name varchar(100) not null,
    description text,
    price numeric(18, 2) not null,
    currency char(3) not null default 'VND',
    image_url text,
    is_available boolean not null default true,
    deleted_at timestamptz,
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_by uuid references users (id) on delete set null,
    updated_at timestamptz not null default current_timestamp,
    constraint menus_price_check check (price >= 0),
    constraint menus_currency_check check (currency = upper(currency))
);

create index menus_active_restaurant_idx
    on menus (restaurant_id, category_id, name)
    where deleted_at is null;
