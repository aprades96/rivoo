# Reserva pública end-to-end — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task by task. The steps use checkbox syntax (`- [ ]`) for tracking.

**Objective:** Que un cliente sin cuenta pueda reservar online en `/book/{slug}`: elegir servicio, elegir profesional, ver huecos reales y confirmar.

**Architecture:** El agregado público `GET /api/v1/salons/public/{slug}` pasa a devolver también `businessHours`, `services` y `employees`. salon-service obtiene staff mediante un `StaffServiceAdapter` nuevo (patrón `AuthServiceAdapter`/`BillingServiceAdapter`) contra endpoints internos de listado nuevos en staff-service. La disponibilidad, que cambia constantemente y no puede ir en un agregado cacheable, se expone como un endpoint público propio en appointment-service resolviendo tenant por slug igual que ya hace `PublicBookingUseCase`. En el gateway se añade **una sola** regla `permitAll` nueva.

**Tech Stack:** Java 25 · Spring Boot 4.0.3 · Maven multi-módulo · hexagonal · MySQL 8 + Flyway · MapStruct · JUnit 5 + Mockito + Testcontainers + WireMock · Next.js 16 (App Router) · React Query · Zustand · Vitest

**Complejidad: Very complex** — 2 repos, 4 subsistemas (salon-service, staff-service, appointment-service, api-gateway) + frontend. Requiere proponer motor de ejecución al usuario (ver *Execution Handoff*).

---

## Diagnóstico (verificado en código, no supuesto)

| Hecho | Evidencia |
|---|---|
| Solo hay 2 rutas públicas | `api-gateway/.../GatewaySecurityConfig.java:24,26` |
| Servicios exigen rol | `staff-service/.../ServiceOfferingController.java:33-34` |
| Empleados exigen rol | `staff-service/.../EmployeeController.java:58-59` |
| Disponibilidad exige rol | `appointment-service/.../AppointmentController.java:108-109` |
| `SalonPublicResponse` no lleva `businessHours`, `services` ni `employees` | `salon-service/.../dto/SalonPublicResponse.java` (9 campos) |
| El paso 1 siempre sale vacío | `rivoo-frontend/src/components/booking/public-service-step.tsx:22` lee `salon.services`, que nunca existe |
| El paso de fecha llama al endpoint autenticado sin token | `rivoo-frontend/src/components/booking/public-datetime-step.tsx:32-34` |
| El tipo del frontend miente | `rivoo-frontend/src/types/salon.ts:24-34` declara `id`, `address`, `businessHours`; el backend no envía ninguno |

**Consecuencia:** el `400` por `employeeExternalId` vacío ni siquiera es alcanzable — el cliente no pasa de la primera pantalla.

---

## ⚠️ Riesgo crítico de seguridad: fuga cross-tenant

`TenantFilterAspect.java:20,30` — si `tenantId` es `null`, el filtro de Hibernate **no se activa** (es el caso `PLATFORM_ADMIN`, acceso cross-tenant deliberado).

`EmployeeService.getInternal():120-124` recibe `tenantId` y **lo ignora**: se apoya en el `TenantContext` ambiental, que `TenantInterceptor` rellena desde el header `X-Tenant-Id`.

Una petición pública **no tiene JWT, luego no tiene `TenantContext`**. `InterServiceRestClientConfig` propaga `X-Tenant-Id` *desde el TenantContext actual*, que estará vacío. Por tanto:

> Si los listados se apoyaran en el `TenantContext` ambiental, devolverían los empleados y servicios de **todos los salones de la plataforma** a cualquier visitante anónimo.

**Corrección (2026-08-27, tras ejecutar las Tasks 2-5).** El párrafo de arriba describe el riesgo del stack por defecto, pero **ya no describe el código**: las Tasks 2 y 3 resolvieron el listado con filtrado por **columna explícita** (`findByTenantIdAndActiveTrue`), tomando el `tenantId` de la **ruta**, no del contexto. Con eso los datos son correctos aunque el header no viaje.

El `X-Tenant-Id` explícito de la Task 5 sigue siendo necesario, pero como **defensa en profundidad**, no como única barrera: `TenantInterceptor` lo lee y reactiva el `@Filter` de Hibernate, de modo que si mañana alguien añade a esa ruta una consulta que olvide el filtro explícito, el fallo sea cerrado (no devuelve nada) en vez de abierto (devuelve todos los tenants).

Mantener la redacción exagerada tenía un coste real: el día que alguien comprobase que no es cierta, dejaría de creerse el resto del documento. La regla es la misma que aplicamos a los comentarios de código.

**Lo que sí sigue abierto y es una fuga real:** `EmployeeService.getInternal()` y `ServiceOfferingService.getInternal()` ignoran su parámetro `tenantId`, y `d060fe4` los ha vuelto alcanzables **sin JWT** desde `GET /api/v1/appointments/public/availability`. Registrado como **RP.15** en `tasks/todo.md`; debe cerrarse antes de que esta rama llegue a producción. Ningún reviewer debe aprobar esta feature sin ver en verde los tests de aislamiento cross-tenant (RP.12).

---

## ⚠️ Corrección al plan (descubierta ejecutando la Task 7)

**Un endpoint público necesita DOS reglas, no una.** Cada servicio tiene su propia cadena de Spring Security terminada en `.anyRequest().authenticated()`. Quitar `@PreAuthorize` **no** basta: sin una regla `permitAll` en la config del propio servicio, el endpoint sigue exigiendo JWT aunque el gateway lo deje pasar.

| Servicio | Config propia | ¿Hace falta tocarla? |
|---|---|---|
| `appointment-service` | `AppointmentSecurityConfig:37-38` | **Sí** — hecho en Task 7 |
| `salon-service` | `SalonSecurityConfig:38` | **No** — ya permite `GET /api/v1/salons/public/**`; el agregado de la Task 6 viaja en esa regla |
| `staff-service` | ninguna (usa la compartida) | **No** — los endpoints de la Task 4 van bajo `/api/internal/**`, ya permitido y protegido por PSK |

