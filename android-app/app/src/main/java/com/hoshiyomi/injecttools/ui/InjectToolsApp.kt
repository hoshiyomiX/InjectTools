package com.hoshiyomi.injecttools.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hoshiyomi.injecttools.ui.screens.*

@Composable
fun InjectToolsApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToScan = { navController.navigate("scan") },
                onNavigateToDiscover = { navController.navigate("discover") },
                onNavigateToResults = { navController.navigate("results") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        
        composable("scan") {
            ScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("discover") {
            DiscoverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("results") {
            ResultsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
