# Rivoo — Project CLAUDE.md

## Project Overview

**Rivoo** is a B2B multi-tenant SaaS platform for hair salons and barbershops in Barcelona. Built as a Maven monorepo with 10 modules (1 shared library + 1 gateway + 8 microservices), running on Java 25 with Spring Boot 4.

**Base package**: `com.rivoo.<module>`

**Language**: all code, class names, variable names, and API fields in **English**. Comments and documentation may be in Spanish.

---

## Modules

| # | Module | Port | DB | Purpose |
|---|--------|------|----|---------|
| — | `rivoo-common` | — | — | Shared library (security, tenant, observability, web, client) |
| 1 | `api-gateway` | 8080 | — | Entry point. Routing, JWT validation via JWKS, tenant propagation, CORS, rate limiting |
| 2 | `auth-service` | 8081 | `auth_db` | Wrapper over Keycloak Admin API. User/role management, onboarding support |
| 3 | `salon-service` | 8082 | `salon_db` | Salon profile, business hours, onboarding orchestrator |
| 4 | `staff-service` | 8083 | `staff_db` | Employees, working hours, service catalog, employee-service mapping |
| 5 | `client-service` | 8084 | `client_db` | Salon clients (personal data, notes, GDPR) |
| 6 | `appointment-service` | 8085 | `appointment_db` | Appointment booking, availability, conflict detection |
| 7 | `notification-service` | 8086 | `notification_db` | Emails (reminders, confirmations, welcome), templates, scheduling |
| 8 | `billing-service` | 8087 | `billing_db` | Stripe integration, subscriptions, plans, plan limits, webhooks |
| 9 | `admin-service` | 8088 | — | Platform admin dashboard, cross-tenant aggregation |

---

## Architecture: Hexagonal + Clean Architecture

Every microservice follows the same internal structure:

```
<service>/src/main/java/com/rivoo/<service>/
├── domain/                      ← CORE (zero external dependencies)
│   ├── model/                   ← Domain entities, Value Objects, enums
│   ├── port/
│   │   ├── in/                  ← INPUT PORTS (use case interfaces)
│   │   └── out/                 ← OUTPUT PORTS (repository, external service interfaces)
│   ├── service/                 ← Domain services (implement input ports)
│   └── exception/               ← Domain exceptions
├── application/                 ← USE CASES (orchestration)
│   ├── <UseCase>Service.java   ← Implements port/in interfaces
│   └── dto/                     ← Request/Response DTOs
└── infrastructure/              ← ADAPTERS (concrete implementations)
    ├── adapter/
    │   ├── in/web/              ← REST Controllers
    │   └── out/
    │       ├── persistence/     ← JPA entities, Spring Data repos, persistence adapters
    │       ├── rest/            ← REST clients to other services
    │       └── keycloak/        ← Keycloak adapter (auth-service only)
    ├── config/                  ← Spring configuration classes
    └── mapper/                  ← MapStruct mappers (DTO ↔ domain)
```

### Dependency Rule

```
infrastructure → application → domain
     ↓                ↓           ↓
  Spring, JPA     Uses ports    Pure Java
  Keycloak        Orchestrates  No Spring annotations
  REST clients    Use cases     No framework imports
```

