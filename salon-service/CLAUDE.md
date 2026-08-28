# salon-service — Module CLAUDE.md

## Purpose

Manages salon profiles, business hours, and special dates. **Orchestrates the onboarding saga** (registration of a new salon with compensations).

**Port**: 8082 | **DB**: `salon_db` | **Package**: `com.rivoo.salon`

---

## Database: `salon_db`

### Table: `salons` (prefix: `sal_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `sal_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | Same as external_id for salons (salon IS the tenant) |
| `name` | VARCHAR(200) NOT NULL | |
| `slug` | VARCHAR(200) UNIQUE | URL-friendly name |
| `owner_user_id` | VARCHAR(36) NOT NULL | Keycloak user UUID |
| `email` | VARCHAR(255) NOT NULL | Contact email |
| `phone` | VARCHAR(20) NOT NULL | |
| `description` | TEXT NULL | |
| `address_street` | VARCHAR(300) NOT NULL | |
| `address_city` | VARCHAR(100) DEFAULT 'Barcelona' | |
| `address_postal_code` | VARCHAR(10) NOT NULL | |
| `timezone` | VARCHAR(50) DEFAULT 'Europe/Madrid' | |
| `currency` | VARCHAR(3) DEFAULT 'EUR' | |
| `subscription_plan` | ENUM('FREE_TRIAL','BASIC','PREMIUM','ENTERPRISE') DEFAULT 'FREE_TRIAL' | Cache (truth in billing-service) |
| `status` | ENUM('ONBOARDING','ACTIVE','INACTIVE','SUSPENDED','FAILED') DEFAULT 'ONBOARDING' | `ONBOARDING` = registered, owner has not confirmed their email yet — NOT publicly visible |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Table: `salon_business_hours`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `salon_id` | BIGINT FK → salons(id) CASCADE | |
| `day_of_week` | TINYINT NOT NULL | 1=Mon...7=Sun (ISO 8601) |
| `is_open` | BOOLEAN DEFAULT TRUE | |
| `open_time` | TIME NULL | |
| `close_time` | TIME NULL | |
| `break_start_time` | TIME NULL | |
| `break_end_time` | TIME NULL | |

UNIQUE: `(salon_id, day_of_week)` — exactly 7 rows per salon.

---

## Onboarding Saga (salon-service orchestrates)

```
STEP 0: Address already has an account?            → send "someone tried to register" mail, RETURN
STEP 1: Create salon (status=ONBOARDING)           → local, if fails: nothing to compensate
STEP 2: Create user in Keycloak via auth-service    → if fails: delete salon
STEP 3: Link owner to salon (status STAYS ONBOARDING) → if fails: delete Keycloak user + salon
STEP 4: Create subscription in billing-service      → if fails: delete Keycloak user + salon
```

The saga ENDS there. It does not activate the salon and it sends no mail of its own: the only mail
this path produces is Keycloak's `VERIFY_EMAIL`, which is what the fixed 202 body ("revisa tu
correo") refers to.

**Between registration and verification** the salon exists but is not published: `status=ONBOARDING`,
`owner_user_id` set, slug and address reserved, FREE_TRIAL subscription created in billing-service.
It is absent from `GET /api/v1/salons/public/{slug}` (404), from public availability and from public
booking in appointment-service (404, same exception as an unknown slug). The owner cannot log in
either — Keycloak blocks them until `VERIFY_EMAIL` is done — so nothing of theirs is unreachable that
they could otherwise reach. Admin surfaces (`GET /api/internal/admin/salons`) and the internal
by-slug lookup still show it, with its real status.

**Activation — owner email verification**: `OwnerVerificationActivationService`, on a 1-minute
`@Scheduled` tick, asks auth-service whether each ONBOARDING salon's owner has confirmed their
address (`GET /api/internal/auth/users/{keycloakUserId}/email-verified`, which reads Keycloak's
`emailVerified`) and promotes the ones that have to `ACTIVE`, then sends the `WELCOME` mail — whose
copy says "tu salón está activo", true only from that moment. Polling rather than a Keycloak event
listener SPI or a browser callback: Keycloak notifies nobody, an SPI means shipping a JAR into the
identity provider for one boolean, and a browser callback would put the trigger back in the hands of
an anonymous caller. Only an explicit `true` promotes; a salon that cannot be checked is left
pending and retried.

