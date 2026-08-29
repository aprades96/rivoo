# Bloque 3 — Calendario. Plan de implementacion

> **Para agentes:** ejecutar con `executing-plans`. Los pasos llevan casilla (`- [ ]`).

**Objetivo:** dejar `/calendar` calcada a `design/Calendario.dc.html` (390px) y
`design/CalendarioDesktop.dc.html` (1440px), en los dos anchos.

**Arquitectura:** la rejilla de dia pasa de una columna fija a N columnas.
En movil N=1 con filtro de empleado por pildoras; en escritorio N = numero de
empleados activos, una columna por empleado y sin filtro. Los dos anchos
comparten primitivas (rejilla horaria, bloque de cita, bloque de descanso) y
se diferencian por tamanos y por que se monta, nunca por CSS oculto.

**Stack:** Next.js 16 App Router · TypeScript · Tailwind v4 · Shadcn/UI +
@base-ui/react · React Query v5 · Vitest 4 · Playwright (visual).

**Complejidad:** compleja (6+ ficheros, transversal). Motor: `executing-plans`.

**Repo unico:** `E:\IdeaProjects\rivoo-frontend`. El backend
`E:\IdeaProjects\rivoo` NO se toca; unica excepcion, escribir en
`tasks\todo.md`.

---

## 1. Datos verificados

Cada dato esta en UN solo sitio. Las tareas de la §5 REFERENCIAN esta seccion
en vez de repetir valores. (Las cinco versiones del plan del bloque 2 fallaron
por duplicar y divergir.)

### 1.1 Chasis de escritorio — `CalendarioDesktop.dc.html`

| Que | Donde | Valores |
|---|---|---|
| Barra superior | `:74` | h72 · `padding: 0 24px` · `border-bottom: 1px solid #E7DCCF` |
| Titulo | `:76` | la FECHA, `.display` 26px, `letter-spacing: -0.015em` |
| Navegador de fecha | `:77-85` | pegado al titulo, gap 16px del titulo, gap 6px entre botones · prev/next 34x34 radius 8 borde `#E7DCCF` fondo blanco icono 16px · "Hoy" h34 `padding: 0 14px` 13px/600 |
| Segmentado Dia/Semana | `:89-92` | contenedor `padding:3px` radius 9 borde `#E7DCCF` fondo `#F5EEE6` · opcion activa h30 `padding:0 14px` radius 6 fondo blanco 13px/600 sombra `0 1px 2px rgba(42,35,32,.06)` · inactiva 13px `#7A6A5F` |
| Boton buscar | `:93-95` | 38x38 radius 8 borde `#E7DCCF` fondo blanco · icono 17px `#7A6A5F` |
| CTA "Nueva cita" | `:96-99` | h38 `padding: 0 16px` radius 8 fondo `#B4522F` blanco 14px/600 · icono 17px · gap 8px |
| Fila de empleados | `:103-128` | canal de horas 64px vacio + `grid-template-columns: repeat(N, minmax(0,1fr))` gap 12px · tarjeta h60 `padding: 0 12px` gap 10px · avatar 30px circulo 11px/700 · nombre 14px/600 · resumen 11px `#9A8A7E` con el texto `4 citas · 5h 30min` |
| Rejilla | `:130` | `padding: 0 24px` · ocupa el alto restante y hace scroll interno (el artboard la recorta a 720px) |
| Canal de horas | `:132-148` | 64px · etiqueta 11px `#9A8A7E` en `position:absolute; top:-8px` sobre la fila de la hora en punto |
| Filas | `:20-21` | `.slot` (media hora) h48 `border-top: 1px solid #EFE6DA` · `.slot-hour` (hora en punto) h48 `border-top: 1px solid #E2D6C6` |
| Columna | `:152` | `position:relative; border-left: 1px solid #EFE6DA` |
| Bloque de cita | `:22` | `left:6px; right:6px` · `padding: 8px 10px` · radius 8 · fondo blanco · borde `#E7DCCF` · sombra `0 1px 2px rgba(42,35,32,.05)` · columna gap 2px · `overflow:hidden` |
| Canalon entre bloques | `:162,:168,:193,:204` y movil `:97,:103,:112,:116` | el alto pintado es **la duracion menos 4px**: 92 para 60 min, 140 para 90, 68 para 45, 44 para 30. Es lo que separa `:225` (acaba en 380) de `:230` (empieza en 384). Sin restarlo, dos citas seguidas se tocan |
| Bloque corto | `:204, :220, :225` | citas de **30 min o menos**: `padding: 6px 10px` y SOLO dos lineas, nombre + hora. El umbral va por DURACION, no por pixeles: 30 min son exactamente 48px con `SLOT_HEIGHT_PX`, asi que un `height < 48` no se dispara jamas |

Contenido del bloque de cita en escritorio (`:162-166`): nombre 13px/600 ·
servicio 12px `#7A6A5F` · `09:00 - 10:00 · 35,00 €` 11px `#9A8A7E` tabulares.

### 1.2 Estados del bloque de cita (los dos anchos)

| Estado | Donde | Borde izq. 3px | Borde | Fondo | Texto extra |
|---|---|---|---|---|---|
| Confirmada | `CalendarioDesktop:162` | `#3F6B4F` | `#E7DCCF` | blanco | — |
| Pendiente | `:168-175` | `#C08A2E` | `#E8D3A6` | `#FFFCF5` | insignia "Pendiente": `padding:2px 7px` radius 999 fondo `#FAEFD6` color `#8A5B12` 9px/700, arriba a la derecha, `white-space:nowrap` |
| Completada | `:193-196` | `#9A8A7E` | `#E7DCCF` | blanco + `opacity:.7` | **siempre dos lineas**, sin servicio ni precio: nombre + `08:00 - 08:45 · Completada`. El artboard lo dibuja a 68px, donde cabrian tres — o sea que la regla es del estado, no del alto |
| Cancelada | `:225-228` | `#A34434` | `#EDD6D0` | `#FDF6F4` | **siempre dos lineas**, sin precio: nombre y `11:30 - 12:00 · Cancelada`, los dos en `#A34434` |
| Descanso | `:177-180` | sin borde izq. | `#E2D6C6` | `repeating-linear-gradient(135deg,#F5EEE6,#F5EEE6 6px,#EFE6DA 6px,#EFE6DA 12px)` | titulo 12px/600 `#7A6A5F` + rango 11px `#9A8A7E` |