**Segunda corrección:** la Task 7 decía implementar `getPublicAvailableSlots` en `AppointmentService`. Es erróneo: `CheckAvailabilityUseCase` lo implementa `AvailabilityService`, y añadirlo a `AppointmentService` habría creado un segundo bean del mismo puerto → inyección ambigua → Spring no arranca. Implementado en `AvailabilityService`.

---

## Estructura de ficheros

### Backend (`E:\IdeaProjects\rivoo`)

| Fichero | Responsabilidad |
|---|---|
| `staff-service/.../dto/EmployeePublicResponse.java` **(crear)** | Empleado visible al público: **sin email ni teléfono** |
| `staff-service/.../dto/ServiceOfferingPublicResponse.java` **(crear)** | Servicio visible al público |
| `staff-service/.../port/in/GetEmployeeUseCase.java` **(modificar)** | `+ List<EmployeePublicResponse> listPublicByTenant(String tenantId)` |
| `staff-service/.../port/in/ManageServiceOfferingUseCase.java` **(modificar)** | `+ List<ServiceOfferingPublicResponse> listPublicByTenant(String tenantId)` |
| `staff-service/.../port/out/EmployeePersistencePort.java` **(modificar)** | `+ findAllActiveByTenantId(String tenantId)` — filtrado **explícito**, no ambiental |
| `staff-service/.../port/out/ServiceOfferingPersistencePort.java` **(modificar)** | ídem |
| `staff-service/.../application/EmployeeService.java` **(modificar)** | Implementa `listPublicByTenant`, valida `tenantId` no vacío |
| `staff-service/.../application/ServiceOfferingService.java` **(modificar)** | ídem |
| `staff-service/.../in/web/StaffInternalController.java` **(modificar)** | `+ GET /{tenantId}/public/employees`, `+ GET /{tenantId}/public/services` |
| `salon-service/.../port/out/StaffServicePort.java` **(crear)** | Puerto de salida hacia staff-service |
| `salon-service/.../adapter/out/rest/StaffServiceAdapter.java` **(crear)** | Adaptador REST. **Pone `X-Tenant-Id` explícito** |
| `salon-service/.../adapter/out/rest/dto/EmployeePublicDto.java` **(crear)** | DTO de transporte |
| `salon-service/.../adapter/out/rest/dto/ServiceOfferingPublicDto.java` **(crear)** | DTO de transporte |
| `salon-service/.../dto/SalonPublicResponse.java` **(modificar)** | `+ businessHours`, `+ services`, `+ employees` |
| `salon-service/.../application/SalonService.java` **(modificar)** | `getPublicBySlug` compone el agregado; degrada si staff-service cae |
| `salon-service/src/main/resources/application*.yml` **(modificar)** | `rivoo.services.staff-service.url` |
| `appointment-service/.../dto/PublicAvailabilityRequest.java` **(crear)** | slug + employeeId + date + serviceId |
| `appointment-service/.../application/AppointmentService.java` **(modificar)** | `getPublicAvailability`: slug → tenantId → use case existente |
| `appointment-service/.../port/in/CheckAvailabilityUseCase.java` **(modificar)** | `+ getPublicAvailableSlots(...)` |
| `appointment-service/.../in/web/AppointmentController.java` **(modificar)** | `+ GET /api/v1/appointments/public/availability` (sin `@PreAuthorize`) |
| `api-gateway/.../GatewaySecurityConfig.java` **(modificar)** | **1 regla nueva**: `GET /api/v1/appointments/public/**` |

### Frontend (`E:\IdeaProjects\rivoo-frontend`)

| Fichero | Responsabilidad |
|---|---|
| `src/types/salon.ts` **(modificar)** | `SalonPublic` real: sin `id`, con `services` y `employees` tipados |
| `src/types/employee.ts` **(modificar/crear)** | `EmployeePublic` |
| `src/lib/api/salons.ts` **(modificar)** | Sin cambios de firma; sí de tipo devuelto |
| `src/lib/api/appointments.ts` **(modificar)** | `+ getPublicAvailability(params)` sin token |
| `src/lib/stores/public-booking-store.ts` **(modificar)** | 6 pasos (`nextStep` topa en 6, no en 5) |
| `src/components/booking/public-employee-step.tsx` **(crear)** | Paso 2 — Profesional |
| `src/components/booking/public-service-step.tsx` **(modificar)** | Quitar el cast `as unknown as`; leer `salon.services` ya tipado |
| `src/components/booking/public-datetime-step.tsx` **(modificar)** | Usar el endpoint público |
| `src/components/booking/public-confirm-step.tsx` **(modificar)** | `employeeExternalId` siempre presente |
| `src/app/book/[slug]/page.tsx` **(modificar)** | 6 pasos, barra de progreso de 6 |

---

## Visual Inventory

**Referencia:** `E:\IdeaProjects\rivoo-frontend\design\ReservaPaso1..6.dc.html` (móvil 390×844) y `ReservaDesktopPaso1..6.dc.html` (1440×900). Publicados en https://claude.ai/code/artifact/83a0e2ff-3115-46e6-86b5-6dde703b7466

### Inventario de componentes — paso 2 (Profesional), el único realmente nuevo

| Componente | Referencia (file:lines) | Forma / valores |
|---|---|---|
| Tarjeta de opción `.opt` | `ReservaPaso2.dc.html:18` | radio **10px** · borde 1px `#E7DCCF` (`--border`) · fondo `#FFFFFF` (`--card`) · padding 14px · gap 12px |
| Avatar `.av` | `ReservaPaso2.dc.html:19` | **44×44** · radio 999 · 14px/600 |
| Título de paso | `ReservaPaso2.dc.html:47` | 28px · line-height 1.1 · letter-spacing **-0.015em** · peso 600 |
| Subtítulo (servicio elegido) | `ReservaPaso2.dc.html:48` | 13px · `#7A6A5F` (`--muted-foreground`) · precio en `tabular-nums` |
| Nombre de profesional | `ReservaPaso2.dc.html:69` | 15px/600 |
| Rol + especialidad | `ReservaPaso2.dc.html:70` | 12px · `--muted-foreground` |
| Cabecera "O elige profesional" | `ReservaPaso2.dc.html:64` | 11px/600 · letter-spacing 0.05em · **uppercase** · `#9A8A7E` |
| Barra de progreso | `ReservaPaso2.dc.html:38-43` | **6 segmentos** · alto 3px · radio 999 · activo `--primary`, inactivo `--border` |
| Contador de paso | `ReservaPaso2.dc.html:32` | 11px · `tabular-nums` · formato `2 / 6` |
| Estado deshabilitado | `ReservaPaso2.dc.html:94` | texto "No ofrece {servicio}" en lugar del rol |
| Nota de cierre | `ReservaPaso2.dc.html:101` | 11px · `#9A8A7E` · centrado · line-height 1.5 |

