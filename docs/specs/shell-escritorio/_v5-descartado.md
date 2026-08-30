# Shell de escritorio — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task by task. The steps use checkbox syntax (`- [ ]`) for tracking.

**Objetivo:** dar a la app interna el chasis que dibujan los artboards, **en los dos anchos**: barra lateral de 248px + barra superior de 72px en escritorio, y la cabecera de 56px en móvil. Fiel al artboard, no al código actual.

> **Esto cambió por decisión explícita del usuario (2026-08-29).** Las versiones anteriores de este plan trataban el chasis móvil actual como intocable ("byte a byte"). Es al revés: **el artboard es la verdad y el código actual es lo antiguo**. Donde el código y el artboard no coincidan, se cambia el código. Los artboards móviles dibujan una cabecera de 56px uniforme para las trece pantallas; el código tiene hoy tres formas distintas, ninguna de ellas la dibujada.

**Architecture:** el layout de `(app)` monta la barra lateral y el contenedor; **la barra superior NO la monta el layout**, la monta cada pantalla a través de `PageShell`, porque los artboards demuestran que su contenido es distinto en cada una (título, subtítulo, navegador de fecha, uno o dos botones, o nada). Móvil y escritorio se montan de forma EXCLUYENTE con `useMediaQuery`, nunca los dos a la vez ocultando uno con CSS.

**Tech Stack:** Next.js 16 (App Router), TypeScript, Tailwind v4, Shadcn/UI + `@base-ui/react`, React Query v5, Vitest 4 + Testing Library, Playwright (visual).

**Complejidad:** **Compleja** (7+ ficheros, transversal a toda la app interna) → `executing-plans` + revisión final del conjunto.

**Repo:** `E:\IdeaProjects\rivoo-frontend`. Repo único. El backend NO se toca; única excepción, escribir en `E:\IdeaProjects\rivoo\tasks\todo.md`.

---

## Estado actual, verificado a mano

Todo lo de esta sección se comprobó contra el código el 2026-08-29. No es memoria ni suposición.

| Hecho | Dónde |
|---|---|
| El layout de la app no tiene NI UNA clase responsive | `src/app/(app)/layout.tsx` (32 líneas, completo) |
| `min-h-full` es inerte, pero NO por lo que parece: `html` **si** tiene `h-full` (`src/app/layout.tsx:42`); la cadena se rompe en `body`, que solo lleva `min-h-full` (`:44`), asi que su `height` computa `auto` | `(app)/layout.tsx:21` + `src/app/layout.tsx:42,44` |
| Barra inferior con 4 pestañas: Hoy · Citas · Equipo · Mas | `src/components/layout/bottom-nav.tsx:7-12` |
| Cabecera móvil: nombre del salón + nombre/email del usuario, h-14 | `src/components/layout/app-header.tsx:11-23` |
| El único test de `components/layout/` es el de `onboarding-gate` | `src/components/layout/onboarding-gate.test.tsx` |
| `useAuth()` da `{ id, name, email, tenantId, role, subscriptionPlan }` | `src/hooks/use-auth.ts:7-14` |
| `UserRole = "ROLE_PLATFORM_ADMIN" \| "ROLE_SALON_OWNER" \| "ROLE_EMPLOYEE"` | `src/types/auth.ts:1` |
| NO existe la ruta `/services`; Servicios es una pestaña de `/staff` | `src/app/(app)/staff/page.tsx:60-65` |
| `?tab=services` ya aterriza en esa pestaña | `src/app/(app)/staff/page.tsx:35-37` |
| El SVG de la marca YA existe y ya se exporta | `src/components/booking/booking-salon-header.tsx:9-29,55` |
| Cada página trae su propio `p-4 md:py-6` | `staff/page.tsx:59`, `clients/page.tsx:38`, `settings/page.tsx:20`, `today/page.tsx:77` |
| **El `<h1>` NO es universal, y hay TRES formas distintas.** (a) título `text-lg` suelto: `clients:41`, `settings:21`, `today:81`. (b) **sin ningún `<h1>`**: `staff/page.tsx` y `calendar/page.tsx`. (c) **fila de botón atrás + `<h1 className="text-sm">`**: `clients/[id]:41-46`, `staff/[id]:119-124`, `settings/account:18-23`, `settings/billing:70-75`, `settings/booking:49-54`, `settings/business-hours:52-57`, `settings/salon:75-80` — **siete pantallas** | comprobado fichero a fichero |
| `useSearchParams` **exige su propio `<Suspense>`** o Next lo trata como error de build | `staff/page.tsx:18-20` (el comentario) y `:22-24` (el boundary). Único uso en todo `src/` |
| `<Tabs>` de `/staff` es **no controlado**: `defaultValue` solo se lee al montar | `staff/page.tsx:60`, sobre `TabsPrimitive.Root` (`ui/tabs.tsx:8-11`) |
| `appointments/new` depende del chasis móvil con números a mano: `top-14` (los 56px del `AppHeader`) y `min-h-[calc(100vh-8rem)]` (56+64) | `appointments/new/page.tsx:30-31` |
| **NO existe `staff/page.test.tsx`**; el que hay es `staff/[id]/page.test.tsx`, que es otra pantalla | `ls "src/app/(app)/staff/"*.test.tsx` |
| `useMediaQuery` ya existe y ya lo usa el chasis de reserva | `src/hooks/use-media-query.ts:26`, usado en `booking-step-shell.tsx:8,65` |
| Los seis iconos de Lucide existen en la versión instalada | `CalendarCheck`, `LayoutGrid`, `User`, `Users`, `Scissors`, `Settings` — comprobados uno a uno el 2026-08-29 |

**Línea base a batir:** 275 tests en 50 ficheros en verde, `tsc` limpio, `lint` 0 errores (25 avisos preexistentes).

---

## Decisiones de diseño

Cinco decisiones no obvias. Cada una lleva la alternativa que se descartó y el porqué, para que ningún implementador las reabra a mitad de camino.

### D1 — La barra superior es un SLOT por pantalla, no un mapa de rutas en el shell

**Descartado:** que el layout pinte la barra superior a partir de un mapa `ruta → {título, CTA}`.

**Por qué:** ese mapa no puede expresar lo que los artboards piden. Comprobado en cinco:

| Artboard | Qué lleva la barra superior |
|---|---|
| `EquipoDesktop.dc.html:82` | título 24px + un CTA primario |
| `ClientesDesktop.dc.html:73` | título 24px + un CTA primario |
| `HoyDesktop.dc.html:74-86` | título 24px **con subtítulo** (fecha · hora) + botón-icono de refrescar + CTA |
| `CalendarioDesktop.dc.html:74-85` | título 26px **que es la fecha** + navegador ‹ / "Hoy" / › |
| `AjustesDesktop.dc.html:77-79` | **solo** el título, sin ningún botón |
| `DetalleEmpleadoDesktop.dc.html:90-102` | botón atrás + título + **dos** botones secundarios |

**Precedente del propio repo:** en el carril B (reserva pública) la v1 del plan montaba el chasis en `page.tsx`; eso habría obligado a las seis tareas de paso a editar un mismo fichero y habría matado la ola paralela. Se corrigió a "dispatcher puro, cada paso monta su chasis" y funcionó. Aquí es lo mismo: el layout monta la barra lateral, cada pantalla monta su barra superior.

**Descartado también:** un contexto React (`usePageHeader({title, actions})`) que el layout consume. Funciona, pero mete estado global para algo que es composición, y la página escribe el título en un efecto — un fotograma con el título por defecto antes del real en Hoy y Calendario, que son justo las dos de título dinámico.

### D2 — `PageShell` se aplica a las trece pantallas, no a una piloto

La v2 de este plan lo limitaba a `/staff` para no pisar los bloques 3-7. Estaba mal, y lo destapó la segunda revisión: en escritorio el `<main>` tiene que ir a ancho completo, porque la barra superior lleva un borde inferior que va de lado a lado. Eso significa perder el `mx-auto max-w-3xl` que hoy tiene `(app)/layout.tsx:26` — y las trece pantallas que no montaran `PageShell` pasarían de 768px centrados a **1190px a sangre** en un monitor de 1440. No es un estado intermedio: es una regresión visual en trece pantallas.

Con la forma que tiene `PageShell` (ver D2b), aplicarlo es **dos líneas por pantalla**: cambiar el `<div className="p-4 md:py-6">` por `<PageShell title="…">` y, en las que hoy tienen un `<h1>` propio en el cuerpo, quitarlo — porque `PageShell` ya lo pinta en móvil y lo sube a la barra superior en escritorio.

Lo que este bloque **sigue sin hacer** es reconstruir el interior de ninguna pantalla: ni tablas de escritorio, ni diálogos modales, ni el navegador de mes, ni los CTA de la barra superior. Eso es de los bloques 3-7, que encontrarán el chasis ya puesto.

### D2b — Dos cabeceras con contenidos DISTINTOS, no una con dos formas

**El inventario completo de las 26 cabeceras** (13 móviles + 13 de escritorio), que es el dato que faltaba en las tres primeras versiones de este plan:

