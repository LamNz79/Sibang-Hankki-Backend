package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.application.model.OwnerMedia;
import com.sibang.hankki.restaurant.application.port.in.OwnerMediaUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/owner/media")
public class OwnerMediaController {

    private final OwnerMediaUseCase useCase;

    public OwnerMediaController(OwnerMediaUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public OwnerMedia get(@AuthenticationPrincipal SessionUser user) {
        return useCase.get(user.restaurantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerMedia.Image create(
            @RequestBody OwnerMedia.Update request, @AuthenticationPrincipal SessionUser user) {
        return useCase.create(user.restaurantId(), request);
    }

    @PutMapping("/{id}")
    public OwnerMedia.Image update(
            @PathVariable UUID id,
            @RequestBody OwnerMedia.Update request,
            @AuthenticationPrincipal SessionUser user) {
        return useCase.update(user.restaurantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal SessionUser user) {
        useCase.delete(user.restaurantId(), id);
    }
}
