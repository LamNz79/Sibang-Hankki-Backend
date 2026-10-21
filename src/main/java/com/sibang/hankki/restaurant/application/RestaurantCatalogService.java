package com.sibang.hankki.restaurant.application;

import com.sibang.hankki.restaurant.application.exception.InvalidBookingRequestException;
import com.sibang.hankki.restaurant.application.model.OwnerMenu;
import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
import com.sibang.hankki.restaurant.application.port.out.OwnerMenuPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantCatalogPort;
import com.sibang.hankki.restaurant.application.port.out.RestaurantData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantGalleryCount;
import com.sibang.hankki.restaurant.application.port.out.RestaurantImageData;
import com.sibang.hankki.restaurant.application.port.out.RestaurantTagData;
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

    private static final String NEUTRAL_HERO_ACCENT = "#f3f4f6";
    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RestaurantCatalogPort catalogPort;
    private final OwnerMenuPort menuPort;

    public RestaurantCatalogService(RestaurantCatalogPort catalogPort, OwnerMenuPort menuPort) {
        this.catalogPort = catalogPort;
        this.menuPort = menuPort;
    }

    @Override
    public List<RestaurantSummaryResponse> restaurants(Integer provinceCode, Integer wardCode) {
        if (provinceCode != null && provinceCode <= 0) {
            throw new InvalidBookingRequestException("provinceCode must be positive");
        }
        if (wardCode != null && wardCode <= 0) {
            throw new InvalidBookingRequestException("wardCode must be positive");
        }
        return responses(catalogPort.findAllActiveRestaurants(provinceCode, wardCode), false).stream()
                .map(RestaurantSummaryResponse::from)
                .toList();
    }

    @Override
    public List<RestaurantSummaryResponse> restaurantSummaries(List<UUID> restaurantIds) {
        if (restaurantIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, RestaurantData> activeRestaurants = catalogPort.findActiveRestaurantsByIds(restaurantIds).stream()
                .collect(java.util.stream.Collectors.toMap(RestaurantData::id, Function.identity()));
        List<RestaurantData> orderedRestaurants = restaurantIds.stream()
                .map(activeRestaurants::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return responses(orderedRestaurants, false).stream().map(RestaurantSummaryResponse::from).toList();
    }

    @Override
    public Optional<RestaurantResponse> restaurant(String slug) {
        return catalogPort.findActiveRestaurantBySlug(slug)
                .map(restaurant -> responses(List.of(restaurant), true).get(0));
    }

    private List<RestaurantResponse> responses(List<RestaurantData> restaurants, boolean includeDetails) {
        if (restaurants.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = restaurants.stream().map(RestaurantData::id).toList();
        Map<UUID, List<RestaurantTagData>> tags = groupByRestaurant(
                catalogPort.findTagsByRestaurantIds(ids), RestaurantTagData::restaurantId);
        Map<UUID, List<RestaurantBusinessHourData>> hours = groupByRestaurant(
                catalogPort.findBusinessHoursByRestaurantIds(ids), RestaurantBusinessHourData::restaurantId);
        Map<UUID, Long> galleryCounts = galleryCounts(catalogPort.countActiveImagesByRestaurantIds(ids));
        Map<UUID, List<RestaurantImageData>> images = groupByRestaurant(
                catalogPort.findImagesByRestaurantIds(ids), RestaurantImageData::restaurantId);

        return restaurants.stream()
                .map(restaurant -> response(restaurant, tags.getOrDefault(restaurant.id(), List.of()),
                        hours.getOrDefault(restaurant.id(), List.of()), galleryCounts.getOrDefault(restaurant.id(), 0L),
                        images.getOrDefault(restaurant.id(), List.of()), includeDetails))
                .toList();
    }

    private RestaurantResponse response(
            RestaurantData restaurant,
            List<RestaurantTagData> restaurantTags,
            List<RestaurantBusinessHourData> businessHours,
            long galleryCount,
            List<RestaurantImageData> images,
            boolean includeDetails) {
        List<String> benefits = restaurantTags.stream()
                .filter(RestaurantTagData::showInBenefits)
                .map(RestaurantTagData::tag)
                .toList();
        List<String> tags = restaurantTags.stream().map(RestaurantTagData::tag).map(this::tagLabel).toList();
        RestaurantImageData primaryImage = images.isEmpty() ? null : images.get(0);

        return new RestaurantResponse(
                restaurant.slug(),
                restaurant.name(),
                restaurant.citySlug(),
                restaurant.area(),
                restaurant.district(),
                restaurant.cuisineLabel(),
                restaurant.cuisineType(),
                0,
                0,
                restaurant.priceRange(),
                NEUTRAL_HERO_ACCENT,
                primaryImage == null ? null : primaryImage.imageUrl(),
                primaryImage == null ? restaurant.name() : primaryImage.altText(),
                openHours(businessHours),
                restaurant.address(),
                null,
                null,
                priceKey(restaurant.priceRange()),
                benefits,
                tags,
                Math.toIntExact(galleryCount),
                images.stream()
                        .map(image -> new RestaurantResponse.GalleryImage(
                                image.imageUrl(), image.altText() == null ? restaurant.name() : image.altText()))
                        .toList(),
                restaurant.description(),
                Map.of(),
                includeDetails ? publicMenu(menuPort.findAll(restaurant.id())) : List.of());
    }

    private List<RestaurantResponse.MenuCategory> publicMenu(OwnerMenu menu) {
        return menu.categories().stream()
                .map(category -> new RestaurantResponse.MenuCategory(
                        category.id(), category.name(), category.displayOrder(), menu.items().stream()
                                .filter(OwnerMenu.Item::available)
                                .filter(item -> category.id().equals(item.categoryId()))
                                .map(item -> new RestaurantResponse.MenuItem(
                                        item.id(), item.name(), item.description(), item.price(),
                                        item.currency(), item.imageUrl()))
                                .toList()))
                .filter(category -> !category.items().isEmpty())
                .toList();
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

}
