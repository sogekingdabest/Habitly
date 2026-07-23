package com.monsteraltech.habitly.feature.main.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.monsteraltech.habitly.ui.theme.habitly
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.feature.dashboard.presentation.DashboardScreen
import com.monsteraltech.habitly.feature.household.presentation.HouseholdScreen
import com.monsteraltech.habitly.feature.household.presentation.OnboardingScreen
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.routines.presentation.RoutinesScreen
import com.monsteraltech.habitly.feature.routines.presentation.add.AddRoutineScreen
import com.monsteraltech.habitly.feature.routines.presentation.add.AddRoutineViewModel
import com.monsteraltech.habitly.feature.shopping.presentation.ShoppingScreen
import com.monsteraltech.habitly.feature.shopping.presentation.add.AddProductScreen
import com.monsteraltech.habitly.feature.shopping.presentation.history.HistoryScreen
import com.monsteraltech.habitly.feature.settings.presentation.SettingsScreen
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
    const val RoutinesAdd = "routines_add"
    const val Settings = "settings"
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
            HabitlyBottomBar(
                items = items,
                currentDestination = currentDestination,
                onSelect = { screen ->
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavRoute.Dashboard.route,
            // consumeWindowInsets marca estos insets como ya aplicados: sin ello, cada
            // Scaffold interior volvía a sumar barra de estado y de navegación, dejando
            // un doble margen arriba y un hueco muerto sobre la barra de pestañas.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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
                    },
                    onNavigateToAddRoutine = {
                        navController.navigate(
                            "${HiddenRoutes.RoutinesAdd}?${AddRoutineViewModel.TYPE_ARG}=${RoutineType.PERSONAL.name}"
                        )
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
                RoutinesScreen(
                    onNavigateToAddRoutine = { type ->
                        navController.navigate("${HiddenRoutes.RoutinesAdd}?${AddRoutineViewModel.TYPE_ARG}=${type.name}")
                    }
                )
            }
            composable(BottomNavRoute.Household.route) {
                HouseholdScreen(
                    onNavigateToSettings = { navController.navigate(HiddenRoutes.Settings) }
                )
            }
            composable(HiddenRoutes.Settings) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSignOut = onSignOut
                )
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
            composable(
                route = "${HiddenRoutes.RoutinesAdd}?${AddRoutineViewModel.TYPE_ARG}={${AddRoutineViewModel.TYPE_ARG}}",
                arguments = listOf(
                    navArgument(AddRoutineViewModel.TYPE_ARG) {
                        type = NavType.StringType
                        defaultValue = RoutineType.PERSONAL.name
                    }
                )
            ) {
                AddRoutineScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Barra inferior de Habitly: papel crema con borde superior, pastilla de resalte en la
 * pestaña activa e ícono central elevado (Habi) con sombra de color. Mantiene el
 * comportamiento accesible de una barra de navegación (rol de pestaña, estado
 * seleccionado) con la piel Cozy en vez del `NavigationBar` gris de Material.
 */
@Composable
private fun HabitlyBottomBar(
    items: List<BottomNavRoute>,
    currentDestination: NavDestination?,
    onSelect: (BottomNavRoute) -> Unit,
) {
    val habitly = MaterialTheme.habitly
    Column(modifier = Modifier.fillMaxWidth().background(habitly.card)) {
        HorizontalDivider(thickness = 1.dp, color = habitly.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                if (screen == BottomNavRoute.AiAssistant) {
                    HabitlyCenterNavItem(screen, selected) { onSelect(screen) }
                } else {
                    HabitlyNavItem(screen, selected) { onSelect(screen) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HabitlyNavItem(
    route: BottomNavRoute,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val habitly = MaterialTheme.habitly
    val contentColor = if (selected) MaterialTheme.colorScheme.onBackground else habitly.navIdle
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                route.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(route.titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.HabitlyCenterNavItem(
    route: BottomNavRoute,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-10).dp)
                .size(52.dp)
                .shadow(elevation = 14.dp, shape = CircleShape, spotColor = primary, ambientColor = primary)
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = stringResource(route.titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.habitly.accentText,
            maxLines = 1,
        )
    }
}
