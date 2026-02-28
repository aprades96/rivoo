# Rivoo — Stack Tecnológico Completo

---

## 1. Stack Core

| Tecnología | Versión | Propósito |
|---|---|---|
| **Java** | 25 LTS | Lenguaje base, **Virtual Threads habilitados** |
| **Spring Boot** | 4 | Framework de aplicación |
| **Spring Cloud Gateway** | — | API Gateway (enrutamiento, filtros, rate limiting) |
| **Spring Security OAuth2 Resource Server** | — | Validación JWT via JWKS en cada servicio |
| **Spring Data JPA / Hibernate** | — | ORM + `@Filter` para multi-tenancy automático |
| **Flyway** | — | Migraciones de base de datos versionadas |
| **MySQL** | 8.0 | Base de datos (7 schemas, 1 instancia) |
| **Keycloak** | 26.x | IdP — OAuth2/OIDC, login, tokens, gestión de usuarios |
| **Stripe Java SDK** | — | Pagos, suscripciones, webhooks |

---

## 2. Librerías Complementarias

| Librería | Propósito |
|---|---|
| **MapStruct** | Mapping compilado entre entidades y DTOs. Cero reflection, errores en compilación |
| **Lombok** | Reducir boilerplate (getters, constructors, builders) |
| **lombok-mapstruct-binding** | Compatibilidad Lombok + MapStruct en annotation processing |
| **Jackson** | Serialización/deserialización JSON |
| **Caffeine** | Cache local en memoria (plan limits, TTL 5min) |
| **Resilience4j** | Circuit breaker, reintentos con backoff exponencial |
| **Spring Mail** | Envío de emails (SMTP Gmail o SendGrid) |
| **keycloak-admin-client** | Keycloak Admin API (solo en auth-service) |
| **HikariCP** | Connection pool JDBC (incluido en Spring Boot) |
| **logstash-logback-encoder** | Structured Logging — logs en formato JSON |

---

## 3. Virtual Threads (Project Loom)

Habilitados globalmente en Spring Boot 4:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**Impacto**: cada request HTTP y cada llamada REST inter-servicio se ejecuta en un virtual thread en lugar de un platform thread. Esto elimina el cuello de botella de pools de threads limitados en operaciones I/O-bound (llamadas a MySQL, Keycloak, Stripe, otros servicios).

**Beneficio concreto en Rivoo**: con 8 servicios comunicándose por REST síncrono, las llamadas bloqueantes (que eran el principal argumento contra REST síncrono) dejan de ser un problema. Un virtual thread bloqueado en I/O no consume un platform thread del OS.

---

## 4. Manejo de Errores: Problem Details RFC 9457

Estándar para respuestas de error en APIs HTTP. Spring Boot 4 lo soporta nativamente.

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

**Formato de respuesta de error**:
```json
{
  "type": "https://rivoo.com/errors/tenant-not-found",
  "title": "Tenant Not Found",
  "status": 404,
  "detail": "No salon found with slug 'barberia-xyz'",
  "instance": "/api/v1/salons/public/barberia-xyz",
  "timestamp": "2026-02-28T10:30:00Z",
  "correlationId": "abc-123-def"
}
```

Implementado mediante `@ControllerAdvice` + `ProblemDetail` (clase nativa de Spring):

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://rivoo.com/errors/resource-not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get("correlationId"));
        return problem;
    }

    @ExceptionHandler(TenantMismatchException.class)
    ProblemDetail handleTenantMismatch(TenantMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Cross-tenant access denied");
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    ProblemDetail handlePlanLimit(PlanLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setProperty("currentPlan", ex.getCurrentPlan());
        problem.setProperty("limit", ex.getLimit());
        problem.setProperty("upgradeUrl", "/api/v1/billing/plans");
        return problem;
    }
}
```

**Sustituye**: `ApiResponse`, `ErrorResponse` custom → se usa el estándar RFC 9457 directamente. `ApiResponse<T>` se mantiene solo para respuestas exitosas si se desea uniformidad, o se devuelve la entidad/DTO directamente.

---

## 5. Structured Logging (JSON)

Logs en formato JSON estructurado en lugar de texto plano, usando `logstash-logback-encoder`.

**Dependencia** (en `rivoo-common`):
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

**logback-spring.xml**:
```xml
<configuration>
    <!-- Perfil local: texto legible para humanos -->
    <springProfile name="local">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <!-- Perfil prod: JSON estructurado -->
    <springProfile name="prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <includeMdcKeyName>tenantId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
            </encoder>
        </appender>
    </springProfile>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

