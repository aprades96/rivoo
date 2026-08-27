package com.rivoo.billing.infrastructure.adapter.in.web;

import com.rivoo.billing.application.dto.PortalResponse;
import com.rivoo.billing.domain.exception.StripeCustomerNotLinkedException;
import com.rivoo.billing.domain.exception.SubscriptionNotFoundException;
import com.rivoo.billing.domain.port.in.BillingPortalUseCase;
import com.rivoo.billing.domain.port.in.CheckoutUseCase;
import com.rivoo.billing.domain.port.in.GetSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.ListPlansUseCase;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc: the Boot 4 test autoconfigure jar bundled here ships only the
 * {@code json} and {@code jdbc} slices, so {@code @WebMvcTest} / {@code @AutoConfigureMockMvc}
 * are not available. {@code @PreAuthorize} is therefore NOT exercised here — this test pins
 * routing, the empty-body contract and the error mapping, not authorization.
 */
class BillingControllerPortalTest {

    private BillingPortalUseCase billingPortalUseCase;
    private CheckoutUseCase checkoutUseCase;
    private GetSubscriptionUseCase getSubscriptionUseCase;
    private MockMvc mockMvc;

    private static final String TENANT_ID = "sal_tenant-001";
    private static final String PORTAL_URL = "https://billing.stripe.com/mock-portal/abc";

    @BeforeEach
    void setUp() {
        billingPortalUseCase = mock(BillingPortalUseCase.class);
        checkoutUseCase = mock(CheckoutUseCase.class);
        getSubscriptionUseCase = mock(GetSubscriptionUseCase.class);

        BillingController controller = new BillingController(
                getSubscriptionUseCase,
                checkoutUseCase,
                mock(ListPlansUseCase.class),
                billingPortalUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new BillingExceptionHandler())
                .build();

        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createPortalSession_realFrontendRequestShape_returns200AndUrlField() throws Exception {
        when(billingPortalUseCase.createPortalSession(TENANT_ID)).thenReturn(new PortalResponse(PORTAL_URL));

        // This is the exact shape the browser sends. apiFetch
        // (rivoo-frontend/src/lib/api/client.ts) sets "Content-Type: application/json"
        // unconditionally, whether or not there is a body, and billingApi.createPortalSession
        // passes no body — so `body: body ? JSON.stringify(body) : undefined` yields
        // undefined. Net result: POST + Content-Type: application/json + a zero-length body.
        // That combination is precisely what a @RequestBody parameter cannot handle: Jackson
        // gets an empty stream and the request dies as a 400 before the use case is reached.
        // Hence the handler takes no @RequestBody.
        mockMvc.perform(post("/api/v1/billing/portal")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(PORTAL_URL));
    }

    @Test
    void createPortalSession_withoutContentTypeHeader_alsoReturns200() throws Exception {
        when(billingPortalUseCase.createPortalSession(TENANT_ID)).thenReturn(new PortalResponse(PORTAL_URL));

        // Defensive: the handler must not depend on the Content-Type the client happens to
        // send, so a caller that omits it entirely (curl, another service) works too.
        mockMvc.perform(post("/api/v1/billing/portal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(PORTAL_URL));
    }

    @Test
    void createPortalSession_passesCurrentTenantIdFromTenantContext() throws Exception {
        when(billingPortalUseCase.createPortalSession(anyString())).thenReturn(new PortalResponse(PORTAL_URL));

        mockMvc.perform(post("/api/v1/billing/portal"))
                .andExpect(status().isOk());

        verify(billingPortalUseCase).createPortalSession(TENANT_ID);
        verifyNoInteractions(checkoutUseCase, getSubscriptionUseCase);
    }

    @Test
    void createPortalSession_stripeCustomerNotLinked_returns422ProblemDetail() throws Exception {
        when(billingPortalUseCase.createPortalSession(TENANT_ID))
                .thenThrow(new StripeCustomerNotLinkedException(TENANT_ID));

        mockMvc.perform(post("/api/v1/billing/portal"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Business Validation Failed"));
    }

    @Test
    void createPortalSession_noSubscription_returns404ProblemDetail() throws Exception {
        when(billingPortalUseCase.createPortalSession(TENANT_ID))
                .thenThrow(new SubscriptionNotFoundException(TENANT_ID));

        mockMvc.perform(post("/api/v1/billing/portal"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }
}