### Comprobación de primitivas

| Necesidad | Primitiva del repo | ¿Sirve? |
|---|---|---|
| Tarjeta seleccionable | `components/ui/card.tsx` | **Sí** — mismo patrón que `public-service-step.tsx:50-52` (`border-primary bg-primary/5` al seleccionar). Reutilizar, no inventar |
| Avatar con iniciales | `components/ui/avatar.tsx` (`AvatarFallback`) | **Sí** — ya se usa en `appointment-card.tsx` |
| Barra de progreso | Ninguna | No hace falta: `book/[slug]/page.tsx:64-73` ya la pinta a mano. **Solo cambiar `[1,2,3,4]` por `[1,2,3,4,5,6]`** |
| Estado deshabilitado | — | `opacity-50 pointer-events-none` sobre la `Card`. No crear variante nueva |

> **No se extiende ninguna primitiva.** Todo el paso se construye con `Card` + `Avatar` existentes.

### Comprobación de tokens

Todos los colores del artboard existen ya en `globals.css` tras el reskin: `#E7DCCF → --border`, `#FFFFFF → --card`, `#7A6A5F → --muted-foreground`, `#9A8A7E` ≈ `--muted-foreground` al 80%, `#B4522F → --primary`. **Radio 10px**: `--radius` es `0.5rem` (8px) y `--radius-xl` es `calc(0.5rem*1.4)` = 11.2px → usar `rounded-xl`. **Tracking -0.015em**: no hay token exacto; `--tracking-display` es `-0.02em`. Usar `tracking-display` (diferencia de 0.005em, imperceptible) en lugar de crear un token nuevo. **Ningún token falta.**

### Fuera de alcance, explícitamente

El artboard muestra un chip "Antes · Mié 11:00" con el primer hueco libre de cada profesional (`ReservaPaso2.dc.html:73-74`). **Se deja fuera de esta entrega**: exige calcular disponibilidad de N empleados en la carga del paso. Se implementará cuando exista un endpoint de disponibilidad agregada. El paso funciona sin él.

---

## Fases y paralelización

| Fase | Nombre | `paths_touched` | Depende de |
|---|---|---|---|
| **1** | Contrato de DTOs públicos | `staff-service/**/dto/*Public*.java` | ninguna |
| **2** | Listados por tenant en staff-service | `staff-service/**/port/**`, `staff-service/**/application/{Employee,ServiceOffering}Service.java`, `staff-service/**/persistence/**` | 1 |
| **3** | Endpoints internos de staff | `staff-service/**/in/web/StaffInternalController.java` | 2 |
| **4** | `StaffServiceAdapter` en salon-service | `salon-service/**/port/out/StaffServicePort.java`, `salon-service/**/out/rest/**`, `salon-service/**/resources/application*.yml` | 3 |
| **5** | Agregado público del salón | `salon-service/**/dto/SalonPublicResponse.java`, `salon-service/**/application/SalonService.java`, `salon-service/**/mapper/**` | 4 |
| **6** | Disponibilidad pública | `appointment-service/**` | ninguna |
| **7** | Regla del gateway | `api-gateway/**/GatewaySecurityConfig.java` | 6 |
| **8** | Tipos y clientes API del frontend | `rivoo-frontend/src/types/**`, `rivoo-frontend/src/lib/api/**` | contrato de 1 y 6 |
| **9** | Store de 6 pasos | `rivoo-frontend/src/lib/stores/public-booking-store.ts` | ninguna |
| **10** | Paso Profesional + cableado | `rivoo-frontend/src/components/booking/**`, `rivoo-frontend/src/app/book/**` | 8, 9 |
| **11** | Verificación de aislamiento cross-tenant | tests de integración en `staff-service` y `salon-service` | 5 |

**Olas paralelizables:**

- **Ola A:** Fase 1 ‖ Fase 6 ‖ Fase 9 (rutas disjuntas, repos/servicios distintos)
- **Ola B:** Fase 2 ‖ Fase 7 ‖ Fase 8
- **Ola C:** Fase 3
- **Ola D:** Fase 4 ‖ Fase 10
- **Ola E:** Fase 5
- **Ola F:** Fase 11 (verificación final)

Backend y frontend nunca colisionan: repos distintos. En cuanto la Fase 1 y la Fase 6 fijan el contrato, todo el frontend (8, 9, 10) puede ir en paralelo al backend.

---

## Tareas

### Task 1: DTOs públicos de staff (Fase 1)

**Files:**
- Create: `staff-service/src/main/java/com/rivoo/staff/application/dto/EmployeePublicResponse.java`
- Create: `staff-service/src/main/java/com/rivoo/staff/application/dto/ServiceOfferingPublicResponse.java`

- [ ] **Step 1: Crear `EmployeePublicResponse`**

> **Decisión de diseño:** NO se reutiliza `EmployeeInternalResponse` (`dto/EmployeeInternalResponse.java`) aunque tenga casi los mismos campos, porque lleva `email` y `phone`. Exponer eso en un endpoint anónimo es una fuga de datos personales del empleado. Un record nuevo hace imposible el error por descuido.

