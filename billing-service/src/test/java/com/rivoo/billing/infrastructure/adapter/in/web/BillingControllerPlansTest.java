package com.rivoo.billing.infrastructure.adapter.in.web;

import com.rivoo.billing.application.dto.PlanLimitsPublicResponse;
import com.rivoo.billing.application.dto.PlanResponse;
import com.rivoo.billing.domain.port.in.BillingPortalUseCase;
import com.rivoo.billing.domain.port.in.CheckoutUseCase;
import com.rivoo.billing.domain.port.in.GetSubscriptionUseCase;
import com.rivoo.billing.domain.port.in.ListPlansUseCase;
import com.rivoo.common.tenant.TenantContext;
import com.rivoo.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/billing/plans} is the anonymous plan catalogue: permitAll in
 * {@code BillingSecurityConfig} ({@code requestMatchers(HttpMethod.GET,
 * "/api/v1/billing/plans").permitAll()}) and in the gateway's
 * {@code GatewaySecurityConfig} ({@code pathMatchers(HttpMethod.GET,
 * "/api/v1/billing/plans").permitAll()}), and {@code billingApi.getPlans()} in
 * {@code rivoo-frontend/src/lib/api/billing.ts} is the only call in that file that passes
 * no token.
 * <p>
 * Standalone MockMvc: the Boot 4 test-autoconfigure jar bundled here ships only the
 * {@code json} and {@code jdbc} slices, so {@code @WebMvcTest} / {@code @AutoConfigureMockMvc}
 * do not exist and neither the filter chain nor the method-security interceptor is
 * installed. This test therefore does NOT prove end-to-end anonymity — no test in this
 * module can, see the module CLAUDE.md — and it does not pretend to. What it does cover is
 * the half of the contract that lives in this class: the handler needs no tenant and no
 * request input to answer, and it carries no method-security annotation. The latter is
 * load-bearing precisely because {@code @EnableMethodSecurity} IS active in production, so
 * a {@code @PreAuthorize} added here would reject every anonymous caller while both
 * security configs still say permitAll.
 */
class BillingControllerPlansTest {

    private ListPlansUseCase listPlansUseCase;
    private GetSubscriptionUseCase getSubscriptionUseCase;
    private CheckoutUseCase checkoutUseCase;
    private BillingPortalUseCase billingPortalUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listPlansUseCase = mock(ListPlansUseCase.class);
        getSubscriptionUseCase = mock(GetSubscriptionUseCase.class);
        checkoutUseCase = mock(CheckoutUseCase.class);
        billingPortalUseCase = mock(BillingPortalUseCase.class);

        BillingController controller = new BillingController(
                getSubscriptionUseCase, checkoutUseCase, listPlansUseCase, billingPortalUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new BillingExceptionHandler())
                .build();

        // Deliberately NOT populated: an anonymous request carries no JWT, so the gateway
        // never injects X-Tenant-Id and TenantInterceptor never fills the ThreadLocal.
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listPlans_withoutAnyTenantOrAuthArtifact_returns200WithTheCatalogue() throws Exception {
        when(listPlansUseCase.listActivePlans()).thenReturn(catalogue());

        mockMvc.perform(get("/api/v1/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("BASIC"))
                .andExpect(jsonPath("$[1].name").value("PREMIUM"));

        // No tenant was read, and no tenant-scoped collaborator was touched.
        assertThat(TenantContext.getCurrentTenantId()).isNull();
        verifyNoInteractions(getSubscriptionUseCase, checkoutUseCase, billingPortalUseCase);
    }

    @Test
    void listPlans_exposesEachTiersLimitsInTheBody() throws Exception {
        when(listPlansUseCase.listActivePlans()).thenReturn(catalogue());

        mockMvc.perform(get("/api/v1/billing/plans"))
                .andExpect(status().isOk())
                // The point of the whole change: one anonymous call yields what each tier
                // includes, so a comparison screen needs no second request.
                .andExpect(jsonPath("$[0].limits.maxEmployees").value(3))
                .andExpect(jsonPath("$[0].limits.maxAppointmentsPerMonth").value(200))
                .andExpect(jsonPath("$[0].limits.emailRemindersEnabled").value(true))
                .andExpect(jsonPath("$[0].limits.smsRemindersEnabled").value(false))
                .andExpect(jsonPath("$[1].limits.maxEmployees").value(10))
                .andExpect(jsonPath("$[1].limits.maxAppointmentsPerMonth").value(-1));
    }

    @Test
    void listPlans_missingLimitRowsSurfaceAsJsonNull() throws Exception {
        when(listPlansUseCase.listActivePlans()).thenReturn(List.of(new PlanResponse(
                "pln_basic", "BASIC", "Plan Basic", new BigDecimal("29.00"), 0,
                new PlanLimitsPublicResponse(null, 200, true, null))));

        mockMvc.perform(get("/api/v1/billing/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].limits.maxEmployees").isEmpty())
                .andExpect(jsonPath("$[0].limits.smsRemindersEnabled").isEmpty())
                .andExpect(jsonPath("$[0].limits.maxAppointmentsPerMonth").value(200));
    }

    @Test
    void listPlans_handlerCarriesNoMethodSecurityAnnotation() throws Exception {
        Method listPlans = BillingController.class.getMethod("listPlans");

        assertThat(listPlans.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(BillingController.class.getAnnotation(PreAuthorize.class)).isNull();

        // Control: the probe above is only meaningful if it can actually see the
        // annotation when it is there. The sibling handlers on this same controller are
        // SALON_OWNER-only, so a change that made this reflection blind (wrong annotation
        // type, non-runtime retention) fails here instead of passing vacuously.
        assertThat(BillingController.class.getMethod("getSubscription").getAnnotation(PreAuthorize.class))
                .isNotNull();
        assertThat(BillingController.class.getMethod("createPortalSession").getAnnotation(PreAuthorize.class))
                .isNotNull();
    }

    @Test
    void listPlans_takesNoRequestInput() throws Exception {
        // Nothing about the caller can influence the answer: no path variable, no query
        // parameter, no body, no header. The catalogue is identical for everyone, which is
        // what makes serving it anonymously safe.
        assertThat(BillingController.class.getMethod("listPlans").getParameterCount()).isZero();
    }

    private List<PlanResponse> catalogue() {
        return List.of(
                new PlanResponse("pln_basic", "BASIC", "Plan Basic", new BigDecimal("29.00"), 0,
                        new PlanLimitsPublicResponse(3, 200, true, false)),
                new PlanResponse("pln_premium", "PREMIUM", "Plan Premium", new BigDecimal("59.00"), 0,
                        new PlanLimitsPublicResponse(10, -1, true, true)));
    }
}
