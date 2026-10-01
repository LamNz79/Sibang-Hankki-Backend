create table reservations (
    id uuid primary key,
    reference varchar(32) not null unique,
    idempotency_key varchar(255) not null unique,
    request_fingerprint varchar(64) not null,
    restaurant_id uuid not null references restaurants (id) on delete restrict,
    booking_slot_id uuid references booking_slots (id) on delete restrict,
    customer_id uuid references users (id) on delete restrict,
    customer_name varchar(120) not null,
    customer_email varchar(320),
    customer_phone varchar(30) not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    party_size integer not null,
    status varchar(30) not null default 'PENDING',
    capacity_override boolean not null default false,
    visit_status varchar(30),
    special_request text,
    pre_order_note text,
    check_in_token_hash varchar(255) unique,
    checked_in_at timestamptz,
    checked_in_by uuid references users (id) on delete restrict,
    version integer not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint reservations_time_range_check check (ends_at > starts_at),
    constraint reservations_party_size_check check (party_size > 0),
    constraint reservations_status_check check (
        status in ('PENDING', 'ALTERNATIVE_PROPOSED', 'CONFIRMED', 'DECLINED', 'EXPIRED', 'CANCELLED')
    ),
    constraint reservations_visit_status_check check (
        visit_status is null or visit_status in ('EXPECTED', 'ARRIVED', 'SEATED', 'COMPLETED', 'NO_SHOW')
    ),
    constraint reservations_confirmed_visit_status_check check (
        (status = 'CONFIRMED' and visit_status is not null)
        or (status <> 'CONFIRMED' and visit_status is null)
    ),
    constraint reservations_capacity_override_check check (
        capacity_override = false or status = 'CONFIRMED'
    )
);

create index reservations_restaurant_starts_at_idx on reservations (restaurant_id, starts_at);
create index reservations_restaurant_status_idx on reservations (restaurant_id, status);
create index reservations_customer_created_at_idx on reservations (customer_id, created_at);
create index reservations_booking_slot_id_idx on reservations (booking_slot_id);

create table reservation_events (
    id uuid primary key,
    reservation_id uuid not null references reservations (id) on delete restrict,
    event_type varchar(40) not null,
    actor_user_id uuid references users (id) on delete restrict,
    command_id varchar(255) unique,
    request_fingerprint varchar(64),
    metadata jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint reservation_events_event_type_check check (
        event_type in (
            'REQUESTED', 'CONFIRMED', 'DECLINED', 'PENDING_EXPIRED', 'REOPENED',
            'ALTERNATIVE_PROPOSED', 'ALTERNATIVE_ACCEPTED', 'ALTERNATIVE_DECLINED',
            'ANOTHER_TIME_REQUESTED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_RESTAURANT',
            'RESCHEDULED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'NO_SHOW'
        )
    )
);

create index reservation_events_reservation_created_at_idx on reservation_events (reservation_id, created_at);