`IN_PROGRESS` y `NO_SHOW` no estan dibujados. Se resuelven por analogia
declarada: `IN_PROGRESS` como Confirmada, `NO_SHOW` como Cancelada con su
etiqueta ("No asistio"). Queda anotado como supuesto, no como dato del canvas.

### 1.3 Chasis movil — `Calendario.dc.html`

| Que | Donde | Valores |
|---|---|---|
| Cabecera | `:25-35` | h56 `padding: 0 16px` · titulo **"Citas"** `.display` 21px `-0.01em` · dos botones 36x36 radius 8 borde `#E7DCCF` fondo blanco, iconos 17px `#7A6A5F`: buscar y conmutador de agenda |
| Fila de fecha | `:37-48` | `padding: 12px 16px` · `border-bottom: 1px solid #EFE6DA` · prev/next 36x36 (mismo estilo) · centro en columna gap 1px: fecha `.display` 19px `line-height:1.1` + `HOY` 11px/600 `letter-spacing:.06em` MAYUSCULAS `#B4522F`. **`HOY` es un indicador pasivo, no un control**: el boton "Hoy" solo existe en escritorio (§1.1) |
| Pildoras de empleado | `:50-63` | fila gap 6px `padding: 12px 16px` · pildora h34 radius 999 · "Todos" `padding: 0 14px` 12px/500 borde `#E7DCCF` fondo blanco · empleado `padding: 0 12px 0 5px` gap 7px con avatar 24px 9px/700 · SELECCIONADA: borde y fondo `#B4522F`, texto blanco 12px/600, avatar sobre `rgba(255,255,255,.22)` |
| Rejilla | `:66` | `padding: 0 12px` · canal de horas 46px · etiquetas 10px `top:-7px` · `gap: 0` entre canal y columna |
| Columna | `:83` | `position:relative; flex-grow:1` — **sin `border-left`**, al contrario que la de escritorio (§1.1). Es la unica diferencia estructural entre las dos |
| Bloque de cita | `:97-101` | `left:4px; right:4px` · `padding: 8px 10px` · columna gap 3px · nombre 13px/600 `line-height:1.2` · `Corte y secado · 09:00` 11px `#7A6A5F` · `60min · 35,00 €` 11px `#7A6A5F` tabulares |
| Hueco libre | `:112-114` | h44 `padding: 6px 10px` radius 8 · `border: 1px dashed #D8C9B8` · fondo `#F5EEE6` · texto `Libre · toca para crear` 11px `#9A8A7E` |
| Boton flotante | `:123-125` | ya existe (`FAB_ROUTES` incluye `/calendar`); no se toca |

El bloque de cita movil escribe la hora DENTRO de la linea del servicio
(`Corte y secado · 09:00`) y la duracion en la tercera (`60min · 35,00 €`).
Escritorio escribe el rango completo (`09:00 - 10:00 · 35,00 €`). Son formatos
distintos a proposito.

### 1.4 Estado del codigo

- `src/app/(app)/calendar/page.tsx` (174 lineas): `PageShell` con la fecha como
  titulo en los dos anchos (formato corto por debajo de 1024, `:65`), navegador
  de escritorio en `titleAdjacent` (`:74-102`), CTA `Nueva cita` en `actions`,
  buscar + conmutador en `mobileActions` **solo presentacionales** (`:114-128`),
  y en el cuerpo `DateNavigator` + `EmployeeFilter` + `DayView`.
- **La fecha sale dos veces** (titulo y `DateNavigator`): deuda declarada del
  bloque 2 en `:131-135`.
- `page.tsx:46` **descarta las citas canceladas**. El artboard las dibuja.
- `src/components/calendar/day-view.tsx`: una sola columna;
  `ScrollArea className="h-[calc(100vh-16rem)]"` (`:21`) es el numero magico
  que el bloque 2 dejo anotado.
- `time-grid.tsx`: canal `w-12` (48px), lineas `border-dashed border-muted`
  iguales para hora y media hora, etiqueta cada dos filas con `-mt-2`.
- `appointment-block.tsx`: colores de estado con clases crudas de Tailwind
  (`border-l-yellow-500`...), sin insignia, sin precio, umbral `pos.height > 36`.
- `employee-filter.tsx`: la pildora "Todos" es `px-3 py-1.5` (`:21`), las de
  empleado `px-2 py-1` (`:36`), avatar 20px (`:42`); todas `text-xs`.
- `date-navigator.tsx`: dos `Button ghost icon-sm` y un `<button>` pelado en medio.
- `src/lib/utils/calendar.ts`: `GRID_START_HOUR=8`, `GRID_END_HOUR=21`,
  `SLOT_MINUTES=30`, `SLOT_HEIGHT_PX=48`, `generateTimeLabels()` (26 etiquetas,
  una por media hora), `calculateBlockPosition(start,end)` -> `{top,height}`
  recortado a la rejilla, alto minimo medio hueco.
- `lib/utils/calendar.test.ts` **si existe** (100 lineas, del commit `a34c157`), y
  tres de sus aserciones fijan el alto de bloque SIN el canalon de 4px: hay que
  actualizarlas, no borrarlas. Lo que NO tiene test es la pagina ni ninguno de
  los cinco componentes de `components/calendar/`.
- Nadie fuera de `/calendar` importa `components/calendar` ni `utils/calendar`
  (comprobado con grep). El radio de impacto es la pantalla.
- `PageShell` (`src/components/layout/page-shell.tsx`) pinta el contenido con
  `px-7 py-6` y capa interna `max-w-[1084px]` + `gap-[18px]` en escritorio, y
  `mx-auto max-w-3xl p-4 md:py-6` en movil. `contentClassName` sustituye el
  espaciado de la capa interna, **nunca el padding exterior** (`:50-51`).
  Su barra superior de escritorio fija `pr-7` + `pl-7` = 28px (`:174-175`).
