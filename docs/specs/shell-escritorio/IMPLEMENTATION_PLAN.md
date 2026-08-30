# Shell de escritorio — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: `executing-plans`. Los pasos usan `- [ ]`.

**Objetivo:** dar a la app interna el chasis que dibujan los artboards, en los dos anchos: barra lateral 248px + barra superior 72px en escritorio, cabecera de 56px en móvil, seis destinos de navegación. Aplicado a las doce pantallas de `src/app/(app)/`.

**Complejidad:** compleja (10+ ficheros, transversal). Motor: `executing-plans`.

**Repo:** `E:\IdeaProjects\rivoo-frontend`. Único. El backend no se toca; excepción, escribir en `E:\IdeaProjects\rivoo\tasks\todo.md`.

**Línea base:** 275 tests / 50 ficheros en verde, `tsc` limpio, lint 0 errores + 25 avisos. Medida el 2026-08-29.

---

## Cómo leer este documento

**Cada hecho está en un solo sitio.** Las tareas no repiten valores: los referencian. Si vas a implementar una tarea, lee §1 (datos) y §2 (decisiones) una vez, y luego tu tarea.

Este plan es la v6. Las cinco anteriores acumularon cinco revisiones bloqueantes: las primeras por errores de diseño, las últimas porque el documento repetía cada hecho hasta diecinueve veces y las correcciones nunca llegaban a todas las copias. Por eso ahora no hay copias. El histórico está en `_v5-descartado.md`.

---

## §1 — Datos verificados

Todo comprobado contra el código y los artboards el 2026-08-29. Nada de esto es memoria.

### 1.1 Estado del código

| Hecho | Dónde |
|---|---|
| El layout de la app no tiene ni una clase responsive | `src/app/(app)/layout.tsx` (32 líneas) |
| `AppHeader` es `h-14` = **56px**, salón a la izquierda y usuario a la derecha; se monta en un solo sitio | `app-header.tsx:12`, montado en `(app)/layout.tsx:25` |
| Barra inferior, 4 pestañas: Hoy · Citas · Equipo · Mas | `bottom-nav.tsx:7-12` |
| `min-h-full` es inerte: `html` sí tiene `h-full`, pero `body` solo `min-h-full`, así que su altura computa `auto` y el `min-height:100%` del hijo resuelve a 0 | `src/app/layout.tsx:42,44` + `(app)/layout.tsx:21` |
| `useMediaQuery` existe y devuelve `false` en el primer render | `src/hooks/use-media-query.ts:26,30` |
| El SVG de la marca existe y se exporta | `booking-salon-header.tsx:9-29,55` |
| `useAuth()` da `{id,name,email,tenantId,role,subscriptionPlan}`; `UserRole` son tres constantes sin género | `use-auth.ts:7-14`, `types/auth.ts:1` |
| No existe `/services`: Servicios es una pestaña de `/staff`, y `?tab=services` ya aterriza en ella | `staff/page.tsx:39,60` |
| Esos `<Tabs>` son **no controlados** (`defaultValue` solo se lee al montar) | `staff/page.tsx:60` sobre `ui/tabs.tsx` |
| `useSearchParams` exige su propio `<Suspense>` o el build falla. Único uso en `src/`, con su boundary | `staff/page.tsx:18-20` (el porqué), `:22-24` (el boundary) |
| `appointments/new` depende del chasis móvil con dos números a mano | `appointments/new/page.tsx:30` (`min-h-[calc(100vh-8rem)]`) y `:32` (`sticky top-14`) |
| `day-view.tsx` tiene un tercero | `day-view.tsx:21` (`h-[calc(100vh-16rem)]`) |
| `fab-button` se posiciona contra la barra inferior | `fab-button.tsx:10` (`bottom-20`) |
| No existe `staff/page.test.tsx`; el que hay es de la ficha, otra pantalla | `staff/[id]/page.test.tsx` |
| El mock de `next/navigation` del repo es inerte (`replace: vi.fn()`) | `staff/[id]/page.test.tsx:8-10`, y otros diez ficheros con la misma forma |
| `src/test/setup.ts` configura `asyncUtilTimeout` (`:12`) y un `matchMedia` que devuelve **siempre** `matches:false` (`:23-34`). No configura `testIdAttribute` | — |
| Tallas de botón: `default` h-8 (`:26-27`), `lg` h-9 (`:30`), `xl` h-11 = 44px (`:31`), `2xl` h-[50px] (`:32`) | `ui/button.tsx` |

### 1.2 El chasis, transcrito del artboard

Fuente: `design/EquipoDesktop.dc.html`.

