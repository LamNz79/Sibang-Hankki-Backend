package com.sibang.hankki.restaurant;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
class RestaurantCatalogService {

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogRepository repository;

    RestaurantCatalogService(RestaurantCatalogRepository repository) {
        this.repository = repository;
    }

    List<RestaurantSummaryResponse> restaurants() {
        return responses(repository.findAllActive(), false).stream()
                .map(RestaurantSummaryResponse::from)
                .toList();
    }

    Optional<RestaurantResponse> restaurant(String slug) {
        return repository.findActiveBySlug(slug)
                .map(restaurant -> responses(List.of(restaurant), true).get(0));
    }

    private List<RestaurantResponse> responses(List<RestaurantEntity> restaurants, boolean includeSlotMatrix) {
        if (restaurants.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = restaurants.stream().map(RestaurantEntity::getId).toList();
        Map<UUID, List<RestaurantTagEntity>> tags = groupByRestaurant(
                repository.findTagsByRestaurantIds(ids), RestaurantTagEntity::getRestaurantId);
        Map<UUID, List<RestaurantBusinessHourEntity>> hours = groupByRestaurant(
                repository.findBusinessHoursByRestaurantIds(ids), RestaurantBusinessHourEntity::getRestaurantId);
        Map<UUID, Long> galleryCounts = galleryCounts(repository.countActiveImagesByRestaurantIds(ids));

        return restaurants.stream()
                .map(restaurant -> response(restaurant, tags.getOrDefault(restaurant.getId(), List.of()),
                        hours.getOrDefault(restaurant.getId(), List.of()), galleryCounts.getOrDefault(restaurant.getId(), 0L),
                        includeSlotMatrix))
                .toList();
    }

    private RestaurantResponse response(
            RestaurantEntity restaurant,
            List<RestaurantTagEntity> restaurantTags,
            List<RestaurantBusinessHourEntity> businessHours,
            long galleryCount,
            boolean includeSlotMatrix) {
        RestaurantPrototypePresentation.Presentation presentation = RestaurantPrototypePresentation.forSlug(
                restaurant.getSlug());
        RestaurantAvailabilityMockData.Availability availability = RestaurantAvailabilityMockData.forSlug(
                restaurant.getSlug());
        List<String> benefits = restaurantTags.stream()
                .filter(RestaurantTagEntity::isShowInBenefits)
                .map(RestaurantTagEntity::getTag)
                .toList();
        if (availability != null && availability.showInBenefits()) {
            benefits = availability.availableFirst() ? prepend(benefits, "available") : append(benefits, "available");
        }
        List<String> tags = restaurantTags.stream().map(RestaurantTagEntity::getTag).map(this::tagLabel).toList();
        if (availability != null && availability.showInTags()) {
            tags = prepend(tags, "Available");
        }

        return new RestaurantResponse(
                restaurant.getSlug(),
                restaurant.getName(),
                restaurant.getCitySlug(),
                restaurant.getArea(),
                restaurant.getDistrict(),
                restaurant.getCuisineLabel(),
                restaurant.getCuisineType(),
                presentation.rating(),
                presentation.ratingCount(),
                restaurant.getPriceRange(),
                presentation.heroAccent(),
                openHours(businessHours),
                restaurant.getAddress(),
                availability == null ? "Availability unavailable" : "Available today from " + availability.availableFrom(),
                availability == null ? null : availability.availableFrom(),
                priceKey(restaurant.getPriceRange()),
                benefits,
                tags,
                Math.toIntExact(galleryCount),
                restaurant.getDescription(),
                includeSlotMatrix ? RestaurantAvailabilityMockData.slotMatrix(restaurant.getSlug()) : Map.of());
    }

    private <T> Map<UUID, List<T>> groupByRestaurant(List<T> values, Function<T, UUID> restaurantId) {
        Map<UUID, List<T>> grouped = new HashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(restaurantId.apply(value), ignored -> new java.util.ArrayList<>()).add(value);
        }
        return grouped;
    }

    private Map<UUID, Long> galleryCounts(Collection<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private String openHours(List<RestaurantBusinessHourEntity> businessHours) {
        return businessHours.stream()
                .filter(hour -> hour.getDayOfWeek() == 1)
                .findFirst()
                .or(() -> businessHours.stream().findFirst())
                .map(hour -> TIME.format(hour.getOpensAt()) + " - " + TIME.format(hour.getClosesAt()))
                .orElse("");
    }

    private String priceKey(String priceRange) {
        Matcher matcher = FIRST_NUMBER.matcher(priceRange == null ? "" : priceRange);
        return matcher.find() && Integer.parseInt(matcher.group()) < 300 ? "under300" : "over300";
    }

    private String tagLabel(String tag) {
        return switch (tag) {
            case "michelin" -> "Michelin";
            case "special_deal" -> "Special deal";
            case "date_night" -> "Date night";
            default -> tag;
        };
    }

    private List<String> append(List<String> values, String value) {
        return java.util.stream.Stream.concat(values.stream(), java.util.stream.Stream.of(value)).toList();
    }

    private List<String> prepend(List<String> values, String value) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(value), values.stream()).toList();
    }
}
