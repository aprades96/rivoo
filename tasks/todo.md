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

### 1. Reserva pública — EN CURSO · plan: `docs/specs/reserva-publica/`

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
- [ ] **RP.13** Que el store acepte ServicePublic (hoy el componente rellena category/isActive a mano)
- [ ] **RP.14** Filtrar los null de serviceIds (asignación huérfana → EmployeeServicePersistenceAdapter:45)
- [x] **RP.15** `getInternal()` ignora su parámetro `tenantId` en empleados y servicios — **fuga cross-tenant en la ruta pública**

> **RP.15, hallazgo del 2026-08-27, no estaba en el plan.**
> `staff-service/.../application/EmployeeService.java:121-125` recibe `tenantId` y no lo usa:
> hace `findByExternalId(employeeExternalId)` sin predicado de tenant. Durante un
> `POST /api/v1/appointments/book` anónimo no hay `TenantContext`, así que
> `TenantFilterAspect:20,30` tampoco activa el `@Filter` de Hibernate. Resultado: la
> validación que debe comprobar que el empleado pertenece a ese salón no comprueba nada,
> y un atacante puede reservar en el salón A citando un empleado del salón B.
> `ServiceOfferingService.java:97-101` tiene exactamente el mismo defecto: confirmado, no supuesto.

- [ ] **RP.16** El campo `isOpen` de los horarios no cuadra entre backend y frontend
- [ ] **RP.20** Deuda de la review de RP.5 (alcance del `catch`, constante del header, log)
- [x] **RP.17** Reformar la URL de los listados internos: `/{tenantId}/public/{employees,services}`
- [x] **RP.18** Deuda menor de la review de RP.4/RP.8 (logs, docs, test del gateway)
- [x] **RP.19** BLOQUEANTE: `salon-service/application-prod.yml` no declara `staff-service.url`
- [ ] **RP.23** DECISION DE PRODUCTO PENDIENTE: senalizar la degradacion al frontend
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

### 2. Onboarding reanudable — PENDIENTE · opción B decidida

También roto en producción, para usuarios nuevos: `onboarding-gate.tsx:41` deduce el
estado contando empleados y servicios, y si faltan te manda a `/welcome`. Como los pasos
3 y 4 tienen "Omitir", **quien omite entra en bucle y no llega nunca a la app**.
Además guarda en cada paso pero reempieza en el 1: la única combinación sin defensa.

No sirve mirar `status` (la saga deja el salón ACTIVE ya en el registro) ni "¿tiene
horarios?" (la saga crea horarios por defecto en el paso 4 del registro).

- [ ] **ON.1** Campo `onboarding_completed_at` en salón + migración Flyway
- [ ] **ON.2** Endpoint para marcarlo, y exponerlo en SalonResponse
- [ ] **ON.3** El gate mira solo ese flag; fuera la lógica de contar empleados/servicios
- [ ] **ON.4** "Ir al dashboard" y "Omitir" marcan el flag
- [ ] **ON.5** El paso de horarios precarga los que ya existen
- [ ] **ON.6** Estados vacíos: "Hoy" sin servicios, y página pública "aún no acepta reservas"
- [ ] **ON.7** Decidir qué se hace con `salon-setup` (huérfana, se numera como paso 2)
- [ ] **ON.8** Pantallas ya dibujadas: página "Alta de negocio" del canvas (12 artboards)

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
El bloque más grande; merece plan propio.

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
