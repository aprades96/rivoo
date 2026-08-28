# billing-service — Module CLAUDE.md

## Purpose

Manages subscriptions, plans, plan limits, and Stripe integration. Handles the full subscription lifecycle: trial, checkout, payments, upgrades/downgrades, cancellation, and dunning.

**Port**: 8087 | **DB**: `billing_db` | **Package**: `com.rivoo.billing`

---

## Database: `billing_db`

### Table: `subscription_plans` (prefix: `pln_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `pln_` prefix |
| `name` | ENUM('FREE_TRIAL','BASIC','PREMIUM','ENTERPRISE') UNIQUE | |
| `display_name` | VARCHAR(100) NOT NULL | "Plan Premium" |
| `monthly_price` | DECIMAL(10,2) NOT NULL | |
| `stripe_monthly_price_id` | VARCHAR(100) NULL | Stripe Price ID |
| `trial_days` | INT DEFAULT 0 | 14 for FREE_TRIAL |
| `is_active` | BOOLEAN DEFAULT TRUE | |

### Table: `plan_limits`

| limit_key | FREE_TRIAL | BASIC | PREMIUM | ENTERPRISE |
|-----------|-----------|-------|---------|------------|
| `max_employees` | 1 | 3 | 10 | -1 (unlimited) |
| `max_appointments_per_month` | 50 | 200 | -1 | -1 |
| `email_reminders_enabled` | 0 | 1 | 1 | 1 |
| `sms_reminders_enabled` | 0 | 0 | 1 | 1 |

### Table: `subscriptions` (prefix: `sub_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `sub_` prefix |
| `tenant_id` | CHAR(44) UNIQUE | One salon = one subscription |
| `plan_id` | BIGINT FK → subscription_plans(id) | |
| `status` | ENUM('TRIALING','ACTIVE','PAST_DUE','CANCELLED','EXPIRED') DEFAULT 'TRIALING' | |
| `stripe_customer_id` | VARCHAR(100) NULL UNIQUE | |
| `stripe_subscription_id` | VARCHAR(100) NULL UNIQUE | |
| `trial_start` | TIMESTAMP NULL | |
| `trial_end` | TIMESTAMP NULL | |
| `current_period_start` | TIMESTAMP NULL | |
| `current_period_end` | TIMESTAMP NULL | |
| `cancel_at_period_end` | BOOLEAN DEFAULT FALSE | |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Table: `webhook_event_log` (idempotency)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `stripe_event_id` | VARCHAR(100) NOT NULL UNIQUE | Idempotency key |
| `event_type` | VARCHAR(100) NOT NULL | |
| `processed_at` | TIMESTAMP | |
| `payload` | JSON NULL | |

---

## Stripe Integration

### Object Mapping

| Domain Concept | Stripe Object |
|----------------|---------------|
| Salon (tenant) | `stripe.Customer` |
| Subscription Plan | `stripe.Product` (3 fixed products) |
| Monthly Price | `stripe.Price` |
| Active Subscription | `stripe.Subscription` |

### Stripe Products (created via Dashboard)

```
"Rivoo - Basic"      → Price: 2900 (EUR, recurring/month) = 29 EUR
"Rivoo - Premium"    → Price: 5900 (EUR, recurring/month) = 59 EUR
"Rivoo - Enterprise" → Price: 9900 (EUR, recurring/month) = 99 EUR
```

**FREE_TRIAL has NO Product in Stripe**. Managed locally.

### Subscription Lifecycle

