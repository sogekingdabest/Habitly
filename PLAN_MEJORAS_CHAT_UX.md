# Plan de implementación — Mejoras UX del chat del asistente IA

> Fecha: 2026-07-22 · Estado: propuesto (pendiente de implementar)
>
> Origen: 7 peticiones de UX sobre el chat (copiar, spinner, scroll, selección de texto,
> chip lento, respuestas largas, contexto largo) + revisión del repo
> [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery) como referencia.

---

## 0. Qué nos aporta el AI Edge Gallery

Ficheros estudiados (rama `main`, `Android/src/app/src/main/java/com/google/ai/edge/gallery/`):

| Fichero | Qué resuelve | Lo usamos en |
|---|---|---|
| `ui/common/chat/ChatPanel.kt` | Detección "estoy al final" con `!canScrollForward` + debounce 500 ms; botón flotante **ScrollToBottomButton**; spacer dinámico que ancla el último mensaje del usuario arriba; fila de acciones (copiar + latencia) bajo la respuesta terminada | Fases 2 y 3 |
| `ui/common/chat/MessageBodyText.kt` | Mensajes del usuario envueltos en `LongPressCopyContainer`; respuesta del agente seleccionable | Fases 1 y 4 |
| `ui/common/chat/LongPressCopyContainer.kt` | Componente portable (Apache-2.0): long-press → menú "Copiar" con haptic + semántica a11y | Fase 1 (opcional) |
| `ui/common/chat/MessageBodyLoading.kt` | El loader existe **solo como mensaje propio antes del primer token**; durante el streaming no hay ningún indicador | Fase 2 |
| `ui/common/BufferedFadingMarkdownText.kt` | Streaming con crossfade en dos capas (el texto nuevo aparece con fundido, sin parpadeo) | Fase 2 (pulido opcional) |
| `ui/common/MarkdownText.kt` | Usan **compose-richtext** (nativo Compose) → `SelectionContainer` funciona directo | Contexto Fase 1 |

Conclusiones clave:

1. **Gallery no sigue el stream con scroll**: al enviar, ancla tu mensaje al borde superior
   (con un spacer inferior dinámico) y la respuesta crece hacia abajo en espacio vacío.
   No hay "tirón" porque nunca hay auto-scroll durante el streaming. Es el patrón
   ChatGPT/Claude. Nuestra Fase 3 ofrece un arreglo mínimo (A) y este patrón como opción (B).
2. **El spinner nunca convive con texto**: o hay mensaje de carga (esperando primer token),
   o hay texto creciendo. Nunca ambos.
3. **Copiar**: icono discreto bajo la respuesta terminada (28 dp, tinte al 60 %) + long-press
   en los mensajes del usuario.
4. Para el **punto 7 (compactar contexto) gallery no aporta patrón** (sus chats son
   efímeros por tarea): el diseño es nuestro.
5. Nuestra `AiAssistantRepositoryImpl` ya sigue sus patrones de motor (cancelProcess,
   conversaciones efímeras…), así que las piezas nuevas (resumen efímero) encajan igual.

---

## Orden recomendado y estimación

| Fase | Petición | Esfuerzo | Riesgo | Dependencias |
|---|---|---|---|---|
| **F1** Copiar + selección | 1 y 4 | ~1-2 h | Bajo | ✅ IMPLEMENTADO 2026-07-22 |
| **F2** Streaming sin spinner | 2 | ~1-2 h | Bajo | ✅ IMPLEMENTADO 2026-07-22 |
| **F3** Scroll libre + botón bajar | 3 | ~2-3 h | Medio | ✅ IMPLEMENTADO 2026-07-22 |
| **F4** Chip → tarjeta directa | 5 | ~3-4 h | Medio | ✅ IMPLEMENTADO 2026-07-22 |
| **F5** Respuestas más cortas | 6 | ~1 h + pruebas en dispositivo | Bajo | ✅ IMPLEMENTADO 2026-07-22 |
| **F6** Contexto largo: medidor + compactar | 7 | ~5-8 h | Alto (migración Room + motor) | ✅ IMPLEMENTADO 2026-07-22 |

Sugerencia de PRs: **PR-1 = F2+F3** (la molestia diaria más grande), **PR-2 = F1**,
**PR-3 = F4**, **PR-4 = F5**, **PR-5 = F6**.

---

## Fase 1 — Botón de copiar y selección de texto (peticiones 1 y 4)

