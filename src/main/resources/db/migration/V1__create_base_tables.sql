create table restaurants (
    id uuid primary key,
    slug varchar(160) not null unique,
    name varchar(200) not null,
    description text,
    cuisine_type varchar(160),
    city_slug varchar(160) not null,
    district varchar(160),
    address text,
    phone varchar(30),
    email varchar(320),
    timezone varchar(64) not null default 'Asia/Ho_Chi_Minh',
    price_range varchar(40),
    approval_status varchar(30) not null default 'DRAFT',
    deleted_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint restaurants_approval_status_check check (
        approval_status in ('DRAFT', 'PENDING', 'CHANGES_REQUESTED', 'ACTIVE', 'SUSPENDED')
    )
);

create index restaurants_city_slug_idx on restaurants (city_slug);
create index restaurants_approval_status_idx on restaurants (approval_status);

create table users (
    id uuid primary key,
    userid varchar(30) not null,
    email varchar(320),
    password_hash varchar(255) not null,
    name varchar(120) not null,
    phone varchar(30),
    profile_image text,
    role varchar(20) not null default 'CUSTOMER',
    status varchar(20) not null default 'ACTIVE',
    restaurant_id uuid references restaurants (id) on delete restrict,
    status_changed_at timestamptz not null default current_timestamp,
    deleted_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint users_role_check check (role in ('CUSTOMER', 'OWNER', 'STAFF', 'ADMIN')),
    constraint users_status_check check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

create index users_userid_idx on users (userid);
create index users_restaurant_id_idx on users (restaurant_id);
