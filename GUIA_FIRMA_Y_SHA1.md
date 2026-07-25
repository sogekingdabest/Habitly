# Guía: firma del release y SHA-1 de Play App Signing

Guía paso a paso para firmar Habitly y dejar el login con Google funcionando en la build de
Play. Pensada para Windows (PowerShell) con el JDK que trae Android Studio.

Valores del proyecto (ya fijados en el código):

| Dato | Valor |
|---|---|
| `applicationId` | `com.monsteraltech.habitly` |
| Alias de la clave | `habitly` |
| Fichero del keystore | `habitly-upload.jks` (en la raíz del proyecto) |
| Fichero de credenciales | `keystore.properties` (raíz, **ignorado por git**) |

> El `build.gradle.kts` ya está preparado: si existe `keystore.properties`, el release se firma
> solo; si no, sale sin firmar. No hay que tocar Gradle.

---

## Parte 1 · Crear el keystore de subida (una sola vez)

El *keystore* es el fichero que te identifica como autor de la app. Se crea una vez y **se
reutiliza para siempre**.

1. Abre PowerShell en la raíz del proyecto:

   ```powershell
   cd "C:\Users\Dani\Documents\Proyectos\Android Studio"
   ```

2. Genera el keystore (`keytool` viene con Android Studio):

   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore habitly-upload.jks -alias habitly -keyalg RSA -keysize 2048 -validity 10000
   ```

3. Te preguntará, en este orden:
   - **Contraseña del keystore** (elígela y apúntala; te la volverá a pedir para confirmar).
   - **Nombre y apellidos, unidad, organización, ciudad, provincia, código de país** — puedes
     dejarlos en blanco pulsando Enter, o rellenarlos. Al final escribe `sí` (o `yes`) para confirmar.
   - **Contraseña de la clave (`habitly`)**: pulsa Enter para usar la misma que el keystore
     (lo más sencillo), o pon una distinta.

Al terminar tendrás `habitly-upload.jks` en la raíz del proyecto.

> ⚠️ **Guarda a buen recaudo el `.jks` y las contraseñas** (gestor de contraseñas + copia del
> fichero en un sitio seguro que no sea este repo). Es tu llave de subida a Play; si la pierdes,
> se puede resetear desde Play Console, pero es un incordio. **Nunca** subas el `.jks` a git
> (ya está en `.gitignore`).

---

## Parte 2 · Rellenar `keystore.properties`

1. Copia la plantilla:

   ```powershell
   Copy-Item keystore.properties.example keystore.properties
   ```

2. Abre `keystore.properties` y pon tus valores reales:

   ```properties
   storeFile=../habitly-upload.jks
   storePassword=TU_CONTRASEÑA_DEL_KEYSTORE
   keyAlias=habitly
   keyPassword=TU_CONTRASEÑA_DE_LA_CLAVE
   ```

   - `storeFile` es relativo al módulo `:app`, por eso el `../` (el `.jks` está en la raíz).
   - Si en el paso anterior pusiste la misma contraseña para clave y keystore, repite la misma
     en `keyPassword`.

Este fichero está ignorado por git: no se subirá nunca.

---

## Parte 3 · Generar el AAB firmado

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:bundleRelease
```

El resultado firmado queda en:

```
app\build\outputs\bundle\release\app-release.aab
```

Ese `.aab` es lo que subes a Play Console (Internal testing o Closed testing).

> Comprobación rápida de que salió firmado: el comando termina en `BUILD SUCCESSFUL` y el
> fichero existe. Play también te avisará al subirlo si algo no cuadra con la firma.

---

## Parte 4 · Entender Play App Signing (importante para el SHA-1)

Cuando subes tu primer AAB, Play activa **Play App Signing**. A partir de ahí hay **dos claves**:

| Clave | Quién la tiene | Para qué |
|---|---|---|
| **Upload key** | Tú (`habitly-upload.jks`) | Firma lo que **subes** a Play |
| **App signing key** | Google (la custodia) | Firma lo que **descargan los usuarios** |

Es decir: Google **re-firma** tu app con *su* clave antes de distribuirla. Por eso el login con
Google necesita conocer el SHA-1 de **esa** clave (la de Google), no solo la tuya. Este es el
motivo por el que el login con Google suele fallar en la primera build de Play si no añades el
SHA-1: la app que instala el usuario está firmada con una clave que Firebase todavía no conoce.

---

## Parte 5 · Añadir los SHA-1 a Firebase (login con Google)

### 5.1 · Sube primero el AAB

Para que Play te muestre el SHA-1 de la *app signing key*, primero tienes que:

1. Crear la app en **Play Console** (`com.monsteraltech.habitly`).
2. Subir el `app-release.aab` a una pista (Internal testing vale y es lo más rápido).

### 5.2 · Copia los SHA-1 desde Play Console

En Play Console → tu app → **Test and release → Setup → App integrity** (antes "App signing"),
verás dos certificados. Copia el **SHA-1** de cada uno:

- **App signing key certificate** → SHA-1  ← *el importante* (con el que Google re-firma)
- **Upload key certificate** → SHA-1

### 5.3 · Pégalos en Firebase

1. [Firebase Console](https://console.firebase.google.com) → tu proyecto → ⚙️ **Configuración
   del proyecto** → pestaña **General**.
2. Baja a **Tus apps** → la app Android `com.monsteraltech.habitly`.
3. **Agregar huella digital** → pega el SHA-1 → **Guardar**.
4. Repite con el segundo SHA-1 (añade los dos).

### 5.4 · Actualiza `google-services.json`

Tras añadir las huellas:

1. En esa misma pantalla, **descarga el `google-services.json` actualizado**.
2. Sustituye el que hay en `app/google-services.json` por el nuevo.
3. Vuelve a generar el AAB (Parte 3) y súbelo de nuevo.

Con eso, el login con Google funciona en la build que instalan los testers desde Play.

### 5.5 · (Opcional) SHA-1 de depuración para probar en local

Si quieres que el login con Google funcione también en las builds **debug** que instalas por
cable, añade además el SHA-1 del keystore de depuración:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Copia su SHA-1 y añádelo en Firebase igual que los otros. (Si ya te funcionaba el login con
Google en debug, es que ya lo tienes puesto.)

---

## Resumen / checklist

- [ ] `keytool -genkeypair …` → `habitly-upload.jks` creado y guardado a salvo
- [ ] `keystore.properties` rellenado (copiado del `.example`)
- [ ] `./gradlew :app:bundleRelease` → `app-release.aab` firmado
- [ ] App creada en Play Console y AAB subido a una pista de test
- [ ] SHA-1 de **app signing key** y de **upload key** copiados de Play Console
- [ ] Ambos SHA-1 añadidos en Firebase
- [ ] `google-services.json` re-descargado y sustituido en `app/`
- [ ] AAB regenerado y re-subido → login con Google probado desde Play

> Tip: para ver el SHA-1 de tu upload key sin pasar por Play Console:
> ```powershell
> & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore habitly-upload.jks -alias habitly
> ```
