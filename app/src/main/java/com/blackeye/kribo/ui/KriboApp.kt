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
import com.blackeye.kribo.ui.home.NotesViewModel
import com.blackeye.kribo.ui.note.NoteEditorScreen
import com.blackeye.kribo.ui.scan.ScanScreen
import com.blackeye.kribo.ui.search.SearchScreen
import com.blackeye.kribo.ui.settings.SettingsScreen

@Composable
fun KriboApp(
    container: AppContainer,
    billingState: BillingUiState,
    onPurchase: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = notesViewModel(container),
                onQuickNote = { navController.navigate(Routes.NOTE_NEW) },
                onScan = { navController.navigate(Routes.SCAN) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
            )
        }

        composable(Routes.NOTE_NEW) {
            NoteEditorScreen(
                noteId = null,
                repository = container.noteRepository,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.NOTE_EDIT,
            arguments = listOf(
                navArgument(Routes.NOTE_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong(Routes.NOTE_ID_ARG) ?: 0L
            NoteEditorScreen(
                noteId = noteId.takeIf { it > 0L },
                repository = container.noteRepository,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = notesViewModel(container),
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                billingState = billingState,
                onBack = { navController.popBackStack() },
                onPurchase = onPurchase,
                onRestore = container.billingManager::refresh,
            )
        }
    }
}

@Composable
private fun notesViewModel(container: AppContainer): NotesViewModel = viewModel(
    factory = viewModelFactory {
        initializer { NotesViewModel(container.noteRepository) }
    },
)
