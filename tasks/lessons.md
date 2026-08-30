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

### Un test que mockea POR ENCIMA de la capa arreglada no prueba nada del arreglo (2026-08-27)

**Patron del error:** al cerrar el oraculo de enumeracion de salones se escribio
`AppointmentPublicEndpointsEnumerationTest`, cuyo nombre afirma la propiedad de seguridad
("unknownSlugAndSuspendedSalon_produceIdenticalResponseBodies"). Sus dos escenarios stubean **el
mismo mock** (`checkAvailabilityUseCase`) con **la misma** `new SalonNotFoundException(slug)`, y
el comentario dice "same slug, different underlying cause" cuando no hay ninguna causa distinta:
es el mismo `throw`. Compara una respuesta consigo misma.

El mock esta a nivel de **caso de uso**, o sea por encima de todo lo que el arreglo toco (el
manejo del 404 en `SalonServiceAdapter` y la comprobacion de estado en los dos servicios), asi
que los puentea. Prueba dura del revisor: **revirtiendo el arreglo al 100%, ese test pasa 2/2**
mientras fallan otros cinco. Lo mismo en `SalonExceptionHandlerOrderTest:114-145`.

**Regla:** antes de dar por cubierta una propiedad, localizar **en que capa vive el codigo que la
implementa** y comprobar que el mock esta POR DEBAJO de esa capa. Si el arreglo esta en un
adaptador REST, el doble tiene que ser el borde HTTP (`MockRestServiceServer`), no el puerto que
lo envuelve. Un test cuyo mock esta por encima del arreglo solo prueba el cableado que ya
funcionaba.

**Como detectarlo barato:** revertir el arreglo y ejecutar el test. Si sigue verde, no cubre el
arreglo, por muy convincente que sea su nombre. Es la misma mutacion que ya aplicamos al codigo;
hay que aplicarsela tambien a los tests que afirman propiedades de seguridad.

**Y el nombre agrava el dano:** un test llamado como la propiedad hace que el siguiente lector la
de por cubierta sin mirar. Si el test no la ejercita, o se arregla o se renombra a lo que de
verdad comprueba — nunca se deja el nombre optimista.

### Al renombrar un campo, comprobar que el nombre nuevo no afirma mas de lo que el valor garantiza (2026-08-27)

**Patron del error:** una review objeto que `degraded` no decia QUE estaba degradado. Decidi
renombrarlo a `catalogueUnavailable`. La review siguiente encontro que el nombre nuevo es **peor**:
el flag se calcula como `services.isEmpty() || employees.isEmpty()`, asi que puede valer `true`
mientras la respuesta lleva un array `services` real y con datos. "Unavailable" afirma una
totalidad que el propio payload contradice. `degraded`, mas vago, al menos no mentia.

El sintoma estaba a la vista y no lo mire: el test se llama
`getPublicBySlug_employeesCallFails_catalogueUnavailableButServicesStillArrive` — "unavailable
**pero** siguen llegando". Un nombre de test con un "pero" que contradice el campo es la senal.

**Regla:** antes de fijar el nombre de un campo de contrato, leer **como se calcula su valor** y
comprobar que el nombre es cierto en TODOS los casos que ese calculo admite. Un `||` entre dos
condiciones casi siempre significa que el estado es **parcial**, y un nombre en absoluto
("unavailable", "failed", "empty") sera falso en la mitad de las ramas. Si el estado es parcial,
o el nombre lo dice (`Incomplete`, `Partial`) o hay que desdoblar el campo.

**Corolario:** cuando el consumidor tiene una pantalla por cada parte —aqui `public-service-step`
y `public-employee-step` son pasos distintos del flujo— un solo flag obliga a las dos a mostrar
error aunque solo una haya fallado. Ahi desdoblar no es sobreingenieria: es lo que el consumidor
necesita para no tirar datos buenos.

### Un revert COMBINADO no prueba que cada sitio este cubierto (2026-08-27)

**Patron del error:** al encargar los tests anti-enumeracion puse como criterio de aceptacion
"revierte el arreglo de seguridad y comprueba que tus tests nuevos fallan". El implementador
revirtio los cuatro sitios a la vez, vio los tests en rojo, y lo dio por probado. Yo lo acepte.

El revisor hizo la matriz **sitio por sitio** (adaptador, check ACTIVE de disponibilidad, check
ACTIVE de reserva, filtro del loader) y encontro que en el revert combinado los dos tests de
appointment morian **en el escenario A**, o sea por la pata del adaptador. La cobertura de los
checks ACTIVE no quedaba demostrada por esa evidencia. Lo estaban —la matriz lo confirmo— pero
la prueba aportada no lo sostenia.

**Regla:** cuando un arreglo toca N sitios, el criterio de aceptacion es una **matriz de N
mutaciones independientes**, una por sitio, no un revert global. Un revert combinado solo prueba
que al menos uno de los N esta cubierto, y no dice cual. Escribirlo asi en el prompt:
"muta cada sitio por separado y dime que test muere con cada uno".

**Corolario:** exigir que la mutacion se verifique en el bytecode (`javap`) antes de creer el
resultado. En este repo, con `core.autocrlf=true`, una mutacion por `sed` sobre un arbol extraido
con `git archive` puede ser un **no-op silencioso**: el patron no casa, no se sustituye nada, los
tests pasan, y se concluye —al reves— que el test no cubre el arreglo. Ya le paso a dos revisores.

### El desajuste booleano backend/frontend es SISTEMICO, no incidental (2026-08-28)

**Patron del error:** van ya TRES casos del mismo defecto, encontrados de uno en uno:
1. `salon` `BusinessHoursResponse` emitia `open`, el frontend leia `isOpen`.
2. `staff` `WorkingHoursRequest`/`Response` emitian y esperaban `open`, el frontend usaba `isOpen`
   (roto en las dos direcciones: todo dia se guardaba cerrado y se leia cerrado).
3. `staff` `ServiceOfferingResponse:13` emite `active`, el frontend filtra por `s.isActive`
   → `undefined` → `filter` devuelve vacio → **la lista de servicios del wizard interno sale
   siempre vacia** (`wizard/service-step.tsx:20`, `staff/service-assignment.tsx:19`).

La causa comun: los **records de Java** exponen el componente con su propio nombre; no hay ninguna
`PropertyNamingStrategy` de Jackson en el repo; y el frontend hace `apiFetch<T>`, que es un **cast
sin validacion** — los genericos de TS se borran en ejecucion, asi que nada falla, el campo llega
`undefined` y el sintoma es una lista vacia o un booleano siempre falso. Silencioso por diseno en
las dos puntas.

**Regla:** dejar de arreglarlos de uno en uno. Cuando aparezca uno, **auditar todos los booleanos
de todos los DTO de respuesta** contra los tipos del frontend, de una vez. Un `grep` de
`boolean \w+` en los `application/dto/*Response.java` de cada servicio contra las interfaces de
`rivoo-frontend/src/types/` cierra la clase entera en minutos; buscarlos por sintoma cuesta meses
(el de `isOpen` llevaba desde el principio).

**Y la causa raiz de que sean invisibles:** `apiFetch<T>` promete un tipo que no verifica. Mientras
la frontera no valide (zod o equivalente), CUALQUIER divergencia de contrato pasa silenciosa. Es
tambien lo que convierte un dato corrupto en un crash: ver la leccion sobre `formatCurrency`.

### Un DTO de respuesta puede tener DOS consumidores: el frontend y otro servicio (2026-08-28)

**Patron del error:** renombre `WorkingHoursResponse.open` a `isOpen` en staff-service (commit
`9b8061b`) para que casara con lo que espera el frontend. Correcto para el frontend. Pero **ese
mismo DTO se sirve tambien a appointment-service** por `/api/internal/**`, y su consumidor
`WorkingHoursInternalDto` seguia declarando `open`. Arreglamos un contrato y rompimos el otro en
el mismo commit.

Consecuencia: Jackson 3 (Boot 4) trae `FAIL_ON_NULL_FOR_PRIMITIVES` **activado por defecto** —al
contrario que Jackson 2, que habria puesto `false` calladamente— asi que lanza
`MismatchedInputException`, `StaffServiceAdapter` lo envuelve en `RuntimeException`, y
`GET /api/v1/appointments/public/availability` devuelve **500**. Justo el endpoint de la feature.

