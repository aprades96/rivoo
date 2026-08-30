# Pantalla "Hoy" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: usa `executing-plans` para implementar
> este plan tarea a tarea. Los pasos usan casillas (`- [ ]`) para seguimiento.

**Objetivo:** que `/today` sea identica a sus dos artboards y que los datos que
pinta sean de verdad los de HOY — hoy no lo son.

**Arquitectura:** la pantalla se reconstruye sobre `PageShell` (chasis ya cerrado)
con un unico `useMediaQuery` a 1024px decidiendo por MONTAJE CONDICIONAL, nunca
con clases. Todo el dato que pinta sale de tres consultas ya existentes
(`useTodayAppointments`, `useEmployees`, `useEmployeesWorkingHours`) mas la de
servicios que ya usa la guarda de catalogo: **cero endpoints nuevos y cero cambios
de backend**. La derivacion (KPIs, "Ahora mismo", reservas online sin confirmar)
vive en modulos PUROS y testeables, separada del JSX, igual que
`appointment-detail-facts.ts` y `wizard-summary.ts`.

**Tech Stack:** Next.js 16 App Router · TypeScript · Tailwind v4 · Shadcn/UI +
`@base-ui/react` · React Query v5 · Vitest 4 · Playwright.

**Complejidad:** **Compleja** (10+ ficheros, dos pantallas afectadas, un fallo de
datos transversal). Ejecucion con `executing-plans`; revision final = panel de 3
revisores independientes (T12).

---

> ### AVISO SOBRE ESTE DOCUMENTO
>
> **Cada hecho esta en UN SOLO SITIO.** §1 son los datos verificados, §2 las
> decisiones, §3 el reparto de ficheros, §5 las tareas — y las tareas
> **referencian** §1/§2 en vez de repetir valores. Cinco versiones del plan del
> bloque 2 se hundieron por duplicar y divergir. Si al implementar necesitas un
> valor, ve a §1; si necesitas un porque, ve a §2.
>
> **Todas las referencias `fichero:linea` se generaron con `grep -n` contra el
> fichero real.** Si lees un fichero con un comando que renumere (`sed -n 'N,$p'
> | cat -n` renumera desde 1), NO transcribas esos numeros.

---

## 1 · Datos verificados

### 1.1 · Artboard movil — `design/Main.dc.html` (390x844)

El fichero se llama `Main.dc.html`; `design/canvas.json` lo titula **"Hoy"**.
Buscar `Hoy.dc.html` no encuentra nada y **no** significa que falte el diseno.

| Zona | Linea | Valores medidos |
|---|---|---|
| Cabecera 56px | `:23-29` | `padding: 0 16px`, `border-bottom 1px #E7DCCF`, bg `#FBF7F2`. Izquierda: **NOMBRE DEL SALON** `.display` 21px `letter-spacing -0.01em`. Derecha: "Maria G." 12px `#7A6A5F` + avatar 32px circulo `accentSoft`/`accentDark` 11px/600 |
| Cuerpo | `:31` | `padding: 16px 16px 80px`, `gap: 16px` |
| Saludo | `:33-41` | "Buenos dias, Maria" `.display` 27px `line-height 1.1` `tracking -0.015em`; fecha "Martes, 27 de agosto" 13px `#7A6A5F`; `gap: 2px`. A la derecha, refrescar **44x44** radio 8 borde `#E7DCCF` bg `#FFFFFF`, icono 18px `#7A6A5F` sw 1.75 |
| KPIs (**TRES**) | `:43-65` | `grid-template-columns: repeat(3, minmax(0,1fr))`, `gap: 8px`. Cada uno: `padding 12px`, radio 8, borde `#E7DCCF`, bg `#FFFFFF`, `gap: 2px`. Fila superior: icono **14px** + label **11px**. Numero `.display .num` **30px** `line-height 1.05` |
| KPI 1 | `:44-50` | "Total", icono `calendar-check`, color `#7A6A5F`, valor 8 |
| KPI 2 | `:51-57` | "Pendientes", icono `clock`, borde `#E8D3A6`, bg `#FAEFD6`, color `#8A5B12` (label Y numero), valor 2 |
| KPI 3 | `:58-64` | "Completadas", icono `check-circle`, color `#7A6A5F`, valor 3 |
| Tarjeta "Ahora mismo" | `:67-112` | `padding 12px`, radio **10**, borde `accentLine`, bg `accentSoft`, `gap: 10px` |
| — rotulo | `:68-71` | **DENTRO** de la tarjeta. "Ahora mismo" 11px/600 uppercase `letter-spacing .06em` color `accentDark`, y **la HORA ACTUAL** al lado ("10:10", `.num` 11px/600 `accentDark`), separados con `justify-content: space-between` |
| — fila | `:75-84` | punto **8px** radio 999 color del empleado `margin-top: 5px`; `gap: 10px`. Nombre 14px/600; badge a la derecha; segunda linea 12px `#7A6A5F`; `gap: 2px` |
| — separador | `:86` | `height: 1px`, bg `accentLine` |
| — badge ocupado | `:80` | "En curso": `padding 2px 8px`, radio 999, bg `#E2E9EE`, color `#3A5A70`, 10px/600, `white-space: nowrap` |
| — badge libre | `:93` | "Libre 2h 20min": `padding 2px 8px`, radio 999, **borde 1px `#D8C9B8`**, color `#7A6A5F`, 10px/600 |
| — 2a linea ocupado | `:82` | "Ana Garcia · Corte + Tinte · hasta las 11:30" — **CON el servicio**; la hora en `.num` |
| — 2a linea libre | `:95` | "Siguiente: 12:30 · Laia Roca" — la hora en `.num` |
| Rotulo de la lista | `:115` | "Todas las citas de hoy" 13px **`font-weight: 500`** `#7A6A5F`; contenedor `gap: 8px` |
| Fila de cita | `:117-136` | `padding 12px`, radio 8, borde `#E7DCCF`, bg `#FFFFFF`, `gap: 12px`, `align-items: center` |
| — columna hora | `:118-121` | ancho **56px**, centrada. Hora `.display .num` **22px** `line-height 1.1`; duracion 10px `#7A6A5F` |
| — barra | `:122` | `width: 2px`, radio 999, **color del empleado**, `align-self: stretch` |
| — columna datos | `:123-135` | `gap: 5px`. Nombre 14px/600 + badge (`padding 3px 8px`, radio 999, 10px/600). Linea de servicio: **icono de tijeras 12px** + nombre + `·` + precio, todo 12px `#7A6A5F`, precio en `.num`. **TERCERA** linea: "Sofia Puig · 09:00 - 10:00" 12px `#7A6A5F` |
| — badge confirmada | `:126` | bg `#E4EDE1`, color `#3F6B4F` |
| — badge en curso | `:147` | bg `#E2E9EE`, color `#3A5A70` |
| FAB | `:182-184` | 56px, `right: 16px`, `bottom: 80px`, bg `accent`, sombra `0 6px 18px rgba(42,35,32,.22)`. **Ya lo pinta `(app)/layout.tsx`** (`:13` `FAB_ROUTES` incluye `/today`, `:35` solo movil, `:112`). **T8 no lo monta: lo duplicaria** |
| Barra inferior | `:186-204` | 64px — la pinta `(app)/layout.tsx`, no esta pantalla |

**NO dibuja**: tarjeta de reservas online sin confirmar, KPI de facturacion,
tarjeta "Proxima cita".

Los `{{accent*}}` son props del canvas (`:207-224`), no del producto. Con el
acento por defecto `#B4522F`: `accentSoft = #F9F1EE`, `accentLine = #ECD2C9`,
`accentDark = #904226`. Ver §1.3.H.

`accentDark` **no es decorativo**: es el color del rotulo "AHORA MISMO" y de la
hora en movil (`Main:69-70`). El `mix()` del canvas (`Main:209-222`) es
`round(x + (y - x) * t)` con `t = 0.2` sobre negro: `180->144 (0x90)`,
`82->66 (0x42)`, `47->38 (0x26)`. **`#904226`**, que **no** coincide con
`--primary-pressed` (`#8f3f24`). Lo resuelve D14.

### 1.2 · Artboard escritorio — `design/HoyDesktop.dc.html` (1440x900)

