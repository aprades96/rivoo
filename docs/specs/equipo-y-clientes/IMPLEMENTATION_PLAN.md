# Bloque 6 — Equipo y clientes · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: usa `executing-plans` para implementar
> este plan tarea a tarea. Los pasos usan casillas (`- [ ]`) para seguimiento.

**Objetivo:** reconstruir las seis pantallas de Equipo y Clientes contra sus doce
artboards, y anadir en el backend los tres datos sin los cuales la mitad de lo
dibujado seria imposible de pintar.

**Arquitectura:** un primitivo de tabla nuevo (`DataTable`) y un contenedor de
formulario que conmuta hoja/modal (`ResponsiveFormModal`) absorben lo que hoy es
CSS por pantalla; las cuatro rutas se reescriben sobre ellos con montaje
condicional en JS (nunca `lg:hidden`); tres tareas de backend abren los datos que
los artboards dan por existentes (empleados inactivos, contadores de visita,
historial de citas del cliente).

**Stack:** frontend Next.js 16 App Router · TypeScript · Tailwind v4 ·
Shadcn/UI + `@base-ui/react` · Zustand · React Query v5 · Vitest 4 · Playwright.
Backend Java 25 · Spring Boot 4 · hexagonal · MySQL 8.0 · Flyway · MapStruct ·
Testcontainers.

**Complejidad:** **Muy compleja** — dos repos, tres microservicios, seis
pantallas, migracion de esquema. Segun la tabla de triaje, el MOTOR de ejecucion
se propone al usuario (`executing-plans` vs `Workflow`); no se decide aqui.

**Repos:**
- Frontend: `E:\IdeaProjects\rivoo-frontend`, rama `master`
- Backend: `E:\IdeaProjects\rivoo`, rama `master`

**Linea base (medida sobre el arbol quieto, 2026-08-30):**
- Frontend: `e7210e9`, arbol limpio, `tsc` **0 errores**, **1021 tests en 92
  ficheros**, eslint 0 errores + 5 avisos, build OK.
- Backend: `bc44bf5`, arbol limpio.
- Los dos empujados a `origin/master`.

---

## COMO ESTA ESCRITO ESTE PLAN

**Cada hecho vive en UN solo sitio.** La seccion §1 son los datos verificados,
§2 las decisiones, §5 las tareas — y las tareas REFERENCIAN §1/§2 en vez de
repetir valores. Un implementador que solo reciba el texto de su tarea NO tiene
los valores: el brief tiene que incluir tambien las subsecciones de §1 y las
decisiones de §2 que su tarea cita.

---

# §1 · DATOS VERIFICADOS

Todo lo de esta seccion se comprobo leyendo el fichero. Cada afirmacion lleva
`fichero:linea`. Nada aqui es suposicion.

## §1.1 · Alcance: seis pantallas, doce artboards

El canvas (`design/canvas.json`) agrupa las pantallas en paginas. La pagina
`page-5` se llama "Equipo y clientes" y contiene ocho artboards; los cuatro que
faltan (los dos listados) viven en `page-1` "Pantallas". El bloque son los doce:

| # | Pantalla | Ruta | Artboard movil (390x844) | Artboard escritorio (1440x900) |
|---|---|---|---|---|
| 1 | Equipo (lista) | `/staff` | `design/Equipo.dc.html` | `design/EquipoDesktop.dc.html` |
| 2 | Detalle de empleado | `/staff/[id]` | `design/DetalleEmpleado.dc.html` | `design/DetalleEmpleadoDesktop.dc.html` |
| 3 | Alta/edicion de empleado | hoja/modal sobre 1 y 2 | `design/FormularioEmpleado.dc.html` | `design/FormularioEmpleadoDesktop.dc.html` |
| 4 | Clientes (lista) | `/clients` | `design/Clientes.dc.html` | `design/ClientesDesktop.dc.html` |
| 5 | Detalle de cliente | `/clients/[id]` | `design/DetalleCliente.dc.html` | `design/DetalleClienteDesktop.dc.html` |
| 6 | Alta/edicion de cliente | hoja/modal sobre 4 y 5 | `design/FormularioCliente.dc.html` | `design/FormularioClienteDesktop.dc.html` |

**FUERA de este bloque, aunque lo parezca:**
- `design/Horario*.dc.html` NO es el horario del empleado: dibuja el **horario de
  apertura del salon**, con descanso y "Copiar lunes al resto". Corresponde a
  `settings/business-hours`. Correccion de mapeado ya registrada en
  `tasks/todo.md`.
- El catalogo de **Servicios** (la segunda pestana de `/staff`). Ningun artboard
  dibuja su panel: ni `Equipo.dc.html:42` ni `EquipoDesktop.dc.html:93` pintan lo
  que hay al otro lado del segmentado. Este bloque conserva la pestana y su
  contenido actual **sin tocarlo**, y solo reconstruye el panel "Empleados".
  Ver §2 D6.

## §1.2 · Los artboards no usan tokens

Verificado por grep sobre los doce: **cero `{{...}}`, cero `var(--x)`, cero
custom properties.** Todo son hex literales e inline styles. Los seis ficheros de
cada familia repiten el mismo bloque `<style>`:

- `body`: fuente `'Schibsted Grotesk', system-ui, -apple-system, 'Segoe UI', sans-serif`,
  `background #FBF7F2`, `color #2A2320`, `-webkit-font-smoothing: antialiased`
- `* { box-sizing: border-box }`
- `a { color: #B4522F }`, `a:hover { color: #8F3F24 }`
- `.display { font-weight: 600; letter-spacing: -0.02em }`
- `.num { font-variant-numeric: tabular-nums }`

La correspondencia hex -> token del repo esta en §1.12.

## §1.3 · Inventario visual — Equipo (lista)

### Movil · `Equipo.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Cabecera | `Equipo.dc.html:35-36` | h **56**, `padding 0 16px`, `border-bottom 1px #E7DCCF`; titulo `.display` **21px/600**, ls `-0.02em`, texto `Equipo` |
| Cuerpo | `:39` | `flex column`, `gap 14px`, `padding 16px 16px 80px 16px` |
| Segmentado | `:27-29`, `:41-46` | `inline-grid repeat(2, minmax(0,1fr))`, `padding 3px`, `border 1px #E7DCCF`, `radius 999`, `bg #F0E7DC`; opcion activa h **32**, `padding 0 18px`, `radius 999`, `bg #FFFFFF`, `shadow 0 1px 2px rgba(42,35,32,.07)`, **13px/600** `#2A2320`; inactiva **13px/500** `#7A6A5F`. Textos `Empleados` / `Servicios` |
| Contador | `:49` | `.num` **13px** `#7A6A5F`, texto `5 empleados` |
| CTA anadir | `:50` | h **38**, `padding 0 14px`, `radius 8`, `bg #B4522F`, `#FFFFFF`, **13px/600**, `gap 7`; icono `plus` **16x16** `sw 2` `linecap round`; texto `Anadir` |
| Fila de empleado | `:24`, `:58` | `gap 12`, `padding 12`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF` |
| Avatar | `:25` | **40x40**, `radius 999`, **13px/600** |
| Nombre / puesto | `:59-60` | nombre **14px/600**; puesto **12px** `#7A6A5F` |
| Badge estado | `:26` | `padding 4px 10px`, `radius 999`, **11px/600**, `nowrap`. Activo: `bg #F0EAE3` `#7A6A5F`. Inactivo (`:100`): `border 1px #E7DCCF`, `bg #FFFFFF`, `#9A8A7E` |
| Fila inactiva | `:94-100` | nombre `#7A6A5F` (`:97`), puesto `#9A8A7E` (`:98`) — la fila NO cambia de fondo en movil |
| Pie de ayuda | `:104` | **11px** `#B8A99C`, `line-height 1.5`, texto `Toca un empleado para ver su horario y los servicios que realiza.` |

Datos dibujados: Laura Martinez/Estilista, Sofia Prat/Colorista, Marc
Oliva/Barbero, Laia Serra/Estilista junior, **Nil Bosch/Recepcion INACTIVO**.

### Escritorio · `EquipoDesktop.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Topbar | `:82-86` | h **72**, `padding 0 28px`; titulo `.display` **24px**; CTA h **38**, `padding 0 18px`, `radius 8`, `bg #B4522F`, **14px/600**, `gap 8`, icono `plus` 16, texto `Anadir empleado` |
| Contenido | `:90` | `column`, `gap 18px`, `padding 24px 28px` |
| Barra de filtros | `:92-97` | `space-between`, `max-width 1084px`; segmentado con opciones de h **30** (`:30-31`); contador `.num` **13px** `#7A6A5F`, texto `5 empleados &middot; 4 activos` |
| **Tabla** | `:100` | `max-width 1084px`, `border 1px #E7DCCF`, `radius 12`, `bg #FFFFFF`, `overflow hidden` |
| Rejilla de fila | `:28` | `grid-template-columns: minmax(0,1.5fr) 170px minmax(0,1.5fr) 128px 96px 20px`, `align-items center`, `gap 16px`, `padding 0 18px` |
| Cabecera de tabla | `:27`, `:102-108` | h **44**, `bg #F8F2EA`, `border-bottom 1px #E7DCCF`; celdas **11px/700**, `ls .08em`, `text-transform uppercase`, `#9A8A7E`. Textos: `Empleado`, `Puesto`, `Contacto`, `Color`, `Estado`, y una **sexta celda vacia** para el chevron |
| Fila de datos | `:111` | h **68** |
| Col 1 Empleado | `:112-115` | avatar **38x38** + nombre **14px/600**, `gap 12` |
| Col 2 Puesto | `:117` | **170px fijo**, **13px** `#7A6A5F` |
| Col 3 Contacto | `:118-121` | `column gap 2`: email **13px** color base; telefono `.num` **12px** `#9A8A7E` |
| Col 4 Color | `:122-124` | **128px fijo**: punto **12x12** `radius 999` + hex `.num` **12px** `#7A6A5F`, `gap 8` |
| Col 5 Estado | `:125` | **96px fijo**, badge con `justify-self: start` |
| Col 6 | `:126` | **20px fijo**, chevron `polyline 9 18 15 12 9 6` **17x17**, `#B8A99C`, `sw 2` |
| Separadores | `:129,149,169,189` | `height 1px`, `bg #EFE6DA`, **a sangre completa**, NO tras la ultima fila |
| Fila inactiva | `:191-201` | `background #FAF6F0`; email `#7A6A5F` (`:198`); telefono ausente -> `Sin telefono` **12px** `#B8A99C` sin `.num` (`:199`); color ausente -> `Por defecto` **12px** `#B8A99C`, **sin punto** (`:201`) |
| Nota al pie | `:207` | **12px** `#9A8A7E`, texto `Un empleado inactivo conserva su historial pero no recibe citas nuevas.` |

**No se dibuja hover en ninguna fila** (la unica regla `:hover` de los doce
ficheros es `a:hover` del `<style>` base, y no hay ningun `<a>` en el markup).

## §1.4 · Inventario visual — Detalle de empleado

### Movil · `DetalleEmpleado.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Cabecera con atras | `:40-44` | h **56**, `padding 0 14px 0 8px`, `gap 4`; zona de toque **44x44**, chevron `polyline 15 18 9 12 15 6` **20x20** `#2A2320` `sw 2`; titulo **15px/600** SIN `.display`, texto `Detalle empleado` |
| Cuerpo | `:47` | `column`, `gap 12px`, `padding 16px 16px 80px 16px` |
| Identidad | `:49-54` | avatar **56x56** `radius 999` `bg #F6E7E0` `#B4522F` **18px/600**; nombre **17px/600** sin `.display`; puesto **13px** `#7A6A5F`; badge `align-self flex-start` |
| Acciones | `:25`, `:56-62` | dos `.iconbtn` **36x36**, `radius 8`, `border 1px #E7DCCF`, `bg #FFFFFF`; editar = `pencil` **16x16** `#7A6A5F` `sw 1.75`; borrar = `trash` **16x16** `#A34434`, con `border-color #EDD6D0`. **Sin etiqueta de texto** |
| Contacto | `:66-74` | `column gap 6`, color `#7A6A5F`; iconos **14x14**; email **13px**; telefono `.num` **13px** |
| Color | `:75-81` | caja 14x14 con punto **10x10** `radius 999`; hex `.num` **13px** `#7A6A5F`; caption **12px** `#B8A99C` `Color identificativo` |
| Divisor | `:84` | `1px #EFE6DA`, `margin 2px 0` |
| Segmentado | `:86-91` | `Horarios` / `Servicios`, opciones h **32**, `padding 0 18px` |
| Fila de dia | `:29-34`, `:95` | h **52**, `gap 10`, `padding 0 10px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`. Toggle **42x24** `padding 3`, `radius 999`, ON `bg #B4522F` alineado a la derecha, OFF `bg #E0D3C4` a la izquierda; `knob` **18x18** `#FFFFFF`. `.day` **74px**, **13px/600**. `.time` h **36**, `padding 0 10px`, `border 1px #E7DCCF`, `radius 8`, **14px/500**, tabular. Separador `a` **12px** `#9A8A7E` |
| Dias | `:95-153` | Lunes-Jueves `09:00`-`20:00`; Viernes `09:00`-**`21:00`**; Sabado `09:00`-**`14:00`** |
| **Domingo cerrado** | `:155-159` | fila `bg #FAF6F0`, toggle **OFF**, `.day` `#9A8A7E`, **sin campos de hora**, texto **12px** `#B8A99C`: `Cerrado &middot; sin horas guardadas` |
| CTA | `:162` | h **46**, `radius 8`, `bg #B4522F`, **15px/600**, texto `Guardar horarios` |

**El panel "Servicios" del segmentado NO esta dibujado en movil.**

### Escritorio · `DetalleEmpleadoDesktop.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Topbar | `:90-105` | h **72**, `padding 0 28px 0 18px`; atras en caja **38x38** `radius 8` `border 1px #E7DCCF` `bg #FFFFFF`, chevron **18x18** `sw 2`; titulo `.display` **24px**, `padding-left 8px`, `Detalle empleado`; `.btn` (`:28`) h **38**, `padding 0 16px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`, **13px/500** — `Editar` con `pencil` 15, y `Desactivar` con `trash` 15, `border-color #EDD6D0`, `color #A34434` |
| Fila de tarjetas | `:109` | `flex`, `gap 24px`, `padding 24px 28px` |
| `.card` | `:25` | `padding 20px`, `border 1px #E7DCCF`, `radius 12`, `bg #FFFFFF` |
| **Tarjeta 1 — perfil** | `:111-144` | **width 300 fija**, `gap 16`. Avatar **64x64** **21px/600**; nombre `.display` **19px** `lh 1.15`; puesto 13px `#7A6A5F`; badge. Divisores `1px #EFE6DA` (`:121`, `:134`). Contacto `column gap 10`, iconos **15x15**, email 13px, telefono `.num` 13px. Color: `.lbl` **12px/600** `#7A6A5F` `Color identificativo`; muestra **28x28** `radius 8` `bg #B4522F` `border 1px rgba(42,35,32,.12)`; hex `.num` **13px**; ayuda **11px** `#9A8A7E` `lh 1.45`: `Colorea su avatar en las listas y en el filtro de la agenda.` |
| **Tarjeta 2 — horario** | `:146-231` | **width 386 fija**, `gap 12`. `.cardtitle` (`:26`) **15px/600** `Horario semanal` + meta **12px** `#9A8A7E` `Horas propias de Laura`. `.dayrow` h **44**; toggle **40x22**, `knob` **16x16**; `.day` **78px**; `.time` h **32**, **13px/500** |
| **Domingo recien activado** | `:214-222` | toggle **ON**, `border-color #EBD3C8`, `background #FAEFE9`, `.day` en color normal, dos `.time` con valor `--:--`, `color #B8A99C`, `border-color #DFB3A8` |
| Aviso del domingo | `:225-228` | `align-items flex-start`, `gap 8`, `padding 10px 12px`, `border 1px #E7DCCF`, `radius 8`, `bg #F5EEE6`; icono `info` **15x15** `#7A6A5F` `sw 1.75` con `margin-top 1px`; texto **12px** `#7A6A5F` `lh 1.45`: `El domingo llega sin horas guardadas. Al activarlo hay que escribirlas antes de guardar.` |
| CTA horarios | `:39`, `:230` | h **40**, `radius 8`, `bg #B4522F`, **14px/600**, `Guardar horarios` |
| **Tarjeta 3 — servicios** | `:233-301` | **width 372 fija**, `gap 12`. `.cardtitle` `Servicios que realiza` + contador `.num` **12px** `#9A8A7E` `4 de 6`. `.svc` (`:35`) `gap 12`, `padding 10px 12px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`; `.svc-on` (`:36`) `border 1px #B4522F`, `bg #FAEFE9`. `.box` (`:37`) **18x18** `radius 5` `border 1px #D8C9B8` `bg #FFFFFF`; `.box-on` (`:38`) **18x18** `radius 5` `bg #B4522F` con check `polyline 20 6 9 17 4 12` **12x12** `#FFFFFF` `sw 3`. Nombre **14px/500**; meta `.num` **12px** `#7A6A5F` |
| Servicios dibujados | `:241-296` | `Corte caballero` `30 min · 18,00 €` OFF · `Corte y secado` `45 min · 28,00 €` ON · `Color` `1h 30min · 55,00 €` ON · `Mechas` `2h · 78,00 €` ON · `Barba` `20 min · 12,00 €` OFF · `Recogido` `1h · 45,00 €` ON |
| Ayuda servicios | `:298` | **11px** `#9A8A7E` `lh 1.45`: `Solo aparecen los servicios activos del catalogo.` |
| CTA servicios | `:300` | `.cta .num` -> `Guardar servicios (4)` |

**En escritorio NO hay segmentado**: los servicios son una tercera tarjeta.

## §1.5 · Inventario visual — Formulario de empleado

| Componente | Referencia | Forma / valores |
|---|---|---|
| **Hoja movil** | `FormularioEmpleado.dc.html:89-91` | scrim `rgba(42,35,32,0.42)`; hoja `left/right/bottom 0`, `column gap 12`, `padding 10px 16px 20px 16px`, `radius 16px 16px 0 0`, `bg #FBF7F2`, `shadow 0 -8px 30px rgba(42,35,32,.2)`, **sin borde** |
| Grabber | `:94` | **36x4**, `radius 999`, `bg #D8C9B8`, centrado |
| Cabecera movil | `:97-100` | titulo `.display` **23px** `lh 1.1` `Nuevo empleado`; cerrar **32x32** `radius 8` **sin borde ni fondo**, icono `X` **18x18** `#7A6A5F` `sw 1.75` |
| Campo movil | `:30-34` | `.fld column gap 6`; `.lbl` **12px/600** `#7A6A5F`; `.in` h **44**, `padding 0 14px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`, **14px**; `.ph` `#9A8A7E`; `.help` **11px** `#9A8A7E` `lh 1.45` |
| Campos | `:104-136` | grid 2 col `gap 10`: `Nombre *`, `Apellidos *`; luego `Email *`, `Telefono`, `Puesto`, y `Color identificativo` con muestra **32x32** `radius 8` `border 1px rgba(42,35,32,.12)` + hex `.num` **12px** `#7A6A5F` |
| Divisor | `:138` | `1px` **`#E7DCCF`** (no `#EFE6DA`) |
| Cuenta de acceso | `:140-149` | checkbox marcado **18x18** `radius 5` `bg #B4522F` **sin borde**, check **12x12** `#FFFFFF` `sw 3`; icono `key` **14x14** `#9A8A7E` `sw 1.75`; etiqueta **14px** `Crear cuenta de acceso`; ayuda con `padding-left 27px`: `Permite al empleado iniciar sesion y gestionar sus citas` |
| Contrasena | `:151-155` | label `Contraseña temporal *` (**unico literal acentuado de los doce artboards**); input con `letter-spacing .18em` y 10 bullets; ayuda `El empleado podra cambiarla despues` |
| CTA movil | `:157` | h **48**, `radius 8`, `bg #B4522F`, **15px/600**, `Crear empleado` |
| **Modal escritorio** | `FormularioEmpleadoDesktop.dc.html:297-299` | scrim `rgba(42,35,32,0.34)`; modal centrado `translate(-50%,-50%)`, **width 512**, `padding 24px`, `border 1px #E7DCCF`, `radius 12`, `bg #FBF7F2`, `shadow 0 24px 60px rgba(42,35,32,.26)`, `gap 14` |
| Cabecera escritorio | `:301-304` | titulo `.display` **20px** `Editar empleado`; cerrar **32x32** `radius 8` **con `border 1px #E7DCCF` y `bg #FFFFFF`**, `X` **16x16** |
| Campo escritorio | `:20-23` | `.lbl` **12px/600** `#7A6A5F`; `.in` h **40**, `padding 0 12px`; `.fld gap 6`; grid 2 col `gap 12` |
| CTA escritorio | `:342` | h **42**, `margin-top 2px`, **14px/600**, `Guardar cambios` |
| Nota escritorio | `:344` | **11px** `#9A8A7E` `lh 1.45`: `La cuenta de acceso solo se crea al dar de alta al empleado.` |

**El artboard movil dibuja un ALTA y el de escritorio una EDICION.** No son dos
versiones de la misma pantalla: son los dos modos del mismo formulario. Por eso
el bloque de cuenta de acceso solo aparece en el movil (alta) y la nota
explicativa solo en el escritorio (edicion). Ver §2 D17.

## §1.6 · Inventario visual — Clientes

### Lista movil · `Clientes.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Cabecera | `:25-29` | h **56**, `padding 0 16px`, `border-bottom 1px #E7DCCF`; titulo `.display` **21px** con override `letter-spacing: -0.01em`; CTA h **38**, `padding 0 14px`, **13px/600**, `gap 7`, icono `plus` 16, texto `Anadir` |
| Cuerpo | `:33` | `column`, `gap 12px`, `padding 16px 16px 96px 16px` |
| Buscador | `:35-38` | h **44**, `padding 0 14px 0 40px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`; icono `search` **17x17** `#9A8A7E` `sw 1.75`, `absolute left 13px`; placeholder `Buscar clientes...` **14px** `#9A8A7E` |
| Contador | `:40` | **12px** `#7A6A5F`, **sin `.num`**, texto `248 clientes` |
| Fila | `:18-19`, `:44` | `gap 12`, `padding 12`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`; avatar **40x40** `radius 999` **13px/600** |
| Nombre / subtitulo | `:46-48` | nombre **14px/600**; subtitulo **12px** `#7A6A5F` con `.num`, **una sola linea** `612 345 678 · ana@mail.com`; sin contacto -> `Sin contacto` **12px** `#9A8A7E` sin `.num` (`:84`) |
| Bloque de visitas | `:50-52` | `column align-items flex-end`: numero `.display .num` **20px** `lh 1.1`; etiqueta `visitas` **10px** `#9A8A7E` |
| Chevron | — | **NO se dibuja en movil** |

