package mg.baiboly.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.Screen

@Composable
fun BaibolyApp(vm: AppViewModel) {
    BaibolyTheme(vm.theme) {
        Silent.Provide {
            Surface(Modifier.fillMaxSize()) {
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
                BackHandler(enabled = !drawer.isOpen && vm.screen != Screen.Main) { vm.screen = Screen.Main }
                ModalNavigationDrawer(
                    drawerState = drawer,
                    // Edge swipes belong to the reader (list navigation); the menu opens from its button.
                    gesturesEnabled = drawer.isOpen,
                    drawerContent = {
                        SideMenu(vm) { scope.launch { drawer.close() } }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        when (vm.screen) {
                            Screen.Main -> MainScreen(vm, openMenu = { scope.launch { drawer.open() } })
                            Screen.History -> HistoryScreen(vm)
                            Screen.Notes -> NotesScreen(vm)
                            Screen.Highlights -> HighlightsScreen(vm)
                            Screen.Search -> SearchScreen(vm)
                            Screen.Lists -> SavedListsScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                            Screen.About -> AboutScreen(vm)
                            Screen.Backup -> BackupScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
