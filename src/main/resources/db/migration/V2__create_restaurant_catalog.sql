alter table restaurants add column area varchar(160);
alter table restaurants add column cuisine_label varchar(160);

create table restaurant_images (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    image_url text not null,
    alt_text text,
    sort_order integer not null default 0,
    deleted_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint restaurant_images_sort_order_check check (sort_order >= 0)
);

create index restaurant_images_active_restaurant_idx
    on restaurant_images (restaurant_id)
    where deleted_at is null;

create table restaurant_business_hours (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    day_of_week smallint not null,
    opens_at time not null,
    closes_at time not null,
    constraint restaurant_business_hours_day_check check (day_of_week between 1 and 7),
    constraint restaurant_business_hours_time_check check (opens_at < closes_at),
    constraint restaurant_business_hours_unique unique (restaurant_id, day_of_week, opens_at)
);

create index restaurant_business_hours_restaurant_idx
    on restaurant_business_hours (restaurant_id, day_of_week);

create table restaurant_tags (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    tag varchar(30) not null,
    show_in_benefits boolean not null default false,
    constraint restaurant_tags_tag_check check (tag in ('michelin', 'special_deal', 'date_night')),
    constraint restaurant_tags_unique unique (restaurant_id, tag)
);

create index restaurant_tags_restaurant_idx on restaurant_tags (restaurant_id);
