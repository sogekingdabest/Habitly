package com.monsteraltech.habitly

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release variant: attests the app with Play Integrity.
 *
 * Play Integrity only issues valid verdicts for builds signed with the real key and distributed
 * by Play, so a sideloaded APK gets no token. This guards the project quota, not other users'
 * data — that is what `firestore.rules` is for.
 *
 * Requires the app to be registered in Firebase Console under App Check with Play Integrity, and
 * every Play app-signing certificate (current *and* previous, if the key was rotated) to be
 * listed there. While App Check stays unenforced, a missing token breaks nothing.
 */
fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
