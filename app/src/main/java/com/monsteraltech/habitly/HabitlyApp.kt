package com.monsteraltech.habitly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HabitlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase App Check. Provider depends on build variant:
        // Play Integrity in release vs Debug Provider in debug (see AppCheckInstaller.kt).
        installAppCheck()
    }
}
