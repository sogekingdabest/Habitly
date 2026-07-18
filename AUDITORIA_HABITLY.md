# Auditoría de Habitly — Arquitectura, funcionalidades, mejoras y monetización

> Revisión del código a fecha 2026-07-03. Cubre: evaluación de arquitectura, fallos encontrados (con referencia a archivo), mejoras propuestas, nuevas funcionalidades y una estrategia de monetización no intrusiva.

---

## 1. Resumen ejecutivo

Habitly está **bien planteada**: la separación `data / domain / presentation` por feature es real (no solo carpetas), los use cases existen y aportan, Hilt está bien usado, y la apuesta por IA on-device con LiteRT-LM es un diferenciador genuino (privacidad + coste cero de servidor).

Los problemas graves no están en la estructura sino en tres frentes:

1. **Seguridad de Firestore**: las reglas actuales permiten a cualquier usuario autenticado leer y modificar *cualquier* casa, incluidos sus códigos de invitación y su lista de miembros. Es el problema nº 1 de la app.
2. **Errores silenciados**: el repositorio de rutinas devuelve `Result.success` en los `catch`, así que las escrituras fallidas parecen exitosas y se pierden datos sin avisar.
3. **Recordatorios rotos en la práctica**: falta el permiso `POST_NOTIFICATIONS`, por lo que en Android 13+ (la inmensa mayoría de dispositivos con targetSdk 36) las notificaciones de rutinas nunca se muestran.

Nada de esto es difícil de arreglar, y el resto del informe está ordenado por prioridad.

---

## 2. Lo que está bien

- **Clean Architecture real por feature**: interfaces de repositorio en `domain`, implementaciones en `data`, ViewModels que solo hablan con use cases (casi siempre). Ejemplo limpio: `feature/shopping`.
- **Archivado atómico de la lista de compra** (`ShoppingRepositoryImpl.archiveShoppingList`): batch que copia al historial y borra la lista activa en una sola operación. Correcto.
- **Restore del historial** regenerando IDs y reseteando `isChecked` — bien pensado.
- **MVI completo en register** (`Intent` / `UiState` / `Effect`): es el patrón mejor ejecutado de la app.
- **IA on-device**: gestión de descarga con archivo temporal + rename, limpieza de modelos legacy, sesiones de chat persistidas en Room, system prompt con contexto de la app inyectado. El planteamiento es sólido.
- **Tests unitarios** existentes para login, rutinas y asistente IA, con fakes de repositorio. Buena base para ampliar.
- `google-services.json` correctamente fuera de git.

---

## 3. Seguridad (CRÍTICO)

### 3.1 Reglas de Firestore: cualquier usuario puede leer/escribir cualquier casa

En [firestore.rules:25](firestore.rules):

```
match /households/{householdId} {
  allow read, write: if request.auth != null;
```

El comentario del archivo asume que "la seguridad reside en que el inviteCode es secreto y el householdId es un UUID ininteligible", pero la regla permite **queries de colección completa**: cualquier usuario autenticado puede ejecutar `firestore.collection("households").get()` y obtener *todas* las casas con sus `inviteCode` y `members`. Y con `write` abierto, puede:

- Añadirse a sí mismo a `members` de cualquier casa (y con ello leer su lista de la compra por la regla de subcolecciones).
- Renombrar, vaciar `members` o directamente **borrar** el documento de cualquier casa.

**Solución recomendada** (sin Cloud Functions):

1. Crear una colección lookup `invite_codes/{code}` → `{ householdId }`. El cliente que se une lee **solo ese documento** (get puntual, no query).
2. Restringir `households`:
   ```
   match /households/{householdId} {
     allow read: if request.auth.uid in resource.data.members;
     allow create: if request.auth != null
                   && request.auth.uid in request.resource.data.members;
     // Unirse: solo puedes añadirte a ti mismo, sin tocar nada más
     allow update: if request.auth.uid in resource.data.members
                   || (request.resource.data.members.hasAll(resource.data.members)
                       && request.resource.data.members.removeAll(resource.data.members) == [request.auth.uid].toSet()
                       && request.resource.data.diff(resource.data).affectedKeys() == ['members'].toSet());
   }
   ```
   (La expresión exacta se puede afinar; la idea clave: un no-miembro solo puede *añadirse a sí mismo* a `members` y nada más.)
3. Adaptar `HouseholdRepositoryImpl.joinHousehold` ([HouseholdRepositoryImpl.kt:86](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt:86)) para resolver el código vía `invite_codes/{code}` en lugar de `whereEqualTo("inviteCode", ...)`.

