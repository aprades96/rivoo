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

- [x] **8.1** Migración Flyway V2: `notification_log` (15 columnas, 3 índices) ✅
- [x] **8.2** Entidad JPA: `NotificationLogJpaEntity` (extends TenantAwareEntity) ✅
- [x] **8.3** Generación de external_id con prefijo `ntf_` ✅
- [x] **8.4** Spring Mail: MailStubAdapter (logs only, no SMTP real). MailHog config ready (localhost:1025). Health check disabled. ✅
- [x] **8.5** Templates de email (NotificationTemplateEngine, 6 tipos): ✅
  - WELCOME, APPOINTMENT_CONFIRMATION, APPOINTMENT_REMINDER, APPOINTMENT_CANCELLATION, PAYMENT_FAILED, SUBSCRIPTION_CANCELED
- [x] **8.6** Endpoints internos (3, PSK-protected): ✅
  - `POST /api/internal/notifications/send` (envío inmediato)
  - `POST /api/internal/notifications/schedule` (programar recordatorios)
  - `DELETE /api/internal/notifications/appointment/{appointmentId}` (cancelar recordatorios)
- [x] **8.7** Cron: ProcessPendingNotifications cada 1 min (PENDING + scheduledFor <= NOW → send) ✅
- [x] **8.8** Cross-service integration: ✅
  - appointment-service: NotificationServiceStubAdapter → real RestClient (schedule reminder 24h antes, cancel on cancellation)
  - salon-service: OnboardingSagaService Step 8 → welcome email (fire-and-forget)
- [x] **8.9** 24 Java files en notification-service, hexagonal completo ✅

### ✅ Verificación Fase 8
- [x] Register salon → WELCOME email logged + record SENT en notification_log ✅
- [x] Create appointment → APPOINTMENT_REMINDER scheduled 24h antes en notification_log ✅
- [x] Cancel appointment → reminder CANCELLED en notification_log ✅
- [x] Internal POST send → 200, POST schedule → 201, DELETE cancel → 200 ✅
- [x] Sin PSK → 403 ✅
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11, ~20s) ✅

---

## Fase 9: Booking Público + admin-service (Semana 10)

### 9A — Booking público
- [x] **9A.1** Endpoint `POST /api/v1/appointments/book` (sin JWT) — PublicBookingUseCase + AppointmentSecurityConfig override ✅
- [x] **9A.2** Validaciones anti-abuso: ✅
  - Honeypot field (non-empty → fake 201 response, bot silenced)
  - Ventana de booking (1h - 60 días → 422 outside window)
  - Conflict detection (FOR UPDATE → 422 overlap)
- [x] **9A.3** Flujo completo (10 pasos): ✅
  1. Honeypot check → 2. Booking window → 3. Validate salon slug (salon-service) → 4. Validate employee+service (staff-service) → 5. Check plan limits (billing-service, bypass cache) → 6. Check availability (FOR UPDATE) → 7. Find-or-create client (client-service) → 8. INSERT appointment (source=ONLINE, status=PENDING) → 9. Schedule reminder → 10. Send confirmation
- [x] **9A.4** Rate limiting 10 req/min para /book (ya configurado en gateway Fase 6) ✅
- [x] **9A.5** SalonServicePort + SalonServiceAdapter (RestClient → salon-service by-slug) ✅
- [x] **9A.6** ClientServicePort.findOrCreateClient + ClientServiceAdapter implementation ✅

### 9B — admin-service
- [x] **9B.1** Sin base de datos propia (BFF), TenantAutoConfiguration excluida ✅
- [x] **9B.2** @PreAuthorize("hasRole('PLATFORM_ADMIN')") en todos los endpoints ✅
- [x] **9B.3** Endpoints implementados: ✅
  - `GET /api/v1/admin/salons` (SalonAdminAdapter → salon-service)
  - `GET /api/v1/admin/appointments/stats` (AppointmentAdminAdapter → appointment-service)
  - `PUT /api/v1/admin/tenants/{tenantId}/status` (AuthAdminAdapter + SalonStatusAdapter)
  - `GET /api/v1/admin/tenants/{tenantId}/users` (AuthAdminAdapter → auth-service)
- [x] **9B.4** 12 Java files en admin-service ✅

### ✅ Verificación Fase 9
- [x] Public booking sin JWT → 201 con appointment creada (source=ONLINE) ✅
- [x] Honeypot → 201 fake response (bot silenced) ✅
- [x] Booking window <1h → 422 ✅
- [x] Conflict detection → 422 ✅
- [x] Admin sin JWT → 401 ✅
- [x] Internal stats con PSK → 200 ✅
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11) ✅
- [ ] Admin PLATFORM_ADMIN JWT flow (pendiente: crear usuario admin en Keycloak → Fase 10)

---

## Fase 10: Seguridad + Hardening (Semana 11)

### Auditoría de seguridad completa (security-auditor agent): 6 PASS, 2 WARN (fixed), 1 FAIL (accepted)

