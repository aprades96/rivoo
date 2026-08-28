package com.rivoo.salon.infrastructure.config;

import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.in.ActivateVerifiedSalonsUseCase;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ONBOARDING now means two different things, and this job must not confuse them.
 * <ul>
 *   <li>No {@code ownerUserId}: the saga died before it could create the Keycloak user. Nothing is
 *       ever going to finish it — reap it.</li>
 *   <li>An {@code ownerUserId}: the registration completed and the salon is waiting for its owner
 *       to click the link in their mail. Reaping THAT after an hour would mean anyone who reads
 *       their mail in the evening ends up with a permanently invisible salon and no self-service
 *       way out — the exact outcome the whole change exists to prevent.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SalonSchedulingConfigTest {

    @Mock
    private SalonPersistencePort salons;

    @Mock
    private ActivateVerifiedSalonsUseCase activation;

    private SalonSchedulingConfig config;

    @BeforeEach
    void setUp() {
        config = new SalonSchedulingConfig(salons, activation);
    }

    private static Salon stale(String externalId, String ownerUserId) {
        return Salon.builder()
                .id(1L)
                .externalId(externalId)
                .tenantId(externalId)
                .name("Demo Salon")
                .slug("demo-salon")
                .ownerUserId(ownerUserId)
                .status(SalonStatus.ONBOARDING)
                .createdAt(Instant.now().minus(9, ChronoUnit.HOURS))
                .build();
    }

    @Test
    void salonWaitingForItsOwnerToVerifyIsNotReaped_howeverOldItIs() {
        when(salons.findByStatusAndCreatedAtBefore(eq(SalonStatus.ONBOARDING), any()))
                .thenReturn(List.of(stale("sal_waiting", "9f1c2d3e-0000-4444-8888-aaaabbbbcccc")));

        config.cleanupStaleOnboardings();

        verify(salons, never()).save(any());
    }

    @Test
    void salonWhoseSagaNeverReachedKeycloakIsStillMarkedFailed() {
        when(salons.findByStatusAndCreatedAtBefore(eq(SalonStatus.ONBOARDING), any()))
                .thenReturn(List.of(stale("sal_corpse", null)));

        config.cleanupStaleOnboardings();

        ArgumentCaptor<Salon> saved = ArgumentCaptor.forClass(Salon.class);
        verify(salons).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SalonStatus.FAILED);
        assertThat(saved.getValue().getExternalId()).isEqualTo("sal_corpse");
    }

    @Test
    void activationTickDelegatesToTheUseCase() {
        config.activateVerifiedOwners();

        verify(activation).activateVerifiedOwners();
    }

    @Test
    void aFailingActivationPassDoesNotEscapeTheScheduler() {
        // A scheduled method that throws is not retried by every executor, and owners staying
        // invisible for ever is the failure that must be logged rather than silently fatal.
        doThrow(new RuntimeException("auth-service is down")).when(activation).activateVerifiedOwners();

        assertThatCode(() -> config.activateVerifiedOwners()).doesNotThrowAnyException();
    }
}
