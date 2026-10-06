package com.sibang.hankki.restaurant.adapter.in.web;

import com.sibang.hankki.auth.SessionUser;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.application.port.out.RestaurantBusinessHourData;
import java.time.LocalTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/settings")
public class OwnerBusinessHoursController {

    private final OwnerBusinessHoursUseCase businessHoursUseCase;
    private final OwnerSlotRegenerationUseCase regenerationUseCase;

    public OwnerBusinessHoursController(
            OwnerBusinessHoursUseCase businessHoursUseCase,
            OwnerSlotRegenerationUseCase regenerationUseCase) {
        this.businessHoursUseCase = businessHoursUseCase;
        this.regenerationUseCase = regenerationUseCase;
    }

    @GetMapping("/business-hours")
    public List<BusinessHourResponse> get(@AuthenticationPrincipal SessionUser user) {
        return businessHoursUseCase.get(user.restaurantId()).stream().map(BusinessHourResponse::from).toList();
    }

    @PutMapping("/business-hours")
    public BusinessHoursUpdateResponse update(
            @RequestBody BusinessHoursRequest request,
            @AuthenticationPrincipal SessionUser user) {
        OwnerBusinessHoursUseCase.UpdateResult result = businessHoursUseCase.update(
                user.restaurantId(), request == null || request.hours() == null
                        ? null
                        : request.hours().stream().map(hour -> hour.toData(user)).toList());
        return new BusinessHoursUpdateResponse(
                result.hours().stream().map(BusinessHourResponse::from).toList(),
                result.regeneration().deletedSlots(), result.regeneration().generatedSlots());
    }

    @PostMapping("/regenerate-slots")
    public SlotRegenerationResponse regenerate(@AuthenticationPrincipal SessionUser user) {
        OwnerSlotRegenerationUseCase.Result result = regenerationUseCase.regenerate(user.restaurantId());
        return new SlotRegenerationResponse(result.deletedSlots(), result.generatedSlots());
    }

    public record BusinessHoursRequest(List<BusinessHourRequest> hours) {
    }

    public record BusinessHourRequest(short dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        private RestaurantBusinessHourData toData(SessionUser user) {
            return new RestaurantBusinessHourData(user.restaurantId(), dayOfWeek, opensAt, closesAt);
        }
    }

    public record BusinessHourResponse(short dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        private static BusinessHourResponse from(RestaurantBusinessHourData hour) {
            return new BusinessHourResponse(hour.dayOfWeek(), hour.opensAt(), hour.closesAt());
        }
    }

    public record BusinessHoursUpdateResponse(
            List<BusinessHourResponse> hours, int deletedSlots, int generatedSlots) {
    }

    public record SlotRegenerationResponse(int deletedSlots, int generatedSlots) {
    }
}
