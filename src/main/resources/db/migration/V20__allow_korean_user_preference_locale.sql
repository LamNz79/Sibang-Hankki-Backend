alter table user_preferences
    drop constraint user_preferences_locale_check;

alter table user_preferences
    add constraint user_preferences_locale_check
        check (locale in ('vi', 'en', 'ko'));
