package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.Assignment;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.AssignmentCommand;
import com.sibang.hankki.admin.application.port.in.RestaurantAssignmentUseCase.AssignmentPage;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurant-assignments")
public class RestaurantAssignmentController {

    private final RestaurantAssignmentUseCase assignmentUseCase;

    public RestaurantAssignmentController(RestaurantAssignmentUseCase assignmentUseCase) {
        this.assignmentUseCase = assignmentUseCase;
    }

    @GetMapping
    public AssignmentPage findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID restaurantId) {
        return assignmentUseCase.findAll(page, size, query, role, restaurantId);
    }

    @PostMapping
    public ResponseEntity<Assignment> create(@RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentUseCase.create(request.toCommand()));
    }

    @PutMapping("/{userId}")
    public Assignment update(@PathVariable UUID userId, @RequestBody AssignmentRequest request) {
        return assignmentUseCase.update(userId, request.toCommand());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID userId) {
        assignmentUseCase.remove(userId);
    }

    record AssignmentRequest(UUID userId, UUID restaurantId, String role) {
        AssignmentCommand toCommand() {
            return new AssignmentCommand(userId, restaurantId, role);
        }
    }
}
