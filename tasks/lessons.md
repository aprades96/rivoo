# Rivoo — Lessons Learned

## Fase 4 (staff-service + client-service)

### CRITICAL: @ConditionalOnBean(EntityManager.class) no funciona en auto-configuration
- `@ConditionalOnBean(EntityManager.class)` en `TenantAutoConfiguration` NUNCA se evalúa como `true`.
- **Root cause**: `EntityManager` no es un bean estándar de Spring — se proporciona via `SharedEntityManagerCreator` y `@PersistenceContext`. El `@ConditionalOnBean` evalúa el `BeanDefinitionRegistry` donde `EntityManager` no aparece como bean registrado.
- **Consecuencia**: `TenantFilterAspect` nunca se creaba → Hibernate `@Filter` nunca se activaba → **data leak cross-tenant** (queries sin filtro `tenant_id`).
- **Agravante**: salon-service no expuso el bug porque usa `findByTenantId()` (filtro explícito en queries), pero staff-service y client-service usan `findByActiveTrue()`, `findAll()`, `findByExternalId()` que dependen del `@Filter`.
- **Fix**: Cambiar a `@ConditionalOnClass(name = "jakarta.persistence.EntityManagerFactory")` (verifica que JPA está en classpath) + `@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)` (garantiza ordering).
- **Lección general**: Siempre verificar que los beans conditional se crean realmente. Usar logging en el constructor del bean para diagnóstico rápido.

### Keycloak credentials bug persiste: usar PUT reset-password
- Al crear usuarios via `POST /users` con el body que incluye credentials, la contraseña no se persiste correctamente en Keycloak 26.x.
- **Workaround**: Después de crear el usuario, hacer `PUT /admin/realms/rivoo/users/{id}/reset-password` con `{"type":"password","value":"...","temporary":false}` usando un JSON file (evitar shell quoting).
- Esto aplica a todos los flujos de onboarding de salon-service.

### Composite PK con @IdClass: equals/hashCode obligatorios
- `EmployeeServiceJpaEntity` usa composite PK `(employee_id, service_id)` con `@IdClass(EmployeeServiceId.class)`.
- `EmployeeServiceId` DEBE implementar `Serializable`, tener constructor vacío, y `equals()`/`hashCode()` basados en los campos PK.
- Sin esto, Hibernate no puede comparar identidades correctamente.

### EmployeeServiceAssignment: enriquecer en persistence adapter, no en domain
- La tabla `employee_services` solo tiene FKs (employee_id, service_id) + custom overrides.
- Pero el domain model necesita el nombre del servicio, duración y precio por defecto para calcular `getEffectiveDuration/Price`.
- **Solución**: El `EmployeeServicePersistenceAdapter` carga los servicios asociados (batch fetch via findAllById) y construye el domain model completo.
- Esto es aceptable: el adapter traduce entre la representación JPA y el domain model enriquecido.

### Domain exceptions con jerarquía RivooException: zero boilerplate
- Crear `EmployeeNotFoundException extends ResourceNotFoundException` → solo 1 constructor con `super("employee", identifier)`.
- El `GlobalExceptionHandler` de rivoo-common ya cubre todas las excepciones RivooException → 0 handlers adicionales necesarios.
- Solo se añade handler extra para excepciones no-RivooException como `AuthServiceException` (→ 502) y `DataIntegrityViolationException` (→ 409 safety net).

### BillingServiceStubAdapter: -1 = unlimited
- Cuando un servicio externo no existe aún, implementar un `StubAdapter` que retorna valores permisivos.
- `-1` significa "unlimited" — el check `if (maxEmployees >= 0)` solo aplica el límite cuando hay valor real.
- El port interface está lista para reemplazo directo cuando billing-service exista.

### Flyway V2 (no V1) en servicios ya arrancados
- Los skeleton services de Fase 1 ya tienen `V1__placeholder.sql`. La primera migración real es V2.
- NUNCA editar una migración ya aplicada — siempre crear V{n+1}.

## Fase 3.5 (Refactoring pre-Fase 4)

