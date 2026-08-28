# Onboarding reanudable — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task by task. The steps use checkbox syntax (`- [ ]`) for tracking.
> **Reglas del usuario que anulan la skill:**
> 1. Cada despacho —implementador O revisor— es un agente NUEVO. Nunca se reanuda el que ya trabajó. El que revisa nunca es el que implementó.
> 2. **La revisión NO va tarea a tarea: va por bloque terminado.** Una pasada de revisión sobre el backend completo (T1-T3) y otra sobre el frontend completo (T4-T9), más el panel de tres sobre los dos puntos críticos. Motivo del usuario, aceptado: el ciclo implementador→revisor→implementador por cada tarea multiplica el tiempo de reloj sin multiplicar la calidad, en un plan que ya lleva dos revisiones independientes encima.
> 3. La red de seguridad que sustituye a la revisión por tarea: **cada implementador pega la salida real de sus tests**, y el orquestador lee los informes enteros —incluida la parte donde el agente dice lo que NO hizo— antes de encadenar la tarea siguiente.

**Objective:** Que un dueño que omite pasos del alta llegue al panel en vez de quedar atrapado en un bucle, y que las cinco pantallas del asistente existan en móvil y escritorio tal como están dibujadas.

**Architecture:** Un único hecho decide si el alta terminó: una marca de tiempo `onboarding_completed_at` en el salón, que se escribe una sola vez desde el último paso. El portero del panel deja de deducirlo contando empleados y servicios. El asistente pasa de 6 rutas mal numeradas a 5 pasos que coinciden con el diseño.

**Tech Stack:** Backend Spring Boot 4.0.3 / Java 25 / MySQL 8 / Flyway / MapStruct, hexagonal y multi-tenant. Frontend Next.js 16.2.1 (App Router), TypeScript, Tailwind v4, Shadcn/UI, React Query v5, Zustand, Vitest.

**Complejidad: COMPLEJA** (2 repos, ~25 ficheros, cambio de contrato + cambio de esquema). Motor de ejecución: `executing-plans` (opción A, model-driven).

**Versión 2** — incorpora los 16 hallazgos de la revisión independiente del plan v1. Los dos BLOCK están en las Tareas 6 y 8; si vuelves a escribirlos como en v1, reintroduces el bucle.

---

## Decisiones tomadas al escribir el plan

Se dejan escritas para que ningún implementador las vuelva a abrir.

- **D1 — Cinco pasos, no seis.** El diseño declara `Paso N de 5` en los 5 artboards y la barra avanza 20/40/60/80/100%. El store ya dice `totalSteps: 5`. Lo que sobra es la ruta `salon-setup`.
- **D2 — `salon-setup` se borra, no se reengancha.** Motivos, por orden de peso: tiene **cero referencias** en `src/` (verificado dos veces con grep); llama a la API con el token en cadena vacía (`salon-setup/page.tsx:42`), lo que suprime la cabecera `Authorization` en `client.ts:56` y además desactiva el reintento de refresco en `client.ts:67`, o sea 401 seco; y se numera como paso 2 chocando con `business-hours`. Resuelve **ON.7**.
  *(Corrección respecto a v1: el motivo "exige campos que el contrato no admite" era **falso a medias**. El record de backend `UpdateSalonRequest.java:12-14` **sí** acepta `addressStreet`, `addressCity` y `addressPostalCode`, y `SalonService.java:178-180` los aplica. Quien no los admite es el tipo del **frontend** (`rivoo-frontend/src/types/salon.ts:112-118`). Este plan cruza dos repos: al citar `UpdateSalonRequest` di siempre cuál de los dos.)*
- **D3 — La marca es `onboarding_completed_at TIMESTAMP NULL` en `salons`,** no un booleano. Un instante responde "¿terminó?" y además "¿cuándo?". Coherente con la convención del repo (`CLAUDE.md` raíz: toda columna temporal es `TIMESTAMP`).
- **D4 — La migración rellena los salones que ya existen** que no estén en `ONBOARDING`, con `onboarding_completed_at = created_at`. Sin esto, los 14 salones de la base local (todos `ACTIVE`, comprobado) aparecerían como "alta sin terminar" y serían expulsados al asistente. Los que estén en `ONBOARDING` se quedan a NULL a propósito: son altas que de verdad no terminaron. Hoy en local hay **cero** filas así, o sea que la cláusula no cambia nada ahora mismo, pero hace que la sentencia diga lo que la justificación promete.
  **Efecto colateral asumido: también rellena `FAILED`.** El `WHERE status IS NULL OR status <> 'ONBOARDING'` abarca `ACTIVE`, `INACTIVE`, `SUSPENDED` y también `FAILED` — y un salón `FAILED` es, por la regla de limpieza de `SalonSchedulingConfig`, un alta que precisamente **no** terminó (`ONBOARDING` sin dueño durante más de una hora). La migración los marca como si hubieran completado el asistente. Es a sabiendas, no un descuido, y es inocuo hoy: hay **cero** filas `FAILED` en la base local, y un salón en ese estado no tiene forma de que su dueño entre autenticado (nunca llegó a vincularse un `owner_user_id` capaz de autenticar), así que nadie puede aprovechar esa marca para saltarse el asistente. Si algún día aparecen filas así y hiciera falta corregirlo, la corrección es una **`V5`** que ponga `onboarding_completed_at = NULL` donde `status = 'FAILED'` — nunca editando la V4 ya aplicada.
- **D5 — Un único sitio que escribe la marca *deliberadamente*:** `POST /api/v1/salons/me/onboarding/complete`, idempotente por compare-and-set (`WHERE tenant_id = :t AND onboarding_completed_at IS NULL`).
  **No es un invariante del código, y no lo presentes como tal.** `SalonService.update` (`SalonService.java:170-189`) y `SalonService.updateStatus` (`:235-241`) son leer-modificar-**guardar** sobre el agregado entero: cargan el `Salon`, mutan campos y llaman a `save`, que mapea el objeto completo a una entidad nueva y la fusiona. Un `PUT /api/v1/salons/me` que haya leído la fila **antes** de que el CAS confirmara reescribirá el `null` encima. La ventana es estrecha y se acepta; lo que no se acepta es que un lector futuro crea que la columna solo se toca en un sitio.
- **D6 — El método nuevo entra en `UpdateSalonUseCase`, no en un puerto de entrada nuevo.** Un puerto nuevo cambiaría el constructor de `SalonController`, que **siete tests construyen a mano** (lista completa en la Tarea 2). Ninguno de los siete implementa la interfaz: seis pasan `mock(UpdateSalonUseCase.class)` o un `SalonService`, y el séptimo pasa `(UpdateSalonUseCase) null` — así que **añadir un método al use case no toca ninguno**. Lo que sí rompe es añadirlo al puerto de *salida*: ver Tarea 2 Paso 4.
- **D7 — El portero solo mira la marca, y solo para el dueño.** Fuera las dos consultas de empleados y servicios. Un `EMPLOYEE` **nunca** se manda al asistente (ver Tarea 6 Paso 3, motivo). Y deja de fallar hacia dentro: hoy, si `GET /api/v1/salons/me` devuelve un 500, `needsOnboarding` sale falso y **la app se pinta sin salón**.
- **D8 — No se guarda "por qué paso iba".** El asistente siempre abre por el paso 1. Lo que se arregla es que cada paso llegue relleno y sea idempotente, de modo que volver a pasar cueste cuatro clics y no pierda nada. Guardar el paso costaría una columna, una migración y una escritura por paso, y no compra nada que la precarga no dé ya.
- **D9 — Dos primitivas nuevas (`Switch`, `Progress`)** en vez de repetir el marcado. Hoy la barra está escrita a mano (`(onboarding)/layout.tsx:17-22`) y el interruptor del diseño está resuelto con un `<input type="checkbox">` crudo (`add-employee/page.tsx:118-123`). El diseño usa el interruptor en 7 filas de horario y la barra en los 5 pasos.
- **D10 — Corrección a `tasks/todo.md`.** El todo afirma que los artboards ponen `rivoo.app/<slug>` frente al `rivoo.app/book/<slug>` del código. Es falso para el onboarding: `Onboarding5.dc.html:33` y `Onboarding5Desktop.dc.html:35` ponen **`rivoo.app/book/bella-vista`**, que coincide con el código. El desajuste está en los artboards de Ajustes.

