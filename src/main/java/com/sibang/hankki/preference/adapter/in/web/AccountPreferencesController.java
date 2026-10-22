package com.sibang.hankki.preference.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.Preferences;
import com.sibang.hankki.preference.application.port.in.AccountPreferencesUseCase.UpdatePreferencesCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/preferences")
public class AccountPreferencesController {

    private final AccountPreferencesUseCase useCase;

    public AccountPreferencesController(AccountPreferencesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    Preferences get(@AuthenticationPrincipal SessionUser user) {
        return useCase.get(user.id());
    }

    @PutMapping
    Preferences update(
            @RequestBody(required = false) PreferencesRequest request,
            @AuthenticationPrincipal SessionUser user) {
        UpdatePreferencesCommand command = request == null
                ? null
                : new UpdatePreferencesCommand(
                        request.themePreset(),
                        request.displayDensity(),
                        request.tablePageSize(),
                        request.dateFormat(),
                        request.locale());
        return useCase.update(user.id(), command);
    }

    @DeleteMapping
    ResponseEntity<Void> delete(@AuthenticationPrincipal SessionUser user) {
        useCase.delete(user.id());
        return ResponseEntity.noContent().build();
    }

    record PreferencesRequest(
            String themePreset,
            String displayDensity,
            Integer tablePageSize,
            String dateFormat,
            String locale) {
    }
}
