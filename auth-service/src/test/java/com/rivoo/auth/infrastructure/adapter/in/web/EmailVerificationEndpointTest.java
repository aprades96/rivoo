package com.rivoo.auth.infrastructure.adapter.in.web;

import com.rivoo.auth.domain.port.in.CheckEmailVerificationUseCase;
import com.rivoo.auth.domain.port.in.ListTenantUsersUseCase;
import com.rivoo.auth.domain.port.in.ManageTenantStatusUseCase;
import com.rivoo.auth.domain.port.in.RegisterEmployeeUseCase;
import com.rivoo.auth.domain.port.in.RegisterOwnerUseCase;
import com.rivoo.auth.domain.port.in.UpdateTenantAttributeUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the auth-service HALF of the contract that salon-service's
 * {@code AuthServiceAdapter#isOwnerEmailVerified} depends on: the exact path and the exact field
 * name. The two are wired across module boundaries with nothing but a string, and this repository
 * already carries a live example of what that costs — {@code AuthServiceAdapter#deleteUser} calls
 * {@code DELETE /api/internal/auth/users/{userId}}, which no controller here serves.
 */
class EmailVerificationEndpointTest {

    private static final String USER_ID = "9f1c2d3e-0000-4444-8888-aaaabbbbcccc";

    private static MockMvc mockMvcFor(CheckEmailVerificationUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new AuthController(
                mock(RegisterOwnerUseCase.class),
                mock(RegisterEmployeeUseCase.class),
                mock(ManageTenantStatusUseCase.class),
                mock(UpdateTenantAttributeUseCase.class),
                mock(ListTenantUsersUseCase.class),
                useCase)).build();
    }

    @Test
    void confirmedAddress_isReportedAsVerified() throws Exception {
        CheckEmailVerificationUseCase useCase = mock(CheckEmailVerificationUseCase.class);
        when(useCase.isEmailVerified(USER_ID)).thenReturn(true);

        mockMvcFor(useCase)
                .perform(get("/api/internal/auth/users/" + USER_ID + "/email-verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloakUserId").value(USER_ID))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void unconfirmedAddress_isReportedAsNotVerified() throws Exception {
        CheckEmailVerificationUseCase useCase = mock(CheckEmailVerificationUseCase.class);
        when(useCase.isEmailVerified(USER_ID)).thenReturn(false);

        mockMvcFor(useCase)
                .perform(get("/api/internal/auth/users/" + USER_ID + "/email-verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }
}
