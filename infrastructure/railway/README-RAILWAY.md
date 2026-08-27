# Rivoo — Despliegue en Railway

## Arquitectura en Railway

```
Railway Project: "rivoo"
├── MySQL          (plugin)
├── keycloak       (Docker: quay.io/keycloak/keycloak:26.0.6)
├── api-gateway    (Dockerfile: api-gateway/Dockerfile)
├── auth-service   (Dockerfile: auth-service/Dockerfile)
├── salon-service  (Dockerfile: salon-service/Dockerfile)
├── staff-service  (Dockerfile: staff-service/Dockerfile)
├── client-service (Dockerfile: client-service/Dockerfile)
├── appointment-service (Dockerfile: appointment-service/Dockerfile)
├── notification-service (Dockerfile: notification-service/Dockerfile)
├── billing-service (Dockerfile: billing-service/Dockerfile)
└── admin-service  (Dockerfile: admin-service/Dockerfile)

Frontend → Vercel (separado)
```

## Paso a paso

### 1. Crear proyecto en Railway

1. Ve a https://railway.com y crea una cuenta
2. Click "New Project" → "Empty Project"
3. Nombra el proyecto "rivoo"

### 2. Anadir MySQL

1. En el proyecto, click "New" → "Database" → "MySQL"
2. Railway crea la instancia y te da las variables:
   - `MYSQL_URL` (connection string completa)
   - `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE`
3. Conectate y crea las 7 bases de datos:
   ```sql
   CREATE DATABASE IF NOT EXISTS auth_db;
   CREATE DATABASE IF NOT EXISTS salon_db;
   CREATE DATABASE IF NOT EXISTS staff_db;
   CREATE DATABASE IF NOT EXISTS client_db;
   CREATE DATABASE IF NOT EXISTS appointment_db;
   CREATE DATABASE IF NOT EXISTS notification_db;
   CREATE DATABASE IF NOT EXISTS billing_db;
   ```

### 3. Anadir Keycloak

1. Click "New" → "Docker Image"
2. Image: `quay.io/keycloak/keycloak:26.0.6`
3. Configurar variables de entorno:
   ```
   KC_HTTP_PORT=9080
   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=(genera una segura)
   KC_PROXY_HEADERS=xforwarded
   KC_HOSTNAME_STRICT=false
   ```
4. Start command: `start --optimized --import-realm`
5. Genera un dominio publico (Settings → Networking → Generate Domain)
6. Anota la URL: `https://keycloak-production-xxxx.up.railway.app`

### 4. Anadir cada microservicio

Para cada servicio (auth-service, salon-service, etc.):

1. Click "New" → "GitHub Repo" → selecciona el repo `rivoo`
2. Railway detectara el Dockerfile. Configurar:
   - **Root Directory**: `/` (raiz del monorepo)
   - **Dockerfile Path**: `{servicio}/Dockerfile`
3. Configurar variables de entorno (ver seccion siguiente)
4. Generar dominio solo para `api-gateway` (los demas son internos)

### 5. Variables de entorno por servicio

#### Compartidas (todas las necesitan)

```
SPRING_PROFILES_ACTIVE=prod
RIVOO_SECURITY_INTERNAL_SERVICE_KEY=(genera un UUID seguro)
```

#### api-gateway

```
SERVER_PORT=8080
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://{keycloak-url}/realms/rivoo/protocol/openid-connect/certs
```
Habilitar dominio publico. Anota: `https://api-gateway-production-xxxx.up.railway.app`

#### auth-service

```
SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:mysql://{MYSQL_HOST}:{MYSQL_PORT}/auth_db
SPRING_DATASOURCE_USERNAME={MYSQL_USER}
SPRING_DATASOURCE_PASSWORD={MYSQL_PASSWORD}
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://{keycloak-url}/realms/rivoo/protocol/openid-connect/certs
RIVOO_KEYCLOAK_SERVER_URL=https://{keycloak-url}
RIVOO_KEYCLOAK_REALM=rivoo
RIVOO_KEYCLOAK_CLIENT_ID=salon-admin-cli
RIVOO_KEYCLOAK_CLIENT_SECRET=(secret del client salon-admin-cli)
```

#### salon-service

```
SERVER_PORT=8082
SPRING_DATASOURCE_URL=jdbc:mysql://{MYSQL_HOST}:{MYSQL_PORT}/salon_db
SPRING_DATASOURCE_USERNAME={MYSQL_USER}
SPRING_DATASOURCE_PASSWORD={MYSQL_PASSWORD}
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=https://{keycloak-url}/realms/rivoo/protocol/openid-connect/certs
RIVOO_SERVICES_AUTH_SERVICE_URL=http://auth-service.railway.internal:8081
RIVOO_SERVICES_BILLING_SERVICE_URL=http://billing-service.railway.internal:8087
RIVOO_SERVICES_NOTIFICATION_SERVICE_URL=http://notification-service.railway.internal:8086
RIVOO_SERVICES_STAFF_SERVICE_URL=http://staff-service.railway.internal:8083
```

