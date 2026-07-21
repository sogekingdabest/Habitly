# Plan de implementación — TOP 5 de mejoras

> Elaborado el 2026-07-18 sobre el código actual.
> Análisis de origen: [MEJORAS_LISTA_COMPRA.md](MEJORAS_LISTA_COMPRA.md) · [MEJORAS_RUTINAS.md](MEJORAS_RUTINAS.md) · [MEJORAS_ASISTENTE_IA.md](MEJORAS_ASISTENTE_IA.md)

## El TOP 5 y su orden de ejecución

El orden no es el de la lista original del análisis, sino el que dictan las dependencias: primero lo barato que multiplica valor (fase 1), luego el modelo de frecuencias que la IA necesitará para proponer rutinas (fase 2 antes que 3), y la despensa antes de cerrar, porque realimenta el contexto de la fase 1.

| Fase | Feature | Tamaño | Depende de |
|---|---|---|---|
| 1 ✅ | Contexto IA enriquecido + quick prompts contextuales | S (~1 sesión) | — |
| 2 ✅ | Rutinas flexibles: "cada N días", rachas conscientes del calendario, protector de racha, heatmap | M (~2-3 sesiones) | — |
| 3 ✅ | `@@RUTINA@@`: crear rutinas desde el chat | M (~2 sesiones) | 1 (prompt), 2 (frecuencias) |
| 4 ✅ | Despensa ligera + "qué cocino con lo que tengo" | M-L (~2-3 sesiones) | 1 (extiende contexto y prompts) |
| 5 ✅ | Rotación y balance del hogar | M (~2 sesiones) | 2 (recomendable, no estricta) |

**TOP 5 COMPLETADO el 2026-07-18** — `assembleDebug` OK y 240 tests unitarios en verde.

**Dos hechos que simplifican todo el plan:**

1. **No hay que tocar `firestore.rules` en ninguna fase.** Las subcolecciones de `households` están cubiertas por el comodín `match /{document=**}` (incluye la futura `pantry_items` y las `completions` existentes), y las rutinas personales tienen su propio comodín anidado. Única excepción documentada más abajo: si algún día se usara una *collection group query* para el balance, sí haría falta regla nueva — el plan la evita a propósito.
2. **La infraestructura `@@LISTA@@` (marcador → parser tolerante → tarjeta → batch) está probada y es replicable** tal cual para rutinas.

### Antes de empezar

- El working tree tiene ahora mismo muchos archivos modificados sin commitear. **Commitear/estabilizar ese trabajo primero**; después, un commit por fase.
- Criterio de cierre de cada fase: `assembleDebug` OK + tests unitarios en verde + smoke test manual del flujo tocado.
- Toda string nueva de UI va a `values/`, `values-en/` y `values-gl/` (la i18n está completa; no romperla).

---

## Fase 1 — Contexto IA enriquecido + quick prompts contextuales ✅ COMPLETADA (2026-07-18)

**Objetivo:** que el asistente deje de responder "a ciegas": hoy `GetAiContextUseCase` solo pasa nombres de la lista y rutinas personales, sin fecha, sin cantidades, sin rutinas de casa, sin rachas.

> **Resultado:** compila y los 140 tests unitarios pasan (41 nuevos o reescritos).
>
> **Dos desviaciones respecto a lo planificado, a tener en cuenta en fases siguientes:**
> 1. **`RoutineSchedule` se creó ya en la fase 1** (no en la 2): el contexto y los quick prompts
>    necesitaban los dos "¿toca hoy?" / "¿está hecha hoy?" y no tenía sentido duplicarlos para
>    luego unificarlos. Hoy expone `isScheduledOn`, `isCompletedOn` e `isPendingOn`. **La fase 2
>    lo extiende** con `EVERY_N_DAYS` y pausa, y sigue pendiente el refactor de los 4 call sites
>    que aún duplican la lógica (`RoutinesViewModel.isRoutineCompletedToday`, `DashboardViewModel`,
>    `BuildWidgetSnapshotUseCase`, `RoutineReminderWorker`).
> 2. **Se borraron `GenerateShoppingListUseCase` y `GenerateRecipeSuggestionsUseCase`**: su texto
>    era un *system prompt* que se enviaba como mensaje de usuario. Los chips ahora usan prompts
>    redactados como peticiones reales dentro de `GetContextualQuickPromptsUseCase`.
>    `GenerateWeeklyMenuUseCase` **sí** se conserva (estaba bien redactado) y lo usa el chip
>    "Menú semanal".
>
> También se unificó el modelo duplicado `QuickPrompt` (presentación) en `AiQuickPrompt` (dominio).