### Lista escritorio · `ClientesDesktop.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Topbar | `:73-77` | h **72**, `padding 0 28px`; titulo `.display` **24px**; CTA h **38**, `padding 0 18px`, **14px/600**, `gap 8`, `Anadir cliente` |
| Contenido | `:81` | `column gap 18`, `padding 24px 28px` |
| Toolbar | `:83-88` | `space-between`, `max-width 1084px`; buscador **340x40**, `padding 0 14px 0 38px`, icono 17 en `left 12`, placeholder **14px**; contador `.num` **13px** `#7A6A5F` |
| **Tabla** | `:91` | `max-width 1084px`, `border 1px #E7DCCF`, `radius 12`, `bg #FFFFFF`, `overflow hidden` |
| Rejilla | `:22` | `grid-template-columns: minmax(0,1.5fr) minmax(0,1.5fr) 150px 96px 20px`, `gap 16px`, `padding 0 18px` |
| Cabecera | `:21`, `:93-98` | h **44**, `bg #F8F2EA`, `border-bottom 1px #E7DCCF`; celdas **11px/700** `ls .08em` uppercase `#9A8A7E`: `Cliente`, `Contacto`, `Ultima visita`, `Visitas` (con `justify-self: end`), y **quinta celda vacia** |
| Fila | `:101` | h **68**; avatar **38x38**; nombre **14px/600** |
| Col Contacto | `:106-109` | `column gap 2`: email **13px** color base + telefono **12px** `#9A8A7E` `.num`. Invertido cuando no hay email (`:139-140`, `:184-185`): telefono **13px** `.num` arriba + `Sin correo` **12px** `#9A8A7E`. Sin nada: `Sin contacto` **13px** `#9A8A7E` (`:154`) |
| Col Ultima visita | `:110` | **150px fijo**, `.num` **13px** `#7A6A5F`, formato `12 ago 2026` |
| Col Visitas | `:111` | **96px fijo**, `.display .num` **18px**, `justify-self: end` |
| Chevron | `:112` | **17x17** `#B8A99C` `sw 2` |
| Separadores | `:115,131,147,160,176` | `1px #EFE6DA`, **no tras la ultima fila** |
| Linea de paginacion | `:193` | **fuera** de la tarjeta, **12px** `#9A8A7E`, **sin `.num`**: `Mostrando 6 de 248 &middot; la lista pide 50 por pagina` |

**Ningun artboard de Clientes dibuja estado vacio** (ni lista sin clientes, ni
busqueda sin resultados), **ni filtros, ni orden, ni controles de paginacion.**

## §1.7 · Inventario visual — Detalle de cliente

### Movil · `DetalleCliente.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Cabecera | `:33-37` | h **56**, `padding 0 14px 0 8px`, `gap 4`; atras **44x44**, chevron **20x20** `sw 2`; titulo **15px/600** `Detalle cliente` (**generico, no el nombre**) |
| Cuerpo | `:40` | `column gap 13`, `padding 16px 16px 80px 16px` |
| Identidad | `:42-50` | avatar **56x56** **18px/700**; nombre `.display` **20px** `lh 1.15`; `Cliente desde 12/03/2023` `.num` **12px** `#9A8A7E`; editar **36x36** `radius 8` `border 1px #E7DCCF`, `pencil` **16x16** `#7A6A5F`, **sin texto** |
| KPIs | `:24`, `:53-62` | grid `repeat(2, minmax(0,1fr))` `gap 10`; `.kpi` `padding 12px 14px`, `border 1px #E7DCCF`, `radius 10`, `bg #FFFFFF`, `gap 2`. `Visitas` label **12px** `#7A6A5F` + `14` `.display .num` **30px** `lh 1.05`. `Ultima visita` + `05/08/2026` **21px** `lh 1.5` |
| Grupo de contacto | `:19-21`, `:64-80` | `.grp border 1px #E7DCCF`, `radius 10`, `bg #FFFFFF`, `overflow hidden`; `.item` h **56**, `padding 0 14px`, `gap 12`; `.sep` `1px #EFE6DA` con **`margin-left 44px`**; iconos **18x18** `sw 1.75` |
| Boton Llamar | `:73` | h **32**, `padding 0 12px`, `radius 8`, `border 1px #E7DCCF`, `bg #FFFFFF`, color **`#8F3F24`**, **12px/600** |
| Notas | `:76-79` | `align-items flex-start`, `padding 13px 14px`, icono `file-text` **18x18** con `margin-top 1px`, texto **13px** `#7A6A5F` `lh 1.45` |
| Historial | `:82-109` | rotulo `.sec` (`:18`) **11px/700** `ls .08em` uppercase `#9A8A7E` `Historial de citas`; tarjeta `.grp` con **3** filas `.hist` (`:26`) `padding 11px 14px`, `gap 12`; `.sep` con `margin-left` **14px** (override, `:92`, `:100`). Linea 1 `.num` **14px/600** `05 ago 2026 · Corte + Secado`; linea 2 `.num` **12px** `#7A6A5F` `Laura Martinez · 35,00 €`; badge `.badge` (`:25`) `padding 3px 9px`, `radius 999`, **10px/600** |
| Badges de cita | `:90,98,106` | `Completada` `bg #F0EAE3` `#7A6A5F`; `No asistio` `bg #F2D9D3` `#8A3125` |
| GDPR | `:111-127` | `padding 14`, `border 1px #E8D3A6`, `radius 10`, `bg #FFFCF5`, `gap 10`; icono `shield-alert` **16x16** `#8A5B12` `sw 1.75`; titulo **14px/600** `#8A5B12` `Proteccion de datos (GDPR)`; texto `Consentimiento dado: 12/03/2023` con `.num` en toda la linea, **12px** `#7A6A5F`; `.btn` (`:27`) h **40**, `flex-grow 1`, `radius 8`, `border 1px #E7DCCF`, `bg #FFFFFF`, **13px/600**, `gap 7` — `Exportar datos` con `download` 15 `#7A6A5F`; `Anonimizar` con `border-color #EDD6D0`, `color #A34434`, `shield-x` 15 |

**El movil muestra 3 de 14 citas sin ninguna via para ver el resto.**

### Escritorio · `DetalleClienteDesktop.dc.html`

| Componente | Referencia | Forma / valores |
|---|---|---|
| Topbar | `:78-96` | h **72**, `padding 0 28px 0 18px`, `gap 20`; atras **38x38** `radius 8`, chevron **19x19**; **titulo = nombre del cliente** `.display` **26px** `ls -0.015em`, alineado `baseline` con `Cliente desde 12/03/2023` `.num` **12px** `#9A8A7E`, `gap 12`; `.act` (`:26`) h **38**, `padding 0 16px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`, **13px/600**, `gap 7` con `pencil` 15 y texto `Editar`; CTA `Nueva cita` h **38**, `padding 0 16px`, `bg #B4522F`, **14px/600**, `gap 8`, `plus` **17x17** |
| Cuerpo | `:100` | `flex gap 24`, `padding 24px 28px`; columna izquierda **400 fija** `gap 16`; derecha `flex-grow 1` `gap 10` |
| Tarjeta perfil | `:104-129` | `padding 20`, `radius 12`; avatar **64x64** **21px/700**; nombre `.display` **21px** `lh 1.1`; **badge `Reserva online`** `align-self flex-start`, `padding 3px 10px`, `radius 999`, `bg #F5EEE6`, `#7A6A5F`, **11px/600**; `.sep` `1px #EFE6DA` **a ancho completo**; contacto `column gap 12`, iconos **17x17**, **sin boton Llamar** |
| KPIs | `:22`, `:131-140` | grid 2 col `gap 12`; `.kpi` `padding 14px 16px`, `radius 10`. `Visitas` **30px** `lh 1.05`; `Ultima visita` **22px** `lh 1.43` |
| GDPR | `:142-158` | `padding 18`, `radius 12`, `gap 12`; icono **17x17**; texto **12px** `lh 1.5`: `Consentimiento dado: 12/03/2023. La exportacion entrega un JSON con todos sus datos y su historial.` — **solo la fecha va en `.num`**; botones `.act` h **38** con `flex-grow 1` |
| Cabecerilla del historial | `:165` | `.sec` `Historial de citas` + `.num` **12px** `#9A8A7E` `14 citas &middot; 612,00 € facturados` |
| **Tabla de historial** | `:27`, `:169` | `grid-template-columns: 132px minmax(0,1fr) 150px 86px 108px`, `gap 12px`, `padding 0 18px`, filas h **58**. Cabecera: h **40**, `bg #FAF6F0`, `border-bottom 1px #EFE6DA`, `radius 12px 12px 0 0`, celdas `.sec` **forzadas a 10px**: `Fecha`, `Servicio`, `Profesional`, `Importe` (`text-align right`), `Estado` |
| Celdas | `:171-183` | Fecha `.num` **13px/600**; Servicio **14px**; Profesional **13px** `#7A6A5F`; Importe `.num` **14px/600** alineado a la derecha; Estado badge (`padding 3px 10px`, **11px/600**) con `justify-self: start` |
| Importe de una cita no cobrada | `:197` | el importe de la fila `No asistio` va en **`#9A8A7E`** |
| Badge `Cancelada` | `:230` | `bg #F7E2DD` `#A34434` |
| Footer de la tabla | `:233-237` | h **48**, `padding 0 18px`, `space-between`: `Mostrando 7 de 14 citas` `.num` **12px** `#9A8A7E`; enlace `Ver todas` `#B4522F` **13px/600**, `gap 6`, + chevron **15x15** `sw 2` |

## §1.8 · Inventario visual — Formulario de cliente

| Componente | Referencia | Forma / valores |
|---|---|---|
| Hoja movil | `FormularioCliente.dc.html:41-46` | scrim `rgba(42,35,32,0.42)`; hoja `column gap 16`, `padding 10px 16px 20px 16px`, `radius 16px 16px 0 0`, `bg #FBF7F2`, `shadow 0 -8px 30px`, **sin borde**; grabber **36x4** `#D8C9B8` |
| Cabecera movil | `:49-54` | titulo `.display` **23px** `lh 1.1` `Nuevo cliente`; cerrar **32x32** `radius 8` **con** `border 1px #E7DCCF` y `bg #FFFFFF`, `X` **15x15** `#7A6A5F` `sw 1.75` |
| Campos | `:18-22`, `:56-83` | `.lbl` **12px/500** color **`#5F534B`**; `.fld` h **42**, `padding 0 12px`, `border 1px #E7DCCF`, `radius 8`, `bg #FFFFFF`, **14px**, color de vacio `#B8A99C`; `.fldv` igual con `#2A2320`. Grid 2 col `gap 10`: `Nombre *`, `Apellidos *`; `Email` placeholder `email@ejemplo.com`; `Telefono`; `Notas` = caja h **64**, `padding 12`, `radius 8`, **14px**, `lh 1.45`, placeholder `Notas internas sobre el cliente` |
| CTA movil | `:85` | h **48**, `radius 8`, `bg #B4522F`, **15px/600**, `gap 8`, ancho completo, `Crear cliente` |
| Modal escritorio | `FormularioClienteDesktop.dc.html:162-171` | scrim `rgba(42,35,32,0.42)` **que tapa tambien la barra lateral**; modal **width 512**, `padding 22px 24px 24px 24px`, `border 1px #E7DCCF`, `radius 16px 16px 12px 12px`, `bg #FBF7F2`, `shadow 0 18px 48px rgba(42,35,32,.28)`, `gap 20`, **sin grabber**; titulo `.display` **23px** `lh 1.1` `Editar cliente` |
| Campos escritorio | `:175-198` | grid 2 col `gap 12`; los cinco campos con valor real; `Notas` sin `color` explicito (hereda `#2A2320`) |
| CTA escritorio | `:202` | h **48**, `radius 8`, **15px/600**, ancho completo, `Guardar cambios` |

**No hay boton secundario ni Cancelar en ninguno de los dos anchos, y no se
dibuja ningun estado de error ni de validacion.**

## §1.9 · Estado del codigo — frontend

### `/staff` — `src/app/(app)/staff/page.tsx` (192 lineas)

- **Un unico layout para los dos anchos.** No importa `useMediaQuery`; la unica
  bifurcacion por ancho que recibe viene de `PageShell` (`page-shell.tsx:115`).
- **Dos `lg:hidden`, contra la regla del repo**: `:110-120` (boton "Anadir" de
  empleados), `:148-158` (el de servicios). En jsdom quedan montados a la vez
  que `addAction`, y `mockMatchMedia(true)` no los quita porque son CSS.
- `:22-34` `<Suspense>` obligado por `useSearchParams`.
- `:48` `tab` derivado de `?tab=`; `:90-101` `<Tabs>` **controlado** por la query
  con `router.replace(..., {scroll:false})`. El primitivo es `@base-ui/react/tabs`.
- `:70-86` `addAction` = UN solo boton que cambia de etiqueta y handler segun
  pestana; `:89` `mobileActions={null}`.
- `:104-139` panel de empleados: contador `{employees.length} empleado(s)`,
  `LoadingSkeleton count={4}`, `EmptyState title="Sin empleados"`, `.map` de
  `EmployeeCard` con `onTap` -> `router.push('/staff/'+id)` (`:58`).
- **Asimetria**: tocar un empleado navega (`:58-60`), tocar un servicio abre una
  hoja (`:62-65`).
- `EmployeeCard` es `<Card onClick>` **sin `role="button"`, sin `tabIndex`, sin
  `onKeyDown`** (`employee-card.tsx:14-17`): no accesible por teclado e invisible
  para `getByRole("button")`.
- **`/staff` no comprueba `isOwner`**: cualquier rol ve "Anadir empleado".
- No hay buscador, ni filtro por estado, ni orden, ni paginacion, ni contador de
  activos.

### `/staff/[id]` — `src/app/(app)/staff/[id]/page.tsx` (281 lineas)

- Un unico layout salvo `PageShell` y el `lg:hidden` de `:182`.
- `:47-51` `useQuery(["employee", id])`; `:53-61` `useQuery(["employee-working-hours", id])`
  con `refetch` e `isError`; `:72` `workingHoursNotReady`; `:80` `workingHoursFailed`.
- `:82` `useEmployeeServices(id)` **sin guarda equivalente**.
- `:151-203` perfil con `backgroundColor: employee.colorHex + "20"` en linea
  (`:155`), duplicado literal en `employee-card.tsx:24`.
- `:206-209` telefono **en crudo**, sin `formatPhone`.
- `:214-247` `<Tabs defaultValue="hours">` — **NO ligado a la URL**, al contrario
  que `/staff`: la pestana no es enlazable ni sobrevive a una recarga.
- `:257-278` `Dialog` de desactivacion.
- `:104-112` `deleteMutation` invalida **solo** `["employees"]`.

### `/clients` — `src/app/(app)/clients/page.tsx` (101 lineas)

- Un unico arbol. `:39-53` `PageShell` con `actions` "Anadir cliente" y
  `mobileActions` "Anadir".
- `:56-64` buscador con `<Search>` absoluto + `<Input placeholder="Buscar clientes...">`.
- `:22-23` `useState` + **`useDeferredValue`**, que **NO es un debounce**: es
  prioridad de render. En cada pausa de tecleo el valor diferido se actualiza y
  React Query lanza peticion. Y como la clave nueva no tiene datos, `isLoading`
  sube y `:67` desmonta la lista para montar `LoadingSkeleton` **en cada letra**.
  El debounce real de 250 ms ya existe a dos ficheros de distancia:
  `src/hooks/use-clients.ts:14-25`.
- `:80-82` contador `totalElements` sobre una lista truncada a `size: 50`
  (`:30`): **la pantalla anuncia un total que no puede mostrar**.
- `:67-91` tres ramas; `EmptyState` sin `action` en el vacio inicial.
- **Ninguna de las dos rutas de Clientes tiene fichero de test.**

### `/clients/[id]` — `src/app/(app)/clients/[id]/page.tsx` (151 lineas)

- **Dos `lg:hidden`**: `:69` (duplicado del `titleAdjacent` de `:48`) y `:76-86`
  (duplicado del `actions` de `:51-54`, con comentario que lo justifica en
  `:71-75`). En jsdom hay **dos** botones "Editar" a la vez.
- `:90-103` grid **`grid-cols-2` sin `lg:`** -> en escritorio dos tarjetas
  estiradas a 1084px.
- `:106-125` contacto; telefono **en crudo**, sin `formatPhone` (`:116`).
- `:130-140` `<GdprPanel>` solo si `isOwner`.
- `:31-37` **`isLoading || !client` colapsados**: un 404/500 pinta esqueleto
  **para siempre**. No hay rama de error.
- `:40`, `:98` y `gdpr-panel.tsx:66` hacen `toLocaleDateString("es-ES")` a mano
  -> `27/8/2026`, incompatible con el `27 ago 2026` del resto de la app, y sin
  zona fijada.
- **No hay Historial de citas**, que los dos artboards dibujan.

### Formularios

- `employee-form.tsx:126-127` y `client-form.tsx:107-108`: `<Sheet side="bottom">`
  **en los dos anchos**. No hay variante de escritorio.
- Los dos usan `useState` plano y el patron "derived state during render" con
  `syncKey` (`employee-form.tsx:61-66`, `client-form.tsx:56-61`).
  **No hay `react-hook-form` ni `zod` en el repo** (grep vacio en `src` y
  `package.json`).
- Validacion imperativa duplicada en dos sitios (`employee-form.tsx:93-94` y
  `:122-123`). No valida formato de email; no hay `<form>`, el boton es un
  `<Button onClick>` suelto (`:204`).
- Los dos mandan `undefined` para strings vacios (`employee-form.tsx:102-104`,
  `client-form.tsx:93-95`): en un `PUT` eso significa "no tocar", **asi que un
  email o un telefono ya guardado no se puede vaciar desde el formulario**.
- Los cuatro `onError` muestran mensaje generico y **tiran el `detail` del
  ProblemDetail** que `apiFetch` si propaga (`src/lib/api/client.ts:96`).
- `employee-form.tsx:159-164` usa `<input type="color">` nativo crudo con
  `value={form.colorHex || "#3B82F6"}` — **hex azul literal**, ademas de que
  `form.colorHex` sigue vacio hasta que el usuario lo toca, asi que la UI muestra
  azul y guarda `undefined`.
- `employee-form.tsx:173-178` y `service-assignment.tsx:64-69` usan
  `<input type="checkbox">` crudo existiendo `src/components/ui/checkbox.tsx`.
- `client-form.tsx` **no expone `gender` ni `dateOfBirth`**, que si estan en
  `CreateClientRequest` (`src/types/client.ts:23-24`).
- `useCreateClient()` (`use-clients.ts:53-64`) es **codigo muerto**: ningun
  consumidor lo usa.

### Datos y paginacion

| Hook / llamada | Endpoint | Parametros |
|---|---|---|
| `useEmployees()` `use-staff.ts:11-19` | `GET /api/v1/staff/employees` | **ninguno** -> Spring devuelve **20** |
| `useServices()` `use-staff.ts:21-29` | `GET /api/v1/services` | **ninguno** -> **20** |
| `useQuery` inline `clients/page.tsx:26-34` | `GET /api/v1/clients` | `search?`, `page:0`, `size:50` |
| `useClients(search)` `use-clients.ts:45-50` | `GET /api/v1/clients` | `search`, `page:0`, `size:10`, `staleTime 10s` |

- El contador de `staff/page.tsx:107` usa `employees.length`, o sea la longitud
  de la **pagina**: con 30 empleados dira "20 empleados".
- `use-clients.ts:38-44` documenta que metio `size: 10` en la clave para no
  colisionar con `/clients`, que pide 50. **El arreglo esta solo en un lado**: la
  clave de `clients/page.tsx:27` sigue sin `size`. Y `client-form.tsx:67,78`
  invalidan `["clients"]` a secas, que barre las dos.

### Tests: lo que cubren de verdad

| Fichero | `it(` | `mockMatchMedia(true)` |
|---|---|---|
| `src/app/(app)/staff/page.test.tsx` | 4 | 1 |
| `src/app/(app)/staff/[id]/page.test.tsx` | 5 | 1 |
| `src/components/staff/employee-card.test.tsx` | 5 | 0 |
| `src/components/staff/employee-form.test.tsx` | 4 | 0 |
| `src/components/staff/service-assignment.test.tsx` | **2** | 0 |
| `src/components/staff/working-hours-editor.test.tsx` | 10 | 0 |
| `src/components/clients/client-card.test.tsx` | 5 | 0 |
| `src/components/clients/client-form.test.tsx` | **4** | 0 |
| `src/hooks/use-clients.test.tsx` | 5 | 0 |
| `src/hooks/use-staff.test.tsx` | 9 | 0 |
| `src/components/clients/gdpr-panel.tsx` | **0** | — |
| `src/app/(app)/clients/page.tsx` | **0** | — |
| `src/app/(app)/clients/[id]/page.tsx` | **0** | — |
| `src/lib/api/clients.ts` | **0** | — |

Agujeros que importan para este bloque:
- `employee-form.test.tsx` y `client-form.test.tsx` prueban **solo la
  re-sincronizacion de estado**. Las mutaciones **nunca se ejecutan**: ningun
  test mockea `staffApi`/`clientsApi` en esos ficheros. Por eso el fallo de
  §1.11.1 lleva ahi sin que nadie lo vea.
- `service-assignment.test.tsx` no cubre el estado vacio, ni el filtro
  `isActive`, ni el `disabled`, ni la llamada a `onSave`.
- `staff/page.test.tsx` **no prueba nada de la lista**: mockea
  `useEmployees`/`useServices` (`:45-48`) y solo verifica el titulo y el
  comportamiento de las pestanas.
- `useEmployees`, `useServices` y `useEmployeeServices` **no tienen ni un test**.
- `gdpr-panel.tsx` es destructivo e irreversible y tiene **cero** tests.

## §1.10 · Estado del codigo — backend

### Empleados · `EmployeeController.java`, base `/api/v1/staff/employees`

| Verbo | Ruta | Firma real | Roles | Linea |
|---|---|---|---|---|
| GET | `` | **solo `Pageable`** | `SALON_OWNER`, `EMPLOYEE` | `:58-64` |
| GET | `/{id}` | `id` | ambos | `:66-72` |
| POST | `` | body | `SALON_OWNER` | `:49-56` |
| PUT | `/{id}` | `id` + body | `SALON_OWNER` | `:74-82` |
| DELETE | `/{id}` | `id` -> 204 | `SALON_OWNER` | `:84-91` |
| GET/PUT | `/{id}/working-hours` | `id` | | `:93-110` |
| GET/POST | `/{id}/services` | `id` | | `:112-129` |

- **Cero `@RequestParam` en todo el controller** (grep confirmado: `staff-service/src/main`
  no tiene ni uno). Cualquier `search`/`q`/`status`/`active` que mande el
  frontend se **descarta en silencio**.
- `EmployeeJpaRepository.java:19` es `findByActiveTrue(Pageable)`: el
  `WHERE active = true` esta **cableado**. El servidor **nunca** devuelve un
  inactivo por el listado (`getById` si, porque `findByExternalId` no filtra).
- `page=0`, `size=20`, **sin `sort` por defecto** (no hay
  `spring.data.web.pageable.*` en ningun `.yml`): el orden es el que devuelva
  MySQL -> **paginacion no determinista**.
- `sort` **si se honra** (derived query). Una propiedad inexistente revienta con
  `PropertyReferenceException` -> **500**.