- **domain/**: Pure Java. No imports from Spring, JPA, or HTTP. Only business logic.
- **application/**: Orchestrates use cases. Knows ports but NOT implementations.
- **infrastructure/**: Concrete implementations. Depends on Spring, JPA, Keycloak, REST.

### Dual Entity Pattern

Each domain concept has TWO representations:

1. **Domain entity** (`domain/model/Salon.java`) — pure Java, business logic, no JPA annotations
2. **JPA entity** (`infrastructure/adapter/out/persistence/SalonJpaEntity.java`) — `@Entity`, `@Filter`, persistence concerns

MapStruct mappers bridge both:
- `SalonPersistenceMapper` — domain model ↔ JPA entity
- `SalonMapper` — domain model ↔ DTO

---

## Multi-Tenancy (6 Layers of Protection)

Complete pipeline: **JWT claim → Gateway anti-spoofing → Header → ThreadLocal → Hibernate @Filter → @PrePersist**

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| 1 | Keycloak Protocol Mapper | Includes `tenant_id` as JWT claim |
| 2 | Gateway `TenantPropagationFilter` | REMOVES `X-Tenant-Id` from original request (anti-spoofing), extracts from JWT, injects as header |
| 3 | `TenantInterceptor` | Reads `X-Tenant-Id` header, stores in `TenantContext` (ThreadLocal) |
| 4 | `TenantFilterAspect` (AOP) | Activates Hibernate `@Filter` on every query with current tenantId |
| 5 | `TenantEntityListener` (`@PrePersist`) | Auto-sets `tenant_id` when creating entities |
| 6 | `@Column(updatable = false)` | Prevents changing tenant of existing entities |

**Headers propagated by Gateway**:
- `X-Tenant-Id` — from JWT `tenant_id` claim
- `X-User-Id` — from JWT `sub` claim
- `X-User-Role` — from JWT `realm_access.roles`
- `X-User-Email` — from JWT `email` claim
- `X-Subscription-Plan` — from JWT `subscription_plan` claim

**Special case: `PLATFORM_ADMIN`** — has `tenant_id = null`. The AOP aspect does NOT activate the Hibernate filter when tenantId is null, allowing cross-tenant queries.

---

## Security

### Authentication: Keycloak (OAuth2/OIDC)

- **Keycloak** (port 9080) emits JWTs, handles login/logout/refresh. Services are OAuth2 Resource Servers.
- **Realm**: `rivoo`
- **Clients**: `salon-frontend` (public, PKCE), `salon-backend` (confidential), `salon-admin-cli` (confidential, for Admin API)
- **Signing**: RS256 (asymmetric). Keycloak signs with private key, services validate via JWKS endpoint.
- **Custom claims via Protocol Mappers** (Client Scope `tenant-info`): `tenant_id`, `subscription_plan`, `salon_name`

### Roles

| Role | Scope |
|------|-------|
| `ROLE_PLATFORM_ADMIN` | Everything. Cross-tenant. |
| `ROLE_SALON_OWNER` | Full CRUD within own tenant |
| `ROLE_EMPLOYEE` | Read staff/services, manage own appointments only |

Employee restrictions (own appointments, own clients) are enforced in **business logic**, not just roles.

### Endpoint Security

- **Public** (no JWT): Keycloak endpoints, `GET /api/v1/salons/public/{slug}`, `POST /api/v1/appointments/book`, `POST /api/webhooks/stripe`, `GET /actuator/health`
- **Authenticated**: All other `/api/v1/**` — JWT required, role-based access via `@PreAuthorize`
- **Internal** (`/api/internal/**`): Protected by Pre-Shared Key (PSK) via `X-Internal-Service-Key` header. `InternalEndpointFilter` in rivoo-common validates.

### SecurityConfig Pattern (shared via rivoo-common)

```java
.authorizeHttpRequests(auth -> {
    auth.requestMatchers("/actuator/**").permitAll();
    auth.requestMatchers("/api/internal/**").permitAll(); // authenticated by InternalEndpointFilter (PSK)
    auth.anyRequest().authenticated();
})
.addFilterBefore(internalEndpointFilter, UsernamePasswordAuthenticationFilter.class)
.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter)))
```

---

## Database Conventions

### Engine & Connection

- **MySQL 8.0**, single instance, 7 schemas (one per service with DB)
- User: `rivoo` / `rivoo123` (local), connection via HikariCP
- Character set: `utf8mb4`, collation: `utf8mb4_unicode_ci`

### Primary Keys: Dual ID Strategy

ALL domain tables use:

```sql
id              BIGINT       AUTO_INCREMENT PRIMARY KEY,  -- internal PK (never exposed in API)
external_id     CHAR(44)     NOT NULL UNIQUE,             -- prefixed UUID (exposed in API)
```

- **Internal FKs** (same service): `BIGINT` referencing `id`
- **Cross-service references**: `CHAR(44)` storing `external_id` (no real FK constraint)

### External ID Prefixes

| Entity | Prefix | Example |
|--------|--------|---------|
| Salon | `sal_` | `sal_98765432-abcd-ef01-2345-678901234567` |
| Employee | `emp_` | `emp_...` |
| Service | `svc_` | `svc_...` |
| Client | `cli_` | `cli_...` |
| Appointment | `apt_` | `apt_...` |
| Subscription | `sub_` | `sub_...` |
| Plan | `pln_` | `pln_...` |
| Notification | `ntf_` | `ntf_...` |

### Timestamps

ALL temporal columns use `TIMESTAMP` (stored in UTC by MySQL), **not** `DATETIME`. Conversion to local timezone (`Europe/Madrid`) happens in the application layer.

```sql
created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
```

### Migrations: Flyway

Path: `src/main/resources/db/migration/`

Convention: `V{n}__description.sql` (e.g., `V1__create_initial_schema.sql`)

**Rule**: NEVER edit an already-applied migration. Always create a new `V{n+1}__description.sql`.

---

## API Conventions

### Error Responses: Problem Details RFC 9457

Spring Boot 4 native support. All errors return standard `ProblemDetail`:

```json
{
  "type": "https://rivoo.com/errors/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "No salon found with slug 'barberia-xyz'",
  "instance": "/api/v1/salons/public/barberia-xyz",
  "timestamp": "2026-02-28T10:30:00Z",
  "correlationId": "abc-123-def"
}
```

Implemented via `@ControllerAdvice` extending `ResponseEntityExceptionHandler` in rivoo-common (`GlobalExceptionHandler`). Custom properties: `correlationId` (from MDC), `timestamp`.

### Endpoint Convention

- **Public API**: `/api/v1/<resource>` — JWT required (except explicitly public endpoints)
- **Internal API**: `/api/internal/<resource>` — PSK required, no JWT
- **Webhooks**: `/api/webhooks/<provider>` — provider-specific verification (e.g., Stripe signature)

### Validation

Bean Validation (`jakarta.validation`) on request DTOs. Validation errors return `ProblemDetail` with `status: 400` and field-level details.

### Pagination

Standard Spring `Pageable` for list endpoints. Response wraps content + pagination metadata.

---

## Inter-Service Communication

### Pattern: `@HttpExchange` + `RestClient`

```java
@HttpExchange("/api/internal")
public interface StaffServiceClient {
    @GetExchange("/staff/{tenantId}/employees/{employeeId}")
    EmployeeResponse getEmployee(@PathVariable String tenantId, @PathVariable Long employeeId);
}
```

### Configuration (via `InterServiceRestClientConfig` in rivoo-common)

- Propagated headers: `X-Tenant-Id`, `X-Correlation-Id`, `X-Internal-Service-Key`
- **Timeouts**: connect = 2s, read = 3s
- **Retries**: exponential backoff 500ms → 1s → 2s (max 3 retries)
- **Only retry on**: 503, 429, IOException. **Never retry on**: 4xx, 500
- **Circuit breaker**: Resilience4j with fallback to cache/permissive values
- **Virtual Threads**: enabled globally — blocking REST calls don't consume platform threads

### Graceful Degradation

- notification-service down → appointment still created (log warning)
- billing-service down → appointment-service falls back to cache
- client-service down → appointment created without client_id linking

---

## Mapping: MapStruct

### Configuration

```xml
<!-- maven-compiler-plugin annotationProcessorPaths -->
<path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId></path>
<path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
<path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId></path>
```

### Two Mapper Types per Service

1. **PersistenceMapper** (`infrastructure/adapter/out/persistence/`) — domain model ↔ JPA entity
2. **DtoMapper** (`infrastructure/mapper/`) — domain model ↔ request/response DTOs

### Conventions

- `@Mapper(componentModel = "spring")`
- API exposes `external_id` as `"id"` in responses: `@Mapping(target = "id", source = "externalId")`
- Internal fields (`id`, `externalId`, `tenantId`) are `@Mapping(target = ..., ignore = true)` on create

---

## Observability

### Structured Logging

- **All profiles**: JSON structured logging via Spring Boot 4 built-in `StructuredLogEncoder`
- Configuration: `logback-spring.xml` (single appender, no profile switching)
- **Fluent API obligatoria** (SLF4J 2.0): todas las llamadas a log DEBEN usar la fluent API para que los valores sean campos JSON independientes y filtrables

```java
// CORRECTO — valores como campos JSON separados
log.atInfo().addKeyValue("email", email).addKeyValue("keycloakUserId", userId).log("Owner registered");

// INCORRECTO — valores interpolados en el string message, no filtrables
log.info("Owner registered: email={}, userId={}", email, userId);
```

- **NO duplicar claves MDC**: `tenantId` y `correlationId` ya aparecen automáticamente como campos JSON (inyectados por `TenantInterceptor` y `CorrelationIdFilter`). No añadirlos con `.addKeyValue()`
- Para excepciones: usar `.setCause(ex)` en vez de pasar la excepción como último parámetro

### Correlation ID

`CorrelationIdFilter` (in rivoo-common, `@Order(HIGHEST_PRECEDENCE)`):
1. Reads `X-Correlation-Id` header (or generates UUID)
2. Stores in MDC (`correlationId`)
3. Sets response header
4. Propagated in all inter-service REST calls via `InterServiceRestClientConfig`

### MDC Keys

| Key | Source |
|-----|--------|
| `correlationId` | CorrelationIdFilter |
| `tenantId` | TenantInterceptor |
| `userId` | JWT `sub` claim |

---

## Caching

- **Library**: Caffeine (local in-memory)
- **Default TTL**: 5 minutes
- **Primary use**: Plan limits from billing-service

### Mandatory Bypass for Write Operations

```java
public PlanLimitsResponse getPlanLimits(String tenantId, boolean forWriteOperation) {
    if (!forWriteOperation) {
        PlanLimitsResponse cached = caffeineCache.getIfPresent(tenantId);
        if (cached != null) return cached;
    }
    PlanLimitsResponse fresh = billingServiceClient.getPlanLimits(tenantId);
    caffeineCache.put(tenantId, fresh);
    return fresh;
}
```

**Rule**: creating appointments, adding employees, adding clients → ALWAYS bypass cache.

---

## Testing

### Methodology: TDD (Red → Green → Refactor)

Applied especially to:
- Availability logic (algorithmically complex)
- Business validations (plan limits, cross-tenant)
- State flows (PENDING → CONFIRMED → COMPLETED)
- Onboarding compensations

### Tools

| Tool | Purpose |
|------|---------|
| JUnit 5 | Unit and integration tests |
| Mockito | Dependency mocking |
| Testcontainers | Temporary MySQL per integration test |
| WireMock | Mock external services |
| Spring Cloud Contract / Pact | Contract tests between services |
| Stripe CLI | `stripe listen --forward-to` for webhook testing |

### Test Distribution

- **Unit tests (80%)**: Mockito, pure business logic
- **Integration tests (15%)**: `@SpringBootTest` + Testcontainers + WireMock
- **Contract tests (5%)**: Spring Cloud Contract / Pact

### Spring Profiles for Testing

| Profile | Use |
|---------|-----|
| `local` | Dev with all services on localhost |
| `local-standalone` | Isolated dev: WireMock replaces dependencies |
| `test` | Integration tests: Testcontainers (temporary MySQL) |
| `prod` | Production (future) |

---

## Design Principles

### SOLID

| Principle | Application in Rivoo |
|-----------|---------------------|
| **S — Single Responsibility** | Each service has a bounded responsibility. Within: one class = one reason to change |
| **O — Open/Closed** | Extensible via Strategy/Template Method (e.g., NotificationChannel → EmailChannel, SmsChannel) |
| **L — Liskov Substitution** | Port interfaces are interchangeable with any adapter implementation |
| **I — Interface Segregation** | Specific ports per use case (e.g., `AppointmentCreator`, `AppointmentFinder`) instead of mega-repository |
| **D — Dependency Inversion** | Domain defines interfaces (ports). Infrastructure implements (adapters). Domain has NO dependency on Spring, JPA, or HTTP |

### Clean Code

- Descriptive, consistent names (classes, methods, variables)
- Short methods with a single responsibility
- No unnecessary comments — code is self-explanatory
- No dead code or abandoned TODOs
- Explicit error handling (no swallowed exceptions)
- Tests as living documentation of behavior

---

## Design Patterns

| Pattern | Where |
|---------|-------|
| **Strategy** | Notification channels (Email, SMS), booking validators |
| **Template Method** | Base CRUD flow in services (create → validate → persist → map) |
| **Builder** | Complex object construction (DTOs, configs) via Lombok `@Builder` |
| **Factory Method** | External ID generation with entity-type prefix |
| **Observer** | Internal domain events (AppointmentCreated → schedule notification) |
| **Adapter** | All infrastructure layer: JPA adapters, REST adapters, Keycloak adapter |
| **Facade** | Application services (use cases) that orchestrate multiple ports |
| **Decorator** | Cache bypass wrapper over billing-service client |
| **Chain of Responsibility** | Security filters: CorrelationId → InternalEndpoint → TenantPropagation |

---

## Key Files in rivoo-common

```
rivoo-common/src/main/java/com/rivoo/common/
├── security/
│   ├── KeycloakJwtConverter.java              — Extracts realm roles from JWT
│   ├── TenantAwareJwtAuthenticationToken.java — Adds getTenantId(), getSubscriptionPlan(), etc.
│   ├── SecurityConfig.java                    — Base security chain (OAuth2 RS + PSK filter)
│   └── InternalEndpointFilter.java            — Validates X-Internal-Service-Key for /api/internal/**
├── tenant/
│   ├── TenantContext.java                     — ThreadLocal holder for current tenantId
│   ├── TenantInterceptor.java                 — Reads X-Tenant-Id header into TenantContext
│   ├── TenantAwareEntity.java                 — @MappedSuperclass with @Filter + tenant_id column
│   ├── TenantEntityListener.java              — @PrePersist: auto-sets tenant_id from TenantContext
│   └── TenantFilterAspect.java                — AOP: activates Hibernate @Filter per query
├── web/
│   └── GlobalExceptionHandler.java            — @ControllerAdvice, Problem Details RFC 9457
├── observability/
│   ├── CorrelationIdFilter.java               — Generates/propagates X-Correlation-Id + MDC
│   ├── ObservabilityAutoConfiguration.java    — Auto-config for observability beans
│   └── LoggingInterceptor.java                — Logs inter-service call details
└── client/
    └── InterServiceRestClientConfig.java      — RestClient config: header propagation, timeouts, retries
```

---

## Consistency Model

- **Synchronous orchestration** + explicit compensation (not event sourcing)
- **Idempotency**: `X-Idempotency-Key` header on write operations
- **Transitional states**: `ONBOARDING`, `PENDING_STRIPE` for intermediate data
- **Nightly reconciliation** (03:00 CET): Stripe ↔ local DB + Keycloak attributes

---

## What We Do NOT Use

| Excluded | Reason |
|----------|--------|
| Docker (for dev) | Rebuild overhead. Direct local execution is faster |
| Eureka / Consul | Fixed localhost URLs with fixed ports |
| Event Sourcing / CQRS | 10x more complex than needed for expected scale |
| Spring Cloud Config | `application-{profile}.yml` is sufficient |
| Gradle | Maven multi-module is more standard |
| API versioning | We control both backend and frontend |
| i18n in backend | MVP in Spanish. i18n is a frontend concern |
| MapStruct for < 5 fields | Simple `fromEntity()`/`toEntity()` methods when entities are small |

---

## Tech Stack Quick Reference

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 25 | Virtual Threads enabled |
| Spring Boot | 4 | Application framework |
| Spring Cloud Gateway | — | API Gateway (reactive, WebFlux) |
| Spring Security OAuth2 RS | — | JWT validation via JWKS |
| Spring Data JPA / Hibernate | — | ORM + `@Filter` for multi-tenancy |
| Flyway | — | Database migrations |
| MySQL | 8.0 | Database (7 schemas, 1 instance) |
| Keycloak | 26.x | IdP — OAuth2/OIDC |
| Stripe Java SDK | — | Payments, subscriptions, webhooks |
| MapStruct | — | Compile-time entity ↔ DTO mapping |
| Lombok | — | Boilerplate reduction |
| Caffeine | — | Local in-memory cache |
| Resilience4j | — | Circuit breaker, retries |
| Spring Mail | — | Email sending (SMTP) |
| keycloak-admin-client | 26.x | Keycloak Admin API (auth-service only) |
| logstash-logback-encoder | — | Structured JSON logging |
| JUnit 5 + Mockito | — | Unit testing |
| Testcontainers | — | Integration testing with real MySQL |
| WireMock | — | Mock external services in tests |
