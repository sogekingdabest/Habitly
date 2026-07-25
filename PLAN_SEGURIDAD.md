# Plan 1 — Seguridad

> **Este plan se ejecuta PRIMERO**, antes de `PLAN_USABILIDAD.md` y `PLAN_FUNCIONALIDADES.md`.
> Cambia el modelo de datos de los miembros de la casa, del que dependen los otros dos planes.

---

## Contexto del proyecto (léelo antes de empezar)

**Habitly** es una app Android de convivencia doméstica: lista de la compra compartida,
despensa, rutinas del hogar con rachas y rotación entre miembros, y un asistente de IA que
corre **en local** (Gemma vía LiteRT-LM, modelo de 2,5–3,6 GB descargado bajo demanda).

- **Stack**: Kotlin, Jetpack Compose (Material 3), Hilt, Firebase Auth + Firestore, Room
  (solo para el historial del chat de IA), WorkManager, Glance (widget de inicio).
- **minSdk 29, targetSdk 36.**
- **Arquitectura**: `feature/<nombre>/{data,domain,presentation}`. Los repositorios tienen
  interfaz en `domain/repository` e implementación en `data/repository`; la lógica de
  negocio vive en use cases (`domain/usecase`). Los ViewModels exponen un `StateFlow` de
  un `data class ...UiState`.
- **Estado**: en preparación de beta cerrada en Google Play. Hay ~12 testers previstos.
  Esto importa: **hay datos reales en producción**, las migraciones no pueden romperlos.
- **Idioma**: comentarios y textos de UI en español. Los `strings.xml` están traducidos
  (`values/` y `values-en/`), **nunca metas texto literal en los Composables**.

### Cómo compilar y verificar

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

En PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` y `.\gradlew.bat`.

Hay 23 tests unitarios en `app/src/test/`, casi todos del asistente de IA. **Deben seguir
pasando al terminar.** Hay fakes de repositorio reutilizables en
`app/src/test/java/com/monsteraltech/habitly/feature/aiassistant/data/repository/`.

### Qué NO debes tocar en este plan

Para no colisionar con los agentes que vienen después:

- `feature/dashboard/**` (lo rehace el plan de usabilidad)
- `feature/shopping/presentation/**` (ídem)
- `feature/widget/**` (ídem)
- `feature/main/presentation/MainScreen.kt` (ídem)

Si necesitas tocar alguno de esos ficheros, hazlo con el cambio mínimo y anótalo en la
sección "Notas para los siguientes planes" al final de este documento.

---

## S1 — Fijar el SHA-256 de los modelos de IA

**Esfuerzo: 10 minutos.** Empieza por aquí: es la mejora de seguridad con mejor relación
coste/beneficio de todo el plan.

### Problema

`LocalModelManager` descarga 2,5–3,6 GB desde Hugging Face y se los pasa a un motor de
inferencia **nativo**, validando únicamente que el tamaño del fichero esté dentro del 95 %
del esperado ([`LocalModelManager.kt:64`](app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/data/source/LocalModelManager.kt)).

Lo llamativo es que la verificación criptográfica **ya está escrita y funcionando**
(`verifyChecksum`, línea 235): calcula el SHA-256 en streaming durante la descarga y lo
compara con `AiModelConfig.sha256`. Pero ese campo es `null` en los dos modelos del
catálogo, así que el hash se calcula, se loguea y se tira.

### Cambio

En `app/src/main/java/com/monsteraltech/habitly/feature/aiassistant/domain/model/AiModelConfig.kt`,
rellena `sha256` (y de paso corrige `sizeBytes` con el valor exacto):

```kotlin
val Gemma4_E2B_IT = AiModelConfig(
    id = "gemma-4-e2b",
    name = "Gemma 4 (2B) - Inteligente",
    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
    sizeBytes = 2_588_147_712L,
    filename = "gemma-4-e2b.litertlm",
    sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
)

val Gemma4_E4B_IT = AiModelConfig(
    id = "gemma-4-e4b",
    name = "Gemma 4 (4B) - Muy Potente",
    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
    sizeBytes = 3_659_530_240L,
    filename = "gemma-4-e4b.litertlm",
    sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"
)
```

Estos hashes salen del puntero LFS de Hugging Face, que se consulta **sin descargar el
fichero**. Si algún día cambias de modelo, el comando es:

```bash
curl -sS "https://huggingface.co/<repo>/raw/main/<fichero>.litertlm"
```

Devuelve `oid sha256:<hash>` y `size <bytes>`. Documenta este comando en el KDoc del campo
`sha256` para que el siguiente que toque el catálogo no tenga que redescubrirlo.

### Aceptación

- Un test unitario nuevo comprueba que **todos** los modelos de `AvailableAiModels.models`
  tienen `sha256 != null` y de 64 caracteres hexadecimales. Así, añadir un modelo sin hash
  rompe la build en vez de degradar la seguridad en silencio.
- Descarga real de un modelo en dispositivo: debe completar sin lanzar
  `NonRetryableDownloadException`. **Si falla la verificación, no fuerces el hash: significa
  que el artefacto remoto cambió y hay que investigar por qué.**

---

## S2 — Cerrar la lectura de perfiles ajenos y cortar el acceso no autorizado a casas

**Esfuerzo: alto. Es el hallazgo grave del análisis.** Tómate el tiempo que haga falta.

### Problema (cadena de dos fallos)

1. [`firestore.rules:24`](firestore.rules) — `allow read: if isSignedIn()` sobre
   `/users/{userId}`: **cualquier usuario registrado puede leer el perfil de cualquier
   otro**, incluido su `activeHouseholdId`.
2. [`firestore.rules:77`](firestore.rules) — `isSelfJoin()` permite que un no-miembro se
   añada a `members` de una casa. Solo comprueba que se añade *a sí mismo* y que no toca
   otros campos. **No comprueba que haya presentado un código de invitación válido.**

Encadenados: me registro → leo `/users/{cualquier-uid}` → obtengo su `activeHouseholdId` →
lanzo un `update` añadiéndome a `members` → estoy dentro de su casa, con su lista de la
compra, su despensa y sus rutinas. Sin código de invitación.

El comentario de la línea 20 del fichero de reglas ya reconoce la lectura abierta como
deuda técnica, pero no contempla que se combina con el self-join.

### Por qué el arreglo es cerrar `/users`

En reglas de Firestore **no se puede demostrar criptográficamente que el cliente conoce el
código** de invitación: el cliente no puede leer el doc de la casa (la lectura exige ser
miembro), así que exigir `request.resource.data.inviteCode == resource.data.inviteCode` se
cumple sola —`request.resource.data` es el estado *posterior* a la escritura y arrastra el
campo sin que el cliente lo envíe—. Es una comprobación inútil.

La defensa real es **que el `householdId` deje de ser obtenible**. Cerrando `/users` a su
dueño, la única forma de conocer un `householdId` pasa a ser: ser ya miembro, o resolverlo
desde `/invite_codes/{code}`, que exige conocer el código. La cadena se rompe por su primer
eslabón.

> **Endurecimiento posterior (fuera de alcance de este plan):** mover la unión a una Cloud
> Function `callable` que valide el código en servidor. Requiere plan Blaze. Anótalo como
> deuda, pero **no lo implementes ahora**.

### Cambio

**a) Desnormalizar los perfiles dentro del documento de la casa.**

`Household` (`feature/household/domain/model/Household.kt`) gana un campo:

```kotlin
data class Household(
    var id: String = "",
    var name: String = "",
    var inviteCode: String = "",
    var members: List<String> = emptyList(),
    var customStores: List<String> = emptyList(),
    /**
     * Perfil público de cada miembro, duplicado aquí para poder cerrar la lectura de
     * /users a su propio dueño. Clave = uid. Se mantiene al día con auto-relleno
     * perezoso: cada usuario reescribe su entrada al abrir la app (ver S2).
     */
    var memberProfiles: Map<String, MemberProfile> = emptyMap()
)

