package com.rivoo.appointment.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the clock the booking-window checks read, so that "now" is an injected
 * collaborator instead of a static call and the one-hour rule can be tested at its
 * exact boundary without depending on the wall clock.
 *
 * <p>The zone carried by this bean is irrelevant: every consumer re-zones it to the
 * salon's timezone before deriving a local date-time.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
