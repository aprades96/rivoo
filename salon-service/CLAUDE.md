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

**Between registration and the owner's first authenticated call** the salon exists but is not
published: `status=ONBOARDING`, `owner_user_id` set, slug and address reserved, FREE_TRIAL
subscription created in billing-service. It is absent from `GET /api/v1/salons/public/{slug}` (404),
from public availability and from public booking in appointment-service (404, same exception as an
unknown slug). The owner cannot log in either — Keycloak blocks them until `VERIFY_EMAIL` is done —
so nothing of theirs is unreachable that they could otherwise reach. Admin surfaces
(`GET /api/internal/admin/salons`) and the internal by-slug lookup still show it, with its real
status.

**Publication — the owner's first authenticated request**: `GET /api/v1/salons/me` publishes the
salon. Keycloak creates the owner with a pending `VERIFY_EMAIL` required action and refuses to
complete a login while any required action is pending, so a token for this tenant **cannot exist**
unless the owner confirmed the address. The proof therefore arrives with the request; nothing has to
go and ask, and there is no timer, no polling and no state machine. `SalonService.getByTenantId`
promotes the salon to `ACTIVE` and sends the `WELCOME` mail — whose copy says "tu salón está
activo", true only from that moment — and then serves the dashboard as usual.

Three properties of that promotion, all pinned by `SalonRegistrationPublicVisibilityTest`:

- **Conditional.** The write is reachable only from a salon that is still `ONBOARDING`. Every later
  dashboard load is a plain read: no update statement is issued at all.
- **Concurrency-safe.** The promotion is one conditional statement
  (`SalonPersistencePort.activateIfOnboarding` → `UPDATE ... WHERE tenant_id = ? AND status =
  'ONBOARDING'`) and the caller acts on the rows-affected count, never on its own earlier read. Two
  simultaneous dashboard loads therefore produce one promotion and **one** welcome mail; the loser
  re-reads the row rather than assuming what the winner wrote.
- **`email_verified` is belt and braces, not the gate.** The claim reaches the controller through
  `KeycloakJwtConverter` → `TenantAwareJwtAuthenticationToken.getEmailVerified()` (three-valued:
  `TRUE` / `FALSE` / `null` when the realm does not map it). An explicit `FALSE` withholds the
  promotion. An **absent** claim does not: the token's existence is the real proof, and failing
  closed there would strand every owner on a realm whose `email` client scope is not default. In
  the `rivoo` realm the claim IS mapped and IS in the access token — `email` is listed in
  `defaultDefaultClientScopes` and carries the `email verified` protocol mapper
  (`infrastructure/keycloak/rivoo-realm.json`).

`GET /api/v1/salons/me` also allows `EMPLOYEE`, and that cannot be used to publish a salon early: an
employee account only exists because a `SALON_OWNER` of the same tenant created it through
staff-service, which needs an owner token, which needs the owner to have completed `VERIFY_EMAIL` —
by which time the salon is already published. Were that ever to stop holding, an employee arriving
first would still be sound proof of the same fact.

**An owner who never opens their dashboard keeps an invisible salon for ever.** That is the accepted
outcome, not an oversight: they cannot take a booking anyway without first adding services, which is
done from that same dashboard. The row is kept (see the cleanup rule below) so its address and slug
stay reserved.

**Account enumeration is NOT closed.** What is closed is one specific channel; two remain, and both
are real.

- **Closed — the registration response itself.** `POST /api/v1/salons` answers **202 Accepted with
  one fixed body**, byte-identical across all three outcomes (address free, address already has a
  salon, address known to Keycloak with no salon row), and never 201/409. The difference goes to the
  address owner by email (`REGISTRATION_ATTEMPT_EXISTING_ACCOUNT`) instead of to the caller. Pinned
  by `SalonRegistrationEnumerationTest`. **That is the whole of what is closed. Nothing else.**
- **OPEN — the slug-allocation oracle.** Slugs are derived from the attacker-supplied `name` and
  de-duplicated by appending `-2`, `-3`, … (`OnboardingSagaService#generateUniqueSlug`), so slug
  allocation leaks whether the previous registration created a row:
  1. `POST /api/v1/salons {email: victim, name: "probe-x"}` → always 202.
  2. `POST /api/v1/salons {email: <disposable mailbox the attacker controls>, name: "probe-x"}` →
     always 202. This one gets `probe-x` if the first attempt created **nothing** (address TAKEN)
     and `probe-x-2` if it created a row (address FREE).
  3. The attacker verifies their own disposable address, opens the dashboard once (which publishes
     their salon) and reads the answer off `GET /api/v1/salons/me` — or simply off the public page:
     200 on `probe-x-2` means the victim's address was free, 200 on `probe-x` means it was taken.

  Two anonymous POSTs, one disposable mailbox, one click. Deterministic, no timing analysis. Making
  registration end in `ONBOARDING` did NOT close this: the row still exists and still holds the slug.
- **OPEN — the timing channel.** Three distinguishable classes of work, differing in ROUND-TRIP
  COUNT rather than by a statistical residue, so discriminable from a single sample in most
  deployments:
  1. *Address already has a salon* (`existsByEmail` is true): one query, plus one notification POST.
  2. *Address known to Keycloak but with no salon row* (auth-service answers 409): the slug
     existence queries, the salon INSERT, seven business-hours rows, the auth-service call (≥ 4
     Keycloak round trips inside it), the compensating DELETE, plus one notification POST.
  3. *Address free*: as (2) minus the DELETE and minus the notification POST, plus the second salon
     UPDATE that links the owner and one billing-service call.

  Closing it needs asynchronous registration (accept, queue, answer immediately, do the work off the
  request thread), which is a redesign.

**Do not describe this endpoint as non-enumerable, closed, or safe against enumeration.** Only the
response body is uniform.

**Compensations**:
- Step 2 fails → delete salon from DB
- Step 3/4 fail → call auth-service to delete Keycloak user, then delete salon

**Stale onboarding cleanup**: `@Scheduled` job finds salons with `status=ONBOARDING` older than 1
hour **and no `owner_user_id`** → marks as `FAILED`. The owner-less condition is load-bearing:
ONBOARDING now also means "waiting for the owner to turn up authenticated", and reaping that after
an hour would leave an owner who reads their mail in the evening with a permanently invisible salon
and no self-service way out. Such a salon waits indefinitely and keeps its address and slug —
releasing them would let the next probe re-create it. Pinned by `SalonSchedulingConfigTest`.

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

---

## Business Rules

1. Salon `external_id` = `tenant_id` (the salon IS the tenant)
2. `subscription_plan` in salon is a **cache** — source of truth is billing-service
3. Slug must be unique and URL-safe
3b. A salon is **not publicly visible until its owner makes an authenticated request**
   (`GET /api/v1/salons/me`). Registration is anonymous, so until then nobody has proved they
   control the address that was submitted; a Keycloak token for the tenant is that proof.
4. Business hours: exactly 7 rows (Mon-Sun), created on salon registration

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: auth-service (register-owner), billing-service (create subscription), notification-service (welcome email)
- **Called by**: billing-service (suspend), appointment-service (slug lookup), admin-service (list)
