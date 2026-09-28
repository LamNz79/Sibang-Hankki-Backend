create table restaurant_booking_settings (
    restaurant_id uuid primary key references restaurants (id) on delete restrict,
    guest_capacity integer not null,
    booking_interval_minutes smallint not null,
    dining_duration_minutes smallint not null,
    checkout_hold_minutes smallint not null,
    pending_expiry_minutes integer not null,
    confirmation_mode varchar(20) not null,
    manual_confirmation_min_party_size smallint,
    booking_window_days smallint not null,
    minimum_party_size smallint not null,
    maximum_online_party_size smallint not null,
    large_party_threshold smallint not null,
    customer_cancellation_cutoff_minutes integer,
    no_show_grace_minutes integer,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint restaurant_booking_settings_capacity_check check (guest_capacity > 0),
    constraint restaurant_booking_settings_booking_interval_check check (booking_interval_minutes > 0),
    constraint restaurant_booking_settings_dining_duration_check check (dining_duration_minutes > 0),
    constraint restaurant_booking_settings_checkout_hold_check check (checkout_hold_minutes > 0),
    constraint restaurant_booking_settings_pending_expiry_check check (pending_expiry_minutes > 0),
    constraint restaurant_booking_settings_booking_window_check check (booking_window_days > 0),
    constraint restaurant_booking_settings_minimum_party_check check (minimum_party_size > 0),
    constraint restaurant_booking_settings_maximum_party_check check (maximum_online_party_size >= minimum_party_size),
    constraint restaurant_booking_settings_large_party_check check (large_party_threshold >= maximum_online_party_size),
    constraint restaurant_booking_settings_cancellation_cutoff_check check (
        customer_cancellation_cutoff_minutes is null or customer_cancellation_cutoff_minutes >= 0
    ),
    constraint restaurant_booking_settings_no_show_grace_check check (
        no_show_grace_minutes is null or no_show_grace_minutes >= 0
    ),
    constraint restaurant_booking_settings_confirmation_mode_check check (
        confirmation_mode in ('AUTO', 'MANUAL', 'HYBRID')
    ),
    constraint restaurant_booking_settings_manual_confirmation_check check (
        case
            when confirmation_mode = 'HYBRID' then manual_confirmation_min_party_size is not null
                and manual_confirmation_min_party_size > 0
            when confirmation_mode in ('AUTO', 'MANUAL') then manual_confirmation_min_party_size is null
            else false
        end
    )
);
