# Reserva pública en escritorio — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task by task. The steps use checkbox syntax (`- [ ]`) for tracking.

**Objective:** dar a la reserva pública (`/book/<slug>`) la composición de escritorio que sus 14 artboards especifican, y construir la pantalla de "ese hueco se acaba de ocupar" que no existe.

**Architecture:** DOS chasis hermanos resuelven lo responsive una sola vez cada uno. `BookingStepShell` (pasos 1-5): cabecera, progreso, rejilla de dos columnas de 1120px, huecos `aside` y `footer`. `BookingResultShell` (paso 6 y pantalla de error): cabecera centrada sin stepper, icono antes del título, contenedor de 860px. Son dos tipos de pantalla distintos en los artboards y forzarlos por el mismo chasis obligaría a apagar la mitad de sus condicionales.

**Tech Stack:** Next.js 16 (App Router), TypeScript, Tailwind v4, Shadcn/UI + `@base-ui/react`, Zustand (`usePublicBookingStore`), React Query v5, Vitest 4, Playwright (solo comparación visual).

**Complejidad:** **Compleja** (6+ ficheros, transversal). Motor: `executing-plans`. Revisión: **panel de 3 revisores independientes al terminar el BLOQUE ENTERO**, no por tarea (regla del usuario, 2026-08-28).

**Versión 2.** La v1 pasó por un revisor independiente que encontró 26 defectos, 4 bloqueantes. Los que cambiaron el diseño están marcados **[v2]** en su sitio.

---

## Reglas de ejecución en vigor

- El revisor se lanza **al terminar el bloque entero**, nunca por tarea.
- Cada despacho es un **agente NUEVO**. El revisor nunca es el implementador.
- **PROHIBIDO** tocar `node_modules`. **PROHIBIDO** ejecutar `npm ci` — uno previo destruyó `node_modules/.bin` devolviendo exit code 0. Si falta algo: `npm install`.
- No cambiar de rama. **No tocar código del repo backend** (`E:\IdeaProjects\rivoo`). Única excepción: escribir en `E:\IdeaProjects\rivoo\tasks\todo.md`, que es el cuaderno de los dos repos (el frontend no tiene `tasks/`).
- `AGENTS.md` del repo: *"This is NOT the Next.js you know"* — consultar `node_modules/next/dist/docs/` antes de escribir código de Next.
- `AGENTS.md`: en tests con React Query, `await findBy*` sobre algo que el componente **no** posee antes de aseverar; un payload deep-equal no prueba nada (`structuralSharing`).

---

## Breakpoints — decisión cerrada

Tailwind v4 por defecto; el proyecto no declara ninguno propio.

| Rango | Composición |
|---|---|
| base, 0–767 | Artboard móvil. `max-w-[390px] mx-auto` — la anchura exacta del artboard. **[v2]** `max-w-md` son 448px y se estiraría un 15%. |
| `md:` 768+ | Cabecera de escritorio (76px), stepper horizontal, una columna ancha. El aside de 320px todavía no cabe. |
| `lg:` 1024+ | Dos columnas: contenido + aside de 320px (340px en el paso 3). |
| `xl:` 1280+ | Contenedor tope `max-w-[1120px]` (860px en el chasis de resultado). |

**NADA de `sm:`.** No hay artboard a esa anchura. Decisión explícita del usuario (2026-08-29).

---

## Inventario visual

Referencia: `E:\IdeaProjects\rivoo-frontend\design\` — **14 artboards** **[v2]**: `ReservaPaso1..6` + `ReservaDesktopPaso1..6` + `ReservaError` + `ReservaErrorDesktop`, más `Estilo.dc.html` (sistema).

### Chasis de paso (1-5)

| Componente | Referencia (file:lines) | Forma y valores |
|---|---|---|
| Cabecera móvil, paso 1 | `ReservaPaso1.dc.html:24-36` | padding `28px 20px 22px` · fondo `#F5EEE6` · borde inferior 1px `#E7DCCF` · icono 30×30 stroke `#B4522F` sw 2.5 · nombre 30px/600 lh 1.05 ls -0.02em · dirección 13px `#7A6A5F` · **[v2]** incluye el estado abierto: punto 7×7 `#5C7A5E` + "Abierto hoy hasta las HH:MM" 12px/500 `#3F6B4F` (`ReservaPaso1.dc.html:32-34`) — en móvil vive AQUÍ, no en el aside |
| Cabecera móvil, pasos 2-5 | `ReservaPaso2.dc.html:25-33` | altura **60px** · padding `0 16px 0 8px` · botón atrás 44×44 (SVG 20×20 stroke `#2A2320` sw 2) · nombre 19px/600 · contador `N / 6` 11px `#7A6A5F` tabular-nums |
| Cabecera escritorio | `ReservaDesktopPaso1.dc.html:37-43` | altura **76px** · fondo `#F5EEE6` · borde inferior 1px `#E7DCCF` · centrada · icono 30×30 · nombre **26px**/600 lh 1.05 · dirección 12px `#7A6A5F` |
| Barra de progreso móvil | `ReservaPaso1.dc.html:40-49` | **6 segmentos** · alto 3px · radio 999 · gap 5px · activo `#B4522F`, inactivo `#E7DCCF` |
| Stepper escritorio | `ReservaDesktopPaso1.dc.html:50-60` | **5 pasos** · dot 22×22 radio 999 11px/700 · activo fondo `#B4522F` texto `#FFFFFF` · pendiente borde 1px `#D8C9B8`, etiqueta `#B8A99C` · completado fondo `#E4EDE1` color `#3F6B4F` con check SVG 12×12 sw 3 · conectores 26×1px: `#D8C9B8` tras completado, `#E7DCCF` el resto · etiqueta activa `#2A2320`/600 · **[v2] etiqueta de completado: usar `#7A6A5F`** (`ReservaDesktopPaso3.dc.html:48`, clase `stepdone`). Los artboards no coinciden entre sí — D2 usa `#B8A99C` — y se elige el de D3 por ser el más tardío |
| Contenedor de contenido | `ReservaDesktopPaso1.dc.html:45-46` | padding `32px 40px` · ancho 1120px · dos columnas gap **40px** · aside `flex-shrink:0`, `align-self:flex-start` |
| Título de paso | `ReservaPaso1.dc.html:53` / `ReservaDesktopPaso1.dc.html:63` | móvil **28px**/600 lh 1.1 ls -0.015em · escritorio **34px**/600 lh 1.05 · alineado a la izquierda |
| Subtítulo de paso | `ReservaPaso1.dc.html:54` | móvil 13px lh 1.45 `#7A6A5F` · escritorio 14px |
| Footer fijo móvil | `ReservaPaso3.dc.html:101-104` | borde superior 1px `#E7DCCF` · fondo `#FBF7F2` · padding `14px 20px 20px` · resumen 12px `#7A6A5F` centrado · CTA **50px** alto radio 8px |
| Nota de confianza, móvil | `ReservaPaso1.dc.html:109-112` | candado SVG **14×14** stroke `#9A8A7E` · 11px `#9A8A7E` · literal "Confirmacion inmediata por email · Cancela gratis hasta 24h antes" |
| Nota de confianza, aside | `ReservaDesktopPaso2.dc.html:110` | **[v2]** candado SVG **13×13** · 11px `#9A8A7E` · literal distinto: "Sin registro · cancela gratis hasta 24h antes" |