- **La cadena de alturas de movil esta rota en dos eslabones.** (a)
  `src/app/(app)/layout.tsx:46-54`: la rama movil de `<main>` es
  `w-full flex-1 pb-20`, que NO es `display:flex`, asi que el
  `flex flex-1 flex-col` de `page-shell.tsx:133` es hijo de bloque y su
  `flex-1` es inerte. (b) `page-shell.tsx:146`: la capa interna movil es
  `cn(mobileContentClassName)` a secas, sin `flex`, `flex-1` ni `min-h-0`.
  En escritorio la cadena si llega (`layout.tsx:41` `min-h-dvh` -> `:46-49`
  -> `page-shell.tsx:89`), aunque `:89` tampoco lleva `min-h-0`.
- `calendar.ts:48` fuerza un alto minimo de `SLOT_HEIGHT_PX / 2` = 24px.
- `getTodayBusinessHours` esta tipada sobre `BusinessHoursResponse`
  (`types/salon.ts`), no sobre `WorkingHoursResponse` (`types/employee.ts`).
  Son estructuralmente identicos: compila. **No "arreglar" el tipo a mitad de ola.**
- Los 28px de la barra superior son la norma: los 15 artboards de escritorio del
  bloque 2 usan 28. Solo `CalendarioDesktop:74` y `DetalleCitaDesktop:79`
  (bloque 4) usan 24 — la familia de la rejilla es la excepcion.
- `Button` `size="action"` = `h-[38px] gap-1.5 px-[18px] text-sm font-semibold`;
  `size="icon-lg"` = `size-9` (36px, el de la cabecera movil).
- `Employee` tiene `colorHex: string | null`. `Appointment` tiene
  `employeeId`, `employeeName`, `serviceName`, `servicePrice`,
  `serviceDurationMinutes`, `startTime`, `endTime`, `status`.
- `staffApi.getWorkingHours(id, token)` ->
  `GET /api/v1/staff/employees/{id}/working-hours` -> `WorkingHoursResponse[]`
  con `dayOfWeek` (lunes=1), `isOpen`, `openTime`, `closeTime`,
  `breakStartTime`, `breakEndTime`. Ya lo consume `staff/[id]/page.tsx`.
  `getTodayBusinessHours(hours, date)` (`lib/utils/business-hours.ts:82`)
  resuelve la fila del dia; `formatTimeOfDay` (`:103`) recorta `09:00:00`.

### 1.5 Tokens

Ya existen y coinciden exactamente con el canvas: `--border` `#e7dccf` ·
`--hairline` `#efe6da` · `--muted-foreground` `#7a6a5f` ·
`--muted-foreground-2` `#9a8a7e` · `--success` `#3f6b4f` ·
`--warning-border` `#e8d3a6` · `--destructive` `#a34434` ·
`--destructive-border` `#edd6d0` · `--muted`/`--secondary` `#f5eee6` ·
`--accent` `#f6e7e0` · `--primary` `#b4522f` · `--chart-1..5` (colores de
empleado) · y los seis pares `--color-status-*-bg` / `-text`, entre ellos
`#faefd6` / `#8a5b12`, que SON los de la insignia "Pendiente" de la §1.2.

**Faltan cinco.** Un `var()` no definido no da error: la declaracion se
descarta en silencio y la pantalla sale mal sin que nada falle.

| Token | Valor | Para que |
|---|---|---|
| `--hairline-strong` | `#e2d6c6` | linea de la hora en punto |
| `--warning` | `#c08a2e` | borde izquierdo de Pendiente |
| `--warning-soft` | `#fffcf5` | fondo de Pendiente |
| `--destructive-tint` | `#fdf6f4` | fondo de Cancelada |
| `--border-dashed` | `#d8c9b8` | borde del hueco libre |

---

## 2. Decisiones

**D1 — El titulo es distinto por ancho.** Movil "Citas" (§1.3), escritorio la
fecha (§1.1). Lo resuelve la propia pagina con su `useMediaQuery`, que ya usa
para elegir formato; `PageShell` no se toca. Consecuencia directa: desaparece
el formato corto `EEE, d MMM` (§1.4) porque en movil el titulo ya no es la
fecha, y desaparece la fecha duplicada, porque en escritorio la fecha vive solo
en el titulo y en movil solo en su fila.

**D2 — Escritorio: una columna por empleado activo; sin filtro.** El filtro de
pildoras no esta dibujado en escritorio y las columnas ocupan su funcion. En
escritorio la consulta NO lleva `employeeId` y las citas se agrupan en cliente
por `employeeId`. En movil se mantiene el filtro y la consulta sigue llevando
`employeeId`.

**D3 — Las canceladas y las completadas se pintan.** Se elimina el filtro de
`page.tsx:46`. El artboard las dibuja (§1.2) y ocultarlas deja huecos falsos.

**D4 — `PageShell` gana una prop `layout?: "default" | "fill"`.** `fill` quita
el padding exterior del contenido, hace que la capa de contenido sea
`flex-1 min-h-0`, y **baja el padding horizontal de la barra superior de 28 a
24px** (§1.1 frente a §1.4: la familia de la rejilla es la unica que usa 24, y
si la cabecera se queda en 28 el titulo y el CTA salen desalineados 4px del
canal de horas que va debajo). Es lo que pide el artboard: la fila de empleados
pegada a la barra superior con `padding: 0 24px` propio, y la rejilla ocupando
el alto restante con scroll interno. Con esto muere el `h-[calc(100vh-16rem)]`
de `day-view.tsx:21` sin sustituirlo por otro numero magico. `default` deja
todas las demas pantallas exactamente como estan. En movil `fill` conserva
`mx-auto max-w-3xl` y solo quita el padding: cada franja (fecha, pildoras,
rejilla) trae el suyo, que es lo que dibuja §1.3.

**La prop no basta por si sola.** §1.4 documenta que en movil la cadena de
alturas esta rota en dos eslabones anteriores a `PageShell`. `fill` no puede
funcionar sin arreglar los dos: `src/app/(app)/layout.tsx` (rama movil de
`<main>`) y la capa interna de `page-shell.tsx:146`. Los dos entran en T3. Si
solo se toca la prop, en movil la rejilla se pinta a su alto natural (26 x 48 =
1248px), no hay scroll interno, y la cabecera se va con el scroll de pagina —
exactamente lo contrario de lo que dibuja `Calendario.dc.html:66`.