```java
package com.rivoo.staff.application.dto;

import java.util.List;

/**
 * Empleado tal y como lo ve un visitante anonimo en la pagina de reserva.
 * NO incluye email ni telefono: son datos personales del trabajador.
 */
public record EmployeePublicResponse(
        String id,
        String firstName,
        String lastName,
        String jobTitle,
        List<String> serviceIds
) {
}
```

- [ ] **Step 2: Crear `ServiceOfferingPublicResponse`**

```java
package com.rivoo.staff.application.dto;

import java.math.BigDecimal;

public record ServiceOfferingPublicResponse(
        String id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        String currency
) {
}
```

> `active` no se expone: el listado público solo devuelve activos, así que el campo sería siempre `true` y solo invita a que el frontend lo compruebe otra vez.

- [ ] **Step 3: Compilar**

Run: `cd /e/IdeaProjects/rivoo && ./mvnw -pl staff-service -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add staff-service/src/main/java/com/rivoo/staff/application/dto/
git commit -m "feat(staff): add public DTOs for anonymous booking page"
```

---

### Task 2: Listado de empleados por tenant, con filtro explícito (Fase 2)

**Files:**
- Modify: `staff-service/src/main/java/com/rivoo/staff/domain/port/out/EmployeePersistencePort.java`
- Modify: `staff-service/src/main/java/com/rivoo/staff/infrastructure/adapter/out/persistence/adapter/EmployeePersistenceAdapter.java`
- Modify: `staff-service/src/main/java/com/rivoo/staff/domain/port/in/GetEmployeeUseCase.java`
- Modify: `staff-service/src/main/java/com/rivoo/staff/application/EmployeeService.java`
- Test: `staff-service/src/test/java/com/rivoo/staff/application/EmployeeServicePublicListTest.java`

- [ ] **Step 1: Escribir el test que falla**

> Este test es el que impide la fuga cross-tenant. El `tenantId` debe llegar hasta la consulta, no quedarse en la firma como en `getInternal()`.

```java
@Test
void listPublicByTenant_passesTenantIdDownToTheQuery() {
    when(employeePersistencePort.findAllActiveByTenantId("sal_A"))
            .thenReturn(List.of(employeeOf("emp_1", "sal_A")));

    List<EmployeePublicResponse> result = service.listPublicByTenant("sal_A");

    assertThat(result).hasSize(1);
    verify(employeePersistencePort).findAllActiveByTenantId("sal_A");
    verify(employeePersistencePort, never()).findAllActive(any());
}

@Test
void listPublicByTenant_rejectsBlankTenantId() {
    assertThatThrownBy(() -> service.listPublicByTenant(""))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.listPublicByTenant(null))
            .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(employeePersistencePort);
}

@Test
void listPublicByTenant_neverExposesEmailOrPhone() {
    when(employeePersistencePort.findAllActiveByTenantId("sal_A"))
            .thenReturn(List.of(employeeWithContact("emp_1", "ana@salon.com", "612345678")));

    String json = new ObjectMapper().writeValueAsString(service.listPublicByTenant("sal_A"));

    assertThat(json).doesNotContain("ana@salon.com").doesNotContain("612345678");
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `./mvnw -pl staff-service test -Dtest=EmployeeServicePublicListTest`
Expected: FALLA — `listPublicByTenant` no existe

- [ ] **Step 3: Añadir el método al puerto de salida**

```java
// EmployeePersistencePort.java
List<Employee> findAllActiveByTenantId(String tenantId);
```

- [ ] **Step 4: Implementarlo en el adaptador con filtro EXPLÍCITO**

> El repositorio JPA debe filtrar por columna, **no** confiar en el `@Filter` de Hibernate: el `TenantContext` está vacío en una petición pública y `TenantFilterAspect:20` desactiva el filtro cuando es nulo.

```java
// EmployeePersistenceAdapter.java
@Override
public List<Employee> findAllActiveByTenantId(String tenantId) {
    return employeeJpaRepository.findByTenantIdAndActiveTrue(tenantId)
            .stream().map(mapper::toDomain).toList();
}
```

```java
// EmployeeJpaRepository.java
List<EmployeeJpaEntity> findByTenantIdAndActiveTrue(String tenantId);
```

- [ ] **Step 5: Añadir al puerto de entrada e implementar**

```java
// GetEmployeeUseCase.java
List<EmployeePublicResponse> listPublicByTenant(String tenantId);
```

```java
// EmployeeService.java
@Override
@Transactional(readOnly = true)
public List<EmployeePublicResponse> listPublicByTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException("tenantId is required for public employee listing");
    }
    return employeePersistencePort.findAllActiveByTenantId(tenantId).stream()
            .map(e -> new EmployeePublicResponse(
                    e.getExternalId(), e.getFirstName(), e.getLastName(), e.getJobTitle(),
                    employeeServicePersistencePort.findServiceIdsByEmployeeId(e.getId())))
            .toList();
}
```

- [ ] **Step 6: Ejecutar tests**

Run: `./mvnw -pl staff-service test -Dtest=EmployeeServicePublicListTest`
Expected: PASAN los 3

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(staff): list active employees by explicit tenantId for public booking"
```

---

### Task 3: Listado de servicios por tenant (Fase 2)

**Files:**
- Modify: `staff-service/.../port/out/ServiceOfferingPersistencePort.java`, su adaptador y repositorio
- Modify: `staff-service/.../port/in/ManageServiceOfferingUseCase.java`
- Modify: `staff-service/.../application/ServiceOfferingService.java`
- Test: `staff-service/src/test/java/com/rivoo/staff/application/ServiceOfferingPublicListTest.java`

- [ ] **Step 1: Test que falla** — mismo trío que Task 2: propaga tenantId, rechaza vacío, solo activos.
- [ ] **Step 2: Verificar que falla.** Run: `./mvnw -pl staff-service test -Dtest=ServiceOfferingPublicListTest`
- [ ] **Step 3: `findByTenantIdAndActiveTrue` en repo + puerto + adaptador** (idéntico patrón a Task 2, Step 4).
- [ ] **Step 4: `listPublicByTenant` en el use case**, con la misma guarda de `tenantId` en blanco.
- [ ] **Step 5: Tests en verde.**
- [ ] **Step 6: Commit** — `feat(staff): list active services by explicit tenantId`

