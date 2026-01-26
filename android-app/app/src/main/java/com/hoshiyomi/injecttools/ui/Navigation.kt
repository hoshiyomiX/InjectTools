package com.hoshiyomi.injecttools.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoshiyomi.injecttools.ui.screens.DiscoverScreen
import com.hoshiyomi.injecttools.ui.screens.HomeScreen
import com.hoshiyomi.injecttools.ui.screens.ResultsScreen
import com.hoshiyomi.injecttools.ui.screens.ScanScreen
import com.hoshiyomi.injecttools.ui.screens.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    object Discover : Screen("discover")
    object Results : Screen("results")
    object Settings : Screen("settings")
}

@Composable
fun InjectToolsApp(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToDiscover = { navController.navigate(Screen.Discover.route) },
                onNavigateToResults = { navController.navigate(Screen.Results.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.Scan.route) {
            ScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Discover.route) {
            DiscoverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Results.route) {
            ResultsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
