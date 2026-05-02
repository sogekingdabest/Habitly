package com.monsteraltech.habitly.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.monsteraltech.habitly.feature.login.presentation.LoginScreen
import com.monsteraltech.habitly.feature.register.presentation.emailverification.EmailVerificationScreen
import com.monsteraltech.habitly.feature.register.presentation.register.RegisterScreen

// ─────────────────────────────────────────────────────────────────────────────
// Rutas tipadas del grafo de autenticación
// ─────────────────────────────────────────────────────────────────────────────

sealed class AuthRoute(val route: String) {
    object Login : AuthRoute("login")
    object Register : AuthRoute("register")
    object EmailVerification : AuthRoute("email_verification")
    object ForgotPassword : AuthRoute("forgot_password")
}

// ─────────────────────────────────────────────────────────────────────────────
// NavHost raíz (Auth)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuthNavGraph(
    navController: NavHostController = rememberNavController(),
    onNavigateToHome: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = AuthRoute.Login.route
    ) {

        // ── Login ─────────────────────────────────────────────────────────────
        composable(AuthRoute.Login.route) {
            LoginScreen(
                onNavigateToHome = onNavigateToHome,
                onNavigateToRegister = {
                    navController.navigate(AuthRoute.Register.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(AuthRoute.ForgotPassword.route)
                }
            )
        }

        // ── Register ──────────────────────────────────────────────────────────
        composable(AuthRoute.Register.route) {
            RegisterScreen(
                navController = navController,
                onNavigateToHome = {
                    // Limpia todo el back stack de Auth al navegar a Home
                    onNavigateToHome()
                }
            )
        }

        // ── Email Verification ────────────────────────────────────────────────
        composable(AuthRoute.EmailVerification.route) {
            EmailVerificationScreen(
                navController = navController,
                onNavigateToHome = {
                    onNavigateToHome()
                }
            )
        }

        // ── Forgot Password ───────────────────────────────────────────────────
        composable(AuthRoute.ForgotPassword.route) {
            com.monsteraltech.habitly.feature.login.presentation.ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