| Ruta | Cabecera móvil (56px) | Barra superior (72px) | ¿Coinciden? |
|---|---|---|---|
| `/today` | "Bella Vista" + usuario y avatar (`Main:23-29`) | "Buenos dias, Maria" + fecha · hora (`HoyDesktop:76-77`) | **no** |
| `/calendar` | "Citas" (`Calendario:25`) | "Martes, 27 de agosto" + navegador (`CalendarioDesktop:76`) | **no** |
| `/clients` | "Clientes" (`Clientes:25`) | "Clientes" (`ClientesDesktop:74`) | sí |
| `/clients/[id]` | ‹ "Detalle cliente" (`DetalleCliente:33`) | "Ana Garcia" + "Cliente desde…" (`DetalleClienteDesktop:84-85`) | **no** |
| `/staff` | "Equipo" (`Equipo:35`) | "Equipo" (`EquipoDesktop:83`) | sí |
| `/staff/[id]` | ‹ "Detalle empleado" (`DetalleEmpleado:40`) | "Detalle empleado" (`DetalleEmpleadoDesktop:95`) | sí |
| `/settings` | "Ajustes" (`Ajustes:29`) | "Ajustes" (`AjustesDesktop:78`) | sí |
| `/settings/salon` | ‹ "Perfil del salon" (`AjustesSalon:34`) | **"Ajustes"** (`AjustesSalonDesktop:82`) | **no** |
| `/settings/booking` | ‹ "Reservas online" (`AjustesReserva:31`) | **"Ajustes"** (`AjustesReservaDesktop:81`) | **no** |
| `/settings/billing` | ‹ "Facturacion y plan" (`AjustesFacturacion:32`) | **"Ajustes"** (`AjustesFacturacionDesktop:79`) | **no** |
| `/settings/account` | ‹ "Mi cuenta" (`AjustesCuenta:31`) | **"Ajustes"** (`AjustesCuentaDesktop:80`) | **no** |
| `/settings/business-hours` | ‹ "Horario de apertura" + "Guardar" (`Horario:30`) | **"Ajustes"** (`HorarioDesktop:81`) | **no** |
| *(sin ruta)* | ‹ "Notificaciones" + "Guardar" (`AjustesNotificaciones:37`) | **"Ajustes"** (`AjustesNotificacionesDesktop:86`) | **no** |

**Nueve de trece no coinciden.** Y el patrón no es aleatorio: en escritorio la subpágina de ajustes se identifica por la **subnav de 210px** (`AjustesDesktop:81-89`), no por el título, así que las seis dicen "Ajustes" y ninguna lleva flecha de volver. En móvil no hay subnav, así que cada una lleva su nombre y su flecha.

**Decisión del usuario (2026-08-29): el título se UNIFICA.** Una sola cadena por pantalla, la misma en los dos anchos. Donde los artboards discrepan, gana el que da más información:

| Ruta | Título unificado | Por qué, y de cuál se aparta |
|---|---|---|
| `/today` | **"Buenos dias, {nombre}"** | El saludo dice algo; el nombre del salón no, y ya está en la barra lateral y en Ajustes. Se aparta del móvil (`Main:24`). |
| `/calendar` | **la fecha visible** ("Martes, 27 de agosto") | Es el dato que cambia al navegar y el que dice dónde estás. "Citas" repite el destino de la barra lateral. Se aparta del móvil (`Calendario:26`). |
| `/clients/[id]` | **el nombre del cliente** ("Ana Garcia") | Genérico contra útil. Se aparta del móvil (`DetalleCliente:37`). |
| `/staff/[id]` | **el nombre del empleado** | Los dos artboards dicen "Detalle empleado", pero dejarlo así al lado de un cliente que sí se nombra es incoherente. Se aparta de los dos, a propósito. |
| las **seis** de ajustes | **su propio nombre** ("Perfil del salon", "Horario de apertura", …) | En escritorio los seis artboards dicen "Ajustes" y la subnav de 210px indica cuál es; el nombre propio no estorba y quita ambigüedad al leer la pestaña del navegador. Se aparta del escritorio (`AjustesSalonDesktop:82` y sus cinco gemelos). |
| resto | el dibujado | `/clients`, `/staff`, `/settings` ya coinciden en los dos. |

**Lo que NO se unifica es la flecha de volver**, porque ahí los artboards no discrepan por descuido. Comprobado en los 26:

| Ruta | `back` (móvil) | `desktopBack` |
|---|---|---|
| `/clients/[id]` | **sí** (`DetalleCliente:33`) | **sí** — 38×38 (`DetalleClienteDesktop:80`) |
| `/staff/[id]` | **sí** (`DetalleEmpleado:40`) | **sí** — 38×38 con borde (`DetalleEmpleadoDesktop:92`) |
| `/settings/salon` · `booking` · `billing` · `account` · `business-hours` | **sí** (`AjustesSalon:34` y sus cuatro gemelos) | **no** — la subnav de 210px es la salida (`AjustesSalonDesktop:82`) |
| `/today` · `/calendar` · `/clients` · `/staff` · `/settings` | no | no |

O sea: **siete pantallas con flecha en móvil, dos en escritorio.** Las cinco subpáginas de ajustes son las que divergen, y por un motivo de navegación real: en escritorio tienen la subnav y en móvil no.

> **Cuidado al transcribir:** la flecha de `CalendarioDesktop:78` **no es un botón atrás**, es el `‹` del navegador de fecha (34×34, con "Hoy" en medio y `›` detrás). Mismo icono, otra función: va en `titleAdjacent`, no en `desktopBack`. Confundirlos mete un "volver" en el calendario y deja la fecha sin navegación.

Con eso el contrato queda:

```tsx
interface PageShellProps {
  title: string               // el mismo en los dos anchos (tabla de arriba)
  back?: boolean              // flecha en la cabecera MOVIL
  desktopBack?: boolean       // flecha en la barra superior; por defecto false
  actions?: ReactNode         // cluster derecho, los dos anchos
  mobileActions?: ReactNode   // cuando movil y escritorio no llevan lo mismo
  subtitle?: ReactNode        // solo escritorio — HoyDesktop:77
  titleAdjacent?: ReactNode   // pegado al titulo — CalendarioDesktop:75
  titleSize?: "default" | "lg"
  contentClassName?: string   // capa interna del contenido
  children: ReactNode
}
```

**La FORMA de la cabecera móvil sí es uniforme**, y eso el plan lo tenía bien: `height:56px`, `border-bottom: 1px solid #E7DCCF`, dos paddings — `0 16px` sin flecha, `0 14px 0 8px` con ella — flecha en caja de 44×44 con chevron de 20px trazo 2, título `.display` 21px sin flecha y 15px/600 con ella. No hay una tercera forma.

### D2c — `AppHeader` desaparece: la cabecera móvil del artboard ES `AppHeader`

`app-header.tsx:12` es `h-14` = **56px**, con el nombre del salón a la izquierda y el usuario a la derecha. Eso es literalmente `Main.dc.html:23-29`, la cabecera móvil de Hoy: "Bella Vista" + "Maria G." + avatar.

Si el layout siguiera montando `AppHeader` y además cada pantalla pintara su cabecera de 56px, saldrían **112px de cromo donde el artboard dibuja 56**. Así que `AppHeader` no se conserva: se absorbe en `PageShell`, que pinta la variante correcta por pantalla. Hoy queda como el caso `mobileActions` = usuario y avatar.

> **Esto obliga a ceder en D4.** `appointments/new/page.tsx:32` es `sticky top-14`, calibrado contra los 56px de `AppHeader`. Al desaparecer, esa pantalla queda con 56px de hueco arriba. Es UN número y **lo cambia T6** (`top-14` → `top-0`), en la misma tarea que borra `AppHeader`, porque es su consecuencia directa. El resto del asistente sigue siendo de su bloque.

### D3 — La barra lateral entra en `lg` (1024px). En `md` se queda el chasis móvil

**No hay ni un artboard de la app interna a 768px.** Los `*Desktop` son de 1440 y los móviles de 390. Inventar un diseño intermedio que nadie ha dibujado es lo que este plan evita.

- **base → 1023px:** cabecera móvil + barra inferior, exactamente como hoy.
- **1024px+:** barra lateral 248px + barra superior 72px; desaparece la barra inferior. El botón flotante **se queda** en los dos anchos, por lo que explica T6 punto 4: sin él no habría forma de crear una cita en escritorio hasta que su bloque ponga el CTA en la barra superior.

El usuario pidió "móvil y escritorio, md y lg". Aquí **`md` es deliberadamente "móvil ensanchado"**: el contenido ya usa `max-w-3xl`, que a 768px respira. No se inventa un tercer diseño. Sin `sm`, por decisión ya tomada en el carril B.

### D4 — `/appointments/new` se queda como está. Sacarlo del shell es del bloque del asistente

`NuevaCitaDesktopPaso1.dc.html:29,42-43` dibuja el asistente interno **a pantalla completa**: cabecera propia de 68px (fondo `#F8F2EA`, marca 24px + "Nueva cita" 20px a la izquierda; "Cancelar" + botón X de 38px a la derecha) y contenedor de 1120px a dos columnas con gap 40 — el mismo chasis que la reserva pública. Ninguno de los cinco pasos lleva barra lateral. Y hoy la ruta vive dentro de `(app)/layout.tsx`, así que en escritorio saldría con barra lateral.

**Aun así, este bloque no se lo quita.** La primera versión del plan sí lo hacía, y era un error: `appointments/new/page.tsx:30` y `:32` tienen el chasis móvil metido en números a mano —

```tsx
<div className="flex min-h-[calc(100vh-8rem)] flex-col">
  <div className="sticky top-14 z-30 border-b bg-background px-4 py-3">
```

`top-14` son exactamente los 56px del `AppHeader` (`app-header.tsx:12`); `8rem` son 128px = cabecera 56 + barra inferior 64. Quitarle el shell sin darle su cabecera propia deja esa barra flotando 56px por debajo del borde, con nada encima, y 128px de altura sobrante. **Peor que ahora, y también en móvil.**

