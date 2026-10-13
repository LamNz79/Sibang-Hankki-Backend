create table reservation_proposals (
    id uuid primary key,
    reservation_id uuid not null references reservations (id) on delete restrict,
    proposed_booking_slot_id uuid not null references booking_slots (id) on delete restrict,
    proposed_starts_at timestamptz not null,
    proposed_party_size integer not null,
    reservation_version integer not null,
    message text,
    respond_by timestamptz,
    status varchar(20) not null,
    created_by uuid not null references users (id) on delete restrict,
    created_at timestamptz not null default current_timestamp,
    responded_by uuid references users (id) on delete restrict,
    responded_at timestamptz,
    constraint reservation_proposals_party_size_check check (proposed_party_size > 0),
    constraint reservation_proposals_status_check check (
        status in ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')
    )
);

create index reservation_proposals_reservation_status_idx
    on reservation_proposals (reservation_id, status);

create unique index reservation_proposals_one_pending_idx
    on reservation_proposals (reservation_id)
    where status = 'PENDING';