> ✅ **IMPLEMENTADO 2026-07-22.** `ChatMessageItem` pasó de `Row` a `Column` (burbuja +
> fila de acción); botón de copiar (28 dp, icono 18 dp, tinte 0.6) bajo cada burbuja —
> siempre en el usuario, y en el asistente solo con el mensaje terminado (`!isStreaming`).
> El `Text` del usuario va en `SelectionContainer`; a todos los `MarkdownText` (texto y
> tabla) se les pasó `isTextSelectable = true` (0.5.7 lo soporta). Callback `onCopyMessage`
> en `AiAssistantScreen` con `LocalClipboardManager` (consistente con HouseholdScreen; el
> snackbar "Copiado" solo se lanza en <Android 13, en 13+ lo avisa el sistema). Copia del
> asistente = `stripFromDisplay` (sin el bloque `@@…@@`). Strings `ai_copy`/`ai_copied` (×3).
> Compila. Nota: el parámetro del componente pasó de `isAwaitingFirstToken` a `isStreaming`
> (los puntos se derivan internamente de `isStreaming && displayText.isBlank()`).

### Objetivo
Copiar cualquier mensaje (el prompt propio y las respuestas) con un toque, y poder
seleccionar texto dentro de las burbujas.

### Problema actual
- `ChatMessageItem.kt` no ofrece ninguna acción de copia.
- El texto del usuario es `Text` plano (no seleccionable) y el del asistente es
  `MarkdownText` de jeziellago (TextView interno vía AndroidView): **`SelectionContainer`
  no le afecta**, hay que activar la selección del propio TextView.

### Cambios

`presentation/components/ChatMessageItem.kt`
1. Mensaje del usuario: envolver el `Text` en `SelectionContainer`.
2. Mensajes del asistente: pasar `isTextSelectable = true` a **todas** las llamadas a
   `MarkdownText` (segmentos de texto y tablas). La librería (`compose-markdown 0.5.7`,
   ya en `libs.versions.toml`) expone ese parámetro y activa `setTextIsSelectable(true)`
   en el TextView. *(Verificar la firma exacta al compilar.)*
3. Fila de acciones bajo la burbuja (patrón gallery, `ChatPanel.kt` líneas 542-560):
   - Nuevo parámetro `onCopy: (String) -> Unit` y `showActions: Boolean`.
   - `IconButton` de 28 dp con `Icons.Rounded.ContentCopy` a 18 dp, tinte
     `onSurfaceVariant.copy(alpha = 0.6f)`, alineado al lado de la burbuja.
   - Usuario: siempre visible (es el "copiar el prompt" pedido).
   - Asistente: solo con el mensaje terminado (`showActions = false` para el último
     mensaje mientras `isGenerating`).
   - Texto copiado: usuario → `message.content` tal cual; asistente →
     `AiStructuredBlocks.stripFromDisplay(message.content)` (sin el bloque `@@…@@`).

`presentation/AiAssistantScreen.kt`
4. Crear el callback una vez y pasarlo a cada item:
   ```kotlin
   val clipboard = LocalClipboardManager.current
   val copiedMsg = stringResource(R.string.ai_copied)
   val onCopy: (String) -> Unit = { text ->
       clipboard.setText(AnnotatedString(text))
       // Android 13+ ya muestra su propia confirmación del sistema; solo pre-13 avisamos.
       if (Build.VERSION.SDK_INT < 33) scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
   }
   ```

Opcional (pulido): portar `LongPressCopyContainer.kt` de gallery (Apache-2.0, conservar
cabecera de licencia) para long-press con haptic sobre la burbuja del usuario, además del icono.

### Strings (values, values-en, values-gl)
- `ai_copy` = "Copiar" / "Copy" / "Copiar"
- `ai_copied` = "Copiado" / "Copied" / "Copiado"

### Pruebas
- Manual en dispositivo: seleccionar texto en respuesta con markdown (negritas, listas),
  en tabla, y en mensaje propio; copiar ambos lados; comprobar que el JSON oculto no sale.
- Ojo conocido: la selección del TextView dentro de `LazyColumn` funciona, pero al hacer
  scroll la selección se pierde — aceptable (a ChatGPT le pasa igual).

---

## Fase 2 — Streaming limpio, sin circulito (petición 2)

> ✅ **IMPLEMENTADO 2026-07-22.** Nuevo `TypingIndicator.kt`; `ChatMessageItem` acepta
> `isAwaitingFirstToken`; en `AiAssistantScreen` se eliminó el `CircularProgressIndicator`
> (y su import) y la condición pasó a `isGenerating || isExtractingSuggestions`, dejando
> solo la `SuggestionPreparingCard`. Compila (`:app:compileDebugKotlin`).

### Objetivo
Mientras el modelo responde solo se ve el mensaje completándose (como ChatGPT/Claude).
Indicador únicamente cuando **aún no hay nada que enseñar** (esperando el primer token).

