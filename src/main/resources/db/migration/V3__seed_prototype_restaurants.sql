-- Prototype/demo catalog data only. Review or replace before production.

insert into restaurants (
    id, slug, name, description, cuisine_type, cuisine_label, city_slug, area, district,
    address, price_range, approval_status
) values
    ('00000000-0000-0000-0000-000000000001', 'anan-saigon', 'Anan Saigon',
        'Modern Vietnamese tasting menus with refined plating and a lively city-dining atmosphere.',
        'vietnamese', 'Vietnamese contemporary', 'ho-chi-minh-city', 'District 1', 'District 1', 'District 1',
        '150K - 350K', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'royal-pavilion', 'The Royal Pavilion',
        'Elegant Cantonese dining with private-table ambience and evening reservation demand.',
        'chinese', 'Chinese', 'ho-chi-minh-city', 'District 1', 'District 1', 'District 1',
        '350K - 500K', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000003', 'refinery', 'The Refinery',
        'French comfort dining in a restored colonial setting with strong dinner demand.',
        'western', 'French', 'ho-chi-minh-city', 'District 1', 'District 1', 'District 1',
        '350K - 500K', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000004', 'mori-teppan', 'Mori Teppan',
        'Interactive teppan-style dinner counters that work best for small evening parties.',
        'japanese', 'Japanese', 'ho-chi-minh-city', 'Binh Thanh', 'Binh Thanh', 'Binh Thanh',
        '150K - 300K', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000005', 'hanoi-hearth', 'Hanoi Hearth',
        'Contemporary northern Vietnamese dishes in a warm dining room near the Old Quarter.',
        'vietnamese', 'Vietnamese', 'hanoi', 'Hoan Kiem', 'Hoan Kiem', 'Hoan Kiem, Hanoi',
        '150K - 300K', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000006', 'han-river-dining', 'Han River Dining',
        'Relaxed riverside dining focused on central Vietnamese seafood and shareable evening menus.',
        'vietnamese', 'Vietnamese seafood', 'da-nang', 'Son Tra', 'Son Tra', 'Son Tra, Da Nang',
        '150K - 350K', 'ACTIVE');

insert into restaurant_business_hours (id, restaurant_id, day_of_week, opens_at, closes_at)
select
    ('10000000-0000-0000-0000-' || lpad((restaurant_number * 10 + day_of_week)::text, 12, '0'))::uuid,
    ('00000000-0000-0000-0000-' || lpad(restaurant_number::text, 12, '0'))::uuid,
    day_of_week,
    opens_at::time,
    closes_at::time
from (
    values
        (1, '11:30', '22:00'),
        (2, '11:30', '22:00'),
        (3, '11:30', '22:00'),
        (4, '17:30', '22:30'),
        (5, '11:00', '22:00'),
        (6, '16:30', '22:30')
) as prototype_hours(restaurant_number, opens_at, closes_at)
cross join generate_series(1, 7) as days(day_of_week);

insert into restaurant_tags (id, restaurant_id, tag, show_in_benefits) values
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'michelin', true),
    ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'special_deal', true),
    ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'date_night', false),
    ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 'michelin', false),
    ('20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 'special_deal', false),
    ('20000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', 'date_night', true),
    ('20000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000003', 'date_night', true),
    ('20000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000005', 'date_night', true),
    ('20000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000006', 'special_deal', true);

insert into restaurant_images (id, restaurant_id, image_url, alt_text, sort_order)
select
    ('30000000-0000-0000-0000-' || lpad((restaurant_number * 10 + image_number)::text, 12, '0'))::uuid,
    ('00000000-0000-0000-0000-' || lpad(restaurant_number::text, 12, '0'))::uuid,
    'https://placehold.co/1200x800?text=Prototype+' || restaurant_number || '-' || image_number,
    'Prototype restaurant image',
    image_number
from (
    values (1, 5), (2, 5), (3, 4), (4, 3), (5, 4), (6, 5)
) as image_counts(restaurant_number, image_count)
cross join lateral generate_series(1, image_count) as images(image_number);
