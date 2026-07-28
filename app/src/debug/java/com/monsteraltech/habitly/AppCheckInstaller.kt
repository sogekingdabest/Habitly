package com.monsteraltech.habitly

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug variant: App Check debug provider.
 *
 * Play Integrity does not work on debug builds or emulators, so this issues a token from a local
 * secret instead. The first run prints that secret to Logcat under the `DebugAppCheckProvider`
 * tag; register it in Firebase Console → App Check → the Android app → ⋮ → Manage debug tokens.
 *
 * The token is per install: uninstalling the app invalidates it. Registering it only matters once
 * App Check is enforced — until then, unverified requests are served anyway.
 */
fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
