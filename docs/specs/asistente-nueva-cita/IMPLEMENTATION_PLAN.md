# Asistente de nueva cita — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: `executing-plans`. Los pasos usan
> casillas (`- [ ]`). El revisor se lanza al terminar el BLOQUE ENTERO (T11),
> nunca por tarea.

**Objetivo:** que `/appointments/new` sea identico a sus diez artboards, y que
deje de heredar el chasis de la app interna (barra lateral en escritorio, barra
inferior en movil) cuando los diez dibujan pantalla completa.

**Arquitectura:** el asistente sale del grupo de rutas `(app)` a un grupo nuevo
`(fullscreen)` que solo monta `OnboardingGate`. Dentro, un chasis propio
(`NewAppointmentShell`) con cabecera de 56px en movil y 68px en escritorio, y
los cinco pasos reescritos contra sus artboards. Las dos piezas de escritorio
que la reserva publica ya tiene construidas y probadas —el stepper de cinco
nodos y la tarjeta de resumen de 320px— se promueven a primitivas compartidas
en vez de copiarse.

**Complejidad:** COMPLEJA (6+ ficheros, transversal, dos repos). Motor de
ejecucion: `executing-plans` (opcion A, model-driven).

**Stack:** Next.js 16 App Router · TypeScript · Tailwind v4 · Shadcn/UI +
`@base-ui/react` · Zustand · React Query v5 · Vitest 4 · Playwright.
Backend: Java 25 / Spring Boot 4 (`client-service`).

---

## 1 · Datos verificados

Todo lo de esta seccion esta LEIDO, no supuesto. Cada hecho vive en UN solo
sitio; las tareas de §5 lo REFERENCIAN en vez de repetirlo. Las cinco versiones
del plan del bloque 2 fallaron por duplicar valores y dejarlos divergir.

### 1.1 · Artboards de movil (390x844)

Ficheros `design/NuevaCitaPaso{1..5}.dc.html`. Marco: `width:390px;
height:844px; overflow:hidden; background:#FBF7F2`.

> **Las referencias de linea de esta seccion y de §1.2 se regeneraron con `grep`
> contra los ficheros.** La primera version del plan las tenia desplazadas en
> nueve de los diez artboards porque se transcribieron de una lectura
> renumerada (`sed -n 'N,$p' | cat -n`). Si al abrir una referencia no aparece
> lo que dice el plan, **es el plan el que esta mal**: verificalo con `grep` y
> avisa, no adivines.

**Cabecera, identica en los cinco pasos** (`Paso1:25`, `Paso2:24`, `Paso3:24`,
`Paso4:26`, `Paso5:25`):
- `height:56px; padding: 0 12px 0 8px; border-bottom:1px solid #E7DCCF`,
  `justify-content: space-between`.
- Izquierda: caja de 44x44. En el paso 1 esta **VACIA** (`Paso1:26` es un div
  sin contenido — el hueco se reserva, no se colapsa). En los pasos 2-5 lleva
  un chevron izquierda de 20px, `stroke #2A2320`, `stroke-width 2`.
- Centro: "Nueva cita", 14px/600.
- Derecha: caja de 44x44 con una X de 20px, `stroke #7A6A5F`,
  `stroke-width 1.75`.

**Barra de progreso, identica en los cinco** (`Paso1:33`, `Paso2:34`,
`Paso3:34`, `Paso4:36`, `Paso5:35`): `display:flex; gap:5px;
padding: 14px 16px 0 16px`. CINCO tramos `height:3px; flex-grow:1;
border-radius:999px`. Los superados y el actual en `#B4522F`, el resto en
`#E7DCCF`. **NO lleva contador "1 / 5"** (a diferencia de la reserva publica,
que si lo lleva — `booking-step-shell.tsx:242`).

**Cuerpo, identico en los cinco**: `display:flex; flex-direction:column;
gap:16px; padding: 14px 16px 0 16px`.

**Titulo, identico en los cinco** (eyebrow en `Paso1:44`, `Paso2:45`,
`Paso3:45`, `Paso4:47`, `Paso5:46`): columna `gap:3px` con
- eyebrow "Paso N de 5", 11px/600, `letter-spacing:0.06em`, mayusculas,
  `#9A8A7E`;
- titulo `.display` 27px, `line-height:1.1`, `letter-spacing:-0.015em`.

Titulos: 1 "Elige un profesional" · 2 "Elige un servicio" · 3 "Elige fecha y
hora" · 4 "Selecciona un cliente" · 5 "Confirma la cita".
**Movil no lleva subtitulo en ningun paso.**

**Paso 1 — filas de profesional.** Clase `.opt` (`Paso1:18`): `display:flex;
align-items:center; gap:12px; padding:14px; border:1px solid #E7DCCF;
border-radius:10px; background:#FFFFFF`. Clase `.av` (`Paso1:19`): 40px
circulo, 13px/600. Lista en columna, `gap:10px`.
- Primera fila "Sin preferencia" / "Cualquier disponible" (`Paso1:50`):
  `border-style:dashed; border-color:#D8C9B8; background:#F5EEE6`; avatar con
  `background:#E7DCCF; color:#7A6A5F` y un icono de usuarios de 19px.
  **LLEVA chevron derecha igual que las demas** (`Paso1:58`).
- Filas de empleado: avatar con las iniciales, nombre 15px/600, puesto 12px
  `#7A6A5F`, y un chevron derecha de 18px `stroke #B8A99C` al final.
- Empleado que hoy no trabaja (`Paso1:88`): la fila entera a `opacity:0.55`,
  subtitulo con el puesto, un separador y "hoy no trabaja"; avatar
  `#F0EAE3`/`#9A8A7E` (`Paso1:89`); y **SIN chevron**.
  *(Lo dibujado. Pero **D33 decide conservar el chevron y dejar la fila
  seleccionable**: quitarselo dejaria a ese empleado sin poder recibir ninguna
  cita en los 30 dias del horizonte, no solo hoy. Leer D33 antes de construir
  esta fila.)*

**Paso 2 — servicios agrupados.** Clase `.svc` (`Paso2:18`): misma caja que
`.opt` pero `justify-content:space-between`.
- Bajo el titulo, una fila de "pildoras" de contexto (`Paso2:49-54`): pildora
  de 32px, `padding: 0 11px 0 5px; border-radius:999px; border:1px solid
  #E7DCCF; background:#FFFFFF; font-size:12px`, con un avatar de 22px (9px/700)
  y el nombre de pila.
- Cabecera de categoria: 11px/600, `letter-spacing:0.05em`, mayusculas,
  `#9A8A7E`. La segunda y siguientes llevan `margin-top:4px`.
- Tarjeta de servicio: nombre 15px/600, duracion 12px `#7A6A5F`, y el precio a
  la derecha `.display .num` 20px, `white-space:nowrap`.
- Servicio que el empleado NO ofrece (`Paso2:94`): `opacity:0.5` y el subtitulo
  sustituido por "{nombre} no ofrece este servicio". **NO se oculta.**

**Paso 3 — fecha y hora.**
- Dos pildoras de contexto: profesional (con avatar) y servicio (con icono de
  tijeras de 13px) + separador y duracion.
- Tira de dias (`Paso3:60-85`) — **SEIS celdas es el ANCHO VISIBLE, no el
  horizonte: D29 lo pone sobre una tira de 30 dias con scroll**. Cada celda,
  `width:52px; height:62px;
  border-radius:10px; border:1px solid #E7DCCF; background:#FFFFFF`, en fila
  con `gap:8px`, `flex-shrink:0`. Dentro: dia de la semana 10px `#7A6A5F` en
  mayusculas, y el numero `.display .num` 20px `line-height:1`.
  - Primera celda en `:61`; **seleccionada** en `:65`
    (`border-color:#B4522F; background:#B4522F; color:#FFFFFF`, con el dia de
    la semana a `opacity:0.85`); **cerrada** en `:77`
    (`border-color:#EFE6DA; background:#F5EEE6; color:#B8A99C`).
- Secciones de huecos: etiqueta "Manana"/"Tarde" 12px/600,
  `letter-spacing:0.05em`, mayusculas, `#9A8A7E`; rejilla
  `repeat(3, minmax(0,1fr))` con `gap:8px`.
- Boton de hueco `.slotbtn` (`Paso3:18`): **`height:46px`** — OJO, el de
  escritorio es 44px (§1.2) —, `border-radius:8px; border:1px solid #E7DCCF;
  background:#FFFFFF; font-size:14px; font-weight:500`, tabular.
  Seleccionado: `#B4522F` de fondo y borde, texto blanco, 600.
  Ocupado (`Paso3:92,93,98`): `border-color:#EFE6DA; background:#F5EEE6;
  color:#B8A99C; text-decoration:line-through`.
- **Pie fijo** (`Paso3:112`): `position:absolute; left/right:0; bottom:0;
  padding: 14px 16px 20px 16px; border-top:1px solid #E7DCCF;
  background:#FBF7F2`, columna `gap:10px`. Fila superior: resumen a la
  izquierda (dia + separador + rango horario, 12px `#7A6A5F`) y precio a la
  derecha (`.display .num` 20px). Debajo, CTA "Continuar" de `height:50px`,
  `border-radius:8px`, `#B4522F`, blanco 15px/600.

**Paso 4 — cliente.** Clases: `.row` (`Paso4:18`, `padding:12px;
border-radius:8px`), `.av` (`:19`, 40px), `.chip` (`:20`, 30px).
- Tres pildoras de contexto: profesional con avatar, servicio, y el dia + la
  hora en tabular.
- Buscador: `height:44px; padding: 0 14px 0 40px; border:1px solid #E7DCCF;
  border-radius:8px; background:#FFFFFF`, lupa de 17px `#9A8A7E` absoluta a
  `left:13px`, placeholder "Buscar por nombre..." 14px `#9A8A7E`
  (`Paso4:62`).
- Tarjeta "Crear nuevo cliente" (`Paso4:67`): `.row` con
  `border-style:dashed; border-color:#DCC9BB; background:#F6E7E0`; avatar
  blanco con un "+" de 19px `#B4522F`; titulo 14px/600 `#8F3F24`; subtitulo
  "Anadir datos manualmente" 12px `#7A6A5F`.
- Etiqueta "Clientes recientes" 12px `#7A6A5F`, `margin-top:4px` (`Paso4:77`).
- Fila de cliente: avatar 40px con iniciales, nombre 14px/600, y una segunda
  linea `.num` 12px `#7A6A5F` con el telefono, un separador y "N visitas". Sin
  telefono: "Sin contacto" + separador + "N visitas", en `#9A8A7E`.
- Lista en columna con `gap:8px`.

**Paso 5 — confirmacion.** Clases `.line` (`Paso5:18`) y `.lbl` (`:19`,
`width:74px`, 12px `#9A8A7E`).
- Tarjeta: `padding:16px; border:1px solid #E7DCCF; border-radius:12px;
  background:#FFFFFF`, columna `gap:14px`.
  - Cabecera: `align-items:baseline; justify-content:space-between;
    padding-bottom:14px; border-bottom:1px solid #EFE6DA`. Izquierda: rango
    horario `.display .num` 26px `line-height:1.1` y debajo la fecha larga
    13px `#7A6A5F`. Derecha: pildora "Pendiente" (`Paso5:57`),
    `padding:3px 9px; border-radius:999px; background:#FAEFD6; color:#8A5B12;
    font-size:10px; font-weight:600`.
  - Tres filas `.line` (`gap:12px`, `align-items:flex-start`): "Cliente"
    (nombre 14px/600 + telefono `.num` 12px), "Profesional" (punto de 8px del
    color del empleado + nombre 14px), "Servicio" (nombre 14px + duracion
    `.num` 12px).
- Notas: etiqueta "Notas para el profesional" 12px/600 `#7A6A5F`; caja de
  `height:76px; padding:12px 14px; border-radius:8px`, placeholder "Notas
  (opcional)" 13px `#9A8A7E`.
- **Pie fijo** (`Paso5:91`): igual que el del paso 3 pero con "Total" 13px
  `#7A6A5F` a la izquierda y `.display .num` 24px a la derecha; CTA de 50px
  con un check de 18px (`stroke-width:2.25`) + "Crear cita".

### 1.2 · Artboards de escritorio (1440x900)

Ficheros `design/NuevaCitaDesktopPaso{1..5}.dc.html`. Marco:
`display:flex; flex-direction:column; width:1440px; height:900px;
overflow:hidden; background:#FBF7F2`.

> **`DesktopPaso1` tiene un bloque `<style>` mas corto que los otros cuatro**
> (le faltan `.sumval`, `.slotbtn`, `.day`, `.lbl`, `.secl`, `.in`, `.ph`,
> `.fld`), asi que sus lineas van 8 por debajo. Por eso la cabecera esta en
> `:29` en el paso 1 y en `:37` en los pasos 2-5, y el contenedor en `:43` y
> `:51` respectivamente. No es un error de transcripcion.

**Clases compartidas** (`Paso1:18-23`; en `Paso2..5` estan en `:18-31`):
`.step` (`:18`), `.dot` (`:19`), `.card` (`:20`), `.av` (`:21`, 44px),
`.sum` (`:22`), `.sumlbl` (`:23`), y en los pasos 2-5 ademas `.sumval` (`:24`),
`.slotbtn` (`:25`, **44px**), `.day` (`:26`, 68px), `.lbl` (`:27`) y `.secl`
(`:31`).

**Cabecera, identica en los cinco** (`Paso1:29`, `Paso2..5:37`):
`height:68px; flex-shrink:0; padding: 0 28px; border-bottom:1px solid #E7DCCF;
background:#F8F2EA`, `justify-content: space-between`.
- Izquierda: marca svg de 24px `stroke #B4522F` + "Nueva cita" `.display` 20px,
  `gap:12px`.
- Derecha (`gap:10px`): "Cancelar" 13px `#7A6A5F`, y un boton de 38x38,
  `border-radius:8px; border:1px solid #E7DCCF; background:#FFFFFF` con una X
  de 18px `stroke #7A6A5F`, `stroke-width 1.75`.

**Contenedor, identico en los cinco** (`Paso1:43`, `Paso2..5:51`):
`display:flex; justify-content:center; padding: 32px 40px`, y dentro
`display:flex; gap:40px; width:1120px`.
- Columna principal: `flex-direction:column; gap:26px; flex-grow:1;
  min-width:0`.
- Aside (`Paso1:122`, `Paso2:129`, `Paso3:138`, `Paso4:128`, `Paso5:93`): `width:320px;
  flex-shrink:0; padding:22px; border:1px solid #E7DCCF; border-radius:12px;
  background:#FFFFFF; align-self:flex-start`, columna `gap:16px`.

**Stepper, identico en los cinco**: fila `gap:14px`.
- `.step`: `gap:7px; font-size:13px; color:#B8A99C`. Activo: `color:#2A2320;
  font-weight:600`.
- `.dot`: circulo de 22px, 11px/700. Pendiente: `border:1px solid #D8C9B8`.
  Activo: `background:#B4522F; color:#FFFFFF`. Superado:
  `background:#E4EDE1; color:#3F6B4F` con un check de 12px
  (`stroke-width:3`).
- Conector: `width:26px; height:1px`. `#D8C9B8` si el paso a su izquierda ya
  se supero, `#E7DCCF` si no.
- Etiquetas: **Profesional · Servicio · Fecha y hora · Cliente · Confirmar**.

**Titulo, identico en los cinco**: columna `gap:6px`, titulo `.display` 34px
`line-height:1.05`, subtitulo 14px `#7A6A5F` (`Paso1:61`, `Paso2:69`,
`Paso3:69`, `Paso4:69`, `Paso5:69`).
Subtitulos: 1 "Solo aparecen los que trabajan hoy." (ver D12) · 2 "Solo los
que ofrece {nombre}." · 3 "Huecos libres de {nombre} para {servicio}
({duracion})." · 4 "Busca uno existente o crea uno nuevo sin salir del flujo." ·
5 "Revisa antes de crearla en la agenda."

**Aside — estructura comun, con TRES variaciones por paso (§1.3.G y §1.3.H):**
- Encabezado "Resumen" 12px/600, `letter-spacing:0.06em`, mayusculas,
  `#9A8A7E`.
- Filas `.sum`: etiqueta `.sumlbl` 12px `#9A8A7E`; valor `.sumval` 14px/600
  alineado a la derecha, o una raya larga en 14px `#C4B5A6` si esta vacio.
- **Variacion 1** — en el paso 1 el valor de "Profesional" no es la raya sino
  el texto "Sin elegir", **con el estilo de la raya** (`Paso1:125`:
  `font-size:14px; color:#C4B5A6`, sin `font-weight:600`).
- Separadores de `height:1px; background:#EFE6DA` entre filas.
- **Variacion 2** — la fila "Servicio" lleva segunda linea (`<br>` +
  `.sumlbl .num` con duracion + separador + precio) en los pasos **3 y 4**
  (`Paso3:142`, `Paso4:132`) y **NO** en el paso 5 (`Paso5:97`).