### Chasis de resultado (paso 6 y error) **[v2]**

| Componente | Referencia | Forma y valores |
|---|---|---|
| Contenedor escritorio | `ReservaDesktopPaso6.dc.html:46,53` | padding `64px 40px 0` (error: `52px 40px 0`, `ReservaErrorDesktop.dc.html:36`) · ancho **860px** · **sin stepper** |
| Cabecera móvil | `ReservaPaso6.dc.html:24` | altura 60px · **centrada, sin atrás ni contador** |
| Título | `ReservaPaso6.dc.html:35` / `ReservaDesktopPaso6.dc.html:50` | móvil **30px centrado** · escritorio **40px centrado** · **después** del icono, no antes |
| Icono de éxito | `ReservaPaso6.dc.html:30-32` / `ReservaDesktopPaso6.dc.html:46-48` | círculo **68×68** móvil / **80×80** escritorio · radio 999 · fondo `#E4EDE1` · SVG 32×32 (38×38) stroke `#3F6B4F` |
| Icono de error | `ReservaError.dc.html:32-33` / `ReservaErrorDesktop.dc.html:38-39` | círculo 68×68 / 80×80 · fondo **`#FAEFE9`** · SVG 32×32 (38×38) stroke **`#A34434`** · calendario con **X**, no check |
| Disposición escritorio del error | `ReservaErrorDesktop.dc.html:48,50,75` | **[v2]** flex gap **20px**, ancho 860px · columna principal `flex-grow` = horas alternativas · lateral **320px** `flex-shrink:0` = la cita perdida. Es la disposición **estándar**, no invertida (la v1 decía "invertidas"; es falso) |
| Botón "Anadir al calendario" | `ReservaDesktopPaso6.dc.html:69` | **[v2]** alto **42px** · borde 1px `#E7DCCF` · radio 8px · fondo `#FFFFFF` · 13px/600. Tercera altura de botón: la cubre `size="lg"` con override de alto, o una variante propia (decidir en T1) |

### Asides — dos componentes distintos, no uno

| Componente | Referencia | Forma y valores |
|---|---|---|
| Aside "El salon" (**solo paso 1**) | `ReservaDesktopPaso1.dc.html:132-145` | ancho **320px** · padding 22px · borde 1px `#E7DCCF` · radio 12px · fondo `#FFFFFF` · rótulo `EL SALON` 12px/600 ls 0.06em uppercase `#9A8A7E` · descripción 13px `#7A6A5F` lh 1.55 · separador 1px `#EFE6DA` · punto 7×7 `#5C7A5E` + estado abierto 13px/500 `#3F6B4F` · filas de horario: etiqueta 12px `#9A8A7E`, valor 13px tabular-nums, cerrado `#B8A99C` · teléfono icono 14×14 + 14px/600 `#B4522F` |
| Aside "Tu reserva" (**pasos 2-5**) | `ReservaDesktopPaso2.dc.html:102-111` | ancho 320px (**340px en el paso 3**, `ReservaDesktopPaso3.dc.html:153`) · rótulo `TU RESERVA` · filas etiqueta 12px `#9A8A7E` / valor 14px/600 a la derecha · valor vacío `—` color `#C4B5A6` · separadores `#EFE6DA` |
| CTA del aside | `ReservaDesktopPaso5.dc.html:94` | **[v2]** alto **46px** radio 8px 15px/600 · deshabilitado fondo `#E3D3C6` color `#9A8A7E` (`ReservaDesktopPaso2.dc.html:109`) · activo fondo `#B4522F` · **excepción: 48px en el paso 3** (`ReservaDesktopPaso3.dc.html:182`) |
| Fila "Total" (**solo paso 5**) | `ReservaDesktopPaso5.dc.html:85-96` | valor 20px `.display num` |
| Bloque rico del aside (paso 3) | `ReservaDesktopPaso3.dc.html:157-180` | servicio 16px/600 + duración 12px `#7A6A5F` · precio 22px · avatar 36×36 radio 999 12px/700 · hora **24px** lh 1.1 · fecha 13px `#7A6A5F` |

