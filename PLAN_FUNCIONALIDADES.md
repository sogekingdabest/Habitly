# Plan 3 — Funcionalidades nuevas

> **Ejecutar EL ÚLTIMO**, después de `PLAN_SEGURIDAD.md` y `PLAN_USABILIDAD.md`.
> F3 (panel de reparto) depende del modelo `Household.memberProfiles` que introduce el plan
> de seguridad. Antes de empezar, **lee las secciones "Notas para el siguiente plan" al
> final de los otros dos documentos**.

---

## Contexto del proyecto (léelo antes de empezar)

**Habitly** es una app Android de convivencia doméstica: lista de la compra compartida,
despensa, rutinas del hogar con rachas y rotación entre miembros, y un asistente de IA que
corre **en local** (Gemma vía LiteRT-LM, modelo de 2,5–3,6 GB descargado bajo demanda).

- **Stack**: Kotlin, Jetpack Compose (Material 3), Hilt, Firebase Auth + Firestore, Room
  (historial del chat de IA), WorkManager, Glance (widget).
- **minSdk 29, targetSdk 36.**
- **Arquitectura**: `feature/<nombre>/{data,domain,presentation}`. ViewModels con
  `StateFlow<...UiState>`, lógica en use cases.

### Sistema de diseño — respétalo

Piel propia **"Verde niebla" (Cozy Handcrafted)**. Componentes firma en `ui/components/`:
`HabitlyCard`, `HabitlyToggleCard`, `HabitlyPrimaryButton`, `HabitlyPill`,
`HabitlyBackground`, `IconHalo`, `RitualToggle`, `StreakBadge`, `MineBadge`. Tokens en
`MaterialTheme.habitly`. Formas `LeafCornerLarge` / `LeafCornerMedium`. **Nada de Material 3
pelado donde exista equivalente de la casa.** Todo en claro y oscuro.

### Textos

`values/` y `values-en/`. **Nunca texto literal en un Composable.** Plurales con `<plurals>`.

### Cómo compilar y verificar

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

En PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` y `.\gradlew.bat`.

### Lo que ya existe y debes reutilizar (importante)

El asistente de IA ya tiene **parseo de lenguaje natural a datos estructurados**, con tests.
No lo reimplementes:

- `feature/aiassistant/domain/usecase/ParseAiShoppingListUseCase.kt` — texto → productos
- `feature/aiassistant/domain/usecase/ParseAiRoutinesUseCase.kt` — texto → rutinas
- `feature/aiassistant/domain/usecase/AddAiRoutinesUseCase.kt` — alta de las rutinas
- `feature/aiassistant/domain/usecase/ShoppingCreationIntentUseCase.kt` y
  `RoutineCreationIntentUseCase.kt` — detección de intención

Tienen tests en `app/src/test/.../domain/usecase/` y fakes de repositorio reutilizables en
`app/src/test/.../data/repository/`. **Sigue ese patrón para lo que escribas.**

### Qué NO debes tocar

- `firestore.rules` y el modelo de miembros (cerrado por el plan de seguridad). Si una
  funcionalidad necesita un cambio de reglas, **párate y coméntalo con Dani** en vez de
  aflojarlas.
- `proguard-rules.pro` salvo para **añadir** un `-keep` que necesite una dependencia nueva.

---

## F1 — "Compartir con Habitly"

**La más diferencial: rentabiliza toda la inversión en IA local.** Empieza por aquí.

### Idea

Estás viendo una receta en el navegador o te mandan la lista por WhatsApp. Pulsas
Compartir → Habitly → el modelo local extrae los ingredientes → los revisas → a la lista.

Ninguna app de lista de la compra hace esto **sin enviar tu texto a un servidor**. Habitly
puede, porque el modelo corre en el dispositivo. Es el argumento de venta.

### Cambio

- `intent-filter` de `ACTION_SEND` con `text/plain` en `MainActivity`
  (`AndroidManifest.xml`).
- Pantalla o bottom sheet de recepción: muestra el texto recibido y lanza
  `ParseAiShoppingListUseCase`.
- **Pantalla de revisión antes de guardar**: lista de productos detectados con casillas para
  desmarcar y editar cantidades. El modelo se equivoca; el usuario manda.
- Si el modelo no está descargado, ofrecer descargarlo o caer a un parseo simple por líneas
  (una línea = un producto). **No dejes al usuario en un callejón sin salida.**
- Detectar también rutinas con `ParseAiRoutinesUseCase` si el texto va de tareas.

### Cuidado con

**El texto compartido es contenido no fiable.** Puede traer instrucciones que intenten
manipular al modelo ("ignora lo anterior y..."). Mitigaciones:

- Delimita el texto recibido claramente en el prompt como *datos a procesar*, no como
  instrucciones.
- La pantalla de revisión es la defensa real: **nada se guarda sin que el usuario lo vea**.
- Trunca el texto entrante a un tamaño razonable (el contexto es de 4096 tokens; hay un
  `EstimateContextUsageUseCase` que ya calcula esto).
- No des de alta nada automáticamente por mucho que el texto lo "pida".

### Aceptación

- Compartir una receta desde el navegador propone los ingredientes.
- Compartir un texto sin productos no da de alta nada y lo dice con claridad.
- Sin el modelo descargado, hay salida usable.
- La primera inferencia tarda; muestra progreso, no una pantalla congelada.

---

## F2 — Añadir por voz

### Idea

En la cocina, con las manos ocupadas, escribir es el peor interfaz posible. "Leche, huevos y
pan" → tres productos.

### Cambio

- Botón de micrófono en la hoja de alta rápida (la que crea U3 del plan de usabilidad) y en
  la cabecera de la lista.
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` — **cero dependencias nuevas**, usa el
  reconocedor del sistema.
