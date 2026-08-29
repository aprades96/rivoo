# Detalle de cita — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: `executing-plans`. Los pasos usan casillas (`- [ ]`).

**Objetivo:** montar el detalle de una cita en sus DOS formas dibujadas — hoja inferior en movil
(`design/DetalleCita.dc.html`) y panel acoplado de 360px en escritorio
(`design/DetalleCitaDesktop.dc.html`) — y el estado "seleccionado" del bloque de la rejilla que lo
acompana.

**Arquitectura:** el panel de escritorio NO es una hoja modal: es una COLUMNA HERMANA de la rejilla
dentro del cuerpo de `/calendar`. La hoja de movil sigue siendo un `Sheet`. Comparten los DATOS
(un modulo de derivacion y un componente de acciones) pero no el chasis ni la disposicion, porque
los dos artboards ordenan y agrupan los mismos hechos de forma distinta.

**Stack:** Next.js 16 (App Router), TypeScript, Tailwind v4, Shadcn/UI + `@base-ui/react`,
React Query v5, Vitest 4, Playwright (visual).

**Complejidad:** COMPLEJA (6+ ficheros, atraviesa primitiva compartida, rejilla y dos pantallas).
Motor de ejecucion: `executing-plans`. Revision: panel de 3 revisores AL CERRAR EL BLOQUE.

**Repo unico:** `E:\IdeaProjects\rivoo-frontend`. Rama `master`, arbol limpio, `origin/master`
al dia en `18e3b06`.

**Linea base a batir:** 574 tests en 66 ficheros, verde (`npx vitest run`, 2026-08-29). `tsc`
limpio. `npm run build` OK.

---

## COMO LEER ESTE PLAN

Cada hecho esta EN UN SOLO SITIO. La §1 son los datos verificados contra el canvas y contra el
codigo, la §2 las decisiones, la §5 las tareas — y las tareas REFERENCIAN §1/§2 en vez de repetir
valores. Un implementador que solo reciba el texto de su tarea NO tiene los numeros: el brief
tiene que incluir ademas las subsecciones de §1 y las decisiones §2 que su tarea cita.

---

## §1 · DATOS VERIFICADOS

### §1.1 · Artboard movil — `design/DetalleCita.dc.html` (390x844)

Es una HOJA INFERIOR sobre la pantalla de Hoy. El fondo (`:23-32`) es el contenido de Hoy
desenfocado (`filter: blur(1.5px); opacity: 0.5`) y encima va un velo
`rgba(42, 35, 32, 0.42)` (`:34`). El desenfoque del contenido es del ARTBOARD (dibuja lo que hay
detras); lo que la hoja aporta es el velo.

| Elemento | Linea | Valores |
|---|---|---|
| Hoja | `:36` | `left/right/bottom: 0` · `flex-col gap:16px` · `padding: 10px 16px 20px 16px` · `border-radius: 16px 16px 0 0` · `background: #FBF7F2` · `box-shadow: 0 -8px 30px rgba(42,35,32,0.2)` |
| Asa | `:38-40` | pildora `36x4px`, `border-radius:999px`, `background:#D8C9B8`, centrada |
| Cabecera | `:42-45` | fila `space-between`, gap 12px. Titulo `.display` **23px / line-height 1.1**. Badge a la derecha |
| Badge estado | `:44` | `padding: 4px 10px` · `radius 999` · `background:#FAEFD6` · `color:#8A5B12` · `11px/600` · texto **"Pendiente"** |
| Lista de hechos | `:47` | `flex-col gap:14px` |
| Fila hora | `:49-55` | icono reloj 18px `stroke:#9A8A7E` `stroke-width:1.75`, `margin-top:1px`; gap 12px. Titulo `.num` 15px/600 = `10:00 - 11:30`. Debajo 12px `#7A6A5F` = `Martes, 27 de agosto · 1h 30min` (gap 2px) |
| Fila cliente | `:57-72` | icono usuario 18px. Nombre 15px/600. Debajo fila gap 14px, 12px `#7A6A5F`, con dos grupos de `gap:5px`: telefono (icono 12px + `.num`) y email (icono 12px). Columna gap **4px** |
| Fila servicio | `:74-80` | icono tijeras 18px. Nombre 15px/600. Debajo `.num` 12px `#7A6A5F` = `1h 30min · 65,00 €`. gap 2px |
| Fila empleado | `:82-87` | **NO lleva icono**: lleva un PUNTO de 10px `border-radius:999px` centrado en una caja de 18x18. Color del punto `#5C7A5E` (el del empleado). Texto 14px, peso NORMAL, gap 12px, `align-items:center` |
| Fila nota | `:89-92` | icono documento 18px. Texto 13px `#7A6A5F` `line-height:1.45`. Sin recuadro |
| Separador | `:95` | `height:1px; background:#E7DCCF` |
| Acciones | `:97-112` | columna `gap:8px` |
| CTA | `:98-101` | alto **48px** · `radius 8` · `background:#B4522F` · blanco · `15px/600` · icono check 18px `stroke-width:2.25` · gap 8px |
| Fila secundaria | `:102-111` | dos botones `flex-grow:1`, `gap:8px`, alto **46px**, `radius 8`, `background:#FFFFFF`, `14px/500`, gap interno 7px, icono 16px |
| — "No asistio" | `:103-106` | `border:1px solid #E7DCCF`, icono `stroke:#7A6A5F` |
| — "Cancelar" | `:107-110` | `border:1px solid #EDD6D0`, `color:#A34434`, icono `currentColor` |
| Meta | `:114` | 11px `#9A8A7E` = `Fuente: Reserva online · Recordatorio enviado` |

**No dibujado en movil:** boton de cerrar (X), "Reprogramar", precio destacado, tarjetas.

**Incoherencia del propio canvas, anotada para que no se tome por defecto de implementacion:** el
punto de Laura Martinez aqui es `#5C7A5E` (`:84`), verde, pero su avatar es `#B4522F` en el panel
(`DetalleCitaDesktop:294`) y en la cabecera de columna (`:116`), terracota. El mismo empleado, dos
colores. Manda la REGLA, no el pixel: el color sale del empleado (`colorHex` o la posicion en la
paleta de reserva, D12), asi que los dos saldran iguales. La lente de fidelidad de T12 lo marcara
como desviacion — no lo es.

### §1.2 · Artboard escritorio — `design/DetalleCitaDesktop.dc.html` (1440x900)

Chasis identico al del calendario: barra lateral 248px con **"Citas"** activa, barra superior de
72px a `padding: 0 24px` con la fecha a 26px, el navegador prev/Hoy/next, el segmentado Dia/Semana,
el boton-lupa y el CTA "Nueva cita" (`:79-106`). **Nada de eso cambia de valor**: ya esta
construido. (Gana un `flex-shrink:0` respecto a `CalendarioDesktop:74`, que `page-shell.tsx:228`
ya trae.)

El panel (`:249-330`) es hermano de la zona de rejilla dentro de la fila
`display:flex; flex-grow:1; min-height:0` de `:108`. **Empieza debajo de la barra superior**, que
sigue ocupando el ancho completo. NO hay velo ni backdrop dibujado.

| Elemento | Linea | Valores |
|---|---|---|
| Panel | `:249` | `width:360px; flex-shrink:0` · `padding:20px` · `border-left:1px solid #E7DCCF` · `background:#FAF6F0` · `flex-col gap:14px` |
| Rotulo + cierre | `:251-256` | fila `space-between`. Rotulo **12px/600**, `letter-spacing:0.06em`, MAYUSCULAS, `#9A8A7E` = `Detalle de cita`. Boton X `30x30`, `radius 8`, `border:1px solid #E7DCCF`, `background:#FFFFFF`, icono 15px `#7A6A5F` |
| Bloque de hora | `:258-262` | columna gap **7px**. Badge `align-self:flex-start`, `4px 10px`, `radius 999`, `#FAEFD6`/`#8A5B12`, **11px/700**, texto **"Pendiente de confirmar"**. Hora `.display .num` **30px / line-height 1.1**. Fecha 13px `#7A6A5F` = `Martes, 27 de agosto · 1h 30min` |
| `.sec` (tarjeta) | `:25` | `flex; align-items:center; gap:12px; padding:12px; border:1px solid #E7DCCF; border-radius:10px; background:#FFFFFF` |
| `.ico` (chip) | `:26` | `36x36`, `radius 8`, `background:#F5EEE6`, icono 17px `stroke:#B4522F` `stroke-width:1.75` |
| Tarjeta cliente | `:264-280` | `.ico` usuario + columna gap 1px (nombre 14px/600, telefono `.num` 12px `#7A6A5F`) + dos botones `32x32`, `radius 8`, `border:1px solid #E7DCCF`, `background:#FFFFFF`, iconos 15px `#7A6A5F`: **telefono** y **mensaje**. Los dos botones van en un `flex gap:6px` |
| Tarjeta servicio | `:282-291` | `.ico` tijeras + columna (nombre 14px/600, duracion 12px `#7A6A5F`) + precio `.display .num` **17px** a la derecha |
| Tarjeta empleado | `:293-299` | **avatar 36px circular** (`radius 999`, `background:#F6E7E0`, `color:#B4522F`, `12px/700`, iniciales `LM`) + columna (nombre 14px/600, `jobTitle` 12px `#7A6A5F` = `Estilista`). Sin `.ico`, sin acciones |
| Recuadro nota | `:301-307` | `flex gap:10px; padding:12px; border:1px solid #E8D3A6; border-radius:10px; background:#FFFCF5`. Icono triangulo 15px `stroke:#8A5B12`, `flex-shrink:0`, `margin-top:1px`. Rotulo `.fldlbl` (11px/600, `letter-spacing:.06em`, MAYUSCULAS) en `#8A5B12` = `Nota`. Cuerpo 12px **`#5F4A28`** `line-height:1.45`. Columna `.fld` gap 3px |
| Meta | `:309-312` | fila gap 7px, icono reloj 13px `#9A8A7E`, texto 11px `#9A8A7E` = `Reserva online · recibida hace 2 h · recordatorio enviado` |
| Acciones | `:314-329` | columna gap **9px** con **`margin-top:auto`** (pegadas al fondo del panel) |
| CTA | `:315-318` | alto **46px**, `radius 8`, `#B4522F`, blanco, `15px/600`, icono 17px `stroke-width:2.25`, gap 8px |
| Fila secundaria | `:319-328` | `grid-template-columns: repeat(2, minmax(0, 1fr))`, `gap:9px` (el `minmax(0,·)` importa: `1fr` a secas no impide el desbordamiento por contenido; Tailwind `grid-cols-2` ya emite la forma correcta). `.act` (`:27`) = alto **40px**, `border:1px solid #E7DCCF`, `radius 8`, `background:#FFFFFF`, **13px/600**, `color:#2A2320`, gap 7px, icono 15px |
| — "Reprogramar" | `:320-323` | `.act` tal cual, icono calendario `stroke:#7A6A5F` |
| — "Cancelar" | `:324-327` | `.act` + `border-color:#EDD6D0; color:#A34434`, icono `currentColor` |