> `RIVOO_SERVICES_STAFF_SERVICE_URL` no es opcional: salon-service la lee al construir
> `StaffServiceAdapter`, que es el puente que trae servicios y empleados a la pagina de
> reserva publica. El `@Value` no tiene valor por defecto, asi que si falta la variable
> Spring no resuelve el placeholder y **el contexto no arranca**: no se cae solo la
> reserva publica, se cae salon-service entero, API autenticada y alta de negocio incluidas.

#### staff-service, client-service, appointment-service, notification-service, billing-service, admin-service

Mismo patron: DATASOURCE apuntando a su BD, JWKS URI a Keycloak, y URLs internas usando `{servicio}.railway.internal:{puerto}`.

**IMPORTANTE**: En Railway, los servicios se comunican internamente via `{nombre-servicio}.railway.internal:{puerto}`. No necesitan dominio publico.

#### billing-service — variables adicionales

```
STRIPE_API_KEY=(secret de Stripe)
STRIPE_WEBHOOK_SECRET=(signing secret del endpoint de webhook)
RIVOO_BILLING_PORTAL_RETURN_URL=https://tu-dominio.vercel.app/settings/billing
```

> `RIVOO_BILLING_PORTAL_RETURN_URL` es la URL a la que Stripe devuelve al usuario cuando
> sale del portal de facturacion (`POST /api/v1/billing/portal`). A diferencia de
> `RIVOO_SERVICES_*`, **no es una URL interna**: la abre el navegador, asi que tiene que ser
> el dominio publico del frontend en Vercel, no `*.railway.internal`.
> **No es opcional en prod.** `application-prod.yml` la declara como `${RIVOO_BILLING_PORTAL_RETURN_URL}`
> sin valor por defecto, igual que `STRIPE_API_KEY` o los `RIVOO_SERVICES_*`: si falta,
> Spring no resuelve el placeholder y **el contexto no arranca**, asi que el fallo se ve en
> el deploy y no llega a produccion. Es deliberado. La alternativa —dejar un default en el
> yml— hacia que el servicio arrancase sano, con los health checks en verde, y el error solo
> apareciese cuando un cliente de pago terminase de gestionar su facturacion en Stripe,
> pulsase "Volver" y aterrizase en `http://localhost:3000/...` con `ERR_CONNECTION_REFUSED`:
> un fallo silencioso que solo reproduce un usuario real completando un pago real.
>
> El `@Value` de `BillingPortalService` si lleva default inline
> (`http://localhost:3000/settings/billing`). Eso es lo que garantiza que la ausencia de la
> propiedad no tumbe los perfiles que no la fijan; no debilita el fail-fast de prod, porque
> ahi el placeholder del yml se evalua antes y ya no resuelve.

### 6. Frontend (Vercel)

1. Ve a https://vercel.com
2. Importa el repo `rivoo-frontend`
3. Variables de entorno:
   ```
   NEXTAUTH_URL=https://tu-dominio.vercel.app
   NEXTAUTH_SECRET=(genera uno seguro)
   AUTH_KEYCLOAK_ID=salon-frontend
   AUTH_KEYCLOAK_SECRET=
   AUTH_KEYCLOAK_ISSUER=https://{keycloak-url}/realms/rivoo
   NEXT_PUBLIC_API_URL=https://{api-gateway-url}
   NEXT_PUBLIC_KEYCLOAK_URL=https://{keycloak-url}
   ```
4. Deploy automatico con cada push a main

### 7. Actualizar Keycloak redirect URIs

En la consola admin de Keycloak, actualiza el client `salon-frontend`:
- Valid Redirect URIs: `https://tu-dominio.vercel.app/*`
- Web Origins: `https://tu-dominio.vercel.app`
- Post Logout Redirect URIs: `https://tu-dominio.vercel.app/*`

Y actualiza el CORS del api-gateway para permitir el dominio de Vercel.

## Costes estimados Railway

| Servicio | RAM | Coste aprox |
|----------|-----|-------------|
| MySQL | 1GB | $7/mes |
| Keycloak | 512MB | $8/mes |
| api-gateway | 256MB | $5/mes |
| 8 microservicios | 256MB x 8 | $40/mes |
| **Total** | | **~$60/mes** |

## Tips

- **Logs**: Railway tiene logs en tiempo real por servicio
- **Redeploy**: cada push a main redespliega automaticamente
- **Rollback**: un click en Railway dashboard
- **Sleep**: los servicios no duermen en el plan Pro ($5/mes base)
- **Networking interno**: usa `.railway.internal` para comunicacion entre servicios (gratis, sin latencia)