Construir la cabecera propia del asistente es el bloque del asistente. Se apunta allí junto con los dos números que hay que ajustar. Aquí, el asistente se queda exactamente como está: con barra lateral en escritorio, que es feo pero funciona, hasta que su bloque lo haga bien.

> **Un dato que el revisor comprobó y conviene guardar:** el motivo por el que la v1 descartó mover la carpeta a `(fullscreen)/appointments/new` era falso. No hay colisión de rutas — `normalizeAppPath` quita los grupos y `/appointments/new` y `/appointments/[id]` quedan como claves distintas (`node_modules/next/dist/build/entries.js:277-291`), y de hecho hoy ya conviven. Cuando llegue el bloque del asistente, esa vía está abierta.

### D5 — Servicios enlaza a `/staff?tab=services`, y eso arrastra DOS cosas más

"Servicios" es uno de los seis destinos de la barra lateral pero **no existe `/services`**: hoy es una pestaña de `/staff`, y `?tab=services` ya aterriza en ella (`staff/page.tsx:39`).

Depender de la query tiene dos consecuencias que la v1 de este plan no vio, y las dos son bloqueantes si se dejan pasar:

**1. `useSearchParams` exige su propio `<Suspense>`.** No es una preferencia de estilo: sin boundary, Next lo trata como error de build. El repo ya pagó esa cicatriz y la dejó documentada en `staff/page.tsx:18-20`, con el boundary en `:22-24` — y es el **único** uso de `useSearchParams` en todo `src/`. Si la barra lateral lo usa desde el layout, el bailout sube por encima de las catorce páginas del grupo y **rompe el build de todas**. Ningún test de Vitest lo vería: jsdom no prerenderiza.
→ **`app-sidebar.tsx` monta su propio `<Suspense>`** alrededor de la lista de destinos, y **T8 ejecuta `npm run build`**, que hoy no ejecuta ninguna tarea. Ese hueco es lo que dejaba pasar el fallo.

**2. El destino no funcionaría.** `<Tabs defaultValue={initialTab}>` (`staff/page.tsx:60`) es **no controlado**: `defaultValue` solo se lee al montar. Estando ya en `/staff`, pulsar "Servicios" es una navegación de cliente dentro de la misma ruta — el componente no se remonta, la URL cambia, el destino se enciende… y el contenido sigue en Empleados. El activo diría la verdad y la pantalla mentiría.
→ **T7 pasa `Tabs` a controlado por la query.** Son cuatro líneas en la pantalla que este bloque ya toca.

El activo se decide con un predicado por destino, no con una comparación de cadenas: `pathname.startsWith(href)` **no ve la query** y encendería Equipo y Servicios a la vez.

**Descartado:** unificar la barra inferior móvil y la barra lateral en una sola lista de destinos. Las listas son distintas de verdad (4 contra 6) y la etiqueta del mismo destino cambia ("Mas" en móvil, "Ajustes" en escritorio, ambos a `/settings`). Forzar la unificación cuesta más de lo que ahorra. **`bottom-nav.tsx` no se toca en este bloque.**

**Descartado también:** crear una ruta real `/services`. Es más limpio a largo plazo, pero `EquipoDesktop.dc.html:93-97` dibuja **además** un segmentado Empleados/Servicios dentro de la propia pantalla: el diseño quiere las dos cosas sobre la misma vista, no dos rutas. Partirla es del bloque 6, si es que hace falta.

---

## Inventario visual

Fuente canónica del chasis: `design/EquipoDesktop.dc.html`. Los valores están transcritos, no interpretados.

### Barra lateral

| Elemento | Referencia | Forma y valores |
|---|---|---|
| contenedor | `EquipoDesktop.dc.html:37` | `width:248px` · `flex-shrink:0` · `padding:20px 14px` · `border-right:1px solid #E7DCCF` · `background:#F8F2EA` · `justify-content:space-between` (nav arriba, tarjeta abajo) |
| bloque superior | `:38` | `flex-direction:column` · `gap:22px` |
| marca | `:39-42` | svg 26px trazo `#B4522F` + nombre del salón `.display` 23px · `gap:10px` · `padding:0 8px` |
| lista de destinos | `:43` | `flex-direction:column` · `gap:3px` |
| destino inactivo `.navitem` | `:18` | `height:40px` · `padding:0 12px` · `border-radius:8px` · `font-size:14px` · `color:#6B5C53` · icono 18px trazo 1.75 · `gap:10px` |
| destino activo `.navitem-on` | `:19` | idéntico + `font-weight:600` + `color:#B4522F` + `background:#F6E7E0` |
| tarjeta de usuario | `:71-77` | `padding:10px` · `border-radius:10px` · `border:1px solid #E7DCCF` · `background:#FFFFFF` · `gap:10px` |
| avatar | `:72` | 34px círculo · `background:#F6E7E0` · `color:#B4522F` · 12px/700 · iniciales |
| nombre / rol | `:74-75` | 13px/600 · 11px `#9A8A7E` |

**Los seis destinos, en este orden** (`:44-67`): Hoy · Citas · Clientes · Equipo · Servicios · Ajustes.

### Barra superior y contenido

| Elemento | Referencia | Forma y valores |
|---|---|---|
| barra superior | `EquipoDesktop.dc.html:82` | `height:72px` · `padding:0 28px` · `border-bottom:1px solid #E7DCCF` · `justify-content:space-between` |
| título | `:83` | `.display` 24px |
| CTA primario | `:84-87` | `height:38px` · `padding:0 18px` · `border-radius:8px` · `background:#B4522F` · blanco 14px/600 · icono 16px · `gap:8px` |
| contenido | `:90` | `padding:24px 28px` · `gap:18px` |
| ancho del contenido | `:92,:100` | `max-width:1084px` |

**Dos desviaciones del artboard, conscientes:**

1. `CalendarioDesktop.dc.html:74` usa `padding:0 24px` en vez de 28. Son 4px sobre una barra que en las otras cinco pantallas mide 28. **Se unifica a 28** y se anota en `todo.md` como ruido de P4. Un componente compartido con un `padding` distinto por pantalla es como se bifurca un sistema de diseño.
2. `DetalleEmpleadoDesktop.dc.html:90` usa `padding:0 28px 0 18px` cuando hay botón atrás. Eso **sí** se respeta: no es ruido, es que el botón aporta su propio aire a la izquierda. `PageShell` baja el padding izquierdo a 18px cuando recibe `back`.

### Cabecera móvil (56px)

| Elemento | Referencia | Forma y valores |
|---|---|---|
| cabecera, sin volver | `Main.dc.html:23`, `Clientes:25`, `Equipo:35`, `Ajustes:29` | `height:56px` · `padding:0 16px` · `border-bottom:1px solid #E7DCCF` |
| cabecera, con volver | `DetalleEmpleado.dc.html:40`, `DetalleCliente:33`, `AjustesSalon:34`, `AjustesCuenta:31`, `Horario:30` | igual, pero `padding:0 14px 0 8px` y `gap:4px` |
| botón volver | `DetalleEmpleado.dc.html:41-42` | caja 44×44 · flecha 20px trazo 2 · cumple de paso los 44px táctiles de `Estilo.dc.html:107-109` |
| acción a la derecha | `Horario.dc.html:30` | la cabecera usa `justify-content:space-between` solo cuando hay algo a la derecha ("Guardar") |

### Chequeo de primitivas

| Necesito | ¿Existe? | Qué hacer |
|---|---|---|
| SVG de la marca | **Sí** — `booking-salon-header.tsx:9-29`, ya exportado en `:55` | **Extraer** a `src/components/brand/salon-mark.tsx` y que `booking-salon-header.tsx` lo importe y lo siga re-exportando. Los imports y tests existentes siguen funcionando. |
| `Avatar` | Sí — `src/components/ui/avatar.tsx` | La tarjeta de usuario pinta iniciales sobre color plano, sin imagen. Se usa el `AvatarFallback` si encaja sin forzarlo; si no, un `div` de 34px es más honesto que doblar la primitiva. Decide el implementador **mirando el fichero**. |
| `Button` con la forma del CTA (h38, `px-18`) | Parcial | Las tallas reales son `default` = `h-8` (32px) y `lg` = `h-9` (36px) — `ui/button.tsx:26-30`. Ninguna da los 38px del artboard. **No se añade una talla aquí:** el CTA de la barra superior lo construyen las pantallas (bloques 3-7); este bloque solo lo deja pasar por el slot `actions`. Que la diferencia sea de 6px y no de 2 lo decidirá quien lo construya, con el dato correcto delante. |
| talla táctil 44/50px | Sí — `size="xl"` y `size="2xl"` en `ui/button.tsx` | Este bloque no añade CTAs móviles nuevos. La condición se hereda pero **no aplica**: no hay nada que convertir. Decirlo al cerrar, no callarlo. |

### Chequeo de tokens

Los del chasis **ya existen todos**, y coinciden con el artboard uno a uno:

| Artboard | Token | `globals.css` |
|---|---|---|
| `#F8F2EA` fondo lateral | `--sidebar` | `:130` |
| `#E7DCCF` borde | `--sidebar-border` / `--border` | `:136` / `:120` |
| `#F6E7E0` fondo del activo | `--sidebar-accent` / `--accent` | `:134` / `:117` |
| `#B4522F` terracota | `--sidebar-primary` / `--primary` | `:132` / `:111` |
| `#FBF7F2` fondo general | `--background` | `:105` |
| `#2A2320` texto | `--sidebar-foreground` | `:131` |
| `#9A8A7E` rol | `--muted-foreground-2` | `:142` |
| `#7A6A5F` | `--muted-foreground` | `:116` |