### Diseño

1. **Enriquecer `GetAiContextUseCase`** (`feature/aiassistant/domain/usecase/GetAiContextUseCase.kt`):
   - Añadir **fecha y día de la semana** actuales (con `Locale` del dispositivo) — imprescindible para "menú semanal" o "qué toca hoy".
   - Lista de la compra con **cantidad, unidad, categoría y tienda**: `- 6 unidad Tomate (Frutas y Verduras) [Mercadona] (pendiente)`.
   - **Rutinas de casa** además de las personales (`routinesRepository.observeHouseholdRoutines(householdId)` — el `householdId` ya se resuelve en el use case), indicando quién completó la última vez.
   - **Rachas**: `- Gimnasio (racha: 5 días)` cuando `currentStreak >= 2`.
   - ⚠️ **Presupuesto de contexto**: los modelos van con KV cache de 4096 tokens (`ekv4096`). Capar: máx. ~30 items de lista y ~15 rutinas, con "… y N más" si se excede. Constantes nombradas en el use case.
2. **Nuevo `GetContextualQuickPromptsUseCase`** (`feature/aiassistant/domain/usecase/`):
   - Entrada: fecha (inyectable para test), nº de items pendientes, rutinas de hoy pendientes (y, desde la fase 4, tamaño de despensa).
   - Salida: `List<QuickPrompt>` — ejemplos: domingo/lunes → "Planifica el menú de la semana"; ≥8 items pendientes → "¿Qué recetas salen de mi lista?"; siempre 2-3 estáticos de relleno.
   - `AiAssistantViewModel.init` lo llama con lecturas one-shot (mismo patrón `firstOrNull` + timeout que ya usa `GetAiContextUseCase`).

### Archivos

- **Modificar:** `GetAiContextUseCase.kt`, `AiAssistantViewModel.kt` (quick prompts dinámicos), `strings.xml` ×3 si algún prompt se traduce.
- **Crear:** `GetContextualQuickPromptsUseCase.kt` + test.

### Tests

- `GetAiContextUseCaseTest`: contexto con casa activa incluye rutinas de casa y día de la semana; respeta los caps; sin casa → personalidad base.
- `GetContextualQuickPromptsUseCaseTest`: prompts por día de semana y por estado de lista.

### Riesgos

- Contexto demasiado largo degrada a los modelos pequeños → los caps son parte del diseño, no opcionales. Verificar manualmente con Qwen 1.5B (el peor caso).

---

## Fase 2 — Rutinas flexibles: "cada N días", rachas conscientes del calendario, protector, heatmap ✅ COMPLETADA (2026-07-18)

**Objetivo:** atacar la causa nº 1 de abandono (ansiedad de racha + rigidez) y arreglar de paso una limitación real: hoy `StreakCalculator` cuenta días de calendario consecutivos, así que **una rutina semanal nunca pasa de racha 1** y el badge 🔥 (≥2) le es inalcanzable.

