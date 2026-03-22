# Rivoo — Wireframes de Interfaz de Usuario

**Version**: 1.0
**Fecha**: 2026-03-22
**Stack**: Next.js 14 + Shadcn/UI + Tailwind CSS
**Auth**: Keycloak OIDC (PKCE)
**Dispositivo primario**: Mobile 375px
**Idioma**: Castellano (Barcelona)

---

## Notas de Diseño

### Sistema de Componentes (Shadcn/UI)
- `Sheet` — bottom sheets y drawers laterales
- `Card` — tarjetas de citas y empleados
- `Badge` — estados (PENDIENTE, CONFIRMADA, etc.)
- `Avatar` — fotos de empleados y clientes
- `ScrollArea` — listas y timelines con scroll
- `Dialog` — confirmaciones destructivas
- `Command` — búsqueda de clientes con fuzzy search
- `Calendar` — selector de fecha
- `Tabs` — vista dia/semana/mes en calendario

### Paleta de Color (Tailwind)
```
Fondo principal    : bg-background (white / zinc-950)
Fondo secundario   : bg-muted (zinc-100 / zinc-900)
Acento primario    : bg-primary (zinc-900 / white)  — botones CTA
Acento destructivo : bg-destructive (red-600)
Badges de estado:
  PENDIENTE  → bg-yellow-100 text-yellow-800
  CONFIRMADA → bg-green-100 text-green-800
  EN CURSO   → bg-blue-100 text-blue-800
  COMPLETADA → bg-zinc-100 text-zinc-600
  CANCELADA  → bg-red-100 text-red-700
```

### Tipografia
- Heading: `font-semibold text-base` (salones = marca local, no bold extremo)
- Body: `text-sm text-foreground`
- Captions: `text-xs text-muted-foreground`

---

## Notacion de Wireframes

```
[ Boton ]          = boton clickable
[___texto___]      = campo de texto / input
< elemento >       = seleccionable / pill
{ icono }          = icono (Lucide)
* elemento *       = elemento activo / seleccionado
(x)                = badge / indicador numerico
---                = separador de seccion
~~~                = area con scroll
...                = contenido que continua
```

---

## 1. Barra de Navegacion Inferior (Bottom Nav)

Componente global persistente en todas las pantallas autenticadas.
Altura: 64px + safe area iOS/Android.

```
┌─────────────────────────────────────────┐
│                                         │
│  {Casa}    {Cal}    {Users}    {Menu}  │
│   Hoy      Citas    Equipo      Mas    │
│    *                                    │
└─────────────────────────────────────────┘
```

Detalle de estados activo/inactivo:

```
┌─────────────────────────────────────────┐
│                                         │
│ ┌───────┐ ┌───────┐ ┌───────┐ ┌──────┐ │
│ │{Casa} │ │{Cal}  │ │{User} │ │{Menu}│ │
│ │ Hoy   │ │ Citas │ │Equipo │ │ Mas  │ │
│ │  * *  │ │       │ │       │ │      │ │
│ └───────┘ └───────┘ └───────┘ └──────┘ │
│   activo   inactivo  inactivo  inactivo │
└─────────────────────────────────────────┘

Activo   : icono + label en color primario, punto indicador
Inactivo : icono + label en muted-foreground
Badge    : numero sobre icono (ej: citas pendientes de confirmar)
```

---

## 2. Pantalla "Hoy" — Dashboard Principal

Vista inicial al abrir la app. Muestra el resumen del dia y el timeline de citas de hoy.

```
┌─────────────────────────────────────────┐  <- status bar
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  Buenos dias, Maria           {Campana} │  <- saludo + notificaciones
│  Martes, 22 de marzo                    │  <- fecha de hoy
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ {Calendario} │  │   {Reloj}    │    │  <- stat cards
│  │      8       │  │      2       │    │
│  │ citas hoy    │  │  pendientes  │    │
│  └──────────────┘  └──────────────┘    │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  Proxima cita                           │  <- next appointment highlight
│  ┌─────────────────────────────────┐   │
│  │  {Avatar}  Ana Garcia           │   │
│  │  10:00 · Corte + Tinte · 90min  │   │
│  │  {Scissors} Laura (empleada)    │   │
│  │                     [Confirmar] │   │
│  └─────────────────────────────────┘   │
│                                         │
├─────────────────────────────────────────┤
│  Timeline de hoy                {Hoy >} │
│ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│  │                                      │
│  │  09:00 ─────────────────────────    │
│  │  ┌───────────────────────────────┐  │
│  │  │ {Av} Carla Ruiz              │  │
│  │  │ Manicura francesa · 60min     │  │
│  │  │ Sofia · CONFIRMADA {check}    │  │  <- cita confirmada
│  │  └───────────────────────────────┘  │
│  │                                      │
│  │  10:00 ─────────────────────────    │
│  │  ┌───────────────────────────────┐  │
│  │  │ {Av} Ana Garcia              │  │
│  │  │ Corte + Tinte · 90min        │  │
│  │  │ Laura · PENDIENTE {punto}     │  │  <- pendiente de confirmar
│  │  └───────────────────────────────┘  │
│  │                                      │
│  │  11:30 ─────────────────────────    │
│  │  ┌───────────────────────────────┐  │
│  │  │ {Av} Pedro Sanchez           │  │
│  │  │ Afeitado clasico · 30min      │  │
│  │  │ Marc · CONFIRMADA {check}     │  │
│  │  └───────────────────────────────┘  │
│  │                                      │
│  │  12:00 ─────────────────────────    │
│  │  (libre)                            │
│  │                                      │
│  │  ...                                 │
│ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│                                         │
├─────────────────────────────────────────┤
│  {Casa}   {Cal}   {Users}   {Menu}    │
│   Hoy *   Citas   Equipo     Mas      │
└─────────────────────────────────────────┘
                              ↑
                    ┌─────────────┐
                    │  {Plus}     │  <- FAB: Nueva Cita
                    │  Nueva cita │     posicion: bottom-right
                    └─────────────┘     sobre el nav bar
```

