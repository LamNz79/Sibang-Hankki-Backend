package com.sibang.hankki.auth.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.user.application.port.in.RegisterCustomerUseCase;
import com.sibang.hankki.user.application.port.in.RegisterCustomerUseCase.RegisterCustomerCommand;
import com.sibang.hankki.user.application.port.in.RegisterCustomerUseCase.RegisteredCustomer;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterCustomerUseCase registerCustomerUseCase;

    public AuthController(RegisterCustomerUseCase registerCustomerUseCase) {
        this.registerCustomerUseCase = registerCustomerUseCase;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal SessionUser user) {
        return new MeResponse(user.id(), user.userid(), user.role(), user.restaurantId());
    }

    @PostMapping("/register")
    ResponseEntity<RegisterResponse> register(@RequestBody(required = false) RegisterRequest request) {
        RegisterCustomerCommand command = request == null
                ? null
                : new RegisterCustomerCommand(request.userid(), request.email(), request.name(), request.password());
        RegisteredCustomer customer = registerCustomerUseCase.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.from(customer));
    }

    record CsrfResponse(String headerName, String parameterName, String token) {
    }

    record MeResponse(UUID id, String userid, String role, UUID restaurantId) {
    }

    record RegisterRequest(String userid, String email, String name, String password) {

        @Override
        public String toString() {
            return "RegisterRequest[userid=" + userid + ", email=" + email
                    + ", name=" + name + ", password=[PROTECTED]]";
        }
    }

    record RegisterResponse(UUID id, String userid, String email, String name, String role) {

        static RegisterResponse from(RegisteredCustomer customer) {
            return new RegisterResponse(
                    customer.id(), customer.userid(), customer.email(), customer.name(), customer.role());
        }
    }
}