**Output en prod** (JSON):
```json
{
  "@timestamp": "2026-02-28T10:30:00.123Z",
  "level": "INFO",
  "logger_name": "com.rivoo.appointment.service.AppointmentService",
  "message": "Appointment created successfully",
  "correlationId": "abc-123-def",
  "tenantId": "sal_98765432-abcd",
  "userId": "a1b2c3d4-e5f6",
  "thread_name": "virtual-42"
}
```

**Beneficio**: logs parseables por herramientas (ELK, Loki, CloudWatch). Filtrar por tenantId, correlationId, o nivel de error sin regex.

---

## 6. MapStruct

Mapping entre entidades JPA y DTOs generado en tiempo de compilación.

**Configuración Maven** (en `rivoo-common` o parent):
```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-mapstruct-binding</artifactId>
</dependency>
<!-- annotation processor en maven-compiler-plugin -->
<annotationProcessorPaths>
    <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId></path>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId></path>
</annotationProcessorPaths>
```

**Ejemplo** (salon-service):
```java
@Mapper(componentModel = "spring")
public interface SalonMapper {

    @Mapping(target = "id", source = "externalId")  // API expone external_id como "id"
    SalonResponse toResponse(Salon entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "externalId", ignore = true)   // generado en @PrePersist
    @Mapping(target = "tenantId", ignore = true)      // inyectado por TenantEntityListener
    Salon toEntity(CreateSalonRequest request);
}
```

---

## 7. Testing

| Herramienta | Propósito |
|---|---|
| **JUnit 5** | Tests unitarios y de integración |
| **Mockito** | Mocking de dependencias |
| **Testcontainers** | MySQL temporal por test de integración |
| **WireMock** | Mock de servicios externos |
| **Spring Cloud Contract / Pact** | Tests de contrato entre servicios |
| **Stripe CLI** | `stripe listen --forward-to` para testing webhooks |

**Metodología: TDD (Test-Driven Development)**:
1. **Red**: escribir el test que define el comportamiento esperado (falla)
2. **Green**: escribir el código mínimo para que el test pase
3. **Refactor**: mejorar el código manteniendo los tests verdes

Aplicado especialmente en:
- Lógica de disponibilidad de citas (algorítmicamente compleja)
- Validaciones de negocio (límites de plan, cross-tenant)
- Flujos de estado (PENDING → CONFIRMED → COMPLETED)
- Compensaciones de onboarding

---

## 8. Principios y Metodologías de Diseño

### SOLID

| Principio | Aplicación en Rivoo |
|---|---|
| **S — Single Responsibility** | Cada servicio tiene una responsabilidad acotada. Dentro del servicio: una clase = una razón de cambio |
| **O — Open/Closed** | Extensible via Strategy/Template Method (ej: NotificationChannel → EmailChannel, SmsChannel) sin modificar código existente |
| **L — Liskov Substitution** | Las interfaces de puertos (ports) son intercambiables con cualquier implementación de adapter |
| **I — Interface Segregation** | Ports específicos por caso de uso (ej: `AppointmentCreator`, `AppointmentFinder`) en vez de un mega-repository |
| **D — Dependency Inversion** | El dominio define interfaces (ports). La infraestructura las implementa (adapters). El dominio NO depende de Spring, JPA, ni HTTP |

### Clean Code