**Y todos los tests pasaban**, porque los de appointment-service mockean `StaffServicePort` y
construyen el DTO a mano: la deserializacion real no se ejercita en ningun sitio. No existia
ningun test del adaptador para ese camino.

**Regla:** antes de renombrar un campo de un DTO de respuesta, buscar **todos** sus consumidores,
no solo el evidente. Dos poblaciones distintas:
- el frontend, que se encuentra mirando `rivoo-frontend/src/types/`;
- **otros servicios**, que se encuentran con `grep -rn "<NombreDelDto>\|<nombre-del-endpoint>"` y
  mirando los `*/infrastructure/adapter/out/rest/dto/` del monorepo.
El segundo grupo NO aparece en ninguna auditoria de tipos de TypeScript, asi que es invisible para
el reflejo de "compruebo el frontend".

**Regla de test:** un adaptador REST cuyo unico test mockea el puerto que lo envuelve no prueba la
deserializacion. Si el contrato viaja por JSON, el doble tiene que ser el **borde HTTP**
(`MockRestServiceServer`) y el payload tiene que copiarse **de la fuente productora**, no
escribirse de memoria.

## Todo agente se lanza fresco. Siempre. Implementador incluido.

**Patrón:** tras un veredicto BLOCK propuse que el mismo revisor verificase las
correcciones, y reanudé al implementador original para hacerlas, las dos veces
"porque ya tienen el contexto". El usuario corrigió ambas cosas.

**Por qué importa:** el contexto que se ahorra al reanudar es exactamente el sesgo
que se introduce. Un revisor que verifica sus propios hallazgos tiene incentivo a
darlos por buenos. Un implementador reanudado defiende sus decisiones anteriores en
vez de reconsiderarlas, y arrastra sus propias premisas equivocadas — en esta misma
sesión un implementador concluyó "no hay Docker, luego no hay MySQL" y no verificó
la migración; un agente fresco encontró el servidor escuchando en localhost:3306.

**Regla:** cada despacho es un agente nuevo, sin excepción y en los dos roles.
Implementación → implementador A, revisor B. Correcciones → implementador C,
revisor D. A los revisores se les dan los hallazgos previos como afirmaciones a
verificar, nunca como conclusiones establecidas.

**El coste lo paga el orquestador, y es el punto:** sin reanudación, cada brief ha
de ser autocontenido — hallazgos, ficheros, trampas del repo, criterio de
verificación. Eso es trabajo mío, no del agente. Nota: la skill `executing-plans`
dice "el implementador (mismo subagente) corrige"; esta regla la sobrescribe.

**NOTA (esta sesión):** la pasada de correcciones del portal se lanzó reanudando al
implementador original, antes de que existiera esta regla. Se deja terminar y la
verifica un revisor fresco; a partir de ahí, ninguna reanudación más.

## Los defectos se concentran en el alcance que nadie pidió

**Patrón:** pedí añadir UNA fila a la tabla de endpoints de `billing-service/CLAUDE.md`.
El implementador reescribió las dos tablas y afirmó cinco correcciones documentales por
iniciativa propia. La parte encargada (tests) salió impecable; de las cinco afirmaciones
voluntarias, cuatro eran erróneas, una de ellas grave.

**Por qué importa:** un `CLAUDE.md` es normativo en este repo — los agentes construyen a
partir de él. Una corrección equivocada que ha pasado por revisión pesa MÁS que la deriva
que sustituye. Y el alcance no pedido no lo cubre ningún criterio de verificación, porque
el brief no lo contemplaba.

**Regla:** en el brief, acotar explícitamente qué NO se toca. Si un agente encuentra
deriva adyacente, que la REPORTE, no que la arregle: entra como tarea propia con su
propia verificación. Al revisar, tratar todo lo que exceda el encargo como la zona de
mayor riesgo, no como celo profesional.

**Fallo técnico concreto del que salió todo:** dedujo el nivel de autorización de
`GET /plans` de la AUSENCIA de `@PreAuthorize`. En este stack eso no determina nada —
lo fija `authorizeHttpRequests` en el security config más el gateway. El endpoint es
anónimo (`permitAll` en los dos sitios) y el frontend lo llama sin token.

## Un test dirigido por React Query puede dar verde sin comprobar nada

**Patrón:** un test que siembra la caché de React Query, empuja un resultado nuevo y
afirma sobre el componente puede pasar **con el bug reintroducido**. React Query
notifica a sus observadores de forma asíncrona (microtarea de `notifyManager`), así
que el componente nunca se repinta: la identidad en caché cambia, pero el dato nuevo
no llega al formulario. La afirmación se evalúa sobre el render viejo.

**Por qué importa:** no falla, no avisa, y parece cobertura. Solo se detectó porque la
matriz de mutación exigía revertir ese sitio concreto y verlo en rojo — y no lo hizo.
Ni `act()` síncrono ni `await act(async ...)` lo vacían.

**Regla:** en un test que simule un refetch, primero **esperar a un campo que el
componente bajo prueba NO controle** (`await findByText(...)`) para demostrar que el
refetch aterrizó; solo entonces afirmar sobre lo que el usuario estaba editando. Los
tests dirigidos por props (rerender síncrono) no tienen este problema.

**Corolario:** la matriz de mutación por sitio no es burocracia. Aquí fue lo único que
distinguió un test que protege de un test que decora.

## `mvn -pl <modulo> test` sin `-am` da verde en falso

**Patron:** compilar un modulo suelto sin `-am` resuelve el `rivoo-common-0.1.0-SNAPSHOT.jar`
que haya en `~/.m2`, no el codigo de trabajo. Si el cambio esta en rivoo-common, el modulo
se compila y pasa contra la version ANTIGUA. Verde, y no prueba nada.

**Regla:** toda verificacion que toque rivoo-common va con `-am`, o con un
`mvn -o clean test` de reactor completo. Un resultado por modulo sin `-am` no es evidencia.

## Enumerar por `new Excepcion(` no encuentra las referencias cualificadas

**Patron:** para decidir por excepcion si su mensaje era publicable, el agente enumero los
sitios de lanzamiento con `grep "new BusinessValidationException"`. Se dejo tres:

    throw new com.rivoo.common.exception.BusinessValidationException("Client is not active");

Escritas con el nombre completo del paquete, no coinciden con el patron. Los tres eran
endpoints AUTENTICADOS, asi que la conclusion publicada — "el cambio solo afecta a
endpoints anonimos" — quedo escrita en el javadoc, en un test de politica y en el mensaje
del commit. Falsa en los tres sitios.

**Regla:** enumerar por el TIPO, no por la cadena de construccion. `grep -rn "NombreExcepcion"`
a secas, o mejor aun apoyarse en el compilador/IDE. Y cuando el resultado de una enumeracion
se convierta en justificacion escrita en el arbol, verificarla dos veces: una premisa falsa
que pasa revision se hereda.

**Corolario:** el alcance de una decision tomada en una clase BASE no se enumera mirando la
clase base. Hay que mirar tambien todo lo que hereda de ella.

## Los finales de linea son MIXTOS, no "el repo es CRLF"

**Patron:** durante toda una sesion instrui a los agentes con "core.autocrlf=true, los ficheros
son CRLF". Es falso a medias: `BillingController.java` es CRLF pero **todos los ficheros bajo
`src/test` son LF**. Un agente normalizo sus patrones a CRLF, encontro 2 de 4, y aborto dejando
el fichero intacto. Otro menos cuidadoso habria dejado un fichero medio editado.

**Por que importa:** el modo de fallo no es solo el no-op silencioso que ya conociamos (mutacion
que da verde en falso). Es tambien la edicion PARCIAL, que es peor: compila, pasa tests, y ha
cambiado la mitad de lo que pretendia.

**Regla:** detectar el final de linea POR FICHERO, nunca asumirlo. En cualquier script de
edicion o mutacion: usar `\r?\n` en los patrones, comprobar el hash antes y despues, y **abortar
sin escribir** si el numero de coincidencias no es el esperado. Nunca escribir a medias.

## Una lista blanca sobre CLASES no es una lista blanca sobre lo ALCANZABLE

