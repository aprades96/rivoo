---
goal: "Un dueño de salón que omite los pasos de empleado y de servicio en el asistente de alta llega al panel y sigue llegando en visitas posteriores; y las cinco pantallas del asistente existen en móvil y escritorio contra los artboards Onboarding{1..5}(Desktop).dc.html"
verified: 2026-08-28T20:53:45Z
status: gaps_found
score: 8/9 must-haves verified
scope:
  backend: "E:/IdeaProjects/rivoo @ feat/onboarding-reanudable (c67043c), 19 ficheros vs master"
  frontend: "E:/IdeaProjects/rivoo-frontend @ feat/onboarding-reanudable (c67043c), 43 ficheros vs master"
  ultima_ronda_sin_revisar: [ab5c1e7, 79b5e50, 29fe939, b92313a, c67043c, 2cc6162, 0236534, 572796a, bc95b0c, 0a6dc0b, 6f176b4]
evidence:
  backend_suite: "92/92 verdes (92 <testcase> contados en surefire-reports, 0 failure/error)"
  backend_integration: "5/5 verdes contra MySQL real 127.0.0.1:3306/salon_db"
  frontend_suite: "198/198 verdes, 38 ficheros (npx vitest run --no-file-parallelism)"
  frontend_lint: "0 errores, 25 warnings, ninguno introducido por esta rama"
  db_readonly: "salons antes 15 filas / max(updated_at) 2026-08-28 20:03:58 -- idéntico después"
gaps:
  - truth: "Ninguna guarda del asistente puede quedarse cierta para siempre"
    status: failed
    reason: >-
      Las tres guardas reescritas en 2cc6162 pasan de `isLoading` a
      `!accessToken || data === undefined`, pero ninguna de las tres páginas
      tiene rama de error. Si el GET falla de forma definitiva (React Query
      reintenta 1 vez y para), `data` se queda `undefined` para siempre, la
      guarda es cierta para siempre y la pantalla nunca se pinta: esqueleto
      infinito, sin mensaje, sin reintentar. El peor de los tres es el paso 2
      del asistente, donde además "Continuar" queda deshabilitado para siempre
      -- una forma nueva de quedarse atrapado en el asistente, que es
      exactamente lo que este plan existe para eliminar.
    artifacts:
      - path: "rivoo-frontend/src/app/(onboarding)/business-hours/page.tsx:51,89,113"
        issue: "hoursNotReady sin rama isError; ctaDisabled={hoursNotReady} deja Continuar muerto"
      - path: "rivoo-frontend/src/app/(app)/settings/business-hours/page.tsx:40,55"
        issue: "hoursNotReady sin rama isError; esqueleto permanente (se puede navegar fuera)"
      - path: "rivoo-frontend/src/app/(app)/staff/[id]/page.tsx:61,162"
        issue: "workingHoursNotReady sin rama isError; pestaña Horarios en esqueleto permanente"
    missing:
      - "Rama de error en las tres: `if (query.isError) return <error con botón Reintentar que llame a refetch()>` antes de la comprobación de `hoursNotReady`"
      - "En (onboarding)/business-hours, desacoplar `ctaDisabled` del estado de error o dar una salida explícita ('Continuar sin cargar el horario' / reintentar)"
      - "Un test por página con el GET rechazando, asertando que aparece un mensaje de error y NO un esqueleto permanente"
  - truth: "El javadoc del test de integración dice cómo ejecutarlo"
    status: failed
    reason: >-
      El comando que documenta falla. Reproducido: con `-am`, surefire corre
      también en rivoo-common, donde ningún test casa con `-Dtest=...`, y
      surefire 3.5.4 aborta la build con "No tests matching pattern ... were
      executed". Falta `-Dsurefire.failIfNoSpecifiedTests=false`.
    artifacts:
      - path: "rivoo/salon-service/src/test/java/com/rivoo/salon/infrastructure/adapter/out/persistence/repository/SalonJpaRepositoryOnboardingCompletionIntegrationTest.java:38-40"
        issue: "El comando del javadoc termina en BUILD FAILURE tal cual está escrito"
    missing:
      - "Añadir `-Dsurefire.failIfNoSpecifiedTests=false` al comando del javadoc (verificado: con ese flag da Tests run: 5, BUILD SUCCESS)"
      - "Corregir el comentario del perfil `integration-test` en el pom raíz (líneas 157-160): ya no es cierto que baste con Docker; este test exige MySQL local en 127.0.0.1:3306 con salon_db migrado"
  - truth: "Los dos cambios de semántica del backend de la última ronda están cubiertos por tests"
    status: failed
    reason: >-
      Ni `updatable = false` (29fe939) ni `@Transactional` en
      `completeOnboarding` (79b5e50) tienen un solo test que muera si se
      quitan. Los 92 tests siguen verdes con cualquiera de las dos mutaciones.
      Es la misma clase de agujero que el panel encontró para el JPQL en
      b92313a, y se ha cerrado para el JPQL pero no para estos dos.
    artifacts:
      - path: "rivoo/salon-service/src/main/java/com/rivoo/salon/infrastructure/adapter/out/persistence/entity/SalonJpaEntity.java:90"
        issue: "updatable = false sin cobertura: ningún test prueba que un save() posterior al CAS ya no pisa la marca"
      - path: "rivoo/salon-service/src/main/java/com/rivoo/salon/application/SalonService.java:205"
        issue: "@Transactional sin cobertura: ningún test prueba que el CAS y la relectura comparten transacción"
    missing:
      - "Test en SalonJpaRepositoryOnboardingCompletionIntegrationTest: CAS -> cargar la entidad -> setStatus -> saveAndFlush -> releer y asertar que onboardingCompletedAt sigue puesto (muere si se quita updatable = false)"
      - "Test con el molde de SalonServiceTransactionBoundaryTest: el puerto mockeado asserta isActualTransactionActive() == true dentro de markOnboardingCompleted y de findByTenantId (muere si se quita @Transactional)"