### 3.2 Códigos de invitación débiles y sin unicidad

[HouseholdRepositoryImpl.kt:29](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt:29): `UUID.randomUUID().toString().substring(0, 6)` produce solo caracteres hex (0-9, A-F) → 16⁶ ≈ 16,7M combinaciones, y **no se comprueba colisión** (dos casas podrían compartir código; `joinHousehold` coge `first()` arbitrariamente).

**Mejora**: generar con `SecureRandom` sobre un alfabeto de 32 caracteres sin ambiguos (p. ej. `ABCDEFGHJKMNPQRSTUVWXYZ23456789`), 8 caracteres → 10¹² combinaciones, y comprobar unicidad contra `invite_codes` al crear. Añadir opción de **regenerar código** (hoy el código es eterno: un ex-miembro puede volver a entrar siempre).

### 3.3 Perfiles de usuario legibles por cualquiera

[firestore.rules:9](firestore.rules): `allow read: if request.auth != null` sobre `users/{userId}` expone displayName, nickname y `activeHouseholdId` de **todos los usuarios de la app** a cualquier autenticado. Restringir la lectura a usuarios que compartan casa (o duplicar el nickname dentro del documento de la casa, que suele ser más simple y barato).

### 3.4 Token persistido sin uso

[AuthRepositoryImpl.kt:184](app/src/main/java/com/monsteraltech/habitly/feature/login/data/repository/AuthRepositoryImpl.kt:184) guarda el ID token de Firebase en DataStore en claro, pero **nunca se lee** (Firebase SDK ya gestiona la sesión y esos tokens caducan en 1 hora). Es código muerto que además almacena material sensible. Eliminar `persistToken` y las claves de DataStore.

---

## 4. Bugs y fallos funcionales

Ordenados por impacto:

