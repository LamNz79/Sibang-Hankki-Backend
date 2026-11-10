alter table restaurants
    add column latitude double precision,
    add column longitude double precision,
    add constraint chk_restaurant_coordinates_pair
        check ((latitude is null and longitude is null)
            or (latitude is not null and longitude is not null)),
    add constraint chk_restaurant_latitude
        check (latitude is null or latitude between -90 and 90),
    add constraint chk_restaurant_longitude
        check (longitude is null or longitude between -180 and 180);