---

## Sin resolver — decisión de producto, NO la tome el implementador

- **El interruptor "Crear cuenta de acceso" en OFF.** `Onboarding3.dc.html:34` lo dibuja siempre en ON, con `Contraseña temporal *` obligatorio. **No existe artboard del estado OFF.** El código actual oculta el campo (`add-employee/page.tsx:132-145`). **Se mantiene ocultar** y se anota.

---

## Inventario visual

Referencia: `E:\IdeaProjects\rivoo-frontend\design\Onboarding{1..5}.dc.html` (390×844) y `Onboarding{1..5}Desktop.dc.html` (1440×900).

**Aviso:** el bloque `<style>` (líneas 11-29) es byte-idéntico en los 10 artboards (mismo hash) y **no contiene ni una sola custom property** — todo son hex literales. La referencia canónica de las clases compartidas es `Onboarding1.dc.html:11-29`.

### Tabla de componentes

| Componente | Referencia (file:lines) | Forma y valores |
|---|---|---|
| **Contenedor de página (móvil)** | `Onboarding1.dc.html:31` — idéntico en los 5 | `flex-direction:column; gap:18px; width:390px; height:844px; padding:18px 16px; overflow:hidden; background:#FBF7F2` |
| **Contenedor de página (escritorio)** | `Onboarding1Desktop.dc.html:31` — idéntico en los 5 | `flex-direction:column; align-items:center; width:1440px; height:900px; padding:44px 40px; overflow:hidden; background:#FBF7F2` · **sin `gap`** — el espaciado lo pone el `margin-bottom` de la marca |
| **Bloque de marca (solo escritorio)** | `Onboarding1Desktop.dc.html:32` — idéntico en los 5 | fuera de la tarjeta: `align-items:center; gap:11px; margin-bottom:26px`; svg tijeras `26×26` `stroke:#B4522F; stroke-width:2.5`; `Rivoo` con `.display` a `22px` |
| Barra de progreso | `Onboarding1.dc.html:32` | pista `height:6px; border-radius:999px; background:#EFE6DA` · relleno mismo alto y radio, `background:#B4522F`, `width` 20/40/60/80/100% · idéntica en móvil y escritorio |
| Cabecera de paso (móvil) | `Onboarding1.dc.html:32` | `flex-direction:column; gap:8px`; fila superior `space-between`; izquierda `Paso N de 5` `12px; color:#7A6A5F; tabular-nums`; derecha `gap:10px` → `Rivoo` `12px/600 #B4522F` + separador `1px×12px #E0D3C4` + Salir |
| Cabecera de paso (escritorio) | `Onboarding1Desktop.dc.html:34` | igual **sin** el wordmark ni el separador — la marca sube fuera de la tarjeta |
| Control «Salir» | `Onboarding1.dc.html:32` | `gap:5px; color:#9A8A7E`; icono log-out `13×13`, `stroke-width:1.75`; texto literal `Salir` `12px` |
| Botón primario `.cta` | `Onboarding1.dc.html:21` | `height:48px; border-radius:8px; background:#B4522F; color:#FFFFFF; 15px/600; gap:8px` · flecha derecha `16×16 stroke-width:2` **a la derecha del texto**, en los 5 pasos · móvil `flex-grow:2`, escritorio `padding:0 28px` |
| Botón «Omitir» `.ghost` | `Onboarding1.dc.html:22` | `height:48px; border-radius:8px; border:1px solid #E7DCCF; background:#FFFFFF; 15px/600; color:#2A2320`; sin icono; texto literal `Omitir` · móvil `flex-grow:1`, escritorio `padding:0 24px` |
| Pie de acciones (móvil) | `Onboarding3.dc.html:35` | `display:flex; gap:10px; margin-top:auto` — pegado al fondo · proporción Omitir 1 : CTA 2 |
| Pie de acciones (escritorio) | `Onboarding3Desktop.dc.html:37` | `justify-content:flex-end; gap:10px`, sin `margin-top:auto` |
| Tarjeta `.card` | `Onboarding1.dc.html:28` | `border:1px solid #E7DCCF; border-radius:12px; background:#FFFFFF` |
| Contenedor escritorio | `Onboarding1Desktop.dc.html:33` / `Onboarding2Desktop.dc.html:33` | `.card` + `gap:22px; padding:32px` · **640px en pasos 1 y 5; 760px en pasos 2, 3 y 4** (verificado en los cinco) |
| Interruptor ON / OFF | `Onboarding1.dc.html:23-25` | `width:42px; height:24px; padding:3px; border-radius:999px` · ON `background:#B4522F; justify-content:flex-end` · OFF `background:#E0D3C4; justify-content:flex-start` · knob `18×18` radio `999px` `#FFFFFF` |
| Selector de hora `.time` | `Onboarding1.dc.html:27` | `height:36px; padding:0 10px; border:1px solid #E7DCCF; border-radius:8px; background:#FFFFFF; 13px/500; tabular-nums` · **sin icono de reloj y sin chevron** · **en escritorio, `width:92px` fijo** (`Onboarding2Desktop.dc.html:36`) |
| **Separador «a» entre las dos horas** | `Onboarding2.dc.html:34`, `Onboarding2Desktop.dc.html:36` | literal `a` minúscula, `font-size:12px; color:#9A8A7E` · la fila lleva `gap:8px` en móvil y `gap:9px` en escritorio |
| **Divisor de 1px** | `Onboarding2.dc.html:34`, `Onboarding3.dc.html:34`, `Onboarding2Desktop.dc.html:36`, `Onboarding3Desktop.dc.html:36` | `height:1px; background:#EFE6DA` — **mismo hex que la pista de progreso**, por eso el token se llama `--hairline` y no `--progress-track` |
| Etiqueta `.lbl` | `Onboarding1.dc.html:16` | `12px/500; color:#5F534B` · los obligatorios llevan sufijo ` *` literal |
| Campo `.fld` (placeholder) | `Onboarding1.dc.html:17` | `height:42px; padding:0 12px; border:1px solid #E7DCCF; border-radius:8px; background:#FFFFFF; 14px; color:#B8A99C` |
| Campo `.fldv` (con valor) | `Onboarding1.dc.html:18` | igual pero `color:#2A2320` |
| Textarea (paso 4) | `Onboarding4.dc.html:34` | `height:78px; padding:11px 12px`, resto igual que `.fld`, `align-items:flex-start` |
| Muestras de color (paso 3) | `Onboarding1.dc.html:26` + uso en `Onboarding3.dc.html:34` | `30×30` radio `999px`; cuatro: `#B4522F` (seleccionada, `box-shadow: 0 0 0 2px #FBF7F2, 0 0 0 4px #B4522F`), `#5C7A5E`, `#4A6274`, `#A8762F` |
| Hero circular (pasos 1 y 5) | `Onboarding1.dc.html:33`, `Onboarding5.dc.html:33` | móvil `76×76`, escritorio `88×88`, radio `999px` · paso 1 `background:#F6E7E0` + tijeras `stroke:#B4522F` · paso 5 `background:#E4EDE1` + varita `stroke:#3F6B4F` |
| Checklist numerada (paso 1) | `Onboarding1.dc.html:33` | badge `28×28` radio `999px` `background:#F6E7E0; color:#B4522F; 12px/700`; etiqueta `14px`; `gap:12px` — **idéntica en móvil y escritorio** |
| Callout informativo (paso 4) | `Onboarding4.dc.html:34` | `padding:12px 14px; border:1px solid #E7DCCF; border-radius:8px; background:#F5EEE6`; icono info `15×15 stroke:#7A6A5F`; texto `12px; color:#7A6A5F; line-height:1.45` |
| Tarjeta «Tu pagina de reservas» (paso 5) | `Onboarding5.dc.html:33` | `.card` + `padding:16px; gap:7px`; `max-width:320px` móvil / `420px` escritorio; cabecera icono globo `15×15` + `12px/600`; URL `13px; color:#B4522F; word-break:break-all`; pie `11px; color:#9A8A7E` |
| Fila de día (móvil) | `Onboarding2.dc.html:34` | apilada: `flex-direction:column; gap:9px; padding:12px 14px` |
| Fila de día (escritorio) | `Onboarding2Desktop.dc.html:36` | `grid-template-columns:130px 52px 1fr; align-items:center; gap:16px; padding:11px 14px` |
| Fila «Domingo / Cerrado» | `Onboarding2.dc.html:34` | `space-between; padding:14px; background:#FAF6F0`; `Domingo` `14px/600 #9A8A7E`; `Cerrado` `12px #B8A99C` — **única fila idéntica en las dos plataformas** |
| Título `.display` | `Onboarding1.dc.html:14` | `600; letter-spacing:-0.02em` · móvil 27px (pasos 1 y 5) / 26px (2-4) · escritorio 32px |
| Subtítulo | `:33` móvil / `:35` escritorio | móvil `13px`, escritorio `14px`, ambos `color:#7A6A5F; line-height:1.5`. **El texto NO es el mismo en las dos plataformas: ver la tabla de textos.** |

