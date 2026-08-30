---
goal: "Verificar que los 23 hallazgos del panel de revision del bloque 6 (equipo y clientes) estan realmente cerrados en el codigo"
verified: 2026-08-31T00:45:00Z
status: gaps_found
score: 21/23 cerrados
scope:
  backend: "E:/IdeaProjects/rivoo @ 806a3c6 (master)"
  frontend: "E:/IdeaProjects/rivoo-frontend @ f2e6cad (master) -- OJO: el prompt decia 0050219, pero HEAD tiene un commit mas (f2e6cad, solo AGENTS.md)"
gaps:
  - truth: "M16 -- ningun literal visible sin tildes"
    status: partial
    reason: "Tres cadenas visibles del dialogo de confirmacion de anonimizacion siguen sin acentuar"
    artifacts:
      - path: "src/components/clients/gdpr-panel.tsx"
        issue: "lineas 129-131: 'Se eliminaran' / 'se mantendran' / 'Esta accion'"
    missing:
      - "eliminaran -> eliminarán"
      - "mantendran -> mantendrán"
      - "accion -> acción"
  - truth: "M17 -- todo text-[Npx] lleva su leading-* explicito escrito DESPUES"
    status: partial
    reason: "La trampa de ORDEN esta cerrada (0 casos de leading-* antes de text-[Npx]), pero 8 text-[Npx] de ficheros del bloque siguen SIN ningun leading-*, con lo que heredan el line-height 1.5 del preflight en vez del ~1.25 del artboard -- la segunda mitad de la regla de AGENTS.md:59-60"
    artifacts:
      - path: "src/components/clients/client-table.tsx"
        issue: "lineas 68, 124, 135, 142, 145"
      - path: "src/components/clients/client-card.tsx"
        issue: "linea 57"
      - path: "src/components/clients/client-appointment-history.tsx"
        issue: "linea 212"
      - path: "src/components/staff/working-hours-editor.tsx"
        issue: "linea 30"
    missing:
      - "Anadir un leading-* explicito tras cada text-[Npx] o justificar la herencia"
---

# Verificacion de cierre — bloque 6

**Backend:** `E:\IdeaProjects\rivoo` @ `806a3c6` · **Frontend:** `E:\IdeaProjects\rivoo-frontend` @ `f2e6cad`
(el prompt decia `0050219`; HEAD tiene un commit posterior, `f2e6cad`, que solo toca `AGENTS.md`).

**Verificacion ejecutada, no solo leida:**
- Backend: `mvn -o -pl appointment-service,client-service -am test` → `AppointmentJpaRepositoryContractTest` 1/1, `AppointmentPersistenceAdapterTest` 2/2, `ClientServiceTest` 14/14, **BUILD SUCCESS** con el perfil por defecto (que excluye `@Tag("integration")`).
- Frontend: `npx vitest run` → **104 ficheros, 1220 tests, 0 fallos**. `npx tsc --noEmit` limpio.

## Resumen

**CERRADOS: 21/23 · PARCIALES: 2 · ABIERTOS: 0**

Parciales: **#16** (tildes) y **#17** (`text-[Npx]` sin `leading-*`).

## Uno a uno