---

### Task 4: Endpoints internos de listado (Fase 3)

**Files:**
- Modify: `staff-service/src/main/java/com/rivoo/staff/infrastructure/adapter/in/web/StaffInternalController.java`
- Test: `staff-service/src/test/java/com/rivoo/staff/infrastructure/adapter/in/web/StaffInternalControllerTest.java`

- [ ] **Step 1: Test de contrato** — `GET /api/internal/staff/{tenantId}/public/employees` devuelve 200 y un JSON sin `email` ni `phone`.
- [ ] **Step 2: Verificar que falla** (404).
- [ ] **Step 3: Implementar**

```java
@GetMapping("/{tenantId}/public/employees")
public ResponseEntity<List<EmployeePublicResponse>> listPublicEmployees(@PathVariable String tenantId) {
    log.atInfo().log("GET /api/internal/staff/{tenantId}/public/employees");
    return ResponseEntity.ok(getEmployeeUseCase.listPublicByTenant(tenantId));
}

@GetMapping("/{tenantId}/public/services")
public ResponseEntity<List<ServiceOfferingPublicResponse>> listPublicServices(@PathVariable String tenantId) {
    log.atInfo().log("GET /api/internal/staff/{tenantId}/public/services");
    return ResponseEntity.ok(manageServiceOfferingUseCase.listPublicByTenant(tenantId));
}
```

> `tenantId` va en la ruta, no en el header. Es un endpoint `/api/internal/**`, protegido por PSK (`InternalEndpointFilter`), nunca expuesto en el gateway.
> **No duplicar `tenantId` con `.addKeyValue()`**: ya sale como campo JSON automático (CLAUDE.md § Observability).

- [ ] **Step 4: Tests en verde.**
- [ ] **Step 5: Commit** — `feat(staff): internal endpoints listing public employees and services`

---

### Task 5: Puerto y adaptador de staff en salon-service (Fase 4)

**Files:**
- Create: `salon-service/src/main/java/com/rivoo/salon/domain/port/out/StaffServicePort.java`
- Create: `salon-service/src/main/java/com/rivoo/salon/infrastructure/adapter/out/rest/StaffServiceAdapter.java`
- Create: `salon-service/.../out/rest/dto/EmployeePublicDto.java`, `ServiceOfferingPublicDto.java`
- Modify: `salon-service/src/main/resources/application.yml` y `application-local.yml`
- Test: `salon-service/src/test/java/com/rivoo/salon/infrastructure/adapter/out/rest/StaffServiceAdapterTest.java`

- [ ] **Step 1: Test con WireMock — el header `X-Tenant-Id` DEBE ir**

> **Este es el test de seguridad del plan.** Sin el header, staff-service resolvería `tenantId = null`, el `@Filter` no se activaría y devolvería empleados de todos los salones.

```java
@Test
void getPublicEmployees_sendsExplicitTenantIdHeader() {
    stubFor(get(urlEqualTo("/api/internal/staff/sal_A/public/employees"))
            .willReturn(okJson("[{\"id\":\"emp_1\",\"firstName\":\"Laura\",\"lastName\":\"Martinez\",\"jobTitle\":\"Estilista\",\"serviceIds\":[\"svc_1\"]}]")));

    adapter.getPublicEmployees("sal_A");

    verify(getRequestedFor(urlEqualTo("/api/internal/staff/sal_A/public/employees"))
            .withHeader("X-Tenant-Id", equalTo("sal_A")));
}

@Test
void getPublicEmployees_returnsEmptyListWhenStaffServiceIsDown() {
    stubFor(get(urlPathMatching("/api/internal/staff/.*")).willReturn(serviceUnavailable()));

    assertThat(adapter.getPublicEmployees("sal_A")).isEmpty();
}
```

- [ ] **Step 2: Verificar que falla.** Run: `./mvnw -pl salon-service test -Dtest=StaffServiceAdapterTest`
- [ ] **Step 3: Crear el puerto**

```java
package com.rivoo.salon.domain.port.out;

import java.util.List;

public interface StaffServicePort {

    record EmployeePublicInfo(String id, String firstName, String lastName,
                              String jobTitle, List<String> serviceIds) {}

    record ServicePublicInfo(String id, String name, String description,
                             int durationMinutes, java.math.BigDecimal price, String currency) {}

    List<EmployeePublicInfo> getPublicEmployees(String tenantId);

    List<ServicePublicInfo> getPublicServices(String tenantId);
}
```

- [ ] **Step 4: Implementar el adaptador** (calcado de `SalonServiceAdapter.java` de appointment-service, con dos diferencias marcadas)

```java
@Slf4j
@Component
public class StaffServiceAdapter implements StaffServicePort {

    private final RestClient restClient;

    public StaffServiceAdapter(RestClient.Builder interServiceRestClientBuilder,
                               @Value("${rivoo.services.staff-service.url}") String staffServiceUrl) {
        this.restClient = interServiceRestClientBuilder.baseUrl(staffServiceUrl).build();
    }

    @Override
    public List<EmployeePublicInfo> getPublicEmployees(String tenantId) {
        try {
            List<EmployeePublicDto> dtos = restClient.get()
                    .uri("/api/internal/staff/{tenantId}/public/employees", tenantId)
                    // DIFERENCIA 1: header explicito. La peticion publica no tiene
                    // TenantContext, asi que InterServiceRestClientConfig no puede
                    // propagarlo. Sin esto, staff-service consulta SIN filtro de tenant.
                    .header("X-Tenant-Id", tenantId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return dtos == null ? List.of() : dtos.stream().map(this::toInfo).toList();
        } catch (Exception e) {
            // DIFERENCIA 2: degradacion elegante. Que staff-service caiga no puede
            // tumbar la pagina publica del salon; se muestra sin servicios.
            log.atError().setCause(e).log("Failed to fetch public employees from staff-service");
            return List.of();
        }
    }
    // getPublicServices: identico
}
```

