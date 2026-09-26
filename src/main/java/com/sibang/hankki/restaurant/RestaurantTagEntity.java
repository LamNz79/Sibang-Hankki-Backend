package com.sibang.hankki.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "restaurant_tags")
class RestaurantTagEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 30)
    private String tag;

    @Column(nullable = false)
    private boolean showInBenefits;

    UUID getRestaurantId() {
        return restaurantId;
    }

    String getTag() {
        return tag;
    }

    boolean isShowInBenefits() {
        return showInBenefits;
    }
}
