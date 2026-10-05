-- Demo-profile account only. The default profile does not load this migration.

insert into users (
    id,
    userid,
    email,
    password_hash,
    name,
    role,
    status,
    restaurant_id
) values (
    '40000000-0000-0000-0000-000000000001',
    'owner',
    'owner@prototype.local',
    '$2a$10$aZjOK0DphuwOdi4MGr7OMeTeUXJp8i5NsfLX3/uMGhPypqqv1s6hK',
    'The Royal Pavilion Demo Owner',
    'OWNER',
    'ACTIVE',
    '00000000-0000-0000-0000-000000000002'
)
on conflict do nothing;