| Elemento | Ref | Valores |
|---|---|---|
| barra lateral | `:37` | `width:248px` · `flex-shrink:0` · `padding:20px 14px` · `border-right:1px solid #E7DCCF` · `background:#F8F2EA` · `justify-content:space-between` |
| envoltorio superior | `:38` | `gap:22px` (agrupa marca + lista; sin él la separación la decide el `space-between`) |
| marca | `:39-42` | svg 26px `#B4522F` + nombre del salón `.display` 23px · `gap:10px` · `padding:0 8px` |
| lista | `:43` | `gap:3px` |
| destino | `:18` | `height:40px` · `padding:0 12px` · `radius:8px` · 14px · `color:#6B5C53` · icono 18px trazo 1.75 · `gap:10px` |
| destino activo | `:19` | + `font-weight:600` · `color:#B4522F` · `background:#F6E7E0` |
| destinos, en orden | `:44-67` | Hoy · Citas · Clientes · Equipo · Servicios · Ajustes |
| tarjeta de usuario | `:71-77` | `padding:10px` · `radius:10px` · `border:1px solid #E7DCCF` · `background:#FFF` · `gap:10px`; avatar 34px círculo `#F6E7E0`/`#B4522F` 12px/700; nombre 13px/600; rol 11px `#9A8A7E` |
| barra superior | `:82` | `height:72px` · `padding:0 28px` · `border-bottom:1px solid #E7DCCF` · `justify-content:space-between` |
| título | `:83` | `.display` 24px (26px en `/calendar` y `/clients/[id]`) |
| contenido | `:90` · `:92,:100` | `padding:24px 28px` · `gap:18px` · contenido a `max-width:1084px` |
| cabecera móvil | `Main:23` · `DetalleEmpleado:40` | `height:56px` · `border-bottom:1px solid #E7DCCF`; `padding:0 16px` sin flecha, `0 14px 0 8px` con ella; flecha en caja 44×44 con chevron 20px trazo 2; título `.display` 21px sin flecha, 15px/600 con ella |

**Dos desviaciones conscientes:** `CalendarioDesktop:74` usa `padding:0 24px` en la barra superior (los otros cinco, 28) — se unifica a 28 y va a P4 como ruido. `DetalleEmpleadoDesktop:90` usa `0 28px 0 18px` con flecha, y `:95` añade `padding-left:8px` al título — eso **sí** se respeta.

### 1.3 Tokens

Ya existen y coinciden: `--sidebar` (`globals.css:130`), `--sidebar-foreground` (`:131`), `--sidebar-primary` (`:132`), `--sidebar-accent` (`:134`), `--sidebar-border` (`:136`), `--background` (`:105`), `--muted-foreground` (`:116`), `--muted-foreground-2` (`:142`); expuestos en `@theme inline:31-38`.

**Falta uno: `#6B5C53`**, el destino inactivo. Cero apariciones en `src/`. Un custom property no definido falla en silencio —la declaración se descarta— así que el token va antes que el componente.

### 1.4 Las doce pantallas: título, flecha y props

**Fuente única.** Ninguna tarea repite estos valores; las tareas citan esta tabla.

El título está **unificado por decisión del usuario**: una sola cadena en los dos anchos, aunque los artboards pongan textos distintos (nueve de las doce discrepaban). La última columna dice de cuál se aparta.

**Dos reglas que gobiernan la columna de props, y que no son obvias:**

1. **`actions` se pinta en los dos anchos; `mobileActions`, cuando existe, lo SUSTITUYE por debajo de 1024** (no se suma). Por eso hay filas con `mobileActions`=ninguna: son pantallas cuyo artboard móvil deja la acción en el cuerpo, no en la cabecera.
2. **Este bloque solo MUEVE controles que ya existen.** Los que el artboard dibuja y el código no tiene —el segmentado Día/Semana de `/calendar`, su buscador— **no se construyen aquí** (§2.8): son de los bloques 3-7. La columna dice explícitamente cuáles.

| Ruta | Título | `back` | `desktopBack` | Otras props | Se aparta de |
|---|---|---|---|---|---|
| `/today` | "Buenos dias, {nombre}" | no | no | `subtitle`=fecha·hora (solo escritorio, `HoyDesktop:77`); `actions`=refrescar (existe: `today/page.tsx:22,91`) + el CTA del estado vacío; `mobileActions`=usuario y avatar (`Main:25-28`). **El refrescar en móvil se queda en el CUERPO**, que es donde lo dibuja el artboard (`Main:38-40`, caja 44×44), no en la cabecera | móvil `Main:24` decía "Bella Vista" |
| `/calendar` | la fecha visible | no | no | `titleSize="lg"`; `titleAdjacent`=navegador ‹ Hoy › ; `mobileActions`=buscar + conmutador de agenda (`Calendario:28-33`). **`actions` de escritorio queda vacío en este bloque**: el segmentado Día/Semana y el buscador que dibuja `CalendarioDesktop:88-100` no existen en el código y son del bloque 3 (regla 2) | móvil `Calendario:26` decía "Citas" |
| `/clients` | "Clientes" | no | no | `actions`="Anadir cliente", hoy en el cuerpo (`clients/page.tsx:42-45`); en móvil el artboard lo mantiene en la cabecera (`Clientes:25`, con `space-between`) | — |
| `/clients/[id]` | nombre del cliente | **sí** | **sí** (38×38 **sin** borde, `DetalleClienteDesktop:80`) | `titleSize="lg"`; "Cliente desde…" va **inline** junto al título con `gap:12px` (`:83-85`) → `titleAdjacent`, **no** `subtitle`; `actions`="Editar" (`:91`) + "Nueva cita" (`:95`), que hoy están en el cuerpo (`clients/[id]:62`) | móvil `DetalleCliente:37` decía "Detalle cliente" |
| `/staff` | "Equipo" | no | no | `actions`="Anadir" (escritorio); **`mobileActions`=ninguna**: en móvil el artboard deja el CTA en el cuerpo (`Equipo:50-52`) y la cabecera lleva solo el título (`:35-37`) | — |
| `/staff/[id]` | nombre del empleado | **sí** | **sí** (38×38 **con** borde, `DetalleEmpleadoDesktop:92`; y el título lleva `padding-left:8px`, `:95`) | `actions`="Editar" (`:100`) + "Desactivar" (`:104`), que hoy están en el cuerpo (`staff/[id]:149,152`) | de los **dos**: ambos dicen "Detalle empleado" |
| `/settings` | "Ajustes" | no | no | — | — |
| `/settings/salon` | "Perfil del salon" | **sí** | no | — | escritorio decía "Ajustes" |
| `/settings/booking` | "Reservas online" | **sí** | no | — | ídem |
| `/settings/billing` | "Facturacion y plan" | **sí** | no | — | ídem |
| `/settings/account` | "Mi cuenta" | **sí** | no | — | ídem |
| `/settings/business-hours` | "Horario de apertura" | **sí** | no | `mobileActions`="Guardar" (`Horario:37`). En **escritorio no va a la barra**: el artboard lo deja en el cuerpo (`HorarioDesktop:126`) | ídem |