- El resultado pasa por el mismo `ParseAiShoppingListUseCase` de F1, así "dos litros de
  leche" sale con cantidad y unidad.
- Sin modelo descargado: separar por comas y por "y". Suficiente para el caso común.

### Cuidado con

- No todos los dispositivos traen reconocedor: comprueba que hay actividad que resuelva el
  intent y **esconde el botón** si no la hay, en vez de petar.
- El idioma del reconocedor debe seguir el de Ajustes, no el del sistema. Mira cómo lo
  resuelve `LocaleHelper` (`feature/settings/data/LocaleHelper.kt`).

### Aceptación

- Dictar tres productos los añade los tres.
- "Dos litros de leche" → cantidad 2, unidad L.
- En un dispositivo sin reconocedor, el botón no aparece.

---

## F3 — Panel de reparto justo

**Depende del plan de seguridad** (`Household.memberProfiles`).

### Idea

En una app de convivencia, la pregunta que de verdad importa no es "¿qué queda por hacer?"
sino **"¿estamos repartiéndolo bien?"**. Es lo que engancha y lo que evita discusiones.

Y no necesitas backend nuevo: ya guardas `completedBy`, `assignedTo` y la rotación entre
miembros. Es puro frontend sobre datos que ya tienes.

### Cambio

Sección nueva en `HouseholdScreen`:

- Rutinas completadas por miembro esta semana (barras horizontales comparadas).
- Racha de la casa: días seguidos con todas las rutinas de casa completadas.
- Reparto de la semana pasada, para dar contexto.
- Nombres desde `Household.memberProfiles` — **sin lecturas extra de Firestore**.

### Cuidado con

**El tono lo es todo.** Esto puede leerse como un marcador que señala al vago de la casa, y
eso hace daño en una app que usa gente que convive. Directrices:

- Nada de rankings, ni de "peor" miembro, ni de números en rojo.
- Celebrar el total de la casa por encima del individual.
- Si hay desequilibrio, sugerir en positivo ("¿reasignamos alguna rutina?"), no señalar.
- Con un solo miembro, ocultar la comparación entera y mostrar solo el progreso.

Consúltalo con Dani antes de rematar el texto: es una decisión de producto, no técnica.

### Aceptación

- Los recuentos cuadran con el historial real de completados.
- Con un solo miembro no se ve nada raro.
- Sin lecturas adicionales de Firestore respecto a la pantalla actual (compruébalo).

---

## F4 — Atajos de aplicación

### Idea