| # | Hallazgo | Estado | Evidencia |
|---|----------|--------|-----------|
| 1 | `Object[]` colection-like → `ClassCastException` en el historial | **CERRADO** | `AppointmentJpaRepository.java:112` devuelve `AppointmentAggregateProjection` (record, `AppointmentAggregateProjection.java:22`) via expresion constructora JPQL `new ...(COUNT, SUM, MAX)` en `:107-108`. El adaptador ya no castea: `AppointmentPersistenceAdapter.java:97-101` usa accesores del record con guardas de null, sin `(Number) row[0]`. Test que **corre en el build por defecto**: `AppointmentJpaRepositoryContractTest.java:32-41` — sin `@Tag`, verificado ejecutandolo con el perfil que `pom.xml:43` aplica (`surefire.excluded.groups=integration`); afirma `TypeInformation.fromReturnTypeOf(method).isCollectionLike()` es `false`, que es la misma API con la que Spring Data decide `CollectionExecution`, asi que caza cualquier reintroduccion de array o `List`. Ejecutado: 1/1 verde. |
| 2 | `registerVisit` resucitaba un cliente anonimizado | **CERRADO** | `ClientService.java:233-240`: guarda `if (client.isAnonymized())` → log de warning y `return` sin `save`. Test `ClientServiceTest.java:221-231` (`registerVisit_anonymizedClient_isNoOpAndDoesNotUndoAnonymization`) afirma `totalVisits == 0`, `lastVisitAt == null` y `verify(port, never()).save(any())`. Sin `@Tag`; ejecutado, 14/14 verde. |
| 3 | `/clients/[id]`: guarda de carga sin `!accessToken` → error en cada carga en frio | **CERRADO** | `clients/[id]/page.tsx:75` → `if (isLoading \|\| !accessToken)`. Test `clients/[id]/page.test.tsx:157` ("con accessToken null (carga en frio), pinta el esqueleto, NO el error"), que ademas afirma `getClientById` **no** se llamo. |
| 4 | KPIs pintando `Visitas 0 · Última visita —` como datos con el historial en error | **CERRADO** | `clients/[id]/page.tsx:106-111`: `const visits = appointmentsError ? "—" : (summary?.completedCount ?? 0)` y el mismo tratamiento en `lastVisit`. Test `clients/[id]/page.test.tsx:336` afirma `visitsCard.textContent` **no** contiene `"0"` y si contiene `"—"`. (Ver residuo R1 mas abajo sobre el estado de CARGA, no el de error.) |
| 5 | `/staff/[id]`: esqueleto perpetuo con 404/500 | **CERRADO** | `staff/[id]/page.tsx:78` separa `employeeFetchReallyFailed = !!accessToken && employeeFetchFailed`; la rama de error con "Reintentar" esta en `:170-183` y la de esqueleto (`!employee`) en `:185-195`. Tests `staff/[id]/page.test.tsx:138` (error + recuperacion tras reintentar) y `:162` (arranque en frio → esqueleto, no error). |
| 6 | `useServices()` sin guarda → "N de 0" y "No hay servicios en el catálogo" falsos | **CERRADO** | `service-assignment.tsx:46-48` captura `isLoading`/`isError`/`refetch` del catalogo y deriva `catalogUnavailable`; `:88` cambia el contador a `"{n} asignados"` mientras no haya catalogo confirmado; `:93-105` sustituye el mensaje falso por "No se ha podido cargar el catálogo de servicios." + "Reintentar", dejando el "Crea uno primero" solo en `:106-109` (catalogo cargado y vacio de verdad). Tests `service-assignment.test.tsx:188` (fallo), `:207` (reintento) y `:236` (en vuelo). |
| 7 | `/staff` y `/clients` pintaban `EmptyState` con las peticiones fallando | **CERRADO** | `staff/page.tsx:202-211` (empleados) y `:258-265` (servicios); `clients/page.tsx:99-109`. Las tres ramas `isError` van **antes** de la de lista vacia y llevan `refetch`. Tests: `staff/page.test.tsx:263` y `:277`, `clients/page.test.tsx:167`. |
| 8 | `/staff` con `size=100` y 150 empleados, sin "Mostrando X de Y" | **CERRADO** | `employee-table.tsx:145-147`: `Mostrando {employees.length} de {totalElements} · la lista pide {pageSize} por página`, FUERA de la tarjeta, con `pageSize` inyectado desde `staff/page.tsx:26,221`. Tests `employee-table.test.tsx:95` y `staff/page.test.tsx:375` ("con 150 empleados y una página de 100, avisa del recorte con números reales"). (Ver residuo R2: el movil sigue sin esa linea.) |
| 9 | `data-table.tsx`: `<Link>` sin `role="row"` → tabla de una sola fila | **CERRADO** | `ui/data-table.tsx:96`: `<Link key={key} href={href(row)} role="row" ...>`. Los tests comprueban el destino por `a[href]`, no por `getByRole("link")`: `data-table.test.tsx:60`, `client-table.test.tsx:119`, `employee-table.test.tsx:54,66`, `staff/page.test.tsx:338`. No queda ningun `getByRole("link")` sobre filas de `DataTable` (los que quedan son de `EmployeeCard`/`ClientCard` de movil y de la barra lateral, que no viven en una tabla). |
| 10 | Cabecera `variant="screen"` sin `border-b` | **CERRADO** | `ui/data-table.tsx:63`: `"grid items-center px-[18px] border-b border-hairline"` en la clase BASE, comun a los dos variantes. Confirmado contra el artboard: `design/ClientesDesktop.dc.html:92` dibuja `border-bottom: 1px solid #E7DCCF` en la fila de cabecera. Tests `data-table.test.tsx:74-75` (screen) y `:87-88` (nested). |
| 11 | Cuatro paneles de `/clients/[id]` sin borde (`ui/card.tsx` no trae clase `border`) | **CERRADO** | Arreglado en los CONSUMIDORES: perfil `clients/[id]/page.tsx:146` (`border border-border`), KPIs `:266` y `:275`, GDPR `gdpr-panel.tsx:75` (`border border-warning-border`). **`ui/card.tsx` NO se toco**: `git diff 93e7460^..HEAD -- src/components/ui/` devuelve unicamente `data-table.tsx`/`data-table.test.tsx`; el ultimo commit sobre `card.tsx` es `f689764` (scaffolding, pre-bloque). (Ver residuo R3 sobre el `ring-1` que sigue puesto.) |
| 12 | Bloque GDPR a otra escala | **CERRADO** | `gdpr-panel.tsx:68-71`: `flex-1 gap-[7px] text-[13px] leading-none font-semibold` + `isDesktop ? "h-[38px]" : "h-10"` → 40px movil / 38px escritorio y `flex-grow` via `flex-1`. Icono en Anonimizar: `ShieldX` en `:107` (importado en `:6`). Frase de exportacion en escritorio: `:84`, `{isDesktop && ". La exportación entrega un JSON con todos sus datos y su historial."}` — y el anidado dentro de `gdprConsentAt` es correcto, el artboard la escribe en el MISMO span (`design/DetalleClienteDesktop.dc.html:147`). |
| 13 | `/clients`: buscador y contador apilados, `Input` heredando `h-8` | **CERRADO** | `clients/page.tsx:79`: `isDesktop ? "items-center justify-between" : "flex-col"`. `Input` en `:86`: `h-11 bg-card pl-9 lg:h-10` → 44px movil / 40px escritorio, que gana a `h-8` de `ui/input.tsx:12` porque `className` va el ultimo en `cn`. Contador en la misma fila: `:89-93`. Cotejado con los artboards: `design/Clientes.dc.html:35` (44px) y `design/ClientesDesktop.dc.html:82-87` (fila `space-between`, input 340×40, contador 13px `.num`) — `lg:max-w-[340px]` en `:80`. |
| 14 | Filas de dia del editor de horarios como contenedor unico | **CERRADO** | `working-hours-editor.tsx:164` (`flex flex-col gap-2` = gap 8px) y `:169-176`: cada dia es `h-[52px] items-center gap-2.5 rounded-lg border border-border bg-card px-2.5 md:h-11`. Orden `[toggle][dia][horas]`: `Switch` en `:177`, etiqueta `w-[74px]` en `:183-190`, horas en `:192`. Calca `design/DetalleEmpleado.dc.html:29` (`.dayrow`: 52px, gap 10, padding 0 10, border 1px, radius 8) y `:34` (`.day`: width 74px) y el orden de `:95-97`. |
| 15 | `/clients/[id]` escritorio: avatar encima del nombre, telefono antes que email, notas fuera del grupo | **CERRADO** | Avatar AL LADO: `clients/[id]/page.tsx:147` (`flex items-center gap-3.5`, avatar `:148`, nombre `:154`). Email ANTES que telefono: `:315` (email) vs `:321` (telefono) en escritorio, y `:336` vs `:348` en movil. Notas DENTRO del grupo de contacto: `:327` (`{notes && <NotesBlock .../>}` dentro del mismo `flex flex-col gap-3`) y `:366-376` como tercera fila del grupo en movil. |
| 16 | Texto sin tildes en literales visibles | **PARCIAL** | El barrido completo de copy visible del scope (comentarios excluidos) deja **tres cadenas sin acentuar, todas en el dialogo de confirmacion de anonimizacion**: `gdpr-panel.tsx:129` "Se **eliminaran** todos los datos personales", `:130` "Sus citas se **mantendran**", `:131` "Esta **accion** no se puede deshacer". Son texto de `DialogDescription`, visible al usuario, en un fichero que el commit `68a5879` ya toco. El resto del scope esta correcto (`Añadir`, `conexión`, `Inténtalo`, `catálogo`, `Miércoles`/`Sábado`, `Teléfono`, `Última visita`). |
| 17 | `text-[Npx]` sin su `leading-*` escrito DESPUES | **PARCIAL** | La trampa de ORDEN esta cerrada: barrido de todas las cadenas de clase y de todos los bloques `cn(...)` del scope → **0 casos** de `leading-*` escrito antes de un `text-[Npx]`. Pero AGENTS.md:59-60 pide ademas que "every `text-[Npx]` needs its own explicit `leading-*`", y quedan **8 sin ninguno**: `client-table.tsx:68,124,135,142,145`, `client-card.tsx:57`, `client-appointment-history.tsx:212`, `working-hours-editor.tsx:30`. Heredan `line-height: 1.5` del preflight donde el artboard pide ~1.25. |
| 18 | `bg-white` crudo donde `--card` es el token | **CERRADO** | `grep bg-white` sobre los 32 ficheros fuente del bloque: **cero coincidencias**. Los `bg-white` que quedan en el repo estan fuera del bloque (`(onboarding)/complete/page.tsx:84`, `_components/field-styles.ts:8,11`, `onboarding-footer.tsx:34`, `ui/checkbox.tsx:13`, `ui/switch.tsx:19`, `calendar/employee-filter.tsx:74`). |
| 19 | Badge de estado de `/staff/[id]` con color propio | **CERRADO** | `staff/[id]/page.tsx:24` importa `EmployeeStatusBadge` de `staff/employee-card`, y lo usa en `:289` (tarjeta de perfil de escritorio) y `:357` (identidad movil). El componente compartido vive en `employee-card.tsx:22-37`. No queda ningun `<Badge>` de estado escrito a mano en la ficha. |
| 20 | CTA de guardar al tamano por defecto de 32px | **CERRADO** | `service-assignment.tsx:143-145`: `size="xl"` + `className="h-[46px] w-full md:h-10 md:text-sm"`. `working-hours-editor.tsx:240-242`: identico. → 46px movil / 40px escritorio. |
| 21 | `<input type="checkbox">` crudo en la lista de servicios | **CERRADO** | `service-assignment.tsx:6` importa `Checkbox` de `@/components/ui/checkbox`, usado en `:121-124` con `checked`/`onCheckedChange`. No queda ningun `type="checkbox"` en el fichero. |
| 22 | Avatares de cliente todos grises | **CERRADO** | `client-table.tsx:6,45`: `employeeFallbackAvatarClassName(clients.indexOf(client))`; `client-card.tsx:4,33`: mismo helper con la prop `index`, que `clients/page.tsx:127` pasa (`index`). El helper (`lib/utils/avatar.ts:57-59`) reparte los cinco pares `bg-chart-N/12 text-chart-N` de `:18-24`. Test `client-table.test.tsx:73-74` afirma `bg-chart-1/12` y `bg-chart-2/12` en avatares consecutivos. |
| 23 | Contadores no tabulares ni de 13px; el de Equipo fuera de la fila del segmentado | **CERRADO** | Equipo: `staff/page.tsx:154` (`flex items-center` + `justify-between` en escritorio) con el contador en `:165-169`, clase `text-[13px] leading-none tabular-nums`; la fila movil separada queda en `:180-198`. Clientes: `clients/page.tsx:90` con la misma clase. El movil de Clientes usa `text-xs` (12px) sin tabular, que es lo que dibuja `design/Clientes.dc.html:38` (12px, sin `class="num"`). |

