create table user_preferences (
    user_id uuid primary key references users (id) on delete cascade,
    theme_preset varchar(20) not null,
    display_density varchar(20) not null,
    table_page_size integer not null,
    date_format varchar(20) not null,
    locale varchar(10) not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint user_preferences_theme_preset_check
        check (theme_preset in ('CORAL', 'ROSE', 'TEAL', 'BLUE')),
    constraint user_preferences_display_density_check
        check (display_density in ('COMFORTABLE', 'COMPACT')),
    constraint user_preferences_table_page_size_check
        check (table_page_size in (10, 20, 50)),
    constraint user_preferences_date_format_check
        check (date_format in ('DD_MM_YYYY', 'MM_DD_YYYY')),
    constraint user_preferences_locale_check
        check (locale in ('vi', 'en'))
);
