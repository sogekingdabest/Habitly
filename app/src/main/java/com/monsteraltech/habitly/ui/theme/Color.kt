package com.monsteraltech.habitly.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Habitly's "Misty Green" palette.
 *
 * Material 3 keeps its structure and behaviour (accessibility, focus, touch targets); only the
 * skin changes: sage green on cream paper, with a warm green-tinted shadow instead of Material's
 * grey elevation. Raw constants — the schemes and [HabitlyColors] consume them.
 */

// --- Sage green (brand / primary) -----------------------------------------
val SageHover = Color(0xFF2F5A4E)   // deep green (:hover links)
val SageDark = Color(0xFF487165)    // primaryDark — text on light containers
val SageText = Color(0xFF3F7263)    // accentText — accent labels
val Sage = Color(0xFF5F8F82)        // primary / accent — the brand green
val SageSoft = Color(0xFFDBE8E2)    // primarySoft — icon halo, primary container
val SageMist = Color(0xFFD7E6DF)    // accentSoft — "View" pill, secondary container

// --- Surfaces (paper + mist) ----------------------------------------------
// Paper and background used to sit at 1.1:1, so a card only existed thanks to its shadow. The
// background drops a step and the paper goes white: the card now has an edge of its own.
val Paper = Color(0xFFFFFFFF)       // surface / card — the "paper"
val Cream = Color(0xFFF9FBF6)       // text and icons on top of the accent green
val MeshBase = Color(0xFFE3ECE5)    // mesh gradient base / background
val BoxIdle = Color(0xFFEAF0EC)     // surfaceVariant — empty checkbox, hints
val MistBorder = Color(0xFFD1DDD6)  // dividers and hairlines (decorative only)
val CardBorder = Color(0xFFCBDAD2)  // card edge, so paper does not float on the background
val ControlOutline = Color(0xFF7E948A) // outline of toggles and fields — 3.2:1 on paper

// --- Text -----------------------------------------------------------------
// Every colour that carries text clears 4.5:1 on the paper.
val InkPrimary = Color(0xFF2F3D38)   // primary text
// 4.7:1 against the mesh background and 5.7:1 against the paper. The lighter #5C7268 cleared AA
// on a card but landed at 4.28:1 on the background, and plenty of secondary text sits there.
val InkSecondary = Color(0xFF566B61) // secondary text, descriptions, inactive tabs
val InkMuted = Color(0xFF7E948A)     // Material `outline` — never text

// --- Mesh gradient blobs (background radials) -----------------------------
val Mesh1 = Color(0xFFCFE6DC)
val Mesh2 = Color(0xFFE9DDCA)
val Mesh3 = Color(0xFFCBE3D8)
val Mesh4 = Color(0xFFD8E8DD)
val Mesh5 = Color(0xFFEEF5F0)

// --- Streak / mustard (warm badges) ---------------------------------------
val StreakBg = Color(0xFFF6E6C8)   // streak pill background
val StreakFg = Color(0xFF8A5A10)   // streak text/emoji — 4.8:1 on its own pill
val Mustard = Color(0xFFD99A4E)    // warm mustard accent, decorative only

// --- Tinted shadow (the warm signature, never Material grey) --------------
val WarmShadow = Color(0x663C6E5F) // rgba(60,110,95,0.40) — translucent deep green

// --- Error (standard Material 3 ramp) -------------------------------------
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)

// --- Dark mode -------------------------------------------------------------
// The design mockup sits in a very narrow band of greens, which muddles together on a real
// screen. The hierarchy is widened here: deeper background, cards lifted by a light hairline
// (a black shadow is invisible in dark), brighter mesh blobs.
val SageDarkPrimary = Color(0xFF74A596)     // primary / accent — brand green (legible against cream)
val SageDarkPrimaryDark = Color(0xFF6BA491) // primaryDark — "Your turn" text
val SageDarkAccent = Color(0xFF88CEBA)      // bright accent — links, "View" pill, chips
val SageDarkOnPrimary = Color(0xFFF9FBF6)   // text/icon on the accent (cream)
val SageDarkContainer = Color(0xFF2A3B33)   // container — icon halo, chips, "Your turn"
val SageDarkNavActive = Color(0xFF31473D)   // active tab background (accentSoft)

val InkDarkBg = Color(0xFF0F1915)           // background / mesh gradient base
val InkDarkCanvas = Color(0xFF0A100D)       // deepest tone (canvas behind the app)
val InkDarkSurface = Color(0xFF20302A)      // card — the "paper" in dark
val InkDarkVariant = Color(0xFF2A3B33)      // surfaceVariant — chips, fields, segments
val InkDarkBoxIdle = Color(0xFF16211C)      // unchecked checkbox/toggle (a "hole")
val InkDarkOnSurface = Color(0xFFEAF2ED)    // primary text
val InkDarkSecondary = Color(0xFF9DB2A8)    // secondary text / descriptions
val InkDarkMuted = Color(0xFF778A81)        // muted labels, inactive nav icons
val InkDarkBorder = Color(0xFF33443C)       // borders, dividers, field outlines
val InkDarkHairline = Color(0x1AFFFFFF)     // card hairline (~10% white) — depth in dark
val WarmShadowDark = Color(0x99000000)      // dark-mode shadow (translucent black)

// Mesh gradient blobs in dark (brighter, so the gradient still reads).
val MeshDark1 = Color(0xFF224B3C)
val MeshDark2 = Color(0xFF453D22)
val MeshDark3 = Color(0xFF1B4838)
val MeshDark4 = Color(0xFF285041)
val MeshDark5 = Color(0xFF1E332C)

// Warm badges in dark (streak / mustard).
val StreakBgDark = Color(0xFF3E3417)        // streak pill background
val StreakFgDark = Color(0xFFE0AC4C)        // streak text/emoji, mustard accent