**Falta uno solo: `#6B5C53`**, el color del destino inactivo. Cero apariciones en `src/`. Es más oscuro que `--muted-foreground` (`#7a6a5f`), así que aproximarlo lo aclararía. Se añade como token en T1.

> Un custom property no definido **falla en silencio**: la declaración se descarta y la pantalla sale mal sin un solo error. Por eso el token va antes que el componente que lo usa.

---

## Estructura de ficheros

| Fichero | Responsabilidad | Acción |
|---|---|---|
| `src/components/brand/salon-mark.tsx` | El SVG de la marca, en un sitio que no sea del dominio de reserva pública | **Crear** |
| `src/components/booking/booking-salon-header.tsx` | Deja de definir el SVG; lo importa y lo re-exporta | Modificar (`:9-29`, `:55`) |
| `src/app/globals.css` | Un token: `--nav-foreground` | Modificar |
| `src/lib/nav/app-nav.ts` | Los seis destinos de escritorio, con su predicado de activo | **Crear** |
| `src/lib/nav/app-nav.test.ts` | Fija el predicado de Equipo contra Servicios | **Crear** |
| `src/components/layout/user-card.tsx` | Iniciales + etiqueta de rol neutra | **Crear** |
| `src/components/layout/user-card.test.tsx` | | **Crear** |
| `src/components/layout/app-sidebar.tsx` | La barra lateral entera, con su `<Suspense>` propio alrededor de la lectura de la query | **Crear** |
| `src/components/layout/app-sidebar.test.tsx` | | **Crear** |
| `src/components/layout/page-shell.tsx` | Barra superior + contenedor del contenido, montado por cada pantalla | **Crear** |
| `src/components/layout/page-shell.test.tsx` | | **Crear** |
| `src/app/(app)/layout.tsx` | Elige chasis por breakpoint y arregla `min-h-full` | Modificar (entero) |
| `src/app/(app)/layout.test.tsx` | | **Crear** |
| **Las DOCE pantallas de `(app)`** — `today`, `calendar`, `clients`, `clients/[id]`, `staff`, `staff/[id]`, `settings` y sus cinco subpáginas | Cada una monta `PageShell`. `staff/page.tsx` además pasa sus `Tabs` a controlados (`:22-24`, `:39`, `:58-60`) | Modificar |
| `src/app/(app)/appointments/new/page.tsx` | Solo el `sticky top-14` → `top-0`, consecuencia de borrar `AppHeader` | Modificar (`:32`) |
| `src/components/layout/app-header.tsx` | Su cabecera de 56px pasa a `PageShell` | **Eliminar** |
| `src/app/(app)/staff/page.test.tsx` | **No existe hoy.** Fija que a 390px no aparece encabezado y que cambiar de pestaña cambia el contenido | **Crear** |
| `visual/shell-vs-artboards.spec.ts` | Capturas a 390/768/1024/1440 | **Crear** |

`bottom-nav.tsx` y `fab-button.tsx` **no se tocan** (D5). **`app-header.tsx` se BORRA** (D2c): su cabecera de 56px es la que ahora pinta `PageShell`, y su único montaje está en `(app)/layout.tsx:25`. `appointments/new/page.tsx` tampoco (D4), ni `calendar/day-view.tsx` — las dos deudas que el bloque deja documentadas en vez de resolver a medias.

---

## Fases y paralelización

| Fase | Tareas | `paths_touched` | Depende de |
|---|---|---|---|
| **F1 — piezas sueltas** | T1, T2, T3 | `src/components/brand/**`, `src/components/booking/booking-salon-header.tsx`, `src/app/globals.css`, `src/lib/nav/**`, `src/components/layout/user-card*` | ninguna |
| **F2 — el slot** | T4 | `src/components/layout/page-shell*` | ninguna |
| **F3 — la barra lateral** | T5 | `src/components/layout/app-sidebar*` | T1, T2, T3 |
| **F4 — el layout** | T6 | `src/app/(app)/layout.tsx`, `src/app/(app)/layout.test.tsx` | T5 |
| **F5 — adopción** | T7a, T7b, T7c, T7d | `today` + `calendar` ‖ `clients/**` ‖ `staff/**` ‖ `settings/**` | T4, T6 |
| **F6 — verificación** | T8 | `visual/**`, `tasks/todo.md` | todas |

**Olas:** `(T1 ‖ T2 ‖ T3 ‖ T4) → T5 → T6 → (T7a ‖ T7b ‖ T7c ‖ T7d) → T8`

Los **ficheros** de F1 y F2 son disjuntos — comprobado, y además `src/components/layout/` no tiene barril `index.ts` que los una — así que cuatro agentes pueden trabajar a la vez sin worktree.

> **Lo que sí es compartido: `.git/index`.** Cuatro agentes en el mismo árbol de trabajo comparten el índice de git. Y serializar los commits **no basta**: `git commit` commitea el índice ENTERO, así que el primero que llegue se lleva lo que sus compañeros ya hayan puesto ahí, a medio escribir.
>
> Por eso **los diez pasos de commit de este plan** (T1..T6, T7a-T7d, T8) hacen siempre las dos cosas, en este orden:
>
> ```bash
> git add <sus rutas>
> git commit -o <sus rutas> -m "…"
> ```
>
> El `git add` es obligatorio porque **`git commit -o` falla sobre un fichero que git todavía no conoce** (`error: pathspec '…' did not match any file(s) known to git`), y casi todas estas tareas CREAN ficheros. Y el `-o` (`--only`) es obligatorio porque commitea exclusivamente esas rutas e ignora el resto del índice — sin él, el primer agente que commitee se lleva lo que sus compañeros ya hayan puesto ahí, a medio escribir. Nunca `git add -A`, y nunca un `git commit -m` a secas.

---

## Tareas

### Task 1: el token que falta y la marca compartida

**Files:**
- Create: `src/components/brand/salon-mark.tsx`
- Modify: `src/components/booking/booking-salon-header.tsx:9-29` y `:55`
- Modify: `src/app/globals.css`

- [ ] **Paso 1: añadir el token.** En `globals.css`, junto a los demás `--sidebar-*` del bloque `:root` (alrededor de `:130-137`):

```css
  --nav-foreground: #6b5c53; /* EquipoDesktop.dc.html:18 — destino inactivo de la barra lateral */
```

y exponerlo en `@theme inline`, junto a los `--color-sidebar-*` (alrededor de `:31-38`):

```css
  --color-nav-foreground: var(--nav-foreground);
```

- [ ] **Paso 2: mover el SVG.** Crear `src/components/brand/salon-mark.tsx` con el componente `SalonMark` **tal cual está hoy** en `booking-salon-header.tsx:9-29` — mismo markup, mismo `aria-hidden`, mismo `stroke="currentColor"`. Actualizar el comentario para que no hable solo de la reserva pública: la usan los dos chasis.

- [ ] **Paso 3: que booking lo importe.** En `booking-salon-header.tsx`, borrar la definición local y poner arriba `import { SalonMark } from "@/components/brand/salon-mark"`. **Conservar `export { SalonMark }` en `:55`**: hay imports y tests que dependen de él y no deben moverse en esta tarea.

- [ ] **Paso 4: comprobar que nada se rompió.**

```bash
npm run test -- --run src/components/booking
```
Esperado: la suite de `booking` en verde, igual que antes. Pegar la salida real.

- [ ] **Paso 5: commit.**

```bash
git add src/components/brand/salon-mark.tsx src/components/booking/booking-salon-header.tsx src/app/globals.css
git commit -o src/components/brand/salon-mark.tsx src/components/booking/booking-salon-header.tsx src/app/globals.css -m "Share the salon mark and add the nav-foreground token"
```

---

### Task 2: los seis destinos, con su predicado de activo

**Files:**
- Create: `src/lib/nav/app-nav.ts`
- Test: `src/lib/nav/app-nav.test.ts`

El punto entero de esta tarea es D5: **Equipo y Servicios comparten pathname y solo los distingue la query.** Un `startsWith` los enciende a los dos.

- [ ] **Paso 1: el test que falla primero.**