| Zona | Linea | Valores medidos |
|---|---|---|
| Barra lateral 248px | `:28-70` | Ya construida por el bloque 2. No se toca |
| Topbar | `:74-88` | `height: 72px`, `padding: 0 28px`, `border-bottom 1px #E7DCCF`. Saludo `.display` **24px** `line-height 1.1`; subtitulo "Martes, 27 de agosto · 10:10" 12px `#7A6A5F` con la hora en `.num`; `gap: 1px` |
| — acciones | `:79-87` | refrescar **38x38** radio 8 borde `#E7DCCF` bg blanco icono 17px; CTA "Nueva cita" `height 38`, `padding 0 16px`, radio 8, bg `#B4522F`, blanco 14px/600, icono `+` 17px sw 2, `gap: 8px`. Entre los dos, `gap: 10px` |
| Contenido | `:90` | `padding: 24px 28px`, `gap: 20px` |
| KPIs (**CUATRO**) | `:92-109` | `repeat(4, minmax(0,1fr))`, `gap: 14px`. `.kpi` (`:20`): `padding 14px 16px`, radio **10**, borde `#E7DCCF`, bg blanco, `gap: 2px`. Label **12px SIN ICONO**; numero `.display .num` 30px `line-height 1.05` |
| KPI 1 | `:93-96` | "Citas hoy", 8 |
| KPI 2 | `:97-100` | "Pendientes de confirmar", borde `#E8D3A6`, bg `#FAEFD6`, color `#8A5B12`, 2 |
| KPI 3 | `:101-104` | "Completadas", 3 |
| KPI 4 | `:105-108` | "Facturacion prevista", **"412 €"** |
| Dos columnas | `:111` | `minmax(0, 1.6fr) minmax(0, 1fr)`, `gap: 20px` |
| Rotulo lista | `:114` | "Todas las citas de hoy" 13px **`font-weight: 600`** `#7A6A5F`; contenedor `gap: 10px` |
| Fila `.cita` | `:21`, `:116-128` | `padding: 12px 14px`, `gap: 14px`, radio 8, borde `#E7DCCF`, bg blanco, `align-items: center` |
| — columna hora | `:117-120` | ancho **60px**. Hora `.display .num` **21px** `line-height 1.1`; duracion 10px |
| — barra | `:121` | `width: 2px`, radio 999, color del empleado |
| — centro | `:122-125` | `gap: 3px`. Nombre 14px/600; **"Corte y secado · Sofia Puig" 12px `#7A6A5F`** — SIN icono, SIN precio, SIN rango horario |
| — precio | `:126` | **COLUMNA PROPIA**, `.num` 14px/600 |
| — badge | `:127`, `:22` | `.badge`: `padding 3px 9px`, radio 999, 10px/600, `nowrap`. Confirmada `#E4EDE1`/`#3F6B4F`; En curso `#E2E9EE`/`#3A5A70`; Pendiente `#FAEFD6`/`#8A5B12` |
| — fila en curso | `:130` | **`border-color: #DCC9BB`** en vez de `#E7DCCF`. Es la UNICA fila con borde distinto y es justo la "En curso" (ver §1.3.K) |
| Rotulo "Ahora mismo" | `:188` | **FUERA** de la tarjeta. 13px/**600** `#7A6A5F`. **SIN hora** |
| Tarjeta "Ahora mismo" | `:190-228` | `padding 16px`, radio 10, borde `#EBD3C8`, bg `#FAEFE9`, `gap: 14px`. Filas **casi** identicas al movil: cambian la 2a linea, el `gap` interno de la tarjeta (14 vs 10) y el badge, que aqui usa la clase `.badge` (`3px 9px`) y en movil va en linea (`2px 8px`, `Main:80,93`) |
| — 2a linea ocupado | `:199` | "Ana Garcia · hasta las 11:30" — **SIN el servicio** |
| — separador | `:203` | `height: 1px`, bg `#EBD3C8` |
| Tarjeta reservas online | `:230-234` | `padding 16px`, radio 10, borde `#E8D3A6`, bg `#FFFCF5`, `gap: 8px`. Titulo "2 reservas online sin confirmar" 13px/600 `#8A5B12`. Cuerpo 12px `#7A6A5F` `line-height 1.5`: "Laia Roca (12:30) y Jordi Mas (16:00) estan esperando respuesta del salon." CTA `height 38`, `margin-top 4px`, radio 8, bg `#B4522F`, blanco 13px/600, centrado: "Revisar y confirmar" |

### 1.3 · Inconsistencias entre los dos artboards

Todas medidas. Su resolucion esta en §2; aqui solo el hecho.

> **Esta lista NO es exhaustiva y no debe leerse como tal.** Recoge las que
> obligan a DECIDIR. Las tablas de §1.1 y §1.2 son la fuente: **cuando una tarea
> pinta la misma pieza en los dos anchos, se comparan las dos filas de la tabla,
> valor a valor**. Los apartados L-R de abajo son diferencias menores ya medidas
> que no cambian ninguna decision, pero que hay que respetar.

- **A.** Movil dibuja **3** KPIs (`Main:43-65`), escritorio **4** (`HoyDesktop:92-109`). Movil no tiene "Facturacion prevista".
- **B.** Etiquetas distintas: "Total"/"Pendientes" vs "Citas hoy"/"Pendientes de confirmar". Movil **con** iconos de 14px, escritorio **sin**.
- **C.** El rotulo de la lista es `font-weight: 500` en movil (`Main:115`) y **600** en escritorio (`HoyDesktop:114`).
- **D.** La fila de cita es **otra pieza** en cada ancho: movil lleva icono de tijeras, precio dentro de la linea de servicio y una TERCERA linea con empleado + rango horario; escritorio lleva "servicio · empleado" en una linea, el precio como columna propia y ninguna tercera linea.
- **E.** "Ahora mismo": rotulo **dentro** de la tarjeta y **con** hora actual en movil; **fuera** y **sin** hora en escritorio (la hora vive en el subtitulo de la topbar).
- **F.** La segunda linea del ocupado lleva el **servicio** en movil (`Main:82`) y no en escritorio (`HoyDesktop:199`).
- **G.** La tarjeta "N reservas online sin confirmar" **solo existe en escritorio**.
- **H.** El rosa de la tarjeta "Ahora mismo" **no coincide**: movil lo deriva de `{{accent}}` (`#F9F1EE` fondo / `#ECD2C9` borde con el acento por defecto), escritorio lo fija a `#FAEFE9` / `#EBD3C8`.
- **I.** El KPI de facturacion dibuja **"412 €"** (entero, espacio normal), pero **el propio artboard escribe los demas precios con dos decimales** ("35,00 €" en `Main:132` y `HoyDesktop:126`). Se contradice consigo mismo.
- **J.** Columna de hora: 22px sobre 56px de ancho en movil; **21px sobre 60px** en escritorio.
- **K.** La fila "En curso" de escritorio lleva `border-color: #DCC9BB` (`HoyDesktop:130`) donde las otras cuatro llevan `#E7DCCF`. Es la unica excepcion y coincide exactamente con el unico estado en curso. **La fila "En curso" del movil NO lo lleva** (`Main:138` usa `#E7DCCF`): el otro artboard dibuja la misma pieza y la contradice. Lo resuelve D32/D13.

Diferencias menores, medidas, sin decision propia — se respetan tal cual:

- **L.** `gap` interno de la tarjeta "Ahora mismo": **10px** movil (`Main:67`) vs **14px** escritorio (`HoyDesktop:190`).
- **M.** `gap` del contenedor de la lista de citas: **8px** movil (`Main:114`) vs **10px** escritorio (`HoyDesktop:113`).
- **N.** Badge de la fila de cita: `3px 8px` movil (`Main:126`) vs `3px 9px` escritorio (`.badge`, `HoyDesktop:22`).
- **O.** Badge de las filas del panel "Ahora mismo": `2px 8px` movil (`Main:80,93`) vs `3px 9px` escritorio (`.badge`, `HoyDesktop:197,210`).
- **P.** Padding de la fila de cita: `12px` movil (`Main:117`) vs `12px 14px` escritorio (`HoyDesktop:21`); su `gap`, **12** vs **14**.
- **Q.** `gap` de la columna central de la fila de cita: **5px** movil (`Main:123`) vs **3px** escritorio (`HoyDesktop:122`).
- **R.** Separador del panel: `accentLine` (`#ECD2C9`) en movil vs `#EBD3C8` en escritorio — la misma unificacion de D14.

### 1.4 · Estado del codigo

`src/app/(app)/today/page.tsx`, 300 lineas, `"use client"` (`:1`).

- Monta `PageShell` (`:86-117`) con `title` = saludo (`:78`), `subtitle` = fecha + hora (`:88`, **solo escritorio**), `actions` = [refrescar `outline`/`icon` 38px (`:96-105`), `Link` "Nueva cita" `size="action"` (`:110-113`)], `mobileActions` = `UserBadge` (`:116`).
- Calcula **cuatro** stats (`:57-63`) y pinta **tres**: `total = sorted.length` (**incluye `CANCELLED` y `NO_SHOW`**), `pending`, `completed`. `confirmed` (`:60`) se calcula y **se descarta**.
- Pinta una tarjeta **"Proxima cita"** (`:188-196`) que **no dibuja ningun artboard**.
- `nextAppointment` (`:71-76`) compara `new Date().toISOString()` (UTC, con `Z`) contra `startTime`, que **todo el repo trata como ISO local sin offset** (`calendar.ts:135-136` `localIso()`, y todas las fixtures). En `Europe/Madrid` da por futura una cita que empezo hasta 2h antes. No memoizado, a diferencia de `sorted` (`:52`) y `stats` (`:57`).
- La fila fecha + refrescar 44x44 usa **`lg:hidden`** (`:125`), violando la regla de §1.7.7: en jsdom quedan **dos** botones `aria-label="Actualizar"` montados a la vez (`:99` y `:130`).
- Los KPIs son `grid grid-cols-3` **fijo en los dos anchos** (`:168`). No hay 4 KPIs, ni dos columnas, ni panel "Ahora mismo", ni tarjeta de reservas online.
- `hasNoServices` (`:43-44`) exige `!servicesError` a proposito (`:32-42`): sin eso un 5xx taparia la agenda entera. Cubierto por `page.test.tsx:141`.
- `StatCard` es **privado** (`:273-293`) y usa `border-yellow-300 bg-yellow-50` — hex crudos de Tailwind en un repo que usa tokens en todo lo demas.
- `formatShortName`/`getInitials` (`:260-271`) duplican parcialmente `initials()` (`format.ts:23`) con reglas distintas.
- `UserBadge` (`:249-258`) **no** reutiliza `UserCard` de la barra lateral, y el comentario `:245-248` explica por que.
- `AppointmentCard` (`appointment-card.tsx:16`) es el componente central de la pantalla y **no tiene fichero de test**. Usa `formatDuration` (con espacio) donde el artboard pide `formatDurationTight` ("30min").
- Tests: `today/page.test.tsx` tiene **4** tests, todos sobre la guarda de catalogo de servicios. **Ninguno** cubre KPIs, "Proxima cita", saludo, fecha, el boton Actualizar, `UserBadge`, la apertura de la hoja, ni nada de escritorio.

### 1.5 · EL FALLO QUE MANDA SOBRE TODO EL BLOQUE: `/today` y `/calendar` no filtran por fecha

Verificado a mano en los dos repos.

1. El frontend declara `date?: string` en `AppointmentListParams` (`types/appointment.ts:106`) y lo serializa tal cual (`lib/api/appointments.ts:14-21`).
2. `useTodayAppointments` manda `{ date, page: 0, size: 100 }` (`use-appointments.ts:82`); `/calendar` manda `{ date: dateStr, employeeId?, page: 0, size: 200 }` (`calendar/page.tsx:147`).
3. **El controlador solo acepta `employeeId`, `startDate` (`Instant`), `endDate` (`Instant`), `status` y `Pageable`** (`AppointmentController.java:70-74`). `date` solo existe en los dos endpoints de disponibilidad (`:112`, `:124`).
4. **Spring descarta en silencio los parametros que no conoce.** No hay reescritura en el gateway (`application-local.yml:31` y `application-prod.yml:31` solo enrutan por `Path`; cero filtros `AddRequestParameter`/`RewritePath`).
5. El JPQL fija **`ORDER BY a.startTime DESC`** (`AppointmentJpaRepository.java:63`).

**Consecuencia:** las dos pantallas reciben las N citas **mas lejanas en el
futuro**, de cualquier fecha. `/today` pinta y cuenta esas 100 (`stats.total` es
literalmente `sorted.length`, asi que un salon con 100+ citas futuras veria
"Total: 100" todos los dias). `/calendar` pide 200, asi que **un salon con mas de
200 citas posteriores a hoy veria el dia de hoy vacio**.

`startDate` / `endDate` estan declarados (`types/appointment.ts:107-108`) y **no
los usa nadie**.

**Y hay un test que lo cementa:** `use-appointments.test.tsx:287` afirma que
`list` se llamo con `{ date, page: 0, size: 100 }` — o sea lo que el frontend
**envia**, nunca lo que el servidor **honra**. Verde con el fallo dentro.

### 1.6 · Limites reales de la API

Todo verificado leyendo el backend. **Cero `nativeQuery` en todo el monorepo**,
asi que no hay riesgo de fuga cross-tenant por consulta nativa en ninguna ruta
implicada.

1. **No existe endpoint de resumen usable.** El unico que se llama `stats`
   (`AppointmentInternalController.java:27`, expuesto en
   `AdminController.java:65`) es `PLATFORM_ADMIN`, **mensual**, y dos de sus tres
   campos devuelven siempre `{}`: `countByTenantAndStatus`/`...AndSource` son
   stubs que retornan `0` (`AppointmentPersistenceAdapter.java:70-79`), y las
   consultas agrupadas que si existen (`AppointmentJpaRepository.java:80,83`)
   **no las llama nadie**. `GET /api/v1/admin/dashboard` devuelve `0L` y
   `Map.of()` hardcodeados (`AdminController.java:134-135`).
2. **`GET /api/v1/appointments`**: params reales en §1.5.3. Filtra
   `startTime >= :startDate AND startTime < :endDate` (media abierta,
   `AppointmentJpaRepository.java:57-70`). Sin `status` **incluye `CANCELLED` y
   `NO_SHOW`**, y como `status` acepta **un solo valor**, no hay forma de pedir
   "todas menos las canceladas" en una llamada. Default `size=20` de Spring (no
   hay `spring.data.web.pageable.*` en ningun `.yml`).
3. **`servicePrice` viaja en la cita y lo escribe alguien de verdad**:
   `AppointmentService.java:136` (salon) y `:363` (booking publico), como
   snapshot inmutable (`service_price DECIMAL(10,2) NOT NULL`). **La facturacion
   prevista NO exige cruzar con el catalogo.** La MONEDA no viaja en la cita
   (esta en `ServiceOfferingResponse.currency` y `salons.currency`, default
   `EUR`).
4. **`GET /api/v1/staff/employees`** solo acepta `Pageable` y devuelve **solo
   activos** (`EmployeeService.java:116` → `findAllActive`). Trae `colorHex`
   (default `#3B82F6`, `EmployeeService.java:81`) y `jobTitle`.
5. **`GET /api/v1/staff/employees/{id}/working-hours`** no acepta parametros y
   devuelve **siempre los 7 dias** (`EmployeeService.java:193-199`). Forma:
   `{ dayOfWeek (1=Lun..7=Dom), isOpen, openTime, closeTime, breakStartTime,
   breakEndTime }`, los tres ultimos nullable. **No existe endpoint bulk** → N
   llamadas.
6. **`source`** existe (`ONLINE|PHONE|WALK_IN|MANUAL`). El booking publico lo
   fija a `ONLINE` **hardcodeado y no falseable desde el request**
   (`AppointmentService.java:368`). Pero **no es filtrable en el servidor**, y
   `parseSource` (`:422-431`) se traga en silencio un valor invalido devolviendo
   `MANUAL`.
7. **`PENDING` es el estado inicial de TODAS las citas**, tambien las de
   mostrador (`AppointmentService.java:140`). `?status=PENDING` **no** significa
   "reserva online sin confirmar".
8. **Filtrar `source` en cliente sobre una pagina da un contador incorrecto**,
   porque la paginacion se aplica en SQL antes del filtro. Solo es fiable sobre
   el conjunto completo del rango.
9. `PUT /api/v1/appointments/{id}/status` (`AppointmentController.java:88`)
   existe y `PENDING → CONFIRMED` es transicion valida
   (`AppointmentStatus.java:23`). **Una llamada por cita**; no hay confirmacion
   en lote.
10. `totalVisits`/`lastVisitAt` **siguen sin escribirlos nadie** (unica escritura
    `.totalVisits(0)` al crear; `lastVisitAt` jamas). Irrelevante para esta
    pantalla — ver §2.D6.
11. `AvailabilityService.java:153-158`: **sin `serviceId` devuelve los huecos
    libres crudos y sale ANTES de calcular `now`**, o sea devuelve huecos ya
    pasados. Irrelevante aqui (§2.D6), pero queda anotado como deuda.
12. **Aviso de permisos:** el `@PreAuthorize` del listado permite `EMPLOYEE`
    (`AppointmentController.java:68`) y `AppointmentService.list` (`:174-180`)
    no restringe nada al empleado que llama. Ese rol veria la agenda **y la
    facturacion** de todo el salon. No es un fallo de esta pantalla, pero
    condiciona a quien se le ensena. Deuda.

### 1.7 · Trampas del repo (en vigor, todas medidas)

1. **`tailwind-merge` BORRA un `leading-*` escrito ANTES de un `text-[Npx]`
   dentro de `cn()`.** Medido: `twMerge("text-sm leading-tight font-semibold
   text-[15px]")` → `"font-semibold text-[15px]"`. El `leading-*` va **siempre
   detras**.
2. **La preflight impone `line-height: 1.5`** y los artboards no declaran ninguno
   (= `normal`, ~1.25). **Cada `text-[Npx]` necesita un `leading-*` detras.**
3. **Los artboards se escriben SIN tildes y el codigo las produce.** Los textos
   VISIBLES van con tilde; los comentarios del repo van en ASCII sin tildes
   (convencion existente).
4. **`formatCurrency` emite U+00A0 antes del €.** Toda asercion de precio
   necesita los helpers `normalize`/`exact` (patron en
   `src/components/calendar/appointment-block.test.tsx:43-51` — en `calendar/`, NO en
   `appointments/`).
5. **`src/test/setup.ts` devuelve SIEMPRE `matches: false` en `matchMedia`.**
   Todo test de escritorio necesita su `mockMatchMedia(true)` local y su
   `afterEach`.
6. **Testing Library busca `data-testid`, NO `data-slot`.**
7. **jsdom no aplica CSS ni calcula layout.** Un elemento con `hidden lg:block`
   **sigue en el DOM**. Las diferencias de ancho se deciden con un **unico
   `useMediaQuery` a 1024px y montaje condicional en JS**, nunca con clases. La
   regla escrita esta en `page-shell.tsx:101-103` y la version larga en
   `new-appointment-shell.tsx:40-58`.
8. **PROHIBIDO tocar `node_modules`. PROHIBIDO `npm ci`** — uno previo destruyo
   `node_modules/.bin` devolviendo exit code 0. Si falta algo: `npm install`.
9. **`useSearchParams` exige su propio `<Suspense>`** o rompe el build del grupo
   de rutas entero. (No aplica aqui: esta pantalla no lo usa.)
10. **Tras mover o crear una ruta, `.next/types` queda rancio** y `tsc --noEmit`
    falla con errores ajenos. Hay que correr `npx next typegen` antes.
11. **Los tests de React Query pueden pasar sin probar nada**: `notifyManager`
    usa un macrotask y `await act(async () => {})` **no** lo vacia (`AGENTS.md`).
    Hay que usar `await findBy*` sobre algo que el componente **no** posee antes
    de aserir, o conducir por hook mockeado (sincrono e inmune).
12. **El `combine` de `useQueries` debe ir memorizado** con `useCallback` o el
    memo de `[results, combine]` falla siempre (`use-staff.ts:69-83`).
13. **`employeePaletteIndex` filtra por `isActive` a proposito** y devuelve `-1`
    que hay que normalizar a `0` en el consumidor (`avatar.ts:97-109`; ejemplo en
    `appointment-detail-sheet.tsx:53-60`). Con un negativo, `paletteIndex` cae en
    el ULTIMO color, no en el primero.
14. **No unificar `formatDuration` con `formatDurationTight`** (`dates.ts:32-36`):
    cambiar el espacio en la primera rompe `/staff/[id]` → `ServiceAssignment`
    (`service-assignment.tsx:73`), pantalla cerrada.
15. **`vi.mock("@/hooks/use-staff", ...)` sustituye el MODULO ENTERO**, asi que
    cualquier hook del modulo que no aparezca en la factoria vale `undefined`
    para **todo** componente que monte esa pagina, se use o no en el test.
    `today/page.test.tsx:29-32` exporta hoy solo `useServices` y `useEmployees`,
    y su propio comentario (`:20-27`) explica la trampa porque ya la pisamos.
    **T8 anade `useEmployeesWorkingHours` a esa factoria** (D36) y **T7 tiene que
    sembrar `useEmployees` en la suya** (D34), o los 4 tests "que no se tocan"
    revientan con un `TypeError`. Es rojo inmediato y legible, pero cuesta una
    vuelta si no se sabe de antemano.

### 1.8 · Precedentes: que existe y que no

**NO EXISTE, hay que construirlo:**
- Ninguna tabla ni rejilla con cabecera de columnas en todo el repo (grep de
  `<table|role="table"|columnheader|thead`: **cero**). *(No hace falta aqui: esta
  pantalla no dibuja tabla.)*
- Ningun componente de KPI/metrica compartido. `StatCard` es privado de
  `today/page.tsx:273-293`.
- Ninguna funcion que calcule "cuanto tiempo libre le queda a un empleado".

**EXISTE y se reutiliza TAL CUAL:**
- **`PageShell`** (`page-shell.tsx:71`, 12 rutas, **CERRADO**). Su
  `DesktopHeader` es de **72px** con `px-7` = exactamente `HoyDesktop:74`.
  `contentClassName` **SUSTITUYE** el gap de 18px, no se suma
  (`page-shell.tsx:131-137`, test `page-shell.test.tsx:252`) → `gap-5` da los
  20px del artboard. Props sin usar hoy en `/today`: `titleAdjacent`,
  `titleSize`, `contentClassName`.
- **`src/lib/utils/avatar.ts`**, 17 tests. `employeeSolidColor(colorHex,
  fallbackIndex)` (`:93`) es el resolutor exacto del punto de 8px y de la barra
  de 2px. `employeePaletteIndex(employees, id)` (`:111`) — ver §1.7.13.
- **`statusConfig`** (`status-badge.tsx:11-40`): fuente unica de rotulos y tokens
  de estado, con `longLabel` para escritorio. Ya tiene "Confirmada", "En curso",
  "Pendiente".
- **`useEmployeesWorkingHours(ids)`** (`use-staff.ts:65-95`): `useQueries` con
  `combine` **ya memorizado**, devuelve
  `{ data: Record<employeeId, WorkingHoursResponse[]>, isLoading, isError }`, y
  un empleado cuya peticion falla simplemente no aparece en el mapa. **Cubierto
  por `use-staff.test.tsx`.** Se consume tal cual.
- **La conversion `Date.getDay()` -> `dayOfWeek` YA EXISTE**, junto con la
  lectura del horario: `employee-step.tsx:38-42` (`todayDayOfWeek`, envuelve
  domingo 0 -> 7) y `:44-53` (`isWorkingToday(hours, dayOfWeek)`), con tests en
  `employee-step.test.tsx`. **T3 no la reinventa: la lee y replica su criterio.**
  Ojo a su decision deliberada, documentada en `:44-49`: con los horarios
  `undefined` (peticion en vuelo) devuelve **`true`** — "atenuar antes de saber
  la respuesta real pintaria un parpadeo". `/today` debe responder **igual** a la
  misma incertidumbre, o dos pantallas del mismo producto discreparan.
- `formatDurationTight` (`dates.ts:49`), `formatCurrency`, `capitalizeFirst`
  (`format.ts:39`), `formatTime`/`formatTimeRange` (`dates.ts:12,56`).
- `Card` (`ui/card.tsx:5`), `Button` con `size="action"` = `h-[38px] gap-1.5
  px-[18px] text-sm font-semibold` (`button.tsx:35`).

**EXISTE pero NO sirve:**
- **`nextFreeSlot`** (`calendar.ts:370-454`): acotado a 08:00-21:00
  (`GRID_START_HOUR`/`GRID_END_HOUR`, `:6-7`), devuelve `top`/`height` en pixeles
  de rejilla y calcula slots de 30 min. **Es un modelo a copiar, no una funcion a
  llamar.**
- **`EmployeeColumnHeader`** (`employee-column-header.tsx:39`): su docblock
  `:28-38` dice "SOLO ESCRITORIO" y esta acoplado a `EmployeeColumn`, una
  estructura de rejilla de calendario. El artboard de "Ahora mismo" no dibuja
  avatar de iniciales sino un punto de 8px. **No reutilizar; si reutilizar sus
  utilidades de color.**

### 1.9 · Tokens: que hay y que falta

| Valor del artboard | Token | Estado |
|---|---|---|
| `#FAEFD6` (fondo KPI pendiente) | `--color-status-pending-bg` (`globals.css:13`) | ✅ existe |
| `#8A5B12` (texto KPI pendiente) | `--color-status-pending-text` (`:14`) | ✅ existe |
| `#E8D3A6` (borde KPI pendiente y tarjeta reservas) | `--warning-border` (`:162`) → `--color-warning-border` (`:80`) | ✅ existe y mapeado |
| `#FFFCF5` (fondo tarjeta reservas) | `--warning-soft` (`:168`) → `--color-warning-soft` (`:86`) | ✅ existe y mapeado |
| `#FAEFE9` (fondo "Ahora mismo") | `--destructive-soft` (`:163`) → `--color-destructive-soft` (`:81`) | ⚠️ existe con **nombre enganoso** (ver §2.D14) |
| `#DCC9BB` (borde fila en curso, `HoyDesktop:130`) | `--border-dashed-strong` (`:173`) → `--color-border-dashed-strong` (`:89`) | ✅ existe y mapeado — pero **D13 decide NO pintarlo**. Aqui solo por trazabilidad |
| **`#EBD3C8`** (borde "Ahora mismo") | — | ❌ **NO EXISTE. Hay que anadirlo** |
| `#E2E9EE` / `#3A5A70` (badge en curso) | via `statusConfig` | ✅ ya resuelto |
| `#E4EDE1` / `#3F6B4F` (badge confirmada) | via `statusConfig` | ✅ ya resuelto |
| `#D8C9B8` (borde del badge "Libre Xh Ymin", `Main:93` / `HoyDesktop:210`) | `--border-dashed` (`:170`) → `--color-border-dashed` (`:88`) | ✅ **existe y mapeado** — no escribir el hex a pelo |
| `#F0EAE3` (fondo del **avatar** de quien hoy no trabaja) | `--avatar-muted` (`:174`) → `--color-avatar-muted` (`:90`) | ✅ existe y mapeado — pero **el panel de "Ahora mismo" no lleva avatar sino un punto de 8px**, y el canvas atenua esas filas con `opacity`, no recoloreando. Ver T5 Paso 3b |
| **`#904226`** (rotulo "AHORA MISMO" y hora, movil, `Main:69-70`) | — | ❌ **NO EXISTE.** `--primary-pressed` es `#8f3f24`, **distinto**. Hay que anadirlo (D14) |

**En Tailwind v4 un token declarado solo en `:root` y NO mapeado en `@theme
inline` se descarta EN SILENCIO** y la utilidad no existe. Hay que declararlo en
los dos sitios.

### 1.10 · Linea base

Medida sobre el arbol quieto, antes de empezar:

```
tsc --noEmit  → 0 errores
eslint .      → 0 errores, 9 avisos
vitest run    → 916 tests en 86 ficheros
npm run build → OK
```

---

## 2 · Decisiones

### El fallo de la fecha

**D1 — El dia se traduce a `startDate`/`endDate` en la capa de API, no en las
pantallas.** `appointmentsApi.list` (`lib/api/appointments.ts:19-21`) convierte
`date` en el par de instantes y **no manda `date`**. Tres motivos:
(a) la `queryKey` sigue conteniendo `date`, asi que la semantica de cache y el
`differsOnlyByDate` de `use-appointments.ts:15-31` siguen funcionando sin tocarse;
(b) **repara `/calendar` sin tocar `/calendar`**, que es carril cerrado; (c) las
pantallas siguen hablando de "un dia", que es su concepto.

**D2 — La conversion usa la zona LOCAL del dispositivo, no una zona fija.**
`new Date("2026-08-30T00:00:00")` da la medianoche local y `.toISOString()` el
instante UTC correcto, con las reglas de horario de verano aplicadas por el motor.
Es la misma suposicion que ya hace toda la pantalla (`format(new Date(), ...)` en
`today/page.tsx:23,78,83,88`) y que hace `/calendar`. `TIMEZONE` de `dates.ts:5`
esta exportado pero no se usa para convertir en ningun sitio; **no se introduce
aqui una segunda fuente de verdad de zona horaria**. Anotado como deuda para
cuando haya salones fuera de la peninsula.

**D3 — `endDate` es la medianoche del dia SIGUIENTE.** El backend filtra
`startTime >= :startDate AND startTime < :endDate` (§1.6.2), media abierta, asi
que el dia siguiente a las 00:00 es exactamente el corte. Un `23:59:59` dejaria
fuera una cita que empiece en ese ultimo segundo.

**D4 — `use-appointments.test.tsx:287` se queda EXACTAMENTE COMO ESTA; solo se
documenta.** Es la consecuencia directa de D1 y **la trampa mas facil de este
bloque**. Ese fichero **mockea el modulo de API entero**
(`use-appointments.test.tsx:16-20`: `vi.mock("@/lib/api/appointments", ...)`),
asi que con la traduccion viviendo DENTRO de `appointmentsApi.list` **el test no
la ejecuta nunca**: el hook sigue pasando `{date, page, size}` al mock, y eso es
justo lo CORRECTO — `date` es el concepto de pantalla (D1). Ademas el resto del
fichero **depende** de recibirlo: `:277-283` hace `params.date === TODAY ? ... :
...` para simular el prestamo entre dias.

Lo unico que se hace ahi es **anadir un comentario** que diga que `date` es el
concepto de pantalla y que la traduccion se prueba en `appointments.test.ts`.
**Quien intente reescribir ese test acabara moviendo la traduccion al hook, que
es lo que D1 prohibe, y rompera `differsOnlyByDate` y otros cinco tests.**

### Que se pinta

**D5 — Se BORRA la tarjeta "Proxima cita"** (`today/page.tsx:188-196`). No la
dibuja ninguno de los dos artboards: el diseno la sustituyo por "Ahora mismo".
Borrarla elimina de paso el fallo de comparacion UTC-vs-local de §1.4, sin
necesidad de arreglarlo.

**D6 — Ni "huecos libres" ni "clientes atendidos" entran en esta pantalla.**
Ninguno de los dos artboards los dibuja (§1.1, §1.2). Eso hace irrelevantes para
este bloque los dos problemas mas caros del backend: `totalVisits` sin escribir
(§1.6.10) y el fallo de huecos pasados de `AvailabilityService` (§1.6.11). Los
dos quedan anotados como deuda, ninguno se aborda aqui.

**D7 — `total` EXCLUYE `CANCELLED` y `NO_SHOW`.** Hoy es `sorted.length` a secas
(§1.4). Una cita cancelada no es "una cita de hoy" en ningun sentido util, y el
propio backend lo tiene decidido asi para su contador mensual
(`EXCLUDED_STATUSES`, `AppointmentPersistenceAdapter.java:21-22`). Se sigue ese
precedente.

**D8 — "Facturacion prevista" suma `servicePrice` de las citas de hoy excluyendo
`CANCELLED` y `NO_SHOW`**, misma regla que D7. El dato viaja en la cita
(§1.6.3): cero peticiones extra.

**D32 — CRITERIO UNICO para las desviaciones que aparecen una sola vez.** Se fija
aqui porque §1.3.I y §1.3.K son el mismo tipo de evidencia y estaban resueltas
con criterios opuestos:

> Un valor que **se desvia de sus HERMANOS dentro de su propio artboard** (una
> fila de cinco, un KPI de cuatro) se lee como **INTENCIONAL** — salvo que el
> otro artboard dibuje **ese mismo elemento** sin la desviacion; entonces gana la
> forma con **mas instancias** contando los dos artboards. **Las instancias que
> se cuentan son las de la FORMA sobre todos los hermanos de los dos artboards**
> (p. ej. siete filas de cita con un borde contra una con otro), no las del
> elemento concreto — contar el elemento da siempre 1-1 y no desempata nada.

Dos precisiones, porque sin ellas la regla no discrimina:

1. **"Mismo elemento" es el elemento, no la maquetacion.** La fila de una cita en
   curso es el mismo elemento en los dos anchos aunque §1.3.D diga que se dibuja
   con **otra maquetacion**: eso es como se pinta, no que cosa es.
2. **La regla NO alcanza a los valores que son coherentes con sus hermanos en
   cada artboard y solo difieren ENTRE artboards.** Los paddings y gaps de
   §1.3.L-Q son eso: decisiones por ancho, y **se respetan tal cual**. Sin esta
   precision la regla obligaria a unificarlos, que es justo lo contrario de lo
   que mandan T5 y T7 Paso 4b.

Aplicado, invierte las dos decisiones que habia:

**D9 — La facturacion se pinta "412 €", ENTERA, como dibuja el artboard.**
Resuelve §1.3.I. Con D32 en la mano ni siquiera hay caso: los hermanos de ese KPI
son los otros tres, que **no llevan moneda**, asi que no hay desviacion respecto
a sus hermanos que juzgar — se sigue el artboard y ya. La version anterior de
esta decision daba por contradictorio el canvas porque los **precios unitarios**
llevan dos decimales (`Main:132`, `HoyDesktop:126`), pero un precio de servicio y
un agregado de cabecera no son el mismo elemento. Redondear un total de cabecera es ademas un patron deliberado
habitual, y `formatCurrency(412)` = `"412,00 €"` es mas ancho dentro de un numero
`.display` de 30px repartido en cuatro columnas.

Como `formatCurrency` no sabe redondear, **T0 anade
`formatCurrencyRounded(amount)`** a `src/lib/utils/format.ts`
(`maximumFractionDigits: 0`, `minimumFractionDigits: 0`), al lado de la otra y
con un comentario que diga cuando se usa cada una — mismo patron que el par
`formatDuration`/`formatDurationTight` (§1.7.14). **No se toca `formatCurrency`**:
lo consumen las filas de cita, `/staff` y el asistente. Sigue emitiendo
**U+00A0** (§1.7.4), asi que sus aserciones van con `normalize`/`exact`.

**D10 — Movil pinta 3 KPIs y escritorio 4, cada uno con sus etiquetas y su
tipografia.** Resuelve §1.3.A y §1.3.B siguiendo cada artboard al pie de la
letra. La diferencia se decide por **montaje condicional**, no por clases
(§1.7.7).

**D11 — La fila de cita es UN componente con dos maquetaciones, no dos
componentes.** Resuelve §1.3.D. `AppointmentCard` (`appointment-card.tsx`) se
**reescribe en su sitio** con las dos formas y recibe por fin su fichero de test
(§1.4). No se crea un componente nuevo al lado: dejaria dos piezas casi iguales
y `AppointmentCard` solo lo consumen `/today` y `src/app/dev/preview/page.tsx`.

**D12 — El rotulo de la lista y la columna de hora siguen cada artboard.**
Resuelve §1.3.C (`font-medium` movil / `font-semibold` escritorio) y §1.3.J
(22px sobre 56px / 21px sobre 60px).

**D13 — El borde `#DCC9BB` de la fila "En curso" NO se pinta. La fila lleva
`#E7DCCF` como las demas.** Resuelve §1.3.K bajo D32. Se desvia de sus cuatro
hermanas en `HoyDesktop`, asi que entra en la regla; y **el artboard movil dibuja
ese MISMO elemento — la fila de la cita en curso — sin la desviacion**
(`Main:138`, `#E7DCCF`). Contando instancias, siete filas normales contra una. Eso es exactamente la
contradiccion que D32 describe: hay dos dibujos de lo mismo y el minoritario cae.

Consecuencia practica: `AppointmentCard` **no** necesita
`--color-border-dashed-strong`, y la fila en curso deja de tener una rama de
estilo por ancho. La senal de "esta pasando ahora" ya la da el badge.

**D14 — El rosa de "Ahora mismo" se unifica con los valores de ESCRITORIO
(`#FAEFE9` fondo, `#EBD3C8` borde).** Resuelve §1.3.H. Motivo: los del movil
salen de `{{accent}}`, una prop del canvas que **no existe en el producto** — el
producto no tiene acento configurable. Se toman los literales.
Ademas: `#FAEFE9` ya existe como `--destructive-soft` (§1.9), nombre heredado de
`ReservaError.dc.html` que aqui seria enganoso. **Se anaden TRES tokens con
nombre honesto** — `--surface-now` (mismo valor `#faefe9`),
`--surface-now-border` (`#ebd3c8`, nuevo) y **`--surface-now-text` (`#904226`,
nuevo)** — declarados en `:root` **y** mapeados en `@theme inline`. No se toca
`--destructive-soft`: lo consume otra pantalla.

El tercero es el que faltaba: el rotulo "AHORA MISMO" y la hora del movil van en
`accentDark = #904226` (§1.1), que **no** es `--primary-pressed` (`#8f3f24`) ni
ningun otro token existente (§1.9). Sin el, T5 escribiria un hex a pelo o —
peor — un token parecido pero distinto. **Solo lo usa el movil**: en escritorio
el rotulo vive fuera de la tarjeta a `#7A6A5F` = `--color-muted-foreground`
(`HoyDesktop:188`).

**D15 — El rotulo "Ahora mismo" y la hora siguen cada artboard.** Resuelve
§1.3.E: en movil el rotulo va dentro de la tarjeta, en mayusculas de 11px, con la
hora actual al lado; en escritorio va fuera, a 13px/600, sin hora — porque en
escritorio la hora ya sale en el subtitulo de la topbar (`HoyDesktop:77`) y
repetirla seria ruido.

**D16 — La segunda linea del ocupado lleva el servicio solo en movil.** Resuelve
§1.3.F, siguiendo cada artboard. En escritorio el servicio ya sale en la fila de
la cita, en la columna de al lado.

**D17 — La tarjeta de reservas online solo se monta en escritorio.** Resuelve
§1.3.G. El artboard movil no la dibuja, y lo no dibujado no se inventa.

### "Ahora mismo"

**D18 — Los empleados que HOY NO TRABAJAN SI aparecen, con el texto que el canvas
ya aprobo: "Hoy no trabaja".** Pintarles "Libre 10h" seria afirmar algo falso, y
esa parte no cambia. Lo que cambia es la salida: **no hay que elegir entre mentir
y omitir, porque el canvas ya dibujo este estado** — `NuevaCitaPaso1.dc.html:89,92`
y `NuevaCitaDesktopPaso1.dc.html:116` lo pintan con ese texto, el repo ya lo
pinta en `employee-step.tsx:87-91`, y hasta tiene token propio
(`--avatar-muted`, `globals.css:174`, cuyo comentario dice literalmente "fondo
del avatar del empleado que hoy no trabaja"). Reusar un texto aprobado no es
inventar.

**Cuidado con CUAL de los dos textos**: el canvas aprobo **dos**, y son
deliberadamente distintos — `NuevaCitaDesktopPaso1.dc.html:116` dice
**"Hoy no trabaja"**, y `NuevaCitaPaso1.dc.html:92` dice **"Estilista · hoy no
trabaja"**, en minuscula y precedido del cargo. `employee-step.tsx:89-90` lo
documenta: "dos textos medidos, no una variacion de mayusculas del mismo".
Aqui se usa **"Hoy no trabaja" en los dos anchos**, y el motivo es que la fila
del panel **no tiene cargo delante que anteponer**: la del asistente empieza por
el cargo del empleado y por eso continua en minuscula. Sin ese prefijo, la
minuscula quedaria huerfana.

La fila "off" es la fila normal **sin badge de tiempo**: punto atenuado, nombre y
"Hoy no trabaja" como segunda linea. Van **las ultimas** (D37).

**Esto sigue siendo lo que obliga a pedir los horarios** (§1.6.5): sin ellos, la
ausencia de cita no distingue "libre" de "librando".

**Empate con el precedente:** con los horarios todavia sin resolver
(`undefined`), el empleado **NO es `off`** — no se le dice "Hoy no trabaja"
mientras no se sepa. Es lo que decide `isWorkingToday`
(`employee-step.tsx:44-53`) y su razon esta escrita: atenuar antes de saber la
respuesta pinta un parpadeo (§1.8). Que fila produce entonces lo cierra D19.

**D19 — "Libre Xh Ymin" se mide desde AHORA hasta el inicio de su proxima cita de
hoy; si no tiene mas, hasta su hora de cierre.**

El hueco se acota **tambien por la hora de cierre**: `min(proxima cita,
closeTime)`. Nada impide que un salon tenga una cita empezando despues del
cierre, y "Libre 4h 10min" cuando quedan dos horas de jornada seria falso.

**Fuera de su jornada — todavia sin abrir o ya cerrada — el empleado NO produce
fila.** Ojo: esto ya no es "se omite igual que en D18", porque D18 ya no omite a
nadie. Es otra cosa y su consecuencia esta en D37: **cuando NADIE tiene la
jornada abierta, no se monta el panel entero.** "Ahora mismo" describe el salon
mientras esta abierto; a las 20:00 no tiene nada que decir, y decirlo con una
caja vacia o con una lista de los que hoy libraban seria peor que no decirlo.

**HORARIOS SIN RESOLVER — el caso que no es un parpadeo.** Cuando un empleado no
esta en el mapa de `useEmployeesWorkingHours`, la respuesta es esta:

> **No es `off`, y solo produce fila si esa fila se puede sostener con las CITAS,
> que es lo unico que si sabemos: `busy` si tiene una cita solapando `now`;
> `free` si tiene proxima cita hoy, con el hueco medido hasta ella. Sin ninguna
> de las dos, no produce fila.** Una fila asi cuenta como jornada abierta a
> efectos de D37.

El motivo de que esto necesite regla propia y no valga "cuenta como que si
trabaja": `useEmployeesWorkingHours` **deja fuera del mapa a todo empleado cuya
peticion falle** (`use-staff.ts:73-74`: `if (hours) byEmployee[...] = hours`).
Asi que `undefined` **no es solo el parpadeo de carga — es tambien un fallo de
red permanente**, y con N peticiones sueltas (D36) eso ocurre de verdad. Sin
`openTime`/`closeTime` no se puede decidir si la jornada esta abierta ni medir el
hueco "hasta su hora de cierre", asi que las dos improvisaciones naturales rompen
algo escrito: darlo por abierto manda un `NaN` a `formatDurationTight`, y darlo
por cerrado **hace desaparecer del panel, en silencio, a quien solo tuvo un fallo
de red**.

**Lo mismo aplica a `isOpen: true` con `closeTime` nulo** (`EmployeeService.java:305`
escribe `null`, §1.6.5): se trata como horario sin resolver, no como jornada de
duracion infinita.

**D20 — Si un empleado libre no tiene proxima cita, se omite la segunda linea.**
El artboard siempre dibuja "Siguiente: HH:mm · Cliente" porque su ejemplo siempre
la tiene. Sin proxima cita no hay nada que decir ahi, y una frase inventada
("Sin mas citas hoy") es texto que el canvas no ha aprobado. Deuda para el canvas.

**D21 — El descanso (`breakStartTime`/`breakEndTime`) NO se descuenta del hueco
libre.** El artboard no dibuja ningun estado de descanso y descontarlo cambiaria
el numero que se pinta sin que nada lo explique en pantalla. Deuda anotada.

### Reservas online sin confirmar

**D22 — El contador y la lista salen de la MISMA consulta de citas de hoy, no de
una peticion nueva.** Filtro: `status === "PENDING" && source === "ONLINE"`.
Contexto de §1.6.6-8: `source` no es filtrable en el servidor, y filtrar en
cliente sobre una pagina da un numero incorrecto — **pero eso solo pasa si el
conjunto esta paginado**, y aqui el conjunto es "un solo dia" y se pide entero
(D23). Asi que el numero es correcto y cuesta **cero peticiones**.
`?status=PENDING` a secas no valdria: `PENDING` es el estado inicial de todas las
citas, tambien las de mostrador (§1.6.7).

**D23 — La consulta de hoy se queda en `size=100`.** Es el valor actual
(`use-appointments.ts:82`) y basta de sobra para un dia de un salon; con D1 la
peticion ya devuelve solo ese dia, asi que el limite deja de ser el problema que
era. Si algun dia un salon superara las 100 citas en un dia, el sintoma seria
visible y esta anotado como deuda.

**D24 — El CTA "Revisar y confirmar" navega a `/calendar`.** Ningun artboard
dibuja su destino. `/calendar` es donde se confirman las citas hoy (panel de
detalle con "Confirmar", bloque 4). No se construye aqui una pantalla de
confirmacion que el canvas no ha dibujado, ni se confirma en lote (no existe el
endpoint, §1.6.9). Deuda para el canvas.

### Chasis

**D25 — `PageShell` recibe un prop nuevo `mobileTitle?: string`, con default =
`title`.** El artboard movil pinta el **nombre del salon** en la cabecera de 56px
(`Main:24`) y el saludo en el CUERPO (`Main:35`), mientras que en escritorio el
saludo es el `h1` de la topbar (`HoyDesktop:76`). Hoy `PageShell` usa el mismo
`title` en las dos cabeceras. Un prop opcional con default = comportamiento
actual deja **intactas las otras 11 rutas** — mismo patron que el
`completedTone` de `WizardStepper` en el bloque anterior. `/today` le pasa el
nombre del salon (`useSalon()`).

**D26 — `/today` NO entra en `FILL_ROUTES`.** Sigue siendo una pagina que hace
scroll con el documento; ninguno de los dos artboards dibuja una region de
scroll interna. La invariante de `layout.tsx:15-29` exige tocar dos sitios a la
vez, y aqui no hay que tocar ninguno.

**D27 — El `lg:hidden` de `today/page.tsx:125` se sustituye por montaje
condicional** con el `useMediaQuery` propio de la pantalla (§1.7.7). Hoy deja dos
botones `aria-label="Actualizar"` en el DOM a la vez.

**D28 — El contenido de escritorio pasa `contentClassName="gap-5"`** para los
20px de `HoyDesktop:90`, sabiendo que **sustituye** el `gap-[18px]` por defecto
(§1.8). El `padding` exterior lo pone `PageShell` y **no se toca**: si no
coincidiera con los `24px 28px` del artboard, cambiarlo afectaria a las 12 rutas
que lo comparten. Se mide y, si difiere, **se anota como deuda, no se cambia**.

**D29 — `StatCard` se promueve a un componente propio y tokenizado.** Pasa de
privado en `today/page.tsx:273-293` con `border-yellow-300 bg-yellow-50` a
`src/components/today/kpi-card.tsx` con los tokens de §1.9 y las dos variantes
(con icono / sin icono) que piden los artboards.

**D30 — La derivacion vive en un modulo PURO, sin JSX.** `src/components/today/
today-facts.ts`, siguiendo el precedente de `appointment-detail-facts.ts` y
`wizard-summary.ts`: los KPIs, las filas de "Ahora mismo" y el listado de
reservas online sin confirmar se calculan ahi y se prueban sin montar nada. Es lo
que permite cubrir la logica de verdad sin pelearse con jsdom.

**D31 — `formatShortName`/`getInitials` (`today/page.tsx:260-271`) se quedan como
estan.** Duplican parcialmente `initials()` pero con reglas distintas
(primer + ultimo nombre), y unificarlas cambiaria `UserBadge` sin que ningun
artboard lo pida. YAGNI. Deuda anotada.

**D33 — `now` se congela AL MONTAR y el boton "Actualizar" lo vuelve a sembrar.**
Toda la pantalla usa **un solo** `now`, creado en `today/page.tsx` con
`const [now, setNow] = useState(() => new Date())` — el precedente exacto de
`calendar/page.tsx:69`, cuyo docblock explica por que: un `new Date()` leido en
cada render cambia con cualquier interaccion y lo que se pulsa deja de ser lo que
se vio. De ahi baja `now` a `getNowRows` (T3, que lo recibe inyectado) y al rotulo de la
hora. **`getTodayStats` NO lo recibe**: los KPIs son conteos por estado sobre las
citas del dia y no dependen del reloj — el contrato de T3 manda.

Un `now` congelado y nada mas seria una **mentira lenta**: una pantalla abierta
toda la manana seguiria diciendo "10:10", "Libre 2h 20min" y "hasta las 11:30" a
la una. La salida no es inventar un temporizador que ningun artboard dibuja: **es
el boton de refrescar, que los dos artboards SI dibujan** (`Main:37-40`,
`HoyDesktop:79`). Su `onClick` pasa a hacer las dos cosas — `refetch()` y
`setNow(new Date())` —, que es lo que el usuario ya cree que hace.

Deuda anotada: al pasar la medianoche con la pestana abierta, ni `now` ni el
`today` de `:23` avanzan solos. Hoy tampoco lo hacen.

**D34 — `AppointmentCard` resuelve el color del empleado POR DENTRO. No recibe
ningun prop nuevo obligatorio.** La barra de 2px necesita `colorHex`, y la cita
**no lo lleva** (`types/appointment.ts:12-33` tiene `employeeId`/`employeeName` y
nada mas). El precedente es `appointment-detail-sheet.tsx:51-60`: llama a
`useEmployees()` dentro y resuelve con `employeePaletteIndex` +
`employeeSolidColor`. Se hace igual, y el ancho lo decide con su propio
`useMediaQuery(DESKTOP_QUERY)`, como ya hace `service-step.tsx:24,68`.

El motivo es de contrato, no de gusto: `AppointmentCard` tiene **dos**
consumidores, y el segundo es `src/app/dev/preview/page.tsx:227`, que la invoca
con `appointment` y nada mas. **Un prop nuevo obligatorio rompe `tsc` en un
fichero que no es de nadie en este bloque.** React Query comparte la peticion de
empleados por clave, asi que N tarjetas no son N peticiones.

**D35 — El `max-w-[1084px]` de `PageShell` NO se toca. Es deuda anotada, y la
comparacion visual tiene que saberlo de antemano.** A 1440px el artboard deja
1440 − 248 (barra lateral) − 56 (`px-7`) = **1136px** de contenido;
`page-shell.tsx:131` entrega **1084px** (sin `mx-auto`), 52px menos. No es un
fallo de esta pantalla: es el ancho que comparten las **12 rutas** cerradas en el
bloque 2, y moverlo aqui las mueve todas.

Se anota como deuda **y se escribe en la cabecera de la spec visual (T9)** para
que la diferencia esperada no se reporte como hallazgo nuevo en T10.

**D36 — El coste pasa de 2 a 3 + N peticiones y se acepta, con su deuda.** Hoy
`/today` hace **2** (citas + servicios). Con D18 hacen falta los horarios, y no
hay endpoint bulk (§1.6.5): se suman `useEmployees()` (**1**) y
`useEmployeesWorkingHours(ids)` (**N**, una por empleado). Total **2 + 1 + N**;
para un salon de 8, **11 peticiones**. Esa es la cifra, y es la que usan T8 y
T11: si en algun sitio se lee otra, esta es la buena. Se acepta porque no
hay alternativa sin tocar el backend, que este bloque no toca (§3), y porque
`useEmployeesWorkingHours` ya viene resuelto y memorizado (§1.8).

Deuda doble: (a) falta un endpoint de horarios en lote; (b) **`useEmployees()`
no manda `size`** (`use-staff.ts:11-19`), asi que Spring devuelve **20** por
defecto (§1.6.2) — un salon con mas de 20 empleados veria el panel truncado en
silencio. No se arregla aqui: cambiar esa consulta afecta a las pantallas que ya
la usan.

**D37 — Contrato de `getNowRows`: a quien excluye, que es "ocupado" y en que
orden sale.** Sin esto cada tarea improvisaria la suya.

- **Excluye `CANCELLED` y `NO_SHOW`**, igual que D7 y D8. Una cita cancelada no
  ocupa a nadie: dar "En curso" a quien tiene una cancelada solapando `now` es
  justo la mentira que D18 evita.
- **"Ocupado" = solape con el reloj** (`startTime <= now < endTime`), **no**
  `status === "IN_PROGRESS"`. Motivo: `IN_PROGRESS` solo se pone **a mano**
  (`AppointmentStatus.java:23-25`, `PUT /status`), asi que un salon que no lo use
  — la mayoria — tendria el panel permanentemente sin nadie "En curso", y el
  panel no serviria para nada.
- **Consecuencia asumida y anotada:** las dos "En curso" de la pantalla tienen
  fuentes distintas. La del **panel** habla de la PERSONA segun el reloj; la de
  la **fila de cita** habla del ESTADO REGISTRADO de la cita (`statusConfig`).
  Las dos son ciertas y pueden no coincidir. Deuda para el canvas: decidir si la
  fila deberia derivar tambien del reloj.
- **Orden:** ocupados primero, luego libres **por hueco DESCENDENTE** (mas hueco
  primero), y los `off` al final. **Descendente, no ascendente**: los dos
  artboards dibujan "Libre 2h 20min" **antes** que "Libre 1h 20min"
  (`Main:88,101`; `HoyDesktop:205,218`), y ademas es la lectura util del panel —
  quien tiene mas hueco es a quien se le puede encajar algo. No depende del orden
  de llegada de las peticiones. **Un test que solo compruebe "estabilidad" no
  prueba esto**: hay que afirmar el orden concreto, con dos libres de huecos
  distintos.
- **Cuando no se monta el panel:** si **ningun** empleado tiene la jornada
  abierta ahora mismo. Pasa en dos casos — el salon no tiene empleados activos, y
  **el mas frecuente con diferencia: esta cerrado** (antes de abrir o despues de
  cerrar), donde por D19 nadie produce fila. En los dos no se monta la tarjeta
  **ni su rotulo**: en escritorio el rotulo va fuera (`HoyDesktop:188`) y
  quedaria huerfano sobre una caja vacia.
  **Las filas `off` no sostienen el panel por si solas.** Un salon cerrado a las
  20:00 no debe pintar "Ahora mismo" con los tres que hoy libraban y nadie mas:
  seria una lista de ausencias presentada como el estado del salon. La condicion
  es "hay al menos una jornada abierta", no "hay al menos una fila".
  **Traducido a lo que T8 tiene de verdad en la mano** — que es el array y nada
  mas —: **existe al menos una fila `busy` o `free`.** Las dos formas son
  equivalentes gracias a D19 (fuera de jornada no se produce fila), y esta
  segunda es la que se implementa: nadie exporta un booleano de "salon abierto".

---

## 3 · Ficheros, con propietario

**Ningun fichero tiene dos propietarios en la misma ola.** Es lo que permite que
las olas paralelas corran sobre el mismo arbol.

### Frontend (`E:\IdeaProjects\rivoo-frontend`)

| Fichero | Accion | Tarea |
|---|---|---|
| `src/app/globals.css` | Modificar (**3** tokens, D14) | T0 |
| `src/lib/utils/format.ts` (+ su test) | Modificar (`formatCurrencyRounded`, D9/D32) | T0 |
| `src/lib/api/appointments.ts` | Modificar (traduccion `date` → `startDate`/`endDate`, D1-D3) | T1 |
| `src/lib/api/appointments.test.ts` | **Crear** (hoy no existe) | T1 |
| `src/types/appointment.ts` | Modificar (documentar `date` como concepto de pantalla) | T1 |
| `src/hooks/use-appointments.test.tsx` | Modificar (**solo un comentario**; el test NO se reescribe, D4) | T1 |
| `src/components/layout/page-shell.tsx` | Modificar (`mobileTitle`, D25) | T2 |
| `src/components/layout/page-shell.test.tsx` | Modificar (default + override) | T2 |
| `src/components/today/today-facts.ts` (+ test) | **Crear** (D30) | T3 |
| `src/components/appointments/wizard/employee-step.tsx` (+ su test) | **Condicional, solo si T3 extrae `todayDayOfWeek`/`isWorkingToday` a un modulo compartido.** Es del bloque 8, cerrado. Si T3 lo toca, **corre tambien `employee-step.test.tsx`** — la regla de "solo tus ficheros de test" (§4) no exime de proteger un fichero que estas modificando — y **dilo en el informe**. Si no lo toca, esta fila no existe | T3 (condicional) |
| `src/components/today/kpi-card.tsx` (+ test) | **Crear** (D29) | T4 |
| `src/components/today/now-panel.tsx` (+ test) | **Crear** (D15-D21) | T5 |
| `src/components/today/pending-online-card.tsx` (+ test) | **Crear** (D17, D22, D24) | T6 |
| `src/components/appointments/appointment-card.tsx` | **Reescribir** (D11-D13) | T7 |
| `src/components/appointments/appointment-card.test.tsx` | **Crear** (hoy no existe) | T7 |
| `src/app/dev/preview/page.tsx` | **Leer, NO modificar** — es el 2o consumidor de `AppointmentCard` y la invoca solo con `appointment` (`:227`). Por eso D34 prohibe props nuevos obligatorios | T7 (solo lectura) |
| `src/app/(app)/today/page.tsx` | **Reescribir** (D5, D10, D27, D28) | T8 |
| `src/app/(app)/today/page.test.tsx` | Modificar (los 4 tests existentes se conservan) | T8 |
| `visual/shell-vs-artboards.spec.ts` | Modificar (cabecera "que mirar" de `/today`) | T9 |
| `E:\IdeaProjects\rivoo\tasks\todo.md` | Modificar (volcado y deudas) | T12 |
| `E:\IdeaProjects\rivoo\tasks\lessons.md` | Modificar (si hay correccion del usuario) | T12 |

### Backend (`E:\IdeaProjects\rivoo`)

**NINGUNO.** Este bloque no toca el backend. Es la consecuencia de D6 y D22.

---

## 4 · Olas y protocolo

```
Ola 0:  T0                              (tokens; nadie mas toca globals.css)
Ola 1:  T1 ‖ T2 ‖ T3                    (API · chasis · modulo puro — disjuntos)
Ola 2:  T4 ‖ T5 ‖ T6 ‖ T7               (los cuatro componentes — dependen de T3)
Ola 3:  T8                              (la pagina — depende de todo lo anterior)
Ola 4:  T9 + puertas globales
Ola 5:  T10 ‖ T11 ‖ T12                 (panel de 3 revisores) → volcado
```

### Protocolo de commit — en TODAS las tareas, sin excepcion

```bash
git add <sus rutas>
git commit -o <sus rutas> -m "..."
```

**Las dos cosas.** `git add` porque `git commit -o` falla sobre ficheros que git
aun no conoce, y casi todas las tareas crean ficheros. `-o` porque commitea solo
esas rutas e ignora el resto del indice: en una ola de cuatro agentes sobre el
mismo arbol, sin el, el primero que commitea se lleva el trabajo a medio escribir
de los otros.

**NUNCA `git add -A`. NUNCA `git commit -m` a secas.** Un fichero tocado y no
nombrado no se commitea nunca, y las puertas siguen verdes sobre un arbol sucio.

Mensajes en ingles, con el trailer:
```
Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

### Reglas de ejecucion

1. **Cada despacho es un agente NUEVO. El revisor nunca es el implementador.**
2. **El revisor se lanza al terminar el BLOQUE ENTERO, no por tarea.** Prohibido
   el ciclo spec-review + quality-review despues de cada tarea.
3. **En una ola paralela, cada implementador ejecuta SOLO sus ficheros de test.**
   Nada de `vitest run` completo, `tsc`, `eslint` ni `npm run build`: con otros
   agentes escribiendo a la vez, esas puertas dan rojos falsos. **Las puertas
   globales las pasa el orquestador al final, sobre el arbol quieto.**
4. **Un agente que MUTA ficheros corre SOLO.** La prueba de mutacion es
   obligatoria (ver abajo), asi que se hace **dentro** de la tarea del propio
   implementador sobre sus propios ficheros, nunca como agente aparte en paralelo.
5. **Prueba de mutacion obligatoria en cada tarea**: para cada test nuevo, romper
   a proposito la linea de produccion que deberia protegerlo, ejecutar, comprobar
   que el test CAE, y revertir inmediatamente. Pegar la evidencia en el informe.
   Un test verde que sigue verde con el codigo roto no prueba nada.
6. **No cambiar de rama.** No tocar `node_modules`. No ejecutar `npm ci`.

### Puertas globales (las pasa el orquestador, entre olas y al final)

```bash
npx next typegen && npx tsc --noEmit    # esperado: 0 errores
npx eslint .                            # esperado: 0 errores (avisos <= 9)
npx vitest run                          # esperado: > 916 tests, todos verdes
npm run build                           # esperado: OK
```

---

## 5 · Tareas

### T0 · Tokens y formateador

**Ficheros:** los que §3 asigna a esta tarea. Es D14 y D9/D32.

- [ ] **Paso 1: leer §1.9 y verificar el estado real de los tokens.**
      `grep -n "surface-now\|ebd3c8\|faefe9" src/app/globals.css`.
      Esperado: `#faefe9` aparece como `--destructive-soft`; `#ebd3c8` no aparece.

- [ ] **Paso 2: anadir los TRES tokens en `:root`.**
      Junto a los demas tokens de superficie, con el comentario de procedencia al
      estilo de los vecinos (`globals.css:162-173`):

```css
  --surface-now: #faefe9; /* HoyDesktop.dc.html:190 — fondo del panel "Ahora mismo". Mismo valor que --destructive-soft, que viene de ReservaError y aqui seria un nombre enganoso */
  --surface-now-border: #ebd3c8; /* HoyDesktop.dc.html:190,203 — borde y separadores del panel "Ahora mismo" */
  --surface-now-text: #904226; /* Main.dc.html:69-70 — rotulo "AHORA MISMO" y hora, SOLO movil. Es accentDark = mix(#B4522F, #000, .2). NO es --primary-pressed (#8f3f24) */
