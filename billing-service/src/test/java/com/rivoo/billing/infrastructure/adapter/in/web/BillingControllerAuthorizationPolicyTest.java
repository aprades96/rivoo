package com.rivoo.billing.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The authorization guard for {@link BillingController}: every handler on the controller,
 * and the exact {@code @PreAuthorize} expression it carries — or the explicit statement that
 * it carries none.
 * <p>
 * <b>Why reflection.</b> Authorization on this controller is enforced at runtime by
 * {@code @PreAuthorize} plus {@code @EnableMethodSecurity}
 * ({@code BillingSecurityConfig}). Exercising that would need the method-security
 * interceptor, which only exists inside a Spring context: the Boot 4
 * {@code spring-boot-test-autoconfigure} jar resolved by this build ships exactly two slices,
 * {@code json} and {@code jdbc}, so {@code @WebMvcTest} and {@code @AutoConfigureMockMvc} do
 * not exist here, and {@code MockMvcBuilders.standaloneSetup} — what every controller test in
 * this module uses — installs neither the filter chain nor the method-security interceptor.
 * Reading the annotations is the only mechanism available.
 * <p>
 * <b>What this proves and what it does not.</b> It proves the annotations are written as
 * intended. It does NOT prove Spring enforces them: nothing in this module observes a caller
 * without {@code ROLE_SALON_OWNER} being rejected. That needs a {@code @SpringBootTest}-based
 * security test (Testcontainers, {@code @Tag("integration")}, excluded from the default
 * surefire run) and is still open — see the module CLAUDE.md.
 * <p>
 * <b>Allowlist, not a snapshot.</b> The handlers are enumerated from the controller rather
 * than listed here, and every one found must appear in {@link #EXPECTED_POLICIES}. A handler
 * added to the controller is therefore red until someone writes down its authorization
 * policy — the same reasoning {@code PlanCatalogueExposureTest} applies to the fields of the
 * anonymous catalogue. Without that, this file would only pin the handlers that happened to
 * exist the day it was written, which is the drift that produced it: three assertions inside
 * {@code BillingControllerPlansTest} were incidentally the only thing pinning
 * {@code getSubscription} and {@code createPortalSession}, while {@code createCheckout} — the
 * handler that starts a Stripe payment — was pinned by nothing at all.
 * <p>
 * The expected expressions are hardcoded string literals on purpose. An expectation derived
 * from the annotation under test cannot fail, and the expression is the half that was
 * previously unchecked: swapping {@code hasRole('SALON_OWNER')} for {@code hasRole('EMPLOYEE')}
 * used to leave the suite green.
 */
class BillingControllerAuthorizationPolicyTest {

    /**
     * The policy value for a handler that deliberately carries no {@code @PreAuthorize}.
     * Not a valid SpEL expression, so it cannot be confused with a real annotation value.
     */
    private static final String NO_METHOD_SECURITY = "(none - reachable without a role check)";

    /**
     * The declared authorization policy of every handler on {@link BillingController}.
     * <p>
     * {@code listPlans} is the anonymous plan catalogue: {@code permitAll} in
     * {@code BillingSecurityConfig} (GET {@code /api/v1/billing/plans}) and in the gateway's
     * {@code GatewaySecurityConfig}, and {@code billingApi.getPlans()} in
     * {@code rivoo-frontend/src/lib/api/billing.ts} is the only call in that file that passes
     * no token. A {@code @PreAuthorize} here would reject every anonymous caller while both
     * security configs still say {@code permitAll}, so its absence is load-bearing and is
     * pinned as deliberately as the three that are present.
     */
    private static final Map<String, String> EXPECTED_POLICIES = Map.of(
            "getSubscription", "hasRole('SALON_OWNER')",
            "createCheckout", "hasRole('SALON_OWNER')",
            "createPortalSession", "hasRole('SALON_OWNER')",
            "listPlans", NO_METHOD_SECURITY);

    @Test
    void everyHandlerHasADeclaredAuthorizationPolicy() {
        assertThat(policiesOf(BillingController.class).keySet())
                .as("The handlers on BillingController are no longer the ones whose authorization "
                        + "policy is declared in EXPECTED_POLICIES. A handler was added, removed or "
                        + "renamed. Add it to EXPECTED_POLICIES with the @PreAuthorize expression it "
                        + "must carry, or with NO_METHOD_SECURITY if it is meant to be reachable "
                        + "without a role check — and if so, check that BillingSecurityConfig and the "
                        + "gateway's GatewaySecurityConfig agree, because they, not this annotation, "
                        + "decide whether a JWT is required at all.")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_POLICIES.keySet());
    }

    @Test
    void everyHandlerCarriesExactlyItsDeclaredPolicy() {
        assertThat(policiesOf(BillingController.class))
                .as("A @PreAuthorize expression on BillingController no longer matches the declared "
                        + "policy. Neither the presence of the annotation nor the role inside it is "
                        + "verified anywhere else in this module: standalone MockMvc installs no "
                        + "method-security interceptor, so a weakened expression reaches production "
                        + "silently. If the change is intended, state the new policy here.")
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_POLICIES);
    }

    @Test
    void controllerCarriesNoClassLevelPreAuthorize() {
        // A class-level @PreAuthorize applies to every handler, including listPlans, and would
        // break the anonymous catalogue without touching any method. The per-method probe below
        // reads method-level annotations only, so this is asserted separately.
        assertThat(BillingController.class.getAnnotation(PreAuthorize.class))
                .as("A class-level @PreAuthorize was added to BillingController. It applies to every "
                        + "handler, including the anonymous GET /api/v1/billing/plans, which both "
                        + "security configs serve with permitAll.")
                .isNull();
    }

    @Test
    void theProbeReadsHandlersAndExpressionsRatherThanPassingVacuously() {
        // Control. Every assertion above compares this probe's output against hardcoded
        // literals, so a probe returning nothing would fail on an empty map. What that would
        // NOT catch is a probe that finds only some handlers (missing @PostMapping, say),
        // reports every expression as absent, or reports absence for a handler that has one —
        // each of which would let the exact mutation this class exists to stop slip through.
        // The decoy mixes both HTTP verbs, an unguarded handler, a guarded one and a
        // non-handler method, and the probe must report all four facts.
        assertThat(policiesOf(AuthorizationProbeDecoy.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "guardedByGet", "hasRole('DECOY_ROLE')",
                        "unguardedByPost", NO_METHOD_SECURITY));
    }

    @RequestMapping("/decoy")
    static class AuthorizationProbeDecoy {

        @GetMapping("/guarded")
        @PreAuthorize("hasRole('DECOY_ROLE')")
        public void guardedByGet() {
        }

        @PostMapping("/unguarded")
        public void unguardedByPost() {
        }

        /** Not a request handler: it must not appear in the probe's output. */
        public void plainMethod() {
        }
    }

    /**
     * Maps every request handler declared on {@code controllerType} to the expression of its
     * method-level {@code @PreAuthorize}, or to {@link #NO_METHOD_SECURITY} when it has none.
     * <p>
     * A handler is a declared, non-synthetic method carrying {@code @RequestMapping} or any
     * annotation meta-annotated with it ({@code @GetMapping}, {@code @PostMapping}, and the
     * rest), which is what makes a handler added with any verb show up here.
     */
    private static Map<String, String> policiesOf(Class<?> controllerType) {
        Map<String, String> policies = new LinkedHashMap<>();
        Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .forEach(method -> policies.put(method.getName(), policyOf(method)));
        return policies;
    }

    private static String policyOf(Method handler) {
        PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);
        return preAuthorize == null ? NO_METHOD_SECURITY : preAuthorize.value();
    }
}
