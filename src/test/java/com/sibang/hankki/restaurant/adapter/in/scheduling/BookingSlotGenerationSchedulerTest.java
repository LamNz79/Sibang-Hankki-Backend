package com.sibang.hankki.restaurant.adapter.in.scheduling;
import com.sibang.hankki.restaurant.application.port.in.BookingSlotGenerationUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
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
    void runsJobWhenApplicationReadyEventIsPublished() {
        contextRunner.withPropertyValues("app.booking-slot-generation.enabled=true").run(context -> {
            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(), new String[0], context, Duration.ZERO));

            verify(context.getBean(BookingSlotGenerationUseCase.class)).run();
        });
    }

    @Test
    void registersConfiguredCronInHoChiMinhTimezone() {
        contextRunner
                .withPropertyValues(
                        "app.booking-slot-generation.enabled=true",
                        "app.booking-slot-generation.cron=0 0 9 * * *")
                .run(context -> {
                    ScheduledTaskHolder taskHolder = context.getBean(ScheduledTaskHolder.class);
                    assertEquals(1, taskHolder.getScheduledTasks().size());

                    CronTask cronTask = assertInstanceOf(CronTask.class,
                            taskHolder.getScheduledTasks().iterator().next().getTask());
                    assertEquals("0 0 9 * * *", cronTask.getExpression());
                    assertEquals(Instant.parse("2026-09-29T02:00:00Z"), cronTask.getTrigger().nextExecution(
                            new SimpleTriggerContext(Clock.fixed(
                                    Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC))));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(BookingSlotGenerationScheduler.class)
    static class SchedulerTestConfiguration {

        @Bean
        BookingSlotGenerationUseCase job() {
            return mock(BookingSlotGenerationUseCase.class);
        }

    }
}
