package com.rivoo.auth.application;

import com.rivoo.auth.application.dto.RegisterEmployeeRequest;
import com.rivoo.auth.application.dto.RegisterOwnerRequest;
import com.rivoo.auth.domain.port.out.KeycloakAdminPort;
import com.rivoo.auth.domain.port.out.OnboardingEventPort;
import com.rivoo.auth.domain.port.out.TenantUserMappingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Owner registration must ASK Keycloak to mail the verification link, not merely create the user
 * with the action pending: a pending required action alone produces no email, so the owner would be
 * locked out of an account they can neither use nor unlock.
 */
class OwnerVerificationEmailTest {

    private static final String OWNER_ID = "owner-uuid";
    private static final String EMPLOYEE_ID = "employee-uuid";

    private KeycloakAdminPort keycloakAdminPort;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        keycloakAdminPort = mock(KeycloakAdminPort.class);
        when(keycloakAdminPort.createUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(OWNER_ID);
        when(keycloakAdminPort.createEmployeeUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(EMPLOYEE_ID);
        authService = new AuthService(
                keycloakAdminPort, mock(OnboardingEventPort.class), mock(TenantUserMappingPort.class));
    }

    private void registerOwner() {
        authService.registerOwner(new RegisterOwnerRequest(
                "sal_00000000-1111-2222-3333-444444444444", "owner@example.com", "chosen-password",
                "Ana", "Lopez", "Demo Salon", "FREE_TRIAL"));
    }

    @Test
    void registerOwner_asksKeycloakToMailTheVerificationLink() {
        registerOwner();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> actions = ArgumentCaptor.forClass(List.class);
        verify(keycloakAdminPort).sendRequiredActionsEmail(eq(OWNER_ID), actions.capture());

        assertThat(actions.getValue())
                .as("the owner chose their own password: VERIFY_EMAIL and nothing else")
                .containsExactly("VERIFY_EMAIL")
                .doesNotContain("UPDATE_PASSWORD");
    }

    @Test
    void registerOwner_emailFailureDoesNotUndoAnOtherwiseCompleteRegistration() {
        // Best-effort by design, and pinned here because the alternative — letting it propagate —
        // would compensate (delete) a user whose salon row the caller already committed to.
        doThrow(new IllegalStateException("SMTP down"))
                .when(keycloakAdminPort).sendRequiredActionsEmail(anyString(), any());

        assertThatCode(this::registerOwner).doesNotThrowAnyException();

        verify(keycloakAdminPort, never()).deleteUser(anyString());
    }

    @Test
    void registerEmployee_stillAsksOnlyForUpdatePassword() {
        // The employee flow shares the method whose signature this change altered. Its behaviour
        // must be byte-identical to before: same single action, unchanged.
        authService.registerEmployee(new RegisterEmployeeRequest(
                "sal_00000000-1111-2222-3333-444444444444", "employee@example.com", "temp-pass",
                "Luis", "Gomez"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> actions = ArgumentCaptor.forClass(List.class);
        verify(keycloakAdminPort).sendRequiredActionsEmail(eq(EMPLOYEE_ID), actions.capture());

        assertThat(actions.getValue()).containsExactly("UPDATE_PASSWORD");
    }
}