### Problema actual
`AiAssistantScreen.kt` líneas 466-488: mientras `isGenerating` hay un item extra con
`CircularProgressIndicator` debajo del mensaje que crece, durante TODO el streaming.

### Cambios

`presentation/AiAssistantScreen.kt`
1. Eliminar el `CircularProgressIndicator` y su `Box`. El item extra queda solo para el
   caso útil que ya existe: `SuggestionPreparingCard` cuando
   `uiState.isExtractingSuggestions || AiStructuredBlocks.hasPendingStructuredBlock(tail)`
   (el aviso de "preparando sugerencias" sí hace falta: el texto visible deja de crecer
   mientras el modelo escribe el bloque oculto).
   ```kotlin
   if (uiState.isGenerating || uiState.isExtractingSuggestions) {
       val streamingTail = …
       if (uiState.isExtractingSuggestions ||
           AiStructuredBlocks.hasPendingStructuredBlock(streamingTail)) {
           item(key = "preparing") { SuggestionPreparingCard() }
       }
       // sin else: ya no hay spinner
   }
   ```
   *(Nota: el `|| isExtractingSuggestions` en la condición exterior lo necesita la Fase 4,
   que extrae sin generar.)*

2. Pasar a `ChatMessageItem` si el mensaje está "vacío y generando":
   ```kotlin
   ChatMessageItem(
       message = message,
       isAwaitingFirstToken = uiState.isGenerating &&
           message.id == uiState.chatSession.messages.lastOrNull()?.id &&
           message.role is MessageRole.Assistant &&
           AiStructuredBlocks.stripFromDisplay(message.content).isBlank()
   )
   ```

`presentation/components/ChatMessageItem.kt`
3. Si `isAwaitingFirstToken`, en lugar del markdown vacío pintar un **TypingIndicator**
   dentro de la burbuja (y ocultar el timestamp hasta que llegue texto).

`presentation/components/TypingIndicator.kt` (nuevo, ~40 líneas)
4. Tres puntos de 7 dp con `rememberInfiniteTransition` y desfase (el clásico "···"
   de los chats). Alternativa aún más simple: el patrón "breathing alpha" de
   `MessageBodyLoading.kt` de gallery (alpha 0.3→1 en 1 s, `RepeatMode.Reverse`).

Opcional (pulido posterior): adaptar la técnica de `BufferedFadingMarkdownText.kt`
(dos capas con crossfade de 120 ms) para que los trozos nuevos entren con fundido en vez
de "a saltos". Funciona superponiendo dos composables cualesquiera; con el MarkdownText
de AndroidView duplica el coste de render, así que solo si tras F2+F3 aún se ve brusco.

### Pruebas
- Manual: enviar mensaje → puntos animados en burbuja → texto crece sin spinner →
  si hay bloque `@@` aparece la tarjeta "preparando" → fin.
- Regresión: parar la generación con el mensaje aún vacío (los puntos deben desaparecer).

---

## Fase 3 — Scroll libre durante la generación + botón "ir al final" (petición 3)

> ✅ **IMPLEMENTADO 2026-07-22 (Opción A).** `autoFollow` + `NestedScrollConnection`
> (rompe el seguimiento al arrastrar hacia abajo), item ancla `"bottom-anchor"`, efecto de
> streaming reescrito (objetivo = ancla, solo si `autoFollow`), y FAB `ScrollToBottomFab`
> con debounce de 500 ms sobre `canScrollForward`. String `ai_scroll_to_bottom` (×3).
> `AnimatedVisibility` se extrajo a un composable propio para evitar que resolviera a la
> sobrecarga `ColumnScope`. Compila.

### Objetivo
Poder subir a releer mientras el modelo escribe, sin que te arrastre de vuelta.
Botón flotante para volver al final (y re-enganchar el seguimiento), como ChatGPT.

### Diagnóstico del bug actual
`AiAssistantScreen.kt` líneas 137-149 ya intentan "solo seguir si estás al final", pero la
condición es `lastVisible >= totalItemsCount - 2`, y **el mensaje en streaming es un único
item muy alto**: aunque subas una pantalla entera, ese item sigue siendo visible, la
condición sigue cumpliéndose y `scrollToItem` te devuelve abajo en cada refresco (~12/s).

### Cambios (Opción A — recomendada, diff mínimo)

`presentation/AiAssistantScreen.kt`