human_verification:
  - test: "Comparación visual artboard a artboard de los 5 pasos a 390x844 y 1440x900 (Tarea 10 Paso 3 del plan)"
    expected: "Alto de botón 48px, radios 8/12/999, tarjeta 640px en pasos 1 y 5 / 760px en 2-4, los cuatro colores, las tres divergencias de copy"
    why_human: "Requiere la app levantada y capturas; grep confirma tokens, textos y clases pero no el píxel"
  - test: "El chip de 'cita confirmada' no se ha movido tras re-apuntar --color-status-confirmed-bg/text (Tarea 10 Paso 4)"
    expected: "Mismo color y mismo tamaño que antes de la rama"
    why_human: "Comparación visual; el valor hex es idéntico por construcción pero no se ha comprobado renderizado"
---

# Informe de verificación: alta reanudable

**Objetivo:** que un dueño que omite los pasos de empleado y de servicio llegue al panel y siga llegando en visitas posteriores, en vez de quedar atrapado en el bucle que producía deducir el fin del alta contando empleados y servicios.
**Objetivo secundario:** las cinco pantallas del asistente existen en móvil y escritorio contra los artboards.
**Alcance:** `rivoo` y `rivoo-frontend`, rama `feat/onboarding-reanudable`, ambos en `c67043c`. 19 + 43 ficheros frente a `master`.
**Verificado:** 2026-08-28T20:53:45Z
**Estado:** `gaps_found`

---

## Verdades observables