```ts
import { describe, it, expect } from "vitest"
import { APP_NAV_ITEMS } from "./app-nav"

function activeLabels(pathname: string, params: URLSearchParams) {
  return APP_NAV_ITEMS.filter((item) => item.isActive(pathname, params)).map((i) => i.label)
}

describe("APP_NAV_ITEMS", () => {
  it("son seis, en el orden del artboard", () => {
    expect(APP_NAV_ITEMS.map((i) => i.label)).toEqual([
      "Hoy", "Citas", "Clientes", "Equipo", "Servicios", "Ajustes",
    ])
  })

  /**
   * Equipo y Servicios comparten `/staff` y solo los separa `?tab=`. Con el
   * `pathname.startsWith` que usa la barra inferior se encenderian los dos a
   * la vez, que es justo lo que este predicado existe para evitar.
   */
  it("en /staff sin query se enciende Equipo y NO Servicios", () => {
    expect(activeLabels("/staff", new URLSearchParams())).toEqual(["Equipo"])
  })

  it("en /staff?tab=services se enciende Servicios y NO Equipo", () => {
    expect(activeLabels("/staff", new URLSearchParams("tab=services"))).toEqual(["Servicios"])
  })

  it("una ficha de empleado sigue siendo Equipo", () => {
    expect(activeLabels("/staff/emp_1", new URLSearchParams())).toEqual(["Equipo"])
  })

  it("nunca hay dos destinos encendidos a la vez", () => {
    // `/appointments/*` esta a proposito: es la unica ruta que ejercita el
    // `|| p.startsWith("/appointments")` del destino Citas.
    for (const path of ["/today", "/calendar", "/clients", "/clients/cli_1", "/staff", "/staff/emp_1", "/settings", "/settings/salon", "/appointments/new", "/appointments/apt_1"]) {
      expect(activeLabels(path, new URLSearchParams())).toHaveLength(1)
    }
  })
})
```

- [ ] **Paso 2: verlo fallar.** `npm run test -- --run src/lib/nav` → falla, el módulo no existe.

- [ ] **Paso 3: escribirlo.**

```ts
import { CalendarCheck, LayoutGrid, Users, UserRound, Scissors, Settings } from "lucide-react"
import type { LucideIcon } from "lucide-react"

export interface AppNavItem {
  href: string
  label: string
  icon: LucideIcon
  /**
   * Cada destino decide si esta activo. No vale un `startsWith` compartido:
   * Equipo y Servicios son la MISMA ruta (`/staff`) y solo los distingue
   * `?tab=services` (ver `staff/page.tsx:35-37`), asi que con una comparacion
   * de cadenas se encenderian los dos.
   */
  isActive: (pathname: string, params: URLSearchParams) => boolean
}

const isServicesTab = (params: URLSearchParams) => params.get("tab") === "services"

/** Los seis destinos de escritorio, en el orden de `EquipoDesktop.dc.html:44-70`. */
export const APP_NAV_ITEMS: readonly AppNavItem[] = [
  { href: "/today", label: "Hoy", icon: CalendarCheck, isActive: (p) => p.startsWith("/today") },
  { href: "/calendar", label: "Citas", icon: LayoutGrid, isActive: (p) => p.startsWith("/calendar") || p.startsWith("/appointments") },
  { href: "/clients", label: "Clientes", icon: User, isActive: (p) => p.startsWith("/clients") },
  { href: "/staff", label: "Equipo", icon: Users, isActive: (p, q) => p.startsWith("/staff") && !isServicesTab(q) },
  { href: "/staff?tab=services", label: "Servicios", icon: Scissors, isActive: (p, q) => p.startsWith("/staff") && isServicesTab(q) },
  { href: "/settings", label: "Ajustes", icon: Settings, isActive: (p) => p.startsWith("/settings") },
] as const
```

> **Iconos:** los seis salen del artboard (`EquipoDesktop.dc.html:45,49,53,57,61,65` — calendario con check, cuadrícula, persona, dos personas, tijeras, engranaje) y los seis nombres **están verificados** contra `node_modules/lucide-react/dist/lucide-react.d.ts`. No hace falta volver a comprobarlo.

- [ ] **Paso 4: verde.** `npm run test -- --run src/lib/nav` — pegar la salida.

- [ ] **Paso 5: commit**, con las rutas explícitas (ver la nota de la ola paralela): `git commit -o <rutas de esta tarea> -m "Define the six desktop nav destinations"`

---

### Task 3: la tarjeta de usuario

**Files:**
- Create: `src/components/layout/user-card.tsx`
- Test: `src/components/layout/user-card.test.tsx`

Referencia: `EquipoDesktop.dc.html:71-77` (avatar `:72`, nombre `:74`, rol `:75`). **No `:83-89`, que es la barra superior con su CTA** — la v1 lo citó mal ahí y el error sobrevivió a la primera corrección.

**Sobre el rol:** el artboard pone "Propietaria" porque su usuaria de ejemplo se llama María. `UserRole` **no lleva género** (`src/types/auth.ts:1`). Deducirlo del nombre sería inventar un dato sobre una persona real. Etiquetas neutras.

- [ ] **Paso 1: el test.**

```tsx
import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen } from "@testing-library/react"
import { UserCard } from "./user-card"

const mockUseAuth = vi.fn()
vi.mock("@/hooks/use-auth", () => ({ useAuth: () => mockUseAuth() }))