```

- [ ] **Paso 3: mapearlos en `@theme inline`.** Sin esto Tailwind v4 los descarta
      **en silencio** y las utilidades no existen (§1.9):

```css
  --color-surface-now: var(--surface-now);
  --color-surface-now-border: var(--surface-now-border);
  --color-surface-now-text: var(--surface-now-text);
```

- [ ] **Paso 4: NO tocar `--destructive-soft`.** Lo consume otra pantalla.
      Tampoco `--border-dashed`, `--border-dashed-strong` ni `--avatar-muted`:
      **ya existen y ya estan mapeados** (§1.9), solo hay que consumirlos.

- [ ] **Paso 4b: anadir `formatCurrencyRounded` a `src/lib/utils/format.ts`**
      (D9/D32), junto a `formatCurrency` y con un comentario que diga cuando se
      usa cada una — el par existe por el mismo motivo que
      `formatDuration`/`formatDurationTight` (§1.7.14):

```ts
// El KPI de facturacion dibuja "412 €" ENTERO (HoyDesktop.dc.html:107): es un
// agregado de cabecera, no un precio unitario. Los precios unitarios siguen
// usando `formatCurrency` ("35,00 €"). Ver D32 en el plan de la pantalla Hoy.
export function formatCurrencyRounded(amount: number, currency: string = "EUR"): string
```

      Implementarla con `maximumFractionDigits: 0` **y** `minimumFractionDigits: 0`,
      reutilizando la validacion de `VALID_CURRENCY_CODE` que ya hay en el
      fichero. **No tocar `formatCurrency`.** Tests en el fichero de test de
      `format.ts`: entero, con decimales (`412.4` → `"412 €"`), cero, y que
      `formatCurrency` **sigue** dando `"412,00 €"`. Las aserciones van con
      `normalize`/`exact` (§1.7.4): sale **U+00A0**.

- [ ] **Paso 5: verificar que las utilidades existen.** Escribir un fichero
      temporal con `bg-surface-now border-surface-now-border`, correr
      `npm run build`, comprobar que las clases aparecen en el CSS generado, y
      **borrar el fichero temporal**. (Alternativa aceptable: comprobar el patron
      contra un token ya mapeado que se sepa que funciona, p. ej.
      `--color-warning-soft`.)
      **`npm run build` esta permitido AQUI y solo aqui**: T0 corre sola en la
      ola 0 (§4). La regla que lo prohibe a los implementadores existe para las
      olas en paralelo, donde el build de uno lee el arbol a medio escribir de
      otro. Si prefieres no arrancarlo, usa la alternativa.

- [ ] **Paso 6: commit.**

---

### T1 · El dia viaja de verdad al servidor

**Ficheros:** los que §3 asigna a esta tarea. Es el arreglo de §1.5 segun D1-D4.

- [ ] **Paso 1: leer §1.5 entero y `lib/api/appointments.ts:14-21`.**

- [ ] **Paso 2: escribir el test que falla** en `src/lib/api/appointments.test.ts`
      (fichero nuevo). Mockear `apiFetch` y afirmar sobre la URL construida:
      - con `{ date: "2026-08-30", page: 0, size: 100 }` la URL lleva
        `startDate` y `endDate`, y **NO lleva `date=`**;
      - `endDate` es la medianoche del **dia siguiente** (D3);
      - los dos instantes son los que corresponden a la medianoche **local**
        (D2) — el test debe fijar la zona o construir el esperado con la misma
        aritmetica, no con un literal UTC quemado, o fallara en otra maquina;
      - `employeeId`, `status`, `page` y `size` siguen pasando intactos;
      - un `date` ausente no inventa rango.

- [ ] **Paso 3: ejecutar y ver que falla.**
      `npx vitest run src/lib/api/appointments.test.ts` → FALLA.

- [ ] **Paso 4: implementar la traduccion en `appointmentsApi.list`.** El resto
      de metodos del objeto no se tocan. Documentar en un comentario **por que**
      la traduccion vive aqui y no en la pantalla (D1) y **por que** el corte es
      la medianoche siguiente (D3), citando `AppointmentJpaRepository.java:57-70`.

- [ ] **Paso 5: documentar `date` en `types/appointment.ts:106-108`** como el
      concepto de PANTALLA que la capa de API traduce, y que `startDate`/`endDate`
      son lo que entiende el servidor. Sin esto, el proximo que lea el tipo
      volvera a creer que `date` es un parametro real.

- [ ] **Paso 6: NO reescribir `use-appointments.test.tsx`. Solo documentarlo**
      (D4). Ese fichero **mockea el modulo de API entero** (`:16-20`), asi que la
      traduccion que acabas de escribir **no se ejecuta ahi**: el hook seguira
      pasando `{date, page, size}` al mock, y eso es lo correcto. Ademas `:277-283`
      **depende** de recibir `date` para simular el prestamo entre dias.
      Anade solo un comentario sobre la asercion de `:287` diciendo que `date` es
      el concepto de PANTALLA (D1) y que la traduccion se prueba en
      `src/lib/api/appointments.test.ts`.

      > **Si te ves moviendo la traduccion al hook para que un test de este
      > fichero la vea, PARA.** Eso es lo que D1 prohibe y rompe
      > `differsOnlyByDate` y otros cinco tests. Es la trampa de esta tarea.

- [ ] **Paso 7: ejecutar solo tus dos ficheros de test.** Verdes.

- [ ] **Paso 8: prueba de mutacion.** Quitar la traduccion (volver a mandar
      `date`) → deben caer los tests de **`appointments.test.ts`**. Ahi es donde
      cae, y **solo ahi**: `use-appointments.test.tsx` no puede verlo (Paso 6), y
      esperar que caiga es la senal de que se ha entendido mal la tarea.
      Segunda mutacion: poner `endDate` en el MISMO dia en vez del siguiente
      (D3) → debe caer. Revertir las dos. Pegar la evidencia.

- [ ] **Paso 9: commit.**

---

### T2 · `mobileTitle` en `PageShell`

**Ficheros:** los que §3 asigna a esta tarea. Es D25.

> **`PageShell` lo comparten 12 rutas y esta CERRADO.** El unico cambio admisible
> es un prop **opcional** cuyo default reproduzca exactamente el comportamiento
> de hoy. Si algo obliga a cambiar el comportamiento por defecto, **para y
> escalalo**: no es esta tarea.

- [ ] **Paso 1: escribir los dos tests que faltan** en `page-shell.test.tsx`:
      (a) **sin** `mobileTitle`, la cabecera movil pinta `title` — el default;
      (b) **con** `mobileTitle`, la cabecera movil pinta ese texto y la de
      escritorio sigue pintando `title`.
      Los dos con `mockMatchMedia` local y su `afterEach` (§1.7.5).

- [ ] **Paso 2: ejecutar y ver que (b) falla.**

- [ ] **Paso 3: anadir el prop** `mobileTitle?: string` a
      `PageShellProps`, documentado como los demas (`page-shell.tsx:13-69`), con
      default `title`, y usarlo solo en `MobileHeader`.

- [ ] **Paso 4: ejecutar solo `page-shell.test.tsx`.** Los **26** tests
      existentes siguen verdes, mas los 2 nuevos.

- [ ] **Paso 5: prueba de mutacion.** Cambiar el default a un literal → cae (a).
      Hacer que `DesktopHeader` use `mobileTitle` → cae (b). Revertir las dos.

- [ ] **Paso 6: commit.**

---

### T3 · `today-facts.ts` — toda la derivacion, pura

**Ficheros:** los que §3 asigna a esta tarea. Es D30, y **la tarea mas importante
del bloque**: aqui vive la logica que las cuatro tareas de la ola siguiente solo
pintan.

Cero JSX en este fichero. Precedente: `appointment-detail-facts.ts`,
`wizard-summary.ts`.

- [ ] **Paso 1: definir el contrato.** Exporta, como minimo:

```ts
export interface TodayStats {
  total: number      // D7: excluye CANCELLED y NO_SHOW
  pending: number
  completed: number
  expectedRevenue: number  // D8: suma de servicePrice, misma exclusion
}