| # | Verdad | Estado | Evidencia |
|---|--------|--------|-----------|
| 1 | El portero decide por la marca, no contando empleados ni servicios | VERIFICADA | `onboarding-gate.tsx` importa solo `useSalon` y `useAuth`; no queda ninguna `useQuery` de staff. El test *"renders the children for an owner with the flag set even with no employees or services"* asserta `listEmployees`/`listServices` **no** llamados |
| 2 | Se puede omitir el paso 3 y el paso 4 y llegar al paso 5 | VERIFICADA | `add-employee/page.tsx:200` → `/add-service`; `add-service/page.tsx:133` → `/complete`. Cadena completa `/welcome` → `/business-hours` → `/add-employee` → `/add-service` → `/complete` |
| 3 | El paso 5 escribe la marca antes de navegar y nunca navega sin ella | VERIFICADA | `complete/page.tsx:31-54`: si la respuesta llega sin `onboardingCompletedAt` no escribe ni navega (`:39-43`); `cancelQueries` antes de `setQueryData` con la clave exacta `["salon","me"]`; `router.push` solo tras la escritura; `catch` sin navegación |
| 4 | La escritura es idempotente y no cruza tenants, y lo arbitra la base de datos | VERIFICADA | 5/5 tests de integración **ejecutados contra MySQL real**: primera llamada devuelve 1 y escribe; segunda devuelve 0 y no mueve el timestamp; el tenant vecino queda intacto (marca `NULL` y `updated_at` sin mover); tenant inexistente devuelve 0; `updated_at` avanza pese a saltarse `@PreUpdate` |
| 5 | `updatable = false` no impide que el bulk JPQL escriba la columna | VERIFICADA | Comprobado **empíricamente**, no solo leído: los 5 tests anteriores corren con `updatable = false` puesto en la entidad y el primero asserta `isNotNull().isCloseTo(...)`. La afirmación del implementador es cierta |
| 6 | La marca viaja con el nombre correcto de extremo a extremo | VERIFICADA | Columna `onboarding_completed_at` (`SHOW COLUMNS`, V4 aplicada) → `SalonJpaEntity` → `SalonPersistenceMapperImpl:44,77` → `Salon` → `SalonDtoMapperImpl:65,70` → `SalonResponse` → JSON `"onboardingCompletedAt"` fijado por `SalonResponseJsonTest` → `Salon.onboardingCompletedAt` en TS → `onboarding-gate.tsx:43` |
| 7 | `@Transactional` en `completeOnboarding` es seguro | VERIFICADA (por lectura) | El método no hace ninguna llamada HTTP (a diferencia de `getByTenantId`), así que la razón para no anotarlo no aplica. Propagación `REQUIRED` por defecto: el `@Transactional` del repositorio se **une**, no abre una nueva. `clearAutomatically = true` limpia el contexto tras el bulk, así que la relectura va a la base y ve su propia escritura. Solo dos sentencias dentro de la transacción: no retiene conexión de más. **Sin cobertura de tests** (ver hueco 3) |
| 8 | Las cinco pantallas existen en móvil y escritorio | VERIFICADA | 5 rutas + 5 tests bajo `(onboarding)/`; los 10 artboards presentes; los 6 tokens que faltaban declarados dos veces (crudo en `:root` + `--color-*` en `@theme inline`, `globals.css:68-75,132-139`); `switch.tsx` y `progress.tsx` creados; los subtítulos divergentes de los pasos 2, 3 y 4 implementados en las dos variantes con `md:hidden` / `hidden md:block`; ancho de tarjeta 640/760 por paso (`onboarding-store.ts:28-30`); ruta huérfana `salon-setup` borrada (0 referencias) |
| 9 | Ninguna guarda puede quedarse cierta para siempre | **FALLIDA** | Las tres guardas de 2cc6162 no tienen rama de error → esqueleto permanente. Ver hueco 1 |

**Puntuación: 8/9.**

---

## Alcance especial: la última ronda, mirada con lupa

### Backend

#### `79b5e50` — `@Transactional` en `SalonService.completeOnboarding`

Las cuatro preguntas, contestadas:

- **¿Cambia los límites transaccionales de forma segura?** Sí. `completeOnboarding` no llama a nadie fuera de la base de datos (comprobado: el cuerpo son dos llamadas al puerto de persistencia y un mapeo puro). La razón por la que `getByTenantId` **no** lleva la anotación —que puede enviar el correo de bienvenida por HTTP con una conexión JDBC en la mano— no aplica aquí, y el javadoc lo dice correctamente.
- **¿La relectura ve su propia escritura?** Sí. Al compartir transacción, InnoDB muestra siempre a una transacción sus propias modificaciones, incluso bajo el `REPEATABLE READ` por defecto de MySQL. Además `@Modifying(clearAutomatically = true)` vacía el contexto de persistencia después del bulk, así que el `findByTenantId` posterior emite un `SELECT` de verdad y no devuelve una instancia cacheada con el valor viejo.
- **¿El `@Transactional` del repositorio se une o abre una nueva?** Se une. `SalonJpaRepository.markOnboardingCompletedIfPending` lleva `@Transactional` sin `propagation`, luego `REQUIRED`, luego se suma a la transacción exterior. No hay ningún `REQUIRES_NEW` en el camino.
- **¿Puede el endpoint retener la conexión más de lo debido?** No. La transacción abarca exactamente dos sentencias (`UPDATE` + `SELECT`), sin E/S de red intercalada.

**Un detalle de cableado que sí verifiqué porque podía romperlo todo en silencio:** `completeOnboarding` está declarado en `UpdateSalonUseCase` (`:18`) y el controlador se lo inyecta por esa interfaz (`SalonController:46`), no por la clase concreta. Con proxy JDK o CGLIB, el `@Transactional` se aplica igual. Si el método hubiera vivido solo en la clase concreta, un proxy JDK lo habría dejado fuera y la anotación no habría hecho nada.