**Siete pantallas con flecha en móvil, dos en escritorio.** Las cinco subpáginas de ajustes divergen por un motivo real de navegación: en escritorio la subnav de 210px (`AjustesDesktop:81-89`) es la salida, y por eso los seis artboards de escritorio dicen "Ajustes" sin flecha; en móvil no hay subnav.

> **Trampa verificada:** la flecha de `CalendarioDesktop:78` **no es un botón atrás**, es el `‹` del navegador de fecha (34×34, con "Hoy" en medio y `›` detrás). Mismo icono, otra función: va en `titleAdjacent`. Confundirlas mete un "volver" en el calendario y deja la fecha sin navegación.

### 1.5 Lo que queda fuera, y por qué

| Ruta / artboard | Motivo |
|---|---|
| `appointments/[id]` | **No tiene artboard de pantalla en ningún ancho.** `DetalleCita.dc.html:36` es una hoja inferior sobre el calendario (`radius:16px 16px 0 0`, con asa); `DetalleCitaDesktop:79` es la barra del **calendario** con un panel acoplado. Es del bloque 4. |
| `appointments/new` | Sus cinco artboards van a pantalla completa con cabecera propia de 68px (`NuevaCitaDesktopPaso1:29,42-43`). Construirla es de su bloque. **Aquí solo se le cambia `sticky top-14` → `top-0`**, consecuencia directa de borrar `AppHeader` (§2.3), y lo hace T6. |
| `AjustesNotificaciones` | Dibujado en los dos anchos pero **no tiene ruta** en `src/app/(app)/`. Es FE.11. |
| `day-view.tsx:21` | Su `h-[calc(100vh-16rem)]` quedará mal dimensionado en escritorio. Es del bloque 3; T8 lo captura como deuda. |
| `bottom-nav.tsx`, `fab-button.tsx` | No se tocan (§2.6, §2.7). |

---

## §2 — Decisiones

### 2.1 La barra superior es un slot por pantalla, no un mapa de rutas

Un mapa `ruta → {título, CTA}` en el shell no puede expresar los artboards: `EquipoDesktop:82` y `ClientesDesktop:73` llevan título + un CTA; `HoyDesktop:74-86` título con subtítulo + dos botones; `CalendarioDesktop:74-85` la fecha + un navegador; `AjustesDesktop:77-79` solo el título; `DetalleEmpleadoDesktop:90-102` flecha + título + dos secundarios.

Así que el layout monta la barra lateral y **cada pantalla monta su cabecera** vía `PageShell`. Precedente del repo: en el carril B el chasis se montaba en `page.tsx` y obligaba a las seis tareas a editar un mismo fichero; se corrigió a "cada paso monta su chasis" y funcionó.

**Descartado:** un contexto React (`usePageHeader`). Mete estado global para lo que es composición, y la página escribiría el título en un efecto — un fotograma con el título por defecto justo en las dos pantallas de título dinámico.

### 2.2 El contrato de `PageShell`

```tsx
interface PageShellProps {
  title: string               // el mismo en los dos anchos — §1.4
  back?: boolean              // flecha en la cabecera MOVIL
  desktopBack?: "plain" | "bordered"  // flecha 38x38 en la barra superior; por defecto ninguna
  actions?: ReactNode         // cluster derecho, los DOS anchos
  mobileActions?: ReactNode | null    // SUSTITUYE a `actions` por debajo de 1024; `null` = ninguna
  subtitle?: ReactNode        // COLUMNA bajo el titulo, solo escritorio — HoyDesktop:77
  titleAdjacent?: ReactNode   // INLINE pegado al titulo — CalendarioDesktop:75, gap 16px
  titleSize?: "default" | "lg"
  contentClassName?: string   // capa INTERNA del contenido, nunca el padding de 28px
  children: ReactNode
}
```

