alter table restaurant_booking_settings
    drop constraint restaurant_booking_settings_manual_confirmation_check;

alter table restaurant_booking_settings
    add constraint restaurant_booking_settings_manual_confirmation_check check (
        case
            when confirmation_mode = 'HYBRID' then manual_confirmation_min_party_size is not null
                and manual_confirmation_min_party_size > 0
            when confirmation_mode in ('AUTO', 'MANUAL') then manual_confirmation_min_party_size is null
            else false
        end
    );
