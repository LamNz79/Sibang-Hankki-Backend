package com.sibang.hankki.preference.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_preferences")
public class UserPreferencesEntity {

    @Id
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String themePreset;

    @Column(nullable = false, length = 20)
    private String displayDensity;

    @Column(nullable = false)
    private int tablePageSize;

    @Column(nullable = false, length = 20)
    private String dateFormat;

    @Column(nullable = false, length = 10)
    private String locale;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected UserPreferencesEntity() {
    }

    public UserPreferencesEntity(
            UUID userId,
            String themePreset,
            String displayDensity,
            int tablePageSize,
            String dateFormat,
            String locale) {
        this.userId = userId;
        replace(themePreset, displayDensity, tablePageSize, dateFormat, locale);
    }

    public void replace(
            String themePreset,
            String displayDensity,
            int tablePageSize,
            String dateFormat,
            String locale) {
        this.themePreset = themePreset;
        this.displayDensity = displayDensity;
        this.tablePageSize = tablePageSize;
        this.dateFormat = dateFormat;
        this.locale = locale;
    }

    public String getThemePreset() {
        return themePreset;
    }

    public String getDisplayDensity() {
        return displayDensity;
    }

    public int getTablePageSize() {
        return tablePageSize;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public String getLocale() {
        return locale;
    }
}
