package mg.baiboly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppMode
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.app.Style

/*
 * The four themes are four designs, not four colourings. Each component that
 * differs asks LocalStyle and draws its own version:
 *
 *              Manuscript        Modern              Page               Glass
 *  top bar     title + gilt rule bold title, chips   thin line, ☰       coloured header + tabs
 *  navigation  bottom bar        floating pill       full-page menu     tabs in the header
 *  chapters    gilt discs        floating pill       links at the end   bottom bar ‹ · ›
 *  actions     bottom bar        floating toolbar    top action bar     tile panel
 *  settings    gilt sections     card groups         flat list          categories → pages
 */

// ---------------------------------------------------------------- navigation

/** Opens a main page, refreshing what it shows. */
fun navigateTo(vm: AppViewModel, screen: Screen) {
    if (screen == Screen.Map) vm.mapForChapter = true
    when (screen) {
        Screen.Library -> { vm.refreshNotes(); vm.refreshHighlights(); vm.refreshHistory(); vm.refreshSavedLists() }
        Screen.Notes -> vm.refreshNotes()
        Screen.Highlights -> vm.refreshHighlights()
        Screen.History -> vm.refreshHistory()
        Screen.Lists -> vm.refreshSavedLists()
        Screen.Search -> vm.search(vm.searchQuery, vm.translationId)
        else -> Unit
    }
    vm.screen = screen
}

/** The theme's navigation at the foot of a main page (Page and Glass have none there). */
@Composable
fun StyledNavigation(vm: AppViewModel) {
    when (LocalStyle.current) {
        Style.Manuscript -> AppNavigationBar(vm)
        Style.Modern -> FloatingNavigation(vm)
        Style.Page, Style.Glass -> Unit
    }
}

