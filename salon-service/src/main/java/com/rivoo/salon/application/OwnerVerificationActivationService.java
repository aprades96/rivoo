package com.rivoo.salon.application;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.ActivateVerifiedSalonsUseCase;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns "the owner confirmed their address" into "the salon is publicly visible".
 * <p>
 * <b>Why polling and not something smarter.</b> Keycloak owns the verification flow end to end: it
 * sends the mail, it serves the link, it flips {@code emailVerified}. It tells nobody. The three
 * ways out of that are (1) deploy an event-listener SPI extension into Keycloak, (2) have the
 * browser report back after the redirect, (3) ask Keycloak. (1) means building and shipping a JAR
 * into the identity provider for one boolean. (2) puts the trigger in the hands of an
 * unauthenticated caller, which is how the salon became publicly visible on an attacker's say-so in
 * the first place. (3) needs one read-only internal endpoint on a service that already holds the
 * Keycloak admin credentials, and it cannot be driven from outside. Hence (3), on a timer.
 * <p>
 * <b>Only an explicit {@code true} promotes.</b> A salon that cannot be checked (auth-service down,
 * Keycloak down, user gone) is left exactly as it was and retried on the next pass: failing to ask
 * is not an answer. One salon's failure never aborts the pass, or a single deleted Keycloak user
 * would freeze every other owner's activation for ever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerVerificationActivationService implements ActivateVerifiedSalonsUseCase {

    private final SalonPersistencePort salonPersistencePort;
    private final AuthServicePort authServicePort;
    private final NotificationServicePort notificationServicePort;

    /**
     * Deliberately NOT {@code @Transactional}. Each promotion is its own unit of work - there is no
     * invariant spanning two salons - and wrapping the whole pass would hold one JDBC connection,
     * plus row locks on everything already promoted, across every remote call the loop makes. With
     * a 3-second read timeout and a queue of pending salons that is minutes, which is long enough
     * to hit MySQL's lock wait timeout. Same rule {@link SalonPublicSnapshotLoader} exists to
     * enforce on the public read path: never call another service while holding a connection.
     */
    @Override
    public int activateVerifiedOwners() {
        List<Salon> pending = salonPersistencePort.findByStatus(SalonStatus.ONBOARDING);
        if (pending.isEmpty()) {
            return 0;
        }

        int activated = 0;
        for (Salon salon : pending) {
            if (salon.getOwnerUserId() == null) {
                // The saga never got as far as creating the Keycloak user, so there is nobody to ask
                // about. Left to the stale-onboarding sweep, which is what that job is for.
                continue;
            }
            if (activateIfVerified(salon)) {
                activated++;
            }
        }

        if (activated > 0) {
            log.atInfo().addKeyValue("activated", activated).log("Activated salons whose owner verified their address");
        }
        return activated;
    }

    private boolean activateIfVerified(Salon salon) {
        try {
            if (!authServicePort.isOwnerEmailVerified(salon.getOwnerUserId())) {
                return false;
            }
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("externalId", salon.getExternalId())
                    .log("Could not read the owner's verification state, leaving the salon pending");
            return false;
        }

        salon.setStatus(SalonStatus.ACTIVE);
        salonPersistencePort.save(salon);
        log.atInfo().addKeyValue("externalId", salon.getExternalId())
                .log("Owner address confirmed, salon is now publicly visible");

        // The welcome mail says "tu salon esta activo", so it belongs HERE and not at registration:
        // until this moment the statement was false. Fire-and-forget, like every other notification
        // in this flow - a mail that does not go out must not undo an activation that did.
        try {
            notificationServicePort.sendWelcomeEmail(salon.getTenantId(), salon.getEmail(), salon.getName());
        } catch (Exception e) {
            log.atWarn().setCause(e).addKeyValue("externalId", salon.getExternalId())
                    .log("Failed to send welcome email after activation, continuing");
        }
        return true;
    }
}
