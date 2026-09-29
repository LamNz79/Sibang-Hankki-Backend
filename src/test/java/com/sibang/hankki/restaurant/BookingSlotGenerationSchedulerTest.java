package com.sibang.hankki.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BookingSlotGenerationSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerTestConfiguration.class)
            .withPropertyValues("app.booking-slot-generation.cron=0 5 0 * * *");

    @Test
    void isAbsentWhenSchedulingIsDisabled() {
        contextRunner.run(context -> assertNull(context.getBeanProvider(BookingSlotGenerationScheduler.class).getIfAvailable()));
    }

    @Test
    void delegatesDailyExecutionWhenSchedulingIsEnabled() {
        contextRunner.withPropertyValues("app.booking-slot-generation.enabled=true").run(context -> {
            BookingSlotGenerationScheduler scheduler = context.getBean(BookingSlotGenerationScheduler.class);
            assertNotNull(scheduler);

            scheduler.generateAtStartup(null);
            scheduler.generateDaily();

            verify(context.getBean(BookingSlotGenerationJob.class), times(2)).run();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(BookingSlotGenerationScheduler.class)
    static class SchedulerTestConfiguration {

        @Bean
        BookingSlotGenerationJob job() {
            return mock(BookingSlotGenerationJob.class);
        }

    }
}