### RivooException jerarquía: un handler para gobernarlos a todos
- Tener N handlers individuales en `GlobalExceptionHandler` (uno por excepción) es repetitivo y frágil.
- **Solución**: Crear `RivooException` abstracta con `errorType`, `errorTitle`, `httpStatus`. Cada excepción de dominio extiende esta base. Un único `@ExceptionHandler(RivooException.class)` genera el `ProblemDetail` correcto para todas.
- Beneficio: añadir nuevas excepciones solo requiere crear la clase — el handler ya las cubre.

### @ConfigurationProperties con records en vez de @Value
- `@Value("${rivoo.security.internal-service-key}")` repetido en varios sitios es frágil y propenso a typos.
- **Solución**: `@ConfigurationProperties(prefix = "rivoo.security")` con un `record RivooSecurityProperties(String internalServiceKey)` — type-safe, un solo punto de configuración, validación en compact constructor.
- Requiere `@EnableConfigurationProperties(RivooSecurityProperties.class)` en la auto-configuration.

### Extraer saga a su propia clase
- `SalonService` tenía demasiada responsabilidad: CRUD + onboarding saga + slug generation + default hours.
- **Solución**: Extraer `OnboardingSagaService` que implementa `RegisterSalonUseCase`. SalonService queda limpio con solo CRUD + business hours.
- Patrón: cuando un método en un service tiene > 50 líneas con try/catch de compensaciones, merece su propia clase.

### TenantFilterAspect: NUNCA ejecutar sin filtro
- Si `entityManager.unwrap(Session.class)` falla silenciosamente, la query se ejecuta SIN filtro de tenant → data leak cross-tenant.
- **Solución**: Envolver en try-catch y lanzar `IllegalStateException` para abortar la operación. Es preferible un error 500 a un data leak.

### KeycloakAdminAdapter: DRY con functional interface
- El patrón try-catch con traducción de excepciones se repetía en 7 métodos.
- **Solución**: Extraer `executeKeycloakOperation(String operationName, KeycloakOperation<T> op)` con una `@FunctionalInterface` privada. Reduce boilerplate y garantiza manejo consistente de errores.

### Validaciones @Size/@Pattern en DTOs: defensa en profundidad
- Los DTOs de registro solo tenían `@NotBlank` y `@Email`. Sin `@Size`, un campo de 10MB pasa validación.
- **Solución**: Añadir `@Size(max=N)` a todos los campos string y `@Pattern` para formatos específicos (teléfono). Primera línea de defensa antes de llegar al dominio.

### Auto-configuration ordering explícito
- El orden de auto-configurations en Spring Boot depende de classpath scanning y puede variar.
- **Solución**: Usar `@AutoConfiguration(after = X.class)` para garantizar: Observability → Tenant → Security → Web/Client. Importante cuando Security depende de beans de Tenant.

### Keycloak credentials: Map<String,String> serializa boolean como string
- Al crear un usuario en Keycloak via POST /users con credentials `[{"type":"password","value":"...","temporary":"false"}]`, el `"temporary":"false"` es un string, NO un boolean.
- Keycloak 26.x no coerce el string a boolean → silenciosamente NO persiste el password. El usuario se crea (201) pero sin credentials funcionales.
- Login falla con `invalid_grant`. Solo `PUT /users/{id}/reset-password` funciona (endpoint dedicado).
- **Root cause**: `List<Map<String, String>>` en `KeycloakUserRepresentation` fuerza todos los valores a String.
- **Fix**: Reemplazar con `record CredentialRepresentation(String type, String value, Boolean temporary)`. El `Boolean false` serializa correctamente como JSON `false`.
- **Lección general**: en APIs externas, los tipos de datos importan. Un Map<String,String> pierde información de tipo.

### Constantes centralizadas para headers HTTP
- Magic strings como `"X-Tenant-Id"` esparcidos en 4+ clases es un bug waiting to happen.
- **Solución**: Clase `RivooHeaders` con constantes `public static final String`. Las clases existentes referencian las constantes.
- **Nota**: Gateway (WebFlux) no comparte rivoo-common, así que sus constantes quedan locales — aceptable.

## Fase 3

### Spring Cloud Gateway 5.0.x — New config prefix
- Spring Cloud 2025.0.0 renamed the configuration property prefix from `spring.cloud.gateway.*` to `spring.cloud.gateway.server.webflux.*` (for WebFlux) or `spring.cloud.gateway.server.webmvc.*` (for MVC).
- Old prefix `spring.cloud.gateway.routes` is silently ignored — routes count shows 0 with no error.
- **Solution**: Use `spring.cloud.gateway.server.webflux.routes` in application-local.yml. Decompile `GatewayProperties.class` to verify: `@ConfigurationProperties("spring.cloud.gateway.server.webflux")`.