- [ ] **Step 5: Configuración**

```yaml
# application.yml
rivoo:
  services:
    staff-service:
      url: ${STAFF_SERVICE_URL:http://localhost:8083}
```

- [ ] **Step 6: Tests en verde.**
- [ ] **Step 7: Commit** — `feat(salon): add staff-service adapter with explicit tenant header`

---

### Task 6: Agregado público del salón (Fase 5)

**Files:**
- Modify: `salon-service/src/main/java/com/rivoo/salon/application/dto/SalonPublicResponse.java`
- Modify: `salon-service/src/main/java/com/rivoo/salon/application/SalonService.java:59-64`
- Modify: `salon-service/src/main/java/com/rivoo/salon/infrastructure/mapper/SalonDtoMapper.java`
- Test: `salon-service/src/test/java/com/rivoo/salon/application/SalonPublicAggregateTest.java`

- [ ] **Step 1: Test que falla** — el agregado incluye horarios, servicios y empleados; y si staff-service devuelve vacío, el salón sigue respondiendo 200 con listas vacías.
- [ ] **Step 2: Verificar que falla.**
- [ ] **Step 3: Ampliar el DTO**

```java
public record SalonPublicResponse(
        String name,
        String slug,
        String phone,
        String description,
        String logoUrl,
        String primaryColor,
        String addressStreet,
        String addressCity,
        String addressPostalCode,
        List<BusinessHoursResponse> businessHours,
        List<ServicePublicInfo> services,
        List<EmployeePublicInfo> employees
) {
}
```

> **Decisión:** no se añade `id`. El frontend lo declara en su tipo pero no lo usa en ningún sitio (verificado con `grep -rn "salon\.id"` → 0 resultados en el flujo público). Añadir un identificador interno a una respuesta anónima sin necesitarlo es superficie de más.

- [ ] **Step 4: Componer en `SalonService.getPublicBySlug`**

```java
@Override
@Transactional(readOnly = true)
public SalonPublicResponse getPublicBySlug(String slug) {
    Salon salon = salonPersistencePort.findBySlug(slug)
            .orElseThrow(() -> new SalonNotFoundException(slug));

    if (salon.getStatus() != SalonStatus.ACTIVE) {
        throw new SalonNotFoundException(slug);   // un salon inactivo no existe para el publico
    }

    String tenantId = salon.getExternalId();      // para salones, external_id == tenant_id
    List<BusinessHoursResponse> hours = businessHoursPersistencePort.findBySalonId(salon.getId())
            .stream().map(salonDtoMapper::toBusinessHoursResponse).toList();

    return salonDtoMapper.toPublicResponse(salon, hours,
            staffServicePort.getPublicServices(tenantId),
            staffServicePort.getPublicEmployees(tenantId));
}
```

> **Nota:** el chequeo de `ACTIVE` es nuevo y deliberado. Hoy `getPublicBySlug` devuelve cualquier salón, incluso uno en `ONBOARDING` o suspendido por impago. `PublicBookingUseCase` sí lo comprueba (`AppointmentService.java:292-294`), así que hoy un cliente puede recorrer los 5 pasos de un salón suspendido y llevarse el error solo al confirmar.

- [ ] **Step 5: Tests en verde.**
- [ ] **Step 6: Commit** — `feat(salon): public salon endpoint returns hours, services and employees`

---

### Task 7: Disponibilidad pública (Fase 6)

**Files:**
- Modify: `appointment-service/.../port/in/CheckAvailabilityUseCase.java`
- Modify: `appointment-service/.../application/AppointmentService.java`
- Modify: `appointment-service/.../in/web/AppointmentController.java:108`
- Test: `appointment-service/src/test/java/com/rivoo/appointment/application/PublicAvailabilityTest.java`

- [ ] **Step 1: Test que falla** — resuelve slug→tenant, rechaza salón no `ACTIVE`, delega en el use case existente.

```java
@Test
void publicAvailability_resolvesTenantFromSlugAndDelegates() {
    when(salonServicePort.getSalonBySlug("bella-vista"))
            .thenReturn(new SalonInfo("sal_A", "Bella Vista", "ACTIVE"));

    service.getPublicAvailableSlots("bella-vista", "emp_1", LocalDate.of(2026, 9, 1), "svc_1");

    verify(checkAvailabilityUseCase).getAvailableSlots("sal_A", "emp_1", LocalDate.of(2026, 9, 1), "svc_1");
}

@Test
void publicAvailability_rejectsInactiveSalon() {
    when(salonServicePort.getSalonBySlug("suspendido"))
            .thenReturn(new SalonInfo("sal_B", "Suspendido", "SUSPENDED"));

    assertThatThrownBy(() -> service.getPublicAvailableSlots("suspendido", "emp_1", LocalDate.now(), "svc_1"))
            .isInstanceOf(BusinessValidationException.class);
}
```

- [ ] **Step 2: Verificar que falla.**
- [ ] **Step 3: Implementar el use case** — reutiliza `getAvailableSlots(tenantId, employeeId, date, serviceId)` tal cual; solo antepone la resolución de slug, exactamente como `AppointmentService.java:291-295`.
- [ ] **Step 4: Endpoint sin `@PreAuthorize`**

```java
@GetMapping("/public/availability")
public ResponseEntity<AvailabilityResponse> publicAvailability(
        @RequestParam String salonSlug,
        @RequestParam String employeeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) String serviceId) {
    log.atInfo().addKeyValue("salonSlug", salonSlug).log("GET /api/v1/appointments/public/availability");
    return ResponseEntity.ok(
            checkAvailabilityUseCase.getPublicAvailableSlots(salonSlug, employeeId, date, serviceId));
}
```

> Ruta bajo `/public/` a propósito: hace que la regla del gateway sea un prefijo cerrado (`/api/v1/appointments/public/**`) en vez de una ruta suelta que haya que recordar ampliar.

- [ ] **Step 5: Tests en verde.**
- [ ] **Step 6: Commit** — `feat(appointment): public availability endpoint resolved by salon slug`