### Textos literales (sin tildes, tal cual el artboard)

Los subtítulos de los pasos 2, 3 y 4 **difieren entre móvil y escritorio**. No es reflow: es copy distinto. Se implementan los dos.

| Paso | Título (ambos) | Subtítulo móvil | Subtítulo escritorio |
|---|---|---|---|
| 1 | `Bienvenido a Rivoo` | `Configura tu salon en unos minutos y empieza a gestionar tus citas.` | *(igual)* |
| 2 | `Horarios de apertura` | `La reserva online solo ofrecera huecos dentro de este horario.` | `La reserva online solo ofrecera huecos dentro de este horario. Podras cambiarlo luego en Ajustes.` |
| 3 | `Anade tu primer empleado` | `Puedes ser tu mismo. Anadiras mas cuando quieras.` | `Puedes ser tu mismo. Anadiras mas cuando quieras desde Equipo.` |
| 4 | `Anade tu primer servicio` | `Es lo que tus clientes veran al reservar.` | `Es lo que tus clientes veran al reservar online.` |
| 5 | `Tu salon esta listo` | `Ya puedes empezar a gestionar tus citas y atender a tus clientes.` | *(igual)* |

CTA por paso: `Comencemos` · `Continuar` · `Continuar` · `Continuar` · `Ir al dashboard`. Todos con flecha a la derecha. «Omitir» solo en los pasos 3 y 4.

### Comprobación de primitivas

| Necesita | ¿Existe en `src/components/ui/`? | Acción |
|---|---|---|
| `button` (cta / ghost) | Sí | `variant="default"` y `variant="outline"` |
| `input`, `label`, `textarea`, `card`, `separator` | Sí | usar tal cual |
| **interruptor** | **No** | **crear `src/components/ui/switch.tsx`** (Tarea 5) |
| **barra de progreso** | **No** | **crear `src/components/ui/progress.tsx`** (Tarea 5) |
| checkbox | No | el diseño no dibuja ningún checkbox: es un **interruptor**. Se sustituye el `<input type="checkbox">` actual por `Switch` |
| stepper | No | no hace falta: la cabecera es texto + barra |

### Comprobación de tokens

Ya existen y se usan tal cual (verificado en `globals.css:89-121`): `#FBF7F2` → `--background` · `#2A2320` → `--foreground` · `#B4522F` → `--primary` · `#FFFFFF` → `--card` · `#E7DCCF` → `--border`/`--input` · `#F5EEE6` → `--secondary`/`--muted` · `#F6E7E0` → `--accent` · `#7A6A5F` → `--muted-foreground` · `#5C7A5E`/`#4A6274`/`#A8762F` → `--chart-2/3/4`.

**Ya existen pero con otro nombre — NO crees un duplicado:**

| Hex | Token existente | Qué hacer |
|---|---|---|
| `#E4EDE1` | `--color-status-confirmed-bg` (`globals.css:15`) | ver abajo |
| `#3F6B4F` | `--color-status-confirmed-text` (`globals.css:16`) | ver abajo |

> Son el fondo y el trazo del hero del paso 5. "Cita confirmada" es la semántica equivocada para un hero de éxito, pero el color es el mismo y **dos nombres para un color es exactamente lo que esta sección existe para evitar**. Decisión: declarar `--success-soft: #e4ede1` y `--success: #3f6b4f` en `:root`, y **re-apuntar** `--color-status-confirmed-bg: var(--success-soft)` y `--color-status-confirmed-text: var(--success)`. El valor no cambia, así que el chip de cita confirmada no debe moverse ni un píxel — compruébalo igualmente en la Tarea 10.

**Faltan de verdad — son seis, no ocho** (verificado por grep sobre las 144 líneas de `globals.css`):

| Hex | Token nuevo | Dónde se usa |
|---|---|---|
| `#EFE6DA` | `--hairline` | pista de la barra de progreso **y** los divisores de 1px |
| `#E0D3C4` | `--switch-off` | interruptor apagado, separador de cabecera |
| `#B8A99C` | `--text-subtle` | marcador de campo vacío **y** la etiqueta `Cerrado` |
| `#9A8A7E` | `--muted-foreground-2` | «Salir», pies de tarjeta |
| `#FAF6F0` | `--muted-subtle` | fila «Domingo / Cerrado» |
| `#5F534B` | `--label` | etiquetas de campo |

> Una custom property no definida **falla en silencio**: la declaración se descarta y la pantalla sale mal sin un solo error.

### Radios

El diseño usa `8px` en botones y campos, `12px` en tarjetas y `999px` en píldoras. Con `--radius: 0.5rem` (=8px), `--radius-lg` = 8px cuadra para botones y campos. Pero `--radius-xl` es 8×1.4 = 11.2px, **no** los 12px de las tarjetas. Decisión: `rounded-[12px]` explícito en las tarjetas del asistente, en vez de mover `--radius` y repintar toda la app por 0,8 píxeles.

---

## Fases y paralelización

