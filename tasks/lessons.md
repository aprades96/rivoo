# Rivoo — Lessons Learned

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
