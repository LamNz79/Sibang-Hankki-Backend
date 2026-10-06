alter table reservations
    add column management_token_hash varchar(64);

alter table reservations
    add constraint reservations_management_token_hash_check check (
        management_token_hash is null or management_token_hash ~ '^[0-9a-f]{64}$'
    );
