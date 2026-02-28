# auth-service — Module CLAUDE.md

## Purpose

Wrapper over **Keycloak Admin API**. Does NOT emit JWTs (Keycloak does that). Manages users, roles, and attributes in Keycloak programmatically. Supports onboarding, tenant disabling, and attribute synchronization.

**Port**: 8081 | **DB**: `auth_db` | **Package**: `com.rivoo.auth`

---

## Database: `auth_db`

### Table: `onboarding_events`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `tenant_id` | CHAR(44) NOT NULL | Salon external_id |
| `keycloak_user_id` | VARCHAR(36) NOT NULL | UUID from Keycloak |
| `email` | VARCHAR(255) NOT NULL | |
| `event_type` | ENUM('OWNER_CREATED','EMPLOYEE_CREATED','USER_DISABLED','USER_ENABLED','ROLE_CHANGED') | |
| `details` | JSON NULL | Additional event data |
| `created_at` | TIMESTAMP | |

Indexes: `idx_onboarding_tenant (tenant_id)`, `idx_onboarding_keycloak_user (keycloak_user_id)`

### Table: `tenant_user_mapping`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `tenant_id` | CHAR(44) NOT NULL | |
| `keycloak_user_id` | VARCHAR(36) NOT NULL UNIQUE | |
| `role` | ENUM('SALON_OWNER','EMPLOYEE') | Cached role |
| `is_active` | BOOLEAN DEFAULT TRUE | |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

Indexes: `idx_tenant_user_tenant (tenant_id)`, `idx_tenant_user_keycloak (keycloak_user_id, UNIQUE)`

**Note**: No `users` table with passwords, no `refresh_tokens`. Keycloak manages all of that.

---

## Keycloak Admin Client

Dependency: `org.keycloak:keycloak-admin-client:26.x` (ONLY in this service)

Core class: `KeycloakAdminService` (or adapter in hexagonal terms) using `Keycloak` bean:

- `createUser(email, password, firstName, lastName)` → creates user in Keycloak realm `rivoo`
- `setUserAttributes(keycloakUserId, attributes)` → sets `tenant_id`, `subscription_plan`, `salon_name`
- `assignRealmRole(keycloakUserId, roleName)` → assigns `ROLE_SALON_OWNER` or `ROLE_EMPLOYEE`
- `disableUsersForTenant(tenantId)` → searches by attribute, disables all users
- `deleteUser(keycloakUserId)` → compensation: removes user if onboarding fails
- `updateAttribute(keycloakUserId, key, value)` → updates single attribute (e.g., plan change)

---

## Ports

### Input Ports (use cases)

| Port | Purpose |
|------|---------|
| `RegisterOwnerUseCase` | Create Keycloak user + assign attributes + assign ROLE_SALON_OWNER |
| `RegisterEmployeeUseCase` | Create Keycloak user for employee + assign ROLE_EMPLOYEE |
| `DisableTenantUseCase` | Disable all Keycloak users for a tenant |
| `UpdateTenantAttributeUseCase` | Update subscription_plan or salon_name in Keycloak |

### Output Ports

| Port | Purpose |
|------|---------|
| `KeycloakAdminPort` | Interface to Keycloak Admin API |
| `OnboardingEventRepository` | Persist audit events |
| `TenantUserMappingRepository` | Persist tenant-user mappings |

---

## Endpoints (ALL internal)

| Method | Path | Purpose | Called by |
|--------|------|---------|----------|
| POST | `/api/internal/auth/register-owner` | Create owner user in Keycloak | salon-service (onboarding) |
| POST | `/api/internal/auth/register-employee` | Create employee user in Keycloak | staff-service |
| PUT | `/api/internal/auth/tenants/{tenantId}/disable` | Disable all tenant users in Keycloak | billing-service (payment failed) |
| PUT | `/api/internal/auth/tenants/{tenantId}/attributes` | Update tenant attributes (plan change) | billing-service |
| GET | `/api/internal/auth/tenants/{tenantId}/users` | List users for a tenant | admin-service |
| PUT | `/api/internal/admin/tenants/{tenantId}/status` | Enable/disable tenant (admin action) | admin-service |

---

## Business Rules

1. auth-service does NOT participate in login/logout/refresh — that's Keycloak directly
2. If Keycloak is down, only NEW user creation fails. Existing tokens still work.
3. All operations are audited in `onboarding_events`
4. Compensations: `deleteUser()` called if downstream steps of onboarding fail

---

## Dependencies

- **Keycloak** (Admin API via `keycloak-admin-client`)
- **rivoo-common** (security, tenant, observability)
- Called by: salon-service, billing-service, admin-service, staff-service
- Calls: only Keycloak Admin API (external)
