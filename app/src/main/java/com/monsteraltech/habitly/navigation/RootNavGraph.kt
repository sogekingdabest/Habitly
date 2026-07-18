package com.monsteraltech.habitly.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.monsteraltech.habitly.feature.main.presentation.MainScreen

sealed class RootRoute(val route: String) {
    object Auth : RootRoute("auth_root")
    object Main : RootRoute("main_root")
}

@Composable
fun RootNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = RootRoute.Auth.route,
    navigateToRoutines: Boolean = false,
    onRoutinesDeepLinkConsumed: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(RootRoute.Auth.route) {
            AuthNavGraph(
                onNavigateToHome = {
                    navController.navigate(RootRoute.Main.route) {
                        popUpTo(RootRoute.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(RootRoute.Main.route) {
            MainScreen(
                onSignOut = {
                    navController.navigate(RootRoute.Auth.route) {
                        popUpTo(RootRoute.Main.route) { inclusive = true }
                    }
                },
                navigateToRoutines = navigateToRoutines,
                onRoutinesDeepLinkConsumed = onRoutinesDeepLinkConsumed
            )
        }
    }
}
