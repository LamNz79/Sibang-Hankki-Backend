package com.sibang.hankki.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 160)
    private String cuisineType;

    @Column(nullable = false, length = 160)
    private String citySlug;

    @Column(length = 160)
    private String district;

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

    protected RestaurantEntity() {
    }
}
