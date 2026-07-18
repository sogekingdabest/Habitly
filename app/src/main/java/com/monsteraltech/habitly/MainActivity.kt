package com.monsteraltech.habitly

import android.Manifest
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
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.monsteraltech.habitly.feature.widget.HabitlyWidget
import com.monsteraltech.habitly.ui.theme.HabitlyTheme
import dagger.hilt.android.AndroidEntryPoint
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.navigation.RootRoute
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    // Señal de deep link: true cuando se abre la app tocando una notificación de rutina.
    private val routinesDeepLink = mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado ignorado */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        handleDeepLink(intent)

        val startDestination = if (firebaseAuth.currentUser != null) {
            RootRoute.Main.route
        } else {
            RootRoute.Auth.route
        }

        setContent {
            HabitlyTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.monsteraltech.habitly.navigation.RootNavGraph(
                        startDestination = startDestination,
                        navigateToRoutines = routinesDeepLink.value,
                        onRoutinesDeepLinkConsumed = { routinesDeepLink.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app, refrescamos el widget para que refleje el estado actual.
        lifecycleScope.launch {
            runCatching { HabitlyWidget().updateAll(applicationContext) }
        }
    }

    /** Si la notificación de rutina trae un routine_id, pedimos ir a la pestaña de Rutinas. */
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ROUTINE_ID) != null) {
            routinesDeepLink.value = true
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