**Comportamiento**:
- Tap en tarjeta de cita -> bottom sheet de detalle (pantalla 5)
- Tap en "Confirmar" en proxima cita -> confirma directamente, badge cambia a CONFIRMADA
- FAB siempre visible, abre flujo "Nueva Cita" (pantalla 4)
- Pull-to-refresh recarga datos del servidor
- Scroll en el timeline es independiente del scroll de la pagina

---

## 3. Vista Calendario — Citas (Tab: Citas)

Vista de dia con columnas por empleado. En mobile, una columna a la vez con selector de empleado.

```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  Citas               {Filtro} {Buscar} │  <- header
│                                         │
│  < Hoy >     Mar 22 de marzo >         │  <- navegacion de fecha
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  Empleado:                              │  <- selector horizontal (pills)
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│  * Laura *  < Sofia >  < Marc >  Todos  │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  {Laura — avatar mini} Laura Martinez  │  <- columna activa
│                                         │
│ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│  08:00 │                                │
│  ──────┤                                │
│  08:30 │                                │
│  ──────┤                                │
│  09:00 │ ┌─────────────────────────┐   │
│  ──────┤ │ Carla Ruiz              │   │
│  09:30 │ │ Corte y secado          │   │
│  ──────┤ │ 60min · 35€             │   │
│  10:00 │ │ CONFIRMADA              │   │
│  ──────┤ └─────────────────────────┘   │
│  10:30 │                                │
│  ──────┤ ┌─────────────────────────┐   │
│  11:00 │ │ Ana Garcia              │   │
│  ──────┤ │ Corte + Tinte           │   │
│  11:30 │ │ 90min · 65€             │   │
│  ──────┤ │ PENDIENTE               │   │
│  12:00 │ └─────────────────────────┘   │
│  ──────┤                                │
│  12:30 │  (libre — tap para crear)     │  <- slot vacio es clickable
│  ──────┤                                │
│  13:00 │ ┌─────────────────────────┐   │
│        │ │ Bloqueo: Almuerzo       │   │  <- bloqueo manual
│  14:00 │ └─────────────────────────┘   │
│  ──────┤                                │
│  ...   │                                │
│  21:00 │                                │
│ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│                                         │
├─────────────────────────────────────────┤
│  {Casa}   {Cal}   {Users}   {Menu}    │
│   Hoy    * Citas * Equipo     Mas     │
└─────────────────────────────────────────┘
                              ↑
                    ┌─────────────┐
                    │  {Plus}     │  <- FAB
                    │  Nueva cita │
                    └─────────────┘
```

**Colores de bloques** (border-left coloreado por empleado):
```
Laura  : border-l-4 border-violet-500
Sofia  : border-l-4 border-sky-500
Marc   : border-l-4 border-emerald-500
```

**Comportamiento**:
- Swipe horizontal en el area del calendario -> cambia de dia
- Tap en pill de empleado -> filtra columna
- Tap en slot vacio -> abre "Nueva Cita" con fecha/hora y empleado pre-rellenados
- Tap en bloque de cita -> bottom sheet de detalle
- Selector "Todos" muestra scroll horizontal de columnas una por empleado (ver wireframe desktop)

---

## 4. Nueva Cita — Flujo Multi-Paso (Bottom Sheet)

Sheet que sube desde abajo, ocupa ~90% de la pantalla. Tiene indicador de paso en la cabecera.

### 4A. Step 1 — Seleccionar Empleado

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │  <- drag handle
│                                         │
│  Nueva cita            [X] cerrar       │
│  Paso 1 de 5: Empleado                  │
│  ●────○────○────○────○                  │  <- progress dots
│                                         │
│  Quien lo atendera?                     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar} Laura Martinez        │   │  <- empleada seleccionada
│  │  Estilista · Disponible         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar} Sofia Ramos           │   │
│  │  Colorista · Disponible         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar} Marc Torres           │   │
│  │  Barbero · Ocupado 10:00-11:00  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar-generic} Sin preferencia│  │
│  │  Cualquier empleado disponible  │   │
│  └─────────────────────────────────┘   │
│                                         │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │       [Siguiente →]             │   │  <- CTA primario
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

