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

## Fase 4: staff-service + client-service (Semana 4)

### 4A — staff-service
- [ ] **4A.1** Migración Flyway V1: `employees`, `employee_working_hours`, `services`, `employee_services`
- [ ] **4A.2** Entidades JPA (extends TenantAwareEntity): `Employee`, `EmployeeWorkingHours`, `Service`, `EmployeeService`
- [ ] **4A.3** Generación de external_id: prefijos `emp_`, `svc_`
- [ ] **4A.4** CRUD completo: employees, services, employee_working_hours, employee_services
- [ ] **4A.5** Endpoints internos:
  - `GET /api/internal/staff/{tenantId}/employees/{employeeId}`
  - `GET /api/internal/staff/{tenantId}/services/{serviceId}`
- [ ] **4A.6** Validación de límites contra billing-service (mock con valores permisivos por ahora)

### 4B — client-service
- [ ] **4B.1** Migración Flyway V1: `clients` (con campos GDPR)
- [ ] **4B.2** Entidad JPA: `Client` (extends TenantAwareEntity)
- [ ] **4B.3** Generación de external_id con prefijo `cli_`
- [ ] **4B.4** CRUD completo de clients
- [ ] **4B.5** Endpoint de anonimización GDPR: `DELETE /api/v1/clients/{id}/gdpr`
  - Anonimiza datos personales (no DELETE físico)
  - Cancela citas futuras del cliente (cuando appointment-service esté listo)
- [ ] **4B.6** UNIQUE constraint: `(tenant_id, email)`
- [ ] **4B.7** Endpoints internos:
  - `GET /api/internal/clients/{clientId}?tenantId={tenantId}`
- [ ] **4B.8** Validación de límites contra billing-service (mock)

### ✅ Verificación Fase 4
- [ ] CRUD de employees, services, clients funcional
- [ ] Anonimización GDPR de un cliente funciona
- [ ] Aislamiento multi-tenant verificado en ambos servicios

---

## Fase 5: appointment-service — Core del Producto (Semana 5)

- [ ] **5.1** Migración Flyway V1: `appointments` con índices críticos
- [ ] **5.2** Entidad JPA: `Appointment` (extends TenantAwareEntity) con campos denormalizados (snapshots)
- [ ] **5.3** Generación de external_id con prefijo `apt_`
- [ ] **5.4** Lógica de disponibilidad:
  - Obtener horarios del empleado (staff-service)
  - Obtener citas existentes del empleado en el rango
  - Calcular slots libres
  - Conversión UTC ↔ timezone del salón
- [ ] **5.5** Detección de conflictos con `SELECT ... FOR UPDATE` (evitar race conditions)
- [ ] **5.6** Flujo de creación de cita:
  1. Validar límites plan (billing-service, bypass cache)
  2. Validar employee + service (staff-service)
  3. Validar cliente (client-service)
  4. Verificar disponibilidad (local)
  5. INSERT cita
  6. Programar notificación (notification-service, fire-and-forget)
- [ ] **5.7** Flujo de estados: PENDING → CONFIRMED → IN_PROGRESS → COMPLETED / CANCELLED / NO_SHOW
- [ ] **5.8** Endpoint de cancelación (con cancelación de recordatorios)
- [ ] **5.9** Endpoints públicos:
  - `POST /api/v1/appointments`
  - `GET /api/v1/appointments` (listado por tenant, filtros por fecha/empleado/estado)
  - `GET /api/v1/appointments/{id}`
  - `PUT /api/v1/appointments/{id}/status`
  - `DELETE /api/v1/appointments/{id}` (cancelar)
  - `GET /api/v1/appointments/availability` (slots disponibles)
- [ ] **5.10** Endpoints internos:
  - `GET /api/internal/admin/appointments/stats` (para admin-service)

### ✅ Verificación Fase 5
- [ ] Crear cita validando employee, service, client, disponibilidad
- [ ] Race conditions prevenidas con FOR UPDATE
- [ ] Timestamps en UTC, conversión correcta a Europe/Madrid
- [ ] Flujo de estados completo

---

## Fase 6: Gateway completo + Integración E2E (Semanas 6-7)

### 6A — Gateway completo (Semana 6)
- [ ] **6A.1** Rutas a TODOS los servicios
- [ ] **6A.2** Rate limiting general: 100 req/min por IP
- [ ] **6A.3** Rate limiting específico para booking público: 10 req/min por IP
- [ ] **6A.4** Ruta pública para Keycloak endpoints
- [ ] **6A.5** Ruta pública para `/api/v1/salons/public/{slug}`
- [ ] **6A.6** Ruta pública para `/api/v1/appointments/book`
- [ ] **6A.7** Ruta pública para `/api/webhooks/stripe`
- [ ] **6A.8** Primer test E2E completo:
  Login Keycloak → crear staff → crear client → crear cita (todo vía gateway)

### 6B — Buffer de integración (Semana 7)
- [ ] **6B.1** Resolver bugs descubiertos en Semana 6
- [ ] **6B.2** Tests de aislamiento cross-tenant E2E (2 salones completos vía gateway)
- [ ] **6B.3** Verificar propagación de X-Correlation-Id en toda la cadena
- [ ] **6B.4** Documentar colección Postman/Bruno con todos los flujos

### ✅ Verificación Fase 6
- [ ] Flujo E2E completo: login → CRUD staff/client → crear cita (vía gateway)
- [ ] Rate limiting funcional
- [ ] Cross-tenant aislado E2E
- [ ] Correlation IDs propagados correctamente

---

## Fase 7: billing-service + Stripe (Semana 8)

- [ ] **7.1** Migración Flyway V1: `subscription_plans`, `plan_limits`, `subscriptions`, `webhook_event_log`
- [ ] **7.2** Seed data: insertar los 4 planes (FREE_TRIAL, BASIC, PREMIUM, ENTERPRISE) con sus límites
- [ ] **7.3** Entidades JPA + repositorios
- [ ] **7.4** Integrar Stripe Java SDK
- [ ] **7.5** Crear Stripe Customer al registrar salón
- [ ] **7.6** `POST /api/v1/billing/checkout-session` → Stripe Checkout Session
- [ ] **7.7** Webhook handler `POST /api/webhooks/stripe`:
  - `checkout.session.completed` → vincular stripe_subscription_id
  - `invoice.paid` → status=ACTIVE, actualizar periodo
  - `invoice.payment_failed` → status=PAST_DUE, notificar
  - `customer.subscription.updated` → cambio de plan + actualizar Keycloak
  - `customer.subscription.deleted` → CANCELLED + suspender salón + deshabilitar Keycloak
- [ ] **7.8** Idempotencia con tabla `webhook_event_log`
- [ ] **7.9** Cache Caffeine con bypass para escrituras en `GET /api/internal/tenants/{tenantId}/plan-limits`
- [ ] **7.10** Integrar con salon-service (onboarding real crea suscripción)
- [ ] **7.11** Integrar con staff/appointment/client (validación real de límites)
- [ ] **7.12** Actualizar atributo `subscription_plan` en Keycloak al cambiar plan
- [ ] **7.13** Instalar Stripe CLI para testing local: `stripe listen --forward-to localhost:8087/api/webhooks/stripe`

### ✅ Verificación Fase 7
- [ ] Flujo Stripe completo en modo test: registro → trial → checkout → pago
- [ ] Webhooks idempotentes
- [ ] Límites de plan aplicados en staff/appointment
- [ ] Cache bypass funcional en operaciones de escritura

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
