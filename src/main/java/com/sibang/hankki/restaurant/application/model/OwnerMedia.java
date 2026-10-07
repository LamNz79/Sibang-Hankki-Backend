package com.sibang.hankki.restaurant.application.model;

import java.util.List;
import java.util.UUID;

public record OwnerMedia(List<Image> images) {
    public record Image(UUID id, String imageUrl, String altText, int sortOrder) {
    }

    public record Update(String imageUrl, String altText, int sortOrder) {
    }
}
