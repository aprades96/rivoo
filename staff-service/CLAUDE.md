# staff-service — Module CLAUDE.md

## Purpose

Manages employees, their working hours, the service catalog (haircuts, coloring, etc.), and the employee-service relationship (which employee offers which service).

**Port**: 8083 | **DB**: `staff_db` | **Package**: `com.rivoo.staff`

---

## Database: `staff_db`

### Table: `employees` (prefix: `emp_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `emp_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | |
| `keycloak_user_id` | VARCHAR(36) NULL UNIQUE | NULL if employee has no account |
| `first_name` | VARCHAR(100) NOT NULL | |
| `last_name` | VARCHAR(100) NOT NULL | |
| `email` | VARCHAR(255) NULL | |
| `phone` | VARCHAR(20) NULL | |
| `job_title` | VARCHAR(100) NULL | "Barbero Senior", "Estilista" |
| `color_hex` | VARCHAR(7) DEFAULT '#3B82F6' | Calendar color |
| `active` | BOOLEAN DEFAULT TRUE | Columna real: `active`, NO `is_active` |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Table: `employee_working_hours`

Same structure as `salon_business_hours` but with `employee_id` FK. UNIQUE: `(employee_id, day_of_week)`. Exactly 7 rows per employee.

### Table: `services` (prefix: `svc_`)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | Internal PK |
| `external_id` | CHAR(44) NOT NULL UNIQUE | `svc_` prefix |
| `tenant_id` | CHAR(44) NOT NULL | |
| `name` | VARCHAR(200) NOT NULL | "Corte caballero" |
| `description` | TEXT NULL | |
| `duration_minutes` | INT NOT NULL | 30, 45, 60... |
| `price` | DECIMAL(10,2) NOT NULL | |
| `category` | VARCHAR(100) NULL | "Cortes", "Barba", "Color" |
| `active` | BOOLEAN DEFAULT TRUE | Columna real: `active`, NO `is_active` |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### Table: `employee_services` (join table)

| Column | Type | Notes |
|--------|------|-------|
| `employee_id` | BIGINT FK → employees(id) CASCADE | |
| `service_id` | BIGINT FK → services(id) CASCADE | |
| `tenant_id` | CHAR(44) NOT NULL | Denormalized for tenant filtering |
| `custom_duration_minutes` | INT NULL | If differs from service default |
| `custom_price` | DECIMAL(10,2) NULL | If differs from service default |

PK: `(employee_id, service_id)`

---

## Plan Limits Validation

Before adding an employee, staff-service calls billing-service to check:

```
GET /api/internal/billing/tenants/{tenantId}/plan-limits
→ Check maxEmployees against current count
→ MUST bypass cache (forWriteOperation=true)
```

| Plan | Max Employees |
|------|--------------|
| FREE_TRIAL | 1 |
| BASIC | 3 |
| PREMIUM | 10 |
| ENTERPRISE | unlimited (-1) |

---

## Endpoints

### Authenticated (JWT required)

| Method | Path | Purpose | Roles |
|--------|------|---------|-------|
| GET | `/api/v1/staff/employees` | List employees | SALON_OWNER, EMPLOYEE |
| POST | `/api/v1/staff/employees` | Add employee | SALON_OWNER |
| GET | `/api/v1/staff/employees/{id}` | Get employee | SALON_OWNER, EMPLOYEE |
| PUT | `/api/v1/staff/employees/{id}` | Update employee | SALON_OWNER |
| DELETE | `/api/v1/staff/employees/{id}` | Deactivate employee | SALON_OWNER |
| GET/PUT | `/api/v1/staff/employees/{id}/working-hours` | Working hours | SALON_OWNER |
| GET | `/api/v1/services` | List services | SALON_OWNER, EMPLOYEE |
| POST | `/api/v1/services` | Create service | SALON_OWNER |
| PUT | `/api/v1/services/{id}` | Update service | SALON_OWNER |
| DELETE | `/api/v1/services/{id}` | Deactivate service | SALON_OWNER |
| POST | `/api/v1/staff/employees/{id}/services` | Assign services to employee | SALON_OWNER |

### Internal (PSK required)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| GET | `/api/internal/staff/{tenantId}/employees/{employeeId}` | Validate employee exists & active | appointment-service |
| GET | `/api/internal/staff/{tenantId}/services/{serviceId}` | Get service details (duration, price) | appointment-service |
| GET | `/api/internal/staff/{tenantId}/public/employees` | List active employees for public booking | salon-service |
| GET | `/api/internal/staff/{tenantId}/public/services` | List active services for public booking | salon-service |

---

## Business Rules

1. Employee with `keycloak_user_id = NULL` can exist (registered by owner but no login account yet)
2. Employee restrictions: ROLE_EMPLOYEE can only see/manage their own appointments and assigned clients
3. Service deactivation: soft delete (`active = false`), not physical delete
4. Employee deactivation: check for future appointments first

---

## Dependencies

- **rivoo-common** (security, tenant, observability)
- **Calls**: billing-service (plan limits check), auth-service (register employee in Keycloak)
- **Called by**: appointment-service (validate employee/service)
