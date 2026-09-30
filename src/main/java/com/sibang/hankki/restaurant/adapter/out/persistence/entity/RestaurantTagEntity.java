package com.sibang.hankki.restaurant.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "restaurant_tags")
public class RestaurantTagEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 30)
    private String tag;

    @Column(nullable = false)
    private boolean showInBenefits;

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public String getTag() {
        return tag;
    }

    public boolean isShowInBenefits() {
        return showInBenefits;
    }
}