**Diferencias reales entre los dos anchos, no cosmeticas — OCHO:**
1. El segundo boton secundario es **"No asistio" en movil** (`DetalleCita:103-106`) y
   **"Reprogramar" en escritorio** (`DetalleCitaDesktop:320-323`).
2. El badge dice **"Pendiente"** en movil (`:44`) y **"Pendiente de confirmar"** en escritorio
   (`:259`).
3. La meta lleva **tiempo relativo** ("recibida hace 2 h") SOLO en escritorio.
4. **La meta cambia de forma, no solo de contenido:** movil `Fuente: Reserva online ·
   Recordatorio enviado` (`:114`) — con prefijo "Fuente:" y en mayuscula inicial; escritorio
   `Reserva online · recibida hace 2 h · recordatorio enviado` (`:311`) — sin prefijo y en
   minusculas. Son dos cadenas distintas, no una con un tramo extra.
5. **El EMAIL del cliente se dibuja en movil y DESAPARECE en escritorio.** Movil lo pinta junto al
   telefono (`:66-69`, `ana@mail.com`); la tarjeta de escritorio (`:264-280`) solo trae telefono y
   los dos botones de contacto. Escritorio no anade acciones al mismo contenido: cambia el
   contenido.
6. Escritorio agrupa en tarjetas (`.sec`) con avatar de iniciales y `jobTitle`; movil los pone como
   filas planas con icono y un punto de color.
7. La nota va en recuadro de aviso en escritorio y como fila plana en movil.
8. Movil lleva asa y no lleva X; escritorio lleva X y no lleva asa.

### §1.3 · Lo que cambia en la REJILLA al abrir el panel — el MODO ESTRECHO

Abrir el panel no solo pone un anillo: **el canvas redibuja la rejilla entera para caber en menos
ancho**. Son CINCO cambios, y el plan v1 solo recogia dos. Comparados los dos artboards elemento a
elemento (no solo sus atributos `style`):

**(1) El bloque seleccionado.**

| | Calendario | Detalle |
|---|---|---|
| Bloque de Ana Garcia | `CalendarioDesktop:168` | `DetalleCitaDesktop:177` |
| Estilo | `top:240px; height:140px; border-left:3px solid #C08A2E; border-color:#E8D3A6; background:#FFFCF5` | lo mismo **+ `box-shadow: 0 0 0 2px #B4522F, 0 6px 14px rgba(42,35,32,0.12)`** |

Ese `box-shadow` **SUSTITUYE** al de la clase `.blk` (`0 1px 2px rgba(42,35,32,0.05)`), no se suma.

**(2) Los bloques NO seleccionados pierden el tramo final de su tercera linea.** Cinco de los nueve.
El seleccionado NO lo pierde:

| Bloque | Calendario | Detalle |
|---|---|---|
| Carla Ruiz | `:165` `09:00 - 10:00 · 35,00 €` | `:174` `09:00 - 10:00` |
| **Ana Garcia (seleccionada)** | `:174` `10:30 - 12:00 · 65,00 €` | `:183` `10:30 - 12:00 · 65,00 €` — **lo conserva** |
| Nuria Camps | `:195` `08:00 - 08:45 · Completada` | `:204` `08:00 - 08:45` |
| Laia Roca | `:201` `11:00 - 12:00 · 22,00 €` | `:210` `11:00 - 12:00` |
| Marta Vidal | `:227` `11:30 - 12:00 · Cancelada` | `:236` `11:30 - 12:00` |
| Oriol Bosch | `:233` `12:00 - 13:30 · 32,00 €` | `:242` `12:00 - 13:30` |

Almuerzo, Jordi Mas y Pedro Sanchez no cambian porque su tercera linea nunca tuvo sufijo.
**La regla:** en modo estrecho el bloque pinta solo el rango horario; el SELECCIONADO mantiene su
sufijo (precio o etiqueta de estado terminal). Es coherente: la columna es mas estrecha, se recorta
lo accesorio, y el que el usuario esta mirando conserva su dato.

**(3) El canal de horas se estrecha: 64px -> 58px.**

| | Calendario | Detalle |
|---|---|---|
| Cabecera | `:104` `width: 64px` | `:113` `width: 58px` |
| Rejilla | `:132` `width: 64px` | `:141` `width: 58px` |