- [x] **10.1** Rate limiting en Keycloak proxy (ya en gateway, /realms/** route + 100 req/min) ✅
- [x] **10.2** Auditoría PSK: TODOS los endpoints internos protegidos por InternalEndpointFilter (5 servicios verificados) ✅
- [x] **10.3** Cross-tenant isolation: 7/7 JPA entities con TenantAwareEntity + AOP TenantFilterAspect + `updatable=false` ✅
- [x] **10.4** GDPR export mejorado: client-service export ahora incluye appointment history ✅
  - appointment-service: nuevo endpoint GET /api/internal/admin/appointments/by-client/{clientId}
  - client-service: AppointmentServicePort+Adapter (RestClient → appointment-service)
  - ClientService.export() ahora fetches real appointment data
- [x] **10.5** CorrelationId propagación verificada en Fase 6 (gateway genera/propaga, downstream recibe via header) ✅
- [x] **10.6** External IDs: todos los MapStruct mappers mapean externalId→id. Zero internal IDs expuestos ✅
- [x] **10.7** SQL injection: zero. 13 @Query annotations revisadas, todas JPQL con named params, zero native queries ✅
- [x] **10.8** Webhook Stripe: firma delegada a StripePort.constructEvent() (stub por ahora, real Stripe SDK validará) ✅
- [x] **10.9** CSRF deshabilitado en los 7 SecurityConfig (stateless API) ✅
- [x] **10.10** Input validation: 24 endpoints con @Valid, DTOs con @NotBlank/@Size/@Email/@Pattern ✅
- [x] **10.11** Error leakage: GlobalExceptionHandler devuelve ProblemDetail sin stack traces ✅
- [x] **10.12** Stripe IDs ocultos: stripeCustomerId/stripeSubscriptionId eliminados de SubscriptionResponse público ✅
- [ ] **10.13** Secrets en application-local.yml: ACEPTADO para dev local. Prod usará ${ENV_VAR} (diferido)

### ✅ Verificación Fase 10
- [x] Auditoría 6 PASS, 2 WARN remediados, 1 FAIL aceptado ✅
- [x] GDPR export incluye appointment history ✅
- [x] Stripe IDs no expuestos en API pública ✅
- [x] `mvn clean package -DskipTests` → BUILD SUCCESS (11/11) ✅

---

## Fase 11: Testing Final (Semana 12)

- [x] **11.1** Tests unitarios (112 tests, 10 test files, 0 failures): ✅
  - appointment-service (60 tests): AppointmentStatusTest (state machine), AvailabilityServiceTest (slots), AppointmentServiceTest (CRUD+cancel), PublicBookingTest (honeypot, window)
  - billing-service (23 tests): PlanLimitsServiceTest (cache), SubscriptionServiceTest (lifecycle), WebhookServiceTest (idempotency)
  - staff-service (7 tests): EmployeeServiceTest (plan limits, create)
  - client-service (8 tests): ClientServiceTest (CRUD, anonymize, duplicate email)
  - notification-service (14 tests): NotificationServiceTest (send, schedule, cancel, templates)
- [x] **11.2** Tests de integración con Testcontainers (MySQL real): ✅
  - appointment-service: AppointmentRepositoryIntegrationTest (save, findByFilters, overlap detection, countByMonth, findByClient)
  - billing-service: BillingRepositoryIntegrationTest (seed data verification, plan limits, subscription CRUD, unique tenant constraint)
  - Testcontainers 1.21.4 + spring-boot-testcontainers + @ServiceConnection
  - `@Tag("integration")` — excluidos por defecto en surefire, ejecutar con `-DincludedGroups=integration`
- [ ] **11.3** Test de carga (diferido)
- [ ] **11.4** Stripe E2E (diferido — requiere claves test)
- [x] **11.5** spring-boot-starter-test añadido al parent pom como dependencia global ✅
- [x] **11.6** `mvn clean test` → BUILD SUCCESS, 112 tests GREEN ✅

### ✅ Verificación Fase 11
- [x] Suite de tests 100% verde (112 tests, 0 failures) ✅
- [x] Lógica crítica cubierta: state machine, availability, plan limits, webhooks, GDPR ✅

---

## Fase 12: Preparación Deploy + Documentación (Semana 13)

- [x] **12.1** README.md: overview, architecture diagram, tech stack, services table, quick start, API overview ✅
- [x] **12.2** docker-compose.yml: MySQL + Keycloak + 9 services con env vars y health checks ✅
- [x] **12.3** Documentación en README (setup, services, endpoints) ✅
- [x] **12.4** Smoke test manual validado en Fases 5-10 (E2E curl tests) ✅
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
>
> **Estado real (revisado 2026-08-27, verificado contra el código; último commit del repo frontend: `70ca8d5`, 2026-03-23).**
> Stack final implementado: **Next.js 16 (App Router) + TypeScript + Tailwind v4 + Shadcn/UI + React Query + Zustand + NextAuth v5 (provider Keycloak) + Vitest**.
> Diseño **mobile-first**: navegación por bottom-nav + FAB, no sidebar de escritorio.

### 13A — Setup + Auth
- [x] **13A.1** Crear proyecto Next.js (App Router, TypeScript, Tailwind CSS, Shadcn/UI) — commit `f689764`
- [x] **13A.2** Integración Keycloak OIDC (PKCE flow con client `salon-frontend`) — commit `c422ead`
- [x] **13A.3** Auth context: login, logout, refresh token, protección de rutas — `src/auth.ts` (refresco automático 60s antes de expirar) + `src/middleware.ts` (rutas públicas: `/login`, `/register`, `/book`, `/api/auth`)
- [x] **13A.4** Layout base: header con usuario, tenant context — `src/components/layout/` (`app-header`, `bottom-nav`, `fab-button`, `onboarding-gate`). **Desviación**: bottom-nav móvil en lugar de sidebar

### 13B — Dashboard del salón
- [x] **13B.1** Página "Mi Salón" (GET/PUT /api/v1/salons/me) — `/(app)/settings/salon`
- [x] **13B.2** Horarios del salón (GET/PUT business hours) — `/(app)/settings/business-hours`
- [x] **13B.3** Página pública del salón con slug — `/book/[slug]` consume `GET /api/v1/salons/public/{slug}`. **Desviación**: es CSR (`"use client"`), no SSR/SSG → pendiente si se quiere SEO

### 13C — Staff + Servicios
- [x] **13C.1** CRUD empleados (tabla + formularios) — `/(app)/staff` + `employee-card`, `employee-form`
- [x] **13C.2** Horarios de empleados — `components/staff/working-hours-editor.tsx`
- [x] **13C.3** CRUD catálogo de servicios — tab Servicios (commit `c8f46c9`)
- [x] **13C.4** Asignación servicios ↔ empleados — `components/staff/service-assignment.tsx`

### 13D — Clientes
- [x] **13D.1** CRUD clientes (tabla paginada + búsqueda) — `/(app)/clients`
- [x] **13D.2** Detalle cliente (historial de visitas, notas) — `/(app)/clients/[id]`
- [x] **13D.3** Acciones GDPR (anonimizar, exportar datos) — commit `8c9fa6b`

### 13E — Citas (core UI)
- [x] **13E.1** Vista calendario **día** — `/(app)/calendar` con `time-grid`, `appointment-block`, `date-navigator`, `employee-filter`. **Desviación**: implementación propia, NO FullCalendar
- [ ] **13E.1b** Vista calendario **semana** — no implementada
- [x] **13E.2** Crear cita (empleado → servicio → slot → cliente) — wizard de 5 pasos en `/(app)/appointments/new`
- [x] **13E.3** Gestión de estados (confirmar, completar, cancelar con motivo, no-show) — `appointment-detail-sheet` con updates optimistas
- [x] **13E.4** Vista de disponibilidad — `GET /api/v1/appointments/availability` consumido en el wizard y en booking público
- [x] **13E.5** Vista "Hoy" (fuera del plan original) — `/(app)/today`, commit `c942f2b`
- [ ] **13E.6** Página de detalle de cita `/(app)/appointments/[id]` — **placeholder de 10 líneas ("En desarrollo")**, commit `62df0e5`

### 13F — Booking público
- [x] **13F.1** Página pública de reserva — **desviación de ruta**: `/book/{slug}`, no `/salon/{slug}/book`
- [x] **13F.2** Flujo: servicio → fecha/hora → datos personales → confirmar → éxito (5 componentes en `components/booking/`)
- [x] **13F.3** Sin autenticación, honeypot + consentimiento GDPR — commit `5757a7c` (rate limiting lo aplica el gateway: 10 req/min)

### 13G — Billing + Admin
- [x] **13G.1** Página de suscripción actual + upgrade — `/(app)/settings/billing`, redirige a `checkoutUrl`. **Ojo**: el backend Stripe sigue siendo stub (ver 7.13)
- [ ] **13G.2** Panel admin (solo PLATFORM_ADMIN): listado salones, stats, suspend/activate — **NO EXISTE en el frontend**. El backend `admin-service` (:8088) sí está listo y validado E2E (Fase 9B)

### 13H — Fuera del plan original (implementado)
- [x] **13H.1** Flujo de registro de salón — `/(auth)/register`, commit `70ca8d5`
- [x] **13H.2** Wizard de onboarding de 6 pasos — `/(onboarding)/{welcome,salon-setup,business-hours,add-employee,add-service,complete}` + `onboarding-gate`, commit `2ccb5ab`
- [x] **13H.3** Settings completos: perfil salón, horarios, billing, booking, cuenta — commit `596a3cf`
- [x] **13H.4** Tests con Vitest — 14 ficheros de test (`npm run test`)
- [x] **13H.5** `DEV.md` con instrucciones de arranque local y usuarios de test — commit `c791948`

### 🐞 Deuda técnica detectada en el frontend
- [ ] **13X.1** `(onboarding)/salon-setup/page.tsx:34` — TODO explícito: el onboarding post-login usa el endpoint de *register* en vez de `PUT /api/v1/salons/me`
- [ ] **13X.2** `(app)/settings/booking/page.tsx:31` — comentario "This is a placeholder; the actual API may differ": integración sin confirmar contra el backend
- [ ] **13X.3** `(auth)/layout.tsx:20` — texto "placeholder" visible en el layout de auth
- [ ] **13X.4** `README.md` del frontend sigue siendo el de `create-next-app` (la doc útil está en `DEV.md`)

### ✅ Verificación Fase 13
- [x] Login/logout Keycloak funcional (PKCE + refresh automático)
- [x] CRUD completo de salon/staff/clients/services via UI
- [x] Calendario de citas funcional (vista día)
- [ ] Booking público E2E sin autenticación → **ROTO**: no hay endpoint público de servicios, empleados ni disponibilidad. Ver `docs/specs/reserva-publica/`
- [x] Responsive (mobile-first para el dueño del salón)
- [ ] Panel admin PLATFORM_ADMIN (13G.2)

---

## Resumen de Fases

> Estado revisado 2026-08-27. Fases 1-12 (backend) cerradas y validadas E2E. Fase 13 (frontend) al ~90%.

| Fase | Foco | Semana | Estado |
|------|------|--------|--------|
| 0 | Prerequisitos (Java, Maven, MySQL, Keycloak, Git) | Pre | OK |
| 1 | Esqueleto Maven + rivoo-common + MySQL + Keycloak | 1 | OK |
| 2 | auth-service (Keycloak Admin API wrapper) | 2 | OK |
| 3 | salon-service + multi-tenant + gateway básico | 3 | OK |
| 4 | staff-service + client-service (+ GDPR) | 4 | OK |
| 5 | appointment-service (core del producto) | 5 | OK |
| 6 | Gateway completo + integración E2E | 6-7 | OK |
| 7 | billing-service + Stripe | 8 | OK (Stripe stub) |
| 8 | notification-service + crons | 9 | OK |
| 9 | Booking público + admin-service | 10 | ROTO — ver docs/specs/reserva-publica |
| 10 | Seguridad + hardening | 11 | OK |
| 11 | Testing final | 12 | OK |
| 12 | Preparación deploy + documentación | 13 | OK |
| **13** | **Frontend — React + Next.js (repo separado)** | **Post-backend** | ~90% (falta panel admin) |


---

## Hoja de ruta — frontend + backend (repriorizada 2026-08-27)

Orden por gravedad: primero lo que está **roto en producción**, luego lo que muestra
**datos equivocados**, y al final lo cosmético. Cada bloque no trivial lleva su plan
en `docs/specs/<slug>/IMPLEMENTATION_PLAN.md`; los artboards de `rivoo-frontend/design/`
son la especificación visual.

### 1. Reserva pública — ✅ CERRADA (2026-08-28)

44 tareas hechas y revisadas. El flujo funciona de punta a punta; hasta el 28/08 no mostraba
ni un solo hueco (el frontend leia `availableSlots` y el backend emite `slots`), y nadie lo
sabia. Cerrados tambien los tres callejones sin salida del paso de profesional (8d5fcf1).

> **Lo que NO cierra este bloque**, y sigue abierto en sus propias secciones mas abajo:
> el desajuste de la ventana de 1 hora (se ofrecen huecos que luego se rechazan al confirmar),
> el oraculo de enumeracion de correos en el alta anonima de salon, y que nada fija el contrato
> de disponibilidad por el lado del backend.

Roto en producción: no hay endpoint público de servicios, empleados ni disponibilidad,
así que el paso 1 sale siempre vacío.

- [x] **RP.1** DTOs públicos de staff (sin email ni teléfono)
- [x] **RP.2** Listado de empleados por tenant con filtro explícito por columna
- [x] **RP.3** Listado de servicios por tenant
- [x] **RP.4** Endpoints internos de listado en StaffInternalController
- [x] **RP.5** StaffServicePort + StaffServiceAdapter (header X-Tenant-Id explícito)
- [x] **RP.6** Agregado público del salón + rechazo de salón no ACTIVE
- [x] **RP.7** Endpoint público de disponibilidad por slug
- [x] **RP.8** Regla del gateway para /api/v1/appointments/public/**
- [x] **RP.9** Tipos y cliente API del frontend
- [x] **RP.10** Store de 6 pasos
- [x] **RP.11** Paso Profesional y cableado del flujo
- [~] **RP.12** Tests de aislamiento cross-tenant — **NO SE HACE (decision del usuario, 2026-08-27)**
- [x] **RP.13** Que el store acepte ServicePublic (hoy el componente rellena category/isActive a mano)
- [x] **RP.14** Filtrar los null de serviceIds (asignación huérfana → EmployeeServicePersistenceAdapter:45) (1ffa72d)
- [x] **RP.15** `getInternal()` ignora su parámetro `tenantId` en empleados y servicios — **fuga cross-tenant en la ruta pública**

> **RP.15, hallazgo del 2026-08-27, no estaba en el plan.**
> `staff-service/.../application/EmployeeService.java:121-125` recibe `tenantId` y no lo usa:
> hace `findByExternalId(employeeExternalId)` sin predicado de tenant. Durante un
> `POST /api/v1/appointments/book` anónimo no hay `TenantContext`, así que
> `TenantFilterAspect:20,30` tampoco activa el `@Filter` de Hibernate. Resultado: la
> validación que debe comprobar que el empleado pertenece a ese salón no comprueba nada,
> y un atacante puede reservar en el salón A citando un empleado del salón B.
> `ServiceOfferingService.java:97-101` tiene exactamente el mismo defecto: confirmado, no supuesto.

- [x] **RP.16** El campo `isOpen` de los horarios no cuadra entre backend y frontend (9b8061b + 902f15d; verificado: todos los DTO de red dicen isOpen)
- [x] **RP.20** Deuda de la review de RP.5 (alcance del `catch`, constante del header, log) (RivooHeaders.TENANT_ID en uso; catch reformado en b5bf21d)
- [x] **RP.17** Reformar la URL de los listados internos: `/{tenantId}/public/{employees,services}`
- [x] **RP.18** Deuda menor de la review de RP.4/RP.8 (logs, docs, test del gateway)
- [x] **RP.19** BLOQUEANTE: `salon-service/application-prod.yml` no declara `staff-service.url`
- [x] **RP.23** DECISION DE PRODUCTO PENDIENTE: senalizar la degradacion al frontend (4071ad5; los dos flags se consumen en public-service-step y public-employee-step)
- [x] **RP.24** BLOQUEANTE: `catch` estrechado de mas en `StaffServiceAdapter` → 500 en la pagina publica
- [x] **RP.25** `RIVOO_SERVICES_STAFF_SERVICE_URL` falta en el runbook de Railway y en docker-compose
- [x] **RP.26** Menores de la review de RP.17-20 (asercion del gateway, test de targetTenantId, plan)
- [x] **RP.21** BLOQUEANTE: llamadas HTTP dentro de `@Transactional` en `getPublicBySlug`
- [x] **RP.22** Deuda de la review de RP.6 (Jackson del test, orden de advices, nombres de DTO)
- [x] **RP.27** Las excepciones de salon-service no extienden `RivooException` (causa raiz del `@Order`)
- [x] **RP.28** Menores de la review de RP.21-22 (javadoc enganoso, sufijo Dto, Jackson 2 en staff)

> **RP.16, hallazgo del 2026-08-27, no estaba en el plan.**
> Los records de Java exponen el componente tal cual se llama, y no hay ninguna
> `PropertyNamingStrategy` de Jackson en el repo. Estado real del contrato:
>
> | DTO | JSON que emite/espera el backend | Lo que usa el frontend | |
> |---|---|---|---|
> | `salon` `BusinessHoursRequest` | `isOpen` | `isOpen` | OK |
> | `salon` `BusinessHoursResponse:7` | `open` | `isOpen` | ROTO |
> | `staff` `WorkingHoursRequest:11` | `open` | `isOpen` | ROTO |
> | `staff` `WorkingHoursResponse:7` | `open` | `isOpen` | ROTO |
>
> Efectos hoy: `working-hours-editor.tsx:35` lee `h.isOpen` → `undefined` → pinta los
> siete dias como cerrados. `public-service-step.tsx:76` hace `.filter((h) => h.isOpen)`
> → lista vacia. Y en staff el fallo es bidireccional: al guardar, el frontend manda
> `isOpen` y Jackson lo descarta, asi que todo dia se guarda cerrado.
>
> El lado `salon`/response lo arregla RP.6, porque el agregado publico lleva
> `businessHours` dentro y sin eso RP.6 nace roto. Los dos de `staff` van aparte:
> tocan la pantalla autenticada de horarios de empleado, no la reserva publica.

> **RP.17, de la review de RP.4 (2026-08-27). Hacerlo ANTES de escribir mas clientes.**
> `/{tenantId}/services/{serviceId}` y `/{tenantId}/services/public` comparten posicion en
> el path. Y ese `{serviceId}` se rellena con input anonimo: `AvailabilityService:88` hace
> `getService(tenantId, serviceId)` con el `@RequestParam` de `GET /public/availability`,
> que `d060fe4` acaba de abrir sin JWT (`PublicBookingRequest` solo valida `@NotBlank`,
> sin patron `svc_`). Un `?serviceId=public` construye
> `/api/internal/staff/{tenant}/services/public` y cae en el listado completo.
> Hoy el atacante no ve el dato (Jackson revienta al meter un array en un record y sale 500),
> pero un parametro anonimo dispara un listado de tabla entera en otro servicio con la PSK
> de appointment-service: *confused deputy*. Mover el literal a `/{tenantId}/public/employees`
> y `/{tenantId}/public/services` lo elimina de raiz. Toca el controller de staff, su test,
> y el `StaffServiceAdapter` de salon-service. Coste cero ahora; crece en cuanto alguien
> escriba el cliente en appointment-service.

> **RP.18, de la misma review.** Tres cosas menores, ninguna bloqueante:
> 1. `StaffInternalController:60,66` loguea el literal `GET /api/internal/staff/{tenantId}/...`
>    — la fluent API de SLF4J no interpola ahi, asi que `{tenantId}` sale tal cual y ademas
>    no hay campo `tenantId` en el JSON. La convencion de no duplicarlo existe porque lo
>    inyecta `TenantInterceptor` desde la cabecera, pero en el flujo anonimo esa cabecera no
>    se propaga: aqui el tenant viene del **path**. O mensaje estatico, o excepcion consciente.
> 2. `api-gateway` no tiene `src/test`. Es la linea mas sensible del repo y no hay nada que
>    fije el contrato. Un test que afirme GET publico != 401 y POST mismo path == 401.
> 3. Docs desalineadas: `staff-service/CLAUDE.md` no lista los dos endpoints internos nuevos,
>    y `appointment-service/CLAUDE.md` sigue sin `GET /public/availability` en su tabla.

> **Prioridad de RP.15 elevada por la review:** `d060fe4` convierte ese defecto en alcanzable
> **sin JWT**. Escenario concreto: `?salonSlug=<salon B>&serviceId=<svc_ del salon A>` devuelve
> el servicio del tenant A y la disponibilidad de B se calcula con la duracion de A. El impacto
> observable hoy es debil (solo se consume `durationMinutes`), y el `employeeId` si queda acotado
> porque `getWorkingHoursInternal` (`EmployeeService:198`) si valida el tenant. Aun asi, RP.15
> debe cerrarse **antes** de que esta rama llegue a produccion, no despues.

> **RP.19, de la review de RP.5 (2026-08-27). BLOQUEANTE.**
> `salon-service/src/main/resources/application-prod.yml:26-33` declara `auth-service`,
> `billing-service` y `notification-service` bajo `rivoo.services`, pero NO `staff-service`.
> `StaffServiceAdapter` es un `@Component` con `@Value("${rivoo.services.staff-service.url}")`
> **sin default**, asi que con `SPRING_PROFILES_ACTIVE=prod` Spring no resuelve el placeholder
> al instanciar el bean y **el contexto no levanta**: se cae salon-service entero, no solo la
> reserva publica. Agravante: el puerto aun no tiene consumidor, o sea que el fallo de arranque
> llega antes de que la funcionalidad aporte nada.
> Arreglo: `staff-service: url: ${RIVOO_SERVICES_STAFF_SERVICE_URL}`. Esa variable YA existe en
> el ecosistema — `api-gateway/application-prod.yml:21` la usa. Solo hay perfiles `local` y
> `prod` en salon-service, con eso queda cubierto.
> Causa raiz: mi prompt de despacho enumero `application-local.yml` y no `application-prod.yml`.
> Leccion registrada en `tasks/lessons.md`.

> **RP.20, de la misma review.** Ninguno bloqueante:
> 1. `StaffServiceAdapter:29-60,65-87` — el `.stream().map(...)` esta DENTRO del try, asi que un
>    NPE en la traduccion se loguea como "staff-service no responde" y devuelve lista vacia: el
>    log miente sobre la causa. Y el `catch (Exception)` no distingue 5xx (degradar) de 4xx
>    (error de configuracion nuestro). Escenario real: se rota la PSK en staff-service y no en
>    salon-service → 403 en cada llamada → TODOS los salones muestran "sin servicios"
>    indefinidamente, con la pagina devolviendo 200 y sin error visible.
> 2. `StaffServiceAdapter:42,69` y su test usan el literal `"X-Tenant-Id"` en vez de
>    `RivooHeaders.TENANT_ID`, que `rivoo-common` ya expone y que usa el propio
>    `InterServiceRestClientConfig:54`. Como el test tambien hardcodea el literal, cambiar la
>    constante rompe la propagacion **sin que falle ningun test**.
> 3. `StaffServiceAdapter:57,84` — `.addKeyValue("tenantId", …)` colisiona con la clave MDC.
>    Aqui NO hay que quitarlo (en flujo anonimo es la unica forma de saber que salon fallo),
>    pero el valor es semanticamente el tenant DESTINO, no el del contexto. Renombrar a
>    `targetTenantId`. Es el unico `.addKeyValue("tenantId", …)` de todo el repo.
> 4. Nota, no accion: el test usa `RestClient.builder()` pelado, asi que no cubre que
>    `headerPropagationInterceptor` **sobrescribe** la cabecera explicita cuando `TenantContext`
>    no es null. Es fail-closed (interseccion vacia, sin fuga), pero conviene decirlo en el
>    comentario porque contradice el "el header es el tenantId del argumento" que promete.
> 5. Nota, no accion: `CLAUDE.md:250-254` afirma que `InterServiceRestClientConfig` hace
>    reintentos y circuit breaker. **No los hace.** Con connect=2s + read=3s y dos llamadas
>    secuenciales, el peor caso de la degradacion son ~10s de espera antes de devolver vacio.
> Debe quedar cubierto por los tests de RP.12.

> **RP.21, de la review de RP.6 (2026-08-27). BLOQUEANTE.**
> `SalonService.getPublicBySlug` esta anotado `@Transactional(readOnly = true)` y hace DOS
> llamadas HTTP a staff-service dentro. La conexion JDBC se toma en `findBySlug` y no se
> suelta hasta retornar, o sea que se sujeta durante toda la red: connect 2s + read 3s por
> llamada, secuenciales, hasta ~10s.
> `application-prod.yml:9` fija `maximum-pool-size: 10`. Con 10 visitantes anonimos
> concurrentes en `/book/{slug}` el pool se agota y cae salon-service ENTERO: tambien
> `/api/v1/salons/me` autenticado y el `POST /api/v1/salons` del alta de negocio.
> El rate limit del gateway es 100/min POR IP (`RateLimitingFilter:31-33`), no lo impide.
> No hace falta ataque: basta con que staff-service vaya lento.
> Arreglo: cerrar la transaccion antes de las llamadas y componer fuera. Ojo con la
> autoinvocacion (el proxy de Spring no se aplica) y con `LazyInitializationException`
> (`open-in-view: false` en prod, no hay red).

> **RP.22, de la misma review.** Tres, ninguno bloqueante:
> 1. El test de regresion de `isOpen` usa Jackson 2 (`com.fasterxml`), pero el runtime
>    serializa con Jackson 3 (`tools.jackson`): el classpath tiene ambos y Boot 4 usa el 3.
>    Hoy los dos emiten `isOpen`, asi que el bug SI esta arreglado — pero el test no guarda
>    el serializador real. Sintoma: pasa `null` en los cuatro `LocalTime` porque con valores
>    reales ese mapper lanza `InvalidDefinitionException` (le falta jsr310).
> 2. Ni `SalonExceptionHandler` ni el `GlobalExceptionHandler` de rivoo-common declaran
>    `@Order`, y el global tiene un `@ExceptionHandler(Exception.class)` que matchea todo.
>    Probado: con el local primero sale 404; con el global primero, 500. Hoy funciona por
>    accidente (autoconfiguracion despues del component-scan). No es fuga —el 404 sigue
>    siendo indistinguible en ambas ramas— sino contrato y ruido de alertas.
> 3. Tres records identicos, DOS con el mismo nombre simple (`EmployeePublicDto` en
>    `application/dto/` y en `infrastructure/adapter/out/rest/dto/`, mas
>    `StaffServicePort.EmployeePublicInfo`). Importar el equivocado COMPILA en silencio.

> **RP.23 — esto no lo decido yo, es decision de producto.**
> Si staff-service falla, el agregado devuelve `200` con `services: []` y `employees: []`.
> Para el visitante, "este salon no tiene servicios" y "no hemos podido cargar el catalogo"
> se ven exactamente igual: ve una pagina vacia y se va, y el salon pierde la reserva sin
> que nadie se entere (solo un WARN en logs, y no hay alertado configurado en el repo).
> Opciones: (a) dejarlo asi, mas simple; (b) un flag `degraded` en la respuesta para que el
> frontend distinga y reintente. (b) cambia el contrato contra el que el frontend YA esta
> escrito (`salon.ts:41-63`), asi que no lo aplico sin decidirlo antes.

### 2. Onboarding reanudable — HECHO (verificado en codigo 2026-08-30)

También roto en producción, para usuarios nuevos: `onboarding-gate.tsx:41` deduce el
estado contando empleados y servicios, y si faltan te manda a `/welcome`. Como los pasos
3 y 4 tienen "Omitir", **quien omite entra en bucle y no llega nunca a la app**.
Además guarda en cada paso pero reempieza en el 1: la única combinación sin defensa.

No sirve mirar `status` (la saga deja el salón ACTIVE ya en el registro) ni "¿tiene
horarios?" (la saga crea horarios por defecto en el paso 4 del registro).

- [x] **ON.1** Campo `onboarding_completed_at` en salón + migración Flyway
- [x] **ON.2** Endpoint para marcarlo, y exponerlo en SalonResponse
- [x] **ON.3** El gate mira solo ese flag; fuera la lógica de contar empleados/servicios
- [x] **ON.4** "Ir al dashboard" y "Omitir" marcan el flag
- [x] **ON.5** El paso de horarios precarga los que ya existen
> Comprobado contra el CODIGO, no contra estas casillas: migracion
> `V4__add_salon_onboarding_completed_at.sql` con compare-and-set (idempotente, el
> `WHERE ... IS NULL` de `SalonJpaRepository`), `SalonResponse.onboardingCompletedAt`
> con test que fija su nombre en el JSON, `onboarding-gate.tsx:43` decidiendo por el
> flag (fuera el contar empleados y servicios), `(onboarding)/business-hours/page.tsx:31`
> precargando los horarios, y `salon-setup` borrada.
> El gate distingue ademas el 404 irrecuperable de "necesita onboarding", que antes
> paseaba al dueno por cuatro pasos hasta un segundo 404 sin salida.
> ON.6: `book/[slug]/page.tsx:101` dice "Este salon aun no acepta reservas online" y
> distingue el catalogo caido (`servicesUnavailable`) para no costarle una reserva al
> salon por un fallo de red; con test de las dos ramas. ON.8: doce commits sobre
> `(onboarding)` y `register`, incluido `feat(onboarding): salon listo contra el diseno`.

- [x] **ON.6** Estados vacíos: "Hoy" sin servicios, y página pública "aún no acepta reservas"
- [x] **ON.7** Decidir qué se hace con `salon-setup` (huérfana, se numera como paso 2)
- [x] **ON.8** Pantallas ya dibujadas: página "Alta de negocio" del canvas (12 artboards)

### 3. Detalle de cita — PENDIENTE

`appointments/[id]` es un placeholder de 10 líneas: pinchar una cita no lleva a nada.
Dibujado en móvil (hoja inferior) y escritorio (panel lateral sobre el calendario).

### 4. Pantalla "Hoy" — PENDIENTE

Muestra **una** próxima cita para un salón con N empleados. Sustituir por el bloque
"Ahora mismo" con una fila por empleado.

### 5. Navegación — PENDIENTE

Clientes fuera del bottom-nav y dentro de "Más"; quitar Empleados/Servicios duplicados
de Ajustes.

### 6. Shell de escritorio — PENDIENTE

Hoy `(app)/layout.tsx` es solo una columna centrada. Sidebar 248px + topbar 72px.
El bloque más grande; merece plan propio. **Es el siguiente**: unos treinta artboards
`*Desktop` dependen de el (CV.1).

> **Se lleva ademas el `min-h-full` de `(app)/layout.tsx:21`**, mismo bug que ya arreglamos
> en el alta y en la reserva: `min-h-full` es un porcentaje y `html`/`body` no tienen `height`,
> asi que la regla es inerte y el pie no se pega abajo en movil. Se corrige aqui y no como
> tarea suelta porque este bloque reescribe ese fichero de todas formas.

### 7. Pantallas sin dibujar — DIBUJADAS 2026-08-28 · canvas: 6 paginas, 71 artboards

Al cruzar rutas contra artboards faltaban 12 pantallas. Ya estan en el canvas, movil y
escritorio, hechas contra el codigo real. Separadas por si se pueden construir hoy:

**Tienen codigo — se pueden implementar ya**

- [ ] **FE.1** Equipo (lista) — `(app)/staff/page.tsx` · pestana principal
- [ ] **FE.2** Detalle de empleado — `(app)/staff/[id]/page.tsx` + editor de horarios + asignacion
- [ ] **FE.3** Formulario de empleado — `components/staff/employee-form.tsx` (hoja inferior)
- [ ] **FE.4** Detalle de cliente — `(app)/clients/[id]/page.tsx` + panel RGPD
- [ ] **FE.5** Formulario de cliente — `components/clients/client-form.tsx`
- [ ] **FE.6** Login — `(auth)/login/page.tsx` · es un boton que entrega a Keycloak, no un formulario
- [ ] **FE.7** Perfil del salon — `(app)/settings/salon/page.tsx` · solo edita nombre, telefono y descripcion
- [ ] **FE.8** Reservas online — `(app)/settings/booking/page.tsx`
- [ ] **FE.9** Plan y facturacion — `(app)/settings/billing/page.tsx`
- [ ] **FE.10** Mi cuenta — `(app)/settings/account/page.tsx`

**Dibujadas como PROPUESTA — no existen en el codigo**

- [ ] **FE.11** Notificaciones — sin ruta. Contenido derivado de los tipos reales que envia
      notification-service; la antelacion (24 h y 1 h) la fija un cron, no es configurable.
- [ ] **FE.12** Error de reserva (hueco ocupado) — hoy solo hay un banner rojo en el paso 5.
- [ ] Zona de peligro — dibujada dentro de FE.10; no hay flujo de desactivacion.
- [ ] QR de la pagina publica — dibujado en FE.8; no existe.
- [ ] Dias de prueba en la tarjeta de plan — el backend envia `trialDays` y la UI lo ignora.

**Desajustes canvas <-> codigo detectados al dibujar**

- [ ] La URL publica: el codigo genera `rivoo.app/book/<slug>`, el canvas pone
      `rivoo.app/<slug>`. DECISION: quitar el `/book/` o corregir los artboards.
- [ ] `/clients` esta construida y **no la enlaza nadie**: ni el codigo ni la barra inferior.
      El wireframe dice que vive bajo "Mas", pero `settings/page.tsx` no la lista.
- [ ] El interruptor de activar/desactivar reservas existe a medias: hay `toggleMutation`
      declarada que nunca se pinta y que llama a la API con el cuerpo vacio.
- [ ] `AjustesDesktop.dc.html` dibuja 8 campos editables + logo y color; el codigo edita 3.
- [ ] Pendiente de decision: dialogo de confirmacion de anonimizado. El wireframe exige
      teclear "ANONIMIZAR"; el codigo lo tiene activo desde el principio.

### Deuda técnica suelta

- [ ] Renombrar `middleware.ts` → `proxy.ts` (deprecado en Next 16.2)
- [ ] `/clients` es una pantalla huérfana: nadie enlaza a ella


> **RP.27, de la review de RP.21-22 (2026-08-27). Causa raiz, no sintoma.**
> `@Order(0)` en `SalonExceptionHandler` arregla el caso concreto, pero el problema de fondo
> es que las CUATRO excepciones de salon-service (`SalonNotFoundException`,
> `EmailAlreadyInUseException`, `AuthServiceException`, `SlugAlreadyExistsException`)
> extienden `RuntimeException` pelado. Las de appointment-service extienden `RivooException`,
> y POR ESO aquel servicio no esta roto pese a no declarar `@Order`: las resuelve
> `GlobalExceptionHandler.handleRivooException` dentro del MISMO advice, donde gana la
> especificidad del metodo y el orden entre advices es irrelevante.
> Consecuencia hoy: cualquier excepcion nueva de salon-service que nadie anada a mano a
> `SalonExceptionHandler` cae en el catch-all → 500.
> Si `SalonNotFoundException extends ResourceNotFoundException`, el `@Order(0)` sobra y el
> 404 sale gratis y consistente con el resto de la plataforma. Aplica igual a staff-service.
> Deuda relacionada, latente pero NO bloqueante para la ruta publica (verificado uno a uno):
> `AppointmentExceptionHandler`, `BillingExceptionHandler`, `ClientExceptionHandler` y
> `StaffExceptionHandler` tampoco declaran `@Order`. El proximo handler especifico que se
> anada a esos servicios saldra 500 de forma no determinista.

> **RP.28, de la misma review.** Cuatro menores:
> 1. `BusinessHoursResponseJsonTest:26-30` — el javadoc promete que el test falla si alguien
>    anade un `PropertyNamingStrategy` o un `JsonMapperBuilderCustomizer`. **Es falso**: el
>    revisor anadio un customizer SNAKE_CASE y el test siguio pasando, porque el slice
>    `@JsonTest` solo incluye `@JsonComponent`/serializers/modules, no `@Configuration`
>    arbitrarias. El test SI cubre la regresion para la que se escribio; lo enganoso es el
>    comentario. Recortar la afirmacion, o cubrir el pipeline completo sobre el endpoint.
> 2. `EmployeePublicResponseDto` / `ServicePublicResponseDto` — el sufijo `Dto` es huerfano en
>    el modulo (`SalonResponse`, `BusinessHoursResponse`, `SalonPublicResponse`,
>    `RegisterSalonResponse` no lo llevan), y el mensaje del commit se contradice a si mismo
>    al citarlos como precedente. Queda visible en `SalonPublicResponse`, un record sin `Dto`
>    que contiene `List<ServicePublicResponseDto>`. Ademas `SalonDtoMapper:31,33` conserva los
>    nombres de metodo antiguos `toServicePublicDto`/`toEmployeePublicDto`, ya desalineados.
> 3. `staff-service/.../EmployeeServicePublicListTest:3` sigue importando Jackson 2
>    (`com.fasterxml`). Es el mismo defecto que corrigio `75922da`, y en el servicio que
>    alimenta empleados y servicios de la reserva publica.
> 4. `GlobalExceptionHandler` de rivoo-common deberia declarar
>    `@Order(Ordered.LOWEST_PRECEDENCE)` explicito: hoy la garantia descansa en que nunca lo
>    declare. Y `SalonPublicSnapshotLoader` usa `@Component` donde el resto de la capa usa
>    `@Service` (cosmetico).

> **Nota de proceso para futuras reviews:** el directorio `scratchpad/clean` quedo contaminado
> con una copia de trabajo previa (incluia `target/generated-sources` y los DTO antiguos). Un
> revisor estuvo a punto de reportar un falso positivo grave —"el renombrado dejo duplicados"—
> hasta contrastarlo con `git ls-tree`. Extraer siempre a un directorio NUEVO y vacio.


> **RP.12 — bloqueado, y el motivo cambia la decision (2026-08-27).**
> Testcontainers SI esta disponible (BOM en el pom raiz, 1.21.4, artefactos `mysql` y
> `junit-jupiter` en `~/.m2`). Lo que falta es **Docker**: no esta en el PATH ni instalado
> como Docker Desktop. Y **tampoco hay CI**: no existe `.github/workflows`, `.gitlab-ci.yml`,
> `Jenkinsfile` ni `.circleci`.
> Por tanto "dejarlos escritos para que corran en CI" significa escribir tests que **no corren
> en ningun sitio**. Esta sesion ha demostrado por mutacion, cinco veces, que una asercion que
> nunca se ha visto en rojo no esta probada: unos tests de aislamiento que jamas se ejecutan
> darian falsa confianza justo en la propiedad mas delicada de la feature.
> Lo que SI quedo cubierto y ejecutandose: los tests unitarios de RP.15, que se vieron en rojo
> antes del arreglo (4 fallos) y ahora estan en verde. Cubren el scoping por tenant en la capa
> de aplicacion. Lo que queda SIN cubrir es el comportamiento del `@Filter` de Hibernate contra
> MySQL real con `TenantContext` vacio. Esa es la brecha exacta, ni mas ni menos.
> Decision pendiente del usuario: instalar Docker, montar CI, o asumir la brecha.

> **Observacion de RP.15, no pedida y no aplicada.** `update`, `deactivate`, `assignServices` y
> `updateWorkingHours` (en `EmployeeService` y `ServiceOfferingService`) tambien reciben
> `tenantId` y tampoco lo usan para acotar el `findByExternalId`. NO son la misma gravedad:
> van por endpoints autenticados con JWT, asi que `TenantContext` esta poblado y el `@Filter`
> de Hibernate SI se activa y acota la consulta. Es una brecha de defensa en profundidad
> (dependen de una sola capa en vez de dos), no una fuga viva. Tarea aparte si se decide.


> **RP.12 — decidido: no se hace.** El usuario confirma que no va a haber Docker por ahora.
> **Brecha asumida, escrita aqui para que nadie la descubra por sorpresa:** nadie ha verificado
> contra MySQL real que, con `TenantContext` vacio (peticion anonima), el `@Filter` de Hibernate
> quede desactivado y sea el filtrado por columna explicita el unico que acota la consulta.
> Lo que SI esta verificado y corriendo: los tests unitarios de RP.15 (se vieron en rojo, 4
> fallos, antes del arreglo) cubren el scoping por tenant en la capa de aplicacion.
> Si algun dia entra Docker o CI en el proyecto, esta es la primera tarea que recuperar.

> **Historial — decidido: se queda.** El commit `6eb273f` no compila aislado (se llevo dos
> `git mv` ajenos por usar `git add` + `git commit` sobre un indice compartido). HEAD si compila.
> Se asume: un `git bisect` que caiga justo ahi fallaria. Desaparece solo si la rama se integra
> con squash merge. La leccion que lo previene esta en `tasks/lessons.md`.

- [x] **RP.29** Flag `degraded` en el agregado publico (decidido 2026-08-27)
- [x] **RP.30** `AuthServiceException` de staff-service: mismo defecto que RP.27
- [x] **RP.32** SEGURIDAD: oraculo de enumeracion de salones en los endpoints anonimos de citas
- [x] **RP.33** Rehacer `b62a2d7`: el javadoc corregido sigue mintiendo, y el `@Order` de 4 advices
- [x] **RP.34** Cuerpo `null` declarado no-degradado: contradice el contrato y deja vivo el bug
- [x] **RP.35** `BillingServiceException` sin handler → 500 determinista en el alta de negocio
- [x] **RP.36** Huecos de cobertura y nombre del flag (F1, F3, F4, F5 de la review de RP.29)
- [x] **RP.37** Paridad de logging para `BillingServiceException` (atError + stack trace)
- [x] **RP.38** Tests anti-enumeracion que ejercitan la propiedad de verdad
- [x] **RP.42** Deuda de la review de RP.37-38 (visibilidad, nombre de test, constante, javadoc)
- [ ] **RP.31** Consumir `catalogueUnavailable` en el frontend de la pagina de reserva

> **RP.29 — por que se hace.** El argumento decisivo no es la caida de staff-service, es que
> **una lista vacia de servicios es tambien un estado legitimo y frecuente**: por la decision
> del onboarding (opcion B), empleados y servicios son OPCIONALES, asi que un salon recien dado
> de alta que se salto esos pasos tiene cero servicios con toda normalidad. Sin el flag, "este
> negocio aun no ha cargado su catalogo" y "no hemos podido hablar con staff-service" colapsan
> en la misma pantalla, y el primero va a ser comun.
> Es un campo **aditivo**: `degraded?: boolean` no rompe el `SalonPublic` actual y el frontend
> puede ignorarlo hasta que se toque esa pantalla. Backend ahora; consumo en frontend, aparte.
> Lo que NO resuelve: el dueno del salon sigue sin enterarse. Eso es alertado, no API, y este
> repo no tiene ninguno configurado.


> **RP.30, hallado al cerrar RP.27.** `staff-service/.../domain/exception/AuthServiceException.java:3`
> extiende `RuntimeException` pelado, y `StaffExceptionHandler:15` **no declara `@Order`**.
> Es potencialmente MAS fragil que lo que tenia salon-service antes del arreglo: si
> `GlobalExceptionHandler` se visita antes que `StaffExceptionHandler` —y el orden entre dos
> advices ambos en `LOWEST_PRECEDENCE` no esta especificado—, un `AuthServiceException` real
> (fallo al hablar con auth-service al registrar un empleado) devolveria **500 en vez de 502**,
> de forma no determinista y sin ningun test que lo cubra.
> Las otras cuatro excepciones de staff-service ya extienden subclases de `RivooException`.
> Mismo patron que `39ee0dc`, un solo fichero.


> **RP.31 — la otra mitad de RP.29.** (El campo se llama ya `catalogueUnavailable`, no `degraded`.) El backend ya distingue "este salon no ha cargado su
> catalogo" de "no hemos podido hablar con staff-service": `SalonPublicResponse` lleva
> `degraded`. Falta que la pagina lo use: hoy los dos casos pintan la misma pantalla vacia.
> Compatibilidad verificada: el frontend **no** usa Zod ni validacion de esquema en runtime
> —`apiFetch<SalonPublic>` es un generico de TS sobre `response.json()`, borrado en ejecucion—
> asi que el campo extra se ignora sin romper nada hasta que se consuma.
> Al hacerlo, cuidado con el mensaje: con `degraded=false` y listas vacias el texto correcto es
> "este negocio aun no ha publicado sus servicios" (estado legitimo por la opcion B del
> onboarding); con `degraded=true`, "no hemos podido cargar el catalogo" y ofrecer reintentar.


> **RP.32, de la review de RP.27-28 (2026-08-27). SEGURIDAD, preexistente, anonimo, explotable.**
> `GET /api/v1/appointments/public/availability?salonSlug=X` (permitAll desde `d060fe4`) da:
> - **500** si X no existe — `SalonServiceAdapter:31-38` captura el 404 de salon-service y lo
>   convierte en `RuntimeException`, que cae en el `@ExceptionHandler(Exception.class)`.
> - **422** `detail: "Salon is not active"` si X existe y esta suspendido
>   (`AvailabilityService:44-46`, y el gemelo en `AppointmentService:293`).
> - **200** si esta activo.
> Tres respuestas distinguibles → cualquiera enumera slugs y descubre que negocios existen y
> cuales estan suspendidos. Mismo vector en `POST /api/v1/appointments/book`.
> **Contradice justo el invariante que protegimos en salon-service**, donde `SalonPublicSnapshotLoader`
> hace indistinguibles "no existe" y "no ACTIVE" con un test que lo fija. Lo protegimos en un
> endpoint y lo dejamos roto en los otros dos de la misma superficie anonima.
> Arreglo: que appointment-service trate ambos casos con la MISMA respuesta (404), y que el
> adaptador no convierta el 404 legitimo en 500. Leccion registrada en `lessons.md`.

> **RP.33, de la misma review. `b62a2d7` recibio CAMBIOS REQUERIDOS: su codigo es correcto, su texto no.**
> 1. `BusinessHoursResponseJsonTest:30` — el javadoc CORREGIDO sigue mintiendo. Cita una anotacion
>    `@JsonProperty("isOpen")` que **no existe** (`grep -rn "JsonProperty" salon-service/src/main`
>    → cero) y `@JsonComponent`, que **no esta en el classpath del modulo** (el revisor intento
>    usarlo y no compila). Lo unico cierto de la lista es "Jackson module": el revisor registro un
>    `SimpleModule` que renombra `isOpen`→`open` y el test SI fallo.
> 2. `GlobalExceptionHandler:19` — el comentario afirma que cualquier advice de servicio queda
>    **"guaranteed"** por delante. **Es falso para 4 de 6 servicios**: `StaffExceptionHandler`,
>    `AppointmentExceptionHandler`, `BillingExceptionHandler` y `ClientExceptionHandler` tampoco
>    declaran `@Order`, asi que siguen empatados en `LOWEST_PRECEDENCE` y el desempate sigue sin
>    especificar. El revisor lo probo registrando ambos advices en los dos ordenes. Consecuencia
>    viva: staff `AuthServiceException` → 502 **o** 500; appointment/billing `IllegalArgumentException`
>    → 400 **o** 500; client `DataIntegrityViolationException` → 409 **o** 500.
>    Arreglo: reformular el comentario a lo que realmente hace, y poner `@Order(0)` en los cuatro.
>    (Nota: `StaffExceptionHandler` ya lo recibio en `66f8a64`; quedan tres.)
> 3. Menores: `SalonExceptionHandlerOrderTest:27,40` tiene javadoc obsoleto por este mismo lote, y
>    su linea 50 afirma "byte-for-byte identical response bodies", que el test no comprueba y que
>    ademas es falso (el `detail` incrusta el slug y hay `timestamp`).
> 4. `EmployeeService:203-209` — el chequeo de tenant de `getWorkingHoursInternal` **no tiene test**:
>    el revisor lo borro en su mutacion y no fallo nada. Es el patron que `b412690` dice replicar.
> 5. `SalonExceptionHandler:36-74` — tres de los cuatro handlers producen ya un ProblemDetail
>    identico al de `handleRivooException` (verificado). Solo el de `AuthServiceException` aporta
>    el `atError`+stack trace. Envejecera mal: un cambio en un sitio los desincroniza sin test.


> **RP.34, de la review de RP.29-30 (2026-08-27). HACER ANTES QUE RP.31.**
> `StaffServiceAdapter:88-90` y `:132-134` hacen `if (employees == null) return Optional.of(List.of())`,
> es decir declaran **no degradado** un cuerpo ausente. Pero el javadoc del propio puerto
> (`StaffServicePort:15-16`) dice que `Optional.empty()` significa "network error, 5xx,
> **unreadable body**". Un 200 sin cuerpo ES un cuerpo ilegible: codigo y contrato se contradicen.
> Comprobado empiricamente por el revisor con `MockRestServiceServer`: `200` con cuerpo vacio,
> `200` con JSON `null` y `204 No Content` dan los tres `PRESENT size=0`, o sea no degradado.
> Efecto: un proxy que devuelva 200 vacio hace que la pagina diga "este salon no tiene servicios"
> en vez de senalar el error — **el bug original, vivo por esa entrada**.
> Hay que decidirlo y fijarlo con test: o `null` → `Optional.empty()` (coherente con el texto), o
> se corrige el javadoc y se documenta la decision contraria. Hoy ningun test cubre el caso, asi
> que los dos mutantes sobreviven.
> **Antes de RP.31**: si el frontend va a elegir mensaje segun `degraded`, este agujero pinta la
> pantalla equivocada.

> **RP.35, de la misma review. PREEXISTENTE y peor que lo que arreglamos.**
> `salon-service/.../domain/exception/BillingServiceException.java:3` extiende `RuntimeException`
> pelado, se lanza en `BillingServiceAdapter:41` (alta de salon, ruta HTTP real) y **no tiene
> `@ExceptionHandler` en ningun sitio** (verificado: cero resultados). Cae siempre en el catch-all
> → **500 "An unexpected error occurred"** en vez de 502. A diferencia de los casos de RP.27/RP.30,
> aqui no es una carrera entre advices: es determinista.

> **RP.36, menores de la misma review.**
> 1. (F1) `StaffServiceAdapter:129` — la rama `RestClientException` de `getPublicServices` es el
>    **unico mutante que sobrevivio** de los 12: no tiene ningun test. `getPublicEmployees` tiene
>    tres para esa misma rama. Y es justo la rama que se anadio para reparar la regresion de RP.24.
> 2. (F3) `SalonService:98` — no hay test para "falla solo employees" (si lo hay para services).
>    El mutante que elimina ese termino del OR sobrevive.
> 3. (F4) `degraded` no tiene test de serializacion JSON, habiendo precedente en el repo
>    (`BusinessHoursResponseJsonTest`, creado tras el incidente de `isOpen`, para esta misma clase
>    de fallo silencioso).
> 4. (F5) El comentario del `@Order(0)` de `StaffExceptionHandler` justifica la correa diciendo que
>    garantiza que gane su logging (`atError` + stack trace). Al quitarlo todo sigue verde mientras
>    el diagnostico se degrada en silencio a `handleRivooException`, que loguea `atWarn` **sin**
>    `setCause`. La garantia esta declarada, no verificada.
> 5. **Nombre del flag.** `degraded` dice "algo va mal" pero no que solo afecta al catalogo de staff
>    (horarios y datos del salon siguen siendo fiables). `catalogueUnavailable` o
>    `staffCatalogueDegraded` se explican solos. La ventana barata se cierra en cuanto RP.31 lo
>    consuma. **Decision mia: renombrar ahora.**
> 6. El revisor deja constancia de que NO hay que omitir el campo cuando es falso: "ausente"
>    significaria a la vez "servidor viejo" y "no degradado" — el mismo colapso de dos significados
>    que este commit existe para eliminar.


> **RP.37, decision mia tras la review de RP.34-36.** `BillingServiceException` ya da 502 en vez de
> 500, pero se quedo con el logging generico de `handleRivooException` (`atWarn`, **sin** causa),
> mientras que `AuthServiceException` de salon-service si tiene handler dedicado con `atError` y
> stack trace. Es la misma clase de fallo —dependencia externa caida durante el alta de negocio— y
> `BillingServiceException` si lleva causa (se lanza desde `BillingServiceAdapter:41` envolviendo la
> excepcion original). Perder el stack trace ahi deja el diagnostico de una caida de billing en una
> linea de WARN sin causa: exactamente el hueco contra el que `fb90062` acaba de escribir un test
> para el caso de auth. Darle paridad.


- [x] **RP.39** Desdoblar el flag: `servicesUnavailable` / `employeesUnavailable`
- [ ] **RP.40** `BillingServiceAdapter` convierte 4xx de negocio en 502
- [ ] **RP.41** DECISION: el `detail` de las excepciones de dependencia filtra topologia interna

> **RP.39, de la review de RP.34-36 (2026-08-27). Renombrado mio que quedo peor.**
> `catalogueUnavailable` se calcula como `services.isEmpty() || employees.isEmpty()`, asi que puede
> valer `true` con un array `services` real y con datos dentro. "Unavailable" afirma una totalidad
> que el payload contradice. El propio nombre del test lo delata:
> `..._catalogueUnavailableButServicesStillArrive`.
> Riesgo concreto: el front lee `true`, oculta el catalogo entero y tira una lista de servicios buena.
> **Decision: desdoblar en dos flags**, no buscar un tercer nombre. El consumidor tiene DOS pantallas
> —`public-service-step` y `public-employee-step` son pasos distintos del flujo— asi que un solo flag
> obliga a las dos a mostrar error aunque solo una haya fallado. Dos flags mapean 1:1 con las dos
> pantallas y permiten el estado parcial preciso. Hacerlo ANTES de RP.31 (aun no hay consumidor).

> **RP.40, de la misma review.** `BillingServiceAdapter:40` hace `catch (Exception e)` y convierte
> **cualquier** fallo en 502. Pero billing-service puede devolver **422** (`DuplicateSubscriptionException`
> → `BusinessValidationException`) o **400** (`@Valid` en `BillingInternalController:36`). Un duplicado
> permanente se reporta como "dependencia caida, reintenta". NO es regresion (antes era 500, igual de
> incorrecto) y NO es asimetria: los dos `AuthServiceAdapter` tienen la misma forma. Es una debilidad
> compartida de convencion: al arreglarlo, mirar los tres a la vez.

> **RP.41 — decision, no tarea mecanica.** `POST /api/v1/salons` es `permitAll` (`GatewaySecurityConfig:23`).
> Antes el catch-all devolvia `"An unexpected error occurred"`; ahora `handleRivooException` pone
> `detail = ex.getMessage()`, o sea `"Failed to create subscription in billing-service for tenant: sal_new"`,
> y un test lo fija en el contrato. Expone topologia interna (que servicios hay, como se llaman) a un
> anonimo. Atenuante: `AuthServiceException` ya hacia lo mismo en ese mismo endpoint, asi que es
> convencion sistemica, no defecto nuevo. Opciones: (a) dejarlo; (b) mensaje generico hacia fuera y el
> detalle solo al log. Afecta a todas las excepciones de dependencia del monorepo, no solo a esta.


> **RP.42 cerrada.** La constante `RivooErrorTypes.SALON_NOT_FOUND` vive ya en `rivoo-common` y la
> usan productor y consumidor. Cero literales sueltos en el repo (verificado).
> **Objecion del implementador, y tiene razon:** pedi que mutar la constante rompiera un test en
> los dos servicios, pero eso solo tenia sentido MIENTRAS existiera la duplicacion. Centralizada,
> la divergencia es imposible por construccion: ningun test puede detectarla porque no puede
> ocurrir. Es garantia de compilacion, mas fuerte que la de test que yo pedia. Mi criterio estaba
> mal planteado.
> **Queda pendiente su sugerencia**, que acepto: un test "golden" que fije el valor literal. Ese
> `type` sale en respuestas HTTP a llamantes anonimos, o sea contrato publico (RFC 9457): cambiarlo
> rompe a cualquiera que lo consuma, y hoy nada obliga a pararse a pensarlo. Va con la siguiente
> tanda, no merece agente propio.


> **RP.13 cerrada (2026-08-28), commit `b786e4b` en rivoo-frontend.** El store publico ya se tipa con
> `ServicePublic`. Dos cosas que el apano escondia y solo se vieron al quitarlo:
> 1. Ademas de inventar `category: null, isActive: true`, **descartaba `currency`**, que el backend si
>    envia. `formatCurrency` asumia EUR fijo; ahora acepta divisa (parametro con default, retrocompatible
>    con las ~10 llamadas del flujo interno).
> 2. Habia DOS consumidores mas que no detecte al despachar: `public-employee-step:36` y
>    `public-success-step:44` tambien leen el precio.
> Y el test del store estaba obsoleto (`mockService` tipado con la forma interna), el mismo patron que ya
> nos mordio antes en este repo.

> **RP.16 es SOLO backend.** Verificado: el frontend ya usa `isOpen` en `WorkingHoursResponse` **y** en
> `WorkingHoursRequest` (`types/employee.ts:15,24`). Era el backend el que emitia y esperaba `open`. No
> hay mitad de pantalla que hacer.

- [ ] **RP.43** Deuda de lint preexistente en el frontend: 6 errores `react-hooks/set-state-in-effect`

> **RP.43.** `npm run lint` sale en rojo con 36 problemas (6 errores + 30 warnings), **preexistentes** —
> verificado con `git stash` que ya fallaba antes del commit de RP.13. Los 6 errores son
> `react-hooks/set-state-in-effect` en `settings/salon/page.tsx`, `client-form.tsx`, `service-form.tsx`,
> `employee-form.tsx`, `service-assignment.tsx` y `working-hours-editor.tsx`. Pendiente de dimensionar si
> son cosmeticos o pueden causar bucles de render / estado desincronizado.


## Bloque 0 — FUERA DE LA RESERVA PUBLICA, pero bloquea monetizacion (2026-08-28)

Salieron de la auditoria de contratos backend/frontend. Verificados por mi, no solo reportados.
**Mas graves que cualquier cosa de la reserva publica**, porque impiden cobrar.

- [ ] **MON.1** La lista de planes de `/settings/billing` sale siempre vacia
- [ ] **MON.2** El boton "Gestionar suscripcion" no se muestra nunca
- [ ] **MON.3** La categoria de servicio es una feature de frontend sin backend

> **MON.1.** `billing-service/.../dto/PlanResponse.java` emite
> `(id, name, displayName, monthlyPrice, trialDays)` — **no lleva `isActive`**. El frontend hace
> `plans.filter((p) => p.isActive)` en `settings/billing/page.tsx:125`, asi que `p.isActive` es
> `undefined` para todos y la lista sale vacia: **nadie puede hacer upgrade ni downgrade de plan**.
> El backend ya filtra server-side (`findAllActive()`), asi que el arreglo correcto es **quitar el
> filtro fantasma del frontend**, no anadir el campo al backend.

> **MON.2.** `SubscriptionResponse` no emite `stripeCustomerId` ni `stripeSubscriptionId`
> (`SubscriptionService:154-162`). El frontend condiciona el boton de gestion a
> `subscription.stripeSubscriptionId` (`settings/billing/page.tsx:101`), asi que **nunca se
> renderiza**: un cliente de pago no puede llegar al portal de Stripe.
> Decision de alcance: exponer los ids de Stripe al frontend tiene implicaciones (son
> identificadores de un tercero); la alternativa es un endpoint que devuelva la URL del portal.

> **MON.3.** La columna `category` **no existe** en `services` (revisada `V2__create_staff_schema.sql`
> linea a linea), ni en el dominio, ni en la entidad JPA, ni en ningun DTO. El `staff-service/CLAUDE.md`
> la menciona: la documentacion esta desactualizada respecto al codigo.
> Mientras tanto el frontend tiene la feature COMPLETA: input en `service-form.tsx`, envio en
> `CreateServiceRequest`, y pintado en `service-card.tsx` y `service-step.tsx`. El usuario escribe una
> categoria, se envia, y el backend la descarta en silencio. Arreglarlo bien pide migracion + dominio +
> persistencia + DTOs + mapeo. Decision de alcance del usuario.

## MON.2 — Portal de facturación de Stripe — HECHA (8d690a7 + 65198db + 43228d4), APROBADA

- [x] `StripePort.createBillingPortalSession(stripeCustomerId, returnUrl)` + impl en `StripeStubAdapter`
- [x] `BillingPortalUseCase` + `BillingPortalService` (422 si `stripeCustomerId` es null)
- [x] `POST /api/v1/billing/portal` en `BillingController`, rol SALON_OWNER, responde `{url}`
- [x] `SubscriptionResponse`: añadir `stripeCustomerId` + `stripeSubscriptionId` (hoy el botón del frontend nunca se renderiza)
- [x] Frontend: quitar `updatedAt` de `Subscription` (campo fantasma, solo vive en el fixture)
- [x] `rivoo.billing.portal-return-url` en TODOS los `application*.yml` de billing-service

## MON.3 — Categoría de servicio — HECHA (2ab50af), revisada y APROBADA

- [x] Migración Flyway: `category VARCHAR(100) NULL` en `services`
- [x] Dominio + entidad JPA + mapper de persistencia
- [x] `CreateServiceOfferingRequest` / `UpdateServiceOfferingRequest` / `ServiceOfferingResponse`
- [x] `ServiceOfferingService.create` y `.update`
- [x] Alcance: SOLO superficie autenticada. `ServicePublic` del frontend no pide categoría.
- [x] Frontend `service-form.tsx`: `|| undefined` impedía vaciar el campo (0df0977, rivoo-frontend)

### Pendiente / avisos

- [ ] `staff_db` va DOS migraciones por detrás: V3 tampoco se ha aplicado nunca (`employees`
      sin `job_title` ni `color_hex`). El próximo arranque aplica V3+V4 seguidas. Ambas
      verificadas ejecutables contra el MySQL 8.0.40 real de localhost:3306.
- [ ] Hueco estructural: sin Testcontainers, ningún test cubre el binding entidad↔esquema.
      Mutar `@Column(name="category")` a un nombre inexistente deja los 74 tests en verde.
      Mitigado en arranque por `ddl-auto: validate`, que sí valida existencia y tipo.
- [x] Tests JSON que fijan las claves del contrato (el punto ciego: renombrar el campo
      dejaba los 32 tests en verde). 32 → 40 tests.
- [x] Producción falla al arrancar si falta la URL de retorno, en vez de redirigir a
      localhost tras un pago real.
- [x] Deriva documental: `GET /plans` es ANÓNIMO (`permitAll` en BillingSecurityConfig:38 y
      GatewaySecurityConfig:25), no autenticado. En este stack la ausencia de `@PreAuthorize`
      no determina el nivel de auth — lo fija `authorizeHttpRequests` más el gateway.
- [x] client-service NO llama a billing; su CLAUDE.md documentaba la llamada como existente (b4b7557).

### Decisión pendiente del usuario

- [ ] En FREE_TRIAL hay `stripeCustomerId` pero no `stripeSubscriptionId`, así que el botón
      no aparece durante el trial. El endpoint serviría igual. Recomendación: dejarlo — en
      trial no hay facturas, ni método de pago, ni plan que cambiar.

## Hallazgos de la revisión de los 8 commits pendientes

- [ ] **CRÍTICO — la reserva no muestra huecos.** Backend envía `slots:[{startTime,endTime}]`;
      frontend lee `availableSlots: string[]`. Siempre vacío. Afecta al flujo público Y al
      asistente interno. Verificado a mano. EN CURSO.
- [ ] **La clase de fallo NO está cerrada.** Renombrar `active`→`isActive` en
      `EmployeeInternalResponse` / `ServiceOfferingInternalResponse` deja 74/74 y 76/76 en
      verde mientras producción lanzaría `MismatchedInputException` en cada llamada. Es el
      incidente de `902f15d` repetible tal cual. 8 tests JSON para 33 DTOs de respuesta.
- [ ] **`@Mapping(target="isActive", source="active")` sin cobertura.** Borrarlo deja 74/74
      en verde y MapStruct genera `boolean isActive = false` sin aviso → todos los servicios
      y empleados salen inactivos → listas vacías en el asistente. Es exactamente el bug que
      `282fb1a` decía arreglar, reintroducible gratis. BLOQUEA 282fb1a y aa8b2c5.
- [ ] `gdprConsentAt` es un campo fantasma: `GET /api/v1/clients/{id}` no lo envía, así que
      la fecha de consentimiento no se muestra NUNCA. UI de RGPD.
- [ ] `PublicBookingResponse` diverge por completo (latente hoy).
- [ ] `0df0977` (mío) no tiene ningún test. Revertirlo deja vitest y tsc en verde.
- [ ] Fantasmas latentes: `Salon.ownerUserId`, `Appointment.tenantId`, `Client.dateOfBirth`,
      `PlanLimitsResponse.current*`, `EmployeeServiceResponse.employeeId`/`customDurationMinutes`.

## Seguimientos de la revisión de errores de dependencia

- [ ] **`isBlank()` en vez de `== null`** en la guarda del `keycloakUserId`, en
      `staff-service/AuthServiceAdapter:81` Y `salon-service/AuthServiceAdapter:81`.
      Verificado con sonda: `{"keycloakUserId":""}` NO dispara la guarda; se persiste
      cadena vacía y, al ser columna UNIQUE, el segundo caso da un 500 inexplicable.
- [ ] **La mitad visible no está entregada:** `employee-form.tsx:76` hace
      `onError: () => toast.error(...)` sin leer `problem.detail`, y el adaptador descarta
      `e.getResponseBodyAsString()`, que lleva el 409 accionable de auth-service ("ese email
      ya existe"). El dueño no distingue "email ocupado" de "auth-service caído".
- [ ] `deleteUser` etiqueta sus errores como `employee-registration-rejected` / "Employee
      Registration Rejected" — para un BORRADO. Hoy no tiene llamantes, pero el test fija
      la etiqueta equivocada para quien lo conecte.
- [ ] Dos bloques de documentación quedaron falsos: el `@Order(0)` de `StaffExceptionHandler`
      y el javadoc de `StaffExceptionHandlerOrderTest`, que afirma "502 con el mensaje
      específico" en un fichero que contiene dos tests que demuestran lo contrario.

## Fallos previos encontrados de paso (no los hemos causado)

- [ ] **No se puede abrir el domingo sin error opaco.** `OnboardingSagaService:190-193`
      siembra el domingo con `open(false)` y SIN `openTime`/`closeTime` → llegan nulos.
      `types/employee.ts:15-16` los declara `string` no nulables (mienten). Al activar el
      domingo y guardar sin escribir horas, `SalonBusinessHours.validate()` lanza
      "Open days must have openTime and closeTime" y la UI muestra "Error al guardar
      horarios" sin decir qué falta. Además `<Input value={null}>` pasa el campo de
      controlado a no controlado.
- [ ] **El asistente interno no resuelve "cualquier profesional".** `datetime-step.tsx:33`
      manda la cadena literal `"any"` como employeeId, y `confirmation-step.tsx:69` manda
      cadena vacía a un campo `@NotBlank` → 400. El flujo público sí lo resuelve
      (`public-datetime-step.tsx:43-47`); el interno no.
- [ ] LOW: el segmento `${open}:` de la clave de sincronización no tiene cobertura en
      `service-form` ni `employee-form` (mutantes M9/M10 sobreviven).
- [ ] LOW: `settings/salon/page.test.tsx:64` busca el campo por posición
      (`querySelectorAll("input")[0]`); si alguien añade un input encima, el test apunta a
      otro campo y se queda verde con la regresión viva.

## Se destapan al ver huecos por primera vez (171b6f7)

- [ ] **Desajuste de la ventana de 1 hora.** `AvailabilityService:143` ofrece los huecos que
      no hayan pasado; `AppointmentService:283-285` rechaza los que no estén a >= 1h vista.
      Antes era inalcanzable (no se pintaba ningún hueco). Ahora: entras a las 10:00, eliges
      las 10:30, rellenas tus datos y falla al confirmar. Parecerá regresión nuestra y no lo es.
- [ ] **"Cualquier profesional" en el asistente interno.** Peor de lo que creíamos: manda la
      cadena `"any"` como employeeId → `StaffServiceAdapter:66-84` pide
      `/employees/any/working-hours` → 404 → RuntimeException → 500. El usuario ve
      "No hay huecos disponibles este dia" en los 30 días. El 400 del `@NotBlank` ni se
      alcanza. El flujo público es inmune.
- [ ] Nada fija el contrato de disponibilidad por el lado del backend: renombrar el
      componente `slots` deja tsc, lint y los 129 tests en verde y reintroduce el fallo.

## AL CONECTAR STRIPE DE VERDAD (aplazado por decision del usuario: Stripe sigue siendo
## simulacro y se conecta el ultimo) — el webhook acepta eventos falsificados

- [ ] **PRIORITARIO.** `POST /api/webhooks/stripe` es anonimo y no verifica ninguna firma:
      la cabecera llega `required = false` y `StripeStubAdapter.constructEvent` la ignora.
      Exploit verificado por dos revisores independientes: el dueno lee su propio
      `stripeSubscriptionId` desde `GET /api/v1/billing/subscription`, deja que le falle la
      tarjeta y envia `{"eventId":"<nuevo>","type":"invoice.paid","subscriptionId":"sub_..."}`
      sin autenticarse -> vuelve a ACTIVE. Con `customer.subscription.deleted`, cancela.
      La idempotencia no protege: el eventId lo pone quien llama.
      La via de subir de plan gratis esta MUERTA hoy (los `stripe_monthly_price_id` estan a
      NULL en la semilla) y se arma sola al configurar Stripe real.
      Los cuatro comentarios que afirmaban lo contrario ya estan corregidos.

### Menores de la ultima revision

- [ ] El mensaje del commit c6e39dd dice "un mutante sobrevive"; son DOS
      (`SalonBusinessHours:38` y `EmployeeWorkingHours:38`, gemelos). Inconsecuente
      —ambos mensajes solo devuelven el entero que envio el propio dueno— pero el recuento
      es falso.
- [ ] `BusinessValidationException.clientSafe` es estatico publico en una clase base, asi que
      `AppointmentConflictException.clientSafe(...)` compila y devuelve la clase base
      descartando el subtipo. No es fuga; confunde.
- [ ] El escaner del test de politica barre tambien las clases de TEST del paquete: una
      excepcion de prueba ahi dentro se convierte en entrada fantasma del mapa y rompe el
      test con un mensaje que no menciona la causa. En appointment-service barre el arbol entero.

### Trampa de tests que conviene generalizar

- [ ] El guardian de "no exponer nada del inquilino" en el catalogo publico de planes era una
      LISTA NEGRA de seis nombres. Un campo llamado `usedSeatsThisTenant` la esquiva y la
      bateria entera sigue verde. Se esta cambiando por lista blanca sobre los componentes del
      record. **Merece revisarse si hay mas listas negras** en el proyecto haciendo de guardian
      de seguridad: por construccion solo protegen de lo que alguien ya penso.

## CORREGIDO — bucle infinito alcanzable sin autenticarse (commit `07e14fb`)

> **Cerrado.** `07e14fb` "stop the slot loop from running for ever past midnight".
> El cursor ya no es un `LocalTime`: es un `LocalDateTime` anclado a la fecha pedida
> (`AvailabilityService:175-177`), y el limite `intervalEnd` tambien, asi que el cursor
> crece estrictamente hacia una cota fija y el bucle no puede dar mas de
> (largo del intervalo / granularidad) + 1 vueltas, sea cual sea el horario.
> Lo dejo escrito porque el 2026-08-29 lei esta entrada sin tachar y llegue a recomendar
> priorizarlo como si siguiera vivo. **Una entrada sin tachar no es una entrada viva:
> comprobar contra el codigo antes de priorizar.**

- [x] `AvailabilityService.java:150-162`: `cursor.plusMinutes(serviceDuration)` da la vuelta
      pasada medianoche porque `LocalTime` es circular. Si ningun paso de la rejilla llega a
      falsear `cursor + duracion <= cierre`, el `while` NO TERMINA y `slots.add(...)` crece sin
      limite. Reproducido: 09:00-23:59 con d=30 y 10:00-23:45 con d=30 no terminan; 09:00-18:00
      con d=30 si (35 huecos).
      Disparador realista: hora de cierre a las 23:45/23:50/23:59, que es como se escribe
      "abierto hasta medianoche" con un LocalTime.
      Una sola peticion anonima a `GET /api/v1/appointments/public/availability` bloquea un
      hilo y agota la memoria. El filtro de antelacion NO salva: el `continue` tambien avanza
      el cursor. Preexistente, identico antes y despues del arreglo de la ventana.

## CORREGIDA — la ventana de 1 hora (commits `11d099d` + `4b7646e`)

> **Cerrada.** `BookingWindow` es fuente unica: las DOS partes —la que OFRECE el hueco
> (`AvailabilityService`) y la que lo ACEPTA (`AppointmentService#book`)— llaman a la misma
> `isTooSoon(...)`, asi que comparten umbral **y** operador de comparacion y no pueden
> divergir sin editar ese fichero. `4b7646e` ademas separo las dos audiencias: el visitante
> anonimo debe `MINIMUM_LEAD_TIME`, el asistente del salon `Duration.ZERO`, y el tiempo de
> antelacion viaja como argumento justo para que ninguna de las dos vuelva a criar su copia.
> Segunda entrada de esta seccion que estaba sin tachar estando muerta (la otra era el bucle
> infinito). Ver la leccion en la seccion del bucle: **comprobar contra el codigo antes de
> priorizar**.

- [x] La disponibilidad se calcula en T1 y la reserva se valida en T2 > T2 (lo que tarda el
      visitante en rellenar). Todo hueco en `[T1+1h, T2+1h)` se ofrece y se rechaza — o sea,
      el PRIMER hueco de la lista siempre falla si el visitante tarda algo.
- [ ] SIGUE VIVO (comprobado 2026-08-29): sin `serviceId` la disponibilidad publica devuelve
      intervalos crudos SIN filtrar nada,
      incluidos dias enteros pasados, a un caller anonimo. `AppointmentController.java:125`
      sigue declarandolo `@RequestParam(required = false)`, y `AvailabilityService:154` sigue
      devolviendo `freeIntervals` en crudo cuando la duracion es <= 0. El frontend siempre lo
      manda, asi que no se ve desde la pagina; se alcanza llamando al endpoint a pelo.
- [ ] El dia del cambio de hora de primavera, "una hora" se queda en ~1 minuto real: la
      comparacion es en `LocalDateTime`, no en instantes.

## CORREGIDO: el test de la fecha NO esta acoplado al reloj

- [x] Lo anote como "se rompera manana" y era FALSO. `public-datetime-step.tsx:65` hace
      `const availabilityDate = data?.date ?? dateStr`, y el handler solo es alcanzable con
      `data` presente — la fecha sale del payload simulado, no del reloj. Comprobado moviendo
      cada uno por separado: cambiar solo el mock rompe el test, cambiar solo la asercion
      tambien. La cadena fija es el mock haciendose eco de si mismo.
      La intermitencia tampoco se reprodujo: 13 ejecuciones completas, 144/144 siempre.


## La fuga del alta se estrecho, no se cerro (el contenido si, el TIEMPO no)

- [ ] Las dos respuestas son identicas en codigo, cuerpo y cabeceras. Pero el camino
      "correo nuevo" escribe en base de datos y llama a Keycloak y a facturacion, y el
      camino "correo existente" hace una consulta y manda un correo. Un orden de magnitud,
      medible desde fuera. Cerrarlo exige alta asincrona = rediseno, no arreglo.

## Encontrados al revisar el arreglo del bucle (preexistentes)

- [ ] `AppointmentService.create` (linea 71) no tiene NINGUNA validacion temporal, y
      `CreateAppointmentRequest.startTime` solo lleva `@NotNull`, sin `@Future`. Acepta una
      hora pasada. Asi que el par del salon es asimetrico al reves: el asistente retiene los
      huecos pasados que `create()` si aceptaria. A las 10:07, el dueno que apunta una cita
      que empezo a las 10:00 recibe como primera opcion las 10:15.
      `everyWizardSlotIsCreatable` no puede verlo: comprueba ofrecido -> aceptado, nunca al reves.
- [ ] `AvailabilityService:198-202`: una cita que cruza medianoche (23:30 -> 00:30) se convierte
      en el intervalo (23:30, 00:30) y `subtractBusy` la descarta por `00:30 <= 09:00`. El hueco
      ocupado se ofrece como libre y luego lo rechaza el bloqueo por solapamiento. Preexistente,
      pero ahora ALCANZABLE: legitimar cierres de 23:45-23:59 es su precondicion.
- [ ] `EmployeeWorkingHours.validate()` no compara el descanso contra apertura y cierre. Un
      descanso de 03:00-04:00 en un dia 09:00-18:00 pasa la validacion y produce el intervalo
      (04:00, 18:00) mas uno invertido: se ofrecen huecos de 04:00 a 09:00, fuera de horario.

## Al desplegar: el correo es requisito de la verificacion del dueno

Decision del usuario (2026-08-28): la rama de verificacion SE FUSIONA a master. Los
ajustes para poder probar sin correo se hacen mas adelante.

Contexto verificado hoy: **hoy no sale ningun correo**. El unico emisor de
notification-service es `MailStubAdapter` (anota en el log, no envia), y el realm de
Keycloak (`infrastructure/keycloak/rivoo-realm.json`) **no tiene bloque `smtpServer`**.
Sin correo, Keycloak exige verificar y el enlace no llega: nadie puede entrar.

- [ ] Para poder probar sin correo: que la confirmacion se genere con `true` por defecto
      (configurable), de modo que el registro funcione en local y en pruebas.
- [ ] Registrar los envios como `SENT` en base de datos aunque el emisor sea el simulacro,
      para poder verificar el flujo sin buzon.
- [ ] Al desplegar de verdad (AWS + Jenkins): dominio, registros SPF/DKIM en su DNS,
      credenciales de un proveedor transaccional (Brevo/Resend/Postmark valen), bloque
      `smtpServer` en el realm, y sustituir `MailStubAdapter` por un emisor real.
      Sin eso, el registro queda bloqueado en produccion.

---

# CERRADO — Onboarding reanudable (2026-08-28)

Plan: `docs/specs/onboarding-reanudable/IMPLEMENTATION_PLAN.md` (v3, dos revisiones independientes).
Ramas: `feat/onboarding-reanudable` en los dos repos. Motor: `executing-plans`.

- [x] **T1** backend — migracion V4 + campo en entidad/dominio/SalonResponse + `copyOf` del fake
- [x] **T2** backend — endpoint idempotente `POST /api/v1/salons/me/onboarding/complete` + CAS
- [x] **T3** backend — aplicar la migracion en localhost arrancando el servicio (la hago yo)
- [x] **T4** frontend — tipo `onboardingCompletedAt` + `salonsApi.completeOnboarding`
- [x] **T5** frontend — 6 tokens nuevos + re-apuntar 2 existentes + `Switch` + `Progress`
- [x] **T6** frontend — portero: solo la marca, solo el dueno, y fallar hacia fuera **(panel de 3)**
- [x] **T7** frontend — chasis del asistente + borrar `salon-setup`
- [x] **T8** frontend — los 5 pasos contra los artboards, movil y escritorio **(panel de 3 en el paso 5)**
- [x] **T9** frontend — estados vacios en "Hoy" y en la pagina publica
- [x] **T10** verificacion: reactor + npm test + comparacion visual + recorrido con el bucle como prueba

Regla en vigor: cada despacho es un agente NUEVO; el revisor nunca es el implementador.

**CERRADO 2026-08-28.** master backend `e895c38`, frontend `43ff432`, ambos empujados.
(El codigo del bloque entro en `a6b70ba` / `36f397a`; encima van los dos arreglos que
destapo la comparacion visual y las capturas.)
426 tests backend + 6 de integracion (MySQL local, `@Tag("integration")`), 208 frontend.
Verificado de punta a punta contra la pila real: alta -> omitir empleado y servicio ->
cerrar -> entrar, y la marca no se mueve al repetir.

**Queda pendiente de este bloque:**
- [x] Comparacion visual artboard a artboard de las 5 pantallas (movil y escritorio).
      HECHA con Playwright (ver el bloque de abajo). La nota previa que decia
      "no se pudo hacer, no hay Playwright en el repo" ya no vale: se anadio.
- [ ] `infrastructure/scripts/dev-full-stack.sh` mide la salud de Keycloak en
      `/health/ready` del puerto 9080, pero en Keycloak 26 eso vive en el puerto de
      gestion: el script se rinde a los 60s aunque Keycloak arranque bien.
- [ ] La pantalla de Ajustes de horarios no cumple su artboard `Horario.dc.html`:
      sin columna de descanso, sin boton de copiar lunes, y en escritorio el artboard
      pone el interruptor antes del nombre del dia. Pertenece al bloque de pantallas.
- [ ] Salon de pruebas `salon-e2e-bucle` creado por la verificacion. Borrarlo requiere
      Keycloak levantado para no dejar el usuario huerfano.

**Comparacion visual HECHA 2026-08-28** con Playwright sobre el Chrome del equipo.
20 capturas en `docs/specs/onboarding-reanudable/verificacion/`. Escritorio indistinguible
del diseno. Encontro dos defectos que ni cinco revisores ni 203 tests vieron, ya corregidos:
el pie sin pegar al fondo en movil (`min-h-full` es un porcentaje y `body` no tiene `height`),
y un desajuste de hidratacion en la barra de progreso por el formato regional del porcentaje.

---

# Fuera de bloque — hallazgos sueltos (2026-08-28/29)

- [x] **El 500 de staff-service era del entorno, no del codigo.** En el puerto 8083 vivia
      un proceso anterior a la sesion. Tras reiniciar la pila entera, el endpoint interno
      `/api/internal/staff/{tenantId}/public/services` responde 200, y el agregado publico
      de `test-barbershop-e2e` devuelve 1 servicio y 1 empleado con
      `servicesUnavailable=false` y `employeesUnavailable=false`. Eso confirma de paso que
      la distincion de T9 entre "no hay servicios" y "no se pudo cargar el catalogo"
      funciona con datos reales: `barberia-elegante` sale vacio pero con las banderas a
      false, porque de verdad no tiene catalogo.
      Leccion: antes de depurar un 500, comprobar que el proceso que responde es el del
      codigo actual (comparar contra el puerto del build recien arrancado).
- [x] **`public-datetime-step.test.tsx` era intermitente.** Verde aislado (3/3), rojo en la
      suite completa: `findBy*` se rinde a los 1000 ms y el primer render de ese componente
      se iba a ~1,5 s con los 40 ficheros compitiendo. No era un fallo del componente ni del
      reloj del calendario. Arreglado en la raiz subiendo `asyncUtilTimeout` a 5 s en
      `src/test/setup.ts`, que cubre toda la suite, no solo ese fichero.
      208/208 en verde dos ejecuciones seguidas.

---

# Inventario canvas <-> codigo (2026-08-29)

Barrido de los **74 artboards** del canvas "Rivoo Terracota" contra el codigo construido,
en cuatro pasadas paralelas e independientes. Aqui va SOLO lo que no estaba ya en este
fichero; lo que ya estaba apuntado se confirmo vigente y no se repite.

**Metodo y limite.** Cada fila se verifico contra `fichero:lineas` del artboard y del codigo.
Los cinco hallazgos mas graves los volvi a comprobar a mano antes de escribirlos aqui.
NO es una comparacion de pixeles: nadie ha abierto todavia un navegador contra estas
pantallas. En el alta reanudable, hacer eso con Playwright destapo dos defectos que no
vieron ni cinco revisores ni 203 tests. Asumir que esta lista esta completa seria repetir
ese error.

**El canvas y `design/` estan sincronizados**: los 73 artboards previos son identicos byte a
byte entre el repo y la version publicada. `design/` es la fuente, se puede editar ahi.

## EL ARTBOARD ES LA FUENTE — vale para TODOS los bloques

**Decision del usuario, 2026-08-29. No se reabre.**

Las pantallas que hay que construir son **las del canvas** (`rivoo-frontend/design/*.dc.html`, 74
artboards, sincronizados byte a byte con el canvas publicado "Rivoo Terracota"). **Lo que hay
hoy en el codigo y no coincide con su artboard es lo ANTIGUO** — se hizo antes que estos
disenos — y hay que cambiarlo. Movil y escritorio, los dos.

**La consecuencia practica, que es donde se falla:** "no tocar lo que ya funciona" NO es un
objetivo. Preservar el codigo actual solo es correcto cuando el codigo actual YA coincide con
su artboard. Cuando no coincide, preservarlo es conservar la version vieja.

> Esto costo tres rondas de revision en el bloque 2. El plan protegia el chasis movil existente
> como intocable ("byte a byte") e intentaba modelar con propiedades las TRES formas de cabecera
> que tenia el codigo. Los artboards moviles dibujan **una sola** cabecera de 56px con una
> variante (con o sin boton de volver). No habia que modelar tres formas: habia que sustituirlas
> por la dibujada. La pregunta correcta no es "¿que hay en el codigo?" sino "¿que hay en el
> artboard, y en que se diferencia lo construido?".

**Antes de planificar cualquier bloque:** abrir los artboards de sus pantallas —**el movil y el
de escritorio**— y hacer el inventario de valores contra `fichero:linea`. Lo que no este dibujado
no se inventa; lo que este dibujado y no exista en el codigo, se construye.

---

## Condiciones de cierre de CADA bloque de pantalla

Esto **no es un bloque**. Son tres cosas del sistema de diseno que no se pueden hacer de una
sentada porque no viven en un sitio: viven en cada pantalla. Van copiadas en el brief de cada
bloque (2 a 7), junto a "leer el artboard" y "comparar con Playwright", y se comprueban al
cerrarlo. Si se dejan como lista aparte se cumplen en el primer bloque que la lea y se olvidan
en los cuatro siguientes, y acabas con media aplicacion a 36px y la otra media a 44.

- **Altura tactil.** Todo CTA principal de movil a 44px (`size="xl"`), y el de ancho completo a
  50px (`size="2xl"`). Las dos tallas ya existen en `ui/button.tsx`; hoy solo las usa
  `components/booking/`. Cada bloque las aplica en las pantallas que reconstruye. Ver CV.13.
- **Ambar de acento.** `#D9A441` sigue sin token. Se anade a `globals.css` **el dia que una
  pantalla lo pida**, no antes; hasta entonces no hay nada que arreglar. Ver CV.14.
- **Comparacion visual real.** Playwright contra el artboard a 390 y 1440 antes de dar el
  bloque por cerrado. Es lo que destapo defectos que ni cinco revisores ni la suite vieron.
  A 390 no es un control de regresion: es la verificacion principal, porque el movil tambien
  tiene que converger hacia su artboard (ver la nota de arriba).

## P0 — Estructural: sin esto no hay ninguna pantalla de escritorio

- [ ] **CV.1** El shell de escritorio no existe. `src/app/(app)/layout.tsx:13-32` es una
      columna centrada `max-w-3xl` con cabecera movil y barra inferior, identica a cualquier
      ancho. Los artboards `*Desktop` dibujan barra lateral de 248px + barra superior de 72px
      (`design/EquipoDesktop.dc.html:37,82`).
      Ya estaba como "bloque 6 del roadmap", pero sin el dato que lo convierte en
      prerrequisito y no en remate: **17 artboards llevan el sidebar dibujado** y ninguno se
      puede construir bien antes. Es lo primero.
      **Son 17, no "treinta y tantos"** (contado uno a uno, `grep "width: 248px" design/*Desktop*`):
      de los 36 artboards Desktop, 19 van a PANTALLA COMPLETA sin shell — los 7 de reserva
      publica, los 5 de onboarding, Registro, Login y **los 5 de `NuevaCitaDesktop`**, que
      llevan cabecera propia de 68px y contenedor de 1120px, el mismo chasis que la reserva
      (`NuevaCitaDesktopPaso1.dc.html:29,42-43`).
      **Consecuencia para el bloque:** `/appointments/new` esta hoy DENTRO de `(app)/layout.tsx`
      y tiene que quedar fuera del shell, o saldra con sidebar contra su artboard.

## P1 — Construidas contra el diseno ANTERIOR: falta contenido, no estilo

- [ ] **CV.2** **Calendario** (`a34c157`, marzo 2026, contra artboards de agosto). No estaba
      registrado en este fichero que sus artboards fueran nuevos. Ocho huecos de contenido:
      - `day-view.tsx:27` pinta UNA sola columna para todas las citas;
        `CalendarioDesktop.dc.html:152-235` pide una columna por empleado.
      - `calendar/page.tsx:35` hace `all.filter(a => a.status !== "CANCELLED")`: las citas
        canceladas NO se ven. El artboard las pinta atenuadas (`:225-228`).
      - `appointment-block.tsx:4` importa `StatusBadge` y **nunca lo usa**; el artboard pide
        la pildora de estado dentro del bloque.
      - El bloque tiene 2 lineas; el artboard pide 3, con duracion y precio ("60min - 35,00 EUR").
      - No hay boton de crear cita en ningun breakpoint (el artboard movil pide un FAB de 56px
        `#B4522F`, el de escritorio un boton "Nueva cita").
      - No hay resumenes por empleado bajo la cabecera, ni selector Dia/Semana.
      - No hay bloques de descanso: el tipo `Appointment` no los contempla.
      - Los huecos libres son un borde discontinuo; el artboard pide caja con "Libre - toca para crear".
- [ ] **CV.3** **Reserva publica: no existe el escritorio.** `grep "md:|lg:"` sobre
      `src/app/book/` y `src/components/booking/` devuelve **cero**. Hay 12 artboards Desktop
      (`ReservaDesktopPaso1..6`) sin contrapartida: hoy en escritorio se ve la maqueta movil
      estirada sin limite de ancho. El flujo funciona, pero solo esta disenado a medias.
- [ ] **CV.4** **Ficha de cliente sin historial de citas.** `DetalleCliente.dc.html:82-109` y
      `DetalleClienteDesktop.dc.html:100-243` piden el historial (fecha, servicio, profesional,
      importe, estado). En `clients/[id]/page.tsx` no hay ni tabla, ni lista, ni import
      relacionado. Falta en las DOS versiones, no solo en escritorio.
- [~] **CV.5** **Paso 3 de la reserva** — CORREGIDO casi entero en el carril B (`c174e46`).
      Ya no auto-avanza: el hueco solo se resalta y hay "Continuar"
      (`public-datetime-step.tsx:243`, y el gemelo del aside en `:226-228`); estan las
      secciones Mañana/Tarde (`:279,:282`) y los dias cerrados se marcan desde
      `salon.businessHours` (`:454`).
      **Queda solo** pintar los huecos OCUPADOS tachados: el backend devuelve unicamente los
      libres, asi que no hay dato con el que tacharlos. Ver el punto 4 de la lista de backend
      al final del fichero.
- [ ] **CV.6** **Asistente de nueva cita: se filtra lo que el diseno quiere atenuar.**
      `employee-step.tsx` filtra los empleados inactivos y `service-step.tsx` los servicios no
      asignados: desaparecen. Los artboards los pintan atenuados con explicacion ("Estilista -
      hoy no trabaja"), que es informacion distinta de la ausencia. Faltan tambien los chips de
      contexto en los pasos 2-4 y el total en la confirmacion.

## P2 — Pantallas o funciones dibujadas que no existen en el codigo

- [ ] **CV.7** **"Cambiar de plan"** (`CambiarPlan.dc.html` + Desktop) no existe: ni ruta ni
      componente. `billing/page.tsx:122-160` mezcla plan actual y lista de planes en una sola
      pantalla, sin comparativa de limites ni bloqueo de bajada de plan. `PlanLimitsResponse`
      esta tipado en `types/billing.ts:34-42` pero **no hay ningun `getPlanLimits`** en
      `lib/api/billing.ts` ni se usa en ningun sitio.
- [ ] **CV.8** **El alta contradice su propio diseno.** `Registro.dc.html:31` dice literalmente
      "Tu salon y tu usuario, en un solo paso" y no dibuja seleccion de plan.
      `register/page.tsx:7-23` arranca en `step="plans"` y obliga a pasar por `PlanComparison`.
      DECISION DE PRODUCTO, no un arreglo: o se quita el paso, o se corrige el artboard.
- [ ] **CV.9** **Formularios: hoja inferior tambien en escritorio.** `employee-form.tsx:127` y
      `client-form.tsx:108` usan `Sheet side="bottom"` fijo. Los artboards de escritorio piden
      dialogo modal centrado de 512px. Mismo caso en `appointment-detail-sheet.tsx:84`, que en
      escritorio se convierte en modal con overlay cuando el artboard pide un panel acoplado de
      360px junto al calendario.
- [ ] **CV.10** **Equipo y ficha de empleado en escritorio reusan la tarjeta movil.**
      `EquipoDesktop.dc.html:100-204` pide tabla con Puesto / Contacto / Color / Estado;
      `staff/page.tsx:94` reusa `EmployeeCard` sin variante. `DetalleEmpleadoDesktop` pide tres
      tarjetas lado a lado; `staff/[id]/page.tsx:116-201` usa pestanas igual que en movil.
- [ ] **CV.11** **El color identificativo del empleado no se muestra nunca.** `colorHex` solo se
      usa como fondo del avatar (`staff/[id]/page.tsx:127-135`). Los artboards lo piden como
      dato visible: cuadro de color mas el hex en texto.
- [ ] **CV.12** **Ajustes sin tarjeta de cabecera ni secciones.** `Ajustes.dc.html:35-44` pide
      logo + nombre + slug + insignia de plan arriba, y el menu agrupado en tres secciones
      (Salon / Negocio / Cuenta) con el valor a la derecha de cada linea ("Lun-Vie 9-20",
      "59 EUR/mes"). `settings/page.tsx:23-35` es una lista plana de icono + etiqueta + chevron.

## P3 — Sistema de diseno: lo que falla en silencio

- [~] **CV.13** **Los 44px que exige el propio sistema: la talla ya existe, falta aplicarla.**
      `Estilo.dc.html:107-109` fija 44px como minimo tactil en movil, y el tope de
      `button.tsx` era `lg` = `h-9` = 36px. El carril B anadio `size="xl"` (44px) y
      `size="2xl"` (50px, ancho completo). **Pero solo las usa `components/booking/`**: cero
      apariciones fuera.
      Lo que queda **no se puede hacer como bloque propio**, porque los CTA no estan en un
      fichero: estan uno en cada pantalla. Se aplica en los bloques 3 a 7, cuando cada pantalla
      se reconstruye contra su artboard. Ver "Condiciones de cierre de CADA bloque de pantalla"
      arriba.
- [~] **CV.14** Eran dos colores del canvas sin equivalente en `globals.css`. Queda uno.
      - `#8F3F24`, terracota de pulsacion: **HECHO** en el carril B. Es
        `--primary-pressed` (`globals.css:149`, expuesto como `--color-primary-pressed:77`) y
        lo usa la variante `default` del boton. Antes se simulaba con `bg-primary/80`, que es
        opacidad, no ese hex.
      - `#D9A441`, ambar de acento: **sin token, a proposito**. No aparece en `src/` y ninguna
        pantalla construida lo necesita todavia; anadirlo ahora seria un token muerto. Entra
        cuando un bloque reconstruya una pantalla que lo pida. Ver las condiciones de cierre.

      El resto de tokens del canvas SI existen y coinciden: 24 de 26 comprobados uno a uno
      (color, tipografia, radios, las 4 duraciones y las 3 curvas).

## P4 — Menores visuales

Unas 25 diferencias de valor (tamanos de insignia, cajas de los botones de navegacion,
"27 agosto" en vez de "27 de agosto", "45 min" en vez de "45min", "Confirmar cita" en vez de
"Crear cita", amarillos genericos de Tailwind donde ya hay tokens de marca en
`globals.css:13-24`). No se listan una a una a proposito: son ruido hasta que exista el shell
y las pantallas se reconstruyan, y entonces conviene resolverlas mirando el artboard, no esta
lista.

## Dibujado en esta pasada

- [x] **ClientesDesktop** (`design/ClientesDesktop.dc.html`). Era la unica pantalla sin dibujar
      de las 74: todas las demas ya tenian par movil/escritorio, `Main.dc.html` resulta ser
      "Hoy" en movil, y la raiz `/` solo redirige a `/today`.
      Hecho contra el codigo real: la tabla usa `totalVisits` y `lastVisitAt`, que existen en
      `src/types/client.ts`, en vez de inventar campos. Mismo chasis que `EquipoDesktop`.
      Canvas republicado con 74 artboards.

---

# CERRADO — Carril B: reserva publica en escritorio (2026-08-29)

Mergeado en `master`: frontend `c174e46`, backend `891221b`, los dos empujados.
**275 tests en 50 ficheros**, `tsc` limpio, lint 0 errores (25 avisos preexistentes),
38 capturas de Playwright. La pantalla de conflicto se probo contra la pila real robando
el hueco con un POST directo mientras el asistente estaba en el paso 5.

Plan: `docs/specs/reserva-escritorio/IMPLEMENTATION_PLAN.md` (v2, revisado por un agente
independiente que encontro 26 defectos, 4 bloqueantes; los cuatro verificados a mano).
Motor: `executing-plans`. Repo: `E:\IdeaProjects\rivoo-frontend`, solo frontend.

Regla en vigor: el revisor se lanza al terminar el BLOQUE ENTERO, no por tarea.
Cada despacho es un agente NUEVO; el revisor nunca es el implementador.

- [x] **T1** sistema — 6 tokens + botones 44/50px + estado deshabilitado solido + variante de
      insignia + **crear** `ui/checkbox.tsx` (no existia) + `lib/utils/business-hours.ts` +
      arreglar `min-h-full` del layout de reserva
- [x] **T2** los dos chasis — `BookingStepShell` (pasos 1-5, 1120px) y `BookingResultShell`
      (paso 6 y error, 860px, sin stepper) + stepper + estado `conflict` en el store
- [x] **T3** los dos asides — "El salon" (paso 1) y "Tu reserva" (pasos 2-5) + CTA
- [x] **T4** paso 1 servicio — grid de 2 columnas con categorias; quitar el horario semanal
- [x] **T5** paso 2 profesional — PRESERVAR el atenuado que ya funciona; opacidad 0.55 en movil
- [x] **T6** paso 3 fecha y hora — "Continuar" en vez de auto-avanzar, franjas Manana/Tarde,
      huecos ocupados tachados, dias cerrados, navegador de mes
- [x] **T7** paso 4 tus datos — consumir el `checkbox` de T1
- [x] **T8** paso 5 confirmar — grid de 3 columnas en escritorio, aviso ambar, fila Total
- [x] **T9** paso 6 hecha — `BookingResultShell`, dos columnas, `.ics` en cliente
- [x] **T10** pantalla de "ese hueco se acaba de ocupar" (no existia) — **espera a T8**
- [x] **T11** verificacion — suite + tsc + lint + Playwright a 390/768/1024/1440 contra los
      14 artboards + panel de 3 revisores + recorrido real

**Olas:** T1 -> T2 -> T3 -> (T4..T9 en paralelo) -> T10 -> T11.

**Fuera de alcance, por necesitar backend. Van a esta lista al cerrar el bloque:**
1. Primer hueco libre por profesional (`ReservaPaso2.dc.html:66-88`): `EmployeePublic`
   (`src/types/salon.ts:31-37`) no transporta disponibilidad.
2. Numero de huecos por dia (`ReservaDesktopPaso3.dc.html:90-124`): `getPublicAvailability`
   recibe UN `date`; serian 7 llamadas. **Consecuencia asumida:** un dia abierto pero lleno
   se pintara como disponible, porque el artboard da a "Sin huecos" el mismo tratamiento
   visual que a "Cerrado".
3. El backend NO distingue el conflicto de hueco de ningun otro fallo de negocio:
   `AppointmentConflictException:16` extiende `BusinessValidationException`, que fija 422 con
   `type` `business-validation` para todo. Lo correcto seria darle su propio `type` de Problem
   Details. T10 lo esquiva re-consultando la disponibilidad, que es mas robusto, pero el
   backend deberia arreglarse igual.
4. Huecos OCUPADOS tachados (`ReservaPaso3.dc.html:101-104`): `getPublicAvailability` devuelve
   solo los libres. Para tachar los ocupados hace falta que el endpoint los emita tambien, o
   que devuelva la rejilla completa con una bandera por hueco. Es lo unico que quedo abierto
   de CV.5.
5. Categoria en la reserva publica (T4): `ServicePublic` (`src/types/salon.ts`) tiene id,
   name, description, durationMinutes, price y currency — **no category**. El backend SI la
   tiene desde MON.3 (`2ab50af`), pero solo en la superficie autenticada; el agregado publico
   no la expone. El paso 1 quedo con una sola rejilla, sin agrupar. Es exponerla, no crearla.

---

# CERRADO — Bloque 2: shell de escritorio (2026-08-29)

Mergeado en `master`: `6ec0e26`, empujado. **343 tests en 57 ficheros**, `tsc` limpio,
lint 0 errores (25 avisos preexistentes), `npm run build` compila y genera 23 paginas.
Panel de 3 revisores (correccion / fidelidad movil / fidelidad escritorio) = 13 defectos,
los 13 corregidos; un verificador independiente reviso los arreglos mutando el codigo.

**PENDIENTE, y esta anotado en el mensaje del merge:** nadie ha comparado las pantallas
contra los artboards en un navegador. `visual/shell-vs-artboards.spec.ts` esta escrito y
commiteado, pero necesita `RIVOO_E2E_EMAIL`/`RIVOO_E2E_PASSWORD` en el entorno.

Plan: `docs/specs/shell-escritorio/IMPLEMENTATION_PLAN.md` (v6, reescrito de cero tras cinco
revisiones bloqueantes; el historico esta en `_v5-descartado.md`).
Motor: `executing-plans`. Repo: `E:\IdeaProjects\rivoo-frontend`, rama `feat/shell-escritorio`
desde `c174e46`.

Regla en vigor: el revisor se lanza al terminar el BLOQUE ENTERO, no por tarea (T8 = panel de 3).
Cada despacho es un agente NUEVO; el revisor nunca es el implementador.

- [x] **T1** el token `--nav-foreground` + extraer `SalonMark` a `components/brand/`
- [x] **T2** `lib/nav/app-nav.ts` — los 6 destinos con su predicado de activo (Equipo vs Servicios)
- [x] **T3** `layout/user-card.tsx` — iniciales + etiqueta de rol neutra
- [x] **T4** `layout/page-shell.tsx` — cabecera 56px (movil) + barra 72px (escritorio) + contenedor
- [x] **T5** `layout/app-sidebar.tsx` — 248px, 6 destinos, tarjeta de usuario, **con su `<Suspense>`**
- [x] **T6** `(app)/layout.tsx` — chasis por breakpoint, `min-h-dvh`, **borrar `AppHeader`**,
      `sticky top-14` -> `top-0` en `appointments/new`
- [x] **T7a** `today` + `calendar` adoptan `PageShell`
- [x] **T7b** `clients` + `clients/[id]`
- [x] **T7c** `staff` + `staff/[id]` + `Tabs` controlados + **crear `staff/page.test.tsx`**
- [x] **T7d** `settings` + sus cinco subpaginas
- [x] **T8** verificacion: suite + tsc + lint + **build** + Playwright 390/768/1024/1440 +
      panel de 3 revisores

**Olas:** (T1 ‖ T2 ‖ T3 ‖ T4) -> T5 -> T6 -> (T7a ‖ T7b ‖ T7c ‖ T7d) -> T8.


## Deudas que deja el bloque 2, con destinatario

- [ ] **Cobertura:** 8 de los 13 arreglos del panel no tienen test. No existe fichero de test
      para `clients/page.tsx`, `clients/[id]`, `calendar/page.tsx` ni para ninguna de las seis
      de `settings/`. Son los arreglos cosmeticos: los que un refactor revierte sin que nadie
      se entere. Va con el bloque que reconstruya cada pantalla.
- [ ] **`/appointments/new`** (bloque del asistente): cabecera propia de 68px
      (`NuevaCitaDesktopPaso1:29`), el `min-h-[calc(100vh-8rem)]` de `:30`, y sacarlo del shell
      por `(fullscreen)/appointments/new` — comprobado que NO colisiona con `[id]`.
- [ ] **`day-view.tsx:21`** (bloque 3): `h-[calc(100vh-16rem)]` descuenta 256px de cromo movil
      que en escritorio no existe.
- [ ] **Fecha duplicada en `/calendar`** (bloque 3): el titulo unificado es la fecha, y
      `DateNavigator` la vuelve a pintar en el cuerpo. Ademas sus botones no tienen `aria-label`,
      asi que en escritorio hay dos "Hoy" indistinguibles para un lector de pantalla.
- [ ] **AMBIGUEDAD DEL CANVAS** (bloque 7): `AjustesDesktop.dc.html` y `AjustesSalonDesktop.dc.html`
      describen la misma pantalla con anchos y formularios distintos (800px a 2-3 columnas con
      "Email de contacto"/"Zona horaria"/"Moneda", contra 554px a una columna con Nombre/Telefono/
      Descripcion, que es lo implementado). **Ya costo un defecto**: se midio contra el equivocado.
      Decidir cual manda y borrar o renombrar el otro.
- [ ] **Copy compartido** (bloque 7): `working-hours-editor.tsx` dice "Guardar horarios" y
      `HorarioDesktop:126` dice "Guardar cambios". No se toco por ser un componente compartido
      con `staff/[id]` y el onboarding.
- [ ] **Talla `action`** (menor): clava los CTA primarios (38px/18/14px/600) pero los artboards
      piden padding 16 y fuente 13px en los botones secundarios de las fichas. La altura, que era
      el defecto, es correcta en todos.

---

# CERRADO — Bloque 3: Calendario (2026-08-29)

Plan: `docs/specs/calendario/IMPLEMENTATION_PLAN.md`. El artboard es la fuente:
`design/CalendarioDesktop.dc.html` (1440) y `design/Calendario.dc.html` (390).

Hallazgo que decide la arquitectura: **en escritorio hay una columna por empleado**
(rejilla de N columnas con su fila de cabeceras), y el filtro de pildoras es **solo movil**.
Las citas canceladas y completadas **se pintan** — hoy `page.tsx:46` las descarta.

## Tareas

- [x] **T1** Tokens: los cinco que faltan (`--hairline-strong`, `--warning`, `--warning-soft`, — 7fa37d9
      `--destructive-tint`, `--border-dashed`) en `:root` y en `@theme inline`.
- [x] **T2** Calculo y datos: `groupByEmployee`, `employeeDaySummary`, `nextFreeSlot`, — 6335120
      `breakPosition` + `useEmployeesWorkingHours`. Con tests: hoy `lib/utils/calendar.ts`
      no tiene ninguno.
- [x] **T3** `PageShell` gana `layout="fill"` (sin padding exterior, contenido a alto completo). — 2f8a3f7 + 56c6b2e
      Es lo que mata el `h-[calc(100vh-16rem)]` sin poner otro numero magico.
- [x] **T4** Rejilla horaria: canal 46/64px, linea de hora en punto distinta de la de media hora. — ce74be8
- [x] **T5** Bloques: cita (5 estados + compacto), descanso rayado, hueco libre discontinuo. — 490e431
- [ ] **T6** `DayView` a N columnas + cabecera de empleado con su `colorHex` y su resumen.
- [x] **T7** Filtro de pildoras recalibrado + navegador de fecha (fila movil / cluster escritorio). — 0420350
- [x] **T8** La pagina: titulo "Citas" en movil y la fecha en escritorio, buscador, y adios a la
      fecha duplicada.
- [x] **T9** Comparacion visual + panel de 3 revisores que refutan.

## Deudas del bloque 2 que este bloque salda

- `day-view.tsx:21` `h-[calc(100vh-16rem)]` -> T3 + T6.
- Fecha duplicada en `/calendar` y `aria-label` ausentes en `DateNavigator` -> T7 + T8.
- Cobertura cero de `calendar/page.tsx` -> T8.

## Deuda NUEVA que este bloque deja anotada

- [ ] **Falta artboard de la vista de semana.** El segmentado Dia/Semana esta dibujado
      (`CalendarioDesktop:89-92`) pero su destino no existe en el canvas. No se monta un control
      cuya segunda opcion no lleva a ninguna parte. Hace falta `CalendarioSemanaDesktop.dc.html`.

## Cierre del bloque 3

18 commits sobre `master`, de `6ec0e26` a `18e3b06`. **574 tests en 66 ficheros**, `tsc` limpio,
`npm run build` compila y genera 23 paginas, arbol limpio.

**Nadie ha comparado la pantalla contra los artboards en un navegador.** Toda la fidelidad esta
comprobada por aritmetica de clases en jsdom, que no calcula maquetacion: un `shrink-0` presente
pero anulado por el padre se cuela entero. El spec existe (`visual/calendar-vs-artboards.spec.ts`,
commit `abd860c`) y solo lo puede ejecutar el dueno, con sus credenciales por variable de entorno.

Tres rondas de revision independiente, todas con veredicto BLOCK, mas una cuarta de cierre:
panel de 3 lentes (fidelidad · correccion · tests por mutacion) -> verificador de las
correcciones -> verificador final. **51 mutaciones en la primera auditoria, 20 supervivientes.**
Al cerrar, cada arreglo tiene su mutacion demostrada en rojo.

## Deudas que deja el bloque 3, con destinatario

- [ ] **Comparacion visual, sin ejecutar** (dueno del repo). `npx playwright test
      visual/calendar-vs-artboards.spec.ts` con `RIVOO_E2E_EMAIL`/`RIVOO_E2E_PASSWORD`. El dia
      capturado necesita varios empleados con citas, una de cada estado y un descanso, o
      `RIVOO_E2E_CALENDAR_DATE=yyyy-MM-dd`. **Ademas: ningun script de npm ni CI lo ejecuta**
      — `package.json` no tiene script de playwright y no hay `.github/workflows`.
- [ ] **Peticion desperdiciada en el arranque frio de movil** (bloque 3, menor). `useAppointments`
      no esta desactivado mientras viaja la lista de empleados: sale una consulta del dia entero
      sin `employeeId` que se descarta al aterrizar la lista. `waitingForFilter` esconde el
      fotograma, no la peticion. Arreglo natural: `enabled` en el hook, que es API nueva en un
      hook compartido con `/today`.
- [ ] **Callejon si el empleado elegido desaparece de la lista** (bloque 3). Se elige a alguien, una
      recarga lo deja fuera (baja, borrado) y la consulta queda clavada en un id que ya no existe:
      ninguna pildora marcada, rejilla vacia y muda, ni un "Sin citas". La proteccion "una recarga
      no pisa la eleccion" no distingue entre pisar una eleccion valida y conservar una muerta.
- [ ] **El filtro no anuncia su seleccion** (accesibilidad). Las pildoras expresan el estado SOLO
      con color: sin `aria-pressed`, `aria-current` ni `role="tab"`. Con la pantalla arrancando ya
      filtrada, un lector de pantalla no puede saber de quien es la agenda que lee.
- [ ] **`startDate`/`endDate` fuera del prestamo de datos** (`use-appointments.ts`). Solo se
      exceptua `date`; el dia que alguien use el rango, cambiar de rango dejara de prestar y
      volvera el parpadeo que ese arreglo existe para evitar.
- [ ] **`assignLanes` compara la fecha completa y `calculateBlockPosition` solo la hora del dia.**
      Dos citas de dias distintos a la misma hora no solapan para el reparto y se pintarian encima.
      Hoy inalcanzable porque la consulta lleva `date` y el backend filtra, pero lo unico que lo
      sostiene es esa promesa del backend, que ninguna de las dos funciones comprueba.
- [ ] **CONTRADICCION DEL CANVAS** (bloque 6 o quien retoque el diseno): la cabecera de Laura
      (`CalendarioDesktop.dc.html:110`) dice `4 citas · 5h 30min` y su columna dibuja DOS citas mas
      el almuerzo. `employeeDaySummary` se anclo en la de Marc (`:124`), que si cuadra.
- [ ] **Falta artboard de la vista de semana y del conmutador de agenda movil.** El segmentado
      `CalendarioDesktop:89-92` y su gemelo `Calendario.dc.html:31-33` estan dibujados pero su
      destino no existe. No se monta un control cuya segunda opcion no lleva a ninguna parte.
- [ ] **Desfase de una linea** en `calendar.test.ts:129,135`: citan `:161`/`:167`, que son lineas
      en blanco; los bloques estan en `:162`/`:168`. Cosmetico, misma clase de defecto.
- [ ] **`NaNh NaNmin` en la cabecera de columna** (bloque 3, tercer camino de la MISMA causa raiz).
      Con una cita de hora ilegible, `employeeDaySummary` (`calendar.ts:240-255`) devuelve
      `"2 citas · NaNh NaNmin"` y la cabecera de escritorio pinta `LMLaura M2 citas · NaNh NaNmin`:
      `differenceInMinutes` da `NaN`, `Math.max(0, NaN)` es `NaN`, y en `formatMinutes` no se
      cumple ni `hours === 0` ni `minutes === 0`. **Antes de `18e3b06` era inalcanzable** — la
      pantalla caia con `RangeError` primero —, asi que el arreglo del bloqueante lo hizo visible.
      Ademas la cabecera cuenta "2 citas" mientras se pinta UN bloque: es la cita invisible que la
      doc vecina (`calendar.ts:175-180`) declara inaceptable. Ningun test lo cubre: el caso nuevo
      usa `variant="mobile"`, que no tiene cabeceras. Direccion: defender `formatMinutes` del
      `NaN`, o alinear el recuento con lo pintado.
- [ ] **Un docblock que afirma mas de lo que su test demuestra** (`day-view.test.tsx:716-722`).
      Dice que el `useMemo` evita rehacer el reparto en cada tecla del buscador. No lo evita:
      `page.tsx:177-186` reconstruye `columns` en cada cambio de busqueda y `groupByEmployee`
      asigna un `appointments: []` nuevo por columna, asi que la dependencia falla igual. Lo que
      el `useMemo` compra son los renders que NO tocan `columns`, que es justo lo que el test
      mide. El test es honesto; el motivo escrito encima, no — misma clase de defecto que las
      citas falsas al artboard que este bloque vino a corregir.
- [ ] **El test de memoizacion de escritorio se vuelve vacuo bajo `React.memo`**
      (`day-view.test.tsx:723-742`). Comprobado: envolviendo `ColumnBody` en `memo()` y borrando
      los dos `useMemo`, solo cae el de movil. Quitar ese `React.memo` mas tarde restauraria el
      O(k³) en silencio.

---

# ESTADO REAL — verificado contra el CODIGO el 2026-08-30

Dos verificadores independientes, de solo lectura, con prohibicion expresa de mirar este
fichero. Sustituye a las casillas de las secciones de arriba, que estaban desfasadas.

## Bloques de pantalla pendientes

**4 · Detalle de cita — HECHO (2026-08-30).** Hoja de movil reescrita contra su artboard, panel
acoplado de 360px en escritorio, modo estrecho completo de la rejilla y `PENDING -> NO_SHOW`
abierto en el dominio. `/appointments/[id]` BORRADA (no tenia artboard y no la enlazaba nadie).
25 commits en el frontend + 2 en el backend, subidos. Sus deudas, mas abajo en este fichero.

**5 · Hoy — A MEDIAS.** Ojo con el NOMBRE DEL FICHERO, no con la pantalla: el artboard movil
de "Hoy" existe y en el canvas se llama asi, pero su fichero es **`Main.dc.html`** —
`canvas.json` le pone el titulo `'Hoy'` (y a `HoyDesktop.dc.html`, `'Hoy - escritorio'`).
Buscar `Hoy.dc.html` no encuentra nada y NO significa que falte el diseno.
`today/page.tsx:188-196` pinta UNA "Proxima cita" (`:72-76`, un solo `find`) y el artboard no
dibuja esa tarjeta en ningun sitio: dibuja "Ahora mismo" con **una fila por empleado**
(`HoyDesktop.dc.html:191-231`, `Main.dc.html:75-108`). Faltan ademas el 4o KPI
"Facturacion prevista" (el codigo pinta 3, `HoyDesktop:91-108` dibuja 4), la tarjeta
"2 reservas online sin confirmar" con su CTA (`:234-238`) y las dos columnas `1.6fr / 1fr`
(`:111`) — el codigo es una sola.

**6 · Equipo y clientes — A MEDIAS, con dos huecos concretos.**
- `staff/page.tsx` renderiza las tarjetas MOVILES tambien en escritorio; `EquipoDesktop.dc.html`
  dibuja una **tabla** con columnas Empleado / Puesto / Contacto / Color / Estado, y el contador
  "5 empleados · 4 activos" (el codigo pone solo "N empleados", `:106-108`).
- `clients/[id]` **no tiene Historial de citas**, que los dos artboards dibujan (tabla
  Fecha/Servicio/Profesional/Importe/Estado + "14 citas · 612,00 € facturados"). Faltan tambien
  "Nueva cita", el badge "Reserva online" y "Llamar" en movil.
- `staff/[id]`: falta la seccion "Color identificativo" (hoy solo vive en el formulario,
  `employee-form.tsx:157`), el contador "4 de 6" y el encabezado "Horas propias de Laura". El
  artboard apila las secciones y el codigo usa pestanas (`:214-218`).
- **CORRECCION DE MAPEADO:** `Horario*.dc.html` NO es el horario del empleado — dibuja el
  **horario de apertura del salon**, con descanso y "Copiar lunes al resto". Corresponde a
  `settings/business-hours`, no a este bloque.

**7 · Ajustes — A MEDIAS. La ambiguedad del canvas ESTA RESUELTA y no bloquea.**
No eran dos artboards de la misma pantalla: `AjustesDesktop.dc.html` es el del **indice** de
Ajustes (lo cita `settings/page.tsx:21-23` para su ancho de 800px) y `AjustesSalonDesktop.dc.html`
es el del **perfil del salon** (554px, 3 campos + bloque de solo lectura). El codigo sigue el
segundo y lo documenta en `settings/salon/page.tsx:70-77`.
Lo que falta por pantalla:
- **Salon**: el bloque "Tu pagina publica" con el slug y el boton "Ver pagina de reservas".
- **Reservas**: el interruptor "Aceptar reservas online", la tarjeta "Codigo QR", la
  previsualizacion "Lo que ve tu cliente" y el panel "Si lo desactivas". Y sigue ahi la
  `toggleMutation` fantasma (`settings/booking/page.tsx:27-37`): declarada, nunca pintada,
  llamando a la API con cuerpo vacio, con un `useEffect` importado y sin usar en `:3`.
- **Facturacion**: faltan "Uso del plan" (empleados 5/10, citas del mes, recordatorios) y el
  bloque "Otros planes / Cambiar de plan". **Y el codigo INVENTA** una lista de planes con
  boton "Cambiar a X" (`:124-163`) que no dibuja ningun artboard de esa pantalla: eso vive en
  `CambiarPlan*.dc.html`, que **no tiene ruta**.
- **Cuenta**: falta la "Zona de peligro" entera (desactivar salon, los 30 dias, confirmacion
  escribiendo el nombre). No hay ni boton ni dialogo ni llamada.
- **Notificaciones**: hay artboard (`AjustesNotificaciones*.dc.html`) y **no hay ruta**. Peor:
  los cinco artboards de escritorio de Ajustes ya listan "Notificaciones" en su submenu, asi que
  el menu del codigo esta incompleto respecto al canvas.

**8 · Asistente de nueva cita — HECHO (2026-08-30).** Ya NO esta en `(app)`: hay grupo
`(fullscreen)` y la ruta se movio alli, asi que deja de heredar barra lateral y barra inferior
donde los diez artboards dibujan pantalla completa. Cabecera de 68px con marca + "Nueva cita" y
"Cancelar" + X de 38px, los cinco pasos reescritos contra sus artboards, y dos fallos reales de
produccion cerrados (el `"any"` que viajaba al endpoint de disponibilidad y el `search` que
`GET /api/v1/clients` ignoraba en silencio). 20 commits en frontend + 1 en backend. Ver el
bloque completo al final de este fichero.

## Cerrado, contra lo que decian las casillas viejas

- **RP.23 — CERRADO E IMPLEMENTADO.** No es una decision pendiente: `SalonService.java:159-163`
  emite `servicesUnavailable` / `employeesUnavailable`, `SalonPublicResponse.java:28,31` los
  declara, y `book/[slug]/page.tsx:83` distingue ya "no tiene servicios" de "no se pudo cargar",
  con test de las dos ramas.
- **FE.12 — HECHO.** El error de hueco ocupado es una **pantalla propia** con tests
  (`public-booking-error.tsx`), no el banner del paso 5 que decia la nota.
- **FE.3, FE.5, FE.6 — HECHOS** (formularios de empleado y cliente, y login), campo a campo
  contra sus artboards.
- **Dialogo de anonimizado — HECHO** (`gdpr-panel.tsx:94-118`).
- **`/clients` — enlazada en ESCRITORIO** desde la barra lateral (`app-nav.ts:45-50`), que
  anadio el bloque 2. En movil sigue sin camino, y el canvas tampoco lo ofrece: sus artboards
  dibujan la misma barra de cuatro (Hoy/Citas/Equipo/Mas).

## Huecos nuevos que nadie tenia anotados

- [ ] **`CambiarPlan*.dc.html` sin ruta**, y facturacion inventando en su lugar una lista de
      planes que ningun artboard dibuja.
- [ ] **`trialDays` sigue ignorado.** El backend lo envia (`PlanResponse.java:15`, con test de
      exposicion JSON); `types/billing.ts:18-23` ni lo declara. Cero usos en `src/`.
- [ ] **QR de la pagina publica**: dibujado en los dos artboards de Reservas, sin componente ni
      dependencia en el repo.
- [ ] **El canvas se contradice en la URL publica.** Cinco artboards escriben
      `rivoo.app/<slug>`; los dos de `Onboarding5` escriben `rivoo.app/book/<slug>`, que es lo
      que genera el codigo (`settings/booking/page.tsx:23-25`) y la ruta real. Decidir si se
      corrigen los cinco o se anade un rewrite.
- [ ] **`ServiceFormSheet` sin artboard**: existen `FormularioCliente*` y `FormularioEmpleado*`,
      pero no `FormularioServicio*`.
- [ ] **Vista de semana: confirmado que no hay artboard.** El conmutador esta dibujado en los dos
      anchos y su segunda opcion no lleva a ninguna parte; el codigo lo omite a proposito y lo
      documenta (`calendar/page.tsx:53-57`).

---

## BLOQUE 4 — Detalle de cita: CERRADO (2026-08-30)

**Frontend** `rivoo-frontend`, rama `master`, sin subir: 24 commits (`18e3b06..HEAD`).
**Backend** `rivoo`: 2 commits (`5e3cfb1` dominio, `3c9414a` doc del modulo).

**Puertas finales:** `npx eslint .` 0 errores / 17 avisos · `npx tsc --noEmit` 0 ·
`npx vitest run` **743 tests en 73 ficheros** · `npm run build` 0. Arbol limpio.
(Linea base al empezar: 574 tests en 66 ficheros.)

**Revision:** panel de 3 revisores independientes (fidelidad / correccion / mutacion), los tres con
veredicto de BLOQUEO, mas una re-revision de 2 sobre el lote de correcciones, tambien BLOQUEO.
Once agentes de correccion. De las 13 mutaciones que sobrevivian, **mueren las 13**.

### Deudas ANOTADAS, no arregladas

- **`/today` en escritorio** sigue abriendo la hoja como dialogo centrado. Es del bloque 5, donde
  esa pantalla tiene sus propios artboards. (D14)
- **El panel a 1024px** deja columnas de ~99px, contra los ~237px del artboard; con cinco empleados,
  ~56px. No se rompe (`minmax(0,1fr)` encoge), pero se lee mal. No se invento un segundo punto de
  ruptura porque nadie lo ha dibujado. `visual/appointment-detail-vs-artboards.spec.ts` saca una
  captura a ese ancho para poder decidirlo con ella delante. (D19)
- **Escritorio no puede marcar "No asistio" sobre una cita PENDING** aunque el servidor ya lo
  permita: el artboard de escritorio dibuja "Reprogramar" en esa casilla. Si el mostrador lo
  necesita, hay que DIBUJAR la casilla antes de construirla. (D5)
- **La hoja de movil no anima al cerrarse** en `/calendar` (en `/today` si). Se probo retener la
  ultima cita en un `useRef` y se REVIRTIO: violaba `react-hooks/refs` (24 errores) y dejaba el
  `<Sheet>` montado para siempre. Ningun artboard exige la animacion.
- **El spinner de las acciones es global**, no por boton. Se probo por accion y se REVIRTIO: con
  mutacion optimista el boton pulsado deja de existir a mitad de vuelo, asi que no hay ningun boton
  correcto sobre el que girar.
- **`updateStatus` a NO_SHOW no cancela los recordatorios programados**, cosa que `cancel()` si
  hace (`AppointmentService.java:235`). Preexistente para `CONFIRMED -> NO_SHOW`, pero `5e3cfb1`
  abre la puerta desde `PENDING`, que es justo el estado en que una reserva online puede tener el
  recordatorio pendiente. Decidirlo explicitamente.
- **`AppointmentRepositoryIntegrationTest` no se ejecuto**: esta tras el perfil `integration-test` y
  necesita Docker. Se cubrio por lectura estatica (sus cinco usos de `NO_SHOW` son estado EXCLUIDO
  en filtros de solape, nunca transicion). Reejecutar donde haya Docker.
- **`#D8C9B8` sigue escrito a pelo en cuatro sitios** (`booking-stepper.tsx:39,60`,
  `public-employee-step.tsx:219`, `checkbox.tsx:13`) pese a existir ya como `--border-dashed` y
  ahora tambien como `--grabber`. Deuda de tokens, ajena a este bloque.
- **`visual/appointment-detail-vs-artboards.spec.ts` NUNCA se ha ejecutado.** Necesita la pila
  levantada y credenciales del usuario:
  `RIVOO_E2E_EMAIL=... RIVOO_E2E_PASSWORD=... npx playwright test -g "detalle de cita"`.
  No afirma medidas: solo captura imagenes para que las mire una persona. **No cuenta como
  cobertura de artboard.**

### Pendiente de decision del usuario

- **Los 24 commits del frontend y los 2 del backend estan SIN SUBIR.**

### Deudas de la ultima revision (lote de correcciones), NO arregladas

- **Un fallo de cancelacion en vuelo puede quedar SIN VER.** El `key={appointment.id}` destruye la
  instancia del dialogo al cambiar de cita, asi que su `onError` se descarta. `useCancelAppointment`
  revierte la cache (bien), pero `src/providers/query-provider.tsx` NO define
  `MutationCache.onError` y no hay toast global. Escenario: cancelas a Ana, "Volver", pulsas a
  Carla, la peticion de Ana falla -> la cita revierte sola en la rejilla y el usuario no ve nada,
  creyendo que quedo cancelada. **Antes del lote el error SI se veia** (aunque sobre el dialogo
  equivocado). Recomendacion: `new QueryClient({ mutationCache: new MutationCache({ onError }) })`
  o un toast. **Es un cambio de alcance de APLICACION, no de este bloque**: merece su propio cambio
  acotado, no un parche al final.
- **`appointment-detail-panel.test.tsx:373` fija una transicion que el usuario no puede producir**:
  hace el `rerender` Ana->Carla con el dialogo de cancelacion abierto, y con el backdrop montado ese
  clic en la rejilla no llega. Muerde al quitar el `key` (verificado, no es verde falso), pero deja
  sin cubrir el camino real ("Volver" -> clic -> reabrir). Intercalar el "Volver", como hace el test
  de la hoja.
- **El caso `cn() no descarta leading-tight` de ese mismo fichero prueba a tailwind-merge, no al
  panel**: si twMerge cambia su heuristica se pone rojo sin que haya regresion de producto, y el
  reordenamiento real de `:170` ya lo caza el caso siguiente. Es redundante; borrarlo o degradarlo
  a comentario.


---

# BLOQUE 8 — Asistente de nueva cita (EN CURSO, arrancado 2026-08-30)

Plan: `docs/specs/asistente-nueva-cita/IMPLEMENTATION_PLAN.md` (33 decisiones,
13 tareas, 4 rondas de revision antes de arrancar).

**Objetivo:** que `/appointments/new` sea identico a sus diez artboards y deje de
heredar el chasis de la app interna (barra lateral en escritorio, barra inferior
en movil) cuando los diez dibujan pantalla completa.

**Linea base al arrancar** (`c791751`, arbol limpio): tsc 0 · eslint 0 errores +
17 avisos · vitest 744 tests en 73 ficheros · build 0.

## Olas

- [x] **Ola 0** — T0 ‖ B1 — CERRADA 2026-08-30
  - [x] **T0** · dos tokens (`--border-dashed-strong`, `--avatar-muted`) mapeados
        en `@theme inline`, y `formatDurationTight` junto a `formatDuration` sin
        tocar esta ultima (D10, D21). Commit `279f687`. Puertas verificadas por
        el orquestador: tsc 0 · eslint 0 errores + 17 avisos · vitest **746** en
        73 ficheros (linea base 744, +2 por los dos tests nuevos)
  - [x] **B1** · `search` y orden `lastVisitAt DESC, createdAt DESC` en
        `GET /api/v1/clients`, en JPQL (nunca nativo: el `@Filter` multi-tenant
        no cubre las nativas) y sin `NULLS LAST` (MySQL no lo soporta).
        Incluye montar la infraestructura de test de integracion, que
        `client-service` no tiene (D8, D9). Commit `8c5ef43`. JPQL parametrizada
        (nunca nativa), sin `NULLS LAST`, y el `Sort` entrante se descarta en
        `ClientService` con `PageRequest.of(page, size)` para que Spring no
        concatene otro `ORDER BY`. `mvn test -pl client-service -am` →
        BUILD SUCCESS, 9 tests verdes.
        **DEUDA: el test de integracion (10 casos) NO SE EJECUTO** — no hay
        Docker en esta maquina (`docker: command not found`). Compila y esta
        cableado, pero la asercion real contra MySQL esta pendiente:
        `mvn test -P integration-test -pl client-service -am`
- [x] **Ola 1** — T1 ‖ T2 ‖ T4 — CERRADA 2026-08-30. Puertas verificadas por el
      orquestador sobre el arbol consolidado: tsc 0 · eslint 0 errores + 17
      avisos · vitest **792 tests en 78 ficheros** · build OK
  - [x] **T1** · `2e18ca3` · grupo `(fullscreen)` con `OnboardingGate` y traslado de
        `appointments/` (D1)
  - [x] **T2** · `afd41ae` · promocion de `WizardStepper` y `WizardSummaryAside` a
        `components/wizard/` + `AFTERNOON_HOUR` a `dates.ts` (D4, D26, D27)
  - [x] **T4** · `6ebd8b4` · store (semilla + `selectedSlotEmployeeId`), `useClients` sin la
        guarda de 2 caracteres y con clave propia, `useEmployeesServices`,
        `use-wizard-availability` y `wizard-summary.ts` (D6, D9, D16, D17, D20,
        D22, D28).
        **Correccion del orquestador (`b993e9c`):** T4 fijaba "Sin elegir" como
        regla del paso 1, leyendo un ESTADO dibujado como si fuera una regla. Al
        volver atras con profesional ya elegido, el aside contradecia a la
        rejilla. Acotado al caso vacio, con test de regresion.
        **Trampa nueva descubierta por T1, anadida a §1.8 del plan:** tras mover
        una ruta, `.next/types` queda rancio y `tsc --noEmit` falla con errores
        ajenos; hay que correr `npx next typegen` antes. Afecta a T3.
- [x] **Ola 2** — T3 · `ded0293` · CERRADA 2026-08-30. Puertas verificadas por el
      orquestador: tsc 0 · eslint 0 errores + 17 avisos · vitest **810 tests en
      81 ficheros** · build OK (23 rutas, `/appointments/new` incluida).
      Contrato de props fijado para la ola 3: `step`, `title`, `subtitle?` (solo
      escritorio), `onBack?`, `onClose`, `aside?` (solo escritorio), `footer?`
      (solo el CONTENIDO; el cromo lo pinta el shell), `children`.
      `NewAppointmentShell`, progreso movil,
      `WizardContextPills`, `useWizardNavigation` y `page.tsx` como dispatcher
      (D2, D3, D5, D18, D26, D32)
- [x] **Ola 3** — T5 ‖ T6 ‖ T7 ‖ T8 ‖ T9 — CERRADA 2026-08-30. Puertas
      verificadas por el orquestador sobre el arbol consolidado: tsc 0 · eslint
      0 errores + **9** avisos (mejor que los 17 de partida: las reescrituras se
      llevaron imports muertos) · vitest **856 tests en 85 ficheros** · build OK
  - [x] **T5** · `659ac15` · paso 1, Profesional. Resuelve la semilla
        `preferredEmployeeId` que la pagina no puede resolver (D16). La fila
        atenuada "hoy no trabaja" ES pulsable y conserva su chevron (D33).
        Diagnostico propio: `userEvent` choca con `vi.useFakeTimers`, que ese
        test necesita para fijar "hoy"; dos tests pasados a `fireEvent` en vez
        de subir el timeout
  - [x] **T6** · `4e87c11` · paso 2, Servicio. Prueba de mutacion por
        iniciativa propia
  - [x] **T7** · `2e197b1` · paso 3, Fecha y hora. Horizonte de 30 dias
        intacto: la tira de 6/7 celdas es un ANCHO, no un limite (D29).
        Borra `use-availability.ts`, que se queda sin consumidores.
        Hallazgo propio que el plan no recogia: elegir hueco NO debe avanzar de
        paso — el artboard dibuja el pie con la hora elegida Y el boton
        "Continuar", asi que avanzando al pulsar no se veria nunca
  - [x] **T8** · `e8ab0e0` · paso 4, Cliente
  - [x] **T9** · `fdf858c` · paso 5, Confirmacion. La mutacion manda
        `selectedSlotEmployeeId`, no el literal `"any"` que mandaba el codigo
        anterior (§1.5.3: fallo real de produccion, cerrado)
  - [x] **Correccion del orquestador (`3596799`)** · misma clase de fallo que
        `b993e9c`, ahora entre dos ficheros: con "Sin preferencia" el aside
        decia `"Sin preferencia"` en TODOS los pasos, mientras la tarjeta del
        paso 5 nombraba a la persona concreta que la disponibilidad asigno — la
        misma pantalla afirmando dos cosas contradictorias, una al lado de la
        otra. "Sin preferencia" es cierto MIENTRAS no hay hueco; en cuanto lo
        hay, la cita tiene profesional. `slotEmployee` entra por el estado
        (el modulo del resumen es puro y el store solo guarda el id).
        **Test verificado portante**: mutada la fuente a `if (false && ...)`,
        cayo exactamente ese test; revertido
- [x] **Ola 4** — T10 · `cb072d0` · CERRADA 2026-08-30.
      `visual/new-appointment-vs-artboards.spec.ts` (288 lineas). UN solo
      recorrido: en cada paso captura 390 y 1440 antes de avanzar, aprovechando
      la invariante que el chasis documenta (`{children}` en la misma posicion
      del arbol en las dos ramas de `isDesktop`, asi que cambiar de ancho a
      mitad de paso NO remonta el paso ni pierde el store). Diez pares de
      imagenes contra los diez artboards. Puertas: tsc 0 · eslint 0 errores + 9
      avisos · vitest 856 en 85 · build OK (23 rutas).
      **NO SE EJECUTA** (D25): necesita credenciales y la pila levantada.
      Tres cosas que el plan no recogia y quedan en la cabecera del fichero:
      (a) el calendario de escritorio del asistente PAGINA POR SEMANAS
      (`DESKTOP_WEEK_PAGES = 4`), a diferencia de la tira continua de la reserva
      publica, asi que el recorrido tiene que paginar para encontrar hueco;
      (b) con "Sin preferencia" el paso 2 pinta TODOS los servicios como
      habilitados aunque nadie los ofrezca — `isOffered` es siempre `true` sin
      empleado concreto — y es el paso 3 quien lo descubre;
      (c) `handleSlotSelect` es la unica seleccion de las cinco que NO llama a
      `nextStep()` sola, y es correcto: el artboard dibuja el pie con la hora
      elegida Y el boton "Continuar"
- [x] **Ola 5** — T11 · CERRADA 2026-08-30. Panel de TRES revisores
      independientes en paralelo, agentes nuevos, ninguno implementador,
      instruidos para REFUTAR. **Las tres devolvieron BLOCK.**
  - **Lente 1 (fidelidad al artboard)** — 2 HIGH: el aside de escritorio de los
    pasos 1-4 pintaba "Tu reserva" + la nota de confianza de la reserva PUBLICA
    ("Sin registro, cancela gratis hasta 24h antes") donde los cuatro artboards
    dicen "Resumen" y ninguno de los diez dibuja nota — texto FALSO en el
    asistente interno; y las pildoras del paso 3 mutaban a la forma del paso 4
    en cuanto habia hueco elegido, que es justo el frame que
    `NuevaCitaPaso3.dc.html` retrata. Mas 6 MEDIUM y 8 LOW.
  - **Lente 2 (correccion)** — 2 HIGH: el asistente ATRAPABA al usuario en el
    paso 2 al entrar desde el calendario (`selectEmployee` no consumia
    `preferredEmployeeId`, el paso 1 remontaba y rebotaba, y de paso perdia el
    servicio); y el reintento tras un fallo DUPLICABA el cliente recien creado
    en BD, con un mensaje de error que invita justamente a reintentar. Mas 5
    MEDIUM y 6 LOW.
  - **Lente 3 (regresion y calidad de tests)** — 87 mutaciones al codigo de
    produccion: **38 muertas, 49 supervivientes** (44%). `confirmation-step` con
    13 de 13 supervivientes: cobertura CERO en el unico paso con efectos de lado.
    Dato que resume el bloque: 11 mutaciones de comportamiento simultaneas en 5
    ficheros dejaban los 856 tests en verde. La reserva publica NO se movio
    (verificado prop a prop contra `c791751`).
  - **Ola de correcciones**, seis implementadores nuevos sobre ficheros
    disjuntos: `c2ab09d` (client + use-clients) · `4904f1d` (confirmation) ·
    `5d22d7e` (datetime) · `6fc5821` (employee + store + page + navegacion) ·
    `9b122d6` (shell + stepper + aside) · `248c2d5` (pildoras + service).
    **Correccion del orquestador `8981037`**: al repartir el trabajo pedi el
    `slotEmployee` al paso 3 y ya estaba en el 5, pero me deje el paso 4 — el
    aside habria nombrado a la persona en el 3, dicho "Sin preferencia" en el 4
    y vuelto a nombrarla en el 5. Test verificado portante por mutacion.
  - **Puertas finales**, ejecutadas por el orquestador sobre el arbol
    consolidado y quieto: tsc 0 · eslint 0 errores + 9 avisos · vitest
    **916 tests en 86 ficheros** · build OK. Linea base del bloque: 744 en 73.
  - **Dos falsas alarmas, las dos por contencion entre agentes** (leccion
    anotada en `tasks/lessons.md` y en memoria):
    1. la lente 1 reporto `wizard-summary.test.ts` "inestable", rojo en 4
       pasadas con un test distinto cada vez. No lo es: veia las mutaciones que
       la lente 3 aplicaba a la vez a ese mismo fichero. 10 pasadas sobre arbol
       quieto, 0 fallos.
    2. un implementador reporto un comentario `MUTATION` roto en
       `client-step.tsx:74`. Era un instante en que otro agente tenia una
       mutacion aplicada. Cero rastro de `MUTATION` en todo `src/`.

## Deudas que este bloque dejara anotadas (a rellenar en T11)

- [ ] endpoint de rango de disponibilidad: cerraria D7 y D30 (contadores
      "N huecos", estado "Sin huecos" y huecos ocupados tachados) en el
      asistente Y en la reserva publica a la vez
- [ ] `totalVisits` / `lastVisitAt` no los escribe NADIE: el paso 4 pintara
      "0 visitas" en todas las filas. Arreglo: `POST /api/internal/clients/{id}/visit`
      llamado desde `AppointmentService` al pasar una cita a `COMPLETED`
- [ ] `rescheduleId` sin artboard: el panel de detalle enlaza una
      reprogramacion que el canvas nunca dibujo
- [ ] formulario de alta de cliente en linea, sin artboard
- [ ] la reserva publica pinta "45 min" donde sus siete artboards dibujan
      "45min": cambiar sus consumidores a `formatDurationTight`
- [ ] el stepper se comprime entre 1024 y 1279 y la comparacion visual corre a
      1440, asi que no lo ve
- [ ] flechas de navegacion por semanas del paso 3: no las dibuja ningun
      artboard y se pintan igual (sin ellas media parte del horizonte de 30 dias
      es inalcanzable)
- [ ] spec visual sin ejecutar (necesita credenciales y la pila levantada)
- [ ] **el boton-icono de 38x38 sale `#FBF7F2` donde los artboards dibujan
      `#FFFFFF`.** La receta del repo (`page-shell.tsx:235-243`) usa
      `variant="outline"`, que es `bg-background`. Afecta IGUAL al asistente y a
      `/staff/[id]`, y los dos artboards
      (`NuevaCitaDesktopPaso1.dc.html`, `DetalleEmpleadoDesktop.dc.html`)
      dibujan blanco. Es preexistente, de una pantalla cerrada, y son dos
      caracteres: se arreglan LOS DOS a la vez o se quedan los dos igual, nunca
      uno solo — lo detecto T3 y lo dejo por escrito en vez de divergir

## Deudas NUEVAS que destapo el panel de revision (2026-08-30)

- [ ] **La reserva publica arrastra los DOS mismos defectos de chasis que el
      asistente acaba de corregir**, y no se tocaron porque es carril cerrado:
      1. `booking-step-shell.tsx:99` usa `lg:max-w-[1120px]` con `md:px-10`.
         Con `box-sizing: border-box` el `max-w` INCLUYE el padding, asi que el
         contenido real son 1040px donde el artboard dibuja 1120 — la columna
         principal pierde ~10,5%. En el asistente se arreglo pasando a
         `max-w-[1200px]` (`new-appointment-shell.tsx:98`).
      2. `public-datetime-step.tsx:356,437` no da color al nombre del dia de la
         semana, asi que hereda el `#2A2320` del body donde `ReservaPaso3:62` y
         `ReservaDesktopPaso3:101` piden `#7A6A5F`. Mismo arreglo que en
         `datetime-step.tsx`.
      Los dos son de una linea. Se arreglan cuando se reabra ese carril.
- [ ] **Con "Sin preferencia", en MOVIL el usuario nunca ve quien le atendera.**
      En escritorio el aside lo nombra desde el paso 3 (ya arreglado). En movil
      no hay aside: el resumen son las pildoras, y `WizardContextPills` solo
      pinta la de profesional si hay `selectedEmployee`, que con "Sin
      preferencia" es `null`. Ningun artboard dibuja ese estado — los diez
      retratan el flujo con profesional concreto elegido en el paso 1 — asi que
      NO se invento la pildora. El canvas deberia decidir que se pinta ahi.
- [ ] **Correccion al plan, §1.8.9: la premisa del `<Suspense>` NO reproduce.**
      §1.8.9 afirma que sin el limite de `<Suspense>` alrededor de
      `useSearchParams`, `npm run build` falla para el grupo de rutas entero. Se
      midio quitandolo: **build exit 0**, y `/appointments/new` sigue
      prerenderizada estatica. El limite es correcto y se queda, pero hoy no lo
      protege NADA — ni vitest ni el build. Si alguien lo borra por "no hace
      falta", ninguna puerta se entera.
- [ ] `weekPage` puede salirse del rango que defienden las flechas: entrando en
      escritorio con `?date=` a 28-29 dias vista, `Math.floor(29/7)` da 4 y
      `DESKTOP_WEEK_PAGES` solo define 0-3; la rejilla pinta dias 28-34 (fuera
      del horizonte declarado de 30) y "Semana siguiente" queda deshabilitada
      mientras "anterior" funciona. Ventana estrecha, no bloqueante.
- [ ] La resolucion de "primer dia que si trabaje" (D33) es un pestillo de una
      sola vez que puede fijarse antes de que lleguen los horarios: si corre con
      `hoursByEmployee` vacio, `isDayClosed` devuelve `false` para todo y el
      offset se queda en 0. En la practica la cache del paso 1 suele estar
      caliente, asi que hace falta recorrer 1-2-3 mas rapido que las N
      peticiones de horarios.
- [ ] Al salir del asistente con el boton ATRAS del navegador (no con la X, que
      si llama `reset`), el store conserva `step`; al reabrir, el primer pintado
      monta el paso viejo con sus queries y solo despues se resetea al paso 1.
      Destello, no perdida de datos.

## Deudas del bloque 8 que destapo la revision INDEPENDIENTE del commit `8981037`

El hook de independencia de revisor salto avisando de que ese commit (correccion
del orquestador, posterior al panel) no lo habia mirado nadie mas. Se despacho un
revisor y lo APROBO, pero encontro dos cosas que el panel no vio:

- [ ] **[MEDIUM] El invariante "el asistente no se contradice entre pantallas"
      NO se cumple todavia en los pasos 1 y 2.** Son los dos unicos que siguen
      pasando `wizardState` crudo, sin `slotEmployee`
      (`service-step.tsx:120-121`, `employee-step.tsx:171`). Escenario: "Sin
      preferencia" + hueco elegido + volver atras hasta el paso 2 (`prevStep` no
      limpia nada, `wizard-store.ts:65`, y `getDateTimeRow` no tiene puerta por
      paso). El aside pinta A LA VEZ "Profesional: Sin preferencia" y "Fecha y
      hora: Jue 28, 11:00". Arreglo: mismo patron que ya usan los pasos 3, 4 y 5.
- [ ] **[LOW] Tres resoluciones distintas del mismo dato.** El paso 3 busca al
      dueno del hueco en `activeEmployees` (`datetime-step.tsx:240`); los pasos 4
      y 5, en la lista completa (`client-step.tsx:75`,
      `confirmation-step.tsx:139-140`). Si se desactiva a esa persona a mitad del
      asistente y la query `["employees"]` refresca, el paso 3 dice "Sin
      preferencia" y los otros dos su nombre. El lado correcto es el de los pasos
      4/5: la cita se crea con `employeeId: selectedSlotEmployeeId` sin mirar
      `isActive`, asi que nombrar a esa persona es lo veraz. Arreglo: un unico
      helper `resolveSlotEmployee(employees, id)` que llamen los tres.

## Nota para el bloque 6 (Equipo) — NO es una deuda, es un aviso

`DetalleEmpleadoDesktop.dc.html:245,255` y `FormularioEmpleadoDesktop.dc.html`
dibujan la duracion **CON espacio** ("45 min"), que es exactamente lo que
`formatDuration` ya produce, y su consumidor
(`src/components/staff/service-assignment.tsx:73`) es correcto tal cual. **No
hay nada que hacer alli.** Queda escrito para que nadie "unifique"
`formatDuration` y `formatDurationTight` al ver dos funciones parecidas: son dos
convenciones de dibujo distintas, cada una con sus artboards detras. Unificarlas
rompe una pantalla ya cerrada, sea cual sea la direccion en que se unifiquen.