### Elementos por paso

| Componente | Referencia | Forma y valores |
|---|---|---|
| Tarjeta de servicio móvil | `ReservaPaso1.dc.html:18` | flex row space-between · gap 12px · padding 14px · borde 1px `#E7DCCF` · radio 10px · fondo `#FFFFFF` · seleccionada borde `#DCC9BB` · nombre 15px/600 · duración 12px `#7A6A5F` · precio **22px**/600 tabular-nums nowrap |
| Tarjeta de servicio escritorio | `ReservaDesktopPaso1.dc.html:20` | padding 16px · **grid 2 columnas gap 14px** · precio **20px** |
| Rótulo de categoría (**solo escritorio**) | `ReservaDesktopPaso1.dc.html:67,103` | 12px/600 ls 0.05em uppercase `#9A8A7E` |
| Opción "Sin preferencia" | `ReservaPaso2.dc.html:53-62` | borde **dashed** 1px `#D8C9B8` · fondo `#F5EEE6` · radio 10px · avatar 44×44 fondo `#E7DCCF` color `#7A6A5F` · chevron 18×18 `#B8A99C` (**solo móvil**) |
| Avatar de profesional | `ReservaPaso2.dc.html:19` | 44×44 radio 999 14px/600 · colores por persona: `#F6E7E0`/`#B4522F`, `#E8EEE7`/`#5C7A5E`, deshabilitado `#F0EAE3`/`#9A8A7E` |
| Profesional no disponible | `ReservaPaso2.dc.html:90-96` | opacity **0.55** móvil / **0.5** escritorio · subtítulo "No ofrece \<servicio\>" |
| Navegador de mes (**solo escritorio**) | `ReservaDesktopPaso3.dc.html:77-87` | **[v2]** rótulo "Agosto 2026" 12px/600 ls 0.05em uppercase `#9A8A7E` · 2 botones 32×32 radio 8px borde 1px `#E7DCCF` fondo `#FFFFFF`, chevrons 15×15 stroke `#2A2320` |
| Celda de día | `ReservaPaso3.dc.html:19` / `ReservaDesktopPaso3.dc.html:19` | móvil **52×62** radio 10px, día 10px + número 20px · escritorio alto **68px** en grid de 7 columnas gap 10px, día 11px + número 21px + **tercera línea 10px** · cerrado: borde `#EFE6DA` fondo `#F5EEE6` color `#B8A99C` · seleccionado borde y fondo `#B4522F` color `#FFFFFF` |
| Botón de hueco | `ReservaPaso3.dc.html:18` / `ReservaDesktopPaso3.dc.html:18` | móvil alto **46px** en grid **3 columnas** gap 8px · escritorio alto **44px** en grid **6 columnas** gap 10px · radio 8px borde 1px `#E7DCCF` fondo `#FFFFFF` 14px/500 tabular-nums |
| Hueco agotado | `ReservaPaso3.dc.html:84-85` | borde `#EFE6DA` · fondo `#F5EEE6` · color `#B8A99C` · **`line-through`** |
| Hueco seleccionado | `ReservaPaso3.dc.html:86` | borde y fondo `#B4522F` · color `#FFFFFF` · peso 600 |
| Rótulo de franja | `ReservaPaso3.dc.html:80,92` | `MANANA` / `TARDE` 12px/600 ls 0.05em uppercase `#9A8A7E` |
| Campo de formulario | `ReservaPaso4.dc.html:19-20` / `ReservaDesktopPaso4.dc.html:28` | **[v2] definiciones de clase, no usos** · móvil `.fld`/`.fldok` alto **46px** padding `0 14px` · escritorio `.in` alto **42px** padding `0 12px` · borde 1px `#E7DCCF` radio 8px fondo `#FFFFFF` 14px · con valor `#2A2320`, placeholder `#9A8A7E` · etiqueta 12px/600 `#7A6A5F` |
| Casilla de consentimiento | `ReservaPaso4.dc.html:75-78` | contenedor padding 12px (escritorio 14px) borde 1px `#E7DCCF` radio 8px · fondo `#FFFFFF` móvil / **`#FBF7F2`** escritorio · caja **18×18** radio 4px borde 1px `#D8C9B8` · texto 12px (13px) `#7A6A5F` lh 1.5 |
| Aviso ámbar | `ReservaPaso5.dc.html:77-80` | borde 1px `#E8D3A6` · fondo `#FAEFD6` · radio **8px** móvil / **10px** escritorio · padding `12px 14px` / `14px 16px` · icono 16×16 (17×17) stroke `#8A5B12` · texto 12px (13px) `#8A5B12` |
| Hora tachada | `ReservaError.dc.html:44` / `ReservaErrorDesktop.dc.html:78` | 22px móvil / **26px** escritorio · `.display num` · color `#B8A99C` · **`line-through`** |
| Insignia "Ocupada" | `ReservaError.dc.html:47` | padding `4px 10px` · radio 999 · borde 1px `#EDD6D0` · fondo `#FFFFFF` · color `#A34434` · 11px/600 · nowrap |
| Rejilla de horas alternativas | `ReservaError.dc.html:59-66` / `ReservaErrorDesktop.dc.html:55-62` | móvil **3 columnas** gap 8px · escritorio **4 columnas** gap 10px · botón 44px |

### Comprobación de primitivas compartidas **[v2]**

