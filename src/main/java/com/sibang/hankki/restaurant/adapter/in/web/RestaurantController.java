package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.restaurant.application.model.RestaurantAvailabilityResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantResponse;
import com.sibang.hankki.restaurant.application.model.RestaurantSummaryResponse;
import com.sibang.hankki.restaurant.application.port.in.RestaurantAvailabilityUseCase;
import com.sibang.hankki.restaurant.application.port.in.RestaurantCatalogUseCase;
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

    private final RestaurantCatalogUseCase catalogUseCase;
    private final RestaurantAvailabilityUseCase availabilityUseCase;

    RestaurantController(
            RestaurantCatalogUseCase catalogUseCase,
            RestaurantAvailabilityUseCase availabilityUseCase) {
        this.catalogUseCase = catalogUseCase;
        this.availabilityUseCase = availabilityUseCase;
    }

    @GetMapping
    List<RestaurantSummaryResponse> restaurants() {
        return catalogUseCase.restaurants();
    }

    @GetMapping("/{slug}")
    ResponseEntity<RestaurantResponse> restaurant(@PathVariable String slug) {
        return catalogUseCase.restaurant(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/availability")
    RestaurantAvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam String date,
            @RequestParam int partySize) {
        return availabilityUseCase.availability(slug, date, partySize);
    }
}
