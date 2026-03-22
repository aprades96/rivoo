package com.rivoo.salon.application;

import com.rivoo.common.util.ExternalIdGenerator;
import com.rivoo.salon.application.dto.RegisterSalonRequest;
import com.rivoo.salon.application.dto.RegisterSalonResponse;
import com.rivoo.salon.domain.exception.EmailAlreadyInUseException;
import com.rivoo.salon.domain.exception.SlugAlreadyExistsException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonBusinessHours;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.model.SubscriptionPlan;
import com.rivoo.salon.domain.port.in.RegisterSalonUseCase;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.domain.port.out.BillingServicePort;
import com.rivoo.salon.domain.port.out.BusinessHoursPersistencePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingSagaService implements RegisterSalonUseCase {

    private static final LocalTime DEFAULT_WEEKDAY_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WEEKDAY_CLOSE = LocalTime.of(20, 0);
    private static final LocalTime DEFAULT_SATURDAY_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_SATURDAY_CLOSE = LocalTime.of(14, 0);

    private final SalonPersistencePort salonPersistencePort;
    private final BusinessHoursPersistencePort businessHoursPersistencePort;
    private final AuthServicePort authServicePort;
    private final BillingServicePort billingServicePort;

    @Override
    @Transactional
    public RegisterSalonResponse register(RegisterSalonRequest request) {
        log.atInfo().addKeyValue("salonName", request.name()).log("Starting salon registration");

        // Step 0: Validate email uniqueness
        if (salonPersistencePort.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(request.email());
        }

        // Step 1: Generate IDs and slug
        String externalId = ExternalIdGenerator.generate("sal");
        String slug = generateUniqueSlug(request.name());

        // Step 2: Create salon entity (status=ONBOARDING, tenantId=externalId)
        Salon salon = Salon.builder()
                .externalId(externalId)
                .tenantId(externalId) // salon IS the tenant
                .name(request.name())
                .slug(slug)
                .email(request.email())
                .phone(request.phone())
                .description(request.description())
                .addressStreet(request.addressStreet())
                .addressCity(request.addressCity() != null ? request.addressCity() : "Barcelona")
                .addressPostalCode(request.addressPostalCode())
                .timezone("Europe/Madrid")
                .currency("EUR")
                .subscriptionPlan(SubscriptionPlan.FREE_TRIAL)
                .status(SalonStatus.ONBOARDING)
                .build();

        // Step 3: Persist salon
        Salon savedSalon = salonPersistencePort.save(salon);
        log.atInfo().addKeyValue("externalId", externalId).addKeyValue("slug", slug).log("Salon persisted");

        // Step 4: Create default business hours
        createDefaultBusinessHours(savedSalon.getId());

        // Step 5: Register owner in Keycloak via auth-service
        String keycloakUserId;
        try {
            keycloakUserId = authServicePort.registerOwner(
                    externalId,
                    request.email(),
                    request.ownerPassword(),
                    request.ownerFirstName(),
                    request.ownerLastName(),
                    request.name(),
                    SubscriptionPlan.FREE_TRIAL.name());
            log.atInfo().addKeyValue("keycloakUserId", keycloakUserId).log("Owner registered in Keycloak");
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("externalId", externalId).log("Failed to register owner in Keycloak, compensating");
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 6: Update salon with owner user ID
        try {
            savedSalon.setOwnerUserId(keycloakUserId);
            savedSalon.setStatus(SalonStatus.ACTIVE);
            savedSalon = salonPersistencePort.save(savedSalon);
            log.atInfo().addKeyValue("externalId", externalId).addKeyValue("ownerUserId", keycloakUserId).log("Salon activated");
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("keycloakUserId", keycloakUserId).addKeyValue("externalId", externalId).log("Failed to activate salon, compensating");
            try {
                authServicePort.deleteUser(keycloakUserId);
            } catch (Exception compEx) {
                log.atError().setCause(compEx).addKeyValue("keycloakUserId", keycloakUserId).log("Compensation failed: could not delete Keycloak user");
            }
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 7: Create FREE_TRIAL subscription in billing-service
        try {
            billingServicePort.createSubscription(externalId, request.email(), request.name());
            log.atInfo().addKeyValue("externalId", externalId).log("Subscription created in billing-service");
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("externalId", externalId).log("Failed to create subscription, compensating");
            try {
                authServicePort.deleteUser(keycloakUserId);
            } catch (Exception compEx) {
                log.atError().setCause(compEx).addKeyValue("keycloakUserId", keycloakUserId).log("Compensation failed: could not delete Keycloak user");
            }
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 8 (SKIP): notification-service — Fase 8
        log.atInfo().log("Skipping notification-service integration (not implemented yet)");

        return new RegisterSalonResponse(
                savedSalon.getExternalId(),
                savedSalon.getSlug(),
                savedSalon.getStatus().name());
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9áéíóúñü]+", "-")
                .replaceAll("^-|-$", "");

        if (!salonPersistencePort.existsBySlug(baseSlug)) {
            return baseSlug;
        }

        for (int i = 2; i <= 100; i++) {
            String candidate = baseSlug + "-" + i;
            if (!salonPersistencePort.existsBySlug(candidate)) {
                return candidate;
            }
        }

        throw new SlugAlreadyExistsException(baseSlug);
    }

    private void createDefaultBusinessHours(Long salonId) {
        List<SalonBusinessHours> defaults = new ArrayList<>();

        // Mon-Fri
        for (int day = 1; day <= 5; day++) {
            defaults.add(SalonBusinessHours.builder()
                    .salonId(salonId)
                    .dayOfWeek(day)
                    .open(true)
                    .openTime(DEFAULT_WEEKDAY_OPEN)
                    .closeTime(DEFAULT_WEEKDAY_CLOSE)
                    .build());
        }

        // Sat
        defaults.add(SalonBusinessHours.builder()
                .salonId(salonId)
                .dayOfWeek(6)
                .open(true)
                .openTime(DEFAULT_SATURDAY_OPEN)
                .closeTime(DEFAULT_SATURDAY_CLOSE)
                .build());

        // Sun: closed
        defaults.add(SalonBusinessHours.builder()
                .salonId(salonId)
                .dayOfWeek(7)
                .open(false)
                .build());

        businessHoursPersistencePort.saveAll(defaults);
        log.atInfo().addKeyValue("salonId", salonId).log("Default business hours created");
    }
}