> **Resultado:** `assembleDebug` OK y 157 tests unitarios en verde (44 de rutinas, entre nuevos y reescritos).
>
> **Desviaciones y decisiones que conviene recordar:**
> 1. **Se eliminaron `RoutinesViewModel.isRoutineCompletedToday` y `Routine.isScheduledForDayOfWeek`**
>    en lugar de dejarlos delegando: toda la lógica vive ya en `RoutineSchedule`. `RoutinesViewModelTest`
>    se borró porque solo probaba esos dos métodos; su cobertura está en `RoutineScheduleTest`.
> 2. **Corrección de comportamiento en el dashboard**: antes listaba como pendientes rutinas de
>    otros días de la semana (filtraba solo por "no completada"). Ahora usa `isPendingOn`.
> 3. **El worker pasó de `Worker` a `CoroutineWorker`** y lee la rutina fresca vía `RoutinesEntryPoint`.
>    Eso obligó a cambiar la firma de `ScheduleReminderUseCase(routine, userId, householdId)` y a
>    meter `userId`/`householdId`/`type` en el `inputData`. Efecto secundario bueno: ya no avisa de
>    algo que ya está hecho, ni de una rutina en pausa, ni con títulos obsoletos.
> 4. **Se extrajo `RoutineFormDialog`**: los diálogos de crear y editar eran casi idénticos y cada
>    campo nuevo había que añadirlo por duplicado.
> 5. **El modo vacaciones se controla desde la ficha de progreso**, no desde el diálogo de edición
>    (que ya estaba cargado). La ficha se abre con el icono de calendario de cada tarjeta.
> 6. **`StreakCalculator` tiene dos reglas**: por ocurrencias programadas (diaria/semanal/personalizada)
>    y por huecos entre completados (cada N días). `forRoutine` despacha según la frecuencia.
>
> ⚠️ **Migración de datos:** `currentStreak`/`bestStreak`/`streakGraceUsed` están denormalizados y solo
> se recalculan al marcar/desmarcar una rutina. Las rutinas existentes conservarán su racha antigua
> hasta el siguiente toggle; a partir de ahí las semanales empezarán a subir de verdad.

### Diseño

1. **Modelo** (`feature/routines/domain/model/Routine.kt`):
   - `RoutineFrequency` gana `EVERY_N_DAYS`; `Routine` gana `intervalDays: Int? = null` y `pausedUntil: Long? = null` (modo vacaciones).
   - ⚠️ Firestore deserializa el enum por nombre: **un cliente viejo casca al leer un valor nuevo**. Actualizar todos los dispositivos de la casa a la vez (riesgo asumible pre-lanzamiento; anotarlo en el CHANGELOG del commit).
2. **Nuevo `RoutineSchedule`** (`feature/routines/domain/util/RoutineSchedule.kt`) — punto único de verdad para "¿toca hoy?":
   - `isDueOn(routine, date): Boolean` — DAILY/WEEKLY/CUSTOM como hoy; EVERY_N_DAYS: `lastCompletedAt == null || díasDesdeÚltimo >= intervalDays`; en pausa → nunca toca.
   - Refactor de los 4 sitios que hoy duplican esta lógica: `RoutinesViewModel.isRoutineCompletedToday`, filtro de "hoy" de `DashboardViewModel`, `BuildWidgetSnapshotUseCase` y `RoutineReminderWorker`.
3. **`StreakCalculator` v2** (mantener puro y testeado):
   - Nueva firma: `calculate(completedDates, today, isScheduled: (LocalDate) -> Boolean, graceMisses: Int = 1)`.
   - La racha cuenta **ocurrencias programadas consecutivas cumplidas** (los días no programados no rompen ni suman). Para DAILY equivale al comportamiento actual.
   - **Protector de racha**: se tolera `graceMisses` ocurrencia(s) fallada(s) sin romper; la UI puede distinguir "racha protegida" (devolver `graceUsed: Boolean` en `StreakInfo`).
   - `RoutinesRepositoryImpl.updateRoutineCompletion` pasa el predicado construido desde la rutina.
