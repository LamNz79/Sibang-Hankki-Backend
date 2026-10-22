-- Prototype/demo data only. Values are stable and safe to reapply.

-- The official venue address places The Royal Pavilion in Phuong Sai Gon (code 26740).
-- Only replace the untouched legacy fixture; preserve later owner/admin edits.
update restaurants
set area = 'Phường Sài Gòn',
    district = 'Phường Sài Gòn',
    address = '22-36 Nguyễn Huệ và 57-69F Đồng Khởi, Phường Sài Gòn, TP.HCM',
    province_code = 79,
    province_name = 'Thành phố Hồ Chí Minh',
    ward_code = 26740,
    ward_name = 'Phường Sài Gòn',
    updated_at = current_timestamp
where id = '00000000-0000-0000-0000-000000000002'
  and slug = 'royal-pavilion'
  and deleted_at is null
  and address = 'District 1'
  and ward_code is null
  and ward_name is null;

insert into menu_categories (id, restaurant_id, name, display_order)
values
    ('40000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002',
        'Royal Pavilion Dim Sum', 1),
    ('40000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000002',
        'Royal Pavilion Signatures', 2)
on conflict do nothing;

insert into menus (
    id, restaurant_id, category_id, name, description, price, currency, is_available
)
select * from (values
    ('41000000-0000-0000-0000-000000000201'::uuid,
        '00000000-0000-0000-0000-000000000002'::uuid,
        '40000000-0000-0000-0000-000000000201'::uuid,
        'Har Gow', 'Steamed shrimp dumplings', 168000.00, 'VND', true),
    ('41000000-0000-0000-0000-000000000202'::uuid,
        '00000000-0000-0000-0000-000000000002'::uuid,
        '40000000-0000-0000-0000-000000000201'::uuid,
        'Siu Mai with Tobiko', 'Steamed pork and shrimp dumplings topped with tobiko', 158000.00, 'VND', true),
    ('41000000-0000-0000-0000-000000000203'::uuid,
        '00000000-0000-0000-0000-000000000002'::uuid,
        '40000000-0000-0000-0000-000000000202'::uuid,
        'Crispy Roasted Duck', 'Cantonese roasted duck with crisp skin', 488000.00, 'VND', true),
    ('41000000-0000-0000-0000-000000000204'::uuid,
        '00000000-0000-0000-0000-000000000002'::uuid,
        '40000000-0000-0000-0000-000000000202'::uuid,
        'Wok-fried Beef Tenderloin', 'Beef tenderloin wok-fried with seasonal vegetables', 428000.00, 'VND', true),
    ('41000000-0000-0000-0000-000000000205'::uuid,
        '00000000-0000-0000-0000-000000000002'::uuid,
        '40000000-0000-0000-0000-000000000202'::uuid,
        'Braised Abalone with Seasonal Greens', 'Braised abalone served with seasonal greens', 688000.00, 'VND', true)
) as demo_items(id, restaurant_id, category_id, name, description, price, currency, is_available)
where exists (
    select 1 from menu_categories category
    where category.id = demo_items.category_id
      and category.restaurant_id = demo_items.restaurant_id
      and category.deleted_at is null
)
on conflict do nothing;

-- Replace only known prototype image records when they still contain a placeholder or Pinterest page URL.
update restaurant_images
set image_url = 'https://www.thereveriesaigon.com/wp-content/uploads/2021/11/detail-dining-the-royal-pavilion-f-the-reverie-saigon.jpg',
    alt_text = 'Dining room details at The Royal Pavilion',
    updated_at = current_timestamp
where id = '30000000-0000-0000-0000-000000000021'
  and restaurant_id = '00000000-0000-0000-0000-000000000002'
  and (image_url like 'https://placehold.co/%' or image_url like '%pinterest.com/pin%');

update restaurant_images
set image_url = 'https://www.thereveriesaigon.com/wp-content/uploads/2021/11/The-Reverie-Saigon-The-Royal-Pavilion-I.-Hjpg-1920x1080-1-sm.jpg',
    alt_text = 'Main dining room at The Royal Pavilion',
    updated_at = current_timestamp
where id = '30000000-0000-0000-0000-000000000022'
  and restaurant_id = '00000000-0000-0000-0000-000000000002'
  and (image_url like 'https://placehold.co/%' or image_url like '%pinterest.com/pin%');

update restaurant_images
set image_url = 'https://www.thereveriesaigon.com/wp-content/uploads/2021/11/The-Reverie-Saigon-The-Royal-Pavilion-III-1920x1080-1.jpg',
    alt_text = 'Elegant Cantonese dining at The Royal Pavilion',
    updated_at = current_timestamp
where id = '30000000-0000-0000-0000-000000000023'
  and restaurant_id = '00000000-0000-0000-0000-000000000002'
  and (image_url like 'https://placehold.co/%' or image_url like '%pinterest.com/pin%');

update restaurant_images
set image_url = 'https://www.thereveriesaigon.com/wp-content/uploads/2021/11/The-Reverie-Saigon-The-Royal-Pavilion-Private-Room-780x600-1.jpg',
    alt_text = 'Private dining room at The Royal Pavilion',
    updated_at = current_timestamp
where id = '30000000-0000-0000-0000-000000000024'
  and restaurant_id = '00000000-0000-0000-0000-000000000002'
  and (image_url like 'https://placehold.co/%' or image_url like '%pinterest.com/pin%');

update restaurant_images
set image_url = 'https://www.thereveriesaigon.com/wp-content/uploads/2021/11/detail-dining-the-royal-pavilion-f-the-reverie-saigon-1200x900.jpg',
    alt_text = 'Table setting at The Royal Pavilion',
    updated_at = current_timestamp
where id = '30000000-0000-0000-0000-000000000025'
  and restaurant_id = '00000000-0000-0000-0000-000000000002'
  and (image_url like 'https://placehold.co/%' or image_url like '%pinterest.com/pin%');
