package com.sibang.hankki;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
class RestaurantController {

    @GetMapping
    List<RestaurantSummaryResponse> restaurants() {
        return RestaurantData.RESTAURANTS.stream()
                .map(RestaurantSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{slug}")
    ResponseEntity<RestaurantResponse> restaurant(@PathVariable String slug) {
        return RestaurantData.RESTAURANTS.stream()
                .filter(restaurant -> restaurant.slug().equals(slug))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
