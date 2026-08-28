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
| `active` | BOOLEAN DEFAULT TRUE | Columna real: `active`, NO `is_active` |

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
| GET | `/api/v1/billing/plans` | List available plans with their per-plan `limits` (pricing page, pre-signup) |

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
> `BillingSecurityConfig.java:45` (`requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()`)
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

### `@PreAuthorize` on `/api/v1/billing/**` is pinned by reflection, not enforced by a test

`spring-boot-test-autoconfigure-4.0.3.jar` ships exactly two slices, `json` and `jdbc`.
The version is the one the build resolves (root `pom.xml` pins `spring-boot-starter-parent`
4.0.3); several versions sit in the local `.m2`, so read the resolved classpath, not a
directory listing:

```
mvn -o -pl billing-service dependency:build-classpath -Dmdep.outputFile=cp.txt
unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-test-autoconfigure/4.0.3/spring-boot-test-autoconfigure-4.0.3.jar \
  | grep "org/springframework/boot/test/autoconfigure/"
```

> **`-pl` without `-am` is a false-green trap everywhere except here.** The command above
> only prints a classpath, so it compiles nothing and cannot go stale. Do not copy that
> idiom into a run meant to *verify* anything: `mvn -o -pl billing-service test` resolves
> `rivoo-common` from `~/.m2` instead of building it, so billing-service compiles and tests
> against whatever jar was installed there last — green, and silent about the working tree.
> Use `-am`, or the full reactor `mvn -o clean test`.

**`@WebMvcTest` and `@AutoConfigureMockMvc` do not exist here**, so controller tests use
`MockMvcBuilders.standaloneSetup(...)`, which installs neither the Spring Security filter
chain nor the method-security interceptor.

#### What IS pinned

`BillingControllerAuthorizationPolicyTest` owns the authorization invariant for every handler
on `BillingController`. It reads the annotations reflectively — the only mechanism available
without a security slice — and pins, per handler, **both** the presence/absence of
`@PreAuthorize` **and its exact expression**, compared against hardcoded string literals (an
expectation derived from the annotation under test cannot fail):

| Handler | Pinned policy |
|---------|---------------|
| `GET /subscription` | `hasRole('SALON_OWNER')` |
| `POST /checkout-session` | `hasRole('SALON_OWNER')` |
| `POST /portal` | `hasRole('SALON_OWNER')` |
| `GET /plans` | no `@PreAuthorize` — anonymous by design |

Two further properties make it durable rather than a snapshot of the day it was written:

- **Allowlist over the handler set, across the whole hierarchy.** Handlers are enumerated
  with `ReflectionUtils.getUniqueDeclaredMethods`, which walks superclasses the way Spring
  MVC's `MethodIntrospector.selectMethods` does (non-synthetic methods carrying
  `@RequestMapping` or anything meta-annotated with it, so any verb is caught). A handler
  present in the code but absent from the expected map fails the build — a new endpoint is red
  until someone states its policy. Same reasoning `PlanCatalogueExposureTest` applies to the
  fields of the public DTOs.

  That claim used to be false by one route: the enumeration read `getDeclaredMethods()`, which
  stops at `BillingController`. A `@GetMapping` on an abstract superclass that the controller
  extended left the suite green while `GET /api/v1/billing/inherited-danger` answered with no
  role check on it. Bounded — `anyRequest().authenticated()` still demands a JWT, so the
  exposure was "any authenticated role", not anonymous — but the map no longer covered every
  live handler. Measured: row 12 below.
- **Class-level `@PreAuthorize` is asserted absent**, through
  `AnnotatedElementUtils.findMergedAnnotation`, which searches the type hierarchy the way Spring
  Security's own lookup does. `Class.getAnnotation` already covered a superclass (`@PreAuthorize`
  is `@Inherited`) but not an *interface*, where `@Inherited` does not apply and Spring Security
  honours the annotation anyway — row 15. One added anywhere in that hierarchy applies to every
  handler, including the anonymous catalogue, without touching a single method.
