package com.rivoo.billing.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire format of {@link SubscriptionResponse} at the JSON level, not at
 * the record-accessor level. Sibling of {@code staff-service}'s
 * {@code ServiceOfferingResponseJsonTest} / {@code WorkingHoursResponseJsonTest}
 * and {@code salon-service}'s {@code SalonPublicResponseJsonTest}, which exist
 * because an {@code isOpen}/{@code open} divergence once shipped to production.
 * This is the first such test in billing-service.
 * <p>
 * Why accessor-level assertions are not enough: a test that calls
 * {@code response.stripeSubscriptionId()} gets renamed together with the record
 * component, so it stays green through a rename that silently changes the emitted
 * key. Only asserting on the serialized string can catch that. Verified by
 * mutation: renaming either component turns exactly this test red while the rest
 * of the suite stays green.
 * <p>
 * {@code stripeSubscriptionId} is the field the whole billing-portal feature
 * hangs off — the settings/billing page renders the "Gestionar suscripcion"
 * button only when it is truthy, so an unnoticed rename makes the button
 * disappear with no error anywhere.
 * <p>
 * Spring Boot 4 serializes HTTP responses with Jackson 3
 * ({@code tools.jackson.databind}). {@code @JsonTest} boots the actual
 * Boot-autoconfigured {@link JacksonTester}, backed by
 * {@code tools.jackson.databind.json.JsonMapper}, so this exercises the same
 * mapper the controller uses. {@code @JsonTest} is available here:
 * {@code spring-boot-test-autoconfigure-4.0.0.jar} ships exactly two slices,
 * {@code json} and {@code jdbc} — verified with {@code unzip -l}. {@code @WebMvcTest}
 * and {@code @AutoConfigureMockMvc} are NOT in that jar, which is why the
 * controller tests use {@code MockMvcBuilders.standaloneSetup}.
 * <p>
 * {@link SubscriptionResponse} is a bare record with no Jackson annotations —
 * {@code grep -rn "JsonProperty" billing-service/src/main} returns nothing — so
 * each component name is what Jackson 3 uses as the property name. There is no
 * {@code PropertyNamingStrategy}, {@code JsonMapperBuilderCustomizer} or
 * naming-related {@code Module}/{@code @JacksonComponent} bean in this module or
 * in {@code rivoo-common} either — verified with
 * {@code grep -rln "PropertyNamingStrategy|JsonMapperBuilderCustomizer|JacksonComponent|JsonComponent" billing-service/src/main rivoo-common/src/main},
 * which also returns nothing — so no global renaming can be in play.
 */
@JsonTest
class SubscriptionResponseJsonTest {

    @Autowired
    private JacksonTester<SubscriptionResponse> json;

    @Test
    void serializesStripeIdentifiersUnderTheirPrefixedKeys() throws Exception {
        String jsonContent = json.write(fullyPopulated()).getJson();

        assertThat(jsonContent).contains("\"stripeCustomerId\"");
        assertThat(jsonContent).contains("\"stripeSubscriptionId\"");

        // The obvious refactor is to drop the redundant-looking "stripe" prefix.
        // The frontend reads the prefixed names, so that rename is a silent break.
        assertThat(jsonContent).doesNotContain("\"customerId\"");
        assertThat(jsonContent).doesNotContain("\"subscriptionId\"");
    }

    @Test
    void serializesEveryKeyTheFrontendSubscriptionTypeDeclares() throws Exception {
        String jsonContent = json.write(fullyPopulated()).getJson();

        // rivoo-frontend/src/types/billing.ts — Subscription, field for field.
        assertThat(jsonContent).contains("\"id\"");
        assertThat(jsonContent).contains("\"tenantId\"");
        assertThat(jsonContent).contains("\"planName\"");
        assertThat(jsonContent).contains("\"status\"");
        assertThat(jsonContent).contains("\"stripeCustomerId\"");
        assertThat(jsonContent).contains("\"stripeSubscriptionId\"");
        assertThat(jsonContent).contains("\"trialStart\"");
        assertThat(jsonContent).contains("\"trialEnd\"");
        assertThat(jsonContent).contains("\"currentPeriodStart\"");
        assertThat(jsonContent).contains("\"currentPeriodEnd\"");
        assertThat(jsonContent).contains("\"createdAt\"");
    }

    @Test
    void doesNotSerializeUpdatedAt() throws Exception {
        // The frontend type used to declare updatedAt; it was removed because this
        // DTO has never carried it. If it is ever added back here, that removal
        // becomes wrong and this test says so.
        assertThat(json.write(fullyPopulated()).getJson()).doesNotContain("\"updatedAt\"");
    }

    @Test
    void keepsStripeSubscriptionIdKeyPresentWhenNull() throws Exception {
        // A FREE_TRIAL tenant has a Stripe customer but no Stripe subscription yet.
        // The key must still be emitted (as null) rather than vanish, so the
        // frontend's `string | null` declaration stays truthful.
        SubscriptionResponse trialing = new SubscriptionResponse(
                "sub_abc123", "sal_tenant-001", "FREE_TRIAL", "Free Trial", BigDecimal.ZERO,
                "TRIALING", "cus_stripe123", null,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-15T00:00:00Z"),
                null, null, false, Instant.parse("2026-08-01T00:00:00Z"));

        String jsonContent = json.write(trialing).getJson();

        assertThat(jsonContent).contains("\"stripeCustomerId\"");
        assertThat(jsonContent).contains("\"stripeSubscriptionId\"");
    }

    private SubscriptionResponse fullyPopulated() {
        return new SubscriptionResponse(
                "sub_abc123", "sal_tenant-001", "PREMIUM", "Plan Premium", new BigDecimal("59.00"),
                "ACTIVE", "cus_stripe123", "sub_stripe456",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-15T00:00:00Z"),
                Instant.parse("2026-08-15T00:00:00Z"), Instant.parse("2026-09-15T00:00:00Z"),
                false, Instant.parse("2026-08-01T00:00:00Z"));
    }
}