- *(La tercera linea de la celda de dia de escritorio tiene TRES textos en el
  canvas: "9 huecos" (`Paso3:83`), "Cerrado" (`:98`) y **"Sin huecos"**
  (`:78`, dia abierto pero lleno). D7 solo descarta el contador; "Sin huecos"
  se decide en D30.)*
- **Variacion 3** — el valor de "Fecha y hora" lleva SIEMPRE el dia delante, en
  forma corta y sin tilde, y lo que cambia es la hora: `"Mie 28, 11:00"` en el
  paso 3 (`Paso3:144`) y `"Mie 28, 11:00 - 12:30"` en los pasos 4 y 5
  (`Paso4:134`, `Paso5:99`). **No es "hora suelta vs rango"**: los tres llevan
  el dia. Y `"Mie"` no lo produce ninguna utilidad del repo tal cual —
  `format(d, "EEE", {locale: es})` da `"mié"`, con tilde y en minuscula
  (§1.8.3).
- Paso 5: separador y fila extra "Total" cuyo valor es `.display .num` 20px.
- CTA de `height:46px; margin-top:4px; border-radius:8px`, 15px/600.
  Deshabilitado (pasos 1, 2 y 4): `background:#E3D3C6; color:#9A8A7E`
  (`Paso1:133`, `Paso4:137`). Activo (pasos 3 y 5): `background:#B4522F;
  color:#FFFFFF`. Texto "Continuar" en 1-4 y "Crear cita" en el 5.
- **El aside de escritorio NO lleva nota de confianza** (la reserva publica si:
  "Sin registro · cancela gratis hasta 24h antes").

**Paso 1 — rejilla de tarjetas**: `grid-template-columns:repeat(2,
minmax(0,1fr)); gap:14px`.
- "Sin preferencia" primero, con el mismo tratamiento discontinuo que en movil,
  y **sin la columna de citas**.
- Cada empleado lleva a la derecha una columna alineada a la derecha con el
  numero de citas de hoy (`.num` 13px/600) y "citas hoy" (10px `#9A8A7E`).
- El que hoy no trabaja (`Paso1:112`): `opacity:0.5`, subtitulo "Hoy no
  trabaja", y **sin la columna de citas**.

**Paso 2 — servicios**: etiqueta de categoria `.secl` y una rejilla de dos
columnas por categoria. Cada tarjeta es `.card` con
`justify-content:space-between`: a la izquierda nombre 15px/600 + duracion 12px
`#7A6A5F`; a la derecha el precio `.display .num` 20px. No ofrecido
(`Paso2:119`): `opacity:0.5` y subtitulo "{nombre} no lo ofrece".

**Paso 3 — fecha y hora**.
- Etiqueta de mes `.secl` y rejilla de SIETE dias `repeat(7, minmax(0,1fr))`
  con `gap:10px`. **Siete es el ancho de UNA PAGINA, no el horizonte: D29 pone
  cuatro paginas con navegacion por semanas**, y por eso el artboard dibuja una
  etiqueta de mes.
- `.day` (`Paso3:26`): `height:68px`, columna centrada `gap:3px`. TRES lineas:
  dia de la semana 11px `#7A6A5F`, numero `.display .num` 21px
  `line-height:1`, y una tercera linea de 10px `#9A8A7E` con "9 huecos"
  (`Paso3:83`) / "Cerrado" / "Sin huecos". Seleccionado: `#B4522F` de fondo y
  borde, blanco, con las lineas 1 y 3 a `opacity:0.85`. Cerrado/sin huecos:
  `border-color:#EFE6DA; background:#F5EEE6; color:#B8A99C`.
- Secciones "Manana"/"Tarde" con rejilla de SEIS columnas, `gap:10px`.
- `.slotbtn` (`Paso3:25`): **`height:44px`**. Ocupado: `Paso3:118`.
- **Escritorio no tiene pie fijo**: el CTA vive en el aside.

**Paso 4 — cliente**: buscador de `height:46px; padding: 0 14px 0 42px` con
lupa de 18px y placeholder "Buscar por nombre, telefono o email...". Debajo,
rejilla de dos columnas `gap:14px` cuya PRIMERA celda es la tarjeta "Crear
nuevo cliente" (mismo tratamiento que en movil, `.av` blanco con "+" de 20px) y
el resto son clientes: avatar, nombre 15px/600, contacto 12px `#7A6A5F`, y a la
derecha el numero de visitas (`.num` 13px/600) sobre la palabra "visitas" (10px
`#9A8A7E`).

**Paso 5 — confirmacion**: tarjeta de `padding:24px; border-radius:12px`,
columna `gap:18px`.
- Cabecera: `align-items:flex-start; padding-bottom:16px; border-bottom:1px
  solid #EFE6DA`. Rango `.display .num` 30px `line-height:1.05`, fecha larga
  14px `#7A6A5F`, y a la derecha la pildora "Se creara como Pendiente"
  (`Paso5:78`, `padding:4px 10px; font-size:11px`, mismos colores que en
  movil).
- Cuerpo: rejilla de TRES columnas `gap:20px`, cada una columna `gap:4px` con
  `.sumlbl` + valor. "Cliente" (15px/600 + telefono `.num` 12px),
  "Profesional" (punto de 8px + nombre 15px), "Servicio" (nombre 15px +
  duracion `.num` 12px).
- Notas: etiqueta `.lbl` (12px/600 `#7A6A5F`) "Notas para el profesional" y
  caja de `height:90px`, texto 14px.

### 1.3 · Incoherencias entre artboards, y como se resuelven

Comprobadas leyendo los diez ficheros. No son ambiguedades: son contradicciones
del propio canvas, y el plan decide cada una aqui o en §2.

| # | Que dibuja | Donde | Resolucion |
|---|---|---|---|
| A | El avatar de la misma empleada sale terracota en el paso 2 y VERDE en el paso 3 | `Paso2` vs `Paso3` (pildora de contexto) | Desliz de dibujo. El color sale del empleado (`colorHex` o paleta), nunca del hex literal. Ver D14 |
| B | La misma duracion sale "90min" en la pildora del paso 3 y "1h 30min" en la tarjeta del paso 2 y en el aside | `Paso3` vs `Paso2`, `DesktopPaso3:142` | Gana la forma larga: 5 apariciones frente a 1, y es la que ya produce `formatDuration`. Ver D10 |
| C | El subtitulo de escritorio del paso 1 dice "Solo aparecen los que trabajan hoy" y el mismo artboard pinta a alguien que hoy no trabaja | `DesktopPaso1:61` vs `:112` | Manda la FILA. El subtitulo se sustituye. Ver D12 |
| D | La pildora de estado dice "Pendiente" en movil y "Se creara como Pendiente" en escritorio | `Paso5:57` vs `DesktopPaso5:78` | No es contradiccion: son dos anchos con espacio distinto. Se pintan los dos textos, cada uno en su ancho |
| E | El buscador promete "nombre" en movil y "nombre, telefono o email" en escritorio | `Paso4:62` vs `DesktopPaso4` | Igual que D: dos placeholders, uno por ancho. La BUSQUEDA es la misma en los dos, sobre los tres campos (B1) |
| F | La tira de dias de movil NO es de seis dias consecutivos: dibuja MAR 27, MIE 28, JUE 29, VIE 30, SAB 31 y **LUN 02**, saltandose el domingo. Escritorio dibuja los SIETE consecutivos, con el domingo marcado "Cerrado". Y movil SI pinta el sabado cerrado, asi que la regla no es "oculta los cerrados" | `Paso3:60-85` vs `DesktopPaso3` | El salto del domingo es un ajuste de dibujo para que quepan seis celdas utiles en 390px, no una regla. **Movil pinta dias CONSECUTIVOS desde hoy, seis VISIBLES sobre una tira de 30 con scroll; escritorio, siete por pagina sobre cuatro paginas.** Las seis/siete celdas son el ANCHO de la tira, no el horizonte reservable. Ver D29, que es la decision completa |
| G | La fila "Servicio" del aside lleva segunda linea en los pasos 3 y 4, y NO en el paso 5 | `DesktopPaso3:142`, `DesktopPaso4:132` vs `DesktopPaso5:97` | Se respeta: el aside NO es identico en los cinco pasos. Ver D20 |
| H | El valor de "Fecha y hora" del aside lleva el dia en los TRES pasos ("Mie 28, ...") y solo cambia la hora: inicio en el 3, rango en el 4 y el 5 | `DesktopPaso3:144` vs `DesktopPaso4:134`, `DesktopPaso5:99` | Se respeta, por el mismo motivo que G. Ver D20. OJO: no es "hora suelta vs rango", el dia esta en los tres |

### 1.4 · Estado del codigo (leido, no supuesto)

**Ruta y chasis**
- `src/app/(app)/appointments/new/page.tsx` (66 lineas): monta cabecera propia
  de ~50px (`px-4 py-3` sobre un `text-sm`), `WizardProgress` a la derecha del
  titulo, y los cinco pasos con `{step === N && ...}`. Hace `reset()` en un
  `useEffect` al montar (`:20-22`).
- Esta DENTRO de `(app)`, asi que `(app)/layout.tsx` le pone barra lateral en
  escritorio (`:83`) y `BottomNav` + `pb-20` en movil (`:104,110`). Los diez
  artboards dibujan pantalla completa: ninguno lleva ni sidebar ni barra
  inferior.
- `(app)/appointments/` contiene UNICAMENTE `new/` — el bloque 4 borro
  `appointments/[id]`. Verificado: `find src/app -path "*appointments*"`
  devuelve un solo fichero.
- Grupos de rutas existentes: `(app)`, `(auth)`, `(onboarding)`, mas `book/`,
  `dev/` y `api/`. El middleware (`src/middleware.ts`) trabaja sobre `pathname`
  y es indiferente a los grupos, asi que D1 no lo toca. **No existe ningun grupo de pantalla completa.**
- `OnboardingGate` (`components/layout/onboarding-gate.tsx`) lo monta HOY
  `(app)/layout.tsx:60`. Sacar la ruta de `(app)` se lo quita: hay que
  remontarlo en el layout nuevo.

**Store**
- `src/lib/stores/wizard-store.ts` (80 lineas): `step`, `selectedEmployee`,
  `anyEmployee`, `selectedService`, `selectedDate`, `selectedSlot`,
  `selectedClient`, `newClientData`, `notes`, con acciones que ya limpian
  aguas abajo (`selectEmployee` borra servicio/fecha/hueco, `selectService`
  borra fecha/hueco). `reset()` vuelve a `INITIAL_STATE`.

**Pasos actuales** (todos en `src/components/appointments/wizard/`)
- `employee-step.tsx` (83): rejilla de 2 columnas de tarjetas CENTRADAS. El
  artboard movil dibuja una LISTA de filas y el de escritorio una rejilla de
  tarjetas horizontales. No pinta puesto en movil ni "citas hoy" ni el estado
  "hoy no trabaja".
- `service-step.tsx` (85): lista plana, **filtra fuera** los servicios que el
  empleado no ofrece (`:23-27`). Los dos artboards los DIBUJAN atenuados. No
  agrupa por categoria.
- `datetime-step.tsx` (153): navegador de mes + tira de 30 dias + rejilla de 4
  columnas con TODOS los huecos juntos. Los artboards dibujan 6 dias (movil) /
  7 (escritorio), separacion Manana/Tarde, y huecos ocupados tachados.
- `client-step.tsx` (204): la lista de clientes solo aparece con
  `search.length >= 2` (`:151`); el artboard dibuja "Clientes recientes" SIN
  buscar. Tiene un boton "Continuar sin cliente" (`:193-201`) que **ningun
  artboard dibuja**. El formulario de alta en linea (`:41-112`) tampoco tiene
  artboard.
- `confirmation-step.tsx` (196): tarjeta de iconos + lineas; el artboard dibuja
  una tarjeta con cabecera de rango horario grande y pildora de estado. Llama a
  `appointmentsApi.create` a pelo con `useState(isSubmitting)` en vez de
  `useMutation`.
- `wizard-progress.tsx` (45): circulos numerados con etiquetas. El artboard
  movil dibuja CINCO BARRAS PLANAS; el de escritorio, el stepper de §1.2.

**Tests existentes del asistente**: TRES —
`wizard/datetime-step.test.tsx` (69 lineas), `wizard/wizard-progress.test.tsx`
(30) y **`src/lib/stores/wizard-store.test.ts`** (5050 B), que ya afirma
`selectDateTime("2026-03-25", "10:00")` con DOS argumentos en `:87` y `:104`.
No hay tests de `employee-step`, `service-step`, `client-step`,
`confirmation-step` ni de la pagina.

**Lo que la reserva publica ya tiene construido y probado**
- `components/booking/booking-stepper.tsx` (88): el stepper de cinco nodos de
  §1.2, MISMOS COLORES Y TAMANOS DE NODO (`size-[22px]`, `text-[11px] font-bold`,
  `bg-success-soft text-success`, conector `bg-[#D8C9B8]`, check de 12px con
  `strokeWidth 3`), pero **el espaciado NO coincide por debajo de `xl:`**:
  `:26` es `gap-2 xl:gap-3.5` donde el artboard pide `gap:14px` fijo, `:37` es
  `w-3 xl:w-[26px]` donde el artboard pide `width:26px` fijo, y `:45` es
  `gap-1.5` donde el artboard pide `gap:7px`. El comentario `:21-25` explica
  por que: a 1024, con el aside de 320px, los cinco nodos no caben con las
  medidas del artboard. Consecuencia para este bloque: entre 1024 y 1279 el
  asistente heredara ese stepper comprimido, y la comparacion visual de T10
  (que corre a 1440) NO lo vera. Se acepta y se anota como deuda: es el mismo
  compromiso que ya vive en la reserva publica, y resolverlo de otra forma
  aqui las haria divergir. Lo unico que no encaja: `STEP_LABELS` esta escrito a fuego
  (`:3`) con las etiquetas de la reserva publica, en OTRO orden ("Servicio",
  "Profesional", ...), y su visibilidad tambien (`hidden md:flex`, `:26`).
  **Tiene UN solo importador** (`booking-step-shell.tsx:6,105`) y ningun test
  propio.
- `components/booking/booking-summary-aside.tsx` (121): la tarjeta de resumen
  de §1.2, con filas `label`/`value`/`detail`, separadores `bg-hairline`, fila
  "Total" a 20px, raya `text-text-placeholder` cuando no hay valor, y CTA
  `size="xl"` con `h-[46px]`. Lo unico que no encaja: el encabezado esta
  escrito a fuego ("Tu reserva", `:58`) y pinta SIEMPRE una nota de confianza
  (`:93-98`) que estos artboards no dibujan. Tiene CUATRO importadores
  (`public-employee-step`, `public-datetime-step`, `public-client-step`,
  `public-confirm-step`) mas su propio test.
- `components/booking/booking-step-shell.tsx` (247): el contenedor de 1120px
  (`:99`), con `lg:flex-row lg:items-start lg:gap-10` y el aside en un hueco de
  320/340px. Sus CABECERAS no sirven: la movil (`:175-195`) mide 60px y pinta
  el nombre del salon y un contador "N / 6"; la de escritorio es
  `BookingDesktopHeader`, que pinta el salon. Y su progreso movil lleva
  contador y SEIS tramos (`:20`, `:231-246`).
- `components/booking/public-datetime-step.tsx` (~19KB): ya resuelve la tira de
  dias de movil, la rejilla semanal de escritorio y el corte Manana/Tarde
  (`AFTERNOON_HOUR = 14`, `:39`).

**Utilidades reutilizables**
- `lib/utils/avatar.ts`: `employeeFallbackAvatarClassName(index)`,
  `employeeAvatarAlphaStyle(colorHex)`, `employeeSolidColor(colorHex, index)`,
  `employeePaletteIndex(employees, id)`. Cubren el avatar de iniciales de los
  pasos 1/2/3/4 y el punto de color del paso 5.
  ATENCION: `employeePaletteIndex` devuelve `-1` si no encuentra al empleado, y
  `paletteIndex` normaliza los negativos hacia el ULTIMO color de la paleta;
  todo consumidor debe mapear `-1 -> 0` antes de pasarlo.
- `lib/utils/format.ts`: `formatCurrency` (emite U+00A0 antes del simbolo de
  euro), `initials`, `formatPhone`, `capitalizeFirst`.
- `lib/utils/dates.ts`: `formatDuration`, `formatTimeRange`, `formatDateLong`
  ("Miercoles, 28 de agosto" — lo que dibuja `NuevaCitaPaso5.dc.html:55`, salvo
  que la funcion lo devuelve CON TILDE; ver §1.8.3).
- `hooks/use-staff.ts`: `useEmployees`, `useServices`, `useEmployeeServices`, y
  **`useEmployeesWorkingHours(ids)`** (`:65-93`), que ya trae los horarios de
  N empleados con `useQueries` y `combine` memorizado.
