package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantGalleryCount;
import com.sibang.hankki.restaurant.application.port.out.RestaurantTagData;
import com.sibang.hankki.restaurant.application.prototype.RestaurantAvailabilityMockData;
import com.sibang.hankki.restaurant.application.prototype.RestaurantPrototypePresentation;
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
public class RestaurantCatalogService implements RestaurantCatalogUseCase {

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogPort catalogPort;

    public RestaurantCatalogService(RestaurantCatalogPort catalogPort) {
        this.catalogPort = catalogPort;
    }

    @Override
    public List<RestaurantSummaryResponse> restaurants() {
        return responses(catalogPort.findAllActiveRestaurants(), false).stream()
                .map(RestaurantSummaryResponse::from)
                .toList();
    }

    @Override
    public Optional<RestaurantResponse> restaurant(String slug) {
        return catalogPort.findActiveRestaurantBySlug(slug)
                .map(restaurant -> responses(List.of(restaurant), true).get(0));
    }

    private List<RestaurantResponse> responses(List<RestaurantData> restaurants, boolean includeSlotMatrix) {
        if (restaurants.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = restaurants.stream().map(RestaurantData::id).toList();
        Map<UUID, List<RestaurantTagData>> tags = groupByRestaurant(
                catalogPort.findTagsByRestaurantIds(ids), RestaurantTagData::restaurantId);
        Map<UUID, List<RestaurantBusinessHourData>> hours = groupByRestaurant(
                catalogPort.findBusinessHoursByRestaurantIds(ids), RestaurantBusinessHourData::restaurantId);
        Map<UUID, Long> galleryCounts = galleryCounts(catalogPort.countActiveImagesByRestaurantIds(ids));

        return restaurants.stream()
                .map(restaurant -> response(restaurant, tags.getOrDefault(restaurant.id(), List.of()),
                        hours.getOrDefault(restaurant.id(), List.of()), galleryCounts.getOrDefault(restaurant.id(), 0L),
                        includeSlotMatrix))
                .toList();
    }

    private RestaurantResponse response(
            RestaurantData restaurant,
            List<RestaurantTagData> restaurantTags,
            List<RestaurantBusinessHourData> businessHours,
            long galleryCount,
            boolean includeSlotMatrix) {
        RestaurantPrototypePresentation.Presentation presentation = RestaurantPrototypePresentation.forSlug(
                restaurant.slug());
        RestaurantAvailabilityMockData.Availability availability = RestaurantAvailabilityMockData.forSlug(
                restaurant.slug());
        List<String> benefits = restaurantTags.stream()
                .filter(RestaurantTagData::showInBenefits)
                .map(RestaurantTagData::tag)
                .toList();
        if (availability != null && availability.showInBenefits()) {
            benefits = availability.availableFirst() ? prepend(benefits, "available") : append(benefits, "available");
        }
        List<String> tags = restaurantTags.stream().map(RestaurantTagData::tag).map(this::tagLabel).toList();
        if (availability != null && availability.showInTags()) {
            tags = prepend(tags, "Available");
        }

        return new RestaurantResponse(
                restaurant.slug(),
                restaurant.name(),
                restaurant.citySlug(),
                restaurant.area(),
                restaurant.district(),
                restaurant.cuisineLabel(),
                restaurant.cuisineType(),
                presentation.rating(),
                presentation.ratingCount(),
                restaurant.priceRange(),
                presentation.heroAccent(),
                openHours(businessHours),
                restaurant.address(),
                availability == null ? "Availability unavailable" : "Available today from " + availability.availableFrom(),
                availability == null ? null : availability.availableFrom(),
                priceKey(restaurant.priceRange()),
                benefits,
                tags,
                Math.toIntExact(galleryCount),
                restaurant.description(),
                includeSlotMatrix ? RestaurantAvailabilityMockData.slotMatrix(restaurant.slug()) : Map.of());
    }

    private <T> Map<UUID, List<T>> groupByRestaurant(List<T> values, Function<T, UUID> restaurantId) {
        Map<UUID, List<T>> grouped = new HashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(restaurantId.apply(value), ignored -> new java.util.ArrayList<>()).add(value);
        }
        return grouped;
    }

    private Map<UUID, Long> galleryCounts(Collection<RestaurantGalleryCount> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (RestaurantGalleryCount row : rows) {
            counts.put(row.restaurantId(), row.count());
        }
        return counts;
    }

    private String openHours(List<RestaurantBusinessHourData> businessHours) {
        return businessHours.stream()
                .filter(hour -> hour.dayOfWeek() == 1)
                .findFirst()
                .or(() -> businessHours.stream().findFirst())
                .map(hour -> TIME.format(hour.opensAt()) + " - " + TIME.format(hour.closesAt()))
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
