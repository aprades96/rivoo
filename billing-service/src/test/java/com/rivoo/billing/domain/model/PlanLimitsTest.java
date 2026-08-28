package com.rivoo.billing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlanLimits} is the single flattening of the {@code plan_limits} key/value rows,
 * shared by the per-tenant internal endpoint ({@code PlanLimitsService}) and the anonymous
 * plan catalogue ({@code SubscriptionService#listActivePlans}). It is pinned directly here
 * because a drift in the key strings or in the int-to-boolean encoding would silently
 * change both endpoints at once.
 */
class PlanLimitsTest {

    @Test
    void flattensTheFourKnownKeys() {
        PlanLimits limits = PlanLimits.from(List.of(
                row("max_employees", 3),
                row("max_appointments_per_month", 200),
                row("email_reminders_enabled", 1),
                row("sms_reminders_enabled", 0)));

        assertThat(limits.maxEmployees()).isEqualTo(3);
        assertThat(limits.maxAppointmentsPerMonth()).isEqualTo(200);
        assertThat(limits.emailRemindersEnabled()).isTrue();
        assertThat(limits.smsRemindersEnabled()).isFalse();
    }

    @Test
    void keepsMinusOneAsIs_itMeansUnlimitedNotAbsent() {
        PlanLimits limits = PlanLimits.from(List.of(
                row("max_employees", -1),
                row("max_appointments_per_month", -1)));

        // -1 is a configured value with a meaning ("unlimited"), so it must survive
        // untouched and must not be confused with the null used for an absent row.
        assertThat(limits.maxEmployees()).isEqualTo(-1);
        assertThat(limits.maxAppointmentsPerMonth()).isEqualTo(-1);
    }

    @Test
    void reportsAnAbsentRowAsNull_notAsZeroAndNotAsMinusOne() {
        PlanLimits limits = PlanLimits.from(List.of(row("max_appointments_per_month", 50)));

        assertThat(limits.maxEmployees()).isNull();
        assertThat(limits.emailRemindersEnabled()).isNull();
        assertThat(limits.smsRemindersEnabled()).isNull();
        assertThat(limits.maxAppointmentsPerMonth()).isEqualTo(50);
    }

    @Test
    void emptyRows_yieldAllNulls() {
        PlanLimits limits = PlanLimits.from(List.of());

        assertThat(limits.maxEmployees()).isNull();
        assertThat(limits.maxAppointmentsPerMonth()).isNull();
        assertThat(limits.emailRemindersEnabled()).isNull();
        assertThat(limits.smsRemindersEnabled()).isNull();
    }

    @Test
    void treatsOnlyOneAsEnabled() {
        // The column is INT, so nothing at the schema level stops a 2 from being stored.
        // The established encoding is 1 = enabled; anything else is not enabled, which is
        // the safe reading for a feature flag.
        assertThat(PlanLimits.from(List.of(row("sms_reminders_enabled", 2))).smsRemindersEnabled())
                .isFalse();
        assertThat(PlanLimits.from(List.of(row("sms_reminders_enabled", 0))).smsRemindersEnabled())
                .isFalse();
        assertThat(PlanLimits.from(List.of(row("sms_reminders_enabled", 1))).smsRemindersEnabled())
                .isTrue();
    }

    @Test
    void ignoresKeysItDoesNotKnow() {
        PlanLimits limits = PlanLimits.from(List.of(
                row("max_storage_mb", 500),
                row("max_employees", 10)));

        // A future limit_key added to the table must not disturb the four this record
        // exposes, nor leak into the response by accident.
        assertThat(limits.maxEmployees()).isEqualTo(10);
        assertThat(limits.maxAppointmentsPerMonth()).isNull();
    }

    @Test
    void keyConstantsMatchTheSeededLimitKeys() {
        // V1__create_initial_schema.sql seeds exactly these four strings, verified against
        // the live billing_db.plan_limits table. A typo here reads as "no row configured".
        assertThat(PlanLimits.MAX_EMPLOYEES_KEY).isEqualTo("max_employees");
        assertThat(PlanLimits.MAX_APPOINTMENTS_PER_MONTH_KEY).isEqualTo("max_appointments_per_month");
        assertThat(PlanLimits.EMAIL_REMINDERS_ENABLED_KEY).isEqualTo("email_reminders_enabled");
        assertThat(PlanLimits.SMS_REMINDERS_ENABLED_KEY).isEqualTo("sms_reminders_enabled");
    }

    private PlanLimit row(String key, int value) {
        return PlanLimit.builder().planId(1L).limitKey(key).limitValue(value).build();
    }
}