## Regresiones detectadas

**Ninguna regresion funcional.** Comprobaciones hechas:

- **Solapamiento entre agentes:** ningun fichero FUENTE fue tocado por mas de uno de los cinco commits de correccion (`68a5879`, `8a594f8`, `e32842a`, `29f65b1`, `0050219`). Los unicos ficheros con dos toques son de test, y el segundo toque siempre es `0050219`, que **solo anade tests** (`git show --stat 0050219`: 7 ficheros, todos `.test.tsx`, +240/-0).
- **Cambio de literal compartido (`29f65b1`):** toca `appointment-actions.tsx:64,76` y `appointment-block.tsx:51`, que estan FUERA de las seis pantallas del bloque. El diff es exclusivamente el acento; los tests de esos consumidores se actualizaron en el mismo commit y la suite entera pasa. Tras el cambio, `grep "No asistio"` sobre todo `src/` devuelve **cero**.
- **Suite y tipos:** 1220/1220 tests verdes, `tsc --noEmit` limpio, backend `BUILD SUCCESS`.

### Residuos (no son regresiones, pero siguen abiertos)

- **R1 — El "Visitas 0" mentiroso sobrevive en el estado de CARGA.** `clients/[id]/page.tsx:106` solo distingue `appointmentsError`. La query del cliente (`:51`) y la del historial (`:64`) son independientes y se resuelven por separado; si el GET del cliente llega primero, la ficha se pinta con `summary === undefined` → `visits = 0` y `lastVisit = "—"` hasta que aterriza el historial. Es exactamente la clase de mentira de M-#4, en la ventana de carga en vez de en la de error. Fix simetrico: propagar tambien `isLoading` del historial al `"—"`.
- **R2 — El recorte de pagina sigue oculto en MOVIL.** `EmployeeTable` (donde vive "Mostrando X de Y") es solo escritorio. En movil, `staff/page.tsx:183` afirma "150 empleados" mientras `:225-227` pinta 100 tarjetas, sin ninguna linea que lo declare. Lo mismo en `clients/page.tsx:123-125` (contador con `totalElements`, hasta 50 tarjetas). El hallazgo #8 hablaba de "la tabla", asi que queda cerrado literalmente, pero la mentira sigue viva al otro ancho.
- **R3 — Los cuatro paneles de `/clients/[id]` ahora llevan borde Y `ring`.** `ui/card.tsx:15` fuerza `ring-1 ring-foreground/10` y ningun consumidor lo quita: `clients/[id]/page.tsx:146,266,275` y `gdpr-panel.tsx:75` anaden `border ...` sin `ring-0`. `border` y `ring` son grupos distintos de tailwind-merge, asi que se pintan las dos lineas. La propia regla que el bloque escribio en `AGENTS.md:88-89` ("con `Card`, escribe `border` **y** el color. Y si el diseno no pide `ring`, quitalo explicitamente") no se cumple en su segunda mitad, y los artboards dibujan un unico borde de 1px.
- **R4 — `HISTORY_SIZE` duplicado.** `clients/[id]/page.tsx:36` y `client-appointment-history.tsx:16` declaran el 7 por separado. Hoy coinciden y la `queryKey` se deduplica (una sola peticion, como afirma el comentario de `:61-63`); si uno de los dos cambia se convierten en dos peticiones sin que ningun test lo note.

