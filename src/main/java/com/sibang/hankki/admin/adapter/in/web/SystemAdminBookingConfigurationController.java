package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminBookingConfigurationUseCase;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerBusinessHoursController.BusinessHourResponse;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerBusinessHoursController.BusinessHoursRequest;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerBusinessHoursController.BusinessHoursUpdateResponse;
import com.sibang.hankki.restaurant.adapter.in.web.OwnerBusinessHoursController.SlotRegenerationResponse;
import com.sibang.hankki.restaurant.application.model.BookingSettingsUpdate;
import com.sibang.hankki.restaurant.application.model.OwnerSettings;
import com.sibang.hankki.restaurant.application.port.in.OwnerBusinessHoursUseCase;
import com.sibang.hankki.restaurant.application.port.in.OwnerSlotRegenerationUseCase;
import com.sibang.hankki.restaurant.domain.model.ConfirmationMode;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}")
public class SystemAdminBookingConfigurationController {

    private final SystemAdminBookingConfigurationUseCase useCase;

    public SystemAdminBookingConfigurationController(SystemAdminBookingConfigurationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/booking-settings")
    public BookingSettingsResponse getBookingSettings(@PathVariable UUID restaurantId) {
        return BookingSettingsResponse.from(useCase.getBookingSettings(restaurantId));
    }

    @PutMapping("/booking-settings")
    public BookingSettingsResponse updateBookingSettings(
            @PathVariable UUID restaurantId,
            @RequestBody BookingSettingsRequest request) {
        return BookingSettingsResponse.from(useCase.updateBookingSettings(
                restaurantId, request == null ? null : request.toUpdate()));
    }

    @GetMapping("/business-hours")
    public List<BusinessHourResponse> getBusinessHours(@PathVariable UUID restaurantId) {
        return useCase.getBusinessHours(restaurantId).stream().map(BusinessHourResponse::from).toList();
    }

    @PutMapping("/business-hours")
    public BusinessHoursUpdateResponse updateBusinessHours(
            @PathVariable UUID restaurantId,
            @RequestBody BusinessHoursRequest request) {
        OwnerBusinessHoursUseCase.UpdateResult result = useCase.updateBusinessHours(
                restaurantId, request == null || request.hours() == null
                        ? null
                        : request.hours().stream().map(hour -> hour.toData(restaurantId)).toList());
        return new BusinessHoursUpdateResponse(
                result.hours().stream().map(BusinessHourResponse::from).toList(),
                result.regeneration().deletedSlots(), result.regeneration().generatedSlots());
    }

    @PostMapping("/booking-slots/regenerate")
    public SlotRegenerationResponse regenerateSlots(@PathVariable UUID restaurantId) {
        OwnerSlotRegenerationUseCase.Result result = useCase.regenerateSlots(restaurantId);
        return new SlotRegenerationResponse(result.deletedSlots(), result.generatedSlots());
    }

    public record BookingSettingsRequest(
            ConfirmationMode confirmationMode,
            Integer manualConfirmationMinPartySize,
            int guestCapacity,
            int slotIntervalMinutes,
            int diningDurationMinutes,
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold,
            Integer cancellationCutoffMinutes,
            Integer noShowGraceMinutes) {

        BookingSettingsUpdate toUpdate() {
            return new BookingSettingsUpdate(
                    confirmationMode, manualConfirmationMinPartySize, guestCapacity, slotIntervalMinutes,
                    diningDurationMinutes, bookingWindowDays, minimumPartySize, maximumOnlinePartySize,
                    largePartyThreshold, cancellationCutoffMinutes, noShowGraceMinutes);
        }
    }

    public record BookingSettingsResponse(
            ConfirmationMode confirmationMode,
            Integer manualConfirmationMinPartySize,
            int guestCapacity,
            int slotIntervalMinutes,
            int diningDurationMinutes,
            int bookingWindowDays,
            int minimumPartySize,
            int maximumOnlinePartySize,
            int largePartyThreshold,
            Integer cancellationCutoffMinutes,
            Integer noShowGraceMinutes) {

        static BookingSettingsResponse from(OwnerSettings settings) {
            return new BookingSettingsResponse(
                    settings.confirmationMode(), settings.manualConfirmationMinPartySize(),
                    settings.guestCapacity(), settings.bookingIntervalMinutes(), settings.diningDurationMinutes(),
                    settings.bookingWindowDays(), settings.minimumPartySize(), settings.maximumOnlinePartySize(),
                    settings.largePartyThreshold(), settings.customerCancellationCutoffMinutes(),
                    settings.noShowGraceMinutes());
        }
    }
}
