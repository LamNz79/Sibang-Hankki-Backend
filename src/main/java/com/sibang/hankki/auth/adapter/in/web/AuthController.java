package com.sibang.hankki.auth.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal SessionUser user) {
        return new MeResponse(user.id(), user.userid(), user.role(), user.restaurantId());
    }

    record CsrfResponse(String headerName, String parameterName, String token) {
    }

    record MeResponse(UUID id, String userid, String role, UUID restaurantId) {
    }
}