1. **Estado de seguimiento explícito** que se rompe con el gesto del usuario y se
   re-engancha al volver al final:
   ```kotlin
   var autoFollow by remember { mutableStateOf(true) }

   // El gesto del usuario hacia arriba rompe el seguimiento (available.y > 0 = revelar
   // contenido anterior). Solo gestos reales: el scroll programático no pasa por aquí
   // como UserInput.
   val nestedScrollConnection = remember {
       object : NestedScrollConnection {
           override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
               if (source == NestedScrollSource.UserInput && available.y > 0f) autoFollow = false
               return Offset.Zero
           }
       }
   }

   // Volver al final (a mano o con el botón) re-engancha.
   LaunchedEffect(listState) {
       snapshotFlow { !listState.canScrollForward }
           .collect { atBottom -> if (atBottom) autoFollow = true }
   }
   ```
   `nestedScroll(nestedScrollConnection)` en el `LazyColumn`.

2. **Item ancla al final de la lista** (sustituye al item del spinner como objetivo de
   scroll; `scrollToItem` sobre el mensaje alto pinaría su INICIO, no la cola):
   ```kotlin
   item(key = "bottom-anchor") { Spacer(Modifier.height(1.dp)) }
   ```

3. **Reescribir el efecto de seguimiento**:
   ```kotlin
   LaunchedEffect(uiState.isGenerating, streamingLength) {
       if (!uiState.isGenerating || !autoFollow) return@LaunchedEffect
       val total = listState.layoutInfo.totalItemsCount
       if (total > 0) listState.scrollToItem(total - 1)   // el ancla
   }
   ```
   (Se elimina el cálculo de `lastVisible`.) El efecto de "scroll al enviar" (línea 130)
   se mantiene y además pone `autoFollow = true`.

4. **Botón flotante "ir al final"** (patrón `ScrollToBottomButton` de gallery, con su
   debounce de 500 ms para que no parpadee):
   ```kotlin
   var showScrollToBottom by remember { mutableStateOf(false) }
   LaunchedEffect(listState) {
       snapshotFlow { listState.canScrollForward }.collectLatest { canScroll ->
           if (canScroll) delay(500)          // solo aparece si te quedas arriba
           showScrollToBottom = canScroll
       }
   }
   ```
   `AnimatedVisibility(showScrollToBottom)` con un `SmallFloatingActionButton`
   (`Icons.Default.KeyboardArrowDown`, `contentDescription = R.string.ai_scroll_to_bottom`),
   superpuesto sobre la lista, centrado, justo encima del `PromptInput` (envolver la
   `LazyColumn` en un `Box` con el FAB `align(Alignment.BottomCenter)`). Al pulsarlo:
   `autoFollow = true` + `animateScrollToItem(total - 1)`.

### Opción B (alternativa "premium", si tras A se quiere el patrón ChatGPT completo)
Anclar el último mensaje del usuario al borde superior al enviar, con **spacer inferior
dinámico** = `alturaViewport − (alturas de los items desde el último mensaje del usuario)`
(gallery `ChatPanel.kt` líneas 215-279 + 596-600, con `itemHeights` vía `onSizeChanged`).
La respuesta crece hacia abajo en hueco vacío y el auto-scroll durante streaming
desaparece por completo. Más invasiva (medir alturas de items en LazyColumn); solo si A
no deja la sensación deseada.

### Strings
- `ai_scroll_to_bottom` = "Ir al final" / "Scroll to bottom" / "Ir ao final"

### Pruebas
- Manual: durante una respuesta larga, subir → no arrastra; aparece FAB; tocar FAB →
  baja y vuelve a seguir; dejarse estar abajo → sigue pegado al final.
- Con teclado abierto (imePadding) y con la tarjeta "preparando sugerencias" visible.

---

## Fase 4 — Chip de seguimiento sin segunda respuesta larga (petición 5)

> ✅ **IMPLEMENTADO 2026-07-22.** `onQuickPrompt` desvía el chip de seguimiento a
> `onFollowUpChip` (nuevo), que añade la confirmación del usuario + una burbuja "¡Voy! Aquí
> lo tienes 👇" (`FOLLOW_UP_ACK`, constante, sin modelo) y lanza directamente el turno 2 de
> extracción sobre la propuesta anterior (`extraSource`), sin turno conversacional. La
> tarjeta cuelga de la confirmación. Si la extracción no encuentra nada → se repone el chip
> + error para reintentar. Al terminar, `repository.resetSession()` (la conversación nativa
> no vio el intercambio). Se eliminó el vestigial `pendingFollowUpTarget` y se simplificó el
> bloque de puertas de `onSendMessage` (las confirmaciones **tecleadas** siguen por el camino
> conversacional, intactas). 3 tests nuevos + suite del asistente en verde (32/32 en el
> ViewModelTest). **Pendiente de validar en dispositivo** el tiempo real (~3-8 s esperado).