- `components/ui/button.tsx`: `size="2xl"` es `h-[50px] w-full` — exactamente el
  CTA del pie movil; `size="xl"` es 44px y es el que usa el aside con `h-[46px]`
  encima. **Ninguna talla da un cuadrado de 38x38**: `action` es
  `h-[38px] px-[18px]` (boton de texto) e `icon` es `size-8`. La receta que el
  repo ya usa para el boton-icono de 38px de las cabeceras de escritorio esta en
  `page-shell.tsx:235-243`:
  `<Button variant="outline" size="icon" className="size-[38px] shrink-0">` con
  el icono a `className="size-[18px]"`.
- `components/booking/public-datetime-step.tsx:39`: `AFTERNOON_HOUR = 14`, el
  corte Manana/Tarde. **NO esta exportado.**

**Enlaces entrantes que no se pueden romper**
- `components/layout/fab-button.tsx:9` → `/appointments/new`.
- `app/(app)/today/page.tsx:110,212` → `/appointments/new`.
- `app/(app)/calendar/page.tsx:293` → `/appointments/new?date&time[&employeeId]`,
  con la limitacion anotada en su propio comentario (`:284-290`): el asistente
  todavia no lee esos parametros.
- `components/appointments/appointment-detail-panel.tsx:132` →
  `/appointments/new?rescheduleId&date&time&employeeId`, misma nota (`:121-123`).
- `lib/nav/app-nav.ts:42-43` enciende "Citas" en `/appointments*`; su fila en
  `app-nav.test.ts:63` prueba la funcion pura, no el montaje.
- **La URL `/appointments/new` NO cambia**: un grupo de rutas entre parentesis
  no aparece en la URL. Ningun enlace se toca.

### 1.5 · Limites REALES de la API (leidos en el backend)

Esto es lo que decide el alcance del bloque. Los tres primeros ya los sufrio la
reserva publica y los dejo anotados en su propio codigo.

1. **`GET /api/v1/appointments/availability` es POR EMPLEADO Y POR DIA.**
   `AppointmentController:108-118` exige `employeeId` y un solo `date`.
   → No hay forma barata de pintar "9 huecos" en las siete celdas de dia del
   artboard de escritorio (`NuevaCitaDesktopPaso3.dc.html:83`): serian siete peticiones solo
   para el contador. `public-datetime-step.tsx:447-456` ya documenta que por
   eso no lo pinta.
2. **Solo devuelve huecos LIBRES.** `AvailabilityResponse(date, employeeId,
   slots)` — `calculateFreeSlots` (`application/AvailabilityService.java:117`). No existe la
   lista de huecos OCUPADOS, asi que los botones tachados de
   `NuevaCitaPaso3.dc.html:92,93,98` y
   `NuevaCitaDesktopPaso3.dc.html:118,119,131` no se pueden pintar.
   `public-datetime-step.tsx:125-128` ya lo documenta igual.
3. **No existe disponibilidad "cualquier empleado".** `employeeId` es
   obligatorio. Hoy `datetime-step.tsx:33-37` manda literalmente `"any"` como
   id cuando el usuario elige "Sin preferencia" → el backend pide a
   staff-service el horario del empleado `"any"`. **Es un fallo real en
   produccion**, no una limitacion de diseno.
4. **`GET /api/v1/clients` NO acepta `search`.** `ClientController:44` es
   `list(Pageable pageable)` y nada mas. `lib/api/clients.ts:6-12` manda
   `search=...` y el backend lo ignora en silencio: el buscador del paso 4
   devuelve hoy los diez primeros clientes escribas lo que escribas.
   **Tambien es un fallo real**, y los dos artboards dibujan la caja.
5. **`totalVisits` y `lastVisitAt` existen en el esquema y NADIE LOS ESCRIBE
   NUNCA.** Es el hecho que mas cambia lo que se puede pintar en el paso 4, y no
   se ve mirando la entidad:
   - `ClientService.java:66` y `:193` ponen `.totalVisits(0)` al crear el
     cliente. **No hay ni un solo `setTotalVisits` ni `setLastVisitAt` en todo
     `client-service`.**
   - `ClientInternalController` —la unica puerta de entrada de otros
     servicios— expone SOLO `GET /{clientId}` (`:26`) y `POST /find-or-create`
     (`:35`). No existe ningun endpoint de "visita registrada".
   - `appointment-service` no llama a client-service al completar una cita:
     `grep -ni "visit"` sobre su codigo devuelve seis aciertos y **los seis son
     la palabra "visitor" en comentarios**.
   - La migracion los declara `DEFAULT 0` / `NULL` y no los rellena.

   O sea que en produccion `totalVisits` vale 0 y `lastVisitAt` es NULL para el
   100% de las filas. Consecuencias directas sobre los artboards:
   los "14 visitas" / "7 visitas" que dibujan `NuevaCitaPaso4.dc.html:83,91,99`
   y el escritorio saldran todos como **"0 visitas"**, y el orden "Clientes
   recientes" degenera al desempate. Ver D9 y D31.

6. **`colorHex` de un empleado NUNCA llega nulo desde el backend.**
   `EmployeeService.java:81` lo crea con
   `.colorHex(request.colorHex() != null ? request.colorHex() : "#3B82F6")`.
   Consecuencia para D14: la rama de paleta de reserva
   (`employeeFallbackAvatarClassName`) es en la practica CODIGO MUERTO para
   empleados venidos de la API, y un salon cuyo formulario de alta no toco el
   selector de color (`employee-form.tsx:104,113` manda
   `form.colorHex || undefined`) tendra a TODA la plantilla en `#3B82F6`, un
   azul que no esta en la paleta de los artboards. El alta de onboarding si
   elige color (`add-employee/page.tsx:45`, `COLOR_SWATCHES[0]`).
   No se arregla aqui —es el mismo comportamiento que ya tienen el calendario y
   la hoja de detalle, y cambiarlo los haria divergir—, pero se escribe para que
   nadie construya la rama de reserva creyendo que se ejecuta, ni se sorprenda
   de ver todos los avatares iguales.

7. **Las citas del asistente SI se crean como `PENDING`**
   (`AppointmentService.java:140`), asi que la pildora del paso 5 ("Pendiente" /
   "Se creara como Pendiente", §1.1 y §1.2) dice la verdad. Comprobado, no
   supuesto.

8. **El asistente no manda `source` y esta bien asi.** El
   `CreateAppointmentRequest` del frontend (`types/appointment.ts:35-44`) no
   tiene ese campo; el del backend si (`:19`), y `parseSource(null)` devuelve
   **`MANUAL`** (`AppointmentService.java:422-424`), que es exactamente el valor
   correcto para un alta hecha a mano en el salon. La hoja de detalle del bloque
   4 lo pinta como "Fuente". No anadir el campo "para arreglarlo".

9. **`list(Pageable)` no impone NINGUN orden**, y el motor decide la semantica
   de los NULL. La columna existe (`ClientJpaEntity:62-63`, `last_visit_at`);
   lo que no existe es quien la llene (§1.5.5).
   **El motor es MySQL 8.0**, no Postgres:
   `client-service/src/main/resources/application-local.yml:3` →
   `jdbc:mysql://localhost:3306/client_db`, y el `CLAUDE.md` del backend lo
   confirma ("MySQL 8.0, single instance, 7 schemas"). Consecuencias, las dos
   contrarias a lo que suele asumirse:
   - En MySQL los NULL ordenan como el valor MAS BAJO, asi que
     `ORDER BY last_visit_at DESC` ya deja a los clientes sin visitas **al
     final** — no hace falta pedir nada. (Con la columna siempre a NULL hoy,
     §1.5.5, esa propiedad todavia no se ejerce: ver D9.)
   - MySQL 8.0 **no soporta la clausula `NULLS FIRST` / `NULLS LAST`**. No es
     una limitacion del string `sort` de Spring: la sintaxis no existe en el
     motor. Escribirla en JPQL o en SQL nativo es un error de sintaxis.

### 1.6 · Tokens

Casi todos los hexes de los diez artboards ya tienen token en
`src/app/globals.css`:

| Hex | Token | Hex | Token |
|---|---|---|---|
| `#FBF7F2` | `bg-background` | `#EFE6DA` | `bg-hairline` |
| `#2A2320` | `text-foreground` | `#D8C9B8` | `border-dashed` |
| `#FFFFFF` | `bg-card` | `#E3D3C6` | `primary-disabled` |
| `#B4522F` | `bg-primary` | `#C4B5A6` | `text-placeholder` |
| `#8F3F24` | `primary-pressed` | `#FAEFD6` | `status-pending-bg` |
| `#E7DCCF` | `border-border` | `#8A5B12` | `status-pending-text` |
| `#F8F2EA` | `bg-sidebar` | `#E4EDE1` | `success-soft` |
| `#7A6A5F` | `muted-foreground` | `#3F6B4F` | `success` |
| `#9A8A7E` | `muted-foreground-2` | `#F5EEE6` | `bg-muted` |
| `#B8A99C` | `text-subtle` | `#F6E7E0` | `bg-accent` |

**Faltan DOS**, y en Tailwind v4 una utilidad cuyo `--color-*` no este mapeado
en `@theme inline` se descarta EN SILENCIO:
- `#DCC9BB` — borde discontinuo de "Crear nuevo cliente"
  (`NuevaCitaPaso4.dc.html:67`, `NuevaCitaDesktopPaso4.dc.html:78`). No es
  `--border-dashed` (`#D8C9B8`).
- `#F0EAE3` — fondo del avatar del empleado inactivo
  (`NuevaCitaPaso1.dc.html:89`, `NuevaCitaDesktopPaso1.dc.html:113`). Coincide en hex con `--color-status-completed-bg`, pero
  usar ese token aqui seria una mentira semantica: no es un estado de cita.

Los avatares de colores de los artboards (`#E8EEE7`/`#5C7A5E`,
`#E4EAEE`/`#4A6274`, `#F5EDDD`/`#A8762F`) son `--chart-2..4` al 12,5%: los
resuelve `employeeFallbackAvatarClassName`, no hacen falta tokens nuevos.

### 1.7 · Linea base (medida en `c791751`, arbol limpio)

```
npx tsc --noEmit        -> 0 errores
npx eslint .            -> 0 errores, 17 avisos
npx vitest run          -> 744 tests en 73 ficheros, todos pasando
npm run build           -> 0 errores
```

Ninguna puerta puede quedar por debajo de esto. **`npx eslint .` se ejecuta en
CADA cierre de ola**: el bloque 4 introdujo 27 errores de lint porque las
puertas de ola solo corrian build + tsc + vitest (`tasks/lessons.md`).

### 1.8 · Trampas del repo que ya han costado un BLOQUE

Se repiten en cada brief de implementador. No son teoricas: todas han fallado
antes en este repo.

1. **`tailwind-merge` borra un `leading-*` escrito ANTES de un `text-[Npx]`
   dentro de `cn()`.** Medido: `twMerge("leading-tight text-[11px] font-bold")`
   devuelve `"text-[11px] font-bold"`. En una cadena literal de `className`
   (sin `cn()`) el orden da igual.
2. **La preflight de Tailwind impone `line-height: 1.5`**
   (`node_modules/tailwindcss/preflight.css:30`). Los artboards no declaran
   `line-height` salvo donde se indica en §1.1/§1.2, o sea `normal` (~1.25):
   **cada `text-[Npx]` necesita su `leading-tight` DETRAS**.
3. **Los artboards estan escritos SIN TILDES y el codigo las produce.**
   `formatDateLong` (`dates.ts:41-43`) devuelve "Miercoles, 28 de agosto" CON
   tilde en la e (comprobado ejecutandolo), y `format(d, "EEE")` con locale `es`
   da "mie" con tilde. Los diez artboards escriben sistematicamente sin tildes
   ("Miercoles", "Manana", "Anadir", "telefono", "Barberia"). Un test que copie
   el texto del artboard **no encuentra nada**. Es la misma clase de trampa que
   la del U+00A0 de abajo: el texto correcto en pantalla es el ACENTUADO; lo que
   hay que corregir es la afirmacion del test.
4. **`formatCurrency` emite U+00A0** antes del simbolo de euro. Un test que
   afirme el resultado con un espacio normal tecleado a mano no encuentra nada
   y se queda verde en falso. Antidoto ya en el repo: los helpers
   `normalize`/`exact` de `appointment-block.test.tsx:43-51`.
5. **`src/test/setup.ts` devuelve SIEMPRE `matches:false` en `matchMedia`.**
   Cada prueba de ESCRITORIO necesita su `mockMatchMedia(true)` local y su
   `afterEach`. Patron en `booking-step-shell.test.tsx:24`,
   `public-datetime-step.test.tsx:19`.
6. **Testing Library busca `data-testid`, NO `data-slot`.**
7. **jsdom no aplica CSS ni calcula layout**: los tests fijan clases y numeros,
   nunca pixeles pintados. Lo que se ve solo lo prueba la comparacion visual.
   Corolario que cuesta una iteracion si se olvida: un elemento con
   `hidden lg:block` **SIGUE ESTANDO EN EL DOM**, asi que `getByText(...)` lo
   encuentra con `mockMatchMedia(true)` Y con `false`. Cualquier afirmacion del
   tipo "en escritorio aparece X y en movil no" exige **montaje condicional en
   JS** (`useMediaQuery`), no clases. Vale para el cromo del shell (D26) y
   tambien para el CUERPO de los pasos: "citas hoy" (T5), la columna de visitas
   (T8) y la pildora de estado (T9) se montan por `isDesktop`, no por `lg:`.
8. **PROHIBIDO tocar `node_modules`. PROHIBIDO ejecutar `npm ci`** — uno previo
   destruyo `node_modules/.bin` devolviendo exit code 0. Si falta algo:
   `npm install`.
