# rivoo-common — Module CLAUDE.md

## Purpose

Shared Maven module providing cross-cutting infrastructure used by ALL services. Not an executable service — it's a dependency JAR.

**Package**: `com.rivoo.common`

---

## Packages & Key Classes

### `security/`

| Class | Responsibility |
|-------|----------------|
| `KeycloakJwtConverter` | `Converter<Jwt, AbstractAuthenticationToken>`. Extracts `realm_access.roles` from JWT, filters for `ROLE_*` prefix, creates `TenantAwareJwtAuthenticationToken` |
| `TenantAwareJwtAuthenticationToken` | Extends `JwtAuthenticationToken`. Adds `getTenantId()`, `getSubscriptionPlan()`, `getSalonName()`, `getUserId()`, `getEmail()` |
| `SecurityConfig` | Base `SecurityFilterChain`: CSRF disabled, stateless sessions, actuator permitAll, internal endpoints permitAll (PSK-guarded), all others authenticated, OAuth2 Resource Server with JWKS |
| `InternalEndpointFilter` | `OncePerRequestFilter`. Validates `X-Internal-Service-Key` header for requests to `/api/internal/**`. Returns 403 if key is missing or doesn't match `${internal.service-key}` |

### `tenant/`

| Class | Responsibility |
|-------|----------------|
| `TenantContext` | Static ThreadLocal holder. `setTenantId(String)`, `getTenantId()`, `clear()` |
| `TenantInterceptor` | `HandlerInterceptor`. Reads `X-Tenant-Id` header → `TenantContext.setTenantId()`. Clears on `afterCompletion` |
| `TenantAwareEntity` | `@MappedSuperclass`. Declares `tenant_id` column with `@FilterDef` + `@Filter(condition = "tenant_id = :tenantId")`. All tenant-scoped JPA entities extend this |
| `TenantEntityListener` | `@PrePersist` listener. Sets `tenant_id` from `TenantContext.getTenantId()` on new entities |
| `TenantFilterAspect` | AOP aspect. Before repository calls, activates Hibernate `tenantFilter` with current tenantId. Skips activation when tenantId is null (PLATFORM_ADMIN cross-tenant access) |

### `web/`

| Class | Responsibility |
|-------|----------------|
| `GlobalExceptionHandler` | `@ControllerAdvice` extending `ResponseEntityExceptionHandler`. Maps domain exceptions to `ProblemDetail` (RFC 9457). Enriches with `correlationId` from MDC and `timestamp` |

**Mapped exceptions**:
- `ResourceNotFoundException` → 404
- `TenantMismatchException` → 403
- `PlanLimitExceededException` → 402 (with `currentPlan`, `limit`, `upgradeUrl`)
- `ConstraintViolationException` → 400
- Generic `Exception` → 500

### `observability/`

| Class | Responsibility |
|-------|----------------|
| `CorrelationIdFilter` | `OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`. Reads `X-Correlation-Id` header (or generates UUID). Stores in MDC. Sets response header. Cleared in `finally` block |
| `ObservabilityAutoConfiguration` | Spring auto-configuration for observability beans |
| `LoggingInterceptor` | Logs inter-service REST call details (method, URL, status, duration) |

### `client/`

| Class | Responsibility |
|-------|----------------|
| `InterServiceRestClientConfig` | Configures `RestClient` beans for inter-service calls. Adds interceptors for: `X-Tenant-Id` propagation, `X-Correlation-Id` propagation, `X-Internal-Service-Key` injection. Sets timeouts (connect=2s, read=3s) |

---

## Important Notes

- **Not executable**: no `@SpringBootApplication`, no `main()` method, no `application.yml`
- **Changes affect everything**: any modification here impacts all 9 services. Test thoroughly.
- **Auto-configuration**: uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for auto-registration of beans
- **No domain logic**: only infrastructure cross-cutting concerns. Business logic belongs in individual services.
