package com.monsteraltech.habitly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.monsteraltech.habitly.feature.settings.data.LocaleHelper
import com.monsteraltech.habitly.feature.settings.domain.model.ThemeMode
import com.monsteraltech.habitly.feature.settings.domain.repository.SettingsRepository
import com.monsteraltech.habitly.feature.widget.HabitlyWidget
import com.monsteraltech.habitly.ui.theme.HabitlyTheme
import dagger.hilt.android.AndroidEntryPoint
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.navigation.ExternalDestination
import com.monsteraltech.habitly.navigation.RootRoute
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Pantalla pedida desde fuera: notificación de recordatorio o atajo del launcher.
    private val externalDestination = mutableStateOf<ExternalDestination?>(null)

    // Texto recibido de otra app por "Compartir con Habitly" (ACTION_SEND).
    private val sharedText = mutableStateOf<String?>(null)

    // Aplica el idioma persistido antes de crear la Activity (antes de la inyección de Hilt),
    // por eso LocaleHelper lee la preferencia de forma síncrona. Un cambio de idioma en Ajustes
    // llama a recreate(), lo que vuelve a ejecutar este envoltorio con la nueva locale.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado ignorado */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        handleIntent(intent)

        val startDestination = if (firebaseAuth.currentUser != null) {
            RootRoute.Main.route
        } else {
            RootRoute.Auth.route
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            HabitlyTheme(darkTheme = themeMode.toDarkOverride()) {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.monsteraltech.habitly.navigation.RootNavGraph(
                        startDestination = startDestination,
                        externalDestination = externalDestination.value,
                        onExternalDestinationConsumed = { externalDestination.value = null },
                        sharedText = sharedText.value,
                        onSharedTextConsumed = { sharedText.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app, refrescamos el widget para que refleje el estado actual.
        lifecycleScope.launch {
            runCatching { HabitlyWidget().updateAll(applicationContext) }
        }
    }

    /**
     * Traduce el intent de entrada a lo que la UI tiene que hacer: ir a una pestaña concreta
     * (notificación de rutina o atajo del launcher) o abrir la revisión del texto compartido.
     *
     * El texto de `ACTION_SEND` **no se procesa aquí**: se guarda tal cual y lo recoge la hoja de
     * revisión, que es quien lo sanea y lo trata como datos no fiables.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getStringExtra(EXTRA_ROUTINE_ID) != null) {
            externalDestination.value = ExternalDestination.ROUTINES
        }
        ExternalDestination.fromAction(intent.action)?.let { externalDestination.value = it }

        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val received = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!received.isNullOrBlank()) sharedText.value = received
            // Un ACTION_SEND ya consumido volvería a abrir la hoja al rotar o al volver a la app.
            intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"
    }
}