### 4B. Step 2 — Seleccionar Servicio

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │
│                                         │
│  Nueva cita            [X] cerrar       │
│  Paso 2 de 5: Servicio                  │
│  ●────●────○────○────○                  │
│                                         │
│  [___Buscar servicio...___] {Lupa}      │  <- busqueda
│                                         │
│  Cortes                                 │  <- categoria
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ Corte de pelo                   │   │
│  │ 45 min · 25€            {Check} │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ * Corte + Secado *              │   │  <- servicio seleccionado
│  │ 60 min · 35€            {Check} │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Color                                  │  <- categoria
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ Tinte completo                  │   │
│  │ 90 min · 55€                    │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ Mechas                          │   │
│  │ 120 min · 75€                   │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Siguiente →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
```

### 4C. Step 3 — Elegir Fecha y Hora

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │
│                                         │
│  Nueva cita            [X] cerrar       │
│  Paso 3 de 5: Fecha y hora              │
│  ●────●────●────○────○                  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  < Marzo 2026 >                 │   │  <- mini calendar (Shadcn)
│  │  Lu Ma Mi Ju Vi Sa Do          │   │
│  │   -  -   -   -  -  -  1       │   │
│  │   2  3   4   5  6  7  8       │   │
│  │   9 10  11  12 13 14 15       │   │
│  │  16 17  18  19 20 21 *22*     │   │  <- hoy seleccionado
│  │  23 24  25  26 27 28 29       │   │
│  │  30 31                        │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Horas disponibles · Martes 22 mar      │
│                                         │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐  │  <- grid de slots
│  │9:00│ │9:30│ │10:0│ │10:3│ │11:0│  │
│  └────┘ └────┘ └────┘ └────┘ └────┘  │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐  │
│  │11:3│ │12:0│ │    │ │    │ │    │  │  <- grises = no disponible
│  └────┘ └────┘ └────┘ └────┘ └────┘  │
│  ┌──────┐                             │
│  │*10:30│                             │  <- seleccionado = primario
│  └──────┘                             │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Siguiente →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
```

### 4D. Step 4 — Seleccionar Cliente

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │
│                                         │
│  Nueva cita            [X] cerrar       │
│  Paso 4 de 5: Cliente                   │
│  ●────●────●────●────○                  │
│                                         │
│  [___Buscar cliente por nombre...___]   │  <- Command/fuzzy search
│                                         │
│  Recientes                              │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Ana Garcia                 │   │
│  │ ultima visita hace 2 semanas    │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Carla Ruiz                 │   │
│  │ ultima visita hace 1 mes        │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Pedro Sanchez              │   │
│  │ ultima visita hace 3 semanas    │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │  {UserPlus}  Crear nuevo cliente│   │  <- crear cliente nuevo
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Siguiente →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
```

### 4E. Step 5 — Confirmacion

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │
│                                         │
│  Nueva cita            [X] cerrar       │
│  Paso 5 de 5: Confirmar                 │
│  ●────●────●────●────●                  │
│                                         │
│  Resumen de la cita                     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │  {Av} Ana Garcia                │   │
│  │  +34 612 345 678                │   │
│  │                                 │   │  <- tarjeta resumen
│  │  ─────────────────────────────  │   │
│  │                                 │   │
│  │  {Scissors} Corte + Secado      │   │
│  │  60 minutos                     │   │
│  │                                 │   │
│  │  {Avatar} Laura Martinez        │   │
│  │                                 │   │
│  │  {Cal}  Martes 22 de marzo      │   │
│  │  {Reloj} 10:30 — 11:30          │   │
│  │                                 │   │
│  │  ─────────────────────────────  │   │
│  │                                 │   │
│  │  Total: 35,00 €                 │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  {Mail} Enviar confirmacion al cliente  │  <- toggle (activado por defecto)
│                                         │
│  ┌─────────────────────────────────┐   │
│  │         [ Reservar ]            │   │  <- CTA final, color primario
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐                      │
│  │  [← Atras]   │                      │
│  └──────────────┘                      │
│                                         │
└─────────────────────────────────────────┘
```

**Estado de exito tras "Reservar"**:
```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │
│                                         │
│              {Check-circulo}            │
│                                         │
│          Cita creada con exito          │
│                                         │
│  Ana Garcia · Martes 22 mar · 10:30     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      [ Ver en el calendario ]   │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      [ Nueva cita ]             │   │
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## 5. Detalle de Cita — Bottom Sheet

Se abre al tocar cualquier cita en el dashboard o el calendario.

```
┌─────────────────────────────────────────┐
│                  ▬▬▬                    │  <- drag handle
│                                         │
│  Detalle de cita          [X] cerrar   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  PENDIENTE                      │   │  <- Badge de estado
│  └─────────────────────────────────┘   │
│                                         │
│  {Avatar grande} Ana Garcia             │
│  {Telefono} +34 612 345 678  [Llamar]  │  <- tap en tel o en [Llamar]
│  {Mail} ana.garcia@email.com            │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  {Scissors} Corte + Secado              │
│             60 minutos · 35,00 €        │
│                                         │
│  {Avatar}   Laura Martinez              │
│             Estilista                   │
│                                         │
│  {Cal}      Martes 22 de marzo          │
│  {Reloj}    10:30 — 11:30               │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Notas                                  │
│  (sin notas)                            │  <- o mostrar nota si existe
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Acciones                               │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Check}   [ Confirmar cita ]   │   │  <- visible si PENDIENTE
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Play}    [ Iniciar sesion ]   │   │  <- visible si CONFIRMADA
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Flag}    [ Completar ]        │   │  <- visible si EN CURSO
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Edit}    [ Editar cita ]      │   │  <- siempre visible (si no completada)
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {X-rojo}  [ Cancelar cita ]    │   │  <- destructivo, abre Dialog de confirmacion
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