export type NowRow =
  | { kind: "busy"; employee: Employee; clientName: string
      serviceName: string; until: string }              // "11:30"
  | { kind: "free"; employee: Employee; freeFor: string // "2h 20min"
      next?: { time: string; clientName: string } }     // D20: opcional
  | { kind: "off"; employee: Employee }                 // D18: "Hoy no trabaja"

export function getTodayStats(appointments: Appointment[]): TodayStats
export function getNowRows(
  appointments: Appointment[], employees: Employee[],
  hoursByEmployee: Record<string, WorkingHoursResponse[]>, now: Date
): NowRow[]
export function getPendingOnline(appointments: Appointment[]): Appointment[]
```

      **`now` se INYECTA, nunca se lee de `new Date()` dentro.** Es lo que hace
      los tests deterministas sin congelar el reloj global — mismo criterio que
      `formatRelativeTime` (`dates.ts:70`). Quien lo produce es la pantalla, una
      sola vez (D33).

      **Lee D37 entero antes de escribir nada**: fija a quien excluye la funcion,
      que cuenta como "ocupado" y en que orden sale el resultado. Los tres eran
      huecos del contrato y los tres tienen ahora una respuesta unica.

- [ ] **Paso 2: escribir los tests ANTES.** Cubrir, como minimo:
      - `getTodayStats`: que `CANCELLED` y `NO_SHOW` **no** cuentan ni en `total`
        ni en `expectedRevenue` (D7, D8); que una lista vacia da ceros.
      - `getNowRows`: empleado con cita en curso → `busy` con `until` correcto;
        empleado sin cita ahora pero con una despues → `free` con el hueco medido
        hasta ese inicio (D19); empleado sin mas citas → `free` hasta su hora de
        cierre y **sin** `next` (D20); **empleado que hoy no trabaja → fila
        `off`** (D18); empleado cuya jornada ya termino → se omite (D19).
      - **Una cita `CANCELLED` solapando `now` NO pone al empleado `busy`**
        (D37), y tampoco puede ser su `next`.
      - **"Ocupado" se decide por el RELOJ, no por `status === "IN_PROGRESS"`**
        (D37): una cita `CONFIRMED` que solapa `now` da `busy`.
      - **El ORDEN concreto**: ocupados → libres **por hueco DESCENDENTE** → `off`
        (D37). Afirmarlo con **dos libres de huecos distintos**: no basta con que
        sea estable — una lista ordenada por llegada tambien es "estable" y
        pasaria. Y **descendente**, que es lo que dibujan los dos artboards; si
        vienes de una version anterior del plan, decia ascendente.
      - **Fuera de jornada** (todavia sin abrir, o ya cerrada) el empleado **no
        produce fila** (D19). Es distinto de `off`, que es "hoy no trabaja".
      - **Horarios sin resolver** (empleado ausente del mapa — peticion en vuelo
        **o caida**, `use-staff.ts:73-74`): la regla esta en D19 y hay que
        afirmar **las tres ramas**, no "que no revienta":
        (a) con cita solapando `now` → `busy`;
        (b) sin cita ahora pero con proxima cita hoy → `free`, con el hueco
        medido hasta ella;
        (c) sin ninguna de las dos → **no produce fila**.
        Y en ningun caso `off`: a quien solo tuvo un fallo de red no se le dice
        "Hoy no trabaja".
      - **`isOpen: true` con `closeTime` nulo** se trata igual que el caso
        anterior (D19), no como jornada infinita.
      - **El hueco libre se acota por `closeTime`**: `min(proxima cita,
        closeTime)` (D19). Prueba una proxima cita **posterior al cierre**.
      - `getPendingOnline`: solo `PENDING` **y** `ONLINE`; que una `PENDING` de
        mostrador (`MANUAL`) **no** entra (§1.6.7); que una `ONLINE` ya
        `CONFIRMED` tampoco.

- [ ] **Paso 3: ejecutar y ver que fallan.**

- [ ] **Paso 4: implementar.** Notas:
      - `dayOfWeek` de `WorkingHoursResponse` es **1=Lunes .. 7=Domingo**
        (§1.6.5); `Date.getDay()` es 0=Domingo. **Esa conversion YA EXISTE**:
        `todayDayOfWeek` en `employee-step.tsx:38-42`, junto a
        `isWorkingToday` (`:44-53`). **Leelas y replica su criterio**; si
        acaban siendo identicas, extraerlas a un modulo compartido es correcto
        (y entonces `employee-step.tsx` pasa a ser tuyo — dilo en el informe).
        Lo que **no** vale es escribir una tercera version que responda distinto.
      - `openTime` **tambien puede ser `null`**, no solo los tres ultimos:
        `EmployeeService.java:305` escribe `null` los dias cerrados. Mira
        `isOpen` **antes** que las horas.
      - Los `openTime`/`closeTime` son `LocalTime` (`"09:00:00"`), no instantes.
      - **No llames a `nextFreeSlot`** (§1.8): no sirve.
      - La duracion se formatea con `formatDurationTight` (§1.7.14).

- [ ] **Paso 5: ejecutar. Verdes.**

- [ ] **Paso 6: prueba de mutacion,** al menos **seis**: invertir la exclusion de
      `CANCELLED` en `getTodayStats`; dejar que una `CANCELLED` ponga a alguien
      `busy` (D37); cambiar `>=`/`>` en el cruce de "ahora"; quitar el filtro
      `ONLINE`; convertir la fila `off` en `free` (D18); **invertir el orden de
      libres y `off`**; y **ordenar los libres ASCENDENTE en vez de descendente**
      (D37) — esta ultima es la que se le escapa a un test que solo compruebe
      "estabilidad", y es justo el fallo que hubo que corregir en el plan.
      Las siete deben tumbar un test. Revertir.

- [ ] **Paso 7: commit.**

---

### T4 · `KpiCard`

**Ficheros:** los que §3 asigna a esta tarea. Es D29. Valores en §1.1 (movil) y
§1.2 (escritorio); tokens en §1.9.

- [ ] **Paso 1:** componente con dos variantes — **con** icono de 14px y label de
      11px (movil) y **sin** icono con label de 12px (escritorio) — y un tono de
      alerta que use `--color-status-pending-bg` / `--color-status-pending-text`
      / `--color-warning-border`. **Nunca `border-yellow-300`** (§1.4).
- [ ] **Paso 2:** cuidado con §1.7.1 y §1.7.2 — todo `text-[Npx]` lleva su
      `leading-*` **detras**.
- [ ] **Paso 3:** tests de las dos variantes y del tono de alerta, aseverando
      **clases**, no solo texto (jsdom no aplica CSS, §1.7.7).
- [ ] **Paso 4:** prueba de mutacion (invertir el tono; quitar el icono).
- [ ] **Paso 5:** commit.

---

### T5 · `NowPanel`

**Ficheros:** los que §3 asigna a esta tarea. Es D15, D18-D21 y D37. **Consume
`getNowRows` de T3 y no recalcula nada.** Valores en §1.1 y §1.2 — y como esta
pieza se pinta en los dos anchos, **compara las dos filas de las tablas valor a
valor** (§1.3 avisa de que su lista no es exhaustiva). Las que ya sabemos que
difieren: `gap` de la tarjeta 10 vs 14 (§1.3.L), badge `2px 8px` vs `3px 9px`
(§1.3.O), separador `#ECD2C9` vs `#EBD3C8` — unificado por D14 (§1.3.R).