### Objetivo
Al tocar "Sí, a la lista" / "Sí, créalas": una confirmación instantánea tipo "¡Voy!" y
directamente la tarjeta de añadir. Sin re-narrar la lista entera.

### Problema actual
`onQuickPrompt` mete el texto del chip por `onSendMessage` → **turno conversacional
completo** (el modelo re-escribe la lista entera, 30-60 s) → y solo después el turno 2 de
extracción (`extractSuggestionsInto`) → tarjeta. El turno conversacional intermedio no
aporta nada: la propuesta ya está en el mensaje anterior y la extracción ya sabe leerla
de ahí (`extraSource = previousAssistantProse`).

### Cambios

`presentation/AiAssistantViewModel.kt`

1. En `onQuickPrompt`, desviar el chip de seguimiento a un camino nuevo (los chips
   normales y lo tecleado a mano no cambian):
   ```kotlin
   fun onQuickPrompt(prompt: String) {
       val followUp = _uiState.value.followUpPrompt
       if (followUp != null && prompt == followUp.prompt) {
           onFollowUpChip(followUp)   // camino rápido: SIN turno conversacional
           return
       }
       _uiState.update { it.copy(currentInput = prompt) }
       onSendMessage()
   }
   ```

2. Nuevo `onFollowUpChip(followUp: FollowUpSuggestion)`:
   ```kotlin
   private fun onFollowUpChip(followUp: FollowUpSuggestion) {
       if (_uiState.value.isGenerating) return
       // La propuesta vive en los mensajes ANTERIORES: misma fuente que hoy (2 últimos
       // mensajes del asistente, sin bloques, acotados por el final).
       val source = _uiState.value.chatSession.messages
           .filter { it.role is MessageRole.Assistant }
           .takeLast(FOLLOW_UP_SOURCE_MESSAGES)
           .joinToString("\n\n") { AiStructuredBlocks.stripFromDisplay(it.content) }
           .trim().takeLast(FOLLOW_UP_SOURCE_MAX_CHARS)

       launchChatOperation {
           _uiState.update { it.copy(followUpPrompt = null, error = null) }
           // Intercambio visible SIN modelo: tu confirmación + un "voy" instantáneo.
           var session = _uiState.value.chatSession
               .addUserMessage(followUp.prompt)
               .addAssistantMessage(FOLLOW_UP_ACK)
           _uiState.update { it.copy(chatSession = session) }
           repository.setActiveSession(session)
           repository.saveSession(session)

           try {
               if (followUp.target.includesRoutines) {
                   session = extractSuggestionsInto(session, AiStructuredBlocks.ROUTINES_MARKER,
                       extraSource = source) { repository.extractRoutines(it) }
               }
               if (followUp.target.includesShopping) {
                   session = extractSuggestionsInto(session, AiStructuredBlocks.SHOPPING_MARKER,
                       extraSource = source) { repository.extractShopping(it) }
               }
               parseAndStoreSuggestions(session)
               // La conversación nativa no ha visto este intercambio: que el siguiente
               // envío la recree desde la sesión persistida.
               repository.resetSession()
           } finally {
               _uiState.update { it.copy(isExtractingSuggestions = false) }
           }
       }
   }
   ```
   - `FOLLOW_UP_ACK = "¡Voy! Aquí lo tienes 👇"` (constante en el companion; si se
     prefiere localizable, moverla a strings y pasarla por el UiState — decisión menor).
   - La tarjeta se adjunta al mensaje "¡Voy!" (es el último del asistente cuando corre
     `extractSuggestionsInto`) → aparece justo debajo, como pide el flujo.
   - **Fallo de extracción** (sin JSON): `parseAndStoreSuggestions` no saca tarjeta →
     reponer el chip (`followUpPrompt = followUp`) y `error = "No pude preparar la
     tarjeta; vuelve a intentarlo."` para que el snackbar avise.

3. `extractSuggestionsInto` ya enciende/apaga `isExtractingSuggestions` — con el cambio
   de F2 (`if (isGenerating || isExtractingSuggestions)`) la `SuggestionPreparingCard`
   se ve también en este camino, que no pasa por `isGenerating`.

Nota de alcance: las confirmaciones **tecleadas** ("sí, añádelas") siguen yendo por el
camino conversacional (ahí el usuario sí espera respuesta del modelo, y con F5 será
corta). Si más adelante se quiere, pueden desviarse a este mismo camino rápido con las
puertas `isFollowUpConfirmation + looksLike*Proposal` que ya existen.

### Resultado esperado
Chip → burbuja tuya + "¡Voy! 👇" instantáneos → tarjeta "preparando" (~segundos del turno
de extracción a baja temperatura) → tarjeta de añadir. De ~30-60 s a ~3-8 s.

