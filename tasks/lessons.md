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

### Endpoint público = dos reglas, no una (2026-08-27)

**Patrón del error:** al planificar la reserva pública asumí que quitar `@PreAuthorize`
más una regla en `GatewaySecurityConfig` bastaba para hacer público un endpoint.
No basta. Cada servicio tiene su propia cadena de Spring Security terminada en
`.anyRequest().authenticated()`, así que el endpoint seguía exigiendo JWT aunque
el gateway lo dejara pasar. Lo detectó el implementador, no el plan.

**Regla:** antes de dar por público un endpoint nuevo, comprobar las DOS capas:
1. `api-gateway/.../GatewaySecurityConfig.java`
2. La `*SecurityConfig` del servicio dueño — y si no tiene, la compartida de `rivoo-common`.
La prueba de que el patrón ya existía estaba a la vista: `AppointmentSecurityConfig:37`
ya llevaba el `permitAll` de `POST /book`, y `SalonSecurityConfig:38` el de
`GET /api/v1/salons/public/**`. Leer la config del servicio antes de planificar,
no después.

### Verificar quién implementa un puerto antes de decir dónde añadir un método (2026-08-27)

**Patrón del error:** el plan mandaba añadir `getPublicAvailableSlots` a
`AppointmentService` sin comprobar que `CheckAvailabilityUseCase` lo implementa
`AvailabilityService`. Habría creado dos beans del mismo puerto → inyección
ambigua → Spring no arranca.

**Regla:** en hexagonal, antes de asignar un método a una clase concreta, ejecutar
`grep -rln "implements.*<NombreDelPuerto>"` y escribir en el plan la clase que
salga, no la que suene razonable.

### Un `@Value` nuevo obliga a recorrer TODOS los perfiles, no solo el que estás tocando (2026-08-27)

**Patrón del error:** al despachar RP.5 (nuevo `StaffServiceAdapter` en salon-service)
le dije al implementador que añadiera `rivoo.services.staff-service.url` a
`application-local.yml` y que "comprobara si `application.yml` declara URLs de servicios".
Nunca mencioné `application-prod.yml`, que sí existe y sí declara el bloque
`rivoo.services`. El implementador hizo exactamente lo que le pedí.

Resultado: `StaffServiceAdapter` es un `@Component` con `@Value` **sin default**, así que
con `SPRING_PROFILES_ACTIVE=prod` Spring falla al resolver el placeholder durante el
arranque y **no levanta salon-service entero** — no solo la reserva pública, también toda
la API autenticada que hoy funciona. Un bean nuevo e inerte tumba el servicio antes de
aportar nada. Lo cazó el revisor, no el implementador ni yo.

**Regla:** al introducir un `@Value("${...}")` nuevo, listar los perfiles con
`ls <modulo>/src/main/resources/application*.yml` y nombrarlos TODOS explícitamente en el
prompt. Y buscar si la variable de entorno ya existe en el ecosistema antes de inventar
un nombre: `RIVOO_SERVICES_STAFF_SERVICE_URL` ya la usaba
`api-gateway/application-prod.yml:21`. Alternativa defensiva cuando el valor es opcional:
dar default (`@Value("${...:}")`) y fallar al usarlo, no al arrancar.

**Corolario sobre el reparto de culpa:** "el implementador hizo lo que le pedí" no es
excusa del prompt. Un prompt que enumera ficheros concretos convierte la lista en el
límite del trabajo; si la lista está incompleta, el agente no lo va a descubrir. Enumerar
ficheros va bien para acotar, pero hay que acompañarlo del criterio ("todos los perfiles",
"todos los llamantes") para que el agente pueda detectar lo que falta en mi lista.

### Estrechar un `catch` sin dejar red debajo es una regresión de disponibilidad (2026-08-27)

**Patrón del error:** una review señaló que el `catch (Exception)` de `StaffServiceAdapter`
era demasiado amplio (trataba igual un 5xx de staff-service que un 4xx de configuración
nuestra). Al despachar el arreglo pedí sustituirlo por capturas concretas —
`HttpClientErrorException`, `HttpServerErrorException`, `ResourceAccessException`— para
distinguir el nivel de log. No pedí ninguna captura de respaldo.

