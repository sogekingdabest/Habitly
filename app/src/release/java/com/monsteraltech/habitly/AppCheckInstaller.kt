package com.monsteraltech.habitly

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Variante de **release**: acredita la app con Play Integrity.
 *
 * Play Integrity solo emite veredictos válidos para builds firmados con la clave real y
 * distribuidos por Play, así que un APK sideloaded o un script hablando con la API de Firestore
 * no consigue token. Esto no protege los datos ajenos —de eso se encargan `firestore.rules`—,
 * protege la cuota del proyecto.
 *
 * Requiere que la app esté registrada en Firebase Console → App Check → Play Integrity.
 * Mientras App Check esté en modo *unenforced*, no tener token no rompe nada: las peticiones
 * pasan igual y la consola solo las contabiliza como no verificadas.
 */
fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