| Fase | Qué | `paths_touched` | Depende de |
|---|---|---|---|
| **1** | Migración V4 + campo en entidad/dominio/respuesta | `rivoo/salon-service/src/main/resources/db/migration/V4__*.sql`, `.../entity/SalonJpaEntity.java`, `.../domain/model/Salon.java`, `.../application/dto/SalonResponse.java` | — |
| **2** | Endpoint + compare-and-set + tests | `.../repository/SalonJpaRepository.java`, `.../port/out/SalonPersistencePort.java`, `.../adapter/SalonPersistenceAdapter.java`, `.../port/in/UpdateSalonUseCase.java`, `.../application/SalonService.java`, `.../in/web/SalonController.java`, `rivoo/salon-service/src/test/**` | 1 |
| **3** | Aplicar la migración en localhost y verificar | ninguno (solo ejecución) | 1, 2 |
| **4** | Contrato en el frontend: tipo + cliente de API | `rivoo-frontend/src/types/salon.ts`, `rivoo-frontend/src/lib/api/salons.ts` | contrato de 1 |
| **5** | Tokens + primitivas `Switch` y `Progress` | `rivoo-frontend/src/app/globals.css`, `src/components/ui/switch.tsx`, `src/components/ui/progress.tsx` | — |
| **6** | Portero: solo la marca, solo el dueño, y que falle hacia fuera | `src/components/layout/onboarding-gate.tsx` + su test | 4 |
| **7** | Chasis del asistente y borrado de `salon-setup` | `src/app/(onboarding)/layout.tsx`, `src/lib/stores/onboarding-store.ts`, borrar `src/app/(onboarding)/salon-setup/` | 5 |
| **8** | Los 5 pasos contra los artboards, móvil y escritorio, con precarga de horarios | `src/app/(onboarding)/{welcome,business-hours,add-employee,add-service,complete}/page.tsx` + tests | 4, 5, 7 |
| **9** | Estados vacíos | `src/app/(app)/today/page.tsx`, `src/app/book/[slug]/**` | — |
| **10** | Verificación visual y de extremo a extremo | ninguno | todas |

**Olas:** A = 1 ‖ 5 ‖ 9 · B = 2 ‖ 4 ‖ 7 · C = 3 ‖ 6 · D = 8 · E = 10.

---

## Task 1: Migración V4 y el campo en el agregado

**Files:**
- Create: `salon-service/src/main/resources/db/migration/V4__add_salon_onboarding_completed_at.sql`
- Modify: `.../infrastructure/adapter/out/persistence/entity/SalonJpaEntity.java`
- Modify: `.../domain/model/Salon.java`
- Modify: `.../application/dto/SalonResponse.java`

- [ ] **Paso 1: Escribir la migración**, con el estilo de `V3__add_salon_branding.sql` (sin comentarios, sin `IF NOT EXISTS`, tipos alineados, `AFTER` para posicionar). V4 es la siguiente libre y `AFTER status` es válido contra el orden real de columnas — ambas cosas verificadas contra la base viva.

```sql
ALTER TABLE salons
    ADD COLUMN onboarding_completed_at TIMESTAMP NULL AFTER status;

UPDATE salons
SET onboarding_completed_at = created_at,
    updated_at = updated_at
WHERE status IS NULL OR status <> 'ONBOARDING';
```

> El `UPDATE` es la decisión D4. El enum real es `('ONBOARDING','ACTIVE','INACTIVE','SUSPENDED','FAILED')` y la columna **es nulable**: sin el `status IS NULL OR`, una fila con `status` nulo evalúa a NULL, no se rellena, y queda marcada como alta sin terminar. Hoy hay cero filas así, igual que hay cero en `ONBOARDING`; se cubre por coherencia con la propia justificación de D4.
> **`updated_at = updated_at` no es redundante.** La columna es `TIMESTAMP … on update CURRENT_TIMESTAMP` (comprobado con `SHOW COLUMNS`), y MySQL documenta que una columna auto-actualizada **no** se actualiza si se le asigna explícitamente su valor actual en el `SET`. Sin esa línea, el relleno pisaría la última modificación de **todas** las filas con la fecha de la migración y destruiría esa señal para siempre.
> El `NULL` explícito en el `ADD COLUMN` se pone por portabilidad y claridad. *(No repitas el motivo que traía la v1 de este plan: decía "bajo ciertos `sql_mode`" y es falso — la variable que gobierna eso es `explicit_defaults_for_timestamp`, que en este servidor vale 1, así que aquí ya sería nulable de todos modos.)*

- [ ] **Paso 2: Campo en la entidad JPA**, después de `status`:

```java
@Column(name = "onboarding_completed_at")
private Instant onboardingCompletedAt;
```

- [ ] **Paso 3: Campo en el dominio** `Salon.java`: `private Instant onboardingCompletedAt;`

> **En cuanto añadas esta línea, un fake de los tests empieza a mentir en silencio.** `Salon` es `@Builder` con lista de campos explícita (`Salon.java:18-36`), y el `copyOf` del store en memoria de los tests lo reconstruye enumerando campos a mano (`SalonRegistrationPublicVisibilityTest.java:603-617`). Un campo que no esté en esa lista **se descarta en cada `save` y cada `findByTenantId`**, así que el CAS escribiría bien y la lectura devolvería `null`, y el síntoma señalaría al servicio en vez de al fake. Añade `.onboardingCompletedAt(s.getOnboardingCompletedAt())` a `copyOf` **en este mismo paso**, no cuando fallen los tests.

- [ ] **Paso 4: Componente en `SalonResponse`**, al final del record: `Instant onboardingCompletedAt`.

> **No toques ningún mapper y no lo añadas a `SalonPublicResponse`.** MapStruct empareja por nombre y ambos mappers son puros; verificado contra los `*Impl` generados. `SalonPublicResponse` es la superficie anónima: la fecha de alta del dueño no pinta nada ahí.

- [ ] **Paso 5: Compilar** — `mvn -o -pl salon-service -am compile` desde `E:\IdeaProjects\rivoo`.
  Maven no está en el PATH: `C:\Users\Usuario\.m2\wrapper\dists\apache-maven-3.9.9-bin\4nf9hui3q3djbarqar9g711ggc\apache-maven-3.9.9\bin\mvn.cmd`

- [ ] **Paso 6: Commit** — `feat(salon): registrar cuando el dueno termina el alta`

---

## Task 2: Endpoint idempotente para marcar el alta como terminada

**Files:**
- Modify: `.../repository/SalonJpaRepository.java`, `.../port/out/SalonPersistencePort.java`, `.../adapter/SalonPersistenceAdapter.java`, `.../port/in/UpdateSalonUseCase.java`, `.../application/SalonService.java`, `.../in/web/SalonController.java`
- Modify: `src/test/java/com/rivoo/salon/application/SalonRegistrationPublicVisibilityTest.java` (el fake, ver Paso 4)
- Test: `src/test/java/com/rivoo/salon/application/SalonOnboardingCompletionTest.java` (nuevo)

- [ ] **Paso 1: Test que falla primero.** Molde: `SalonRegistrationPublicVisibilityTest.java`. Cuatro casos: (1) de `null` a fecha; (2) llamar dos veces no cambia la primera fecha; (3) dos hilos concurrentes (molde `CyclicBarrier` del mismo fichero) → una sola escritura; (4) un tenant no puede marcar el salón de otro.

- [ ] **Paso 2: Verlo fallar** — `mvn -o -pl salon-service -am test -Dtest=SalonOnboardingCompletionTest`.

