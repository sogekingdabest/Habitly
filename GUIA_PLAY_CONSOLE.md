# Guía de configuración de Play Console — Habitly

Respuestas concretas para las 11 tareas de **"Termina de configurar tu aplicación"**.
Todo está verificado contra el código real de la app (manifest, dependencias, política de
privacidad publicada), no son respuestas genéricas.

**Datos base de la app**

| Dato | Valor |
|---|---|
| applicationId | `com.monsteraltech.habitly` |
| versionCode / versionName | `10000` / `1.0.0` — el `versionCode` se **deriva** del `versionName` en `app/build.gradle.kts` (`MAJOR*10000 + MINOR*100 + PATCH`). Para publicar, cambia solo `habitlyVersionName`. |
| minSdk / targetSdk | 29 / 36 |
| Permisos | `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` |
| SDKs de terceros | Firebase Auth, Cloud Firestore, Google Play Services (Credential Manager), LiteRT-LM |
| Publicidad / analítica | **Ninguna** (sin AdMob, sin Analytics, sin Crashlytics) |
| Idiomas | es (por defecto), en, gl |
| Contacto público | daniolanetafarina@protonmail.com |

> ⚠️ **Antes de nada:** si tu cuenta de desarrollador es **personal** y se creó después de
> noviembre de 2023, Google exige una **prueba cerrada con 12 testers como mínimo, opt-in
> durante 14 días seguidos**, antes de poder solicitar acceso a producción. Esto no aparece
> en esta lista de tareas pero bloquea la publicación. Empieza el test cerrado cuanto antes,
> porque el reloj de los 14 días corre en paralelo a todo lo demás.

---

## 1. Establece la política de privacidad

**Campo único → URL:**

```
https://sogekingdabest.github.io/habitly-legal/privacidad.html
```

✅ Verificado: la página carga públicamente (GitHub Pages ya está activo).

---

## 2. Datos de inicio de sesión (App access)

Habitly exige cuenta para todo, así que **no** puedes marcar "todas las funciones están
disponibles sin restricciones". Marca **"Todas o algunas funciones tienen acceso restringido"**
y añade una instrucción.

**Antes de rellenarlo:** crea una cuenta de prueba real en Firebase Authentication
(email/contraseña, *no* de Google — el revisor no puede usar Google Sign-In) con datos ya
cargados: una casa, 2-3 rutinas y algo en la lista de la compra.

| Campo | Valor |
|---|---|
| Nombre de las instrucciones | `Cuenta de prueba (email y contraseña)` |
| Nombre de usuario | `review@habitly.app` *(el que crees)* |
| Contraseña | *(la que crees)* |
| ¿Se necesitan otras instrucciones? | Sí → texto de abajo |

**Texto de "otras instrucciones"** (cópialo tal cual):

```
La app requiere iniciar sesión. Usa el email y la contraseña indicados en la pestaña
"Correo electrónico / Contraseña" (no uses "Continuar con Google": el inicio de sesión
con Google requiere una cuenta real de Google).

La cuenta de prueba ya tiene una casa creada con rutinas y lista de la compra, así que
todas las pantallas principales se pueden revisar directamente tras iniciar sesión.

El asistente de IA es OPCIONAL y funciona 100% en el dispositivo: al abrirlo por primera
vez ofrece descargar un modelo de lenguaje de ~2,6 GB desde Hugging Face. No es necesario
descargarlo para revisar el resto de la app. Si quieres probarlo, la descarga necesita
Wi-Fi y unos minutos, y requiere un dispositivo con al menos 6 GB de RAM.
```

---

## 3. Anuncios

**Respuesta: No, mi aplicación no contiene anuncios.**