| # | Fallo | Dónde |
|---|-------|-------|
| 1 | **Falta `POST_NOTIFICATIONS`** en el manifest y no se solicita en runtime → los recordatorios de rutinas no se muestran nunca en Android 13+ | [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [RoutineReminderWorker.kt](app/src/main/java/com/monsteraltech/habitly/feature/routines/data/worker/RoutineReminderWorker.kt) |
| 2 | **Errores silenciados como éxito**: `updateRoutineCompletion`, `updateRoutine`, `reorderRoutines`, `deleteRoutine` y `addRoutine` devuelven `Result.success(Unit)` dentro del `catch`. El usuario cree que guardó y no se guardó | [RoutinesRepositoryImpl.kt:79-193](app/src/main/java/com/monsteraltech/habitly/feature/routines/data/repository/RoutinesRepositoryImpl.kt) |
| 3 | **Caché de rutinas que no sirve**: `RoutinesCacheManager` se escribe al añadir/borrar, pero sus flows **nunca se observan desde la UI** (solo se leen dentro del propio repositorio) y lo cacheado en un fallo jamás se re-sincroniza con Firestore → datos fantasma. Además, **Firestore ya trae persistencia offline por defecto en Android**, así que esta caché es redundante. Recomendación: eliminarla entera y apoyarse en la persistencia de Firestore | [RoutinesCacheManager.kt](app/src/main/java/com/monsteraltech/habitly/feature/routines/data/cache/RoutinesCacheManager.kt) |
| 4 | **El recordatorio ignora los días programados**: el `PeriodicWorkRequest` es diario y el worker no comprueba `scheduledDays` → una rutina semanal notifica los 7 días | [ReminderUseCases.kt:42](app/src/main/java/com/monsteraltech/habitly/feature/routines/domain/usecase/ReminderUseCases.kt:42) |
| 5 | **WorkManager periódico es inexacto** (Doze puede retrasarlo horas). Para recordatorios a hora fija usar `AlarmManager.setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, o aceptar la inexactitud conscientemente | ídem |
| 6 | **Descarga de modelos (1.6–3.6 GB) atada al ViewModel**: `onDownloadModel` corre en `viewModelScope`; si el usuario cambia de pestaña, el VM muere y la descarga de gigas se cancela y **empieza de cero** (sin resume, sin checksum, sin restricción a Wi-Fi). Debería ser un `WorkManager` worker con notificación de progreso y soporte de `Range` para reanudar | [AiAssistantViewModel.kt:132](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/presentation/AiAssistantViewModel.kt:132), [LocalModelManager.kt:61](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/source/LocalModelManager.kt:61) |
| 7 | **Casas huérfanas**: cada usuario nuevo crea automáticamente una casa ("Casa de X"); si luego se une a otra, la casa vieja queda vacía para siempre en Firestore. Además no existe "salir de casa", "expulsar miembro" ni "borrar casa" | [HouseholdRepositoryImpl.kt:19](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt:19) |
| 8 | `joinHousehold` no es transaccional (lecturas fuera del batch) → condiciones de carrera al unirse dos personas a la vez. Usar `firestore.runTransaction` | [HouseholdRepositoryImpl.kt:86](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt:86) |
| 9 | `getFrequentItems` usa `.limit(20)` **sin `orderBy`** → analiza 20 historiales arbitrarios, no los más recientes | [ShoppingRepositoryImpl.kt:277](app/src/main/java/com/monsteraltech/habitly/feature/shopping/data/repository/ShoppingRepositoryImpl.kt:277) |
| 10 | El `init` del repositorio de IA hace I/O de disco (`deleteRecursively`, `File.exists`) en el hilo que crea el singleton (main) → jank al arrancar. Mover a un scope con `Dispatchers.IO` | [AiAssistantRepositoryImpl.kt:53](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/repository/AiAssistantRepositoryImpl.kt:53) |
| 11 | Motor de IA fijado a `Backend.CPU()` aunque el manifest declara librerías OpenCL para GPU. Detectar y ofrecer GPU cuando esté disponible; `largeHeap="true"` es un parche relacionado | [AiAssistantRepositoryImpl.kt:159](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/repository/AiAssistantRepositoryImpl.kt:159) |
| 12 | `onProgress` emite por cada 8 KB → cientos de miles de updates de StateFlow para un modelo de 2.5 GB. Emitir cada ~1% o cada 500 ms | [LocalModelManager.kt:96](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/source/LocalModelManager.kt:96) |
| 13 | La notificación mete `routine_id` como extra pero **nadie lo lee** en `MainActivity` → tocar la notificación no lleva a la rutina | [RoutineReminderWorker.kt:45](app/src/main/java/com/monsteraltech/habitly/feature/routines/data/worker/RoutineReminderWorker.kt:45), [MainActivity.kt](app/src/main/java/com/monsteraltech/habitly/MainActivity.kt) |
| 14 | `MainActivity` decide `startDestination` solo con `currentUser != null`, sin comprobar email verificado → un usuario sin verificar que reabre la app entra directo | [MainActivity.kt:25](app/src/main/java/com/monsteraltech/habitly/MainActivity.kt:25) |
| 15 | El contexto de la IA (lista + rutinas) solo se inyecta en el **primer** mensaje de la sesión → en chats largos queda obsoleto. Además solo incluye rutinas personales, no las de la casa | [AiAssistantViewModel.kt:95](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/presentation/AiAssistantViewModel.kt:95), [GetAiContextUseCase.kt:31](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/domain/usecase/GetAiContextUseCase.kt:31) |
| 16 | `getMemberProfiles` hace N `get()` secuenciales → usar `whereIn(FieldPath.documentId(), chunk)` (chunks de 30) o paralelizar con `async` | [HouseholdRepositoryImpl.kt:151](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt:151) |
| 17 | `isMinifyEnabled = false` en release → APK grande y sin ofuscar. Activar R8 con reglas keep para Firestore (los modelos con reflexión) | [app/build.gradle.kts:29](app/build.gradle.kts:29) |

---

## 5. Arquitectura — evaluación y mejoras

### 5.1 Inconsistencia de patrón de presentación

Conviven tres estilos: MVI completo en `register` (Intent/UiState/Effect), MVI parcial en `login`, y "ViewModel con métodos públicos" en `shopping`/`routines`/`household`. No es grave, pero encarece el mantenimiento. **Recomendación**: no reescribir todo; adoptar como estándar el estilo de shopping (métodos + `StateFlow`) para pantallas simples y reservar Intent/Effect para flujos con navegación/one-shot events, y documentarlo en un `CLAUDE.md`/`CONTRIBUTING.md`.

### 5.2 `FirebaseAuth` inyectado en ViewModels

`ShoppingViewModel`, `RoutinesViewModel`, `DashboardViewModel`, `HouseholdViewModel` y `MainViewModel` reciben `FirebaseAuth` directamente. Eso acopla presentación a Firebase y complica los tests (los tests actuales lo esquivan usando fakes de repos, pero no pueden testear estos VMs). **Crear `GetCurrentUserIdUseCase` / `ObserveAuthStateUseCase`** sobre `AuthRepository` y quitar Firebase de la capa de presentación.

### 5.3 `domain` con dependencias de Android

`ReminderUseCases` (en `domain/usecase`) usa `Context` y `WorkManager` directamente. Si el criterio es Clean Architecture, eso pertenece a `data`/infra detrás de una interfaz `ReminderScheduler` en domain. Lo mismo aplica a `@PropertyName` de Firestore dentro de `ShoppingItem` (modelo de dominio): la solución canónica es un DTO en `data` + mapper, y dejar el dominio con `val` e inmutable.

### 5.4 Dependencias cruzadas entre features

`AuthRepository` vive en `feature/login` pero lo consumen `register`, `household` y `aiassistant`; los modelos de `household` los usan todas las features. Funciona porque todo está en un módulo, pero el grafo real ya pide un paquete `core/` (`core/auth`, `core/model`, `core/common`). No hace falta multi-módulo Gradle todavía; con paquetes basta a este tamaño.

### 5.5 Estado calculado en getters

`ShoppingUiState` calcula `filteredPendingItems`, `pendingItemsByStore`, etc. en getters → se re-filtra y re-agrupa **en cada recomposición**. Precalcular estos campos al actualizar el estado en el VM (o `derivedStateOf` en la UI).

### 5.6 Otros detalles

- Navegación con strings (`"shopping_add_product"`): Navigation Compose ya soporta rutas type-safe con `@Serializable`. Migración barata y elimina typos.
- La inicialización usuario+casa como efecto colateral del `init` de `MainViewModel` es frágil (si falla, no hay reintento ni estado visible). Convertirla en un paso explícito de onboarding (ver §6.1).
- Strings de UI hardcodeados en español por todo el código (`"Casa de..."`, `"Cualquiera"`, tiendas por defecto duplicadas en [ShoppingViewModel.kt:25](app/src/main/java/com/monsteraltech/habitly/feature/shopping/presentation/ShoppingViewModel.kt:25) y línea 108). Mover a `strings.xml` — el README promete inglés y gallego, y hoy es imposible sin tocar código.
- `"Cualquiera"` es a la vez una tienda asignable y el filtro "todas" — semántica confusa; separar el concepto "sin tienda" del filtro "ver todas".
- Tiendas por defecto (Mercadona/Lidl/Carrefour) muy España-céntricas: hacerlas editables/eliminables por casa.

---

## 6. Funcionalidades: valoración y nuevas propuestas

### 6.1 Fallas de diseño de producto en lo existente

- **Onboarding**: hoy se crea una casa automáticamente al entrar. Mejor una pantalla "¿Crear casa o unirse con código?" — evita las casas huérfanas y comunica la propuesta multiusuario desde el minuto uno.
- **Rutinas sin historial**: solo se guarda `lastCompletedAt/lastCompletedBy` → imposible mostrar rachas, estadísticas o "quién hizo qué esta semana". Guardar completados en una subcolección `completions` (fecha + usuario) habilita muchísimo producto (ver abajo).
- **Rutinas de casa de completado único**: cualquiera la marca y queda hecha para todos. Probablemente intencional (fregar platos), pero convendría soportar también rutinas "para cada miembro" (p. ej. hacer la cama).
- **Recordatorios por dispositivo**: se programan solo donde se activaron; el resto de la casa no se entera. Con FCM (ver §6.2) serían compartidos.
- **La IA habla pero no actúa**: genera una lista de la compra en texto que el usuario tiene que copiar a mano. La feature estrella está a un paso: pedir al modelo salida estructurada (JSON) y ofrecer un botón **"Añadir estos 12 productos a la lista"**. Es la mejora con mejor ratio esfuerzo/impacto de toda la app.

### 6.2 Nuevas funcionalidades propuestas

Ordenadas por relación impacto/esfuerzo:

1. **IA → acciones**: "Añadir a la lista" desde una respuesta del asistente (salida JSON + parser + confirmación). También "crea una rutina de limpieza semanal" → rutinas pre-rellenadas.
2. **Widget de pantalla de inicio** (Glance): lista de la compra con checks y rutinas de hoy. Para una app de hogar, el widget se usa más que la app.
3. **Notificaciones de casa (FCM)**: "Dani añadió 3 productos", "María completó Sacar la basura". Requiere Cloud Functions (plan Blaze, coste ~0 a esta escala) o supresión si se prefiere evitar backend.
4. **Rachas y estadísticas de rutinas** (requiere el historial de completados de §6.1): calendario de cumplimiento, racha actual, reparto por miembro. Gamificación ligera sin puntos artificiales.
5. **Planificador de menús semanal**: la IA propone menú → receta → ingredientes a la lista. Une las dos features existentes (IA + compra) en un flujo con valor real.
6. **Precios y presupuesto**: precio opcional por item, total estimado del carro y gasto por compra archivada → gráfica mensual. El historial ya existe; es terreno fértil.
7. **Despensa**: al archivar la compra, opción de "mover a despensa"; la IA sugiere recetas con lo que hay ("¿qué ceno con lo de la despensa?"). Refuerza el diferenciador on-device.
8. **Asignación y rotación de tareas**: asignar rutina a un miembro, o rotación automática semanal ("esta semana friega Juan").
9. **Entrada rápida por voz** y/o **escáner de código de barras** para añadir productos.
10. **Compartir lista como texto** (WhatsApp/Telegram) para hogares donde no todos tienen la app — también funciona como canal de adquisición.
11. **Multi-casa con selector**: el modelo (`activeHouseholdId`) ya lo soporta; falta la UI.
12. **Deep link desde la notificación de rutina** a la rutina concreta (arreglando de paso el bug #13).

---

## 7. Monetización no intrusiva y opcional

**Contexto favorable**: la IA corre en el dispositivo y Firebase en plan gratuito aguanta miles de usuarios de este perfil → el coste marginal por usuario es ~0. Eso permite una monetización 100% opcional sin presión.

**Principio rector**: el núcleo colaborativo (lista + rutinas + casa compartida) debe ser gratis *para todos los miembros de la casa*, porque el valor de la app depende de que toda la familia la use. Nunca poner un muro entre dos miembros de una misma casa.

### Estrategia recomendada: "Habitly Plus" + propina

**Nivel 1 — Propina (fricción cero)**
Producto in-app de pago único tipo "Invítanos a un café" (2–5 €), opcionalmente con una insignia o icono de app alternativo como agradecimiento. Se integra con Play Billing en una tarde y no toca ninguna feature. Para una app de nicho con usuarios agradecidos, rinde más de lo que parece.

**Nivel 2 — Habitly Plus (compra única "de por vida", o suscripción baja ~1 €/mes)**
Solo funciones de *conveniencia y poder*, nunca el núcleo:

| Gratis (siempre) | Plus |
|---|---|
| Lista de la compra completa, tiempo real, historial 30 días | Historial ilimitado + export CSV |
| Rutinas + recordatorios | Estadísticas, rachas, calendario de cumplimiento |
| 1 casa | Multi-casa |
| IA on-device con los 3 modelos | Planificador de menús, despensa, presupuesto |
| Tema estándar | Temas/iconos, widget configurable |

Recomiendo **compra única** antes que suscripción: no hay costes recurrentes que justificar, y en apps de hogar la suscripción genera rechazo. Si en el futuro se ofrece **IA en la nube opcional** (modelo grande vía API para móviles modestos), *eso* sí es una suscripción legítima porque tiene coste real por uso.

### Qué evitar

- **Banners e intersticiales**: destrozan la experiencia doméstica y el posicionamiento. Como mucho, un rewarded ad explícito y voluntario ("prueba Plus 24h"), pero ni siquiera lo recomiendo: el ancla de la app es *privacidad* ("tu IA no sale de tu móvil"), y los SDK de ads contradicen ese mensaje con su tracking.
- **Vender/compartir datos**: incompatible con el pitch de privacidad, que es el mejor argumento de marketing que tiene Habitly.
- **Limitar el nº de items o de miembros**: castiga justo el caso de uso core.

### Requisitos previos para publicar (bloqueantes)

1. **Borrado de cuenta**: Google Play **exige** que las apps con registro ofrezcan eliminación de cuenta y datos (en la app y vía web). Hoy no existe → añadir en `HouseholdScreen` (borrar usuario de Auth + documento de perfil + salida de casa).
2. **Política de privacidad** publicada (obligatoria en la ficha de Play).
3. Arreglar las reglas de Firestore (§3) **antes** de tener usuarios reales.
4. Activar R8/minify y firmar release.

---

## 8. Hoja de ruta sugerida

**Fase 1 — Seguridad y corrección (antes de cualquier usuario real)**
Reglas de Firestore + lookup de invite codes → permiso de notificaciones → dejar de silenciar errores en rutinas (eliminar caché redundante) → borrado de cuenta → descarga de modelos con WorkManager.

**Fase 2 — Redondear producto**
Onboarding crear/unirse → salir de casa / expulsar / regenerar código → deep link de notificaciones → recordatorios que respeten `scheduledDays` → strings a recursos (i18n ES/EN/GL).

**Fase 3 — Diferenciación**
IA que añade a la lista → widget → historial de completados + rachas → planificador de menús.

**Fase 4 — Monetización**
Propina → Habitly Plus (compra única) sobre las features de la fase 3.
