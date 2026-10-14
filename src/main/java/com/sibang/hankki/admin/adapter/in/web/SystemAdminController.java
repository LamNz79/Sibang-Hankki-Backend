package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurantPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUserPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.AdminRestaurantDetail;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.CreateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminRestaurantUseCase.UpdateRestaurantCommand;
import com.sibang.hankki.admin.application.port.in.SystemAdminStatusUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SystemAdminController {

    private final SystemAdminReadUseCase readUseCase;
    private final SystemAdminStatusUseCase statusUseCase;
    private final SystemAdminRestaurantUseCase restaurantUseCase;

    public SystemAdminController(
            SystemAdminReadUseCase readUseCase,
            SystemAdminStatusUseCase statusUseCase,
            SystemAdminRestaurantUseCase restaurantUseCase) {
        this.readUseCase = readUseCase;
        this.statusUseCase = statusUseCase;
        this.restaurantUseCase = restaurantUseCase;
    }

    @GetMapping("/users")
    public AdminUserPage findUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return readUseCase.findUsers(page, size, query, role, status);
    }

    @GetMapping("/restaurants")
    public AdminRestaurantPage findRestaurants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String citySlug,
            @RequestParam(required = false) Integer provinceCode) {
        return readUseCase.findRestaurants(page, size, query, status, citySlug, provinceCode);
    }

    @GetMapping("/restaurants/{restaurantId}")
    public AdminRestaurantDetail findRestaurant(@PathVariable UUID restaurantId) {
        return restaurantUseCase.findById(restaurantId);
    }

    @PostMapping("/restaurants")
    public ResponseEntity<AdminRestaurantDetail> createRestaurant(@RequestBody RestaurantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantUseCase.create(request.toCommand()));
    }

    @PutMapping("/restaurants/{restaurantId}")
    public AdminRestaurantDetail updateRestaurant(
            @PathVariable UUID restaurantId,
            @RequestBody RestaurantUpdateRequest request) {
        return restaurantUseCase.update(restaurantId, request.toCommand());
    }

    @DeleteMapping("/restaurants/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveRestaurant(@PathVariable UUID restaurantId) {
        restaurantUseCase.archive(restaurantId);
    }

    @PatchMapping("/users/{userId}/status")
    public SystemAdminReadUseCase.AdminUser updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody StatusUpdateRequest request) {
        return statusUseCase.updateUserStatus(userId, request.status());
    }

    @PatchMapping("/restaurants/{restaurantId}/status")
    public SystemAdminReadUseCase.AdminRestaurant updateRestaurantStatus(
            @PathVariable UUID restaurantId,
            @RequestBody StatusUpdateRequest request) {
        return statusUseCase.updateRestaurantStatus(restaurantId, request.status());
    }

    record StatusUpdateRequest(String status) {
    }

    record RestaurantCreateRequest(
            String slug,
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            Integer provinceCode,
            Integer wardCode) {

        CreateRestaurantCommand toCommand() {
            return new CreateRestaurantCommand(
                    slug, name, description, cuisineType, cuisineLabel, address, phone, email, timezone, priceRange,
                    provinceCode, wardCode);
        }
    }

    record RestaurantUpdateRequest(
            String name,
            String description,
            String cuisineType,
            String cuisineLabel,
            String address,
            String phone,
            String email,
            String timezone,
            String priceRange,
            Integer provinceCode,
            Integer wardCode) {

        UpdateRestaurantCommand toCommand() {
            return new UpdateRestaurantCommand(
                    name, description, cuisineType, cuisineLabel, address, phone, email, timezone, priceRange,
                    provinceCode, wardCode);
        }
    }
}
