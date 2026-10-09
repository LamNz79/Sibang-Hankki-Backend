package com.sibang.hankki.reservation.adapter.in.web;

import com.sibang.hankki.reservation.application.port.in.OwnerReservationReadUseCase.OwnerReservationPage;
import java.util.List;

public record OwnerReservationPageResponse(
        List<OwnerReservationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Summary summary) {

    static OwnerReservationPageResponse from(OwnerReservationPage result) {
        return new OwnerReservationPageResponse(
                result.items().stream().map(OwnerReservationResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                new Summary(
                        result.summary().confirmed(),
                        result.summary().checkedIn(),
                        result.summary().cancelled(),
                        result.summary().noShow()));
    }

    public record Summary(long confirmed, long checkedIn, long cancelled, long noShow) {
    }
}
