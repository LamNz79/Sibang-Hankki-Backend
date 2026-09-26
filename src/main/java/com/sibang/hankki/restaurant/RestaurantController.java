package com.sibang.hankki.restaurant;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
class RestaurantController {

    private final RestaurantCatalogService catalogService;
    private final RestaurantAvailabilityService availabilityService;

    RestaurantController(
            RestaurantCatalogService catalogService,
            RestaurantAvailabilityService availabilityService) {
        this.catalogService = catalogService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    List<RestaurantSummaryResponse> restaurants() {
        return catalogService.restaurants();
    }

    @GetMapping("/{slug}")
    ResponseEntity<RestaurantResponse> restaurant(@PathVariable String slug) {
        return catalogService.restaurant(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/availability")
    RestaurantAvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam String date,
            @RequestParam int partySize) {
        return availabilityService.availability(slug, date, partySize);
    }
}