4. **Recordatorios**: `RoutineReminderWorker` decide con `RoutineSchedule.isDueOn`. Para EVERY_N_DAYS necesita `lastCompletedAt` fresco → el worker lee el doc de la rutina al dispararse (la caché offline de Firestore lo cubre sin red). De paso arregla títulos/horas obsoletos en `inputData`.
5. **Heatmap** (los datos ya existen: `completions/{yyyy-MM-dd}` con `date` y `userId`):
   - Repo: `getCompletions(userId, householdId, routineId, type, from, to): Result<List<CompletionDay>>`.
   - UI: al tocar una `RoutineCard` se abre `RoutineDetailSheet` (nuevo) con: heatmap mensual (`CompletionHeatmap.kt`, grid Compose simple, sin dependencias), racha actual/mejor, tasa de cumplimiento del mes (completados ÷ programados, calculado con `RoutineSchedule`).
   - Diálogo de crear/editar: selector de frecuencia con stepper de N días + toggle "Modo vacaciones".

### Archivos

- **Modificar:** `Routine.kt`, `RoutinesUseCases.kt` (parámetros nuevos), `RoutinesRepository.kt` + `RoutinesRepositoryImpl.kt`, `StreakCalculator.kt`, `StreakInfo.kt`, `RoutinesViewModel.kt`, `RoutinesScreen.kt` (diálogo + sheet), `DashboardViewModel.kt`, `BuildWidgetSnapshotUseCase.kt`, `RoutineReminderWorker.kt`, `ReminderUseCases.kt`, strings ×3.
- **Crear:** `RoutineSchedule.kt` + test, `CompletionHeatmap.kt`, `RoutineDetailSheet.kt`.

### Tests

- `StreakCalculatorTest` (ampliar): rutina semanal de lunes acumula racha con completados en lunes consecutivos; hueco no programado no rompe; grace de 1 fallo; EVERY_N_DAYS.
- `RoutineScheduleTest`: cada frecuencia × casos límite (recién creada, pausada, intervalo cumplido/no cumplido).

### Riesgos

- El refactor toca dashboard, widget y worker: hacer el refactor a `RoutineSchedule` **primero con comportamiento idéntico** (commit propio) y añadir EVERY_N_DAYS después.
- Cambio de semántica de rachas existentes (recalculo puede subir rachas de rutinas semanales — es una mejora, pero avisar en el CHANGELOG).

---

## Fase 3 — `@@RUTINA@@`: crear rutinas desde el chat ✅ COMPLETADA (2026-07-18)

**Objetivo:** replicar el flujo probado de `@@LISTA@@` para que "proponme un plan de limpieza semanal" termine en una tarjeta "Crear 5 rutinas". Es además el mejor onboarding posible de la app.

> **Resultado:** `assembleDebug` OK y 195 tests unitarios en verde (38 nuevos en esta fase).
>
> **Decisiones que conviene recordar:**
> 1. **`AiShoppingListFormat` se absorbió en `AiStructuredBlocks`** (la opción que el plan dejaba
>    abierta). Ahí viven los dos marcadores, el `stripFromDisplay` común y un `extractJsonRegion`
>    compartido por ambos parsers, que **corta el alcance en el siguiente marcador** para que una
>    respuesta con los dos bloques no mezcle un JSON con el otro.
> 2. **El parser de rutinas es deliberadamente más estricto que el de la compra**: exige el marcador
>    `@@RUTINA@@` o una clave propia (`routines`/`rutinas`). Sin eso devolvería rutinas fantasma a
>    partir del bloque de la compra, porque ambos usan `name`/`nombre`.
> 3. **Coherencias que aplica el parser** porque los modelos pequeños se las saltan: una semanal sin
>    días válidos se degrada a diaria (si no, no tocaría nunca), `intervalDays` solo sobrevive en las
>    de intervalo, y las de intervalo sin número reciben uno por defecto.
> 4. **Las rutinas se crean sin recordatorio**: ponerle hora a cinco rutinas de golpe sin preguntar
>    sería invasivo. El usuario se lo añade al editarlas.
> 5. **La tarjeta lista los títulos propuestos** y deja elegir Personal/Casa antes de crear: crear
>    cosas a ciegas da mal cuerpo.
> 6. **Éxito parcial**: si alguna rutina falla pero otras entran, se informa del número creado; solo
>    es error si no entra ninguna (no hay batch porque personales y de casa viven en colecciones
>    distintas).
> 7. **Quick prompt "Plan de limpieza"** contextual: aparece cuando la casa tiene menos de 3 rutinas.

