package com.rivoo.salon.infrastructure.config;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.port.in.ActivateVerifiedSalonsUseCase;
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
    private final ActivateVerifiedSalonsUseCase activateVerifiedSalonsUseCase;

    /**
     * Makes a salon publicly visible once its owner has confirmed their address.
     * <p>
     * One minute, not five: this is the delay between the owner clicking the link in their mail and
     * their own salon page answering, so it is user-visible latency rather than housekeeping. The
     * pass only touches salons still in ONBOARDING, so a steady state costs one query.
     */
    @Scheduled(fixedRate = 60_000) // every minute
    public void activateVerifiedOwners() {
        try {
            activateVerifiedSalonsUseCase.activateVerifiedOwners();
        } catch (Exception e) {
            // A scheduled method that throws is simply not retried by some executors and its stack
            // trace never reaches anyone. Owners staying invisible for ever is exactly the failure
            // that must be loud.
            log.atError().setCause(e).log("Owner-verification activation pass failed");
        }
    }

    /**
     * Marks abandoned onboardings FAILED.
     * <p>
     * Restricted to salons with NO owner in Keycloak: the saga died before it could create one, so
     * there is nothing left to wait for. A salon that HAS an owner is not stale, it is waiting for
     * that owner to click a link, and that wait has no deadline here - reaping it after an hour
     * would mean an owner who reads their mail in the evening ends up with a permanently invisible
     * salon and no way to fix it themselves, which is the one outcome this whole change forbids.
     * Such a salon keeps its row (and therefore its address and slug) for as long as it takes; that
     * is deliberate, because releasing the address would let the next probe re-create it.
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanupStaleOnboardings() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Salon> staleSalons = salonPersistencePort
                .findByStatusAndCreatedAtBefore(SalonStatus.ONBOARDING, oneHourAgo)
                .stream()
                .filter(salon -> salon.getOwnerUserId() == null)
                .toList();

        if (staleSalons.isEmpty()) {
            return;
        }

        log.atInfo().addKeyValue("count", staleSalons.size()).log("Found stale ONBOARDING salons, marking as FAILED");
        for (Salon salon : staleSalons) {
            salon.setStatus(SalonStatus.FAILED);
            salonPersistencePort.save(salon);
            log.atWarn().addKeyValue("externalId", salon.getExternalId()).log("Marked salon as FAILED (stale onboarding)");
        }
    }
}