`back` y `desktopBack` son independientes porque la flecha es propiedad del breakpoint, no de la pantalla (§1.4). `desktopBack` no es booleano porque los dos artboards que la llevan no la dibujan igual: `DetalleClienteDesktop:80` es 38×38 **sin** borde y `DetalleEmpleadoDesktop:92` es 38×38 **con** borde, y este último añade `padding-left:8px` al título (`:95`) — la desviación que §1.2 dice respetar, y que sin esta prop ninguna otra transporta.

`mobileActions` **sustituye**, no suma: pasar `null` deja la cabecera móvil sin acciones aunque `actions` tenga contenido, que es el caso de `/staff` (§1.4). `subtitle` y `titleAdjacent` no son intercambiables: uno es columna y el otro inline. `contentClassName` existe porque el `gap:18px` en columna de Equipo no es universal — `HoyDesktop:90` usa 20px y `AjustesDesktop:81` es una **fila** de 28px sin `max-w`.

**Móvil y escritorio se montan de forma EXCLUYENTE**, con `useMediaQuery` (§1.1), nunca los dos ocultando uno con CSS: jsdom no aplica CSS y un árbol duplicado escondido con `hidden` sigue rompiendo `getByRole`.

### 2.3 `AppHeader` se borra

`AppHeader` es una cabecera de 56px con el salón a la izquierda y el usuario a la derecha (§1.1). Eso **es** `Main.dc.html:23-29`, la cabecera móvil de Hoy. Si el layout lo siguiera montando y además cada pantalla pintara la suya, serían **112px de cromo donde el artboard dibuja 56**.

Se borra el fichero. Hoy queda como el caso `mobileActions` = usuario y avatar. Consecuencia obligatoria: el `sticky top-14` de `appointments/new` (§1.1) pasa a `top-0`, en la misma tarea.

### 2.4 El artboard manda, también en móvil

Decisión del usuario: las pantallas a construir son las de los artboards; **el código que no coincide es la versión antigua**. El chasis móvil actual tiene tres formas de cabecera (título `text-lg` suelto; nada; flecha + `text-sm`) y ninguna es la dibujada. Las doce convergen a la de §1.2.

La única desviación deliberada del artboard es el título unificado (§1.4), documentada caso a caso.

### 2.5 La barra lateral entra en `lg` (1024px)

No hay **ni un** artboard de la app interna a 768px: los `*Desktop` son 1440 y los móviles 390. `md` es "móvil ensanchado" a propósito — no se inventa un tercer diseño que nadie ha dibujado. Sin `sm`, por decisión ya tomada en el carril B.

### 2.6 Servicios, la query y el `<Suspense>`

"Servicios" es un destino del artboard pero no existe `/services` (§1.1). Enlaza a `/staff?tab=services`, y eso arrastra tres cosas:

1. **El activo no se puede decidir con `pathname.startsWith`**, que no ve la query: Equipo y Servicios se encenderían a la vez. Cada destino lleva su predicado.
2. **`useSearchParams` exige su `<Suspense>`** (§1.1). Como la barra lateral la monta el *layout*, sin boundary el fallo no se queda en una página: rompe el build de las doce. Por eso `app-sidebar.tsx` monta el suyo, y **T8 ejecuta `npm run build`** — que ninguna otra tarea ejecuta y es lo único que ve ese fallo.
3. **Y el destino no funcionaría:** los `<Tabs>` son no controlados (§1.1), así que pulsar "Servicios" cambiaría la URL, encendería el destino y dejaría el contenido en Empleados. T7c los pasa a controlados.

**Descartado:** unificar la barra inferior móvil con la lateral. Son listas distintas (4 contra 6) y la etiqueta del mismo destino cambia ("Mas" contra "Ajustes", ambos a `/settings`).

### 2.7 El botón flotante se queda en los dos anchos

Quitarlo en escritorio dejaría `/today` y `/calendar` **sin ninguna forma de crear una cita**: el enlace "Crear cita" de `today/page.tsx:171-174` solo aparece en el estado vacío, `/appointments/new` no es un destino de la barra lateral, y los CTA de la barra superior son de los bloques 3-7. Se queda hasta que llegue su CTA. Ojo a su `bottom-20` (§1.1): sin barra inferior flota 80px sobre el borde.

### 2.8 Alcance

Este bloque construye el **chasis** y la **navegación**. No reconstruye el interior de ninguna pantalla: ni tablas de escritorio, ni diálogos modales, ni el navegador de mes. Eso es de los bloques 3-7, que se encontrarán el chasis puesto.

---

## §3 — Ficheros

| Fichero | Qué | Acción |
|---|---|---|
| `src/components/brand/salon-mark.tsx` | El SVG de la marca, fuera del dominio de reserva | Crear |
| `src/components/booking/booking-salon-header.tsx` | Lo importa y lo sigue re-exportando | Modificar `:9-29`, `:55` |
| `src/app/globals.css` | `--nav-foreground` + su `--color-nav-foreground` | Modificar |
| `src/lib/nav/app-nav.ts` (+ `.test.ts`) | Los seis destinos con su predicado de activo | Crear |
| `src/components/layout/user-card.tsx` (+ test) | Iniciales + etiqueta de rol neutra | Crear |
| `src/components/layout/app-sidebar.tsx` (+ test) | La barra lateral, con su `<Suspense>` | Crear |
| `src/components/layout/page-shell.tsx` (+ test) | Las dos cabeceras + el contenedor | Crear |
| `src/components/layout/app-header.tsx` | §2.3 | **Eliminar** |
| `src/app/(app)/layout.tsx` (+ test nuevo) | Chasis por breakpoint; `min-h-dvh`; sin `AppHeader` | Modificar entero |
| Las **doce** pantallas de §1.4 | Cada una monta `PageShell` | Modificar |
| `src/app/(app)/staff/page.test.tsx` | No existe | Crear |
| `src/app/(app)/appointments/new/page.tsx` | `sticky top-14` → `top-0` | Modificar `:32` |
| `visual/shell-vs-artboards.spec.ts` | Capturas | Crear |

