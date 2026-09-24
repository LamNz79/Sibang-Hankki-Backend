package com.sibang.hankki.user;

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
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String userid;

    @Column(length = 320)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "text")
    private String profileImage;

    @Column(nullable = false, length = 20)
    private String role = "CUSTOMER";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    private UUID restaurantId;

    @Column(nullable = false)
    private Instant statusChangedAt = Instant.now();

    private Instant deletedAt;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }
}
