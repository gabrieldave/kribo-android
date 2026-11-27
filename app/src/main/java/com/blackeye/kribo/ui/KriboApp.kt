package com.blackeye.kribo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.blackeye.kribo.ui.drive.DriveSyncScreen
import com.blackeye.kribo.ui.onboarding.UserGuideScreen
import com.blackeye.kribo.ui.onboarding.UserManualScreen
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

    val onboardingSeen by container.uiPreferences.onboardingSeen.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = if (onboardingSeen) Routes.HOME else Routes.GUIDE,
    ) {
        composable(Routes.GUIDE) {
            UserGuideScreen(
                onFinish = {
                    container.uiPreferences.markOnboardingSeen()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.GUIDE) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val autoSyncState by container.driveAutoSyncScheduler.state.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = library,
                uiPreferences = container.uiPreferences,
                billingState = billingState,
                autoSyncState = autoSyncState,
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
                onOpenDrive = { navController.navigate(Routes.DRIVE) },
                onManual = { navController.navigate(Routes.MANUAL) },
                onRefreshBilling = { container.entitlementRepository.refresh() },
            )
        }

        composable(Routes.MANUAL) {
            UserManualScreen(onBack = { navController.popBackStack() })
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

        composable(Routes.DRIVE) {
            DriveSyncScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