- `DELETE` es `active=false` (`EmployeeService.java:178-187`). **No hay
  reactivacion**: `UpdateEmployeeRequest` no tiene campo `active`. **No comprueba
  citas futuras** pese a lo que dice `staff-service/CLAUDE.md`. **No toca
  Keycloak**: el empleado desactivado puede seguir entrando.
- **Sabado y domingo nacen con horas nulas.** `EmployeeService.java:305-306`
  escribe `day <= 5 ? LocalTime.of(9,0) : null` al crear el empleado, asi que dos
  de las siete filas de horario salen con `openTime: null, closeTime: null`
  mientras `src/types/employee.ts:14-15` las declara `string` no-nulo. **El tipo
  miente.** (Lo contrario tambien es cierto y cierra el caso: un dia con
  `open: true` y horas nulas **no puede existir** en la base de datos, porque
  `EmployeeWorkingHours.validate()` lo rechaza — §1.11.6 punto 3.)
- `EmployeeResponse` (`:5-17`): `email`, `phone`, `jobTitle` son **NULLABLE**;
  `colorHex` lleva default `#3B82F6` (`EmployeeService.java:81`); `isActive` sale
  literalmente como `"isActive"` (test de regresion en
  `EmployeeResponseJsonTest.java:32-42`).

### Clientes · `ClientController.java`, base `/api/v1/clients`

| Verbo | Ruta | Firma real | Roles | Linea |
|---|---|---|---|---|
| GET | `` | `@RequestParam(required=false) String search`, `Pageable` | ambos | `:43-50` |
| POST | `` | body | `SALON_OWNER` | `:52-59` |
| GET | `/{id}` | `id` | ambos | `:61-67` |
| PUT | `/{id}` | `id` + body | `SALON_OWNER` | `:69-76` |
| DELETE | `/{id}` | `id` -> 204 (anonimiza) | `SALON_OWNER` | `:78-84` |
| GET | `/{id}/export` | `id` | `SALON_OWNER` | `:86-92` |

- `search` **SI se honra** (`ClientJpaRepository.java:39-47`, JPQL parametrizada):
  busca en `firstName`, `lastName`, `phone`, `email` con `LIKE '%x%'`
  insensible. **No busca en `notes` ni por nombre completo**: `search=Ana Lopez`
  no encuentra a "Ana Lopez" porque compara los dos campos por separado.
- **`sort` se DESCARTA a proposito**: `ClientService.java:93` reconstruye
  `PageRequest.of(page, size)` sin `Sort`. Es el mismo fallo silencioso de las
  citas: llamada valida, respuesta 200, orden ignorado.
- Orden fijo `lastVisitAt DESC, createdAt DESC` (`ClientJpaRepository.java:46`).
- El `WHERE` **NO incluye `active = true`**: los anonimizados aparecen en el
  listado como `"ANONYMIZED CLIENT"` con `active:false`, y matchean
  `search=anonymized`.
- `ClientResponse` (`:5-19`) **no expone `gdprConsentAt`** (solo sale en
  `/export`) ni `gdprAnonymizedAt`. **No existe `dateOfBirth`** ni en el DTO, ni
  en `CreateClientRequest`, ni en la tabla (`V2__create_clients_schema.sql`),
  pese a que `src/types/client.ts:8` lo declara.
- `DELETE` = anonimizado GDPR (`Client.java:44-53`). **NO cancela las citas
  futuras**, en contra de lo que afirma `client-service/CLAUDE.md`.

### Los dos campos muertos

- **`totalVisits`**: `ClientService.java:67` y `:206` lo fijan a `0` en creacion
  y **nadie lo incrementa nunca**. Grep de `totalVisits|total_visits` en todo el
  repo: solo el DTO, el modelo, la entidad, la migracion y esos dos `.totalVisits(0)`.
- **`lastVisitAt`**: el unico `setLastVisitAt` del repo esta en un test
  (`ClientRepositoryIntegrationTest.java:112`). Siempre `null` en produccion —
  **y es la primera clave del `ORDER BY` del listado, que por tanto es inerte**.

### Historial de citas: existe, pero solo dentro del export

`GET /api/v1/clients/{id}/export` -> `ClientService.export` (`:149-164`) ->
`AppointmentServiceAdapter.getClientAppointments` (`:32-57`) ->
`GET /api/internal/admin/appointments/by-client/{clientId}?tenantId=`
(`AppointmentInternalController.java:34-40` -> `AppointmentService.java:184-185`
-> `AppointmentJpaRepository.findByClientIdAndTenantId` `:85`).

Devuelve `List<ClientAppointmentDto>{id, serviceName, employeeName, startTime, endTime, status}`.
Caveats: **sin `ORDER BY`**, **sin paginar**, y si appointment-service falla el
adapter **se traga la excepcion y devuelve lista vacia**
(`AppointmentServiceAdapter.java:52-55` y otra vez en `ClientService.java:156-161`).

### Transicion de estado

`AppointmentService.updateStatus` (`:190-210`), expuesto en
`PUT /api/v1/appointments/{id}/status` (`AppointmentController.java:88-96`).
La maquina de estados (`AppointmentStatus.java:14-26`) declara
`COMPLETED, CANCELLED, NO_SHOW` como **terminales**: una vez en `COMPLETED` no
se sale. **Eso da idempotencia gratis** a cualquier efecto colateral que se
enganche a esa transicion.

`appointment-service` ya tiene puerto de salida hacia clientes:
`ClientServicePort` (`domain/port/out/ClientServicePort.java`), hoy con
`getClient` y `findOrCreateClient`.

## §1.11 · Fallos de produccion que este bloque cierra

### §1.11.1 · Asignar servicios a un empleado devuelve 400 · **CONFIRMADO**

`src/app/(app)/staff/[id]/page.tsx:96` envia:

```ts
staffApi.assignServices(id, { serviceIds }, accessToken!)
```

`AssignServicesRequest.java` exige:

```java
public record AssignServicesRequest(
        @NotEmpty @Valid List<ServiceAssignment> services
) {
    public record ServiceAssignment(
            @NotBlank String serviceId,
            Integer customDuration,
            BigDecimal customPrice
    ) {}
}
```

El cuerpo que se manda no tiene `services`, asi que `@NotEmpty` falla y el
endpoint responde **400**. **"Guardar servicios" no funciona hoy.** Nadie lo vio
porque `service-assignment.test.tsx` solo prueba la adopcion de estado y la
mutacion **nunca se ejecuta en ningun test**.

Y el tipo del frontend miente en las dos direcciones
(`src/types/employee.ts:51-61`):

| Campo declarado en el frontend | Realidad del backend |
|---|---|
| `employeeId: string` | **no existe** en `EmployeeServiceResponse.java` |
| `customDurationMinutes: number \| null` | se llama `customDuration` |
| — | faltan `serviceName`, `effectiveDuration`, `effectivePrice` |

Sobrevive porque `service-assignment.tsx:22` solo lee `serviceId`.

### §1.11.2 · Se pueden perder asignaciones de servicios con un clic — **perdida PARCIAL, no total**

`staff/[id]/page.tsx:82` obtiene `employeeServices` y `:240-245` monta
`ServiceAssignment` **incondicionalmente**. Mientras el GET esta en vuelo (o si
fallo), `assignedServices === undefined` -> `selectedIds` vacio
(`service-assignment.tsx:21-23`) -> el editor pinta **todo desmarcado** aunque el
empleado tenga seis servicios asignados, y el boton esta **habilitado**
(`:81-88`; `isSaving` solo refleja la mutacion). `EmployeeService.java:259` hace
`deleteByEmployeeId` antes de recrear, asi que lo que se guarde **sustituye** a lo
que habia.

**Precision importante, porque cambia el arreglo**: el borrado TOTAL **no es
alcanzable**. Con `selectedIds` vacio el cuerpo seria `{ services: [] }`, y
`AssignServicesRequest.java:10` lleva `@NotEmpty`: Spring responde **400 antes**
de llegar al `deleteByEmployeeId`. Y `assignServices` es `@Transactional`
(`EmployeeService.java:252`), asi que un fallo a mitad tampoco deja al empleado
sin nada.

**Lo alcanzable es la perdida parcial**: con el GET en vuelo, el usuario marca
**un** servicio y guarda -> el cuerpo ya no esta vacio, pasa la validacion, y los
otros cinco desaparecen.

Es la misma clase de fallo que `:63-72` documenta y arregla **para horarios**;
para servicios no se arreglo y no hay test. Hoy el 400 de §1.11.1 lo enmascara
del todo; **al arreglar el contrato, la perdida parcial se vuelve alcanzable**,
por eso los dos van en el mismo bloque de trabajo (D16).

### §1.11.5 · Con `@NotEmpty` vivo, la UI no puede desasignar el ultimo servicio

Consecuencia directa de lo anterior, y **el plan tiene que decidirlo, no
heredarlo**: `AssignServicesRequest.java:10` exige `@NotEmpty`, asi que
"Guardar servicios (0)" — desmarcar todo y guardar — es una operacion **imposible
por contrato**. Hoy nadie lo nota porque el endpoint devuelve 400 siempre. En
cuanto el contrato se arregle, el unico caso que seguira fallando sera
precisamente el legitimo. Ver D16b.

### §1.11.6 · Seis afirmaciones que un plan anterior habria dado por buenas y son FALSAS

Comprobadas contra el codigo, y escritas aqui porque cada una **anulaba una
decision**:

1. **`useEmployeeServices` SI expone `isError`.** `use-staff.ts:95-98` es un
   `useQuery` plano y `service-step.tsx:75` ya lo destructura. Lo unico que falta
   es destructurarlo en `staff/[id]/page.tsx:82`.
2. **`gdpr-panel.tsx` YA deshabilita durante la mutacion**: `:75`
   (`disabled={exportMutation.isPending}`) y `:111`
   (`disabled={anonymizeMutation.isPending}`). Lo que falta de verdad es que
   `Cancelar` **no** lleva `disabled` y que el `onOpenChange` del dialogo no se
   bloquea mientras corre.
3. **El backend ya rechaza un dia abierto sin horas.**
   `EmployeeWorkingHours.validate()` lanza
   `BusinessValidationException.clientSafe("Open days must have openTime and closeTime")`,
   dentro de `@Transactional` (`EmployeeService.java:219`). **Nada se corrompe**;
   el usuario recibe un 400. Corolario: **la base de datos no puede contener una
   fila con `open = true` y horas nulas**, asi que ese caso de carga es imposible.
4. **`ClientAppointmentDto.java` vive en `client-service`**
   (`client-service/src/main/java/com/rivoo/client/application/dto/`), **no** en
   appointment-service.
5. **`servicePrice` ya existe en appointment-service**:
   `AppointmentInternalResponse.java:11`, `AppointmentResponse.java:16` y
   `Appointment.java:36`. Lo que falta no es crear el dato, es **transportarlo**
   hasta `ClientAppointmentDto`.
6. **`WorkingHoursEditor` no es solo de `/staff/[id]`.** Lo montan tambien
   `src/app/(app)/settings/business-hours/page.tsx:110` y
   `src/app/(onboarding)/business-hours/page.tsx:109`, los dos con
   `showSaveButton={false}` y guardando por el handle imperativo
   `editorRef.save()` (`working-hours-editor.tsx:96-98`). **Cualquier freno que
   se ponga solo en el boton interno no cubre esas dos pantallas.**

### §1.11.7 · Once ficheros de test se romperan si nadie los declara

`tsc` cae en cuanto cambien dos tipos, y **ninguno de estos ficheros aparecia en
§3**:

`dateOfBirth` (que D32 borra) esta en siete fixtures:
`src/components/appointments/wizard/client-step.test.tsx:49`,
`confirmation-step.test.tsx:96`, `wizard-summary.test.ts:49`,
`src/lib/stores/wizard-store.test.ts:36`,
`src/components/clients/client-card.test.tsx:13`,
`src/components/clients/client-form.test.tsx:18`,
`src/hooks/use-clients.test.tsx:32`.

La forma vieja de `EmployeeServiceResponse` (que D16 reescribe) esta en
`src/components/appointments/wizard/datetime-step.test.tsx:82`,
`service-step.test.tsx:100`, **`src/components/staff/service-assignment.test.tsx:33-39`**
y `src/hooks/use-staff.test.tsx:152-158`.

**`service-assignment.test.tsx` es el peligroso**: por contenido parece de T9
(ola 3), pero construye un tipo que T4 reescribe en la **ola 1**. Si se queda en
T9, el arbol esta en rojo durante dos olas. Es de T4.

Con `git commit -o <sus rutas>` (§4.2), una tarea **no puede** tocar lo que no
tiene declarado. Estan asignados en §3 a T4, que es quien cambia los tipos.

### §1.11.3 · La ficha de cliente muestra esqueleto para siempre ante un error

`clients/[id]/page.tsx:31-37` colapsa `isLoading || !client` en el mismo camino.

### §1.11.4 · El buscador de clientes lanza una peticion por pausa de tecleo

`clients/page.tsx:23` usa `useDeferredValue` como si fuera un debounce, y
desmonta la lista en cada letra. Ver §1.9.

## §1.12 · Tokens: el mapa hex -> token

`src/app/globals.css` ya tiene casi todo. **Cada token nuevo va en DOS sitios**:
declarado en `:root` y mapeado en `@theme inline`. Si falta el mapeo, la utilidad
no se genera y no hay ni error ni aviso (trampa documentada en `AGENTS.md:44-51`).

| Hex del artboard | Token existente | Declaracion |
|---|---|---|
| `#FBF7F2` | `--background` | `globals.css:118` |
| `#FFFFFF` | `--card` | `:120` |
| `#2A2320` | `--foreground` | `:119` |
| `#B4522F` | `--primary` | `:124` |
| `#8F3F24` | `--primary-pressed` | `:163` |
| `#E7DCCF` | `--border` | `:133` |
| `#EFE6DA` | `--hairline` | `:153` |
| `#F8F2EA` | `--sidebar` | `:143` |
| `#FAF6F0` | `--muted-subtle` | `:157` |
| `#F5EEE6` | `--muted` / `--secondary` | `:128` / `:126` |
| `#F6E7E0` | `--accent` | `:130` |
| `#B8A99C` | `--text-subtle` | `:155` |
| `#9A8A7E` | `--muted-foreground-2` | `:156` |
| `#7A6A5F` | `--muted-foreground` | `:129` |
| `#5F534B` | `--label` | `:158` |
| `#6B5C53` | `--nav-foreground` | `:151` |
| `#E0D3C4` | `--switch-off` | `:154` |
| `#D8C9B8` | `--grabber` / `--border-dashed` | `:175` / `:173` |
| `#A34434` | `--destructive` | `:132` |
| `#EDD6D0` | `--destructive-border` | `:167` |
| `#E8D3A6` | `--warning-border` | `:165` |
| `#FFFCF5` | `--warning-soft` | `:171` |
| `#FAEFE9` | `--surface-now` | `:178` |
| `#EBD3C8` | `--surface-now-border` | `:179` |
| `#F0EAE3` / `#7A6A5F` | `--color-status-completed-bg` / `-text` | `:19-20` |
| `#F2D9D3` / `#8A3125` | `--color-status-no-show-bg` / `-text` | `:23-24` |
| `#F7E2DD` / `#A34434` | `--color-status-cancelled-bg` / `-text` | `:21-22` |
| `#8A5B12` | `--color-status-pending-text` | `:14` |
| `#F0EAE3` | `--avatar-muted` | `:177` |
| avatares `#E8EEE7/#5C7A5E`, `#E4EAEE/#4A6274`, `#F5EDDD/#A8762F` | resueltos por `src/lib/utils/avatar.ts` | — |

**Faltan exactamente dos** (ver T0):

| Hex | Donde | Token propuesto |
|---|---|---|
| `#F0E7DC` | fondo del carril del segmentado, `Equipo.dc.html:27` | `--segmented-track` |
| `#DFB3A8` | borde del campo de hora vacio del domingo recien activado, `DetalleEmpleadoDesktop.dc.html:218,220` — **y borde de error de validacion** en `Estilo.dc.html:148` ("Email no valido") | `--input-border-attention` |

## §1.13 · Trampas del repo — leer `AGENTS.md` ENTERO antes de tocar nada

1. **Los tests de React Query pueden pasar sin probar nada** (`AGENTS.md:7-27`).
   `notifyManager` notifica en un **macrotask**; `await act(async () => {})`
   drena microtareas y no lo alcanza. En cualquier test que simule un refetch,
   primero `await findBy*` sobre algo que el componente **no** posee.
2. **Todo test corre como MOVIL salvo que diga lo contrario** (`AGENTS.md:29-42`).
   `src/test/setup.ts` devuelve `matches:false` **siempre**. Cada asercion de
   escritorio necesita su `mockMatchMedia(true)` **y** un `afterEach` que lo
   restaure. La cobertura se cuenta **por rama**, no por fichero.
3. **Un token de Tailwind v4 que falte en `@theme inline` desaparece sin decir
   nada** (`AGENTS.md:44-51`).
4. **`tailwind-merge` borra en silencio un `leading-*` escrito ANTES de un
   `text-[Npx]`** (`AGENTS.md:53-61`). Medido:
   `twMerge("text-sm leading-tight font-semibold text-[15px]")` -> `"font-semibold text-[15px]"`.
   Como la preflight impone `line-height: 1.5` y los artboards dibujan ~1.25,
   **cada `text-[Npx]` necesita su `leading-*` propio, escrito DESPUES**.
5. **`vitest.config.ts` fija `TZ` a proposito — no quitarlo** (`AGENTS.md:63-71`).
6. **`This is NOT the Next.js you know`** (`AGENTS.md:1-5`): leer
   `node_modules/next/dist/docs/` antes de escribir codigo de Next.
7. Testing Library busca `data-testid`, **no `data-slot`**.
8. El primitivo `Card` (`ui/card.tsx`) fuerza `gap-4 rounded-xl py-4 ring-1
   ring-foreground/10`; **una clase de borde NO quita el ring** (grupos distintos
   de tailwind-merge).
9. `formatCurrency` emite **U+00A0** entre numero y simbolo.
10. **NO unificar `formatDuration` (con espacio) y `formatDurationTight` (sin
    espacio)**: `dates.ts:32-42` documenta que `DetalleEmpleadoDesktop.dc.html:245,255`
    dibuja "45 min" y que su consumidor (`service-assignment.tsx:73`) es correcto
    tal cual. Unificarlas rompe una pantalla ya cerrada, en cualquier direccion.

## §1.14 · Primitivos disponibles

