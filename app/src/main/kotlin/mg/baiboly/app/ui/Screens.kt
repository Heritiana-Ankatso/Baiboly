package mg.baiboly.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import mg.baiboly.app.AppMode
import mg.baiboly.app.Tradition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

/** A row of mutually exclusive choices, engraved style: gilt outline, oxblood when chosen. */
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, tag: String, onSelect: (T) -> Unit) {
    val gold = LocalOrnament.current.gold
    Column {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).border(1.dp, gold.copy(alpha = 0.8f), RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)),
        ) {
            options.forEachIndexed { i, (value, text) ->
                val on = value == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(value) }
                        .testTag("$tag:$value"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (i < options.lastIndex) Box(Modifier.width(1.dp).heightIn(min = 44.dp).background(gold.copy(alpha = 0.5f)))
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
            if (isTopLevel(vm.mode, vm.screen)) MainTopBar(vm, title, actions)
            else TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Main }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { actions() },
            )
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
    if (confirm) AlertDialog(
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
        LazyColumn(contentPadding = padding) {
            items(vm.savedLists, key = { it.id }) { l ->
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
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.delete) + "?") },
            confirmButton = { TextButton(onClick = { vm.deleteSavedList(id); confirmDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var advanced by remember { mutableStateOf(false) }
    SubScreen(vm, stringResource(R.string.settings)) { padding ->
        Column(Modifier.padding(padding).testTag("settings-list").verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            SettingsSection(Icons.Outlined.Tune, stringResource(R.string.mode)) {
                ChoiceRow(
                    label = "",
                    options = AppMode.entries.map { it to it.label() },
                    selected = vm.mode,
                    tag = "settings-mode",
                ) { vm.changeMode(it); vm.screen = Screen.Settings }
            }
            SettingsSection(Icons.Outlined.Palette, stringResource(R.string.appearance)) {
                QuickSettings(vm, "settings")
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                Slider(value = vm.textSize, onValueChange = { vm.changeTextSize(it) }, valueRange = 14f..40f, steps = 12,
                    modifier = Modifier.padding(horizontal = 20.dp))
                Text(stringResource(R.string.sample_text), fontSize = vm.textSize.sp, lineHeight = (vm.textSize * 1.55f).sp,
                    fontFamily = TextSerif, modifier = Modifier.padding(horizontal = 20.dp))
            }
            SettingsSection(Icons.Outlined.AutoStories, stringResource(R.string.default_bible)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.default_bible_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val langName = mapOf("mg" to "Malagasy", "fr" to "Français", "en" to "English")
                    vm.visibleTranslations.groupBy { it.lang }.forEach { (lang, ts) ->
                        Text(langName[lang] ?: lang, style = MaterialTheme.typography.titleSmall, color = LocalOrnament.current.gold,
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
            SettingsSection(Icons.Outlined.Headphones, stringResource(R.string.read_aloud)) { ReadAloudSettings(vm) }
            SettingsLink(Icons.Outlined.SettingsBackupRestore, stringResource(R.string.backup), "settings-backup") { vm.screen = Screen.Backup }
            SettingsLink(Icons.Outlined.Info, stringResource(R.string.about), "settings-about") { vm.screen = Screen.About }
            // Rarely changed: kept out of the way.
            SettingsLink(Icons.Outlined.ExpandMore.takeIf { !advanced } ?: Icons.Outlined.ExpandLess, stringResource(R.string.advanced), "settings-advanced") {
                advanced = !advanced
            }
            if (advanced) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ChoiceRow(
                        label = stringResource(R.string.tradition),
                        options = listOf(Tradition.Protestant to stringResource(R.string.protestant), Tradition.Catholic to stringResource(R.string.catholic)),
                        selected = vm.tradition,
                        tag = "settings-tradition",
                    ) { vm.changeTradition(it) }
                    Text(stringResource(R.string.tradition_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** A titled group of settings with its icon. */
@Composable
private fun SettingsSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalOrnament.current.gold, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
        GoldRule(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

/** A row that opens another page. */
@Composable
private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).testTag(tag).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalOrnament.current.gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        AlertDialog(
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