Todas las que faltan se crean o extienden en **T1**, no en la tarea que las consume: son ficheros compartidos y meterlas en T4-T10 rompería la disjunción que permite paralelizar esa ola.

| Necesidad | Estado real | Acción |
|---|---|---|
| CTA de 50px (móvil) y 46px (aside) | **NO existe.** `button.tsx:24-36`: tope `lg` = `h-9` = 36px | T1: añadir `xl` (44px) y `2xl` (50px). **No modificar `lg`**: lo usan otras pantallas |
| Botón de 42px ("Anadir al calendario") | NO existe | T1: decidir variante o alto explícito |
| Estado de pulsación `#8F3F24` | **NO existe.** `button.tsx:13` usa `bg-primary/80`, que es opacidad | T1: token + `active:` |
| CTA deshabilitado sólido `#E3D3C6` | **NO existe.** `button.tsx:9` lleva `disabled:opacity-50` en la clase base | T1: sustituir por `disabled:bg-primary-disabled disabled:text-muted-foreground-2` en la variante `default`. Sin esto, T3 y T7 no tienen forma legal de pintarlo |
| Casilla 18×18 radio 4px | **`src/components/ui/checkbox.tsx` NO EXISTE.** `public-client-step.tsx:78-83` usa un `<input type="checkbox">` nativo | T1: **crear** la primitiva sobre `@base-ui/react/checkbox`. T7 solo la consume |
| Insignia pastilla "Ocupada" | **NO la pinta.** `badge.tsx:8` fija `h-5 rounded-4xl px-2 py-0.5 text-xs`; el `h-5` choca con el padding `4px 10px` | T1: añadir variante. T10 solo la consume |
| Nombres de día y agrupación "Lun - Jue" | **No hay helper público.** `src/types/salon.ts:78-85` da 7 filas planas con `dayOfWeek` numérico; el único `dayName` está privado en `public-service-step.tsx:87-90`, fichero que T4 vacía | T1: extraer `dayName` y `groupBusinessHours` a `src/lib/utils/business-hours.ts` |

### Comprobación de tokens **[v2]**

Dos cuentas distintas, que la v1 mezclaba:
- **Sistema** (`Estilo.dc.html`, auditado en `tasks/todo.md` CV.14): 26 tokens, 24 existen; faltan `#8F3F24` y `#D9A441`.
- **Artboards de reserva**: 6 valores sin token. `#D9A441` no lo usa ninguno, así que **no se añade** (YAGNI).

| Token | Valor | Comprobado ausente en `globals.css` | Nombre |
|---|---|---|---|
| Terracota de pulsación | `#8f3f24` | sí | `--primary-pressed` |
| CTA deshabilitado | `#e3d3c6` | sí | `--primary-disabled` |
| Borde del aviso ámbar | `#e8d3a6` | sí (`--color-status-pending-bg` es `#faefd6`, el FONDO) | `--warning-border` |
| Fondo del icono de error | `#faefe9` | sí (`--color-status-cancelled-bg` es `#f7e2dd`) | `--destructive-soft` |
| Borde de "Ocupada" | `#edd6d0` | sí | `--destructive-border` |
| Valor vacío del aside | `#c4b5a6` | sí | `--text-placeholder` |

---

## FUERA DE ALCANCE — necesitan backend que hoy no existe

Se construye todo lo demás de esas pantallas y estos huecos quedan apuntados en `E:\IdeaProjects\rivoo\tasks\todo.md` al cerrar el bloque, en vez de inventar el dato:

1. **Primer hueco libre por profesional** (`ReservaPaso2.dc.html:66-88`, "Antes · Mie 11:00"). `EmployeePublic` (`src/types/salon.ts:31-37`) no transporta disponibilidad y calcularla exigiría una llamada por empleado.
2. **Número de huecos por día** (`ReservaDesktopPaso3.dc.html:90-124`, "9 huecos"). `getPublicAvailability` (`src/lib/api/appointments.ts:42-50`) recibe **un** `date`: pintar siete contadores serían siete llamadas.
   **[v2] Consecuencia que hay que asumir por escrito:** el artboard da a "Sin huecos" el mismo tratamiento visual que a "Cerrado". Sin el contador, **un día abierto pero lleno se pinta como disponible**. La tercera línea de la celda dice `Cerrado` cuando `businessHours` lo diga y queda **vacía** en el resto; la altura de 68px se mantiene para no descuadrar la rejilla.
3. **[v2] El backend no distingue el conflicto de hueco de ningún otro fallo de negocio.** `AppointmentConflictException:16` extiende `BusinessValidationException`, que en `BusinessValidationException:48` fija `HttpStatus.UNPROCESSABLE_ENTITY` con `type` `business-validation` y título "Business Validation Failed" — **los mismos** que la ventana de reserva o un empleado inactivo. No hay 409 ni discriminador. Lo correcto sería darle su propio `type` de Problem Details (para eso existe ese campo en RFC 9457), pero es backend y este bloque es frontend.
   **Solución sin backend, en T10:** al fallar la reserva se re-consulta la disponibilidad de ese día — petición que la pantalla de error necesita de todos modos para ofrecer alternativas. Si el hueco elegido **ya no está** en la respuesta, es el conflicto: pantalla de error. Si sigue estando, es otro fallo: banner. Comprueba la condición real en vez de fiarse de un código de error, así que sigue siendo correcta el día que el backend añada el `type`.

---

## Estructura de ficheros