**D5 — El descanso es por empleado, no del salon.** El artboard lo pinta solo
en la columna de Laura (`:177`), no en las tres. La fuente es
`working-hours` por empleado (§1.4). Cuesta una consulta por empleado; se
acepta: son unos pocos y React Query las cachea. Si un empleado no tiene
`breakStartTime`, su columna no lleva descanso.

**D6 — El hueco "Libre · toca para crear" es el primer hueco de 30 min a partir
de ahora, y solo si el dia visible es hoy.** Evidencia: el artboard es "Martes,
27 de agosto" marcado `HOY`, la cita anterior acaba a las 12:00 y el hueco cae
en 12:00-12:30 (`top:384px` = 8h + 4h). No esta dibujado en escritorio, asi que
alli no se pinta. Toda la rejilla vacia es pulsable en los dos anchos; el
recuadro discontinuo es solo la pista visual. La funcion recibe **la fecha
visible ademas de `now`** y devuelve `null` cuando no coinciden: sin esa
guarda, al navegar a manana el recuadro aparece a la hora de hoy.

**D7 — El CTA "Nueva cita" se queda en `size="action"` (px 18) aunque este
artboard dibuje px 16.** Dos pixeles frente a que el CTA primario sea el mismo
en las cinco pantallas que lo llevan. Diferencia consciente, anotada.

**D8 — El cambio de vista NO se monta en este bloque, en ninguno de los dos
anchos.** Son el segmentado Dia/Semana de escritorio (§1.1) y su gemelo movil,
el conmutador de agenda de `Calendario.dc.html:31-33`. Los dos estan dibujados;
su destino no: no existe ningun artboard de vista semanal ni de vista de lista.
Construirla seria inventarse una pantalla, que es lo que la regla del canvas
prohibe, y montar un control cuya segunda opcion no lleva a ninguna parte es
peor que no montarlo. Deuda con destinatario: *hace falta un artboard
`CalendarioSemanaDesktop` y su equivalente movil*. Consecuencia asumida: la
barra superior y la cabecera movil se apartan del artboard en ese control, y
esta escrito aqui para que T9 no lo cace como defecto.

**El buscador si se construye**, en los dos anchos, y el boton de buscar de la
cabecera movil (`:28-30`) se conecta a el en vez de quedarse decorativo como
hoy. Filtrar las citas del dia por cliente o servicio no inventa ninguna
pantalla. Lo que si es invencion admitida es **el estado desplegado del campo**
(ancho, marcador de posicion, boton de cerrar): no esta dibujado en ningun
artboard. Se resuelve con las primitivas del repo y se anota como tal, no se
disfraza de dato del canvas.

**D9 — El resumen por empleado cuenta TODAS las citas, canceladas incluidas**, y
suma sus minutos (`endTime - startTime`). No es una suposicion: la columna de
Marc Oliva es la unica que cabe entera en el recorte del artboard y cuadra
exacta solo contando la cancelada — `:220` 09:30-10:00 (30) + `:225` 11:30-12:00
**Cancelada** (30) + `:230` 12:00-13:30 (90) = 3 bloques y 150 min, y su
cabecera (`:124`) dice `3 citas · 2h 30min`. Excluyendolas saldria "2 citas ·
2h". Es ademas coherente con D3, que decide pintarlas.

**D10 — Rango de la rejilla: se queda en 08:00-21:00.** Los dos artboards
recortan por altura de marco (llegan a 15:00 y a 13:00), asi que no dicen nada
del rango real. No se cambia lo que hay.

**D11 — Cada tarea deja sus tests.** Es el unico modo de que no se repita la
deuda del bloque 2. Las pruebas de escritorio necesitan su `mockMatchMedia(true)`
local y su `afterEach`: el polyfill de `src/test/setup.ts` devuelve SIEMPRE
`matches:false`. Patron en `booking-step-shell.test.tsx:24`. Testing Library
busca `data-testid`, NO `data-slot`.

**D12 — En movil el estado inicial es el PRIMER EMPLEADO ACTIVO, no "Todos".** El
artboard dibuja el filtro con una empleada elegida: `Calendario.dc.html:51` es la
pildora "Todos" en REPOSO (fondo `#FFFFFF`, peso 500) y `:52-55` es Laura
SELECCIONADA (fondo `#B4522F`, peso 600). El contenido lo remata: la rejilla movil
pinta los tres bloques de la columna de Laura del artboard de escritorio — Carla
Ruiz 09:00-10:00, Ana Garcia 10:30-12:00 y el Almuerzo 13:00-14:00, identicos a
`CalendarioDesktop.dc.html:162,168,177` — y ninguno de los otros cinco. Con
"Todos", esa columna llevaria las ocho citas de los tres empleados repartidas en
carriles, no dos bloques a ancho completo.

"Todos" sigue existiendo como eleccion explicita, y por eso hace falta el reparto
en carriles: con una sola columna las citas de N empleados se apilarian unas sobre
otras como bloques absolutos. Ese reparto sirve ademas en escritorio cuando un
mismo empleado tiene dos citas solapadas.

**Este dato se leyo mal durante la ejecucion** y llego a escribirse cuatro veces en
el fuente como si fuera del artboard (`calendar.ts:315,435`, `day-view.tsx:224`,
`calendar.test.ts:585`), citando `:51` como prueba. Las cuatro citas son falsas y
se corrigen. Ver `tasks/lessons.md`, "El dato del brief se copia de la spec".

**D14 — En las rutas de rejilla, el contenedor de la app tiene altura DEFINIDA.**
Conectar la cadena de `flex-1 min-h-0` (D4) no basta: `min-h-0` y `flex-1` solo
acotan de verdad si algun ancestro tiene altura definida, y el contenedor de
`(app)/layout.tsx:41` es `min-h-dvh` — un suelo, no una altura. Sin esto, un
`day-view` con `flex-1 min-h-0 overflow-y-auto` crece a sus 1248px naturales en
vez de hacer scroll interno, en los dos anchos.