### Pruebas
- `AiAssistantViewModelTest` (usa `FakeAiAssistantRepository`):
  - chip de compra → NO se llama `sendMessage`; SÍ `extractShopping`; la sesión termina
    con 2 mensajes nuevos y el último lleva `@@LISTA@@`; `shoppingSuggestions` pobladas.
  - chip de rutinas → ídem con `extractRoutines`.
  - chip BOTH → ambas extracciones.
  - extracción vacía → chip repuesto + error.
  - `resetSession` invocado al terminar.
- Manual: pedir lista → chip → cronometrar; después seguir la conversación (el siguiente
  mensaje debe recrear la conversación nativa sin errores).

---

## Fase 5 — Respuestas más cortas (petición 6)

> ✅ **IMPLEMENTADO 2026-07-22.** Reescrito el bloque de estilo de `getBasePersonality()`
> en `GetAiContextUseCase.kt`: se quitó "respuestas completas, detalladas" y se añadió un
> bloque "ESTILO DE RESPUESTA (IMPORTANTE)" con reglas de brevedad (2-6 frases, sin relleno,
> sin repetir, listas escuetas, detalle solo si se pide). Conservados verbatim "Eres
> Habitly", "gestión del hogar", "NO uses tablas markdown" y el párrafo de despensa
> ("qué productos harían falta COMPRAR"), que son las frases que verifican los tests.
> `GetAiContextUseCaseTest` (22 tests) en verde. El toggle opcional "Breve/Detallada" NO se
> implementó (a decidir tras probar en dispositivo). **Pendiente de validar en dispositivo**
> que la extracción de tarjetas siga encontrando los ítems con respuestas cortas.

### Objetivo
Respuestas concisas por defecto (menos scroll, menos gasto de KV cache → conversaciones
más largas antes de compactar). Detalle solo si se pide.

### Problema actual
`GetAiContextUseCase.getBasePersonality()` pide literalmente **"Da respuestas completas,
detalladas y bien estructuradas"**. El modelo obedece.

### Cambios

`domain/usecase/GetAiContextUseCase.kt` — reescribir el bloque de estilo de
`getBasePersonality()` (el resto del prompt no cambia):

```
Eres Habitly, un asistente amigable experto en gestión del hogar. Tu objetivo es ayudar
al usuario a organizarse, dar ideas de rutinas, recetas para la lista de la compra y
consejos de limpieza.

ESTILO DE RESPUESTA:
- Sé BREVE y directo: responde en 2-6 frases, o con una lista corta si encaja mejor.
- Sin introducciones ni cierres de relleno ("¡Claro que sí!", "Espero que te sirva…").
- No repitas la pregunta del usuario ni información que ya diste en la conversación.
- En listas (compra, menús, rutinas): solo los elementos con su cantidad o frecuencia,
  sin párrafos explicativos por elemento.
- Amplía con detalle SOLO si el usuario lo pide explícitamente.
- Usa markdown cuando ayude: viñetas y negritas. NO uses tablas markdown. Cuando
  compares opciones o planifiques por días, usa un encabezado o lista por día.
```
(+ conservar las frases de contexto oculto y despensa tal como están.)

Notas técnicas:
- LiteRT-LM **no permite limitar tokens de salida por turno** (`maxNumTokens` del
  `EngineConfig` es el total de contexto), así que el control es solo por prompt.
- El prompt vive en la sesión (`systemPrompt`): los chats YA creados conservan el estilo
  antiguo hasta que `onLoadChat` refresque su contexto (ya lo hace hoy).

Opcional (si tras probar se echa de menos el detalle): ajuste "Breve / Detallada" en el
desplegable del modelo, persistido en `SharedPreferences`, que añade o quita el bloque
de estilo. Se decide después de una semana de uso real.

### Pruebas
- `GetAiContextUseCaseTest`: actualizar las aserciones que dependan del texto de la
  personalidad (hay tests que comprueban el contenido del contexto).
- Manual en dispositivo (Gemma E2B y E4B): "dame ideas para cenar hoy", "hazme la lista
  de la compra para la semana", "propónme rutinas de limpieza" → comprobar longitud y
  que la extracción (tarjetas) sigue encontrando los ítems con respuestas cortas.
  **Riesgo a vigilar**: respuestas demasiado escuetas empobrecen la fuente del turno de
  extracción; si pasa, añadir al estilo "en listas de compra incluye siempre cantidad".

---

## Fase 6 — Contexto largo: medidor + compactar en el mismo chat (petición 7)