**What is closed, and what is not**

- **Closed — the response**: `POST /api/v1/salons` answers **202 Accepted with one fixed body**
  whether the address was free or already had an account, and never 201/409. The difference goes to
  the address owner by email (`REGISTRATION_ATTEMPT_EXISTING_ACCOUNT`) instead of to the caller. The
  same applies when auth-service answers 409 (address known to Keycloak but with no salon row): the
  saga rolls the salon back and returns that identical 202. See `SalonRegistrationEnumerationTest`.
- **Closed — the side effect**: this used to be open, and the uniform response above did NOT close
  it. Registration published the salon immediately (`status=ACTIVE`) under a slug derived from the
  attacker-supplied `name`, so `POST /api/v1/salons {email: victim, name: "probe-aaa-111"}` followed
  by `GET /api/v1/salons/public/probe-aaa-111` answered **200 for a FREE address and 404 for a TAKEN
  one** — the same yes/no, one anonymous request later, no timing analysis, ~50 addresses a minute
  under the gateway's general tier. Registering into ONBOARDING closes it: an unverified probe and a
  taken address both yield 404. See `SalonRegistrationPublicVisibilityTest`, which drives the real
  saga down both paths and compares the public surface.
- **NOT closed — timing**: the free path does DB writes plus three synchronous inter-service calls
  (auth-service alone makes ≥ 4 Keycloak round trips) while the taken path does one query and one
  notification POST. That is a difference in ROUND-TRIP COUNT, not a statistical residue —
  discriminable from a single sample in most deployments. Closing it needs asynchronous registration
  (accept, queue, answer immediately, do the work off the request thread), which is a redesign.
  **Do not describe the endpoint as non-enumerable without qualification.**

**Compensations**:
- Step 2 fails → delete salon from DB
- Step 3/4 fail → call auth-service to delete Keycloak user, then delete salon

**Stale onboarding cleanup**: `@Scheduled` job finds salons with `status=ONBOARDING` older than 1
hour **and no `owner_user_id`** → marks as `FAILED`. The owner-less condition is load-bearing:
ONBOARDING now also means "waiting for the owner to click the link", and reaping that after an hour
would leave an owner who reads their mail in the evening with a permanently invisible salon and no
self-service way out. Such a salon waits indefinitely and keeps its address and slug — releasing
them would let the next probe re-create it. Pinned by `SalonSchedulingConfigTest`.

---

## Endpoints

### Public (no JWT)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/salons/public/{slug}` | Public salon page (for booking) |

### Authenticated (JWT required)

| Method | Path | Purpose | Roles |
|--------|------|---------|-------|
| POST | `/api/v1/salons` | Register new salon (onboarding) | unauthenticated (new user) |
| GET | `/api/v1/salons/me` | Get current salon profile | SALON_OWNER, EMPLOYEE |
| PUT | `/api/v1/salons/me` | Update salon profile | SALON_OWNER |
| GET | `/api/v1/salons/me/business-hours` | Get business hours | SALON_OWNER, EMPLOYEE |
| PUT | `/api/v1/salons/me/business-hours` | Update business hours | SALON_OWNER |

### Internal (PSK required)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| PUT | `/api/internal/salons/{tenantId}/status` | Update salon status | billing-service (suspend on payment failure) |
| GET | `/api/internal/salons/by-slug/{slug}` | Get salon by slug | appointment-service (public booking) |
| GET | `/api/internal/admin/salons` | List all salons | admin-service |

**Consumed** from auth-service: `GET /api/internal/auth/users/{keycloakUserId}/email-verified` — the
activation tick's only input.

---

## Business Rules

1. Salon `external_id` = `tenant_id` (the salon IS the tenant)
2. `subscription_plan` in salon is a **cache** — source of truth is billing-service
3. Slug must be unique and URL-safe
3b. A salon is **not publicly visible until its owner has verified their email address**. Registration
   is anonymous, so until then nobody has proved they control the address that was submitted.
4. Business hours: exactly 7 rows (Mon-Sun), created on salon registration

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: auth-service (register-owner), billing-service (create subscription), notification-service (welcome email)
- **Called by**: billing-service (suspend), appointment-service (slug lookup), admin-service (list)