Resultado: quedó fuera `RestClientException`, que es la rama por la que salen los fallos de
**cuerpo** de respuesta. Tres escenarios reales pasaron de degradar a lista vacía con WARN a
propagar hasta el `@ExceptionHandler(Exception.class)` de rivoo-common y devolver **500 en la
pagina publica de reserva**: `200 text/html` de un proxy, JSON truncado por corte a mitad de
cuerpo, y objeto JSON donde se espera array (skew de despliegue). Lo demostró el revisor
provocando las tres, no leyendo el código.

**Regla:** al estrechar un `catch` en un punto de integración, las capturas concretas sirven
para **clasificar** (nivel de log, métrica, decisión), nunca para **sustituir** la red de
seguridad. La forma correcta es capturas específicas primero y un `catch` amplio al final que
preserve el comportamiento degradado. Antes de aprobar el estrechamiento, enumerar qué
excepciones puede lanzar la librería —no de memoria: mirar el jar de la versión resuelta— y
comprobar que cada una cae en alguna rama.

**Por qué me lo perdí:** asumí que las dos metas de la review (no enmascarar bugs de mapeo /
distinguir 4xx de 5xx) exigían quitar el `catch` amplio. No era así: el primer problema ya lo
resolvía sacar el mapeo fuera del `try`. Con el mapeo fuera, un `catch` amplio de respaldo no
enmascara nada. Cuando dos objetivos parezcan exigir un sacrificio, verificar que el conflicto
existe antes de pagarlo.

**Corolario, de los otros dos hallazgos de la misma review:** cerrar el hueco de un `@Value`
en el `application-*.yml` no cierra el problema, solo lo mueve al entorno. Falta comprobar las
superficies donde ese entorno se define: `infrastructure/railway/README-RAILWAY.md` (el runbook
que sigue el operador) y `docker-compose.yml`. En ambos faltaba
`RIVOO_SERVICES_STAFF_SERVICE_URL` para salon-service, asi que el arranque en prod seguia roto
para quien siguiera la documentacion, y la reserva publica no funciona en el stack de docker.
Extension de la leccion anterior sobre perfiles: yml, runbook y compose, los tres.

### `git commit` publica el INDICE entero, no las rutas que acabas de anadir (2026-08-27)

**Patron del error:** con dos agentes trabajando en paralelo sobre el mismo working tree,
cerre una tarea con `git add <rutas explicitas> && git commit -F -`. Di por hecho que
commiteaba solo esas rutas. No: `git commit` sin `-a` publica **todo lo que este en el
indice**, venga de donde venga. El otro agente tenia un `git mv` en vuelo —y `git mv` estadea
solo—, asi que mi commit `6eb273f` (un fix de config de despliegue) se llevo dentro dos
renombrados de DTO ajenos. Viajaron con el fichero renombrado pero el nombre de clase antiguo
dentro: ese commit **no compila aislado**, aunque HEAD si.

**Regla:** cuando haya mas de un agente sobre el mismo arbol, commitear siempre con pathspec:

    git commit -- <ruta> <ruta>          # solo esas rutas, ignora el resto del indice

y comprobar con `git show --stat <sha>` que el commit contiene lo que esperabas y nada mas.
Antes de commitear, `git status --short` no basta: hay que mirar tambien `git diff --cached
--name-only` para ver que hay estadeado por otros.

**Corolario de diseno:** el aislamiento no se pide por prompt, se impone por herramienta. Si
dos agentes van a commitear en paralelo, o se les da un worktree propio
(`Agent(isolation: "worktree")`), o se serializa el cierre de sus tareas. Pedirles "estadea
por ruta explicita" no protege del indice compartido, porque el problema no era su `git add`,
era mi `git commit`.

### El entorno de test de Spring Boot 4 no trae `@WebMvcTest` (2026-08-27)

