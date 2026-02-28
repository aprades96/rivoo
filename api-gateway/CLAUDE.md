# api-gateway — Module CLAUDE.md

## Purpose

Single entry point for all client requests. Routes to downstream services, validates JWTs, propagates tenant context, applies rate limiting and CORS.

**Port**: 8080 | **DB**: none | **Package**: `com.rivoo.gateway`

---

## CRITICAL: Reactive Stack

This is the ONLY module using **Spring WebFlux** (reactive). All other services use Spring MVC (servlet).

- Use `WebFilter`, NOT `OncePerRequestFilter`
- Use `ServerHttpRequest` / `ServerHttpResponse`, NOT `HttpServletRequest`
- Use `Mono`/`Flux` return types
- Do NOT import servlet classes
- Does NOT depend on `rivoo-common` directly (rivoo-common is servlet-based). Shared logic must be reimplemented reactively.

---

## Filters (execution order)

| Order | Filter | Responsibility |
|-------|--------|----------------|
| 1 | CORS filter | Cross-Origin Resource Sharing headers |
| 2 | Rate limiting filter | Token bucket per IP (general: 100 req/min, booking public: 10 req/min) |
| 3 | JWT validation | Validates JWT signature via JWKS endpoint from Keycloak |
| 4 | `TenantPropagationFilter` | **REMOVES** `X-Tenant-Id` from incoming request (anti-spoofing), extracts `tenant_id` from JWT, injects `X-Tenant-Id`, `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Subscription-Plan` as headers |

---

## Route Configuration

### Public Routes (no JWT)

| Method | Path | Target |
|--------|------|--------|
| GET | `/api/v1/salons/public/{slug}` | salon-service |
| POST | `/api/v1/appointments/book` | appointment-service |
| POST | `/api/webhooks/stripe` | billing-service |
| GET | `/actuator/health` | self |

### Authenticated Routes (JWT required)

| Path prefix | Target | Port |
|-------------|--------|------|
| `/api/v1/auth/**` | auth-service | 8081 |
| `/api/v1/salons/**` | salon-service | 8082 |
| `/api/v1/staff/**`, `/api/v1/services/**` | staff-service | 8083 |
| `/api/v1/clients/**` | client-service | 8084 |
| `/api/v1/appointments/**` | appointment-service | 8085 |
| `/api/v1/notifications/**` | notification-service | 8086 |
| `/api/v1/billing/**` | billing-service | 8087 |
| `/api/v1/admin/**` | admin-service | 8088 |

---

## Key Behaviors

- **No inter-service calls**: gateway only routes, never calls other services
- **No business logic**: pure routing, security, and cross-cutting filters
- **JWKS caching**: Spring Security caches Keycloak public keys (NimbusJwtDecoder). If Keycloak goes down, existing tokens still validate.
- **Rate limiting**: differentiated by path — stricter for public booking endpoint

---

## Dependencies

- Spring Cloud Gateway (reactive)
- Spring Security OAuth2 Resource Server (reactive variant)
- Spring Boot Actuator
- **Does NOT depend on** rivoo-common (different stack)