9. **`useSearchParams` exige un `<Suspense>` PROPIO, y sin el `npm run build`
   falla para el GRUPO DE RUTAS ENTERO, no para una pagina.** Ya se pago una
   vez en este repo y esta escrito en dos sitios:
   `app-sidebar.tsx:12-18` ("without one, Next treats the missing boundary as a
   build error for the whole route group") y `staff/page.tsx:24-32`, que lleva
   el limite explicito. **Vitest NO lo ve**: solo `npm run build`.
   Importa especialmente aqui porque D1 saca la ruta de `(app)`, con lo que
   `AppSidebar` —que hoy aporta el unico limite de ese grupo— deja de montarse,
   y en `(fullscreen)` no queda ninguno.
10. **Tras mover o crear una ruta, `.next/types` queda RANCIO y `tsc --noEmit`
    falla con errores que no son tuyos.** Lo descubrio T1: despues del `git mv`,
    `.next/types/validator.ts` seguia apuntando a
    `../../src/app/(app)/appointments/new/page.js`. La solucion es regenerarlo,
    que es el flujo que el propio Next documenta
    (`node_modules/next/dist/docs/01-app/03-api-reference/05-config/02-typescript.md`:
    `next typegen && tsc --noEmit`):

    ```
    npx next typegen
    npx tsc --noEmit
    ```

    `.next/` esta en `.gitignore:17`, asi que regenerarlo no toca fuente de
    nadie. **Afecta a T3**, que reescribe `page.tsx` en ese grupo de rutas.

11. **En una ola con varios agentes, `git mv` de un DIRECTORIO puede fallar con
    "Permission denied" intermitente** (lock del sistema de ficheros con otros
    procesos leyendo el arbol). Le paso a T1. Solucion: `git mv` fichero a
    fichero y `rmdir` de los directorios vacios; el resultado en git es el mismo
    rename. No es motivo para abandonar la tarea.

12. **`AGENTS.md` avisa: "This is NOT the Next.js you know".** Leer
   `node_modules/next/dist/docs/` antes de escribir codigo de Next.

---

## 2 · Decisiones

**D1 — El asistente sale de `(app)` a un grupo nuevo `(fullscreen)`.**
`src/app/(fullscreen)/layout.tsx` monta `OnboardingGate` y nada mas: ni
`AppSidebar`, ni `BottomNav`, ni `FabButton`, ni `useSwipeNavigation`, ni el
`pb-20`. Los diez artboards dibujan pantalla completa (§1.1, §1.2). La URL no
cambia (§1.4) y ningun enlace entrante se toca.
Se mueve el directorio `appointments/` entero, que tras el bloque 4 solo
contiene `new/`.
*Por que un grupo y no un `if` en `(app)/layout.tsx`*: ese layout ya arbitra
doce pantallas con `FAB_ROUTES`/`FILL_ROUTES`; una tercera lista de excepciones
para apagarlo ENTERO es peor que no montarlo. Y el precedente del repo es el
grupo: `(onboarding)` y `(auth)` ya existen por lo mismo.

**D2 — Cabecera propia, no `PageShell`.** Movil 56px, escritorio 68px, valores
en §1.1 y §1.2. `PageShell` sirve a doce pantallas con barra de titulo, no a un
asistente a pantalla completa: no tiene ni marca, ni "Cancelar", ni el fondo
`--sidebar` de la cabecera de escritorio.

**D3 — El progreso son DOS piezas, montadas por ancho.** Movil: cinco barras
planas, sin contador (§1.1). Escritorio: el stepper de cinco nodos (§1.2).
Es exactamente lo que dibujan los artboards, y difiere del progreso de la
reserva publica (seis tramos + contador), asi que no se comparte.

**D4 — `BookingStepper` y `BookingSummaryAside` se PROMUEVEN a primitivas
compartidas.** Pasan a `src/components/wizard/wizard-stepper.tsx` y
`src/components/wizard/wizard-summary-aside.tsx`, renombrados a `WizardStepper`
y `WizardSummaryAside`, con tres props nuevas cuyos valores POR DEFECTO
reproducen exactamente lo que pintan hoy:
- `WizardStepper`: `labels?: readonly string[]` (defecto = las cinco etiquetas
  actuales de la reserva publica).
- `WizardSummaryAside`: `heading?: string` (defecto `"Tu reserva"`) y
  `note?: ReactNode` (defecto = la nota de confianza actual; `null` la quita).
Cero cambio visual en las cinco pantallas publicas; `tsc` verifica cada
importacion. *Por que mover y no copiar*: los dos artboards de escritorio
dibujan el MISMO componente con distinto texto. Copiarlo lo condena a divergir
— es literalmente el bug que `lib/utils/avatar.ts` existe para haber corregido
(§1.4, su propio comentario lo dice).
*Ojo a las DOS carpetas*: las primitivas compartidas viven en
`src/components/wizard/` (las usan la reserva publica Y el asistente) y los
cinco pasos en `src/components/appointments/wizard/`. Las rutas de importacion
se parecen mucho (`@/components/wizard/wizard-summary-aside` vs
`@/components/appointments/wizard/wizard-summary`) y no se consolidan: la
primera es de las dos pantallas, la segunda solo del asistente.
*Por que renombrar*: dejarlo bajo `components/booking/` obliga al asistente
interno a importar de "booking", y ese nombre equivocado se propaga a los cinco
pasos nuevos.

**D5 — El asistente NO reutiliza `BookingStepShell`.** Su cromo (las dos
cabeceras y el progreso movil) es distinto en las dos anchuras, y lo unico
comun es el contenedor de 1120px, que son cuatro clases de Tailwind.
Generalizarlo obligaria a meter `salon: SalonPublic` opcional y un numero de
tramos variable en un chasis que hoy sirve, cerrado y probado, a cinco
pantallas publicas. Se construye `NewAppointmentShell` propio y se copia el
contenedor, citando el original.

**D6 — "Sin preferencia" se resuelve en el FRONTEND, abanicando con
`useQueries`.** `GET /availability` exige `employeeId` (§1.5.3) y hoy el codigo
manda `"any"`: es un fallo real que este bloque cierra. El hook nuevo pide la
disponibilidad de **un solo dia** a cada empleado activo que ofrezca el
servicio, une los huecos y anota en cada uno de quien es, porque
`CreateAppointmentRequest.employeeId` es obligatorio y al confirmar hay que
asignarselo a alguien. Son N peticiones de UN dia, no N x 7.
*Por que no un endpoint nuevo de backend*: seria el arreglo correcto a largo
plazo, pero sirve tambien a la reserva publica y merece su propio bloque; ver
D7. `useEmployeesWorkingHours` ya prueba que el patron `useQueries` funciona en
este repo (§1.4).

**D7 — Se aceptan las DOS limitaciones que la reserva publica ya documento.**
(a) La tercera linea de la celda de dia de escritorio no pinta "N huecos"
(§1.5.1); (b) los huecos OCUPADOS no se pintan tachados (§1.5.2). En los dos
casos con la MISMA redaccion y el mismo motivo que
`public-datetime-step.tsx:125-128` y `:447-456`, para que las dos pantallas
digan lo mismo. La celda de dia SI pinta "Cerrado" cuando el empleado no
trabaja ese dia, que si es derivable de `useEmployeesWorkingHours`; "Sin
huecos" cae con el contador, ver D30.
Cerrarlo del todo es un endpoint de rango en `appointment-service` que sirve a
las DOS pantallas: **se anota como deuda en `tasks/todo.md` con la propuesta
concreta**, y sale de este bloque.

**D8 — `search` en `GET /api/v1/clients`: SI, y es el unico trabajo de
backend.** Ojo al alcance: ese endpoint tiene DOS consumidores, el paso 4 y la
pantalla `/clients` (`clients/page.tsx:27`), asi que el orden que fija D9 les
cambia a las dos. La lista de clientes recibe hoy el orden por defecto de
`findAll(Pageable)`, que no es ninguno en concreto; pasar a "ultima visita
primero" es una mejora y ningun artboard de `Clientes*.dc.html` impone otro
orden. Se declara aqui para que T11 lo verifique en vez de descubrirlo.
 Los dos artboards dibujan la caja y el de escritorio promete
"nombre, telefono o email" (§1.1, §1.2). Hoy el frontend manda `search=` y el
backend lo ignora (§1.5.4): la caja miente. Es un parametro de controlador, un
metodo de repositorio y una firma de caso de uso.

**D9 — "Clientes recientes" lo ordena el BACKEND, y hoy el orden efectivo es
"los ultimos dados de alta".** Porque `lastVisitAt` es NULL siempre (§1.5.5):
el `ORDER BY c.lastVisitAt DESC, c.createdAt DESC` degenera al desempate. Se
escribe igualmente con los dos criterios —para que el dia que D31 se cierre la
pantalla empiece a decir la verdad sin tocar nada— y se acepta a sabiendas que
hoy significa "recien anadidos". No es lo mismo que la caja de busqueda de D8:
alli el control MIENTE (dice que busca y no busca); aqui el orden es una
aproximacion razonable y el unico texto en pantalla es una etiqueta.
En MySQL 8.0 los NULL son el valor mas bajo, asi que
`ORDER BY last_visit_at DESC` ya deja al final a quien nunca ha venido
(§1.5.9) — la premisa contraria, que es la de Postgres, no aplica aqui.
El orden se fija igualmente en la CONSULTA del backend y no con
`sort=lastVisitAt,desc` desde el frontend, por dos razones: el desempate
(`created_at DESC`) tiene que existir para que la lista sea estable entre
peticiones, y un orden que la pantalla del paso 4 da por hecho no puede
depender de que cada consumidor recuerde mandar el parametro.
**Prohibido escribir `NULLS LAST`**: MySQL 8.0 no soporta esa clausula y es un
error de sintaxis, no una degradacion silenciosa.
En el frontend, `useClients` deja de exigir `search.length >= 2` y gana un modo
"lista inicial" para que el paso 4 pueda pintar la lista sin buscar.

**D10 — `formatDuration` NO se cambia. Se anade una segunda forma,
`formatDurationTight`, y SOLO la usa el asistente.**

El canvas quiere las dos formas y las dos estan en pantallas reales:
- **sin espacio** ("45min") — los diez artboards del asistente y los siete de
  la reserva publica;
- **con espacio** ("45 min") — `DetalleEmpleadoDesktop.dc.html:245,255` y
  `FormularioEmpleadoDesktop`.

Y la segunda **ya esta construida y cerrada**: `/staff/[id]` monta
`ServiceAssignment` (`staff/[id]/page.tsx:240`), que pinta
`formatDuration(...)` en `service-assignment.tsx:73` — o sea que hoy esa
pantalla coincide con su artboard al caracter. Cambiar `formatDuration` en
global la rompe.
Asi que `formatDuration` se queda como esta y `formatDurationTight` es nueva.
Consumidores que cambian: **solo los del asistente** (`service-step`,
`confirmation-step` y el modulo de resumen).

*Deuda que esto NO cierra, y hay que anotarla*: la reserva publica dibuja
"45min" en sus artboards y pinta "45 min" con `formatDuration`. Es un desajuste
REAL y preexistente. Cambiarla aqui seria meter mano en un carril cerrado que
este bloque no revisa; se anota en `todo.md` con el arreglo exacto (cambiar sus
siete consumidores a `formatDurationTight`).

**Son CATORCE consumidores de `formatDuration` en produccion**, no diez:
`appointment-card`, `appointment-detail-facts`, `appointment-detail-panel`,
`confirmation-step`, `service-step`, `public-booking-error`,
`public-client-step`, `public-confirm-step`, `public-datetime-step`,
`public-employee-step`, `public-service-step`, `public-success-step`,
`service-card`, `service-assignment`. Quien pare al encontrar diez deja cuatro
sin mirar.

**Ningun test existente deberia ponerse rojo con esta decision.** Si alguno lo
hace —por ejemplo `service-card.test.tsx:32`, que afirma `/30 min/`—, es que se
ha tocado algo que no tocaba: se investiga, no se "corrige" el test.

**D11 — El paso 2 agrupa por categoria y NO oculta lo que el empleado no
ofrece.** Lo pinta atenuado con el subtitulo sustituido (§1.1, §1.2). Hoy
`service-step.tsx:23-27` los filtra fuera. Los servicios sin categoria van a
un grupo final sin cabecera (ningun artboard dibuja una cabecera "Otros").
**"Sin categoria" es `null` O CADENA VACIA, y en produccion es casi siempre la
segunda:** `service-form.tsx:88-97` manda `category: form.category` —`""` cuando
el campo se deja vacio— y lo hace A PROPOSITO (su comentario `:91-92` explica
que el PUT del backend fusiona por presencia, asi que omitir la clave significa
"no cambies" y vaciar el campo se ignoraria); `ServiceOfferingService.java:47`
lo guarda tal cual sin normalizar. Agrupar por `category === null` o por
`category ?? X` crea un grupo de mas con la cabecera `.secl` VACIA (y su
`margin-top:4px`). Hay que normalizar con `category?.trim() || null`.
Con **"Sin preferencia"** no hay a quien atenuar: ningun artboard dibuja ese
caso. Se pintan todos los servicios activos sin atenuar, y el subtitulo de
escritorio —que es "Solo los que ofrece {nombre}"— **se omite**, porque su
frase no tiene sujeto. No se inventa una alternativa.

**D12 — El subtitulo de escritorio del paso 1 se sustituye.** El artboard dice
"Solo aparecen los que trabajan hoy." y en la misma rejilla pinta a alguien que
hoy no trabaja (§1.3.C). Se pinta **"Quien atendera al cliente."**. Afirmar en
pantalla algo que la propia pantalla desmiente es peor que apartarse de una
linea de copy; el resto del artboard —incluida la fila atenuada— se respeta al
pie de la letra.

**D13 — "Hoy no trabaja" se deriva de `useEmployeesWorkingHours`.** Ya existe,
ya esta probado y ya comparte cache con la ficha de empleado (§1.4). No se
anade ningun hook ni endpoint para esto.

**D14 — El color del avatar sale SIEMPRE de `lib/utils/avatar.ts`,** nunca del
hex del artboard (§1.3.A). `employeeAvatarAlphaStyle(colorHex)` cuando el
empleado tiene color propio; `employeeFallbackAvatarClassName(index)` cuando no,
con el indice de `employeePaletteIndex(employees, id)` y `-1` mapeado a `0`.
El punto solido de 8px del paso 5 usa `employeeSolidColor`.
La rama de paleta de reserva se cablea aunque en la practica casi nunca corra
(§1.5.6: el backend nunca devuelve `colorHex` nulo), porque es el mismo contrato
que ya usan el calendario y la hoja de detalle y separarse de el los haria
divergir — que es justo lo que `avatar.ts` existe para evitar.

**D15 — "N citas hoy" (escritorio, paso 1) sale de `useTodayAppointments(hoy)`
(`use-appointments.ts:81`) agrupando por `employeeId`.** Una sola peticion, con la clave que `/today` y
`/calendar` ya calientan. No cuenta las canceladas: una cita `CANCELLED` no es
carga de trabajo, y contarla haria que el numero no cuadre con lo que ese
empleado ve en su columna del calendario.

**D16 — Prefill desde la query, parcial y explicito.** `/calendar` y el panel
de detalle ya empujan `?date&time[&employeeId]` con la limitacion anotada en su
propio codigo (§1.4). Este bloque la cierra asi:
- La pagina **no resuelve nada**: siembra `preferredEmployeeId`,
  `preferredDate` y `preferredSlot` en el store y arranca en el paso 1.
  *Por que asi*: `selectedEmployee` guarda un objeto `Employee` COMPLETO
  (`wizard-store.ts:10`), y su unica fuente es `useEmployees`, que es
  ASINCRONA — en el efecto de montaje de la pagina todavia no existe, y "id
  valido" no es comprobable sin ella. Un efecto que reaccionase a la query
  ademas chocaria con D17.
- **El paso 1 (T5) lo resuelve**, porque ya tiene esa query montada: cuando
  llega la lista, si `preferredEmployeeId` casa con un empleado activo, lo
  selecciona y avanza al paso 2; si no casa, limpia la preferencia y el usuario
  elige a mano. Ni la pagina ni el shell tocan esto.
- `date` y `time` se aplican al llegar al paso 3 si ese hueco sigue libre; si
  no, el paso 3 abre en ese dia con el hueco sin elegir;
- `rescheduleId` **se ignora**: ningun artboard dibuja una variante de
  reprogramacion, y no se inventa. Queda anotado como deuda. El comportamiento
  actual (crear una cita nueva) no cambia.
Los comentarios de `calendar/page.tsx:284-290` y
`appointment-detail-panel.tsx:121-123` se actualizan para decir lo que ES
verdad tras este bloque, no lo que era.

**D17 — El `reset()` al montar se conserva como efecto, sembrado.** Con una
salvedad medida: lo verificado es que `useEffect(() => { reset() }, [reset])`
pasa lint hoy (`page.tsx:20-22`). Con la semilla derivada de la query dentro,
`react-hooks/exhaustive-deps` avisara por las dependencias que faltan. Son
AVISOS, no errores, pero §1.7 fija el liston en 17 avisos y ninguna puerta
puede quedar por debajo: los parametros de la query van en las dependencias, o
el efecto lee un valor estable calculado fuera. Lo que NO se hace es silenciar
la regla. Sigue
siendo `useEffect` con dependencias estables, igual que hoy
(`page.tsx:20-22`) — se ha verificado que esa forma pasa lint hoy (`eslint .`
= 0 errores en la linea base). La accion del store pasa a aceptar una semilla:
`reset(seed?)`. No se convierte en inicializador perezoso ni en estado
derivado; el bloque 4 ya perdio una ola por reescribir un efecto que
funcionaba.

**D18 — X y "Cancelar" hacen lo mismo: `reset()` + `router.back()`.** El
chevron izquierdo de la cabecera movil retrocede un paso y **solo se pinta de
paso 2 en adelante**; en el paso 1 el hueco de 44x44 se reserva vacio, tal cual
lo dibuja `Paso1:26`.

**D19 — El pie fijo de movil SOLO existe en los pasos 3 y 5.** Es lo que
dibujan los artboards: los pasos 1, 2 y 4 avanzan al tocar la fila, sin CTA.
No se anade un "Continuar" donde no lo hay.

**D20 — El aside de escritorio se monta en los CINCO pasos, y su contenido
VARIA por paso.** CTA deshabilitado en 1, 2 y 4 (§1.2). Las tres variaciones
estan medidas en §1.2 y §1.3.G/H: el valor "Sin elegir" con estilo de
placeholder en el paso 1; la segunda linea de "Servicio" en los pasos 3 y 4 pero
NO en el 5; y "Fecha y hora" con el dia SIEMPRE delante ("Mie 28, ...") y solo
la hora cambiando: inicio en el paso 3, rango en el 4 y el 5. **No es "hora
suelta vs rango"** — §1.2 Variacion 3 lo da medido en los tres ficheros.
El modulo unico (`wizard-summary.ts`) sigue siendo la fuente —cinco
derivaciones paralelas del mismo resumen es como se rompe la coherencia entre
pasos, y es el patron que funciono en el bloque 4 con
`appointment-detail-facts.ts`—, pero su firma **recibe el paso**:
`getWizardSummaryRows(state, step)`. Un modulo que devolviera lo mismo para los
cinco se apartaria del artboard en tres sitios.

**Y arrastra una regla de reparto**: lo crea T4 en la ola 1 y lo consumen las
CINCO tareas de la ola 3, ninguna de las cuales lo posee. T4 tiene que cubrir
§1.2 entera —las tres variaciones incluidas— para que no falte nada. Si aun asi
un paso descubre que necesita una fila o una etiqueta que T4 no previo, **NO
edita el modulo compartido**: se la deriva en su propio fichero y lo senala en
su informe para que T11 lo consolide. Cinco agentes editando a la vez un modulo
que ninguno posee es como la ola 3 se pisa a si misma.

**D21 — Dos tokens nuevos en `globals.css`,** con su `--color-*` mapeado en
`@theme inline` (§1.6): `--border-dashed-strong: #dcc9bb` y
`--avatar-muted: #f0eae3`. Sin el mapeo, Tailwind v4 descarta la utilidad en
silencio.

**D22 — El `employeeId` de cada hueco vive en un tipo LOCAL del asistente,** no
en `types/appointment.ts`. Ese fichero refleja el JSON que manda el backend, y
el backend no manda `employeeId` por hueco: anadirlo ahi seria mentir sobre el
contrato. El tipo enriquecido (`WizardSlot`) vive junto al hook de D6.

**D23 — Los dos tests existentes del asistente se REESCRIBEN, no se
parchean.** `wizard-progress.test.tsx` prueba circulos numerados que dejan de
existir; `datetime-step.test.tsx` prueba una tira de 30 dias y una rejilla de 4
columnas que tambien.

**D24 — "Continuar sin cliente" y el formulario de alta en linea.** Ningun
artboard dibuja el boton "Continuar sin cliente" (`client-step.tsx:193-201`):
**se borra**. El formulario de alta en linea tampoco tiene artboard, pero la
tarjeta "Crear nuevo cliente" que lo abre SI se dibuja en los dos anchos: el
formulario **se conserva tal cual**, sin rediseno, porque quitarlo dejaria un
destino dibujado sin destino. Se anota como hueco de canvas.

**D25 — Verificacion visual.** Se escribe una spec de Playwright que compara
las diez vistas con sus artboards a 390 y 1440. Su EJECUCION necesita
credenciales (`RIVOO_E2E_EMAIL` / `RIVOO_E2E_PASSWORD`, que viven en variables
de entorno y NO en el repo) y la pila levantada: queda como paso manual
documentado, igual que en el bloque 4.
Las specs visuales viven en `visual/` en la RAIZ del repo (`visual/
appointment-detail-vs-artboards.spec.ts` y otras cinco), no bajo `e2e/`.

**D26 — Un solo punto de corte para todo el cromo del asistente: `lg:`
(1024px).** Cabecera, progreso, aside y pie cambian todos a la vez.
*Por que*: no existe ningun artboard del asistente a 768 — son 390 y 1440 —, y
el bloque 2 ya decidio que entre 768 y 1023 la app interna conserva la forma
movil. La reserva publica mezcla `md:` (stepper) y `lg:` (aside), asi que entre
768 y 1023 pinta un hibrido que ningun artboard dibuja; ese hibrido no se copia.
Consecuencia sobre D4: `WizardStepper` lleva hoy su visibilidad escrita dentro
(`hidden md:flex`, `booking-stepper.tsx:26`) y el asistente la necesita en
`lg:`. Gana una prop `visibleFrom?: "md" | "lg"` (defecto `"md"`, o sea lo de
hoy) resuelta con DOS cadenas de clases COMPLETAS, nunca construidas en
ejecucion — Tailwind escanea el fuente y no veria `hidden ${bp}:flex`.
**Y el punto de corte lo decide UN SOLO mecanismo: `useMediaQuery`, en JS.**
Montar cabecera, progreso y stepper con clases CSS (`lg:hidden`) y aside y pie
con `useMediaQuery` los desincroniza: `use-media-query.ts` devuelve `false` en
SSR y en el primer pintado, asi que en un 1440 real, antes de hidratar, el CSS
ya pinta la cabecera de 68px y el stepper mientras el JS todavia pinta el pie
fijo movil y ningun aside. Ningun artboard dibuja eso. Con `isDesktop` como
unica fuente, antes de hidratar la pantalla es enteramente MOVIL —que si es un
artboard— y despues cambia entera de golpe.
`WizardStepper` conserva igualmente `visibleFrom` porque la reserva publica lo
sigue montando por CSS; el asistente le pasa `visibleFrom="lg"` y ademas lo
monta condicionalmente, que es redundante y deliberado: si alguien quita una de
las dos mitades, la otra sigue sosteniendo el corte.
Anadir `className` en su lugar NO sirve: `tailwind-merge` no considera
conflictivos `md:flex` y `lg:flex` (variantes distintas), asi que los dos
sobrevivirian y el stepper reaparecia a 768.

**D27 — `AFTERNOON_HOUR` se promueve.** Hoy es una constante privada de
`public-datetime-step.tsx:39`. El paso 3 del asistente necesita el MISMO corte,
y ninguna de las dos tareas que lo consumen (T7) es propietaria de ese fichero:
copiarlo lo condena a divergir. **T2** —que ya toca la carpeta `booking/`— la
mueve a `lib/utils/dates.ts` y actualiza el consumidor publico.

**D28 — Con "Sin preferencia", el paso 3 filtra por quien OFRECE el servicio, y
ese filtro tambien se abanica.** No hay endpoint masivo:
`staffApi.getEmployeeServices` es por empleado (§1.4). Hace falta un
`useEmployeesServices(ids)` gemelo de `useEmployeesWorkingHours`, y luego la
disponibilidad solo del subconjunto que si lo ofrece.
*Por que no saltarse el filtro y preguntar a todos*: `AppointmentService:86`
solo consulta el servicio para sacar duracion y precio — **no comprueba que el
empleado lo ofrezca**. Un `POST` mal dirigido no falla: crea la cita en
silencio con alguien que no hace ese servicio. El filtro tiene que estar en el
frontend porque el backend no lo pone.
Son **3N** peticiones en el peor caso, no 2N: `useEmployeesServices` (N),
`useEmployeesWorkingHours` (N, que T7 necesita para pintar "Cerrado"), y la
disponibilidad del subconjunto (<=N). Todas cacheadas por React Query y todas
de UN dia. En un salon real N esta entre 3 y 8.

**"Cerrado" con "Sin preferencia"** = ningun empleado del subconjunto trabaja
ese dia. D7 lo define en singular ("el empleado no trabaja ese dia") porque
nace del caso con empleado elegido; aqui se generaliza asi y no de otra forma:
si al menos uno trabaja, el dia esta abierto.

**Caso sin cubrir por ningun artboard, y hay que cubrirlo igual:** con "Sin
preferencia" el paso 2 pinta todos los servicios activos sin atenuar (D11), asi
que el usuario puede elegir uno que NINGUN empleado activo ofrezca. Entonces el
subconjunto es vacio, no se lanza ninguna peticion de disponibilidad y la
pantalla se quedaria en blanco sin causa visible. El paso 3 pinta en ese caso
un estado vacio explicito ("Ningun profesional ofrece este servicio") con vuelta
al paso 2. No es invencion gratuita: la alternativa es una pantalla muerta.

**D29 — La tira de dias es de SEIS/SIETE CELDAS, no de seis/siete DIAS de
horizonte.** Los artboards dibujan una sola semana, pero eso es el ancho de la
tira, no el limite de lo reservable. Hoy `datetime-step.tsx:13` permite reservar
a **30 dias vista** (`DAYS_AHEAD = 30`, con navegador de mes); recortarlo a una
semana seria una regresion funcional del flujo central del producto, metida en
silencio y que ninguna de las cuatro puertas veria.

La reserva publica se topo con artboards que dibujan **exactamente lo mismo** y
lo resolvio conservando el horizonte, no recortandolo:
- `public-datetime-step.tsx:28` → `MOBILE_STRIP_DAYS = 30` (la tira movil
  hace scroll horizontal);
- `:33-34` → `DESKTOP_WEEK_SIZE = 7` y `DESKTOP_WEEK_PAGES = 4`, con el
  comentario "4 x 7 = 28 dias, horizonte similar al de la tira movil";
- `:404-416` → flechas de semana anterior/siguiente, **que ningun artboard
  dibuja** y que se anadieron porque sin ellas la segunda semana es
  inalcanzable.

Este bloque hace lo mismo, y por el mismo motivo:
- **Movil**: seis celdas VISIBLES con los valores de §1.1, sobre una tira de 30
  dias con scroll horizontal. Las seis del artboard son consecutivas desde hoy
  (el salto del domingo es un ajuste de dibujo, no una regla: "salta los
  domingos" daria una tira de longitud variable segun el dia en que se abra).
- **Escritorio**: rejilla de siete dias con los valores de §1.2, mas navegacion
  por semanas (4 paginas). El propio artboard pinta una etiqueta de mes
  ("Agosto 2026", `NuevaCitaDesktopPaso3.dc.html:73`), que solo tiene sentido si
  se puede pasar de una semana.
- Las flechas de semana **no las dibuja ningun artboard**; se pintan igualmente,
  discretas, con la misma justificacion escrita que `booking-step-shell.tsx:107-116`
  ya usa para su boton de volver: perder el acceso a la mitad del horizonte es
  peor que anadir un control que el artboard no previo. Se anota en las deudas.

**D30 — "Sin huecos" tampoco se pinta, por el mismo motivo que el contador.**
La tercera linea de la celda de dia de escritorio tiene tres textos en el canvas
(§1.2): "9 huecos", "Cerrado" y "Sin huecos" (`NuevaCitaDesktopPaso3.dc.html:78`,
dia ABIERTO pero lleno). D7 descarta el contador; "Sin huecos" cae con el mismo
argumento —distinguir "abierto y lleno" de "abierto con huecos" exige saber
cuantos hay, y eso es la peticion por dia que §1.5.1 descarta—. La celda pinta
"Cerrado" cuando el empleado no trabaja y deja la tercera linea VACIA en el
resto. Va a la misma deuda que D7.

**D31 — "N visitas" se pinta con lo que hay, que hoy es 0, y NO se inventa.**
Los dos artboards lo dibujan (§1.1, §1.2) y el campo existe, asi que la fila se
construye leyendo `client.totalVisits`. Como nadie lo escribe (§1.5.5), toda
fila real dira "0 visitas".
**Prohibido derivarlo en el frontend** contando citas del cliente: serian N
peticiones mas por pantalla, daria un numero distinto del que la ficha de
cliente ensena, y taparia el fallo de fondo justo donde mas se nota.
El arreglo real es de backend y es una funcionalidad propia, no parte del
asistente: un `POST /api/internal/clients/{id}/visit` en `ClientInternalController`
que incremente `totalVisits` y ponga `lastVisitAt`, llamado desde
`AppointmentService` cuando una cita pasa a `COMPLETED`, con degradacion
elegante (que falle no puede impedir cerrar la cita). Se anota en las deudas de
T11 con esa forma exacta.

**D32 — Las pildoras de contexto son UN componente compartido, propiedad de T3
— pero NO son identicas entre pasos.** Los artboards las dibujan en tres pasos y
crecen: una en el paso 2 (profesional), dos en el 3 (+ servicio) y tres en el 4
(+ dia y hora). **Son de MOVIL**: ningun artboard de escritorio las dibuja —
alli ese contexto vive en el subtitulo y en el aside.

Lo que comparten es el avatar de 22px (9px/700) y la forma de pastilla
(`border-radius:999px`, borde `#E7DCCF`, fondo blanco, 12px). Lo que **NO**
comparten, medido en los tres ficheros:

| | Paso 2 | Paso 3 | Paso 4 |
|---|---|---|---|
| Alto | **32px** (`Paso2:50`) | **32px** (`Paso3:50,54`) | **30px** (`.chip`, `Paso4:20`) |
| Pildora de profesional | avatar + nombre | avatar + nombre | avatar + nombre |
| Pildora de servicio | — | **icono de tijeras de 13px** + nombre + separador + duracion (`Paso3:54-56`) | **texto pelado**, sin icono ni duracion (`Paso4:56`) |
| Pildora de fecha | — | — | `28 · 11:00` — numero de dia SUELTO, sin nombre de dia ni mes (`Paso4:57`) |

`WizardContextPills` recibe el paso y pinta lo de ese paso: un solo fichero con
las tres variantes, no tres copias. *Por que compartirlo aun asi*: el avatar y
el texto derivado son los mismos, y §1.3.A documenta que el canvas YA dibuja el
avatar de la misma empleada de dos colores distintos en dos de estas pildonas —
tres tareas paralelas construyendolo por separado garantizan esa divergencia.
El avatar sale de `lib/utils/avatar.ts` (D14), nunca del hex del artboard.

**Los textos los deriva `wizard-summary.ts` (T4), no T3.** Incluido el `28 ·
11:00` del paso 4, que es una **CUARTA forma de fecha** —numero de dia suelto—
distinta de las tres de §1.1/§1.2. Si T3 lo formatea por su cuenta, el asistente
acaba con cuatro formatos nacidos en dos sitios, que es justo lo que D20 evita.


**D33 — La fila del empleado que hoy no trabaja SI es seleccionable.** El
artboard la atenua (0.55 en movil, 0.5 en escritorio), le cambia el subtitulo y
le quita el chevron — y la lectura natural de eso es "deshabilitada". **Seria un
error grave.** El empleado se elige en el paso 1 y la fecha en el paso 3, y el
asistente reserva a 30 dias vista (D29): bloquear la fila deja a esa persona
**sin poder recibir NINGUNA cita, ningun dia**, por librar hoy. Es la misma
clase de regresion silenciosa que D29 rechaza, y ninguna puerta la veria.
Ademas ningun artboard dice que la fila este deshabilitada, y la pantalla
hermana esta de acuerdo: `public-employee-step.tsx:79` deshabilita SOLO por
"no ofrece este servicio", nunca por horario.
Asi que: se pinta la atenuacion y el subtitulo tal cual se dibujan —son
INFORMACION util, no un estado deshabilitado— y **se conserva el chevron**,
porque la fila es accionable y quitarselo mentiria sobre lo que hace. Es la
unica desviacion del artboard en esta fila, y la alternativa es un empleado
inreservable.
Al elegirlo, el paso 3 abre en el primer dia que si trabaje.

---

## 3 · Ficheros, con propietario

Ningun fichero tiene dos propietarios en la misma ola. Es lo que permite que
las olas 1 y 3 corran en paralelo sobre el mismo arbol.

### Frontend (`E:\IdeaProjects\rivoo-frontend`)

| Fichero | Accion | Tarea |
|---|---|---|
| `src/app/globals.css` | Modificar (2 tokens, D21) | T0 |
| `src/lib/utils/dates.ts` | Modificar (`formatDuration`, D10) | T0 |
| `src/lib/utils/dates.test.ts` | Modificar (existe) | T0 |
| `src/app/(fullscreen)/layout.tsx` | Crear | T1 |
| `src/app/(fullscreen)/layout.test.tsx` | Crear | T1 |
| `src/app/(fullscreen)/appointments/new/page.tsx` | **Mover** y dejar compilando (NO reescribir: eso es T3) | T1 |
| `src/app/(app)/appointments/**` | Borrar | T1 |
| `src/components/wizard/wizard-stepper.tsx` (+ test nuevo) | Mover desde `booking/` | T2 |
| `src/components/wizard/wizard-summary-aside.tsx` (+ su test) | Mover desde `booking/` | T2 |
| `src/components/booking/booking-step-shell.tsx`, `public-employee-step.tsx`, `public-datetime-step.tsx`, `public-client-step.tsx`, `public-confirm-step.tsx` | Modificar (importaciones; `public-datetime-step` ademas por D27) | T2 |
| `src/lib/utils/dates.ts` | Modificar (`AFTERNOON_HOUR`, D27) | T2 |
| `src/lib/stores/wizard-store.ts` | Modificar (semilla, D17) | T4 |
| `src/lib/stores/wizard-store.test.ts` | **Modificar** (ya existe, 5050 B) | T4 |
| `src/components/appointments/wizard/datetime-step.tsx` | Modificar (SOLO la llamada de `:46`, D22) | T4 |
| `src/hooks/use-clients.ts` | Modificar (D9) | T4 |
| `src/hooks/use-clients.test.tsx` | Crear (hoy no existe) | T4 |
| `src/hooks/use-staff.ts` | Modificar (`useEmployeesServices`, D28) | T4 |
| `src/hooks/use-staff.test.tsx` | Modificar (ya existe) | T4 |
| `src/lib/api/clients.ts` | Modificar (`search`, D8) | T4 |
| `src/hooks/use-wizard-availability.ts` (+ test) | Crear (D6, D22) | T4 |
| `src/components/appointments/wizard/wizard-summary.ts` (+ test) | Crear (D20) | T4 |
| `src/components/appointments/wizard/new-appointment-shell.tsx` (+ test) | Crear (D2, D5) | T3 |
| `src/components/appointments/wizard/wizard-progress.tsx` (+ test) | Reescribir (D3, D23) | T3 |
| `src/components/appointments/wizard/use-wizard-navigation.ts` | Crear (cierre y vuelta compartidos) | T3 |
| `src/components/appointments/wizard/wizard-context-pills.tsx` (+ test) | Crear (D32) | T3 |
| `src/app/(fullscreen)/appointments/new/page.tsx` | **Reescribir** como dispatcher (D16, A5) | T3 |
| `src/app/(fullscreen)/appointments/new/page.test.tsx` | Crear | T3 |
| `src/app/(app)/layout.tsx` | Modificar (solo el comentario `:50-58`, que nombra a esta pantalla y deja de ser cierto tras D1) | T3 |
| `src/components/appointments/wizard/employee-step.tsx` (+ test) | Reescribir | T5 |
| `src/components/appointments/wizard/service-step.tsx` (+ test) | Reescribir | T6 |
| `src/components/appointments/wizard/datetime-step.tsx` (+ test) | Reescribir | T7 |
| `src/components/appointments/wizard/client-step.tsx` (+ test) | Reescribir | T8 |
| `src/components/appointments/wizard/confirmation-step.tsx` (+ test) | Reescribir | T9 |
| `src/app/(app)/calendar/page.tsx` | Modificar (solo el comentario, D16) | T9 |
| `src/components/appointments/appointment-detail-panel.tsx` | Modificar (solo el comentario, D16) | T9 |
| `src/hooks/use-availability.ts` | **Borrar** (queda sin consumidores) | T7 |
| `visual/new-appointment-vs-artboards.spec.ts` | Crear (D25) | T10 |

**`src/app/(fullscreen)/appointments/new/page.tsx` lo tocan DOS tareas**, T1
(que lo MUEVE y lo deja compilando) y T3 (que lo reescribe como dispatcher),
en OLAS DISTINTAS (1 y 2). Ninguna tarea de la ola 3 lo toca — ese es justamente
el motivo de que cada paso monte su propio shell (T3, A5).

**Los ficheros con DOS tareas estan en la tabla DOS VECES, una por tarea.** Son
tres: `dates.ts` (T0 en la ola 0, T2 en la ola 1), `page.tsx` (T1 en la ola 1,
T3 en la ola 2) y `datetime-step.tsx` (T4 en la ola 1 con una sola linea, T7 en
la ola 3 entero). Los tres en olas distintas, asi que no se pisan. Las dos tienen que nombrarlo en su `git add` y en su `git commit -o`: un
fichero tocado y no nombrado no se commitea nunca y las puertas siguen verdes
sobre un arbol sucio (§4).

### Backend (`E:\IdeaProjects\rivoo`)

| Fichero | Accion | Tarea |
|---|---|---|
| `client-service/.../in/web/ClientController.java` | Modificar (`search`) | B1 |
| `client-service/.../port/in/GetClientUseCase.java` | Modificar (firma) | B1 |
| `client-service/.../application/ClientService.java` (implementa `GetClientUseCase`, `:40`) | Modificar | B1 |
| `client-service/.../port/out/ClientPersistencePort.java` | Modificar | B1 |
| `client-service/.../out/persistence/**` (adapter + repository) | Modificar (consulta + orden) | B1 |
| `client-service/src/test/resources/application-test.yml` | **Crear** (el modulo no tiene `src/test/resources`) | B1 |
| `client-service/src/test/java/.../ClientRepositoryIntegrationTest.java` | **Crear** (Testcontainers MySQL, `@Tag("integration")`) | B1 |
| `client-service/src/test/java/.../ClientServiceTest.java` | Modificar si la firma del puerto cambia | B1 |
| `tasks/todo.md` | Modificar (volcado del plan + deudas) | T11 |
| `tasks/lessons.md` | Modificar (si hay correccion del usuario) | T11 |

---

## 4 · Olas y protocolo

```
Ola 0:  T0  ‖  B1                       (repos distintos / ficheros disjuntos)
Ola 1:  T1  ‖  T2  ‖  T4
Ola 2:  T3                              (necesita T1 y T2)
Ola 3:  T5 ‖ T6 ‖ T7 ‖ T8 ‖ T9          (necesitan T3 y T4)
Ola 4:  T10 + puertas globales
Ola 5:  T11 — revision del BLOQUE ENTERO (panel de 3, en paralelo)
```

**Por que T3 va sola en su ola:** fija el contrato de props que consumen los
cinco pasos de la ola 3. Si se solapara con ellos, cinco agentes escribirian
contra un contrato que todavia se esta moviendo.

**Por que T1 y T2 pueden ir juntas:** T1 toca `src/app/`, T2 toca
`src/components/booking|wizard/`. Disjuntos. T4 toca `src/hooks`, `src/lib` y
dos ficheros nuevos bajo `wizard/`, ninguno de ellos de T1 ni T2.

**Protocolo de commit — LAS ONCE TAREAS, SIN EXCEPCION:**

```bash
git add <sus rutas>
git commit -o <sus rutas> -m "..."
```

Las dos cosas. `git add` porque `git commit -o` falla sobre ficheros que git
aun no conoce, y casi todas las tareas crean ficheros. `-o` porque commitea
SOLO esas rutas e ignora el resto del indice: en una ola de cinco agentes sobre
el mismo arbol, sin el, el primero que commitea se lleva el trabajo a medio
escribir de los demas. **NUNCA `git add -A`. NUNCA `git commit -m` a secas.**
Cuidado con `-o` y las rutas: un fichero que la tarea toco pero no nombro en su
lista **nunca se commitea**, y las puertas siguen verdes sobre un arbol sucio.

**Las puertas POR TAREA son informativas; la VINCULANTE es la de la ola.** Las
olas 1 y 3 corren tres y cinco agentes **sobre el mismo arbol de trabajo**, asi
que el `tsc` de una tarea ve los ficheros a medio escribir de sus vecinas. Un
agente cuya puerta salga roja **por un fichero que no es suyo** lo dice en su
informe y NO lo toca: arreglarlo es pisar el trabajo de otro, y su commit `-o`
no lo recogeria de todas formas. Solo se detiene por errores en SUS ficheros.

**"Puertas" quiere decir SIEMPRE estas tres, y donde una tarea escribe
"Puertas + commit" significa exactamente esto**, con la salida pegada:

```
npx tsc --noEmit
npx eslint .
npx vitest run
```

Mas `npm run build` en T3 (introduce `useSearchParams` en un grupo de rutas
nuevo, §1.8.9) y en T10 (cierre del bloque).

**Puerta de cierre de CADA ola** (las mismas, siempre, con la salida pegada):

```
npx tsc --noEmit
npx eslint .
npx vitest run
```

y ademas `npm run build` al cerrar la ola 4. Ninguna puede quedar por debajo de
§1.7.

**Seguridad operativa, en CADA brief de implementador:**
- PROHIBIDO tocar `node_modules`. PROHIBIDO `npm ci`. Si falta algo:
  `npm install`.
- No cambiar de rama.
- Los agentes del frontend NO tocan `E:\IdeaProjects\rivoo` salvo B1 (que solo
  toca `client-service/`) y T11 (que solo escribe en `tasks/`).
- Leer `node_modules/next/dist/docs/` antes de escribir codigo de Next.
- Las credenciales de e2e viven en variables de entorno y NO se piden por chat
  ni se escriben en el repo.
- Verificacion CON EVIDENCIA: pegar la salida real, nunca afirmar "pasa".
- Las trampas de §1.8 se copian enteras en cada brief.

---

## 5 · Tareas

### T0 · Tokens y `formatDuration`

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Anadir a `:root` los dos tokens de D21 con su comentario de procedencia
      (§1.6 los da: `NuevaCitaPaso4.dc.html:67` /
      `NuevaCitaDesktopPaso4.dc.html:78` y `NuevaCitaPaso1.dc.html:89` /
      `NuevaCitaDesktopPaso1.dc.html:113`), y **mapearlos en
      `@theme inline`** como `--color-border-dashed-strong` y
      `--color-avatar-muted`. Sin el mapeo, Tailwind v4 descarta la utilidad en
      silencio (§1.6).
- [ ] Test primero, en `src/lib/utils/dates.test.ts`:
      `formatDurationTight(45) === "45min"`, `(30) === "30min"`,
      `(90) === "1h 30min"`, `(120) === "2h"`. Verlos fallar (la funcion no
      existe).
- [ ] Anadir `formatDurationTight` JUNTO a `formatDuration`, sin tocar esta
      ultima (D10). Comentario en las dos diciendo que artboards usa cada una y
      por que coexisten, para que nadie "unifique" en el futuro.
- [ ] **NO se toca ningun consumidor en esta tarea** y ningun test existente
      deberia ponerse rojo. Si alguno cae, se ha tocado algo que no tocaba.
- [ ] Comprobar con `grep -rn "formatDuration" src/` que los CATORCE
      consumidores (D10) siguen llamando a la funcion de siempre.
- [ ] `npx tsc --noEmit`, `npx eslint .` y `npx vitest run`, salida pegada.
- [ ] Commit.

### B1 · `search` y orden en `GET /api/v1/clients`

**Repo:** `E:/IdeaProjects/rivoo`, modulo `client-service`. **Nadie mas toca
ese repo en esta ola.**
ese repo en esta ola.**

**DOS restricciones que deciden la implementacion, y ninguna es negociable:**

1. **JPQL o consulta derivada. NUNCA `nativeQuery = true`.** El aislamiento
   entre salones lo pone el `@Filter` de Hibernate declarado en
   `rivoo-common/.../tenant/TenantAwareEntity.java:15-16`
   (`condition = "tenant_id = :tenantId"`), que `ClientJpaEntity` hereda. **Ese
   filtro NO se aplica a consultas nativas**, solo a HQL/JPQL y Criteria. Una
   `@Query(nativeQuery = true)` aqui hace que `GET /api/v1/clients` devuelva
   clientes de TODOS los tenants: datos personales de un salon en la pantalla de
   otro, con las puertas en verde. Si la consulta parece pedir SQL nativo, la
   respuesta es reformularla en JPQL, no desactivar el filtro.
2. **Ni `NULLS FIRST` ni `NULLS LAST`.** El motor es MySQL 8.0
   (`client-service/src/main/resources/application-local.yml:3`) y esa clausula
   no existe ahi: es un error de sintaxis. Tampoco hace falta — en MySQL los
   NULL son el valor mas bajo, asi que `ORDER BY c.lastVisitAt DESC` ya los deja
   al final (§1.5.9).

- [ ] Localizar la cadena completa: `ClientController.list` →
      `GetClientUseCase.list` → `ClientService` (`:40`, implementa
      `GetClientUseCase`) → `ClientPersistencePort` → el adaptador → el
      repositorio JPA.
- [ ] **Montar la infraestructura de test de integracion, que este modulo NO
      tiene.** `find client-service/src/test -type f` devuelve hoy solo
      `ClientServiceTest.java` y `ClientExceptionDetailPolicyTest.java`, los dos
      Mockito puro; y no existe `client-service/src/test/resources/`. Copiar el
      montaje de `appointment-service` (Testcontainers MySQL +
      `src/test/resources/application-test.yml` + `@Tag("integration")`), que es
      el patron del monorepo.
      **Por que no vale el patron actual del modulo:** sobre un puerto mockeado
      el orden de la consulta lo decide el `thenReturn` del propio test, no la
      base de datos. Un test asi pasa en verde afirmando un orden que nunca se
      ejecuto.
- [ ] Test primero, de integracion: (a) `search` nulo o vacio devuelve todo;
      (b) `search` casa por nombre, apellido, telefono y email, sin distinguir
      mayusculas y por subcadena; (c) el orden es `lastVisitAt DESC,
      createdAt DESC` y un cliente **con `lastVisitAt = null`** sale el ULTIMO —
      sin ese cliente en el juego de datos, el test no prueba nada;
      (d) **aislamiento entre tenants**: un cliente de OTRO tenant cuyo nombre
      casa con `search` NO aparece. Es la prueba que cierra la restriccion 1.
      Para que ese cuarto caso pruebe algo, el test tiene que fijar
      `TenantContext` en `@BeforeEach` y limpiarlo en `@AfterEach` —el molde lo
      hace (`AppointmentRepositoryIntegrationTest.java:38-45`)—: con
      `tenantId == null` el aspecto NO activa el filtro (semantica
      PLATFORM_ADMIN) y el test saldria rojo contra una implementacion
      correcta.
- [ ] **Verlos fallar de verdad.** `mvn test` los SALTA: `pom.xml:43` fija
      `<surefire.excluded.groups>integration</surefire.excluded.groups>` y
      `:123` se lo pasa a surefire, asi que un test con `@Tag("integration")` no
      se ejecuta y por tanto **nunca sale rojo**. Hay que activar el perfil:
      `mvn test -P integration-test -pl client-service -am`
      (`pom.xml:164-169`), con el `mvn.cmd` de `~/.m2/wrapper/dists` — el repo
      NO tiene `./mvnw`.
- [ ] Implementar. `search` es `@RequestParam(required = false)`. La consulta va
      PARAMETRIZADA (nunca concatenada) y en **JPQL** (restriccion 1). El orden
      se fija en la consulta, no con `Pageable`, para que sea estable y no
      dependa de que el consumidor mande el parametro (D9).
- [ ] Verlos pasar, con el mismo comando y la salida pegada. **Necesita
      Docker.** Si no lo hay, decirlo EXPLICITAMENTE en el informe como "no
      ejecutado" en vez de darlo por bueno: el bloque 4 cerro con un test de
      integracion que nunca corrio y eso ya esta anotado como deuda.
- [ ] Ejecutar tambien la bateria normal del modulo (`-pl client-service -am`,
      sin perfil) para comprobar que los dos tests Mockito existentes siguen
      verdes.
- [ ] Commit (protocolo de §4).

### T1 · Grupo `(fullscreen)` y traslado de la ruta

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] `git mv` del directorio `appointments/` entero (D1). Comprobar antes que
      solo contiene `new/page.tsx`.
- [ ] Crear el layout: `"use client"`, monta `OnboardingGate` y devuelve
      `{children}` dentro de un contenedor `flex min-h-dvh flex-col
      bg-background`. **`min-h-dvh`, no `min-h-full`**: `body` solo fija
      `min-height`, asi que un porcentaje no resuelve — es el mismo bug que ya
      se arreglo dos veces en este repo, documentado en
      `(onboarding)/layout.tsx:35-44`.
- [ ] NO monta `AppSidebar`, `BottomNav`, `FabButton` ni `useSwipeNavigation`.
- [ ] Test del layout: (a) monta `OnboardingGate`; (b) NO hay barra lateral;
      (c) NO hay barra inferior. **Con `mockMatchMedia(true)` y su `afterEach`**
      (§1.8.5) — no porque el layout dependa del ancho (no depende: D1 le quita
      `AppSidebar`, `BottomNav`, `FabButton` y el swipe), sino porque el caso de
      escritorio es el unico donde la ausencia de barra lateral significa algo.
      Sabiendo que el test no puede fallar por construccion, su valor es de
      REGRESION: se pone rojo el dia que alguien remonte el chasis de `(app)`
      aqui. Dejarlo escrito en el propio test.
- [ ] Ajustar en `page.tsx` unicamente lo imprescindible para que compile tras
      el traslado. **La reescritura de la pagina es de T3**: aqui no se toca su
      maqueta.
- [ ] Comprobar que `src/app/(app)/appointments` ya no existe y que
      `grep -rn "appointments/new" src/` devuelve **exactamente la misma salida
      que antes del traslado** (guardarla antes y compararla). Es un diff, no un
      recuento: la ruta no cambia (§1.4), asi que cualquier diferencia es un
      fallo. §1.4 nombra los enlaces principales pero no es una lista cerrada —
      hay ademas apariciones en tests y en comentarios.
- [ ] `npx tsc --noEmit`, `npx eslint .`, `npx vitest run`. Salida pegada.
- [ ] Commit.

### T2 · Promocion de `WizardStepper` y `WizardSummaryAside`

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] `git mv` de los dos componentes y del test que exista. Renombrar los
      simbolos a `WizardStepper` / `WizardSummaryAside` y los tipos
      (`WizardSummaryRow`, `WizardSummaryAsideProps`, `WizardStepperProps`).
