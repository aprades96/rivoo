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

## Keycloak Admin Integration

**Approach**: Spring `RestClient` calling Keycloak Admin REST API directly (NO `keycloak-admin-client` — avoids Jackson 2.x/3.x classpath conflicts in SB4).

**Token**: `client_credentials` grant with `salon-admin-cli` client. `KeycloakTokenManager` caches token, auto-refreshes 30s before expiry.

**Base URL**: `http://localhost:9080/admin/realms/rivoo` (configured via `rivoo.keycloak.admin.*`)

Core class: `KeycloakAdminAdapter` (implements `KeycloakAdminPort`):

- `createUser(email, password, firstName, lastName)` → POST `/users`, returns userId from Location header
- `setUserAttributes(keycloakUserId, attributes)` → GET user + merge attributes + PUT (Map body, excludes credentials)
- `assignRealmRole(keycloakUserId, roleName)` → GET role + POST role-mappings
- `searchUserIdsByAttribute(attr, value)` → GET `/users?q=attr:value`
- `setUserEnabled(keycloakUserId, enabled)` → GET user + PUT with Map body
- `updateUserAttribute(keycloakUserId, key, value)` → delegates to setUserAttributes
- `deleteUser(keycloakUserId)` → DELETE `/users/{id}` (compensation)

**IMPORTANT**: User PUT operations use `Map<String, Object>` body (not records) to deliberately exclude `credentials` field — otherwise Keycloak wipes passwords.

### Owner creation and email verification

`KeycloakUserRepresentation.forCreation` creates the salon owner with `emailVerified=false` and the
`VERIFY_EMAIL` required action, so Keycloak refuses their login until they click the link. That is
the production behaviour and the default.

**Temporary switch** — `rivoo.keycloak.owner.email-verified-on-creation` (read in
`KeycloakAdminAdapter`, inline default `false`): set to `true` the owner is created already
verified, with no required action. It exists only because there is no SMTP server yet — neither the
application's own sender (notification-service's `MailStubAdapter` only logs) nor Keycloak's realm
(no `smtpServer` block in `infrastructure/keycloak/rivoo-realm.json`), and it is Keycloak, not
notification-service, that mails the verification link. Set it back to `false` in every profile as
soon as SMTP is configured, so the owner receives a real confirmation email.
The infrastructure that has to exist first is listed in `tasks/todo.md`, section "Al desplegar: el
correo es requisito de la verificacion del dueno".

| Profile | Value | Where |
|---------|-------|-------|
| (any, default) | `false` | `src/main/resources/application.yml` |
| `local` | `true` | `src/main/resources/application-local.yml` |
| `test` | `true` | `src/test/resources/application-test.yml` |
| `prod` | `false` | `src/main/resources/application-prod.yml` |

**The switch also decides whether Keycloak is asked to mail anything at all.** Keycloak's
`execute-actions-email` SETS the required actions on the user as part of sending, not merely mails
them, so asking it to send `VERIFY_EMAIL` to an owner created already verified would re-impose the
action and lock them out — on exactly the profiles the switch exists to unblock. The decision has
ONE home: `KeycloakUserRepresentation.ownerRequiredActions(flag)`, read both to build the creation
body and to answer `KeycloakAdminPort.pendingActionsForNewOwner()`. `AuthService.registerOwner`
asks that port method instead of carrying its own action list, and
`sendRequiredActionsEmail` makes no HTTP request at all for an empty list. `AuthService` does NOT
read the property: it stays a single `@Value` in `KeycloakAdminAdapter`.

`forEmployeeCreation` is NOT affected: employees keep their temporary password plus
`UPDATE_PASSWORD` + `VERIFY_EMAIL` in every profile, and are always mailed `UPDATE_PASSWORD`.

---

## Ports

### Input Ports (use cases)

| Port | Purpose |
|------|---------|
| `RegisterOwnerUseCase` | Create Keycloak user + assign attributes + assign ROLE_SALON_OWNER |
| `RegisterEmployeeUseCase` | Create Keycloak user for employee + assign ROLE_EMPLOYEE |
| `ManageTenantStatusUseCase` | Disable/enable all Keycloak users for a tenant |
| `UpdateTenantAttributeUseCase` | Update subscription_plan or salon_name in Keycloak |
| `ListTenantUsersUseCase` | List users for a tenant from local DB |

### Output Ports

| Port | Purpose |
|------|---------|
| `KeycloakAdminPort` | Interface to Keycloak Admin REST API (10 methods) |
| `OnboardingEventPort` | Persist audit events |
| `TenantUserMappingPort` | Persist/query tenant-user mappings |

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

- **Keycloak** (Admin REST API via Spring RestClient)
- **rivoo-common** (security, tenant, observability)
- Called by: salon-service, billing-service, admin-service, staff-service
- Calls: only Keycloak Admin API (external)

---

## Keycloak Realm Setup (for auth-service to work)

Keycloak realm `rivoo` requires:
1. Custom User Profile: `tenant_id`, `subscription_plan`, `salon_name` attributes (applied via `PUT /admin/realms/rivoo/users/profile`)
2. Client scope `tenant-info` with 3 protocol mappers assigned as default to all clients
3. Service account `service-account-salon-admin-cli` with `realm-management` roles: `manage-users`, `view-users`, `query-users`, `view-realm`

**Setup files**: `infrastructure/keycloak/rivoo-realm.json` + `infrastructure/keycloak/rivoo-user-profile.json`
**Setup strategy**: Empty realm creation → partialImport → REST API for scopes/profile (see `tasks/lessons.md`)