### JPA deleteBySalonId + saveAll: unique constraint violation
- Spring Data derived delete (`deleteBySalonId`) does SELECT + entity-by-entity DELETE in the persistence context but does NOT flush to the database.
- Subsequent `saveAll` with the same unique key values causes `SQLIntegrityConstraintViolationException` because the deletes haven't hit the DB yet.
- **Solution**: Call `entityManager.flush()` after the delete operation, before the saveAll.

### JPA @PrePersist for timestamps instead of MySQL DEFAULT
- MySQL `DEFAULT CURRENT_TIMESTAMP` only works when Hibernate doesn't include the column in the INSERT. But Hibernate always includes all mapped columns, even if null → NULL overrides the DEFAULT.
- **Solution**: Use `@PrePersist` / `@PreUpdate` callbacks on the JPA entity to set `createdAt`/`updatedAt` in Java code.

### Git Bash shell quoting issues with curl
- Git Bash on Windows mangles special characters in `curl -d '...'` (single-quoted strings), especially `!` and non-ASCII chars.
- **Solution**: Write JSON to a file first, then use `curl -d @/tmp/file.json`. Use heredoc with `<< 'EOF'` (single-quoted delimiter) to prevent shell expansion.

### MapStruct + Lombok @Builder en JPA entities que extienden MappedSuperclass
- Si una JPA entity usa `@Builder` y extiende una `@MappedSuperclass` (como `TenantAwareEntity`), Lombok genera un builder que NO incluye los campos del padre.
- MapStruct detecta el builder y lo usa, pero falla con "Unknown property tenantId in result type SalonJpaEntity.SalonJpaEntityBuilder".
- **Solución**: No usar `@Builder` en JPA entities que extienden `TenantAwareEntity`. Usar solo `@Getter @Setter @NoArgsConstructor`. MapStruct usará setters directamente y el campo `tenantId` se hereda correctamente.

### Interfaces con el mismo nombre de método y distintos tipos de retorno
- Si un servicio implementa múltiples interfaces (ej: `GetSalonUseCase.getByTenantId() → SalonResponse` y `ManageBusinessHoursUseCase.getByTenantId() → List<BusinessHoursResponse>`), Java no permite la clase implementadora porque los métodos tienen la misma firma pero diferente tipo de retorno.
- **Solución**: Usar nombres distintos para métodos de distintas interfaces: `getByTenantId()` vs `getBusinessHours()`.