- [ ] `WizardStepper`: anadir `labels?: readonly string[]` con las cinco
      etiquetas actuales como defecto (D4) y `visibleFrom?: "md" | "lg"` con
      `"md"` por defecto (D26), resuelto con dos cadenas de clases COMPLETAS.
      **No se cambia ni un valor visual**; el comentario que documenta el
      desacuerdo entre artboards sobre el color de la etiqueta superada se
      conserva tal cual.
- [ ] Mover `AFTERNOON_HOUR` de `public-datetime-step.tsx:39` a
      `src/lib/utils/dates.ts` como constante exportada, con su comentario, y
      actualizar el consumidor publico (D27). **Nombrar `dates.ts` en el
      `git add` y en el `git commit -o`.**
- [ ] `WizardSummaryAside`: anadir `heading?: string` (defecto `"Tu reserva"`),
      `note?: ReactNode` (defecto = el bloque actual del candado; `null` lo
      quita) y `valueTone?: "default" | "placeholder"` en `WizardSummaryRow`
      (defecto `"default"`).
      **`valueTone` no es opcional en la practica**: el aside del paso 1 dibuja
      "Sin elegir" con el ESTILO de la raya vacia (`DesktopPaso1:125`:
      `font-size:14px; color:#C4B5A6`, sin negrita), y hoy `hasValue`
      (`booking-summary-aside.tsx:104-114`) da `true` para cualquier string no
      vacio y lo pinta `text-sm font-semibold` en color de primer plano. Sin
      esta prop, T5 (ola 3) descubriria que necesita tocar un fichero cuyo
      unico propietario es T2 (ola 1). El resto no se toca.