- **The "no annotation" marker is a sentinel object, not a string.** `@PreAuthorize` values are
  always `String`, so nothing written between the parentheses can be `equals` to it. It used to
  be the literal `"(none - reachable without a role check)"`, documented as impossible to
  confuse with a real value; writing exactly that string on `listPlans` left the suite green
  (row 14). Not exploitable — Spring would have failed evaluating unparseable SpEL rather than
  opened the endpoint — but the guard should not lean on that.

Measured by mutation (full reactor `mvn -o clean test`, one edit at a time, with the file hash
asserted to have **changed** before the run is trusted — line endings are **mixed** in this
repo, `BillingController.java` is CRLF while the test files under `src/test` are LF, so a
pattern anchored on `\n` silently matches nothing in the former and one anchored on `\r\n`
matches nothing in the latter; use `\r?\n` and verify the hash):

| Mutation | Result |
|----------|--------|
| delete `@PreAuthorize` from `getSubscription` | **BUILD FAILURE** |
| delete `@PreAuthorize` from `createCheckout` | **BUILD FAILURE** |
| delete `@PreAuthorize` from `createPortalSession` | **BUILD FAILURE** |
| `getSubscription` expression → `hasRole('EMPLOYEE')` | **BUILD FAILURE** |
| `createCheckout` expression → `hasRole('EMPLOYEE')` | **BUILD FAILURE** |
| `createPortalSession` expression → `hasRole('EMPLOYEE')` | **BUILD FAILURE** |
| `createCheckout` expression → `permitAll()` | **BUILD FAILURE** |
| add `@PreAuthorize` to `listPlans` | **BUILD FAILURE** |
| add a new handler (unguarded), test untouched | **BUILD FAILURE** |
| add a new handler (guarded), test untouched | **BUILD FAILURE** |
| add a class-level `@PreAuthorize` | **BUILD FAILURE** |
| add a handler on an abstract superclass `BillingController` extends | **BUILD FAILURE** |
| add a class-level `@PreAuthorize` on that superclass | **BUILD FAILURE** |
| `listPlans` expression → the literal text of the old "no annotation" marker | **BUILD FAILURE** |
| add a class-level `@PreAuthorize` on an interface `BillingController` implements | **BUILD FAILURE** |

Rows 12, 14 and 15 were **BUILD SUCCESS** until the enumeration, the marker and the class-level
lookup were changed as described above. Row 13 already failed beforehand — `@PreAuthorize` is
`@Inherited`, so `Class.getAnnotation` saw one on a superclass; it is listed because the lookup
changed under it, not because it was a hole. Rows 1-11 were re-measured afterwards and still
fail.

Before this test existed, only rows 1, 3, 8 and 11 were **BUILD FAILURE**; every other row was
**BUILD SUCCESS**. Rows 1-4 were measured directly on the pre-change tree; the rest follow from
reading the five assertions that were doing the work at the time, in
`BillingControllerPlansTest.listPlans_handlerCarriesNoMethodSecurityAnnotation` — it compared
annotations against `null`/non-`null` and never read `.value()` (so rows 5-7, the expression
swaps, could not fail), never enumerated the controller's methods (so rows 9-10, a new handler,
could not fail), and did assert the class-level annotation was absent (so row 11 was already
covered, and remains so in the new class).

So `createCheckout` — the handler that starts a Stripe payment — was guarded by nothing, and no
expression on any handler was checked. What coverage `getSubscription` and `createPortalSession`
had was incidental: two of those assertions existed only as a control that the probe was not
blind. That test method has been removed — the new class subsumes all of it, and two files
half-asserting one invariant is exactly how it drifted in the first place.

#### What is still NOT covered

A reflection test proves the annotation is **written** as intended. It does **not** prove Spring
**enforces** it. Nothing in this module — or anywhere in the repo — observes a caller without
`ROLE_SALON_OWNER` actually being rejected. Unverified, specifically: that `@EnableMethodSecurity`
(`BillingSecurityConfig`) wires an interceptor onto these methods at all; that the security
filter chain runs; and that `KeycloakJwtConverter` emits the `ROLE_` prefix `hasRole` expects
(that class has no test of its own). If method security were silently switched off with every
annotation still in place, the whole suite would stay green.