data class MemberProfile(
    var displayName: String = "",
    var nickname: String = ""
)
```

Ojo: Firestore necesita constructor sin argumentos y `var` para deserializar; mantén el
estilo de los modelos existentes.

**b) Escribir `memberProfiles` en los tres puntos donde cambia la pertenencia**, en
`feature/household/data/repository/HouseholdRepositoryImpl.kt`:

- `createHousehold` — el creador entra en `memberProfiles` a la vez que en `members`.
- `joinHousehold` — el que se une se añade a ambos. **Debe ir en la misma escritura**, o la
  regla de self-join (punto d) la rechazará.
- Donde se edite el nickname/displayName — hay que propagar el cambio a la casa activa.
  Búscalo con `grep -rn "nickname" app/src/main/java`.
- `leaveHousehold` — al salir, elimina también su entrada de `memberProfiles`.

**c) Auto-relleno perezoso para los datos que ya existen en producción.**

Los testers actuales tienen casas **sin** `memberProfiles`. Si cierras `/users` sin más,
verán "Desconocido" por todas partes. Solución sin script de administración: al observar la
casa, si `memberProfiles[currentUserId]` falta o no coincide con el perfil local, el propio
cliente escribe su entrada (tiene permiso: es miembro). Cada usuario se auto-rellena la
primera vez que abre la app tras actualizar.

Implementa esto en el use case que observa la casa, no en el Composable. Que sea
idempotente y que **no escriba nada si ya coincide** (si no, provocas un bucle de escrituras
y te comes la cuota de Firestore).

**d) Reglas.** En `firestore.rules`:

```
match /users/{userId} {
  allow read, write: if isSignedIn() && request.auth.uid == userId;
  // ...subcolección routines igual que ahora
}
```

Y en `isSelfJoin()`, permitir que la unión toque `members` **y** `memberProfiles`
(hoy exige `hasOnly(['members'])`, lo que rechazaría la escritura del punto b), verificando
que solo añade su propia entrada:

```
function isSelfJoin() {
  return isSignedIn()
    && !(request.auth.uid in resource.data.members)
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['members', 'memberProfiles'])
    && request.resource.data.members.hasAll(resource.data.members)
    && request.resource.data.members.size() == resource.data.members.size() + 1
    && request.auth.uid in request.resource.data.members
    && request.resource.data.memberProfiles.diff(resource.data.memberProfiles)
         .affectedKeys().hasOnly([request.auth.uid]);
}
```

**e) Leer de `memberProfiles` en lugar de `/users`.** `getMemberProfiles`
(`HouseholdRepositoryImpl.kt:188`) hace hoy una lectura punto a punto por miembro. Pasa a
resolverlos desde el doc de la casa, que ya tienes cargado. Además de cerrar el agujero,
**elimina N lecturas de Firestore por pantalla**.

Ficheros que consumen nombres de miembros y hay que revisar (salen de `grep -rn
"getMemberProfiles\|completedByName\|assignedToName"`):

- `feature/household/domain/repository/HouseholdRepository.kt`
- `feature/household/domain/usecase/HouseholdUseCases.kt`
- `feature/household/data/repository/HouseholdRepositoryImpl.kt`
- `feature/household/presentation/HouseholdViewModel.kt`
- `feature/household/presentation/HouseholdScreen.kt`
- `feature/routines/presentation/RoutinesViewModel.kt`
- `feature/routines/presentation/RoutinesScreen.kt`
- `feature/routines/presentation/add/AddRoutineViewModel.kt`

### Aceptación

- `/users/{otro-uid}` devuelve `PERMISSION_DENIED` para un usuario que no es su dueño.
- Con dos cuentas y dos dispositivos (o dos emuladores): la cuenta A ve el nombre de la
  cuenta B en rutinas de casa y en la pantalla "Mi Casa", igual que antes del cambio.
- Una casa creada **antes** del cambio muestra los nombres correctamente después de que
  cada usuario abra la app una vez.
- Tests unitarios nuevos sobre el use case de auto-relleno: no escribe si el perfil ya
  coincide; escribe si falta o difiere.

### Riesgo y orden de despliegue

**Las reglas y la app deben desplegarse coordinadas.** Si publicas las reglas antes que la
app, las versiones antiguas instaladas dejan de resolver nombres. Con 12 testers en beta
cerrada el orden correcto es:

1. Publicar la app con escritura dual y lectura con *fallback* a `/users`.
2. Esperar a que los testers actualicen (Play Console te lo dice).
3. Publicar las reglas cerradas y retirar el *fallback* en la siguiente versión.

Si prefieres hacerlo en un solo paso, es aceptable en beta: el único síntoma es que el
nombre de un tester no aparece para los demás hasta que ese tester abre la app una vez.
**Decide cuál de las dos vías sigues y anótalo en el commit.**

---

## S3 — Rol de propietario de la casa

### Problema

[`firestore.rules:71`](firestore.rules) — `allow delete: if isMemberSelf()`: **cualquier
miembro puede borrar la casa entera**, con su historial de compra y sus rutinas. Y `update`
permite a cualquier miembro reescribir `members`, es decir, expulsar a los demás. En una app
de convivencia esto es un problema de convivencia, no solo técnico.

### Cambio

- `Household` gana `ownerId: String`. En `createHousehold` se rellena con el creador.
- **Retrocompatibilidad**: las casas existentes tienen `ownerId` vacío. Trata el
  `ownerId` en blanco como "el primer elemento de `members`" en el cliente, y rellénalo con
  el mismo auto-relleno perezoso de S2 (solo puede hacerlo quien coincida con
  `members[0]`).
- Reglas: `delete` solo si `request.auth.uid == resource.data.ownerId`. Para `update`,
  permitir que un miembro se quite **a sí mismo** de `members` (salir de la casa) pero que
  solo el propietario pueda quitar a otros.
- UI: en "Mi Casa", el botón de borrar casa solo visible para el propietario, y la opción de
  expulsar miembro solo para él. Los demás ven "Salir de la casa".

### Aceptación

- Un miembro no propietario recibe `PERMISSION_DENIED` al intentar borrar la casa.
- Un miembro no propietario puede salirse solo.
- Una casa creada antes del cambio sigue funcionando y acaba con `ownerId` relleno.

---

## S4 — Caducidad y rotación de códigos de invitación

### Problema

[`firestore.rules:43`](firestore.rules) — `/invite_codes/{code}` tiene `allow get` para
cualquier autenticado, los códigos son de 6 caracteres
([`HouseholdRepositoryImpl.kt:311`](app/src/main/java/com/monsteraltech/habitly/feature/household/data/repository/HouseholdRepositoryImpl.kt))
y **no caducan nunca**. Quien salga de la casa puede volver a entrar cuando quiera con el
código que memorizó.

### Cambio

- `registerInviteCode` (`HouseholdRepositoryImpl.kt:93`) añade `expiresAt` al documento
  (sugerencia: 7 días).
- Regla de `get` sobre `/invite_codes/{code}`: exigir
  `resource.data.expiresAt > request.time.toMillis()`.
- `leaveHousehold` y la expulsión de un miembro **rotan el código automáticamente**
  (ya existe la lógica de rotación, busca `generateUniqueInviteCode` en la línea 244).
- UI en "Mi Casa": mostrar cuándo caduca el código y un botón de regenerar. Ya hay
  regeneración manual; solo falta exponer la caducidad.

### Aceptación

- Un código de más de 7 días devuelve `PERMISSION_DENIED` al resolverlo.
- Al salir un miembro, el código anterior deja de funcionar.
- El mensaje de error en la pantalla de unirse distingue "código caducado" de "código
  incorrecto" (usa `strings.xml`).

---

## S5 — Firebase App Check

### Problema

El `google-services.json` viaja dentro del APK: es público por diseño. Sin App Check,
cualquiera puede extraerlo, registrarse por la API de Auth y hablar directamente con tu
Firestore desde un script, saltándose la app. Las reglas siguen protegiendo *los datos
ajenos*, pero no impiden que te quemen la cuota.

### Cambio

- Añadir la dependencia `firebase-appcheck-playintegrity` (con
  `firebase-appcheck-debug` para `debugImplementation`).
- Inicializar App Check en `HabitlyApp` con el proveedor de Play Integrity en release y el
  de debug en debug.
- Registrar la app en la consola de Firebase (App Check → Play Integrity).

### Aceptación

- **Déjalo en modo "no obligatorio" (unenforced) durante la beta.** Activar el enforcement
  con testers ya instalados puede dejarlos fuera. Verifica en la consola que llegan
  peticiones verificadas antes de forzarlo.
- Documenta en `GUIA_PLAY_CONSOLE.md` el paso de activar el enforcement tras la beta.

> Este paso toca la consola de Firebase, que es una acción fuera del repositorio.
> **Prepara el código y deja escritos los pasos de consola, pero no los ejecutes tú**:
> que los haga Dani.

---

## S6 — Activar R8 y automatizar el `versionCode`

**Va el último a propósito**, pero con reglas `-keep` amplias para que el trabajo de los
planes 2 y 3 quede cubierto sin volver a tocarlo.

### Problema

[`app/build.gradle.kts:53`](app/build.gradle.kts) — `isMinifyEnabled = false`. El release
sale sin ofuscar ni encoger: APK mucho mayor de lo necesario y lógica legible con cualquier
decompilador. Además `versionCode = 1` y `versionName = "1.0"`, cuando el asistente ya va
por 0.14.0; Play rechaza subir dos veces el mismo `versionCode`.

### Cambio

En `app/build.gradle.kts`:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    // ...
}
```

