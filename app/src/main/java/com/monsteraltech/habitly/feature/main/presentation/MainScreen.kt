package com.monsteraltech.habitly.feature.main.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monsteraltech.habitly.feature.dashboard.presentation.DashboardScreen
import com.monsteraltech.habitly.feature.household.presentation.HouseholdScreen
import com.monsteraltech.habitly.feature.routines.presentation.RoutinesScreen
import com.monsteraltech.habitly.feature.shopping.presentation.ShoppingScreen
import com.monsteraltech.habitly.feature.shopping.presentation.history.HistoryScreen
import com.monsteraltech.habitly.feature.aiassistant.presentation.AiAssistantScreen
import androidx.hilt.navigation.compose.hiltViewModel

sealed class BottomNavRoute(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String) {
    object Dashboard : BottomNavRoute("dashboard", Icons.Rounded.Home, "Inicio")
    object Shopping : BottomNavRoute("shopping", Icons.Rounded.ShoppingCart, "Compra")
    object AiAssistant : BottomNavRoute("ai_assistant", Icons.Rounded.SmartToy, "Asistente")
    object Routines : BottomNavRoute("routines", Icons.Rounded.Checklist, "Rutinas")
    object Household : BottomNavRoute("household", Icons.Rounded.Settings, "Casa")
}

object HiddenRoutes {
    const val ShoppingHistory = "shopping_history"
}

@Composable
fun MainScreen(
    onSignOut: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        BottomNavRoute.Dashboard,
        BottomNavRoute.Shopping,
        BottomNavRoute.AiAssistant,
        BottomNavRoute.Routines,
        BottomNavRoute.Household
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavRoute.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavRoute.Dashboard.route) {
                DashboardScreen(
                    onNavigateToShopping = {
                        navController.navigate(BottomNavRoute.Shopping.route) {
                            popUpTo(BottomNavRoute.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToRoutines = {
                        navController.navigate(BottomNavRoute.Routines.route) {
                            popUpTo(BottomNavRoute.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(BottomNavRoute.Shopping.route) {
                ShoppingScreen(
                    onNavigateToHistory = { navController.navigate(HiddenRoutes.ShoppingHistory) }
                )
            }
            composable(BottomNavRoute.AiAssistant.route) {
                AiAssistantScreen()
            }
            composable(BottomNavRoute.Routines.route) {
                RoutinesScreen()
            }
            composable(BottomNavRoute.Household.route) {
                HouseholdScreen(onSignOut = onSignOut)
            }
            composable(HiddenRoutes.ShoppingHistory) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
