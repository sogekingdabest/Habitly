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

    // Destination requested from external triggers (notifications, launcher shortcuts).
    private val externalDestination = mutableStateOf<ExternalDestination?>(null)

    // Text shared from another app via ACTION_SEND.
    private val sharedText = mutableStateOf<String?>(null)

    // Apply persisted language preference before Activity creation (prior to Hilt injection).
    // Changing language in Settings calls recreate(), which re-wraps the base context.
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
        // Refresh home widget state on foregrounding.
        lifecycleScope.launch {
            runCatching { HabitlyWidget().updateAll(applicationContext) }
        }
    }

    /**
     * Parses incoming intent actions (routine notifications, launcher shortcuts, shared text).
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
            // Prevent re-processing shared text on configuration changes or activity resume.
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
