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
    // The interface language is applied live: the UI reads its strings through
    // this context. Sheets, dialogs and menus open in windows of their own,
    // where Compose resets LocalContext to the activity (the phone's language);
    // they get it back from LocalUiContext through the Localized* wrappers.
    val base = LocalContext.current
    val localized = remember(vm.uiLanguage) { UiLanguage.wrap(base, vm.uiLanguage) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalUiContext provides localized,
    ) { AppContent(vm) }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    BaibolyTheme(vm.theme, vm.style, vm.palette, vm.textFont, vm.engravedTitles, vm.lineSpacing) {
        Silent.Provide {
            Surface(Modifier.fillMaxSize()) {
                // Back leads home to the reader (Page: to its menu first); from the reader it leaves the app.
                BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = backTarget(vm) }
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
                            Screen.Menu -> PageMenuScreen(vm)
                        }
                    }
                    // the mode's main pages, one tap away
                    if (isTopLevel(vm.style, vm.mode, vm.screen) && !vm.selectionActive) StyledNavigation(vm)
                }
            }
        }
    }
}

/** Where Back goes: the reader, or in the Page theme the menu the page was opened from. */
fun backTarget(vm: AppViewModel): Screen = when {
    vm.screen == Screen.Main || vm.screen == Screen.Menu -> Screen.Main
    vm.style == mg.baiboly.app.Style.Page && vm.screen != Screen.Books -> Screen.Menu
    else -> Screen.Main
}
