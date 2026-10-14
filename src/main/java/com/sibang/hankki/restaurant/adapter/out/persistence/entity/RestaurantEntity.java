package com.sibang.hankki.restaurant.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "restaurants")
public class RestaurantEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 160)
    private String cuisineType;

    @Column(length = 160)
    private String cuisineLabel;

    @Column(nullable = false, length = 160)
    private String citySlug;

    @Column(length = 160)
    private String area;

    @Column(length = 160)
    private String district;

    private Integer provinceCode;

    @Column(length = 160)
    private String provinceName;

    private Integer wardCode;

    @Column(length = 160)
    private String wardName;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(length = 40)
    private String priceRange;

    @Column(nullable = false, length = 30)
    private String approvalStatus = "DRAFT";

    private Instant deletedAt;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public RestaurantEntity() {
    }

    public RestaurantEntity(
            UUID id,
            String slug,
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            String approvalStatus) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.cuisineType = cuisineType;
        this.cuisineLabel = cuisineLabel;
        this.citySlug = citySlug;
        this.area = area;
        this.district = district;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.timezone = timezone;
        this.priceRange = priceRange;
        this.approvalStatus = approvalStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCuisineType() {
        return cuisineType;
    }

    public String getCuisineLabel() {
        return cuisineLabel;
    }

    public String getCitySlug() {
        return citySlug;
    }

    public String getArea() {
        return area;
    }

    public String getDistrict() {
        return district;
    }

    public Integer getProvinceCode() {
        return provinceCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public Integer getWardCode() {
        return wardCode;
    }

    public String getWardName() {
        return wardName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void updateOwnerProfile(
            String name,
            String description,
            String cuisineLabel,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String priceRange) {
        this.name = name;
        this.description = description;
        this.cuisineLabel = cuisineLabel;
        this.area = area;
        this.district = district;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.priceRange = priceRange;
    }

    public void updateApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public void updateAdminProfile(
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String citySlug,
            String area,
            String district,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            Integer provinceCode,
            String provinceName,
            Integer wardCode,
            String wardName) {
        this.name = name;
        this.description = description;
        this.cuisineType = cuisineType;
        this.cuisineLabel = cuisineLabel;
        this.citySlug = citySlug;
        this.area = area;
        this.district = district;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.timezone = timezone;
        this.priceRange = priceRange;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.wardCode = wardCode;
        this.wardName = wardName;
    }

    public void updateAdministrativeLocation(
            Integer provinceCode, String provinceName, Integer wardCode, String wardName) {
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.wardCode = wardCode;
        this.wardName = wardName;
    }

    public void archive(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