Solucion: `FILL_ROUTES` junto a `FAB_ROUTES` en `(app)/layout.tsx` — el mismo
patron que ese fichero ya usa —, y en esas rutas el contenedor pasa de
`flex min-h-dvh` a `flex h-dvh overflow-hidden`. Consecuencias comprobadas:
en escritorio `<main>` es hijo de un flex en fila y se estira a los 100dvh, que
ya son definidos; en movil el contenedor es columna, `BottomNav` es `fixed` y
por tanto no consume espacio de flex, asi que `<main>` recibe los 100dvh y su
`pb-20` deja el contenido justo por encima de la barra. El `overflow-hidden` es
ademas lo que dibujan los dos artboards a nivel de marco
(`CalendarioDesktop:130`, `Calendario.dc.html:66`).

Las once pantallas que no estan en `FILL_ROUTES` conservan `min-h-dvh` y su
scroll de pagina, sin un solo cambio. **Invariante, y esta escrito aqui una sola
vez:** una ruta en `FILL_ROUTES` pasa `layout="fill"` a `PageShell`, y al reves.
Hoy la lista es `["/calendar"]`; el bloque 4 anadira su panel acoplado.

**D13 — Una cita cuyo empleado no esta en la lista no desaparece.** `useEmployees`
trae solo los activos y `staffApi.listEmployees` no pagina (`staff.ts:20`). Una
cita de un empleado desactivado hoy, o cortado por la paginacion, se quedaria
fuera de todas las columnas sin que nada avise. Van a una columna final
"Otros", con el nombre que trae la propia cita (`employeeName`). Es preferible
una columna fea a una cita invisible.

---

## 3. Ficheros

| Fichero | Accion | Tarea |
|---|---|---|
| `src/app/globals.css` | 5 tokens (§1.5) en `:root` + su mapeo en `@theme inline` | T1 |
| `src/lib/utils/calendar.ts` | canalon de 4px en `calculateBlockPosition`; + `groupByEmployee`, `employeeDaySummary`, `nextFreeSlot`, `breakPosition`, `assignLanes` | T2 |
| `src/lib/utils/calendar.test.ts` | crear | T2 |
| `src/hooks/use-staff.ts` | + `useEmployeesWorkingHours(ids)` con `useQueries` | T2 |
| `src/components/layout/page-shell.tsx` | + prop `layout` (D4) | T3 |
| `src/components/layout/page-shell.test.tsx` | + casos de `layout="fill"` | T3 |
| `src/app/(app)/layout.tsx` | rama movil de `<main>` a columna flex acotada (D4) | T3 |
| `src/components/calendar/time-grid.tsx` | reescribir (§1.1, §1.3) | T4 |
| `src/components/calendar/appointment-block.tsx` | reescribir (§1.2) | T5 |
| `src/components/calendar/appointment-block.test.tsx` | crear | T5 |
| `src/components/calendar/break-block.tsx` | crear (§1.2) | T5 |
| `src/components/calendar/free-slot-hint.tsx` | crear (§1.3, D6) | T5 |
| `src/components/calendar/employee-column-header.tsx` | crear (§1.1) | T6 |
| `src/components/calendar/day-view.tsx` | reescribir a N columnas | T6 |
| `src/components/calendar/day-view.test.tsx` | crear | T6 |
| `src/components/calendar/employee-filter.tsx` | recalibrar (§1.3) | T7 |
| `src/components/calendar/date-navigator.tsx` | reescribir: fila movil + cluster escritorio | T7 |
| `src/components/calendar/date-navigator.test.tsx` | crear | T7 |
| `src/components/calendar/calendar-search.tsx` | crear (D8) | T8 |
| `src/app/(app)/calendar/page.tsx` | reescribir | T8 |
| `src/app/(app)/calendar/page.test.tsx` | crear | T8 |
| `visual/calendar-vs-artboards.spec.ts` | crear | T9 |

---

## 4. Olas y protocolo

```
Ola 1 (paralela, rutas disjuntas):   T1 || T2 || T3
Ola 2 (paralela, rutas disjuntas):   T4 || T5 || T7      (dependen de T1, T2)
Ola 3:                               T6                  (depende de T2, T4, T5)
Ola 4:                               T8                  (depende de todo)
Ola 5:                               T9 + revision
```

**Commit, en las nueve tareas, sin excepcion:**

```bash
git add <sus rutas>
git commit -o <sus rutas> -m "..."
```

Las dos cosas. `git add` porque `git commit -o` falla sobre ficheros que git aun
no conoce y casi todas las tareas crean ficheros. `-o` porque commitea solo esas
rutas e ignora el resto del indice: en una ola de tres agentes sobre el mismo
arbol, sin el, el primero que commitea se lleva el trabajo a medio escribir de
los otros. **NUNCA `git add -A`, NUNCA `git commit -m` a secas.**

**Seguridad operativa, en cada brief:**
- PROHIBIDO tocar `node_modules`. PROHIBIDO `npm ci` (uno previo destruyo
  `node_modules/.bin` devolviendo exit code 0). Si falta algo: `npm install`.
- No cambiar de rama. No tocar el repo backend.
- `AGENTS.md` avisa: *"This is NOT the Next.js you know"*. Leer
  `node_modules/next/dist/docs/` antes de escribir codigo de Next.
- Verificacion con EVIDENCIA: adjuntar la salida real de
  `npm run test -- --run`, nunca afirmar "pasa". Linea base: **343 tests en 57
  ficheros**, `tsc` limpio, lint 0 errores + 25 avisos.

**Revision:** al terminar el BLOQUE ENTERO, no por tarea (regla del usuario del
2026-08-28). Es la T9: panel de 3 revisores independientes en paralelo, lentes
distintas (fidelidad al artboard · correccion · tests que no demuestran nada),
instruidos para REFUTAR.

---

## 5. Tareas

### T1 — Tokens

**Ficheros:** `src/app/globals.css`.

- [ ] Anadir los cinco tokens de §1.5 al bloque `:root`, cada uno con el
      comentario `/* CalendarioDesktop.dc.html:NN */` que lo justifica, siguiendo
      el estilo de los que ya hay ahi (`--warning-border`, `--destructive-soft`).
