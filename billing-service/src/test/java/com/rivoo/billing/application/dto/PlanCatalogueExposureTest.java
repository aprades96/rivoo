package com.rivoo.billing.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <b>Anchored at the root, not at a list of classes.</b> Both assertions start from
 * {@link PlanResponse} - the type the handler returns - and follow the graph outwards. An
 * earlier version named {@link PlanResponse} and {@link PlanLimitsPublicResponse}
 * explicitly, one assertion each, which pinned two classes rather than the payload:
 * changing the TYPE of the {@code limits} component to a variant carrying
 * {@code usedSeatsThisTenant}, with a delegating constructor so no call site had to change,
 * left the suite green while the anonymous endpoint emitted the new field. The two
 * assertions about {@link PlanLimitsPublicResponse} still passed - over a class the
 * response no longer reached. That is the same failure this file exists to stop (a guard
 * asserting it protects something it does not reach), so the anchor is now the root and
 * everything reachable from it has to be listed.
 * <p>
 * <b>Reflection, not a fixture.</b> {@link Class#getRecordComponents()} sees a component
 * whether or not any test bothers to populate it - the second hole in the blocklist, which
 * asserts on a hand-built payload and so is blind to a field left null in every fixture.
 * The instances serialized below are built reflectively from the canonical constructor for
 * the same reason: adding a component must make THIS assertion fail with a readable
 * message, not turn this file into a compile error whose fix is to pass one more argument.
 * <p>
 * <b>Two layers, and they fail differently.</b> They are kept separate because neither
 * subsumes the other:
 * <ul>
 * <li>{@link #everyRecordReachableFromTheCatalogueEntryCarriesExactlyTheAllowedComponents()}
 * walks record components transitively, unwrapping collections, {@link Optional}, arrays,
 * generics and cycles. It therefore reaches a record referenced only through a
 * {@code List<...>}, which the serialization layer below never instantiates. What it
 * cannot see is a component whose type is NOT a record: a plain class with getters
 * contributes keys to the wire and no record components.</li>
 * <li>{@link #theCatalogueEntryEmitsExactlyTheAllowedKeysAtEveryDepth()} flattens the
 * serialized JSON to dotted paths at every depth. It describes what actually leaves the
 * process, so it catches a wire name the component name hides ({@code @JsonProperty},
 * {@code @JsonIgnore}, a naming strategy) and non-record nested types. What it cannot see
 * is a record reached only through a collection, because the builder below fills a
 * collection-typed component with {@code null} rather than fabricating an element.</li>
 * </ul>
 * Serialization goes through the Boot-autoconfigured Jackson 3 {@link ObjectMapper} - the
 * same bean that backs the {@code JacksonTester} used by {@link PlanResponseJsonTest} and
 * the same one the controller serializes with.
 * <p>
 * This test is what the class javadoc on {@link PlanLimitsPublicResponse} refers to.
 */
@JsonTest
class PlanCatalogueExposureTest {

    /**
     * Every record reachable from {@link PlanResponse}, and the exact set of components it
     * is allowed to carry.
     * <p>
     * Every name here describes the TIER, not a tenant: it is identical for every caller
     * and is pricing-page material. A name that can only be answered by knowing WHO is
     * asking does not belong on this list - it belongs on {@link PlanLimitsResponse},
     * served by the PSK-gated per-tenant endpoint. The same rule applies one level up: the
     * catalogue entry itself is public by construction.
     * <p>
     * Keyed by simple name, for readable failure messages. Two reachable records sharing a
     * simple name would be ambiguous, so the walk throws rather than letting one silently
     * overwrite the other.
     */
    private static final Map<String, Set<String>> PUBLIC_CATALOGUE_COMPONENTS = Map.of(
            "PlanResponse", Set.of(
                    "id",
                    "name",
                    "displayName",
                    "monthlyPrice",
                    "trialDays",
                    "limits"),
            "PlanLimitsPublicResponse", Set.of(
                    "maxEmployees",
                    "maxAppointmentsPerMonth",
                    "emailRemindersEnabled",
                    "smsRemindersEnabled"));

    /**
     * The same allowlist expressed as what leaves the process: every key the serialized
     * catalogue entry emits, at every depth, as a dotted path ({@code parent.child}).
     */
    private static final Set<String> PUBLIC_CATALOGUE_KEYS = Set.of(
            "id",
            "name",
            "displayName",
            "monthlyPrice",
            "trialDays",
            "limits.maxEmployees",
            "limits.maxAppointmentsPerMonth",
            "limits.emailRemindersEnabled",
            "limits.smsRemindersEnabled");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void everyRecordReachableFromTheCatalogueEntryCarriesExactlyTheAllowedComponents() {
        assertThat(componentsByReachableRecord(PlanResponse.class))
                .as("The records reachable from PlanResponse, or their components, are no longer "
                        + "exactly the allowlist. PlanResponse is what GET /api/v1/billing/plans "
                        + "serves to anonymous callers, so this covers the entry AND every type "
                        + "nested under it - including one substituted for another, which is how a "
                        + "guard pinned to a fixed list of classes gets walked around. If the new "
                        + "component describes the plan tier, add it here. If it describes a tenant "
                        + "(usage, consumption, subscription state, salon identity), it belongs on "
                        + "PlanLimitsResponse and the PSK-gated per-tenant endpoint instead.")
                .containsExactlyInAnyOrderEntriesOf(PUBLIC_CATALOGUE_COMPONENTS);
    }

    @Test
    void theCatalogueEntryEmitsExactlyTheAllowedKeysAtEveryDepth() {
        assertThat(serializedKeysAtEveryDepthOf(instantiateWithDefaults(PlanResponse.class)))
                .as("The keys leaving the process for the anonymous plan catalogue are no longer "
                        + "exactly the allowlist. This is the whole tree flattened to dotted paths, "
                        + "not just the top level, so a key added inside `limits` - or inside a type "
                        + "swapped in for it - shows up here. A renamed key (@JsonProperty, "
                        + "@JsonIgnore, a naming strategy) diverges from the component name, which "
                        + "is why this is checked separately from the reflection assertion above.")
                .containsExactlyInAnyOrderElementsOf(PUBLIC_CATALOGUE_KEYS);
    }

    @Test
    void bothProbesActuallySeeAnUnlistedFieldNestedUnderTheRoot() {
        // Control. Both assertions above compare a probe's output against a hardcoded
        // allowlist, so a probe that went blind would at least have to fail on an empty
        // set. What that would NOT catch is a probe that stops at the root and never
        // descends - which is exactly the mutation these assertions were rewritten to
        // kill: a nested type carrying a tenant-scoped field. The decoy carries the exact
        // name from that mutation, one level down, in records shaped like the real ones,
        // and both probes must report it at that depth.
        assertThat(componentsByReachableRecord(DecoyCatalogueEntry.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "DecoyCatalogueEntry", Set.of("id", "limits"),
                        "TenantScopedDecoy", Set.of("maxEmployees", "usedSeatsThisTenant")));
        assertThat(serializedKeysAtEveryDepthOf(instantiateWithDefaults(DecoyCatalogueEntry.class)))
                .containsExactlyInAnyOrder("id", "limits.maxEmployees", "limits.usedSeatsThisTenant");
    }

    @Test
    void theWalkFollowsRecordsHiddenInContainersAndTerminatesOnCycles() {
        // Second control, for the two properties of the walk that the real DTOs do not
        // exercise today and that a future edit could rely on: a record referenced only
        // through a collection, an Optional or an array is still reached, and a
        // self-referential record does not spin forever. The serialization probe
        // deliberately does NOT fabricate collection elements, so the container case is
        // this walk's job alone - see the class javadoc.
        assertThat(componentsByReachableRecord(DecoyContainer.class).keySet())
                .containsExactlyInAnyOrder("DecoyContainer", "DecoyCatalogueEntry", "TenantScopedDecoy");

        assertThat(componentsByReachableRecord(DecoySelfReference.class))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "DecoySelfReference", Set.of("id", "next")));
        assertThat(serializedKeysAtEveryDepthOf(instantiateWithDefaults(DecoySelfReference.class)))
                .containsExactlyInAnyOrder("id", "next");
    }

    record TenantScopedDecoy(Integer maxEmployees, Integer usedSeatsThisTenant) {
    }

    record DecoyCatalogueEntry(String id, TenantScopedDecoy limits) {
    }

    record DecoyContainer(List<TenantScopedDecoy> tiers,
                          Optional<DecoyCatalogueEntry> maybeEntry,
                          TenantScopedDecoy[] asArray) {
    }

    record DecoySelfReference(String id, DecoySelfReference next) {
    }

    /**
     * Every record reachable from {@code rootRecord} by following record components, mapped
     * to its component names.
     * <p>
     * A component contributes its own name and, if its type mentions a record anywhere
     * ({@code Nested}, {@code List<Nested>}, {@code Optional<Nested>}, {@code Nested[]},
     * {@code Map<String, List<Nested>>}), that record to the frontier. An already-visited
     * record is not re-queued, so a cycle terminates.
     */
    private static Map<String, Set<String>> componentsByReachableRecord(Class<?> rootRecord) {
        if (!rootRecord.isRecord()) {
            throw new IllegalStateException(rootRecord.getName() + " is not a record. This walk only "
                    + "understands record components, so it would report nothing at all and the "
                    + "allowlist would pass vacuously.");
        }
        Map<String, Set<String>> componentsByRecord = new LinkedHashMap<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> frontier = new ArrayDeque<>();
        visited.add(rootRecord);
        frontier.add(rootRecord);
        while (!frontier.isEmpty()) {
            Class<?> current = frontier.remove();
            Set<String> componentNames = new LinkedHashSet<>();
            for (RecordComponent component : current.getRecordComponents()) {
                componentNames.add(component.getName());
                List<Class<?>> nestedRecords = new ArrayList<>();
                collectRecordTypes(component.getGenericType(), nestedRecords);
                nestedRecords.stream().filter(visited::add).forEach(frontier::add);
            }
            if (componentsByRecord.put(current.getSimpleName(), componentNames) != null) {
                throw new IllegalStateException("Two distinct records reachable from "
                        + rootRecord.getSimpleName() + " share the simple name '"
                        + current.getSimpleName() + "'. This map is keyed by simple name so the "
                        + "allowlist stays readable; rename one rather than silencing this.");
            }
        }
        return componentsByRecord;
    }

    /** Adds every record type mentioned anywhere in {@code type} to {@code found}. */
    private static void collectRecordTypes(Type type, List<Class<?>> found) {
        switch (type) {
            case Class<?> clazz -> {
                if (clazz.isArray()) {
                    collectRecordTypes(clazz.getComponentType(), found);
                } else if (clazz.isRecord()) {
                    found.add(clazz);
                }
            }
            case ParameterizedType parameterized -> {
                collectRecordTypes(parameterized.getRawType(), found);
                for (Type argument : parameterized.getActualTypeArguments()) {
                    collectRecordTypes(argument, found);
                }
            }
            case GenericArrayType genericArray ->
                    collectRecordTypes(genericArray.getGenericComponentType(), found);
            case WildcardType wildcard -> {
                for (Type bound : wildcard.getUpperBounds()) {
                    collectRecordTypes(bound, found);
                }
            }
            case TypeVariable<?> variable -> {
                for (Type bound : variable.getBounds()) {
                    collectRecordTypes(bound, found);
                }
            }
            default -> {
                // Nothing else can mention a record type.
            }
        }
    }

    /**
     * Serializes {@code value} and returns every key it emits as a dotted path, at every
     * depth: {@code limits.maxEmployees}, not {@code limits}. An object or array node
     * contributes its leaves; an empty one contributes its own path, so a key that reaches
     * the wire is never invisible here. Array elements collapse onto the same path - the
     * question is which names are emitted, not how many times.
     */
    private Set<String> serializedKeysAtEveryDepthOf(Object value) {
        Object tree = objectMapper.readValue(objectMapper.writeValueAsString(value), Object.class);
        Set<String> keys = new LinkedHashSet<>();
        collectDottedKeys(tree, "", keys);
        return keys;
    }

    private static void collectDottedKeys(Object node, String path, Set<String> keys) {
        switch (node) {
            case Map<?, ?> map when !map.isEmpty() -> map.forEach((key, child) ->
                    collectDottedKeys(child, path.isEmpty() ? String.valueOf(key) : path + "." + key, keys));
            case List<?> list when !list.isEmpty() -> list.forEach(element ->
                    collectDottedKeys(element, path, keys));
            case null, default -> keys.add(path);
        }
    }

    /**
     * Builds a record from its canonical constructor with a default value per component, so
     * the arity of the record is never baked into this file. Every component of these DTOs
     * is nullable by design (a {@code null} limit means "no row for that limit_key"), and
     * Jackson emits null-valued keys here - this module sets no
     * {@code default-property-inclusion} - so the emitted key set is complete regardless of
     * the values. Primitives get their zero value from a one-element array, which the JVM
     * initialises to the correct default for whichever primitive type it holds.
     * <p>
     * A component whose type is itself a record is built recursively rather than left
     * {@code null}, otherwise the nested object would serialize as {@code "limits":null}
     * and the depth this file exists to inspect would emit no keys at all. A record already
     * under construction on the current path is left {@code null}, so a self-referential
     * record terminates. A collection-typed component is left {@code null}: fabricating an
     * element would need the element type, which is the reachability walk's job.
     */
    private static <T> T instantiateWithDefaults(Class<T> recordType) {
        Set<Class<?>> underConstruction = new LinkedHashSet<>();
        underConstruction.add(recordType);
        return recordType.cast(instantiateWithDefaults(recordType, underConstruction));
    }

    private static Object instantiateWithDefaults(Class<?> recordType, Set<Class<?>> underConstruction) {
        try {
            Class<?>[] componentTypes = Arrays.stream(recordType.getRecordComponents())
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            Constructor<?> canonical = recordType.getDeclaredConstructor(componentTypes);
            canonical.setAccessible(true);
            Object[] arguments = Arrays.stream(componentTypes)
                    .map(type -> defaultValueFor(type, underConstruction))
                    .toArray();
            return canonical.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate " + recordType.getName(), e);
        }
    }

    private static Object defaultValueFor(Class<?> componentType, Set<Class<?>> underConstruction) {
        if (componentType.isPrimitive()) {
            return Array.get(Array.newInstance(componentType, 1), 0);
        }
        if (componentType.isRecord() && underConstruction.add(componentType)) {
            try {
                return instantiateWithDefaults(componentType, underConstruction);
            } finally {
                underConstruction.remove(componentType);
            }
        }
        return null;
    }
}