1. **Registration**: billing-service creates `stripe.Customer` with metadata `{tenantId}`. Local record: plan=FREE_TRIAL, status=TRIALING, trial_end=now()+14d. NO Stripe Subscription yet.
2. **User chooses plan during trial**: `POST /api/v1/billing/checkout-session` → creates `stripe.Checkout.Session` → returns `checkoutUrl` → Stripe processes → webhook `checkout.session.completed` → link stripe_subscription_id
3. **Trial expires without plan**: Cron marks as EXPIRED, suspends salon
4. **Renewal**: Automatic via Stripe. Webhook `invoice.paid` updates period.
5. **Upgrade/Downgrade**: `stripe.subscriptions.update()` with proration. Updates Keycloak attribute via auth-service.
6. **Cancellation**: `cancel_at_period_end: true`. Not immediate.
7. **Payment failure (Dunning)**:
   - 1st failure → status=PAST_DUE + email to owner
   - 2nd failure → urgent email + banner
   - `customer.subscription.deleted` → status=CANCELLED + suspend salon + disable Keycloak users

### Webhook Handler

**Endpoint**: `POST /api/webhooks/stripe` — public (no JWT). **La firma NO se verifica hoy**: `WebhookController` recibe `Stripe-Signature` como `required = false` y `StripeStubAdapter.constructEvent` ignora la cabecera y parsea el body directamente. Verificar la firma con el endpoint secret es un ticket de seguridad aparte.

**Events handled**:
- `checkout.session.completed` → link stripe_subscription_id
- `invoice.paid` → status=ACTIVE, update period
- `invoice.payment_failed` → status=PAST_DUE, notify
- `customer.subscription.updated` → detect plan change, update Keycloak attribute
- `customer.subscription.deleted` → status=CANCELLED, suspend salon, disable Keycloak users

**Idempotency**: check `webhook_event_log` for `stripe_event_id`. If exists → return 200 without processing.

### Keycloak Attribute Sync

On every plan change, billing-service calls auth-service to update `subscription_plan` attribute in Keycloak, so future JWTs reflect the new plan. Max 15 min inconsistency until user refreshes token.

---

## Cache with Bypass

Plan limits are cached with Caffeine (TTL 5min). **Write operations MUST bypass cache**:

```
GET /api/internal/billing/tenants/{tenantId}/plan-limits
→ Callers specify forWriteOperation=true to bypass
```

---

## Seed Data

`V1__create_initial_schema.sql` must include seed data for `subscription_plans` and `plan_limits` tables.

---

## Endpoints

### Public (no JWT)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/webhooks/stripe` | Stripe webhook handler |
| GET | `/api/v1/billing/plans` | List available plans (pricing page, pre-signup) |

### Authenticated (JWT required)

| Method | Path | Purpose | Roles | Status |
|--------|------|---------|-------|--------|
| GET | `/api/v1/billing/subscription` | Get current subscription | SALON_OWNER | Implemented |
| POST | `/api/v1/billing/checkout-session` | Create Stripe checkout session | SALON_OWNER | Implemented |
| POST | `/api/v1/billing/portal` | Create Stripe billing portal session (returns `{ "url": ... }`). No request body | SALON_OWNER | Implemented |
| POST | `/api/v1/billing/change-plan` | Upgrade/downgrade plan | SALON_OWNER | **NOT IMPLEMENTED** — planned |
| POST | `/api/v1/billing/cancel` | Cancel subscription (end of period) | SALON_OWNER | **NOT IMPLEMENTED** — planned |

> The `Status` column exists because this table used to read as a description of reality while
> listing two endpoints that have never existed in `BillingController`. They are kept as
> planned work, not deleted. Check the controller before assuming a row is live.
>
> `GET /plans` is **anonymous**, not authenticated. Evidence:
> `BillingSecurityConfig.java:38` (`requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()`)
> and `api-gateway` `GatewaySecurityConfig.java:25` (`pathMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()`),
> so no JWT is ever required end to end. The frontend agrees: `getPlans()` in
> `rivoo-frontend/src/lib/api/billing.ts:13` is the only call in that file that takes no `token`.
> This table previously listed the endpoint as authenticated, arguing from the absence of
> `@PreAuthorize` on the handler.
>
> **General rule for this stack**: the auth level of an endpoint is determined by
> `authorizeHttpRequests` in the service's security config plus the gateway's `authorizeExchange`.
> `@PreAuthorize` only *narrows* what those already allow. The absence of `@PreAuthorize` proves
> nothing about the auth level — always read the security config first.

