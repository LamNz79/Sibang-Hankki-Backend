create table booking_slots (
    id uuid primary key,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    capacity_total integer not null,
    capacity_reserved integer not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint booking_slots_time_range_check check (ends_at > starts_at),
    constraint booking_slots_capacity_total_check check (capacity_total >= 0),
    constraint booking_slots_capacity_reserved_check check (capacity_reserved >= 0),
    constraint booking_slots_restaurant_starts_at_unique unique (restaurant_id, starts_at)
);
