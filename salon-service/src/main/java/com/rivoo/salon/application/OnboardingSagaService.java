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
import com.rivoo.salon.domain.port.out.NotificationServicePort;
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

    /**
     * The ONLY body {@code POST /api/v1/salons} ever returns, for every outcome that is not an
     * infrastructure failure — a free address and an address that already has an account included.
     * A single shared constant rather than two equal literals: two literals drift, and the day they
     * drift the endpoint goes back to being an account-enumeration oracle.
     */
    private static final RegisterSalonResponse REGISTRATION_ACCEPTED = new RegisterSalonResponse(
            "Hemos recibido tu solicitud. Revisa tu correo para continuar.");

    private final SalonPersistencePort salonPersistencePort;
    private final BusinessHoursPersistencePort businessHoursPersistencePort;
    private final AuthServicePort authServicePort;
    private final BillingServicePort billingServicePort;
    private final NotificationServicePort notificationServicePort;

    @Override
    @Transactional
    public RegisterSalonResponse register(RegisterSalonRequest request) {
        log.atInfo().addKeyValue("salonName", request.name()).log("Starting salon registration");

        // Step 0: an address that already has an account ends here, with the SAME response a free
        // address gets. Nothing is created, nothing is changed, and the only trace the caller could
        // read is a mail they can only read if the address is theirs. Note the early return happens
        // BEFORE any id, slug or salon row is minted: for an existing address the saga must not
        // execute even partially, not even inside a transaction that would roll back.
        if (salonPersistencePort.existsByEmail(request.email())) {
            log.atInfo().log("Registration attempted with an address that already has a salon");
            notificationServicePort.sendExistingAccountRegistrationAttempt(request.email());
            return REGISTRATION_ACCEPTED;
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
        } catch (EmailAlreadyInUseException e) {
            // auth-service answered 409: the address IS a Keycloak user, it just has no salon row
            // (an employee's address, or an orphan left by a compensated onboarding). Semantically
            // identical to the Step 0 pre-check, so it must reach the identical outcome — otherwise
            // this population would still be enumerable through the 422 the generic catch produces.
            log.atInfo().log("Registration attempted with an address Keycloak already knows");
            salonPersistencePort.deleteById(savedSalon.getId());
            notificationServicePort.sendExistingAccountRegistrationAttempt(request.email());
            return REGISTRATION_ACCEPTED;
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("externalId", externalId).log("Failed to register owner in Keycloak, compensating");
            salonPersistencePort.deleteById(savedSalon.getId());
            throw e;
        }

        // Step 6: Update salon with owner user ID. The status deliberately STAYS ONBOARDING.
        //
        // Nobody has proved they control this address yet: the request was anonymous and the address
        // was supplied by whoever sent it. Publishing the salon now is what made the second half of
        // the enumeration oracle - the response is identical either way, but a free address left a
        // slug the attacker had chosen answering 200 on GET /api/v1/salons/public/{slug} while a
        // taken address left nothing, which is the same yes/no in two anonymous requests.
        // OwnerVerificationActivationService promotes it to ACTIVE once Keycloak reports the address
        // confirmed. This is also the coherent behaviour on its own terms: the owner cannot even log
        // in until then, so a salon of theirs taking public bookings would make no sense.
        try {
            savedSalon.setOwnerUserId(keycloakUserId);
            savedSalon = salonPersistencePort.save(savedSalon);
            log.atInfo().addKeyValue("externalId", externalId).addKeyValue("ownerUserId", keycloakUserId)
                    .log("Salon registered, awaiting owner email verification");
        } catch (Exception e) {
            log.atError().setCause(e).addKeyValue("keycloakUserId", keycloakUserId).addKeyValue("externalId", externalId).log("Failed to link the owner to the salon, compensating");
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

        // Step 8 used to send the WELCOME mail here. It does not any more, and this is not an
        // omission: that template reads "tu salon esta activo", which is false until the address is
        // confirmed. It is sent by OwnerVerificationActivationService at the moment it becomes true.
        // The mail this path produces right now is Keycloak's VERIFY_EMAIL, which is the one the
        // fixed 202 body ("revisa tu correo") actually refers to.

        // Byte-identical to the two early returns above. The salon's id, slug and status are
        // deliberately NOT echoed: each of them exists only on this path, and the owner cannot use
        // them yet anyway — Keycloak blocks their login until they confirm the address.
        return REGISTRATION_ACCEPTED;
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
