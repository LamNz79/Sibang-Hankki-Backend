package com.sibang.hankki;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class RestaurantData {

        static final List<RestaurantResponse> RESTAURANTS = List.of(
                        restaurant("anan-saigon", "Anan Saigon", "ho-chi-minh-city", "District 1", "District 1",
                                        "Vietnamese contemporary", "vietnamese", 4.7, 139, "150K - 350K", "#f6ede4",
                                        "11:30 - 22:00", "District 1", "Available today from 18:30", "18:30",
                                        "under300",
                                        List.of("michelin", "special_deal", "available"),
                                        List.of("Michelin", "Special deal", "Date night"),
                                        5,
                                        "Modern Vietnamese tasting menus with refined plating and a lively city-dining atmosphere.",
                                        0),
                        restaurant("royal-pavilion", "The Royal Pavilion", "ho-chi-minh-city", "District 1",
                                        "District 1",
                                        "Chinese", "chinese", 4.7, 166, "350K - 500K", "#edf5f4", "11:30 - 22:00",
                                        "District 1",
                                        "Available today from 18:30", "18:30", "over300",
                                        List.of("available", "date_night"),
                                        List.of("Michelin", "Special deal", "Date night"), 5,
                                        "Elegant Cantonese dining with private-table ambience and evening reservation demand.",
                                        1),
                        restaurant("refinery", "The Refinery", "ho-chi-minh-city", "District 1", "District 1", "French",
                                        "western",
                                        4.7, 121, "350K - 500K", "#eef4f7", "11:30 - 22:00", "District 1",
                                        "Available today from 18:30", "18:30", "over300", List.of("date_night"),
                                        List.of("Date night"), 4,
                                        "French comfort dining in a restored colonial setting with strong dinner demand.",
                                        2),
                        restaurant("mori-teppan", "Mori Teppan", "ho-chi-minh-city", "Binh Thanh", "Binh Thanh",
                                        "Japanese", "japanese",
                                        4.6, 98, "150K - 300K", "#edf2f8", "17:30 - 22:30", "Binh Thanh",
                                        "Available today from 20:00",
                                        "20:00", "under300", List.of("available"), List.of("Available"), 3,
                                        "Interactive teppan-style dinner counters that work best for small evening parties.",
                                        3),
                        restaurant("hanoi-hearth", "Hanoi Hearth", "hanoi", "Hoan Kiem", "Hoan Kiem", "Vietnamese",
                                        "vietnamese",
                                        4.6, 87, "150K - 300K", "#f2ede6", "11:00 - 22:00", "Hoan Kiem, Hanoi",
                                        "Available today from 18:00",
                                        "18:00", "under300", List.of("available", "date_night"),
                                        List.of("Available", "Date night"), 4,
                                        "Contemporary northern Vietnamese dishes in a warm dining room near the Old Quarter.",
                                        4),
                        restaurant("han-river-dining", "Han River Dining", "da-nang", "Son Tra", "Son Tra",
                                        "Vietnamese seafood", "vietnamese",
                                        4.5, 74, "150K - 350K", "#e8f3f5", "16:30 - 22:30", "Son Tra, Da Nang",
                                        "Available today from 18:30",
                                        "18:30", "under300", List.of("available", "special_deal"),
                                        List.of("Available", "Special deal"), 5,
                                        "Relaxed riverside dining focused on central Vietnamese seafood and shareable evening menus.",
                                        5));

        private RestaurantData() {
        }

        private static RestaurantResponse restaurant(
                        String slug, String name, String citySlug, String area, String district, String cuisineLabel,
                        String cuisineKey,
                        double rating, int ratingCount, String priceRangeLabel, String heroAccent, String openHours,
                        String address,
                        String availableText, String availableFrom, String priceKey, List<String> benefits,
                        List<String> tags,
                        int galleryCount, String summary, int variant) {
                return new RestaurantResponse(slug, name, citySlug, area, district, cuisineLabel, cuisineKey, rating,
                                ratingCount,
                                priceRangeLabel, heroAccent, openHours, address, availableText, availableFrom, priceKey,
                                benefits, tags,
                                galleryCount, summary, slotMatrix(variant));
        }

        private static Map<String, Map<String, List<String>>> slotMatrix(int variant) {
                LocalDate today = LocalDate.now();
                return IntStream.range(0, 61).boxed().collect(java.util.stream.Collectors.toMap(
                                offset -> today.plusDays(offset).toString(),
                                offset -> slots(today.plusDays(offset), offset, variant),
                                (first, second) -> first,
                                java.util.LinkedHashMap::new));
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
}