En `app/proguard-rules.pro`, reglas **a nivel de paquete** (no clase por clase), para que
los modelos que añadan los siguientes planes queden cubiertos automáticamente:

```proguard
# Firestore y Gson deserializan por reflexión: los modelos no se pueden renombrar
# ni encoger. Regla por paquete a propósito, para cubrir los modelos que se añadan
# después sin tener que volver aquí.
-keep class com.monsteraltech.habitly.**.domain.model.** { *; }
-keep class com.monsteraltech.habitly.**.data.source.local.** { *; }
-keepclassmembers class com.monsteraltech.habitly.** {
    @com.google.firebase.firestore.PropertyName *;
}

# Conserva las firmas genéricas: sin ellas Gson resuelve List<T> como List<Object>.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Trazas de crash legibles en Play Console.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
```

Revisa además las reglas que requieran LiteRT-LM, Room y Hilt; consulta su documentación si
la app peta al arrancar en release.

Para el `versionCode`, léelo de una propiedad o derívalo del `versionName` para que no haya
que acordarse de subirlo a mano en cada publicación.

### Aceptación

**Esta es la parte crítica, no la saltes.** Un `assembleRelease` que compila no demuestra
nada: R8 rompe *en tiempo de ejecución*.

```bash
./gradlew assembleRelease
```

