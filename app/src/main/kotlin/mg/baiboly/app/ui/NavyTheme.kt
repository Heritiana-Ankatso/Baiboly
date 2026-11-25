package mg.baiboly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mg.baiboly.app.AppMode
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.core.bible.Canon

/*
 * Navy: a white page and a side menu in deep blue that the page slides away from. Nothing else on the
 * screen but the text, a centred title and two round chapter buttons. The menu puts first what is used
 * most (search, the books, notes and highlights), then the tools of the mode, and the app's own pages last.
 */

/** The menu's two blues: always deep (it is a menu, not a page), a little deeper at night. */
@Composable
private fun navyBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.5f
    val top = if (dark) Color(0xFF14213D) else Color(0xFF1D3563)
    val bottom = if (dark) Color(0xFF0B1222) else lerp(Color(0xFF1D3563), Color(0xFF3B5A8F), 0.55f)
    return Brush.verticalGradient(listOf(top, bottom))
}

private val OnNavy = Color(0xFFF2F5FA)
private val OnNavyDim = Color(0xFFB4C0D6)
private val NavyRule = Color(0x33FFFFFF)

/** Wraps the app in the side menu (Navy theme only). */
@Composable
fun NavyDrawer(vm: AppViewModel, content: @Composable () -> Unit) {
    val state = rememberDrawerState(if (vm.drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.drawerOpen) { if (vm.drawerOpen) state.open() else state.close() }
    LaunchedEffect(state) { snapshotFlow { state.currentValue }.collect { vm.drawerOpen = it == DrawerValue.Open } }
    ModalNavigationDrawer(
        drawerState = state,
        // the swipe opens it only from the reader (other pages scroll sideways: maps, tabs)
        gesturesEnabled = vm.screen == Screen.Main || state.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.Transparent, drawerShape = RoundedCornerShape(0.dp), modifier = Modifier.width(320.dp)) {
                Box(Modifier.fillMaxHeight().background(navyBrush())) {
                    NavyMenu(vm) { scope.launch { state.close() } }
                }
            }
        },
        content = content,
    )
}

