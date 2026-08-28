# client-service — Module CLAUDE.md

## Purpose

Manages salon clients (personal data, notes, visit history). Primary GDPR-affected service — handles anonymization and data export.

**Port**: 8084 | **DB**: `client_db` | **Package**: `com.rivoo.client`

---

## Database: `client_db`

### Table: `clients` (prefix: `cli_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `cli_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | |
| `first_name` | VARCHAR(100) NOT NULL | |
| `last_name` | VARCHAR(100) NOT NULL | |
| `email` | VARCHAR(255) NULL | For reminders |
| `phone` | VARCHAR(20) NULL | |
| `gender` | ENUM('MALE','FEMALE','OTHER','NOT_SPECIFIED') DEFAULT 'NOT_SPECIFIED' | |
| `date_of_birth` | DATE NULL | |
| `notes` | TEXT NULL | "Alergia a X producto" |
| `source` | ENUM('WALK_IN','ONLINE_BOOKING','MANUAL','IMPORTED') DEFAULT 'MANUAL' | |
| `total_visits` | INT DEFAULT 0 | Denormalized counter |
| `last_visit_at` | TIMESTAMP NULL | |
| `active` | BOOLEAN DEFAULT TRUE | Columna real: `active`, NO `is_active` |
| `gdpr_consent_at` | TIMESTAMP NULL | When consent was given |
| `gdpr_anonymized_at` | TIMESTAMP NULL | When anonymization was applied |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

UNIQUE: `(tenant_id, email)` — one client per email within a salon.

---

## GDPR Compliance

### Anonymization (Right to Erasure)

Physical DELETE is NOT used (would break appointment snapshots). Instead, anonymize personal data:

```java
client.setFirstName("ANONIMIZADO");
client.setLastName("ANONIMIZADO");
client.setEmail(null);
client.setPhone(null);
client.setDateOfBirth(null);
client.setNotes(null);
client.setActive(false);
client.setGdprAnonymizedAt(Instant.now());
```

**Important**: `client_name` snapshots in past appointments are NOT anonymized (legal basis: accounting/fiscal obligation). Future appointments for the anonymized client are automatically cancelled.

### Data Export (Right to Access)

Endpoint `GET /api/v1/clients/{id}/export` returns JSON with:
- All client personal data
- Appointment history (fetched from appointment-service)
- Consent dates

---

## Plan Limits Validation — NOT IMPLEMENTED

client-service does **not** call billing-service today. Its only outbound REST adapter is
`AppointmentServiceAdapter`; a grep for billing/plan-limits/subscription across
`client-service/src` returns nothing. Planned shape, if it is ever built:

```
GET /api/internal/billing/tenants/{tenantId}/plan-limits
→ No specific client limit in current plan structure, but validate subscription is active
```

> Note the base path: `/api/internal/billing` (`BillingInternalController:26`), not
> `/api/internal`. This section previously documented the call as if it existed, which is
> how `billing-service/CLAUDE.md` came to list client-service among the plan-limits callers.

---

## Endpoints

### Authenticated (JWT required)

| Method | Path | Purpose | Roles |
|--------|------|---------|-------|
| GET | `/api/v1/clients` | List clients (paginated) | SALON_OWNER, EMPLOYEE (*) |
| POST | `/api/v1/clients` | Create client | SALON_OWNER |
| GET | `/api/v1/clients/{id}` | Get client details | SALON_OWNER, EMPLOYEE (*) |
| PUT | `/api/v1/clients/{id}` | Update client | SALON_OWNER |
| DELETE | `/api/v1/clients/{id}` | Anonymize client (GDPR) | SALON_OWNER |
| GET | `/api/v1/clients/{id}/export` | Export client data (GDPR) | SALON_OWNER |

(*) EMPLOYEE sees only clients from their own appointments.

### Internal (PSK required)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| GET | `/api/internal/clients/{clientId}?tenantId={tenantId}` | Validate client exists & belongs to tenant | appointment-service |
| POST | `/api/internal/clients/find-or-create` | Find by email+phone or create new | appointment-service (public booking) |

---

## Business Rules

1. Anonymization instead of deletion — preserves referential integrity with appointment snapshots
2. `source` tracks how the client was added (walk-in, online booking, manual, imported)
3. `total_visits` and `last_visit_at` are denormalized — updated by appointment-service callbacks or reconciliation
4. Client uniqueness is per-tenant (same email can exist in different salons)

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: appointment-service (for GDPR export data). **NOT billing-service** — see "Plan Limits Validation — NOT IMPLEMENTED" above; there is no billing adapter in this module.
- **Called by**: appointment-service (validate client, find-or-create for public booking)