**Maquina de estados de acciones**:
```
PENDIENTE  → [ Confirmar ] [ Editar ] [ Cancelar ]
CONFIRMADA → [ Iniciar ]   [ Editar ] [ Cancelar ]
EN CURSO   → [ Completar ] [ Editar ]
COMPLETADA → (solo lectura, sin acciones)
CANCELADA  → (solo lectura, sin acciones)
```

**Dialog de confirmacion de cancelacion**:
```
┌─────────────────────────────────────────┐
│                                         │
│  Cancelar cita                          │
│                                         │
│  Esta accion no se puede deshacer.      │
│  Se notificara al cliente por email.    │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [Volver]    │  │ [Si, cancelar] │  │
│  └──────────────┘  └────────────────┘  │
│                          rojo           │
└─────────────────────────────────────────┘
```

---

## 6. Lista de Clientes

```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  Clientes                   {UserPlus}  │  <- header + boton anadir
│                                         │
│  [___Buscar por nombre o telefono___]   │  <- search bar persistente
│                                         │
│  342 clientes                           │  <- contador total
│                                         │
├─────────────────────────────────────────┤
│                                         │
│  A                                      │  <- indice alfabetico
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Ana Garcia                 │   │
│  │      +34 612 345 678            │   │
│  │      Ultima visita: hace 2 sem  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  C                                      │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Carla Ruiz                 │   │
│  │      +34 623 456 789            │   │
│  │      Ultima visita: hace 1 mes  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  P                                      │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ {Av} Pedro Sanchez              │   │
│  │      +34 634 567 890            │   │
│  │      Ultima visita: hace 3 sem  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │  <- lista con scroll
│  ... mas clientes ...                   │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│                                         │
├─────────────────────────────────────────┤
│  {Casa}   {Cal}   {Users}   {Menu}    │
│   Hoy     Citas   Equipo     Mas      │
└─────────────────────────────────────────┘
```

**Nota**: La lista de clientes vive en el tab "Hoy" como acceso rapido, o accesible desde "Mas" menu. No ocupa un slot de nav propio para priorizar las vistas de gestion operativa diaria.

---

## 7. Detalle de Cliente

```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  {ChevronLeft} Clientes      {Edit}    │  <- back + editar
│                                         │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │         {Avatar grande}         │   │
│  │         Ana Garcia              │   │
│  │                                 │   │
│  │  {Tel}  +34 612 345 678  [Llamar]│  │
│  │  {Mail} ana.garcia@email.com    │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Estadisticas                           │
│  ┌──────────────┐  ┌──────────────┐    │
│  │   {Hash}     │  │  {Calendario}│    │
│  │     14       │  │   15 mar     │    │
│  │   visitas    │  │ ultima visita│    │
│  └──────────────┘  └──────────────┘    │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Notas del salon                        │
│  ┌─────────────────────────────────┐   │
│  │ Prefiere agua caliente. Alergica│   │  <- nota libre del salon
│  │ al amoniaco. Cabello fino.      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Historial de citas                     │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ 15 mar 2026 · Corte + Secado   │   │
│  │ Laura · 35€ · COMPLETADA       │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 01 feb 2026 · Tinte completo   │   │
│  │ Sofia · 55€ · COMPLETADA       │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 10 ene 2026 · Mechas            │   │
│  │ Sofia · 75€ · COMPLETADA       │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│  ... mas historial ...                  │
│  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~    │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Acciones GDPR                          │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ {Download} Exportar datos       │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ {Shield-X-rojo} Anonimizar      │   │  <- destructivo, dialog de confirmacion
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

**Dialog de confirmacion de anonimizacion**:
```
┌─────────────────────────────────────────┐
│                                         │
│  Anonimizar cliente                     │
│                                         │
│  Esta accion es IRREVERSIBLE.           │
│  Se eliminaran todos los datos          │
│  personales (nombre, email, telefono).  │
│  El historial de citas se mantiene      │
│  anonimizado para estadisticas.         │
│                                         │
│  Escribe "ANONIMIZAR" para confirmar:   │
│  [_______________________]              │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [Cancelar]  │  │[Anonimizar]    │  │
│  └──────────────┘  └────────────────┘  │
│                          rojo, disabled │
│                          hasta confirmar│
└─────────────────────────────────────────┘
```

---

## 8. Lista de Equipo (Tab: Equipo)

```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  Equipo                                 │
│                                         │
│  3 empleados activos                    │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar}  Laura Martinez       │   │
│  │            Estilista            │   │
│  │            ACTIVA {punto-verde} │   │
│  │                      {ChevronR} │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar}  Sofia Ramos          │   │
│  │            Colorista            │   │
│  │            ACTIVA {punto-verde} │   │
│  │                      {ChevronR} │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar}  Marc Torres          │   │
│  │            Barbero              │   │
│  │            ACTIVA {punto-verde} │   │
│  │                      {ChevronR} │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Inactivos                              │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar-grey} Jordi Vila       │   │
│  │               Estilista         │   │
│  │               INACTIVO          │   │
│  │                      {ChevronR} │   │
│  └─────────────────────────────────┘   │
│                                         │
├─────────────────────────────────────────┤
│  {Casa}   {Cal}  * {Users} * {Menu}   │
│   Hoy     Citas    Equipo     Mas     │
└─────────────────────────────────────────┘
                              ↑
                    ┌─────────────┐
                    │  {UserPlus} │  <- FAB
                    │ Añadir empl │
                    └─────────────┘
