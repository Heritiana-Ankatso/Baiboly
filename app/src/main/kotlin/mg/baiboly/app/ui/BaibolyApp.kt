package mg.baiboly.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntOffset
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
    ) {
        // Compact interface: everything drawn in dp (bars, buttons, spacing) a little smaller.
        // The Bible text keeps the size chosen in Settings (its sp are scaled back up).
        val d = androidx.compose.ui.platform.LocalDensity.current
        val scale = vm.uiDensity.scale
        val bibleLang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * scale, d.fontScale),
            LocalTextScale provides 1f / scale,
            LocalBibleLang provides bibleLang,
        ) { AppContent(vm) }
        // the screen stays awake while the app is in use (Settings › Appearance; always in Church mode)
        val view = androidx.compose.ui.platform.LocalView.current
        val awake = vm.keepScreenOn || vm.mode == mg.baiboly.app.AppMode.Church
        androidx.compose.runtime.DisposableEffect(awake) {
            view.keepScreenOn = awake
            onDispose { view.keepScreenOn = false }
        }
    }
}

/**
 * The Bible text's size is the reader's own choice: multiplied by this so the
 * compact interface does not shrink it.
 */
val LocalTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

@Composable
private fun AppContent(vm: AppViewModel) {
    BaibolyTheme(vm.theme, vm.style, vm.palette, vm.textFont, vm.engravedTitles, vm.lineSpacing) {
        Silent.Provide {
            val app: @Composable () -> Unit = { AppFrame(vm) }
            if (vm.style == mg.baiboly.app.Style.Navy) NavyDrawer(vm) { app() } else app()
        }
    }
}

/** How deep a page is: the reader at the surface, the main pages one level down, Settings and the like below them. */
private fun Screen.depth(): Int = when (this) {
    Screen.Main -> 0
    Screen.Settings, Screen.About, Screen.Backup, Screen.Document -> 2
    else -> 1
}

@Composable
private fun AppFrame(vm: AppViewModel) {
    // a note docked under the reader belongs to the reader: leaving for another page puts it away
    LaunchedEffect(vm.screen) { if (vm.screen != Screen.Main && vm.documentDocked) vm.closeDocument() }
    Surface(Modifier.fillMaxSize()) {
        // Back leads home to the reader (Page: to its menu first); from the reader it leaves the app.
        BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = backTarget(vm) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // pages do not swap at once: going deeper slides in from the side, coming back slides out the
                // other way, and the main pages fade into one another, so the move shows where it went
                AnimatedContent(
                    targetState = vm.screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val step = targetState.depth() - initialState.depth()
                        val move = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                        when {
                            step > 0 -> (slideInHorizontally(move) { it / 6 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(move) { -it / 6 } + fadeOut(tween(140)))
                            step < 0 -> (slideInHorizontally(move) { -it / 6 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(move) { it / 6 } + fadeOut(tween(140)))
                            else -> fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "page",
                ) { screen ->
                    when (screen) {
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
                        Screen.Documents -> DocumentsScreen(vm)
                        Screen.Document -> DocumentScreen(vm)
                    }
                }
            }
            // the mode's main pages, one tap away (not over the full-screen map)
            val fullMap = vm.screen == Screen.Map && vm.mapFullscreen
            if (isTopLevel(vm.style, vm.mode, vm.screen) && !vm.selectionActive && !fullMap) StyledNavigation(vm)
        }
    }
}

/** Where Back goes: the reader, or in the Page theme the menu the page was opened from. */
fun backTarget(vm: AppViewModel): Screen = when {
    vm.screen == Screen.Main || vm.screen == Screen.Menu -> Screen.Main
    vm.screen == Screen.Document -> Screen.Documents
    vm.style == mg.baiboly.app.Style.Page && vm.screen != Screen.Books -> Screen.Menu
    else -> Screen.Main
}