---

## §4 — Fases y olas

| Fase | Tareas | `paths_touched` | Depende de |
|---|---|---|---|
| F1 | T1, T2, T3 | `components/brand/**`, `booking-salon-header.tsx`, `globals.css` ‖ `lib/nav/**` ‖ `layout/user-card*` | — |
| F2 | T4 | `layout/page-shell*` | — |
| F3 | T5 | `layout/app-sidebar*` | T1, T2, T3 |
| F4 | T6 | `(app)/layout.tsx*`, `layout/app-header.tsx`, `appointments/new/page.tsx` | T5 |
| F5 | T7a-d | `today`+`calendar` ‖ `clients/**` ‖ `staff/**` ‖ `settings/**` | T4, T6 |
| F6 | T8 | `visual/**`, `tasks/todo.md` | todas |

**Olas:** `(T1 ‖ T2 ‖ T3 ‖ T4) → T5 → T6 → (T7a ‖ T7b ‖ T7c ‖ T7d) → T8`

### Protocolo de commit — vale para las once tareas (T1-T6, T7a-d, T8)

```bash
git add <tus rutas>
git commit -o <tus rutas> -m "…"
```

Las dos cosas, siempre. **`git add`** porque `git commit -o` falla sobre un fichero que git aún no conoce (`pathspec … did not match any file(s)`) y casi todas las tareas crean ficheros. **`-o` (`--only`)** porque commitea solo esas rutas e ignora el resto del índice: sin él, en una ola de cuatro agentes sobre el mismo árbol, el primero que commitea se lleva el trabajo a medio escribir de los otros. Nunca `git add -A`, nunca `git commit -m` a secas.

### Cómo se testea el breakpoint — vale para T4, T5, T6

El polyfill de `src/test/setup.ts` devuelve **siempre** `matches:false` (§1.1), así que cada prueba de escritorio necesita su `mockMatchMedia(true)` local y su `afterEach` de restauración. El patrón está tres veces en el repo: `booking-step-shell.test.tsx:24`, `public-datetime-step.test.tsx:19`, `public-employee-step.test.tsx:69-71`.

---

## §5 — Tareas

### T1 · el token y la marca compartida

- [ ] **Token.** En `globals.css`, junto a los `--sidebar-*` del `:root`: `--nav-foreground: #6b5c53; /* EquipoDesktop.dc.html:18 */`; y en `@theme inline`, junto a los `--color-sidebar-*`: `--color-nav-foreground: var(--nav-foreground);`
- [ ] **Mover el SVG.** Crear `components/brand/salon-mark.tsx` con `SalonMark` **tal cual** está en `booking-salon-header.tsx:9-29` (mismo markup, mismo `aria-hidden`, mismo `currentColor`).
- [ ] **Que booking lo importe.** Borrar la definición local, `import { SalonMark } from "@/components/brand/salon-mark"`, y **conservar el `export { SalonMark }` de `:55`**: hay imports y tests que dependen de él.
- [ ] **Comprobar.** `npm run test -- --run src/components/booking` → verde como antes. Pegar la salida.
- [ ] **Commit** (§4).

### T2 · los seis destinos

- [ ] **Test primero.** Fija: que son seis y en el orden de §1.2; que en `/staff` sin query se enciende Equipo y **no** Servicios; que con `?tab=services` es al revés; que `/staff/emp_1` sigue siendo Equipo; y que **nunca hay dos encendidos** — recorriendo `/today`, `/calendar`, `/clients`, `/clients/cli_1`, `/staff`, `/staff/emp_1`, `/settings`, `/settings/salon`, `/appointments/new` y `/appointments/apt_1` (estas dos ejercitan el `startsWith("/appointments")` de Citas).
- [ ] **Verlo fallar.**
- [ ] **Escribirlo.** `APP_NAV_ITEMS` con `{href, label, icon, isActive(pathname, params)}`. Iconos de Lucide, verificados en la versión instalada: `CalendarCheck`, `LayoutGrid`, `User`, `Users`, `Scissors`, `Settings`, en el orden de §1.2. El predicado por destino existe por §2.6: Equipo es `startsWith("/staff") && tab!=="services"` y Servicios el complementario.
- [ ] **Verde** + **commit** (§4).

### T3 · la tarjeta de usuario

Referencia: `EquipoDesktop.dc.html:71-77` (§1.2). **No `:83-89`, que es la barra superior con su CTA.**

