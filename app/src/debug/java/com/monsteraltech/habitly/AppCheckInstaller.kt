package com.monsteraltech.habitly

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Variante de **debug**: proveedor de depuración de App Check.
 *
 * Play Integrity no funciona en builds de debug (ni en emulador), así que aquí se usa el
 * proveedor que emite un token a partir de un secreto local. La primera ejecución imprime ese
 * secreto en Logcat con la etiqueta `DebugAppCheckProvider`; hay que darlo de alta en
 * Firebase Console → App Check → la app Android → menú ⋮ → *Gestionar tokens de depuración*.
 *
 * Si no lo registras no pasa nada mientras App Check siga en modo *unenforced*: la petición
 * viaja sin token verificado y Firestore la atiende igual.
 */
fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
