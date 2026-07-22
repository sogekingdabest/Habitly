# Plan de mejora del Asistente de IA — Salida estructurada fiable + rendimiento

> Estado: propuesta (2026-07-21). Objetivo: arreglar la generación de rutinas del chat y
> acelerar la inferencia, con un plan por fases que entrega valor pronto y mete el riesgo
> de forma escalonada.

## 1. Problema que resolvemos

Dos síntomas observados en el chat, sobre todo con rutinas:

1. **"Se queda alguna rutina sin poner".** El listado que crea la tarjeta suele estar
   incompleto (normalmente falta la última).
2. **"Hace sugerencias cuando no tiene sentido".** Aparece la tarjeta de crear rutinas
   aunque el usuario solo estuviera preguntando por las que ya tiene.

### Diagnóstico (causas raíz)

- **Problema 1 = estructural.** Hoy el modelo genera cada rutina **dos veces**: en la prosa
  visible y en un bloque JSON oculto `@@RUTINA@@ {...}` al final
  ([GetAiContextUseCase.kt](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/domain/usecase/GetAiContextUseCase.kt) líneas 192-194).
  Con modelos on-device pequeños esto falla por: (a) desincronización prosa↔JSON, y
  (b) **truncado del JSON final** cuando el modelo se queda sin presupuesto de tokens (la KV
  cache es limitada y el system prompt ya la llena). El `regexFallback` exige llave de cierre
  (`OBJECT_REGEX = \{[^{}]*\}` en [ParseAiRoutinesUseCase.kt:231](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/domain/usecase/ParseAiRoutinesUseCase.kt)),
  así que el último objeto truncado se descarta. Agrava: `temperature = 0.9`
  ([AiAssistantRepositoryImpl.kt:257](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/repository/AiAssistantRepositoryImpl.kt))
  es muy alta para fidelidad de formato.
- **Problema 2 = sobre-disparo.** El modelo emite `@@RUTINA@@` de más (se le pide para un
  abanico amplio de casos), y el parser lo permite: `looksLikeRoutinesBlock` acepta el bloque
  si aparece la palabra suelta "rutinas" (en `ARRAY_KEYS`), que sale en casi cualquier
  conversación de esta app.

### Evidencia externa que respalda el diseño

- **"Let Me Speak Freely?" (EMNLP 2024):** restringir el formato **degrada el razonamiento**;
  el JSON-mode es el que peor rinde. Su mitigación recomendada es **"NL-to-Format"**: responder
  en lenguaje natural primero y **convertir a formato en un segundo paso**.