| Fichero | Responsabilidad |
|---|---|
| `src/components/booking/booking-step-shell.tsx` | **NUEVO.** Chasis de los pasos 1-5 |
| `src/components/booking/booking-result-shell.tsx` | **NUEVO.** Chasis del paso 6 y del error |
| `src/components/booking/booking-stepper.tsx` | **NUEVO.** Stepper de 5 con tres estados |
| `src/components/booking/salon-info-aside.tsx` | **NUEVO.** Aside "El salon" |
| `src/components/booking/booking-summary-aside.tsx` | **NUEVO.** Aside "Tu reserva" + CTA |
| `src/components/booking/public-booking-error.tsx` | **NUEVO.** Pantalla de hueco ocupado |
| `src/components/ui/checkbox.tsx` | **NUEVO.** Primitiva que no existe |
| `src/lib/utils/business-hours.ts` | **NUEVO.** `dayName` + `groupBusinessHours` |
| `src/components/ui/button.tsx`, `badge.tsx`, `src/app/globals.css` | Extender primitivas y tokens |
| `src/app/book/[slug]/layout.tsx`, `page.tsx` | Altura, montaje del chasis, estado de conflicto |
| `src/lib/stores/public-booking-store.ts` | Estado de conflicto |
| `src/components/booking/public-*-step.tsx` | Contenido de cada paso, sin plomería responsive |

---

## Fases y paralelización

| Fase | Tareas | `paths_touched` | Depende de |
|---|---|---|---|
| **F1 Sistema** | T1 | `ui/button.tsx`, `ui/badge.tsx`, **`ui/checkbox.tsx`**, `globals.css`, **`lib/utils/business-hours.ts`**, `book/[slug]/layout.tsx` | ninguna |
| **F2 Chasis** | T2 | `booking-step-shell.tsx`, `booking-result-shell.tsx`, `booking-stepper.tsx`, `book/[slug]/page.tsx`, **`stores/public-booking-store.ts`** | F1 |
| **F3 Asides** | T3 | `salon-info-aside.tsx`, `booking-summary-aside.tsx` | F2 |
| **F4 Pasos** | T4–T9 | un `public-*-step.tsx` por tarea, disjuntos | F3 |
| **F5 Error** | T10 | `public-booking-error.tsx`, `public-confirm-step.tsx` | F3 y **T8** |
| **F6 Verificación** | T11 | `visual/` | todas |

**Olas:** `F1` → `F2` → `F3` → `F4 ‖ F5` → `F6`. T8 antes que T10 (comparten `public-confirm-step.tsx`).
**[v2]** `public-booking-store.ts` y `page.tsx` se tocan **solo en F2**, que define el estado de conflicto por adelantado; así T10 no vuelve sobre ficheros de una fase cerrada.

---

## Task 1: Sistema — primitivas, tokens y utilidades

**Files:** `src/app/globals.css` · `src/components/ui/button.tsx:9,13,24-36` · `src/components/ui/badge.tsx:8` · **Create:** `src/components/ui/checkbox.tsx`, `src/lib/utils/business-hours.ts` · **Modify:** `src/app/book/[slug]/layout.tsx:5` · **Create:** `src/app/book/[slug]/layout.test.tsx`

- [ ] **Paso 1: los 6 tokens** en `:root` de `globals.css`, junto a los propios del proyecto (`--hairline`, `--text-subtle`, …), cada uno con el artboard del que sale en un comentario. Un custom property inexistente se descarta en silencio: no da error, deja la pantalla mal.

```css
  /* Reserva publica: valores del canvas que no tenian token. */
  --primary-pressed: #8f3f24;      /* Estilo.dc.html — pulsacion del CTA */
  --primary-disabled: #e3d3c6;     /* ReservaDesktopPaso2.dc.html:109 */
  --warning-border: #e8d3a6;       /* ReservaPaso5.dc.html:77 */
  --destructive-soft: #faefe9;     /* ReservaError.dc.html:32 */
  --destructive-border: #edd6d0;   /* ReservaError.dc.html:47 */
  --text-placeholder: #c4b5a6;     /* ReservaDesktopPaso2.dc.html:106 */
```

- [ ] **Paso 2: exponerlos a Tailwind** en `@theme inline` (`--color-primary-pressed: var(--primary-pressed);` etc.), siguiendo el patrón de `--color-status-*`.

- [ ] **Paso 3: tamaños de botón.** Añadir a `buttonVariants`, sin tocar `lg`:

```ts
        xl: "h-11 gap-2 px-4 text-[15px] font-semibold has-data-[icon=inline-end]:pr-3.5 has-data-[icon=inline-start]:pl-3.5",
        "2xl": "h-[50px] w-full gap-2 px-4 text-[15px] font-semibold",
```

- [ ] **Paso 4: estados del botón.** En la variante `default`, añadir `active:bg-primary-pressed`. Y **sustituir** el `disabled:opacity-50` de la clase base (línea 9) por `disabled:bg-primary-disabled disabled:text-muted-foreground-2` en `default` — el diseño pide un fondo sólido, no un 50% del terracota. Revisar que ningún otro uso de `Button` dependa de ese `opacity-50`.

- [ ] **Paso 5: variante de insignia** en `badge.tsx` para "Ocupada": padding `4px 10px`, radio 999, borde 1px `--destructive-border`, fondo `--card`, color `--destructive`, 11px/600. **Ojo:** la clase base fija `h-5`, incompatible con ese padding; la variante debe anularlo.

- [ ] **Paso 6: crear `src/components/ui/checkbox.tsx`** sobre `@base-ui/react/checkbox`, siguiendo la forma de `switch.tsx` (que sí existe y usa la misma librería). Caja 18×18, radio 4px, borde 1px `#D8C9B8`.

