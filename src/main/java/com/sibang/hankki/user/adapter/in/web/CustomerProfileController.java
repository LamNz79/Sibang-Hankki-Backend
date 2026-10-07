package com.sibang.hankki.user.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.CustomerProfile;
import com.sibang.hankki.user.application.port.in.CustomerProfileUseCase.UpdateCustomerProfileCommand;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/profile")
public class CustomerProfileController {

    private final CustomerProfileUseCase useCase;

    public CustomerProfileController(CustomerProfileUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    CustomerProfile get(@AuthenticationPrincipal SessionUser user) {
        return useCase.get(user.id());
    }

    @PutMapping
    CustomerProfile update(
            @RequestBody(required = false) UpdateProfileRequest request,
            @AuthenticationPrincipal SessionUser user) {
        UpdateCustomerProfileCommand command = request == null
                ? null
                : new UpdateCustomerProfileCommand(request.name(), request.email(), request.phone());
        return useCase.update(user.id(), command);
    }

    record UpdateProfileRequest(String name, String email, String phone) {
    }
}
