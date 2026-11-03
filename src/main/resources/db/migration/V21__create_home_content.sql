create table home_hero_banner (
    id integer primary key,
    title_vi varchar(120),
    title_en varchar(120),
    title_ko varchar(120),
    description_vi varchar(500),
    description_en varchar(500),
    description_ko varchar(500),
    image_url varchar(2000) not null,
    image_alt_vi varchar(300),
    image_alt_en varchar(300),
    image_alt_ko varchar(300),
    target_url varchar(2000) not null,
    enabled boolean not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint home_hero_banner_singleton_check check (id = 1)
);

create table home_city_images (
    province_code integer primary key,
    city_slug varchar(160) not null unique,
    image_url varchar(2000) not null,
    image_alt varchar(300),
    sort_order integer not null,
    enabled boolean not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint home_city_images_province_code_check check (province_code > 0),
    constraint home_city_images_sort_order_check check (sort_order >= 0),
    constraint home_city_images_city_slug_check check (city_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

insert into home_hero_banner (
    id,
    title_vi, title_en, title_ko,
    description_vi, description_en, description_ko,
    image_url,
    image_alt_vi, image_alt_en, image_alt_ko,
    target_url, enabled, created_at, updated_at
) values (
    1,
    'Khám phá trải nghiệm ẩm thực',
    'Discover memorable dining',
    '특별한 미식 경험을 찾아보세요',
    'Tìm và đặt bàn tại những nhà hàng phù hợp với bạn.',
    'Find and book a table at a restaurant that suits you.',
    '나에게 맞는 레스토랑을 찾고 테이블을 예약해 보세요.',
    '/images/sample/restaurants/royal-pavilion-dining-hall.jpg',
    'Không gian nhà hàng The Royal Pavilion',
    'The Royal Pavilion dining room',
    '더 로열 파빌리온 다이닝룸',
    '/restaurants/royal-pavilion',
    true,
    current_timestamp,
    current_timestamp
);

insert into home_city_images (
    province_code, city_slug, image_url, image_alt, sort_order, enabled, created_at, updated_at
) values
    (79, 'ho-chi-minh', '/images/sample/cities/ho-chi-minh-city.jpg', 'Ho Chi Minh City', 1, true,
        current_timestamp, current_timestamp),
    (1, 'hanoi', '/images/sample/cities/hanoi.jpg', 'Hanoi', 2, true,
        current_timestamp, current_timestamp),
    (48, 'da-nang', '/images/sample/cities/da-nang.jpg', 'Da Nang', 3, true,
        current_timestamp, current_timestamp);
