drop index users_userid_idx;

alter table users
    add constraint users_userid_unique unique (userid),
    add constraint users_restaurant_scope_check check (
        role not in ('OWNER', 'STAFF') or restaurant_id is not null
    );
