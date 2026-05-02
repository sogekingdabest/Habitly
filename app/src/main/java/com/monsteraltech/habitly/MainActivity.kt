package com.monsteraltech.habitly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.monsteraltech.habitly.ui.theme.HabitlyTheme
import dagger.hilt.android.AndroidEntryPoint
import com.google.firebase.auth.FirebaseAuth
import com.monsteraltech.habitly.navigation.RootRoute
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val startDestination = if (firebaseAuth.currentUser != null) {
            RootRoute.Main.route
        } else {
            RootRoute.Auth.route
        }
        
        setContent {
            HabitlyTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.monsteraltech.habitly.navigation.RootNavGraph(
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}