### SecurityConfig @ConditionalOnMissingBean para servicios con endpoints públicos
- rivoo-common define un `SecurityFilterChain` base que requiere autenticación para todo excepto actuator e internal.
- Servicios con endpoints públicos (salon-service: POST /api/v1/salons, GET /api/v1/salons/public/**) necesitan definir su propio `SecurityFilterChain`.
- **Solución**: Añadir `@ConditionalOnMissingBean(SecurityFilterChain.class)` en el bean de rivoo-common. El servicio crea su propio `SalonSecurityConfig` y la config de rivoo-common se retira.

### Hibernate dialect property no necesaria en SB4
- `hibernate.dialect: org.hibernate.dialect.MySQLDialect` en application-local.yml produce warning en Hibernate 7. Se eliminó de salon-service.

## Fase 2

### Keycloak Admin Client → RestClient
- `keycloak-admin-client` 26.x depende de Jackson 2.x + JAX-RS/RESTEasy. En SB4 (Jackson 3.x) causa conflictos de classpath. **Solución**: usar `RestClient` de Spring directamente contra la Keycloak Admin REST API. Zero dependencias extra.

### Jackson 2.x vs 3.x en Spring Boot 4
- SB4 incluye AMBAS versiones: `com.fasterxml.jackson` (2.x) y `tools.jackson` (3.x).
- `tools.jackson.annotation` puede no estar directamente en el compile classpath → usar `com.fasterxml.jackson.annotation` que sí está disponible.
- **CRITICAL**: Jackson 2.x `@JsonInclude(NON_NULL)` NO es reconocido por el serializer Jackson 3.x de SB4 RestClient. Los campos `null` SÍ se serializan. **Solución**: usar `Map<String, Object>` en vez de records para PUT bodies donde se necesita excluir campos null (ej: credentials en Keycloak user update).

### Keycloak 26 User Profile
- Custom user attributes (tenant_id, subscription_plan, salon_name) son **silenciosamente ignorados** si no están definidos en el User Profile del realm.
- PUT devuelve 204 pero los atributos NO se persisten.
- **Solución**: crear `rivoo-user-profile.json` y aplicar via `PUT /admin/realms/rivoo/users/profile`.
- No se puede incluir `userProfile` como campo top-level en el realm JSON import → da error "unable to read contents from stream".

### Keycloak Realm Import: Estrategia de 2 Pasos
- `POST /admin/realms` con un JSON completo NO crea los client scopes built-in (roles, profile, email, web-origins, etc.).
- Solo la creación de un realm vacío (`{"realm":"rivoo","enabled":true}`) genera los built-in scopes.
- **Estrategia correcta**:
  1. Crear realm vacío → genera built-in scopes automáticamente
  2. `POST /admin/realms/rivoo/partialImport` con el JSON completo → añade clients, roles, users
  3. Crear custom client scopes (tenant-info) via REST API
  4. Asignar scopes a clients via `PUT /admin/realms/rivoo/clients/{id}/default-client-scopes/{scopeId}`
  5. Aplicar user profile via REST API

### Keycloak Service Account Roles
- El service account de `salon-admin-cli` necesita roles de `realm-management`:
  - `manage-users` — CRUD de usuarios
  - `view-users` — listar usuarios
  - `query-users` — buscar usuarios por atributo
  - `view-realm` — leer roles del realm (necesario para `GET /admin/realms/rivoo/roles/{roleName}`)

### Keycloak User Update: Credentials Wiping Bug
- Al hacer PUT `/admin/realms/rivoo/users/{id}` con el body completo del user, si `credentials` es `null`, Keycloak **borra la contraseña** del usuario.
- **Solución**: usar un `Map<String, Object>` que deliberadamente excluye el campo `credentials` del body.

## Fase 1

### Spring Boot 4 Breaking Changes
1. **`spring-boot-starter-aop` renombrado a `spring-boot-starter-aspectj`** — cambio de nombre en SB4.
2. **`spring-cloud-starter-gateway` renombrado a `spring-cloud-gateway-server-webflux`** — en Spring Cloud 2025.1.x.
3. **Auto-configuration packages cambiados** en SB4:
   - `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` → `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
   - `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration` → `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
   - `org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration` → `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`
4. **Spring Cloud Gateway requiere `spring-boot-starter-webflux` explícito** — no lo incluye transitivamente.
5. **Spring Cloud Gateway requiere `spring-cloud-starter-loadbalancer`** — la auto-config referencia `ReactiveLoadBalancer` que sin esta dep da ClassNotFoundException.

### Keycloak Realm Import
- Los client scopes built-in (openid, profile, email) NO deben listarse en `defaultClientScopes` de los clients — Keycloak ya los asigna automáticamente. Listarlos produce warnings "Referenced client scope doesn't exist" (cosmético pero ruidoso).
- Usar `"defaultDefaultClientScopes": ["tenant-info"]` para añadir scopes custom a nivel de realm.

### Maven Multi-Module
- `mvn spring-boot:run -pl <module>` requiere que dependencias (rivoo-common) estén instaladas en el repo local. Ejecutar `mvn install -DskipTests` primero.

### Gateway Security (Reactive)
- api-gateway usa WebFlux → necesita `SecurityWebFilterChain` (reactiva), NO `SecurityFilterChain` (servlet).
- No puede usar rivoo-common SecurityConfig directamente (es servlet-based).
- Se creó `GatewaySecurityConfig.java` con `@EnableWebFluxSecurity` + `ServerHttpSecurity`.

### Hibernate Dialect Warning
- En SB4/Hibernate 7, no hace falta especificar `hibernate.dialect` explícitamente — se auto-detecta. El warning "MySQLDialect does not need to be specified" es informativo; se puede eliminar la property `hibernate.dialect` de application-local.yml (mejora menor).