private class NavyItem(val icon: ImageVector, val label: Int, val tag: String, val onClick: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavyMenu(vm: AppViewModel, close: () -> Unit) {
    val lang = refLang()
    var openTestament by remember { mutableStateOf<Int?>(null) }
    var openBook by remember { mutableStateOf<Int?>(null) }
    fun go(screen: Screen) { close(); navigateTo(vm, screen) }
    val first = buildList {
        if (vm.mode == AppMode.Church) add(NavyItem(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.nav_lists, "drawer:lists") { go(Screen.Lists) })
        add(NavyItem(Icons.Outlined.EditNote, R.string.my_notes, "drawer:documents") { go(Screen.Documents) })
        add(NavyItem(Icons.Outlined.BorderColor, R.string.highlights, "drawer:highlights") { go(Screen.Highlights) })
        add(NavyItem(Icons.AutoMirrored.Outlined.Notes, R.string.notes, "drawer:notes") { go(Screen.Notes) })
        add(NavyItem(Icons.Outlined.History, R.string.history, "drawer:history") { go(Screen.History) })
    }
    val tools = if (vm.mode == AppMode.Study) listOf(
        NavyItem(Icons.Outlined.Translate, R.string.dictionary, "drawer:dictionary") { vm.dictionaryUnits = false; go(Screen.Dictionary) },
        NavyItem(Icons.Outlined.Map, R.string.map, "drawer:map") { go(Screen.Map) },
    ) else emptyList()
    val app = listOf(
        NavyItem(Icons.Outlined.Settings, R.string.settings, "drawer:settings") { go(Screen.Settings) },
        NavyItem(Icons.Outlined.Cloud, R.string.backup, "drawer:backup") { go(Screen.Backup) },
        NavyItem(Icons.Outlined.Info, R.string.about, "drawer:about") { go(Screen.About) },
    )
    LazyColumn(Modifier.fillMaxWidth().statusBarsPadding().testTag("drawer")) {
        item {
            Text("Baiboly", style = MaterialTheme.typography.titleLarge, color = OnNavyDim,
                modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 10.dp))
            // the mode, one tap each
            // three equal parts, so no name breaks over two lines
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppMode.entries.forEach { m ->
                    val on = m == vm.mode
                    Surface(
                        onClick = { vm.changeMode(m) }, shape = CircleShape,
                        color = if (on) OnNavy else Color.Transparent, contentColor = if (on) Color(0xFF1D3563) else OnNavy,
                        border = BorderStroke(1.dp, if (on) OnNavy else NavyRule), modifier = Modifier.weight(1f).heightIn(min = 40.dp).testTag("drawer-mode:${m.name}"),
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(m.icon(), null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(m.label(), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), color = NavyRule)
            // search first: the quickest way to any verse
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { go(Screen.Search) }.padding(horizontal = 24.dp).testTag("drawer:search"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = OnNavy, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(28.dp))
                Text(stringResource(R.string.search), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), color = OnNavy)
            }
        }
        // the Bible first: a testament unfolds its books, a book its chapters
        item {
            Text(stringResource(R.string.books_of_the_bible), style = MaterialTheme.typography.titleSmall, color = OnNavyDim,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
        }
        listOf(0 to R.string.old_testament, 1 to R.string.new_testament).forEach { (t, label) ->
            item(key = "t$t") {
                val on = openTestament == t
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { openTestament = if (on) null else t; openBook = null }
                        .padding(horizontal = 24.dp).testTag("drawer-testament:$t"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), color = OnNavy, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = OnNavyDim, modifier = Modifier.rotate(if (on) 180f else 0f))
                }
                HorizontalDivider(Modifier.padding(start = 24.dp), color = NavyRule)
            }
            if (openTestament == t) {
                val books = vm.availableBooks.map { Canon.book(it) }.filter { it.isOldTestament == (t == 0) }
                item(key = "books$t") {
                    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        books.forEach { b ->
                            val on = openBook == b.id
                            Surface(
                                onClick = { openBook = if (on) null else b.id }, shape = RoundedCornerShape(8.dp),
                                color = if (on) OnNavy else Color(0x1AFFFFFF), contentColor = if (on) Color(0xFF1D3563) else OnNavy,
                                modifier = Modifier.heightIn(min = 40.dp).testTag("drawer-book:${b.id}"),
                            ) {
                                Text(b.abbr(lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
                openBook?.takeIf { id -> books.any { it.id == id } }?.let { id ->
                    item(key = "chapters$id") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(Canon.book(id).name(lang), style = MaterialTheme.typography.titleSmall, color = OnNavy, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..vm.chapterCount(id)).forEach { c ->
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(Color(0x1AFFFFFF))
                                            .clickable { close(); vm.openChapter(id, c) }.testTag("drawer-chapter:$c"),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("$c", color = OnNavy, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.heightIn(min = 8.dp)) }
        val groups = listOf(first, tools, app).filter { it.isNotEmpty() }
        groups.forEachIndexed { gi, group ->
            item(key = "g$gi") {
                HorizontalDivider(Modifier.padding(top = 8.dp), color = NavyRule)
                group.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = e.onClick).padding(horizontal = 24.dp).testTag(e.tag),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(e.icon, null, tint = OnNavy, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(28.dp))
                        Text(stringResource(e.label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), color = OnNavy)
                    }
                }
            }
        }
        item { Spacer(Modifier.heightIn(min = 32.dp)) }
    }
}

/** Navy's top bar: ☰, the title centred in navy, the actions (the Bible's short name, α, search). */
@Composable
fun NavyTopBar(vm: AppViewModel?, title: String, titleModifier: Modifier, back: (() -> Unit)?, actions: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (back != null) IconButton(onClick = back, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
            } else if (vm != null) IconButton(onClick = { vm.drawerOpen = true }, modifier = Modifier.testTag("menu")) {
                Icon(Icons.Filled.Menu, stringResource(R.string.menu), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).then(titleModifier).padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            // keeps the title centred when there are no actions
            if (back != null) Spacer(Modifier.width(8.dp))
        }
    }
}

/** Navy's chapter buttons: two white rounds with a shadow, at the foot of the page. */
@Composable
fun BoxScope.NavyChapterButtons(vm: AppViewModel) {
    @Composable
    fun round(icon: ImageVector, label: String, tag: String, align: Alignment, onClick: () -> Unit) = Surface(
        onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.align(align).padding(16.dp).size(56.dp).testTag(tag),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(30.dp)) } }
    round(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_chapter), "prev-chapter", Alignment.BottomStart) { vm.previousChapter() }
    round(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_chapter), "next-chapter", Alignment.BottomEnd) { vm.nextChapter() }
}

/** Navy's chapter opening: the book in small letters, the number large, set to the left as in print. */
@Composable
fun NavyChapterHeader(book: String, chapter: Int) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 28.dp, bottom = 4.dp)) {
        Text(book.uppercase(), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$chapter", fontSize = 56.sp, lineHeight = 60.sp, fontFamily = LocalReading.current.text, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Suppress("unused") private val keepIcons = listOf(Icons.Outlined.AutoStories, Icons.Outlined.Church, Icons.Outlined.School)