**Patron:** cambiamos un guardian de seguridad de lista negra (seis nombres) a lista blanca
sobre los componentes de dos records. Mejor, y mato la mutacion que la lista negra no veia.
Pero el revisor encontro el hueco: si en vez de anadir un campo cambias el TIPO del componente
anidado por otro record que si lleva datos del inquilino, y anades un constructor delegador para
que ninguna llamada tenga que cambiar -> cero ficheros tocados, build verde, y el endpoint
anonimo publica el campo. Los tests siguen pasando **sobre una clase que ya nadie usa**.

**Por que importa:** un guardian que enumera CLASES FIJAS deja de alcanzar lo que protege en
cuanto alguien redirige el grafo. Y lo peor es que no se cae: pasa en verde, vacuamente. Es la
misma clase de fallo que el javadoc que afirmaba que un test inexistente protegia algo, un nivel
mas abajo.

**Regla:** un guardian de exposicion se ancla en la RAIZ y recorre el grafo
(`getRecordComponents()` transitivo desde el DTO de respuesta, o aplanar el JSON serializado a
todas las profundidades). Nunca en una lista de clases escrita a mano.

**Como detectarlo:** la mutacion que lo revela no es "anado un campo", es "**cambio el tipo del
componente**". Si tu matriz de mutacion solo anade y renombra campos, no esta probando el
alcance del guardian.

## Una matriz de mutacion sin control sobre el fuente SIN mutar no prueba nada

**Patron:** un revisor reporto "9 de 9 mutaciones muertas" y era **falso**. Habia pasado
`--reporter=basic`, que no existe en Vitest 4, asi que TODAS las ejecuciones morian al arrancar
con exit 1 — y el arnes leia ese 1 como "el test ha fallado, mutacion detectada". Lo cazo el
solo, ejecutando el arnes sobre el fuente sin mutar: exit 0, 8 tests. No es el primer caso en
esta sesion; otro agente leyo mal los codigos de salida por un problema de codificacion cp1252.

**Por que importa:** un exit code distinto de cero significa "algo fue mal", no "el test que me
importa fallo". Un arnes que confunde ambas cosas reporta cobertura perfecta sobre cero
cobertura, y es indistinguible de un buen resultado desde fuera.

**Regla:** toda matriz de mutacion incluye una fila de CONTROL sobre el fuente sin mutar, que
debe salir en VERDE. Y cada fila se valida por el recuento de tests fallados que imprime el
runner, no por el codigo de salida. Si el control falla, la matriz entera se descarta.

## Los agentes con worktree pueden romper el arbol del usuario al limpiar

**Patron:** varios revisores crearon worktrees desechables del frontend con un enlace a
`node_modules`. Al limpiar, uno se llevo por delante `node_modules/.bin` del arbol PRINCIPAL:
`npm test`, `npm run lint` y `npm run build` dejaron de funcionar y nadie se entero hasta que
un revisor posterior intento ejecutarlos.

**Regla:** en el brief de cualquier agente que use worktree en el frontend, prohibir enlazar o
reutilizar el `node_modules` del arbol principal — que haga su propio `npm ci` dentro del
worktree. Y al cerrar una tanda, comprobar que las herramientas del usuario siguen vivas
(`ls node_modules/.bin`), no solo que `git status` este limpio.

## Un test puede estar DEFENDIENDO el bug que vas a arreglar

**Patron:** al cerrar el callejon sin salida del paso de profesional, el arreglo choco con un
test existente que renderizaba `employees: []` con la bandera apagada —el estado roto exacto—
y exigia `getByText("Sin preferencia")`. Es decir, afirmaba como CORRECTO el avance al
calendario vacio. Hubo que reescribirlo, no solo anadir cobertura.

**Por que importa:** un brief que dice "no debilites lo que ya esta cubierto" empuja al agente
a respetar tests que quiza codifican el defecto. Y un test que falla al arreglar un bug parece
una regresion cuando es lo contrario.

**Regla:** antes de arreglar, buscar si algun test AFIRMA el comportamiento roto. Si lo hace,
cambiarlo forma parte del arreglo y hay que decirlo explicitamente en el informe, distinguiendolo
de debilitar cobertura legitima.

## CORREGIDA: la fragilidad del `userEvent` NO existe — y la leccion anterior era falsa

**Lo que escribi aqui primero, y era mentira:** que estos tests pulsan elementos con
`pointer-events-none` y que migrarlos a `userEvent` los dejaria verdes sin probar nada, porque
`userEvent` respeta `pointer-events`.

**Lo que un revisor demostro:** `vitest.config.ts:11` pone `css: false` y jsdom no carga
Tailwind, asi que `pointer-events-none` es un nombre de clase inerte y el estilo computado es
`auto`. La comprobacion de `userEvent` pasa y el click se dispara igual. Probado: con
`userEvent.setup()` por defecto, contra el codigo sano pasa y contra la mutacion que quita la
guarda FALLA. El test sigue mordiendo.

**La fragilidad real, mas estrecha:** si alguien pone `css: true` en la config, o convierte las
tarjetas apagadas en `<button disabled>` en vez de un `div` con clase, entonces si — un test que
solo afirme "el paso no ha avanzado" se quedaria verde sin ejercitar nada. `fireEvent` es inmune
a las dos cosas.

**La leccion de verdad, y es sobre mi:** escribi una leccion a partir del razonamiento de un
agente sin verificarlo, y la deje en el fichero que leen los siguientes. Una leccion falsa es
peor que ninguna, porque viene con autoridad. **Regla: lo que entre en `lessons.md` como hecho
tecnico se verifica antes de escribirlo, igual que un comentario en el codigo.**

## Una decision ya tomada por el usuario no se re-propone

**Patron:** el usuario dijo "Stripe por ahora sera simulacro, ya lo configuraremos en el
futuro, sera lo ultimo". Despues de eso recomende tres veces empezar por el webhook de Stripe,
porque un revisor lo encontro y era el hallazgo mas llamativo de la sesion.

**Por que importa:** la gravedad tecnica de un hallazgo no reordena el plan del usuario.
Un agujero en un sistema que no esta conectado no es urgente: es una PRECONDICION del dia que
se conecte. Insistir gasta la atencion del usuario y le hace repetirse.