- Nombres descriptivos y consistentes (clases, métodos, variables)
- Métodos cortos con una sola responsabilidad
- Sin comentarios innecesarios — el código se explica solo
- Sin código muerto ni TODOs abandonados
- Manejo explícito de errores (no swallow exceptions)
- Tests como documentación viva del comportamiento

### Patrones de Diseño aplicados

| Patrón | Dónde |
|---|---|
| **Strategy** | Canales de notificación (Email, SMS), validadores de booking |
| **Template Method** | Flujo base de CRUD en servicios (create → validate → persist → map) |
| **Builder** | Construcción de objetos complejos (DTOs, configuraciones) via Lombok @Builder |
| **Factory Method** | Generación de external_id con prefijo por tipo de entidad |
| **Observer** | Eventos de dominio internos (AppointmentCreated → schedule notification) |
| **Adapter** | Toda la capa de infraestructura: JPA adapters, REST adapters, Keycloak adapter |
| **Facade** | Servicios de aplicación (use cases) que orquestan múltiples ports |
| **Decorator** | Cache bypass wrapper sobre billing-service client |
| **Chain of Responsibility** | Filtros de seguridad: CorrelationId → InternalEndpoint → TenantPropagation |

---

## 9. Arquitectura Hexagonal (Ports & Adapters) + Clean Architecture

Cada microservicio sigue la misma estructura interna:

```
salon-service/
└── src/main/java/com/rivoo/salon/
    │
    ├── domain/                          ← NÚCLEO (sin dependencias externas)
    │   ├── model/                       ← Entidades y Value Objects de dominio
    │   │   ├── Salon.java
    │   │   ├── SalonStatus.java         (enum)
    │   │   └── BusinessHours.java
    │   ├── port/
    │   │   ├── in/                      ← PORTS DE ENTRADA (interfaces que el dominio expone)
    │   │   │   ├── CreateSalonUseCase.java
    │   │   │   ├── GetSalonUseCase.java
    │   │   │   └── UpdateSalonUseCase.java
    │   │   └── out/                     ← PORTS DE SALIDA (interfaces que el dominio necesita)
    │   │       ├── SalonRepository.java         (persistencia)
    │   │       ├── AuthServicePort.java         (crear usuario en Keycloak)
    │   │       ├── BillingServicePort.java      (crear suscripción)
    │   │       └── NotificationServicePort.java (enviar email)
    │   ├── service/                     ← LÓGICA DE DOMINIO (implementa ports de entrada)
    │   │   └── SalonDomainService.java
    │   └── exception/                   ← Excepciones de dominio
    │       ├── SalonNotFoundException.java
    │       └── SalonAlreadyExistsException.java
    │
    ├── application/                     ← CASOS DE USO (orquestación)
    │   ├── CreateSalonService.java      ← Implementa CreateSalonUseCase
    │   ├── GetSalonService.java
    │   └── dto/                         ← DTOs de aplicación (request/response)
    │       ├── CreateSalonRequest.java
    │       └── SalonResponse.java
    │
    └── infrastructure/                  ← ADAPTERS (implementaciones concretas)
        ├── adapter/
        │   ├── in/
        │   │   └── web/                 ← ADAPTER DE ENTRADA: Controllers REST
        │   │       ├── SalonController.java
        │   │       └── SalonInternalController.java
        │   └── out/
        │       ├── persistence/         ← ADAPTER DE SALIDA: JPA
        │       │   ├── SalonJpaEntity.java       (entidad JPA con @Entity, @Filter)
        │       │   ├── SalonJpaRepository.java   (Spring Data)
        │       │   ├── SalonPersistenceAdapter.java (implementa SalonRepository port)
        │       │   └── SalonPersistenceMapper.java  (MapStruct: domain ↔ JPA entity)
        │       ├── rest/                ← ADAPTER DE SALIDA: clientes REST
        │       │   ├── AuthServiceAdapter.java
        │       │   └── BillingServiceAdapter.java
        │       └── keycloak/            ← ADAPTER DE SALIDA: Keycloak (solo en auth-service)
        │           └── KeycloakAdminAdapter.java
        ├── config/                      ← Configuración Spring
        │   ├── SecurityConfig.java
        │   └── RestClientConfig.java
        └── mapper/                      ← MapStruct: DTO ↔ domain
            └── SalonMapper.java
```