- [ ] Anadir su mapeo en `@theme inline` (`--color-hairline-strong:
      var(--hairline-strong)`, etc.) siguiendo el patron de `--color-muted-foreground-2`.
      Sin el mapeo NO existen las utilidades `bg-warning-soft` / `border-hairline-strong`.
- [ ] `npm run build` compila. Commit.

### T2 — Calculo y datos

**Ficheros:** `src/lib/utils/calendar.ts`, `src/lib/utils/calendar.test.ts`
(crear), `src/hooks/use-staff.ts`.

- [ ] **El canalon de 4px** (§1.1): `calculateBlockPosition` resta 4px al alto,
      con el minimo actual de 24px como suelo. Sin esto, dos citas seguidas se
      tocan; el artboard las separa siempre. Test: 60 min -> 92px, 30 min -> 44px.
- [ ] `groupByEmployee(appointments, employees)` — una entrada por empleado
      activo, en el orden de `employees`, incluidas las vacias (el artboard
      dibuja la columna aunque no tenga citas), **mas la columna "Otros" de D13**
      cuando alguna cita tiene un `employeeId` que no esta en la lista. Test
      explicito del caso huerfano: la cita no puede desaparecer.
- [ ] `employeeDaySummary(appointments): {count, minutes}` segun **D9** —
      cuenta TODAS, canceladas incluidas — y su formato `4 citas · 5h 30min`
      (singular "1 cita"; sin horas -> `45min`; minutos exactos -> `5h`).
      Test con el caso real del artboard: 30 + 30 (cancelada) + 90 ->
      `3 citas · 2h 30min`.
- [ ] `nextFreeSlot(appointments, visibleDate, now): string | null` segun **D6**
      — `null` si `visibleDate` no es el dia de `now`; si no, primer tramo de
      `SLOT_MINUTES` desde `now` redondeado hacia arriba que no solape ninguna
      cita ni el descanso, dentro de la rejilla. `null` si no queda ninguno.
- [ ] `assignLanes(appointments): Array<{appointment, lane, lanes}>` segun **D12**
      — reparto en carriles de las citas que se solapan. Tests: dos citas
      solapadas -> `lanes: 2`; encadenadas sin solape -> `lanes: 1`; tres a la vez.
- [ ] `breakPosition(workingHours, date)` — reusa `getTodayBusinessHours` para
      la fila del dia y devuelve `{top, height, label}` con
      `calculateBlockPosition`, o `null` si el dia esta cerrado o no hay descanso.
      Ojo: `breakStartTime` llega como `09:00:00` (`formatTimeOfDay`). El tipo
      declarado de `getTodayBusinessHours` es `BusinessHoursResponse[]` y
      `WorkingHoursResponse` es estructuralmente identico: **compila, no lo
      "arregles"** (§1.4).
- [ ] Casos borde en los tests: dia sin citas, cita que empieza antes de las
      08:00, `now` posterior al cierre, descanso nulo, dia cerrado.
- [ ] `useEmployeesWorkingHours(ids: string[])` en `use-staff.ts` con
      `useQueries` de React Query v5, `queryKey: ["employee-working-hours", id]`,
      `enabled` con el mismo guardia de token que `useEmployees`. Devuelve
      `Record<string, WorkingHoursResponse[]>`.
- [ ] `npm run test -- --run src/lib/utils/calendar.test.ts` en verde, con la
      salida pegada. Commit.

### T3 — `PageShell layout="fill"`

**Ficheros:** `src/components/layout/page-shell.tsx`,
`src/components/layout/page-shell.test.tsx`, **`src/app/(app)/layout.tsx`**.

Esta tarea arregla una cadena de tres eslabones. Los tres, o no funciona nada
(**D4**, con las pruebas de §1.4).

- [ ] Anadir `layout?: "default" | "fill"` (por defecto `"default"`) con el
      comentario que explique **D4** y cite `CalendarioDesktop.dc.html:103,130`.
- [ ] **Eslabon 1 — `src/app/(app)/layout.tsx:46-54`:** la rama movil de
      `<main>` (`w-full flex-1 pb-20`) no es `display:flex`, asi que corta la
      cadena antes de llegar a `PageShell`. Pasarla a columna flex con altura
      acotada. La rama de escritorio ya funciona: no tocarla mas de lo necesario.
- [ ] **Eslabon 2 — `page-shell.tsx`, escritorio:** con `fill`, el envoltorio
      pasa de `flex flex-col px-7 py-6` a `flex min-h-0 flex-1 flex-col`, y la
      capa interna pierde `max-w-[1084px]` y `gap-[18px]` y gana
      `min-h-0 flex-1`. Ademas la barra superior pasa de `pl-7 pr-7` a 24px
      (**D4**). `contentClassName` sigue mandando sobre el espaciado.
      Anadir `min-h-0` tambien al `flex flex-1 flex-col` de `:89`: hoy
      sobrevive por casualidad y es el eslabon fragil.
- [ ] **Eslabon 3 — `page-shell.tsx`, movil:** con `fill`,
      `mx-auto w-full max-w-3xl p-4 md:py-6` pasa a
      `mx-auto flex w-full min-h-0 flex-1 flex-col max-w-3xl`, **y la capa
      interna de `:146`** (`cn(mobileContentClassName)`, hoy sin flex de ningun
      tipo) gana `flex min-h-0 flex-1 flex-col`. Sin esta segunda, la rejilla se
      pinta a 1248px y la cabecera se va con el scroll de pagina.
- [ ] Tests: (a) con `layout="default"` la capa de contenido conserva
      `max-w-[1084px]` en escritorio — es la prueba de que ninguna otra pantalla
      se mueve; (b) con `fill` no lo lleva, el envoltorio no lleva `px-7` **y
      las dos capas llevan `flex-1` y `min-h-0`**; (c) lo mismo en movil.
      La asercion sobre `flex-1`/`min-h-0` no es opcional: sin ella el test
      pasa igual con la cadena rota, que es el 100% del proposito de D4.
      Contar con `container.querySelectorAll('[data-slot="page-shell-content"]')`,
      NO con `getAllByTestId` (**D11**).
- [ ] Suite completa en verde (el resto de pantallas no debe moverse), salida
      pegada. Commit.