- [ ] **Test primero.** Iniciales ("Maria Gil" → "MG"), nombre y etiqueta de rol **neutra**; que la etiqueta sale del rol y **nunca** del nombre; que un nombre de una palabra da una inicial sin reventar; y que sin usuario no pinta nada, en vez de una tarjeta vacía.
- [ ] **Verlo fallar.**
- [ ] **Escribirlo.** `ROLE_SALON_OWNER`→"Titular del salon", `ROLE_EMPLOYEE`→"Equipo", `ROLE_PLATFORM_ADMIN`→"Plataforma". El artboard pone "Propietaria" porque su ejemplo es María; `UserRole` no lleva género (§1.1) y deducirlo del nombre sería inventar un dato sobre una persona.
- [ ] **Verde** + **commit** (§4).

### T4 · `PageShell`

Contrato en §2.2. Valores en §1.2. Antes de escribir código de Next, leer `node_modules/next/dist/docs/` — `AGENTS.md` avisa de que este no es el Next que uno cree conocer.

- [ ] **Test primero** (breakpoints según §4). Cubre:
  - el mismo `title` sale en los dos anchos, en la cabecera de 56px y en la barra de 72px;
  - **`back` y `desktopBack` son independientes**: con `back` y sin `desktopBack` hay control de volver por debajo de 1024 y **no** en escritorio. Es el caso de las cinco subpáginas de ajustes (§1.4); si alguien los fusiona en una sola prop, esto se pone rojo;
  - el control de volver lleva **`aria-label="Volver"`** — sin nombre accesible la consulta por rol no lo encuentra, y los que hay hoy en el repo son iconos sueltos sin nombre (`clients/[id]:42-44`);
  - `titleAdjacent` se monta junto al título y `subtitle` en columna debajo: no son intercambiables;
  - **el árbol no se duplica**: el contenedor lleva `data-slot="page-shell-content"` y el test cuenta `container.querySelectorAll('[data-slot="page-shell-content"]')` = 1 en los dos breakpoints. **No `getAllByTestId`**: Testing Library busca `data-testid` y `setup.ts` no configura `testIdAttribute` (§1.1), así que no encontraría nada y el test sería rojo permanente.
- [ ] **Verlo fallar.**
- [ ] **Escribirlo.** Escritorio: barra `h-[72px] border-b px-7` (`pl-[18px]` con `desktopBack`), y debajo `px-7 py-6` con el contenido en `flex flex-col gap-[18px] max-w-[1084px]`. Móvil: la cabecera de 56px de §1.2 y debajo `p-4 md:py-6`.
- [ ] **Verde** + **commit** (§4).

### T5 · la barra lateral

Depende de T1, T2, T3. Valores en §1.2.

- [ ] **Test primero** (breakpoints según §4). Cubre: los seis destinos como enlaces y en orden; que el nombre del salón sale de `useSalon()` y no está escrito a mano; que en `/staff` sin query Equipo lleva **`aria-current="page"`** y Servicios no, y al revés con `?tab=services` (mockeando `usePathname` y `useSearchParams`); y que la tarjeta de usuario está montada.
  > El activo se marca con `aria-current`, no solo con clases: es lo que hace que la aserción signifique algo y que un lector de pantalla sepa dónde está. El color no se puede afirmar en jsdom.
- [ ] **Verlo fallar.**
- [ ] **Escribirlo.** `<aside>` con `sticky top-0 h-dvh w-[248px] shrink-0 border-r bg-sidebar px-3.5 py-5 flex flex-col justify-between`. El `sticky top-0 h-dvh` no es opcional: el artboard es un marco `1440×900` con `overflow:hidden`, así que la barra mide lo que la ventana; sin eso, dentro de `flex min-h-dvh` se estira a la altura del **documento** y en `/clients` con cincuenta filas la navegación se va fuera de pantalla. Dentro, el envoltorio con `gap-[22px]` de §1.2. Destinos: `h-10 px-3 rounded-lg text-sm gap-2.5 text-nav-foreground`; activo `font-semibold text-sidebar-primary bg-sidebar-accent`; iconos `size-[18px]`.
- [ ] **El `<Suspense>`, que no es opcional.** La parte que llama a `useSearchParams` va envuelta en su propio boundary **dentro de este fichero**, con un fallback que pinte la lista sin ningún destino encendido (misma altura, para que no salte). El porqué, en §2.6.
- [ ] **Verde** + **commit** (§4).

### T6 · el layout

Depende de T5. Tres cosas, y las tres son consecuencia una de otra.

- [ ] **Test primero** (breakpoints según §4). Cubre:
  - por debajo de 1024: barra inferior sí, barra lateral no, **y `AppHeader` ya no se monta** (§2.3);
  - en 1024+: barra lateral sí, barra inferior no — montaje excluyente, no `hidden`;
  - `OnboardingGate` sigue envolviendo en todos los casos;
  - el botón flotante aparece en `/today` y `/calendar` **en los dos anchos** (§2.7).
- [ ] **Verlo fallar.**
- [ ] **Escribirlo.**

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
        {showFab && <FabButton />}
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

Cuatro detalles que no son cosméticos:

1. `min-h-dvh` sustituye a `min-h-full`, que es inerte (§1.1). **Esto cambia el móvil**: hoy el contenedor mide lo que su contenido y pasará a medir al menos la ventana, con `main flex-1` estirando de verdad. Es el arreglo correcto y va declarado, no de tapadillo.
2. En escritorio **desaparece el gesto de deslizar**: es navegación entre pestañas de la barra inferior, y sin ella no significa nada.
3. `pb-20` sigue solo en el árbol móvil: es el hueco de la barra inferior fija.
4. El `<main>` de escritorio va a ancho completo —la barra superior necesita su borde de lado a lado— y por eso pierde el `mx-auto max-w-3xl`. Es lo que obliga a que las doce monten `PageShell` (T7): sin él quedarían a sangre.

- [ ] **Borrar `AppHeader`** (§2.3): el fichero y su import.
- [ ] **`appointments/new/page.tsx:32`**: `sticky top-14` → `top-0`, consecuencia directa de lo anterior (§1.5). No se toca nada más de esa pantalla.
- [ ] **Verde**, y después **la suite entera**: `npm run test -- --run`. Línea base + los nuevos, cero regresiones. Si algo se pone rojo, **causa raíz primero**; no ajustar el test para que pase.
- [ ] **Commit** (§4).

### T7 · las doce pantallas adoptan `PageShell`

Depende de T4 y T6. **Cuatro grupos de ficheros disjuntos, en paralelo**, un agente y un commit cada uno:

| | Pantallas |
|---|---|
| **T7a** | `today`, `calendar` |
| **T7b** | `clients`, `clients/[id]` |
| **T7c** | `staff` (+ test nuevo, + los `Tabs`), `staff/[id]` |
| **T7d** | `settings` y sus cinco subpáginas |

**Título, flecha y props de cada pantalla: §1.4.** No están aquí para que no puedan divergir. Del artboard móvil se calca todo lo demás (forma, padding, acciones): `Main:23`, `Calendario:25`, `Clientes:25`, `DetalleCliente:33`, `Equipo:35`, `DetalleEmpleado:40`, `Ajustes:29`, `AjustesSalon:34`, `AjustesReserva:31`, `AjustesFacturacion:32`, `AjustesCuenta:31`, `Horario:30`.

Cada pantalla:

- [ ] **Envolver.** El `<div>` raíz (`p-4 md:py-6`, o `p-4` a secas en `clients/[id]:39` y `staff/[id]:117`) pasa a `<PageShell title="…">`, con lo que había dentro.
- [ ] **Entregar la cabecera.** Todo lo que hoy haga de cabecera **se borra del cuerpo** y se expresa con props. Las tres formas actuales (§2.4) desaparecen. Ojo con dos casos donde el `<h1>` no está solo: `clients/page.tsx:40-46` lo tiene en un `justify-between` con el botón "Añadir" —que pasa a `actions`, o el botón se queda solo y salta al borde— y las siete pantallas con flecha lo tienen soldado a su botón de volver, que pasa a `back`.
- [ ] **Todos los estados de carga.** No solo el `<Suspense>` de `staff/page.tsx:23`: también los retornos tempranos de `clients/[id]:33`, `staff/[id]:110`, `settings/billing:64` y `settings/salon:71`. Si el esqueleto no monta `PageShell`, la pantalla salta de un contenedor a otro —y en escritorio, de sin barra superior a con ella— justo al cargar.
- [ ] **Sus tests.** `npm run test -- --run <sus rutas>` — verde sin tocarlos. Pegar la salida.
- [ ] **Commit** (§4).

**Solo T7c, además:**

- [ ] **`Tabs` controlado por la query** (§2.6):

```tsx
const tab = searchParams.get("tab") === "services" ? "services" : "employees"
<Tabs value={tab} onValueChange={(v) => router.replace(`/staff?tab=${v}`, { scroll: false })}>
```

`value`/`onValueChange` son las props reales de `@base-ui/react` y `ui/tabs.tsx` las pasa por spread. `replace` y no `push`: cambiar de pestaña no es un paso que "atrás" deba deshacer.

- [ ] **Crear `staff/page.test.tsx`**, que no existe (§1.1). Cubre: la cabecera dice "Equipo" en los dos anchos; y **al cambiar de pestaña el contenido cambia**.
  > **El mock de `next/navigation` del repo NO sirve** (§1.1): es inerte, así que `replace` no cambia `useSearchParams`, el panel no cambia y el test sería rojo permanente. Hace falta un mock **con estado**: un `URLSearchParams` que `replace` actualiza y que `useSearchParams` lee, con `rerender()` tras el clic. Rebajarlo a `expect(replace).toHaveBeenCalledWith(...)` no vale: probaría que se llamó al router, no que la pantalla cambie, que es el defecto que §2.6 existe para cerrar.

### T8 · verificación

