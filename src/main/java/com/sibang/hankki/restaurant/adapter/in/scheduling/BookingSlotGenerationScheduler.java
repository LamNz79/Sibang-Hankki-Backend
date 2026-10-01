package com.sibang.hankki.restaurant.adapter.in.scheduling;

import com.sibang.hankki.restaurant.application.port.in.BookingSlotGenerationUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.booking-slot-generation", name = "enabled", havingValue = "true")
class BookingSlotGenerationScheduler {

    private final BookingSlotGenerationUseCase bookingSlotGenerationUseCase;

    BookingSlotGenerationScheduler(BookingSlotGenerationUseCase bookingSlotGenerationUseCase) {
        this.bookingSlotGenerationUseCase = bookingSlotGenerationUseCase;
    }

    @EventListener(ApplicationReadyEvent.class)
    void generateAtStartup(ApplicationReadyEvent event) {
        bookingSlotGenerationUseCase.run();
    }

    @Scheduled(cron = "${app.booking-slot-generation.cron}", zone = "Asia/Ho_Chi_Minh")
    void generateDaily() {
        bookingSlotGenerationUseCase.run();
    }
}