**Regla:** cuando aparezca un hallazgo grave sobre algo que el usuario ya ha aplazado
explicitamente, se registra ATADO a la tarea que lo desbloquea ("al conectar Stripe:
verificar la firma"), no como prioridad suelta. Y no se vuelve a proponer como "lo primero"
salvo que el usuario cambie el plan.

## No devolver al usuario decisiones que son mias

**Patron:** acumule tres preguntas para el usuario —separar o no la regla de antelacion,
que arreglar primero, si rebasar una rama— sobre las tres YA tenia recomendacion formada y
argumentada. El usuario respondio: "decisiones de que? has de solucionar los problemas, yo no
tengo que solucionar nada, sigue tu recomendacion honesta".

**Por que importa:** presentar una recomendacion y despues pedir permiso para seguirla no es
prudencia, es devolverle el trabajo. Le obliga a reconstruir un contexto tecnico que yo ya
tengo, para llegar a la conclusion que yo ya le he dado.

**Regla:** si puedo formular una recomendacion con su razonamiento, la EJECUTO y la explico.
Se pregunta solo cuando (a) la decision depende de informacion que solo tiene el usuario
—prioridad de negocio, apetito de riesgo, planes futuros—, o (b) las opciones llevan a
trabajos materialmente distintos y no hay una claramente mejor.

**Prueba rapida antes de preguntar:** si ya se cual recomendaria y por que, no es una pregunta.
Es un anuncio.

## Surefire fusiona metodos con el mismo nombre en @Nested distintos

**Patron:** el agregado final de Surefire imprimio `Tests run: 111, Failures: 8` mientras sus
propias lineas por clase Y el XML decian 112 / 9. Causa: dos metodos con el MISMO nombre en
dos clases `@Nested` distintas del mismo fichero se fusionan en una sola entrada
`Run 1 / Run 2`, como si fuera un reintento por flake. Un test que falla desaparece del recuento.

**Por que importa:** es el quinto mecanismo de verde falso que aparece en este proyecto. Los
otros cuatro: un flag de runner inexistente, un fallo de codificacion cp1252, leer el bloque
`Results:` del modulo equivocado, y un regex que pegaba tests auto-cerrados al siguiente.
Todos daban "verde" o "muerto" cuando no lo era.

**Regla:** nombres de test unicos POR FICHERO, no por clase anidada. Y contar siempre los
elementos `<testcase>` del XML contrastandolos con la linea `Results:` impresa; si discrepan,
la fila de la matriz es invalida hasta averiguar por que.

## Un snippet de plan que declara variables pero no la condicion de render esta incompleto

**Patron:** escribi el arreglo del portero como tres expresiones derivadas (`authReady`,
`needsOnboarding`, `unavailable`) y una frase en prosa: "con `!authReady` se pinta el spinner".
El revisor demostro que ninguna de las tres expresiones se vuelve cierta cuando `authReady` es
falso, asi que con la composicion natural del render la sesion muerta acabaria pintando los
hijos: exactamente el fallo que la variable existia para evitar. La prosa lo decia; el codigo
que se copia, no.

**Por que importa:** el implementador copia el bloque de codigo, no el parrafo de al lado. Una
variable declarada y no cableada es peor que no declararla, porque parece cubierta.

**Regla:** si un plan cambia una condicion de guarda, el snippet incluye la CADENA DE RENDER
completa (`if (...) return X; if (...) return Y; return children`), no solo los booleanos.
Y cada caso de test enumerado debe corresponder a una rama visible del snippet.

## Anadir un campo al dominio rompe en silencio los fakes con @Builder de lista explicita

**Patron:** anadir `onboardingCompletedAt` a `Salon` (que es `@Builder` con lista de campos
explicita) no basta. El store en memoria de los tests reconstruye el objeto con un `copyOf`
que enumera los campos a mano: el campo nuevo se descarta en cada `save` y cada
`findByTenantId`. El compare-and-set escribiria bien y la lectura devolveria `null`, y el
sintoma apuntaria al servicio, no al fake.

**Por que importa:** MapStruct empareja por nombre y no hay que tocarlo — eso hace creer que
"anadir un campo" es gratis en todas las capas. Los fakes escritos a mano son la excepcion, y
fallan sin un solo error de compilacion.

**Regla:** al anadir un campo a un modelo de dominio, `grep` por implementaciones a mano del
puerto de persistencia y por cualquier `copyOf` / `builder()` con lista explicita en los tests,
y actualizarlas EN EL MISMO PASO. Nunca cuando fallen los tests.

## Escribir en la cache de React Query antes de navegar no basta con refetchOnWindowFocus

**Patron:** propuse `setQueryData(clave, respuesta)` antes de `router.push` para que la
pantalla destino no leyera un dato rancio. Correcto pero insuficiente: `refetchOnWindowFocus`
esta en `true` global y la pantalla de origen monta la misma query, asi que puede haber un
refetch EN VUELO que resuelve despues del `setQueryData` y pisa el dato con el payload viejo.

**Por que importa:** la ventana no es teorica — basta que la pantalla lleve mas de `staleTime`
abierta y el usuario cambie de pestana y vuelva antes de pulsar el boton.

**Regla:** `await queryClient.cancelQueries({ queryKey })` ANTES del `setQueryData`, siempre que
la escritura decida una navegacion. Y `["a"]` sirve de prefijo para `invalidateQueries` pero
NO es clave valida para `setQueryData`: ahi la clave es la exacta.

## Un encargo que pide un aspecto y prohibe tocar quien lo pinta es contradictorio

**Patron:** el encargo de las cinco pantallas describia al detalle la anatomia de las filas de
horario (interruptor, selectores sin cromo nativo, separador "a", rejilla de escritorio) y en
el parrafo siguiente prohibia tocar `working-hours-editor.tsx`, que es el UNICO componente que
renderiza esas filas. El implementador no improviso: hizo lo que podia, y devolvio la
contradiccion escrita. Bien hecho por su parte; el fallo era del encargo.

**Por que importa:** la prohibicion tenia un motivo real (ese editor tiene logica sutil de
adopcion de props que no habia que tocar) pero se redacto como "no toques el fichero" en vez de
"no toques ESE mecanismo". Un implementador obediente entrega una pantalla a medias, y uno
desobediente rompe la logica sutil.

**Regla:** antes de prohibir un fichero, comprobar si ese fichero es el que produce lo que
estas pidiendo. Si lo es, la prohibicion se acota al mecanismo concreto ("no toques la logica
de sincronizacion de `:53-58`") y se autoriza el resto. Y si el componente lo comparten varios
consumidores, mirar PRIMERO el artboard del otro consumidor: aqui `Horario.dc.html` resulto
tener la misma anatomia, asi que restilar servia a los dos y la prohibicion sobraba.

## Un comentario que promete una cobertura inexistente apaga la alarma del siguiente

**Patron:** verifique a mano, con un ROLLBACK contra MySQL, que el compare-and-set de la marca
de alta era idempotente y estaba acotado por tenant. Funciono. Y luego dicte un javadoc que
decia que la atomicidad "se verifica por separado, contra MySQL". **Esa verificacion no existia
como test**: era un comando que ejecute yo una vez y no deje escrito en ninguna parte.

Un refutador que muto el codigo de verdad demostro lo que eso costaba: quitar el
`AND ... IS NULL` del JPQL, o hacer que el JPQL no escriba el campo, o que el adaptador nunca
llame al repositorio — las tres dejan la suite entera en verde. La escritura que decide si el
usuario puede entrar en la aplicacion no tenia ninguna cobertura, y el javadoc estaba
diciendole al siguiente revisor que si la tenia.

**Por que importa:** es peor que no comentar nada. Un hueco visible se acaba tapando; un hueco
que un comentario declara tapado no lo mira nadie. Es el mismo mecanismo que los "verdes
falsos" que llevo toda la sesion catalogando, pero en prosa en vez de en una herramienta.

**Regla:** una verificacion manual NO se cita en un comentario como si fuera cobertura. O se
convierte en un test —aunque sea `@Tag("integration")` y no corra en la build normal—, o el
comentario dice explicitamente **que ese camino esta sin cubrir**. Al escribir el javadoc,
preguntarse: "si borro la linea de codigo que esto describe, ¿se pone algo rojo?". Si la
respuesta es no, el comentario no puede afirmar que si.

**Corolario sobre los fakes:** un test cuyo doble reimplementa el mecanismo bajo prueba
(un `synchronized` que ES el compare-and-set) verifica el doble, no el codigo. Sirve para fijar
que el servicio no hace leer-decidir-escribir; no sirve para nada mas, y el javadoc debe
acotarlo asi.

## Cambiar `isLoading` por "no hay datos" arregla un fallo y abre otro

**Patron:** una guarda escrita como `if (isLoading) esqueleto` no protege nada cuando la consulta
esta DESHABILITADA: en React Query v5 `isLoading = isPending && isFetching`, asi que una query
con `enabled:false` tiene `isLoading` en **false**. El arreglo evidente es cambiarla por
`data === undefined`. Y ahi esta la trampa: con reintentos limitados, un 500 deja `data` en
`undefined` **para siempre**, o sea esqueleto perpetuo y boton muerto. Se cambia una pantalla
que pierde datos por una pantalla de la que no se puede salir.

**Por que importa:** en este bloque la distincion mordio TRES veces (el portero, el paso de
horarios, y las otras dos pantallas que comparten ese editor), y el arreglo de la tercera creo
la trampa nueva. Es el mismo error de fondo: tratar un booleano de React Query como si
respondiera a la pregunta que uno tiene en la cabeza.

**Regla:** una consulta tiene CUATRO estados que hay que cubrir explicitamente, no dos:
(1) todavia no se pide —deshabilitada, sesion a medias—, (2) pidiendo, (3) fallo, (4) datos.
Una guarda que solo distingue "hay datos / no hay datos" siempre deja uno de los otros tres sin
salida. Al escribir la guarda, preguntarse: **"si esta peticion falla para siempre, ¿que ve el
usuario y como sale de ahi?"**. Si la respuesta es un esqueleto, falta la rama de error con
reintento.

---

## Una entrada sin tachar en `todo.md` no es una entrada viva

**Patron:** el 2026-08-29 recomende al usuario priorizar el bucle infinito de
`AvailabilityService` "porque una peticion anonima cuelga un hilo". Estaba corregido desde el
dia anterior en `07e14fb`. Dos mensajes despues volvi a hacerlo con la ventana de 1 hora, tambien
cerrada, en `11d099d` + `4b7646e`. Las dos entradas seguian con la casilla vacia porque nadie
las tacho al arreglarlas, y yo lei la casilla como si fuera el estado del codigo.

**Por que importa:** el usuario tuvo que corregirme las dos veces ("pero de que bucle hablas??",
"hemos dicho antes que ya lo habiamos solucionado"). Peor que perder tiempo: le propuse un ORDEN
DE TRABAJO entero —adelantar disponibilidad al detalle de cita— construido sobre un hecho falso.
Una recomendacion de prioridad es exactamente donde mas caro sale equivocarse, porque el usuario
la ejecuta sin volver a comprobarla.

**Regla:** `tasks/todo.md` registra INTENCION, no ESTADO. Antes de citar cualquier entrada suya
como trabajo pendiente —y con mas motivo antes de priorizarla— verificarla contra el codigo o
contra `git log -- <fichero>`. Si la entrada cita `fichero:lineas`, abrir esas lineas. Y al
cerrar un arreglo, tachar la entrada en el mismo commit: dejarla viva convierte el fichero en
una trampa para la siguiente sesion.

---

## El hallazgo de un subagente vale para el fichero que cito, no para el que se le parece

**Patron:** un barrido reporto que "los empleados inactivos se filtran y desaparecen" en el
asistente de nueva cita (`src/components/appointments/wizard/employee-step.tsx`). Escribi ese
hallazgo en el plan del carril B como si fuera del paso publico
(`src/components/booking/public-employee-step.tsx`), que es otro fichero y que YA atenua
correctamente en `:52` con un test que lo fija en `:172-173`. El revisor del plan lo cazo.

**Por que importa:** el brief le habria pedido a un implementador "arregla el filtrado" sobre
codigo que funciona. En el mejor caso pierde el tiempo; en el peor reescribe una rama correcta y
rompe el test que la protegia. Y el fallo es invisible: los dos ficheros hacen lo mismo, se
llaman casi igual y viven en carpetas hermanas.

**Regla:** cuando un subagente devuelve un hallazgo, lo que se copia al plan es su
`fichero:lineas`, no su descripcion. Antes de convertirlo en tarea, abrir ESE fichero y
confirmar que es el que se va a modificar. Si dos componentes tienen nombres parecidos
(`employee-step` / `public-employee-step`), asumir que el hallazgo es del otro hasta demostrar
lo contrario.

---

## No afirmar en un brief que un campo existe sin haberlo abierto

**Patron:** escribi en el brief del paso 1 "el campo de categoria existe en el tipo del servicio
— compruebalo en `src/types/salon.ts`". `ServicePublic` no tiene `category`: cero apariciones en
todo el fichero. El campo vive en `ServiceOffering` (`src/types/service.ts`), el tipo privado del
backoffice, y nunca se propaga al endpoint publico. El implementador lo comprobo y me lo devolvio.

**Por que importa:** el propio brief le pedia agrupar los servicios por categoria. Un agente
menos disciplinado habria inferido la categoria del nombre del servicio para cumplir el encargo,
y eso se ve bien en la captura y es mentira en produccion. La instruccion "si algo de este brief
resulta falso, dilo en vez de improvisar" es lo unico que lo evito.

**Regla:** una afirmacion sobre la forma de un tipo en un brief es una cita, y las citas se
verifican: `grep` antes de escribirla. Y mantener en todos los briefs la clausula que autoriza
al implementador a devolver el brief en vez de cumplirlo — es la red que recoge los errores que
esta regla no llegue a evitar.

## 2026-08-29 — Una cita de artboard leida en un volcado desplazado es una cita falsa

**Patron.** En el plan del shell de escritorio cite la tarjeta de usuario como
`EquipoDesktop.dc.html:83-89`. Esta en `:71-77`. Lo que hay en `:83-89` es el CTA
"Anadir empleado". Los VALORES que transcribi (34px, `#F6E7E0`, 12px/700, 11px `#9A8A7E`)
eran correctos, porque los lei de verdad; lo que estaba mal era el numero de linea, que
saque de un `sed` anterior con otro desplazamiento y no volvi a comprobar.

**Por que importa mas de lo que parece.** El implementador hace lo correcto —abrir la
referencia— y aterriza en un boton terracota. La cita equivocada castiga justo al que
verifica.

**Regla.** Toda cita `fichero:linea` que entre en un plan se comprueba releyendo ESE rango
concreto (`sed -n 'N,Mp'`), no el volcado del que salio. Si el rango no contiene lo que
dice la fila, la fila esta mal.

## 2026-08-29 — No generalizar desde la muestra a la pantalla que vas a tocar

**Patron.** Mire cuatro paginas (`clients`, `settings`, `today`, `staff`) para ver como
maquetaban, y escribi en el plan "cada pagina trae su propio `p-4 md:py-6` y su propio
`<h1>`". Tres lo tienen. **`staff/page.tsx` NO tiene ningun `<h1>`** — y era justo la que
el plan elegia como pantalla piloto. De ahi salio una contradiccion irresoluble: el plan
prometia "ni un pixel de diferencia en movil" y a la vez que `PageShell` pintara un `<h1>`
que en esa pantalla no existe.

**Regla.** Cuando una generalizacion sostenga una decision, comprobarla EXPLICITAMENTE en
el caso concreto que la decision toca, no solo en la muestra que la sugirio. `grep -n "h1"`
sobre el fichero concreto cuesta un segundo.

## 2026-08-29 — El inventario exhaustivo va ANTES de la primera version del plan

**Patron.** El plan del shell de escritorio necesito CUATRO versiones y cuatro revisiones. En
cada una yo citaba los artboards que hacian falta para defender la decision de esa version, y
la revision encontraba los que no habia mirado. La cuarta destapo lo definitivo: **9 de las 13
pantallas tienen titulo distinto en movil y en escritorio** (`/today` es "Bella Vista" en movil
y "Buenos dias, Maria" en escritorio; las SEIS subpaginas de ajustes son "Ajustes" en
escritorio y su propio nombre con flecha atras en movil). Todo mi contrato —`title: string`
unico para los dos anchos— era imposible desde la primera linea, y se tardaron cuatro rondas en
verlo porque nunca abri las 26 cabeceras de golpe.

**Coste.** Cuatro ciclos de escritura + revision para un dato que se saca con dos comandos
`grep` de treinta segundos.

**Regla.** Cuando un plan toque N pantallas, el PRIMER paso —antes de escribir una sola
decision— es volcar el inventario de las N (movil Y escritorio) en una tabla: titulo, acciones,
si lleva volver. Las decisiones se derivan de la tabla completa; no se defienden con los tres
ejemplos que uno miro primero. Si la tabla no cabe, el bloque es demasiado grande.

## 2026-08-29 — Un plan con el mismo hecho repetido en 8 sitios no se puede corregir editando

**Patron.** El plan del shell de escritorio acumulo CINCO revisiones y cinco BLOCK. La
distribucion de los fallos cuenta la historia: ronda 1, tres de diseno y uno de propagacion;
ronda 5, **cero de diseno y seis de propagacion**. El diseno convergio pronto. Lo que no
convergia era el documento: 806 lineas donde "56px" aparecia 19 veces, "AppHeader" 9,
"git commit" 10. Cada decision vivia en ocho secciones (objetivo, decisiones, inventario
visual, tabla de ficheros, fases, tareas, orden de ejecucion, dependencias), asi que cambiarla
exigia ocho ediciones a mano. Corriges siete, se te escapa la octava, y la revision siguiente
la encuentra. El BLOCK critico de la quinta ronda fue exactamente eso: `AppHeader` desaparecia
en la decision D2c y seguia montado en el codigo de la tarea T6.

**El error de fondo no fue ninguna de las correcciones: fue seguir parcheando un formato con
tasa de fallo del 100%.**

**Reglas.**
1. En un plan, cada hecho vive en UN sitio. Las tareas se REFIEREN a la decision
   (`ver D2b`), no la repiten. Si un valor aparece dos veces, va a divergir.
2. Cuando dos revisiones seguidas devuelvan sobre todo fallos de PROPAGACION y no de
   contenido, dejar de editar: reescribir el documento entero de una pasada. Parchear ya no
   converge.
3. No declarar un plan "listo" citando la recomendacion del revisor. Esa recomendacion es una
   afirmacion mas, y la regla de CLAUDE.md §3 —la sintesis de un subagente es una afirmacion a
   verificar— no deja de aplicar porque la afirmacion sea agradable.

## 2026-08-29 — Un defecto encontrado en una pantalla hay que buscarlo en sus gemelas

**Patron.** Revisando el bloque del shell descubri que `/staff/[id]` pintaba "Editar" y
"Desactivar" en la cabecera movil, cuando el artboard los dibuja como botones-icono de 36x36
en el CUERPO. Lo verifique contra `DetalleEmpleado.dc.html:57,60` y mande la correccion.
**No comprobe la pantalla gemela.** `/clients/[id]` tenia exactamente el mismo defecto —
`DetalleCliente.dc.html:33-38` dibuja la cabecera movil vacia y `:47-49` pone el lapiz de
36x36 en el cuerpo— y ademas, al pedirle a ese agente que quitara un boton que sobraba, se
perdio por el camino el boton-icono del cuerpo que si existia antes del bloque.

Resultado: dos pantallas hermanas quedaron con tratamientos opuestos, y la que fallaba es
justo una de las ocho sin test de pantalla.

**Regla.** Cuando una revision destape un defecto en una pantalla, antes de despachar el
arreglo: listar sus gemelas (detalle/detalle, formulario/formulario, listado/listado) y
comprobar el mismo punto en todas. El arreglo se manda para el conjunto, no para la que
salio en el informe. Cuesta un `grep`; no hacerlo cuesta otra ronda de revision entera.

## Un negativo no se afirma desde una comprobacion que no lo cubre

**Patron.** En el inventario del bloque 3 escribi "no hay NI UN test del calendario: ni de la
pagina ni de los cinco componentes ni de `lib/utils/calendar.ts`". El `ls` que ejecute solo
miraba `src/components/calendar/*.test.tsx` y `src/app/(app)/calendar/*.test.tsx`. Nunca mire
`src/lib/utils/calendar.test.ts`, que existia desde `a34c157` con 100 lineas — y tres de sus
aserciones fijaban el alto de bloque sin el canalon de 4px, o sea que contradecian la tarea.
El dato falso entro en el plan y de ahi al brief del implementador, que se encontro el fichero
y tuvo que decidir por su cuenta si borrarlo.

**Regla.** Un "no existe" solo se afirma sobre las rutas que el comando ha mirado de verdad.
Si la frase enumera tres cosas, la comprobacion enumera las tres. Y para un negativo amplio,
la herramienta es una busqueda por patron sobre todo el arbol (`Glob **/calendar*.test.*`),
no un `ls` de dos carpetas elegidas de memoria.

**Coste medido.** El implementador de la T2 lo detecto y lo resolvio bien, pero solo porque
abrio el fichero antes de escribir. Un agente que se hubiera fiado del brief habria borrado
tests validos o dejado tres aserciones en rojo culpando a su propio codigo.

## Transcribir un artboard es leer la cascada y los heredados, no la lista de declaraciones

**Patron.** En el inventario del bloque 3 transcribi los valores de los artboards leyendo las
declaraciones de estilo en linea una por una. Dos cosas se escaparon, y las dos las encontro el
revisor de fidelidad, no yo:

1. **La cascada dentro de la propia declaracion.** `CalendarioDesktop.dc.html:168` pone
   `border-left: 3px solid #C08A2E; border-color: #E8D3A6;` EN ESE ORDEN, asi que `border-color`
   pisa a `border-left-color` y lo que ese fichero pinta de verdad es `#E8D3A6`. Mi §1.2 registro
   `#C08A2E`. Se salvo por pura suerte: el artboard MOVIL invierte el orden y ahi si gana
   `#C08A2E`, que es lo que el implementador siguio.

2. **Lo que el artboard NO declara tambien es un valor.** El `body` de los artboards no declara
   `line-height`, o sea `normal` (~1.25 en Schibsted Grotesk). La preflight de Tailwind del repo
   impone `line-height: 1.5`. Como yo solo transcribi propiedades DECLARADAS, esa diferencia no
   entro en ninguna tabla, y el bloque compacto de 30 min quedo con 46,75px de contenido dentro
   de una caja de 44px con `overflow: hidden` — tres de los ocho bloques dibujados en escritorio,
   cortados por abajo. La aritmetica del artboard cuadra al decimal con `normal`:
   `6 + 13·1.25 + 2 + 11·1.25 + 6 = 44,00`.

**Regla.** Al inventariar un artboard: (a) dentro de cada `style`, la ultima declaracion que toca
una propiedad es la que vale — leer el bloque entero antes de anotar un color de borde; (b) para
cada propiedad que AFECTE AL TAMANO (`line-height`, `box-sizing`, `font-family`), comprobar que
hereda el artboard frente a que impone el reset del repo, y anotar la diferencia aunque el
artboard no la escriba. Y cuando el artboard fija una altura exacta a una caja de texto, **sumar
las cajas de linea y comprobar que cuadra**: si cuadra al decimal, ese es el `line-height` real.

**Senal de que estaba mal.** El implementador puso `leading-tight` en el nombre y dejo las otras
dos lineas heredando 1.5, dentro del MISMO componente. Una regla aplicada a medias en un solo
fichero es sintoma de que el dato nunca estuvo en la especificacion.

## Un mock puesto en `beforeEach` y nunca sobrescrito apaga el codigo que dice cubrir

**Patron.** En el bloque 3, `page.test.tsx:155` hacia
`useEmployeesWorkingHoursMock.mockReturnValue({ data: {} })` en el `beforeEach`, y NINGUN test lo
sobrescribia. Con el mapa vacio, `breaks` sale todo `null` y `workingHours[...]` siempre
`undefined`, asi que **todo el cableado de descansos y hueco libre dejaba de ejecutarse**. La
auditoria por mutacion lo midio: diez mutaciones supervivientes en un solo fichero, cinco de
ellas con una sola causa. Se podia borrar `breaks={breaks}` y `freeSlot={freeSlot}` de la pantalla
y la suite seguia verde. Los doce tests de ese fichero pasaban y daban confianza sobre codigo que
no llegaban a tocar.

**Regla.** Un mock que devuelve la forma VACIA (`{}`, `[]`, `null`) es un mock que apaga una rama.
Vale como valor por defecto, pero entonces **al menos un test tiene que sobrescribirlo con datos
de verdad**. Al escribir el fichero de test, listar que props salen de ese mock y comprobar que
cada una llega a valer algo en algun caso. Si ninguna lo hace, esa parte de la pantalla no esta
probada por mucho que el fichero se llame como ella.

**Corolario, del mismo informe.** Un componente cubierto no es un comportamiento cubierto:
`date-navigator.test.tsx` probaba que los callbacks se disparan, y aun asi se podia hacer que el
boton "Dia siguiente" RETROCEDIERA con la suite entera verde, porque nadie probaba que la pantalla
cambiara de dia. Cubrir la pieza y cubrir el cableado son dos trabajos distintos.

**Como se detecta.** Solo mutando. Leer el test y opinar no lo encuentra: hay que romper el codigo
de produccion a proposito y ver si algo se pone rojo. En este bloque, 51 mutaciones dieron 20
supervivientes que ninguna lectura habia senalado.

## El dato del brief se copia de la spec, no se recuerda

**Patron.** En el bloque 3 escribi en el plan (D12) que **el artboard movil dibuja el estado
FILTRADO**, con la pildora "Laura" seleccionada en `Calendario.dc.html:52-55`. Correcto. Horas
despues, redactando el encargo de una correccion, escribi de memoria lo contrario: *"un filtro de
pildoras cuyo estado INICIAL es 'Todos', que es ademas el que dibuja el artboard"*. Los
implementadores lo tomaron como hecho verificado —que es lo que un brief promete ser— y lo
grabaron **cuatro veces en el fuente**, en comentarios que citan `:51` como prueba. `:51` es la
pildora "Todos" en REPOSO; la seleccionada es la de al lado. Lo encontro el verificador de la
segunda ronda, tres olas despues.

**Regla.** Todo dato que entre en un brief se COPIA de §1 del plan o del artboard abierto en ese
momento; no se escribe de memoria, por fresco que parezca. Si al redactar el brief hace falta un
valor que no esta en la spec, primero se anade a la spec y luego se copia. Un brief es una fuente
secundaria, y su unica autoridad es la fidelidad de la copia.

**Y un informe de revision TAMBIEN es fuente secundaria.** Reincidi en el mismo bloque: el
verificador escribio "la rejilla movil pinta los tres bloques de Laura y ninguno de los otros
CINCO", yo lo copie al plan y al brief sin contar, y de ahi paso al fuente en tres sitios. Son
SEIS: el artboard tiene nueve bloques, tres por columna, y Laura aporta dos citas mas un
descanso. La frase ademas mezclaba bloques con citas para que cuadrase la resta. Un numero se
CUENTA sobre la fuente; no se hereda de quien lo conto antes, por competente que sea — y este
verificador lo era, encontro el bloqueante de la ronda.

**Y una casilla sin marcar en `todo.md` tampoco es prueba de nada.** Tercera reincidencia, ya
fuera del bloque: le presente al usuario el onboarding reanudable como PENDIENTE porque sus
casillas ON.1-ON.8 seguian sin marcar. Estaba hecho — migracion `V4`, el flag en
`SalonResponse`, `onboarding-gate.tsx:43` decidiendo por el, `salon-setup` borrada — y me lo
corrigio el usuario. Un checklist se queda viejo justo cuando el trabajo va rapido, que es
cuando mas se consulta.
**Antes de decirle a alguien que algo esta pendiente, se mira el CODIGO**, no la lista. La
lista sirve para saber que MIRAR, no para saber que hay.

**Por que duele mas que un error normal.** Un dato equivocado en el codigo lo caza un test o una
revision. Un dato equivocado en un BRIEF se convierte en comentarios que dicen "el artboard dibuja
X" citando linea y fichero: adquiere apariencia de verificado, sobrevive a las revisiones que
confian en el, y contamina la siguiente decision que se apoye en el.

Ver [[artboard-es-la-fuente]].

## El nombre del fichero no es el nombre del artboard: manda `canvas.json`

**Patron.** Dije "no existe `Hoy.dc.html`" tras un `ls design/`, y lo presente como si faltara
la pantalla. El artboard de "Hoy" existe en los dos anchos: su fichero es `Main.dc.html`, y
`canvas.json` le pone el titulo `'Hoy'` (igual que a `HoyDesktop.dc.html`, `'Hoy - escritorio'`).
Lo vio el usuario en el canvas publicado, donde los titulos SI se leen. `Main.dc.html` es ademas
el fichero de entrada por convencion de la herramienta, asi que ese caso se repetira.

**Regla.** Para saber si una pantalla esta dibujada, se mira `design/canvas.json` — el mapa
fichero -> titulo -> pagina —, no el listado de ficheros. Y al informar: "no encuentro un fichero
llamado X" y "esa pantalla no esta disenada" son afirmaciones distintas; la primera no autoriza
la segunda.

Ver [[artboard-es-la-fuente]].

---

## Las puertas de verificacion son las del PROYECTO, no las que yo recuerde

**Patron.** Al cerrar cada ola del bloque de detalle de cita ejecute tres comprobaciones —
`npm run build`, `npx tsc --noEmit` y `npx vitest run` — y declare "verde" cinco veces seguidas.
Nunca ejecute `npx eslint`. Un revisor de mutacion lo hizo al final: **27 errores**, todos
introducidos por los commits de correccion de ese mismo bloque, en un repo que tenia 0.

Peor: los tres focos correspondian a decisiones que yo habia dirigido — un `useRef` escrito
durante el render (24 errores `react-hooks/refs`), y dos `set-state-in-effect` de arreglos que
mande resolver "por dentro" en vez de con un `key`. Las tres se acabaron revirtiendo.

**Por que.** Escribi mis puertas de memoria. El plan del bloque 2, en este mismo repo, registra su
linea base como *"275 tests en 50 ficheros, tsc limpio, **lint 0 errores + 25 avisos**"*. El dato
estaba escrito; no lo mire.

**Como evitarlo.**
- Antes de definir la puerta de un bloque, LEER que comprobaciones declaraba la linea base del
  bloque anterior, y ejecutarlas todas. No inventar la lista.
- Mirar `package.json`: si hay un script (`lint`, `typecheck`, `format:check`), es parte del
  contrato del proyecto aunque no me acuerde de el.
- "Verde" sin decir CON QUE no significa nada. Al declararlo, enumerar los comandos.
- El lint no es cosmetica: `react-hooks/refs` y `set-state-in-effect` son reglas de correccion.
  Los tests pasaban con las tres violaciones dentro.

**Corolario, del mismo bloque.** Dos revisores independientes con lentes distintas —correccion y
mutacion— senalaron las MISMAS lineas por motivos distintos ("los efectos no cierran todos los
canales" / "esos efectos son 2 errores de lint y se tapan entre si en el test"). Cuando eso pasa,
lo que esta mal no es el arreglo: es la decision de partida que lo forzo.

Ver [[revision-por-bloque-no-por-tarea]].


---

## "Verificado" quiere decir verificado en el COMPORTAMIENTO, no en el esquema (2026-08-30, bloque 8)

**Patron.** El plan del asistente de nueva cita declaraba su §1 como "LEIDO, no supuesto". Tres
revisiones seguidas encontraron ahi hechos falsos, y los tres eran de la MISMA familia: yo habia
comprobado que algo EXISTIA y habia dado por hecho lo que HACIA.

1. Transcribi numeros de linea de artboards leidos con `sed -n 'N,$p' | cat -n`, que **renumera la
   salida desde 1**. Nueve de los diez ficheros quedaron desviados +21, +10 u +8. Los CONTENIDOS
   eran correctos; los punteros, no.
2. Escribi que `ClientJpaEntity.lastVisitAt` "existe" y construi encima un orden de "clientes
   recientes" y un contador de "N visitas". La columna existe. **Nadie la escribe nunca**:
   `ClientService.java:66,193` pone `totalVisits(0)` al crear y ahi acaba todo; appointment-service
   no llama a client-service al completar una cita. En produccion las dos columnas valen 0 y NULL
   para el 100% de las filas.
3. Cite `ClientController.list(Pageable)` para decir que no acepta `search` — correcto — pero razone
   el orden de los NULL sobre **Postgres** cuando el motor es **MySQL 8.0**, donde la regla es la
   contraria y `NULLS LAST` ni siquiera es sintaxis valida.

**Por que importa.** Una seccion de hechos que se declara verificada no la vuelve a mirar nadie: las
once tareas la REFERENCIAN en vez de repetirla. Un hecho falso ahi se ejecuta once veces, y las
cuatro puertas (`tsc`, `eslint`, `vitest`, `build`) no ven ninguno de los tres — jsdom no pinta, y un
test que siembra a mano el dato que produccion nunca escribe sale verde.

**Reglas.**
- **Nunca transcribir un numero de linea de un comando que renumera.** Los numeros de `fichero:linea`
  salen de `grep -n` sobre el fichero entero, o no salen.
- **Para todo dato que una pantalla vaya a PINTAR u ORDENAR, buscar quien lo ESCRIBE**, no solo donde
  esta declarado. `grep` del setter y del endpoint que lo alimenta. Si no aparece nadie, el dato es
  una constante y el plan tiene que decirlo.
- **Antes de razonar sobre semantica de base de datos, leer el `application-*.yml`.** El motor decide
  el orden de los NULL, la sintaxis disponible y lo que un test de Testcontainers va a ejercer.
- Si una revision encuentra un hecho falso en §1, **no basta con corregir ese hecho**: hay que barrer
  la seccion entera buscando de la misma familia. Las rondas 2 y 3 encontraron mas casos porque la
  ronda 1 solo corrigio los que nombro.

Ver [[revision-por-bloque-no-por-tarea]], [[artboard-es-la-fuente]].


---

## Corregir lo senalado NO es corregir: hay que barrer la familia antes de re-revisar (2026-08-30, bloque 8)

**Correccion del usuario, literal:** *"estoy hasta las narices que necesites 3, 4 o incluso 5 rondas
de un revisor; cuando te diga que esta mal haz el favor de revisar y corregir y luego volver a
revisar antes de que te vuelva a decir block el revisor"*.

**Patron.** Cuatro rondas de BLOQUEO sobre el mismo plan. Ninguna ronda repitio un hallazgo de la
anterior: cada una encontro material NUEVO de la MISMA FAMILIA que la anterior ya habia senalado.
- Ronda 1 encontro numeros de linea falsos en §1.1/§1.2 → corregi §1.1/§1.2 y despache.
  Ronda 2 encontro los mismos numeros falsos en §1.4, §1.5, §1.6 y T0.
- Ronda 2 encontro que `use-staff.ts` faltaba en la tabla de propiedad → lo anadi y despache.
  Ronda 3 encontro `use-availability.ts` faltando por lo mismo. Siete fallos de esa tabla en tres
  rondas.
- Ronda 3 encontro que `lastVisitAt` existe pero nadie lo escribe → lo corregi y despache.
  Y ni siquiera me pregunte que OTROS campos que los artboards pintan estan en la misma situacion.

El error no es tener hallazgos: es **usar al revisor como bucle de iteracion** en vez de como
verificacion final. Cada ronda cuesta ~15 minutos de reloj y el usuario los vive todos.

**Reglas.**
- Cuando un revisor devuelve un hallazgo, ese hallazgo es una MUESTRA de una clase, no un caso
  aislado. Antes de re-despachar: **barrer el documento entero buscando la clase**, no la instancia.
  Si el hallazgo es "una referencia de linea es falsa en §1.1", se comprueban TODAS las referencias
  de TODAS las secciones. Si es "un campo que nadie escribe", se comprueba QUIEN ESCRIBE cada campo
  que la pantalla pinta.
- **Escribir en la respuesta al revisor, o en el propio plan, que barrido se ha hecho.** Si no puedo
  nombrar el barrido, no lo he hecho.
- La revision del REVISOR se despacha cuando yo ya no encuentro nada mas, no cuando he tachado su
  lista.
- Vale igual para el codigo: un `code-reviewer` que senala un bug en un fichero esta describiendo un
  patron que probablemente vive en los cinco ficheros hermanos.

Ver [[revision-por-bloque-no-por-tarea]].


---

## El agente se elige por el ROL del triad, no por el stack que anuncia (2026-08-30, bloque 8)

**Correccion del usuario:** *"por que has lanzado un agente agent-fira-apicore4 si este proyecto no
es de fira?"*.

**Patron.** Para la tarea de backend del bloque 8 (Rivoo, proyecto PERSONAL) despache
`agent-fira-apicore4` porque su descripcion decia "Spring Boot 4.0.x, Java 25" y el stack encajaba.
Intente compensarlo escribiendo en el brief "Rivoo NO es un proyecto de Fira". Eso no arregla nada:
el agente arranca con su system prompt cargado de api-core, librerias internas y convenciones de
Fira, que en un repo ajeno es ruido y puede empujarle a patrones inexistentes.

**Lo que dice CLAUDE.md §2, y que me salte:** los `agent-fira-*` son para codigo de Fira —
"irreemplazable conocimiento interno". Todo lo demas (Spring Boot, React, TS, Python, SQL, tests,
rendimiento...) va a **`code-implementer` + la skill que encaje**. Literal: *"La especializacion vive
en las skills, no en agentes-persona"*.

**Reglas.**
- El `subagent_type` lo decide el ROL en el triad (orquestador / implementador / revisor) y el
  DOMINIO DEL REPO, nunca la coincidencia de stack en la descripcion del agente.
- Un `agent-fira-*` fuera de un repo de Fira es un error aunque el lenguaje y el framework coincidan.
- Si el brief tiene que empezar desmintiendo al agente ("esto NO es de Fira, ignora tus
  convenciones"), es la senal de que el agente elegido es el equivocado. **Elegir otro, no escribir
  el desmentido.**

Ver [[revision-por-bloque-no-por-tarea]].


---

## Un bloque se dimensiona leyendo los artboards, no el resumen de estado (2026-08-30, bloque 8)

**Correccion del usuario, a mitad de la ola 3:** *"pero que es lo que estas haciendo en este bloque?
no se suponia que simplemente era mover/sacar el asistente del shell?"*.

**Patron.** Al recomendar el bloque escribi: *"Asistente de nueva cita — **los cinco pasos
coinciden**, pero sigue dentro de `(app)`"*. Eso salio de la seccion ESTADO REAL de `todo.md`, que
resume, no de los artboards. Al leerlos despues para escribir §1 resulto que **no coincide ninguno de
los cinco**: el paso 2 filtra fuera servicios que el artboard dibuja atenuados, el 3 tiene navegador
de mes donde el artboard pinta tira de dias y corte manana/tarde, el 4 esconde la lista hasta teclear
dos caracteres, el 1 y el 5 tienen otra maqueta entera.

En vez de volver al usuario con "esto no es lo que te dije, son 5 reescrituras y no un traslado", lo
meti todo en el mismo bloque. De "sacar del shell" salieron 13 tareas y seis olas. El usuario se
entero a mitad de la ola 3.

**Reglas.**
- **Dimensionar un bloque exige leer sus artboards contra el codigo, no un resumen previo.** Un
  "coincide" en `todo.md` es una nota de otro momento; el artboard es la fuente.
- Si al escribir el plan el alcance REAL se aleja de lo que anuncie, **eso se dice antes de escribir
  el plan**, no se absorbe. Ampliar el alcance en silencio es peor que un plan grande: el usuario ha
  aprobado otra cosa.
- La regla de "decidir sin preguntar" ([[decidir-sin-preguntar-y-resumir]]) cubre las dudas de
  ejecucion. **NO cubre cambiar el tamano de lo aprobado**: recortar o ampliar el alcance es del
  usuario.

Ver [[artboard-es-la-fuente]], [[decidir-sin-preguntar-y-resumir]].

---

## Un revisor que MUTA el codigo no puede correr en paralelo con nadie que EJECUTE la suite

**Fecha:** 2026-08-30 · Bloque 8 (asistente de nueva cita), panel de revision T11.

**Que paso.** Lance las tres lentes del panel a la vez. La lente 3 hacia prueba de mutacion:
rompia una linea de produccion, ejecutaba, revertia con `git checkout --`, 87 veces. La lente 1,
en paralelo, ejecutaba la suite para comprobar sus hallazgos, y vio `wizard-summary.test.ts`
rojo en 4 pasadas seguidas, con un test distinto cada vez y con valores imposibles para un
modulo puro -- en dos casos, el test recibio exactamente el resultado esperado por el test
SIGUIENTE del fichero. Lo reporto como "inestabilidad del runner, una puerta que no protege".

No habia tal inestabilidad. Eran las mutaciones de la lente 3 sobre ese mismo fichero (9
mutaciones, 7 muertas). "El test recibe el valor del test siguiente" es exactamente lo que
produce cambiar `step >= 4` por `step >= 3` en `getDateTimeRow`. Lo confirme con el arbol
quieto: 10 pasadas del mismo fichero, 0 fallos.

**Por que importa.** El coste no fue el tiempo: fue que estuve a punto de dar por bueno un
diagnostico de "carrera del runner" sobre el fichero central del bloque, que habria mandado a
alguien a perseguir un fantasma en la configuracion de vitest. Un fallo inventado por la propia
orquestacion se disfraza de fallo del codigo, y llega avalado por un agente que dice haberlo
medido cuatro veces.

**Regla.**
1. Los agentes que MUTAN ficheros (prueba de mutacion, bisecciones, `git checkout` de
   restauracion) corren SOLOS. Nunca a la vez que otro agente que ejecute la suite completa.
2. En una ola de implementadores en paralelo sobre un mismo arbol, cada agente ejecuta SOLO
   sus propios ficheros de test. Las puertas globales (`tsc`, `eslint`, `vitest` completo,
   `build`) las pasa el orquestador al final, sobre el arbol consolidado y quieto.
3. Cuando un agente reporte un fallo intermitente, la PRIMERA hipotesis es la contencion entre
   agentes, no el codigo. Se descarta reproduciendo con el arbol quieto ANTES de investigar
   nada mas.

Ver [[revision-por-bloque-no-por-tarea]].
