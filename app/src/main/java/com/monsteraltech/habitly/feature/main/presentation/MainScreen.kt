package com.monsteraltech.habitly.feature.main.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.feature.dashboard.presentation.DashboardScreen
import com.monsteraltech.habitly.feature.household.presentation.HouseholdScreen
import com.monsteraltech.habitly.feature.household.presentation.OnboardingScreen
import com.monsteraltech.habitly.feature.routines.presentation.RoutinesScreen
import com.monsteraltech.habitly.feature.shopping.presentation.ShoppingScreen
import com.monsteraltech.habitly.feature.shopping.presentation.add.AddProductScreen
import com.monsteraltech.habitly.feature.shopping.presentation.history.HistoryScreen
import com.monsteraltech.habitly.feature.aiassistant.presentation.AiAssistantScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.monsteraltech.habitly.R

sealed class BottomNavRoute(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val titleRes: Int) {
    object Dashboard : BottomNavRoute("dashboard", Icons.Rounded.Home, R.string.nav_home)
    object Shopping : BottomNavRoute("shopping", Icons.Rounded.ShoppingCart, R.string.nav_shopping)
    object AiAssistant : BottomNavRoute("ai_assistant", Icons.Rounded.SmartToy, R.string.nav_assistant)
    object Routines : BottomNavRoute("routines", Icons.Rounded.Checklist, R.string.nav_routines)
    object Household : BottomNavRoute("household", Icons.Rounded.Settings, R.string.nav_household)
}

object HiddenRoutes {
    const val ShoppingHistory = "shopping_history"
    const val ShoppingAddProduct = "shopping_add_product"
}

@Composable
fun MainScreen(
    onSignOut: () -> Unit,
    navigateToRoutines: Boolean = false,
    onRoutinesDeepLinkConsumed: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        !uiState.hasHousehold -> {
            OnboardingScreen()
        }
        else -> {
            MainContent(
                onSignOut = onSignOut,
                navigateToRoutines = navigateToRoutines,
                onRoutinesDeepLinkConsumed = onRoutinesDeepLinkConsumed
            )
        }
    }
}

@Composable
private fun MainContent(
    onSignOut: () -> Unit,
    navigateToRoutines: Boolean,
    onRoutinesDeepLinkConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Deep link desde la notificación de rutina.
    LaunchedEffect(navigateToRoutines) {
        if (navigateToRoutines) {
            navController.navigate(BottomNavRoute.Routines.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onRoutinesDeepLinkConsumed()
        }
    }

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
                        icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                        label = { Text(stringResource(screen.titleRes)) },
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
                    onNavigateToHistory = { navController.navigate(HiddenRoutes.ShoppingHistory) },
                    onNavigateToAddProduct = { navController.navigate(HiddenRoutes.ShoppingAddProduct) }
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
            composable(HiddenRoutes.ShoppingAddProduct) {
                AddProductScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
