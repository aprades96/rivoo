# Rivoo — Plan de Implementación Detallado

## Fase 0: Prerequisitos del Sistema
> Verificar/instalar lo que necesita estar en la máquina ANTES de escribir código.

- [x] **0.1** Verificar Java 25 (JDK) instalado y en PATH → Java 25.0.2 LTS ✅
- [x] **0.2** Verificar Maven 3.9+ instalado y en PATH → Maven 3.9.11 (IntelliJ bundled, añadido a .bashrc) ✅
- [x] **0.3** Verificar MySQL 8.0+ instalado y corriendo en puerto 3306 → MySQL 8.0.40/8.0.43, servicio MySQL80 RUNNING ✅
- [x] **0.4** Descargar Keycloak 26.x (ZIP distribution) e instalarlo en una ruta local → Keycloak 26.0.6 en E:\keycloak-26.0.6 ✅
- [x] **0.5** Verificar que Git está disponible e inicializar el repositorio → Git 2.33.0, repo inicializado ✅

---

## Fase 1: Esqueleto del Proyecto + Infraestructura Local (Semana 1)

### 1A — Estructura Maven multi-módulo
- [x] **1A.1** Inicializar repositorio Git con `.gitignore` (Java/Maven/IntelliJ) ✅
- [x] **1A.2** Crear `pom.xml` parent con spring-boot-starter-parent:4.0.3, 10 módulos, Spring Cloud 2025.1.1 BOM ✅
- [x] **1A.3** Crear `pom.xml` de cada módulo hijo con sus dependencias específicas ✅
- [x] **1A.4** Crear la estructura de paquetes base de cada servicio (`com.rivoo.{servicio}`) ✅
- [x] **1A.5** Crear clase `Application.java` (@SpringBootApplication) en cada servicio ✅
- [x] **1A.6** Verificar que `mvn clean compile` pasa sin errores ✅

### 1B — rivoo-common (módulo compartido)
- [x] **1B.1** Paquete `security/`: KeycloakJwtConverter, TenantAwareJwtAuthenticationToken, SecurityConfig, InternalEndpointFilter, SecurityAutoConfiguration ✅
- [x] **1B.2** Paquete `tenant/`: TenantContext, TenantInterceptor, TenantAwareEntity, TenantEntityListener, TenantFilterAspect, TenantAutoConfiguration ✅
- [x] **1B.3** Paquete `web/`: GlobalExceptionHandler (Problem Details RFC 9457), WebAutoConfiguration ✅
- [x] **1B.4** Paquete `exception/`: ResourceNotFoundException, TenantMismatchException, PlanLimitExceededException, BusinessValidationException ✅
- [x] **1B.5** Paquete `observability/`: CorrelationIdFilter, LoggingInterceptor, ObservabilityAutoConfiguration ✅
- [x] **1B.6** Paquete `client/`: InterServiceRestClientConfig ✅
- [x] **1B.7** AutoConfiguration.imports registrado ✅
- [x] **1B.8** Verificar que `rivoo-common` compila y los servicios lo resuelven ✅

### 1C — MySQL local (7 schemas)
- [x] **1C.1** Crear directorio `infrastructure/mysql/` ✅
- [x] **1C.2** Crear `init-local.sql` con 7 CREATE DATABASE + usuario rivoo/rivoo123 + GRANT ✅
- [x] **1C.3** Ejecutar el script contra MySQL local ✅
- [x] **1C.4** Verificar 7 BDs existen y usuario rivoo conecta ✅

### 1D — Keycloak local
- [x] **1D.1-11** Realm JSON importado: realm rivoo, 3 clients (salon-frontend PKCE, salon-backend confidential, salon-admin-cli confidential), 3 roles, client scope tenant-info con 3 protocol mappers, token 15min, refresh rotation ✅

### 1E — Configuración Spring Boot por servicio
- [x] **1E.1** application.yml × 9 (puerto, nombre, virtual threads) ✅
- [x] **1E.2** application-local.yml × 9 (datasource, JWKS URI, PSK) ✅
- [x] **1E.3** Flyway + V1 placeholder migration × 7 servicios con BD ✅
- [x] **1E.4** logback-spring.xml × 9 (local=texto con correlationId, prod=JSON structured) ✅
- [x] **1E.5** Servicios verificados: api-gateway UP, salon-service UP, admin-service UP ✅

### 1F — Scripts de conveniencia
- [x] **1F.1** infrastructure/scripts/dev-setup.sh ✅
- [x] **1F.2** infrastructure/scripts/dev-start-all.sh ✅

### ✅ Verificación Fase 1
- [x] `mvn clean package -DskipTests` pasa (11/11 SUCCESS, ~19s) ✅
- [x] MySQL tiene 7 BDs accesibles con usuario `rivoo` ✅
- [x] Keycloak arranca con realm `rivoo`, 3 clients, 3 roles, protocol mappers ✅
- [x] Servicios arrancan: api-gateway (Netty/8080), salon-service (Tomcat/8082), admin-service (Tomcat/8088) ✅
- [x] Logs muestran [correlationId] [tenantId] en patrón ✅