- [ ] **Paso 1:** recibe las filas ya derivadas y un booleano de ancho; decide
      por **montaje condicional** (§1.7.7): rotulo dentro + hora en movil, fuera
      + sin hora en escritorio (D15); segunda linea con servicio solo en movil
      (D16).
- [ ] **Paso 2:** el punto de 8px usa `employeeSolidColor(colorHex,
      fallbackIndex)` con el `-1 → 0` normalizado (§1.7.13). **No copies la
      funcion.**
- [ ] **Paso 3:** los dos badges van a mano (no son `statusConfig`): el de "En
      curso" comparte valores con el badge de estado, pero el de "Libre Xh Ymin"
      es una forma propia con borde y sin fondo (§1.1 `:93`). Ese borde es
      `#D8C9B8` y **ya tiene token**: `border-border-dashed` (§1.9). **No lo
      escribas a pelo.** El rotulo y la hora del movil van en
      `text-surface-now-text` (T0, D14) — tampoco a pelo, y **no** en
      `--primary-pressed`, que es otro color.
- [ ] **Paso 3b: la fila `off`** (D18): nombre y "Hoy no trabaja" de segunda
      linea, **sin badge de tiempo**. El texto es el de escritorio y **no se
      reformula** — D18 explica cual de los dos que aprobo el canvas es y por que.
      **El atenuado va con `opacity` sobre la FILA ENTERA**, que es el mecanismo
      real del canvas (`NuevaCitaPaso1.dc.html:88` usa `opacity: 0.55`,
      `NuevaCitaDesktopPaso1.dc.html:112` usa `0.5`), **conservando el color real
      del empleado en el punto**. No recolorees el punto con
      `--color-avatar-muted`: ese token es un fondo de AVATAR (`#F0EAE3`) y un
      punto de 8px de ese color sobre el `#FAEFE9` de la tarjeta (D14)
      **desaparece**. jsdom no puede verlo (§1.7.7); solo lo cazaria T10.