- [ ] **Paso 3: Compare-and-set en el repositorio**, con la forma de `updateStatusIfCurrentlyIs` (`SalonJpaRepository.java:44-51`). Los tres puntos obligatorios: `@Modifying(clearAutomatically = true, flushAutomatically = true)`; fijar `updatedAt` a mano porque un bulk JPQL **no dispara `@PreUpdate`** (`SalonJpaEntity.java:93-96`); y `tenantId` en el predicado porque los bulk updates **ignoran el `@Filter` multi-tenant**:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Transactional
@Query("UPDATE SalonJpaEntity s SET s.onboardingCompletedAt = :now, s.updatedAt = :now "
        + "WHERE s.tenantId = :tenantId AND s.onboardingCompletedAt IS NULL")
int markOnboardingCompletedIfPending(@Param("tenantId") String tenantId, @Param("now") Instant now);
```

- [ ] **Paso 4: Puerto de salida, adaptador — y el fake escrito a mano.** Añade `int markOnboardingCompleted(String tenantId)` a `SalonPersistencePort` y delégalo con `Instant.now()`, igual que `activateIfOnboarding` (`SalonPersistenceAdapter.java:68-72`).
  **`SalonPersistencePort` tiene una implementación escrita a mano en los tests**: `SalonRegistrationPublicVisibilityTest.java:518` (`private static class SalonStore implements SalonPersistencePort`). No es un mock: añadir un método abstracto **deja el módulo de test sin compilar**. Extiende `SalonStore` con un `markOnboardingCompleted` `synchronized` que sea un compare-and-set de verdad, calcado de su `activateIfOnboarding` (mismo fichero, `:589-601`).
  Es la **única** implementación a mano de este puerto en todo el árbol de tests, y no hay ninguna de `UpdateSalonUseCase` — comprobado. Y confirma que el `copyOf` de la Tarea 1 Paso 3 ya lleva el campo: sin eso, este CAS escribe en una fila cuya copia lo borra al leerla.

- [ ] **Paso 5: Método en el puerto de entrada**, con este javadoc:

```java
/**
 * Marks this tenant's onboarding as finished, and returns the salon either way.
 *
 * <p>Idempotent: the timestamp is written only while it is still null, so a second call —
 * a double click, two tabs, a retry — keeps the first one. Lives on this use case rather
 * than on a port of its own because a new port would change SalonController's constructor,
 * which seven tests build by hand; the cost of that has no upside here.
 */
SalonResponse completeOnboarding(String tenantId);
```

Los siete: `BusinessHoursValidationDetailTest:81`, `SalonPublicEndpointEnumerationTest:101`, `SalonRegistrationPublicVisibilityTest:443`, `BillingServiceExceptionHandlingTest:65`, `SalonExceptionHandlerOrderTest:71`, `SalonRegistrationDependencyContractTest:123`, `SalonRegistrationEnumerationTest:128`.

- [ ] **Paso 6: Implementar en `SalonService`.** **Relee siempre, en las dos ramas.** El puerto calcula `Instant.now()` por dentro, así que quien llama no sabe qué se escribió ni cuando el CAS devuelve 1; y si devuelve 0 puede ser porque otro llegó antes **o porque no hay salón**:

```java
salonPersistencePort.markOnboardingCompleted(tenantId);   // el recuento no decide nada
Salon salon = salonPersistencePort.findByTenantId(tenantId)
        .orElseThrow(() -> new SalonNotFoundException(tenantId));
return salonDtoMapper.toResponse(salon);
```

Sin `@Transactional`: el CAS ya es atómico, igual que en `publishOnOwnerArrival`.

- [ ] **Paso 7: Endpoint**, molde `SalonController.java:94-101`:

```java
@PostMapping("/api/v1/salons/me/onboarding/complete")
@PreAuthorize("hasRole('SALON_OWNER')")
public ResponseEntity<SalonResponse> completeOnboarding() {
    String tenantId = TenantContext.getCurrentTenantId();
    log.atInfo().log("POST /api/v1/salons/me/onboarding/complete");
    return ResponseEntity.ok(updateSalonUseCase.completeOnboarding(tenantId));
}
```

> **El gateway no se toca**: `Path=/api/v1/salons,/api/v1/salons/**` en `application-local.yml:16-19` **y** `application-prod.yml:16-19`; `GatewaySecurityConfig.java:23` solo abre la ruta exacta `POST /api/v1/salons`, así que esta cae en `anyExchange().authenticated()`.
> **Ojo**: un MockMvc standalone **no instala el interceptor de method-security**, así que no verifica el `@PreAuthorize`. Cúbrelo por reflexión sobre la anotación (patrón `BillingControllerAuthorizationPolicyTest`).

- [ ] **Paso 8: Tests del módulo** — `mvn -o -pl salon-service -am test`. **El `-am` es obligatorio**: sin él Maven resuelve `rivoo-common-0.1.0-SNAPSHOT.jar` desde `~/.m2` en vez de construirlo, y da verde falso. Pega la salida.

> **Este verde NO valida la JPQL.** No hay ningún `@SpringBootTest`, `@DataJpaTest` ni Testcontainers en `salon-service`: Spring Data valida el `@Query` al crear el bean del repositorio, cosa que no ocurre en esta suite. Una JPQL malformada pasa esta tarea y muere en la Tarea 3.
> Y el caso concurrente corre contra `SalonStore`, que es `synchronized` por construcción: **prueba que el servicio no decide con su propia lectura, no que la base de datos sea atómica.** La atomicidad la da el `WHERE … IS NULL`, y quien la demuestra es la Tarea 3.

- [ ] **Paso 9: Tarea 3 ANTES del commit.** Arranca el servicio (Tarea 3 Paso 2) y confirma que el repositorio se crea sin error. Solo entonces: `feat(salon): endpoint idempotente para cerrar el alta`

---

## Task 3: Aplicar la migración en localhost

**Files:** ninguno. Es ejecución y verificación.

- [ ] **Paso 1: Estado previo.**
```bash
MYSQL_PWD=rivoo123 mysql -u rivoo -h 127.0.0.1 -P 3306 salon_db \
  -e "SHOW COLUMNS FROM salons LIKE 'onboarding%'; \
      SELECT version, description FROM flyway_schema_history ORDER BY installed_rank; \
      SELECT COUNT(*) total, SUM(status='ONBOARDING') en_alta, MAX(updated_at) ult_mod FROM salons;"
```
Esperado hoy: cero filas de columna, historial hasta V3, 14 salones, 0 en `ONBOARDING`. Apunta `ult_mod`: la Tarea 3 Paso 3 comprueba que **no** ha cambiado.

- [ ] **Paso 2: Aplicarla arrancando el servicio, no a mano.**
```bash
mvn -o -q install -pl rivoo-common -DskipTests          # jar fresco en ~/.m2
mvn -o -pl salon-service spring-boot:run -Dspring-boot.run.profiles=local
```
> **Aquí NO se usa `-am`, al revés que en los tests.** Con `-am`, `spring-boot:run` se ejecuta también sobre el POM padre y muere con «Unable to find a suitable main class» sin llegar a tocar la base de datos. Pero entonces `salon-service` resolvería `rivoo-common` desde `~/.m2`, que es justo el jar rancio que el `-am` evita en los tests — de ahí el `install` previo. Comprobado el 2026-08-28: sin él, o falla el arranque, o arranca contra código viejo.
Flyway está en `enabled: true` (`application-local.yml:11-13`) y aplica V4 al arrancar **registrándola en `flyway_schema_history` con su checksum**. El `ALTER TABLE` a mano dejaría el esquema cambiado sin fila de historial, y el siguiente arranque moriría con «Duplicate column name».
Keycloak (9080) no hace falta: la configuración usa `jwk-set-uri`, que Spring resuelve de forma perezosa en la primera petición.
**Este arranque es además la única prueba de que la JPQL del Paso 3 de la Tarea 2 es válida.** Si el repositorio no se crea, el arranque falla aquí.

- [ ] **Paso 3: Verificar y parar el servicio.**
```bash
MYSQL_PWD=rivoo123 mysql -u rivoo -h 127.0.0.1 -P 3306 salon_db \
  -e "SHOW COLUMNS FROM salons LIKE 'onboarding%'; \
      SELECT version, checksum, success FROM flyway_schema_history WHERE version='4'; \
      SELECT COUNT(*) total, COUNT(onboarding_completed_at) con_fecha, MAX(updated_at) ult_mod FROM salons;"
```
Esperado: la columna existe; V4 con `success=1`; **`total == con_fecha`** (prueba de que el relleno de D4 funcionó y ningún salón existente será expulsado); y **`ult_mod` idéntico al del Paso 1** (prueba de que el `updated_at = updated_at` hizo su trabajo).

- [ ] **Paso 4:** `ddl-auto: validate` está activo, así que si el arranque no falló, entidad y tabla concuerdan. Anótalo como la evidencia que es.

---

## Task 4: Contrato en el frontend

**Files:** `src/types/salon.ts`, `src/lib/api/salons.ts`

- [ ] **Paso 1: Campo en el tipo `Salon`:**

```ts
/**
 * Instante en que el dueño terminó el alta, o null si no la ha terminado.
 * Nombre de cable exacto del componente del record SalonResponse (Jackson 3,
 * sin PropertyNamingStrategy en salon-service). No renombrar: `apiFetch` es un
 * cast sin validación y un nombre erróneo se leería como `undefined` —falsy—
 * en silencio, que aquí significa mandar al asistente a todo el mundo.
 */