/** Modern: a floating capsule; the current page shows its name, the others only their icon. */
@Composable
private fun FloatingNavigation(vm: AppViewModel) {
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 6.dp) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                destinations(vm.mode).forEach { d ->
                    val on = vm.screen == d.screen
                    val label = stringResource(d.label)
                    Row(
                        Modifier.clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { navigateTo(vm, d.screen) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = if (on) 18.dp else 14.dp)
                            .testTag("nav:${d.screen.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(d.icon, if (on) null else label, tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (on) {
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Glass: the mode's pages as tabs inside the coloured header. */
@Composable
private fun HeaderTabs(vm: AppViewModel) {
    val items = destinations(vm.mode)
    val index = items.indexOfFirst { it.screen == vm.screen }.coerceAtLeast(0)
    val on = LocalContentColor.current
    val tabs: @Composable () -> Unit = {
        items.forEach { d ->
            Tab(
                selected = vm.screen == d.screen,
                onClick = { navigateTo(vm, d.screen) },
                text = { Text(stringResource(d.label), maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                selectedContentColor = on,
                unselectedContentColor = on.copy(alpha = 0.7f),
                modifier = Modifier.testTag("nav:${d.screen.name}"),
            )
        }
    }
    val indicator: @Composable (List<TabPosition>) -> Unit = { positions ->
        if (index < positions.size) TabRowDefaults.SecondaryIndicator(with(TabRowDefaults) { Modifier.tabIndicatorOffset(positions[index]) }, height = 3.dp, color = on)
    }
    if (items.size > 3) {
        ScrollableTabRow(selectedTabIndex = index, containerColor = Color.Transparent, contentColor = on, edgePadding = 8.dp,
            indicator = indicator, divider = {}) { tabs() }
    } else {
        TabRow(selectedTabIndex = index, containerColor = Color.Transparent, contentColor = on, indicator = indicator, divider = {}) { tabs() }
    }
}

// ---------------------------------------------------------------- top bars

/**
 * The top bar of a main page in the theme's own form. [title] opens
 * [onTitle] when given (the reader's chapter title opens the books).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTopBar(
    vm: AppViewModel,
    title: String,
    titleTag: String? = null,
    onTitle: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val style = LocalStyle.current
    val titleModifier = Modifier.clip(MaterialTheme.shapes.small)
        .let { if (onTitle != null) it.clickable(onClick = onTitle) else it }
        .let { if (titleTag != null) it.testTag(titleTag) else it }
    when (style) {
        Style.Manuscript -> Column {
            TopAppBar(
                navigationIcon = { ModeBadge(vm, compact = onTitle != null) },
                title = {
                    Row(titleModifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                        if (onTitle != null) Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { actions(); SettingsButton(vm) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            GoldRule()
        }
        Style.Modern -> Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeBadge(vm, compact = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).then(titleModifier).padding(horizontal = 6.dp, vertical = 4.dp),
                )
                actions()
                SettingsButton(vm)
            }
        }
        Style.Page -> Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.screen = Screen.Menu }, modifier = Modifier.testTag("menu")) {
                        Icon(Icons.Filled.Menu, stringResource(R.string.menu))
                    }
                    Text(
                        title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                        modifier = Modifier.weight(1f).then(titleModifier).padding(vertical = 8.dp),
                    )
                    actions()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        Style.Glass -> Surface(color = glassHeader(), contentColor = onGlassHeader(), shadowElevation = 4.dp) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeBadge(vm, compact = true)
                    Row(titleModifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                        if (onTitle != null) Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    actions()
                    SettingsButton(vm)
                }
                if (isTopLevel(Style.Glass, vm.mode, vm.screen)) HeaderTabs(vm)
            }
        }
    }
}

/** Glass header: the theme's colour by day, a deep tint of it at night. */
@Composable
private fun glassHeader(): Color = MaterialTheme.colorScheme.let { if (it.background.luminance() < 0.3f) it.primaryContainer else it.primary }

@Composable
private fun onGlassHeader(): Color = MaterialTheme.colorScheme.let { if (it.background.luminance() < 0.3f) it.onPrimaryContainer else it.onPrimary }

/** Top bar of a page reached from a main page: back arrow and title, in the theme's form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledSubBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val back: @Composable () -> Unit = {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }
    when (LocalStyle.current) {
        Style.Manuscript -> Column {
            TopAppBar(title = { Text(title) }, navigationIcon = back, actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
            GoldRule()
        }
        Style.Modern -> TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            navigationIcon = back, actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Style.Page -> Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    back()
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), modifier = Modifier.weight(1f))
                    actions()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        Style.Glass -> TopAppBar(
            title = { Text(title) }, navigationIcon = back, actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = glassHeader(), titleContentColor = onGlassHeader(),
                navigationIconContentColor = onGlassHeader(), actionIconContentColor = onGlassHeader(),
            ),
        )
    }
}

/** The reading-text chip in the reader's top bar, in the theme's form. */
@Composable
fun TranslationChip(label: String, onClick: () -> Unit) {
    val style = LocalStyle.current
    val content = LocalContentColor.current
    when (style) {
        Style.Page -> TextButton(onClick = onClick, modifier = Modifier.testTag("translation")) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Surface(
            onClick = onClick,
            shape = if (style == Style.Modern) CircleShape else MaterialTheme.shapes.small,
            color = when (style) {
                Style.Modern -> MaterialTheme.colorScheme.secondaryContainer
                else -> Color.Transparent
            },
            contentColor = if (style == Style.Modern) MaterialTheme.colorScheme.onSecondaryContainer else content,
            border = when (style) {
                Style.Manuscript -> BorderStroke(1.dp, LocalOrnament.current.gold.copy(alpha = 0.6f))
                Style.Glass -> BorderStroke(1.dp, content.copy(alpha = 0.6f))
                else -> null
            },
            modifier = Modifier.padding(horizontal = 2.dp).heightIn(min = 36.dp).testTag("translation"),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = if (style == Style.Modern) 14.dp else 8.dp, vertical = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- Page: the menu

private class MenuEntry(val screen: Screen, val icon: ImageVector, val label: Int, val units: Boolean = false)

/**
 * Page: no bars, so everything is here, grouped by purpose: the Bible,
 * the study tools (Study mode), what you have kept, and the app itself.
 */
@Composable
fun PageMenuScreen(vm: AppViewModel) {
    val groups = buildList<Pair<Int, List<MenuEntry>>> {
        add(R.string.group_bible to listOf(
            MenuEntry(Screen.Main, if (vm.mode == AppMode.Church) Icons.Outlined.Church else Icons.Outlined.AutoStories,
                if (vm.mode == AppMode.Church) R.string.nav_service else R.string.nav_read),
            MenuEntry(Screen.Books, Icons.Outlined.LibraryBooks, R.string.books),
            MenuEntry(Screen.Search, Icons.Outlined.Search, R.string.search),
        ))
        if (vm.mode == AppMode.Study) add(R.string.group_study to listOf(
            MenuEntry(Screen.Dictionary, Icons.Outlined.Translate, R.string.dictionary),
            MenuEntry(Screen.Dictionary, Icons.Outlined.Straighten, R.string.units, units = true),
            MenuEntry(Screen.Map, Icons.Outlined.Map, R.string.map),
        ))
        add(R.string.group_saved to listOf(
            MenuEntry(Screen.Lists, Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.nav_lists),
            MenuEntry(Screen.Notes, Icons.Outlined.EditNote, R.string.notes),
            MenuEntry(Screen.Highlights, Icons.Outlined.BorderColor, R.string.highlights),
            MenuEntry(Screen.History, Icons.Outlined.History, R.string.history),
        ).let { if (vm.mode == AppMode.Church) it else it.drop(1) + it.first() })
        add(R.string.group_app to listOf(
            MenuEntry(Screen.Settings, Icons.Outlined.Settings, R.string.settings),
            MenuEntry(Screen.Backup, Icons.Outlined.SettingsBackupRestore, R.string.backup),
            MenuEntry(Screen.About, Icons.Outlined.Info, R.string.about),
        ))
    }
    Scaffold(topBar = { StyledSubBar(stringResource(R.string.menu), onBack = { vm.screen = Screen.Main }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("menu-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    ChoiceRow(
                        label = stringResource(R.string.mode),
                        options = AppMode.entries.map { it to it.label() },
                        selected = vm.mode,
                        tag = "menu-mode",
                    ) { vm.changeMode(it); vm.screen = Screen.Menu }
                }
            }
            groups.forEach { (title, entries) ->
                item {
                    Text(
                        stringResource(title).uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                entries.forEach { e ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                                if (e.screen == Screen.Dictionary) vm.dictionaryUnits = e.units
                                navigateTo(vm, e.screen)
                            }.testTag(if (e.units) "nav:Units" else "nav:${e.screen.name}").padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(e.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(18.dp))
                            Text(stringResource(e.label), style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- chapters

/** Previous / next chapter laid over the text (Manuscript, Modern). */
@Composable
fun BoxScope.ChapterOverlay(vm: AppViewModel) {
    when (LocalStyle.current) {
        Style.Manuscript -> {
            ChapterDisc(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_chapter),
                Modifier.align(Alignment.BottomStart).padding(14.dp).testTag("prev-chapter")) { vm.previousChapter() }
            ChapterDisc(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_chapter),
                Modifier.align(Alignment.BottomEnd).padding(14.dp).testTag("next-chapter")) { vm.nextChapter() }
        }
        Style.Modern -> Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.previousChapter() }, modifier = Modifier.testTag("prev-chapter")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_chapter))
                }
                Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                IconButton(onClick = { vm.nextChapter() }, modifier = Modifier.testTag("next-chapter")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_chapter))
                }
            }
        }
        Style.Page, Style.Glass -> Unit
    }
}

/** Manuscript: a vellum disc with a gilt edge. */
@Composable
private fun ChapterDisc(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val gold = LocalOrnament.current.gold
    Surface(
        onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = gold,
        border = BorderStroke(1.dp, gold), shadowElevation = 3.dp, modifier = modifier.size(48.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label) } }
}

/** Page: plain links after the last verse, as at the foot of a printed page. */
@Composable
fun ChapterFooterLinks(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp)) {
        HorizontalDivider(Modifier.padding(horizontal = 48.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.previousChapter() }, modifier = Modifier.testTag("prev-chapter")) { Text("‹  " + stringResource(R.string.previous_chapter)) }
            TextButton(onClick = { vm.nextChapter() }, modifier = Modifier.testTag("next-chapter")) { Text(stringResource(R.string.next_chapter) + "  ›") }
        }
    }
}