### Regla de dependencias (Clean Architecture)

```
infrastructure → application → domain
     ↓                ↓           ↓
  Spring, JPA     Usa ports    Puro Java
  Keycloak        Orquesta     Sin frameworks
  REST clients    Use cases    Sin anotaciones Spring
```

- **domain/**: Java puro. No importa nada de Spring, JPA, ni HTTP. Sólo lógica de negocio.
- **application/**: Orquesta use cases. Conoce los ports pero no las implementaciones.
- **infrastructure/**: Implementaciones concretas. Depende de Spring, JPA, Keycloak, REST.

### Separación Entidad de Dominio vs Entidad JPA

```java
// domain/model/Salon.java — DOMINIO PURO (sin @Entity, sin JPA)
public class Salon {
    private SalonId id;
    private TenantId tenantId;
    private String name;
    private String slug;
    private SalonStatus status;
    // ...lógica de dominio
    public void activate() { this.status = SalonStatus.ACTIVE; }
    public void suspend(String reason) { ... }
}

// infrastructure/adapter/out/persistence/SalonJpaEntity.java — JPA
@Entity @Table(name = "salons")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SalonJpaEntity extends TenantAwareEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String externalId;
    private String name;
    // ...columnas JPA
}
```

MapStruct mapea entre ambas: `SalonPersistenceMapper` (domain ↔ JPA) y `SalonMapper` (domain ↔ DTO).

---

## 10. Observabilidad

| Mecanismo | Propósito |
|---|---|
| **CorrelationIdFilter** | UUID por request → MDC → todos los logs |
| **Propagación X-Correlation-Id** | Header reenviado en llamadas inter-servicio |
| **Structured Logging (JSON)** | Logs parseables en producción (ELK, Loki, CloudWatch) |
| **Texto legible en local** | Perfil `local` mantiene logs en formato humano |
| **MDC enrichment** | correlationId + tenantId + userId en cada línea de log |
| **Micrometer Tracing + Zipkin** | Futuro — tracing distribuido completo |

---

## 11. Seguridad

| Mecanismo | Propósito |
|---|---|
| **JWT RS256** | Keycloak firma con clave privada, validación via JWKS |
| **PKCE** | Authorization Code + PKCE para SPA frontend |
| **6 capas anti cross-tenant** | Anti-spoofing → @Filter → @PrePersist → updatable=false → PSK → RS256 |
| **Rate limiting** | 100 req/min general, 10 req/min booking público |
| **Honeypot + deduplicación** | Anti-bot en booking público |
| **GDPR anonimización** | Borrado lógico de datos personales |
| **Verificación firma Stripe** | Webhook valida signature header |
| **Problem Details RFC 9457** | Errores no filtran información interna |

---

## 12. Comunicación Inter-Servicio

| Mecanismo | Detalle |
|---|---|
| **REST síncrono** | `@HttpExchange` con `RestClient` |
| **Virtual Threads** | Llamadas bloqueantes sin consumir platform threads |
| **Propagación de headers** | X-Tenant-Id, X-Correlation-Id, X-Internal-Service-Key |
| **Timeouts estrictos** | connect=2s, read=3s |
| **Reintentos** | Backoff exponencial 500ms→1s→2s, max 3 |
| **Reconciliación nocturna** | 03:00 CET — Stripe ↔ BD + Keycloak attributes |

---

## 13. Lo que NO se usa

| Descartado | Razón |
|---|---|
| Docker (para dev) | Overhead de rebuild. Ejecución local directa |
| Eureka / Consul | URLs fijas en localhost |
| Event Sourcing / CQRS | 10x más complejo de lo necesario |
| Spring Cloud Config | application-{profile}.yml suficiente |
| Gradle | Maven multi-módulo más estándar |
| API versioning | Controlamos backend y frontend |
| i18n en backend | MVP en castellano |