> ✅ **IMPLEMENTADO 2026-07-22.** Campos `contextSummary`/`summarizedUpTo` en `AiChatSession`
> + `AiChatSessionEntity`; **Room v1→v2 con `MIGRATION_1_2`** (en el companion de
> `AiAssistantDatabase`, registrada en `AiAssistantModule`; columnas `NOT NULL DEFAULT`, sin
> `@ColumnInfo` a propósito para que Room omita la comparación de defaults). Nuevo
> `EstimateContextUsageUseCase` (puro, inyectado en el ViewModel). `summarizeConversation`
> en el repo (efímero, calcado de `generateSessionTitle`, con `SUMMARY_INSTRUCTION`; carga el
> engine si hiciera falta). `createConversation` antepone el resumen al system prompt y
> `buildHistory` hace `drop(summarizedUpTo)`. ViewModel: `onCompactContext` (KEEP=4,
> resetSession al final), `recomputeContextUsage` en send/chip/load, banner al 70% con botón
> "Compactar"/"Compactando…" + snackbar `ai_compacted`. Strings ×3. Tests: nuevo
> `EstimateContextUsageUseCaseTest` (5), 3 de compactación en el ViewModelTest (35 total),
> `AiChatSessionTest` (14). Todo compila y en verde.
>
> **La opción "Nuevo chat con resumen" y el auto-compactar (fase 6.5) NO se implementaron.**
> **PENDIENTE de validar en dispositivo**, sobre todo la **migración Room en una actualización
> real** (instalar build vieja con chats → actualizar → abrir historial sin crash) y que el
> modelo recuerde lo resumido tras compactar.

### Objetivo
Aviso discreto cuando la conversación se acerca al límite del modelo, con un botón que
**compacta el contexto sin abrir otro chat** (el historial visible no se toca; solo
cambia lo que se le pasa al modelo). "Nuevo chat con resumen" queda como acción
secundaria opcional.

### Contexto técnico actual
- KV cache total: `AiModelConfig.maxTokens = 4096` (historia + prompt + respuesta).
- Al recrear conversación, `buildHistory` recorta a `HISTORY_CHAR_BUDGET = 6000` chars
  (~1.700 tokens) y hay un reintento silencioso a 1.500 si el prefill revienta — hoy la
  conversación "olvida" sin avisar. La compactación convierte ese olvido silencioso en
  un resumen explícito y controlado.

### Cambios

**1. Modelo de dominio** — `domain/model/AiChatSession.kt`:
```kotlin
/** Resumen de la parte antigua de la conversación (compactación). Vacío = sin compactar. */
val contextSummary: String = "",
/** Nº de mensajes (desde el principio) ya cubiertos por contextSummary. */
val summarizedUpTo: Int = 0
```

**2. Persistencia** — `AiChatSessionEntity` (campos nuevos `String`/`Int` con default) y
`AiAssistantDatabase` **version = 2** con migración explícita (el builder de
`AiAssistantModule` NO tiene `fallbackToDestructiveMigration`; sin migración, crash):
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN contextSummary TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE ai_chat_sessions ADD COLUMN summarizedUpTo INTEGER NOT NULL DEFAULT 0")
    }
}
// AiAssistantModule: .addMigrations(AiAssistantDatabase.MIGRATION_1_2)
```

**3. Estimación de uso** — `domain/usecase/EstimateContextUsageUseCase.kt` (nuevo, puro y
testeable):
```kotlin
// ~3,5 chars/token en español; reserva ~1000 tokens para la respuesta.
operator fun invoke(session: AiChatSession, maxTokens: Int): Float {
    val historyChars = session.messages.drop(session.summarizedUpTo).sumOf { it.content.length }
    val chars = session.systemPrompt.length + session.contextSummary.length + historyChars
    val estimatedTokens = chars / 3.5f
    return (estimatedTokens / (maxTokens - RESPONSE_RESERVE_TOKENS)).coerceIn(0f, 1f)
}
```
El ViewModel lo recalcula al actualizar `chatSession` → `uiState.contextUsage: Float`.

**4. Resumen efímero** — `AiAssistantRepository` + Impl:
`suspend fun summarizeConversation(sourceText: String): String`, calcado de
`generateSessionTitle` (conversación efímera, `EXTRACTION_TEMPERATURE`,
`prepareForEphemeralConversation`, `closeConversationSafely`):
```kotlin
const val SUMMARY_INSTRUCTION =
    "Resume la conversación en como máximo 10 viñetas y 900 caracteres, en español: " +
    "qué pidió el usuario, decisiones tomadas, listas o rutinas ya creadas, y " +
    "preferencias o datos personales mencionados. Sin saludos ni texto fuera de las viñetas."
