package com.rivoo.billing.application.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire format of {@link PlanResponse} and its nested
 * {@link PlanLimitsPublicResponse} at the JSON level. Sibling of
 * {@link SubscriptionResponseJsonTest} and {@link PortalResponseJsonTest} — see the
 * former for why these assertions are made against the serialized string rather than
 * against record accessors (an accessor assertion is renamed together with the record
 * component, so it stays green through a rename that silently changes the emitted key;
 * this repo shipped exactly that bug as {@code active}/{@code isActive}), and for the
 * verification that no {@code PropertyNamingStrategy} or naming-related customization
 * is in play in this module or in {@code rivoo-common}.
 * <p>
 * This DTO is served by {@code GET /api/v1/billing/plans}, which is anonymous end to end:
 * {@code BillingSecurityConfig} permits it and so does the gateway's
 * {@code GatewaySecurityConfig}. Two properties therefore matter here and are asserted
 * below: the keys a pricing screen reads are stable, and nothing tenant-scoped is emitted.
 */
@JsonTest
class PlanResponseJsonTest {

    @Autowired
    private JacksonTester<PlanResponse> json;

    @Test
    void serializesTheKeysTheFrontendPlanInfoAlreadyDeclares() throws Exception {
        String jsonContent = json.write(premium()).getJson();

        // rivoo-frontend/src/types/billing.ts — PlanInfo, field for field. These were
        // already on the wire before `limits` was added and must stay untouched.
        assertThat(jsonContent).contains("\"id\":\"pln_premium\"");
        assertThat(jsonContent).contains("\"name\":\"PREMIUM\"");
        assertThat(jsonContent).contains("\"displayName\":\"Plan Premium\"");
        assertThat(jsonContent).contains("\"monthlyPrice\":59.00");
        assertThat(jsonContent).contains("\"trialDays\":0");
    }

    @Test
    void serializesLimitsAsANestedObjectUnderTheLimitsKey() throws Exception {
        String jsonContent = json.write(premium()).getJson();

        assertThat(jsonContent).contains("\"limits\":{");

        // Jackson 3 exposes each record component under its own name; a boolean component
        // named smsRemindersEnabled emits "smsRemindersEnabled", not "sms" and not
        // "isSmsRemindersEnabled". These four names are the whole point of the field.
        assertThat(jsonContent).contains("\"maxEmployees\":10");
        assertThat(jsonContent).contains("\"maxAppointmentsPerMonth\":-1");
        assertThat(jsonContent).contains("\"emailRemindersEnabled\":true");
        assertThat(jsonContent).contains("\"smsRemindersEnabled\":true");
    }

    @Test
    void flattenedLimitFieldsAreNotEmittedAtTheTopLevel() throws Exception {
        String jsonContent = json.write(premium()).getJson();

        // The alternative shape (four flat fields on PlanResponse) was rejected; if
        // someone flattens it later, the nested `limits` accessor the pricing screen
        // reads disappears and this says so.
        assertThat(jsonContent).contains("\"limits\":{\"maxEmployees\":10");
    }

    @Test
    void keepsAbsentLimitKeysPresentAsNull_neverCoercedToMinusOneOrFalse() throws Exception {
        // A plan with no row for max_employees and none for sms_reminders_enabled.
        PlanResponse response = new PlanResponse(
                "pln_basic", "BASIC", "Plan Basic", new BigDecimal("29.00"), 0,
                new PlanLimitsPublicResponse(null, 200, true, null));

        String jsonContent = json.write(response).getJson();

        // The keys must still be emitted (as null) rather than vanish, so a client can
        // tell "unspecified" apart from a key it forgot to read. And they must NOT be
        // coerced: -1 means "unlimited" in this schema and false means "disabled", so
        // either would advertise a limit nobody ever configured.
        assertThat(jsonContent).contains("\"maxEmployees\":null");
        assertThat(jsonContent).contains("\"smsRemindersEnabled\":null");
        assertThat(jsonContent).doesNotContain("\"maxEmployees\":-1");
        assertThat(jsonContent).doesNotContain("\"maxEmployees\":0");
        assertThat(jsonContent).doesNotContain("\"smsRemindersEnabled\":false");
    }

    @Test
    void emitsNothingTenantScoped() throws Exception {
        String jsonContent = json.write(premium()).getJson();

        // NOT the guard — PlanCatalogueExposureTest is, via an allowlist over every record
        // reachable from PlanResponse and over the emitted keys at every depth. This is a
        // blocklist and fires only for the six names below; `seatsUsed` or `salonId` would
        // sail past it, and so would a nested type swapped wholesale for one carrying a
        // field with a name nobody listed here — the allowlist exists for exactly that.
        //
        // It is kept because the two coverages are disjoint, not because this one is a
        // safety net for the other. This layer is name-dependent but structure-blind: it
        // matches substrings anywhere in the serialized tree, so it catches these six names
        // at any depth, inside a type nobody has allowlisted yet, and also catches a
        // variant that merely contains one of them (`stripeCustomerId`, `tenantIdHash`).
        // The allowlist is the mirror image: name-independent, but it only sees what the
        // walk from PlanResponse reaches. It is also the only place that names the specific
        // fields we are afraid of and says out loud that they must never appear on this
        // endpoint. If the two ever disagree, the allowlist wins.
        //
        // Note that rivoo-frontend/src/types/billing.ts:PlanLimitsResponse — the type for
        // the INTERNAL per-tenant endpoint — declares currentEmployeeCount and
        // currentAppointmentCount, so those names are already in the vocabulary of
        // whoever next edits a "plan limits" DTO.
        assertThat(jsonContent).doesNotContain("tenantId");
        assertThat(jsonContent).doesNotContain("currentEmployeeCount");
        assertThat(jsonContent).doesNotContain("currentAppointmentCount");
        assertThat(jsonContent).doesNotContain("status");
        assertThat(jsonContent).doesNotContain("stripe");
        assertThat(jsonContent).doesNotContain("subscription");
    }

    private PlanResponse premium() {
        return new PlanResponse(
                "pln_premium", "PREMIUM", "Plan Premium", new BigDecimal("59.00"), 0,
                new PlanLimitsPublicResponse(10, -1, true, true));
    }
}