### Diseño

1. **Prompt** (en `GetAiContextUseCase.getBasePersonality()`), regla nueva junto a la de `@@LISTA@@`:
   - Marcador `@@RUTINA@@` + JSON en una línea: `{"routines":[{"title":"Fregar la cocina","description":"","frequency":"semanal","days":["lunes","jueves"],"interval_days":null}]}`.
   - `frequency` ∈ `diaria | semanal | cada_n_dias`; **días con nombre en español**, no números — los modelos pequeños fallan menos con nombres, y el mapeo `"lunes"` → `Calendar.MONDAY` se hace en el parser.
   - Solo emitir el marcador si el usuario pide rutinas/hábitos/plan de limpieza.
2. **Parser** — `ParseAiRoutinesUseCase` (espejo de `ParseAiShoppingListUseCase`: Gson lenient + fallback regex + claves alternativas es/en):
   - Salida: `AiRoutineSuggestion(title, description, frequency: RoutineFrequency, scheduledDays: List<Int>, intervalDays: Int?)`.
   - Tolerancias: frecuencia desconocida → DAILY; días inválidos → ignorados; `interval_days` textual → primer número; máx. 10 rutinas (coerce); dedupe por título normalizado.
3. **Formato/ocultación** — hoy `AiShoppingListFormat.stripFromDisplay` solo conoce la lista. Generalizar en `AiStructuredBlocks` (nuevo, mismo paquete `domain/util`): strip de ambos marcadores + fences con `shopping_list` o `routines` + fence a medio streaming. `ChatMessageItem` pasa a llamar al punto único. `AiShoppingListFormat` queda como constantes del marcador (o se absorbe — decisión al implementar).
4. **Ejecución** — `AddAiRoutinesUseCase` (espejo de `AddAiItemsToShoppingListUseCase`): resuelve usuario y casa activa, crea vía `AddRoutineUseCase` en bucle (reutiliza validación y `Result`), devuelve nº creado. Recordatorios: no se programan (nacen sin `reminderTime`; el usuario lo pone al editar).
5. **UI** — `RoutineSuggestionCard` (espejo de `ShoppingSuggestionCard`): lista de rutinas propuestas + selector Personal/Casa (default Personal) + botón "Crear rutinas" → snackbar plural. Estado en `AiAssistantUiState`: `routineSuggestions: Map<String, List<AiRoutineSuggestion>>`, `addedRoutineMessageIds`, `addingRoutineMessageId` (calcado del patrón de compra). `parseAndStoreSuggestions` del ViewModel analiza ambos bloques.
6. **Quick prompt** nuevo: "Plan de limpieza semanal" (encadena con fase 1: contextual si no hay rutinas de casa aún).

### Archivos

- **Crear:** `AiRoutineSuggestion.kt`, `ParseAiRoutinesUseCase.kt` + test, `AddAiRoutinesUseCase.kt` + test, `AiStructuredBlocks.kt` + test, `RoutineSuggestionCard.kt`.
- **Modificar:** `GetAiContextUseCase.kt` (prompt), `AiAssistantViewModel.kt`, `AiAssistantUiState.kt`, `ChatMessageItem.kt` (strip único + tarjeta nueva), strings ×3.

### Tests

- Parser: marcador limpio, con fences, JSON roto → fallback regex, mapeo de días es/en, frecuencia inválida → DAILY, cap de 10, dedupe.
- `AiStructuredBlocks`: respuestas con uno, otro y ambos bloques; streaming a medio fence.
- `AddAiRoutinesUseCase` con fakes (sin sesión, sin casa, éxito parcial).

### Riesgos

- Los modelos pequeños pueden mezclar los dos JSON o inventar esquema → por eso parser tolerante + verificación manual con Qwen 1.5B y Gemma E2B antes de dar por cerrada la fase.
- Prompt más largo = menos hueco de contexto → medir con los caps de la fase 1 ya puestos.