`src/components/ui/` (23 componentes): `avatar`, `badge`, `button` (tiene
`size="action"`, `size="icon"`, `size="xl"` 44px y `size="2xl"` 50px),
`calendar`, `card`, `checkbox` (**existe y no se usa en staff/**), `command`,
`dialog`, `dropdown-menu`, `input-group`, `input`, `label`, `popover`,
`progress`, `scroll-area`, `select`, `separator`, `sheet`, `skeleton`, `sonner`,
`switch`, `tabs`, `textarea`.

**NO existe ningun primitivo de tabla.** Grep de `<table>`, `role="table"` y
`data-slot="table"` sobre todo `src`: cero resultados.

`src/components/shared/`: `empty-state.tsx`, `loading-skeleton.tsx` (17 lineas,
siempre avatar + 2 lineas, con `p-4` **propio** que se suma al `p-4` de
`PageShell:191`), y **`segmented-control.tsx`** (82 lineas, `role="tablist"`,
pastilla animada) — que es **exactamente** el control que dibujan
`Equipo.dc.html:41-46` y `EquipoDesktop.dc.html:93-96`, y que `/staff` **no usa**
(usa `Tabs`).

Helpers: `src/lib/utils/format.ts` -> `formatCurrency`, `formatCurrencyRounded`,
`formatPhone`, `initials`, `formatAddress`, `capitalizeFirst`.
`src/lib/utils/dates.ts` -> `formatTime`, `formatDate` (**`d MMM yyyy`** ->
`12 ago 2026`), `formatDateShort` (`d MMM`, **sin ano**), `formatRelativeDay`,
`formatDuration`, `formatDurationTight`, `formatTimeRange`, `formatDateLong`,
`formatRelativeTime`, `todayDayOfWeek`, `AFTERNOON_HOUR`.
**No existe ningun formateador `dd/mm/yyyy` en el repo.**

`PageShell` (`src/components/layout/page-shell.tsx`): `:93` `useMediaQuery`,
`:115` `if (isDesktop) return <DesktopHeader/>`, `:130` envoltorio de escritorio
`px-7 py-6`, `:131` `max-w-[1084px]` (sin `mx-auto`), `:191` envoltorio movil
`mx-auto w-full max-w-3xl p-4 md:py-6`, `:266` el `subtitle` se pinta sin
condicion. Props relevantes: `title`, `mobileTitle`, `subtitle`, `titleSize`,
`titleAdjacent`, `back`, `desktopBack`, `actions`, `mobileActions`.
**`mobileActions` SUSTITUYE a `actions` por debajo de 1024, no se suma.**

`/appointments/new` (`src/app/(fullscreen)/appointments/new/page.tsx:36-41`) lee
de la query `employeeId`, `date` y `time`. **No lee `clientId`.**

---

# §2 · DECISIONES

Cada decision dice **que se hace**, **por que**, y **que evidencia la anularia**.
Una regla que excluye algo lleva su "salvo que" por escrito: una regla puede ser
perfectamente coherente con el documento y equivocarse sobre el mundo.

## §2.1 · Idioma y ortografia

### D1 · La app escribe con tildes; los artboards no son la autoridad ortografica

Los doce artboards escriben **todo sin tildes ni ene** (`Anadir`, `Telefono`,
`Ultima visita`, `Recepcion`, `No asistio`, `Alergica`, `Proteccion de datos`,
`Miercoles`, `Sabado`, `catalogo`, `sesion`, `podra`, `despues`) **con una unica
excepcion**: `FormularioEmpleado.dc.html:152` escribe `Contraseña temporal *` con
ene.

Esa excepcion es la prueba: la omision es un atajo de dibujo, no una decision de
producto. **El texto visible se escribe con ortografia correcta.**

Traduccion literal para los implementadores:

| Artboard | Codigo |
|---|---|
| `Anadir` | `Añadir` |
| `Anadir empleado` / `Anadir cliente` | `Añadir empleado` / `Añadir cliente` |
| `Telefono` | `Teléfono` |
| `Sin telefono` | `Sin teléfono` |
| `Ultima visita` | `Última visita` |
| `Recepcion` | `Recepción` |
| `No asistio` | `No asistió` |
| `Proteccion de datos (GDPR)` | `Protección de datos (GDPR)` |
| `Miercoles` / `Sabado` | `Miércoles` / `Sábado` |
| `Cerrado · sin horas guardadas` | igual (no lleva tilde) |
| `Solo aparecen los servicios activos del catalogo.` | `...del catálogo.` |
| `Permite al empleado iniciar sesion y gestionar sus citas` | `...iniciar sesión...` |
| `El empleado podra cambiarla despues` | `...podrá cambiarla después` |
| `La exportacion entrega un JSON con todos sus datos y su historial.` | `La exportación...` |

Las cadenas que ya son correctas se copian tal cual: `Un empleado inactivo
conserva su historial pero no recibe citas nuevas.`, `Colorea su avatar en las
listas y en el filtro de la agenda.`, `El domingo llega sin horas guardadas. Al
activarlo hay que escribirlas antes de guardar.`, `La cuenta de acceso solo se
crea al dar de alta al empleado.`, `Toca un empleado para ver su horario y los
servicios que realiza.`

**Sobre "solo"**: la RAE ya no tilda el adverbio. Se escribe **`solo`** sin tilde
en los tres sitios donde aparece.

Los nombres propios de las semillas (Laura Martinez, Sofia Prat, Ana Garcia,
Pedro Sanchez, Maria Gil, Sofia Puig) son **datos de ejemplo del artboard**, no
cadenas del codigo: no se escriben en ninguna parte.

**Salvo que**: nada. Si un implementador encuentra una cadena que este plan no
lista, aplica ortografia correcta y lo anota.

## §2.2 · La tabla

### D2 · Se construye un primitivo `DataTable`, no una tabla por pantalla

Hay **tres** consumidores con tres rejillas distintas: `/staff`
(`EquipoDesktop.dc.html:28`, 6 columnas), `/clients`
(`ClientesDesktop.dc.html:22`, 5 columnas) y el historial de citas
(`DetalleClienteDesktop.dc.html:27`, 5 columnas). Tres tablas a mano divergen; el
repo ya tiene el precedente de `EmployeeCard` / `ClientCard` / `ServiceCard`,
tres piezas con la misma forma que no comparten nada.

### D3 · `role="table"` sobre `div`, no `<table>`

Los artboards son **CSS grid con columnas `fr` y `px` mezcladas**
(`minmax(0,1.5fr) 170px minmax(0,1.5fr) 128px 96px 20px`). Poner `display: grid`
sobre `<tr>` es CSS valido, pero en varios navegadores **borra los roles ARIA
implicitos** de los elementos de tabla, con lo que se pierde justo lo que
justificaba usar `<table>`. Se usan `div` con `role="table"`, `role="row"`,
`role="columnheader"` y `role="cell"` — explicitos y estables.

**Salvo que**: si el implementador COMPRUEBA (no supone) que el arbol de
accesibilidad de un `<table>` con `<tr style="display:grid">` se conserva en los
navegadores objetivo, `<table>` es mejor y se cambia. La comprobacion tiene que
ser real, no una cita de memoria.

### D4 · Dos variantes de cabecera, porque los artboards dibujan dos

| Variante | Alto | Fondo | Celdas | Donde |
|---|---|---|---|---|
| `screen` | **44** | `--sidebar` (`#F8F2EA`) | **11px/700**, `ls .08em`, uppercase, `--muted-foreground-2` | `EquipoDesktop:102`, `ClientesDesktop:93` |
| `nested` | **40** | `--muted-subtle` (`#FAF6F0`) | **10px/700**, `ls .08em`, uppercase, `--muted-foreground-2`, `border-bottom --hairline`, `radius 12 12 0 0` | `DetalleClienteDesktop:169` |

No es una inconsistencia del canvas: la primera es la tabla que **es** la
pantalla; la segunda es una tabla **anidada dentro de una tarjeta**. Dos pesos
para dos jerarquias.

### D5 · La fila es navegable de verdad

Hoy `EmployeeCard` / `ClientCard` son `<Card onClick>` sin `role`, sin `tabIndex`
y sin `onKeyDown` (`employee-card.tsx:14-17`): **no se pueden usar con teclado**.
Las filas de tabla y las tarjetas de movil pasan a `<Link>` de Next hacia
`/staff/{id}` y `/clients/{id}`, con foco visible. El chevron que los artboards
dibujan al final de cada fila de escritorio confirma que la fila navega.

**Salvo que**: el chevron NO aparece en las tarjetas de movil de ninguna de las
dos listas (`Equipo.dc.html:58`, `Clientes.dc.html:44`). El enlace si; el chevron
no se anade.

## §2.3 · Equipo

### D6 · La pestana "Servicios" se conserva intacta

Ningun artboard dibuja el panel al otro lado del segmentado — ni en movil
(`Equipo.dc.html:42`) ni en escritorio (`EquipoDesktop.dc.html:93`). Lo dibujado
es el control, no su destino. Este bloque reconstruye **solo** el panel
"Empleados" y deja el de "Servicios" exactamente como esta.

**Salvo que**: el control SI esta dibujado y su destino existe y funciona, asi
que **no se desmonta**. No es el caso del segmentado Dia/Semana del bloque 3,
cuya segunda opcion no llevaba a ninguna parte.

### D7 · `Tabs` pasa a `SegmentedControl`

`/staff` usa hoy `@base-ui/react/tabs` (`staff/page.tsx:90-101`). Los dos
artboards dibujan la pastilla del `SegmentedControl` que **ya existe** en
`src/components/shared/segmented-control.tsx`. Se sustituye, conservando el
control por la query (`?tab=`) que ya funciona y esta probado
(`staff/page.test.tsx:130-182`).

**Cuidado**: `SegmentedControl` es `role="tablist"` con `role="tab"`, igual que
`Tabs`, pero **no monta paneles**. El panel pasa a ser un condicional en la
pagina. Los tests existentes de `/staff` afirman sobre `tabpanel`: hay que
**actualizarlos**, no borrarlos.

### D8 · El contador de escritorio necesita a los inactivos

`EquipoDesktop.dc.html:97` dibuja `5 empleados · 4 activos`, y `:191-201` dibuja
la fila de un empleado **inactivo** con tinte y badge. Hoy el servidor **nunca**
manda un inactivo (§1.10). **Sin B1 esto no es construible.**

**Una sola fuente para los dos numeros, y una regla para cuando no se pueden
saber.** Con `size=100` (D11) un salon de 150 empleados recibe 100 filas, todas
activas por el orden `active DESC` de B1. Ahi:
- "100 empleados · 100 activos" **miente** sobre el total;
- "150 empleados · 100 activos" **miente peor**: sugiere 50 inactivos que no
  existen.

Regla:

```
sin empleados      totalElements === 0
                   ->  no se pinta contador; manda el EmptyState (D23)

con desglose       content.some(e => !e.isActive) || totalElements === content.length
                   ->  "{totalElements} empleados · {activos} activos"

sin desglose       en el resto
                   ->  "{totalElements} empleados"
```

**La guarda no es "cabe todo el mundo", es "cabe todo el mundo O ya he visto un
inactivo".** Por el orden `active DESC` de B1, si la pagina contiene **un solo**
inactivo entonces **todos** los activos estan por encima, dentro de la pagina — y
el desglose es exacto aunque falten filas por debajo. Un salon de 150 empleados
con 40 activos recibe 40 activos + 60 inactivos en la pagina 0 y **puede decir la
verdad**: `150 empleados · 40 activos`. Una guarda de `totalElements >
content.length` se lo prohibiria sin motivo.

El caso que si obliga a callar es el otro: 150 empleados con 120 activos: la
pagina 0 son 100 activos, no se ha visto ningun inactivo, y no hay forma de saber
cuantos hay.

Con una sola fuente (`totalElements` para el total, la pagina para el desglose)
las dos cifras dejan de poder contradecirse.

- Movil: `5 empleados` (`Equipo.dc.html:49`), sin desglose nunca.
- Escritorio: la regla de arriba.
- Singular: `1 empleado · 1 activo`. Los artboards solo dibujan plural; el codigo
  ya pluraliza a mano (`staff/page.tsx:107,145`) y se conserva.

**Salvo que**: si el salon supera los 100 empleados, la pantalla lo dice
callandose el desglose, no inventandolo. Es la unica lectura honesta sin
paginacion, que ningun artboard dibuja.

### D9 · Fila inactiva

| Pieza | Movil | Escritorio |
|---|---|---|
| Fondo de la fila | **sin cambio** (`Equipo.dc.html:94` no la tinta) | `--muted-subtle` (`EquipoDesktop:191`) |
| Nombre | `--muted-foreground` (`:97`) | `--muted-foreground` (`:194`) |
| Puesto | `--muted-foreground-2` (`:98`) | `--muted-foreground-2` (`:196`) |
| Badge | `border --border`, `bg --card`, `--muted-foreground-2` (`:100`) | igual (`:203`) |
| Email | — | `--muted-foreground` (`:198`) |

Los estados vacios `Sin teléfono` y `Por defecto` (**12px** `--text-subtle`, sin
`.num`, sin punto de color) **solo existen en escritorio** (`:199`, `:201`): son
columnas que el movil no tiene.

### D10 · No se inventa un buscador de empleados

Ningun artboard de Equipo dibuja buscador, filtro ni orden — y el backend tampoco
los ofrece (`EmployeeController.java:58-64`, cero `@RequestParam`). No se monta
ninguno.

**Salvo que**: B1 introduce `includeInactive`, que es un parametro de datos, no
un control de UI. La pantalla lo manda siempre en `true` y hace el desglose en
cliente; **no se dibuja ningun filtro Activos/Todos** porque ningun artboard lo
pide.

### D11 · `size=100` en empleados y servicios

Hoy `listEmployees` y `listServices` no mandan `size` y reciben **20** (§1.9).
Con el orden estable de B1 la pagina es reproducible. `size=100` cubre cualquier
salon real. **Deuda anotada**: por encima de 100 empleados la lista se trunca en
silencio.

## §2.4 · Detalle de empleado

### D12 · Escritorio apila tres tarjetas; movil apila secciones. Ninguno usa `Tabs`

`DetalleEmpleadoDesktop.dc.html:109-233` dibuja **tres tarjetas en fila** con
anchos fijos 300 / 386 / 372 y `gap 24`. `DetalleEmpleado.dc.html:86-91` dibuja
un segmentado `Horarios` / `Servicios` en movil — **pero el panel "Servicios" no
esta dibujado en ningun sitio**.

Decision: **el segmentado de movil SI se monta.** Esta dibujado y su destino
existe: es la misma tarjeta de servicios del escritorio, reflowed.

- Escritorio: tres tarjetas lado a lado, sin segmentado.
- Movil: identidad + contacto + color -> divisor -> segmentado -> panel Horarios
  o panel Servicios -> CTA del panel activo.
- El panel Servicios de movil reusa la tarjeta 3 del escritorio sin sus anchos
  fijos, con las mismas piezas: contador `4 de 6`, filas `.svc`, ayuda y CTA.

**Salvo que**: si el panel de servicios en movil queda inutilizable por debajo de
390px, el implementador lo reporta antes de improvisar.

### D13 · El domingo no es una contradiccion: son dos momentos

Los dos artboards parecen decir cosas opuestas del mismo dia:

- `DetalleEmpleado.dc.html:155-159`: toggle **OFF**, fila `#FAF6F0`, **sin campos
  de hora**, texto `Cerrado · sin horas guardadas`.
- `DetalleEmpleadoDesktop.dc.html:214-222`: toggle **ON**, fila `#FAEFE9` con
  `border #EBD3C8`, dos campos con `--:--`, `border-color #DFB3A8`, y un banner
  que dice `El domingo llega sin horas guardadas. Al activarlo hay que
  escribirlas antes de guardar.`

No se contradicen: **el primero es el estado CARGADO de un dia cerrado; el
segundo es el estado tras ACTIVARLO sin haber escrito las horas.** El banner del
escritorio lo dice literalmente ("Al activarlo"). Se construyen los dos, en los
dos anchos:

| Estado | Toggle | Fila | Horas | Extra |
|---|---|---|---|---|
| Cerrado (cargado) | OFF | `--muted-subtle` | no se pintan | texto `Cerrado · sin horas guardadas`, **12px** `--text-subtle` |
| Recien activado, sin horas | ON | `--surface-now`, `border --surface-now-border` | dos campos vacios, `placeholder="--:--"`, texto `--text-subtle`, `border-color --input-border-attention` | banner informativo |
| Abierto con horas | ON | normal | valores | — |

**El estado incompleto se define asi, y cubre la hora a medias:**

```
incompleto(dia)  =  dia.open && (!dia.openTime || !dia.closeTime)
```

Es decir: **una** de las dos horas vacia ya cuenta. Leerlo como "ninguna de las
dos" dejaria fuera el caso mas probable, que es escribir la apertura y olvidar el
cierre.

El banner se pinta **mientras haya al menos un dia incompleto**, y guardar queda
**bloqueado** en ese caso.

**Por que se bloquea — y NO es por corrupcion de datos.** El backend ya rechaza
ese envio: `EmployeeWorkingHours.validate()` lanza
`"Open days must have openTime and closeTime"` dentro de `@Transactional`
(§1.11.6 punto 3). Nada se corrompe. Se bloquea porque, sin freno, el usuario
recibe **un 400 en ingles que no le dice que fila arreglar**, justo despues de
que el propio banner le haya explicado en castellano lo que tenia que hacer.
El freno convierte un error del servidor en una instruccion.

**El freno vive SOLO en el CTA interno del editor. `save()` NO cambia.** Esta es
la parte que hay que leer entera antes de "mejorarla", porque la version obvia es
peor:

`WorkingHoursEditor` lo montan tambien `settings/business-hours/page.tsx:110` y
`(onboarding)/business-hours/page.tsx:109`, las dos con `showSaveButton={false}`,
guardando por `editorRef.save()`. La tentacion es hacer que `save()` tambien se
niegue, para cubrirlas. **Y eso las rompe.** Su `handleContinue` es:

```ts
try { await save(); router.push(...) } catch { /* no-op: el toast ya se disparo */ }
```

(`(onboarding)/business-hours/page.tsx:69-76`, y calcado en
`settings/business-hours/page.tsx:68-74`.)

Ese comentario **solo es cierto cuando quien rechaza es `mutateAsync`**, porque
su `onError` es el que lanza el toast. Si `save()` se niega **antes** de mutar, la
mutacion nunca corre, `onError` nunca dispara, y el `catch {}` se traga el
rechazo: el usuario pulsa "Continuar" y **no pasa absolutamente nada** — ni
navegacion, ni toast, ni mensaje. En el onboarding eso es quedarse atrapado. Y en
Ajustes movil es el **unico** camino de guardado (`showSaveButton={isDesktop}`,
`:115`).

La otra salida —que `save()` resuelva sin guardar— es peor: navega al paso
siguiente con el horario sin guardar.

Y las dos paginas son **intocables** (§3), asi que el `catch {}` no se puede
arreglar desde aqui.

Por eso:

| Consumidor | Con un dia incompleto |
|---|---|
| `/staff/[id]` (CTA interno, de este bloque) | **boton bloqueado** + banner explicativo |
| `settings/business-hours`, `(onboarding)/business-hours` | **exactamente como hoy**: se envia, el backend responde 400, `mutation.onError` lanza su toast |

Ninguna pantalla queda peor que antes, y la que este bloque construye queda
mejor. El banner es informativo en las tres.

**Deuda anotada**: en esas dos pantallas el usuario sigue recibiendo un 400 en
ingles en vez de una instruccion. El arreglo esta fuera de alcance porque exige
tocar su `catch {}`.

**Las dos pantallas son objetivo de verificacion, no de cambio**: §3 las declara
intocables, pero sus tests tienen que seguir en verde — el editor SI cambia
(banner, estilo del campo vacio). Si caen, es un fallo de este bloque.

Recordatorio de §1.10: el backend escribe `openTime: null, closeTime: null` para
sabado y domingo al crear el empleado (`EmployeeService.java:305-306`), pero
`src/types/employee.ts:14-15` los declara `string` no-nulo. **El tipo miente**;
el codigo tiene que tratarlos como nulables.

**Salvo que**: el estado "cargado desde el servidor con `open:true` y horas
nulas" **es imposible** — la validacion del backend impide que esa fila exista
(§1.11.6 punto 3). El estado incompleto solo puede nacer de una edicion en curso.
Si alguien lo observa cargado, es un fallo del backend y se reporta, no se pinta.

### D14 · "Color identificativo" se muestra, no solo se edita

`colorHex` hoy solo tinta el avatar (`staff/[id]/page.tsx:155`). Los artboards lo
piden como **dato visible**:

| Sitio | Forma |
|---|---|
| Tabla de escritorio | punto **12x12** `radius 999` + hex `.num` **12px** `--muted-foreground` (`EquipoDesktop:122-124`) |
| Detalle movil | punto **10x10** `radius 999` + hex `.num` **13px** + caption `Color identificativo` **12px** `--text-subtle` (`DetalleEmpleado:75-81`) |
| Detalle escritorio | cuadrado **28x28** `radius 8`, `border 1px rgba(42,35,32,.12)` + hex `.num` **13px** + label + ayuda (`DetalleEmpleadoDesktop:136-142`) |
| Formulario | cuadrado **32x32** `radius 8`, mismo borde + hex `.num` **12px** (`FormularioEmpleado:131-135`, `FormularioEmpleadoDesktop:335-339`) |

Son cuatro representaciones **a proposito**: punto en listas y detalle compacto,
cuadrado donde el color es editable o protagonista. Se implementan las cuatro.

La concatenacion `colorHex + "20"` esta duplicada literalmente en
`staff/[id]/page.tsx:155` y `employee-card.tsx:24`. **Se unifica en un helper**
dentro de `src/lib/utils/avatar.ts`, que ya es el fichero de esa
responsabilidad.

Cuando `colorHex` es nulo, escritorio pinta `Por defecto` sin punto
(`EquipoDesktop:201`).

### D15 · Contador `4 de 6` y encabezado `Horas propias de Laura`

- `DetalleEmpleadoDesktop:236` dibuja `4 de 6` = servicios asignados sobre
  servicios **activos del catalogo**. El filtro `isActive` ya esta en
  `service-assignment.tsx:19`.
- `:150` dibuja `Horas propias de {nombre}` con el **nombre de pila**, no el
  completo.
- `:300` dibuja `Guardar servicios (4)` con el numero de seleccionados **en
  vivo**, que es lo que ya hace `service-assignment.tsx`.

### D16 · Los tres fallos de servicios se arreglan juntos

§1.11.1 (contrato roto -> 400), §1.11.2 (perdida **parcial** de asignaciones) y
§1.11.5 (no se puede desasignar el ultimo servicio) son el **mismo trabajo**:
arreglar el primero hace **alcanzable** el segundo y **visible** el tercero.
Separarlos seria entregar un bloque que introduce una perdida de datos.

- El tipo `EmployeeServiceResponse` se corrige al contrato real (`serviceId`,
  `serviceName`, `effectiveDuration`, `effectivePrice`, `customDuration`,
  `customPrice`) y `AssignServicesRequest` pasa a
  `{ services: { serviceId: string }[] }`.
- `ServiceAssignment` recibe la misma guarda que `WorkingHoursEditor`: mientras
  `assignedServices === undefined` se pinta el esqueleto y **no** el editor. Es lo
  que impide la perdida parcial.
- Se anade la rama de error. **Correccion**: `useEmployeeServices` **si** expone
  `isError` (`use-staff.ts:95-98`, y `service-step.tsx:75` ya lo usa); lo unico
  que falta es destructurarlo en `staff/[id]/page.tsx:82`. Sin esa rama, un GET
  fallido se ve igual que "no tiene ninguno" — que es justo el estado desde el
  que se pierde el resto.

### D16b · Se relaja `@NotEmpty` para que se pueda quitar el ultimo servicio

`AssignServicesRequest.java:10` exige `@NotEmpty` sobre `services`, asi que
`{ services: [] }` responde 400 (§1.11.5). Consecuencia: un empleado al que se le
asigno un servicio por error **no se puede dejar sin ninguno** desde la UI.

Se sustituye `@NotEmpty` por `@NotNull`: la lista tiene que venir, y puede venir
vacia. Vaciar la lista es una operacion legitima — el propio endpoint ya es un
"reemplaza el conjunto entero" (`deleteByEmployeeId` + recrear,
`EmployeeService.java:259`), asi que el conjunto vacio es un valor valido de ese
conjunto, no un error de forma.

Va en **B1**, que es la unica tarea que ya toca staff-service.

(Se numera `D16b` y no `D40` porque es hija directa de D16: la misma tarea, el
mismo fallo, el reverso de la misma moneda.)

**Por que no es peligroso**: lo que protegia de verdad contra el borrado
accidental nunca fue `@NotEmpty` — era un accidente. La proteccion real es la
guarda de la UI (no ofrecer el boton mientras no se sabe que hay asignado), que
esta bloque anade. Un `@NotEmpty` que impide una operacion legitima y **no**
impide la ilegitima (marcar uno y borrar cinco) no esta protegiendo nada.

**Salvo que**: si al implementarlo aparece otro consumidor del endpoint que
dependa del 400 —hoy no hay ninguno: el unico llamante es
`staff/[id]/page.tsx:96`— se para y se reporta.

## §2.5 · Los formularios

### D17 · Hoja abajo en movil, modal de 512px en escritorio

Un contenedor unico `ResponsiveFormModal` que **monta condicionalmente en JS**
(`useMediaQuery`, nunca `lg:hidden`) `Sheet side="bottom"` o `Dialog`. Valores en
§1.5 y §1.8.

Diferencias reales entre los dos formularios que **se respetan**:

| | Empleado | Cliente |
|---|---|---|
| Cerrar en movil | **sin borde ni fondo**, X **18x18** (`FormularioEmpleado:99`) | **con** `border --border` y `bg --card`, X **15x15** (`FormularioCliente:52`) |
| `.lbl` | **12px/600** `--muted-foreground` (`:31`) | **12px/500** `--label` (`:18`) |
| Alto de campo movil | **44** (`:32`) | **42** (`:19`) |
| Alto de campo escritorio | **40** (`:21`) | **42** (el modal no redefine `.fld`) |
| CTA escritorio | h **42**, **14px/600** (`:342`) | h **48**, **15px/600** (`:202`) |
| Radio del modal | **12** (`:299`) | `16 16 12 12` (`:164`) |

**Radio del modal — decision**: se usa **12** en los dos. El `16 16 12 12`
aparece **una sola vez** entre los doce artboards y tiene la forma exacta de un
resto del radio superior de la hoja movil. Es un desliz de dibujo. Misma regla
que se aplico en el bloque 5 a las desviaciones de aparicion unica entre
hermanos.

**Salvo que**: las diferencias de `.lbl`, altura de campo y CTA aparecen de forma
**consistente en los dos anchos** de cada familia, asi que NO son deslices y se
conservan.

### D18 · El modo determina que se pinta, no el ancho

- **Alta** (`employee === null` / `client === null`): titulo `Nuevo empleado` /
  `Nuevo cliente`, CTA `Crear empleado` / `Crear cliente`, y **solo en empleado**
  el bloque de cuenta de acceso + contrasena temporal.
- **Edicion**: titulo `Editar empleado` / `Editar cliente`, CTA
  `Guardar cambios`, y **solo en empleado** la nota `La cuenta de acceso solo se
  crea al dar de alta al empleado.`

El artboard movil de empleado dibuja un alta y el de escritorio una edicion
(§1.5): **no son dos disenos de ancho, son los dos modos.** Cada modo se pinta
igual en los dos anchos salvo las metricas de la tabla de D17.

### D19 · No se renombran `EmployeeFormSheet` ni `ClientFormSheet`

Aunque en escritorio ya no sean una hoja. Renombrar obliga a tocar los cuatro
sitios que los montan, que son ficheros de OTRAS tareas de la misma ola:
cualquier orden de commits deja el arbol roto a mitad. Deuda cosmetica anotada.

## §2.6 · Clientes

### D20 · El buscador usa el debounce que ya existe

`useDeferredValue` (`clients/page.tsx:23`) se sustituye por el
`useDebouncedValue` de 250 ms que ya vive en `use-clients.ts:14-25`, probado con
fake timers. Ademas se anade `placeholderData: keepPreviousData` para que la
lista **no se desmonte en cada tecla**.

**No se anade filtro, ni orden, ni chips**: ningun artboard los dibuja (§1.6) y
el backend descarta `sort` en silencio (§1.10). Una cabecera clicable seria una
mentira visual.

### D21 · Una sola convencion de fecha: `formatDate`

El canvas se contradice sobre el **mismo dato**: la ultima visita de Ana es
`12 ago 2026` en `ClientesDesktop:110` y `05/08/2026` en `DetalleCliente:60` y
`DetalleClienteDesktop:138`. Y `FormularioClienteDesktop` — cuyo fondo diverge de
`ClientesDesktop` en otros doce valores, o sea el fichero poco fiable — usa
`dd/mm/yyyy`.

Decision: **`formatDate` (`d MMM yyyy` -> `12 ago 2026`) en todas las fechas de
estas seis pantallas**: ultima visita (tabla y KPI), `Cliente desde`,
`Consentimiento dado` y la columna Fecha del historial.

Por que no dos convenciones: el repo **no tiene** formateador `dd/mm/yyyy`;
anadir uno crearia exactamente la deuda que `formatDuration` /
`formatDurationTight` ya documenta como cara. Y `formatDate` es literalmente lo
que dibujan la tabla de clientes y las siete filas del historial (`05 ago 2026`,
`DetalleClienteDesktop:171`).

**El valor vacio es `—` (guion largo), y no es un detalle menor.** `formatDate`
(`dates.ts:16`) acepta `string` no-nulo y `parseISO(null)` lanza `RangeError`,
asi que **tsc obliga a un respaldo en cada sitio**. Sin decidirlo aqui, T6 y T10
elegirian textos distintos para el mismo hueco. El repo ya usa `—`
(`clients/[id]/page.tsx:98`), asi que ese es el valor: en la columna
`Última visita`, en el KPI y en cualquier fecha ausente de estas seis pantallas.

**Y el dia 1 es el 100% de las filas**, no un caso borde: sin backfill (D36),
`lastVisitAt` es `null` para todos los clientes existentes. La columna nueva
nacera entera a `—` y se ira llenando. Esta escrito para que nadie lo lea como un
fallo de la tabla.

**Riesgo a vigilar en la verificacion visual**: el KPI de movil dibuja
`05/08/2026` a **21px** en una tarjeta de ~151px utiles (`DetalleCliente:53-62`).
`12 ago 2026` a 21px mide ~121px: entra, pero se comprueba a 390px antes de dar
la pantalla por cerrada.

**Salvo que**: si a 390px desborda, se baja el tamano del KPI, **no** se cambia
el formato. La coherencia del dato manda sobre el pixel.

### D22 · La linea de paginacion se pinta con numeros reales, sin controles

`ClientesDesktop:193` dibuja, **fuera** de la tarjeta:
`Mostrando 6 de 248 · la lista pide 50 por pagina`.

Es un texto que **describe una limitacion**, no una funcionalidad. Se pinta con
los numeros reales (`content.length`, `totalElements`, `size`). **No se anaden
controles de paginacion**: ningun artboard los dibuja.

**Y el camino de repuesto es peor de lo que parece, asi que se declara aqui en
vez de dejarlo implicito.** El buscador filtra en servidor, si, pero
`ClientJpaRepository.java:40-47` compara `LIKE` contra `firstName` **o**
`lastName` **por separado**: teclear `Ana Garcia` **no encuentra a Ana Garcia**.
Con 248 clientes, sin paginacion y sin orden clicable, un cliente que no este
entre los 50 primeros solo es alcanzable si se busca por **un** nombre, por
telefono o por email.

Se acepta porque **ningun artboard dibuja la salida** — ni paginacion, ni orden,
ni un buscador distinto — y porque inventarla aqui seria diseno sin fuente. Pero
se anota como **deuda de producto, no cosmetica**: es un cliente inalcanzable, no
un pixel.

**Salvo que**: si se decide arreglarlo, el arreglo barato es de una linea en el
JPQL (concatenar `firstName || ' ' || lastName` y comparar contra eso ademas de
por separado). No entra en este bloque porque cambia el comportamiento de una
pantalla que no es de aqui — el paso 4 del asistente usa el mismo endpoint.

El movil **no dibuja esta linea de paginacion**. No se anade.

### D23 · Los estados vacios existentes se conservan

**Ninguno de los doce artboards dibuja un estado vacio** (ni lista sin registros,
ni busqueda sin resultados, ni historial sin citas). El repo ya monta
`EmptyState` en `/staff` y `/clients` con textos aprobados. Se **conserva** lo que
hay: quitarlo dejaria una pantalla en blanco, que es peor que un texto no
dibujado. No se inventan ilustraciones ni CTA nuevos.

Para el historial de citas, que es seccion nueva: si el cliente no tiene ninguna,
se pinta la cabecerilla con `0 citas · 0,00 € facturados` y **no** se monta ni la
tabla ni el footer. Es la lectura minima y no inventa copy.

## §2.7 · Detalle de cliente

### D24 · "Ver todas" NO se monta

`DetalleClienteDesktop:236` dibuja un enlace `Ver todas` con chevron, o sea
**navegacion**. Su destino no existe: no hay ruta de historial de citas de un
cliente, ni artboard que la dibuje.

Precedente del repo, ya aplicado en el bloque 3: **no se monta un control cuyo
destino no existe** (el segmentado Dia/Semana). Se pinta el footer con
`Mostrando N de M citas` y se omite el enlace. Deuda anotada.

**El footer se pinta TAMBIEN en movil, aunque el artboard no lo dibuje.** El
movil ensena 3 de 14 citas (`DetalleCliente:85-107`) y su artboard no tiene
footer, asi que sin el **no hay ni una pista de que existan otras once**: la
pantalla afirmaria por omision que el cliente ha venido tres veces. Anadir
`Mostrando 3 de 14 citas` es la diferencia entre una lista recortada y una lista
que miente. Es la excepcion explicita a D23 ("no se inventa copy"): el texto no
se inventa, se reusa el que ya dibuja el escritorio
(`DetalleClienteDesktop:234`).

**Una sola consulta para los dos anchos**: `size=7` (B3), escritorio pinta las 7,
movil pinta las 3 primeras. No se hacen dos peticiones distintas por ancho.

**Salvo que**: si se decidiera que "Ver todas" cargue mas filas en el sitio, eso
es un control distinto (no lleva chevron de navegacion) y necesita artboard.

### D25 · Piezas que existen en un solo ancho

Lo que dibuja cada artboard, sin unificar:

| Pieza | Movil | Escritorio |
|---|---|---|
| Titulo de la barra | `Detalle cliente` **15px/600** generico (`DetalleCliente:37`) | **el nombre del cliente**, `.display` **26px** `ls -0.015em` (`DetalleClienteDesktop:84`) |
| `Cliente desde` | en el cuerpo, bajo el nombre (`:46`) | en la topbar, `baseline` con el nombre (`:85`) |
| Boton `Llamar` | **si**, h32, color `--primary-pressed` (`:73`) | **no** |
| CTA `Nueva cita` | **no** | **si**, h38 primario (`:93-96`) |
| Badge `Reserva online` | **no** | **si** (`:109`) |
| Editar | icono solo, 36x36 (`:48`) | `.act` con texto `Editar`, h38 (`:89`) |
| Historial | lista de 3 items sin columnas (`:85-107`) | tabla de 5 columnas + cabecera + footer (`:168-239`) |
| Importe de cita no cobrada | color normal (`:104`) | **`--muted-foreground-2`** (`:197`) |

El badge `Reserva online` sale de `ClientResponse.source === "ONLINE_BOOKING"`,
que el backend **si** envia (§1.10).

**Sobre el importe atenuado**: aparece una sola vez, en un solo ancho. Pero **no
es un desliz**: atenuar el importe de una cita que no se cobro es informacion, no
decoracion. Se aplica en los **dos** anchos, a `NO_SHOW` y a `CANCELLED`.

### D26 · `Nueva cita` lleva el cliente consigo

`/appointments/new` lee hoy `employeeId`, `date` y `time` de la query
(`page.tsx:36-41`) pero **no `clientId`**. Un boton "Nueva cita" en la ficha de
un cliente que olvida al cliente es peor que no tenerlo.

**Y NO es simetrico con los otros tres, aunque lo parezca.** `employeeId`, `date`
y `time` se siembran como preferencias de tipo `string`
(`appointments/new/page.tsx:48-57`), mientras que `selectedClient` guarda el
objeto `Client` **completo** (`wizard-store.ts:20,104`). Sembrarlo exige un
`preferredClientId` en `src/lib/stores/wizard-store.ts` y que
`client-step.tsx` lo consuma resolviendo el cliente.

La version anterior de esta decision remataba con un "salvo que" que decia
"si exige mas, enlaza sin parametro" — y como **si** exige mas, esa salida era el
resultado programado: exactamente el boton que la propia decision llama peor que
nada. Se retira.

**Los tres ficheros estan declarados en `paths_touched` de T10** (§3):
`appointments/new/page.tsx`, `wizard-store.ts` y `client-step.tsx`. Ninguna otra
tarea de la ola 3 los toca.

**Salvo que**: si al abrirlos aparece que sembrar el cliente arrastra el paso 4
entero o cambia el flujo del asistente, la salida **no** es un boton mudo: es
**no montar el boton** y anotarlo, igual que D24 hace con "Ver todas". Un control
que no cumple lo que promete es peor que su ausencia — que es el argumento con el
que empieza esta decision.

### D27 · GDPR: tokens en vez de paleta generica, y el dialogo gana frenos

`gdpr-panel.tsx:58,60,61` usa `border-orange-200 bg-orange-50/50`,
`text-orange-600` y `text-orange-800` — paleta cruda de Tailwind. Los artboards
piden `--warning-border` / `--warning-soft` / `--color-status-pending-text`.

**Correccion de la premisa**: `gdpr-panel.tsx` **ya** deshabilita durante la
mutacion — `:75` (`disabled={exportMutation.isPending}`) y `:111`
(`disabled={anonymizeMutation.isPending}`). Lo que falta de verdad es mas
pequeno y mas concreto:

1. el boton `Cancelar` del dialogo **no** lleva `disabled`, asi que se puede
   cancelar mientras la anonimizacion ya esta en vuelo;
2. el `onOpenChange` del `Dialog` **no** se bloquea durante `isPending`, asi que
   un `Esc` o un clic fuera cierran el dialogo sobre una operacion irreversible en
   curso.

Se corrige eso, que es defecto. Ningun artboard dibuja el dialogo, asi que **no
se redisena** (por ejemplo, no se anade "escribe el nombre para confirmar").
Deuda de producto anotada.

**Consecuencia para T10**: el test "que el boton destructivo no se pueda pulsar
dos veces" **pasa hoy sin tocar nada**. El test que hay que escribir es el de
`Cancelar` y el del cierre.

`gdprConsentAt` es hoy un **campo fantasma**: los dos artboards dibujan
`Consentimiento dado: ...` y `GET /api/v1/clients/{id}` no lo envia. Se anade al
DTO en B2 (D37).

## §2.8 · Correcciones transversales

### D28 · `lg:hidden` pasa a montaje condicional en JS

Cinco sitios: `staff/page.tsx:110-120` y `:148-158`, `staff/[id]/page.tsx:182`,
`clients/[id]/page.tsx:69` y `:76-86`. La regla del repo es montar
condicionalmente con `useMediaQuery` (`page-shell.tsx:110-112`), porque con CSS
los dos arboles quedan en el DOM, jsdom no aplica CSS y la cobertura por rama
deja de ser medible.

### D29 · `formatPhone` en todas partes

`format.ts:24-34` existe y se usa en cuatro sitios del repo, pero las cuatro
pantallas de este bloque pintan el telefono **crudo** (`client-card.tsx:28`,
`clients/[id]/page.tsx:116`, `staff/[id]/page.tsx:208`). Los artboards dibujan
`612 345 678` con espacios, que es exactamente lo que produce `formatPhone`.

### D30 · Los `onError` muestran el motivo real

Los cuatro `onError` de los formularios y del panel GDPR tiran el `detail` del
`ProblemDetail` que `apiFetch` si propaga (`src/lib/api/client.ts:96`). Se pasa
al toast, con el mensaje generico como respaldo. Correccion de defecto: no
inventa UI.

### D31 · `trim()` antes de validar y de enviar

Hoy un nombre de espacios pasa el guard (`client-form.tsx:89`,
`employee-form.tsx:93`). Se aplica `trim()` en la validacion y en el envio.
**No se anade validacion de formato de email ni UI de error por campo**: ningun
artboard dibuja estado de error, y el mecanismo actual (boton deshabilitado) es
lo que hay dibujado. Deuda anotada.

### D32 · `dateOfBirth` se borra del tipo; `gender` no se monta

- **`dateOfBirth` no existe en el backend**: ni en `ClientResponse`, ni en
  `CreateClientRequest`, ni en la tabla (`V2__create_clients_schema.sql`). Es un
  fantasma en `src/types/client.ts:8`, `:24` y `:34`. **Se borra del tipo.**
- **`gender` si existe** en el backend, pero **ningun artboard lo dibuja**. No se
  monta. Se anota.

**Borrarlo rompe siete ficheros de test, y todos son de T4.** Los fixtures
anotados `: Client` declaran `dateOfBirth: null`; con el campo fuera del tipo, el
chequeo de propiedades sobrantes de TypeScript convierte cada uno en un error de
`tsc`. Los siete estan en §1.11.7 y en `paths_touched` de T4 (§3).

**Esto no es un detalle de contabilidad**: cuatro de ellos
(`client-step.test.tsx`, `confirmation-step.test.tsx`, `wizard-summary.test.ts`,
`wizard-store.test.ts`) pertenecen al asistente, que no es de este bloque. Si no
estuvieran declarados, T4 no podria tocarlos —`git commit -o` solo commitea las
rutas declaradas— y **el arbol quedaria en rojo desde el final de la ola 1 hasta
la ola 4**, con las olas 2 y 3 trabajando encima. Lo mismo vale para
`datetime-step.test.tsx:82` y `service-step.test.tsx:100`, que construyen la
forma vieja de `EmployeeServiceResponse` que D16 reescribe.

**Regla general que se deriva**: toda tarea que cambia un TIPO compartido es
duena de **todos** los ficheros que lo construyen, aunque pertenezcan a otras
pantallas. Se localizan con `grep` del nombre del campo antes de empezar, no
despues.

### D33 · `useCreateClient` se borra

`use-clients.ts:53-64` no lo usa ningun consumidor y duplica lo que hace
`client-form.tsx:63-72`. Codigo muerto.

### D34 · Colisiones de clave de cache

`use-clients.ts:38-44` documenta que metio `size: 10` en la clave para no chocar
con `/clients`, que pide 50 — **pero el arreglo esta solo en un lado**: la clave
de `clients/page.tsx:27` sigue sin `size`. Se completa: las dos claves llevan
`size`.

Y `useEmployees` gana `includeInactive` **en la clave**, por la misma razon: el
calendario, `/today` y el asistente comparten `["employees"]` y **deben seguir
viendo solo activos**.

## §2.9 · Backend

### D35 · Empleados: `includeInactive` + orden estable

`GET /api/v1/staff/employees?includeInactive=&page=&size=&sort=`

- **Por defecto `false`**, para que los consumidores actuales (calendario,
  asistente, `/today`) sigan viendo solo activos sin tocar ni una linea.
- Orden por defecto **determinista**: `active DESC, firstName ASC, lastName ASC,
  id ASC`. Hoy no hay `ORDER BY` y la paginacion puede repetir o perder filas
  entre paginas.
- El repositorio pasa de `findByActiveTrue(Pageable)` a una consulta con la
  condicion parametrizada. **JPQL, nunca nativa**: el `@Filter` multi-tenant de
  Hibernate **no cubre las consultas nativas** (aviso ya escrito en
  `ClientJpaRepository.java:36-38`).
- El `sort` entrante se sigue honrando; si no viene, se aplica el orden por
  defecto.

### D36 · Contadores de visita: se escriben, no se derivan

`totalVisits` y `lastVisitAt` existen en la tabla, en el modelo, en el DTO y en
el `ORDER BY` del listado. Lo unico que falta es **quien los escribe**. La
alternativa (derivarlos en cada lectura preguntando a appointment-service) exige
una llamada extra por pagina de listado y deja el `ORDER BY` inerte para siempre.
**Se completa el diseno al que el codigo ya se comprometio.**

Mecanismo: `AppointmentService.updateStatus` (`:190-210`), al pasar a
`COMPLETED`, llama a `ClientServicePort.registerVisit(tenantId,
clientExternalId, visitAt)`; client-service expone
`POST /api/internal/clients/{clientId}/visit?tenantId=` que hace `totalVisits++`
y `lastVisitAt = max(lastVisitAt, visitAt)`.

- **Idempotencia gratis**: `COMPLETED` es terminal
  (`AppointmentStatus.java:14`), no se puede entrar dos veces.
- **Degradacion**: si client-service esta caido, el cambio de estado **sigue
  saliendo bien** y se registra un `warn`. Es la regla del proyecto
  ("notification-service down -> appointment still created").
- **Guarda**: `clientId` puede ser nulo (cita sin cliente). Se comprueba.
- **`visitAt` = `startTime` de la cita**, no `Instant.now()`: una cita completada
  a posteriori tiene que contar por su fecha real. Por eso `max(...)` y no
  asignacion directa.

**Sin backfill.** Las citas ya `COMPLETED` antes de este cambio no se cuentan.
**Supuesto explicito y declarado**: el producto aun no tiene historial real que
preservar. Si lo tuviera, la salida es un endpoint interno de recomputo
alimentado desde appointment-service; **no se construye ahora** (YAGNI) y queda
anotado como deuda.

**Pero "sin backfill" produce una contradiccion VISIBLE, y hay que cerrarla.** En
`/clients/[id]` conviven a centimetros el KPI `Visitas` (que saldria de
`client.totalVisits`, o sea **0** para todos el dia 1) y la cabecerilla del
historial (que sale de una consulta real: **14 citas · 612,00 € facturados**). Un
plan que solo lo anote como deuda entrega una pantalla que se desmiente a si
misma.

Regla: **en la FICHA, los dos KPIs se derivan del resumen del historial**, no del
contador almacenado. B3 ya carga ese resumen para la cabecerilla, asi que sale
gratis y es veraz:

| KPI de la ficha | Fuente |
|---|---|
| `Visitas` | `summary.completedCount` (citas `COMPLETED`, que es lo que significa una visita) |
| `Última visita` | `summary.lastCompletedAt`, con `—` si es nulo (D21) |

**En la LISTA se conservan los contadores almacenados** (`totalVisits`,
`lastVisitAt`): pedir el historial de cada fila serian 50 llamadas por pagina.

**Y la divergencia entre las dos pantallas es PERMANENTE, no temporal.** Sin
backfill, `totalVisits` cuenta solo lo posterior al despliegue y `completedCount`
cuenta todo: un cliente con 11 citas completadas antes y 1 despues dira
**12 visitas** en la ficha y **1 visita** en el listado, y el hueco no se cierra
nunca — es exactamente el historico previo, congelado. Decirlo "se ira llenando
sola" seria falso.

Se acepta asi porque la alternativa (50 llamadas por pagina de listado) es peor, y
porque **la cifra veraz es la de la ficha**, que es donde el usuario va a mirar
cuando le importe. **Deuda anotada, y es de las que un dia habra que pagar con un
recomputo.**

D38 anade `completedCount` y `lastCompletedAt` al resumen precisamente por esto.

**Y `anonymize()` limpia los contadores — pero eso NO es borrado GDPR, y hay que
decirlo con precision.** `Client.anonymize()` (`Client.java:44-53`) borra nombre,
email, telefono, notas y genero, pero **no** `totalVisits` ni `lastVisitAt`. Hoy
es inocuo porque nadie los escribe; en cuanto D36 los ponga a escribir, el
listado mostraria `ANONYMIZED CLIENT · 14 visitas · última 05 ago 2026`. Se
limpian: `totalVisits = 0`, `lastVisitAt = null`. `Client.java` ya esta en
`paths_touched` de B2.

**Lo que NO consigue esa limpieza**: la ficha del mismo cliente anonimizado
seguira mostrando `11 visitas`, `última 05 ago 2026` y **la tabla entera del
historial** con fecha, servicio, profesional e importe — porque todo eso vive en
**appointment-service**, a quien `anonymize()` no toca, y la ficha lo deriva de
ahi por decision de esta misma D36. Limpiar los contadores borra **la copia del
listado**, no el dato.

Un borrado GDPR de verdad exige que appointment-service anonimice o desligue las
citas del cliente, y **eso esta fuera de alcance** (D39 ya declara que anonimizar
tampoco cancela las citas futuras). **Deuda anotada como incumplimiento
potencial, no como mejora**: es la clase de hueco que se arregla antes de tener
usuarios reales, no despues. Ningun artboard dibuja la ficha de un cliente
anonimizado, asi que no se construye nada para ese estado.

### D37 · `gdprConsentAt` entra en `ClientResponse`

Un campo en el record y una linea en el mapper. Sin el, la tarjeta GDPR de los
dos artboards dibuja `Consentimiento dado:` sin fecha. Va en la misma tarea que
ya toca client-service.

### D38 · Historial de citas: endpoint propio, y **NO** se traga los errores

`GET /api/v1/clients/{id}/appointments?page=&size=` en client-service, que delega
en `GET /api/internal/admin/appointments/by-client/{clientId}` **extendido** con
`page`, `size`, `ORDER BY startTime DESC` y un resumen.

Forma de la respuesta:

```json
{
  "content": [
    { "id": "apt_...", "startTime": "2026-08-05T10:00:00",
      "serviceName": "Corte + Secado", "employeeName": "Laura Martinez",
      "price": 35.00, "status": "COMPLETED" }
  ],
  "page": 0, "size": 7, "totalElements": 14, "totalPages": 2,
  "summary": {
    "totalAppointments": 14,
    "billedAmount": 612.00,
    "completedCount": 11,
    "lastCompletedAt": "2026-08-05T10:00:00Z"
  }
}
```

- **`totalAppointments` = TODAS las citas**, de cualquier estado. El artboard
  dibuja `14 citas` y entre sus 7 filas hay una `No asistio` y una `Cancelada`
  (`DetalleClienteDesktop:197,230`): el conteo las incluye.
- **`billedAmount` = SOLO las `COMPLETED`.** "Facturados" significa cobrado; una
  cita cancelada o a la que el cliente no vino no se factura. (Comprobacion: las
  7 filas dibujadas suman 348 €, no 612 — el total es sobre las 14, asi que el
  artboard no permite deducir la regla. Se decide por el significado de la
  palabra, y se deja escrito para que nadie lo "arregle" al reves.)
- **`completedCount` y `lastCompletedAt`** alimentan los dos KPIs de la ficha
  (D36). Sin ellos, el KPI "Visitas" diria `0` a centimetros de "14 citas".
- **`price`**: `servicePrice` **ya existe** en appointment-service
  (`AppointmentInternalResponse.java:11`, `AppointmentResponse.java:16`,
  `Appointment.java:36`). Lo que falta no es crear el dato sino **transportarlo**
  hasta `ClientAppointmentDto`, que hoy es
  `{id, serviceName, employeeName, startTime, endTime, status}` y **vive en
  `client-service`** (§1.11.6 punto 4), no en appointment-service.

**Nota de denominadores para el copy** (D38 lo decide, T10 lo pinta): la linea
`14 citas · 612,00 € facturados` pone dos cifras con denominadores distintos —
las 14 incluyen canceladas y no asistidas, los 612 € no. Es lo que dibuja el
artboard y no se cambia el texto, pero el implementador **no debe** derivar de
ahi ningun ticket medio ni ninguna division: no son comparables.
- **El error se propaga.** `AppointmentServiceAdapter.java:52-55` y
  `ClientService.java:156-161` se tragan la excepcion y devuelven lista vacia.
  Para el **export** eso es correcto (degradacion). Para una **pantalla** es una
  mentira: "sin citas" y "no se pudo cargar" se verian igual. El endpoint nuevo
  propaga el fallo y la UI pinta una rama de error propia.

**Salvo que**: el camino del **export no se toca**. Sigue degradando como hoy.

### D39 · Lo que el backend NO hace en este bloque

Escrito para que nadie lo reabra a mitad:

- **No** se anade buscador de empleados (ningun artboard lo pide).
- **No** se honra `sort` en clientes (`ClientService.java:93` lo descarta a
  proposito; cambiarlo sin cabecera clicable no sirve para nada). Deuda.
- **No** se excluyen los anonimizados del listado de clientes: ningun artboard
  dibuja ese filtro, y hacerlo cambiaria el comportamiento de una pantalla que no
  es de este bloque. Deuda.
- **No** se anade reactivacion de empleado, ni comprobacion de citas futuras al
  desactivar, ni desconexion de Keycloak. Tres huecos reales (§1.10), ninguno
  dibujado. Deuda.
- **No** se toca el rol `EMPLOYEE`: sigue viendo el listado completo del salon.
  Decision de producto pendiente, ya anotada en `tasks/todo.md`.
- **No** se cancelan las citas futuras al anonimizar un cliente, pese a lo que
  afirma `client-service/CLAUDE.md`. Deuda, fuera de alcance.

---

# §3 · PROPIEDAD DE FICHEROS

Cada fichero tiene **un solo dueno por ola**. Dos tareas de la misma ola nunca
comparten ruta. Entre olas distintas si puede repetirse una ruta, porque las olas
estan serializadas.

## Backend — `E:\IdeaProjects\rivoo`

| Tarea | `paths_touched` |
|---|---|
| **B1** | `staff-service/.../infrastructure/adapter/in/web/EmployeeController.java` · `staff-service/.../application/EmployeeService.java` · **`staff-service/.../application/dto/AssignServicesRequest.java`** (D16b) · `staff-service/.../domain/port/in/**` (la firma del caso de uso) · `staff-service/.../infrastructure/adapter/out/persistence/EmployeeJpaRepository.java` (+ su adaptador) · `staff-service/src/test/**` |
| **B2** | `client-service/src/main/java/com/rivoo/client/domain/model/Client.java` · `client-service/.../application/ClientService.java` · `client-service/.../application/dto/ClientResponse.java` · `client-service/.../domain/port/in/InternalClientUseCase.java` · `client-service/.../infrastructure/adapter/in/web/ClientInternalController.java` · `client-service/.../infrastructure/mapper/ClientDtoMapper.java` · `client-service/src/test/**` · `appointment-service/.../domain/port/out/ClientServicePort.java` · `appointment-service/.../application/AppointmentService.java` · `appointment-service/.../infrastructure/adapter/out/rest/**` (adaptador de cliente) · `appointment-service/src/test/**` |
| **B3** | `appointment-service/.../infrastructure/adapter/in/web/AppointmentInternalController.java` · `appointment-service/.../application/AppointmentService.java` · `client-service/.../application/dto/ClientAppointmentDto.java` (**vive en client-service**, §1.11.6 punto 4) + el DTO de pagina/resumen nuevo · `appointment-service/.../application/dto/**` (el sobre `content` + `summary`) · `appointment-service/.../infrastructure/adapter/out/persistence/AppointmentJpaRepository.java` · `client-service/.../infrastructure/adapter/in/web/ClientController.java` · `client-service/.../application/ClientService.java` · `client-service/.../domain/port/out/AppointmentServicePort.java` · `client-service/.../infrastructure/adapter/out/rest/AppointmentServiceAdapter.java` · tests de los dos |

**B2 y B3 comparten `AppointmentService.java` y `ClientService.java`: van en olas
distintas, B2 primero.**

## Frontend — `E:\IdeaProjects\rivoo-frontend`

| Tarea | `paths_touched` |
|---|---|
| **T1** | `src/app/globals.css` · `src/lib/utils/avatar.ts` (+ su test) · `src/components/staff/employee-color.tsx` (nuevo) · `.test.tsx` · `src/components/shared/segmented-control.tsx` (+ su test nuevo) · `src/app/dev/preview/page.tsx` (unico consumidor del segmentado) |
| **T2** | `src/components/ui/data-table.tsx` · `src/components/ui/data-table.test.tsx` |
| **T3** | `src/components/shared/responsive-form-modal.tsx` · `.test.tsx` |
| **T4** | `src/types/employee.ts` · `src/types/client.ts` · `src/lib/api/staff.ts` · `src/lib/api/clients.ts` · `src/hooks/use-staff.ts` · `src/hooks/use-clients.ts` (+ sus tests) · **una linea** de `src/app/(app)/staff/[id]/page.tsx:96` · **los once fixtures de §1.11.7**: `src/components/appointments/wizard/client-step.test.tsx` · `confirmation-step.test.tsx` · `wizard-summary.test.ts` · `datetime-step.test.tsx` · `service-step.test.tsx` · `src/lib/stores/wizard-store.test.ts` · `src/components/clients/client-card.test.tsx` · `client-form.test.tsx` · **`src/components/staff/service-assignment.test.tsx`** · `src/hooks/use-staff.test.tsx` (**solo las lineas del fixture** en los cuatro ultimos) |
| **T5** | `src/app/(app)/staff/page.tsx` · `.test.tsx` · `src/components/staff/employee-card.tsx` · `.test.tsx` · `src/components/staff/employee-table.tsx` (nuevo) · `.test.tsx` |
| **T6** | `src/app/(app)/clients/page.tsx` · `page.test.tsx` (nuevo) · `src/components/clients/client-card.tsx` · `.test.tsx` · `src/components/clients/client-table.tsx` (nuevo) · `.test.tsx` |
| **T7** | `src/components/staff/employee-form.tsx` · `.test.tsx` |
| **T8** | `src/components/clients/client-form.tsx` · `.test.tsx` |
| **T9** | `src/app/(app)/staff/[id]/page.tsx` · `.test.tsx` · `src/components/staff/service-assignment.tsx` · `.test.tsx` · `src/components/staff/working-hours-editor.tsx` · `.test.tsx`. **NO crea `employee-color.tsx`**: lo crea T1 y aqui solo se consume |
| **T10** | `src/app/(app)/clients/[id]/page.tsx` · `page.test.tsx` (nuevo) · `src/components/clients/gdpr-panel.tsx` · `gdpr-panel.test.tsx` (nuevo) · `src/components/clients/client-appointment-history.tsx` (nuevo) · `.test.tsx` · `src/app/(fullscreen)/appointments/new/page.tsx` · `src/lib/stores/wizard-store.ts` · `src/components/appointments/wizard/client-step.tsx` · `client-step.test.tsx` (**los cuatro solo para sembrar `clientId`**, D26) |
| **T11** | `visual/equipo-clientes.spec.ts` (nuevo) · `AGENTS.md` si aparece una trampa nueva |
| **T12-T14** | **ninguno**. T12 y T13 son de solo lectura. **T14 muta ficheros a proposito** (pruebas de mutacion) y por eso corre SOLA, despues de las otras dos, y devuelve el arbol limpio |

**Ficheros que NADIE toca en este bloque**: `src/components/layout/page-shell.tsx`,
`src/app/(app)/layout.tsx`, `src/lib/nav/app-nav.ts`, `src/components/services/**`,
`src/app/(app)/calendar/**`, `src/app/(app)/today/**`, `src/app/(app)/settings/**`,
`src/app/(onboarding)/**`, y todo `src/components/booking/**`.

**Objetivos de VERIFICACION, que no se tocan pero tienen que seguir en verde**:
`src/app/(app)/settings/business-hours/page.tsx` y
`src/app/(onboarding)/business-hours/page.tsx` montan `WorkingHoursEditor`, que
T9 SI modifica (§1.11.6 punto 6). Sus tests son la red que detecta si el freno de
D13 las rompe.

---

# §4 · OLAS Y PROTOCOLO

## §4.1 · Olas

```
BACKEND (repo rivoo) — arranca a la vez que el frontend
  Ola B0:  B1 ‖ B2          (ficheros disjuntos: staff-service vs client+appointment)
  Ola B1:  B3               (depende de B2: comparte AppointmentService y ClientService)

FRONTEND (repo rivoo-frontend)
  Ola 0:   T1               SOLA — es la unica que toca globals.css
  Ola 1:   T2 ‖ T3 ‖ T4
  Ola 2:   T5 ‖ T6 ‖ T7 ‖ T8
  Ola 3:   T9 ‖ T10
  Ola 4:   T11              spec visual + puertas globales sobre el arbol quieto
  Ola 5:   T12 ‖ T13         panel: fidelidad y correccion, en paralelo
  Ola 6:   T14              pruebas de mutacion — corre SOLA (muta ficheros)
```

Los dos repos avanzan **en paralelo**: el contrato del backend esta fijado en §2.9
(D35-D38), asi que el frontend puede codificar y probar contra el sin esperar. Lo
que **si** espera es la verificacion contra la pila real, que es T11.

## §4.2 · Protocolo de commit — en TODAS las tareas, sin excepcion

```bash
git add <sus rutas>
git commit -o <sus rutas> -m "..."
```

Las **dos** cosas:
- `git add` porque `git commit -o` falla sobre ficheros que git aun no conoce, y
  casi todas las tareas crean ficheros.
- `-o` porque commitea **solo esas rutas** e ignora el resto del indice. En una
  ola de cuatro agentes sobre el mismo arbol, sin `-o` el primero que commitea se
  lleva el trabajo a medio escribir de los otros.

**NUNCA `git add -A`. NUNCA `git commit -m` a secas.**

Mensaje en ingles, y con el trailer:

```
Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

## §4.3 · Seguridad operativa — repetir en CADA brief de implementador

- **PROHIBIDO tocar `node_modules`. PROHIBIDO ejecutar `npm ci`** — un `npm ci`
  previo destruyo `node_modules/.bin` devolviendo **exit code 0**. Si falta algo:
  `npm install`.
- **No cambiar de rama.** Los dos repos estan en `master`.
- **Credenciales**: `RIVOO_E2E_EMAIL` y `RIVOO_E2E_PASSWORD` viven en variables de
  entorno. **No se piden por chat y no entran en el repo.**
- **Leer `AGENTS.md` entero** antes de escribir codigo de frontend, y
  `node_modules/next/dist/docs/` antes de escribir codigo de Next.
- **Cada prueba de escritorio necesita su `mockMatchMedia(true)` local y su
  `afterEach`** — el polyfill de `src/test/setup.ts` devuelve SIEMPRE
  `matches:false`. Patron en `staff/[id]/page.test.tsx:104-106,181-195`.
- **Verificacion con EVIDENCIA**: adjuntar la salida real del comando, nunca
  afirmar "pasa".
- **En una ola paralela, cada implementador ejecuta SOLO sus ficheros de test.**
  Las puertas globales las corre el orquestador al final de la ola, sobre el
  arbol quieto. Un agente que muta ficheros mientras otro corre la suite fabrica
  falsos rojos que parecen bugs.
- **El revisor nunca es el implementador.** Cada despacho es un agente NUEVO.

## §4.4 · Comandos

**Frontend** (desde `E:\IdeaProjects\rivoo-frontend`):

```bash
npx tsc --noEmit                          # esperado: 0 errores
npm run test -- --run                     # linea base: 1021 tests en 92 ficheros
npm run test -- --run src/ruta/al.test.tsx # el fichero de una tarea
npm run lint                              # linea base: 0 errores + 5 avisos
npm run build                             # esperado: OK
```

**Backend** (desde `E:\IdeaProjects\rivoo`): **no hay wrapper de Maven** —
`./mvnw` falla en seco. Se usa el `mvn.cmd` de `~/.m2/wrapper/dists`, y **siempre
con `-am`**:

```bash
mvn test -pl staff-service -am
mvn test -pl client-service -am
mvn test -pl appointment-service -am
```

Los tests de integracion con Testcontainers **necesitan Docker**, que **no esta
disponible en esta maquina** (`docker: command not found`, comprobado en el
bloque 8). Los tests de integracion se **escriben y se compilan**, y su ejecucion
queda anotada como deuda con el comando exacto:

```bash
mvn test -P integration-test -pl <modulo> -am
```

## §4.5 · Definicion de "hecho" por tarea

1. El codigo hace lo que dice su tarea, contra los valores de §1 y las decisiones
   de §2 que cita.
2. Sus tests pasan, **con la salida pegada**.
3. **La cobertura se cuenta por rama de ancho, no por fichero**: toda pieza que
   solo existe en escritorio necesita su test con `mockMatchMedia(true)`.
4. El commit sigue §4.2.
5. Si la tarea descubre que una decision de §2 produce algo absurdo **en un caso
   real**, lo reporta como hallazgo de primera, no como nota al pie. Un revisor de
   plan comprueba que la regla sea coherente con el documento; el implementador es
   el unico que la ejecuta contra el mundo.

---

# §5 · TAREAS

## BACKEND

### B1 · Empleados: `includeInactive` y orden estable

**Decide:** D35. **Desbloquea:** D8, D9, D11 (frontend T5).

**Ficheros:**
- Modificar: `staff-service/.../in/web/EmployeeController.java:58-64`
- Modificar: `staff-service/.../application/EmployeeService.java:113-117`
- Modificar: el puerto de entrada correspondiente en `staff-service/.../domain/port/in/`
- Modificar: `staff-service/.../out/persistence/EmployeeJpaRepository.java:18` y su adaptador
- Test: `staff-service/src/test/java/com/rivoo/staff/application/EmployeeServiceTest.java` (o el que exista)

- [ ] **Paso 1: test que falla — el listado por defecto NO trae inactivos**

Un test unitario con Mockito sobre `EmployeeService.list(...)` que verifique que,
con `includeInactive = false`, el repositorio se invoca con el predicado que
excluye inactivos, y con `true` con el que los incluye. Y un segundo test que
afirme el `Sort` por defecto cuando el `Pageable` entrante viene sin orden.

- [ ] **Paso 2: ejecutar y ver que falla**

`mvn test -pl staff-service -am` — FALLA (el metodo no acepta el parametro).

- [ ] **Paso 3: repositorio**

Sustituir `findByActiveTrue(Pageable)` por una consulta **JPQL** parametrizada:

```java
@Query("""
        SELECT e FROM EmployeeJpaEntity e
        WHERE (:includeInactive = true OR e.active = true)
        """)
Page<EmployeeJpaEntity> search(@Param("includeInactive") boolean includeInactive, Pageable pageable);
```

**JPQL, nunca nativa**: el `@Filter` multi-tenant de Hibernate no cubre las
consultas nativas (aviso en `ClientJpaRepository.java:36-38`).

- [ ] **Paso 4: orden por defecto**

En `EmployeeService.list`, si `pageable.getSort().isUnsorted()`, aplicar
`Sort.by(Sort.Order.desc("active"), Sort.Order.asc("firstName"),
Sort.Order.asc("lastName"), Sort.Order.asc("id"))`. Si viene `sort`, se respeta.

- [ ] **Paso 5: controller**

```java
@GetMapping
@PreAuthorize("hasAnyRole('SALON_OWNER','EMPLOYEE')")
public ResponseEntity<Page<EmployeeResponse>> list(
        @RequestParam(defaultValue = "false") boolean includeInactive,
        Pageable pageable) { ... }
```

El **default `false`** es lo que deja intactos al calendario, `/today` y el
asistente.

- [ ] **Paso 6: relajar `@NotEmpty` en `AssignServicesRequest`** (D16b)

Test que falla primero: `POST /api/v1/staff/employees/{id}/services` con
`{ "services": [] }` devuelve **204/200 y deja al empleado sin servicios**, en vez
de 400. Luego `AssignServicesRequest.java:10` pasa de `@NotEmpty` a `@NotNull`.

Va aqui porque **B1 es la unica tarea que toca staff-service** (§3). Sin esto, en
cuanto T4 arregle el contrato del frontend, el unico caso que seguira fallando
sera el legitimo: quitarle a un empleado su ultimo servicio (§1.11.5).

Comprobar que el bucle de `EmployeeService.java:262-270` aguanta la lista vacia:
`deleteByEmployeeId` corre igual y `saveAll` recibe una lista vacia. Si no
aguanta, arreglarlo aqui.

- [ ] **Paso 7: tests en verde, con la salida pegada**
- [ ] **Paso 8: commit** (protocolo §4.2)

`feat(staff): list employees with optional inactive, a deterministic order, and allow clearing services`

---

### B2 · Contadores de visita y `gdprConsentAt`

**Decide:** D36, D37. **Desbloquea:** las columnas Visitas / Ultima visita
(frontend T6, T10) y la tarjeta GDPR.

**Ficheros:** los de §3 para B2.

- [ ] **Paso 1: test que falla en client-service**

`registerVisit` sobre un cliente con `totalVisits = 2` y
`lastVisitAt = 2026-07-01` y una visita de `2026-08-05` deja `totalVisits = 3` y
`lastVisitAt = 2026-08-05`. Segundo caso: una visita **anterior**
(`2026-06-01`) incrementa el contador pero **no retrocede** `lastVisitAt`.

- [ ] **Paso 2: dominio**

En `Client.java`, un metodo `registerVisit(Instant visitAt)` que haga
`totalVisits++` y `lastVisitAt = (lastVisitAt == null || visitAt.isAfter(lastVisitAt)) ? visitAt : lastVisitAt`.
Logica pura, sin Spring — es `domain/model/`.

- [ ] **Paso 3: caso de uso y endpoint interno**

`InternalClientUseCase.registerVisit(String tenantId, String clientExternalId, Instant visitAt)`
y, en `ClientInternalController`:

```java
@PostMapping("/{clientId}/visit")
public ResponseEntity<Void> registerVisit(
        @PathVariable String clientId,
        @RequestParam String tenantId,
        @RequestParam Instant visitAt) { ... }
```

Sigue el patron de los dos endpoints que ya hay ahi (`/{clientId}` y
`/find-or-create`), protegidos por PSK via `InternalEndpointFilter`.

- [ ] **Paso 4: `gdprConsentAt` en `ClientResponse`**

Anadir el componente al record (`ClientResponse.java:5-19`) y su linea en
`ClientDtoMapper`. **Comprobar que el mapper generado lo puebla** — MapStruct no
avisa de un campo que no mapea si el nombre no coincide. Test de exposicion JSON,
como el que ya existe para `isActive`
(`EmployeeResponseJsonTest.java:32-42`).

- [ ] **Paso 5: test que falla en appointment-service**

`updateStatus(id, "COMPLETED")` sobre una cita **con** cliente llama a
`clientServicePort.registerVisit(tenantId, clientId, appointment.getStartTime())`.
Tres casos mas: (a) con `clientId == null` **no** se llama; (b) una transicion
que **no** es a `COMPLETED` no llama; (c) **si el puerto lanza, el cambio de
estado se completa igual** y no se propaga la excepcion.

- [ ] **Paso 6: puerto y adaptador**

Anadir `registerVisit` a `ClientServicePort` y su implementacion en el adaptador
REST, con `@PostExchange`. La configuracion de `InterServiceRestClientConfig` ya
propaga `X-Internal-Service-Key` y `X-Tenant-Id`.

- [ ] **Paso 7: enganche en `updateStatus`**

En `AppointmentService.updateStatus` (`:190-210`), despues de guardar y **dentro
de un try/catch que solo registra un `warn`**:

```java
if (targetStatus == AppointmentStatus.COMPLETED && saved.getClientId() != null) {
    try {
        clientServicePort.registerVisit(TenantContext.get(), saved.getClientId(), saved.getStartTime());
    } catch (Exception ex) {
        log.atWarn().addKeyValue("appointmentId", externalId).setCause(ex)
           .log("Could not register client visit");
    }
}
```

Usar la **fluent API** de SLF4J: es obligatoria en este repo. **No** anadir
`tenantId` ni `correlationId` con `addKeyValue`: ya salen solos por MDC.

- [ ] **Paso 8: los dos modulos en verde, con la salida pegada**

`mvn test -pl client-service -am` y `mvn test -pl appointment-service -am`.

- [ ] **Paso 9: commit** (protocolo §4.2)

`feat(clients): count visits when an appointment completes, and expose gdprConsentAt`

---

### B3 · Historial de citas del cliente

**Decide:** D38. **Depende de:** B2 (comparte `AppointmentService.java` y
`ClientService.java`). **Desbloquea:** la seccion entera de historial (T10).

**Ficheros:** los de §3 para B3.

- [ ] **Paso 1: test que falla en appointment-service**

El endpoint interno por cliente devuelve las citas **ordenadas `startTime DESC`**,
**paginadas**, y un resumen con `totalAppointments` = todas y `billedAmount` =
suma de las `COMPLETED`. Caso obligatorio: un cliente con una `COMPLETED` de 35 €,
una `NO_SHOW` de 75 € y una `CANCELLED` de 35 € da
`totalAppointments = 3`, `billedAmount = 35.00`.

- [ ] **Paso 2: transportar `price`, que YA existe**

`servicePrice` existe en `AppointmentInternalResponse.java:11`,
`AppointmentResponse.java:16` y `Appointment.java:36`. Lo que falta es llevarlo
hasta `ClientAppointmentDto` —que **vive en `client-service`**, no en
appointment-service (§1.11.6 puntos 4 y 5)— y que hoy es
`{id, serviceName, employeeName, startTime, endTime, status}`. El artboard dibuja
el importe como columna propia (`DetalleClienteDesktop:171-183`).

- [ ] **Paso 3: consulta**

`AppointmentJpaRepository.findByClientIdAndTenantId` (`:85`) pasa a devolver
`Page<...>` con `Sort` por `startTime DESC`. El resumen se calcula con dos
consultas agregadas, **no** cargando todas las citas en memoria.

- [ ] **Paso 4: endpoint interno**

`AppointmentInternalController` (`:34-40`) acepta `page` y `size` y devuelve el
sobre con `content` + `summary` de D38.

- [ ] **Paso 5: puerto y adaptador en client-service**

`AppointmentServicePort` gana el metodo paginado. **El adaptador nuevo NO se
traga la excepcion**: la propaga. `AppointmentServiceAdapter.java:52-55` y
`ClientService.java:156-161` conservan su comportamiento actual **solo para el
camino del export** (D38, "salvo que").

- [ ] **Paso 6: endpoint publico**

```java
@GetMapping("/{id}/appointments")
@PreAuthorize("hasAnyRole('SALON_OWNER','EMPLOYEE')")
public ResponseEntity<ClientAppointmentsResponse> appointments(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "7") int size) { ... }
```

`size = 7` por defecto porque es lo que dibuja el artboard de escritorio
(`Mostrando 7 de 14 citas`).

- [ ] **Paso 7: los dos modulos en verde, con la salida pegada**
- [ ] **Paso 8: commit** (protocolo §4.2)

`feat(clients): paginated appointment history endpoint with a billing summary`

---

## FRONTEND

### T1 · Tokens y el helper del color de empleado

**Decide:** D14, y §1.12. **Corre SOLA**: es la unica tarea que toca
`globals.css`.

**Ficheros:**
- Modificar: `src/app/globals.css`
- Modificar: `src/lib/utils/avatar.ts`
- Test: `src/lib/utils/avatar.test.ts`
- Crear: `src/components/staff/employee-color.tsx`, `.test.tsx`

- [ ] **Paso 1: los dos tokens que faltan**

**Cada uno va en DOS sitios.** En `:root`:

```css
--segmented-track: #f0e7dc; /* Equipo.dc.html:27 — carril del control segmentado */
--input-border-attention: #dfb3a8; /* DetalleEmpleadoDesktop.dc.html:218,220 — campo de hora vacio de un dia recien activado */
```

Y en `@theme inline`:

```css
--color-segmented-track: var(--segmented-track);
--color-input-border-attention: var(--input-border-attention);
```

Si falta el mapeo, la utilidad **no se genera** y no hay ni error ni aviso
(`AGENTS.md:44-51`).

- [ ] **Paso 2: comprobar que la utilidad EXISTE de verdad**

No basta con mirar la declaracion. Correr `npm run build` y hacer grep de la
clase generada en `.next/`. Pegar la evidencia.

- [ ] **Paso 3: test que falla para el helper**

En `avatar.test.ts`: dado `colorHex = "#B4522F"`, el helper devuelve
`{ backgroundColor: "#B4522F20", color: "#B4522F" }`; dado `null`, devuelve el
par por defecto que ya usa el repo para un avatar sin color.

- [ ] **Paso 4: el helper**

En `src/lib/utils/avatar.ts`, un `employeeAvatarStyle(colorHex: string | null)`
que sustituya a las dos copias literales de
`{ backgroundColor: employee.colorHex + "20", color: employee.colorHex }`
(`staff/[id]/page.tsx:155`, `employee-card.tsx:24`). **Esta tarea NO cambia los
consumidores** — los cambian T5 y T9, que son sus duenos.

- [ ] **Paso 5: `EmployeeColor`, el componente compartido** (D14)

```tsx
interface EmployeeColorProps {
  colorHex: string | null
  shape: "dot-sm" | "dot" | "square-sm" | "square"  // 10 · 12 · 28 · 32
  showHex?: boolean
  emptyLabel?: string   // "Por defecto" en la tabla de escritorio
}
```

Las cuatro formas de D14 en un solo sitio. Lo crea T1 **precisamente para que T5,
T7 y T9 lo compartan sin que ninguno tenga que tocar los ficheros de los otros**.
Tests: las cuatro formas, el hex visible o no, y el caso `colorHex === null` con y
sin `emptyLabel`.

- [ ] **Paso 6: `SegmentedControl` contra su artboard** (D7)

El componente existe (`src/components/shared/segmented-control.tsx`, 82 lineas) y
**hoy no coincide con los artboards**: pinta `text-sm` (14px) `font-medium` (500)
**uniforme** para activa e inactiva, `px-4` (16px) y carril `bg-muted`
(`#F5EEE6`). Los artboards piden:

| | Artboard | Hoy |
|---|---|---|
| Opcion activa | **13px / 600** | 14px / 500 |
| Opcion inactiva | **13px / 500**, `--muted-foreground` | 14px / 500 |
| Padding horizontal | **18px** | 16px |
| Carril | `--segmented-track` (`#F0E7DC`) | `--muted` (`#F5EEE6`) |
| Radio | `999` contenedor y pastilla | `999` (coincide) |
| Alto de opcion | **32** movil / **30** escritorio | uniforme |

Fuentes: `Equipo.dc.html:27-29` (movil), `EquipoDesktop.dc.html:29-31`
(escritorio), `DetalleEmpleado.dc.html:26-28`.

**Se hace con una VARIANTE, no cambiando el componente entero.** El artboard del
calendario pide **otras** metricas para el mismo control:
`CalendarioDesktop.dc.html:89-91` dibuja `border-radius 9px` en el contenedor y
`6px` en la pastilla (**no** `999px`), `padding 0 14px` (**no** 18px) y carril
`#F5EEE6` — que es `--muted`, justo lo que el componente ya tiene hoy. Un cambio
global alejaria al calendario de su propio artboard.

Asi que: `variant="pill"` (el de Equipo: radio 999, 18px, `--segmented-track`,
13px/600 y 13px/500, alto 32/30) y `variant="square"` (el actual, que se queda
como esta y es el **default**). Ninguna pantalla viva cambia de aspecto.