- [ ] Actualizar TODAS las importaciones. `npx tsc --noEmit` es la red: cero
      errores significa cero olvidos.
- [ ] Anadir al test de `WizardSummaryAside` dos casos nuevos: con `heading`
      propio lo pinta, y con `note={null}` la nota NO aparece. Si el stepper no
      tenia test, crear uno minimo que fije las etiquetas por defecto y que
      `labels` las sustituye.
- [ ] Ejecutar la bateria ENTERA (no solo `components/`): las cinco pantallas
      publicas tienen que seguir en verde sin tocar sus afirmaciones. Si alguna
      cae, es que el movimiento cambio algo visual — se investiga la causa, no
      se ajusta el test.
- [ ] `npx tsc --noEmit`, `npx eslint .`, `npx vitest run`. Salida pegada.
- [ ] Commit.

### T4 · Store, hooks y derivacion del resumen

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] **Store (D16, D17, D22).** Es el unico sitio del bloque donde se toca
      `wizard-store.ts`: si algo falta aqui, T7 y T9 —que corren juntas en la
      ola 3— tendrian que editarlo las dos. Cambios:
      - `reset` pasa a `reset(seed?: Partial<WizardState>)`, fusionando la
        semilla sobre `INITIAL_STATE`.
      - Estado nuevo para el prefill (D16): `preferredEmployeeId: string | null`,
        `preferredDate: string | null`, `preferredSlot: string | null`.
        `preferredEmployeeId` existe porque el store guarda un `Employee`
        ENTERO (`wizard-store.ts:10`) y la query que lo resuelve es asincrona:
        el id tiene que poder esperar en algun sitio. Lo consume T5.
      - **Estado nuevo para el empleado del hueco (D6, D22):**
        `selectedSlotEmployeeId: string | null`, y `selectDateTime` pasa a
        `(date, slot, employeeId)`. Sin esto, T7 no tiene donde guardar de quien
        es el hueco elegido con "Sin preferencia" y T9 vuelve a mandar la cadena
        vacia (`confirmation-step.tsx:68`), que es justo el bug que el bloque
        cierra. **No vale llamar a `selectEmployee`**: borra
        `selectedService`, `selectedDate` y `selectedSlot`
        (`wizard-store.ts:49-58`), o sea que destruiria la seleccion recien
        hecha.
      - `selectDateTime` limpia las tres preferencias al elegir.
      - **Y arrastra DOS ficheros mas, que T4 tiene que actualizar en la misma
        tarea y nombrar en su `git commit -o`:**
        `src/lib/stores/wizard-store.test.ts` (que YA EXISTE y afirma
        `selectDateTime("2026-03-25", "10:00")` con dos argumentos en `:87` y
        `:104`) y la unica llamada de produccion,
        `datetime-step.tsx:46`. Sin eso, `npx tsc --noEmit` y `npx vitest run`
        —las propias puertas de T4— salen rojos en la ola 1 sobre un fichero de
        T7, que corre en la ola 3.
        En `datetime-step.tsx` **solo se toca esa linea**: pasarle
        `selectedEmployee?.id ?? ""` de momento. T7 lo reescribe entero en la
        ola 3 y ahi es donde el id pasa a salir del hueco.
        El tercer argumento es OBLIGATORIO, no opcional: opcional deja vivo el
        bug de la cadena vacia (`confirmation-step.tsx:68`) que D22 cierra.