---

## Fase 4 — Despensa ligera + "¿qué cocino con lo que tengo?" ✅ COMPLETADA (2026-07-18)

**Objetivo:** el diferenciador señalado por el análisis de mercado: cruzar lista, casa e IA. MVP deliberadamente simple: "esto hay en casa", sin caducidades ni escaneos.

> **Resultado:** `assembleDebug` OK y 222 tests unitarios en verde (27 nuevos en esta fase).
> Como se anticipó, **no hizo falta tocar `firestore.rules`**: `pantry_items` cae bajo el comodín
> `{document=**}` de `households`.
>
> **Decisiones que conviene recordar:**
> 1. **La fusión de cantidades vive en `PantryMerge`, una función pura y testeada**, no en el
>    repositorio. Así la comparten los dos sitios que escriben en la despensa: el archivado de la
>    compra (dentro de su mismo batch atómico) y `PantryRepositoryImpl`.
> 2. **Se descartó `FieldValue.increment`**: no permite aplicar la regla de "solo sumo si la unidad
>    coincide". En su lugar se lee la despensa antes de montar el batch y se escribe el estado final.
>    Efecto secundario: dos dispositivos archivando a la vez podrían pisarse (última escritura gana).
>    Asumido y documentado; es un caso poco probable y de impacto bajo.
> 3. **El id de documento es el nombre normalizado** (`ProductNameNormalizer`), así que "Plátano",
>    " platano " y "PLÁTANO" son la misma entrada. El normalizador es reutilizable para el
>    autocompletado predictivo que quedó fuera del top 5.
> 4. **Unidades distintas no se suman** ("2 kg" + "3 unidad" no significa nada): se conserva lo que
>    había y solo se refresca la fecha. Convertir unidades sería sobreingeniería.
> 5. **El aviso "ya tienes N en casa" se pinta en el alta de producto**, que es donde evita la
>    compra duplicada, además de alimentar el contexto de la IA.
> 6. **La regla de prompt más valiosa de la fase**: si el modelo propone recetas usando la despensa,
>    en `@@LISTA@@` debe incluir **solo lo que falte**. Ese es el momento "wow" de la feature.
>
> **Fuera de alcance, como estaba previsto:** consumir la despensa automáticamente al cocinar
> (requiere emparejar receta→despensa) y añadir productos a mano sin pasar por la compra.

### Diseño

1. **Modelo y datos:**
   - `PantryItem(id, name, quantity: Int, unit, category, updatedAt)` en `feature/shopping/domain/model/`.
   - Firestore: `households/{id}/pantry_items/{itemId}` — **cubierta por las reglas actuales, sin redeploy**.
   - **Id del doc = nombre normalizado** (lowercase, sin acentos, trim): dedupe gratis y merge atómico con `SetOptions.merge()` + `FieldValue.increment(quantity)`.
2. **Repositorio:** `PantryRepository` + `PantryRepositoryImpl` (mismo patrón `callbackFlow` del shopping): `observePantry`, `upsertItems(items)` (batch), `adjustQuantity(itemId, delta)` (elimina si llega a 0), `deleteItem`.
3. **Flujo de entrada — archivar compra:** `ArchiveShoppingListUseCase` gana parámetro `stockPantry: Boolean`. En `ShoppingRepositoryImpl.archiveShoppingList`, **el mismo batch atómico** que archiva añade los items marcados a `pantry_items` (set merge + increment). El diálogo de archivar muestra checkbox "Guardar lo comprado en la despensa" (default activado).
4. **UI:** `SegmentedButton` M3 arriba de `ShoppingScreen`: **Lista | Despensa**. La vista Despensa agrupa por categoría, steppers +/− por item, swipe para borrar, estado vacío explicativo. En `AddProductScreen` y quick add: hint "ya tienes N en la despensa" (lookup por nombre normalizado sobre el estado observado).
5. **IA (cierra el círculo):**
   - `GetAiContextUseCase` añade sección "Despensa (lo que hay en casa)" (cap ~30 items).
   - Regla de prompt: *"si propones recetas usando la despensa, en `@@LISTA@@` incluye SOLO los ingredientes que falten"* → la tarjeta "Añadir a la lista" pasa a añadir únicamente lo que falta. Este es el momento "wow" de la feature.
   - Quick prompt contextual (fase 1): "¿Qué cocino con lo que tengo?" si despensa ≥ 3 items.

