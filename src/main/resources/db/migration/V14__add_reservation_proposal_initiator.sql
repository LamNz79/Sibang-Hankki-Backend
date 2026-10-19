alter table reservation_proposals
    add column initiator varchar(20) not null default 'RESTAURANT',
    alter column created_by drop not null,
    add constraint reservation_proposals_initiator_check
        check (initiator in ('RESTAURANT', 'CUSTOMER')),
    add constraint reservation_proposals_creator_check
        check (initiator = 'CUSTOMER' or created_by is not null);

alter table reservation_events
    drop constraint reservation_events_event_type_check,
    add constraint reservation_events_event_type_check check (
        event_type in (
            'REQUESTED', 'CONFIRMED', 'DECLINED', 'PENDING_EXPIRED', 'REOPENED',
            'ALTERNATIVE_PROPOSED', 'ALTERNATIVE_ACCEPTED', 'ALTERNATIVE_DECLINED',
            'ANOTHER_TIME_REQUESTED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_RESTAURANT',
            'RESCHEDULED', 'RESCHEDULE_DECLINED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'NO_SHOW'
        )
    );
