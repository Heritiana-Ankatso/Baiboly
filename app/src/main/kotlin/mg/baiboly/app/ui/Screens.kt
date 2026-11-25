package mg.baiboly.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.DensityLarge
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Info
import mg.baiboly.app.Tradition
import mg.baiboly.app.Palette
import mg.baiboly.app.TextFont
import mg.baiboly.app.LineSpacing
import mg.baiboly.app.VerseLayout
import mg.baiboly.app.PageMargin
import mg.baiboly.app.ParagraphBreak
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.app.ThemeMode
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.text.TextNorm
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- side menu

/** Theme and language, one tap each: in the menu and at the top of Settings. */
@Composable
fun QuickSettings(vm: AppViewModel, where: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(
            label = stringResource(R.string.theme),
            options = listOf(
                ThemeMode.Light to "☀  " + stringResource(R.string.theme_light),
                ThemeMode.Dark to "☾  " + stringResource(R.string.theme_dark),
                ThemeMode.System to "◐  " + stringResource(R.string.theme_device_short),
            ),
            selected = vm.theme,
            tag = "$where-theme",
        ) { vm.changeTheme(it) }
        ChoiceRow(
            label = stringResource(R.string.interface_language),
            options = listOf("mg" to "MG", "fr" to "FR", "en" to "EN"),
            selected = vm.uiLanguage,
            tag = "$where-ui-lang",
        ) { vm.chooseUiLanguage(it) }
    }
}