- [ ] **Paso 7: crear `src/lib/utils/business-hours.ts`** con `dayName(dayOfWeek: number)` y `groupBusinessHours(hours: BusinessHoursResponse[])`, que agrupa días consecutivos con el mismo horario en "Lun - Jue" (`ReservaDesktopPaso1.dc.html:137-140`). Mover ahí el `DAYS`/`dayName` privado de `public-service-step.tsx:87-90`. Tests de la agrupación: días consecutivos iguales, días sueltos, todo cerrado.

- [ ] **Paso 8: altura del layout de reserva.** `book/[slug]/layout.tsx:5` usa `min-h-full`. **[v2] La premisa correcta:** `<html>` sí lleva `h-full` (`src/app/layout.tsx:41`), pero `<body>` declara **`min-h-full`, nunca `height`** (`:43`), así que el porcentaje del hijo resuelve contra `auto` y queda inerte. Es el mismo defecto del onboarding.

```tsx
    <div className="flex min-h-dvh flex-col bg-background">
```

  **[v2] Sin `md:min-h-full`**: en el onboarding esa clase se conservó por no tocar un layout que ya funcionaba; aquí sería código muerto en código nuevo.

- [ ] **Paso 9: test de regresión** en `book/[slug]/layout.test.tsx`, con la forma de `(onboarding)/layout.test.tsx:25-51`: el contenedor lleva `min-h-dvh` y no `min-h-full`.

- [ ] **Paso 10: `npm run test -- --run`.** Esperado: la línea base actual (203 tests en 40 ficheros) más los nuevos, todo verde. Adjuntar la salida. Commit.

---

## Task 2: Los dos chasis

**Files:** **Create:** `booking-step-shell.tsx`, `booking-result-shell.tsx`, `booking-stepper.tsx` + tests · **Modify:** `src/app/book/[slug]/page.tsx`, `src/lib/stores/public-booking-store.ts`, `src/app/book/[slug]/layout.tsx`

**Contratos** (los consumen T3–T10; no cambiarlos sin avisar):

```tsx
interface BookingStepShellProps {
  salon: SalonPublic
  step: 1 | 2 | 3 | 4 | 5
  title: string
  subtitle?: string
  onBack?: () => void       // sin esto no se pinta el boton atras (paso 1)
  aside?: ReactNode         // columna derecha desde lg:
  footer?: ReactNode        // barra inferior, hasta lg:
  asideWidth?: 320 | 340    // el paso 3 usa 340
  children: ReactNode
}

interface BookingResultShellProps {
  salon: SalonPublic
  tone: "success" | "error" // decide el circulo del icono
  icon: ReactNode
  title: string
  subtitle?: ReactNode
  children: ReactNode       // contenedor de 860px
}
```

- [ ] **Paso 1: `BookingStepper`.** Etiquetas `Servicio · Profesional · Fecha y hora · Tus datos · Confirmar`, tres estados con los valores del inventario. Oculto por debajo de `md:`.

- [ ] **Paso 2: barra de progreso móvil.** 6 segmentos + contador `N / 6`. Oculta desde `md:`. Son **6** en móvil y **5** en el stepper: así están los artboards, no unificarlos.

- [ ] **Paso 3: cabeceras.** Móvil 60px con atrás y contador, salvo el paso 1 que usa la variante alta con el estado "Abierto hoy". Escritorio 76px centrada.

- [ ] **Paso 4: rejilla.** `mx-auto max-w-[390px]` en base; `md:max-w-2xl` con padding `32px 40px`; `lg:flex lg:gap-10`; `xl:max-w-[1120px]`.

- [ ] **Paso 5: aside y footer — decisión cerrada. [v2]** El aside se **monta condicionalmente**, solo desde `lg:`, con un `useMediaQuery`; el footer, solo por debajo. **No** se resuelve con CSS. Motivo concreto: los dos contienen un botón llamado "Continuar" (`ReservaPaso3.dc.html:103` y `ReservaDesktopPaso3.dc.html:182`), y con ambos en el DOM cualquier `getByRole("button", {name:"Continuar"})` falla por coincidencia múltiple — el estilo de consulta que ya usan los tests (`public-datetime-step.test.tsx:82`). Los tests de T2 asertan **presencia según viewport**, no clases.

- [ ] **Paso 6: `BookingResultShell`.** Cabecera centrada sin atrás ni progreso, icono, título centrado (30px móvil / 40px escritorio), contenedor 860px.

- [ ] **Paso 7: estado de conflicto en el store.** `public-booking-store.ts:53` limita `step` a 6. Añadir un campo aparte, **no** un séptimo paso: `conflict: { slot: string; date: string } | null`, con `setConflict` y `clearConflict`. Un séptimo paso rompería la barra de progreso de 6 y el stepper de 5.

- [ ] **Paso 8: montar en `page.tsx`.** Sustituir el `<div className="p-4">` y la cabecera manual (líneas 92-123). El despacho de pasos (126-131) no cambia, más una rama: si `conflict` no es nulo, se pinta `PublicBookingError`. **[v2]** Pasar también por un chasis las otras dos pantallas del fichero: "Salon no encontrado" (`:36-45`) y catálogo vacío (`:54-90`) — tienen su propio `p-4` y su propia cabecera manual, y al vaciar `layout.tsx` se quedarían sin chasis.

- [ ] **Paso 9: `layout.tsx`** pierde su cabecera "Reserva online" y su pie "Powered by Rivoo": ninguno está en ningún artboard.

- [ ] **Paso 10: tests + `npm run test -- --run`.** Actualizar `book/[slug]/page.test.tsx` si aserta sobre el chasis viejo. Commit.

---

## Task 3: Los dos asides

