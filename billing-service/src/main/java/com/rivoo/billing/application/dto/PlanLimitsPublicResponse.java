package com.rivoo.billing.application.dto;

/**
 * What a plan includes, as served by the ANONYMOUS {@code GET /api/v1/billing/plans}
 * catalogue. Nested inside {@link PlanResponse} under the {@code limits} key.
 * <p>
 * Deliberately a separate type from {@link PlanLimitsResponse}, which serves the
 * per-tenant, PSK-gated {@code GET /api/internal/billing/tenants/{tenantId}/plan-limits}.
 * The two answer different questions ("what does each tier include" vs "what does MY plan
 * allow") and only the second one is allowed to grow tenant-scoped fields. The frontend
 * type {@code rivoo-frontend/src/types/billing.ts:PlanLimitsResponse} already declares
 * {@code currentEmployeeCount} / {@code currentAppointmentCount} — fields the backend
 * record does not have today — so reusing that class here would put a type that is
 * expected to carry usage counts on an unauthenticated endpoint. Nothing tenant-scoped
 * may ever be added to THIS record: no usage counts, no consumption, no subscription
 * state. {@code PlanCatalogueExposureTest} enforces that as an ALLOWLIST — it pins the
 * record components and the emitted JSON keys to exactly the four names below, so any
 * new component fails the build regardless of what it is called, and adding one means
 * consciously editing that allowlist.
 * <p>
 * Boxed types, not primitives: {@code null} means the plan has no row for that
 * {@code limit_key} at all, i.e. "unspecified". It is distinct from {@code -1}
 * ("unlimited", the established convention in this schema) and from {@code 0} / {@code false}
 * ("none" / "disabled"). A pricing page must be able to tell "unlimited employees" from
 * "we never configured this", so the absence is surfaced rather than defaulted away.
 * Jackson emits the key with a {@code null} value (this module sets no
 * {@code default-property-inclusion}), so the shape stays stable.
 */
public record PlanLimitsPublicResponse(
        Integer maxEmployees,
        Integer maxAppointmentsPerMonth,
        Boolean emailRemindersEnabled,
        Boolean smsRemindersEnabled
) {
}