```

**Detalle de Empleado** (pantalla separada):
```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  {ChevronLeft} Equipo        {Edit}    │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │         {Avatar grande}         │   │
│  │         Laura Martinez          │   │
│  │         Estilista               │   │
│  │         ACTIVA {punto-verde}    │   │
│  │                                 │   │
│  │  {Mail} laura@rivoo-salon.com   │   │
│  │  {Tel}  +34 645 678 901         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Horario laboral                        │
│  ┌─────────────────────────────────┐   │
│  │ Lu  Mi  Vi   09:00 — 18:00     │   │
│  │ Ma  Ju       09:00 — 20:00     │   │
│  │ Sa            10:00 — 15:00    │   │
│  │ Do            Descanso         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Servicios que realiza                  │
│  ┌─────────────────────────────────┐   │
│  │ Corte de pelo     Corte + Secado│   │
│  │ Brushing          Recogido      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  Estadisticas (este mes)                │
│  ┌──────────────┐  ┌──────────────┐    │
│  │   {Hash}     │  │   {Euro}     │    │
│  │     47       │  │  1.645€      │    │
│  │   citas      │  │  facturado   │    │
│  └──────────────┘  └──────────────┘    │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ {Power-rojo} Desactivar empleado│   │  <- toggle activo/inactivo
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## 9. Menu "Mas" — Configuracion y Ajustes

```
┌─────────────────────────────────────────┐
│ ●●● 09:41              ▲▲ ☐ ■■■ 87%   │
├─────────────────────────────────────────┤
│                                         │
│  Mas                                    │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar}  Salon Cortes Mireia  │   │
│  │            Maria Garcia (owner) │   │
│  │            Plan: Premium {badge}│   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│  Configuracion del salon                │
│  ─────────────────────────────────────  │
│                                         │
│  {Store}   Mi Salon           {ChevR}  │  <- nombre, direccion, foto, slug
│  {Clock}   Horarios del salon {ChevR}  │  <- horario de apertura/cierre
│  {Scissors} Servicios         {ChevR}  │  <- catalogo de servicios y precios
│  {Globe}   Pagina publica     {ChevR}  │  <- preview y link de la pagina publica
│                                         │
│  ─────────────────────────────────────  │
│  Cuenta y facturacion                   │
│  ─────────────────────────────────────  │
│                                         │
│  {CreditCard} Suscripcion     {ChevR}  │  <- plan, proxima factura, cambiar plan
│  {Users}   Clientes           {ChevR}  │  <- lista completa de clientes
│                                         │
│  ─────────────────────────────────────  │
│  Soporte                                │
│  ─────────────────────────────────────  │
│                                         │
│  {HelpCircle} Ayuda           {ChevR}  │
│  {MessageSquare} Contactar    {ChevR}  │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  {LogOut-rojo} Cerrar sesion            │  <- sin ChevronRight, accion directa
│                                         │
│  ─────────────────────────────────────  │
│  v1.0.0 · Rivoo · Barcelona            │  <- version + branding
│                                         │
├─────────────────────────────────────────┤
│  {Casa}   {Cal}   {Users}  * {Menu} * │
│   Hoy     Citas   Equipo     Mas      │
└─────────────────────────────────────────┘
```

**Subpantalla: Suscripcion**:
```
┌─────────────────────────────────────────┐
│ {ChevronLeft} Suscripcion               │
├─────────────────────────────────────────┤
│                                         │
│  Tu plan actual                         │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  ★ PREMIUM                      │   │
│  │  59€ / mes                      │   │
│  │  Proxima factura: 1 abr 2026    │   │
│  │  Empleados: 3/5                 │   │
│  │  Clientes: 342/ilimitados       │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│  Cambiar de plan                        │
│  ─────────────────────────────────────  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  BASIC · 29€/mes                │   │
│  │  Hasta 2 empleados · 200 clientes│  │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  ENTERPRISE · 99€/mes           │   │
│  │  Empleados ilimitados           │   │
│  │  Clientes ilimitados            │   │
│  │  API access                     │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {X} Cancelar suscripcion       │   │  <- destructivo
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

---

## 10. Pagina de Reserva Publica (Sin Autenticacion)

Esta pagina la ve el cliente final al hacer clic en el link de reserva del salon. URL ejemplo: `rivoo.app/salons/cortes-mireia/book`

No tiene Bottom Nav. Es un flujo lineal tipo wizard.

### 10A. Cabecera del Salon (compartida en todos los pasos)

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  [foto del salon — banner]      │   │  <- imagen cabecera
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Cortes Mireia                          │  <- nombre del salon
│  Carrer de Balmes, 123 · Barcelona      │  <- direccion
│  {Star} 4.8 (127 resenas)              │  <- valoracion (futuro)
│                                         │
└─────────────────────────────────────────┘
```

