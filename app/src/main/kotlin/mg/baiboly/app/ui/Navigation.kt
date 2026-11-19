package mg.baiboly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppMode
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.core.bible.Canon

// ---------------------------------------------------------------- modes

fun AppMode.icon(): ImageVector = when (this) {
    AppMode.Casual -> Icons.Outlined.AutoStories
    AppMode.Church -> Icons.Outlined.Church
    AppMode.Study -> Icons.Outlined.School
}

@Composable
fun AppMode.label(): String = stringResource(
    when (this) { AppMode.Casual -> R.string.mode_casual; AppMode.Church -> R.string.mode_church; AppMode.Study -> R.string.mode_study }
)

@Composable
private fun AppMode.description(): String = stringResource(
    when (this) { AppMode.Casual -> R.string.mode_casual_desc; AppMode.Church -> R.string.mode_church_desc; AppMode.Study -> R.string.mode_study_desc }
)

/** Each mode's own quiet accent: gold for Casual, oxblood for Church, deep green for Study. */
@Composable
fun AppMode.accent(): Color = when (this) {
    AppMode.Casual -> LocalOrnament.current.gold
    AppMode.Church -> LocalOrnament.current.rubric
    AppMode.Study -> MaterialTheme.colorScheme.tertiary
}

/** The mode badge, top left of every main page: icon and name; tap to change mode. */
@Composable
fun ModeBadge(vm: AppViewModel, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, vm.mode.accent().copy(alpha = 0.7f)),
        modifier = Modifier.padding(start = 8.dp).heightIn(min = 40.dp).testTag("mode-badge"),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(vm.mode.icon(), if (compact) vm.mode.label() else null, tint = vm.mode.accent(), modifier = Modifier.size(20.dp))
            // compact (reader): the title needs the room, so icon only; the name stays for screen readers
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(vm.mode.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (open) ModeSheet(vm) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.choose_mode), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            AppMode.entries.forEach { m ->
                val on = m == vm.mode
                Surface(
                    onClick = { vm.changeMode(m); onDismiss() },
                    shape = RoundedCornerShape(16.dp),
                    color = if (on) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (on) 1.5.dp else 1.dp, if (on) m.accent() else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("mode:${m.name}"),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon(), null, tint = m.accent(), modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(m.label(), style = MaterialTheme.typography.titleMedium)
                            Text(m.description(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on) Icon(Icons.Filled.Check, null, tint = m.accent())
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bottom navigation

private data class Destination(val screen: Screen, val icon: ImageVector, val label: Int)

private fun destinations(mode: AppMode): List<Destination> = when (mode) {
    AppMode.Casual -> listOf(
        Destination(Screen.Main, Icons.Outlined.AutoStories, R.string.nav_read),
        Destination(Screen.Search, Icons.Outlined.Search, R.string.nav_search),
        Destination(Screen.Library, Icons.Outlined.Bookmarks, R.string.nav_library),
    )
    AppMode.Church -> listOf(
        Destination(Screen.Main, Icons.Outlined.Church, R.string.nav_service),
        Destination(Screen.Books, Icons.Outlined.LibraryBooks, R.string.books),
        Destination(Screen.Lists, Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.nav_lists),
    )
    AppMode.Study -> listOf(
        Destination(Screen.Main, Icons.Outlined.AutoStories, R.string.nav_read),
        Destination(Screen.Search, Icons.Outlined.Search, R.string.nav_search),
        Destination(Screen.Dictionary, Icons.Outlined.Translate, R.string.nav_dictionary),
        Destination(Screen.Map, Icons.Outlined.Map, R.string.map),
        Destination(Screen.Library, Icons.Outlined.Bookmarks, R.string.nav_library),
    )
}

/** Main pages of the current mode; the others are reached from them. */
fun isTopLevel(mode: AppMode, screen: Screen): Boolean = destinations(mode).any { it.screen == screen } || screen == Screen.Books

@Composable
fun AppNavigationBar(vm: AppViewModel) {
    val items = destinations(vm.mode)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { d ->
            val selected = vm.screen == d.screen
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (d.screen == Screen.Map) vm.mapForChapter = true
                    when (d.screen) {
                        Screen.Library -> { vm.refreshNotes(); vm.refreshHighlights(); vm.refreshHistory(); vm.refreshSavedLists() }
                        Screen.Lists -> vm.refreshSavedLists()
                        Screen.Search -> vm.search(vm.searchQuery, vm.translationId)
                        else -> Unit
                    }
                    vm.screen = d.screen
                },
                icon = { Icon(d.icon, null) },
                // five tabs in Study mode: a smaller label so none is cut
                label = {
                    Text(
                        stringResource(d.label), maxLines = 1, softWrap = false,
                        style = if (items.size > 3) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = vm.mode.accent(),
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = vm.mode.accent().copy(alpha = 0.16f),
                ),
                modifier = Modifier.testTag("nav:${d.screen.name}"),
            )
        }
    }
}

/** Gear to Settings, at the end of every main page's top bar. */
@Composable
fun SettingsButton(vm: AppViewModel) {
    IconButton(onClick = { vm.screen = Screen.Settings }, modifier = Modifier.testTag("settings")) {
        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
    }
}

/** Top bar of a main page: mode badge, title, then actions and the Settings gear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(vm: AppViewModel, title: String, actions: @Composable () -> Unit = {}) {
    Column {
        TopAppBar(
            navigationIcon = { ModeBadge(vm) },
            title = { Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp)) },
            actions = { actions(); SettingsButton(vm) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        GoldRule()
    }
}

// ---------------------------------------------------------------- books

/**
 * Choose a book, then a chapter. In Church mode the chapter shows its verses
 * so passages can be picked and added to the service list.
 */
@Composable
fun BooksScreen(vm: AppViewModel) {
    var book by remember { mutableStateOf<Int?>(null) }
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    Scaffold(topBar = { MainTopBar(vm, stringResource(R.string.books)) }) { padding ->
        if (vm.mode == AppMode.Church) {
            BrowseContent(vm, Modifier.padding(padding).fillMaxSize(), onDone = null)
            return@Scaffold
        }
        val b = book
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (b == null) {
                val books = vm.availableBooks.map { Canon.book(it) }
                var testament by remember { mutableIntStateOf(if (Canon.book(vm.location.book).isOldTestament) 0 else 1) }
                TabRow(selectedTabIndex = testament, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = testament == 0, onClick = { testament = 0 }, text = { Text(stringResource(R.string.old_testament)) }, modifier = Modifier.testTag("testament:0"))
                    Tab(selected = testament == 1, onClick = { testament = 1 }, text = { Text(stringResource(R.string.new_testament)) }, modifier = Modifier.testTag("testament:1"))
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("books"),
                ) {
                    items(books.filter { it.isOldTestament == (testament == 0) }, key = { it.id }) { bk ->
                        Surface(
                            onClick = { book = bk.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (bk.id == vm.location.book) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, LocalOrnament.current.gold.copy(alpha = 0.4f)),
                            modifier = Modifier.heightIn(min = 52.dp).testTag("book-item:${bk.id}"),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    bk.name(lang),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (bk.id == vm.location.book) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f), maxLines = 1,
                                )
                                if (bk.isDeuterocanonical) Text("✦", color = LocalOrnament.current.gold)
                            }
                        }
                    }
                    if (testament == 0 && books.any { it.isDeuterocanonical }) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("✦ " + stringResource(R.string.deuterocanonical), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    IconButton(onClick = { book = null }) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(Canon.book(b).name(lang), style = MaterialTheme.typography.titleLarge, color = LocalOrnament.current.rubric)
                }
                Box(Modifier.testTag("chapters")) {
                    ChapterGrid(vm.chapterCount(b)) { c -> vm.openChapter(b, c) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- library

/** Notes, highlights, history and service lists in one place. */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { MainTopBar(vm, stringResource(R.string.nav_library)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                listOf(R.string.notes, R.string.highlights, R.string.history, R.string.nav_lists).forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), maxLines = 1) },
                        modifier = Modifier.testTag("library-tab:$i"))
                }
            }
            CompositionLocalProvider(LocalEmbedded provides true) {
                when (tab) {
                    0 -> NotesScreen(vm)
                    1 -> HighlightsScreen(vm)
                    2 -> HistoryScreen(vm)
                    else -> SavedListsScreen(vm)
                }
            }
        }
    }
}

