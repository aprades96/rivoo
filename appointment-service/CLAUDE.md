# appointment-service — Module CLAUDE.md

## Purpose

Core product value. Handles appointment booking, availability checking, conflict detection, and appointment lifecycle management. Also serves the public booking endpoint (no JWT).

**Port**: 8085 | **DB**: `appointment_db` | **Package**: `com.rivoo.appointment`

---

## Database: `appointment_db`

### Table: `appointments` (prefix: `apt_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `apt_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | |
| `client_id` | CHAR(44) NULL | NULL for walk-ins. Cross-service ref to client-service external_id |
| `client_name` | VARCHAR(200) NOT NULL | **Denormalized snapshot** (immutable after booking) |
| `client_phone` | VARCHAR(20) NULL | Snapshot |
| `client_email` | VARCHAR(255) NULL | Snapshot |
| `employee_id` | CHAR(44) NOT NULL | Cross-service ref to staff-service external_id |
| `employee_name` | VARCHAR(200) NOT NULL | Snapshot |
| `service_id` | CHAR(44) NOT NULL | Cross-service ref to staff-service external_id |
| `service_name` | VARCHAR(200) NOT NULL | Snapshot |
| `service_price` | DECIMAL(10,2) NOT NULL | Snapshot at booking time |
| `service_duration_minutes` | INT NOT NULL | Snapshot |
| `start_time` | TIMESTAMP NOT NULL | **Stored in UTC**, converted to salon timezone in application |
| `end_time` | TIMESTAMP NOT NULL | `start_time + duration` (UTC) |
| `status` | ENUM('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW') DEFAULT 'PENDING' | |
| `cancellation_reason` | VARCHAR(500) NULL | |
| `cancelled_by` | ENUM('CLIENT','SALON','SYSTEM') NULL | |
| `source` | ENUM('ONLINE','PHONE','WALK_IN','MANUAL') DEFAULT 'MANUAL' | |
| `notes` | TEXT NULL | |
| `reminder_sent` | BOOLEAN DEFAULT FALSE | |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Critical Indexes

```sql
idx_appointments_tenant_start       (tenant_id, start_time)
idx_appointments_employee_start     (tenant_id, employee_id, start_time)
idx_appointments_overlap_check      (tenant_id, employee_id, start_time, end_time, status)
idx_appointments_reminder           (start_time, reminder_sent, status)
```

---

## State Machine

```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
             ↓
          CANCELLED       NO_SHOW
```

Valid transitions:
- PENDING → CONFIRMED, CANCELLED
- CONFIRMED → IN_PROGRESS, CANCELLED, NO_SHOW
- IN_PROGRESS → COMPLETED
- COMPLETED, CANCELLED, NO_SHOW → terminal (no further transitions)

---

## Availability Algorithm

1. Get employee working hours (from staff-service or cached)
2. Get salon business hours (from salon-service or cached)
3. Get existing appointments for the employee on the requested date
4. Calculate free slots = working hours - existing appointments
5. Check that requested time fits within a free slot

**Concurrency protection**: `SELECT ... FOR UPDATE` on the employee's appointments for the time range to prevent double-booking race conditions.

---

## UTC Timestamps

- All `start_time` and `end_time` are stored in **UTC** in MySQL `TIMESTAMP` columns
- Application layer converts to/from the salon's timezone (`Europe/Madrid`) for display
- API accepts local time from clients, converts to UTC before persisting
- API returns UTC times; frontend converts to local display

---

## Public Booking (no JWT)

### Endpoint: `POST /api/v1/appointments/book`

**Anti-abuse protections**:
- **Rate limiting**: 10 req/min per IP (enforced at gateway, stricter than general 100 req/min)
- **Honeypot field**: hidden form field. If populated → reject silently (bot detection)
- **Email validation**: valid format + MX record check
- **Booking window**: only 1 hour to 60 days in the future
- **Deduplication**: no 2 appointments from same email+phone on same day at same salon

### Public Booking Flow

```
STEP 0: Rate limiting at gateway (10 req/min per IP)
STEP 1: Validate salon slug (salon-service: GET /api/internal/salons/by-slug/{slug})
        → Get tenantId + verify salon is ACTIVE
STEP 2: Validate employee and service (staff-service)
STEP 3: Verify plan appointment limit (billing-service, BYPASS cache)
STEP 4: Check availability (local)
STEP 5: Find or create client by email+phone (client-service)
        → If exists: link. If not: create with source=ONLINE_BOOKING
STEP 6: INSERT appointment with source=ONLINE, status=PENDING
STEP 7: Schedule confirmation notification + reminders
```

### Request

```java
public record PublicBookingRequest(
    @NotBlank String salonSlug,
    @NotBlank String employeeExternalId,
    @NotBlank String serviceExternalId,
    @NotBlank String clientFirstName,
    @NotBlank String clientLastName,
    @Email String clientEmail,
    String clientPhone,
    @NotNull @Future LocalDateTime requestedTime,  // converted to UTC internally
    String honeypot  // if non-null/non-empty → reject silently
) {}
```

---

## Endpoints

### Public (no JWT)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/appointments/book` | Public booking |

### Authenticated (JWT required)

| Method | Path | Purpose | Roles |
|--------|------|---------|-------|
| GET | `/api/v1/appointments` | List appointments (date range) | SALON_OWNER, EMPLOYEE (*) |
| POST | `/api/v1/appointments` | Create appointment (salon-side) | SALON_OWNER, EMPLOYEE |
| GET | `/api/v1/appointments/{id}` | Get appointment details | SALON_OWNER, EMPLOYEE (*) |
| PUT | `/api/v1/appointments/{id}/status` | Change status | SALON_OWNER, EMPLOYEE (*) |
| PUT | `/api/v1/appointments/{id}/cancel` | Cancel appointment | SALON_OWNER, EMPLOYEE (*) |
| GET | `/api/v1/appointments/availability` | Check available slots | SALON_OWNER, EMPLOYEE |

(*) EMPLOYEE sees/manages only their own appointments.

### Internal (PSK required)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| GET | `/api/internal/admin/appointments/stats` | Appointment statistics | admin-service |

---

## Plan Limits

| Plan | Max Appointments/Month |
|------|----------------------|
| FREE_TRIAL | 50 |
| BASIC | 200 |
| PREMIUM | unlimited (-1) |
| ENTERPRISE | unlimited (-1) |

Checked via billing-service with **cache bypass** for write operations.

---

## Business Rules

1. Snapshots are **immutable** — changing an employee's name doesn't affect past appointments
2. Cross-service references use `CHAR(44)` external_ids (no real FK)
3. Walk-in appointments: `client_id = NULL`, client info filled manually
4. Cancellation triggers notification-service to cancel scheduled reminders and send cancellation notice
5. `reminder_sent` flag prevents duplicate reminder sends

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: staff-service (validate employee/service), client-service (validate/find-or-create client), billing-service (plan limits), notification-service (schedule/cancel reminders), salon-service (slug lookup for public booking)
- **Called by**: admin-service (stats)