### T4 — Rejilla horaria

**Ficheros:** `src/components/calendar/time-grid.tsx`.

- [ ] Reescribir con `variant: "mobile" | "desktop"`: canal 46px/10px/`top:-7px`
      o 64px/11px/`top:-8px` (§1.1, §1.3).
- [ ] La linea de la hora en punto usa `border-hairline-strong` y la de la media
      hora `border-hairline`; hoy las dos son `border-dashed border-muted`, que
      no es lo que dibuja el canvas (§1.1, filas).
- [ ] Exportar tambien el fondo de filas que usan las columnas
      (`GridRows`), para que columna y canal compartan una sola definicion de
      las lineas en vez de duplicarla. La columna de escritorio lleva
      `border-left` y la movil NO (§1.3): es parte del `variant`, no del CSS
      de la pagina.
- [ ] `tsc` limpio. Commit.

### T5 — Bloques

**Ficheros:** `appointment-block.tsx` (reescribir), `appointment-block.test.tsx`
(crear), `break-block.tsx` (crear), `free-slot-hint.tsx` (crear).

- [ ] `AppointmentBlock` con `variant: "mobile" | "desktop"`:
      posicion y caja de §1.1 (`left/right` 6px escritorio, 4px movil), estados
      de §1.2 con los tokens de §1.5, insignia "Pendiente" con
      `--color-status-pending-*`, y el texto de cada ancho segun §1.3.
- [ ] Variante compacta **por DURACION, no por pixeles**: 30 minutos o menos ->
      `padding: 6px 10px` y solo nombre + hora (§1.1). Un umbral en pixeles
      (`< 48`) no se dispara nunca, porque 30 min miden exactamente 48px.
      Sustituye al `pos.height > 36` actual.
- [ ] **Completada y Cancelada llevan siempre dos lineas** (§1.2), sin servicio
      ni precio, aunque el bloque sea alto. Es regla del estado, no del alto:
      el artboard dibuja una Completada de 68px con dos lineas.
- [ ] Precio con el formato del canvas: `35,00 €` (`Intl.NumberFormat("es-ES")`).
- [ ] Tests: un caso por estado que compruebe el texto ("Pendiente",
      "· Cancelada", "· Completada"), **y ademas las clases del borde izquierdo y
      del fondo**. Solo con el texto, confundir `bg-warning-soft` con
      `bg-destructive-tint` sobrevive intacto — y §1.5 avisa de que un `var()`
      inexistente se descarta en silencio, sin error.
- [ ] Test del umbral: una cita de 30 min sale compacta y una de 60 no. Una de
      15 min queda en el suelo de 24px de `calculateBlockPosition`, donde no
      cabe ni la compacta; no esta dibujado, basta con que no reviente.
- [ ] **Cada test debe fallar si se muta el componente** — demostrarlo mutando y
      pegando el rojo.
- [ ] `BreakBlock` (§1.2, gradiente rayado) y `FreeSlotHint` (§1.3, discontinuo).
- [ ] Suite en verde, salida pegada. Commit.

### T6 — Vista de dia a N columnas

**Ficheros:** `day-view.tsx` (reescribir), `employee-column-header.tsx` (crear),
`day-view.test.tsx` (crear).

- [ ] `EmployeeColumnHeader`: tarjeta de §1.1 con avatar coloreado por
      `colorHex` (fondo al 12% y texto al color pleno, como
      `employee-filter.tsx:46-49`), recurriendo a `--chart-N` por indice cuando
      `colorHex` es `null`, y el resumen de `employeeDaySummary` (T2).
- [ ] `DayView` recibe `columns: Array<{employee?, appointments, breakPos}>` y
      `variant`. Escritorio: fila de cabeceras + `grid-template-columns:
      repeat(N, minmax(0,1fr))` gap 12px, alineada con el canal de 64px
      (§1.1). Movil: una sola columna sin cabecera, canal 46px.
- [ ] La rejilla ocupa el alto restante y hace scroll interno: `flex-1 min-h-0`
      + `overflow-y-auto`. **Sin `calc(100vh-...)`** — esa era la deuda.
- [ ] **La barra de scroll no puede desalinear las cabeceras.** La rejilla mide
      1248px y siempre desborda, asi que la barra siempre esta; con las
      cabeceras fuera del scroller, sus columnas y las de la rejilla dejan de
      coincidir. Resolverlo aqui, no en T9: o `scrollbar-gutter: stable` en el
      scroller mas la reserva equivalente en la fila de cabeceras, o meter la
      fila dentro del scroller como `sticky top-0`. **Test que fije la decision.**
- [ ] Reparto en carriles (**D12**) con `assignLanes` de T2: las citas que se
      solapan dividen el ancho de la columna. Es lo que hace usable "Todos" en
      movil, donde hoy se apilarian unas sobre otras.
- [ ] Hueco pulsable: al pulsar la rejilla vacia se llama `onSlotTap(employeeId,
      time)`; `FreeSlotHint` solo en movil y solo si `nextFreeSlot` no es `null`
      — la guarda de "solo si el dia visible es hoy" ya vive dentro de la
      funcion (**D6**), no se duplica aqui.
- [ ] Tests con `mockMatchMedia`: (a) escritorio con tres empleados pinta tres
      cabeceras y tres columnas, incluida la del empleado sin citas; (b) movil
      pinta una sola columna y ninguna cabecera; (c) el descanso solo aparece en
      la columna del empleado que lo tiene.
- [ ] Suite en verde, salida pegada. Commit.

### T7 — Filtro y navegador de fecha

**Ficheros:** `employee-filter.tsx`, `date-navigator.tsx`,
`date-navigator.test.tsx` (crear).

- [ ] `EmployeeFilter` recalibrado a §1.3: h34, radios 999, avatar 24px,
      tipografias 12px, y el estado seleccionado con avatar sobre
      `rgba(255,255,255,.22)`. Sigue siendo **solo movil** (D2).
- [ ] `date-navigator.tsx` exporta dos: `DateNavigatorRow` (fila movil completa
      de §1.3, con `HOY` cuando el dia es hoy) y `DateNavigatorCluster` (los tres
      controles de 34px de §1.1, que hoy viven inline en `page.tsx:74-102`).
      Una sola definicion de la navegacion, dos presentaciones.