Instala el APK de release en un dispositivo y recorre a mano:

1. Login con Google y con email
2. Crear casa / unirse con código
3. Lista de la compra: añadir, tachar, archivar
4. Despensa
5. Rutinas: crear, completar, racha, recordatorio
6. Asistente de IA: **descargar modelo y enviar un mensaje** (es el más frágil bajo R8,
   por la deserialización de las respuestas del modelo)
7. Widget en la pantalla de inicio

Cualquier `ClassNotFoundException`, `NoSuchMethodError` o campo que llegue nulo es una regla
`-keep` que falta.

---

## Estado

| | Tarea | Estado |
|---|---|---|
| S1 | SHA-256 de los modelos | ✅ hecha |
| S2 | Cerrar `/users` + `memberProfiles` | ✅ código hecho — **reglas SIN desplegar** |
| S3 | Rol de propietario | ✅ código hecho — **reglas SIN desplegar** |
| S4 | Caducidad de códigos | ✅ código hecho — **reglas SIN desplegar** |
| S5 | App Check | ✅ código hecho — **pasos de consola SIN hacer** |
| S6 | R8 + versionCode | ✅ código hecho — **sin recorrer en dispositivo** |

343 tests unitarios en verde. `assembleDebug` y `assembleRelease` correctos; el APK de release
sale firmado, con `versionCode 10000` / `versionName 1.0.0`, y pasa de 130,5 MB a **52,9 MB**.

Verificación estática de R8 hecha sobre `mapping.txt` y el DEX: siguen sin renombrar los modelos
de `domain.model`, el paquete `com.google.ai.edge.litertlm` y `RoutineProposalTools`, y en la
tabla de strings del DEX sobreviven `addRoutine`, `title`, `frequency`, las descripciones de
`@Tool`/`@ToolParam` y `Lkotlin/Metadata;` — que es lo que lee kotlin-reflect para construir el
esquema de la tool. **Esto no sustituye al recorrido a mano del apartado "Aceptación" de S6**,
que sigue pendiente.

---

## Notas para los siguientes planes

### Cambios de firma (te afectan si tocas estos ficheros)