**Files:** **Create:** `salon-info-aside.tsx`, `booking-summary-aside.tsx` + tests

- [ ] **Paso 1: `SalonInfoAside`** (paso 1). Valores de `ReservaDesktopPaso1.dc.html:132-145`. Datos de `SalonPublic`: `description`, `businessHours`, `phone`. Horario agrupado con `groupBusinessHours` de T1. El estado "Abierto hoy hasta las HH:MM" sale del `businessHours` de hoy; si hoy está cerrado, pintar el estado cerrado, no inventar una hora.
- [ ] **Paso 2: `BookingSummaryAside`** (pasos 2-5). Filas etiqueta/valor con `—` en `--text-placeholder` cuando falte el dato. Props: filas, fila `Total` opcional (solo paso 5), CTA (texto, `disabled`, `onClick`), nota de confianza con el literal de escritorio. Ancho 320 o 340.
- [ ] **Paso 3: tests.** Un valor ausente pinta `—`, no `undefined`; el CTA deshabilitado no dispara `onClick`; un salón cerrado hoy no pinta "Abierto".
- [ ] **Paso 4: `npm run test -- --run`.** Commit.

---

## Tasks 4-9: los seis pasos

Cada tarea toca **un solo** `public-*-step.tsx`. Forma común, no se repite abajo:
1. Leer el artboard móvil **y** el de escritorio, y transcribir los VALORES del inventario. Transcribir la intención en vez del valor es el fallo que esta sección existe para evitar.
2. Quitar del componente lo que ahora vive en el chasis (título, subtítulo, cabecera, CTA).
3. Declarar su `aside` y su `footer`.
4. Los tests existentes siguen verdes; añadir uno por comportamiento nuevo.
5. `npm run test -- --run` y commit.

- [ ] **T4 — Paso 1, servicio** (`public-service-step.tsx`). Escritorio: grid de 2 columnas con rótulo de categoría. Móvil: lista sin agrupar. **Quitar el bloque de horario semanal (líneas 69-82)**: en escritorio vive en el aside y en móvil no está en el artboard. Su `dayName` privado ya se movió en T1. Aside: `SalonInfoAside`.
- [ ] **T5 — Paso 2, profesional** (`public-employee-step.tsx`). **[v2] Corrección: el componente YA atenúa correctamente** — `:52` aplica `pointer-events-none opacity-50` y `:66` pinta "No ofrece \<servicio\>", fijado por `public-employee-step.test.tsx:172-173`. La v1 decía que los filtraba: era falso, venía de confundirlo con el asistente autenticado. **Preservar ese comportamiento y sus tests.** Lo que cambia: opacidad a **0.55 en móvil** (0.5 se queda en escritorio), avatares con color propio en vez del gris uniforme de `avatar.tsx:41-55`, y la composición en grid. Sin el dato de primer hueco libre (fuera de alcance 1).
- [ ] **T6 — Paso 3, fecha y hora** (`public-datetime-step.tsx`). La más grande. (a) tocar un hueco **solo lo selecciona**; se avanza con "Continuar" — hoy `nextStep()` va en el propio click (líneas 68-71); (b) secciones `MANANA` / `TARDE`; (c) huecos ocupados **visibles y tachados**; (d) días cerrados marcados desde `salon.businessHours`, tercera línea "Cerrado" o vacía (fuera de alcance 2); (e) grid 3 columnas móvil / 6 escritorio; (f) **[v2]** navegador de mes en escritorio, y decidir y **escribir** si los 7 días son "los 7 siguientes" o la semana natural — hoy `:15-17` usa `DAYS_AHEAD = 30` en tira horizontal, que no es ninguna de las dos. Aside a 340px con el bloque rico. **Ojo:** `public-datetime-step.test.tsx` aserta hoy que pulsar un hueco avanza; reescribirlo para el flujo nuevo, no borrarlo.
- [ ] **T7 — Paso 4, tus datos** (`public-client-step.tsx`). Campos 46px móvil / 42px escritorio. Sustituir el `<input type="checkbox">` nativo (`:78-83`) por la primitiva `checkbox.tsx` creada en T1 — **solo consumirla**. Subtítulo literal: "Solo para gestionar esta reserva." CTA "Revisar reserva" deshabilitado hasta que valide.
- [ ] **T8 — Paso 5, confirmar** (`public-confirm-step.tsx`). Hero con hora 26px / 30px. Escritorio: grid de 3 columnas (Servicio / Profesional / A nombre de). Aviso ámbar con los tokens nuevos y el literal "El salon confirmara tu reserva. Recibiras un email en cuanto lo haga." Aside con fila `Total`. **Serializar antes de T10.**
- [ ] **T9 — Paso 6, hecha** (`public-success-step.tsx`). Usa `BookingResultShell`. Icono con `--success-soft`/`--success`, **no** `bg-green-100`/`text-green-600`. Escritorio: dos columnas dentro de los 860px (detalle | tarjeta del local, con teléfono accionable `tel:` en `#B4522F` y el código postal que solo está en escritorio). "Anadir al calendario" (42px) genera un `.ics` en cliente, sin backend.

---

## Task 10: Pantalla de "ese hueco se acaba de ocupar"

**Files:** **Create:** `public-booking-error.tsx` + test · **Modify:** `public-confirm-step.tsx`