**Lo que no tiene:** un solo test que muera al quitar la anotación. Ver hueco 3.

#### `29fe939` — `updatable = false` en `SalonJpaEntity.onboardingCompletedAt`

- **¿De verdad impide que un `merge` la pise?** Sí, por semántica de Hibernate: una columna `updatable = false` queda fuera del `UPDATE` que genera el persister de la entidad. Cierra las dos vías que el javadoc de `Salon.onboardingCompletedAt` documenta (`SalonService.update` y `SalonService.updateStatus`, ambas leer-modificar-guardar sobre el agregado entero vía `SalonPersistenceAdapter.save` → `merge`). **No verificado empíricamente** (ver hueco 3).
- **¿De verdad *no* impide que el bulk JPQL la escriba?** **Sí, y esto lo comprobé ejecutando, no leyendo.** El implementador dice haberlo verificado contra MySQL real; lo confirmé yo: los 5 tests de `SalonJpaRepositoryOnboardingCompletionIntegrationTest` corren con `updatable = false` en su sitio y el primero asserta que tras `markOnboardingCompletedIfPending` la columna releída **no es null** y está a menos de 2 segundos del instante pasado. Ese test **demuestra** la afirmación, no la enuncia. Salida real:

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.761 s -- in
com.rivoo.salon.infrastructure.adapter.out.persistence.repository.SalonJpaRepositoryOnboardingCompletionIntegrationTest
[INFO] BUILD SUCCESS
```

La base quedó como estaba: 15 filas y `max(updated_at) = 2026-08-28 20:03:58` antes y después. La clase es `@Transactional` y `TransactionalTestExecutionListener` deshace cada método.

#### `ab5c1e7` — javadoc corregido

Correcto y comprobado. El javadoc de `SalonOnboardingCompletionTest` ahora distingue **lo que prueba** (que `SalonService.completeOnboarding` no hace leer-decidir-escribir por su cuenta, sino que delega comprobación y escritura en una única llamada al puerto) de **lo que no prueba** (el predicado `onboarding_completed_at IS NULL` real contra MySQL), y apunta al test de integración por nombre y paquete. La referencia cruzada existe y es correcta.

#### `b92313a` — el `@Tag("integration")`

- **¿Es deliberado que no corra en la build normal?** Sí, y por partida doble: el pom raíz declara `<surefire.excluded.groups>integration</surefire.excluded.groups>` con un comentario explícito (`pom.xml:42-43`) y hay un perfil `integration-test` que la limpia (`:156-166`). El javadoc de la clase además explica por qué no usa Testcontainers (no hay Docker en esta máquina) y marca la desviación de la convención del repositorio como deliberada.
- **¿Su javadoc dice cómo ejecutarlo?** Dice cómo, pero **el comando no funciona**. Ver hueco 2. Lo reproduje.

#### `c67043c` — el nombre de cable

Correcto. `SalonResponseJsonTest` fija `"onboardingCompletedAt"` y que un `null` serializa como `null` de JSON (no como campo ausente, que en TS sería `undefined` — también falsy, pero por otro camino). Es el par exacto del comentario defensivo de `types/salon.ts:22-28`.

### Frontend

#### `572796a` — el 404 ya no manda al asistente

**El juicio que se me pide: el cambio es correcto y no rompe ningún caso legítimo.**

Contesto directamente a la pregunta del instante justo después del registro: **no, un usuario nuevo y válido no puede recibir un 404 y quedarse fuera.** Dos razones independientes, ambas verificadas en el código:

1. **El orden de la saga.** `OnboardingSagaService` guarda el salón **primero** (`:91`) y crea el usuario de Keycloak **después**. Si Keycloak falla, compensa borrando el salón (`:115`, `:120`, `:147`, `:162`). No existe una ventana en la que haya un dueño válido en Keycloak sin fila de salón.
2. **El registro no deja entrar a nadie.** `register-form.tsx` no navega tras el alta: enseña `CheckEmailNotice`. El dueño no puede obtener un token hasta verificar el correo (Keycloak rechaza completar el login con una acción `VERIFY_EMAIL` pendiente), y para entonces la fila lleva commiteada un buen rato.

El único residuo es un doble fallo: que falle el enlace dueño↔salón *y además* falle la compensación del usuario de Keycloak (`OnboardingSagaService:144`), dejando una cuenta huérfana. Ese usuario tampoco funcionaba con el comportamiento anterior — lo habrían paseado por cuatro pasos hasta un segundo 404 en el paso 5, que es literalmente el argumento del commit.

Sobre extender el 404 a **cualquier rol**: antes, un `EMPLOYEE` con 404 caía por el `return children` final (porque `needsOnboarding` exigía `isOwner`, y `unavailable` excluía el 404) y pintaba el panel sin salón. El panel roto era peor que la pantalla de error. El cambio mejora ese caso.

#### `2cc6162` — las tres guardas: **aquí está el hueco**

La motivación del commit es sólida y está bien demostrada: en React Query v5 `isLoading = isPending && isFetching`, así que una query deshabilitada reporta `isLoading: false`, y guardar por `isLoading` dejaba montar el editor sobre `DEFAULT_HOURS` con el guardado habilitado durante la sesión medio viva. Los tests nuevos cubren esa ventana en las tres páginas.

Pero la pregunta que se me hace —«¿queda algún estado en que la guarda sea cierta para siempre?»— tiene respuesta afirmativa: **sí, el error**. Ninguna de las tres páginas tiene rama de error. Con `retry: failureCount < 1` heredado del provider, un 500 o un fallo de red se reintenta una vez y se queda en error definitivo con `data === undefined`. La guarda es cierta para siempre. El caso peor es `(onboarding)/business-hours`, donde además `ctaDisabled={hoursNotReady || ...}` deja «Continuar» muerto: esqueleto infinito, sin mensaje, sin reintentar, y la única salida es «Salir» (cerrar sesión). Detalle en el hueco 1.

Comparado con lo anterior: con `isLoading` la página al menos pintaba el editor sobre valores por defecto tras el error. Se ha cambiado un fallo silencioso (pisar el horario) por un bloqueo visible, que es mejor, pero el arreglo se quedó a medio: falta la tercera rama.

#### `bc95b0c` — tests del portero

Ocho casos, sustantivos, con aserciones positivas donde importa (el caso del 500 asserta explícitamente que aparece «No se ha podido cargar tu salon», con un comentario que explica que sin esa aserción el caso pasaría también si el 500 cayera en el spinner infinito). Los tests dirigen el portero por hooks mockeados, sin `QueryClient` vivo, así que no dependen del macrotask de `notifyManager` que `AGENTS.md` advierte.

#### `unavailable` ahora exige `!salon`: ¿algún caso sin cubrir?

Enumeré los ocho estados alcanzables con `authReady && !isLoading`. Solo uno cae por el `return children` final sin pantalla propia: `salon === undefined && salonError === null`. **Comprobé que no es alcanzable**: la query se habilita con la misma condición que `authReady`, y React Query marca `fetchStatus: 'fetching'` de forma optimista tanto al montar como al pasar `enabled` de `false` a `true` (`queryObserver.js:250-256`, con `shouldFetchOptionally` devolviendo `true` cuando `prevOptions.enabled === false`, `:457-458`). En ese render `isLoading` ya es `true` y se pinta el spinner. No hay parpadeo del panel.

El otro estado, `salonError` 404 **con** `salon` cacheado, cae también en `return children` — y es lo correcto: se sigue sirviendo el último payload bueno. Es el mismo caso que el test de «background refetch fails but cached salon data is still valid» fija para el 500.

#### `6f176b4`, `0236534`, `0a6dc0b`

- `Salon.ownerUserId` eliminado: correcto, `SalonResponse` no lo lleva (18 componentes, ninguno es `ownerUserId`).
- `salons.test.ts` es la pieza de cableado que faltaba: es el único sitio que espía `fetch` de verdad, porque los otros 197 tests mockean `salonsApi`. Fija `POST` + `/api/v1/salons/me/onboarding/complete` + cabecera `Authorization`. Contrastado con el controlador: `SalonController.java:103-104` casa exactamente. Y `api-gateway/src/main/resources/application-local.yml:19` enruta `/api/v1/salons/**` a salon-service, sin estar en la lista `permitAll` de `GatewaySecurityConfig`.
- El test de «Hoy» ahora asserta que el esqueleto **se pinta**, no solo que algo está ausente.

---

## Cableado (nivel 3)

| Desde | Hasta | Vía | Estado |
|---|---|---|---|
| `complete/page.tsx:31` | `salonsApi.completeOnboarding` | import directo + `await` | CABLEADO |
| `salonsApi.completeOnboarding` | `POST /api/v1/salons/me/onboarding/complete` | `apiFetch`, fijado por `salons.test.ts` | CABLEADO |
| navegador | salon-service | gateway `Path=/api/v1/salons/**` | CABLEADO |
| `SalonController:103` | `UpdateSalonUseCase.completeOnboarding` | inyección por interfaz, `tenantId` de `TenantContext` (no del cliente) | CABLEADO |
| `SalonService:207` | `SalonPersistencePort.markOnboardingCompleted` | puerto → `SalonPersistenceAdapter:75` → repositorio | CABLEADO (fijado por `SalonPersistenceAdapterOnboardingCompletionTest`) |
| repositorio | MySQL `salons.onboarding_completed_at` | JPQL bulk CAS | CABLEADO (5 tests contra MySQL real) |
| `SalonResponse.onboardingCompletedAt` | `onboarding-gate.tsx:43` | JSON, nombre fijado en ambos lados | CABLEADO |

---

## El dato fluye (nivel 4)

| Artefacto | Variable | Origen | ¿Dato real? | Estado |
|---|---|---|---|---|
| `onboarding-gate.tsx` | `salon.onboardingCompletedAt` | `useSalon` → `GET /salons/me` → columna real | Sí | FLUYE |
| `complete/page.tsx` | `updated.onboardingCompletedAt` | respuesta del `POST`, validada antes de escribir en caché | Sí | FLUYE |
| `(onboarding)/business-hours` | `hoursQuery.data` | `GET /salons/me/business-hours` (precarga real, ya no `hours={undefined}`) | Sí | FLUYE |
| `today/page.tsx` | `hasNoServices` | GET de servicios, con `servicesError` separado del vacío legítimo | Sí | FLUYE |
| `book/[slug]` | `salon.servicesUnavailable` | flag propio del backend, distinto de «no hay servicios» | Sí | FLUYE |

**Prueba en la base de datos viva:** la fila `sal_44cb08bb-4489-49f2-9167-7a92035539dd` tiene `onboarding_completed_at = 2026-08-28 20:03:58` — la marca escrita hoy, una sola vez, por el recorrido de extremo a extremo. Las otras 14 filas la tienen rellenada por el backfill de `V4` (`WHERE status IS NULL OR status <> 'ONBOARDING'`), que es lo que evita que los salones que ya existían caigan en el asistente.

---

## Comprobaciones ejecutadas

| Comprobación | Comando | Resultado |
|---|---|---|
| Suite backend | `mvn -o -pl salon-service -am test` | `Tests run: 92, Failures: 0, Errors: 0` · BUILD SUCCESS |
| Recuento real de tests | `grep -c "<testcase" surefire-reports/*.xml` | 92, coincide con el resumen (sin `Run 1/Run 2` ocultando nada); 0 ficheros con `<failure>`/`<error>` |
| Integración MySQL | `mvn -o -pl salon-service -am test -Dtest=SalonJpaRepositoryOnboardingCompletionIntegrationTest -Dsurefire.excluded.groups= -Dsurefire.failIfNoSpecifiedTests=false` | `Tests run: 5, Failures: 0` · BUILD SUCCESS |
| El comando del javadoc, tal cual | (el mismo sin `failIfNoSpecifiedTests`) | **BUILD FAILURE** en rivoo-common |
| Suite frontend | `npx vitest run --no-file-parallelism` | 38 ficheros, **198/198** verdes |
| Lint frontend | `npm run lint` | 0 errores, 25 warnings; el único en fichero de alcance (`today/page.tsx:47`) es de `c942f2b8`, previo a esta rama |
| Base intacta | `SELECT COUNT(*), SUM(...), MAX(updated_at) FROM salons` | 15 / 15 / `2026-08-28 20:03:58` antes **y** después |

---

## Anti-patrones

Barrido sobre los ficheros de producción de ambos alcances (`TODO|FIXME|XXX|HACK|PLACEHOLDER|coming soon|not yet implemented`, implementaciones vacías, props vacías en JSX, handlers `() => {}`): **cero hallazgos**. La ruta huérfana `salon-setup` está borrada y no queda ni una referencia.

---

## Huecos

### 1. Guardas sin rama de error (BLOQUEANTE por temática)

`2cc6162` cambió tres guardas a `!accessToken || data === undefined` sin añadir rama de error. Con `retry: failureCount < 1`, un 500 o un fallo de red deja `data` en `undefined` de forma definitiva y la guarda cierta para siempre.

- `(onboarding)/business-hours/page.tsx:51,89,113` — el peor: esqueleto infinito **y** «Continuar» deshabilitado para siempre. El dueño queda atrapado en el paso 2, sin mensaje de error y sin botón de reintentar. Es una forma nueva de quedarse encerrado en el asistente, que es justamente lo que este plan existe para eliminar.
- `settings/business-hours/page.tsx:40,55` — esqueleto permanente; menos grave porque la página vive dentro del shell y se puede navegar fuera.
- `staff/[id]/page.tsx:61,162` — pestaña «Horarios» en esqueleto permanente; el resto de la ficha funciona.

Falta: rama `isError` en las tres, con botón que llame a `refetch()`; desacoplar `ctaDisabled` del error en el paso 2; y un test por página con el GET rechazando.

### 2. El comando documentado del test de integración falla

`SalonJpaRepositoryOnboardingCompletionIntegrationTest:38-40`. Reproducido: con `-am`, surefire corre en rivoo-common, ningún test casa con `-Dtest=`, y surefire 3.5.4 aborta la build. Falta `-Dsurefire.failIfNoSpecifiedTests=false`. Es una instrucción que el próximo lector seguirá al pie de la letra y le fallará.

Añadido: el comentario del perfil `integration-test` (`pom.xml:157-160`) dice «Requires Docker to be running». Ya no es cierto: desde este commit, `mvn test -P integration-test` también exige MySQL local en 127.0.0.1:3306 con `salon_db` migrado, o falla en una máquina que solo tenga Docker.

### 3. Los dos cambios de semántica del backend no tienen cobertura

`b92313a` cerró tres mutaciones del JPQL que dejaban la suite verde. Quedaron dos más de la misma familia, ambas de esta misma ronda:

- Quitar `updatable = false` de `SalonJpaEntity:90` → los 92 tests siguen verdes.
- Quitar `@Transactional` de `SalonService:205` → los 92 tests siguen verdes.

Para la primera el molde ya está montado (basta un método más en la clase de integración: CAS, cargar, `setStatus`, `saveAndFlush`, releer, asertar que la marca sigue). Para la segunda el molde también existe: `SalonServiceTransactionBoundaryTest` ya sabe montar un contexto con `@EnableTransactionManagement` y un gestor sin recurso, y asertar `isActualTransactionActive()` desde dentro de un puerto mockeado.

### 4. Sin salida de las pantallas de error del portero (aviso)

Ambas pantallas de error se pintan **antes** de `AppHeader` (`(app)/layout.tsx:19-30` — el portero envuelve todo el shell), así que quien cae en ellas no tiene botón de cerrar sesión y no puede cambiar de cuenta. Además, en la pantalla de 404 el único botón es «Reintentar», que por definición no puede resolver un 404, mientras el texto dice que hay que contactar con soporte. Preexistente para `unavailable`; nuevo para `salonNotFound`.

---

## Resumen

El objetivo principal está conseguido y demostrado a los cuatro niveles: la marca existe en la base, se escribe una sola vez y por compare-and-set arbitrado por MySQL, viaja con el nombre correcto hasta el portero, y el portero decide solo con ella. Omitir los pasos 3 y 4 lleva al panel, y la fila `sal_44cb08bb` con `onboarding_completed_at` puesto hoy prueba que la escritura persiste para las visitas siguientes. Las cinco pantallas existen en las dos plataformas con los tokens, las primitivas y las divergencias de copy que pedía el inventario visual.

De la última ronda sin revisar, las dos afirmaciones más fuertes resisten: `updatable = false` **no** impide el bulk JPQL (comprobado ejecutando contra MySQL real, no leyendo), y `@Transactional` es seguro porque el método no hace E/S de red y la propagación `REQUIRED` une la transacción del repositorio en vez de abrir otra.

Lo que falta es de acabado, no de arquitectura: una rama de error en tres guardas que hoy pueden quedarse ciertas para siempre, un comando de javadoc que no funciona, y dos cambios de semántica del backend que ningún test defiende.

---
_Verificado: 2026-08-28T20:53:45Z_
_Verificador: Claude (goal-verifier), independiente de los implementadores_