### Internal (PSK required)

Base path is `/api/internal/billing` (`BillingInternalController`), not `/api/internal`.

| Method | Path | Purpose | Called by | Status |
|--------|------|---------|-----------|--------|
| POST | `/api/internal/billing/subscriptions` | Create FREE_TRIAL subscription | salon-service (onboarding) | Implemented |
| GET | `/api/internal/billing/tenants/{tenantId}/plan-limits` | Get plan limits + current usage flags | appointment-service, staff-service | Implemented |
| PUT | `/api/internal/billing/subscriptions/{tenantId}/status` | Update subscription status | webhook/reconciliation flows | Implemented |
| GET | `/api/internal/admin/subscriptions/summary` | Subscription statistics | admin-service | **NOT IMPLEMENTED** — planned |

---

## Business Rules

1. One subscription per tenant (UNIQUE constraint on `tenant_id`)
2. **Downgrade prevalidation**: if current usage exceeds new plan limits → return 409 with violation details
3. **Upgrade**: Stripe first (blocking) → local DB → cache invalidation → update Keycloak attribute
4. FREE_TRIAL is entirely local — no Stripe objects until user chooses a plan
5. Nightly reconciliation (03:00 CET): Stripe is source of truth if discrepancy found

---

## Testing constraints and known gaps

### `@PreAuthorize` on `/api/v1/billing/**` is NOT covered by any test

`spring-boot-test-autoconfigure-4.0.3.jar` ships exactly two slices, `json` and `jdbc`.
The version is the one the build resolves (root `pom.xml` pins `spring-boot-starter-parent`
4.0.3); several versions sit in the local `.m2`, so read the resolved classpath, not a
directory listing:

```
mvn -o -pl billing-service dependency:build-classpath -Dmdep.outputFile=cp.txt
unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-test-autoconfigure/4.0.3/spring-boot-test-autoconfigure-4.0.3.jar \
  | grep "org/springframework/boot/test/autoconfigure/"
```

**`@WebMvcTest` and `@AutoConfigureMockMvc` do not exist here**, so
controller tests use `MockMvcBuilders.standaloneSetup(...)`, which does not install the Spring
Security filter chain or the method-security interceptor.

Consequence, measured rather than assumed: **deleting the `@PreAuthorize` from
`BillingController#createPortalSession` leaves the whole suite green.** The same holds for the
other `/api/v1/billing` handlers. The annotations are load-bearing in production and unguarded
in CI, so the endpoint table above is the only record of the intended role — keep it accurate,
and treat any change to an authorization annotation as needing manual verification.

Closing this properly needs a `@SpringBootTest`-based security test (Testcontainers, `@Tag("integration")`,
excluded from the default surefire run) rather than another standalone slice.

### Response DTO field names must be pinned at the JSON level

`SubscriptionResponseJsonTest` and `PortalResponseJsonTest` (`@JsonTest` + `JacksonTester`,
Jackson 3 / `tools.jackson.databind`) assert on the **serialized string**, not on record
accessors. This is deliberate: an accessor assertion is renamed together with the record
component, so it stays green through a rename that silently changes the wire format. The repo
already shipped a production bug this way (`active`/`isActive`) — hence the sibling
`*JsonTest` files in salon-service and staff-service.

Any new field on a response DTO that the frontend or another service reads gets a key-level
assertion. Adding a field is additive and safe; renaming or retyping one is not.

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: salon-service (suspend on payment failure), auth-service (disable users on cancellation, update plan attribute), notification-service (payment failure alerts)
- **Called by**: salon-service (create subscription), appointment-service (plan limits), staff-service (plan limits), admin-service (stats)
- **External**: Stripe API
