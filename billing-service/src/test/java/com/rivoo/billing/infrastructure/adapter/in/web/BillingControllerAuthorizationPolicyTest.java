package com.rivoo.billing.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ReflectionUtils;
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
 * <b>The whole hierarchy, not just the declared methods.</b> Spring MVC maps handlers through
 * {@code MethodIntrospector.selectMethods}, which walks superclasses and interfaces, so a
 * {@code @GetMapping} on an abstract superclass of {@link BillingController} is a live,
 * routable endpoint. This file used to enumerate with {@code getDeclaredMethods()}, which sees
 * none of them: adding such a handler on a superclass left the suite green while the route
 * answered with no role check on it. The enumeration below therefore uses
 * {@code ReflectionUtils.getUniqueDeclaredMethods}, which walks the same hierarchy, and the
 * expression is read with {@code AnnotatedElementUtils.findMergedAnnotation}, which — like
 * method security itself — finds an annotation on the method a handler overrides.
 * <p>
 * The expected expressions are hardcoded string literals on purpose. An expectation derived
 * from the annotation under test cannot fail, and the expression is the half that was
 * previously unchecked: swapping {@code hasRole('SALON_OWNER')} for {@code hasRole('EMPLOYEE')}
 * used to leave the suite green.
 */
class BillingControllerAuthorizationPolicyTest {

    /**
     * The policy value for a handler that deliberately carries no {@code @PreAuthorize}.
     * <p>
     * A sentinel object, not a string: {@link PreAuthorize#value()} is always a
     * {@code String}, so no annotation value can be {@code equals} to this one, whatever
     * someone writes between the parentheses. The previous marker was the literal
     * {@code "(none - reachable without a role check)"}, documented here as impossible to
     * confuse with a real annotation value — writing exactly that string as the expression on
     * {@code listPlans} left the suite green. Spring would have failed evaluating it (it is
     * not parseable SpEL) rather than opened the endpoint, so the hole was never reachable in
     * production, but the guard should not depend on that.
     */
    private static final Object NO_METHOD_SECURITY = new Object() {
        @Override
        public String toString() {
            return "(none: the handler carries no @PreAuthorize)";
        }
    };

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
    private static final Map<String, Object> EXPECTED_POLICIES = Map.of(
            "getSubscription", "hasRole('SALON_OWNER')",
            "createCheckout", "hasRole('SALON_OWNER')",
            "createPortalSession", "hasRole('SALON_OWNER')",
            "listPlans", NO_METHOD_SECURITY);

    @Test
    void everyHandlerHasADeclaredAuthorizationPolicy() {
        assertThat(policiesOf(BillingController.class).keySet())
                .as("The handlers on BillingController are no longer the ones whose authorization "
                        + "policy is declared in EXPECTED_POLICIES. A handler was added, removed or "
                        + "renamed — on the controller itself or on a type it extends, both of which "
                        + "Spring MVC maps. Add it to EXPECTED_POLICIES with the @PreAuthorize "
                        + "expression it must carry, or with NO_METHOD_SECURITY if it is meant to be "
                        + "reachable without a role check — and if so, check that BillingSecurityConfig "
                        + "and the gateway's GatewaySecurityConfig agree, because they, not this "
                        + "annotation, decide whether a JWT is required at all.")
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
        // reads method-level annotations only, so this is asserted separately — and through
        // findMergedAnnotation, which searches the type hierarchy the way Spring Security's own
        // lookup does. Class.getAnnotation would already have caught one on a superclass
        // (@PreAuthorize is @Inherited); what it misses is one on an INTERFACE, which @Inherited
        // does not cover and Spring Security honours regardless.
        assertThat(AnnotatedElementUtils.findMergedAnnotation(BillingController.class, PreAuthorize.class))
                .as("A class-level @PreAuthorize was added to BillingController or to a type it "
                        + "extends or implements. It applies to every handler, including the "
                        + "anonymous GET /api/v1/billing/plans, which both security configs serve "
                        + "with permitAll.")
                .isNull();
    }

    @Test
    void theProbeReadsInheritedHandlersAndExpressionsRatherThanPassingVacuously() {
        // Control. Every assertion above compares this probe's output against hardcoded
        // literals, so a probe returning nothing would fail on an empty map. What that would
        // NOT catch is a probe that finds only some handlers (missing @PostMapping, say, or
        // missing everything a superclass declares — which is what getDeclaredMethods() did
        // here), reports every expression as absent, or reports absence for a handler that has
        // one — each of which would let the exact mutation this class exists to stop slip
        // through. The decoy mixes both HTTP verbs, an unguarded handler, a guarded one, a
        // non-handler method, and an abstract superclass carrying one of each; the probe must
        // report all of it.
        assertThat(policiesOf(AuthorizationProbeDecoy.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "guardedByGet", "hasRole('DECOY_ROLE')",
                        "unguardedByPost", NO_METHOD_SECURITY,
                        "guardedByInheritedGet", "hasRole('DECOY_INHERITED_ROLE')",
                        "unguardedByInheritedPost", NO_METHOD_SECURITY));
    }

    /**
     * The inherited half of the decoy. Nothing on this class is declared by
     * {@link AuthorizationProbeDecoy}, yet Spring MVC would map both handlers on it, which is
     * the whole point: the probe has to see them.
     */
    abstract static class AuthorizationProbeDecoyBase {

        @GetMapping("/inherited-guarded")
        @PreAuthorize("hasRole('DECOY_INHERITED_ROLE')")
        public void guardedByInheritedGet() {
        }

        @PostMapping("/inherited-unguarded")
        public void unguardedByInheritedPost() {
        }
    }

    @RequestMapping("/decoy")
    static class AuthorizationProbeDecoy extends AuthorizationProbeDecoyBase {

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
     * Maps every request handler Spring MVC would map on {@code controllerType} to the
     * expression of its {@code @PreAuthorize}, or to {@link #NO_METHOD_SECURITY} when it has
     * none.
     * <p>
     * A handler is a non-synthetic method carrying {@code @RequestMapping} or any annotation
     * meta-annotated with it ({@code @GetMapping}, {@code @PostMapping}, and the rest), which
     * is what makes a handler added with any verb show up here.
     * <p>
     * {@code getUniqueDeclaredMethods} walks the class hierarchy and drops methods that a more
     * specific type overrides, which is the same set {@code MethodIntrospector.selectMethods}
     * hands to {@code RequestMappingHandlerMapping}. {@code getDeclaredMethods()} would stop at
     * {@code controllerType} and miss every handler inherited from a superclass.
     */
    private static Map<String, Object> policiesOf(Class<?> controllerType) {
        Map<String, Object> policies = new LinkedHashMap<>();
        Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(controllerType))
                .filter(method -> !method.isSynthetic())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .forEach(method -> policies.put(method.getName(), policyOf(method)));
        return policies;
    }

    private static Object policyOf(Method handler) {
        PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(handler, PreAuthorize.class);
        return preAuthorize == null ? NO_METHOD_SECURITY : preAuthorize.value();
    }
}