onboardingCompletedAt: string | null
```

- [ ] **Paso 2: Llamada en `salonsApi`:**

```ts
completeOnboarding: (token: string) =>
  apiFetch<Salon>("/api/v1/salons/me/onboarding/complete", { method: "POST", token }),
```

- [ ] **Paso 3: Commit** — `feat(api): exponer onboardingCompletedAt y el cierre del alta`

---

## Task 5: Tokens que faltan y las dos primitivas

**Files:** `src/app/globals.css`, `src/components/ui/switch.tsx`, `src/components/ui/progress.tsx`, `src/components/ui/switch.test.tsx`

- [ ] **Paso 1: Tokens. Cada uno se declara DOS veces, y la segunda con el prefijo `--color-`.** Esto es Tailwind v4: la convención del fichero es nombre crudo + hex en `:root` (`globals.css:89-121`) y el alias **namespaced** en `@theme inline` (`:25-59`). **Solo el namespace `--color-*` genera utilidades** `bg-*` / `text-*` / `border-*`. Un `--hairline` suelto dentro de `@theme inline` no emite nada, y la pantalla sale mal sin un solo error.

```css
:root {
  --hairline: #efe6da;
  --switch-off: #e0d3c4;
  --text-subtle: #b8a99c;
  --muted-foreground-2: #9a8a7e;
  --muted-subtle: #faf6f0;
  --label: #5f534b;
  --success-soft: #e4ede1;
  --success: #3f6b4f;
}
@theme inline {
  --color-hairline: var(--hairline);
  --color-switch-off: var(--switch-off);
  --color-text-subtle: var(--text-subtle);
  --color-muted-foreground-2: var(--muted-foreground-2);
  --color-muted-subtle: var(--muted-subtle);
  --color-label: var(--label);
  --color-success-soft: var(--success-soft);
  --color-success: var(--success);
}
```

Y **re-apunta los dos que ya existían** con otro nombre, sin cambiar su valor: `--color-status-confirmed-bg: var(--success-soft);` y `--color-status-confirmed-text: var(--success);` (`globals.css:15-16`).
Usa después `bg-hairline`, `text-label`, `bg-muted-subtle`… **nunca** `bg-[var(--hairline)]`. Ojo con uno: el token se llama `--text-subtle`, así que la utilidad de color de texto es **`text-text-subtle`**, no `text-subtle` — que no emite nada.
La prohibición del `[var(--x)]` es **solo para colores**. `--motion-fast` (`globals.css:85`) vive en `:root` y no en el namespace `--duration-*`, así que ahí la forma correcta sí es `duration-[var(--motion-fast)]`.
**No añadas variantes `dark:`**: `globals.css:5-9` neutraliza el modo oscuro a propósito.

- [ ] **Paso 2: `Switch`** con los valores del artboard: `42×24`, `padding:3px`, `rounded-full`, ON `bg-primary` con el knob a la derecha, OFF `bg-switch-off` con el knob a la izquierda, knob `18×18` blanco. Accesible: `role="switch"`, `aria-checked`, teclado. Transición con `--motion-fast` (140ms), ya tokenizado.

- [ ] **Paso 3: Test del `Switch`** — `aria-checked` refleja el estado; click y Espacio disparan `onCheckedChange`; `disabled` lo impide.

- [ ] **Paso 4: `Progress`** — pista `h-1.5 rounded-full bg-hairline`, relleno `bg-primary` con `width` por porcentaje, `role="progressbar"` con `aria-valuenow/min/max`.

- [ ] **Paso 5:** `npm run test` en `E:\IdeaProjects\rivoo-frontend`. Pega la salida.
  **No toques `node_modules` ni ejecutes `npm ci`.** Un `npm ci` en este árbol ya destruyó `node_modules/.bin` una vez **devolviendo código de salida 0**; si algo falta, `npm install`.

- [ ] **Paso 6: Commit** — `feat(ui): interruptor y barra de progreso del sistema de diseno`

---

## Task 6: El portero deja de adivinar — BLOQUE CRÍTICO

**Files:** `src/components/layout/onboarding-gate.tsx`, `src/components/layout/onboarding-gate.test.tsx` (nuevo — hoy la cobertura es cero)

- [ ] **Paso 1: Tests primero. Siete casos.** **Léete `AGENTS.md:7-27` antes**: un test que siembra la caché de React Query y asserta de forma síncrona **pasa aunque el código esté roto**, porque `notifyManager` publica en un macrotask que `await act(async () => {})` no drena. Espera con `await findBy*` sobre algo que el componente no posea.
  1. Dueño con `onboardingCompletedAt` con fecha → se pintan los hijos.
  2. Dueño con `onboardingCompletedAt: null` → `router.replace("/welcome")`.
  3. `GET /salons/me` da 404 → `router.replace("/welcome")`.
  4. **`GET /salons/me` da 500 → NO se pintan los hijos.** (Hoy sí se pintan.)
  5. Dueño **sin empleados ni servicios** pero con la marca → se pintan los hijos. Es el bucle de "Omitir", convertido en test.
  6. **`EMPLOYEE` con la marca a `null` → se pintan los hijos, NO se le manda al asistente.**
  7. **Sesión a medias** (`isAuthenticated` cierto pero `accessToken` nulo, que es lo que hace `use-auth.ts:22-27` mientras re-autentica) → spinner, **ni error ni hijos**.

- [ ] **Paso 2: Verlos fallar** — `npm run test src/components/layout/onboarding-gate.test.tsx`.

- [ ] **Paso 3: Reescribir el portero.** Fuera las dos `useQuery` de empleados y servicios y sus imports.

**Y redefine `isLoading`**: hoy es `authLoading || salonLoading || (!!salon && (empLoading || svcLoading))` (`onboarding-gate.tsx:37`) y menciona dos variables que dejan de existir. Pasa a `authLoading || salonLoading`.

```tsx
const authReady = isAuthenticated && !!accessToken
const isLoading = authLoading || salonLoading
const salonNotFound = !salonLoading && !salon && salonError instanceof ApiError && salonError.problem.status === 404

