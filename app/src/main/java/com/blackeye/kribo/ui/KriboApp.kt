package com.blackeye.kribo.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blackeye.kribo.AppContainer
import com.blackeye.kribo.billing.BillingUiState
import com.blackeye.kribo.ui.home.HomeScreen
import com.blackeye.kribo.ui.home.LibraryViewModel
import com.blackeye.kribo.ui.note.NoteEditorScreen
import com.blackeye.kribo.ui.settings.SettingsScreen

@Composable
fun KriboApp(
    container: AppContainer,
    billingState: BillingUiState,
    onPurchase: () -> Unit,
) {
    val navController = rememberNavController()
    val library: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        },
    )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = library,
                uiPreferences = container.uiPreferences,
                billingState = billingState,
                onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onPurchase = onPurchase,
            )
        }

        composable(
            route = Routes.NOTE_EDIT,
            arguments = listOf(
                navArgument(Routes.NOTE_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong(Routes.NOTE_ID_ARG) ?: 0L
            if (noteId > 0L) {
                NoteEditorScreen(
                    noteId = noteId,
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                billingState = billingState,
                library = library,
                uiPreferences = container.uiPreferences,
                onBack = { navController.popBackStack() },
                onPurchase = onPurchase,
                onRestorePurchases = container.billingManager::refresh,
            )
        }
    }
}