### Archivos

- **Crear:** `PantryItem.kt`, `PantryRepository.kt`, `PantryRepositoryImpl.kt`, `PantryUseCases.kt` (observe/upsert/adjust/delete) + tests, `PantryContent.kt` (composable), normalizador de nombres (`ProductNameNormalizer.kt` + test — reutilizable por el autocompletado futuro).
- **Modificar:** `ShoppingRepository.kt` + `Impl` (archivar con stock), `ArchiveShoppingListUseCase.kt`, `ShoppingViewModel.kt` + `ShoppingScreen.kt` (segmented + diálogo), `AddProductScreen.kt`/`AddProductViewModel.kt` (hint), módulo DI donde se binde `ShoppingRepository` (añadir binding de `PantryRepository`), `GetAiContextUseCase.kt`, `GetContextualQuickPromptsUseCase.kt`, strings ×3.

### Tests

- `ProductNameNormalizerTest` (acentos, mayúsculas, espacios).
- `ArchiveShoppingListUseCaseTest` con fake: solo los marcados van a despensa; flag off → no stock.
- `PantryUseCasesTest`: merge por nombre, adjust a 0 elimina.

### Riesgos

- Cantidades con unidades distintas ("2 kg" vs "3 unidad" de tomate): MVP suma solo si la unidad coincide; si no, conserva la existente y actualiza `updatedAt` (documentar en el código). No sobre-ingenierizar conversiones.
- Consumo automático al cocinar: **fuera de alcance** (requiere matching receta→despensa); queda anotado como futuro.

---

## Fase 5 — Rotación y balance del hogar ✅ COMPLETADA (2026-07-18)

**Objetivo:** la feature por la que la gente instala Sweepy/Flatastic, con la ventaja de que Habitly ya tiene el household nativo. Sin leaderboard competitivo: asignación, rotación y un balance neutro.

> **Resultado:** `assembleDebug` OK y 240 tests unitarios en verde (18 nuevos en esta fase).
>
> **Decisiones que conviene recordar:**
> 1. **`updateRoutine` pasó a recibir la `Routine` entera** en vez de una lista de parámetros:
>    con la rotación habrían sido 13. `UpdateRoutineUseCase` mantiene su API por parámetros
>    (la usa la UI) y construye el `copy` internamente, así que sus tests siguieron valiendo.
> 2. **`getCompletions` devuelve `RoutineCompletion(date, userId)`**, no solo fechas: el balance
>    necesita saber quién. El heatmap mapea a fechas dentro de su use case.
> 3. **El balance NO es un ranking**: no ordena por "ganador" ni pinta posiciones, respeta el
>    orden de miembros de la casa y añade un total cooperativo. Los leaderboards motivan a unos
>    y queman a otros, y esto es una casa.
> 4. **Deshacer un completado devuelve el turno a quien desmarcó**, no al anterior: si la
>    desmarcas es porque en realidad no estaba hecha.
> 5. **El "te toca a ti" funciona sin backend**: el worker de recordatorios omite la notificación
>    si la rutina está asignada a otro miembro. FCM sigue sin hacer falta.
> 6. **N+1 consultas asumidas** en el balance (una por rutina de casa), como estaba previsto.
>    La alternativa (*collection group query*) obligaría a tocar `firestore.rules`.
>
> **Concurrencia:** dos miembros completando a la vez pueden pisarse el `assignedTo` (última
> escritura gana). Es inofensivo: ambos completados quedan registrados en `completions` y el
> balance los cuenta bien. Sin transacción, como estaba previsto.

