alter table restaurants add column province_code integer;
alter table restaurants add column province_name varchar(160);
alter table restaurants add column ward_code integer;
alter table restaurants add column ward_name varchar(160);

create index restaurants_active_province_idx
    on restaurants (province_code)
    where deleted_at is null;