## Primitivos compartidos

**Ninguno de los cuatro prohibidos fue tocado.**

`git diff --name-only 93e7460^..HEAD -- src/components/ui/` (todo el bloque 6, desde antes del primer commit hasta HEAD) devuelve unicamente:

```
src/components/ui/data-table.tsx
src/components/ui/data-table.test.tsx
```

`data-table.tsx` es el primitivo que el propio bloque creo (`bf1bb26`), no uno de los doce-rutas. Confirmacion cruzada por historial de fichero: el ultimo commit sobre `card.tsx`, `input.tsx` y `dialog.tsx` es `f689764` (scaffolding inicial) y sobre `sheet.tsx` es `d0b2c14`, todos anteriores al bloque.

Las medidas que el bloque necesitaba se consiguieron desde los consumidores, como pedia el veredicto: `border` en las cuatro `Card` de la ficha de cliente, y `h-11 lg:h-10` sobre el `h-8` del `Input` en `clients/page.tsx:86`.

## "No asistio" y otras cadenas sin acentuar

- **`"No asistio"` sin tilde: CERRADO.** `grep -rn "No asistio" src/` → **cero coincidencias** en todo el frontend. Las cuatro copias del literal (`status-badge.tsx:37` como `statusConfig`, `appointment-actions.tsx:64` y `:76`, `appointment-block.tsx:51`) dicen ahora `"No asistió"`, y los tests que lo afirmaban se actualizaron.
- **Otras cadenas visibles sin acentuar: quedan TRES**, todas en `src/components/clients/gdpr-panel.tsx`, dentro del `DialogDescription` de la confirmacion de anonimizacion:
  - `:129` `Se eliminaran todos los datos personales de {clientName}.` → `eliminarán`
  - `:130` `Sus citas se mantendran pero sin datos identificativos.` → `mantendrán`
  - `:131` `Esta accion no se puede deshacer.` → `acción`

  Metodo: extraccion de nodos de texto JSX y de props de copy (`title`, `description`, `placeholder`, `aria-label`, `label`, `emptyLabel`, `toast.*`) sobre los 21 `.tsx` fuente del bloque, con comentarios de bloque y de linea eliminados antes del barrido, contra una lista de ~40 palabras castellanas acentuables. Todo lo demas del scope esta correcto.