### 10B. Step 1 — Seleccionar Servicio

```
┌─────────────────────────────────────────┐
│  [cabecera del salon]                   │
├─────────────────────────────────────────┤
│                                         │
│  Reservar cita · Paso 1 de 5            │
│  ─────────────────────────────────────  │
│  Elige un servicio                      │
│                                         │
│  Cortes                                 │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ Corte de pelo          45min    │   │
│  │                         25,00€  │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ * Corte + Secado *     60min    │   │  <- servicio seleccionado
│  │                         35,00€  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Coloracion                             │
│  ─────────────────────────────────────  │
│  ┌─────────────────────────────────┐   │
│  │ Tinte completo         90min    │   │
│  │                         55,00€  │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ Mechas                120min    │   │
│  │                         75,00€  │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │         [ Continuar → ]         │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Rivoo · Reservas online               │  <- branding discreto al pie
└─────────────────────────────────────────┘
```

### 10C. Step 2 — Seleccionar Empleado

```
┌─────────────────────────────────────────┐
│  [cabecera del salon]                   │
├─────────────────────────────────────────┤
│                                         │
│  Reservar cita · Paso 2 de 5            │
│  ─────────────────────────────────────  │
│  Elige quien te atendera                │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar-generic}               │   │
│  │  Sin preferencia                │   │  <- primera opcion (recomendada)
│  │  Cualquier estilista disponible │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar} Laura Martinez        │   │
│  │  Estilista                      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Avatar} Sofia Ramos           │   │
│  │  Colorista                      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Continuar →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
│  Rivoo · Reservas online               │
└─────────────────────────────────────────┘
```

### 10D. Step 3 — Elegir Fecha y Hora

```
┌─────────────────────────────────────────┐
│  [cabecera del salon]                   │
├─────────────────────────────────────────┤
│                                         │
│  Reservar cita · Paso 3 de 5            │
│  ─────────────────────────────────────  │
│  Cuando quieres venir?                  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  < Marzo 2026 >                 │   │
│  │  Lu Ma Mi Ju Vi Sa Do          │   │
│  │  16 17  18  19 20 21 22       │   │
│  │  23 24  25  26 27 28 29       │   │
│  │  30 31                        │   │
│  │  (dias pasados = gris/disabled)│   │
│  └─────────────────────────────────┘   │
│                                         │
│  Horas disponibles · Lunes 23 mar       │
│                                         │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐  │
│  │9:00│ │9:30│ │10:0│ │    │ │    │  │  <- grises = sin disponibilidad
│  └────┘ └────┘ └────┘ └────┘ └────┘  │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐         │
│  │12:0│ │12:3│ │16:0│ │17:0│         │
│  └────┘ └────┘ └────┘ └────┘         │
│                                         │
│  Seleccionado: Lunes 23 mar · 10:00    │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Continuar →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
│  Rivoo · Reservas online               │
└─────────────────────────────────────────┘
```

### 10E. Step 4 — Tus Datos

```
┌─────────────────────────────────────────┐
│  [cabecera del salon]                   │
├─────────────────────────────────────────┤
│                                         │
│  Reservar cita · Paso 4 de 5            │
│  ─────────────────────────────────────  │
│  Tus datos de contacto                  │
│                                         │
│  Nombre *                               │
│  [___Ana Garcia___________________]     │
│                                         │
│  Email *                                │
│  [___ana.garcia@email.com_________]     │
│                                         │
│  Telefono *                             │
│  [___+34 612 345 678______________]     │
│                                         │
│  Notas (opcional)                       │
│  [___Soy nueva, cabello largo_____]     │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  {Checkbox} Acepto recibir              │
│  confirmaciones y recordatorios         │
│  por email.                             │
│                                         │
│  {Checkbox} He leido la politica        │
│  de privacidad. *                       │
│                                         │
│  ┌──────────────┐  ┌────────────────┐  │
│  │  [← Atras]   │  │  [Continuar →] │  │
│  └──────────────┘  └────────────────┘  │
│                                         │
│  Rivoo · Reservas online               │
└─────────────────────────────────────────┘
```

### 10F. Step 5 — Confirmar Reserva