// Solo el dueño hace el alta. Los endpoints de los pasos 3 y 4 y el de cierre son
// hasRole('SALON_OWNER'): a un EMPLOYEE el asistente le daría un 403 del que no puede salir.
const needsOnboarding = authReady && !isLoading && isOwner
  && (salonNotFound || (!!salon && !salon.onboardingCompletedAt))

// Un fallo REAL, no la simple ausencia de datos.
const unavailable = authReady && !isLoading && !!salonError && !salonNotFound

// LA COMPOSICIÓN DEL RENDER FORMA PARTE DEL ARREGLO. No la improvises.
if (!authReady || isLoading || needsOnboarding) return <Spinner />
if (unavailable) return <ErrorConReintentar />
return <>{children}</>
```

**Los tres errores que ya se han cometido escribiendo este plan, para que no se repitan:**
- `unavailable` **no puede ser `!salon && !salonNotFound`**. Una query deshabilitada (`enabled: isAuthenticated && !!accessToken`, `use-salon.ts:14`) queda en `status: 'pending'`, `fetchStatus: 'idle'` → `isLoading` es **falso** y `data` es `undefined`. Esa condición pintaría "error, reintentar" encima de un re-login en curso. Hay que exigir `salonError`.
- **Declarar `authReady` no basta: hay que usarlo en el render.** Ninguna de las tres expresiones se vuelve cierta cuando `authReady` es falso, así que con la composición ingenua (`if (isLoading || needsOnboarding) spinner; …`) la **sesión muerta pinta los hijos** — el caso 7 de los tests. Es alcanzable: `middleware.ts:32-34` deja pasar porque la cookie existe; lo que falta es el `accessToken` (`use-auth.ts:22-27`).
- `isOwner` es `user?.role === "ROLE_SALON_OWNER"` (`use-auth.ts:44`), y `auth.ts:55` clasifica como `ROLE_EMPLOYEE` a cualquier JWT sin rol con prefijo `ROLE_`. O sea que **un dueño mal etiquetado nunca sería enviado al asistente** y aterrizaría en un panel vacío. D7 promete "deja de fallar hacia dentro" y esta ruta concreta sigue fallando hacia dentro: se acepta a sabiendas, anótalo junto al `isOwner`.

Con `unavailable`, un estado de error con botón de reintentar — **nunca** los hijos. Un panel pintado sin salón es peor que un mensaje de error: enseña una app vacía y hace creer que se han perdido los datos.

- [ ] **Paso 4: Tests en verde.** Pega la salida.

- [ ] **Paso 5: Commit** — `fix(onboarding): que el portero mire la marca y no cuente empleados`

---

## Task 7: Chasis del asistente

**Files:** `src/app/(onboarding)/layout.tsx`, `src/lib/stores/onboarding-store.ts`, borrar `src/app/(onboarding)/salon-setup/`

- [ ] **Paso 1: Borrar `salon-setup`.** Antes, `grep -rn "salon-setup" src/` para confirmar que sigue en cero (lo estaba en la revisión: ni middleware, ni sitemap, ni tests). (D2.)

- [ ] **Paso 2: Reescribir `layout.tsx`** contra `Onboarding1.dc.html:31-32` y `Onboarding1Desktop.dc.html:32-34`:
  - Móvil: contenedor `gap-[18px] px-4 py-[18px]`; cabecera con `Paso N de 5` a la izquierda (`text-xs text-muted-foreground tabular-nums`) y a la derecha `Rivoo` + separador `1px×12px` + «Salir».
  - Escritorio (`md:`): contenedor exterior centrado con `md:px-10 md:py-11` (= `44px 40px`, `Onboarding1Desktop.dc.html:31`) — sin esto la tarjeta queda pegada arriba; bloque de marca **fuera** de la tarjeta (logo `26×26` + `Rivoo` a 22px, `gap-[11px] mb-[26px]`); la cabecera se queda con `Paso N de 5` y «Salir».
  - `Progress` debajo, con `(currentStep / totalSteps) * 100`.
  - Contenedor: móvil a sangre; `md:` tarjeta blanca centrada `rounded-[12px] border p-8`, **`md:max-w-[640px]` en los pasos 1 y 5, `md:max-w-[760px]` en los pasos 2-4**. Son dos plantillas, no un ancho único.

- [ ] **Paso 3: «Salir»** con el icono `log-out` de lucide a 13px y `stroke-width:1.75`, texto literal `Salir`. Llama a `logout()`, que es lo que hoy hace «Cerrar sesion» en `welcome/page.tsx:45-51`.

- [ ] **Paso 4: `cardWidth` en el store** (o derivado de la ruta) sin tocar `totalSteps: 5`, que ya es correcto.

- [ ] **Paso 5:** `npm run lint`.

- [ ] **Paso 6: Commit** — `refactor(onboarding): chasis de 5 pasos y baja de la ruta huerfana`

---

## Task 8: Los cinco pasos

**Files:** los cinco `page.tsx` de `(onboarding)` + un `.test.tsx` junto a cada uno.

De uno en uno, cada uno con su test y su commit. Textos **literales de la tabla de textos**, incluida la divergencia móvil/escritorio de los pasos 2, 3 y 4.

- [ ] **Paso 1 — Bienvenida** (`Onboarding1.dc.html:33`): hero `76×76` (`md:88`) con fondo `--accent` y tijeras `stroke-primary`; checklist de 3 con badge `28×28` `bg-accent text-primary text-xs font-bold`; CTA `Comencemos` → `/business-hours`. **Quita el «Cerrar sesion» del cuerpo**: ahora vive en la cabecera como «Salir».

- [ ] **Paso 2 — Horarios** (`Onboarding2.dc.html:34`, `Onboarding2Desktop.dc.html:36`): **precarga real** (ON.5) — `useQuery` sobre `salonsApi.getBusinessHours` y pasar el resultado al `WorkingHoursEditor` en vez del `hours={undefined}` de hoy (`business-hours/page.tsx:51`). El editor ya sabe adoptar el horario cuando la prop deja de ser `undefined` — el mecanismo está en `src/components/staff/working-hours-editor.tsx:53-58` (`const syncKey = hours !== undefined` … `setLocalHours(hoursStateFrom(hours))`), con su explicación en `:44-52`: **no lo toques**.
  Fila de día: móvil apilada `gap-[9px] px-[14px] py-3`; escritorio `md:grid md:grid-cols-[130px_52px_1fr] md:items-center md:gap-4 md:py-[11px]`, con los `.time` a `md:w-[92px]` y la `a` minúscula entre ambos. Domingo cerrado con `bg-muted-subtle`.
  **Sin «Omitir»**: el diseño no lo dibuja aquí, y con el horario precargado `Continuar` ya es un no-op válido.

- [ ] **Paso 3 — Empleado** (`Onboarding3.dc.html:34`): **`Nombre` y `Apellidos` van en `grid-cols-2 gap-3` YA EN MÓVIL** — el artboard de 390px los pone en dos columnas; solo Email, Telefono, Puesto y Color van apilados. En escritorio (`Onboarding3Desktop.dc.html:36`) son tres rejillas de dos: Nombre|Apellidos, Email|Telefono, Puesto|Color.
  Cuatro muestras de color `30×30` con la primera preseleccionada y el doble anillo `box-shadow`. **Sustituye el `<input type="checkbox">` por `Switch`** para «Crear cuenta de acceso», con la ayuda `Podra entrar y ver su propia agenda`. Pie: `Omitir` (1) + `Continuar` (2).

- [ ] **Paso 4 — Servicio** (`Onboarding4.dc.html:34`): cuerpo idéntico en móvil y escritorio — `Duracion (min)` y `Precio (EUR)` también en `grid-cols-2` en móvil. Textarea de `78px`. Callout `bg-secondary` con `La duracion decide el tamano del hueco en la agenda y en la reserva online.` Pie: `Omitir` + `Continuar`.

- [ ] **Paso 5 — Listo. AQUÍ SE REINTRODUCE EL BUCLE SI SE HACE MAL.** Hero `bg-success-soft` con varita `stroke-success`; tarjeta «Tu pagina de reservas» con `{origin}/book/{salon.slug}` — el `/book/` es correcto (D10).

```tsx
const updated = await salonsApi.completeOnboarding(accessToken!)
await queryClient.cancelQueries({ queryKey: ["salon", "me"] })   // mata el refetch en vuelo
queryClient.setQueryData(["salon", "me"], updated)               // clave EXACTA, escritura síncrona
resetOnboardingStore()                                           // que la barra no reabra al 100%
router.push("/today")
```

**El `cancelQueries` no es adorno.** `refetchOnWindowFocus` está en `true` globalmente (`query-provider.tsx:21`) y esta misma pantalla monta `useSalon()` (`complete/page.tsx:15`), así que la query está viva y observada aquí. Si el asistente lleva más de cinco minutos abierto y el usuario cambia de pestaña y vuelve antes de pulsar el botón, hay un refetch en vuelo que **resuelve después** del `setQueryData` y pisa el dato con el payload viejo — marca a `null`, portero, `/welcome`, bucle. Cancelar primero cierra esa ventana.

**No uses `invalidateQueries`.** El código de hoy (`complete/page.tsx:22-28`) invalida sin esperar y navega; `useSalon` tiene `staleTime: 5 * 60 * 1000` (`use-salon.ts:15`) y, con datos ya en caché, React Query v5 deja `isPending` en **falso** durante el refetch de fondo. El portero de `/today` leería el salón **rancio**, con `onboardingCompletedAt: null`, y dispararía `router.replace("/welcome")`: exactamente el bucle que este plan viene a eliminar.
`["salon"]` sirve como **prefijo de filtro** para `invalidateQueries`, pero **no es una clave válida** para `setQueryData`: la clave es `["salon", "me"]`.
Si la llamada falla, muestra el error y **no navegues**: es la única escritura que decide si el usuario puede entrar.

- [ ] **Paso 6 — Un test por paso.** El del paso 5 es el que importa: que el CTA **no** navegue hasta que la llamada resuelva, que la caché contenga el salón ya marcado **en el momento de navegar**, y que no navegue si falla.

- [ ] **Paso 7:** `npm run test` completo + `npm run lint`. Pega ambas salidas.

- [ ] **Paso 8: Commits** — uno por paso.

---

## Task 9: Estados vacíos (ON.6)

**Files:** `src/app/(app)/today/page.tsx`, `src/app/book/[slug]/**`

- [ ] **Paso 1: «Hoy» sin servicios.** Ahora que omitir es legítimo, un salón puede llegar al panel sin un solo servicio. `EmptyState` (`src/components/shared/empty-state.tsx`) con una acción que lleve a crear el primero.

- [ ] **Paso 2: Página pública sin servicios** → «Este salon aun no acepta reservas online». **Distíngueló de `servicesUnavailable` / `employeesUnavailable`** (`types/salon.ts:66-68`), que significan «no hemos podido cargarlo», no «no hay». Un fallo de red no debe decirle a un visitante que el salón no acepta reservas.

- [ ] **Paso 3:** tests de ambos estados + commit.

---

## Task 10: Verificación

- [ ] **Paso 1: Reactor completo** — `mvn -o clean test` desde `E:\IdeaProjects\rivoo`. Cuenta los `<testcase>` de los XML de surefire, **no** te fíes del código de salida. Surefire fusiona dos métodos con el mismo nombre en `@Nested` distintos de una misma clase externa en un `Run 1 / Run 2`, y el fallo desaparece de la cuenta.
- [ ] **Paso 2:** `npm run test` y `npm run lint`, salida pegada.
- [ ] **Paso 3: Comparación visual.** Con la app levantada, captura los 5 pasos a 390×844 y a 1440×900 y compara artboard a artboard. Guarda en `docs/specs/onboarding-reanudable/verificacion/{paso}-{movil|escritorio}.png`. Elemento a elemento contra la tabla: alto de botón, radios, tamaños de fuente, los cuatro colores, ancho de tarjeta 640 vs 760, y las tres divergencias de copy.
- [ ] **Paso 4: El chip de «cita confirmada» no se ha movido**, después de re-apuntar sus dos tokens (Tarea 5 Paso 1). Mismo color, mismo tamaño.
- [ ] **Paso 5: Recorrido de extremo a extremo con el bucle como prueba.** Dar de alta un salón, **omitir los pasos 3 y 4**, pulsar «Ir al dashboard», comprobar que se llega a `/today` **y que al recargar se sigue llegando**. Esto es el fallo que abre el plan; si no se demuestra, no está hecho.
- [ ] **Paso 6:** `SELECT tenant_id, onboarding_completed_at FROM salons` para ver la marca escrita una sola vez.

---

## Execution Order

**Backend (`E:\IdeaProjects\rivoo`):**
```
Fase 1  Migración + campo                (sin dependencias)
Fase 2  Endpoint + CAS + tests           depende de 1
Fase 3  Aplicar en localhost             depende de 1, 2 — y va ANTES del commit de la Fase 2
```

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**
```
Fase 5  Tokens + primitivas              (sin dependencias)  ┐ paralelas
Fase 9  Estados vacíos                   (sin dependencias)  ┘
Fase 4  Tipo + cliente de API            solo necesita el contrato de la Fase 1
Fase 6  Portero                          depende de 4
Fase 7  Chasis                           depende de 5
Fase 8  Los 5 pasos                      depende de 4, 5, 7
```

**Coordinación:** en cuanto la Fase 1 fija el nombre del campo (`onboardingCompletedAt`), backend y frontend corren en paralelo — repos distintos, sin colisión. La Fase 10 se hace al final con las dos mitades juntas y la migración ya aplicada.

---

## Dependencies on other specs/FRs

| Spec/FR | Relación | Implicación |
|---|---|---|
| **Reserva pública** (`docs/specs/reserva-publica/`) | **Pre-requisito, ya cerrado** | El paso 5 enseña `/book/{slug}`, que solo funciona porque la reserva pública ya está entregada. |
| **FE.1-FE.10** (pantallas con código, bloque 7 del todo) | **Complementaria** | Comparten `Switch`, `Progress` y los seis tokens nuevos de la Tarea 5. Hacer esto primero se las deja hechas. |
| **Verificación de correo del dueño** (`beb92b0`) | **Pre-requisito, ya cerrado** | En local `rivoo.keycloak.owner.email-verified-on-creation: true` permite recorrer el alta sin buzón. En producción sigue sin poder registrarse nadie hasta que haya SMTP. |
| **Detalle de cita / Pantalla «Hoy»** (bloques 3 y 4 del todo) | **Consumidora** | La Tarea 9 toca `today/page.tsx`; quien haga el bloque 4 partirá de ese estado vacío. |
