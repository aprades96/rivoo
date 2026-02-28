# admin-service — Module CLAUDE.md

## Purpose

BFF (Backend For Frontend) for the platform administrator. Aggregates data from all services for a cross-tenant dashboard. Has no database of its own — it's purely an orchestrator.

**Port**: 8088 | **DB**: none | **Package**: `com.rivoo.admin`

---

## Access Control

- **Only** accessible by `ROLE_PLATFORM_ADMIN`
- `tenant_id = null` in JWT (no tenant attribute in Keycloak for admin user)
- Hibernate `@Filter` is NOT activated when tenantId is null → cross-tenant queries are possible
- All endpoints require JWT with `ROLE_PLATFORM_ADMIN`

---

## Endpoints

### Authenticated (JWT required, ROLE_PLATFORM_ADMIN only)

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/api/v1/admin/salons` | List all salons (cross-tenant) | salon-service |
| GET | `/api/v1/admin/salons/{tenantId}` | Get salon details | salon-service |
| PUT | `/api/v1/admin/salons/{tenantId}/status` | Suspend/activate salon | salon-service + auth-service (Keycloak) |
| GET | `/api/v1/admin/subscriptions/summary` | Subscription stats (MRR, churn, etc.) | billing-service |
| GET | `/api/v1/admin/appointments/stats` | Appointment statistics | appointment-service |
| GET | `/api/v1/admin/users/{tenantId}` | List users for a tenant | auth-service |

---

## Architecture Notes

1. **No business logic**: admin-service only aggregates and forwards
2. **No database**: zero tables, zero Flyway migrations
3. **No tenant filtering**: operates cross-tenant by design
4. **All downstream calls use internal endpoints**: `/api/internal/admin/...` with PSK

---

## Dependencies

- **rivoo-common** (security, observability — tenant module is present but tenantId will be null)
- **Calls**: salon-service, billing-service, appointment-service, auth-service
- **Called by**: nobody (it's the top-level aggregator, accessed only by the admin frontend)
