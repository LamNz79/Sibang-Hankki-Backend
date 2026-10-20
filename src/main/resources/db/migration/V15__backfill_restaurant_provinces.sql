update restaurants
set province_code = 79,
    province_name = 'Thành phố Hồ Chí Minh'
where province_code is null
  and city_slug in ('ho-chi-minh-city', 'ho-chi-minh');

update restaurants
set province_code = 1,
    province_name = 'Thành phố Hà Nội'
where province_code is null
  and city_slug = 'hanoi';

update restaurants
set province_code = 48,
    province_name = 'Thành phố Đà Nẵng'
where province_code is null
  and city_slug = 'da-nang';