---

### Task 8: Regla del gateway (Fase 7)

**Files:**
- Modify: `api-gateway/src/main/java/com/rivoo/gateway/config/GatewaySecurityConfig.java:26`
- Test: `api-gateway/src/test/java/com/rivoo/gateway/config/GatewaySecurityConfigTest.java`

- [ ] **Step 1: Test** — `GET /api/v1/appointments/public/availability` sin JWT → **no** 401; `GET /api/v1/appointments` sin JWT → **sigue** 401.
- [ ] **Step 2: Verificar que falla.**
- [ ] **Step 3: Añadir UNA línea**

```java
exchanges.pathMatchers(HttpMethod.GET, "/api/v1/appointments/public/**").permitAll();
```

> Solo `GET`. Es superficie pública nueva: cada regla añadida aquí es superficie de ataque, y por eso el plan agrega en el salón en lugar de abrir tres endpoints más.

- [ ] **Step 4: Tests en verde.**
- [ ] **Step 5: Commit** — `feat(gateway): allow anonymous GET on public availability`

---

### Task 9: Tipos y cliente API del frontend (Fase 8)

**Files:**
- Modify: `rivoo-frontend/src/types/salon.ts:24-34`
- Modify: `rivoo-frontend/src/lib/api/appointments.ts`

- [ ] **Step 1: Corregir `SalonPublic` para que refleje el backend real**

```ts
export interface EmployeePublic {
  id: string
  firstName: string
  lastName: string
  jobTitle: string | null
  serviceIds: string[]
}

export interface ServicePublic {
  id: string
  name: string
  description: string | null
  durationMinutes: number
  price: number
  currency: string
}

export interface SalonPublic {
  name: string
  slug: string
  phone: string
  description: string | null
  logoUrl: string | null
  primaryColor: string | null
  addressStreet: string
  addressCity: string
  addressPostalCode: string
  businessHours: BusinessHoursResponse[]
  services: ServicePublic[]
  employees: EmployeePublic[]
}
```

> **Ojo:** desaparecen `id` y `address`. `address` era un campo compuesto que el backend nunca envió; hay **3 usos** que hay que arreglar: `book/[slug]/page.tsx:56-57`, `public-success-step.tsx:53-54`. Componer con un helper `formatAddress(salon)` → `` `${addressStreet}, ${addressCity} ${addressPostalCode}` ``, igual que `settings/salon/page.tsx:88`.

- [ ] **Step 2: Añadir el cliente de disponibilidad pública** (sin token)

```ts
getPublicAvailability: (params: {
  salonSlug: string; employeeId: string; date: string; serviceId?: string
}) =>
  apiFetch<AvailabilityResponse>(
    `/api/v1/appointments/public/availability?${toQueryString(params)}`
  ),
```

- [ ] **Step 3: `npx tsc --noEmit`** → los errores que salgan son exactamente los 3 usos de `salon.address`. Arreglarlos.
- [ ] **Step 4: Commit** — `fix(types): SalonPublic now matches what the backend actually returns`

---

### Task 10: Store de 6 pasos (Fase 9)

**Files:**
- Modify: `rivoo-frontend/src/lib/stores/public-booking-store.ts:53`
- Test: `rivoo-frontend/src/lib/stores/public-booking-store.test.ts`

- [ ] **Step 1: Test que falla** — `nextStep` debe topar en **6**, no en 5.
- [ ] **Step 2: Verificar que falla.** Run: `npx vitest run public-booking-store`
- [ ] **Step 3: Cambiar `Math.min(s.step + 1, 5)` → `Math.min(s.step + 1, 6)`.**
- [ ] **Step 4: Tests en verde.**
- [ ] **Step 5: Commit** — `fix(booking): public flow has 6 steps, not 5`

---

### Task 11: Paso Profesional y cableado (Fase 10)

**Files:**
- Create: `rivoo-frontend/src/components/booking/public-employee-step.tsx`
- Modify: `rivoo-frontend/src/components/booking/public-service-step.tsx:22`
- Modify: `rivoo-frontend/src/components/booking/public-datetime-step.tsx:32-45`
- Modify: `rivoo-frontend/src/components/booking/public-confirm-step.tsx:41`
- Modify: `rivoo-frontend/src/app/book/[slug]/page.tsx:63-82`

- [ ] **Step 1: Crear `PublicEmployeeStep`** siguiendo el inventario visual de arriba. Reglas de comportamiento:
  - "Sin preferencia" primero → `selectEmployee(null, true)`.
  - Empleado cuyo `serviceIds` **no** contiene `selectedService.id` → `opacity-50 pointer-events-none` y el texto "No ofrece {nombre del servicio}" en lugar del puesto (`ReservaPaso2.dc.html:94`).
  - Al elegir, `selectEmployee(id, false)` + `nextStep()`.
- [ ] **Step 2: Limpiar `public-service-step.tsx:22`** — borrar `as unknown as { services?: ... }`; `salon.services` ya está tipado.
- [ ] **Step 3: `public-datetime-step.tsx`** — usar `appointmentsApi.getPublicAvailability`. Si `anyEmployee` es `true`, hay que resolver un empleado: para esta entrega, consultar la disponibilidad del **primero que ofrezca el servicio** y fijarlo en el store antes de confirmar, porque `employeeExternalId` es `@NotBlank` en el backend.
- [ ] **Step 4: `public-confirm-step.tsx:41`** — `employeeExternalId: selectedEmployeeId ?? undefined` deja de poder ser `undefined`. Si lo fuera, es un bug de flujo: lanzar en vez de mandar una petición que se sabe que va a dar 400.
- [ ] **Step 5: `book/[slug]/page.tsx`** — `[1,2,3,4]` → `[1,2,3,4,5,6]`; `step < 5` → `step < 6`; insertar `<PublicEmployeeStep />` en `step === 2` y desplazar los demás.
- [ ] **Step 6: Verificar** — `npx tsc --noEmit`, `npx vitest run`, `npx next build`.
- [ ] **Step 7: Commit** — `feat(booking): add professional step to public booking flow`