- [ ] **Paso 4:** tests: fila ocupada, fila libre con y sin "Siguiente", **fila
      `off`**, **el orden de D37 con las tres clases mezcladas y dos libres de
      huecos distintos**, y **las dos variantes de ancho** con `mockMatchMedia`
      local. El caso "no se monta el panel" no se prueba aqui sino en T8: quien
      decide no montar la tarjeta **ni su rotulo** es la pagina (D37), porque en
      escritorio el rotulo vive fuera.
- [ ] **Paso 5:** prueba de mutacion. **Al menos una debe demostrar que el rotulo
      y la hora cambian de sitio segun el ancho**, no solo de clase.
- [ ] **Paso 6:** commit.

---

### T6 · `PendingOnlineCard`

**Ficheros:** los que §3 asigna a esta tarea. Es D17, D22, D24. Valores en §1.2
(`:230-234`); tokens `--color-warning-soft` y `--color-warning-border` (§1.9).

- [ ] **Paso 1:** recibe las citas ya filtradas por `getPendingOnline` (T3).
      **No filtra por su cuenta.**
- [ ] **Paso 2:** el cuerpo nombra a las personas y sus horas, como
      `HoyDesktop:232`. Con una sola cita el texto tiene que leerse bien (el
      artboard solo dibuja el caso de dos): decide singular/plural y **pruebalo**.