```
┌─────────────────────────────────────────┐
│  [cabecera del salon]                   │
├─────────────────────────────────────────┤
│                                         │
│  Reservar cita · Paso 5 de 5            │
│  ─────────────────────────────────────  │
│  Confirma tu reserva                    │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │  {Scissors} Corte + Secado      │   │
│  │             60 minutos          │   │
│  │                                 │   │
│  │  {Avatar}   Laura Martinez      │   │
│  │                                 │   │
│  │  {Calendar} Lunes 23 de marzo   │   │
│  │  {Clock}    10:00 — 11:00       │   │
│  │                                 │   │
│  │  {User}     Ana Garcia          │   │
│  │  {Mail}     ana.garcia@...com   │   │
│  │  {Phone}    +34 612 345 678     │   │
│  │                                 │   │
│  │  ─────────────────────────────  │   │
│  │                                 │   │
│  │  Total: 35,00 €                 │   │
│  │  Pago en el salon               │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │      [ Confirmar reserva ]      │   │  <- CTA primario
│  └─────────────────────────────────┘   │
│                                         │
│  ┌──────────────┐                      │
│  │  [← Atras]   │                      │
│  └──────────────┘                      │
│                                         │
│  Rivoo · Reservas online               │
└─────────────────────────────────────────┘
```

### 10G. Exito de Reserva

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  Cortes Mireia                  │   │
│  └─────────────────────────────────┘   │
│                                         │
│                                         │
│           {CheckCircle-verde}           │
│                                         │
│       Reserva confirmada               │
│                                         │
│  Hemos enviado los detalles a           │
│  ana.garcia@email.com                   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Scissors} Corte + Secado      │   │
│  │  {Cal}      Lunes 23 de marzo   │   │
│  │  {Clock}    10:00 — 11:00       │   │
│  │  {Av}       Laura Martinez      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  ¿Quieres cancelar o cambiar la cita?  │
│  Escríbenos a hola@cortes-mireia.com   │
│                                         │
│  ─────────────────────────────────────  │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  {Calendar} Añadir al calendario│   │  <- deep link .ics
│  └─────────────────────────────────┘   │
│                                         │
│  Rivoo · Reservas online               │
└─────────────────────────────────────────┘
```

---

## 11. Vista Desktop — Calendario (1200px)

El calendario en desktop muestra columnas paralelas por empleado, sin necesidad de selector de empleado.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  rivoo                                                      Cortes Mireia        {Bell}(2)  {Avatar} Maria  {v}  │
├────────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────┤
│                │                                                                                                  │
│  {Casa} Hoy    │  Citas                              < Martes, 22 de marzo 2026 >          [ + Nueva cita ]      │
│                │                                                                                                  │
│  {Cal} Citas   │  ┌──────────────────────────────────────────────────────────────────────────────────────────┐  │
│                │  │                                                                                            │  │
│  {Users} Equipo│  │   Hora   │  Laura Martinez     │  Sofia Ramos          │  Marc Torres      │  + Añadir   │  │
│                │  │          │  (Estilista)        │  (Colorista)          │  (Barbero)        │             │  │
│  {Menu} Mas    │  │  08:00   │                     │                       │                   │             │  │
│                │  │          │                     │                       │                   │             │  │
│  ─────────────  │  │  08:30   │                     │                       │                   │             │  │
│                │  │          │                     │                       │                   │             │  │
│  Hoy           │  │  09:00   │ ┌─────────────────┐ │ ┌───────────────────┐ │                   │             │  │
│  8 citas       │  │          │ │ Carla Ruiz      │ │ │ Marta Lopez       │ │                   │             │  │
│                │  │  09:30   │ │ Corte + Secado  │ │ │ Tinte completo    │ │                   │             │  │
│  Pendientes    │  │          │ │ 60min · 35€     │ │ │ 90min · 55€       │ │                   │             │  │
│  2 sin confirm │  │  10:00   │ │ CONFIRMADA      │ │ │ CONFIRMADA        │ │                   │             │  │
│                │  │          │ └─────────────────┘ │ │                   │ │                   │             │  │
│  ─────────────  │  │  10:30   │                     │ └───────────────────┘ │                   │             │  │
│                │  │          │                     │                       │                   │             │  │
│  Proxima:      │  │  11:00   │ ┌─────────────────┐ │                       │ ┌───────────────┐ │             │  │
│  Ana G. 10:30  │  │          │ │ Ana Garcia      │ │                       │ │ Pedro Sanchez │ │             │  │
│  Corte+Secado  │  │  11:30   │ │ Corte + Tinte   │ │                       │ │ Afeitado      │ │             │  │
│                │  │          │ │ 90min · 65€     │ │                       │ │ 30min · 20€   │ │             │  │
│  ─────────────  │  │  12:00   │ │ PENDIENTE       │ │                       │ │ CONFIRMADA    │ │             │  │
│                │  │          │ └─────────────────┘ │                       │ └───────────────┘ │             │  │
│  Filtros       │  │  12:30   │                     │ ┌───────────────────┐ │                   │             │  │
│                │  │          │                     │ │ BLOQUEO           │ │                   │             │  │
│  {Check} Laura │  │  13:00   │                     │ │ Almuerzo          │ │                   │             │  │
│  {Check} Sofia │  │          │                     │ │ 60min             │ │                   │             │  │
│  {Check} Marc  │  │  13:30   │                     │ └───────────────────┘ │                   │             │  │
│                │  │          │                     │                       │                   │             │  │
│                │  │  14:00   │ ┌─────────────────┐ │                       │ ┌───────────────┐ │             │  │
│                │  │          │ │ Lucia Valls     │ │                       │ │ Joan Puig     │ │             │  │
│                │  │  14:30   │ │ Brushing        │ │                       │ │ Corte + barba │ │             │  │
│                │  │          │ │ 45min · 30€     │ │                       │ │ 60min · 40€   │ │             │  │
│                │  │  15:00   │ │ PENDIENTE       │ │                       │ │ CONFIRMADA    │ │             │  │
│                │  │          │ └─────────────────┘ │                       │ └───────────────┘ │             │  │
│                │  │  ...     │ ...                 │ ...                   │ ...               │             │  │
│                │  │  21:00   │                     │                       │                   │             │  │
│                │  └──────────────────────────────────────────────────────────────────────────────────────────┘  │
│                │                                                                                                  │
└────────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Sidebar izquierdo** (240px fijo):
- Logo + nombre del salon
- Navegacion principal (Hoy, Citas, Equipo, Mas)
- Mini-resumen del dia (citas, pendientes, proxima)
- Filtros de empleado (checkboxes)

**Area principal** (flex-1):
- Header con navegacion de fecha y boton "Nueva cita"
- Grid de columnas: una por empleado + columna de "Anadir empleado"
- Timeline vertical 08:00-21:00 con separaciones visuales cada hora
- Bloques de cita con colores de borde por empleado
- Click en slot vacio -> abre modal de nueva cita (no bottom sheet)
- Click en bloque de cita -> panel lateral derecho (no bottom sheet)

**Diferencias clave mobile vs desktop**:
```
MOBILE                          DESKTOP
──────────────────────────────────────────────────
Bottom nav bar                  Sidebar izquierdo fijo
1 columna de empleado           N columnas paralelas
Bottom sheets                   Modales centrados / panel lateral
Selector pill de empleado       Checkboxes en sidebar
Swipe para cambiar dia          Click en flechas de fecha
FAB flotante                    Boton "Nueva cita" en header
Pantalla completa               Split view sidebar + main
```

---

## 12. Patrones de Interaccion y Microcopy

### Estados de Carga (Loading)
```
Skeleton screens — no spinners globales:
┌─────────────────────────────────────────┐
│  ████████████████████████               │  <- texto (shimmer)
│  ████████████████                       │
│  ┌─────────────────────────────────┐   │
│  │  ██████████  ████████████████  │   │  <- tarjeta (shimmer)
│  │  ████████████████████████████  │   │
│  │  ████████████████████          │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

