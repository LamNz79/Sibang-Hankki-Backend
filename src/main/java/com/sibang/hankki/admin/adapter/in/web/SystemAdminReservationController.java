package com.sibang.hankki.admin.adapter.in.web;

import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AdminReservationDetail;
import com.sibang.hankki.admin.application.port.in.SystemAdminReservationReadUseCase.AdminReservationPage;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reservations")
public class SystemAdminReservationController {

    private final SystemAdminReservationReadUseCase readUseCase;

    public SystemAdminReservationController(SystemAdminReservationReadUseCase readUseCase) {
        this.readUseCase = readUseCase;
    }

    @GetMapping
    public AdminReservationPage findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID restaurantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return readUseCase.findPage(page, size, query, restaurantId, status, dateFrom, dateTo);
    }

    @GetMapping("/{reservationId}")
    public AdminReservationDetail findById(@PathVariable UUID reservationId) {
        return readUseCase.findById(reservationId);
    }
}
