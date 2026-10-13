package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurantPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUserPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminStatusUseCase;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SystemAdminController {

    private final SystemAdminReadUseCase readUseCase;
    private final SystemAdminStatusUseCase statusUseCase;

    public SystemAdminController(SystemAdminReadUseCase readUseCase, SystemAdminStatusUseCase statusUseCase) {
        this.readUseCase = readUseCase;
        this.statusUseCase = statusUseCase;
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
            @RequestParam(required = false) String citySlug) {
        return readUseCase.findRestaurants(page, size, query, status, citySlug);
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
}
