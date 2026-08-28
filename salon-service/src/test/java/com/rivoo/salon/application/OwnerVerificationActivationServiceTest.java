package com.rivoo.salon.application;

import com.rivoo.salon.domain.exception.AuthServiceException;
import com.rivoo.salon.domain.model.Salon;
import com.rivoo.salon.domain.model.SalonStatus;
import com.rivoo.salon.domain.port.out.AuthServicePort;
import com.rivoo.salon.domain.port.out.NotificationServicePort;
import com.rivoo.salon.domain.port.out.SalonPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The promotion half of the fix. Registration deliberately leaves a salon invisible, so this is the
 * only thing that can ever make it visible again — if it is wrong in the permissive direction the
 * enumeration channel reopens, and if it is wrong in the restrictive direction every legitimate
 * owner is stranded with a salon nobody can find and no way to fix it themselves.
 */
@ExtendWith(MockitoExtension.class)
class OwnerVerificationActivationServiceTest {

    private static final String OWNER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";

    @Mock
    private SalonPersistencePort salons;

    @Mock
    private AuthServicePort authService;

    @Mock
    private NotificationServicePort notifications;

    private OwnerVerificationActivationService service;

    @BeforeEach
    void setUp() {
        service = new OwnerVerificationActivationService(salons, authService, notifications);
    }

    private static Salon pending(String externalId, String ownerUserId) {
        return Salon.builder()
                .id(1L)
                .externalId(externalId)
                .tenantId(externalId)
                .name("Demo Salon")
                .slug("demo-salon")
                .ownerUserId(ownerUserId)
                .email("owner@example.com")
                .status(SalonStatus.ONBOARDING)
                .build();
    }

    @Test
    void ownerHasVerified_salonIsActivatedAndWelcomed() {
        Salon salon = pending("sal_1", OWNER_ID);
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(salon));
        when(authService.isOwnerEmailVerified(OWNER_ID)).thenReturn(true);

        int activated = service.activateVerifiedOwners();

        assertThat(activated).isEqualTo(1);
        ArgumentCaptor<Salon> saved = ArgumentCaptor.forClass(Salon.class);
        verify(salons).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SalonStatus.ACTIVE);
        verify(notifications).sendWelcomeEmail("sal_1", "owner@example.com", "Demo Salon");
    }

    @Test
    void ownerHasNotVerified_nothingIsWrittenAndNoMailGoesOut() {
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(pending("sal_1", OWNER_ID)));
        when(authService.isOwnerEmailVerified(OWNER_ID)).thenReturn(false);

        int activated = service.activateVerifiedOwners();

        assertThat(activated).isZero();
        verify(salons, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void authServiceCannotAnswer_salonIsLeftPendingRatherThanActivated() {
        // "We could not ask" is not "the owner verified". Treating a failure as a yes would let
        // anyone who can break auth-service publish salons for addresses they do not own.
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(pending("sal_1", OWNER_ID)));
        when(authService.isOwnerEmailVerified(OWNER_ID))
                .thenThrow(AuthServiceException.unavailable("auth-service is down", null));

        int activated = service.activateVerifiedOwners();

        assertThat(activated).isZero();
        verify(salons, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void oneUnanswerableSalonDoesNotBlockTheRest() {
        // Without per-salon isolation a single deleted Keycloak user would freeze every other
        // owner's activation for ever, because the pass would abort on the first one every time.
        Salon broken = pending("sal_broken", "gone-from-keycloak");
        Salon healthy = pending("sal_ok", OWNER_ID);
        healthy.setId(2L);
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(broken, healthy));
        when(authService.isOwnerEmailVerified("gone-from-keycloak"))
                .thenThrow(AuthServiceException.rejected("no such user", null));
        when(authService.isOwnerEmailVerified(OWNER_ID)).thenReturn(true);

        int activated = service.activateVerifiedOwners();

        assertThat(activated).isEqualTo(1);
        ArgumentCaptor<Salon> saved = ArgumentCaptor.forClass(Salon.class);
        verify(salons).save(saved.capture());
        assertThat(saved.getValue().getExternalId()).isEqualTo("sal_ok");
    }

    @Test
    void salonWithNoKeycloakOwnerIsSkippedWithoutCallingAuthService() {
        // The saga never got as far as creating the user, so there is nobody to ask about. That row
        // belongs to the stale-onboarding sweep, not here.
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(pending("sal_1", null)));

        int activated = service.activateVerifiedOwners();

        assertThat(activated).isZero();
        verifyNoInteractions(authService);
        verify(salons, never()).save(any());
    }

    @Test
    void aFailingWelcomeMailDoesNotUndoTheActivation() {
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of(pending("sal_1", OWNER_ID)));
        when(authService.isOwnerEmailVerified(OWNER_ID)).thenReturn(true);
        doThrow(new RuntimeException("notification-service is down"))
                .when(notifications).sendWelcomeEmail(anyString(), anyString(), anyString());

        int activated = service.activateVerifiedOwners();

        assertThat(activated)
                .as("the salon is active; a mail that did not go out must not take that back")
                .isEqualTo(1);
        verify(salons).save(any());
    }

    @Test
    void noPendingSalons_doesNotTouchAnyDependency() {
        when(salons.findByStatus(SalonStatus.ONBOARDING)).thenReturn(List.of());

        assertThat(service.activateVerifiedOwners()).isZero();

        verifyNoInteractions(authService, notifications);
    }
}
