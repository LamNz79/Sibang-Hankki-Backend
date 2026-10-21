alter table booking_slots
    add column bookable boolean not null default true;

alter table booking_slots
    drop constraint booking_slots_restaurant_starts_at_unique;

create unique index booking_slots_restaurant_bookable_starts_at_unique
    on booking_slots (restaurant_id, starts_at)
    where bookable;