- **Field report Gemma 4 E2B + LiteRT-LM en Android (issue #2202):** la fiabilidad de las
  tool-calls cae con el nº de argumentos (0-1 args ≈ 87-100%, 2 args ≈ 78-88%, 3 args ≈ 43-67%,
  **4 args = 0%**), por "quedarse sin presupuesto a mitad del JSON". Confirma el truncado. También:
  corrupción numérica en GPU (~6-9%), y degradación al reconstruir la conversación desde
  `initialMessages`.
- **Google AI Edge Gallery** (app de referencia, misma librería `com.google.ai.edge.litertlm`,
  en **0.11.0**): usa `ExperimentalFlags.enableSpeculativeDecoding` (MTP) y
  `ExperimentalFlags.enableConversationConstrainedDecoding`. El constrained decoding en Kotlin
  **existe pero va acoplado a function-calling (`tools`), no a un esquema JSON libre**.

## 2. Estrategia

Separar dos responsabilidades que hoy van mezcladas en una sola generación:

- **Turno 1 — conversación con chispa.** Prosa libre, temperatura alta (~0.85), sin JSON ni
  tools. Es lo que lee el usuario. Preserva razonamiento y estilo.
- **Turno 2 — extracción estructurada.** Llamada **aislada, single-turn, a baja temperatura
  (~0.15)** que toma el texto del turno 1 y produce SOLO la estructura de rutinas. No se muestra
  ni se persiste como mensaje.

El turno 2 se modela como un **"extractor" intercambiable** detrás de una interfaz:

- `JsonExtractor` (Fase 1): pide un JSON mínimo `[{title, frequency}]` y lo pasa por el parser
  actual. **Funciona sobre la versión actual (0.10.2), sin bump.**
- `ToolExtractor` (Fase 3): usa function-calling (`addRoutine(title, frequency)`) con
  `enableConversationConstrainedDecoding = true`. Estructura **garantizada por el motor**.
  Requiere subir de versión.

Además, una **puerta de intención**: el turno 2 solo se dispara cuando el usuario realmente
pide **crear** rutinas (no cuando consulta las existentes). Esto ahorra una inferencia en la
mayoría de mensajes **y** mata las sugerencias espurias (Problema 2).

> Nota de diseño: el turno 2 es un `Conversation` efímero sobre el `Engine` ya cargado
> (singleton), con el texto del turno 1 como entrada. Al ser single-turn, evita el problema de
> reconstruir historia desde `initialMessages` que señala el field report.

## 3. Fases

Leyenda esfuerzo: **S** = pequeño, **M** = medio, **L** = grande.

---

### Fase 0 — Spikes de de-risking (ramas desechables, medir en el Galaxy S25+)

No toca producción. Convierte incertidumbres en datos antes de comprometer el diseño.

| Spike | Qué se prueba | Salida / criterio |
|------|----------------|-------------------|
| **A. Bump** | Subir `litertlmAndroid` a `0.14.0` en [gradle/libs.versions.toml](gradle/libs.versions.toml), compilar, smoke test del chat actual (CPU). | ¿Compila y responde igual? Anotar breaking changes de API (`EngineConfig`, `ConversationConfig`, `sendMessageAsync`). |
| **B. GPU + MTP** | `Backend.GPU()` + `@OptIn(ExperimentalApi::class) ExperimentalFlags.enableSpeculativeDecoding = true`. | Tabla tokens/s GPU vs CPU **y** verificación de integridad numérica (generar cantidades/intervalos y comprobar que no hay corrupción tipo `2026`→`202026`). Veredicto GPU sí/no en el S25+. |
| **C. Tool-calling constrained** | `RoutineProposalTools` mínima (`addRoutine(title, frequency)`) + `enableConversationConstrainedDecoding = true`, extractor single-turn sobre un texto de prueba con 6 rutinas. | ¿Llama a la tool 1 vez por rutina? ¿acierta title/frequency? Fiabilidad 2 vs 3 args. Veredicto ToolExtractor viable sí/no. |

**Dependencias:** Fase 2 depende de A+B; Fase 3 depende de C (y de Fase 2). Fase 1 no depende de ningún spike.

**Esfuerzo:** M.

---

### Fase 1 — Dos turnos (NL-to-Format) + saneado del prompt/parser

**Sobre la versión actual (0.10.2). Riesgo bajo. Entrega el grueso del arreglo ya.**

Cambios:

1. **Prompt del turno 1 (prosa pura).** En `GetAiContextUseCase.getBasePersonality()`, quitar
   las instrucciones de `@@RUTINA@@` (y, cuando generalicemos, `@@LISTA@@`). El turno 1 deja de
   producir bloques estructurados.
2. **Recorte de contexto.** Bajar los caps de `GetAiContextUseCase` (p. ej. `MAX_ROUTINES`
   15→8; revisar despensa/compra 30) para liberar KV cache y acelerar prefill. Mantener el
   orden "lo que toca hoy primero" que ya existe.
3. **Extractor aislado en el repositorio.** Nuevo método, p. ej.
   `suspend fun extractRoutines(sourceText: String): List<AiRoutineSuggestion>`, que crea un
   `Conversation` efímero sobre el `Engine` cargado con `SamplerConfig(temperature≈0.15, ...)`,
   `systemInstruction` de extractor, envía `sourceText` y devuelve el resultado (sin persistir
   ni mostrar). Cierra el conversation al terminar.
4. **Puerta de intención.** Nueva `RoutineCreationIntentUseCase` (o señal en el envío) que
   decide si procede extraer. Verde si: origen de un quick-prompt de creación
   (`CLEANING_PLAN`, `ROUTINE_IDEAS` en [GetContextualQuickPromptsUseCase.kt](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/domain/usecase/GetContextualQuickPromptsUseCase.kt)),
   o keywords de creación en el mensaje del usuario ("crea", "propón/proponme", "plan de
   limpieza", "hábitos"...). Conservador: ante duda, no extraer. **Ataca el Problema 2.**
5. **Orquestación en el ViewModel.** En `AiAssistantViewModel.onSendMessage`, tras completar el
   turno 1, si la puerta da verde: mostrar `SuggestionPreparingCard`, lanzar el extractor con
   prompt tipo *"Del texto de abajo, devuelve SOLO un array JSON
   `[{\"title\":\"...\",\"frequency\":\"diaria|semanal|cada_n_dias\"}]` con las rutinas
   propuestas. Nada más."*, parsear con `ParseAiRoutinesUseCase`, guardar en
   `routineSuggestions`.
6. **Endurecer el parser** (para el turno 2 y por defensa):
   - `looksLikeRoutinesBlock`: exigir marcador **o** clave en forma JSON (`"routines"`/`"rutinas"`
     seguida de `:` y `[`), no la palabra suelta.
   - **Recuperar objeto truncado:** en `regexFallback`, además de `OBJECT_REGEX` (con cierre),
     capturar un `{...` final sin cerrar y extraerle `title`/`frequency` con los regex de campo.
7. **Alinear caps:** dejar el mismo número en el prompt de extracción y en
   `ParseAiRoutinesUseCase.MAX_ROUTINES`.

**Criterios de aceptación:**
- El turno 1 nunca muestra JSON ni marcadores.
- Con un plan de 6 rutinas, la tarjeta contiene las 6 (varias corridas en dispositivo).
- "¿Qué rutinas tengo hoy?" (consulta) **no** muestra tarjeta de creación.

**Riesgo/rollback:** todo es lógica de app. Feature flag para desactivar la puerta de intención
y volver al comportamiento actual si algo empeora.

**Esfuerzo:** M-L.

---

### Fase 2 — Bump a 0.14.0 + GPU/MTP (rendimiento)

**Depende de Spikes A y B.** Acelera todo el chat y abarata el turno 2.

Cambios:

1. `gradle/libs.versions.toml`: `litertlmAndroid = "0.14.0"`.
2. `AiAssistantRepositoryImpl.loadModel`:
   - `Backend.GPU()` con **fallback try/catch a `Backend.CPU()`** y log de cuál arrancó
     (patrón Gallery: la GPU puede caer a CPU en silencio).
   - `EngineConfig(..., maxNumTokens = <valor>)` explícito (hoy se deja por defecto).
   - `@OptIn(ExperimentalApi::class) ExperimentalFlags.enableSpeculativeDecoding = <soportado>`
     **antes** de `engine.initialize()`.
3. **Enriquecer `AiModelConfig`** (patrón Gallery `DefaultConfig`/capabilities): añadir
   `supportsSpeculativeDecoding`, `defaultTemperature`, `maxTokens`, backends soportados; usarlos
   en la config en vez de constantes hardcodeadas.

**Criterios:** decode notablemente más rápido en el S25+; sin corrupción numérica (Spike B); el
chat sigue coherente.

**Riesgo/rollback:** si la GPU da problemas en el S25+, quedarse en CPU (MTP aporta menos en CPU;
el resto del plan no depende de GPU). Rollback = revertir versión y backend.

**Esfuerzo:** M.

---

### Fase 3 — Turno 2 vía function-calling con constrained decoding

**Depende de Spike C + Fase 2.** Sustituye el `JsonExtractor` por uno con estructura garantizada
por el motor.

Cambios:

1. **`RoutineProposalTools : ToolSet`** con `@Tool addRoutine(@ToolParam title, @ToolParam
   frequency)` que dispara un callback `onRoutineProposed` (ver anexo). Solo Strings → esquiva la
   corrupción numérica.
2. **`ToolExtractor` en el repositorio:** crea el `Conversation` con
   `tools = listOf(tool(routineTools))`, `ExperimentalFlags.enableConversationConstrainedDecoding
   = true`, `automaticToolCalling = true` (default; el handler recoge por efecto lateral).
   `systemInstruction` de extractor. Envía el texto del turno 1; recoge las llamadas; construye
   `AiRoutineSuggestion` con la lógica de frecuencia/días ya existente en
   `ParseAiRoutinesUseCase` (extraer esa lógica a un builder reutilizable).
3. **Interfaz `RoutineExtractor`** con dos implementaciones (`JsonExtractor` de Fase 1,
   `ToolExtractor` de Fase 3), elegidas por flag/capacidad del modelo. **Fallback a
   `JsonExtractor`** si el modelo no soporta tools con fiabilidad.

**Criterios:** la tarjeta refleja exactamente lo que el modelo llamó; cero fallos de parseo;
fiabilidad ≥ Fase 1 en el S25+.

**Riesgo/rollback:** API experimental (`@OptIn`); si falla, el `ToolExtractor` cae al
`JsonExtractor` (misma interfaz).

**Esfuerzo:** M.

---

### Fase 4 — Generalizar a la lista de la compra + pulido

- Aplicar el mismo patrón (dos turnos / tool) al bloque `@@LISTA@@` (mismo problema, misma
  solución): p. ej. `addShoppingItem(name, quantityUnit)` o JSON mínimo.
- Telemetría local: contar cuántas veces el extractor devuelve vacío/parcial, para seguir
  midiendo la fiabilidad real.
- Warmup opcional tras cargar el modelo (patrón field report) si el primer mensaje va lento.

**Esfuerzo:** S-M.

## 4. Riesgos globales y mitigación

| Riesgo | Mitigación |
|--------|------------|
| Bump de librería nativa rompe algo | Spike A antes de comprometer; Fase 1 no depende del bump. |
| GPU inestable en el S25+ | Spike B; fallback a CPU. |
| Tool-calling poco fiable en E2B | Spike C; `ToolExtractor` cae a `JsonExtractor`. Tools de ≤2 args, solo strings. |
| Latencia del 2º turno | MTP (Fase 2) + puerta de intención (no corre siempre) + prompt mínimo. |
| API experimental de constrained decoding | Aislada tras la interfaz `RoutineExtractor`. |

## 5. Métricas de éxito

- **Completitud:** % de planes donde la tarjeta contiene todas las rutinas descritas (objetivo >90%).
- **Precisión de disparo:** % de mensajes de consulta que **no** muestran tarjeta espuria (objetivo ~100%).
- **Velocidad:** tokens/s con MTP+GPU vs baseline CPU.

## 6. Anexos (plantillas)

### 6.1 `RoutineProposalTools` (Fase 3) — calcado del patrón del Gallery

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** El modelo llama a addRoutine una vez por rutina; nosotros solo recogemos (no creamos aún). */
class RoutineProposalTools(
    private val onRoutineProposed: (title: String, frequency: String) -> Unit
) : ToolSet {
    @Tool(description = "Registra una rutina propuesta para que el usuario la revise y confirme.")
    fun addRoutine(
        @ToolParam(description = "Título corto que empieza por verbo, p. ej. 'Fregar la cocina'.")
        title: String,
        @ToolParam(description = "Frecuencia: 'diaria', 'semanal' o 'cada_n_dias'.")
        frequency: String,
    ): Map<String, String> {
        onRoutineProposed(title, frequency)
        return mapOf("result" to "ok")
    }
}
```

Registro y config del turno 2:

```kotlin
val proposals = mutableListOf<Pair<String, String>>()
val tools = listOf(tool(RoutineProposalTools { t, f -> proposals += t to f }))

val config = ConversationConfig(
    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.15),
    systemInstruction = Contents.of(
        "Eres un extractor. Del texto del usuario, registra cada rutina propuesta " +
        "llamando a addRoutine(title, frequency). No escribas nada más."
    ),
    tools = tools,
)
// Requiere, antes de crear el Engine/Conversation:
// @OptIn(ExperimentalApi::class) ExperimentalFlags.enableConversationConstrainedDecoding = true
```

### 6.2 `EngineConfig` con GPU + MTP (Fase 2)

```kotlin
@OptIn(ExperimentalApi::class)
ExperimentalFlags.enableSpeculativeDecoding = config.supportsSpeculativeDecoding

val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.GPU(),            // con fallback try/catch a Backend.CPU()
    maxNumTokens = config.maxTokens,
    cacheDir = context.cacheDir.path,
)
```

## 7. Referencias

- LiteRT-LM (Kotlin/Android): https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- Google AI Edge Gallery — `LlmChatModelHelper.kt`, `MobileActionsTools.kt`, `ToolsProvider.kt`
- Field report Gemma 4 E2B + LiteRT-LM (issue #2202): https://github.com/google-ai-edge/LiteRT-LM/issues/2202
- "Let Me Speak Freely?" (EMNLP 2024): https://arxiv.org/abs/2408.02442
- Speed-up Gemma 4 con MTP: https://ai.google.dev/gemma/docs/mtp/overview