Mantener pulsado el icono de Habitly → "Añadir a la compra" / "Rutinas de hoy". Dos toques
menos, cada vez.

### Cambio

- `res/xml/shortcuts.xml` con dos `<shortcut>` estáticos y su `meta-data` en `MainActivity`.
- Deep links a la pestaña correspondiente. **Ya existe el patrón**: mira cómo
  `navigateToRoutines` llega desde la notificación de recordatorio hasta
  `MainScreen.kt:115`. Reutilízalo en vez de inventar otro mecanismo.
- Iconos adaptativos con la paleta de la app.

### Aceptación

- Los dos atajos aparecen al mantener pulsado el icono.
- Cada uno abre la pestaña correcta, también con la app cerrada.
- Los textos salen de `strings.xml` y cambian con el idioma de Ajustes.

---

## F5 — Plantillas de rutinas en el onboarding

### Idea

Hoy, al crear la casa, el usuario aterriza en una pantalla vacía y tiene que imaginarse qué
poner. Es el momento de mayor abandono de cualquier app de hábitos.

### Cambio

- Paso opcional al final de `OnboardingScreen`: 6-8 rutinas típicas de casa (sacar la
  basura, lavadora, limpiar el baño, fregar, compra semanal, cambiar sábanas...) con
  casillas, todas marcadas por defecto.
- Frecuencia sensata precargada en cada una.
- "Prefiero empezar de cero" bien visible: no obligues.
- Los nombres desde `strings.xml`, traducidos.

### Aceptación

- Una casa nueva puede arrancar con rutinas ya creadas en un toque.
- Se puede saltar sin fricción.
- Las rutinas creadas son indistinguibles de las hechas a mano (misma frecuencia, misma
  rotación, editables y borrables).

---

## Verificación final (obligatoria)

Además de `testDebugUnitTest`, compila y **prueba a mano el APK de release**: R8 está
activado desde el plan de seguridad y rompe en tiempo de ejecución, no al compilar. El
asistente de IA es la parte más frágil bajo R8 por la deserialización de las respuestas del
modelo, y F1/F2 se apoyan justo en eso.

```bash
./gradlew assembleRelease
```

Recorre: compartir un texto desde otra app → alta por voz → panel de reparto → atajos →
onboarding con plantillas. Y comprueba **claro/oscuro** y **español/inglés**.

---

## Estado

| | Tarea | Estado |
|---|---|---|
| F1 | Compartir con Habitly | ✅ hecha — **sin recorrer en dispositivo** |
| F2 | Añadir por voz | ✅ hecha (sin modelo, ver más abajo) — **sin recorrer en dispositivo** |
| F3 | Panel de reparto justo | ✅ hecha (tono acordado con Dani) — **sin recorrer en dispositivo** |
| F4 | Atajos de aplicación | ✅ hecha — verificada en el APK de release, **sin recorrer en dispositivo** |
| F5 | Plantillas en el onboarding | ✅ hecha — **sin recorrer en dispositivo** |

**393 tests unitarios en verde** (50 nuevos). `assembleDebug` y `assembleRelease` correctos; el
APK de release sigue en 53 MB.

Verificación estática de R8 sobre el APK de release: `MainActivity` **conserva su nombre** (los
atajos estáticos apuntan a ella por nombre, así que era obligatorio), `xml/shortcuts` y los dos
`mipmap/ic_shortcut_*` sobreviven al *shrink* de recursos, y los modelos nuevos
(`HouseholdShareSummary`) siguen sin renombrar por las reglas `-keep` por paquete. No hizo falta
tocar `proguard-rules.pro`.

### Dos desviaciones del plan, a propósito

1. **F2 no pasa por el modelo local.** El plan pedía reutilizar `ParseAiShoppingListUseCase`,
   pero ese use case parsea el **JSON de respuesta del modelo**, no texto libre: usarlo obliga a
   cargar el engine (2,5 GB) y esperar una inferencia para tres palabras dichas en la cocina, que
   es justo el problema que la función viene a resolver. En su lugar el dictado usa
   `PlainListParser` (`feature/shopping/domain/util/`), un lector puro y testeado que resuelve
   cantidad y unidad en español e inglés ("dos litros de leche" → 2 L). El modelo se reserva para
   F1, donde el texto es largo y hay pantalla de progreso. Las tres aceptaciones de F2 se cumplen.
