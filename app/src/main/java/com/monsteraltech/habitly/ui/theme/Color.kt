package com.monsteraltech.habitly.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta **Verde niebla** de Habitly — dirección "Cozy Handcrafted".
 *
 * Fuente: proyecto de diseño *Mejora visual de app* (`Habitly App.dc.html`). La idea
 * es mantener la estructura y el comportamiento de Material 3 (accesibilidad, focus,
 * touch targets) y cambiar solo la piel: verde salvia sereno sobre papel crema, con
 * sombra teñida en verde cálido en vez de la elevación gris de Material.
 *
 * Estas son las constantes crudas. [LightColorScheme]/[DarkColorScheme] y
 * [HabitlyColors] las consumen; cualquier ajuste de marca se hace aquí y se propaga.
 */

// --- Verde salvia (marca / primario) --------------------------------------
val SageHover = Color(0xFF2F5A4E)   // verde profundo (enlaces :hover)
val SageDark = Color(0xFF487165)    // primaryDark — texto sobre contenedores claros
val SageText = Color(0xFF3F7263)    // accentText — etiquetas de acento
val Sage = Color(0xFF5F8F82)        // primary / accent — el verde de marca
val SageSoft = Color(0xFFDBE8E2)    // primarySoft — halo de iconos, contenedor primario
val SageMist = Color(0xFFD7E6DF)    // accentSoft — pastilla "Ver", contenedor secundario

// --- Superficies (papel crema + niebla) -----------------------------------
val Cream = Color(0xFFF9FBF6)       // surface / tarjeta — el "papel"
val MeshBase = Color(0xFFEAF1EC)    // fondo base del mesh gradient
val BoxIdle = Color(0xFFEAF0EC)     // surfaceVariant — casilla vacía, pistas
val MistBorder = Color(0xFFD1DDD6)  // bordes, divisores, contorno de campos

// --- Texto ----------------------------------------------------------------
val InkPrimary = Color(0xFF2F3D38)   // texto principal
val InkSecondary = Color(0xFF7F938C) // texto secundario / descripciones
val InkMuted = Color(0xFFA6B6AE)     // etiquetas apagadas, iconos de nav inactivos

// --- Manchas del mesh gradient (radiales del fondo) -----------------------
val Mesh1 = Color(0xFFCFE6DC)
val Mesh2 = Color(0xFFE9DDCA)
val Mesh3 = Color(0xFFCBE3D8)
val Mesh4 = Color(0xFFD8E8DD)
val Mesh5 = Color(0xFFEEF5F0)

// --- Racha / mostaza (badges cálidos) -------------------------------------
val StreakBg = Color(0xFFF6E6C8)   // fondo de la pastilla de racha
val StreakFg = Color(0xFFB57A1F)   // texto/emoji de racha, acento mostaza
val Mustard = Color(0xFFD99A4E)    // acento mostaza cálido (tertiary)

// --- Sombra teñida ("firma" cálida, nunca gris de Material) ---------------
// rgba(60,110,95,0.40) — verde profundo translúcido.
val WarmShadow = Color(0x663C6E5F)

// --- Error (rampa estándar de Material 3) ---------------------------------
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)

// --- Modo oscuro (Verde niebla nocturno — a partir de `Habitly App Oscuro.dc.html`) ---
// El mockup vive en una banda muy estrecha de verdes y en pantalla real todo se
// funde. Aquí se abre la jerarquía: fondo más hondo, tarjetas que suben con un
// filo claro (la sombra negra no se ve en oscuro), manchas del mesh más luminosas
// y un acento salvia que se mantiene legible con texto crema.
val SageDarkPrimary = Color(0xFF74A596)     // primary / accent — verde de marca (legible con crema)
val SageDarkPrimaryDark = Color(0xFF6BA491) // primaryDark — texto "Te toca" (más luminoso que el mockup)
val SageDarkAccent = Color(0xFF88CEBA)      // acento luminoso — enlaces, pastilla "Ver", chips
val SageDarkOnPrimary = Color(0xFFF9FBF6)   // texto/icono sobre el acento (crema)
val SageDarkContainer = Color(0xFF2A3B33)   // contenedor — halo de iconos, chips, "Te toca"
val SageDarkNavActive = Color(0xFF31473D)   // fondo de la pestaña activa (accentSoft)

val InkDarkBg = Color(0xFF0F1915)           // fondo / base del mesh gradient (más hondo → todo separa)
val InkDarkCanvas = Color(0xFF0A100D)       // el tono más hondo (lienzo tras la app)
val InkDarkSurface = Color(0xFF20302A)      // tarjeta — el "papel" en oscuro, claramente sobre el fondo
val InkDarkVariant = Color(0xFF2A3B33)      // surfaceVariant — chips, campos, segmentos
val InkDarkBoxIdle = Color(0xFF16211C)      // casilla/toggle sin marcar (un "hueco")
val InkDarkOnSurface = Color(0xFFEAF2ED)    // texto principal
val InkDarkSecondary = Color(0xFF9DB2A8)    // texto secundario / descripciones
val InkDarkMuted = Color(0xFF778A81)        // etiquetas apagadas, iconos de nav inactivos
val InkDarkBorder = Color(0xFF33443C)       // bordes, divisores, contornos de campos
val InkDarkHairline = Color(0x1AFFFFFF)     // filo claro de tarjeta (blanco ~10%) — profundidad en oscuro
val WarmShadowDark = Color(0x99000000)      // sombra en oscuro (negra translúcida, más marcada)

// Manchas del mesh gradient en oscuro (más luminosas para que el degradado se note).
val MeshDark1 = Color(0xFF224B3C)
val MeshDark2 = Color(0xFF453D22)
val MeshDark3 = Color(0xFF1B4838)
val MeshDark4 = Color(0xFF285041)
val MeshDark5 = Color(0xFF1E332C)

// Badges cálidos en oscuro (racha / mostaza).
val StreakBgDark = Color(0xFF3E3417)        // fondo de la pastilla de racha
val StreakFgDark = Color(0xFFE0AC4C)        // texto/emoji de racha, acento mostaza
