-- Stable prototype hero photos. Remaining gallery placeholders will be replaced by the owner media CRUD.
update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1515003197210-e0cd71810b5f?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'Modern Vietnamese dishes served at Anan Saigon'
where restaurant_id = '00000000-0000-0000-0000-000000000001' and sort_order = 1;

update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1552566626-52f8b828add9?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'Elegant dining room at The Royal Pavilion'
where restaurant_id = '00000000-0000-0000-0000-000000000002' and sort_order = 1;

update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'French-inspired dining room at The Refinery'
where restaurant_id = '00000000-0000-0000-0000-000000000003' and sort_order = 1;

update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'Japanese teppan dining at Mori Teppan'
where restaurant_id = '00000000-0000-0000-0000-000000000004' and sort_order = 1;

update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'Warm Vietnamese dining room at Hanoi Hearth'
where restaurant_id = '00000000-0000-0000-0000-000000000005' and sort_order = 1;

update restaurant_images set
    image_url = 'https://images.unsplash.com/photo-1516211697506-8360dbcfe9a4?auto=format&fit=crop&w=1200&q=80',
    alt_text = 'Riverside dining atmosphere at Han River Dining'
where restaurant_id = '00000000-0000-0000-0000-000000000006' and sort_order = 1;
