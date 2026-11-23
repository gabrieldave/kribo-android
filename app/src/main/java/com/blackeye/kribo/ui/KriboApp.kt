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
import com.blackeye.kribo.ui.settings.LicensesScreen
import com.blackeye.kribo.ui.transfer.WifiTransferScreen
import com.blackeye.kribo.ui.settings.SettingsScreen

@Composable
fun KriboApp(
    container: AppContainer,
    billingState: BillingUiState,
    isPro: Boolean,
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
                onOpenNote = { id, query -> navController.navigate(Routes.noteEdit(id, query)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onPurchase = onPurchase,
            )
        }

        composable(
            route = Routes.NOTE_EDIT,
            arguments = listOf(
                navArgument(Routes.NOTE_ID_ARG) { type = NavType.LongType },
                navArgument(Routes.QUERY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong(Routes.NOTE_ID_ARG) ?: 0L
            val query = backStackEntry.arguments?.getString(Routes.QUERY_ARG).orEmpty()
            if (noteId > 0L) {
                NoteEditorScreen(
                    noteId = noteId,
                    initialQuery = query,
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
                isPro = isPro,
                onRestorePurchases = { callback -> container.entitlementRepository.restore(callback) },
                onLicenses = { navController.navigate(Routes.LICENSES) },
                onOpenWifi = { navController.navigate(Routes.WIFI) },
            )
        }

        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.WIFI) {
            WifiTransferScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