### Diseño

1. **Modelo:** `Routine` gana `assignedTo: String? = null` y `rotationEnabled: Boolean = false` (la UI solo lo expone en rutinas de casa).
2. **Rotación** — `RotationCalculator` (nuevo, puro): `next(members: List<String>, current: String?): String?` — siguiente en la lista de `members` de la casa (orden estable), con wraparound y tolerancia a miembros ya no presentes.
   - Disparo: en `RoutinesViewModel.onToggleRoutine`, tras completar con éxito una rutina con `rotationEnabled`, llama a `AdvanceRotationUseCase` (nuevo) con los `members` que el ViewModel ya observa → escribe `assignedTo` (método nuevo `updateRoutineAssignment` en el repo, update de un solo campo).
   - Deshacer un completado devuelve la asignación a quien desmarcó (decisión explícita, documentar en el use case).
3. **Recordatorios conscientes del asignado:** `RoutineReminderWorker` (que tras la fase 2 ya relee el doc fresco) omite la notificación si `assignedTo != null && assignedTo != uid actual`. Resultado: "te toca a ti" sin backend ni FCM.
4. **Balance semanal** — `GetHouseholdBalanceUseCase(householdId, from, to)`:
   - `observeHouseholdRoutines(...).first()` → por cada rutina, query a `completions` con `date` en rango → agrega `Map<userId, Int>`.
   - N+1 queries asumido y documentado (casas con <20 rutinas). La alternativa (collection group query sobre `completions`) exigiría regla nueva de Firestore y **se descarta a propósito** para no tocar reglas.
5. **UI:**
   - `RoutineCard`: chip "→ {nickname}" cuando hay asignado (los nicknames ya están en `RoutinesUiState.memberNicknames`); resaltar "te toca a ti".
   - Diálogo crear/editar (rutinas de casa): switch "Rotar entre miembros" + dropdown de asignado inicial.
   - Card "Balance de la semana" en la sección de casa de `RoutinesScreen` (o en `HouseholdScreen`, decidir al maquetar): barras horizontales por miembro + navegación semana anterior/siguiente + total cooperativo del mes.

### Archivos

- **Crear:** `RotationCalculator.kt` + test, `AdvanceRotationUseCase.kt` + test, `GetHouseholdBalanceUseCase.kt` + test, `HouseholdBalanceCard.kt`.
- **Modificar:** `Routine.kt`, `RoutinesRepository.kt` + `Impl` (`updateRoutineAssignment`, query de completions por rango — reutiliza la de la fase 2), `RoutinesUseCases.kt`, `RoutinesViewModel.kt`, `RoutinesScreen.kt`, `RoutineReminderWorker.kt`, strings ×3.

### Tests

- `RotationCalculatorTest`: wraparound, miembro eliminado de la casa, lista de 1.
- `GetHouseholdBalanceUseCaseTest` con fakes: agregación multi-rutina, rango de semana, miembro sin completados.

### Riesgos

- Dos miembros completan a la vez → última escritura de `assignedTo` gana; inofensivo (ambos completados quedan registrados en `completions`). Documentado, sin transacción.
- Sin FCM, el "te toca" solo se ve al abrir la app o vía recordatorio local. Suficiente para el MVP; el push de casa sigue diferido (auditoría, fase 4).

---

## Qué NO entra en este plan (y dónde está anotado)

Precios/presupuesto, plantillas de lista, autocompletado predictivo, modo "comprando", fotos en items → [MEJORAS_LISTA_COMPRA.md](MEJORAS_LISTA_COMPRA.md). "X veces por semana", zonas, hitos cooperativos → [MEJORAS_RUTINAS.md](MEJORAS_RUTINAS.md). Voz, perfil del hogar, menú persistente, recetario, modelo ligero/Gemini Nano, resumen proactivo → [MEJORAS_ASISTENTE_IA.md](MEJORAS_ASISTENTE_IA.md). Monetización (Habitly Plus) y FCM → `AUDITORIA_HABITLY.md`, fase 4.