describe("UserCard", () => {
  beforeEach(() => {
    mockUseAuth.mockReturnValue({
      user: { id: "u1", name: "Maria Gil", email: "maria@bellavista.es", tenantId: "t1", role: "ROLE_SALON_OWNER", subscriptionPlan: "pro" },
    })
  })

  it("pinta las iniciales, el nombre y una etiqueta de rol neutra", () => {
    render(<UserCard />)
    expect(screen.getByText("MG")).toBeInTheDocument()
    expect(screen.getByText("Maria Gil")).toBeInTheDocument()
    expect(screen.getByText("Titular del salon")).toBeInTheDocument()
  })

  /**
   * El artboard dice "Propietaria" porque su ejemplo es Maria. `UserRole` no
   * lleva genero, asi que deducirlo del nombre seria inventarse un dato sobre
   * una persona real. Si alguien "corrige" la etiqueta al texto del artboard,
   * esto se pone rojo.
   */
  it("la etiqueta sale del rol, nunca del nombre", () => {
    mockUseAuth.mockReturnValue({
      user: { id: "u2", name: "Carlos Ruiz", email: "c@x.es", tenantId: "t1", role: "ROLE_EMPLOYEE", subscriptionPlan: "pro" },
    })
    render(<UserCard />)
    expect(screen.getByText("Equipo")).toBeInTheDocument()
    expect(screen.queryByText(/Propietari/)).not.toBeInTheDocument()
  })

  it("un nombre de una sola palabra da una sola inicial y no revienta", () => {
    mockUseAuth.mockReturnValue({
      user: { id: "u3", name: "Ada", email: "ada@x.es", tenantId: "t1", role: "ROLE_SALON_OWNER", subscriptionPlan: "pro" },
    })
    render(<UserCard />)
    expect(screen.getByText("A")).toBeInTheDocument()
  })

  it("sin usuario no pinta nada, en vez de una tarjeta vacia", () => {
    mockUseAuth.mockReturnValue({ user: null })
    const { container } = render(<UserCard />)
    expect(container).toBeEmptyDOMElement()
  })
})
```

- [ ] **Paso 2: verlo fallar.** `npm run test -- --run src/components/layout/user-card`

- [ ] **Paso 3: escribirlo.** Valores exactos del artboard: contenedor `p-2.5` (10px) · `rounded-[10px]` · `border` · `bg-card` · `gap-2.5`; avatar 34px círculo `bg-accent text-accent-foreground` 12px/700; nombre 13px/600; rol 11px `text-muted-foreground-2`. Iniciales: primera letra de la primera y de la última palabra, en mayúsculas, máximo dos. Mapa de roles:

```ts
const ROLE_LABELS: Record<UserRole, string> = {
  ROLE_SALON_OWNER: "Titular del salon",
  ROLE_EMPLOYEE: "Equipo",
  ROLE_PLATFORM_ADMIN: "Plataforma",
}
```

- [ ] **Paso 4: verde.** Pegar la salida.
- [ ] **Paso 5: commit**, con las rutas explícitas (ver la nota de la ola paralela): `git commit -o <rutas de esta tarea> -m "Add the sidebar user card"`

---

### Task 4: `PageShell` — la barra superior y el contenedor

**Files:**
- Create: `src/components/layout/page-shell.tsx`
- Test: `src/components/layout/page-shell.test.tsx`

Esto es D1. `PageShell` lo monta **cada pantalla**, no el layout.

**Contrato: el de D2b.** No se repite aquí para que no puedan divergir. Dos notas sobre por qué tiene las props que tiene:

- **`titleAdjacent` y `contentClassName` existen desde el principio.** D1 descarta el mapa de rutas porque no expresa los artboards; un slot que tampoco los exprese no vale más. Sin `titleAdjacent`, el bloque 3 tendría que mandar el navegador de fecha al extremo derecho, contra `CalendarioDesktop.dc.html:75`, o romper el contrato al mes de escribirlo. `contentClassName` se aplica a la capa **interna** del contenido (la de `flex flex-col gap-[18px] max-w-[1084px]`), nunca a la externa que fija el padding de 28px: `HoyDesktop:90` usa gap 20px y `AjustesDesktop:81` es una FILA de 28px sin `max-w`.
- **Lo que NO se parametriza es el padding horizontal.** Ahí se unifica a 28px, con las dos desviaciones ya justificadas más arriba.

**Móvil y escritorio son EXCLUYENTES.** jsdom no aplica CSS: un árbol duplicado escondido con `hidden` sigue rompiendo `getByRole` por ambigüedad. Se decide con el hook que ya existe, `useMediaQuery` (`src/hooks/use-media-query.ts:26`), igual que `booking-step-shell.tsx:8,65`, que ya resolvió esto. **No escribir un `matchMedia` a mano.** `window.matchMedia` está polyfillado en `src/test/setup.ts`.

- **En escritorio:** barra superior `h-[72px] border-b px-7` (`pl-[18px]` si hay `back`) con el título a la izquierda y `actions` a la derecha; debajo, `px-7 py-6` y el contenido en un `flex flex-col gap-[18px] max-w-[1084px]`.
- **Por debajo de 1024:** la cabecera de 56px del artboard (D2b) — `h-14 border-b`, `px-4` sin botón atrás y `pl-2 pr-3.5` con él — y debajo el contenedor `p-4 md:py-6`. El título es el `<h1>` de la cabecera; `actions` va a la derecha.

- [ ] **Paso 1: el test.** `src/test/setup.ts:23-34` devuelve `matches: false` **siempre**, así que cada prueba de escritorio necesita su propio `mockMatchMedia(true)` local. El patrón está tres veces en el repo: `booking-step-shell.test.tsx:24`, `public-datetime-step.test.tsx:19`, `public-employee-step.test.tsx:71`. Cubre:
  - en escritorio, `getByRole("heading", { name: title })` lo encuentra, y `actions` está presente;
  - el mismo `title` sale en los dos anchos, en la cabecera de 56px y en la barra superior de 72px;
  - **`back` y `desktopBack` son independientes**: con `back` y sin `desktopBack` hay control de volver (`getByRole("button", { name: /volver/i })`) por debajo de 1024 y **no** en escritorio. Es el caso de las seis subpáginas de ajustes, y si alguien los fusiona en una sola prop esto se pone rojo;
  - **el árbol no se duplica.** Contar headings por nombre no basta como única red —el título es el mismo en los dos anchos, así que un duplicado sí sería ambiguo, pero solo si la pantalla tiene título—; lo que de verdad se duplicaría son los `children`, la pantalla entera. Lo que de verdad se duplicaría son los `children` — la lista entera de la pantalla. Así que el contenedor de contenido lleva `data-slot="page-shell-content"` (la convención del repo, ver `ui/tabs.tsx:15`) y el test cuenta con `container.querySelectorAll('[data-slot="page-shell-content"]')`, que debe dar 1 en los dos breakpoints.
    > **No `getAllByTestId`**: Testing Library busca `data-testid`, y `src/test/setup.ts` solo configura `asyncUtilTimeout` (`:12`), nunca `testIdAttribute`. La consulta no encontraría nada y el test sería rojo permanente.
    > No copiar el truco de `booking-step-shell.test.tsx:128-146`: allí el par duplicado era simétrico (`aside` y `footer`, ambos con un botón "Continuar"). Aquí no lo es, y la asimetría es justo lo que hace inútil la consulta por heading.
  - `titleAdjacent` se monta **junto al título** y no dentro del cluster de `actions`;
  - el control de volver lleva `aria-label="Volver"`. Sin nombre accesible la consulta por rol no lo encuentra, y los botones de volver que hay hoy en el repo son iconos sueltos sin nombre (`clients/[id]:42-44`) — así que hay que ponerlo, no heredarlo.

- [ ] **Paso 2: verlo fallar.**
- [ ] **Paso 3: escribirlo.** Antes de escribir nada de Next, leer `node_modules/next/dist/docs/` — `AGENTS.md` avisa de que este no es el Next que uno cree conocer.
- [ ] **Paso 4: verde.** Pegar la salida.
- [ ] **Paso 5: commit**, con las rutas explícitas (ver la nota de la ola paralela): `git commit -o <rutas de esta tarea> -m "Add the desktop page shell"`

---

### Task 5: la barra lateral

**Files:**
- Create: `src/components/layout/app-sidebar.tsx`
- Test: `src/components/layout/app-sidebar.test.tsx`

**Depende de T1, T2 y T3.** Consume `SalonMark`, `APP_NAV_ITEMS` y `UserCard`.

Referencia: `EquipoDesktop.dc.html:37-89`. Valores en el inventario visual de arriba.

- [ ] **Paso 1: el test.** Cubre:
  - los seis destinos, en orden, como enlaces (`getAllByRole("link")`);
  - el nombre del salón sale de `useSalon()`, no está escrito a mano;
  - en `/staff` sin query, Equipo lleva `aria-current="page"` y Servicios no; con `?tab=services`, al revés (mockeando `usePathname` y `useSearchParams` de `next/navigation`);
  - la tarjeta de usuario está montada.

  > El estado activo se marca con **`aria-current="page"`**, no solo con clases: es lo que hace que la aserción signifique algo y que un lector de pantalla sepa dónde está. El color por sí solo no se puede afirmar en jsdom, que no aplica CSS.

- [ ] **Paso 2: verlo fallar.**
- [ ] **Paso 3: escribirlo.** `<aside>` con `sticky top-0 h-dvh w-[248px] shrink-0 border-r bg-sidebar px-3.5 py-5 flex flex-col justify-between`. El `sticky top-0 h-dvh` no es opcional: el artboard es un marco de `1440×900` con `overflow:hidden` (`EquipoDesktop:35`), así que la barra mide lo que la ventana por construcción. Sin eso, dentro de `flex min-h-dvh` el `<aside>` se estira a la altura del **documento**, y en `/clients` con cincuenta filas la navegación y la tarjeta de usuario se van fuera de la pantalla. Dentro, **un envoltorio intermedio** con `gap-[22px]` que agrupa la marca y la lista (`EquipoDesktop.dc.html:38`): sin él, la separación entre marca y lista la decide el `justify-between` y no serán 22px. Destinos: `h-10 px-3 rounded-lg text-sm gap-2.5 text-nav-foreground`; activo `font-semibold text-sidebar-primary bg-sidebar-accent`. Iconos `size-[18px]`.

- [ ] **Paso 3b: el `<Suspense>`, y esto NO es opcional.** La parte que llama a `useSearchParams` va envuelta en su propio `<Suspense>` **dentro de `app-sidebar.tsx`**, con un fallback que pinte la lista sin ningún destino encendido (misma altura, para que no salte).

  Sin ese boundary, Next trata el uso como error de build. Y como la barra lateral la monta el **layout**, el bailout no se queda en una página: sube por encima de las catorce del grupo y **rompe el build de todas**. El repo ya pagó esa cicatriz una vez y la dejó escrita en `staff/page.tsx:18-20`. Ningún test de Vitest lo detecta, porque jsdom no prerenderiza: lo detecta `npm run build`, que T8 ejecuta.
- [ ] **Paso 4: verde.** Pegar la salida.
- [ ] **Paso 5: commit**, con las rutas explícitas (ver la nota de la ola paralela): `git commit -o <rutas de esta tarea> -m "Add the 248px desktop sidebar"`

---

### Task 6: el layout elige chasis

**Files:**
- Modify: `src/app/(app)/layout.tsx` (los 32 lineas, entero)
- Test: `src/app/(app)/layout.test.tsx` (crear)

**Depende de T5.** Dos cosas, las dos en este fichero: el chasis por breakpoint (D3) y el `min-h-full` inerte. **La exclusión de `/appointments/new` NO entra** — ver D4: quitarle el shell sin darle su cabecera propia lo deja peor que ahora, y también en móvil.

- [ ] **Paso 1: el test.** Las pruebas de escritorio necesitan su `mockMatchMedia(true)` local con su `afterEach` de restauración (patrón en `public-employee-step.test.tsx:69-71`): el polyfill global de `src/test/setup.ts:23-34` devuelve `matches: false` **siempre**. Cubre:
  - **por debajo de 1024**: la barra inferior está y la barra lateral no. **`AppHeader` ya no se monta** (D2c): si siguiera, más la cabecera de `PageShell`, serían 112px donde el artboard dibuja 56;
  - **en 1024+**: la barra lateral está, y la cabecera móvil y la barra inferior **no** (montaje excluyente, no `hidden`);
  - el botón flotante aparece en `/today` y `/calendar` **en los dos anchos** (punto 4 de abajo): sin él, en escritorio no hay forma de crear una cita hasta que su bloque ponga el CTA en la barra superior;
  - `/appointments/new` conserva su cabecera propia, ahora pegada arriba del todo: al desaparecer `AppHeader` (D2c) su `sticky top-14` pasa a `top-0`: es la ruta cuyo interior depende de `top-14` y de `calc(100vh-8rem)` (`appointments/new/page.tsx:30` y `:32`), y este bloque no la toca;
  - `OnboardingGate` sigue envolviendo en todos los casos.

- [ ] **Paso 2: verlo fallar.**

- [ ] **Paso 3: escribirlo.** Forma esperada:

```tsx
export default function AppLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const isDesktop = useMediaQuery("(min-width: 1024px)")
  const showFab = FAB_ROUTES.some((r) => pathname.startsWith(r))
  const { onTouchStart, onTouchEnd } = useSwipeNavigation()

  if (isDesktop) {
    return (
      <OnboardingGate>
        <div className="flex min-h-dvh">
          <AppSidebar />
          <main className="flex min-w-0 flex-1 flex-col">{children}</main>
        </div>
      </OnboardingGate>
    )
  }

  return (
    <OnboardingGate>
      <div className="flex min-h-dvh flex-col" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
        <main className="mx-auto w-full max-w-3xl flex-1 pb-20">{children}</main>
        {showFab && <FabButton />}
        <BottomNav />
      </div>
    </OnboardingGate>
  )
}
```

**Seis detalles que no son cosméticos:**
1. `min-h-dvh` sustituye a `min-h-full`. El diagnóstico exacto: `html` **sí** tiene `h-full` (`src/app/layout.tsx:42`), pero `body` solo lleva `min-h-full` (`:44`), así que su `height` computa `auto` y el `min-height:100%` del hijo resuelve a 0. Mismo fallo ya corregido en el alta reanudable y en la reserva pública.
   **Esto cambia el móvil** — como lo cambia la cabecera de 56px (D2b), porque en este bloque el móvil converge hacia el artboard: hoy la regla es inerte y el contenedor mide lo que mida su contenido; con `min-h-dvh` pasa a medir al menos la altura de la ventana y el `main flex-1` estira de verdad. Es el arreglo correcto —el pie deja de flotar en pantallas con poco contenido—, pero **hay que declararlo**, porque el revisor de regresión móvil de T8 lo va a marcar, y con razón.
2. En escritorio **desaparece el gesto de deslizar**. Es navegación táctil entre pestañas de la barra inferior; sin barra inferior no significa nada, y en un portátil con pantalla táctil sería un salto de pantalla fantasma.
3. `pb-20` sigue **solo** en el árbol móvil: es el hueco para la barra inferior fija. En escritorio dejaría un vacío de 80px al pie.
4. **El botón flotante no tiene sustituto todavía.** Y al conservarlo en escritorio hay que mirarle el `bottom-20` de `fab-button.tsx:10`, que es el hueco de la barra inferior: sin ella flota 80px por encima del borde. Quitarlo en escritorio deja `/today` y `/calendar` **sin ninguna forma de crear una cita** a 1440: el enlace "Crear cita" de `today/page.tsx:171-174` solo se pinta en el estado vacío, `/appointments/new` no es un destino de la barra lateral, y los CTA de la barra superior son de los bloques 3-7 (D2). O el botón flotante se queda en escritorio hasta que llegue su CTA, o T7a pasa un `actions` mínimo. **Se queda**, que es lo conservador: un botón flotante feo en escritorio es mejor que un producto donde no se puede crear una cita.
5. **`/calendar` tiene otro número calado a mano.** `src/components/calendar/day-view.tsx:21` es `<ScrollArea className="h-[calc(100vh-16rem)]">`: 256px descontados para cabecera 56 + barra inferior 64 + el resto del cromo móvil. En escritorio ese cromo no existe, así que el área de scroll queda mal dimensionada. **No se arregla aquí** —es del bloque 3, que reconstruye el calendario— pero T8 lo captura a 1440 para que quede constancia, igual que el asistente.
6. `showFab` se calcula igual para los dos árboles y **el botón flotante se monta también en escritorio** (punto 4). Cuando el bloque que corresponda ponga el CTA en la barra superior, se retira de ahí.

- [ ] **Paso 4: verde.** Pegar la salida.
- [ ] **Paso 5: la suite entera.** `npm run test -- --run`. Esperado: **275 de línea base + los nuevos**, cero regresiones. Si algo se pone rojo, **la causa raíz primero** — no ajustar el test para que pase.
- [ ] **Paso 6: commit.** `git commit -o "src/app/(app)/layout.tsx" "src/app/(app)/layout.test.tsx" -m "Give the app layout a desktop chassis"`

---

### Task 7: adoptar `PageShell` en las trece pantallas, y arreglar el destino "Servicios"

**Depende de T4 y T6.** Esto es D2. Se reparte en **cuatro grupos de ficheros disjuntos que corren en paralelo**; cada uno es su propio agente y su propio commit.

| Grupo | Ficheros | Título de la barra superior |
|---|---|---|
| **T7a** | `today/page.tsx`, `calendar/page.tsx` | del artboard: `HoyDesktop:76`, `CalendarioDesktop:76` |
| **T7b** | `clients/page.tsx`, `clients/[id]/page.tsx` | "Clientes" (`ClientesDesktop:74`), nombre del cliente |
| **T7c** | `staff/page.tsx` (+ su test, + los `Tabs`), `staff/[id]/page.tsx` | "Equipo"; y **el nombre del empleado**, no "Detalle empleado" (título unificado, D2b). `staff/[id]` lleva `back` y `desktopBack` |
| **T7d** | `settings/page.tsx` y las cinco subpáginas (`account`, `billing`, `booking`, `business-hours`, `salon`) | **su propio nombre** en los dos anchos (D2b). Las **cinco subpáginas** llevan `back` y **no** `desktopBack` (en escritorio la salida es la subnav de 210px); `/settings` no lleva ninguno de los dos (`Ajustes:29`) |

**Dos rutas quedan fuera de T7:**
- `appointments/new/page.tsx` (D4). Solo se le ajusta el `sticky top-14` que queda huérfano al desaparecer `AppHeader` (D2c); lo demás es de su bloque.
- `appointments/[id]/page.tsx`: **no tiene artboard de pantalla en ningún ancho**. `DetalleCita.dc.html:36` es una hoja inferior (`border-radius:16px 16px 0 0`, con asa de arrastre) sobre el calendario, y `DetalleCitaDesktop:79` es la barra superior del **calendario** con un panel acoplado al lado. No es una página con cabecera propia: es del bloque 4.

> **`AjustesNotificaciones` está dibujado, móvil y escritorio, pero NO tiene ruta** en `src/app/(app)/`. Los "trece artboards" y las "trece pantallas" no son el mismo conjunto — la de notificaciones es FE.11, no de este bloque.

**Las props que hay que asignar, y que si no se dicen aquí nadie asigna:**

| Pantalla | Props además de `title` |
|---|---|
| `/today` | `subtitle` = fecha · hora (solo escritorio, `HoyDesktop:77`); `actions` = refrescar + CTA; `mobileActions` = usuario y avatar (`Main:25-29`). **La línea de fecha del cuerpo (`today/page.tsx:84-86`) no cabe en móvil**: se queda donde está y solo sube a `subtitle` en escritorio |
| `/calendar` | `titleSize="lg"` (26px, `CalendarioDesktop:76`); `titleAdjacent` = navegador ‹ Hoy › ; `actions` = segmentado + búsqueda + CTA |
| `/clients/[id]` | `titleSize="lg"` (26px); "Cliente desde…" va **inline** junto al título con `gap:12px` (`DetalleClienteDesktop:83-85`), **no** en `subtitle`, que es la columna de `HoyDesktop:77`. Usa `titleAdjacent` |
| `/settings/business-hours` | `mobileActions` = "Guardar" (`Horario:37`). **En escritorio NO va a la barra superior**: el artboard lo deja en el cuerpo (`HorarioDesktop:126`, "Guardar cambios") |
| `/clients` · `/staff` | `actions` = el CTA que ya tienen en el cuerpo |

**El cambio, en cada pantalla:**

- [ ] **Paso 1: envolver.** Cambiar el `<div>` raíz (`p-4 md:py-6`, o `p-4` a secas en `clients/[id]:39` y `staff/[id]:117`) por `<PageShell title="…">`, moviendo dentro lo que había.
- [ ] **Paso 2: entregar la cabecera.** Todo lo que hoy haga de cabecera **se borra del cuerpo** y se expresa con las props: el título por `title`, la flecha de volver por `back`, y lo que hubiera a la derecha por `actions`. Las tres formas actuales desaparecen; queda la del artboard (D2b). **El título sale de la tabla de títulos unificados de D2b, NO del artboard**: es la única cosa que el usuario decidió apartar del diseño, y el artboard móvil de Hoy ("Bella Vista") y el de Citas ("Citas") son justo dos de los que se desvían. Del artboard se calca todo lo demás — forma, padding, flecha y acciones: `Main:23`, `Calendario:25`, `Clientes:25`, `Equipo:35`, `Ajustes:29`, `DetalleCliente:33`, `DetalleEmpleado:40`, `AjustesSalon:34`, `AjustesReserva:31`, `AjustesFacturacion:32`, `AjustesCuenta:31`, `Horario:30` ("Guardar" a la derecha).
- [ ] **Paso 3: el mismo contenedor para TODOS los estados de carga.** No solo el `<Suspense>` de `staff/page.tsx:23`: también los retornos tempranos de `clients/[id]:33`, `staff/[id]:110`, `settings/billing:64` y `settings/salon:71`. Si el esqueleto no monta `PageShell`, la pantalla salta de un contenedor a otro —y en escritorio, de sin barra superior a con ella— justo al terminar de cargar.

**Solo en T7c, además:**

- [ ] **Paso 4: `Tabs` controlado por la query.** Hoy `<Tabs defaultValue={initialTab}>` (`staff/page.tsx:60`) es **no controlado**: `defaultValue` solo se lee al montar. Estando ya en `/staff`, pulsar "Servicios" en la barra lateral no remonta el componente — la URL cambiaría, el destino se encendería y el contenido seguiría en Empleados.

```tsx
const tab = searchParams.get("tab") === "services" ? "services" : "employees"
...
<Tabs value={tab} onValueChange={(v) => router.replace(`/staff?tab=${v}`, { scroll: false })}>
```

`value` y `onValueChange` son las props correctas de la primitiva (`@base-ui/react/tabs`, `TabsRoot.d.ts`), y `ui/tabs.tsx:12` las deja pasar por spread. `replace` y no `push`: cambiar de pestaña no es un paso que el botón "atrás" deba deshacer uno a uno. Comprobar en `node_modules/next/dist/docs/` la firma de `useRouter` en Next 16 antes de escribirlo.

- [ ] **Paso 5: crear `staff/page.test.tsx`,** que **no existe** — el único test bajo `staff/` es `staff/[id]/page.test.tsx`, que es la ficha de empleado. Un `npm run test -- src/app/\(app\)/staff` lo capturaría y daría verde sin probar nada de este cambio. Cubre: a 390px **no** aparece encabezado "Equipo"; en escritorio sí, en la barra superior; y al cambiar de pestaña **el contenido cambia** — con el mock con estado que describe T4, no con el mock inerte del repo.

**Todos los grupos:**

- [ ] **Paso 6: los tests de sus pantallas.** `npm run test -- --run <sus rutas>` — pegar la salida real. Varias de estas pantallas ya tienen test; esperado, verde sin tocarlos.
- [ ] **Paso 7: commit,** con rutas explícitas: `git commit -o <sus ficheros> -m "…"`.

### Task 8: verificación

**Files:**
- Create: `visual/shell-vs-artboards.spec.ts`
- Modify: `E:\IdeaProjects\rivoo\tasks\todo.md`

- [ ] **Paso 1: la suite entera, con evidencia.**

```bash
npm run test -- --run
npx tsc --noEmit
npm run lint
npm run build
```
Pegar la salida REAL de las cuatro. Línea base: 275 tests / 50 ficheros, `tsc` limpio, lint 0 errores y 25 avisos preexistentes.

**`npm run build` es nuevo y es el que importa aquí.** Ninguna tarea de la v1 de este plan lo ejecutaba, y es el único que ve el fallo de `useSearchParams` sin `<Suspense>` (T5 paso 3b): `tsc` compila, la suite pasa en verde y el build es lo único que se cae. Un bloque que toca el layout de catorce rutas no se cierra sin haberlo construido una vez.

**Nunca escribir "pasa" sin la salida.**

- [ ] **Paso 2: las precondiciones, que no son opcionales.** Antes de una sola captura: la pila arrancada, `RIVOO_E2E_EMAIL`/`RIVOO_E2E_PASSWORD` puestas, y el salón E2E con **`onboarding_completed_at` NO nulo**. Si es `NULL`, `OnboardingGate` (`onboarding-gate.tsx:42-43`) redirige a `/welcome` y las capturas de `/staff`, `/today` y las demás fotografiarían el asistente de alta en vez de la pantalla.
  > Ojo: `visual/onboarding-vs-artboards.spec.ts:16-17` exige justo lo contrario para sus propias capturas. Las dos suites no pueden correr con el mismo estado de base de datos; ejecutarlas por separado y dejarlo escrito en el spec.

- [ ] **Paso 3: capturas.** `visual/shell-vs-artboards.spec.ts`, con `channel: "chrome"` (ya configurado en `playwright.config.ts`), a **390, 768, 1024 y 1440**:
  - `/staff` construida, los cuatro anchos;
  - `design/EquipoDesktop.dc.html` como referencia a 1440;
  - `/today`, `/clients` y `/settings` a 1024 y 1440 — tres de las trece que adoptan `PageShell` en T7, para comprobar que ninguna quedó a sangre;
  - **las doce a 390**, contra su artboard móvil. Este bloque cambia la cabecera de las trece, así que la comparación móvil ya no es un control de regresión sino la verificación principal: la cabecera de 56px tiene que estar calcada, con su padding correcto según lleve o no botón de volver;
  - de referencia, a 390: `design/Main.dc.html`, `Clientes.dc.html`, `Equipo.dc.html`, `DetalleEmpleado.dc.html`, `Ajustes.dc.html`, `Horario.dc.html`;
  - `/appointments/new` a 390 y 1440. A 390, **idéntica a hoy**; a 1440 saldrá con barra lateral y sin ancho máximo, que es la deuda que D4 deja a propósito;
  - `/calendar` a 1440 — su `ScrollArea` de `h-[calc(100vh-16rem)]` (`day-view.tsx:21`) quedará mal dimensionada en escritorio. Es del bloque 3; la captura lo deja por escrito.

  Las dos últimas capturas no documentan un éxito, sino una deuda concreta. Sin ellas, el bloque siguiente se encuentra el problema en vez de heredarlo.

  **Ancla de espera: contenido real de la pantalla.** El chasis se pinta en el primer render; esperar solo por él captura el esqueleto. En `/staff`, esperar por una fila de empleado.
  > **Matiz que este bloque introduce, y que hay que respetar:** `use-media-query.ts` devuelve `false` en el primer render, así que en escritorio se pinta primero el chasis **móvil** y luego se cambia. Una fila de empleado existe en los dos, así que esperar solo por ella puede capturar el chasis móvil a 1440. Para las capturas de 1024 y 1440 hay que esperar por **las dos cosas**: la fila de empleado **y** la barra lateral. No es una excepción a la regla: es que aquí la barra lateral no es chasis heredado, es lo que el bloque construye.

- [ ] **Paso 4: comparar de verdad.** Abrir las capturas de 1440 al lado del artboard y recorrer elemento por elemento: anchura de la barra lateral, los seis destinos y su orden, el encendido, la tarjeta de usuario, la altura de la barra superior, los 1084px del contenido. **Esto es lo que en los dos bloques anteriores destapó defectos que no vieron ni cinco revisores ni la suite entera.** No es un trámite.

- [ ] **Paso 5: el panel.** Tres revisores **independientes y en paralelo**, cada uno un agente NUEVO, ninguno el implementador, **instruidos para REFUTAR**:
  - **corrección**: ¿el estado activo miente en algún caso? ¿queda alguna pantalla sin salida en escritorio? ¿el asistente se escapa del `OnboardingGate`?
  - **fidelidad móvil**: por debajo de 1024, ¿la cabecera de cada pantalla coincide con su artboard —altura, padding, botón de volver, acción a la derecha— o se ha colado algo del código antiguo? Y al revés: ¿se perdió por el camino alguna acción que la pantalla sí tenía?
  - **fidelidad visual**: contra `EquipoDesktop.dc.html`, con las capturas delante.

  Se descarta un hallazgo si la mayoría lo refuta.

- [ ] **Paso 6: cerrar en `todo.md`.** Marcar el bloque y anotar las deudas que deja, cada una con su destinatario:
**la fecha duplicada en `/calendar`** — el título unificado es la fecha, y `calendar/page.tsx:47-52` ya la pinta en el cuerpo con `DateNavigator`; sale dos veces en los dos anchos hasta que el bloque 3 reconstruya la pantalla; la cabecera propia de `/appointments/new` con sus dos números calados (`top-14`, `calc(100vh-8rem)`) para el bloque del asistente; el `h-[calc(100vh-16rem)]` de `day-view.tsx:21` para el bloque 3; y los 4px del padding de `CalendarioDesktop` como ruido de P4.

---

## Execution Order

**Frontend (`E:\IdeaProjects\rivoo-frontend`), único subsistema:**

```
F1  piezas sueltas    T1 marca + token   ┐
                      T2 destinos        │ sin dependencias,
                      T3 tarjeta usuario │ caminos disjuntos:
F2  el slot           T4 PageShell       ┘ los cuatro EN PARALELO
F3  barra lateral     T5 AppSidebar        depende de T1, T2, T3
F4  el layout         T6 (app)/layout      depende de T5
F5  adopción         T7a today+calendar  ┐
                     T7b clients/**       │ dependen de T4 y T6;
                     T7c staff/**         │ ficheros disjuntos:
                     T7d settings/**      ┘ los cuatro EN PARALELO
F6  verificación      T8                   depende de todas
```

**Coordinación:** no hay segundo subsistema con el que coordinar — el backend no se toca. La verificación visual es la última tarea y no se puede adelantar: necesita el layout ya montado para que las capturas signifiquen algo. La revisión se lanza **al terminar el bloque entero**, como panel de tres, nunca por tarea.

## Dependencias con otras specs

| Spec / bloque | Relación | Implicación |
|---|---|---|
| **Carril B — reserva pública en escritorio** (`docs/specs/reserva-escritorio/`) | **Pre-requisito, ya cerrado** (`c174e46`) | De ahí salen `SalonMark` (que T1 extrae) y el patrón de montaje excluyente con `matchMedia` que T4 y T6 repiten. También las tallas 44/50px de `ui/button.tsx`, que este bloque hereda pero no usa. |
| **Bloque 3 — Calendario** | **Consumidor** | Montará `PageShell` con `titleSize="lg"`, el navegador de fecha en **`titleAdjacent`** (va pegado al título con `gap:16px`, `CalendarioDesktop.dc.html:75`) y en `actions` lo que de verdad va a la derecha: el segmentado Día/Semana, la búsqueda y el CTA (`:88-100`). Hereda además el `h-[calc(100vh-16rem)]` de `day-view.tsx:21`. |
| **Bloque 4 — Detalle de cita** | **Consumidor** | Panel acoplado de 360px sobre el calendario; necesita el contenedor de escritorio que monta T6. |
| **Bloque 5 — Hoy** | **Consumidor** | `PageShell` con `subtitle` y dos acciones (`HoyDesktop.dc.html:74-86`). |
| **Bloque 6 — Equipo y clientes** | **Consumidor y complementario** | Este bloque deja las cuatro pantallas ya montadas sobre `PageShell` (T7b, T7c); el bloque 6 reconstruye su interior (tabla de escritorio, `colorHex` visible, diálogos modales) sin volver a tocar el chasis. Suyo es también decidir si el móvil de Equipo gana la barra de 56px que dibuja `Equipo.dc.html:35-37`. |
| **Bloque 7 — Ajustes** | **Consumidor** | `PageShell` sin `actions` + su subnav propia de 210px (`AjustesDesktop.dc.html:81-89`). |
| **Bloque de nueva cita** (sin plan aún) | **Consumidor, y hereda una tarea** | Este bloque **no** le quita el shell (D4). El suyo será: darle la cabecera propia de 68px (`NuevaCitaDesktopPaso1.dc.html:29`), ajustar el `top-14` y el `min-h-[calc(100vh-8rem)]` de `appointments/new/page.tsx:30-31` que hoy dependen del chasis móvil, y entonces sí sacarlo del shell — por la vía de `(fullscreen)/appointments/new`, que está comprobado que no colisiona con `[id]`. |
| **CV.13 / condiciones de cierre** (`tasks/todo.md`) | **Complementaria** | La talla táctil no aplica aquí: este bloque no añade ningún CTA móvil. Decirlo al cerrar en vez de callarlo. |