Closing that needs a `@SpringBootTest`-based security test with Testcontainers, tagged
`@Tag("integration")` and excluded from the default surefire run. Still open.

### Response DTO field names must be pinned at the JSON level

`SubscriptionResponseJsonTest` and `PortalResponseJsonTest` (`@JsonTest` + `JacksonTester`,
Jackson 3 / `tools.jackson.databind`) assert on the **serialized string**, not on record
accessors. This is deliberate: an accessor assertion is renamed together with the record
component, so it stays green through a rename that silently changes the wire format. The repo
already shipped a production bug this way (`active`/`isActive`) — hence the sibling
`*JsonTest` files in salon-service and staff-service.

Any new field on a response DTO that the frontend or another service reads gets a key-level
assertion. Adding a field is additive and safe; renaming or retyping one is not.

### The anonymous plan catalogue is guarded by an allowlist, not a blocklist

`GET /api/v1/billing/plans` is readable by anyone on the internet (see the endpoint table),
so `PlanResponse` and everything nested under it are the one part of this module where
**adding** a field is not safe. `PlanCatalogueExposureTest` is the guard, in two layers, both
anchored at `PlanResponse` — the type the handler returns — rather than at a list of classes:

1. **Reachability walk over record components.** From `PlanResponse`, it follows
   `getRecordComponents()` transitively (unwrapping collections, `Optional`, arrays and
   generics, terminating on cycles) and requires the set of reachable records, and the
   components of each, to equal a hardcoded map.
2. **Emitted keys at every depth.** It serializes a reflectively built instance and flattens
   the JSON to dotted paths (`limits.maxEmployees`), requiring the complete set to equal a
   hardcoded set. Components and wire keys diverge under `@JsonProperty`, `@JsonIgnore` or a
   naming strategy, and only the keys describe what leaves the process.

Reflection rather than a populated fixture, because a blocklist over a hand-built payload is
blind to a field that no fixture happens to fill in. Neither layer subsumes the other: the walk
reaches a record referenced only through a `List<...>`, which layer 2 never instantiates; layer
2 sees a nested type that is not a record at all, which the walk cannot describe.

Both layers used to name `PlanResponse` and `PlanLimitsPublicResponse` explicitly, one
assertion per class. That pinned two classes, not the payload: changing the **type** of the
`limits` component to a variant carrying `usedSeatsThisTenant`, with a delegating constructor
so no call site changed, left the build green and the anonymous endpoint emitting the field,
while the two assertions about `PlanLimitsPublicResponse` kept passing over a class the
response no longer reached.

| Mutation | Result |
|----------|--------|
| swap the **type** of the `limits` component for one carrying `usedSeatsThisTenant` | **BUILD FAILURE** (both layers) |
| add `Integer usedSeatsThisTenant` to `PlanLimitsPublicResponse` | **BUILD FAILURE** (both layers) |
| rename a component of `PlanLimitsPublicResponse` | **BUILD FAILURE** (both layers) |
| `@JsonProperty("seats")` on `maxEmployees` | **BUILD FAILURE** (layer 2 only — layer 1 sees no change, which is the point of keeping both) |

Row 1 was **BUILD SUCCESS** before the two layers were re-anchored at the root.

`PlanResponseJsonTest.emitsNothingTenantScoped` is the older blocklist over six names
(`tenantId`, `currentEmployeeCount`, `currentAppointmentCount`, `status`, `stripe`,
`subscription`). It is kept because it is disjoint from the allowlist, not because it backs it
up: it matches substrings anywhere in the serialized tree, so it catches those six names at any
depth and catches variants that merely contain one (`stripeCustomerId`), but it is blind to any
name nobody listed — adding `Integer usedSeatsThisTenant` to the record and populating it left
the whole suite green, and so does swapping the nested type. The allowlist is the guard.

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: salon-service (suspend on payment failure), auth-service (disable users on cancellation, update plan attribute), notification-service (payment failure alerts)
- **Called by**: salon-service (create subscription), appointment-service (plan limits), staff-service (plan limits), admin-service (stats)
- **External**: Stripe API