- [ ] **Las cuatro, con evidencia.** `npm run test -- --run`, `npx tsc --noEmit`, `npm run lint`, `npm run build`. Pegar la salida real de las cuatro. **`npm run build` es el que importa**: es el único que ve el fallo de `useSearchParams` sin boundary (§2.6), porque `tsc` compila y la suite pasa igual. Nunca escribir "pasa" sin la salida.
- [ ] **Precondiciones de las capturas.** Pila arrancada, `RIVOO_E2E_EMAIL`/`RIVOO_E2E_PASSWORD`, y el salón E2E con **`onboarding_completed_at` NO nulo**: si es `NULL`, `OnboardingGate` redirige a `/welcome` y fotografiarías el alta. `visual/onboarding-vs-artboards.spec.ts:15-16` exige lo contrario para sus propias capturas — las dos suites no pueden correr con el mismo estado de base de datos; ejecutarlas por separado y dejarlo escrito en el spec.
- [ ] **Capturas** (`visual/shell-vs-artboards.spec.ts`, `channel:"chrome"` ya configurado) a **390, 768, 1024 y 1440**:
  - las **doce a 390** contra su artboard móvil — aquí la comparación móvil no es un control de regresión, es la verificación principal (§2.4);
  - `/staff`, `/today`, `/clients` y `/settings` a 1024 y 1440;
  - de deuda: `/appointments/new` a 390 y 1440 (saldrá con barra lateral, §1.5) y `/calendar` a 1440 (su `ScrollArea`, §1.5). No documentan un éxito: documentan lo que hereda el bloque siguiente.

  **Ancla de espera: contenido real, nunca solo el chasis.** Y un matiz de este bloque: `useMediaQuery` devuelve `false` en el primer render (§1.1), así que en escritorio se pinta primero el árbol móvil. Una fila de empleado existe en los dos, así que para 1024 y 1440 hay que esperar por **las dos cosas**, la fila y la barra lateral.
- [ ] **Comparar de verdad**, elemento por elemento, contra el artboard. Es lo que en los dos bloques anteriores destapó defectos que ni cinco revisores ni la suite entera vieron. No es un trámite.
- [ ] **Panel de tres revisores** independientes, en paralelo, agentes nuevos, ninguno el implementador, **instruidos para refutar**: (a) corrección — ¿el activo miente en algún caso? ¿queda alguna pantalla sin salida? (b) fidelidad móvil — ¿coincide cada cabecera con su artboard, o se coló algo del código antiguo? ¿se perdió alguna acción por el camino? (c) fidelidad de escritorio, con las capturas delante. Se descarta un hallazgo si la mayoría lo refuta.
- [ ] **Commit** del spec visual (§4), antes de cerrar.
- [ ] **Cerrar en `todo.md`** anotando las deudas con su destinatario: la cabecera propia de `/appointments/new` y su `min-h-[calc(100vh-8rem)]` para el bloque del asistente; el `h-[calc(100vh-16rem)]` de `day-view.tsx:21` y la **fecha duplicada en `/calendar`** (el título unificado es la fecha, y `calendar/page.tsx:47-52` ya la pinta en el cuerpo) para el bloque 3; el `bottom-20` del botón flotante (§2.7); y los 4px de padding de `CalendarioDesktop` como ruido de P4.

---

## Execution Order

Un solo subsistema, el frontend. El backend no se toca.

```
F1  T1 marca + token   ┐
    T2 destinos        │ sin dependencias, caminos disjuntos:
    T3 tarjeta usuario │ los cuatro EN PARALELO
F2  T4 PageShell       ┘
F3  T5 AppSidebar        depende de T1, T2, T3
F4  T6 layout            depende de T5
F5  T7a today+calendar ┐
    T7b clients/**     │ dependen de T4 y T6;
    T7c staff/**       │ ficheros disjuntos: EN PARALELO
    T7d settings/**    ┘
F6  T8 verificación      depende de todas
```

La revisión se lanza **al terminar el bloque entero**, como panel de tres (T8), nunca por tarea.

## Dependencias con otras specs

| Spec / bloque | Relación | Implicación |
|---|---|---|
| **Carril B — reserva en escritorio** (`docs/specs/reserva-escritorio/`) | Pre-requisito, cerrado (`c174e46`) | De ahí salen `SalonMark` (T1 lo extrae) y el patrón de montaje excluyente con `useMediaQuery` que repiten T4 y T6 |
| **Bloque 3 — Calendario** | Consumidor | Hereda `titleSize="lg"` + `titleAdjacent` ya montados (§1.4), la fecha duplicada y el `h-[calc(100vh-16rem)]` |
| **Bloque 4 — Detalle de cita** | Consumidor | `appointments/[id]` queda fuera (§1.5): es un panel acoplado sobre el calendario, no una pantalla |
| **Bloque 5 — Hoy** | Consumidor | Encuentra `subtitle`, `actions` y `mobileActions` ya montados (§1.4) |
| **Bloque 6 — Equipo y clientes** | Consumidor | Las cuatro pantallas ya sobre `PageShell`; reconstruye su interior sin tocar el chasis |
| **Bloque 7 — Ajustes** | Consumidor | Las seis ya montadas; le queda la subnav de 210px (`AjustesDesktop:81-89`) |
| **Bloque del asistente de cita** | Consumidor, hereda tarea | Cabecera propia de 68px, el `min-h-[calc(100vh-8rem)]`, y sacarlo del shell — por `(fullscreen)/appointments/new`, que **no** colisiona con `[id]` (`normalizeAppPath` quita los grupos: `entries.js:277-291`) |
| **FE.11 — Notificaciones** | Consumidor | Su artboard existe en los dos anchos pero no hay ruta (§1.5) |
| **CV.13 — talla táctil** | Complementaria | El botón de volver de la cabecera móvil es 44×44 (§1.2), así que estas doce pantallas ya la cumplen donde este bloque las toca |