- **`Household`** gana `ownerId`, `inviteCodeExpiresAt`, `memberProfiles: Map<String,
  MemberProfile>`, y los helpers `ownerOrFallback` / `isOwner(userId)`.
- **`GetMemberProfilesUseCase`** ya no hace I/O: `invoke(household: Household):
  List<UserProfile>`, sin `suspend` ni `Result`. Los nombres salen del documento de la casa
  que ya tienes cargado. **No vuelvas a leer `/users` de otro usuario: está prohibido por
  reglas.**
- **`UpdateNicknameUseCase`**: `invoke(userId, householdId, newNickname)`.
- **`RemoveMemberUseCase`**: `invoke(household, requesterId, memberId)`; falla si el
  solicitante no es el propietario.
- **`HouseholdUiState`** gana `currentUserId` y la propiedad derivada `isOwner`.
- Nuevos: `SyncOwnMemberProfileUseCase`, `BackfillHouseholdOwnerUseCase`,
  `InvalidInviteCodeException`.
- `FakeHouseholdRepository` (tests) registra ahora `syncedProfiles`, `ownershipClaims` y
  `removedMembers`; úsalos para afirmar que **no** se escribe de más.

### Tolerancia a datos incompletos (importante para la UI)

Durante la migración, un miembro que aún no haya abierto la app **no tiene entrada en
`memberProfiles`**. `GetMemberProfilesUseCase` lo devuelve igualmente, con los nombres en
blanco. Toda UI que pinte nombres de miembros debe tener un texto de reserva
(`R.string.household_member_unknown` o equivalente), nunca una fila vacía.

### Deuda que hereda el plan de usabilidad

Los repositorios lanzan excepciones con **mensajes literales en español** que la UI muestra
tal cual (`uiState.joinError`, `uiState.error`). En inglés se ven en español. No lo he
arreglado porque es un refactor transversal de manejo de errores que encaja mejor con el
trabajo de textos y estados de U6/U7. `InvalidInviteCodeException` sigue ese mismo patrón
para no dejarlo a medias.

### Estado de R8

**S6 ya está hecha**: `isMinifyEnabled = true` e `isShrinkResources = true` en el release.
Las reglas `-keep` de `app/proguard-rules.pro` son **por paquete**
(`**.domain.model.**`, `**.data.source.local.**`), así que los modelos que añadáis quedan
cubiertos solos. Dos cosas que sí os afectan:

- Si creáis un modelo que Firestore, Gson o Room deserialicen **fuera** de esos dos paquetes,
  añadidle su propia regla o los campos os llegarán a null en release sin ningún error.
- El `versionCode` se deriva del `versionName` en `app/build.gradle.kts`
  (`habitlyVersionName`, `MAJOR*10000 + MINOR*100 + PATCH`). No pongáis un `versionCode`
  literal.

### Estado de App Check (S5)

El código está puesto y es transparente para vosotros, pero App Check queda en modo
**no aplicado** durante la beta. Si en algún momento veis `PERMISSION_DENIED` masivo en
Firestore sin haber tocado las reglas, comprobad si alguien activó el *enforcement* en la
consola antes de tiempo.

### Pasos fuera del repositorio, pendientes para Dani

1. **Desplegar `firestore.rules`.** Sin esto, S2/S3/S4 son solo cambios de cliente y el
   agujero sigue abierto.
2. **Aviso de ruptura**: al desplegar, los códigos de invitación existentes (sin
   `expiresAt`) dejan de resolverse. Cada casa debe regenerar el suyo desde "Mi Casa". La
   pantalla ya avisa con "Este código ya no sirve".
3. Decidir la vía de despliegue de S2 (dos versiones vs. una sola) — ver la sección "Riesgo
   y orden de despliegue" de S2.
4. **App Check en la consola de Firebase** (S5): vincular Play Console, registrar Play
   Integrity, dar de alta el token de depuración y **dejarlo en modo no aplicado** hasta que
   acabe la beta. Los cuatro pasos están escritos en `GUIA_PLAY_CONSOLE.md`.
5. **Recorrer el APK de release en un móvil real** (S6): login, casa, compra, despensa,
   rutinas, asistente de IA (descarga del modelo + un mensaje) y widget. El asistente es el
   punto frágil: es el único camino que depende de kotlin-reflect en tiempo de ejecución.