- [ ] Tests de `DateNavigatorRow`: pinta `HOY` solo cuando el dia es hoy, y
      **`HOY` no es pulsable** — es un indicador (§1.3); el boton "Hoy" solo
      existe en el cluster de escritorio. Prev y next llaman a su callback.
      Los botones llevan `aria-label` — hoy los de escritorio lo tienen y los de
      `DateNavigator` no (deuda del bloque 2).
- [ ] Suite en verde, salida pegada. Commit.

### T8 — La pagina

**Ficheros:** `src/app/(app)/calendar/page.tsx` (reescribir),
`page.test.tsx` (crear), `calendar-search.tsx` (crear).

- [ ] Titulo por ancho segun **D1**; se borra el formato corto y el comentario
      que lo justificaba.
- [ ] `layout="fill"` (D4). Cada franja trae su propio padding: fila de fecha y
      pildoras `px-4 py-3` en movil; fila de empleados y rejilla `px-6` en
      escritorio (24px, §1.1).
- [ ] Escritorio: `titleAdjacent={<DateNavigatorCluster/>}`, `actions` = buscador
      + CTA. Movil: `mobileActions` = **solo el boton de buscar**; el conmutador
      de agenda no se monta (**D8**). Cuerpo = `DateNavigatorRow` +
      `EmployeeFilter` + `DayView`.
- [ ] Se borra el filtro de canceladas (**D3**) y el `DateNavigator` duplicado
      del cuerpo en escritorio (**D1**).
- [ ] Consulta segun **D2**: `employeeId` solo en movil. Descansos con
      `useEmployeesWorkingHours` (**D5**).
- [ ] `CalendarSearch` (**D8**): el boton se convierte en campo al pulsarlo y
      filtra las citas del dia por cliente o servicio, sin tocar la consulta.
      `Escape` y el boton de cerrar lo repliegan. **Los dos anchos**: en
      escritorio es el 38x38 de §1.1, en movil el 36x36 de §1.3, que hoy es
      decorativo (`page.tsx:114-128`). El estado desplegado es invencion
      admitida: no esta dibujado, se resuelve con las primitivas del repo.
- [ ] Pulsar un bloque sigue abriendo `AppointmentDetailSheet` (es del bloque 4;
      aqui solo se conserva el cableado).
- [ ] Tests: (a) el titulo es "Citas" en movil y la fecha en escritorio; (b) la
      fecha aparece **una sola vez** en escritorio — es la prueba de regresion
      de la deuda; (c) una cita cancelada se pinta; (d) el buscador filtra.
- [ ] Suite completa + `tsc` + `npm run lint` + `npm run build`, salidas pegadas.
      Commit.

### T9 — Comparacion visual y revision

**Ficheros:** `visual/calendar-vs-artboards.spec.ts`.

- [ ] Spec de Playwright que capture `/calendar` a 1440x900 y a 390x844 y las
      deje junto a los dos artboards, mismo viewport, para comparar elemento a
      elemento. Sigue el patron de `visual/shell-vs-artboards.spec.ts`.
      Credenciales por variables de entorno (`RIVOO_E2E_EMAIL` /
      `RIVOO_E2E_PASSWORD`); **nunca en el repo**.
- [ ] Panel de 3 revisores independientes en paralelo, agentes NUEVOS, ninguno
      de ellos implementador de lo que revisa, instruidos para REFUTAR:
      1. **Fidelidad al artboard** — cada valor de §1.1/§1.2/§1.3 contra el
         codigo, `fichero:linea` de los dos lados. **No son defectos**, y estan
         decididos aqui: el conmutador de vista ausente (**D8**), los 18px del
         CTA (**D7**), y los fondos de avatar, que salen de `colorHex` al 12%
         y difieren 1-4 puntos por canal de los del artboard (`#EBEFEC` frente
         a `#E8EEE7`); los colores de texto si son `--chart-1/2/3` exactos.
      2. **Correccion** — agrupacion por empleado, descansos, hueco libre,
         estados, husos y fechas, y que ninguna otra pantalla se haya movido
         con `layout="fill"`.
      3. **Tests que no demuestran nada** — mutar el codigo y comprobar que el
         test correspondiente se pone rojo. Un test que sobrevive a la mutacion
         es un defecto.
- [ ] Arreglar todo lo confirmado y volver a pasar la suite.
- [ ] Volcar en `E:\IdeaProjects\rivoo\tasks\todo.md` el cierre del bloque y sus
      deudas con destinatario.

---

## Orden de ejecucion

**Frontend (`rivoo-frontend`), unico subsistema:**

```
Ola 1   T1 tokens        - sin dependencias entre si, rutas disjuntas
        T2 calculo
        T3 page-shell
Ola 2   T4 rejilla       - dependen de T1 y T2; rutas disjuntas
        T5 bloques
        T7 filtro/fecha
Ola 3   T6 day-view        depende de T2, T4, T5
Ola 4   T8 pagina          depende de todo lo anterior
Ola 5   T9 visual + panel de revision
```

**Coordinacion:** una sola rama de trabajo; el `-o` del commit es lo que hace
seguras las olas paralelas. La verificacion completa (`tsc` + lint + build +
suite) se hace en T8 y se repite tras los arreglos de T9.

## Dependencias con otras specs

| Spec | Relacion | Implicacion |
|---|---|---|
| **Bloque 2 — shell de escritorio** | Pre-requisito, ya cerrado (`6ec0e26`) | Aporta `PageShell`, la barra lateral y `size="action"`. T3 lo extiende con `layout="fill"`. |
| **Bloque 4 — Detalle de cita** | Consumidor | Sustituira `AppointmentDetailSheet` por el panel acoplado de 360px sobre esta misma rejilla. T8 conserva el cableado del `onTap` para que el bloque 4 solo tenga que cambiar el destino. |
| **Asistente de nueva cita** | Complementario | El CTA de esta pantalla es su unica entrada en escritorio. Sin dependencia de codigo. |
| **Vista de semana** | Bloqueada | Falta artboard (**D8**). No se planifica hasta que exista. |