**Esta tarea es la unica que puede tocarlo**: lo consumen T5 (lista de Equipo) y
T9 (ficha de empleado), en olas distintas, y ninguno puede ser su dueno sin
bloquear al otro. Es tambien lo que da uso al token `--segmented-track` del Paso
1 — sin este paso seria un token muerto.

**Cuidado con `text-[13px]`**: necesita su `leading-*` escrito **DESPUES**
(`AGENTS.md:53-61`).

**El componente hoy no tiene fichero de test.** Se crea, cubriendo los dos altos
(la diferencia 32/30 exige una rama por ancho, con `mockMatchMedia(true)` y su
`afterEach`) y que la pastilla se coloca en la opcion activa.

**Riesgo declarado — y NO es el calendario.** El unico consumidor de
`SegmentedControl` en todo `src` es **`src/app/dev/preview/page.tsx`**.
`calendar/page.tsx:54` dice por escrito que el segmentado Dia/Semana de
escritorio **no se monta** (bloque 3: su segunda opcion no llevaba a ninguna
parte), asi que la red de seguridad que cabria esperar **no existe**.

Consecuencias:
- `src/app/dev/preview/page.tsx` **se anade a `paths_touched` de T1**, por si el
  cambio de props lo rompe. Sin declararlo, el fallo aparece en el `tsc` de T11
  (ola 4) sin dueno.