## Veredicto

**21 de 23 cerrados con evidencia en codigo y test; 2 parciales; 0 abiertos.**

Los dos hallazgos criticos de backend estan cerrados **y demostrados por tests que corren en el build por defecto** — verificado ejecutando Maven, no leyendo el `pom.xml`: el guardia del `Object[]` (`AppointmentJpaRepositoryContractTest`) no lleva `@Tag("integration")` y se ejecuto con la exclusion de `pom.xml:43` activa. Los seis hallazgos funcionales de frontend (#3-#8) estan cerrados y cada uno tiene su test de regresion nombrado. La accesibilidad (#9) y las once de fidelidad estan cerradas contra el artboard, cotejando el HTML de `design/` en los casos medibles (#10, #13, #14, #12).

Lo que **no** esta cerrado es cosmetico y pequeno, pero es real y esta en linea concreta:

1. **#16** — tres cadenas sin acentuar en `gdpr-panel.tsx:129-131`, en un fichero que el agente de correccion ya habia abierto.
2. **#17** — la trampa de orden de `tailwind-merge` esta cerrada (0 casos), pero 8 `text-[Npx]` del bloque siguen sin ningun `leading-*`, incumpliendo la mitad "explicito" de la regla de `AGENTS.md:59-60`.

Ademas dejo cuatro residuos (R1-R4) que **no** formaban parte de los 23 pero que un segundo panel volveria a levantar: el cero mentiroso del KPI durante la carga (R1), el recorte de pagina invisible en movil (R2), el `ring` que sobrevive bajo el borde nuevo de las cuatro tarjetas (R3) y el `7` duplicado del historial (R4). R1 y R3 son los que mas merecen una tarea: R1 es literalmente el hallazgo #4 en otra ventana temporal, y R3 deja las cuatro tarjetas con dos lineas de borde donde el artboard dibuja una.

**Nota metodologica:** el `visual/equipo-clientes.spec.ts` que el bloque anadio (`3f6fef9`, 396 lineas) es una spec de Playwright que **nunca se ha ejecutado** (necesita la pila levantada y credenciales E2E). Toda la fidelidad visual de este informe se verifico leyendo el HTML de `design/*.dc.html` contra el TSX, no con pixeles: los items #11, #12, #15, #19, #20, #21, #22 y el residuo R3 siguen necesitando una pasada humana o esa spec ejecutada.

---
_Verificado: 2026-08-31_
_Verificador: Claude (goal-verifier) — solo lectura, ningun fichero de codigo modificado_
