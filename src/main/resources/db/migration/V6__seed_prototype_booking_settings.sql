-- Prototype/demo booking settings only. Review or replace before production.

insert into restaurant_booking_settings (
    restaurant_id,
    guest_capacity,
    booking_interval_minutes,
    dining_duration_minutes,
    checkout_hold_minutes,
    pending_expiry_minutes,
    confirmation_mode,
    manual_confirmation_min_party_size,
    booking_window_days,
    minimum_party_size,
    maximum_online_party_size,
    large_party_threshold,
    customer_cancellation_cutoff_minutes,
    no_show_grace_minutes
)
select
    restaurant.id,
    20,
    60,
    90,
    10,
    30,
    'HYBRID',
    7,
    30,
    1,
    10,
    11,
    120,
    15
from restaurants restaurant
where restaurant.slug in (
    'anan-saigon',
    'royal-pavilion',
    'refinery',
    'mori-teppan',
    'hanoi-hearth',
    'han-river-dining'
)
on conflict (restaurant_id) do nothing;
