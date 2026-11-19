package mg.baiboly.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.Screen
import mg.baiboly.app.UiLanguage

@Composable
fun BaibolyApp(vm: AppViewModel) {
    // The interface language is applied live: the whole UI (dialogs and sheets
    // included) reads its strings through this context.
    val base = LocalContext.current
    val localized = remember(vm.uiLanguage) { UiLanguage.wrap(base, vm.uiLanguage) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
    ) { AppContent(vm) }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    BaibolyTheme(vm.theme) {
        Silent.Provide {
            Surface(Modifier.fillMaxSize()) {
                // Back always leads home to the reader; from the reader it leaves the app.
                BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = Screen.Main }
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (vm.screen) {
                            Screen.Main -> MainScreen(vm)
                            Screen.History -> HistoryScreen(vm)
                            Screen.Notes -> NotesScreen(vm)
                            Screen.Highlights -> HighlightsScreen(vm)
                            Screen.Search -> SearchScreen(vm)
                            Screen.Lists -> SavedListsScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                            Screen.About -> AboutScreen(vm)
                            Screen.Backup -> BackupScreen(vm)
                            Screen.Dictionary -> DictionaryScreen(vm)
                            Screen.Map -> MapScreen(vm)
                            Screen.Books -> BooksScreen(vm)
                            Screen.Library -> LibraryScreen(vm)
                        }
                    }
                    // the mode's main pages, one tap away
                    if (isTopLevel(vm.mode, vm.screen) && !vm.selectionActive) AppNavigationBar(vm)
                }
            }
        }
    }
}
