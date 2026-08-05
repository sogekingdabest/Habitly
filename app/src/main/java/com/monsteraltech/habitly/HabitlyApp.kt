package com.monsteraltech.habitly

import android.app.Application
import com.monsteraltech.habitly.feature.routines.data.notification.RoutineChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HabitlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Proves to Firebase that the caller is this app, not a script using the
        // google-services.json extracted from the APK (public by design). The provider depends on
        // the variant: see AppCheckInstaller.kt in src/release/java and src/debug/java.
        installAppCheck()
        // Created here rather than when the first reminder fires, because the settings screen links
        // straight into each channel and they have to exist before any notification is posted.
        RoutineChannels.ensureChannels(this)
    }
}