```
Fuente: mensajes `[0, messages.size - KEEP_RECENT_MESSAGES)` (KEEP = 4, los 2 últimos
turnos se conservan literales), pasados por `stripFromDisplay`, acotados a ~8.000 chars
por el final. Si ya había `contextSummary`, se antepone a la fuente ("resumen del
resumen").

**5. Inyección al modelo** — `AiAssistantRepositoryImpl`:
- `createConversation`: `systemInstruction = systemPrompt + (si hay resumen)
  "\n\n[Resumen de la conversación previa]\n" + contextSummary`.
- `buildHistory`: iterar solo `session.messages.drop(session.summarizedUpTo)` (el budget
  de 6.000 chars sigue aplicando encima).

**6. ViewModel** — `onCompactContext()`:
```kotlin
launchChatOperation {
    _uiState.update { it.copy(isCompacting = true) }
    try {
        val summary = repository.summarizeConversation(buildSummarySource(session))
        if (summary.isBlank()) { error = "No se pudo compactar"; return@launchChatOperation }
        val compacted = session.copy(
            contextSummary = summary,
            summarizedUpTo = (session.messages.size - KEEP_RECENT_MESSAGES).coerceAtLeast(0)
        )
        repository.setActiveSession(compacted); repository.saveSession(compacted)
        _uiState.update { it.copy(chatSession = compacted) }
        repository.resetSession()          // recrea ya con resumen + cola reciente
    } finally { _uiState.update { it.copy(isCompacting = false) } }
}
```
UiState nuevo: `contextUsage: Float = 0f`, `isCompacting: Boolean = false`.

**7. UI** — `AiAssistantScreen.kt`: banner fino entre la lista y `PromptInput`, visible
cuando `contextUsage >= 0.7f` y no generando:
```
[⚠ Conversación larga (82 %)]  [Compactar]
```
- `AssistChip` o `TextButton` "Compactar"; mientras `isCompacting`, deshabilitado con
  "Compactando…" (y `CircularProgressIndicator` de 16 dp — aquí sí procede).
- Al terminar: snackbar `ai_compacted` ("Conversación compactada").
- El historial visible NO cambia (todas las burbujas siguen ahí): resolver la
  preocupación de "llenar el historial de chats" es justo esto — mismo chat, misma lista.

Opcional dentro del mismo banner (menú overflow): "Nuevo chat con resumen" →
`startNewSession()` sembrando `contextSummary` en la sesión nueva. Secundario; puede no
implementarse en la primera pasada.

Opcional fase 6.5: **auto-compactar** al cruzar 90 % antes de enviar (sustituyendo el
reintento silencioso de `HISTORY_CHAR_BUDGET_RETRY` por un resumen + snackbar
informativo). Dejarlo para cuando el flujo manual esté probado.

### Strings (×3 idiomas)
- `ai_context_long` = "Conversación larga (%1$d%%)"
- `ai_compact` = "Compactar", `ai_compacting` = "Compactando…",
  `ai_compacted` = "Conversación compactada"
- `ai_compact_failed` = "No se pudo compactar la conversación"

### Pruebas
- Unit: `EstimateContextUsageUseCaseTest` (vacía=0, llena≈1, con `summarizedUpTo` no
  cuenta lo resumido); `AiChatSessionTest` (copy con campos nuevos);
  ViewModel: compactar → sesión con `contextSummary`, `summarizedUpTo` correcto,
  `resetSession` llamado; resumen en blanco → error y sesión intacta.
- Migración: instalar build vieja con chats, actualizar, abrir historial (no crash, chats
  intactos).
- Manual: conversación larga real → banner al 70 % → compactar → seguir conversando y
  comprobar que recuerda lo resumido ("¿qué te pedí al principio?").

---

## Decisiones ya tomadas (para no re-discutir al implementar)

1. **Chip rápido (F4)**: se añade burbuja "¡Voy!" sin modelo y la tarjeta cuelga de ella.
   El usuario pidió explícitamente "debería contestar *ahora voy* y crear la cajita".
2. **Compactar (F6) = mismo chat**, historial visible intacto; "nuevo chat con resumen"
   es secundario/opcional (al usuario le preocupaba llenar el historial).
3. **Scroll (F3)**: Opción A (autoFollow + ancla + FAB). La Opción B (anclar mensaje
   arriba, patrón gallery/ChatGPT) solo si A no convence en uso real.
4. **Selección (F1)**: quedarse en jeziellago `isTextSelectable = true`; migrar a
   compose-richtext (lo que usa gallery) queda fuera de alcance.
5. Umbrales F6: aviso 70 %, `KEEP_RECENT_MESSAGES = 4`, reserva de respuesta 1.000
   tokens, ~3,5 chars/token. Ajustables tras probar en dispositivo.