/**
 * A row of mutually exclusive choices, in the theme's form: engraved
 * segments (Manuscript), separate pills (Modern), underlined words (Page),
 * coloured blocks (Glass).
 */
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, tag: String, onSelect: (T) -> Unit) {
    val style = LocalStyle.current
    val scheme = MaterialTheme.colorScheme
    Column {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        when (style) {
            mg.baiboly.app.Style.Manuscript -> {
                val gold = LocalOrnament.current.gold
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).border(1.dp, gold.copy(alpha = 0.8f), RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))) {
                    options.forEach { (value, text) ->
                        val on = value == selected
                        Box(
                            Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) scheme.primary else Color.Transparent)
                                .clickable { onSelect(value) }.testTag("$tag:$value"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onPrimary else scheme.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            // one segmented track, the chosen option raised on it: separate black pills read as buttons to press
            mg.baiboly.app.Style.Modern, mg.baiboly.app.Style.Navy -> Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHighest).padding(3.dp),
            ) {
                options.forEach { (value, text) ->
                    val on = value == selected
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp)
                            .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) scheme.surface else Color.Transparent)
                            .clickable { onSelect(value) }.testTag("$tag:$value"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                            color = if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            mg.baiboly.app.Style.Page -> Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                options.forEach { (value, text) ->
                    val on = value == selected
                    Column(
                        Modifier.weight(1f).heightIn(min = 44.dp).clickable { onSelect(value) }.testTag("$tag:$value"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                            color = if (on) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(vertical = 8.dp))
                        Box(Modifier.fillMaxWidth().height(if (on) 2.dp else 0.5.dp).background(if (on) scheme.primary else scheme.outlineVariant))
                    }
                }
            }
            mg.baiboly.app.Style.Glass -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, text) ->
                    val on = value == selected
                    Box(
                        Modifier.weight(1f).heightIn(min = 46.dp).clip(MaterialTheme.shapes.small)
                            .background(if (on) scheme.primary else scheme.primaryContainer.copy(alpha = 0.55f))
                            .clickable { onSelect(value) }.testTag("$tag:$value"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, style = MaterialTheme.typography.labelMedium, color = if (on) scheme.onPrimary else scheme.onPrimaryContainer, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- common scaffold

/** True when a screen is shown inside another one (Library tabs): no top bar of its own. */
val LocalEmbedded = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Frame of a secondary page. Main pages of the current mode get the mode
 * badge and the Settings gear; other pages a back arrow; embedded pages
 * (inside Library) only their content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    vm: AppViewModel,
    title: String,
    actions: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    if (LocalEmbedded.current) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { actions() }
        }
        return
    }
    Scaffold(
        topBar = {
            if (onBack == null && isTopLevel(vm.style, vm.mode, vm.screen)) MainTopBar(vm, title, actions)
            else StyledSubBar(title, onBack = onBack ?: { vm.screen = backTarget(vm) }, actions = actions)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
private fun Empty(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

// ---------------------------------------------------------------- history

@Composable
fun HistoryScreen(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    SubScreen(vm, stringResource(R.string.history), actions = {
        if (vm.history.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.clear_history)) }
    }) { padding ->
        if (vm.history.isEmpty()) return@SubScreen Empty(stringResource(R.string.empty_history), padding)
        LazyColumn(contentPadding = padding) {
            items(vm.history, key = { "${it.passage}" }) { r ->
                ListRow(r.passage.longLabel(), formatTime(r.openedAt)) { vm.openPassage(r.passage) }
            }
        }
    }
    if (confirm) LocalizedAlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(stringResource(R.string.clear_history) + "?") },
        confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ListRow(title: String, subtitle: String?, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

// ---------------------------------------------------------------- notes

@Composable
fun NotesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<mg.baiboly.data.Note?>(null) }
    SubScreen(vm, stringResource(R.string.notes)) { padding ->
        if (vm.notes.isEmpty()) return@SubScreen Empty(stringResource(R.string.empty_notes), padding)
        LazyColumn(contentPadding = padding) {
            items(vm.notes, key = { it.id }) { n ->
                ListRow(
                    n.passage.longLabel(),
                    n.text,
                    trailing = { TextButton(onClick = { editing = n }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit_note)) } },
                ) { vm.openPassage(n.passage) }
            }
        }
    }
    editing?.let { n ->
        NoteDialog(
            title = n.passage.longLabel(),
            initial = n.text,
            onDelete = { vm.deleteNote(n.id); editing = null },
            onSave = { vm.updateNote(n.id, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

// ---------------------------------------------------------------- highlights

@Composable
fun HighlightsScreen(vm: AppViewModel) {
    var texts by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(vm.highlights) {
        texts = vm.highlights.associate { h ->
            val full = vm.versesText(h.passage, mg.baiboly.data.BibleRepository.PRIMARY)
            val s = h.charStart
            val e = h.charEnd
            h.id to if (s != null && e != null && e <= full.length && s < e) "“${full.substring(s, e)}”" else full
        }
    }
    SubScreen(vm, stringResource(R.string.highlights)) { padding ->
        if (vm.highlights.isEmpty()) return@SubScreen Empty(stringResource(R.string.empty_highlights), padding)
        LazyColumn(contentPadding = padding) {
            items(vm.highlights, key = { it.id }) { h ->
                ListRow(
                    h.passage.longLabel(),
                    texts[h.id],
                    leading = { Box(Modifier.padding(end = 12.dp).size(20.dp).background(Color(h.colour), CircleShape)) },
                    trailing = { IconButton(onClick = { vm.deleteHighlight(h.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove_highlight)) } },
                ) { vm.openPassage(h.passage) }
            }
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
fun SearchScreen(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SubScreen(vm, stringResource(R.string.search)) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.search(it) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.visibleTranslations, key = { it.id }) { t ->
                    FilterChip(
                        selected = t.id == vm.searchTranslation,
                        onClick = { vm.search(vm.searchQuery, t.id) },
                        label = { Text(t.shortName) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            val n = vm.searchResults.size
            if (vm.searchQuery.isNotBlank()) Text(
                when {
                    n == 0 -> stringResource(R.string.no_results)
                    n >= vm.searchLimit -> stringResource(R.string.results_limited, n)
                    else -> stringResource(R.string.results_count, n)
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val words = remember(vm.searchQuery) { TextNorm.norm(vm.searchQuery).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 } }
            val hl = MaterialTheme.colorScheme.primaryContainer
            val lang = vm.translations.firstOrNull { it.id == vm.searchTranslation }?.lang ?: "mg"
            LazyColumn {
                items(vm.searchResults, key = { "${it.book}-${it.chapter}-${it.verse}" }) { r ->
                    val p = Passage(r.book, r.chapter, r.verse, r.verse)
                    val annotated = remember(r, words) {
                        buildAnnotatedString {
                            append(r.text)
                            // mark matches on the accent-free form; NFC keeps the same length for these scripts
                            val norm = TextNorm.norm(r.text)
                            if (norm.length == r.text.length) for (w in words) {
                                var i = norm.indexOf(w)
                                while (i >= 0) {
                                    addStyle(SpanStyle(background = hl, fontWeight = FontWeight.SemiBold), i, i + w.length)
                                    i = norm.indexOf(w, i + w.length)
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            if (vm.translationId != vm.searchTranslation) vm.setTranslation(vm.searchTranslation)
                            vm.openPassage(p)
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(p.longLabel(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(annotated, fontFamily = TextSerif)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- saved lists

@Composable
fun SavedListsScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    SubScreen(vm, stringResource(R.string.saved_lists), actions = {
        TextButton(onClick = { vm.newList(); vm.screen = Screen.Main }) { Text(stringResource(R.string.new_list)) }
    }) { padding ->
        // The list in use is shown once it has a passage: an empty list is not a saved list, and it
        // could not be deleted anyway (a list is always in use, so deleting it made a new empty one).
        val shown = vm.savedLists.filter { it.id != vm.list.id || vm.list.items.isNotEmpty() }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_lists), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp).testTag("no-saved-lists"),
            )
        }
        LazyColumn(contentPadding = padding) {
            items(shown, key = { it.id }) { l ->
                val isCurrent = l.id == vm.list.id
                ListRow(
                    l.title + if (isCurrent) " · " + stringResource(R.string.current_list) else "",
                    l.date + " · " + stringResource(R.string.items_count, l.itemCount),
                    trailing = {
                        Row {
                            TextButton(onClick = { vm.duplicateSavedList(l.id, l.title) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.duplicate)) }
                            IconButton(onClick = { confirmDelete = l.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    },
                ) { vm.openSavedList(l.id) }
            }
        }
    }
    confirmDelete?.let { id ->
        LocalizedAlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.delete) + "?") },
            confirmButton = { TextButton(onClick = { vm.deleteSavedList(id); confirmDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- settings

/** A group of settings, on its own page; the Settings page lists the groups (every theme). */
private class SettingsGroup(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val content: @Composable () -> Unit)

/**
 * Settings: the light / dark theme and the interface language (changed most often) at the top,
 * then one row per group; each group opens its own page. Every theme works this way, each with
 * its own form of row (it used to be only Stained glass; the other themes showed every setting
 * on one long page).
 */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    // opened from the "new version" notification: straight to Advanced (Updates)
    LaunchedEffect(vm.openUpdateSettings) {
        if (vm.openUpdateSettings) { vm.settingsPage = "advanced"; vm.openUpdateSettings = false }
    }
    // the next visit starts again at the list of groups
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.settingsPage = null } }
    val groups = listOf(
        SettingsGroup("look", Icons.Outlined.Palette, stringResource(R.string.appearance)) { AppearanceSettings(vm) },
        SettingsGroup("bible", Icons.Outlined.AutoStories, stringResource(R.string.default_bible)) { DefaultBibleSettings(vm) },
        SettingsGroup("audio", Icons.Outlined.Headphones, stringResource(R.string.read_aloud)) { ReadAloudSettings(vm) },
    )
    val traditionContent: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            ChoiceRow(
                label = stringResource(R.string.tradition),
                options = listOf(Tradition.Protestant to stringResource(R.string.protestant), Tradition.Catholic to stringResource(R.string.catholic)),
                selected = vm.tradition,
                tag = "settings-tradition",
            ) { vm.changeTradition(it) }
            Text(stringResource(R.string.tradition_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
    val current = vm.settingsPage
    androidx.activity.compose.BackHandler(enabled = current != null) { vm.settingsPage = null }
    val title = when (current) {
        null -> stringResource(R.string.settings)
        "advanced" -> stringResource(R.string.advanced)
        else -> groups.firstOrNull { it.key == current }?.title ?: stringResource(R.string.settings)
    }
    SubScreen(vm, title, onBack = if (current != null) ({ vm.settingsPage = null }) else null) { padding ->
        Column(Modifier.padding(padding).testTag("settings-list").verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            when (current) {
                null -> {
                    QuickSettings(vm, "settings")
                    Spacer(Modifier.height(8.dp))
                    groups.forEach { g -> SettingsLink(g.icon, g.title, "settings-cat:${g.key}") { vm.settingsPage = g.key } }
                    SettingsLink(Icons.Outlined.SettingsBackupRestore, stringResource(R.string.backup), "settings-backup") { vm.screen = Screen.Backup }
                    SettingsLink(Icons.Outlined.Info, stringResource(R.string.about), "settings-about") { vm.screen = Screen.About }
                    // rarely changed: kept out of the way
                    SettingsLink(Icons.Outlined.MoreHoriz, stringResource(R.string.advanced), "settings-advanced") { vm.settingsPage = "advanced" }
                }
                "advanced" -> {
                    Collapsible(stringResource(R.string.tradition), "advanced-tradition", initiallyOpen = true, horizontal = 20.dp,
                        detail = stringResource(if (vm.tradition == Tradition.Protestant) R.string.protestant else R.string.catholic)) { traditionContent() }
                    Collapsible(stringResource(R.string.ql_title), "advanced-quick-look", horizontal = 20.dp) { QuickLookSettings(vm) }
                    Collapsible(stringResource(R.string.updates), "advanced-updates", horizontal = 20.dp) { UpdateSettings(vm) }
                }
                else -> groups.firstOrNull { it.key == current }?.content?.invoke()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Appearance: the theme and its colours, then what the text looks like (a live sample with its size),
 * then one row per setting showing its current value; a tap opens the few choices. Every choice
 * spelled out as a row of buttons filled the screen with ~20 buttons (reported).
 */
@Composable
private fun AppearanceSettings(vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(stringResource(R.string.style), style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        StylePicker(vm)
        PalettePicker(vm)

        // the text as it will read, and its size, together
        SettingsHeading(stringResource(R.string.text_size))
        val sample = vm.textSize * LocalTextScale.current
        Surface(
            shape = RoundedCornerShape(14.dp), color = scheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("settings-sample"),
        ) {
            Text(stringResource(R.string.sample_text), fontSize = sample.sp, lineHeight = (sample * LocalReading.current.lineSpacing).sp,
                fontFamily = TextSerif, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Slider(
                value = vm.textSize, onValueChange = { vm.changeTextSize(it) }, valueRange = mg.baiboly.app.MIN_TEXT..40f, steps = 14,
                // the 15 stops still snap, without a row of dots
                colors = androidx.compose.material3.SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("settings-text-size"),
            )
            Text("A", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
        }

        SettingsHeading(stringResource(R.string.appearance))
        SettingRow(
            label = stringResource(R.string.text_font),
            options = listOf(
                TextFont.Classic to stringResource(R.string.font_classic),
                TextFont.Modern to stringResource(R.string.font_modern),
                TextFont.Sans to stringResource(R.string.font_sans),
            ),
            selected = vm.textFont, tag = "settings-font",
        ) { vm.changeTextFont(it) }
        SettingRow(
            label = stringResource(R.string.titles),
            options = listOf(true to stringResource(R.string.titles_engraved), false to stringResource(R.string.titles_plain)),
            selected = vm.engravedTitles, tag = "settings-titles",
        ) { vm.changeEngravedTitles(it) }
        // how the text is set on the page, in one group that unfolds: layout, flow, alignment, word breaks, spacing
        Collapsible(stringResource(R.string.text_formatting), "settings-text-formatting", horizontal = 20.dp,
            detail = textFormattingSummary(vm)) { TextFormattingSettings(vm) }
        // the size of everything else: bars, buttons, lists
        SettingRow(
            label = stringResource(R.string.ui_density),
            options = listOf(mg.baiboly.app.UiDensity.Compact to stringResource(R.string.ui_compact), mg.baiboly.app.UiDensity.Normal to stringResource(R.string.ui_normal)),
            selected = vm.uiDensity, tag = "settings-density",
        ) { vm.changeUiDensity(it) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.changeKeepScreenOn(!vm.keepScreenOn) }
                .padding(horizontal = 20.dp, vertical = 8.dp).testTag("settings-keep-awake"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.keep_screen_on_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = vm.keepScreenOn, onCheckedChange = null)
        }
        TextButton(onClick = { vm.resetAppearance() }, modifier = Modifier.padding(start = 12.dp, top = 4.dp).testTag("settings-reset-look")) {
            Text(stringResource(R.string.reset_appearance))
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalOrnament.current.rubric,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp).semantics { heading() })
}

/**
 * One setting as one row: its name, its current value under it, and a menu of the choices on a tap
 * (the usual Android list setting). The row is tagged [tag], each choice "[tag]:value".
 */
@Composable
fun <T> SettingRow(label: String, options: List<Pair<T, String>>, selected: T, tag: String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 8.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false },
            offset = androidx.compose.ui.unit.DpOffset(20.dp, 0.dp)) {
            options.forEach { (value, text) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { if (value == selected) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                    onClick = { onSelect(value); open = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("$tag:$value"),
                )
            }
        }
    }
}

@Composable
private fun DefaultBibleSettings(vm: AppViewModel) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.default_bible_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val langName = mapOf("mg" to "Malagasy", "fr" to "Français", "en" to "English")
        vm.visibleTranslations.groupBy { it.lang }.forEach { (lang, ts) ->
            Text(langName[lang] ?: lang, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            ts.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.setDefaultTranslation(t.id) }.testTag("settings-default:${t.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.defaultTranslationId == t.id, onClick = { vm.setDefaultTranslation(t.id) })
                    Column {
                        Text("${t.name} (${t.shortName})", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(styleNote(t), translationNote(t)).joinToString(". "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Colour themes as small pages: each swatch shows the theme's paper, its
 * accent and its gilt, in the day or night version currently shown.
 */
@Composable
private fun PalettePicker(vm: AppViewModel) {
    val dark = isDark(vm.theme)
    Text(stringResource(R.string.colours), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("palettes"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        vm.style.palettes.forEach { p ->
            val (bg, accent, gold) = paletteSwatch(p, dark)
            val on = p == vm.palette
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.changePalette(p) }.testTag("palette:${p.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 64.dp, height = 84.dp)
                        .border(if (on) 2.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else gold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(3.dp).clip(RoundedCornerShape(8.dp)).background(bg),
                ) {
                    // a tiny page: a capital in the accent colour, three lines of text, a gilt rule
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(gold))
                        repeat(3) { i ->
                            Box(Modifier.fillMaxWidth(if (i == 2) 0.6f else 1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.35f)))
                        }
                    }
                    if (on) Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp))
                }
                Text(p.label(), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
fun Palette.label(): String = stringResource(
    when (this) {
        Palette.Vellum -> R.string.palette_vellum
        Palette.Lapis -> R.string.palette_lapis
        Palette.Olive -> R.string.palette_olive
        Palette.Purple -> R.string.palette_purple
        Palette.Sepia -> R.string.palette_sepia
        Palette.Paper -> R.string.palette_paper
        Palette.Slate -> R.string.palette_slate
        Palette.Navy -> R.string.palette_navy
    },
)

/** A row that opens another page (or a group), in the theme's form. */
@Composable
private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tag: String, onClick: () -> Unit) {
    when (LocalStyle.current) {
        mg.baiboly.app.Style.Glass -> Surface(
            onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag(tag),
        ) {
            Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        mg.baiboly.app.Style.Modern -> Surface(
            onClick = onClick, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag(tag),
        ) {
            Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> {
            val page = LocalStyle.current == mg.baiboly.app.Style.Page || LocalStyle.current == mg.baiboly.app.Style.Navy
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).testTag(tag).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (page) MaterialTheme.colorScheme.onSurfaceVariant else LocalOrnament.current.gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(if (page) 18.dp else 12.dp))
                Text(title, style = if (page) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (page) HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

/** Reading aloud: off by default; switching it on asks for the notification permission (Android 13+). */
@Composable
private fun ReadAloudSettings(vm: AppViewModel) {
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                val on = !vm.readAloudEnabled
                vm.enableReadAloud(on)
                if (on && android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }.testTag("settings-read-aloud"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.read_aloud_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = vm.readAloudEnabled, onCheckedChange = null)
        }
        if (vm.readAloudEnabled) {
            Text(stringResource(R.string.reading_speed), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(value = vm.readRate, onValueChange = { vm.changeReadRate(it) }, valueRange = 0.6f..1.6f, steps = 9)
            Text(stringResource(R.string.malagasy_voice_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Updates (in Advanced): the only part of the app that goes online. "Check now"
 * runs on a tap; the switch (off by default) checks about once a day and shows
 * a silent notification when a new version is out.
 */
/** One line saying how the text is set now: "Paragraphs · continuous · justified". */
@Composable
private fun textFormattingSummary(vm: AppViewModel): String = listOf(
    stringResource(if (vm.verseLayout == VerseLayout.Paragraphs) R.string.layout_paragraphs else R.string.layout_lines),
    stringResource(if (vm.continuousReading) R.string.flow_continuous else R.string.flow_chapters),
    stringResource(if (vm.justify) R.string.align_justify else R.string.align_left),
).joinToString(" · ")

/** Text formatting: verse layout, continuous reading, alignment (with the usual icons), word breaks, paragraphs, margins, spacing. */
@Composable
private fun TextFormattingSettings(vm: AppViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag("settings-typesetting"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconChoiceRow(
            label = stringResource(R.string.verse_layout),
            options = listOf(
                Triple(VerseLayout.Lines, Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.layout_lines)),
                Triple(VerseLayout.Paragraphs, Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.layout_paragraphs)),
            ),
            selected = vm.verseLayout, tag = "settings-layout",
        ) { vm.changeVerseLayout(it) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.changeContinuousReading(!vm.continuousReading) }.testTag("settings-continuous"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.continuous_reading), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.continuous_reading_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = vm.continuousReading, onCheckedChange = null)
        }
        Text(stringResource(R.string.typesetting_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        IconChoiceRow(
            label = stringResource(R.string.text_align),
            options = listOf(
                Triple(false, Icons.AutoMirrored.Filled.FormatAlignLeft, stringResource(R.string.align_left)),
                Triple(true, Icons.Filled.FormatAlignJustify, stringResource(R.string.align_justify)),
            ),
            selected = vm.justify, tag = "settings-justify",
        ) { vm.changeJustify(it) }
        IconChoiceRow(
            label = stringResource(R.string.hyphenation),
            options = listOf(
                Triple(false, Icons.AutoMirrored.Filled.WrapText, stringResource(R.string.hyph_off)),
                Triple(true, Icons.Filled.HorizontalRule, stringResource(R.string.hyph_on)),
            ),
            selected = vm.hyphenate, tag = "settings-hyphenate",
        ) { vm.changeHyphenate(it) }
        IconChoiceRow(
            label = stringResource(R.string.paragraph_break),
            options = listOf(
                Triple(ParagraphBreak.Indent, Icons.AutoMirrored.Filled.FormatIndentIncrease, stringResource(R.string.break_indent)),
                Triple(ParagraphBreak.Space, Icons.Filled.FormatLineSpacing, stringResource(R.string.break_space)),
            ),
            selected = vm.paragraphBreak, tag = "settings-paragraph-break",
        ) { vm.changeParagraphBreak(it) }
        SegmentedChoiceRow(
            label = stringResource(R.string.margins),
            options = listOf(
                ChoiceOption(PageMargin.Narrow, stringResource(R.string.margin_narrow)) { c -> MarginGlyph(0.14f, c) },
                ChoiceOption(PageMargin.Normal, stringResource(R.string.margin_normal)) { c -> MarginGlyph(0.26f, c) },
                ChoiceOption(PageMargin.Wide, stringResource(R.string.margin_wide)) { c -> MarginGlyph(0.38f, c) },
            ),
            selected = vm.pageMargin, tag = "settings-margins",
        ) { vm.changePageMargin(it) }
        IconChoiceRow(
            label = stringResource(R.string.line_spacing),
            options = listOf(
                Triple(LineSpacing.Compact, Icons.Filled.DensitySmall, stringResource(R.string.spacing_compact)),
                Triple(LineSpacing.Normal, Icons.Filled.DensityMedium, stringResource(R.string.spacing_normal)),
                Triple(LineSpacing.Airy, Icons.Filled.DensityLarge, stringResource(R.string.spacing_airy)),
            ),
            selected = vm.lineSpacing, tag = "settings-spacing",
        ) { vm.changeLineSpacing(it) }
    }
}

/** One choice of a [SegmentedChoiceRow]: its value, the word under it and the drawing above it (tinted with the colour given). */
class ChoiceOption<T>(val value: T, val label: String, val glyph: @Composable (Color) -> Unit)

/** Width of every segmented control, whatever the number of choices, so all rows line up. */
private val ChoiceControlWidth = 216.dp

/**
 * A setting as a label on the left and a segmented control on the right. The controls all have the same
 * width and height (two or three equal segments), so the section reads as one column.
 */
@Composable
fun <T> SegmentedChoiceRow(label: String, options: List<ChoiceOption<T>>, selected: T, tag: String, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Row(
            Modifier.width(ChoiceControlWidth).height(56.dp).clip(RoundedCornerShape(14.dp))
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)).selectableGroup(),
        ) {
            options.forEachIndexed { i, o ->
                val on = o.value == selected
                if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(scheme.outlineVariant))
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (on) scheme.primaryContainer else Color.Transparent)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o.value) })
                        .testTag("$tag:${o.value}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    val c = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    o.glyph(c)
                    Text(o.label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    }
}

/** Same as [SegmentedChoiceRow] with a Material icon as the drawing. */
@Composable
fun <T> IconChoiceRow(label: String, options: List<Triple<T, androidx.compose.ui.graphics.vector.ImageVector, String>>, selected: T, tag: String, onSelect: (T) -> Unit) =
    SegmentedChoiceRow(label, options.map { (v, icon, text) -> ChoiceOption(v, text) { c -> Icon(icon, null, tint = c, modifier = Modifier.size(22.dp)) } }, selected, tag, onSelect)

/** A page seen from the front: the text block sits [inset] (share of the width) from each side, so the margin is what is drawn. */
@Composable
private fun MarginGlyph(inset: Float, color: Color) {
    Canvas(Modifier.size(width = 24.dp, height = 22.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color, Offset.Zero, Size(w, h), CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        val x0 = w * inset; val x1 = w - x0
        for (i in 0 until 4) {
            val y = h * (0.24f + i * 0.17f)
            drawLine(color, Offset(x0, y), Offset(if (i == 3) x0 + (x1 - x0) * 0.6f else x1, y), strokeWidth = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
private fun UpdateSettings(vm: AppViewModel) {
    val ctx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("settings-updates")) {
        Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                val on = !vm.updateAuto
                vm.changeUpdateAuto(on)
                if (on && android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }.testTag("settings-update-auto"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.update_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = vm.updateAuto, onCheckedChange = null)
        }
        val status = vm.updateStatus
        OutlinedButton(
            onClick = { vm.checkForUpdates() },
            enabled = status != AppViewModel.UpdateStatus.Checking,
            modifier = Modifier.heightIn(min = 48.dp).testTag("settings-update-check"),
        ) { Text(stringResource(if (status == AppViewModel.UpdateStatus.Checking) R.string.update_checking else R.string.update_check_now)) }
        val line = when (status) {
            AppViewModel.UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date, vm.versionName)
            AppViewModel.UpdateStatus.Failed -> stringResource(R.string.update_failed)
            is AppViewModel.UpdateStatus.Available -> stringResource(R.string.update_available, status.release.version)
            is AppViewModel.UpdateStatus.Downloading -> stringResource(R.string.update_downloading, status.release.version)
            is AppViewModel.UpdateStatus.NeedsPermission -> stringResource(R.string.update_needs_permission)
            is AppViewModel.UpdateStatus.Installing -> stringResource(R.string.update_installing, status.release.version)
            is AppViewModel.UpdateStatus.InstallFailed -> stringResource(
                if (status.reason == "checksum" || status.reason == "not this app" || status.reason == "not newer") R.string.update_bad_file
                else if (status.apk != null) R.string.update_install_failed else R.string.update_download_failed,
            )
            else -> null
        }
        line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp).testTag("settings-update-status")) }
        // the system page where Android lets this app install its updates (asked once)
        val allowInstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onInstallPermissionResult() }
        when (status) {
            is AppViewModel.UpdateStatus.Available -> {
                val rel = status.release
                if (rel.apkUrl != null) {
                    Button(
                        onClick = { vm.downloadAndInstallUpdate(rel) },
                        modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp).testTag("settings-update-install"),
                    ) {
                        Text(
                            if (rel.apkSize > 0) stringResource(R.string.update_install_size, "%.1f".format(rel.apkSize / 1_048_576.0))
                            else stringResource(R.string.update_install),
                        )
                    }
                } else {
                    // a release without an APK attached: only its page can help
                    Button(
                        onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(rel.url))) } },
                        modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.update_open_page)) }
                }
            }
            is AppViewModel.UpdateStatus.Downloading -> {
                if (status.progress < 0f) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                else androidx.compose.material3.LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("settings-update-progress"),
                )
                if (status.progress >= 0f) Text("${(status.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.cancelUpdateDownload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            }
            is AppViewModel.UpdateStatus.NeedsPermission -> {
                Button(
                    onClick = { runCatching { allowInstall.launch(mg.baiboly.app.update.UpdateInstaller.permissionIntent(ctx)) } },
                    modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp).testTag("settings-update-allow"),
                ) { Text(stringResource(R.string.update_allow)) }
            }
            is AppViewModel.UpdateStatus.Installing -> {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                TextButton(onClick = { vm.installUpdate(status.release, status.apk) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_install_again))
                }
            }
            is AppViewModel.UpdateStatus.InstallFailed -> {
                Button(onClick = { vm.retryUpdate(status) }, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp).testTag("settings-update-retry")) {
                    Text(stringResource(R.string.update_try_again))
                }
            }
            else -> Unit
        }
        if (vm.updateLastCheck > 0) {
            Text(
                stringResource(R.string.update_last_check,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(Date(vm.updateLastCheck))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(stringResource(R.string.update_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------- backup

@Composable
fun BackupScreen(vm: AppViewModel) {
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    SubScreen(vm, stringResource(R.string.backup)) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.backup_intro))
            Button(onClick = { export.launch("baiboly-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(Date())}.json") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.export_backup)) }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.import_backup)) }
        }
    }
    pendingImport?.let { uri ->
        LocalizedAlertDialog(
            onDismissRequest = { pendingImport = null },
            text = { Text(stringResource(R.string.import_confirm)) },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); pendingImport = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- about

@Composable
fun AboutScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val sources = remember {
        runCatching {
            val arr = JSONArray(ctx.assets.open("licences.json").use { it.readBytes().decodeToString() })
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    SubScreen(vm, stringResource(R.string.about)) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontFamily = TextSerif)
            Text(stringResource(R.string.version_x, version), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_text), modifier = Modifier.padding(vertical = 12.dp))
            var notes by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { notes = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.whats_new)) }
            if (notes) WhatsNewSheet(vm.versionName, onDismiss = { notes = false })
            Text(stringResource(R.string.updates_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.reading_texts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            vm.translations.forEach { t ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${t.name} (${t.shortName})", fontWeight = FontWeight.SemiBold)
                    Text(t.licence, style = MaterialTheme.typography.bodySmall)
                    Text(t.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    translationNote(t)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(stringResource(R.string.sources_licences), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            sources.forEach { s ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(s.optString("title"), fontWeight = FontWeight.SemiBold)
                    Text(s.optString("source"), style = MaterialTheme.typography.bodySmall)
                    Text(s.optString("licence"), style = MaterialTheme.typography.bodySmall)
                    Text(s.optString("url"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.optString("origin").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                stringResource(R.string.about_tools),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Settings, Advanced: what the quick look shows with selected verses, and in which Bible. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickLookSettings(vm: AppViewModel) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("settings-quick-look")) {
        Text(stringResource(R.string.ql_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ql_settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QUICK_LOOK_ITEMS.forEach { item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.changeQuickLookItem(item, item !in vm.quickLookItems) }
                    .testTag("settings-ql:$item"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(quickLookItemLabel(item), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = item in vm.quickLookItems, onCheckedChange = null)
            }
        }
        if ("translation" in vm.quickLookItems) {
            Text(stringResource(R.string.ql_bible), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.FilterChip(selected = vm.quickLookTranslation.isEmpty(), onClick = { vm.changeQuickLookTranslation("") },
                    label = { Text(stringResource(R.string.ql_bible_auto)) }, modifier = Modifier.testTag("settings-ql-bible:auto"))
                vm.visibleTranslations.forEach { t ->
                    androidx.compose.material3.FilterChip(selected = vm.quickLookTranslation == t.id, onClick = { vm.changeQuickLookTranslation(t.id) },
                        label = { Text(t.shortName) }, modifier = Modifier.testTag("settings-ql-bible:${t.id}"))
                }
            }
        }
    }
}