---

### Task 12: Verificación de aislamiento cross-tenant (Fase 11)

**Files:**
- Test: `staff-service/src/test/java/com/rivoo/staff/CrossTenantIsolationIT.java`
- Test: `salon-service/src/test/java/com/rivoo/salon/PublicAggregateIsolationIT.java`

- [ ] **Step 1: Integración con Testcontainers** — sembrar dos tenants (`sal_A`, `sal_B`) con empleados y servicios cada uno.
- [ ] **Step 2: Test** — `GET /api/internal/staff/sal_A/public/employees` devuelve **solo** los de `sal_A`. Repetir sin el header `X-Tenant-Id`: debe seguir devolviendo solo los de `sal_A`, porque el filtro es por columna, no ambiental.
- [ ] **Step 3: Test del agregado** — `GET /api/v1/salons/public/bella-vista` no contiene ningún `external_id` de `sal_B`.
- [ ] **Step 4: Test de PII** — la respuesta pública no contiene ninguna cadena con `@` ni ningún teléfono de la semilla.
- [ ] **Step 5: Ejecutar.** Run: `./mvnw -pl staff-service,salon-service verify`
- [ ] **Step 6: Commit** — `test: prove public endpoints cannot leak across tenants`

---

## Verificación final

1. `cd /e/IdeaProjects/rivoo && ./mvnw clean verify` → BUILD SUCCESS en los 10 módulos.
2. `bash infrastructure/scripts/dev-full-stack.sh` y, sin sesión iniciada (ventana de incógnito):
   - `curl http://localhost:8080/api/v1/salons/public/bella-vista` → 200 con `services` y `employees` no vacíos, **sin `email` ni `phone`**.
   - `curl "http://localhost:8080/api/v1/appointments/public/availability?salonSlug=bella-vista&employeeId=emp_1&date=2026-09-01"` → 200.
   - `curl http://localhost:8080/api/v1/staff/employees` → **401**. (Si esto devuelve 200, la Task 8 abrió de más.)
3. Frontend: `npm run dev`, ir a `/book/bella-vista` en incógnito y **completar la reserva de punta a punta**. La cita debe aparecer en `/today` del dueño con `source: ONLINE` y `status: PENDING`.
4. **Comparación visual:** capturar `/book/bella-vista` paso a paso a 390px y 1440px y compararlo con `ReservaPaso1..6.dc.html` y `ReservaDesktopPaso1..6.dc.html` elemento a elemento. Artefactos esperados en `docs/specs/reserva-publica/verificacion/paso-{1..6}-{movil,escritorio}.png`.
5. `npx tsc --noEmit` limpio · `npx vitest run` en verde · `npx next build` sin errores.

---

## Execution Order

**Backend (`E:\IdeaProjects\rivoo`):**

```
Fase 1  DTOs publicos              (sin dependencias)
Fase 6  Disponibilidad publica     (sin dependencias)   ─┐ paralelas
                                                         │
Fase 2  Listados por tenant        depende de 1          │
Fase 7  Regla del gateway          depende de 6         ─┘

Fase 3  Endpoints internos         depende de 2
Fase 4  StaffServiceAdapter        depende de 3
Fase 5  Agregado publico           depende de 4
Fase 11 Aislamiento cross-tenant   depende de 5
```

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**

```
Fase 9  Store de 6 pasos           (sin dependencias)
Fase 8  Tipos y cliente API        solo necesita el contrato (Fases 1 y 6)
Fase 10 Paso Profesional           depende de 8, 9
```

**Coordinación:** en cuanto las Fases 1 y 6 fijan el contrato de DTOs, **backend y frontend avanzan en paralelo** (repos distintos, colisión imposible). El frontend puede completarse contra datos falsos y `/dev/preview` antes de que el backend esté listo. La verificación final (punto 3 de arriba) es el único momento que exige ambos vivos.

---

## Dependencies on other specs/FRs

| Spec/FR | Relación | Implicación |
|---|---|---|
| **Reskin visual** (`design/`, ya entregado) | **Pre-requisito** — cumplido | Los tokens terracota y `--motion-*` ya están en `globals.css`. El paso nuevo los usa sin añadir nada |
| **Detalle de cita** (`appointments/[id]`, placeholder) | **Complementaria** | Comparte el concepto de cita `PENDING` llegada por `ONLINE`. Sin bloqueo mutuo |
| **Vista de semana del calendario** (13E.1b) | **Consumidora** | Reutilizará el `SegmentedControl` creado en el reskin, no nada de este plan |
| **`salon-setup` huérfano** (deuda técnica) | **Sin relación** | Documentado aparte; no tocar en esta entrega |

> Orden recomendado: este plan primero. Es el único de los cuatro que arregla una funcionalidad **rota en producción**; los demás son mejoras.

---

## Decisiones de diseño registradas

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Agregar en `GET /salons/public/{slug}` | 3 endpoints públicos nuevos (servicios, empleados, disponibilidad) | El gateway ya permite `/salons/public/**`: 1 regla nueva en vez de 3. Menos superficie de ataque y 1 petición en vez de 3 |
| Disponibilidad **fuera** del agregado | Meterla también en el agregado | Depende de fecha y empleado y cambia cada minuto: envenenaría cualquier caché del salón |
| `EmployeePublicResponse` nuevo | Reutilizar `EmployeeInternalResponse` | Ese lleva `email` y `phone`. Un record aparte hace la fuga imposible por descuido |
| Filtro por columna (`findByTenantIdAndActiveTrue`) | Confiar en el `@Filter` de Hibernate | El `TenantContext` está vacío en petición pública y el filtro se desactiva con tenant nulo (`TenantFilterAspect:20,30`) |
| `getPublicBySlug` rechaza salón no `ACTIVE` | Dejarlo como está | Hoy un cliente recorre 5 pasos de un salón suspendido y solo falla al confirmar |
| Chip "primer hueco libre" fuera de alcance | Implementarlo ya | Exige N cálculos de disponibilidad al cargar el paso. El paso funciona sin él |
