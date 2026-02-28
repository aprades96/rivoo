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
| `status` | ENUM('ACTIVE','INACTIVE','SUSPENDED','ONBOARDING') DEFAULT 'ONBOARDING' | |
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
STEP 1: Create salon (status=ONBOARDING)           → local, if fails: nothing to compensate
STEP 2: Create user in Keycloak via auth-service    → if fails: delete salon
STEP 3: Create subscription in billing-service      → if fails: delete Keycloak user + salon
STEP 4: Activate salon (status=ACTIVE)              → local
STEP 5: Send welcome email (notification-service)   → fire-and-forget (non-critical)
```

**Compensations**:
- Step 2 fails → delete salon from DB
- Step 3 fails → call auth-service to delete Keycloak user, then delete salon

**Stale onboarding cleanup**: `@Scheduled` job finds salons with `status=ONBOARDING` older than 1 hour → marks as `FAILED`.

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
4. Business hours: exactly 7 rows (Mon-Sun), created on salon registration

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: auth-service (register-owner), billing-service (create subscription), notification-service (welcome email)
- **Called by**: billing-service (suspend), appointment-service (slug lookup), admin-service (list)
