# Plan 2 — Usabilidad

> **Ejecutar DESPUÉS de `PLAN_SEGURIDAD.md`.** Ese plan cambia cómo se resuelven los
> nombres de los miembros (`Household.memberProfiles`), y el rediseño del dashboard (U5)
> los consume. Antes de empezar, **lee la sección "Notas para los siguientes planes" al
> final de `PLAN_SEGURIDAD.md`**: recoge las firmas que cambiaron.

---

## Contexto del proyecto (léelo antes de empezar)

**Habitly** es una app Android de convivencia doméstica: lista de la compra compartida,
despensa, rutinas del hogar con rachas y rotación entre miembros, y un asistente de IA que
corre **en local** (Gemma vía LiteRT-LM).

- **Stack**: Kotlin, Jetpack Compose (Material 3), Hilt, Firebase Auth + Firestore, Room
  (solo el historial del chat de IA), WorkManager, Glance (widget de inicio).
- **minSdk 29, targetSdk 36.**
- **Arquitectura**: `feature/<nombre>/{data,domain,presentation}`. Los ViewModels exponen un
  `StateFlow` de un `data class ...UiState`; la lógica vive en use cases.
- **Estado**: en preparación de beta cerrada en Google Play.

### Sistema de diseño — respétalo

La app tiene una piel propia llamada **"Verde niebla" (Cozy Handcrafted)**. **No uses
componentes de Material 3 pelados donde exista el equivalente de la casa.** Los
componentes firma están en `ui/components/`:

`HabitlyCard`, `HabitlyToggleCard`, `HabitlyPrimaryButton`, `HabitlyPill`, `HabitlyBackground`
(con `MeshArrangement`), `IconHalo`, `RitualToggle`, `StreakBadge`, `MineBadge`.

Los tokens de color están en `MaterialTheme.habitly` (`ui/theme/`): `accentText`,
`textSecondary`, `card`, `border`, `navIdle`. Las formas de esquina-hoja son
`LeafCornerLarge` / `LeafCornerMedium`. Hay espaciados en `ui/theme/Spacing.kt`.

**Todo debe funcionar en modo claro y oscuro** (hay `values-night/`).

### Textos

Los `strings.xml` están traducidos (`values/` y `values-en/`). **Nunca metas texto literal
en un Composable**; añade la clave en ambos ficheros. Para cantidades usa
`pluralStringResource` con `<plurals>`, no concatenación.

### Cómo compilar y verificar

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

En PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` y `.\gradlew.bat`.

Los 23 tests de `app/src/test/` deben seguir pasando.

### Qué NO debes tocar en este plan

- `firestore.rules` y el modelo de miembros de la casa (lo cerró el plan de seguridad)
- `app/build.gradle.kts` / `proguard-rules.pro` — **si añades una dependencia**, comprueba
  que no necesite reglas `-keep` y anótalo al final de este documento
- `feature/aiassistant/**` (lo toca el plan de funcionalidades)

---

# Bloque A — "Usarla rápido"

## U1 — Widget interactivo

**Empieza por aquí: es la mejora con mayor impacto por línea de código de todo el plan.**

### Problema

[`HabitlyWidget.kt:93`](app/src/main/java/com/monsteraltech/habitly/feature/widget/HabitlyWidget.kt) —
el widget entero es un único `.clickable(actionStartActivity(...))`. Es un cartel: enseña la
lista pero no deja tachar nada. Para marcar la leche hay que abrir la app, ir a la pestaña
de compra y buscarla.

### Cambio

**a) El snapshot necesita identificadores.**
`WidgetSnapshot` (`feature/widget/domain/WidgetSnapshot.kt`) guarda hoy
`pendingItems: List<String>` — solo nombres. Para poder tachar hacen falta los ids:

```kotlin
data class WidgetLine(val id: String, val label: String)

data class WidgetSnapshot(
    val state: WidgetState = WidgetState.NO_SESSION,
    val pendingItems: List<WidgetLine> = emptyList(),
    val pendingRoutines: List<WidgetLine> = emptyList()
)
```

Ajusta `BuildWidgetSnapshotUseCase`, que hoy hace `.map { it.name }` y `.map { it.title }`.

**b) Casilla por línea.** En `ItemRow`, sustituye la viñeta `•` por un `CheckBox` de Glance
(`androidx.glance.appwidget.CheckBox`) con
`actionRunCallback<ToggleShoppingItemAction>(actionParametersOf(itemIdKey to line.id))`.

**c) El `ActionCallback`.** Clase nueva en `feature/widget/`:

```kotlin
class ToggleShoppingItemAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Obtén las dependencias con EntryPointAccessors, igual que HabitlyWidget.provideGlance
        // Reutiliza el use case de toggle que ya usa ShoppingViewModel.onToggleItem
        // Al terminar: HabitlyWidget().update(context, glanceId)
    }
}
```

Hazlo con el mismo `EntryPointAccessors.fromApplication(...)` que ya usa
`HabitlyWidget.provideGlance` — Glance no soporta inyección de Hilt directa en los callbacks.

**d) Lo mismo para completar rutinas**, con su propio callback.

**e) Actualizar el widget cuando cambian los datos desde la app.** Si tachas algo en la app,
el widget debe refrescarse. Llama a `HabitlyWidget().updateAll(context)` desde donde se
confirme la escritura (mejor en el repositorio o en un observador, no en el Composable).

### Cuidado con

- El callback corre **fuera de la UI de la app**: si falla, no hay snackbar. Envuelve en
  `runCatching` y, si falla, refresca el widget para que la casilla vuelva a su sitio en
  lugar de quedarse mintiendo.
- Glance tiene un límite de tamaño en el `RemoteViews`. Con `MAX_SHOPPING_LINES = 3` y
  `MAX_ROUTINE_LINES = 3` vas sobrado, no lo subas mucho.
- Mantén `LocaleHelper.wrap(LocalContext.current)`: sin eso el widget ignora el idioma
  elegido en Ajustes.

### Aceptación

- Tachar un producto desde el widget lo marca en la app **sin abrirla**, y el widget se
  actualiza solo.
- Tachar en la app actualiza el widget.
- Sin red: la acción se encola en la caché offline de Firestore y no se pierde.
- El widget sigue viéndose bien en claro y oscuro.

---

## U2 — Gestos y háptica en la lista de la compra

### Problema

Lo comprobé en los 165 ficheros Kotlin del proyecto: **no hay ni un `SwipeToDismiss`, ni un
`combinedClickable`, ni una sola vibración háptica en toda la app.** Todo se maneja con
toques en botones pequeños.

En el súper, con una mano en el carro, **deslizar es el gesto natural**. Hoy cada fila tiene
un `IconButton` de borrar de 48 dp pegado al checkbox
([`ShoppingScreen.kt:578`](app/src/main/java/com/monsteraltech/habitly/feature/shopping/presentation/ShoppingScreen.kt)):
dos dianas que compiten en la misma fila y un borrado destructivo a un toque de distancia
del gesto que más se repite.

### Cambio

En `ShoppingItemRow`:

- Envolver en `SwipeToDismissBox` (`androidx.compose.material3`) con
  `rememberSwipeToDismissBoxState`.
- **Deslizar →**: tachar/destachar. Fondo verde con icono de check.
- **Deslizar ←**: borrar. Fondo rojo con papelera. Reutiliza el snackbar de deshacer que ya
  existe (`uiState.recentlyDeletedName`, `viewModel.onUndoDelete()`).
- **Quitar el `IconButton` de borrar de la fila** una vez el gesto funcione: deja de tener
  sentido y libera espacio horizontal para el nombre del producto.
- Háptica al confirmar el gesto: `LocalHapticFeedback.current.performHapticFeedback(...)`.

Aplica el mismo patrón a la despensa (`presentation/components/PantryContent.kt`) y a las
tarjetas de rutina de `RoutinesScreen.kt`, donde el borrado ya pide confirmación.

### Cuidado con

- **Accesibilidad**: un gesto no puede ser la *única* forma de hacer algo. Añade las
  acciones equivalentes con `Modifier.semantics { customActions = ... }` para que TalkBack
  las ofrezca.
- Las filas van dentro de un `LazyColumn` en un `HabitlyCard` por tienda; comprueba que el
  gesto horizontal no pelea con el scroll vertical.
- El borrado por gesto es irreversible salvo por el snackbar: asegúrate de que el undo
  funciona **antes** de quitar el botón de borrar.

### Aceptación

- Deslizar tacha; deslizar al otro lado borra con opción de deshacer.
- TalkBack anuncia y ofrece ambas acciones sin necesidad de gesto.
- Se nota el pulso háptico al confirmar.

---

## U3 — Alta rápida de producto

### Problema

[`AddProductScreen.kt`](app/src/main/java/com/monsteraltech/habitly/feature/shopping/presentation/add/AddProductScreen.kt)
navega a **pantalla completa** y pide seis campos: nombre, cantidad, unidad, tienda,
categoría y notas. Al guardar, `LaunchedEffect(uiState.success)` hace `onNavigateBack()`.

Añadir cinco cosas = cinco viajes de ida y vuelta con teclado abriéndose y cerrándose. Para
el 90 % de los casos ("pan") solo hace falta el primer campo.

Mientras tanto, lo mejor de la pantalla de la compra —los chips de productos frecuentes,
[`ShoppingScreen.kt:288`](app/src/main/java/com/monsteraltech/habitly/feature/shopping/presentation/ShoppingScreen.kt)—
está enterrado bajo el filtro de tiendas.

### Cambio

**a) Bottom sheet en lugar de pantalla.** Un `ModalBottomSheet` con **un solo campo de
texto** (nombre) enfocado y el teclado abierto de entrada.

**b) "Guardar y seguir".** La acción principal guarda y **deja la hoja abierta**, vacía el
campo y mantiene el foco. Así se apuntan diez cosas seguidas sin salir. El resto de campos,
plegados tras un "Más opciones" (`AnimatedVisibility`) con los valores por defecto actuales.

**c) Subir los chips frecuentes** por encima del filtro de tiendas.

**d) Conservar lo que ya funciona bien**: el aviso `PantryHint` de "ya lo tienes en casa"
(`uiState.pantryMatch`) es una gran idea; que siga apareciendo bajo el campo de nombre.

**e) Mantén la ruta `HiddenRoutes.ShoppingAddProduct`** o límpiala del `NavHost` de
`MainScreen.kt` si el bottom sheet la sustituye por completo. No dejes código muerto.

### Aceptación

- Añadir 5 productos seguidos sin que se cierre el teclado ni la hoja.
- Un producto con solo nombre se guarda con los valores por defecto de hoy.
- El aviso de despensa sigue apareciendo.

---

## U4 — Buscar y filtrar en la lista

### Problema

No existe ningún buscador en la app (lo verifiqué: cero `SearchBar`, cero `searchQuery`).
Con 40 productos repartidos en varias tiendas y la sección de completados plegada, no hay
forma de comprobar si el arroz ya está apuntado. Se duplica.

### Cambio

- Campo de búsqueda en la cabecera de `ShoppingScreen` que filtra sobre `uiState.allItems`
  (pendientes **y** completados: la duplicación pasa justamente porque lo completado está
  escondido).
- El filtrado en el ViewModel, no en el Composable.
- Al escribir en el alta rápida de U3, avisar si ya existe un producto con nombre parecido
  en la lista, igual que ya se hace con la despensa.

### Aceptación

- Buscar "arr" encuentra "Arroz" esté pendiente o completado, en cualquier tienda.
- La búsqueda se limpia con un botón y no se queda pegada al cambiar de pestaña.

---

# Bloque B — "Verla rápido"

## U5 — Rediseño del dashboard

### Problema

[`DashboardScreen.kt:113`](app/src/main/java/com/monsteraltech/habitly/feature/dashboard/presentation/DashboardScreen.kt) —
la primera pantalla, la que más se mira, se gasta en una fecha y un saludo grande. Y la
tarjeta de compra dice *"N productos pendientes"* **sin decir cuáles**, cuando el ViewModel
ya tiene la lista entera en `uiState.pendingShoppingItems` y la tira.

Faltan las tres cosas que hacen que un panel familiar se lea de un vistazo.

### Cambio

**a) Progreso del día.** "3 de 7 hechas" con un anillo o barra. Hoy `DashboardViewModel`
solo calcula `pendingRoutines` (línea 72, con `RoutineSchedule.isPendingOn`); hay que
calcular también **el total programado para hoy** para poder dividir. Añade ambos campos al
`DashboardUiState`.

**b) Los primeros productos, no solo el número.** En `ShoppingSummaryCard`, mostrar los 2-3
primeros nombres además del recuento.

**c) Quién ha hecho qué hoy.** Ya tienes `completedBy` y `assignedTo` en el modelo de
rutina, y tras el plan de seguridad los nombres se resuelven desde `Household.memberProfiles`
sin lecturas extra. Una línea de "Hoy: Ana 3 · Dani 2" da sensación de equipo.

**d) Comprimir la cabecera.** Fecha y saludo en menos altura para que lo accionable entre en
la primera pantalla sin scroll.

**e) Estado de sincronización.** No hay forma de saber si un tick se ha guardado. Firestore
cachea offline, así que el usuario cree que todo va bien hasta que descubre que no. Un
indicador discreto de "sin conexión · se sincronizará" cuando toque.

### Aceptación

- En un móvil de 5,5" entra sin scroll: progreso del día, tarjeta de compra con nombres y
  las dos primeras rutinas.
- Los números coinciden con la pestaña de rutinas (mismo `RoutineSchedule`, sin duplicar la
  lógica: **reutiliza el use case, no copies el filtro**).
- Con el avión activado aparece el indicador de sin conexión.

---

# Bloque C — Pulido

## U6 — Accesibilidad del dashboard

### Problema

En [`DashboardScreen.kt:235`](app/src/main/java/com/monsteraltech/habitly/feature/dashboard/presentation/DashboardScreen.kt),
`HabitlyToggleCard(checked = false, ...)` está **fijo a `false`**, y ni el `RitualToggle`
(línea 241) ni el `IconHalo` (línea 194) llevan `contentDescription`. Resultado: TalkBack
lee "no marcado" para absolutamente todo, sin decir de qué.

### Cambio

- Estado `checked` real, no literal.
- `contentDescription` en los iconos con significado; `null` solo en los decorativos
  (esa parte está bien hecha donde el icono acompaña a un texto).
- Revisa que las dianas táctiles lleguen a 48 dp.
- Pasa **Accessibility Scanner** por las cinco pestañas y arregla lo que salga.

### Aceptación

- TalkBack anuncia cada rutina con su nombre y su estado real.
- Accessibility Scanner sin errores de contraste ni de tamaño de diana en las pantallas
  principales.

---

## U7 — Plurales e icono de "Mi Casa"

### Problema

1. [`ShoppingScreen.kt:571`](app/src/main/java/com/monsteraltech/habitly/feature/shopping/presentation/ShoppingScreen.kt)
   construye el plural a mano: `"${item.quantity} ${item.unit}${if (...) "s" else ""}"`.
   Produce "2 kgs", "3 unidads", y en inglés es peor. El mismo patrón está duplicado en
   `ShoppingItemCard` (línea 629).
2. [`MainScreen.kt:66`](app/src/main/java/com/monsteraltech/habitly/feature/main/presentation/MainScreen.kt) —
   la pestaña "Mi Casa" usa `Icons.Rounded.Settings`, y desde dentro se navega a los Ajustes
   de verdad. Dos cosas distintas con el mismo símbolo.

### Cambio

1. `<plurals>` por unidad en `values/` y `values-en/`. Ya usas `pluralStringResource`
   correctamente en `dashboard_pending_products` y `shopping_products_count`: sigue ese
   patrón. **`ShoppingItemCard` parece no usarse** (`ShoppingItemRow` la sustituyó);
   comprueba si tiene referencias y si no, bórrala en vez de arreglarla.
2. `Icons.Rounded.Groups` o `Icons.Rounded.Home` para "Mi Casa".

### Aceptación

- "2 kg", "3 unidades", "1 paquete" correctos en español e inglés.
- Sin código muerto nuevo.

---

## Verificación final (obligatoria)

Además de `testDebugUnitTest`, compila y **prueba a mano el APK de release**: el plan de
seguridad activó R8, y R8 rompe en tiempo de ejecución, no al compilar.

```bash
./gradlew assembleRelease
```

Recorre: login → lista de la compra (añadir, tachar, deslizar, buscar, archivar) →
despensa → rutinas → dashboard → **widget en la pantalla de inicio**. Cualquier
`ClassNotFoundException` o campo que llegue nulo es una regla `-keep` que falta en
`proguard-rules.pro`.

Comprueba también **modo claro y oscuro** y **español e inglés** en todo lo que hayas tocado.

---

## Notas para el siguiente plan

### Componentes nuevos en `ui/components/`

- **`HabitlySwipeRow`** (`HabitlySwipeRow.kt`) — fila deslizable: derecha = acción principal
  (la fila vuelve a su sitio), izquierda = borrar. Aporta la háptica y los fondos de color.
  Parámetro `dismissOnDelete = false` cuando el borrado abre un diálogo de confirmación.
  **El contenido debe pintar un fondo opaco**, o el fondo del gesto se ve siempre; por eso
  las filas reciben un `containerColor`.
- **`Modifier.swipeRowSemantics(...)`** (mismo fichero) — las acciones equivalentes para
  TalkBack. Tiene que aplicarse **al mismo nodo que lleva el `toggleable`/`clickable`**, no
  al contenedor: las acciones personalizadas solo se ofrecen sobre el nodo que se enfoca.

Otros reutilizables fuera de `ui/components/`:

- **`ItemQuantityLabel` / `quantityWithUnit`** (`feature/shopping/presentation/components/QuantityLabel.kt`)
  y la constante `DEFAULT_UNIT`. Cualquier sitio que pinte cantidad + unidad debe pasar por
  aquí en vez de concatenar.
- **`ConnectivityObserver`** (`feature/dashboard/data/`) — `Flow<Boolean>` de "hay red".
  Vive en dashboard porque es su único consumidor; si otra pantalla lo necesita, muévelo.

### Dependencias añadidas

Ninguna. Todo sale de lo que ya había (Glance, material3, Compose).

**Sí se tocó `proguard-rules.pro`**, y era obligatorio: `actionRunCallback<T>()` guarda el
**nombre** de la clase en el `PendingIntent` y Glance la reinstancia por reflexión, así que
R8 se la comía. Regla añadida:

```
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
```

Verificado en `mapping.txt`: `ToggleShoppingItemAction` y `CompleteRoutineAction` conservan
su nombre original tras `minifyReleaseWithR8`.

También se añadió `ACCESS_NETWORK_STATE` al manifiesto (Firebase ya la traía por *merge*,
pero declararla explícitamente evita sorpresas si algún día se quita Firestore).

### Cambios de firma

- **`WidgetSnapshot`** ahora expone `pendingItems: List<WidgetLine>` y
  `pendingRoutines: List<WidgetLine>`, con `WidgetLine(id, label)`. `WidgetSnapshot.kt`.
- **`WidgetEntryPoint`** gana `widgetActionsUseCase()`.
- **`WidgetActionsUseCase`** (`feature/widget/domain/`) — `checkShoppingItem(itemId)` y
  `completeRoutine(routineId)`. Resuelve sesión y casa por su cuenta y reutiliza
  `ToggleShoppingItemUseCase` / `ToggleRoutineUseCase` / `AdvanceRotationUseCase`, así que un
  tick desde el widget escribe exactamente lo mismo que uno desde la app.
- **`WidgetRefresher`** (interfaz en `feature/widget/domain/`, impl `GlanceWidgetRefresher`).
  **`ShoppingRepositoryImpl` y `RoutinesRepositoryImpl` la reciben por constructor**: si
  añades un repositorio que escriba algo que el widget pinte, inyéctala y llama a `refresh()`
  tras confirmar la escritura.
- **`ShoppingUiState`** gana `searchQuery`, `quickAdd: QuickAddState`, `isSearching`,
  `isCompletedSectionExpanded`, `quickAddPantryMatch`, `quickAddDuplicate` y
  `recentlyDeletedPantryName`. Constantes públicas nuevas: `ANY_STORE`, `DEFAULT_STORES`.
- **`ShoppingItemRow`** ya no acepta `isCompleted`; ahora exige `containerColor`.
- **`DashboardUiState`** gana `todayRoutinesTotal`, `todayRoutinesDone`,
  `todayByMember: List<MemberTally>`, `isOffline` y la derivada `todayProgress`.
  `DashboardViewModel` recibe además `GetMemberProfilesUseCase` y `ConnectivityObserver`.
- **`ShoppingScreen`** ya no acepta `onNavigateToAddProduct`.
- **`RestorePantryItemUseCase`** nuevo en `PantryUseCases.kt` (deshacer del gesto de la
  despensa).

### Borrado (no queda código muerto)

- `AddProductScreen.kt` y `AddProductViewModel.kt` — sustituidos por `QuickAddSheet.kt`.
  `CATEGORIES` y `UNITS` viven ahora en `QuickAddSheet.kt`.
- `HiddenRoutes.ShoppingAddProduct` y su `composable` en `MainScreen.kt`.
- `ShoppingItemCard` — no tenía referencias, como sospechaba el plan.
- El parámetro `onNavigateToRoutines` de `DashboardScreen` estaba muerto desde antes; ahora
  lo usa la tarjeta de progreso del día.

### Pendiente de verificar en un móvil (no se pudo hacer aquí)

`assembleDebug`, `assembleRelease` (con R8) y los 343 tests de `testDebugUnitTest` pasan, pero
**no había ningún dispositivo conectado**, así que estas aceptaciones siguen sin comprobar:

1. Tachar desde el widget sin abrir la app, y el refresco en los dos sentidos.
2. El pulso háptico y que el gesto no pelee con el scroll vertical.
3. TalkBack: que ofrezca las acciones personalizadas de las filas.
4. El indicador de "sin conexión" con el modo avión.
5. **Accessibility Scanner por las cinco pestañas** (U6). Hallazgo ya conocido sin arreglar:
   las flechas de reordenar de `RoutineCard` (`RoutinesScreen.kt`) son `IconButton` de 32 dp,
   por debajo de los 48 dp. Se dejaron así a propósito: dos dianas de 48 dp apiladas suben la
   altura mínima de cada tarjeta de rutina de ~88 dp a ~120 dp, y esa es una decisión de
   diseño, no una corrección mecánica.
6. Modo claro/oscuro y español/inglés en lo tocado.
