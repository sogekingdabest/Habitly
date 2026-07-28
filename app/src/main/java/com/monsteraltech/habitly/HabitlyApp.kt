package com.monsteraltech.habitly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HabitlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // App Check acredita que quien habla con Firebase es esta app, y no un script que haya
        // extraído el google-services.json del APK (es público por diseño). El proveedor depende
        // de la variante: Play Integrity en release, secreto de depuración en debug. Ver las dos
        // implementaciones de AppCheckInstaller.kt en src/release/java y src/debug/java.
        installAppCheck()
    }
}