/** Glass: a bottom bar with two square buttons and the chapter between them. */
@Composable
fun ChapterBottomBar(vm: AppViewModel, title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { vm.previousChapter() }, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("prev-chapter")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_chapter))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            FilledTonalButton(onClick = { vm.nextChapter() }, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("next-chapter")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_chapter))
            }
        }
    }
}

/** Chapter opening in the theme's form. */
@Composable
fun StyledChapterHeader(book: String, chapter: Int) {
    val ornament = LocalOrnament.current
    when (LocalStyle.current) {
        Style.Manuscript -> Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp, start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(book.uppercase(), style = MaterialTheme.typography.titleMedium, color = ornament.gold, letterSpacing = 4.sp, textAlign = TextAlign.Center)
            Text("$chapter", fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp, color = ornament.rubric)
            Fleuron()
        }
        Style.Modern -> Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text(book, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("$chapter", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        Style.Page -> Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp, start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(book, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$chapter", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Box(Modifier.padding(top = 10.dp).width(36.dp).height(1.dp).background(MaterialTheme.colorScheme.outline))
        }
        Style.Glass -> Surface(
            color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(book, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                Text("$chapter", fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Whether chapters open with a large coloured initial. */
@Composable
fun dropCaps(): Boolean = LocalStyle.current.let { it == Style.Manuscript || it == Style.Glass }

// ---------------------------------------------------------------- verse actions

class VerseAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Actions on the selected verses, in the theme's form. Page draws them as the
 * top bar (see [MainScreen]); the others at the foot of the reader.
 */
@Composable
fun StyledSelectionActions(
    count: Int,
    actions: List<VerseAction>,
    colours: Boolean,
    swatches: @Composable () -> Unit,
    onClear: () -> Unit,
) {
    val countText = stringResource(R.string.n_selected, "$count")
    when (LocalStyle.current) {
        Style.Manuscript -> Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                GoldRule()
                if (colours) swatches()
                // one row: close, the count, then the actions (scrolling when they do not fit)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                    Text(countText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 4.dp))
                    actions.forEach { a ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = a.onClick).widthIn(min = 52.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp).testTag("action:${a.label}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(a.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        Style.Modern -> Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, shadowElevation = 8.dp) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    if (colours) swatches()
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                        Text(countText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 6.dp))
                        actions.forEach { a ->
                            IconButton(onClick = a.onClick, modifier = Modifier.testTag("action:${a.label}")) { Icon(a.icon, a.label) }
                        }
                    }
                }
            }
        }
        Style.Page -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                    Text(countText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 4.dp))
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        actions.forEach { a ->
                            IconButton(onClick = a.onClick, modifier = Modifier.testTag("action:${a.label}")) { Icon(a.icon, a.label) }
                        }
                    }
                }
                if (colours) swatches()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
        }
        Style.Glass -> Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(countText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                }
                if (colours) swatches()
                // large tiles, four to a row
                actions.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { a ->
                            Surface(
                                onClick = a.onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("action:${a.label}"),
                            ) {
                                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(a.icon, null)
                                    Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- theme picker

@Composable
fun Style.label(): String = stringResource(
    when (this) {
        Style.Manuscript -> R.string.style_manuscript
        Style.Modern -> R.string.style_modern
        Style.Page -> R.string.style_page
        Style.Glass -> R.string.style_glass
    },
)

@Composable
fun Style.description(): String = stringResource(
    when (this) {
        Style.Manuscript -> R.string.style_manuscript_desc
        Style.Modern -> R.string.style_modern_desc
        Style.Page -> R.string.style_page_desc
        Style.Glass -> R.string.style_glass_desc
    },
)

/** Themes as small drawings of their reading page, so the choice is seen, not read. */
@Composable
fun StylePicker(vm: AppViewModel) {
    val dark = isDark(vm.theme)
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("styles"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Style.entries.forEach { st ->
                val on = st == vm.style
                Column(
                    Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.changeStyle(st) }.testTag("style:${st.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StyleThumbnail(st, dark, Modifier.size(width = 104.dp, height = 168.dp)
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp).clip(RoundedCornerShape(9.dp)))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                        Text(st.label(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        Text(vm.style.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp))
    }
}

/** A sketch of the theme's reader: where its bars, buttons and text sit. */
@Composable
private fun StyleThumbnail(style: Style, dark: Boolean, modifier: Modifier) {
    val (bg, accent, gold) = paletteSwatch(style.palettes.first(), dark)
    val ink = if (dark) Color(0xFFE6E2DA) else Color(0xFF2A2622)
    Canvas(modifier.background(bg)) {
        val w = size.width; val h = size.height; val u = w / 26f
        fun line(y: Float, x0: Float = 3 * u, x1: Float = w - 3 * u, c: Color = ink.copy(alpha = 0.35f)) =
            drawRoundRect(c, Offset(x0, y), Size(x1 - x0, u * 0.7f), CornerRadius(u / 3))
        fun text(top: Float, n: Int) { for (i in 0 until n) line(top + i * u * 1.7f, x1 = if (i == n - 1) w * 0.6f else w - 3 * u) }
        when (style) {
            Style.Manuscript -> {
                drawRoundRect(accent.copy(alpha = 0f), Offset.Zero, Size(w, 4 * u))
                line(1.6f * u, 3 * u, 12 * u, ink.copy(alpha = 0.6f))
                drawRect(gold, Offset(2 * u, 4.2f * u), Size(w - 4 * u, u * 0.2f))
                drawCircle(accent, u * 1.6f, Offset(w / 2, 8f * u))
                drawRect(gold, Offset(w / 2 - 5 * u, 10.6f * u), Size(10 * u, u * 0.2f))
                text(12.5f * u, 6)
                drawCircle(gold, u * 1.4f, Offset(3.5f * u, h - 9 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(u * 0.25f))
                drawCircle(gold, u * 1.4f, Offset(w - 3.5f * u, h - 9 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(u * 0.25f))
                drawRect(gold, Offset(0f, h - 6 * u), Size(w, u * 0.15f))
                for (i in 0 until 3) drawCircle(if (i == 0) accent else ink.copy(alpha = 0.4f), u * 0.9f, Offset(w * (i + 0.5f) / 3, h - 3 * u))
            }
            Style.Modern -> {
                drawRoundRect(accent.copy(alpha = 0.25f), Offset(2 * u, 1.4f * u), Size(3 * u, 3 * u), CornerRadius(1.5f * u))
                drawRoundRect(ink, Offset(6.5f * u, 1.8f * u), Size(11 * u, 2.2f * u), CornerRadius(u / 2))
                drawRoundRect(accent, Offset(3 * u, 7 * u), Size(5 * u, u), CornerRadius(u / 2))
                drawRoundRect(ink, Offset(3 * u, 8.8f * u), Size(4 * u, 3.2f * u), CornerRadius(u / 2))
                text(14f * u, 6)
                drawRoundRect(ink.copy(alpha = 0.12f), Offset(w - 9 * u, h - 11 * u), Size(7 * u, 3 * u), CornerRadius(1.5f * u))
                drawRoundRect(ink.copy(alpha = 0.14f), Offset(3 * u, h - 6 * u), Size(w - 6 * u, 4 * u), CornerRadius(2 * u))
                drawRoundRect(accent, Offset(3.5f * u, h - 5.5f * u), Size(8 * u, 3 * u), CornerRadius(1.5f * u))
            }
            Style.Page -> {
                for (i in 0 until 3) drawRect(ink.copy(alpha = 0.6f), Offset(2 * u, (1.4f + i * 0.8f) * u), Size(2.2f * u, u * 0.3f))
                line(1.8f * u, 9 * u, 17 * u, ink.copy(alpha = 0.5f))
                drawRect(ink.copy(alpha = 0.2f), Offset(0f, 4.2f * u), Size(w, u * 0.1f))
                line(7.5f * u, 10 * u, 16 * u, ink.copy(alpha = 0.5f))
                text(11f * u, 10)
                line(h - 4 * u, 3 * u, 9 * u, accent.copy(alpha = 0.7f))
                line(h - 4 * u, w - 9 * u, w - 3 * u, accent.copy(alpha = 0.7f))
            }
            Style.Glass -> {
                drawRect(accent, Offset.Zero, Size(w, 8 * u))
                line(1.8f * u, 6 * u, 15 * u, bg.copy(alpha = 0.9f))
                for (i in 0 until 3) line(5.4f * u, (2 + i * 8) * u, (7 + i * 8) * u, bg.copy(alpha = if (i == 0) 0.95f else 0.6f))
                drawRect(bg, Offset(2 * u, 7.4f * u), Size(5 * u, u * 0.5f))
                drawRoundRect(accent.copy(alpha = 0.18f), Offset(2 * u, 10 * u), Size(w - 4 * u, 4.5f * u), CornerRadius(u / 2))
                drawRoundRect(accent, Offset(w - 7 * u, 11 * u), Size(3 * u, 2.5f * u), CornerRadius(u / 3))
                text(17f * u, 6)
                drawRect(ink.copy(alpha = 0.08f), Offset(0f, h - 6 * u), Size(w, 6 * u))
                drawRoundRect(accent.copy(alpha = 0.35f), Offset(2 * u, h - 5 * u), Size(4 * u, 4 * u), CornerRadius(u / 2))
                drawRoundRect(accent.copy(alpha = 0.35f), Offset(w - 6 * u, h - 5 * u), Size(4 * u, 4 * u), CornerRadius(u / 2))
            }
        }
    }
}