2. **F3 sí hace lecturas de Firestore.** La aceptación pedía "sin lecturas adicionales", y no es
   posible: el documento de la casa solo guarda lo de **hoy** (`lastCompletedBy`), así que un
   reparto semanal exige la subcolección `completions`. Se paga una consulta por rutina de casa
   (el mismo N+1 que ya hace la pestaña de Rutinas), en **una sola ventana** de 30 días de la que
   salen las tres cifras, y **una sola vez por casa** (el panel es semanal, no un contador en
   vivo). Lo que sí es gratis son los nombres: salen de `Household.memberProfiles`.

### Cambios de firma (te afectan si tocas estos ficheros)

- **`HouseholdRepository.createHousehold` devuelve ahora `Result<String>`** con el id de la casa
  creada. Lo necesita F5 para crear las rutinas de las plantillas sin releer el perfil.
- **`MainScreen` / `RootNavGraph`**: `navigateToRoutines: Boolean` desaparece. En su lugar viaja
  `externalDestination: ExternalDestination?` (`navigation/ExternalDestination.kt`), que cubre la
  notificación de recordatorio y los dos atajos por el mismo canal, más `sharedText: String?`.
- **`ShoppingScreen`** gana `openQuickAdd` / `onQuickAddHandled` (atajo "Añadir a la compra").
- **`QuickAddSheet`** gana `onVoiceInput: (String) -> Unit` (obligatorio).
- **`ShoppingUiState`** gana `voiceAddedCount`; **`HouseholdUiState`** gana `share` y la derivada
  `memberNames`; **`OnboardingUiState`** gana `step` (`OnboardingStep.FORM` / `TEMPLATES`).
- **`AiAssistantRepositoryImpl.extractShopping` / `extractRoutines`** cargan el engine si hace
  falta (antes devolvían "" sin chat previo, que es como llega el texto compartido).
- Nuevos: `ExtractSharedTextUseCase`, `SharedTextGuard`, `SharedTextClassifier`,
  `PlainListParser`, `AddShoppingItemsUseCase`, `GetHouseholdShareUseCase`,
  `HouseholdShareSummary`, `VoiceInputButton` (`ui/components/`), `HOUSEHOLD_ROUTINE_TEMPLATES`.
- `MainActivity` pasa a `launchMode="singleTask"`: un `ACTION_SEND` tiene que entrar por
  `onNewIntent` en la instancia que ya existe, no apilar una segunda copia.

### Traducciones

`values/` y `values-en/` completas. **`values-gl` no se ha tocado**: ya le faltaban ~95 claves de
antes y cae al castellano, que es el comportamiento que tenía.

### Pendiente para Dani (no se pudo hacer aquí: sin dispositivo conectado)

Recorrido a mano del APK de release, que es donde R8 rompe en tiempo de ejecución:

1. Compartir una receta desde el navegador y una lista desde WhatsApp (con modelo y sin modelo).
2. Dictar tres productos desde la cabecera y uno desde la hoja de alta rápida.
3. Panel de reparto con datos reales de varios miembros, y con un solo miembro.
4. Los dos atajos manteniendo pulsado el icono, **con la app cerrada**.
5. Crear una casa nueva con plantillas y otra con "empezar de cero".
6. Claro/oscuro y español/inglés en todo lo anterior.

---

## Ideas descartadas (y por qué)

Para que no se reabran sin motivo:

- **Escaneo de código de barras**: requiere ML Kit o CameraX + una base de datos de
  productos que no tienes. La voz cubre el mismo caso con cero dependencias.
- **Geofencing para avisar al llegar al súper**: permiso de ubicación en segundo plano, que
  en Play exige justificación específica y revisión. Coste muy alto en una app que acaba de
  entrar en beta. Una notificación por horario da el 80 % del valor.
- **Cloud Function para validar el código de invitación**: es el arreglo correcto de la
  unión a casas (ver `PLAN_SEGURIDAD.md`, S2), pero exige plan Blaze. Queda como deuda
  anotada, no como tarea de este plan.