- [ ] Test del store: `reset()` sin semilla vuelve al inicial; `reset({...})`
      siembra; elegir empleado sigue limpiando aguas abajo;
      `selectDateTime(d, s, "emp_1")` guarda el empleado y NO borra el servicio.
- [ ] **`clients.ts` + `use-clients.ts` (D8, D9):** `search` sigue viajando como
      parametro (ahora el backend lo lee). `useClients` deja de exigir
      `search.length >= 2`: recibe `search` y devuelve la lista inicial cuando
      esta vacio. **La `queryKey` cambia a `["clients", { search, size: 10 }]`.**
      Hoy `useClients` (`use-clients.ts:13`) y la pantalla `/clients`
      (`clients/page.tsx:27`) comparten la clave `["clients", { search }]` con
      TAMANOS DE PAGINA DISTINTOS (10 y 50). No chocan solo porque `useClients`
      esta DESHABILITADO con `search` vacio — la guarda que D9 quita. Sin
      separar la clave, el paso 4 pintaria hasta 50 filas y `/clients` podria
      quedarse con 10, de forma intermitente (`staleTime: 10s`), y ninguna
      puerta lo veria. El prefijo `["clients"]` se conserva para que la
      invalidacion de `useCreateClient` (`use-clients.ts:28`) siga alcanzando
      a las dos. Test: con `search` vacio la query esta habilitada y pide la
      lista; con texto, lo manda; y la clave lleva el tamano.
- [ ] **`useEmployeesServices(ids)` (D28):** gemelo exacto de
      `useEmployeesWorkingHours` (`use-staff.ts:65-93`) — mismo `useQueries`,
      mismo `combine` MEMORIZADO con `useCallback` y `ids` en las dependencias
      (el mapa se indexa POR POSICION: con una lista distinta y el `combine`
      viejo, cada empleado recibiria los servicios de otro). Misma `queryKey`
      que `useEmployeeServices` para compartir cache con el paso 2. Va en
      `hooks/use-staff.ts`, junto a su gemelo. Test: mapa indexado bien, y un
      empleado cuya peticion falla no tumba el resto.
- [ ] **`use-wizard-availability.ts` (D6, D22):** exporta
      `WizardSlot = AvailableSlot & { employeeId: string }` y un hook que recibe
      `{ employeeIds, serviceId, date }`. Con un solo id es una query; con
      varios, `useQueries` con **`combine` memorizado con `useCallback`** —
      `use-staff.ts:65-93` documenta por que una flecha en linea rompe el memo
      SIEMPRE y rehace el resultado en cada render. Une los huecos, los ordena
      por `startTime` y, ante dos empleados libres a la misma hora, se queda con
      el primero de la lista (determinista). Expone `isLoading` y `isError`
      agregados. Test con las queries mockeadas: union, orden, desempate, y que
      un empleado cuya peticion falla no tumba el resto.
- [ ] **`wizard-summary.ts` (D20):** funciones puras que, a partir del estado
      del store, devuelven las filas del aside (`WizardSummaryRow[]`), el total,
      la etiqueta y el estado del CTA por paso, y los textos derivados que
      comparten movil y escritorio (resumen del pie, subtitulos de escritorio de
      §1.2).
      **Tres formas de fecha distintas, y ninguna sale de una sola funcion
      existente:** el pie movil dibuja `"Miercoles 28"` (nombre del dia +
      numero, sin mes ni coma — `NuevaCitaPaso3.dc.html:114`); la tarjeta del
      paso 5 dibuja `"Miercoles, 28 de agosto"` (eso SI es `formatDateLong`); y
      el aside de escritorio dibuja `"Mie 28"` (dia abreviado, §1.2 Variacion
      3). Las tres se derivan AQUI, en un solo sitio, y **las tres llevan tilde
      en el codigo** aunque el artboard las escriba sin ella (§1.8.3).
      Usa `formatCurrency`, **`formatDurationTight`** (D10 — NO
      `formatDuration`: los artboards del asistente dibujan "45min" pegado,
      p. ej. `NuevaCitaDesktopPaso2.dc.html:78,86`) y `formatDateLong`. Test con
      los helpers `normalize`/`exact` de `appointment-block.test.tsx:43-51` para
      el U+00A0 de `formatCurrency` (§1.8.4).
- [ ] `npx tsc --noEmit`, `npx eslint .`, `npx vitest run`. Salida pegada.
- [ ] Commit.

### T3 · `NewAppointmentShell` y el progreso movil

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

**Depende de:** T1 (la ruta ya esta en su sitio) y T2 (las primitivas ya se
llaman `Wizard*`).

- [ ] `WizardProgress` (D3, D23) pasa a ser SOLO las cinco barras planas de
      movil (§1.1), sin contador. **No lleva clase de visibilidad propia**: lo
      monta o no lo monta el shell segun `isDesktop` (D26, mecanismo unico). Cinco tramos `h-[3px] flex-1
      rounded-full`, `gap-[5px]`, `bg-primary` hasta el paso actual incluido y
      `bg-border` el resto. Reescribir su test.
