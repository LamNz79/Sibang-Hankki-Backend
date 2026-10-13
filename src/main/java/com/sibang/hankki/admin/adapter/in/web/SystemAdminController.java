package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminRestaurantPage;
import com.sibang.hankki.admin.application.port.in.SystemAdminReadUseCase.AdminUserPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SystemAdminController {

    private final SystemAdminReadUseCase readUseCase;

    public SystemAdminController(SystemAdminReadUseCase readUseCase) {
        this.readUseCase = readUseCase;
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
}