Ese ancho lo fija `src/components/calendar/time-grid.tsx:20` (`desktop: { width: 64, ... }`), NO
`day-view.tsx` — que lo documenta expresamente en `:129` (*"El ancho no se repite aqui: lo fija
`TimeGrid` (64/46px)"*).

**(4) La cabecera de empleado se comprime.** Cuatro cambios de VALOR por celda (hay dos mas,
`flex-shrink:0` en el avatar y `min-width:0` en la columna de texto, que el codigo ya trae en
`employee-column-header.tsx:84,98` y no dan trabajo):

| | Calendario (`:106,109,110`) | Detalle (`:115,118,119`) |
|---|---|---|
| gap interno | `10px` | `9px` |
| padding | `0 12px` | `0 10px` |
| nombre | `14px` | `13px` |
| meta | `4 citas · 5h 30min` | `4 citas` |

El avatar (30px) y el alto de fila (60px) no cambian. El codigo actual reproduce la version ANCHA:
`employee-column-header.tsx:77` = `gap-2.5 px-3`, `:103` = `text-[14px]`.

**(5) El marco de la rejilla se estrecha.**

| | Calendario | Detalle |
|---|---|---|
| Padding lateral | `padding: 0 24px` (`:103`, `:130`) | `padding: 0 20px` (`:112`, `:139`) |
| Gap entre columnas | `gap: 12px` (`:105`, `:150`) | `gap: 10px` (`:114`, `:159`) |

Afecta a las DOS filas — la de cabeceras y la de la rejilla — porque comparten rejilla CSS.

> **Como se detecto la version falsa de esta seccion:** el plan v1 comparo los atributos `style` de
> los nueve `.blk` y, al salir identicos, afirmo que los bloques eran identicos. El `style` no lleva
> el CONTENIDO. Un negativo no se afirma desde una comprobacion que no lo cubre.

### §1.4 · Estado del codigo

**Ya existe y hay que REESCRIBIR:** `src/components/appointments/appointment-detail-sheet.tsx`
(277 lineas). Hoy es un `Sheet side="bottom"` generico: `SheetHeader` + filas con iconos + un
`StatusActions` con botones `Button` apilados a ancho completo con `justify-start`. No se parece
al artboard en ninguna medida (no hay asa, ni alturas 48/46, ni la fila secundaria de dos, ni el
velo al 42%). Su **maquina de estados** (`:223-240`) SI es real y hay que conservarla:

| Estado | Acciones actuales |
|---|---|
| `PENDING` | Confirmar (`CONFIRMED`) · Cancelar |
| `CONFIRMED` | Iniciar (`IN_PROGRESS`) · No asistio (`NO_SHOW`) · Cancelar |
| `IN_PROGRESS` | Completar (`COMPLETED`) · ~~Cancelar~~ **(CORREGIDO en la revision: el dominio rechaza `IN_PROGRESS -> CANCELLED`, `AppointmentStatus.java:25`; se quito del frontend)** |
| `COMPLETED` / `CANCELLED` / `NO_SHOW` | ninguna (`return null`) |

Ese reparto lo respalda el DOMINIO, en el otro repo:
`E:\IdeaProjects\rivoo\appointment-service\src\main\java\com\rivoo\appointment\domain\model\AppointmentStatus.java:20-27`

```java
case PENDING     -> target == CONFIRMED || target == CANCELLED;
case CONFIRMED   -> target == IN_PROGRESS || target == CANCELLED || target == NO_SHOW;
case IN_PROGRESS -> target == COMPLETED;
case COMPLETED, CANCELLED, NO_SHOW -> false;
```

Lo comprueba `AppointmentService.java:196` antes de guardar, via `Appointment.canTransitionTo`
(`Appointment.java:61-62`), y `AppointmentStatusTest.java:91-93` afirma hoy explicitamente que
**`PENDING -> NO_SHOW is invalid`**. Es justo la transicion que el artboard movil dibuja (D5).

Tambien conserva un `Dialog` de confirmacion de cancelacion con `Textarea` de motivo
(`:176-205`) — no esta dibujado en ningun artboard pero es la unica forma de mandar el `reason`
que la API acepta.

**Ya lo usan dos pantallas:** `(app)/calendar/page.tsx:366` y `(app)/today/page.tsx:236`, las dos
con la misma terna de props `{appointment, open, onOpenChange}`.

**La pagina de calendario ya tiene el cableado**: `selectedAppointment` + `sheetOpen`
(`:78-79`), `handleAppointmentTap` (`:229-232`) y un comentario en `:361-365` que dice
literalmente que el bloque siguiente sustituye la hoja por un panel acoplado de 360px.

**Primitiva `Sheet`** (`src/components/ui/sheet.tsx:56`): con `side="bottom"`, a partir de **`md:`
(768px)** deja de estar anclada abajo y se convierte en **dialogo centrado**. Son **CATORCE**
variantes `data-[side=bottom]:md:*` (posicion, tamano, radio, borde y las cuatro de animacion),
no cuatro: contarlas mal deja `md:` sueltos al migrarlas. El
`SheetOverlay` (`:31`) es `bg-black/10` con `backdrop-blur-xs`. La usan con `side="bottom"` cuatro
componentes: este detalle, `clients/client-form.tsx:109`, `services/service-form.tsx:114`,
`staff/employee-form.tsx:128`.

**`PageShell` en `layout="fill"`** (`page-shell.tsx:121-142`): la capa interna
`data-slot="page-shell-content"` es `flex min-h-0 flex-1 flex-col` — una COLUMNA. El panel tiene
que ir en una FILA, asi que la fila la monta la propia pagina (§2.D2).

**`DayView`** (`day-view.tsx:30-33`): `FRAME_PADDING_CLASSNAME = { desktop: "px-6", mobile: "px-3" }`
y `gap-x-3` en la rejilla compartida (`:191`). No tiene ninguna nocion de seleccion.

**`AppointmentBlock`** (`appointment-block.tsx:183`): sombra base
`shadow-[0_1px_2px_rgba(42,35,32,0.05)]` en el `cn` del boton. `data-testid="appointment-block"`.
No tiene prop de seleccion.

**Avatar con iniciales**: ya existe en `employee-column-header.tsx` — paleta de reserva
`FALLBACK_AVATAR_CLASSNAMES` (`:22`), eleccion por POSICION (`:31-45`) y uso de `colorHex` con
sufijo `"20"` para el fondo (`:93`). El panel necesita exactamente lo mismo a 36px.

**Datos.** `Appointment` (`src/types/appointment.ts:12-33`) trae `clientName`, `clientPhone`,
`clientEmail`, `employeeId`, `employeeName`, `serviceName`, `servicePrice`,
`serviceDurationMinutes`, `startTime`, `endTime`, `status`, `source`, `notes`, `reminderSent`,
`createdAt`. **NO trae** `jobTitle` ni `colorHex` del empleado: eso vive en `Employee`
(`types/employee.ts:1-11`) y llega por `useEmployees()`.

**Mutaciones**: `useUpdateAppointmentStatus` y `useCancelAppointment`
(`hooks/use-appointments.ts:85-184`), las dos con actualizacion optimista sobre
`["appointments"]` e invalidacion en `onSettled`. **No hay** hook de `getById` (el metodo de API
si existe, `lib/api/appointments.ts:23`).

**Utilidades disponibles**: `formatTime`, `formatDate`, `formatDateShort`, `formatRelativeDay`,
`formatDuration`, `formatTimeRange` (`lib/utils/dates.ts`); `formatCurrency`, `formatPhone`,
`initials`, `capitalizeFirst` (`lib/utils/format.ts`).

`formatPhone("612345678")` da `612 345 678`, que es lo dibujado (`DetalleCita:64`,
`DetalleCitaDesktop:270`): el telefono NO se pinta crudo. `initials(firstName, lastName)` acepta el segundo
argumento como OPCIONAL (`format.ts:23`), asi que sirve con el `Employee` pero **no** en la
degradacion de D11: alli solo hay `employeeName` en una sola cadena, y llamarlo con un argumento
devuelve una unica letra ("L"), no "LM". Ahi hay que partir por el espacio.

**TRAMPA DEL ESPACIO DURO, ya conocida por el repo:** `formatCurrency(65)` devuelve `65,00 €` con
un **U+00A0**, no un espacio normal — `Intl.NumberFormat` lo mete siempre. Un test que afirme
`"65,00 €"` tecleado a mano NO encuentra nada y se queda verde en falso. El repo ya tiene el
antidoto y el aviso escrito en `appointment-block.test.tsx:40-51` (`normalize` en `:43`, `exact` en `:48`): los
tests nuevos lo reutilizan en vez de redescubrirlo.

**`statusConfig` tiene mas consumidores de los que parece:** `StatusBadge`
(`status-badge.tsx:36-43`) lo usan `appointment-card.tsx` y `src/app/dev/preview/page.tsx`, ademas
de la hoja. Anadir una variante larga esta bien; cambiar los rotulos existentes rompe esas dos
pantallas y `status-badge.test.tsx`.

**CUIDADO con dos que NO sirven aunque lo parezcan:**

- **`formatDate` NO produce la fecha dibujada.** `dates.ts:10-12` es
  `format(parseISO(iso), "d MMM yyyy", { locale: es })` = **"27 ago 2026"**. Los dos artboards
  escriben **"Martes, 27 de agosto"** (`DetalleCita:53`, `DetalleCitaDesktop:261`). Ninguna de
  las seis funciones de `dates.ts` la da: `formatDateShort` es "27 ago" y `formatRelativeDay`
  devuelve "Hoy"/"Manana" justo en el caso mas frecuente de un detalle de cita. La expresion
  correcta ya esta copiada en el repo, p. ej. `calendar/page.tsx:271` y `date-navigator.tsx:20`:
  `capitalizeFirst(format(d, "EEEE, d 'de' MMMM", { locale: es }))`.
- **No hay formateador de tiempo relativo**, y el de `date-fns` no da la forma del artboard:
  con locale `es`, `formatDistanceToNow` de 2 horas devuelve "alrededor de 2 horas" (con
  `addSuffix`, "hace alrededor de 2 horas") y `formatDistanceToNowStrict`, "2 horas". El
  artboard escribe **"hace 2 h"**, abreviado. Ver D15.

**Ruta `/appointments/[id]`** (`(app)/appointments/[id]/page.tsx`, 13 lineas): stub que pinta
"En desarrollo". **Nadie la enlaza** — verificado por `grep` sobre `src/` (los unicos
`/appointments/${id}` son URLs de la API en `lib/api/appointments.ts`). No tiene artboard: los dos
artboards de este bloque dibujan hoja y panel, no una pantalla suelta.

### §1.5 · Tokens

Ya existen en `src/app/globals.css` y hay que USARLOS, no reescribir el hex:

| Hex del artboard | Token | Linea |
|---|---|---|
| `#FBF7F2` | `--background` | `:111` |
| `#2A2320` | `--foreground` | `:112` |
| `#FFFFFF` | `--card` | `:113` |
| `#B4522F` | `--primary` | `:117` |
| `#F5EEE6` | `--secondary` (`:119`) / `--muted` (`:121`) | `:119`, `:121` |
| `#7A6A5F` | `--muted-foreground` | `:122` |
| `#F6E7E0` | `--accent` | `:123` |
| `#A34434` | `--destructive` | `:125` |
| `#E7DCCF` | `--border` | `:126` |
| `#9A8A7E` | `--muted-foreground-2` | `:149` |
| `#FAF6F0` | `--muted-subtle` | `:150` |
| `#E8D3A6` | `--warning-border` | `:158` |
| `#EDD6D0` | `--destructive-border` | `:160` |
| `#FFFCF5` | `--warning-soft` | `:164` |
| `#FAEFD6` | `--color-status-pending-bg` | `:13` |
| `#8A5B12` | `--color-status-pending-text` | `:14` |

**Faltan dos**, y son los unicos que hay que anadir:

| Hex | Token nuevo | Donde | Por que no vale otro |
|---|---|---|---|
| `#5F4A28` | `--warning-text` | cuerpo de la nota, `DetalleCitaDesktop:305` | `--color-status-pending-text` (`#8a5b12`) es el rotulo, mas claro; `--label` es `#5f534b`, gris, no marron. **NO** llamarlo `--warning-foreground`: en este fichero `-foreground` significa "texto SOBRE ese fondo" y `--warning` es `#c08a2e` (`:163`), el borde de 3px, no el fondo de la nota |
| `#D8C9B8` | `--grabber` | asa de la hoja, `DetalleCita:39` | `--border-dashed` tiene ese mismo valor pero nombra el borde discontinuo del hueco "Libre"; usarlo como fondo de una pildora solida miente sobre su papel |

**Aviso de Tailwind v4:** una utilidad cuyo `--color-*` no este mapeado en `@theme inline` se
descarta EN SILENCIO. Los dos tokens nuevos necesitan su linea en `:root` **y** su mapeo en
`@theme inline`.

**Aviso de tailwind-merge:** trata el tamano de fuente como conflictivo con `leading` (por el
atajo `text-sm/6`). En un `cn()`, un `leading-*` escrito ANTES de un `text-[Npx]` se borra sin
avisar. Orden correcto: `text-[13px] leading-[1.45]`.

---

## §2 · DECISIONES

**D1 · El panel de escritorio NO es una hoja modal.** `DetalleCitaDesktop:249` lo dibuja como
hermano de la rejilla dentro de la fila de `:108`, con `border-left` y fondo propio, y **no dibuja
velo**. Un `Sheet`/`Dialog` lo pintaria por encima con backdrop y capturaria el foco: la rejilla
dejaria de poder pulsarse, cuando el artboard la deja visible y estrechada precisamente para que
se pueda seguir navegando. Se monta como columna en el arbol.

**D2 · La FILA la monta `/calendar`, no `PageShell`.** La capa interna de `PageShell` en `fill` es
`flex-col` (`page-shell.tsx:129`) y da servicio a doce pantallas. Meterle una variante de fila por
`contentClassName` contaminaria tambien la rama movil (`:190`), donde `flex-row` seria falso. La
pagina envuelve rejilla + panel en su propio `<div className="flex min-h-0 flex-1">`, que es
exactamente lo que dibuja `:108`. **Solo en escritorio**: en movil el arbol se queda como esta.

**D3 · Movil y escritorio comparten DATOS, no maquetacion.** Las OCHO diferencias de §1.2 no son
cosmeticas: cambian el texto del badge, las acciones ofrecidas y la meta. Un solo componente con
ramas `lg:` acabaria siendo dos componentes mal separados. Se parten en:
- `appointment-detail-facts.ts` — derivacion pura (etiquetas, meta, duracion, relativo).
- `appointment-actions.tsx` — el conjunto de acciones por estado y por ancho.
- `appointment-detail-sheet.tsx` — chasis y disposicion de MOVIL.
- `appointment-detail-panel.tsx` — chasis y disposicion de ESCRITORIO.

**D4 · La maquina de estados se conserva para los estados NO dibujados.** El artboard dibuja
unicamente `PENDING`. Borrar las transiciones de `CONFIRMED`/`IN_PROGRESS` (§1.4) seria perder
funcion real, no obedecer al artboard: sobre ellas el artboard no dice nada, asi que se quedan
como estan. Lo que se rehace es como se PINTAN: la primera accion de cada estado ocupa el CTA
(48px movil / 46px escritorio) y las demas la fila secundaria. Con tres acciones la fila
secundaria lleva dos botones, que es justo lo dibujado.

`PENDING` es la excepcion, y es la unica: ahi el artboard SI dice, y manda el (D5).

**D5 · La hoja de movil se pinta IDENTICA al artboard, y para eso se abre `PENDING -> NO_SHOW` en
el dominio.** El artboard movil dibuja, sobre una cita `PENDING`, tres acciones: Confirmar /
**No asistio** / Cancelar (`DetalleCita.dc.html:98-111`). Hoy eso es imposible en los dos lados:
`appointment-detail-sheet.tsx:224-227` no ofrece `No asistio` en `PENDING`, y el backend lo
PROHIBE — `appointment-service/src/main/java/com/rivoo/appointment/domain/model/AppointmentStatus.java:22`
solo permite `PENDING -> CONFIRMED | CANCELLED`. Construir el boton sin tocar el dominio daria un
control que el servidor rechaza con un 4xx: peor que no dibujarlo.

Manda el artboard, y ademas el dominio le da la razon: un cliente que reserva online y no aparece
ES un no-show, y obligar al salon a "confirmar" primero una cita que nunca ocurrio es falsear el
historial. Se anade `target == NO_SHOW` a la rama `PENDING` (T0) y la hoja pinta las tres acciones
tal cual estan dibujadas.

**Escritorio no cambia por esto**: alli la segunda casilla la ocupa "Reprogramar"
(`DetalleCitaDesktop.dc.html:320-323`), que es lo dibujado en ese ancho. Los dos artboards ofrecen
acciones distintas sobre el MISMO estado, y eso se respeta: cada ancho pinta lo suyo.

**Y eso deja un desequilibrio que hay que reconocer, no disimular.** El argumento que autoriza a
tocar el dominio es de NEGOCIO — una reserva online a la que nadie acude es un no-show —, y una
regla de negocio no depende del ancho de la pantalla. Con esta decision, T0 abre en el servidor una
transicion que el usuario de escritorio no puede ejercer, justo en el ancho donde esta el mostrador.
Se acepta **porque manda el artboard**, que es la regla del proyecto, y no porque sea lo unico
coherente. Queda anotado como deuda: si el escritorio necesita "No asistio" sobre una cita
pendiente, es una casilla que hay que DIBUJAR antes de construirla.

**D6 · "Reprogramar" lleva la intencion en la URL.** No existe flujo de reprogramacion. Navega a
`/appointments/new?rescheduleId=<id>&date=<yyyy-MM-dd>&time=<HH:mm>&employeeId=<id>`, con la MISMA
limitacion ya documentada en `calendar/page.tsx:238-252`: el asistente todavia no lee parametros.
Precedente en el propio repo: la pulsacion de una franja vacia ya viaja asi. Un boton dibujado que
no hace nada es peor que uno que deja la intencion escrita donde el asistente la recogera.

**D7 · En la primitiva `Sheet`, el lado `bottom` promociona a dialogo centrado en `lg`, no en
`md`.** Hoy lo hace en `md` (`sheet.tsx:56`), pero el bloque 2 decidio que el chasis movil llega
hasta 1023px: entre 768 y 1023 sale un dialogo centrado sobre un chasis movil, que nadie ha
dibujado. Se cambia el punto de promocion en la primitiva — **`md:` -> `lg:`, sin prop nueva**.

Se descarto la alternativa (una prop `anchor` para que solo esta hoja se anclase abajo) por dos
motivos. Uno, no resuelve nada: con `anchor="bottom"` la hoja se quedaria pegada abajo TAMBIEN a
1440px, y con `anchor="auto"` seguiria promocionando en 768 — las dos opciones estan mal en algun
ancho, y ademas obligaban a `/today` y `/calendar` a pasar valores distintos al mismo componente.
Dos, la incoherencia no es de esta pantalla: la comparten los otros tres consumidores de
`side="bottom"` (§1.4), que a 800px pintan hoy un dialogo centrado sobre una barra de pestanas
movil. Es el mismo defecto en cuatro sitios y la correccion es una sola.

**Efecto colateral querido:** `clients/client-form`, `services/service-form` y
`staff/employee-form` cambian de forma entre 768 y 1023 — de dialogo centrado a hoja inferior.
Ninguna prueba fija esas clases (verificado: `grep` de `md:max-w-lg` y `md:top-1/2` en
`src/**/*.test.tsx` no devuelve nada) y ninguna de las tres tiene artboard a esos anchos. Aun asi,
T3 ejecuta las suites de las tres y adjunta la salida.

**D8 · El velo es el del artboard, y hoy la primitiva NO deja llegar hasta el.** `DetalleCita:34`
dibuja `rgba(42,35,32,0.42)`; `SheetOverlay` pinta `bg-black/10` + `backdrop-blur-xs`.

**Trampa que hay que conocer antes de escribir la llamada:** `SheetContent` renderiza
`<SheetOverlay />` **sin propagarle nada** (`sheet.tsx:51`), y `SheetOverlay` ni siquiera esta
exportado (`:129-138`). El `className` que recibe `SheetContent` va al `Popup` (`:56`), no al
`Backdrop`. Escribir `bg-[rgba(42,35,32,0.42)]` en la llamada pintaria **la HOJA** marron
translucida en lugar del velo — en silencio, y jsdom no lo veria porque no aplica CSS. Por eso T3
anade a `SheetContent` una prop `overlayClassName` que se pasa a `SheetOverlay`, y T8 la usa.

El `blur(1.5px)` del contenido de detras (§1.1) es como el artboard dibuja "hay algo desenfocado
ahi"; el `backdrop-blur-xs` de la primitiva ya lo cubre y **no** se replica con un filtro sobre la
pagina.

**D9 · Cerrar el panel: X y `Escape`. Sin click fuera.** No hay velo (D1), asi que no hay "fuera"
que pulsar; y la rejilla sigue siendo pulsable, de modo que un click fuera cerraria el panel justo
cuando el usuario quiere seleccionar otro bloque. Pulsar OTRO bloque cambia el contenido del panel,
no lo cierra. No hay trampa de foco: no es un dialogo.

**D10 · La seleccion es una prop de la rejilla, no un estado interno.** `DayView` recibe
`selectedAppointmentId?: string | null` y lo baja hasta `AppointmentBlock`, que anade el
`box-shadow` de §1.3. Se aplica en los DOS anchos: en movil la hoja tapa la rejilla y el anillo no
se ve, pero ramificar por ancho para no pintar algo invisible anade una condicion que hay que
mantener a cambio de nada.

**D11 · El empleado lo piden los DOS chasis, no solo el panel.** `Appointment` no trae `jobTitle`
ni `colorHex` (§1.4). Los necesitan los dos: el panel para el avatar de iniciales y el cargo
(`DetalleCitaDesktop:293-299`), y **la hoja para el color del punto** (`DetalleCita:84`, un
`#5C7A5E` solido). Cada uno llama a `useEmployees()` — que vive en `src/hooks/use-staff.ts:11`, no
en `use-appointments.ts` — y busca por `employeeId`. Si no lo encuentra (empleado borrado), pinta
el nombre que trae la cita, sin cargo y con el color de reserva. Ni `/calendar` ni `/today` tienen
que pasarles nada nuevo.

**D12 · La paleta de avatares se EXTRAE, y ahi hacen falta DOS resolutores.** Hoy vive en
`employee-column-header.tsx:22-45`. Con dos consumidores nuevos (tarjeta del panel y punto de la
hoja), copiarla la condena a divergir: el mismo empleado saldria de un color en la cabecera de
columna y de otro en el panel de la misma pantalla. Se mueve a `src/lib/utils/avatar.ts`.

Lo que hay hoy resuelve un FONDO CON ALFA — `bg-chart-N/12` en la paleta de reserva (`:22-28`) y
`{ backgroundColor: colorHex + "20" }` (`:93`), es decir 12% y 12,5%. **El punto de la hoja es
SOLIDO** (`DetalleCita:84`). El modulo expone las dos formas: el fondo con alfa que ya se usaba y
un color pleno para el punto. Reutilizar el de alfa para el punto lo dejaria casi invisible sobre
el fondo claro de la hoja.

**D13 · `/appointments/[id]` se BORRA, y con ella la promesa que le hacia la navegacion.** No tiene
artboard, nadie la enlaza (§1.4) y el detalle vive sobre el calendario. Dejar una ruta llamada
"Detalle de cita" pintando "En desarrollo" cuando el detalle ya existe es peor que no tenerla.

**"Nadie la enlaza" no bastaba como salvoconducto** — el `grep` de enlaces no cubria esto:
`src/lib/nav/app-nav.ts:41-43` enciende "Citas" con `pathname.startsWith("/appointments")`, y
`src/lib/nav/app-nav.test.ts:64` fija `["/appointments/apt_1", "", "Citas"]` como caso soportado.
Borrar la pagina sin tocar eso deja la barra prometiendo que `/appointments/apt_1` es ruta valida
de "Citas" cuando es un 404. T7 borra tambien esa fila del test. La regla `startsWith` se queda
como esta: sigue siendo correcta para `/appointments/new`, la unica ruta de esa familia que
sobrevive.

Si algun dia hace falta enlace profundo (recordatorio por email, notificacion), sera su propia
decision con su propio dibujo.

**D14 · `/today` hereda la hoja nueva sin que se toque `today/page.tsx` — pero SU PRUEBA SI.** La
pantalla de Hoy es el bloque 5; aqui solo cambia el componente que ya consume
(`today/page.tsx:236`), con la MISMA terna de props.

**Lo que si hay que tocar es su suite, y no es opcional.** `today/page.test.tsx:20-22` mockea
`@/hooks/use-staff` con una FACTORIA que sustituye el modulo entero y solo exporta `useServices`:

```ts
vi.mock("@/hooks/use-staff", () => ({
  useServices: (...args: unknown[]) => useServicesMock(...args),
}))
```

En cuanto la hoja llame a `useEmployees()` (D11), en esa suite valdra `undefined` y toda la pantalla
reventara con un `TypeError`. `/calendar` no tiene el problema: su mock si exporta `useEmployees`
(`calendar/page.test.tsx:24-27`). El fichero es de T8, que tiene que anadir el export al mock y
ejecutar esa suite. Es lo correcto en movil: el artboard de este bloque dibuja precisamente la hoja SOBRE Hoy
(§1.1).

Consecuencia de D7, dicha sin rodeos: en `/today` el punto donde la hoja pasa a dialogo centrado se
mueve de 768 a 1024. Entre 768 y 1023 gana — deja de haber un modal centrado sobre chasis movil —;
a partir de 1024 se queda como hoy. **`/calendar` no se ve afectado**: a partir de 1024 monta el
panel y no la hoja (D2, T10). Que `/today` en escritorio siga usando un dialogo es deuda del
bloque 5, donde esa pantalla tiene sus propios artboards. **Deuda anotada, no arreglada aqui.**

**D15 · El relativo de la meta se escribe a mano, abreviado.** El artboard pone
`recibida hace 2 h` (`DetalleCitaDesktop:311`). `date-fns` no da esa forma con locale `es`:
`formatDistanceToNow` devuelve "alrededor de 2 horas" ("hace alrededor de 2 horas" con
`addSuffix`) y `formatDistanceToNowStrict`, "2 horas". Ninguna abrevia, y no hay precedente en el
repo — `formatDistanceToNow` no se usa hoy en ningun sitio.

Asi que T4 escribe un formateador propio, corto y con sus casos cerrados por test: minutos
(`hace 40 min`), horas (`hace 2 h`), dias (`hace 3 d`). Es diez lineas y produce lo dibujado; usar
`formatDistanceToNow` produce otra cosa y el test se limitaria a fijar esa otra cosa. Va en
`lib/utils/dates.ts`, con las otras seis.

**D16 · El detalle abierto se DERIVA por id; no se guarda el objeto.** Hoy `/calendar` captura la
cita al pulsarla (`setSelectedAppointment(appointment)`, `calendar/page.tsx:229-232`) y la hoja se
cierra sola al mutar (`onSuccess: () => onOpenChange(false)`,
`appointment-detail-sheet.tsx:63`), asi que el objeto viejo nunca llega a verse.

**D9 rompe justo eso**: el panel se queda ABIERTO. Y la mutacion optimista de
`use-appointments.ts:103-111` no reescribe el objeto capturado — construye otros nuevos
(`{ ...apt, status }`) dentro de la cache. El resultado seria: pulsas "Confirmar cita", el bloque de
la rejilla pasa a confirmada, y el panel de al lado **sigue diciendo "Pendiente de confirmar" y
sigue ofreciendo "Confirmar cita"**.

Por eso el estado que guarda la pagina es `selectedAppointmentId: string | null` — el MISMO que D10
ya necesita para el anillo de la rejilla, no uno nuevo — y la cita se busca en `dayAppointments` en
cada render. Un solo origen de verdad: si la cita desaparece de la lista del dia (se cancela y se
filtra, se cambia de fecha), el panel se cierra solo, que es lo correcto.

**Consecuencia en las props:** el panel y la hoja siguen recibiendo `appointment: Appointment | null`
— quien deriva es la pagina, no ellos. Lo que cambia es de donde sale.

**D17 · El contrato del modo estrecho se fija AQUI, porque lo escriben dos tareas de la misma
ola.** El estrechamiento (§1.3) toca cuatro ficheros de la rejilla, y `employee-column-header.tsx`
ya era de T2 por la extraccion del avatar. Para que T2 y T6 no colisionen, **cada fichero tiene un
solo dueno**: T2 se queda `employee-column-header.tsx` ENTERO (avatar + modo estrecho) y T6 se
queda `time-grid.tsx`, `appointment-block.tsx` y `day-view.tsx`. Las dos escriben contra este
contrato, que ninguna puede cambiar por su cuenta:

```ts
// La prop viaja hacia abajo, siempre con el mismo nombre y significado:
//   narrow?: boolean   — "hay un panel abierto a la derecha; comprime"
//   Solo tiene efecto en variant="desktop". En movil se ignora.
TimeGrid              { narrow?: boolean }   // 64px -> 58px
EmployeeColumnHeader  { narrow?: boolean }   // gap/padding/tamano/meta (§1.3.4)
AppointmentBlock      { narrow?: boolean, selected?: boolean }  // §1.3.1 y §1.3.2
DayView               { narrow?: boolean, selectedAppointmentId?: string | null }
```

`DayView` (T6) es quien lo reparte a los tres. T2 escribe el suyo y su test sin esperar a T6: la
prop es opcional y por defecto `false`, asi que el componente sigue verde por si solo.

**D18 · Ningun cambio de comportamiento en la barra superior ni en la lateral.** El artboard de
escritorio los dibuja identicos a los del calendario (§1.2) y ya estan construidos. Un
implementador que los toque se ha salido del bloque.

**D19 · El panel se acopla en `lg` aunque a 1024px la rejilla quede apretada, y aqui esta el
numero.** El artboard dibuja el panel a 1440px, donde sobra sitio. A 1024 no lo dibuja nadie, y la
cuenta es esta — barra lateral 248 + canal de horas 58 + panel 360:

Cuenta completa CON panel: `(ancho - 248 lateral - 40 padding - 58 canal - 360 panel - 20 canalones) / 3`.
El padding (20px por lado) y los canalones (10px x2) son los del modo estrecho (§1.3.5), que es el
unico modo en que el panel existe:

| Ancho | Por columna SIN panel | Por columna CON panel |
|---|---|---|
| 1024 | 239px | **~99px** |
| 1280 | 325px | ~184px |
| 1440 | 378px | ~237px (lo dibujado) |

Con mas de tres empleados empeora: cinco a 1024 con panel son ~56px por columna. **No se rompe** —
la plantilla es `repeat(N, minmax(0, 1fr))` (`day-view.tsx:195`), asi que las columnas encogen en
vez de desbordar, y el bloque sigue pintandose con el nombre truncado. Pero a 119px se lee poco.

Se acepta tal cual, por la regla del proyecto: **lo no dibujado no se inventa**. Las alternativas
—un segundo punto de ruptura para acoplar solo desde 1280, o un scroll horizontal de la rejilla con
ancho minimo de columna— serian dos disenos que nadie ha hecho. Queda como limitacion MEDIDA, no
como descuido, y T11 la comprueba tambien a 1024 para que se vea antes de decidir si hay que
dibujar algo.

**D20 · El desbordamiento vertical no esta dibujado, y las dos formas necesitan respuesta.** Los
artboards caben en su marco: el contenido de la hoja mide ~467px en un marco de 844, y el del panel
~610px en los 828 utiles de 900. Pero `notes` es texto libre y el numero de acciones cambia con el
estado, asi que ninguno de los dos margenes esta garantizado en un movil bajo o en un portatil de
768px de alto.

- **Hoja:** conserva el `max-h-[85vh] overflow-y-auto` que ya tiene hoy
  (`appointment-detail-sheet.tsx:85`). No esta dibujado, pero es la unica proteccion existente y
  quitarlo seria una regresion.
- **Panel:** las acciones van con `margin-top:auto` (§1.2), lo que exige que la columna tenga alto
  definido — lo tiene, cuelga del `min-h-0` de la fila de D2. Si el contenido crece, **desborda sin
  scroll** y las acciones se salen por abajo. La franja del medio (de la hora a la meta) lleva
  `min-h-0 overflow-y-auto`; el rotulo de arriba y las acciones de abajo se quedan fijos.

---

## §3 · FICHEROS

**Backend** (`E:\IdeaProjects\rivoo`, modulo `appointment-service`) — unica incursion, y es la de D5:

| Accion | Fichero | Responsabilidad |
|---|---|---|
| Modificar | `.../domain/model/AppointmentStatus.java:22` | abrir `PENDING -> NO_SHOW` |
| Modificar | `.../domain/model/AppointmentStatusTest.java:91-93` | el caso que hoy afirma lo contrario |

**Frontend** (`E:\IdeaProjects\rivoo-frontend`):

| Accion | Fichero | Responsabilidad |
|---|---|---|
| Modificar | `src/app/globals.css` | los dos tokens de §1.5 |
| Crear | `src/lib/utils/avatar.ts` | paleta de reserva + los DOS resolutores de color (D12) |
| Modificar | `src/components/calendar/employee-column-header.tsx` | importar de ahi (D12) **+ modo estrecho** (§1.3.4) — dueno unico: T2 (D17) |
| Modificar | `src/components/calendar/time-grid.tsx` | canal de horas 64 -> 58 (§1.3.3) |
| Modificar | `src/components/ui/sheet.tsx` | punto de promocion `md:` -> `lg:` (D7) + prop `overlayClassName` (D8) |
| Crear | `src/components/appointments/appointment-detail-facts.ts` | derivacion compartida (D3) |
| Modificar | `src/lib/utils/dates.ts` + su test | fecha larga y relativo abreviado (D15) — dueno unico: T4 |
| Modificar | `src/components/appointments/status-badge.tsx` | exportar `statusConfig` (hoy privado) + variante larga — dueno unico: T4 |
| Crear | `src/components/appointments/appointment-actions.tsx` | acciones por estado y ancho (D3, D4, D5) |
| Crear | `src/components/appointments/cancel-appointment-dialog.tsx` | motivo de cancelacion, COMPARTIDO por hoja y panel (T5) |
| Modificar | `src/components/calendar/employee-filter.tsx` + su test | tercer consumidor de la paleta (D12) — dueno unico: T2 |
| Modificar | `src/app/(app)/today/page.test.tsx` | su `vi.mock` de `use-staff` (D14) — dueno unico: T8 |
| Modificar | `src/lib/utils/calendar.ts` + su test | SOLO si el recorte de la meta se resuelve en `employeeDaySummary` (§1.3.4) — dueno unico: T2 |
| Reescribir | `src/components/appointments/appointment-detail-sheet.tsx` | hoja de movil (§1.1) |
| Crear | `src/components/appointments/appointment-detail-panel.tsx` | panel de escritorio (§1.2) |
| Modificar | `src/components/calendar/appointment-block.tsx` | estado seleccionado (§1.3, D10) |
| Modificar | `src/components/calendar/day-view.tsx` | `selectedAppointmentId` + estrechamiento (§1.3) |
| Modificar | `src/app/(app)/calendar/page.tsx` + su test | cableado panel/hoja por ancho (D2); su caso de escritorio (`:915-924`) cambia de significado — dueno unico: T10 |
| Borrar | `src/app/(app)/appointments/[id]/page.tsx` | ruta muerta (D13) |
| Modificar | `src/lib/nav/app-nav.test.ts:64` | quitar el caso `/appointments/apt_1` (D13) |
| Crear | `visual/appointment-detail-vs-artboards.spec.ts` | comparacion visual |
| Modificar | `E:\IdeaProjects\rivoo\tasks\todo.md` | seguimiento (solo T12) |

**Tests que acompanan** — TODOS entran en las rutas del commit de su tarea (§4), no solo el codigo:

| Test | Tarea | Nuevo o ampliado |
|---|---|---|
| `src/lib/utils/avatar.test.ts` | T2 | nuevo |
| `src/components/calendar/employee-column-header.test.tsx` | T2 | ampliado (modo estrecho) |
| `src/components/calendar/employee-filter.test.tsx` | T2 | ampliado (import movido) |
| `src/lib/utils/calendar.test.ts` | T2 | solo si se toca `employeeDaySummary` |
| `src/components/ui/sheet.test.tsx` | T3 | nuevo |
| `src/lib/utils/dates.test.ts` | T4 | ampliado (fecha larga + relativo) |
| `src/components/appointments/appointment-detail-facts.test.ts` | T4 | nuevo |
| `src/components/appointments/appointment-actions.test.tsx` | T5 | nuevo |
| `src/components/appointments/cancel-appointment-dialog.test.tsx` | T5 | nuevo |
| `src/components/calendar/time-grid.test.tsx` | T6 | ampliado (64 -> 58) |
| `src/components/calendar/appointment-block.test.tsx` | T6 | ampliado (anillo + recorte) |
| `src/components/calendar/day-view.test.tsx` | T6 | ampliado (reparto de props) |
| `src/lib/nav/app-nav.test.ts` | T7 | reducido (una fila menos) |
| `src/components/appointments/appointment-detail-sheet.test.tsx` | T8 | nuevo |
| `src/app/(app)/today/page.test.tsx` | T8 | ampliado (mock de `use-staff`) |
| `src/components/appointments/appointment-detail-panel.test.tsx` | T9 | nuevo |
| `src/app/(app)/calendar/page.test.tsx` | T10 | ampliado (panel, estrechamiento, D16) |

---

## §4 · OLAS Y PROTOCOLO

**Matiz sobre la ola 1:** sus rutas son disjuntas, pero **no es del todo libre de dependencias**.
T6 hace que `DayView` reparta `narrow` a `EmployeeColumnHeader`, cuya prop la CREA T2 — es una
dependencia de TIPO, no de fichero. Por eso D17 fija el contrato por escrito: cada una escribe
contra el, la prop es opcional con defecto `false`, y las dos suites pasan por separado. Quien lo
comprueba de verdad es `tsc` en la puerta de cierre de ola, no ninguna de las dos tareas.

```
Ola 1 (paralela, rutas disjuntas)
  T0 dominio         appointment-service (OTRO REPO)
  T1 tokens          globals.css
  T2 avatar          lib/utils/avatar.ts + employee-column-header.tsx
  T3 sheet anchor    ui/sheet.tsx
  T4 facts           appointments/appointment-detail-facts.ts
  T5 acciones        appointments/appointment-actions.tsx
  T6 seleccion       calendar/appointment-block.tsx + calendar/day-view.tsx
  T7 borrar ruta     (app)/appointments/[id]/

Ola 2 (paralela; dependen de 1..5)
  T8 hoja movil      appointments/appointment-detail-sheet.tsx
  T9 panel           appointments/appointment-detail-panel.tsx

Ola 3
  T10 cableado       (app)/calendar/page.tsx

Ola 4
  T11 spec visual    visual/appointment-detail-vs-artboards.spec.ts

Ola 5
  T12 REVISION DE BLOQUE — panel de 3 revisores independientes, en paralelo,
      instruidos para REFUTAR. Lentes: (a) fidelidad al artboard valor a valor,
      (b) correccion y estados/mutaciones, (c) suficiencia de las pruebas
      (mutacion). Se descarta un hallazgo si la mayoria lo refuta.
```

**Protocolo de commit, en las TRECE tareas (T0-T12), sin excepcion:**

```bash
git add <sus rutas>
git commit -o <sus rutas> -m "..."
```

Las dos cosas. `git add` porque `git commit -o` falla sobre ficheros que git aun no conoce y casi
todas las tareas crean ficheros. `-o` porque commitea SOLO esas rutas e ignora el resto del
indice: en una ola de siete agentes sobre el mismo arbol, sin el, el primero que commitea se lleva
el trabajo a medio escribir de los demas. **NUNCA `git add -A`, NUNCA `git commit -m` a secas.**

**Seguridad operativa, a repetir en CADA brief:**
- PROHIBIDO tocar `node_modules`. PROHIBIDO ejecutar `npm ci` — uno previo destruyo
  `node_modules/.bin` devolviendo exit code 0. Si falta algo: `npm install`.
- No cambiar de rama. **Del CODIGO del repo backend `E:\IdeaProjects\rivoo` solo se ocupa T0**, y
  solo los dos ficheros Java de §3. De ese mismo repo, T12 escribe ademas `tasks/todo.md` y
  `tasks/lessons.md`, que no son codigo. Ninguna otra tarea lo toca, y un implementador de
  frontend que edite Java se ha salido de su tarea.
- El backend NO tiene wrapper de Maven: `./mvnw` falla en seco. Usar el `mvn.cmd` de
  `~/.m2/wrapper/dists`, y siempre con `-am`.
- Leer `node_modules/next/dist/docs/` antes de escribir codigo de Next: `AGENTS.md` avisa
  "This is NOT the Next.js you know".
- Cada prueba de ESCRITORIO necesita su `mockMatchMedia(true)` local y su `afterEach`: el polyfill
  de `src/test/setup.ts` devuelve SIEMPRE `matches:false`. Patron en `booking-step-shell.test.tsx:24`.
  Testing Library busca `data-testid`, NO `data-slot`.
- Verificacion con EVIDENCIA: adjuntar la salida real, nunca afirmar "pasa".
- **En la ola 1 NADIE ejecuta la suite entera, ni `tsc --noEmit`, ni `npm run build`.** Son siete
  agentes sobre el MISMO arbol de trabajo: esos tres comandos compilan y ejecutan todo el repo,
  ficheros a medio escribir de los otros seis incluidos, y su resultado no es atribuible a la
  tarea que los lanza — ni un rojo ajeno, ni un verde que solo significa "los demas aun no habian
  guardado". Cada tarea de la ola 1 ejecuta **solo sus propios ficheros de prueba**
  (`npx vitest run <ruta>`). El mismo motivo por el que los commits van con `-o`.
- **Las puertas globales van al cierre de cada ola**, no dentro: al terminar la ola 1, y de nuevo
  al terminar la 2, el orquestador ejecuta `npx vitest run`, `npx tsc --noEmit` y `npm run build`
  y no abre la ola siguiente hasta tenerlos en verde. `npm run build` es ademas lo unico que
  detecta la ruta rota de T7 y el `<Suspense>` que Vitest no ve.

**Revision:** el revisor se lanza AL TERMINAR EL BLOQUE (T12), no por tarea. Cada despacho es un
agente NUEVO; el revisor nunca es el implementador.

---

## §5 · TAREAS

### T0 · Dominio: abrir `PENDING -> NO_SHOW`

**Repo:** `E:\IdeaProjects\rivoo`, modulo `appointment-service`. **Es la unica tarea que toca Java.**

**Ficheros:**
- Modificar: `src/main/java/com/rivoo/appointment/domain/model/AppointmentStatus.java:22`
- Modificar: `src/test/java/com/rivoo/appointment/domain/model/AppointmentStatusTest.java:91-93`

- [ ] Invertir primero el TEST: `PENDING -> NO_SHOW` pasa de `assertFalse` a `assertTrue`, y su
      `@DisplayName` de "is invalid" a "is valid". Ejecutarlo y ver que FALLA.
- [ ] Anadir `|| target == NO_SHOW` a la rama `case PENDING` del `switch` (`:22`). No tocar
      ninguna otra rama: `IN_PROGRESS`, `COMPLETED` y los terminales se quedan igual.
- [ ] Ejecutar la clase de test y ver que PASA. Adjuntar la salida real.
- [ ] Ejecutar la suite del modulo y comprobar que no rompe nada mas — en especial
      `AppointmentRepositoryIntegrationTest`, que tambien menciona `NO_SHOW`, y cualquier prueba de
      `AppointmentService` sobre el cambio de estado (`AppointmentService.java:196` es quien
      consulta esta regla).
- [ ] Comentario de una linea en el `case PENDING` explicando POR QUE: una reserva online a la que
      el cliente no acude es un no-show sin haber pasado nunca por confirmada
      (`design/DetalleCita.dc.html:103-106`).
- [ ] Commit.

### T1 · Tokens

**Ficheros:** modificar `src/app/globals.css`.

- [ ] Anadir en `:root` los dos tokens de §1.5 con su comentario `fichero:linea`, junto a los
      demas tokens del canvas (`:155-166`).
- [ ] Anadir su mapeo en `@theme inline`. Sin el, Tailwind v4 descarta la utilidad en silencio.
- [ ] **No ejecutar la suite entera ni `tsc`** (§4): esta tarea no tiene ficheros de prueba
      propios. Su verificacion es la puerta de cierre de la ola 1.
- [ ] Commit.

### T2 · Avatar compartido + modo estrecho de la cabecera de empleado

**Ficheros:** crear `src/lib/utils/avatar.ts` (+ su test); modificar
`src/components/calendar/employee-column-header.tsx` (+ su test),
`src/components/calendar/employee-filter.tsx`, `src/components/calendar/employee-filter.test.tsx`
y — solo si el recorte de la meta se resuelve ahi — `src/lib/utils/calendar.ts` (+ su test).

**Esta tarea es la DUENA UNICA de esos cuatro ficheros (D17).** Ninguna otra tarea de la ola 1 los
toca.

**`employee-filter` esta en la lista por un motivo que no es evidente:** hay un TERCER consumidor de
la paleta, ademas de la cabecera y del panel. `employee-filter.tsx:7` importa
`employeeFallbackAvatarClassName` **desde `employee-column-header`** (uso en `:73`), y
`employee-filter.test.tsx:5` hace lo mismo (usos en `:142,145,178,200`). Lo dice el propio
comentario del fichero de origen (`employee-column-header.tsx:35-40`: *"pildora de filtro en
movil"*). Mover el simbolo sin arrastrar a estos dos deja `employee-filter.tsx` sin compilar y su
suite en rojo — y como cada tarea de la ola 1 ejecuta solo SUS ficheros de prueba (§4), el rojo no
aparece hasta la puerta de cierre de ola, donde no tiene dueno.

- [ ] Mover `FALLBACK_AVATAR_CLASSNAMES` y `employeeFallbackAvatarClassName` desde
      `employee-column-header.tsx:22-45` al modulo nuevo, **sin cambiar valores ni el algoritmo**
      (indice modulo longitud, con la normalizacion de negativos que ya tiene).
- [ ] Anadir ahi la resolucion de estilo por `colorHex` que hoy esta suelta en `:93`
      (`{ backgroundColor: colorHex + "20", color: colorHex }`) — el fondo CON ALFA.
- [ ] Anadir ademas un resolutor de color **PLENO** para el punto de la hoja de movil (D12): mismo
      criterio de eleccion, sin el sufijo de alfa. Los dos consumidores tienen que caer en el
      mismo color para el mismo empleado.
- [ ] `employee-column-header.tsx` importa de ahi; su comportamiento actual no cambia.
- [ ] `employee-filter.tsx` y `employee-filter.test.tsx` pasan a importar de `avatar.ts` tambien.
      Alternativa aceptable: dejar un re-export en `employee-column-header.tsx`. Lo que NO vale es
      moverlo y no mirar a estos dos.
- [ ] **Modo estrecho** (§1.3.4): prop `narrow?: boolean` segun el contrato de D17. Con ella,
      `gap-2.5 -> gap-[9px]`, `px-3 -> px-2.5`, `text-[14px] -> text-[13px]` y la meta pierde la
      duracion (`4 citas · 5h 30min` -> `4 citas`). Sin ella, todo como hoy.
- [ ] Ese ultimo punto es de DATO, no de estilo: la cadena la produce
      `employeeDaySummary` (`lib/utils/calendar.ts:240`). Decidir ahi o en la cabecera, pero que
      el test lo fije. **Ojo al caso vacio:** con cero citas devuelve `"Sin citas"`, que no lleva
      `·` — recortar por el separador lo deja intacto, que es lo correcto, pero hay que
      comprobarlo, no confiarlo.
- [ ] Tests: mismo color para el mismo indice, ciclo al desbordar, indice negativo, alfa vs pleno;
      y la cabecera con y sin `narrow`.
- [ ] Ejecutar SOLO sus ficheros de prueba (§4), pero los CUATRO: `avatar.test.ts`,
      `employee-column-header.test.tsx`, `employee-filter.test.tsx` y — si se toca
      `employeeDaySummary` — `src/lib/utils/calendar.test.ts`, que tiene siete aserciones vivas
      sobre esa funcion (`:341-394`).
- [ ] Commit.

### T3 · Primitiva `Sheet`: punto de promocion y velo

**Ficheros:** modificar `src/components/ui/sheet.tsx` (+ crear su test).

- [ ] **Promocion en `lg`, no en `md`** (D7): en la cadena de clases del lado `bottom`
      (`sheet.tsx:56`), todas las variantes `data-[side=bottom]:md:*` pasan a
      `data-[side=bottom]:lg:*`. **Son CATORCE**, no nueve: `inset-auto`, `bottom-auto`,
      `left-1/2`, `top-1/2`, `-translate-x-1/2`, `-translate-y-1/2`, `w-full`, `max-w-lg`,
      `rounded-xl`, `border`, y las cuatro de animacion (`data-ending-style:translate-y-0`,
      `data-starting-style:translate-y-0`, `data-ending-style:scale-95`,
      `data-starting-style:scale-95`). No se anade ninguna prop de anclaje. Los lados
      `top`/`left`/`right` no se tocan.
- [ ] **Prop `overlayClassName`** (D8): `SheetContent` la acepta y se la pasa a `SheetOverlay`, que
      hoy se renderiza sin nada (`:51`). Sin esto, el velo del artboard es inalcanzable desde la
      llamada y quien lo intente pintara la hoja en lugar del fondo.
- [ ] Test: que `overlayClassName` llega al `data-slot="sheet-overlay"` y NO al
      `data-slot="sheet-content"`; y que el lado `bottom` lleva las variantes `lg:` y ninguna `md:`.
- [ ] Ejecutar ademas las suites de los tres consumidores ajenos que cambian de forma entre 768 y
      1023 (D7): `clients/client-form`, `services/service-form`, `staff/employee-form`. Adjuntar la
      salida. Si alguna se pone roja, es un hallazgo, no algo que ajustar en el test.
- [ ] Commit.

### T4 · Derivacion de hechos

**Ficheros:** crear `src/components/appointments/appointment-detail-facts.ts` + su test; modificar
`src/lib/utils/dates.ts` + `src/lib/utils/dates.test.ts`, y
`src/components/appointments/status-badge.tsx` + `src/components/appointments/status-badge.test.tsx`.

**Los tres ficheros existentes estan en la lista a proposito, y tienen que estar en las rutas del
commit** (§4: `git commit -o` solo se lleva las rutas que se le nombran; lo que quede fuera no se
commitea y las puertas de cierre no lo notan, porque corren sobre el arbol de trabajo):

- `dates.ts` recibe el formateador de fecha larga y el relativo abreviado (D15). Van con las otras
  seis, no sueltos en `appointment-detail-facts.ts`.
- `status-badge.tsx` porque **`statusConfig` HOY ES PRIVADO**: `status-badge.tsx:4` lo declara
  `const`, sin `export`, y solo se usa dentro del propio fichero (`:37`). Reutilizarlo obliga a
  exportarlo. Al anadir la variante larga de escritorio, **no cambiar ningun rotulo existente**:
  `StatusBadge` lo consumen `appointment-card.tsx` y `src/app/dev/preview/page.tsx`, ademas de
  `status-badge.test.tsx`.

- [ ] Funciones puras que reciben una `Appointment` y devuelven las cadenas exactas de §1.1/§1.2:
      rango horario, `fecha · duracion`, precio, etiqueta de estado **por ancho** ("Pendiente" vs
      "Pendiente de confirmar"), etiqueta de origen, y la meta de cada ancho.
- [ ] Las DOS metas son cadenas distintas, no una con un tramo de mas (§1.2, diferencias 3 y 4):
      movil `Fuente: Reserva online · Recordatorio enviado` — con prefijo y mayuscula inicial;
      escritorio `Reserva online · recibida hace 2 h · recordatorio enviado` — sin prefijo, en
      minusculas y con el relativo de `createdAt` **abreviado** (D15): `formatDistanceToNow` de
      `date-fns` da "hace alrededor de 2 horas", que NO es lo dibujado.
- [ ] El EMAIL del cliente lo pinta la hoja y NO el panel (§1.2, diferencia 5): si el modulo
      devuelve una linea de contacto, que sean dos funciones distintas o el panel acabara
      pintandolo.
- [ ] `reminderSent === false` quita el tramo del recordatorio en las dos.
- [ ] Reutilizar `formatTimeRange`, `formatDuration`, `formatCurrency` (§1.4).
- [ ] **NO usar `formatDate`** (§1.4): da "27 ago 2026" y el artboard pide "Martes, 27 de
      agosto". La fecha larga se produce con
      `capitalizeFirst(format(d, "EEEE, d 'de' MMMM", { locale: es }))`, que es lo que ya hacen
      `calendar/page.tsx:271` y `date-navigator.tsx:20`. Si se decide darle nombre, que viva en
      `lib/utils/dates.ts` junto a las otras seis, no aqui.
- [ ] La etiqueta de estado NO se reinventa: `status-badge.tsx:4-29` ya es la fuente unica de los
      seis rotulos y sus tokens, y su `PENDING.label` es exactamente **"Pendiente"**, el badge
      del artboard movil. Se reutiliza `statusConfig` y solo se anade la variante LARGA de
      escritorio ("Pendiente de confirmar"). Forkear el mapa es el mismo error que D12 evita con
      los colores, y ademas esos rotulos son los sufijos terminales del bloque de rejilla
      ("Completada", "Cancelada").
- [ ] Tests con fecha FIJA (nada de `new Date()` sin congelar): el relativo tiene que ser
      determinista.
- [ ] Commit.

### T5 · Acciones por estado y por ancho

**Ficheros:** crear `src/components/appointments/appointment-actions.tsx` y
`src/components/appointments/cancel-appointment-dialog.tsx`, + sus tests.

**El dialogo de cancelacion es COMPARTIDO, y por eso vive aqui.** Es la unica via para mandar el
`reason` que acepta `useCancelAppointment` (`use-appointments.ts:139-147`), hoy esta empotrado en
la hoja (`appointment-detail-sheet.tsx:176-205`), y en escritorio la hoja NO se monta (T10): si se
quedara dentro de ella, "Cancelar" en el panel no haria nada. Se extrae tal cual esta —`Dialog` +
`Textarea` de motivo opcional + `cancelledBy: "SALON"`— y lo consumen los dos chasis (T8 y T9).

- [ ] Componente que recibe `{ status, variant: "sheet" | "panel", onStatusChange, onCancelRequest,
      onReschedule, isPending }` y pinta el CTA + la fila secundaria con las medidas de §1.1
      (48/46px) o §1.2 (46/40px) segun `variant`.
- [ ] El reparto de los estados NO dibujados es EL DE HOY (§1.4), sin anadir ni quitar
      transiciones (D4).
- [ ] `PENDING` es la excepcion y se pinta como lo dibuja CADA artboard (D5):
      - `variant="sheet"`: CTA "Confirmar cita" + fila secundaria **"No asistio" + "Cancelar"**
        (`DetalleCita.dc.html:98-111`). "No asistio" manda `NO_SHOW`, que T0 hace legal.
      - `variant="panel"`: CTA "Confirmar cita" + fila secundaria **"Reprogramar" + "Cancelar"**
        (`DetalleCitaDesktop.dc.html:315-328`).
      Que los dos anchos ofrezcan acciones distintas sobre el mismo estado es lo DIBUJADO, no una
      incoherencia que haya que unificar.
- [ ] **La matriz completa, cerrada aqui para que nadie la improvise.** "Reprogramar" (D6) solo
      aparece en `PENDING` de panel, que es el unico sitio donde el artboard lo dibuja:

| Estado | `variant="sheet"` (movil) | `variant="panel"` (escritorio) |
|---|---|---|
| `PENDING` | CTA Confirmar · sec: **No asistio** + Cancelar | CTA Confirmar · sec: **Reprogramar** + Cancelar |
| `CONFIRMED` | CTA Iniciar · sec: No asistio + Cancelar | CTA Iniciar · sec: No asistio + Cancelar |
| `IN_PROGRESS` | CTA Completar · **sin secundarias** | CTA Completar · **sin secundarias** |
| `COMPLETED` / `CANCELLED` / `NO_SHOW` | nada | nada |

- [ ] Con UNA sola accion secundaria, en `sheet` el boton ocupa el ancho completo
      (la fila es `flex` con `flex-grow:1`, `DetalleCita:102`) y en `panel` ocupa **una celda** de
      la rejilla de dos, no las dos: `grid-cols-2` con un hijo deja la segunda columna vacia, que
      es el comportamiento natural y el que evita un boton de 320px de ancho.
- [ ] `isPending` deshabilita todo y pone el spinner, como hoy.
- [ ] Tests: un caso por estado y por variante, comprobando ROTULOS y clases de talla.
- [ ] Commit.

### T6 · Modo estrecho y seleccion en la rejilla

**Ficheros:** modificar `src/components/calendar/time-grid.tsx`,
`src/components/calendar/appointment-block.tsx` y `src/components/calendar/day-view.tsx`; ampliar
sus tests.

**NO tocar `employee-column-header.tsx`: es de T2** (D17). Escribir contra el contrato de D17.

- [ ] **Seleccion** (§1.3.1): `AppointmentBlock` recibe `selected?: boolean`. Cuando es cierto, la
      sombra base (`appointment-block.tsx:183`) se **sustituye** por
      `0 0 0 2px var(--primary), 0 6px 14px rgba(42,35,32,0.12)` — con `var(--primary)`, no el hex
      (§1.5). Colocarla DESPUES en el `cn` para que tailwind-merge se quede con ella, y
      COMPROBARLO en el test en vez de suponerlo.
- [ ] **Recorte de la tercera linea** (§1.3.2): con `narrow` y SIN `selected`, el bloque pinta solo
      el rango horario y pierde el sufijo (`· 35,00 €`, `· Completada`, `· Cancelada`). Con
      `narrow` y `selected`, lo conserva. Sin `narrow`, todo como hoy.
- [ ] **Canal de horas** (§1.3.3): `time-grid.tsx:20` pasa de `desktop: { width: 64 }` a 58 cuando
      `narrow`. El ancho vive AHI, no en `day-view.tsx` — que lo documenta en `:129`.
- [ ] **Marco** (§1.3.5): en `DayView`, `px-6 -> px-5` y `gap-x-3 -> gap-x-2.5`. Afecta a las DOS
      filas porque comparten rejilla CSS.
- [ ] `DayView` recibe `narrow?: boolean` y `selectedAppointmentId?: string | null`, y los reparte a
      los tres componentes de abajo, cabecera incluida (la prop de T2).
- [ ] Tests: el seleccionado lleva el anillo y el resto no; el seleccionado conserva su sufijo y
      los demas lo pierden; el canal mide 58; las dos clases del marco cambian; `narrow` no tiene
      efecto en movil.
- [ ] Ejecutar SOLO sus ficheros de prueba (§4).
- [ ] Commit.

### T7 · Borrar la ruta muerta

**Ficheros:** borrar `src/app/(app)/appointments/[id]/page.tsx` (y su carpeta si queda vacia);
modificar `src/lib/nav/app-nav.test.ts:64`.

- [ ] Confirmar por `grep` que nadie la enlaza ANTES de borrar (§1.4) — con VARIOS patrones, no
      solo `href=`: `router.push`, `redirect(`, plantillas con `${`, y la cadena
      `/appointments/` a secas.
- [ ] Quitar la fila `["/appointments/apt_1", "", "Citas"]` de `app-nav.test.ts:64` (D13). **No**
      tocar la regla `startsWith("/appointments")` de `app-nav.ts:41-43`: sigue siendo correcta
      para `/appointments/new`.
- [ ] Ejecutar SOLO `app-nav.test.ts` (§4). `npm run build` es la puerta de cierre de la ola, no
      de esta tarea — y es lo unico que detectaria una ruta rota.
- [ ] Commit con el motivo (D13) en el mensaje.

### T8 · Hoja de movil

**Ficheros:** reescribir `src/components/appointments/appointment-detail-sheet.tsx`; crear su test;
modificar `src/app/(app)/today/page.test.tsx`.

**`today/page.test.tsx` esta aqui por D14**, no por capricho: su `vi.mock` de `@/hooks/use-staff`
solo exporta `useServices`, asi que en cuanto esta hoja llame a `useEmployees()` esa suite entera
revienta. Anadir el export al mock y ejecutarla forma parte de esta tarea.

**Es una REESCRITURA, no un retoque.** Lo que hay hoy no coincide con el artboard en ninguna
medida: no tiene asa, ni velo al 42%, ni las alturas 48/46, ni la fila secundaria de dos, ni el
punto de color del empleado. Partir de cero contra §1.1 sale mejor que ir corrigiendo lo viejo.

- [ ] Chasis y valores de §1.1, uno a uno. Asa, sin X (`showCloseButton={false}`).
- [ ] Conservar el `max-h-[85vh] overflow-y-auto` que ya tiene hoy (`:85`). No esta dibujado, pero
      es la unica proteccion contra una nota larga en un movil bajo (D20); quitarlo es regresion.
- [ ] Velo del artboard por **`overlayClassName`** (T3, D8) — `className` a secas pintaria la hoja,
      no el fondo. No hay prop de anclaje: T3 lo resuelve en la primitiva (D7).
- [ ] Pinta el EMAIL del cliente junto al telefono (`:66-69`). Es lo que la distingue del panel,
      que no lo lleva (§1.2, diferencia 5).
- [ ] Usa `appointment-detail-facts` (T4) y `AppointmentActions variant="sheet"` (T5).
- [ ] Conserva la terna de props `{appointment, open, onOpenChange}` — `/today` depende de ella
      (D14).
- [ ] Monta el `CancelAppointmentDialog` de T5 — ya no vive dentro de este fichero.
- [ ] Fila de empleado: PUNTO de color, no icono (§1.1). Color **PLENO** desde `avatar.ts` (T2,
      D12) — el resolutor con alfa lo dejaria invisible. El empleado sale de `useEmployees()`
      (`hooks/use-staff.ts:11`), igual que en el panel (D11).
- [ ] Cuidado con el orden `text-[Npx] leading-[N]` en cada `cn` (§1.5).
- [ ] Tests con `mockMatchMedia(false)`: rotulos, badge "Pendiente", presencia del asa, ausencia de
      X, y que "Confirmar" dispara la mutacion.
- [ ] Commit.

### T9 · Panel de escritorio

**Ficheros:** crear `src/components/appointments/appointment-detail-panel.tsx` + su test.

- [ ] Columna de 360px con los valores de §1.2, tarjeta a tarjeta.
- [ ] `useEmployees()` (`src/hooks/use-staff.ts:11`) para `jobTitle` y `colorHex`; degradacion si
      el empleado no aparece (D11).
- [ ] Monta el `CancelAppointmentDialog` de T5. **Sin el, "Cancelar" en escritorio no hace nada**:
      la hoja, que es donde vivia, no se monta a partir de 1024 (T10).
- [ ] **NO pinta el email del cliente** (§1.2, diferencia 5): la tarjeta lleva nombre, telefono y
      los dos botones de contacto, y nada mas.
- [ ] Avatar 36px desde `avatar.ts` (T2, D12).
- [ ] Acciones al fondo con `mt-auto` (§1.2) y `AppointmentActions variant="panel"` (T5).
- [ ] Franja del medio (de la hora a la meta) con `min-h-0 overflow-y-auto` (D20): con `mt-auto` y
      sin scroll, una nota larga empuja las acciones fuera del panel.
- [ ] Cierre por X y por `Escape`; sin trampa de foco ni click-fuera (D9). La firma es
      `{ appointment: Appointment | null; onClose: () => void }` — T10 pasa el cierre desde la
      pagina, que es quien tiene el estado (D16).
- [ ] **"Reprogramar" navega, y esta es la URL (D6)** — el artboard lo dibuja SOLO aqui
      (`DetalleCitaDesktop.dc.html:320-323`), asi que si esta tarea no lo cablea no lo cablea
      nadie y el boton queda muerto, que es justo lo que D6 prohibe. Con `useRouter()` de
      `next/navigation`:
      `/appointments/new?rescheduleId=<id>&date=<yyyy-MM-dd>&time=<HH:mm>&employeeId=<id>`,
      con `date` y `time` sacados de `startTime` (ISO local: caracteres 0-10 y 11-16). Limitacion
      conocida y ya documentada en `calendar/page.tsx:238-252`: el asistente todavia no lee esos
      parametros.
- [ ] El test necesita mock de `next/navigation`; el del repo es INERTE, asi que hay que afirmar
      sobre el `push`, no confiar en la navegacion.
- [ ] Botones de llamar y mensaje: `tel:` y `sms:` sobre `clientPhone`; ocultos si no hay telefono.
- [ ] Nota en recuadro de aviso; si `notes` es `null`, el recuadro no se pinta.
- [ ] Tests con `mockMatchMedia(true)`: badge "Pendiente de confirmar", meta con relativo, avatar
      con iniciales, `Escape` cierra, sin telefono no hay botones de contacto.
- [ ] Commit.

### T10 · Cableado en `/calendar`

**Ficheros:** modificar `src/app/(app)/calendar/page.tsx` y `src/app/(app)/calendar/page.test.tsx`.

**Su suite ya tiene un caso que este cambio resignifica:** `calendar/page.test.tsx:915-924`
(*"pulsar un bloque abre el detalle de esa cita"*) corre con `mockMatchMedia(true)` — escritorio —
y afirma `getByText("Detalle de cita")` mas dos apariciones de "Ana Garcia". A partir de T10 ese
caso deja de probar la hoja y prueba el PANEL. Puede seguir pasando por casualidad (el panel tambien
lleva el rotulo "Detalle de cita", §1.2); hay que revisarlo a proposito, no dejar que pase solo.

- [ ] En escritorio, envolver **el ternario COMPLETO** (`calendar/page.tsx:330-359`) + el panel en
      la fila de D2 (`flex min-h-0 flex-1`), con el contenido a `flex-1 min-w-0` y el panel a
      `w-[360px] shrink-0`. **No solo `DayView`**: la otra rama del ternario es el esqueleto de
      carga (`:341`, `min-h-0 flex-1 overflow-y-auto`), y si la fila envolviera solo la rama
      cargada, el panel desapareceria y volveria en cada recarga — que es justo lo que pasa tras
      cada mutacion, porque `onSettled` invalida `["appointments"]`
      (`use-appointments.ts:128-130`). La rama de carga pasa a `min-w-0 flex-1` dentro de la fila.
      En movil, el arbol se queda como esta.
- [ ] **El estado pasa a ser `selectedAppointmentId`, no el objeto** (D16). Sustituye a
      `selectedAppointment` (`:78`) y `handleAppointmentTap` (`:229-232`) guarda el id. La cita se
      deriva de `dayAppointments` por id en cada render; si ya no esta, el panel se cierra.
- [ ] Test que fija D16: con el panel abierto, confirmar la cita y comprobar que el panel pasa a
      "Confirmada" **sin cerrarse**. Es el test que separa derivar de capturar; con el objeto
      capturado, pasa a rojo.
- [ ] `narrow={isDesktop && panelOpen}` a `DayView`; `selectedAppointmentId` siempre.
- [ ] Escritorio monta el panel; movil monta la hoja. **No los dos**: montar ambos y esconder uno
      con CSS deja dos arboles en jsdom y rompe `getByRole` por ambiguedad (el mismo motivo que
      documenta `page-shell.tsx:101-103`).
- [ ] Sustituir el comentario de `:361-365`, que ya anuncia este cambio, por la descripcion de lo
      que quede.
- [ ] Pulsar otro bloque cambia el contenido del panel sin cerrarlo (D9).
- [ ] Test de la pagina: en escritorio, pulsar un bloque abre el panel y estrecha la rejilla;
      cerrar lo devuelve. En movil, abre la hoja.
- [ ] Commit.

### T11 · Comparacion visual

**Ficheros:** crear `visual/appointment-detail-vs-artboards.spec.ts`.

- [ ] Mismo patron que `visual/calendar-vs-artboards.spec.ts`: credenciales por
      `RIVOO_E2E_EMAIL` / `RIVOO_E2E_PASSWORD`, **nunca en el repo**.
- [ ] Dos anchos contra sus artboards: 390 (hoja) y 1440 (panel).
- [ ] Y una TERCERA captura a **1024 con el panel abierto**, que no tiene artboard contra el que
      comparar: es la comprobacion de D19 (columnas de 119px). No falla el test — se guarda la
      imagen para poder decidir con ella delante si hay que dibujar algo para ese ancho.
- [ ] Normalizar el espacio duro de `formatCurrency` si el spec compara texto (§1.4).
- [ ] El spec se escribe y se commitea; EJECUTARLO requiere las credenciales del usuario y queda
      fuera de la ejecucion automatica.
- [ ] Commit.

### T12 · Revision de bloque

**Ficheros:** solo lectura sobre `rivoo-frontend`; escritura en `E:\IdeaProjects\rivoo\tasks\todo.md`
y `tasks/lessons.md`.

- [ ] Tres revisores independientes EN PARALELO, agentes nuevos, ninguno implementador, con las
      lentes de §4 e instruidos para REFUTAR.
- [ ] Ejecutar `npx vitest run`, `npx tsc --noEmit` y `npm run build`, y adjuntar la salida real.
- [ ] Sondeo por MUTACION en los tests nuevos: romper a proposito un valor de §1.1/§1.2 y
      comprobar que alguna prueba se pone roja. Una prueba que sobrevive a la mutacion no cubre lo
      que dice cubrir.
- [ ] Volcar hallazgos y deudas en `tasks/todo.md` — incluidas las de D14 (`/today` en escritorio),
      D19 (panel a 1024px) y D5 (el desequilibrio de "No asistio" entre anchos). Correcciones del
      usuario, en `tasks/lessons.md`.
- [ ] Commit de los dos ficheros del repo backend con el protocolo de §4.

---

## Execution Order

**Backend (`rivoo`, `appointment-service`):**

```
T0  abrir PENDING -> NO_SHOW    (sin dependencias; paralelo a toda la ola 1 del frontend)
```

**Frontend (`rivoo-frontend`):**

```
Ola 1  T0 dominio       ┐ (repo backend)
       T1 tokens        │
       T2 avatar        │
       T3 sheet anchor  │ sin dependencias entre si,
       T4 facts         │ rutas disjuntas -> en paralelo
       T5 acciones      │
       T6 seleccion     │
       T7 borrar ruta   ┘
Ola 2  T8 hoja movil    ┐ dependen de T1..T5;
       T9 panel         ┘ rutas disjuntas -> en paralelo
Ola 3  T10 cableado                depende de T6, T8, T9
Ola 4  T11 spec visual             depende de T10
Ola 5  T12 revision de bloque      depende de todo
```

**Coordinacion:** T0 vive en el repo backend y no colisiona con nada del frontend, asi que corre
en la misma ola. El contrato HTTP no cambia — `PUT /api/v1/appointments/{id}/status` ya acepta
cualquier `AppointmentStatus`; lo que T0 mueve es la regla de dominio que lo valida
(`AppointmentService.java:196`). Por eso T5 puede escribirse contra el boton sin esperar a T0: si
T0 no estuviera, el boton existiria y el servidor devolveria 4xx. La verificacion final es la ola 5.

---

## Dependencies on other specs/FRs

| Spec | Relacion | Implicacion |
|---|---|---|
| **shell-escritorio** (bloque 2) | **Prerrequisito, cumplido** | El panel se apoya en el chasis de escritorio y en `PageShell`; su decision de que el chasis movil llega hasta 1023px es la que justifica D7 |
| **calendario** (bloque 3) | **Prerrequisito, cumplido** | La rejilla, `DayView` y `AppointmentBlock` existen; este bloque les anade seleccion y estrechamiento (§1.3) |
| **Hoy** (bloque 5) | **Consumidor** | Hereda la hoja reescrita. La deuda de D14 (dialogo centrado en escritorio) se resuelve alli |
| **Asistente de nueva cita** | **Consumidor** | Recogera los parametros que D6 deja en la URL; hasta entonces la limitacion esta documentada |
| **Equipo** (bloque 6) | **Complementario** | Comparte `avatar.ts` (D12) y `jobTitle`; coordinar si se tocan a la vez |
| **appointment-service** (dominio) | **Prerrequisito, se abre aqui** | T0 abre `PENDING -> NO_SHOW`. Sin el, el "No asistio" que dibuja el artboard movil es un 4xx (D5) |