- Como no hay consumidor de produccion, **este paso no puede romper ninguna
  pantalla viva**. El unico riesgo real es el `tsc`.

- [ ] **Paso 7: `npm run test -- --run src/lib/utils/avatar.test.ts src/components/staff/employee-color.test.tsx src/components/shared/segmented-control.test.tsx` en verde**
- [ ] **Paso 8: commit** (protocolo §4.2)

`feat(design): add the segmented-track and empty-input tokens, and share the employee colour piece`

---

### T2 · El primitivo `DataTable`

**Decide:** D2, D3, D4, D5.

**Ficheros:**
- Crear: `src/components/ui/data-table.tsx`
- Test: `src/components/ui/data-table.test.tsx`

- [ ] **Paso 1: la interfaz, antes de escribir nada**

```tsx
export interface DataTableColumn<T> {
  key: string
  header: string          // "" para la columna del chevron
  width: string           // "minmax(0,1.5fr)" | "170px" | "20px"
  align?: "start" | "end" // por defecto start; "end" para Visitas e Importe
  cell: (row: T) => React.ReactNode
}

export interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string
  variant?: "screen" | "nested"   // D4, por defecto "screen"
  rowHeight?: number              // 68 en las listas, 58 en el historial
  gap?: number                    // 16 en las listas, 12 en el historial
  href?: (row: T) => string       // si viene, la fila entera es un <Link>
  rowClassName?: (row: T) => string | undefined  // fila inactiva (D9)
  footer?: React.ReactNode        // el footer de 48px del historial
  caption: string                 // aria-label de la tabla
}
```

- [ ] **Paso 2: tests que fallan**

1. Pinta una cabecera con `role="columnheader"` por columna, y las cabeceras
   vacias **no** llevan texto pero **si** ocupan su hueco.
2. Pinta una fila con `role="row"` por elemento y una celda por columna.
3. Con `href`, cada fila es un enlace alcanzable por teclado
   (`getAllByRole("link")`) hacia la ruta correcta.
4. `variant="nested"` aplica el alto y el fondo de D4 (comprobar por clase, no
   por pixel).
5. `rowClassName` se aplica a la fila que la devuelve y no a las demas.
6. El separador **no** aparece detras de la ultima fila
   (`EquipoDesktop:189` es el ultimo).
7. `footer` solo se monta si viene.

- [ ] **Paso 3: implementacion**

- Contenedor: `role="table"`, `aria-label={caption}`, `border border-border`,
  `rounded-xl` (12px), `bg-card`, `overflow-hidden`.
- La rejilla se aplica con `style={{ gridTemplateColumns: columns.map(c => c.width).join(" ") }}`
  — **no** con clases de Tailwind: los anchos vienen de datos.
- **`text-[11px]` y `text-[10px]` de la cabecera necesitan su `leading-*`
  escrito DESPUES** (`AGENTS.md:53-61`).
- La separacion entre filas es un `div` de 1px con `bg-hairline`, **a sangre
  completa**, entre filas y **no** tras la ultima.
- **No se dibuja hover** (ningun artboard lo tiene, §1.3), pero **si** foco
  visible: es un requisito de accesibilidad, no una decision de diseno.

- [ ] **Paso 4: `npm run test -- --run src/components/ui/data-table.test.tsx` en verde**
- [ ] **Paso 5: commit** (protocolo §4.2)

`feat(ui): add a grid-based DataTable primitive with screen and nested variants`

---

### T3 · El contenedor `ResponsiveFormModal`

**Decide:** D17.

**Ficheros:**
- Crear: `src/components/shared/responsive-form-modal.tsx`
- Test: `src/components/shared/responsive-form-modal.test.tsx`

- [ ] **Paso 1: tests que fallan — UNO POR RAMA DE ANCHO**

Con `mockMatchMedia(false)`: monta el `Sheet` inferior y **existe el grabber**.
Con `mockMatchMedia(true)` **y su `afterEach`**: monta el `Dialog` centrado y **no
existe el grabber**. Tercer test: `onOpenChange(false)` se propaga en las dos
ramas. Cuarto: el titulo se anuncia como titulo accesible en las dos.

**Sin el `mockMatchMedia(true)`, la rama de escritorio no se ejecuta y el fichero
parece cubierto sin estarlo** (`AGENTS.md:29-42`).

- [ ] **Paso 2: implementacion**

```tsx
interface ResponsiveFormModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  children: React.ReactNode
  footer: React.ReactNode          // el CTA, que cambia de alto por familia
  closeButtonVariant: "plain" | "bordered"  // D17: empleado plain, cliente bordered
  note?: React.ReactNode           // la nota de edicion del formulario de empleado
}
```

- `const isDesktop = useMediaQuery("(min-width: 1024px)")` y **montaje
  condicional**, nunca `lg:hidden` (D28).
- Movil: `Sheet side="bottom"` con `rounded-t-2xl`, `max-h-[85vh]`,
  `overflow-y-auto`, grabber **36x4** `bg-grabber`.
- Escritorio: `Dialog` de **512px**, `p-6`, `border border-border`,
  `rounded-xl` (12, D17), `bg-background`.
- El scrim: `rgba(42,35,32,0.42)` en movil y `0.34` en el modal de empleado /
  `0.42` en el de cliente. **Se unifica en `0.42`**: la diferencia es de una sola
  aparicion y ningun usuario distingue 34 de 42 en un velo. Anotar.

- [ ] **Paso 3: los dos tests de rama en verde, con la salida pegada**
- [ ] **Paso 4: commit** (protocolo §4.2)

`feat(ui): add ResponsiveFormModal, a bottom sheet on mobile and a 512px dialog on desktop`

---

### T4 · La capa de datos: tipos, API y hooks

**Decide:** D11, D16 (la mitad del tipo), D32, D33, D34, y el contrato de
D35/D38.

**Ficheros:**
- Modificar: `src/types/employee.ts`, `src/types/client.ts`
- Modificar: `src/lib/api/staff.ts`, `src/lib/api/clients.ts`
- Modificar: `src/hooks/use-staff.ts`, `src/hooks/use-clients.ts`
- Modificar: **una linea** de `src/app/(app)/staff/[id]/page.tsx:96`
- Test: `src/hooks/use-staff.test.tsx`, `src/hooks/use-clients.test.tsx`, y
  `src/lib/api/clients.test.ts` (**nuevo**)

- [ ] **Paso 1: test que falla — el contrato de asignar servicios**

Un test sobre `staffApi.assignServices` que afirme la **URL y el cuerpo reales**:

```ts
expect(fetchMock).toHaveBeenCalledWith(
  expect.stringContaining("/api/v1/staff/employees/emp_1/services"),
  expect.objectContaining({ body: JSON.stringify({ services: [{ serviceId: "svc_1" }] }) })
)
```

Es el test que no existia y por eso §1.11.1 lleva ahi sin verse. **Mockear
`fetch`, no el modulo de API**: mockear el modulo es exactamente lo que hace que
`use-clients.test.tsx:17-21` no pueda ver una URL mal formada.

- [ ] **Paso 2: corregir los tipos**

En `src/types/employee.ts:51-61`:

```ts
export interface EmployeeServiceResponse {
  serviceId: string
  serviceName: string
  effectiveDuration: number
  effectivePrice: number
  customDuration: number | null
  customPrice: number | null
}

export interface AssignServicesRequest {
  services: { serviceId: string; customDuration?: number; customPrice?: number }[]
}
```

En `src/types/client.ts`: **borrar `dateOfBirth`** (D32) y **NO tocar
`gdprConsentAt`**, que ya existe como `string | null` (`client.ts:13`). Cambiarlo
a `?: string` romperia `gdpr-panel.tsx:23`, cuyo prop es `string | null`, y siete
fixtures que ponen `gdprConsentAt: null`. El tipo del frontend ya era optimista;
B2 lo hace cierto en el servidor, y el frontend no cambia.

- [ ] **Paso 3: el unico consumidor**

`src/app/(app)/staff/[id]/page.tsx:96` pasa de `{ serviceIds }` a
`{ services: serviceIds.map((serviceId) => ({ serviceId })) }`. **Una linea.** El
resto del fichero es de T9.

- [ ] **Paso 4: `size` y `includeInactive`**

```ts
listEmployees: (token: string, opts?: { includeInactive?: boolean; size?: number }) =>
  apiFetch<Page<Employee>>(
    `/api/v1/staff/employees?${buildQuery({
      includeInactive: opts?.includeInactive ? "true" : undefined,
      size: String(opts?.size ?? 100),
    })}`, { token })
```

`listServices` gana `size` (100) igual. Y `useEmployees(opts?)` mete
`includeInactive` **en la queryKey**: el calendario, `/today` y el asistente
comparten `["employees"]` y **deben seguir viendo solo activos** (D34). Como el
parametro es opcional y por defecto `false`, **ningun consumidor actual cambia**.

- [ ] **Paso 5: `useClientAppointments`**

Hook nuevo en `use-clients.ts` contra `GET /api/v1/clients/{id}/appointments`,
con `page` y `size` en la queryKey, y **exponiendo `isError`**: la rama de error
es visible por decision (D38).

- [ ] **Paso 6: limpieza**

Borrar `useCreateClient` (D33). Anadir `size` a la queryKey de `["clients"]` en
los dos lados (D34) — el de `clients/page.tsx` lo hace su dueno, T6; aqui solo el
de `use-clients.ts`.

- [ ] **Paso 7: los tres ficheros de test en verde, con la salida pegada**
- [ ] **Paso 8: commit** (protocolo §4.2)

`fix(staff): send the real assign-services payload, and page employees explicitly`

---

### T5 · `/staff` — la lista de Equipo

**Decide:** D5, D6, D7, D8, D9, D10, D11, D14 (el punto de color), D28, D29.
**Depende de:** T2 (`DataTable`), T4 (hooks), T1 (helper), B1 (datos).

**Ficheros:**
- Modificar: `src/app/(app)/staff/page.tsx`, `src/app/(app)/staff/page.test.tsx`
- Modificar: `src/components/staff/employee-card.tsx`, `.test.tsx`
- Crear: `src/components/staff/employee-table.tsx`, `.test.tsx`

- [ ] **Paso 1: tests que fallan, uno por rama de ancho**

Escritorio (`mockMatchMedia(true)` + `afterEach`): existe `role="table"` con las
seis columnas de §1.3; el contador dice `5 empleados · 4 activos`; la fila
inactiva lleva su clase; `Sin teléfono` y `Por defecto` aparecen; hay un enlace
por fila.
Movil (por defecto): **no** existe `role="table"`; hay cinco tarjetas; el contador
dice `5 empleados`; el CTA "Añadir" esta en el cuerpo, no en la cabecera.

- [ ] **Paso 2: `SegmentedControl` en vez de `Tabs`** (D7)

Conservando el control por `?tab=` y su `router.replace(..., {scroll:false})`.
**Los cuatro tests actuales hablan de `tabpanel`** (`page.test.tsx:130-182`):
se actualizan al nuevo arbol, no se borran. El comportamiento que afirman
(cambio de panel, aterrizaje directo, sin remount) **se conserva**.

- [ ] **Paso 3: `EmployeeTable`** (escritorio)

`DataTable` con las columnas de §1.3, `variant="screen"`, `rowHeight={68}`,
`gap={16}`, `href={(e) => "/staff/" + e.id}`, y `rowClassName` que devuelve el
tinte de la fila inactiva (D9).

- [ ] **Paso 4: `EmployeeCard`** (movil)

Pasa a `<Link>` (D5), usa `employeeAvatarStyle` de T1 en vez de la concatenacion
literal, y pinta el badge de estado segun D9. **Sin chevron** (D5, "salvo que").

- [ ] **Paso 5: los dos `lg:hidden`** (D28)

`:110-120` y `:148-158` pasan a montaje condicional. **Ojo**: el de servicios
pertenece a la pestana que D6 deja intacta — se cambia el mecanismo, **no** su
contenido.

- [ ] **Paso 6: contador** con la regla exacta de D8 — `totalElements` para el
      total, la pagina para el desglose, y **sin desglose** cuando
      `totalElements > content.length`. Test obligatorio: con 150 en
      `totalElements` y 100 filas, la cabecera dice `150 empleados` **y no**
      `· N activos`
- [ ] **Paso 7: sus tres ficheros de test en verde, con la salida pegada**
- [ ] **Paso 8: commit** (protocolo §4.2)

`feat(staff): rebuild the team list as a desktop table and mobile cards`

---

### T6 · `/clients` — la lista de Clientes

**Decide:** D5, D20, D21, D22, D23, D28, D29, D34.
**Depende de:** T2, T4, B2 (visitas).

**Ficheros:**
- Modificar: `src/app/(app)/clients/page.tsx`
- Crear: `src/app/(app)/clients/page.test.tsx` (**hoy no existe ninguno**)
- Modificar: `src/components/clients/client-card.tsx`, `.test.tsx`
- Crear: `src/components/clients/client-table.tsx`, `.test.tsx`

- [ ] **Paso 1: tests que fallan, uno por rama de ancho**

Escritorio: `role="table"` con las cinco columnas de §1.6; `Última visita` con
`formatDate`; `Visitas` alineado a la derecha; `Sin correo` cuando falta el email
y `Sin contacto` cuando no hay nada; la linea `Mostrando N de M · la lista pide
50 por página` **fuera** de la tabla.
Movil: sin tabla; el subtitulo es **una sola linea** `teléfono · email`; el bloque
de visitas a la derecha con la etiqueta `visitas`; **no** hay linea de paginacion.

- [ ] **Paso 2: el buscador** (D20)

`useDeferredValue` -> `useDebouncedValue` de 250 ms + `placeholderData:
keepPreviousData`. Test con **fake timers** que compruebe que tres pulsaciones
seguidas producen **una** peticion, y que la lista **no** se desmonta entre
medias. Para lo segundo, seguir la regla de `AGENTS.md:20-23`: `await findBy*`
sobre algo que el componente no posee antes de afirmar.

- [ ] **Paso 3: `ClientTable` y `ClientCard`** con `formatPhone` (D29) y `<Link>` (D5)
- [ ] **Paso 4: `size` en la queryKey** (D34) y la linea de paginacion con numeros reales (D21)
- [ ] **Paso 5: sus tres ficheros de test en verde, con la salida pegada**
- [ ] **Paso 6: commit** (protocolo §4.2)

`feat(clients): rebuild the client list as a desktop table with a debounced search`

---

### T7 · Formulario de empleado

**Decide:** D17, D18, D30, D31. **Depende de:** T3, T1.

**Ficheros:** `src/components/staff/employee-form.tsx`, `.test.tsx`

- [ ] **Paso 1: tests que fallan — y por fin de la mutacion**

Hoy el fichero **solo** prueba la re-sincronizacion de estado y **nunca ejecuta
las mutaciones** (§1.9). Se anaden, mockeando `staffApi`:
alta llama a `createEmployee` con el cuerpo esperado; edicion llama a
`updateEmployee`; un nombre de solo espacios **no** pasa el guard (D31); el
`detail` del `ProblemDetail` llega al toast (D30). Y **un test de cada rama de
ancho** para el contenedor.

- [ ] **Paso 2: montar sobre `ResponsiveFormModal`** con `closeButtonVariant="plain"` (D17)
- [ ] **Paso 3: alta vs edicion** (D18): el bloque de cuenta de acceso y la contrasena solo en alta; la nota solo en edicion
- [ ] **Paso 4: `trim()`** en validacion y envio (D31); `detail` en los toasts (D30)
- [ ] **Paso 5: el color** — la muestra **32x32** `radius 8` con su hex al lado (D14), con `ui/checkbox` en vez del `<input type=checkbox>` crudo, y **sin** el `#3B82F6` literal: el valor por defecto sale del backend (`EmployeeService.java:81`), asi que el campo vacio no debe mentir pintando azul
- [ ] **Paso 6: sus tests en verde, con la salida pegada**
- [ ] **Paso 7: commit** (protocolo §4.2)

`feat(staff): employee form as a sheet on mobile and a dialog on desktop`

---

### T8 · Formulario de cliente

**Decide:** D17, D18, D30, D31, D32. **Depende de:** T3, T4.

**Ficheros:** `src/components/clients/client-form.tsx`, `.test.tsx`