- [ ] `NewAppointmentShell` (D2, D5). Props:
      `step: 1|2|3|4|5`, `title: string`, `subtitle?: ReactNode`,
      `onBack?: () => void`, `onClose: () => void`, `aside?: ReactNode`,
      `footer?: ReactNode`, `children`.
      - Cabecera movil de 56px con los valores de §1.1, **montada cuando
        `!isDesktop`** (D26: un solo mecanismo, `useMediaQuery`, no clases). Con
        clases mezcladas —`md:hidden` en la movil y `hidden lg:flex` en la de
        escritorio— la franja 768-1023 se queda SIN NINGUNA cabecera y sin X
        para cerrar; y mezclando clases con `useMediaQuery` la pantalla parpadea
        antes de hidratar. A la
        izquierda una caja de 44x44 que lleva el chevron **solo si `onBack`**, y
        que se pinta igual de vacia si no (D18); en el centro "Nueva cita"
        `text-sm font-semibold`; a la derecha la X.
      - Cabecera de escritorio de 68px con los valores de §1.2, montada cuando
        `isDesktop` (D26), `bg-sidebar`, marca + "Nueva cita", "Cancelar" y el
        boton X con la receta de 38x38 que §1.4 cita de `page-shell.tsx:235-243`
        (`variant="outline" size="icon" className="size-[38px] shrink-0"`, icono
        a `size-[18px]`). NO usar `size="action"`: lleva `px-[18px]` y no es
        cuadrado.
      - Contenedor: el ancho, la direccion y el padding los decide **`isDesktop`**,
        igual que todo lo demas (D26), NO clases `lg:`. Mezclarlos deja el primer
        pintado de un 1440 con contenedor de escritorio y cabecera movil, que es
        el hibrido que D26 existe para eliminar.
        **Inspirarse** en el de `booking-step-shell.tsx:99`
        citandolo en un comentario, pero NO copiarlo tal cual. El original es
        `"... max-w-[390px] ... px-5 pt-5 md:max-w-2xl md:gap-[26px] md:px-10
        md:py-8 lg:max-w-[1120px] lg:flex-row ..."`, y con solo mover las
        columnas a `lg:` la franja 768-1023 se queda con cabecera y progreso
        MOVILES dentro de un cuerpo de 672px con padding de ESCRITORIO — el
        hibrido que D26 dice no copiar. Por debajo de 1024 manda el padding
        movil de §1.1 (`14px 16px 0`), no el `px-5 pt-5` ni el `md:px-10
        md:py-8` del original. El aside es siempre 320px y el padding de
        escritorio es `32px 40px`.
      - Aside montado condicionalmente con `useMediaQuery("(min-width:
        1024px)")` y pie condicionalmente con su negacion —
        `booking-step-shell.tsx:67-73` explica por que es montaje condicional y
        no CSS: con los dos en el DOM, `getByRole("button", { name:
        "Continuar" })` encuentra dos.
      - **INVARIANTE, y este repo ya la pago una vez:** `{children}` va SIEMPRE
        en la misma posicion del arbol, en las dos ramas. `useMediaQuery`
        devuelve `false` en SSR y en el primer pintado, asi que toda carga de
        escritorio cruza el punto de corte una vez; si `{children}` cambiara de
        posicion o de tipo de hermano, React desmontaria y remontaria los pasos
        en esa transicion. `(app)/layout.tsx:50-58` documenta ese mismo bug
        senalando A ESTA PANTALLA por su nombre ("reset the /appointments/new
        wizard on resize"). El store de Zustand sobrevive a un remontaje, pero
        el `useState` local de los pasos —el texto del buscador del paso 4, el
        formulario de alta a medio escribir— no.
      - **El CROMO del pie lo pinta el SHELL, no el paso.** La caja es identica
        en los pasos 3 y 5 (§1.1: `fixed` abajo, `padding: 14px 16px 20px`,
        `border-top`, columna `gap:10px`) y solo cambia su contenido; si cada
        paso pintase su propia caja, dos tareas de la ola 3 mantendrian por
        separado el mismo borde y el mismo padding. El slot `footer` recibe
        SOLO el contenido: la fila de resumen y el CTA.
      - `pb-28` cuando hay pie, como `booking-step-shell.tsx:100`.
      - El eyebrow "Paso N de 5" es de MOVIL (§1.1); escritorio no lo dibuja,
        alli lo dice el stepper.
      - Monta `WizardProgress` (movil) y `WizardStepper` con las etiquetas de
        §1.2 y `visibleFrom="lg"` (D26).
      - `leading-tight` DETRAS de cada `text-[Npx]` (§1.8.1, §1.8.2).
- [ ] `WizardContextPills` (D32): componente de MOVIL que pinta una, dos o tres
      pildoras segun lo que haya elegido en el store, con los valores de §1.1
      (`.chip` 30px, avatar de 22px a 9px/700, texto 12px). El avatar sale de
      `lib/utils/avatar.ts` (D14), nunca del hex del artboard. Test: con solo
      profesional pinta una; con servicio, dos; con fecha y hora, tres.
- [ ] Test del shell: cabecera movil sin chevron en el paso 1 y con el a partir
      del 2; la X llama a `onClose`; con `mockMatchMedia(true)` aparecen el
      stepper y el aside y NO el pie; con `false`, al reves. `afterEach` que
      restaure.
- [ ] **CADA PASO monta el shell; `page.tsx` es un dispatcher puro.** Es el
      patron que el repo ya usa: `public-employee-step.tsx:148,160-165`
      construye su `aside` y su `footer` y DEVUELVE `<BookingStepShell ...>`;
      ninguna pagina lo monta. Lo contrario —la pagina montando el shell y los
      pasos pasandole titulo, aside y pie— obligaria a T5..T9, que corren
      JUNTAS en la ola 3, a editar `page.tsx` cada una: cinco propietarios de
      un fichero en una ola, machacandose con `git commit -o`.
- [ ] **`page.tsx` envuelve la lectura de la query en su propio `<Suspense>`**
      (§1.8.9), como hace `staff/page.tsx:24-32`. Sin el, `npm run build` falla
      para TODO el grupo `(fullscreen)`, y como esa puerta solo corre en la ola
      4 el fallo aparece dos olas mas tarde de donde se introduce.
- [ ] Reescribir `page.tsx` como dispatcher: lee los parametros de la query,
      siembra el store en el `useEffect` de montaje (D16, D17), y monta
      `{step === N && <PasoN />}` y nada mas. No monta el shell, no calcula
      titulos, no construye asides.
- [ ] El shell recibe de cada paso: `step`, `title`, `subtitle`, `onBack`,
      `onClose`, `aside`, `footer`, `children`. `onClose` = `reset()` +
      `router.back()` y `onBack` = `prevStep` los expone un helper compartido
      (`useWizardNavigation`) que T3 crea junto al shell, para que los cinco
      pasos no reimplementen el cierre cinco veces.
- [ ] Test de la pagina, **acotado a lo que la pagina hace de verdad** (D16: la
      pagina NO resuelve el empleado, eso es de T5 en la ola 3): sin parametros
      el store queda en el estado inicial y arranca en el paso 1; con
      `employeeId` siembra `preferredEmployeeId` **y sigue en el paso 1**; con
      `date`/`time` siembra las otras dos preferencias; `rescheduleId` no
      cambia nada.
      **No escribir aqui "arranca en el paso 2"**: en la ola 2
      `employee-step.tsx` es todavia el original de 83 lineas y no conoce la
      preferencia, asi que ese test solo puede salir rojo y empujar a resolver
      el empleado dentro de la pagina — justo lo que D16 prohibe.
- [ ] `npx tsc --noEmit`, `npx eslint .`, `npx vitest run` **y `npm run build`**
      — esta tarea introduce `useSearchParams` en un grupo de rutas nuevo y es
      la unica puerta que lo ve (§1.8.9). Salida pegada.
- [ ] Commit.

### T5 · Paso 1 — Profesional

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Reescribir contra §1.1 (lista de filas en movil) y §1.2 (rejilla de dos
      columnas en escritorio). "Sin preferencia" es siempre la primera opcion,
      con el tratamiento discontinuo de los dos artboards.
- [ ] Avatares por D14. Estado "hoy no trabaja" por D13: `opacity-[0.55]` en
      movil, `opacity-50` en escritorio, subtitulo distinto en cada ancho
      (§1.1, §1.2) y sin columna de citas en escritorio.
      **La fila SI es pulsable y conserva su chevron (D33)**: bloquearla dejaria
      a ese empleado sin poder recibir ninguna cita en los 30 dias del horizonte
      (D29), no solo hoy. La atenuacion informa, no deshabilita.
- [ ] Al elegir a alguien que hoy no trabaja, el paso 3 abre en el primer dia
      que SI trabaje, no en hoy (D33).
- [ ] "N citas hoy" solo en escritorio, por D15.
- [ ] Subtitulo de escritorio segun D12; movil no lleva subtitulo (§1.1).
- [ ] Aside y CTA desde `wizard-summary.ts` (D20): en el paso 1 la fila
      "Profesional" vale "Sin elegir" y el CTA esta deshabilitado.
- [ ] **El paso 1 NO pasa `onBack`** (`undefined`), para que el shell deje el
      hueco de 44x44 vacio tal y como lo dibuja `NuevaCitaPaso1.dc.html:26`
      (D18). `useWizardNavigation` lo expone para los cinco pasos; es el paso 1
      quien decide no usarlo.
- [ ] Elegir avanza al paso 2. §1.1 dibuja el chevron de "entrar" en TODAS las
      filas pulsables, **incluida "Sin preferencia"** (`Paso1:58`); solo la del
      empleado que hoy no trabaja va sin el.
- [ ] Resolver el prefill (D16): cuando llega `useEmployees`, si
      `preferredEmployeeId` casa con un empleado activo, seleccionarlo y avanzar
      al paso 2; si no casa, limpiar la preferencia. Es el UNICO sitio del
      bloque donde se resuelve ese id.
- [ ] Tests: la lista sale en el orden del artboard; **el que hoy no trabaja SI
      responde al clic** (D33) y se pinta atenuado; con `mockMatchMedia(true)` aparece "citas hoy" y con `false` no;
      "Sin preferencia" pone `anyEmployee`.
- [ ] Puertas + commit.

### T6 · Paso 2 — Servicio

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Reescribir contra §1.1 y §1.2. Agrupar por `category` (D11), en el orden
      de aparicion de la lista; los que no tienen categoria van a un grupo final
      sin cabecera.
- [ ] Los servicios que el empleado no ofrece se PINTAN atenuados con el
      subtitulo sustituido (D11, textos distintos por ancho segun §1.1/§1.2), y
      no responden al clic. Con "Sin preferencia" no se atenua ninguno.
- [ ] Pildoras de contexto con **`WizardContextPills`** (D32), no propias, en
      movil; subtitulo de
      escritorio con el nombre (§1.2).
- [ ] Precio con `formatCurrency`, duracion con **`formatDurationTight`**
      (D10).
- [ ] Aside de escritorio desde `wizard-summary.ts` (D20): en el paso 2 la fila
      "Profesional" ya tiene valor y el CTA sigue DESHABILITADO
      (`NuevaCitaDesktopPaso2` lo dibuja gris). El paso monta el shell y le pasa
      su `aside` (T3, A5).
- [ ] Tests: agrupacion y cabeceras; el no ofrecido aparece pero no avanza; el
      precio se afirma con `normalize`/`exact` (§1.8.4).
- [ ] Puertas + commit.

### T7 · Paso 3 — Fecha y hora

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Reescribir contra §1.1 (tira de 6 dias, rejilla de 3 columnas, botones de
      hueco de **46px**, pie fijo) y §1.2 (rejilla de 7 dias con tercera linea,
      rejilla de 6 columnas, botones de **44px**, CTA en el aside). Las dos
      alturas de boton son distintas a proposito: §1.1 y §1.2 las dan medidas.
- [ ] Pildoras de contexto con **`WizardContextPills`** (D32): aqui son DOS
      (profesional y servicio), y las pinta el mismo componente, no una copia.
- [ ] **Horizonte de reserva: 30 dias, como hoy (D29). Las seis/siete celdas
      son el ANCHO de la tira, no el limite.** Concretamente:
      - Movil: dias CONSECUTIVOS desde hoy (no los seis del artboard, que se
        salta el domingo), seis VISIBLES sobre una tira de **30** con scroll
        horizontal. Patron y constante en `public-datetime-step.tsx:28`
        (`MOBILE_STRIP_DAYS = 30`).
      - Escritorio: rejilla de siete con **navegacion por semanas, 4 paginas**
        (`public-datetime-step.tsx:33-34`), y la etiqueta de mes que el artboard
        ya dibuja (`NuevaCitaDesktopPaso3.dc.html:73`) reflejando la pagina
        visible.
      - Las flechas de semana **no las dibuja ningun artboard** y se pintan
        igualmente (`public-datetime-step.tsx:402,411` ya lo hizo por lo mismo):
        sin ellas la mitad del horizonte es inalcanzable. Va a las deudas.
      **Recortar el horizonte a una semana es una regresion funcional** — hoy
      `datetime-step.tsx:13` da `DAYS_AHEAD = 30` — y ninguna de las cuatro
      puertas la veria.
- [ ] Estado vacio cuando el subconjunto de empleados es vacio (D28): "Ningun
      profesional ofrece este servicio" con vuelta al paso 2.
- [ ] Disponibilidad por `use-wizard-availability` (T4): con empleado elegido,
      un id; con "Sin preferencia", los activos que **ofrezcan el servicio**,
      resueltos con `useEmployeesServices` (D6, D28). Nunca preguntar por todos
      sin filtrar: el backend no comprueba la asignacion y crearia la cita igual.
- [ ] Corte Manana/Tarde a las 14:00 importando **`AFTERNOON_HOUR` de
      `lib/utils/dates.ts`**, donde T2 la dejo (D27). **Ya NO esta en
      `public-datetime-step.tsx:39`**: si al abrir ese fichero no aparece, es
      porque T2 la movio, no porque haya que declararla de nuevo. Declarar un
      `14` local aqui es exactamente lo que D27 existe para impedir.
- [ ] "Cerrado" en la celda de dia desde `useEmployeesWorkingHours` (D7). La
      tercera linea **no pinta contadores** y lleva el comentario de D7 con la
      misma redaccion que `public-datetime-step.tsx:447-456`. Los huecos
      ocupados **no se pintan tachados**, con el comentario de
      `public-datetime-step.tsx:125-128`.
- [ ] Aplicar `preferredDate`/`preferredSlot` del store (D16) al montar: si el
      hueco preferido sigue en la lista, queda seleccionado; si no, se abre ese
      dia sin hueco elegido.
- [ ] Pie fijo de movil con el resumen y el precio (§1.1); en escritorio el CTA
      vive en el aside (D19, D20).
- [ ] Tests: el corte manana/tarde reparte bien; el dia cerrado no es pulsable;
      elegir hueco guarda fecha, hora **y `employeeId`** cuando venia de "Sin
      preferencia"; el pie solo existe en movil; la preferencia se aplica y se
      descarta cuando el hueco ya no esta.
- [ ] Borrar `src/hooks/use-availability.ts` y su test si lo tiene: sus unicos
      consumidores eran `datetime-step.tsx` y `datetime-step.test.tsx`, y esta
      tarea los sustituye por `use-wizard-availability`. Un export sin usar no
      es error de lint, asi que si no se borra aqui no lo detecta nada.
- [ ] Puertas + commit.

### T8 · Paso 4 — Cliente

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Reescribir contra §1.1 y §1.2. La lista se pinta SIEMPRE (D9): sin texto
      en el buscador es "Clientes recientes"; con texto, resultados.
- [ ] Pildoras de contexto con **`WizardContextPills`** (D32): aqui son TRES
      (profesional, servicio, y dia + hora).
- [ ] Placeholders distintos por ancho (§1.3.E).
- [ ] Tarjeta "Crear nuevo cliente" primera en escritorio (primera celda de la
      rejilla) y sobre la etiqueta "Clientes recientes" en movil, con
      `border border-dashed border-border-dashed-strong` (D21). **`border-dashed`
      es la utilidad NATIVA de estilo de borde y `border-border-dashed-strong`
      la del COLOR; escribir `border-dashed-strong` a secas no existe y se
      descarta en silencio (§1.6).** Receta del repo: `free-slot-hint.tsx:43`.
- [ ] Borrar "Continuar sin cliente" (D24). Conservar el formulario de alta en
      linea tal cual, sin rediseno, con un comentario que diga que no tiene
      artboard y por que se queda.
- [ ] Contacto y visitas segun §1.1 (movil: una linea con separador) y §1.2
      (escritorio: columna a la derecha). **El numero sale de
      `client.totalVisits`, que hoy vale 0 para todos (§1.5.5, D31): es correcto
      que pinte "0 visitas". NO derivarlo contando citas.**
- [ ] Aside de escritorio desde `wizard-summary.ts` (D20): en el paso 4 el CTA
      sigue DESHABILITADO (`NuevaCitaDesktopPaso4:137` lo dibuja gris). El paso
      monta el shell y le pasa su `aside` (T3, A5).
- [ ] Tests: sin buscar ya hay lista; escribir cambia la consulta; elegir
      cliente avanza; "Crear nuevo cliente" abre el formulario; **no existe**
      ningun boton "Continuar sin cliente".
- [ ] Puertas + commit.

### T9 · Paso 5 — Confirmacion, y los dos comentarios

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Reescribir contra §1.1 y §1.2: tarjeta con cabecera de rango horario
      grande, fecha larga con `formatDateLong`, pildora de estado (texto
      distinto por ancho, §1.3.D), y las tres filas / tres columnas.
- [ ] La creacion pasa a `useMutation` (hoy es `useState(isSubmitting)` +
      `try/catch`): estado en vuelo, error y exito los da la mutacion. El alta
      del cliente nuevo, cuando la hay, va DENTRO de la misma `mutationFn`, para
      que un fallo al crear la cita no deje al usuario sin saber que el cliente
      SI se creo — hoy eso ocurre en silencio.
- [ ] `employeeId` del hueco elegido cuando venia de "Sin preferencia" (D6);
      nunca la cadena vacia que manda hoy `confirmation-step.tsx:68`.
- [ ] Duracion del servicio con **`formatDurationTight`** (D10), no
      `formatDuration`.
- [ ] Notas con la etiqueta "Notas para el profesional" y las alturas de §1.1 /
      §1.2.
- [ ] Pie fijo de movil con "Total" y "Crear cita"; en escritorio, el aside con
      la fila "Total" y el CTA "Crear cita" (D20).
- [ ] Actualizar los dos comentarios que hoy dicen que el asistente no lee los
      parametros de la query, para que digan lo que ES verdad tras D16 —
      incluido que `rescheduleId` se sigue ignorando.
- [ ] Tests: pinta el rango, la fecha larga y la pildora correcta por ancho.
      **La fecha larga lleva TILDE** ("Miercoles" con acento, §1.8.3): copiar el
      texto del artboard en la afirmacion no encuentra nada.
      crear con cliente existente manda `clientId`; crear con cliente nuevo lo
      crea antes y manda su id; un fallo deja el paso en pie con el mensaje;
      el `employeeId` enviado es el del hueco cuando se eligio "Sin
      preferencia".
- [ ] Puertas + commit.

### T10 · Comparacion visual y puertas globales

**Ficheros:** los que §3 asigna a esta tarea, TODOS y solo esos. **No se
repiten aqui a proposito**: dos listas del mismo conjunto divergen, y con
`git commit -o` un fichero que se quede fuera no se commitea nunca (§4). Antes
de empezar, copiar de §3 las rutas de esta tarea y usarlas literalmente en el
`git add` y en el `git commit -o`.

- [ ] Escribir la spec siguiendo el patron de
      `visual/appointment-detail-vs-artboards.spec.ts` (bloque 4): recorre los
      cinco pasos a 390x844 y a 1440x900 y compara con los diez artboards.
- [ ] **No se ejecuta aqui** (D25): necesita credenciales y la pila levantada.
      Dejar en el informe la orden exacta, con las credenciales como variables
      de entorno y NUNCA en el fichero.
- [ ] Puertas GLOBALES del bloque: `npx tsc --noEmit`, `npx eslint .`,
      `npx vitest run`, `npm run build`. Las cuatro, con la salida pegada, y
      comparadas contra §1.7.
- [ ] Commit.

### T11 · Revision del bloque entero

**Se lanza cuando T0-T10 y B1 estan cerradas.** Panel de TRES revisores
independientes, en paralelo, agentes NUEVOS, ninguno de ellos implementador de
nada, instruidos para REFUTAR:

- **Lente 1 — fidelidad al artboard.** Los diez artboards contra el codigo,
  valor a valor. Busca especificamente: `leading-*` perdido por `cn()`
  (§1.8.1), `line-height` heredado de la preflight (§1.8.2), y hexes escritos a
  pelo donde habia token (§1.6).
- **Lente 2 — correccion.** Store, hooks, `useQueries` y la mutacion del paso 5.
  Busca: el `combine` sin memorizar, el `-1` de `employeePaletteIndex`, estado
  que sobrevive entre citas, y peticiones en vuelo que aterrizan tarde.
- **Lente 3 — regresion y calidad de los tests.** Que la reserva publica no se
  haya movido tras T2, que los tests reescritos prueben algo (mutar el codigo y
  ver si caen), y que ninguna afirmacion sobre precios use un espacio normal
  donde `formatCurrency` emite U+00A0 (§1.8.4).

Se descarta un hallazgo si la mayoria lo refuta.

- [ ] Volcar el plan y sus deudas a `E:\IdeaProjects\rivoo\tasks\todo.md`.
- [ ] Deudas a anotar explicitamente:
      1. el endpoint de rango de disponibilidad que cerraria D7 y D30
         (contadores "N huecos", el estado "Sin huecos" y los huecos ocupados
         tachados) en las DOS pantallas a la vez;
      2. `rescheduleId` sin artboard (D16): el panel de detalle enlaza una
         reprogramacion que el canvas nunca dibujo;
      3. el formulario de alta de cliente en linea, sin artboard (D24);
      4. **la reserva publica pinta "45 min" donde sus siete artboards dibujan
         "45min"** (D10). Desajuste REAL y preexistente; el arreglo es cambiar
         sus siete consumidores a `formatDurationTight`. No se hace aqui porque
         es un carril cerrado que este bloque no revisa;
      5. **el stepper se comprime entre 1024 y 1279** (§1.4): `gap-2`/`w-3` en
         vez de los 14px/26px del artboard, heredado de la reserva publica, y la
         comparacion visual de T10 corre a 1440 y no lo ve;
      6. **`totalVisits` y `lastVisitAt` no los escribe nadie** (§1.5.5, D31):
         el paso 4 pintara "0 visitas" en todas las filas y "Clientes
         recientes" ordena en la practica por fecha de alta. Arreglo exacto: un
         `POST /api/internal/clients/{id}/visit` en `ClientInternalController`
         que incremente `totalVisits` y ponga `lastVisitAt`, llamado desde
         `AppointmentService` al pasar una cita a `COMPLETED`, con degradacion
         elegante. Es una funcionalidad propia, no parte del asistente;
      7. **las flechas de navegacion por semanas del paso 3 no las dibuja
         ningun artboard** (D29). Se pintan porque sin ellas el horizonte de 30
         dias es inalcanzable; el canvas deberia recogerlas;
      8. la spec visual sin ejecutar (D25).
- [ ] Anotar tambien, para el bloque 6: `DetalleEmpleadoDesktop` y
      `FormularioEmpleadoDesktop` usan la forma CON espacio, que es la que
      `formatDuration` ya produce — no hay nada que hacer alli, pero conviene
      que quede escrito para que nadie "unifique" las dos funciones.
- [ ] Si el usuario corrige algo durante la ejecucion, anotar el patron y la
      regla en `tasks/lessons.md`.

---

## Execution Order

**Backend (`E:\IdeaProjects\rivoo`):**
```
B1  search + orden en GET /api/v1/clients     (sin dependencias)
```

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**
```
T0  tokens + formatDuration                   (sin dependencias)
T1  grupo (fullscreen) + traslado   ┐
T2  promocion de primitivas         ├ paralelas entre si, dependen de T0
T4  store + hooks + resumen         ┘
T3  shell + progreso + pagina                 depende de T1, T2
T5  paso 1   ┐
T6  paso 2   │
T7  paso 3   ├ paralelas entre si, dependen de T3 y T4
T8  paso 4   │
T9  paso 5   ┘
T10 spec visual + puertas globales            depende de T5..T9
T11 revision del bloque                       depende de todo
```

**Coordinacion:** B1 corre en paralelo con T0 (repos distintos). El frontend no
necesita esperar a B1 para nada salvo la comprobacion manual del buscador del
paso 4, que se hace en T10.

## Dependencias con otros specs/FRs

| Spec / bloque | Relacion | Implicacion |
|---|---|---|
| **Bloque 2** — shell de escritorio | **Prerrequisito, ya cerrado** | Fijo `(app)/layout.tsx` y `PageShell`; este bloque saca una ruta de ese chasis, no lo modifica |
| **Bloque 4** — detalle de cita | **Prerrequisito, ya cerrado** | Borro `appointments/[id]`, que es lo que permite mover el directorio entero (D1). Y dejo `lib/utils/avatar.ts`, que D14 reutiliza |
| **Reserva publica** (carril B) | **Complementario** | Comparte las dos primitivas de D4 y las dos limitaciones de D7. T2 la toca: no puede cambiar ni un pixel suyo |
| **Bloque 5** — Hoy | **Consumidor** | Enlaza a `/appointments/new` desde dos sitios; la URL no cambia |
| **Bloque 6** — Equipo y clientes | **Consumidor** | Necesitara la forma con espacio de `formatDuration` (D10), anotada como deuda |
| **Endpoint de rango de disponibilidad** | **Consumidor futuro** | Cerraria D7 aqui y en la reserva publica a la vez |
