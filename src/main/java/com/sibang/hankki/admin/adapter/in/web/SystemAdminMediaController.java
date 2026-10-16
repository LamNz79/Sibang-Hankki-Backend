package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminMediaUseCase;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/media")
public class SystemAdminMediaController {

    private final SystemAdminMediaUseCase useCase;

    public SystemAdminMediaController(SystemAdminMediaUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public OwnerMedia get(@PathVariable UUID restaurantId) {
        return useCase.get(restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMedia.Image create(
            @PathVariable UUID restaurantId, @RequestBody OwnerMedia.Update request) {
        return useCase.create(restaurantId, request);
    }

    @PutMapping("/{mediaId}")
    public OwnerMedia.Image update(
            @PathVariable UUID restaurantId,
            @PathVariable UUID mediaId,
            @RequestBody OwnerMedia.Update request) {
        return useCase.update(restaurantId, mediaId, request);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID restaurantId, @PathVariable UUID mediaId) {
        useCase.delete(restaurantId, mediaId);
    }
}
