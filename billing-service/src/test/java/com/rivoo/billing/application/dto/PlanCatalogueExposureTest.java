package com.rivoo.billing.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exposure guard for the ANONYMOUS plan catalogue, {@code GET /api/v1/billing/plans}
 * ({@code permitAll} in {@code BillingSecurityConfig} and in the gateway's
 * {@code GatewaySecurityConfig}, so no JWT is required end to end). Anything reachable
 * from {@link PlanResponse} is readable by anyone on the internet.
 * <p>
 * <b>Allowlist, not blocklist.</b> {@code PlanResponseJsonTest.emitsNothingTenantScoped}
 * asserts that six specific names ({@code tenantId}, {@code currentEmployeeCount},
 * {@code currentAppointmentCount}, {@code status}, {@code stripe}, {@code subscription})
 * are absent from the payload. That guard only fires for a name someone thought of in
 * advance: adding {@code seatsUsed}, {@code remainingAppointments} or {@code salonId} to
 * {@link PlanLimitsPublicResponse} walks straight past it and ships tenant-scoped data on
 * an unauthenticated endpoint. Verified by mutation - adding
 * {@code Integer usedSeatsThisTenant} and populating it left the whole suite green. The
 * assertions below invert that: the component set and the emitted key set must be EXACTLY
 * the names listed here, so any new field is red until someone deliberately adds it to the
 * allowlist and, in doing so, has to justify putting it on a public endpoint.
 * <p>
 * <b>Reflection, not a fixture.</b> {@link Class#getRecordComponents()} sees a component
 * whether or not any test bothers to populate it - the second hole in the blocklist, which
 * asserts on a hand-built payload and so is blind to a field left null in every fixture.
 * The instances serialized below are built reflectively from the canonical constructor for
 * the same reason: adding a component must make THIS assertion fail with a readable
 * message, not turn this file into a compile error whose fix is to pass one more argument.
 * <p>
 * <b>Both layers.</b> Components and emitted keys are asserted separately because they can
 * diverge: {@code @JsonProperty}, {@code @JsonIgnore} or a naming strategy renames the wire
 * key while the component keeps its name, and only the key set describes what actually
 * leaves the process. Serialization goes through the Boot-autoconfigured Jackson 3
 * {@link ObjectMapper} - the same bean that backs the {@code JacksonTester} used by
 * {@link PlanResponseJsonTest} and the same one the controller serializes with.
 * <p>
 * This test is what the class javadoc on {@link PlanLimitsPublicResponse} refers to.
 */
@JsonTest
class PlanCatalogueExposureTest {

    /**
     * Every name here describes the TIER, not a tenant: it is identical for every caller
     * and is pricing-page material. A name that can only be answered by knowing WHO is
     * asking does not belong on this list - it belongs on {@link PlanLimitsResponse},
     * served by the PSK-gated per-tenant endpoint.
     */
    private static final List<String> PUBLIC_LIMITS_FIELDS = List.of(
            "maxEmployees",
            "maxAppointmentsPerMonth",
            "emailRemindersEnabled",
            "smsRemindersEnabled");

    /** Same rule one level up: the catalogue entry itself is public by construction. */
    private static final List<String> PUBLIC_PLAN_FIELDS = List.of(
            "id",
            "name",
            "displayName",
            "monthlyPrice",
            "trialDays",
            "limits");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicLimitsRecordCarriesExactlyTheAllowedComponents() {
        assertThat(componentNamesOf(PlanLimitsPublicResponse.class))
                .as("A component was added to or removed from PlanLimitsPublicResponse, which is "
                        + "served to anonymous callers by GET /api/v1/billing/plans. If it describes "
                        + "the plan tier, add it to PUBLIC_LIMITS_FIELDS. If it describes a tenant "
                        + "(usage, consumption, subscription state, salon identity), it belongs on "
                        + "PlanLimitsResponse and the PSK-gated per-tenant endpoint instead.")
                .containsExactlyInAnyOrderElementsOf(PUBLIC_LIMITS_FIELDS);
    }

    @Test
    void publicLimitsEmitsExactlyTheAllowedKeys() {
        assertThat(serializedKeysOf(instantiateWithDefaults(PlanLimitsPublicResponse.class)))
                .as("The keys leaving the process for the nested `limits` object are no longer "
                        + "exactly the allowlist. A renamed key (@JsonProperty, @JsonIgnore, a "
                        + "naming strategy) diverges from the component name, so this is checked "
                        + "separately from the reflection assertion.")
                .containsExactlyInAnyOrderElementsOf(PUBLIC_LIMITS_FIELDS);
    }

    @Test
    void catalogueEntryCarriesExactlyTheAllowedComponents() {
        assertThat(componentNamesOf(PlanResponse.class))
                .as("A component was added to or removed from PlanResponse, the entry of the "
                        + "anonymous plan catalogue. Same rule as PlanLimitsPublicResponse: "
                        + "tier-level only, nothing that identifies or describes a tenant.")
                .containsExactlyInAnyOrderElementsOf(PUBLIC_PLAN_FIELDS);
    }

    @Test
    void catalogueEntryEmitsExactlyTheAllowedKeys() {
        assertThat(serializedKeysOf(instantiateWithDefaults(PlanResponse.class)))
                .containsExactlyInAnyOrderElementsOf(PUBLIC_PLAN_FIELDS);
    }

    @Test
    void bothProbesActuallySeeAnUnlistedField() {
        // Control. Every assertion above compares a probe's output against a hardcoded
        // allowlist, so a probe that went blind would at least have to fail on an empty
        // set. What that would NOT catch is a probe that sees only some of the components,
        // or a serializer that silently drops a field it does not recognise - either would
        // let the very mutation this test exists to stop pass unnoticed. This decoy carries
        // the exact name from that mutation, in a record shaped like the real ones, and
        // both probes must report it.
        assertThat(componentNamesOf(TenantScopedDecoy.class))
                .containsExactly("maxEmployees", "usedSeatsThisTenant");
        assertThat(serializedKeysOf(instantiateWithDefaults(TenantScopedDecoy.class)))
                .containsExactlyInAnyOrder("maxEmployees", "usedSeatsThisTenant");
    }

    record TenantScopedDecoy(Integer maxEmployees, Integer usedSeatsThisTenant) {
    }

    private static List<String> componentNamesOf(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private Set<String> serializedKeysOf(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap =
                objectMapper.readValue(objectMapper.writeValueAsString(value), Map.class);
        return asMap.keySet();
    }

    /**
     * Builds a record from its canonical constructor with a default value per component, so
     * the arity of the record is never baked into this file. Every component of these DTOs
     * is nullable by design (a {@code null} limit means "no row for that limit_key"), and
     * Jackson emits null-valued keys here - this module sets no
     * {@code default-property-inclusion} - so the emitted key set is complete regardless of
     * the values. Primitives get their zero value from a one-element array, which the JVM
     * initialises to the correct default for whichever primitive type it holds.
     */
    private static <T> T instantiateWithDefaults(Class<T> recordType) {
        try {
            Class<?>[] componentTypes = Arrays.stream(recordType.getRecordComponents())
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            Constructor<T> canonical = recordType.getDeclaredConstructor(componentTypes);
            canonical.setAccessible(true);
            Object[] arguments = Arrays.stream(componentTypes)
                    .map(type -> type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null)
                    .toArray();
            return canonical.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate " + recordType.getName(), e);
        }
    }
}
