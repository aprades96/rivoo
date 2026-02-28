package com.rivoo.salon.infrastructure.config;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SalonSchedulingConfig {

    private final SalonPersistencePort salonPersistencePort;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanupStaleOnboardings() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Salon> staleSalons = salonPersistencePort
                .findByStatusAndCreatedAtBefore(SalonStatus.ONBOARDING, oneHourAgo);

        if (staleSalons.isEmpty()) {
            return;
        }

        log.info("Found {} stale ONBOARDING salons, marking as FAILED", staleSalons.size());
        for (Salon salon : staleSalons) {
            salon.setStatus(SalonStatus.FAILED);
            salonPersistencePort.save(salon);
            log.warn("Marked salon {} as FAILED (stale onboarding)", salon.getExternalId());
        }
    }
}