- [ ] **Paso 3:** el CTA navega a `/calendar` (D24), con un comentario que diga
      que **ningun artboard dibuja su destino**.
- [ ] **Paso 4:** tests: una cita, dos citas, y que el componente **no se pinta**
      con cero.
- [ ] **Paso 5:** prueba de mutacion (romper el plural; romper el destino).
- [ ] **Paso 6:** commit.

---

### T7 · `AppointmentCard` reescrita

**Ficheros:** los que §3 asigna a esta tarea. Es D11, D12, D13. Valores en §1.1
(`:117-136`) y §1.2 (`:116-130`).

- [ ] **Paso 1: leer el componente actual entero** y `src/app/dev/preview/page.tsx`,
      su otro consumidor, que la invoca con **solo** `appointment` (`:227`).

      > **D34 manda aqui: NO anadas ningun prop obligatorio nuevo.** Si anades
      > `employees` o `isDesktop` como obligatorios, `dev/preview/page.tsx` deja
      > de compilar y la puerta `tsc` del orquestador sale roja sobre un fichero
      > que **no es tuyo y no es de nadie en este bloque**. El color del empleado
      > se resuelve **por dentro** con `useEmployees()` — precedente literal en
      > `appointment-detail-sheet.tsx:51-60` — y el ancho con su propio
      > `useMediaQuery(DESKTOP_QUERY)`, como `service-step.tsx:24,68`.
- [ ] **Paso 2:** una sola pieza con las dos maquetaciones de §1.3.D, decididas
      por **montaje condicional**. Recordatorio de las diferencias reales:
      movil lleva icono de tijeras, precio dentro de la linea de servicio y una
      TERCERA linea con empleado + rango; escritorio lleva "servicio · empleado",
      el precio como **columna propia** y ninguna tercera linea.
- [ ] **Paso 3:** duracion con `formatDurationTight` (§1.7.14); precio con
      `formatCurrency` — **el de dos decimales; `formatCurrencyRounded` es solo
      para el KPI** (D9/D32); badge desde `statusConfig` (§1.8); barra de 2px con
      `employeeSolidColor` + `employeePaletteIndex`, normalizando el `-1 → 0`
      (§1.7.13), sobre los empleados de `useEmployees()` (D34).
- [ ] **Paso 4: la fila en curso NO lleva borde especial.** `#E7DCCF` como las
      demas, en los dos anchos (**D13**, invertida por D32: el artboard movil
      dibuja esa misma fila sin el). **No uses `--color-border-dashed-strong`
      aqui.** Cuidado si vienes de una version anterior del plan: decia lo
      contrario.
- [ ] **Paso 4b: las diferencias menores por ancho** que §1.3 lista y es facil dar
      por iguales: padding `12px` vs `12px 14px` y `gap` 12 vs 14 (§1.3.P), `gap`
      de la columna central 5 vs 3 (§1.3.Q), badge `3px 8px` vs `3px 9px`
      (§1.3.N), columna de hora 22px/56 vs 21px/60 (§1.3.J).
- [ ] **Paso 5: crear el fichero de test que nunca tuvo.** Cubrir las dos
      maquetaciones, los tres estados de badge, y las aserciones de precio con
      `normalize`/`exact` (§1.7.4).
- [ ] **Paso 6:** prueba de mutacion, al menos: cambiar `formatDurationTight` por
      `formatDuration`; quitar la tercera linea de movil; mover el precio de
      columna propia a dentro de la linea de servicio en escritorio; quitar la
      normalizacion `-1 → 0` del indice de color (§1.7.13) — esa ultima **debe**
      tumbar un test con un empleado inactivo, o el color cae en el ultimo de la
      paleta sin que nadie se entere.
- [ ] **Paso 7:** commit.

---

### T8 · La pagina

**Ficheros:** los que §3 asigna a esta tarea. Es D5, D10, D26-D28, D33 y D35.
**Monta lo que las olas anteriores construyeron; no reimplementa nada.**

> **La ficha dice "Reescribir", y eso es lo peligroso de esta tarea.** Reescribir
> **no** es empezar de cero: hay piezas que ningun artboard dibuja (porque los
> artboards dibujan el caso feliz) y que **tienen que sobrevivir**. Antes de
> tocar nada, localizalas y anotalas:
> - el `EmptyState` de "No hay citas para hoy" (`today/page.tsx:206-218`);
> - el `LoadingSkeleton` (`:204-205`);
> - el `UnavailableNotice` de `servicesError` (`:145-150`);
> - el `AppointmentDetailSheet` (`:236-240`) y el estado que lo abre;
> - la guarda `hasNoServices` (`:43-44`), la unica protegida por tests.
>
> **Solo la ultima tiene red.** Si pierdes cualquiera de las otras cuatro, todo
> sigue verde. §1.1 y §1.2 **no** traen valores para reconstruirlas: se
> conservan **tal cual estan**.

- [ ] **Paso 1: BORRAR la tarjeta "Proxima cita"** y con ella `nextAppointment`
      (`today/page.tsx:71-76`) (D5). Con eso desaparece el fallo de comparacion
      UTC-vs-local de §1.4 sin arreglarlo. **`now` NO se borra: cambia de dueno**
      — pasa a ser el `useState` congelado de D33, que alimenta a `getNowRows` y
      al rotulo de la hora (**no** a `getTodayStats`, que no lo recibe).
- [ ] **Paso 2: sustituir el `lg:hidden` de `:125`** por montaje condicional
      (D27), con un unico `useMediaQuery("(min-width: 1024px)")` en la pantalla.
- [ ] **Paso 2b: el boton "Actualizar" tambien re-siembra `now`** (D33):
      `refetch()` **y** `setNow(new Date())`. Sin esto "Ahora mismo" miente en
      cuanto la pestana lleva un rato abierta, y el usuario ya cree que ese boton
      hace justo esto.
