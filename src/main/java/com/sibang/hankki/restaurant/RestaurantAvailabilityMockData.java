package com.sibang.hankki.restaurant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class RestaurantAvailabilityMockData {

    private static final Map<String, Availability> BY_SLUG = Map.of(
            "anan-saigon", new Availability(0, "18:30", true, false, false),
            "royal-pavilion", new Availability(1, "18:30", true, true, false),
            "refinery", new Availability(2, "18:30", false, false, false),
            "mori-teppan", new Availability(3, "20:00", true, true, true),
            "hanoi-hearth", new Availability(4, "18:00", true, true, true),
            "han-river-dining", new Availability(5, "18:30", true, true, true));

    private RestaurantAvailabilityMockData() {
    }

    static Availability forSlug(String slug) {
        return BY_SLUG.get(slug);
    }

    static Map<String, Map<String, List<String>>> slotMatrix(String slug) {
        Availability availability = forSlug(slug);
        if (availability == null) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        return IntStream.range(0, 61).boxed().collect(java.util.stream.Collectors.toMap(
                offset -> today.plusDays(offset).toString(),
                offset -> slots(today.plusDays(offset), offset, availability.variant()),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static Map<String, List<String>> slots(LocalDate date, int offset, int variant) {
        if ((variant % 2 == 0 && date.getDayOfWeek() == DayOfWeek.MONDAY)
                || (variant % 2 == 1 && date.getDayOfWeek() == DayOfWeek.TUESDAY)) {
            return Map.of("2", List.of(), "4", List.of(), "6", List.of());
        }
        return switch (Math.floorMod(offset + date.getDayOfWeek().getValue() + variant, 4)) {
            case 0 -> Map.of("2", List.of("18:00", "18:30", "19:00", "19:30", "20:00"),
                    "4", List.of("18:30", "19:00", "20:00"), "6", List.of("19:30", "20:30"));
            case 1 -> Map.of("2", List.of("17:30", "18:30", "19:30", "20:30"),
                    "4", List.of("18:00", "19:00", "20:30"), "6", List.of("20:00"));
            case 2 -> Map.of("2", List.of("18:30", "19:00", "20:00", "20:30"),
                    "4", List.of("19:00", "19:30"), "6", List.of("20:30"));
            default -> Map.of("2", List.of("18:00", "19:00", "19:30"),
                    "4", List.of("18:30", "20:00"), "6", List.of("19:30"));
        };
    }

    record Availability(
            int variant,
            String availableFrom,
            boolean showInBenefits,
            boolean availableFirst,
            boolean showInTags) {
    }
}