### Toast Notifications (Sonner)
```
Exito (verde, auto-cierre 3s):
┌─────────────────────────────────────────┐
│  {Check} Cita confirmada               │
└─────────────────────────────────────────┘

Error (rojo, cierra manual):
┌─────────────────────────────────────────┐
│  {X} Error al guardar. Intenta de nuevo│
└─────────────────────────────────────────┘

Info (neutro, auto-cierre 4s):
┌─────────────────────────────────────────┐
│  {Info} Recordatorio enviado a Ana G.  │
└─────────────────────────────────────────┘
```

### Estados Vacios (Empty States)
```
Sin citas hoy:
┌─────────────────────────────────────────┐
│                                         │
│            {CalendarX}                  │
│                                         │
│      Sin citas para hoy                 │
│                                         │
│   Disfruta del dia o crea una cita      │
│   con el boton de abajo.                │
│                                         │
│   [ + Nueva cita ]                      │
│                                         │
└─────────────────────────────────────────┘

Sin clientes (busqueda):
┌─────────────────────────────────────────┐
│                                         │
│           {SearchX}                     │
│                                         │
│   No encontramos "Pedro Gonz..."        │
│                                         │
│   [ + Crear nuevo cliente ]             │
│                                         │
└─────────────────────────────────────────┘
```

### Microcopy (tono cercano, directo)
```
ACCION              LABEL CORRECTO     LABEL INCORRECTO
──────────────────────────────────────────────────────────
Crear cita          "Reservar"         "Submit"
Cancelar cita       "Cancelar cita"    "Delete"
Confirmar cita      "Confirmar"        "Approve"
Iniciar sesion      "Iniciar sesion"   "Start"
Empleado sin foto   iniciales nombre   icono generico feo
Fecha hoy           "Hoy"              "2026-03-22"
Error red           "Sin conexion.     "Error 503"
                    Comprueba wifi."
```

---

## 13. Anotaciones de Accesibilidad

```
Elemento                Requerimiento
──────────────────────────────────────────────────────────
Botones de accion       aria-label descriptivo (no solo icono)
Badges de estado        aria-label con estado completo
Bottom sheets           role="dialog", aria-modal="true"
Formularios             label + htmlFor en todos los inputs
Colores de estado       nunca solo color — tambien icono o texto
Contraste               WCAG AA minimo (4.5:1 texto, 3:1 UI)
Touch targets           minimo 44x44px en todos los elementos
Focus management        foco al abrir modal/sheet, restaurar al cerrar
Swipe gestures          alternativa de boton siempre disponible
```

---

*Documento generado para el proyecto Rivoo — Fase 13: Frontend Next.js*
*Estos wireframes son la referencia de diseno para la implementacion con Next.js 14 + Shadcn/UI + Tailwind CSS*