- [ ] **Paso 3: KPIs** — 3 en movil, 4 en escritorio (D10), montados
      condicionalmente, consumiendo `getTodayStats` (T3) y `KpiCard` (T4).
      Borrar el `stats.confirmed` que se calculaba y se descartaba (§1.4).
      **El KPI de facturacion se formatea con `formatCurrencyRounded`** (T0,
      D9/D32) — "412 €", ENTERO. `getTodayStats` devuelve `expectedRevenue` como
      `number` crudo; formatear aqui con `formatCurrency` daria "412,00 €" y
      **ningun test lo cazaria**, solo T10.
- [ ] **Paso 4: layout de escritorio** — dos columnas
      `minmax(0,1.6fr) minmax(0,1fr)` con `gap-5`, y `contentClassName="gap-5"`
      en `PageShell` (D28). El `padding` **ya esta medido y coincide**:
      `page-shell.tsx:121` es `px-7 py-6` = los `24px 28px` del artboard. Lo que
      **no** coincide es el ancho: `max-w-[1084px]` (`:131`) frente a los 1136px
      del artboard. **No lo toques** — es de las 12 rutas (D35). Ya esta anotado
      como deuda y avisado en la spec visual; no hace falta que lo redescubras.
      Ojo tambien: `contentClassName` llega **tambien** al envoltorio movil
      (`page-shell.tsx:157-160,190`), donde `gap-5` es inerte porque ese
      envoltorio no es flex. El `gap: 16px` del cuerpo movil (`Main:31`) lo pones
      **dentro** del cuerpo, no via `contentClassName`.
- [ ] **Paso 5: montar `NowPanel`** (T5) en los dos anchos y
      **`PendingOnlineCard`** (T6) **solo en escritorio** (D17).
- [ ] **Paso 6: cabecera movil** — pasar `mobileTitle` con el nombre del salon
      (`useSalon()`) y mover el saludo al cuerpo (D25, §1.1 `:24,:35`).
- [ ] **Paso 7:** `useEmployees()` + `useEmployeesWorkingHours(ids)` (§1.8, se
      consume tal cual). El `combine` ya viene memorizado; **no lo reimplementes**
      (§1.7.12). Esto sube la pantalla de 2 a **3 + N** peticiones y **es
      esperado** (D36): no busques una via mas barata ni montes una cache propia.
      La deuda del `size` sin fijar de `useEmployees()` ya esta anotada; **no la
      arregles aqui**, afecta a otras pantallas.
- [ ] **Paso 8: conservar los 4 tests existentes.** La guarda de catalogo de
      servicios (§1.4, `page.test.tsx:141`) es una regresion real y **no se
      toca**. Anadir los que faltan: los KPIs por ancho, el saludo, la ausencia
      de "Proxima cita", **que solo hay UN boton "Actualizar" en cada ancho** (la
      regresion de D27), **que el estado vacio y el de carga siguen ahi** (la
      lista de la cabecera de esta tarea — son las piezas sin red), y **los dos
      casos en que no se monta ni la tarjeta "Ahora mismo" ni su rotulo** (D37):
      sin ningun empleado activo, y **con el salon cerrado** — este segundo con
      empleados que hoy libran, para probar que las filas `off` **no** sostienen
      el panel por si solas.
      Acuerdate de la trampa 15 (§1.7): esta pagina sustituye `use-staff` entero
      con una factoria y hay que anadirle `useEmployeesWorkingHours`.
- [ ] **Paso 9:** prueba de mutacion. Obligatoria: **una que demuestre que en
      movil se montan 3 KPIs y en escritorio 4**, no que cambian de clase.
- [ ] **Paso 10:** commit.

---

### T9 · Comparacion visual y puertas globales

**Ficheros:** los que §3 asigna a esta tarea.

- [ ] **Paso 1:** `/today` **ya esta registrado** en
      `visual/shell-vs-artboards.spec.ts:59-63` (movil, artboard `Main`) y `:153`
      (escritorio, `HoyDesktop`), con capturas a 390, 1024 y 1440. **No se crea
      una spec nueva**: dos specs sobre la misma pantalla divergen. Se **amplia
      la cabecera** de esa con un "que mirar" por par, al estilo de
      `visual/appointment-detail-vs-artboards.spec.ts`.
      **Esa cabecera tiene que decir la diferencia ESPERADA de D35**: a 1440 el
      contenido sale 1084px de ancho y el artboard dibuja 1136. Es deuda
      conocida de las 12 rutas, **no un hallazgo de esta pantalla**. Sin esa
      linea, T10 lo reporta como fallo y se pierde una ronda.
- [ ] **Paso 2:** verificar que el **ancla** de `/today` (`:62`) sigue existiendo
      tras la reescritura. Si el texto cambio, actualizarla — una spec que espera
      un texto que ya no existe falla por la razon equivocada.
- [ ] **Paso 3: no se ejecuta aqui.** Necesita credenciales
      (`RIVOO_E2E_EMAIL`/`RIVOO_E2E_PASSWORD`, **solo por variables de entorno,
      nunca en el repo**) y la pila levantada. Dejar en el informe la orden
      exacta.
- [ ] **Paso 4: puertas globales** (§4), las cuatro, con la salida pegada y
      comparada contra §1.10.
- [ ] **Paso 5:** commit.

---

### T10-T12 · Revision del bloque entero

**Se lanza cuando T0-T9 estan cerradas.** Panel de **TRES revisores
independientes, en paralelo, agentes NUEVOS, ninguno implementador de nada,
instruidos para REFUTAR.** Se descarta un hallazgo si la mayoria lo refuta.

- **T10 — Lente 1, fidelidad al artboard.** Los dos artboards contra el codigo,
  valor a valor. Busca especificamente: `leading-*` perdido por `cn()` (§1.7.1),
  `line-height` heredado de la preflight (§1.7.2), hexes a pelo donde habia token
  (§1.9), y que las inconsistencias de §1.3 se hayan resuelto **como dice
  §2** y no de otra forma. **Ojo a las que §2 invirtio en la ronda de revision
  del plan**: la facturacion va **"412 €" entero** (D9/D32) y la fila "En curso"
  **no** lleva borde especial (D13) — lo contrario de lo que decia el plan
  antes. Y §1.3 **no es exhaustiva**: compara las tablas de §1.1 y §1.2 valor a
  valor en toda pieza que se pinte en los dos anchos.
- **T11 — Lente 2, correccion.** La traduccion de fecha de T1 (¿el rango es
  correcto en cambio de horario de verano? ¿y a las 23:59?), `today-facts.ts`
  entero, el cruce de "ahora" con los horarios, y el orden y las exclusiones de
  D37. Busca estados imposibles y datos que se contradicen entre si. Sobre
  peticiones: **3 + N es lo esperado** (D36) — hay que cazar lo que se dispare
  **por encima** de eso, no la N.
- **T12 — Lente 3, regresion y calidad de los tests.** Que `/calendar` **no se
  haya movido** pese a que T1 cambia lo que recibe (es carril cerrado); que los
  tests nuevos prueben algo (mutar el codigo y ver si caen); que ninguna
  asercion de precio use un espacio normal donde `formatCurrency` emite U+00A0
  (§1.7.4); y que los 4 tests de la guarda de catalogo sigan intactos.

- [ ] **Volcar el plan y sus deudas a `E:\IdeaProjects\rivoo\tasks\todo.md`.**
- [ ] **Deudas a anotar explicitamente:**
      1. **`/calendar` heredaba el mismo fallo de fecha y lo arregla T1 sin
         tocarlo** — conviene verificarlo en vivo cuando haya pila, porque nadie
         lo ha visto funcionar.
      2. La zona horaria se toma del dispositivo (D2); `TIMEZONE` de
         `dates.ts:5` sigue exportado y sin usar. Cuando haya salones fuera de la
         peninsula habra que resolver cual manda.
      3. **`source` no es filtrable en el servidor** (§1.6.6). Anadirlo es JPQL y
         de coste bajo, y permitiria `size=1` + `totalElements` para un contador
         de reservas online sin confirmar de **cualquier** fecha, no solo de hoy.
      4. **`PENDING` es el estado inicial de todo** (§1.6.7): si algun dia se
         quiere "pendientes de confirmar" de verdad, hace falta distinguir.
      5. El endpoint `stats` esta **roto** (§1.6.1): dos de sus tres campos
         devuelven `{}` por stubs que retornan `0`, y las consultas agrupadas que
         existen no las llama nadie.
      6. **`totalVisits`/`lastVisitAt` siguen sin escribirlos nadie** (§1.6.10).
      7. **`AvailabilityService.java:153-158` devuelve huecos ya pasados** cuando
         no se le manda `serviceId` (§1.6.11).
      8. **El rol `EMPLOYEE` ve la agenda y la facturacion de todo el salon**
         (§1.6.12). Decision de producto pendiente.
      9. **El descanso no se descuenta del hueco libre** (D21). Y el artboard de
         "Hoy" no dibuja el estado "Hoy no trabaja" que D18 monta reusando el
         texto aprobado en `NuevaCitaPaso1.dc.html:89,92`: hueco de canvas, con
         una solucion honesta mientras tanto.
      10. **El destino del CTA "Revisar y confirmar" no lo dibuja nadie** (D24).
      11. `size=100` para un dia (D23): un salon con mas de 100 citas en un dia
          veria la lista truncada — y **por el peor extremo**: el servidor ordena
          `startTime DESC` (§1.5), asi que devolveria las **ultimas** 100 del dia
          y el `sorted` ascendente del cliente lo disimularia. Ademas
          descuadraria el contador de reservas online de D22, que cuenta sobre
          esa misma pagina.
      12. **`max-w-[1084px]` de `page-shell.tsx:131` frente a los 1136px que
          dibuja el artboard a 1440** (D35). El padding **si** coincide
          (`px-7 py-6`). Son 12 rutas: no se cambia aqui.
      13. `formatShortName`/`getInitials` duplican parcialmente `initials()`
          (D31).
      14. **No hay endpoint de horarios en lote** (D36): `/today` hace N
          peticiones, una por empleado.
      15. **`useEmployees()` no manda `size`** (`use-staff.ts:11-19`) → Spring
          devuelve 20 (§1.6.2). Un salon con mas de 20 empleados veria el panel
          "Ahora mismo" truncado **en silencio**. Afecta a mas pantallas que esta.
      16. **`now` se congela al montar** (D33): el boton de refrescar lo
          re-siembra, pero al pasar la medianoche con la pestana abierta ni `now`
          ni el `today` de `:23` avanzan solos. Hoy tampoco lo hacen.
      17. **Las dos "En curso" de la pantalla tienen fuentes distintas** (D37):
          el panel va por el reloj, la fila de cita por el estado registrado.
          Pueden no coincidir. Decidir en canvas si la fila deberia ir tambien
          por el reloj.
      18. **Fidelidad heredada del chasis, no tocada aqui:** el CTA del artboard
          es `padding: 0 16px` (`HoyDesktop:83`) y `size="action"` es `px-[18px]`
          (`button.tsx:35`); el `h1` de escritorio es `text-2xl` (24px con
          `line-height: 2rem`) frente a los 24px/**1.1** del artboard
          (`HoyDesktop:76`) — la trampa de §1.7.2, dentro de `PageShell`.
      19. **`AppointmentCard` pide los empleados por su cuenta** (D34) porque la
          cita no lleva `colorHex` (`types/appointment.ts:12-33`). Llevarlo en el
          DTO ahorraria la dependencia.
- [ ] **Si el usuario corrige algo durante la ejecucion, anotar el patron y la
      regla en `tasks/lessons.md`.**

---

## Execution Order

**Backend (`E:\IdeaProjects\rivoo`):**
```
NINGUNA TAREA. Este bloque no toca el backend (consecuencia de D6 y D22).
```

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**
```
Ola 0   T0  tokens                                  (sin dependencias)
Ola 1   T1  API: el dia viaja de verdad     ┐
        T2  PageShell: mobileTitle          │ disjuntos, en paralelo
        T3  today-facts.ts (modulo puro)    ┘
Ola 2   T4  KpiCard                         ┐
        T5  NowPanel                        │ dependen de T3;
        T6  PendingOnlineCard               │ disjuntos entre si
        T7  AppointmentCard reescrita       ┘
Ola 3   T8  La pagina                              depende de T0,T2,T3,T4,T5,T6,T7
Ola 4   T9  Comparacion visual + puertas globales  depende de T8
Ola 5   T10 ‖ T11 ‖ T12  panel de 3 revisores      depende de todo
```

**Coordinacion:** no hay trabajo de backend, asi que no hay nada que
sincronizar entre repos. Las puertas globales las pasa el **orquestador** al
cerrar cada ola, sobre el arbol quieto (§4, regla 3), nunca los implementadores.

---

## Dependencias con otros specs/FRs

| Spec/FR | Relacion | Implicacion para este bloque |
|---|---|---|
| **Bloque 2** — Shell de escritorio | **Pre-requisito** (cerrado) | Aporta `PageShell` y la barra lateral de 248px. T2 lo **extiende** con un prop opcional; cualquier cambio de comportamiento por defecto esta prohibido. |
| **Bloque 3** — Calendario | **Consumidor involuntario** (cerrado) | Comparte `useAppointments` y **hereda el mismo fallo de fecha** (§1.5). T1 lo repara **sin tocar sus ficheros**; T12 tiene que verificar que no se ha movido. |
| **Bloque 4** — Detalle de cita | **Complementario** (cerrado) | `/today` monta `AppointmentDetailSheet`, que es suyo. Esta pantalla no lo modifica; solo lo sigue montando. |
| **Bloque 8** — Asistente de nueva cita | **Complementario** (cerrado) | Aporta `formatDurationTight` (§1.7.14) y el patron de prop opcional con default (`completedTone`, D25). Comparte `useEmployeesWorkingHours` (§1.8), que se consume **tal cual**. |
| **Bloque 6** — Equipo y clientes | **Sin relacion** | Ningun fichero en comun. |

---