✅ Verificado en `app/build.gradle.kts`: no hay AdMob ni ningún SDK de publicidad.
Coherente con lo que dice la política de privacidad ("no incorpora analítica de uso ni
publicidad").

---

## 4. Clasificación de contenido (cuestionario IARC)

- **Correo de contacto:** daniolanetafarina@protonmail.com
- **Categoría:** *Utilidad, productividad, comunicación u otros*

Respuestas del cuestionario:

| Sección | Respuesta |
|---|---|
| Violencia (toda la sección) | No |
| Sexualidad | No |
| Lenguaje soez | No |
| Sustancias controladas (drogas, alcohol, tabaco) | No |
| Juegos de azar / apuestas | No |
| Compras digitales | No |
| ¿Comparte la ubicación del usuario? | **No** (la app no pide permisos de ubicación) |
| ¿Permite a los usuarios interactuar o intercambiar contenido? | **Sí** |
| ¿Permite compartir datos personales con terceros? | No |
| ¿Incluye funciones de IA generativa? | **Sí** |

**Por qué "Sí" en interacción:** al unirse a una casa compartida mediante código de
invitación, los miembros ven el apodo de los demás y comparten rutinas, listas y despensa.
No hay chat entre usuarios ni contenido público: es un grupo cerrado por invitación.
Si el formulario pregunta si el contenido es público o moderado, indica que **solo es
visible para los miembros invitados de esa casa**.

**Por qué "Sí" en IA generativa:** el asistente usa un modelo Gemma que se ejecuta en el
dispositivo. Si te preguntan por las salvaguardas, la app **ya cumple** el requisito clave
de Google: incluye un mecanismo en la app para **reportar respuestas ofensivas** del
asistente (implementado en el commit `86c70d1`). Menciónalo si hay campo de texto libre.

---

## 5. Audiencia objetivo

- **Grupos de edad:** solo **18 años o más**.
- **¿Podría tu app atraer a niños de forma no intencionada?** → **No**.

Esto es coherente con los documentos legales: ya he actualizado `privacidad.html` y
`terminos.html` de `habitly-legal` para que digan 18 años (antes ponía 14). **Recuerda
hacer commit y push de ese repo**, porque Google contrasta la ficha con la política.

Al declarar solo 18+ te ahorras la Política de Familias y el escrutinio extra que Google
aplica a apps con IA generativa accesibles a menores. A cambio: la ficha de Play (icono,
gráficos, descripción) no puede tener aspecto infantil — el diseño actual (brote, verde
salvia, tono adulto) cumple sin problema.

---

## 6. Seguridad de los datos (Data safety)

La sección más delicada. Reglas de Google que aplican a tu caso:

- Enviar datos a **Firebase cuenta como "recopilados"** (salen del dispositivo), pero **no
  como "compartidos"**: Firebase es un encargado del tratamiento que actúa por ti.
- Que otros miembros de tu casa vean tu contenido **no** es "compartir" en el sentido de
  Play (eso se refiere a terceras empresas).
- Lo que se procesa **solo en el dispositivo y no sale de él NO se declara**. Por eso las
  conversaciones con el asistente de IA **no** se declaran.

### Preguntas generales

| Pregunta | Respuesta |
|---|---|
| ¿Tu app recopila o comparte alguno de los tipos de datos obligatorios? | **Sí** |
| ¿Se cifran todos los datos en tránsito? | **Sí** (HTTPS/TLS con Firebase) |
| ¿Pueden los usuarios solicitar la eliminación de sus datos? | **Sí** |
| URL de eliminación de datos | `https://sogekingdabest.github.io/habitly-legal/eliminar-cuenta.html` |
| ¿Tu app usa el identificador de publicidad (AD_ID)? | **No** |
| Validación independiente de seguridad | No |

### Tipos de datos a declarar

Todos con **Recopilados: Sí / Compartidos: No / Cifrados en tránsito: Sí / Se pueden eliminar: Sí**.

| Categoría → Tipo | Obligatorio u opcional | Finalidades |
|---|---|---|
| **Información personal → Nombre** (apodo y nombre para mostrar) | Obligatorio | Funciones de la app, Gestión de cuentas |
| **Información personal → Dirección de correo electrónico** | Obligatorio | Funciones de la app, Gestión de cuentas |
| **Información personal → IDs de usuario** (UID de Firebase) | Obligatorio | Funciones de la app, Gestión de cuentas |
| **Actividad en la app → Otro contenido generado por el usuario** (rutinas, listas de la compra, despensa, nombre de la casa) | Obligatorio | Funciones de la app |
| **Mensajes → Otros mensajes dentro de la app** (solo respuestas de IA que el usuario reporta) | **Opcional** | Prevención de fraudes, seguridad y cumplimiento normativo |

### Qué NO declarar (y por qué)

| No declares | Motivo |
|---|---|
| Conversaciones con el asistente de IA | Se guardan solo en el dispositivo (Room) y están excluidas del backup en la nube. Solo sale del dispositivo lo que el usuario reporta expresamente → ya declarado como "Mensajes / opcional". |
| Audio o voz | El dictado abre el reconocedor del sistema Android; tu app solo recibe el texto ya transcrito, nunca el audio. |
| Ubicación | La app no pide ni usa permisos de ubicación. |
| Registros de fallos / diagnósticos | No hay Crashlytics ni Analytics. |
| Descarga del modelo desde Hugging Face | Es una descarga de un fichero; no envía datos del usuario. |

---

## 7. Aplicaciones gubernamentales

**Respuesta: No.** Habitly no está desarrollada por ni en nombre de un organismo público.

---

## 8. Funciones financieras

**Respuesta: "Mi app no ofrece funciones financieras"** (ninguna de las opciones).

Ojo con la tentación de marcar algo por la lista de la compra o la despensa: no hay pagos,
préstamos, criptomonedas, seguros ni gestión de dinero. No es una función financiera.

---

## 9. Salud

**Respuesta: "Ninguna de las anteriores"** / mi app no tiene funciones de salud.

Justificación: Habitly gestiona **tareas del hogar** (rutinas domésticas, limpieza, compra,
despensa). No registra datos de salud ni fitness, no da consejo médico y no es una app de
salud mental. El asistente de IA está acotado por su prompt de sistema a *"gestión del hogar:
rutinas, recetas para la lista de la compra y consejos de limpieza"*.

> 🔸 **Consecuencia para la ficha:** para que esta respuesta siga siendo coherente, **no
> vendas la app como "bienestar", "hábitos saludables" o "salud"** en el título ni en la
> descripción. Los textos que te propongo más abajo ya evitan ese vocabulario a propósito.

---

## 10. Categoría de la app y datos de contacto

| Campo | Valor |
|---|---|
| Tipo de aplicación | Aplicaciones |
| Categoría | **Productividad** *(alternativa razonable: Estilo de vida)* |
| Etiquetas (hasta 5) | Listas de tareas · Organización personal · Notas y listas · Colaboración · Hogar |
| Correo electrónico | daniolanetafarina@protonmail.com |
| Sitio web | `https://sogekingdabest.github.io/habitly-legal/` |
| Teléfono | *(opcional, puedes dejarlo vacío)* |

**Por qué Productividad:** el núcleo de la app son listas y rutinas compartidas, que compite
con organizadores y listas de tareas. "Estilo de vida" es válido si prefieres posicionarte
como app de hogar, pero tiene más ruido y peor intención de búsqueda.

El email es **público** en la ficha: por eso uso el de Proton que ya aparece en los
documentos legales, no tu Gmail personal.

---

## 11. Configura la Ficha de Play Store

### Textos (idioma por defecto: español – España)

**Nombre de la aplicación** (máx. 30 caracteres) — *26 caracteres*:

```
Habitly: rutinas del hogar
```

**Descripción breve** (máx. 80 caracteres) — *77 caracteres*:

```
Rutinas, lista de la compra y despensa para toda la casa, con asistente de IA
```

**Descripción completa** (máx. 4000 caracteres):

```
Habitly organiza la casa en un solo sitio: las rutinas de cada día, la lista de la compra y
lo que tienes en la despensa. Todo sincronizado con las personas con las que vives.

RUTINAS COMPARTIDAS
Crea rutinas y tareas del hogar, decide cada cuánto se repiten y reparte a quién le toca.
Marca lo que vas completando y mantén la racha viva. Con recordatorios opcionales para que
no se te pase nada.

LISTA DE LA COMPRA EN EQUIPO
Una lista única para toda la casa que se actualiza al momento. Si tu pareja añade leche
mientras estás en el supermercado, la ves aparecer. Con historial de lo que compráis
habitualmente para no empezar de cero cada semana.

DESPENSA
Apunta lo que tienes en casa y deja de comprar la tercera botella de aceite. Pasa productos
de la despensa a la lista de la compra con un toque.

ASISTENTE DE IA QUE FUNCIONA SIN CONEXIÓN
Habitly incluye un asistente que se ejecuta ÍNTEGRAMENTE EN TU MÓVIL. Pídele ideas de
rutinas, ayuda para planificar la semana o que añada la compra de una receta a tu lista.
Tus conversaciones no viajan a ningún servidor: se quedan en tu dispositivo.
El asistente es opcional y requiere descargar el modelo una sola vez.

WIDGET EN LA PANTALLA DE INICIO
Consulta las rutinas de hoy y la lista de la compra sin abrir la app.

PENSADA PARA CONVIVIR
Crea una casa, invita con un código y cada miembro ve lo mismo al instante. Sirve igual
para una pareja, una familia o un piso compartido.

ADEMÁS
- Modo claro y oscuro
- Disponible en español, inglés y gallego
- Dictado por voz para añadir cosas rápido
- Sin anuncios y sin rastreadores

Habitly guarda tu cuenta y el contenido de tu casa en Firebase para poder sincronizarlos
entre los miembros. Puedes borrar tu cuenta y tus datos en cualquier momento desde la
propia app.
```

### Recursos gráficos

| Recurso | Requisito de Play | Estado |
|---|---|---|
| Icono | 512 × 512 px, PNG de 32 bits, **sin transparencia**, máx. 1 MB | ✅ `play-store/icon-512.png` |
| Gráfico destacado | 1024 × 500 px, PNG o JPEG, sin transparencia | ✅ `play-store/feature-graphic-1024x500.png` |
| Capturas de teléfono | Mín. **2** (sube 4-8), lado entre 320 y 3840 px, ratio 16:9 o 9:16 | ⬜ **Te toca** |
| Capturas de tablet | Opcional, pero evita el aviso de "no optimizada para tablets" | ⬜ Opcional |

Ambos gráficos están generados con la paleta real de la app (Sage `#5F8F82`, Cream
`#F9FBF6`, mesh gradient del fondo) y con el mismo brote del icono del launcher. Las
fuentes de marca son Baloo 2 y Nunito, las mismas que usa la app. Si quieres retocarlos,
las fuentes están en `play-store/src/*.html`; se regeneran con el comando que hay al final.

**Capturas recomendadas** (4, en este orden):

1. Mi Casa con rutinas del día y la racha
2. Lista de la compra con varios productos
3. Chat con el asistente de IA proponiendo una rutina
4. Despensa o el widget en la pantalla de inicio

Para capturarlas del móvil conectado por USB:

```bash
adb exec-out screencap -p > play-store/captura-1.png
```

### Traducciones de la ficha

La app está traducida a **inglés y gallego**. Merece la pena añadir esas dos fichas en Play
(Play Console → Ficha principal de la tienda → Gestionar traducciones). Si vas justo de
tiempo, con el inglés basta para el lanzamiento: multiplica el alcance mucho más que el
gallego.

---

## Regenerar los gráficos

```bash
"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless=new --disable-gpu --force-device-scale-factor=1 --hide-scrollbars --virtual-time-budget=6000 --screenshot="play-store/feature-graphic-1024x500.png" --window-size=1024,500 "file:///C:/Users/Dani/Documents/Proyectos/Android%20Studio/play-store/src/feature-graphic-1024x500.html"
```

---

## Firebase App Check (pasos de consola — te tocan a ti)

El código ya está: `installAppCheck()` se llama en `HabitlyApp.onCreate()`, con Play Integrity en
release y el proveedor de depuración en debug (`app/src/{release,debug}/java/.../AppCheckInstaller.kt`).
Lo que falta es todo de consola.

### 1. Vincular Play Console con Firebase (requisito de Play Integrity)

Firebase Console → ⚙️ Configuración del proyecto → **Integraciones** → Google Play → *Vincular*.
Sin este paso, Play Integrity no puede verificar los veredictos y App Check rechazará todos los
tokens de release.

### 2. Registrar la app en App Check

Firebase Console → **Compilación → App Check** → pestaña *Apps* → `com.monsteraltech.habitly`
→ **Play Integrity** → *Guardar*.

### 3. Registrar el token de depuración (para poder seguir desarrollando)

Arranca una vez el build de debug y busca en Logcat, con la etiqueta `DebugAppCheckProvider`,
una línea del tipo `Enter this debug secret into the allow list...` seguida de un UUID:

```bash
adb logcat -s DebugAppCheckProvider
```

Copia ese UUID en App Check → la app Android → menú ⋮ → **Gestionar tokens de depuración** →
*Añadir token*. El secreto es por instalación: cambia si desinstalas la app o cambias de móvil.

### 4. ⚠️ NO actives el enforcement todavía

En App Check → pestaña *APIs*, **Cloud Firestore** y **Authentication** deben quedarse en
**"No aplicado" (unenforced)** durante toda la beta cerrada.

Activarlo ahora echaría fuera a cualquier tester que siga con una versión anterior de la app
(la que no manda token), y el síntoma sería `PERMISSION_DENIED` en todas las pantallas, sin
forma de que el tester lo arregle por su cuenta.

**Cuándo activarlo:** cuando el panel de App Check muestre que ≥ 95 % de las peticiones llegan
**verificadas** durante varios días seguidos. Ese porcentaje es la prueba de que ya no queda
nadie con una versión antigua instalada. Entonces sí: *Aplicar* en Firestore y en Authentication.

---

## Checklist de cierre

- [ ] Commit y push de `habitly-legal` con el cambio de 14 → 18 años
- [ ] Crear la cuenta de prueba en Firebase Auth y cargarle datos
- [ ] Capturar 4 screenshots del móvil
- [ ] Rellenar las 11 tareas con las respuestas de este documento
- [ ] Subir el `.aab` firmado (ver `GUIA_FIRMA_Y_SHA1.md`)
- [ ] Añadir el SHA-1 de la clave de firma de Play en Firebase (si no, Google Sign-In falla en producción)
- [ ] Vincular Play Console con Firebase y registrar App Check en modo **no aplicado** (sección de arriba)
- [ ] Registrar el token de depuración de App Check para el móvil de desarrollo
- [ ] Desplegar `firestore.rules` (S2/S3/S4 de `PLAN_SEGURIDAD.md` no protegen nada hasta entonces)
- [ ] Arrancar la prueba cerrada con 12 testers (14 días)
- [ ] **Tras la beta:** activar el *enforcement* de App Check en Firestore y Authentication
