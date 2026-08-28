package com.rivoo.billing.domain.model;

import java.util.List;

/**
 * Flattened view of the {@code plan_limits} key/value rows belonging to a single plan.
 * <p>
 * This is the ONE place that knows the {@code limit_key} strings and how an {@code INT}
 * column encodes a boolean flag ({@code 1} = enabled). It exists because that knowledge
 * used to live inline in {@code PlanLimitsService} and now has two consumers — the
 * per-tenant internal endpoint and the anonymous plan catalogue — which must not be able
 * to drift apart on key names or on the int-to-boolean encoding.
 * <p>
 * <b>Every component is nullable, deliberately.</b> {@code null} means "this plan has no
 * row for that key", which is NOT the same as any in-range value: {@code -1} already means
 * "unlimited" in this schema and {@code 0} means "zero / disabled". Collapsing an absent
 * row into either of those at this level would silently invent a limit, so the decision is
 * pushed to each caller, which states its own default explicitly at the call site.
 */
public record PlanLimits(
        Integer maxEmployees,
        Integer maxAppointmentsPerMonth,
        Boolean emailRemindersEnabled,
        Boolean smsRemindersEnabled
) {

    public static final String MAX_EMPLOYEES_KEY = "max_employees";
    public static final String MAX_APPOINTMENTS_PER_MONTH_KEY = "max_appointments_per_month";
    public static final String EMAIL_REMINDERS_ENABLED_KEY = "email_reminders_enabled";
    public static final String SMS_REMINDERS_ENABLED_KEY = "sms_reminders_enabled";

    /**
     * Flattens the rows of a single plan. Rows belonging to other plans are not filtered
     * out here — callers are expected to pass one plan's rows, which is what both
     * {@code findByPlanId} and a per-plan slice of {@code findByPlanIds} return.
     */
    public static PlanLimits from(List<PlanLimit> rows) {
        return new PlanLimits(
                value(rows, MAX_EMPLOYEES_KEY),
                value(rows, MAX_APPOINTMENTS_PER_MONTH_KEY),
                flag(rows, EMAIL_REMINDERS_ENABLED_KEY),
                flag(rows, SMS_REMINDERS_ENABLED_KEY));
    }

    private static Integer value(List<PlanLimit> rows, String key) {
        return rows.stream()
                .filter(row -> key.equals(row.getLimitKey()))
                .map(PlanLimit::getLimitValue)
                .findFirst()
                .orElse(null);
    }

    private static Boolean flag(List<PlanLimit> rows, String key) {
        Integer raw = value(rows, key);
        return raw == null ? null : raw == 1;
    }
}