---

## Fase 2: auth-service — Wrapper Keycloak Admin API (Semana 2)

- [x] **2.1** Migración Flyway V1: `onboarding_events` + `tenant_user_mapping` ✅
- [x] **2.2** Entidades JPA + domain models (hexagonal): `OnboardingEvent`, `TenantUserMapping` ✅
- [x] **2.3** Keycloak Admin via RestClient (NO keycloak-admin-client — evita conflictos Jackson 2.x/3.x en SB4) ✅
- [x] **2.4** KeycloakTokenManager (client_credentials grant con salon-admin-cli, token cache, auto-refresh) ✅
- [x] **2.5** KeycloakAdminAdapter (implements KeycloakAdminPort): ✅
  - `createUser(email, password, firstName, lastName)` → crea user en Keycloak
  - `setUserAttributes(keycloakUserId, attributes)` → tenant_id, subscription_plan, salon_name
  - `assignRealmRole(keycloakUserId, roleName)`
  - `searchUserIdsByAttribute(attr, value)` → busca usuarios por atributo
  - `setUserEnabled(keycloakUserId, enabled)` → habilita/deshabilita
  - `updateUserAttribute(keycloakUserId, key, value)` → actualiza atributo individual
  - `deleteUser(keycloakUserId)` → compensación
- [x] **2.6** 6 Endpoints internos (protegidos por PSK): ✅
  - `POST /api/internal/auth/register-owner` — crea owner + atributos + ROLE_SALON_OWNER
  - `POST /api/internal/auth/register-employee` — crea employee + atributos + ROLE_EMPLOYEE
  - `PUT /api/internal/auth/tenants/{tenantId}/disable` — deshabilita usuarios del tenant
  - `PUT /api/internal/auth/tenants/{tenantId}/attributes` — actualiza atributos del tenant
  - `GET /api/internal/auth/tenants/{tenantId}/users` — lista usuarios del tenant
  - `PUT /api/internal/admin/tenants/{tenantId}/status` — enable/disable tenant (admin)
- [ ] **2.7** Tests unitarios del servicio (pendiente para Fase 11)
- [x] **2.8** Test manual: JWT contiene tenant_id, subscription_plan, salon_name, realm_access.roles ✅
- [x] **2.9** Verificar que endpoints internos rechazan peticiones sin header PSK → 403 ✅

### ✅ Verificación Fase 2
- [x] auth-service crea usuario en Keycloak con atributos custom ✅
- [x] El JWT obtenido contiene claims `tenant_id`, `subscription_plan`, `salon_name` ✅
- [x] `realm_access.roles` contiene `ROLE_SALON_OWNER` / `ROLE_EMPLOYEE` ✅
- [x] Endpoints internos protegidos por PSK (403 sin header) ✅
- [x] Duplicate email detection → 409 con Problem Details ✅
- [x] Compensación: si falla post-creación → deleteUser se invoca ✅

---

## Fase 3: salon-service + Multi-Tenant + Gateway básico (Semana 3)