- [ ] **Paso 1: discriminar el conflicto sin backend. [v2]** No hay 409: el conflicto llega como un 422 idéntico al de cualquier otro fallo de negocio (ver Fuera de alcance 3). Al fallar la reserva, re-consultar `getPublicAvailability` de ese día y ese profesional. Si el hueco elegido **ya no está** → `setConflict(...)` y pantalla de error, con las alternativas ya cargadas. Si sigue estando → banner, como hoy. Un test por cada rama.
- [ ] **Paso 2: la pantalla**, sobre `BookingResultShell` con `tone="error"`. Icono (círculo `--destructive-soft`, calendario con X), título "Ese hueco se acaba de ocupar", subtítulo con la hora perdida, tarjeta con la hora tachada e insignia "Ocupada" (variante creada en T1).
- [ ] **Paso 3: huecos alternativos.** Mismo día y mismo profesional, ya en mano del paso 1. Grid 3 columnas móvil / 4 escritorio. Elegir uno vuelve al paso 5 con la hora nueva y limpia el conflicto.
- [ ] **Paso 4: "Elegir otro dia".** CTA principal en móvil (50px, outline, icono de calendario); en escritorio, botón secundario de 40px junto a "Ninguna hora te encaja?". Lleva al paso 3.
- [ ] **Paso 5: los datos se conservan.** El store NO se resetea: el artboard promete por escrito "Guardamos tus datos: solo tienes que elegir hora." Un test lo fija.
- [ ] **Paso 6: composición de escritorio. [v2]** Disposición **estándar**: flex gap 20px dentro de 860px, horas alternativas en la columna principal (`flex-grow`) y la cita perdida en la lateral de 320px. La v1 decía "invertidas": era falso.
- [ ] **Paso 7: `npm run test -- --run`.** Commit.

---

## Task 11: Verificación

**Files:** **Create:** `visual/reserva-vs-artboards.spec.ts`

Nada de lo anterior está verificado hasta aquí. En el alta reanudable esta comparación destapó dos defectos que no vieron ni cinco revisores ni 203 tests.

- [ ] **Paso 1: suite completa.** `npm run test -- --run`. Todo verde, sin fichero rojo. Adjuntar la salida; no afirmar "pasa".
- [ ] **Paso 2:** `npx tsc --noEmit` y `npm run lint`. Sin errores.
- [ ] **Paso 3: comparación visual.** Forma de `visual/onboarding-vs-artboards.spec.ts`. Capturar los **14 artboards** **[v2]** y las pantallas construidas a **390, 768, 1024 y 1440** — 768 y 1024 son justo donde cambia la composición, que es donde se rompe. Artefactos en `E:\IdeaProjects\rivoo\docs\specs\reserva-escritorio\verificacion\paso<N>-<ancho>-{diseno,construido}.png`.
- [ ] **Paso 4: anclas de contenido.** Nunca texto del chasis: se pinta en el primer render, antes de cualquier petición, y capturarías el esqueleto. Paso 1 `/elige un servicio/i` **y** el nombre de un servicio real; paso 2 `/sin preferencia/i`; paso 3 una hora concreta; paso 4 `/solo para gestionar/i`; paso 5 `/confirma tu reserva/i`; paso 6 `/reserva confirmada/i`; error `/se acaba de ocupar/i`.
- [ ] **Paso 5: mirar las imágenes**, elemento a elemento contra el inventario. Sin comparación automática de píxeles: dos renders distintos (fuentes, antialiasing) dan falsos rojos y falsos verdes por igual.
- [ ] **Paso 6: panel de 3 revisores** independientes en paralelo, ninguno implementador, con lentes distintas: (1) fidelidad al artboard, (2) corrección del código y accesibilidad, (3) que no se haya roto la reserva que ya funcionaba de punta a punta. Instruidos para **REFUTAR**; se descarta el hallazgo si la mayoría lo refuta.
- [ ] **Paso 7: recorrido real.** Con la pila levantada, reservar de verdad desde `/book/<slug>` a 390 y a 1440. Es lo único que prueba que el flujo sigue vivo.
- [ ] **Paso 8: apuntar los tres huecos** de "Fuera de alcance" en `E:\IdeaProjects\rivoo\tasks\todo.md`, con sus citas.

---

## Execution Order

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**

```
F1  Sistema (T1)                      sin dependencias
F2  Chasis (T2)                       depende de F1
F3  Asides (T3)                       depende de F2
F4  Pasos (T4,T5,T6,T7,T8,T9)  ┐ dependen de F3
F5  Error (T10)                ┘ y T10 espera ademas a T8
F6  Verificacion (T11)                depende de todas
```

**Coordinación:** un solo repo, sin backend. T4-T9 tocan ficheros disjuntos y van en una ola paralela; T10 entra en esa ola en cuanto T8 termine. La verificación es lo último y la revisión es del bloque entero, no por tarea.

## Dependencies on other specs/FRs

| Spec/FR | Relación | Implicación |
|---|---|---|
| **Reserva pública** (`docs/specs/reserva-publica/`) | **Prerrequisito, cerrado** | El flujo funciona de punta a punta desde el 2026-08-28. Este plan es su capa visual; no toca la lógica |
| **Alta reanudable** (`docs/specs/onboarding-reanudable/`) | **Complementaria** | De ahí salen el patrón de altura de T1 y la forma del spec de Playwright de T11 |
| **CV.13/CV.14** (`tasks/todo.md`) | **Consumidora** | T1 cierra los 44px, el `checkbox` y 6 tokens para TODO el repo. El carril A lo hereda |
| **Shell de escritorio** (CV.1) | **Independiente** | `book/[slug]` tiene layout propio: ni lo necesita ni lo bloquea |
| **Discriminador del conflicto** (backend, nuevo) | **Mejora futura** | Dar a `AppointmentConflictException` su propio `type` de Problem Details simplificaría T10 Paso 1; el plan no lo necesita |