- [ ] **Paso 1: tests que fallan**, mismo alcance que T7: las mutaciones se ejecutan por primera vez, `trim()`, `detail` en el toast, y una rama por ancho.
- [ ] **Paso 2: montar sobre `ResponsiveFormModal`** con `closeButtonVariant="bordered"` (D17)
- [ ] **Paso 3: alta vs edicion** (D18)
- [ ] **Paso 4: `trim()` y `detail`**
- [ ] **Paso 5: `gender` NO se monta y `dateOfBirth` ya no existe en el tipo** (D32)
- [ ] **Paso 6: tests en verde, con la salida pegada**
- [ ] **Paso 7: commit** (protocolo §4.2)

`feat(clients): client form as a sheet on mobile and a dialog on desktop`

---

### T9 · `/staff/[id]` — la ficha del empleado

**Decide:** D12, D13, D14, D15, D16, D28, D29. **Depende de:** T1, T4, T7, B1.

**Ficheros:**
- Modificar: `src/app/(app)/staff/[id]/page.tsx`, `.test.tsx`
- Modificar: `src/components/staff/service-assignment.tsx`, `.test.tsx`
- Modificar: `src/components/staff/working-hours-editor.tsx`, `.test.tsx`
- **NO crea `employee-color.tsx`**: lo crea T1 (ola 0) y aqui solo se consume

- [ ] **Paso 1: el fallo de borrado, primero** (D16, §1.11.2)

Test que falla: con `assignedServices === undefined`, **no** existe el boton
"Guardar servicios". Luego la guarda, calcada de la de horarios
(`staff/[id]/page.tsx:63-72`). **Este paso va el primero porque T4 acaba de hacer
el fallo alcanzable** al arreglar el contrato.

- [ ] **Paso 2: la rama de error de servicios** (D16): `isError` expuesto y un `EmptyState` con Reintentar, como ya tienen los horarios (`:222-232`)
- [ ] **Paso 3: los tres estados del domingo** (D13)

Tres tests en `working-hours-editor.test.tsx`: dia cerrado cargado; dia recien
activado sin horas (campos `--:--`, borde `--input-border-attention`, banner
visible, **CTA deshabilitado**); dia abierto con horas. **El segundo estado es el
que hoy no existe y el que el banner del artboard describe.**

- [ ] **Paso 3b: `save()` NO se toca** (D13)

Test obligatorio, y es un test de **no-regresion**: con un dia incompleto, llamar
a `save()` por el ref **sigue disparando la mutacion**, exactamente como hoy.

Parece contraintuitivo y por eso lleva test propio: la version obvia del freno
—que `save()` se niegue— **deja un boton mudo** en `(onboarding)/business-hours`
y en Ajustes movil, porque su `handleContinue` es
`try { await save(); router.push(...) } catch {}` y el toast lo lanza el `onError`
de la mutacion, que en ese caso nunca corre. D13 lo explica entero. **No lo
"arregles".**

- [ ] **Paso 3c: correr los tests de las dos pantallas ajenas** (§3, red de seguridad)

Excepcion explicita a §4.3 ("cada implementador ejecuta solo sus ficheros de
test"): `settings/business-hours/page.test.tsx` y
`(onboarding)/business-hours/page.test.tsx` son la **unica** red que detecta si
este freno las rompe, y esperar a T11 significa cerrar dos olas encima de un
fallo. **Se ejecutan en modo lectura, sin tocarlos, y se pega la salida.** Si
caen, el freno se rehace aqui.

- [ ] **Paso 4: el layout** (D12)

Escritorio: tres tarjetas 300 / 386 / 372 con `gap 24`, **sin** segmentado.
Movil: secciones apiladas + segmentado `Horarios` / `Servicios`, con el panel de
servicios reflowed. Montaje condicional en JS, **no** `lg:hidden` (D28).

- [ ] **Paso 5: consumir `EmployeeColor`** (D14) en sus dos formas de detalle: punto **10x10** con caption en movil, cuadrado **28x28** con label y ayuda en escritorio. El componente **lo crea T1**, precisamente para que T5, T7 y T9 lo compartan sin que ninguno tenga que tocar los ficheros de los otros
- [ ] **Paso 6: `4 de 6`, `Horas propias de {nombre}`, `Guardar servicios (N)`** (D15)
- [ ] **Paso 7: `lg:hidden` de `:182`** (D28) y `formatPhone` en `:208` (D29)
- [ ] **Paso 8: `deleteMutation` invalida las cuatro claves**, no solo
      `["employees"]` (§1.9). Son: `["employees"]`, `["employee", id]`,
      `["employee-working-hours", id]` y `["employee-services", id]`
- [ ] **Paso 9: sus tres ficheros de test en verde, con la salida pegada**
- [ ] **Paso 10: commit** (protocolo §4.2)

`fix(staff): guard the service editor, and rebuild the employee detail against its artboards`

---

### T10 · `/clients/[id]` — la ficha del cliente

**Decide:** D5, D21, D23, D24, D25, **D36**, D26, D27, D28, D29. **Depende de:**
T2, T4, T8, B2, B3.

**Ficheros:**
- Modificar: `src/app/(app)/clients/[id]/page.tsx`
- Crear: `src/app/(app)/clients/[id]/page.test.tsx` (**hoy no existe**)
- Modificar: `src/components/clients/gdpr-panel.tsx`
- Crear: `src/components/clients/gdpr-panel.test.tsx` (**hoy no existe, y es codigo destructivo**)
- Crear: `src/components/clients/client-appointment-history.tsx`, `.test.tsx`
- Modificar: `src/app/(fullscreen)/appointments/new/page.tsx` (D26)
- Modificar: `src/lib/stores/wizard-store.ts` — **solo `preferredClientId`** (D26)
- Modificar: `src/components/appointments/wizard/client-step.tsx` — **solo consumir
  `preferredClientId`** (D26)
- Modificar: `src/components/appointments/wizard/client-step.test.tsx` — **caera
  seguro**: su `:10` mockea el modulo entero con
  `vi.mock("@/hooks/use-clients", () => ({ useClients: vi.fn() }))`, asi que
  cualquier hook nuevo del modulo sale `undefined` y cualquier `useQuery` inline
  se queda sin `QueryClientProvider`. Sus siete tests son de esta tarea

- [ ] **Paso 1: la rama de error** (§1.11.3)

Test que falla: con la query en error, la pantalla pinta un estado de error, **no
un esqueleto perpetuo**. Separar `isLoading` de `!client` en `:31-37`.

- [ ] **Paso 2: los dos `lg:hidden`** (D28): `:69` y `:76-86` pasan a montaje condicional, y desaparecen los dos "Editar" simultaneos del DOM
- [ ] **Paso 3: el layout** (D25)

Escritorio: dos columnas (izquierda **400 fija**, derecha `flex-grow`), con
`Nueva cita` y `Editar` con texto en la topbar, el nombre como titulo, y el badge
`Reserva online`.
Movil: apilado, `Detalle cliente` como titulo, boton `Llamar`, editar como icono.

- [ ] **Paso 4: el historial** (D23, D24)

`ClientAppointmentHistory` con `DataTable` `variant="nested"`, `rowHeight={58}`,
`gap={12}` en escritorio y una lista de tres items en movil; cabecerilla con
`{N} citas · {importe} facturados`; footer de 48px con `Mostrando N de M citas`
**sin** el enlace "Ver todas" (D24); importe atenuado en `NO_SHOW` y `CANCELLED`
(D25); rama de error propia (el endpoint de B3 propaga el fallo, D38); y el vacio
de D23.

- [ ] **Paso 4b: los dos KPIs salen del RESUMEN, no del contador** (D36)

`Visitas` = `summary.completedCount`. `Última visita` = `summary.lastCompletedAt`,
con `—` si es nulo (D21). **No** `client.totalVisits` ni `client.lastVisitAt`.

Sin este paso, el dia 1 la ficha pinta `Visitas 0 · Última visita —` a centimetros
de `14 citas · 612,00 € facturados`, en la misma pantalla: la contradiccion que
D36 existe para cerrar. **Test obligatorio**: con `client.totalVisits = 0` y un
resumen de `completedCount: 11`, el KPI dice **11**.

(La LISTA si usa los contadores almacenados — pedir el historial de 50 filas
serian 50 llamadas. La diferencia temporal entre las dos pantallas esta declarada
en D36 y es esperada.)

- [ ] **Paso 5: GDPR** (D27): tokens en vez de `orange-*`, y `gdprConsentAt` con
      `formatDate` (D21). **Los dos botones YA llevan `disabled={isPending}`**
      (`:75`, `:111`): lo que falta es `disabled` en `Cancelar` y bloquear el
      `onOpenChange` del dialogo mientras corre la anonimizacion. **Primer
      fichero de test de un componente destructivo**: cubrir exportar, anonimizar,
      y sobre todo los dos huecos reales — que `Cancelar` no responda y que `Esc`
      no cierre durante la mutacion
- [ ] **Paso 6: `Nueva cita` lleva el cliente** (D26)

Ya se sabe que **no es simetrico** con `employeeId`/`date`/`time`: esos son
preferencias `string` (`appointments/new/page.tsx:48-57`) y `selectedClient`
guarda el `Client` completo (`wizard-store.ts:20,104`). Hace falta un
`preferredClientId` en el store y que `client-step.tsx` lo resuelva. Los tres
ficheros son de esta tarea.

**Si al abrirlos resulta que arrastra el paso 4 entero o cambia el flujo del
asistente, la salida NO es enlazar sin parametro** — eso produce exactamente el
boton mudo que D26 llama peor que nada. La salida es **no montar el boton** y
anotarlo, igual que D24 hace con "Ver todas".

**Los tests del asistente tienen que seguir en verde.** Si caen, es un fallo de
este paso.
- [ ] **Paso 7: `formatPhone`** en `:116` (D29) y `formatDate` en `:40` y `:98` (D21)
- [ ] **Paso 8: sus tres ficheros de test en verde, con la salida pegada**
- [ ] **Paso 9: commit** (protocolo §4.2)

`feat(clients): rebuild the client detail with its appointment history`

---

### T11 · Spec visual y puertas globales

**Ficheros:** `visual/equipo-clientes.spec.ts` (nuevo), `AGENTS.md` si aparece
una trampa nueva.

- [ ] **Paso 1: las puertas, sobre el arbol QUIETO**

Con todas las tareas commiteadas y ningun agente escribiendo:

```bash
npx tsc --noEmit          # esperado: 0 errores
npm run test -- --run     # esperado: >= 1021 tests, 0 fallos
npm run lint              # esperado: 0 errores, <= 5 avisos
npm run build             # esperado: OK
```

**Pegar las cuatro salidas.** Si el numero de tests bajo respecto a 1021, hay
tests borrados: investigar antes de seguir.

- [ ] **Paso 2: backend**

```bash
mvn test -pl staff-service -am
mvn test -pl client-service -am
mvn test -pl appointment-service -am
```

- [ ] **Paso 3: la spec visual**

Doce capturas, una por artboard, a **390x844** y **1440x900**, con el "que mirar"
de cada pantalla escrito en el fichero. **Avisos esperados que NO son fallos**:
- `max-w-[1084px]` de `page-shell.tsx:131` frente a los 1136px que dibuja el
  artboard a 1440 (deuda 11 de `tasks/todo.md`, comun a doce rutas).
- Las fechas, que este bloque unifica a `formatDate` a proposito (D21).
- El radio del modal de cliente, unificado a 12 (D17).
- El scrim, unificado a 0.42 (T3).

- [ ] **Paso 4: el punto que D21 manda comprobar**

El KPI `Última visita` a **390px** con `12 ago 2026` a 21px. Si desborda, se baja
el tamano del KPI, **no** el formato.

- [ ] **Paso 5: NO ejecutar la spec sin la pila**

Necesita el stack levantado y credenciales por variable de entorno. Si no estan,
**se anota como deuda con el comando exacto**, igual que se hizo en el bloque 5.
No se falsea.

```bash
RIVOO_E2E_EMAIL=... RIVOO_E2E_PASSWORD=... npx playwright test visual/equipo-clientes.spec.ts
```

- [ ] **Paso 6: commit** (protocolo §4.2)

---

### T12 ‖ T13 (ola 5) · T14 (ola 6, sola) · Panel de tres revisores independientes

Tres agentes **nuevos**, de **solo lectura**, **en paralelo**, con **lentes
distintas**, e instruidos para **REFUTAR**. Un hallazgo se descarta si la mayoria
lo refuta.

- **T12 — Lente 1: fidelidad al artboard.** Compara valor a valor lo construido
  contra §1.3-§1.8. Su pregunta es "esto no coincide con el artboard, y aqui esta
  el `fichero:linea` de los dos lados".
- **T13 — Lente 2: correccion y seguridad.** Contratos con el backend, nulabilidad
  real, claves de cache, el camino destructivo del panel GDPR, multi-tenant, y las
  cinco ramas de ancho. Su pregunta es "esto se rompe con estos datos concretos".
- **T14 — Lente 3: calidad de los tests.** **Muta el codigo fuente y comprueba si
  algun test cae.** Es la lente que mas valor dio en el bloque 5 (52 mutaciones,
  11 supervivientes). Devuelve el arbol limpio al terminar, y **corre sola**: un
  agente que muta ficheros en paralelo con otro que ejecuta la suite fabrica
  falsos rojos que parecen bugs.

**T14 corre DESPUES de T12 y T13**, por eso. Las tres lentes son de solo lectura
salvo las mutaciones temporales de T14.

Al cerrar el panel: volcado de deudas a `E:\IdeaProjects\rivoo\tasks\todo.md` y
lecciones a `tasks/lessons.md`.

---

# Execution Order

**Backend (`E:\IdeaProjects\rivoo`):**

```
Ola B0   B1  Empleados: includeInactive + orden estable    ┐ sin dependencias
         B2  Contadores de visita + gdprConsentAt          ┘ ficheros disjuntos, en paralelo

Ola B1   B3  Historial de citas del cliente                  depende de B2
                                                             (comparte AppointmentService y ClientService)
```

**Frontend (`E:\IdeaProjects\rivoo-frontend`):**

```
Ola 0    T1  Tokens, helper de avatar y EmployeeColor        SOLA (unica que toca globals.css)

Ola 1    T2  DataTable                    ┐
         T3  ResponsiveFormModal          ├ sin dependencias entre si
         T4  Tipos, API y hooks           ┘

Ola 2    T5  /staff lista           (T2, T4, T1, B1)  ┐
         T6  /clients lista         (T2, T4, B2)      ├ ficheros disjuntos, en paralelo
         T7  Formulario empleado    (T3, T1)          │
         T8  Formulario cliente     (T3, T4)          ┘

Ola 3    T9  /staff/[id]            (T1, T4, T7, B1)  ┐ en paralelo
         T10 /clients/[id]          (T2, T4, T8, B2, B3) ┘

Ola 4    T11 Spec visual y puertas globales, sobre el arbol quieto

Ola 5    T12 Lente 1: fidelidad al artboard   ┐ en paralelo
         T13 Lente 2: correccion y seguridad  ┘

Ola 6    T14 Lente 3: calidad de los tests      SOLA — muta ficheros a proposito
```

**Coordinacion:**

- Los dos repos arrancan **a la vez**. El contrato del backend esta congelado en
  §2.9 (D35-D38), asi que el frontend codifica y prueba contra el sin esperar.
- El frontend solo **necesita** el backend en T11, que es la verificacion contra
  la pila real. B0 tiene que estar cerrada antes de la Ola 2 **si se quiere
  verificar T5 a mano**; los tests no lo necesitan.
- B3 tiene que estar cerrada antes de la Ola 3, porque T10 construye contra su
  contrato y su rama de error.
- Las puertas globales las corre el **orquestador**, no los implementadores, y
  siempre sobre el arbol quieto (§4.3).
- El panel de la Ola 5 se lanza **al terminar el bloque entero**, nunca por
  tarea.

---

# Dependencies on other specs/FRs

| Spec / bloque | Relacion | Implicacion para este bloque |
|---|---|---|
| **Bloque 2** — Shell de escritorio (`docs/specs/shell-escritorio/`) | **Pre-requisito, ya cerrado** | Las cuatro rutas ya viven sobre `PageShell`, con barra lateral, topbar de 72px y `max-w-[1084px]`. Este bloque **reconstruye el interior y no toca el chasis**. `page-shell.tsx` y `(app)/layout.tsx` no se modifican. |
| **Bloque 3** — Calendario (`docs/specs/calendario/`) | **Complementario** | Comparte `useEmployees` y `useEmployeesWorkingHours`. D34 y D35 estan escritas para que el calendario **siga viendo solo activos**: `includeInactive` es opcional y por defecto `false`. Cualquier cambio que rompa eso es un fallo, no un efecto colateral aceptable. |
| **Bloque 5** — Pantalla "Hoy" (`docs/specs/pantalla-hoy/`) | **Complementario, cerrado** | Mismo consumo de `useEmployees`; misma consideracion que el bloque 3. Ademas dejo los tokens `--surface-now*` que D13 reutiliza para el dia recien activado. |
| **Bloque 8** — Asistente de nueva cita (`docs/specs/asistente-nueva-cita/`) | **Consumidor, cerrado** | D26 anade `clientId` a la query de `/appointments/new`. Es el unico fichero de un bloque cerrado que este plan toca, y solo para anadir un parametro simetrico a los tres que ya lee. Si exige mas, la tarea para y anota. |
| **Bloque 7** — Ajustes | **Sin relacion** | Ningun fichero en comun. `Horario*.dc.html` pertenece a `settings/business-hours`, no a este bloque (§1.1). |
| **Catalogo de Servicios** (segunda pestana de `/staff`) | **Vecino intocable** | D6: el control se conserva, su panel no se reconstruye. `src/components/services/**` no se toca. |

---

# Deudas que este bloque dejara anotadas (se vuelcan en T14, no antes)

**Frontend**
- `EmployeeFormSheet` / `ClientFormSheet` conservan el nombre aunque en escritorio
  ya no sean una hoja (D19).
- **El buscador de clientes no encuentra por nombre completo** (`LIKE` sobre
  `firstName` **o** `lastName` por separado, `ClientJpaRepository.java:40-47`).
  Con 248 clientes y sin paginacion, un cliente fuera de los 50 primeros solo es
  alcanzable buscando por UN nombre, telefono o email (D22). **Deuda de producto,
  no cosmetica.**
- **La LISTA y la FICHA daran cifras distintas de visitas para siempre**, no solo
  el dia 1: la lista cuenta desde el despliegue, la ficha cuenta todo (D36). Un
  cliente con 11 citas previas y 1 posterior dira `12` en su ficha y `1` en la
  lista. Se paga con un recomputo, no con tiempo.
- **Anonimizar un cliente NO borra su historial de citas** (vive en
  appointment-service). La ficha de un anonimizado seguiria mostrando visitas,
  ultima visita y la tabla completa. **Incumplimiento potencial de RGPD, no
  mejora** (D36).
- En `settings/business-hours` y `(onboarding)/business-hours`, un dia activado
  sin horas sigue produciendo un **400 en ingles** en vez de una instruccion: el
  freno de D13 no llega ahi porque su `catch {}` se tragaria el rechazo.
- "Ver todas" del historial no se monta y el movil solo pinta 3 de las 7 citas
  descargadas; el footer `Mostrando 3 de 14` es lo unico que delata las otras
  once (D24).
- Sin controles de paginacion en `/clients`: por encima de 50 el camino es el
  buscador (D22).
- `size=100` en empleados y servicios: por encima se trunca en silencio (D11).
- `gender` existe en el backend y no se monta (D32).
- Sin validacion de formato de email ni UI de error por campo (D31).
- El dialogo de anonimizar sigue sin pedir escribir el nombre para confirmar
  (D27); lo que si se arregla es `Cancelar` y el cierre durante la mutacion.
- `LoadingSkeleton` trae `p-4` propio que se suma al `p-4` de `PageShell:191`.
- `max-w-[1084px]` frente a los 1136px del artboard: comun a doce rutas, no se
  toca aqui.

**Backend**
- **Sin backfill de contadores de visita** (D36). Las citas ya `COMPLETED` no se
  cuentan. Salida si algun dia hace falta: endpoint interno de recomputo.
- **`@NotEmpty` de `AssignServicesRequest` se relaja a `@NotNull`** (D16b). Si
  algun dia aparece un consumidor que dependa del 400, hay que revisarlo.
- El `search` de clientes sigue sin buscar por nombre completo: el arreglo es una
  linea de JPQL pero cambia el paso 4 del asistente, que no es de este bloque
  (D22).
- `sort` sigue descartandose en silencio en clientes (D39).
- Los clientes anonimizados siguen apareciendo en el listado (D39).
- No hay reactivacion de empleado, ni comprobacion de citas futuras al
  desactivar, ni desconexion de Keycloak (D39).
- Anonimizar un cliente **no** cancela sus citas futuras, pese a lo que dice
  `client-service/CLAUDE.md` (D39).
- El rol `EMPLOYEE` sigue viendo el listado completo del salon (D39).
- `search` de clientes no busca por nombre completo ni en `notes`, y hace
  `LIKE '%x%'` sobre cuatro columnas sin indice (§1.10).
- Los tests de integracion con Testcontainers se escriben pero **no se ejecutan**:
  no hay Docker en esta maquina (§4.4).

**Diseno / canvas**
- El canvas usa dos formatos de fecha para el mismo dato; este bloque unifica a
  `formatDate` (D21).
- Ningun artboard de este bloque dibuja estado vacio, ni de carga, ni de error
  (D23).
- El scrim tiene dos opacidades (0.42 y 0.34) sin motivo aparente; unificado a
  0.42 (T3).
- El radio del modal de cliente (`16 16 12 12`) es de aparicion unica; unificado
  a 12 (D17).
- `FormularioClienteDesktop` reproduce mal la pantalla de fondo: diverge de
  `ClientesDesktop` en doce valores. Es el fichero **menos fiable** de los doce y
  no se usa como fuente para nada que no sea el propio modal.
- La barra inferior de movil marca `Mas` como pestana activa estando en
  `/clients`, y no hay item `Clientes` en movil.

---

# Revision del plan

Este documento pasa por **un revisor de plan independiente** antes de ejecutarse.
El revisor comprueba coherencia interna, no verdad sobre el mundo: una regla
puede ser perfectamente coherente con el documento y equivocarse. Por eso §4.5
punto 5 pide a los implementadores que **reporten lo que la regla produce en
casos reales**.

---

# Handoff

Con el plan aprobado:

1. Volcar el plan a `E:\IdeaProjects\rivoo\tasks\todo.md` con casillas antes de
   empezar, e ir marcandolas.
2. Elegir motor de ejecucion — **es una decision del usuario**, no de este
   documento (complejidad "muy compleja", §Complejidad):
   - **A — `executing-plans`** (model-driven, por defecto): mas barato,
     adaptativo. Es el motor con el que se cerraron los bloques 2, 3, 5 y 8.
   - **B — `Workflow`** (code-driven): fan-out masivo + fan-in con panel de
     refutacion. Consume tokens de forma agresiva.
3. Regla en vigor: **el revisor se lanza al terminar el BLOQUE ENTERO, no por
   tarea.** Nada de ciclo spec-review + quality-review despues de cada tarea.