### 3A — salon-service
- [x] **3A.1** Migración Flyway V2: `salons` + `salon_business_hours` ✅
- [x] **3A.2** Entidades JPA: `SalonJpaEntity` (extends TenantAwareEntity), `SalonBusinessHoursJpaEntity` ✅
- [x] **3A.3** Generación de external_id: `ExternalIdGenerator` en rivoo-common (`sal_` + UUID) ✅
- [x] **3A.4** Repositorios JPA: `SalonJpaRepository`, `SalonBusinessHoursJpaRepository` ✅
- [x] **3A.5** DTOs: RegisterSalonRequest/Response, SalonResponse, SalonPublicResponse, UpdateSalonRequest, BusinessHoursRequest/Response, UpdateStatusRequest ✅
- [x] **3A.6** Domain model puro: Salon, SalonBusinessHours, SalonStatus, SubscriptionPlan ✅
- [x] **3A.7** Input ports (6): RegisterSalon, GetSalon, UpdateSalon, ManageBusinessHours, ManageSalonStatus, ListSalons ✅
- [x] **3A.8** Output ports (3): SalonPersistencePort, BusinessHoursPersistencePort, AuthServicePort ✅
- [x] **3A.9** SalonService (implements all 6 input ports) con onboarding saga + compensación ✅
- [x] **3A.10** Persistence adapters: SalonPersistenceAdapter, BusinessHoursPersistenceAdapter ✅
- [x] **3A.11** MapStruct mappers: SalonPersistenceMapper, SalonDtoMapper ✅
- [x] **3A.12** AuthServiceAdapter (RestClient → auth-service POST /api/internal/auth/register-owner) ✅
- [x] **3A.13** SalonSecurityConfig (overrides rivoo-common: POST /api/v1/salons + GET /api/v1/salons/public/** = permitAll) ✅
- [x] **3A.14** SalonController: 9 endpoints (2 public, 4 auth, 3 internal) ✅
- [x] **3A.15** SalonExceptionHandler (409 slug conflict, 404 not found) ✅
- [x] **3A.16** SalonSchedulingConfig: @Scheduled stale ONBOARDING → FAILED cleanup every 5min ✅
- [x] **3A.17** rivoo-common SecurityConfig: added @ConditionalOnMissingBean(SecurityFilterChain.class) ✅

### 3B — api-gateway básico
- [x] **3B.1** TenantPropagationFilter (GlobalFilter): strip headers + extract JWT claims + inject X-Tenant-Id, X-User-Id, X-User-Role, X-User-Email, X-Subscription-Plan ✅
- [x] **3B.2** GatewaySecurityConfig: added POST /api/v1/salons to permitAll ✅
- [ ] **3B.3** CORS configurado (diferido a Fase 6)

### 3C — Verificación multi-tenant
- [x] **3C.1** Crear 2 salones con tenants distintos ✅
- [x] **3C.2** Verificar que salon A NO ve datos de salon B (aislamiento Hibernate @Filter) ✅
- [x] **3C.3** Verificar que el external_id se usa en la API (nunca el id interno) ✅

### ✅ Verificación Fase 3
- [x] `mvn clean package` → BUILD SUCCESS (11/11) ✅
- [x] salon_db Flyway V2 migration applied ✅
- [x] E2E: Register salon via gateway → 201 ACTIVE ✅
- [x] E2E: Login via Keycloak → JWT with tenant_id, ROLE_SALON_OWNER ✅
- [x] E2E: GET/PUT /api/v1/salons/me via gateway with JWT ✅
- [x] E2E: GET/PUT business hours via gateway ✅
- [x] E2E: Public salon page via slug ✅
- [x] E2E: Internal endpoints with PSK (admin list, get-by-slug, status update) ✅
- [x] E2E: Multi-tenant isolation — 2 tenants, each sees only own data ✅
- [x] Gateway TenantPropagationFilter: X-Tenant-Id, X-User-Id, X-User-Role injected from JWT ✅
- [x] Bugs fixed: SCG 5.0 config prefix, JPA flush for delete+save, @PrePersist timestamps ✅

---

## Fase 3.5: Refactoring pre-Fase 4 (20 puntos)

### P0 — Seguridad / Correctness
- [x] **R1** TenantFilterAspect: try-catch de seguridad (abortar query si no se activa filtro) ✅
- [x] **R2** Validación email único en registro de salón (existsByEmail + 409) ✅
- [x] **R3** AuthServiceAdapter: wrapper de excepciones de dominio (AuthServiceException + 502) ✅

### P1 — Consistencia / DRY
- [x] **R4** Clase RivooHeaders con constantes (eliminados magic strings en 4 ficheros) ✅
- [x] **R5** @PrePersist/@PreUpdate en JPA entities de auth-service ✅
- [x] **R6** DRY KeycloakAdminAdapter: executeKeycloakOperation() helper + Location header defensivo ✅
- [x] **R7** Eliminado hibernate dialect explícito en 5 skeleton services (Hibernate 7 auto-detect) ✅
- [x] **R8** MapStruct dependency en 4 POMs de skeleton services (staff, client, appointment, billing) ✅

### P2 — Calidad de diseño
- [x] **R9** Jerarquía de excepciones: RivooException base → GlobalExceptionHandler simplificado a 1 handler ✅
- [x] **R10** Domain model validation en auth-service (Objects.requireNonNull en constructores) ✅
- [x] **R11** Extraer OnboardingSagaService de SalonService (SRP) ✅
- [x] **R12** Documentar flush() en BusinessHoursPersistenceAdapter ✅
- [x] **R13** Validaciones @Size y @Pattern en DTOs de salon-service ✅
- [x] **R14** Location header parsing defensivo en KeycloakAdminAdapter (incluido en R6) ✅

### P3 — Nice to have
- [x] **R15** Externalizar timeouts a properties (`rivoo.client.connect-timeout-seconds`) ✅
- [x] **R16** @ConfigurationProperties para rivoo.security (RivooSecurityProperties record) ✅
- [x] **R17** Auto-configuration ordering explícito (Observability → Tenant → Security → Web/Client) ✅
- [x] **R18** UserRole.fromKeycloakRole() inverso ✅
- [x] **R19** Constantes para horarios por defecto en OnboardingSagaService ✅
- [x] **R20** BusinessHours.validate() con invocación en updateBusinessHours() ✅

### ✅ Verificación Fase 3.5
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11, ~20s) ✅
- [x] Actualizar `tasks/lessons.md` con lecciones de Fase 3.5 (9 lecciones documentadas) ✅
- [x] E2E: 13/13 flujos PASS ✅
  - Bug encontrado y corregido: `KeycloakUserRepresentation.credentials` usaba `Map<String,String>` → `"temporary":"false"` (string). Keycloak no persiste el password. Fix: `CredentialRepresentation` record con `Boolean temporary`.
  - Minor: `BusinessHours.validate()` lanza `IllegalArgumentException` → 500 (debería ser 400/422). Mejora para Fase 4.

---

## Fase 4: staff-service + client-service (Semana 4)

### 4.0 — Fixes pendientes de Fase 3.5
- [x] **4.0.1** `BusinessHours.validate()` lanza `IllegalArgumentException` → 500. Cambiar a `BusinessValidationException` → 422. ✅

### 4A — staff-service
- [x] **4A.1** Migración Flyway V2: `employees`, `employee_working_hours`, `services`, `employee_services` (4 tablas) ✅
- [x] **4A.2** Domain models puros: Employee, EmployeeRole (enum), EmployeeWorkingHours (con validate()), ServiceOffering, EmployeeServiceAssignment (con getEffectiveDuration/Price) ✅
- [x] **4A.3** Generación de external_id: prefijos `emp_`, `svc_` ✅
- [x] **4A.4** Input ports (7): CreateEmployee, GetEmployee, UpdateEmployee, DeactivateEmployee, ManageEmployeeWorkingHours, ManageServiceOffering, ManageEmployeeServices ✅
- [x] **4A.5** Output ports (6): EmployeePersistence, WorkingHoursPersistence, ServiceOfferingPersistence, EmployeeServicePersistence, AuthService, BillingService ✅
- [x] **4A.6** Domain exceptions (5): EmployeeNotFound, ServiceOfferingNotFound, EmployeeLimitExceeded, DuplicateServiceName, AuthServiceException ✅
- [x] **4A.7** DTOs (12 records): CreateEmployee, UpdateEmployee, EmployeeResponse, EmployeeInternalResponse, WorkingHoursRequest/Response, CreateServiceOffering, UpdateServiceOffering, ServiceOfferingResponse/Internal, AssignServicesRequest, EmployeeServiceResponse ✅
- [x] **4A.8** Application services (2): EmployeeService (6 use cases), ServiceOfferingService (5 use cases) ✅
- [x] **4A.9** JPA entities (5): EmployeeJpaEntity (TenantAwareEntity), EmployeeWorkingHoursJpaEntity, ServiceOfferingJpaEntity (TenantAwareEntity), EmployeeServiceJpaEntity (@IdClass), EmployeeServiceId ✅
- [x] **4A.10** Repositories (4), Persistence adapters (4, con flush() en working hours y employee_services) ✅
- [x] **4A.11** REST adapters: AuthServiceAdapter (RestClient), BillingServiceStubAdapter (returns -1 unlimited) ✅
- [x] **4A.12** Controllers (4): EmployeeController (9 endpoints), ServiceOfferingController (4), StaffInternalController (2), StaffExceptionHandler ✅
- [x] **4A.13** MapStruct mappers (4): EmployeePersistence, EmployeeDto, ServiceOfferingPersistence, ServiceOfferingDto ✅
- [x] **4A.14** Config: application-local.yml + auth-service URL ✅

### 4B — client-service
- [x] **4B.1** Migración Flyway V2: `clients` (con campos GDPR, UNIQUE tenant_id+email) ✅
- [x] **4B.2** Domain models (3): Client (con anonymize() e isAnonymized()), Gender (enum), ClientSource (enum) ✅
- [x] **4B.3** Generación de external_id con prefijo `cli_` ✅
- [x] **4B.4** Input ports (6): CreateClient, GetClient, UpdateClient, AnonymizeClient, ExportClientData, InternalClient ✅
- [x] **4B.5** Output port (1): ClientPersistencePort ✅
- [x] **4B.6** Domain exceptions (3): ClientNotFound, ClientAlreadyAnonymized, DuplicateClientEmail ✅
- [x] **4B.7** DTOs (6 records): CreateClient, UpdateClient, ClientResponse, ClientExportResponse, ClientInternalResponse, FindOrCreateClientRequest ✅
- [x] **4B.8** Application service (1): ClientService (6 use cases, incl. find-or-create for public booking) ✅
- [x] **4B.9** JPA entity (1): ClientJpaEntity (TenantAwareEntity, @Enumerated Gender/ClientSource) ✅
- [x] **4B.10** Repository (1), Persistence adapter (1) ✅
- [x] **4B.11** MapStruct mappers (2): ClientPersistence, ClientDto ✅
- [x] **4B.12** Controllers (3): ClientController (6 endpoints), ClientInternalController (2), ClientExceptionHandler (DataIntegrityViolation safety net) ✅

### ✅ Verificación Fase 4
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11, ~23s) ✅
- [x] staff-service: 62 Java files, 15 endpoints, 4 tablas ✅
- [x] client-service: 28 Java files, 8 endpoints, 1 tabla ✅
- [x] Fix 4.0.1: BusinessHours.validate() → BusinessValidationException → 422 ✅
- [x] E2E validation: 40/40 tests PASS ✅
  - Employee CRUD (create, list, get, update, deactivate, working hours, service assignments)
  - Service catalog CRUD + duplicate name → 422
  - Client CRUD + GDPR (anonymize, export) + duplicate email → 409
  - Multi-tenant isolation: T2 sees 0 of T1's data, GET by externalId → 404 cross-tenant
  - Internal endpoints: PSK validation (200 with key, 403 without)
  - Find-or-create: existing by phone → returns existing, new → creates
  - **Bug fix**: `@ConditionalOnBean(EntityManager.class)` → `@ConditionalOnClass` en TenantAutoConfiguration (TenantFilterAspect never created → cross-tenant data leak)

---

## Fase 5: appointment-service — Core del Producto (Semana 5)

- [x] **5.1** Migración Flyway V2: `appointments` con 4 índices críticos (tenant_start, employee_start, overlap_check, reminder) ✅
- [x] **5.2** Entidad JPA: `AppointmentJpaEntity` (extends TenantAwareEntity) con campos denormalizados (snapshots) ✅
- [x] **5.3** Generación de external_id con prefijo `apt_` (ExternalIdGenerator.generate("apt")) ✅
- [x] **5.4** Lógica de disponibilidad (AvailabilityService): ✅
  - Obtener horarios del empleado (staff-service via REST + nuevo endpoint working-hours)
  - Obtener citas existentes del empleado en el rango (excluye CANCELLED/NO_SHOW)
  - Calcular slots libres (work intervals - busy intervals, con breaks)
  - Conversión UTC ↔ timezone del salón (Europe/Madrid)
  - Granularidad de 15 minutos, filtrado por duración del servicio
- [x] **5.5** Detección de conflictos con `@Lock(PESSIMISTIC_WRITE)` + JPQL overlap query ✅
- [x] **5.6** Flujo de creación de cita (AppointmentService.create()): ✅
  1. Validar límites plan (billing-service stub, returns unlimited)
  2. Validar employee + service (staff-service via REST, check active)
  3. Validar cliente (client-service via REST, snapshot data)
  4. Verificar disponibilidad con FOR UPDATE (AppointmentPersistencePort.findOverlappingForUpdate)
  5. INSERT cita con snapshot denormalizado
  6. Programar notificación (notification-service stub, fire-and-forget con try-catch)
- [x] **5.7** Flujo de estados: AppointmentStatus enum con canTransitionTo() y isTerminal() ✅
  - PENDING → CONFIRMED, CANCELLED
  - CONFIRMED → IN_PROGRESS, CANCELLED, NO_SHOW
  - IN_PROGRESS → COMPLETED
  - COMPLETED, CANCELLED, NO_SHOW → terminal
- [x] **5.8** Endpoint de cancelación PUT /api/v1/appointments/{id}/cancel (con cancelación de recordatorios stub) ✅
- [x] **5.9** Endpoints autenticados (6): ✅
  - `POST /api/v1/appointments` (SALON_OWNER, EMPLOYEE)
  - `GET /api/v1/appointments` (filtros: employeeId, startDate, endDate, status + Pageable)
  - `GET /api/v1/appointments/{id}`
  - `PUT /api/v1/appointments/{id}/status`
  - `PUT /api/v1/appointments/{id}/cancel`
  - `GET /api/v1/appointments/availability` (employeeId, date, serviceId opcional)
- [x] **5.10** Endpoints internos (PSK): ✅
  - `GET /api/internal/admin/appointments/stats?tenantId=xxx` (para admin-service)
- [x] **5.11** Staff-service: nuevo endpoint interno `GET /api/internal/staff/{tenantId}/employees/{employeeId}/working-hours` ✅
- [x] **5.12** 47 Java files + 1 SQL migration, `mvn clean package -DskipTests` → BUILD SUCCESS (11/11) ✅

### ✅ Verificación Fase 5
- [x] Crear cita validando employee, service, client, disponibilidad → 201 con snapshot data ✅
- [x] Race conditions prevenidas con FOR UPDATE → 422 "Employee already has appointment" ✅
- [x] Timestamps en UTC, conversión correcta a Europe/Madrid (local 10:00 → UTC 09:00 CET) ✅
- [x] Flujo de estados completo: PENDING→CONFIRMED→IN_PROGRESS→COMPLETED, terminal states block transitions ✅
- [x] Cancelación con razón y cancelledBy → 200, re-cancel → 422 ✅
- [x] Disponibilidad en día laborable → slots cada 15min de 9:00-18:00 (excluye citas existentes) ✅
- [x] Internal stats endpoint con PSK → 200, sin PSK → 403 ✅
- [x] Listado paginado con filtros → 200 ✅
- [x] **Bug fix**: logback-spring.xml en TODOS los servicios requería `<format>ecs</format>` (SB4 StructuredLogEncoder) ✅
- [x] E2E: 19/19 tests PASS ✅

---

## Fase 6: Gateway completo + Integración E2E (Semanas 6-7)

### 6A — Gateway completo (Semana 6)
- [x] **6A.1** Rutas a TODOS los servicios (9 rutas: 8 servicios + Keycloak proxy) ✅
- [x] **6A.2** Rate limiting general: 100 req/min por IP (RateLimitingFilter, in-memory sliding window) ✅
- [x] **6A.3** Rate limiting específico para booking público: 10 req/min por IP ✅
- [x] **6A.4** Ruta pública para Keycloak endpoints (`/realms/**` → localhost:9080) ✅
- [x] **6A.5** Ruta pública para `/api/v1/salons/public/{slug}` (ya existía desde Fase 3) ✅
- [x] **6A.6** Ruta pública para `/api/v1/appointments/book` (ya existía desde Fase 3) ✅
- [x] **6A.7** Ruta pública para `/api/webhooks/stripe` (ya existía desde Fase 3) ✅
- [x] **6A.8** E2E completo: Register → Login → Employee → Service → Client → Appointment → Confirm → InProgress → Complete ✅
- [x] **6A.9** CorrelationIdFilter: genera UUID si no existe, propaga si existe, añade a response ✅
- [x] **6A.10** CorsConfig: localhost:3000/5173 allowed, evil.com blocked, credentials true ✅
- [x] **6A.11** RequestLoggingFilter: structured JSON logs (method, path, status, latencyMs, clientIp, correlationId) ✅

### 6B — Buffer de integración (Semana 7)
- [x] **6B.1** Sin bugs descubiertos ✅
- [x] **6B.2** Cross-tenant E2E: 2 salones (Barberia Norte + Barberia Sur), T2 ve solo sus datos ✅
  - T2 employees: 1 (solo suyo), T2 clients: 0, T2 appointments: 1, T2 GET T1 apt: 404
- [x] **6B.3** Correlation ID propagado en toda la cadena (gateway → downstream, visible en logs JSON) ✅
- [ ] **6B.4** Documentar colección Postman/Bruno (diferido)

### ✅ Verificación Fase 6
- [x] Flujo E2E completo T1: register → staff → client → appointment → complete ✅
- [x] Flujo E2E completo T2: register → staff → appointment ✅
- [x] Rate limiting funcional: 429 a partir de ~100 req/min ✅
- [x] Cross-tenant aislado E2E: T2 no ve datos de T1 (employees 1, clients 0, appointments 1, GET 404) ✅
- [x] Correlation IDs propagados correctamente (auto-generados y custom) ✅
- [x] CORS funcional (allowed origins, blocked origins) ✅
- [x] Gateway structured logging (ECS JSON con method, path, status, latency, correlationId) ✅
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11) ✅
- [x] Nuevos ficheros: 4 Java (CorrelationIdFilter, RateLimitingFilter, CorsConfig, RequestLoggingFilter) ✅

---

## Fase 7: billing-service + Plan Limits (Semana 8)

- [x] **7.1** Migración Flyway V2+V3: `subscription_plans`, `plan_limits`, `subscriptions`, `webhook_event_log` ✅
- [x] **7.2** Seed data: 4 planes (FREE_TRIAL 14d, BASIC €29, PREMIUM €59, ENTERPRISE €99) + 4×4 limits ✅
- [x] **7.3** Entidades JPA (4) + repositorios (4) + persistence adapters (4) + mappers (3) ✅
- [x] **7.4** Stripe: StripeStubAdapter (mock createCustomer, createCheckoutSession, constructEvent) ✅
  - Stripe SDK real se conectará cuando haya claves test, cambiando solo la implementación del adapter
- [x] **7.5** Crear Stripe Customer al registrar salón (stub: cus_mock_UUID) ✅
- [x] **7.6** `POST /api/v1/billing/checkout-session` → returns mock checkout URL ✅
- [x] **7.7** Webhook handler `POST /api/webhooks/stripe` (idempotente): ✅
  - 5 event types: checkout.session.completed, invoice.paid, invoice.payment_failed, customer.subscription.updated, customer.subscription.deleted
- [x] **7.8** Idempotencia con tabla `webhook_event_log` (check stripeEventId before processing) ✅
- [x] **7.9** Cache Caffeine TTL 5min con bypass para escrituras (`forWriteOperation=true`) ✅
- [x] **7.10** Integración salon-service: OnboardingSagaService Step 7 → billing-service POST /api/internal/billing/subscriptions ✅
- [x] **7.11** Integración staff/appointment: stubs reemplazados por BillingServiceAdapter real (RestClient → plan-limits) ✅
- [x] **7.12** Keycloak attribute sync: upgradePlan() → authServicePort.updateTenantAttributes() ✅
- [ ] **7.13** Stripe CLI (diferido hasta tener claves Stripe test)
- [x] **7.14** 57 Java files en billing-service, hexagonal completo ✅
- [x] **7.15** Security config: webhook endpoint public, internal PSK, authenticated JWT ✅

### ✅ Verificación Fase 7
- [x] Register salon → billing crea suscripción FREE_TRIAL + mock Stripe Customer ✅
- [x] GET subscription → TRIALING, FREE_TRIAL, stripeCustomerId=cus_mock_xxx ✅
- [x] GET plans → 4 planes listados con precios ✅
- [x] Plan limits → maxEmployees=1, maxAppointments=50 (FREE_TRIAL) ✅
- [x] Cache bypass → forWriteOperation=true bypasses cache ✅
- [x] Staff limit enforcement → 1st employee OK (201), 2nd employee BLOCKED (402) ✅
- [x] Webhook handler estructura completa con idempotencia ✅
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11, ~20s) ✅

---

## Fase 8: notification-service + Crons (Semana 9)

- [ ] **8.1** Migración Flyway V1: `notification_log`
- [ ] **8.2** Entidad JPA: `NotificationLog`
- [ ] **8.3** Generación de external_id con prefijo `ntf_`
- [ ] **8.4** Configurar Spring Mail (Gmail SMTP o SendGrid)
- [ ] **8.5** Templates de email:
  - APPOINTMENT_REMINDER
  - APPOINTMENT_CONFIRMATION
  - APPOINTMENT_CANCELLATION
  - WELCOME
  - PAYMENT_FAILED
  - SUBSCRIPTION_CANCELED
- [ ] **8.6** Endpoints internos:
  - `POST /api/internal/notifications/send` (envío inmediato, fire-and-forget)
  - `POST /api/internal/notifications/schedule` (programar recordatorios)
  - `POST /api/internal/notifications/send-now` (envío inmediato con tipo)
  - `DELETE /api/internal/notifications/appointment/{appointmentId}` (cancelar recordatorios)
- [ ] **8.7** Crons:
  - Recordatorios 24h y 1h antes de cita
  - Expiración de trials (FREE_TRIAL con trial_end < NOW())
  - Reconciliación nocturna Stripe ↔ BD local
  - Reconciliación atributos Keycloak ↔ billing-service

### ✅ Verificación Fase 8
- [ ] Crear cita → email de confirmación recibido
- [ ] Recordatorios programados se envían correctamente
- [ ] Crons de expiración y reconciliación funcionan

---

## Fase 9: Booking Público + admin-service (Semana 10)

### 9A — Booking público
- [ ] **9A.1** Endpoint `POST /api/v1/appointments/book` (sin JWT)
- [ ] **9A.2** Validaciones anti-abuso:
  - Honeypot field
  - Validación email (formato + MX record)
  - Ventana de booking (1h - 60 días)
  - Deduplicación (mismo email+teléfono, mismo día, mismo salón)
- [ ] **9A.3** Flujo completo:
  1. Validar slug del salón (salon-service)
  2. Validar empleado y servicio (staff-service)
  3. Verificar límites plan (billing-service, bypass cache)
  4. Verificar disponibilidad
  5. Crear o recuperar cliente por email+teléfono (client-service)
  6. INSERT cita con source=ONLINE, status=PENDING
  7. Programar notificaciones
- [ ] **9A.4** Rate limiting específico verificado (10 req/min)

### 9B — admin-service
- [ ] **9B.1** Sin base de datos propia (BFF)
- [ ] **9B.2** Solo accesible por `ROLE_PLATFORM_ADMIN`
- [ ] **9B.3** Endpoints:
  - `GET /api/v1/admin/salons` (agrega desde salon-service)
  - `GET /api/v1/admin/subscriptions/summary` (agrega desde billing-service)
  - `GET /api/v1/admin/appointments/stats` (agrega desde appointment-service)
  - `PUT /api/v1/admin/tenants/{tenantId}/status` (suspender/activar, vía auth-service + salon-service)

### ✅ Verificación Fase 9
- [ ] Booking público funcional con rate limiting y protecciones
- [ ] Admin dashboard lista salones, suscripciones, estadísticas
- [ ] Admin puede suspender/activar tenants

---

## Fase 10: Seguridad + Hardening (Semana 11)

- [ ] **10.1** Rate limiting en auth endpoints (Keycloak proxy)
- [ ] **10.2** Auditoría: TODOS los endpoints internos validan PSK
- [ ] **10.3** Test sistemático cross-tenant: NINGUNA entidad accesible por otro tenant
- [ ] **10.4** Endpoint GDPR de exportación: `GET /api/v1/clients/{id}/export`
  - Incluir historial de citas (consultando appointment-service)
- [ ] **10.5** Revisar logging estructurado: verificar propagación correlationId E2E
- [ ] **10.6** Verificar que external_id NUNCA expone el id interno en ningún endpoint
- [ ] **10.7** Revisar que no hay inyección SQL, XSS, ni vulnerabilidades OWASP top 10
- [ ] **10.8** Verificar firma de webhooks Stripe
- [ ] **10.9** Verificar que CSRF está deshabilitado (API stateless)

### ✅ Verificación Fase 10
- [ ] Test sistemático cross-tenant pasa
- [ ] GDPR export funcional
- [ ] Todos los endpoints internos protegidos
- [ ] Sin vulnerabilidades de seguridad evidentes

---

## Fase 11: Testing Final (Semana 12)

- [ ] **11.1** Tests unitarios: objetivo 70% cobertura en lógica de negocio
  - Mockito para dependencias
  - Lógica pura de disponibilidad, validaciones, estados
- [ ] **11.2** Tests de integración:
  - `@SpringBootTest` + Testcontainers (MySQL temporal) por servicio
  - WireMock para dependencias inter-servicio
- [ ] **11.3** Test de carga básico (10-50 usuarios concurrentes)
- [ ] **11.4** Flujo Stripe completo en modo test:
  registro → trial → checkout → pago → upgrade → atributo Keycloak actualizado
- [ ] **11.5** Flujo Keycloak completo:
  registro → login → refresh → logout
- [ ] **11.6** Test de booking público E2E
- [ ] **11.7** Configurar dependencia WireMock + perfil `local-standalone` para dev aislado

### ✅ Verificación Fase 11
- [ ] Suite de tests verde
- [ ] Cobertura ≥70% en lógica de negocio
- [ ] Flujos E2E completos pasan

---

## Fase 12: Preparación Deploy + Documentación (Semana 13)

- [ ] **12.1** README.md con instrucciones de setup local
- [ ] **12.2** Preparar `docker-compose.yml` (para futuro despliegue)
- [ ] **12.3** Documentar runbook básico:
  - Cómo arrancar el entorno completo
  - Cómo diagnosticar problemas (logs, correlationId)
  - Cómo recuperarse de fallos
- [ ] **12.4** Smoke test final E2E
- [ ] **12.5** Verificar que `rivoo-realm.json` importa correctamente en Keycloak limpio
- [ ] **12.6** Limpieza de código: eliminar TODOs, código muerto, logs innecesarios

### ✅ Verificación Fase 12
- [ ] Todos los servicios arrancan localmente desde cero con scripts
- [ ] Test E2E completo pasa
- [ ] Realm Keycloak reproducible vía JSON
- [ ] Documentación clara y actualizada

---

## Fase 13: Frontend — React + Next.js (Post-backend)

> Repositorio separado: `rivoo-frontend`. NO es un módulo Maven.

### 13A — Setup + Auth
- [ ] **13A.1** Crear proyecto Next.js (App Router, TypeScript, Tailwind CSS, Shadcn/UI)
- [ ] **13A.2** Integración Keycloak OIDC (PKCE flow con client `salon-frontend`)
- [ ] **13A.3** Auth context: login, logout, refresh token, protección de rutas
- [ ] **13A.4** Layout base: sidebar, header con usuario, tenant context

### 13B — Dashboard del salón
- [ ] **13B.1** Página "Mi Salón" (GET/PUT /api/v1/salons/me)
- [ ] **13B.2** Horarios del salón (GET/PUT business hours)
- [ ] **13B.3** Página pública del salón (SSR/SSG con slug)

### 13C — Staff + Servicios
- [ ] **13C.1** CRUD empleados (tabla + formularios)
- [ ] **13C.2** Horarios de empleados
- [ ] **13C.3** CRUD catálogo de servicios
- [ ] **13C.4** Asignación servicios ↔ empleados

### 13D — Clientes
- [ ] **13D.1** CRUD clientes (tabla paginada + búsqueda)
- [ ] **13D.2** Detalle cliente (historial de visitas, notas)
- [ ] **13D.3** Acciones GDPR (anonimizar, exportar datos)

### 13E — Citas (core UI)
- [ ] **13E.1** Vista calendario (día/semana) con FullCalendar
- [ ] **13E.2** Crear cita (seleccionar empleado → servicio → slot → cliente)
- [ ] **13E.3** Gestión de estados (confirmar, completar, cancelar, no-show)
- [ ] **13E.4** Vista de disponibilidad

### 13F — Booking público
- [ ] **13F.1** Página pública de reserva (`/salon/{slug}/book`)
- [ ] **13F.2** Flujo: elegir servicio → empleado → fecha/hora → datos personales → confirmar
- [ ] **13F.3** Sin autenticación, rate limiting, honeypot

### 13G — Billing + Admin
- [ ] **13G.1** Página de suscripción actual + upgrade (Stripe Checkout redirect)
- [ ] **13G.2** Panel admin (solo PLATFORM_ADMIN): listado salones, stats, suspend/activate

### ✅ Verificación Fase 13
- [ ] Login/logout Keycloak funcional (PKCE)
- [ ] CRUD completo de salon/staff/clients/services via UI
- [ ] Calendario de citas funcional
- [ ] Booking público E2E sin autenticación
- [ ] Responsive (mobile-first para el dueño del salón)

---

## Resumen de Fases

| Fase | Foco | Semana |
|------|------|--------|
| 0 | Prerequisitos (Java, Maven, MySQL, Keycloak, Git) | Pre |
| 1 | Esqueleto Maven + rivoo-common + MySQL + Keycloak | 1 |
| 2 | auth-service (Keycloak Admin API wrapper) | 2 |
| 3 | salon-service + multi-tenant + gateway básico | 3 |
| 4 | staff-service + client-service (+ GDPR) | 4 |
| 5 | appointment-service (core del producto) | 5 |
| 6 | Gateway completo + integración E2E | 6-7 |
| 7 | billing-service + Stripe | 8 |
| 8 | notification-service + crons | 9 |
| 9 | Booking público + admin-service | 10 |
| 10 | Seguridad + hardening | 11 |
| 11 | Testing final | 12 |
| 12 | Preparación deploy + documentación | 13 |
| **13** | **Frontend — React + Next.js (repo separado)** | **Post-backend** |