**Dato verificado:** `spring-boot-test-autoconfigure-4.0.0.jar` solo contiene los slices
`json` y `jdbc`. Spring Boot 4 modularizo los slices de test en artefactos separados, asi que
`@WebMvcTest` y `@AutoConfigureMockMvc` **no estan en el classpath** de este repo.

**Regla:** para probar una capa web aqui, usar `MockMvcBuilders.standaloneSetup(controller)`
con los colaboradores mockeados, y `.setControllerAdvice(...)` si hace falta ejercitar el
manejo de excepciones. No es un apano: es la via disponible. Recordar sus limites —no carga
filtros de seguridad ni autoconfiguracion—, asi que no cuenta como cobertura de seguridad.

### Un comentario ya corregido miente con MAS autoridad (2026-08-27)

**Patron del error:** una review encontro que el javadoc de `BusinessHoursResponseJsonTest`
prometia una cobertura falsa. Despache el arreglo. La review siguiente comprobo el texto nuevo
y encontro **dos afirmaciones falsas nuevas**: cita una anotacion `@JsonProperty("isOpen")` que
no existe en el codigo (`grep` da cero) y un mecanismo `@JsonComponent` que **no esta en el
classpath del modulo** (falla al compilar). El proposito declarado de ese commit era eliminar
una afirmacion falsa, y entrego dos.

**Regla:** cuando se corrige un comentario por ser inexacto, la correccion necesita el MISMO
rigor de verificacion que se le exigiria a codigo: cada mecanismo citado, comprobado
(`grep` de la anotacion, compilar contra la clase). Un comentario que ya ha pasado una revision
lleva mas autoridad que el original, asi que equivocarse en la segunda vuelta es peor que en la
primera: el siguiente lector asume que alguien ya lo verifico.

**Corolario para mis prompts:** cuando encargue "arregla este comentario que miente", pedir
explicitamente que **verifique cada afirmacion que escriba** y que diga como la comprobo. Decir
"recorta la afirmacion a lo que el test garantiza" no basta: invita a reescribir de memoria.

### El invariante de no-fuga hay que verificarlo en TODA la superficie anonima (2026-08-27)

**Patron del error:** invertimos esfuerzo en que `GET /api/v1/salons/public/{slug}` devuelva un
404 indistinguible para "salon no existe" y "salon no ACTIVE", con test que lo fija. Un salto
mas alla, los OTROS dos endpoints anonimos —`GET /api/v1/appointments/public/availability` y
`POST /api/v1/appointments/book`— distinguen tres casos: 500 si el slug no existe, 422 con
detail "Salon is not active" si existe y esta suspendido, 200 si esta activo. El invariante
estaba protegido en un endpoint y roto en los otros dos.

**Regla:** una propiedad de no-fuga no es de un endpoint, es de la **superficie anonima
completa**. Al declararla, enumerar TODOS los endpoints `permitAll` (leer
`GatewaySecurityConfig` y cada `*SecurityConfig` de servicio) y comprobar la propiedad en cada
uno. Y ojo con las conversiones de excepcion entre servicios: aqui el 404 legitimo de
salon-service se convertia en `RuntimeException` dentro del adaptador de appointment-service y
salia como 500, creando la diferencia observable.

### En Spring Boot 4 la anotacion es `@JacksonComponent`, no `@JsonComponent` (2026-08-27)

**Dato verificado** listando `spring-boot-jackson-4.0.3.jar`: `org.springframework.boot.jackson.JsonComponent`
**ya no existe**; se llama `@JacksonComponent`. Encaja con el resto del cambio de Boot 4: el runtime
serializa con Jackson 3 (`tools.jackson.databind`), no con Jackson 2 (`com.fasterxml`), aunque el
classpath tenga ambos.

**Regla:** en este proyecto, cualquier codigo o comentario que mencione la integracion de Jackson
hay que contrastarlo con el jar de la version resuelta, no con la memoria de Boot 3. Ya ha causado
dos defectos aqui: un test de regresion escrito contra el `ObjectMapper` equivocado, y un javadoc
que citaba una anotacion inexistente.
