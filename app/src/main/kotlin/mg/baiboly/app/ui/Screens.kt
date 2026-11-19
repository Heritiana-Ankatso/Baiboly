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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun SideMenu(vm: AppViewModel, close: () -> Unit) {
    // Both testaments start expanded (each collapsible), so any chapter is three taps away:
    // menu button, book, chapter.
    var openTestaments by remember { mutableStateOf(setOf(true, false)) } // true = OT, false = NT
    var openBook by remember { mutableStateOf<Int?>(null) }
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    ModalDrawerSheet(Modifier.width(320.dp)) {
        LazyColumn(Modifier.testTag("menu-list"), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                Text(
                    stringResource(R.string.books_of_bible),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            for (ot in listOf(true, false)) {
                item(key = "t$ot") {
                    MenuRow(
                        stringResource(if (ot) R.string.old_testament else R.string.new_testament),
                        expanded = ot in openTestaments,
                    ) { openTestaments = if (ot in openTestaments) openTestaments - ot else openTestaments + ot; openBook = null }
                }
                if (ot in openTestaments) {
                    items(Canon.books.filter { it.isOldTestament == ot }, key = { "b${it.id}" }) { b ->
                        Column {
                            MenuRow(b.name(lang), indent = 16.dp, expanded = openBook == b.id, tag = "menu-book:${b.id}") {
                                openBook = if (openBook == b.id) null else b.id
                            }
                            if (openBook == b.id) {
                                ChapterChips(b.id) { c -> vm.openChapter(b.id, c); close() }
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val entries = listOf(
                    R.string.history to Screen.History,
                    R.string.notes to Screen.Notes,
                    R.string.highlights to Screen.Highlights,
                    R.string.saved_lists to Screen.Lists,
                    R.string.backup to Screen.Backup,
                    R.string.settings to Screen.Settings,
                    R.string.about to Screen.About,
                )
                entries.forEach { (label, screen) ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(label)) },
                        selected = false,
                        onClick = {
                            when (screen) {
                                Screen.History -> vm.refreshHistory()
                                Screen.Notes -> vm.refreshNotes()
                                Screen.Highlights -> vm.refreshHighlights()
                                Screen.Lists -> vm.refreshSavedLists()
                                else -> Unit
                            }
                            vm.screen = screen
                            close()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, indent: androidx.compose.ui.unit.Dp = 0.dp, expanded: Boolean, tag: String = "", onClick: () -> Unit) {
    Row(
        Modifier.testTag(tag).fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(start = 24.dp + indent, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (expanded) FontWeight.Bold else FontWeight.Normal)
        Icon(if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

/** Chapter numbers in rows of six, so any chapter is three taps from the menu button. */
@Composable
private fun ChapterChips(book: Int, onChapter: (Int) -> Unit) {
    val n = Canon.chapterCount(book)
    Column(Modifier.padding(start = 36.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..n).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { c -> Key("$c", Modifier.width(48.dp).testTag("menu-chapter:$c")) { onChapter(c) } }
            }
        }
    }
}

// ---------------------------------------------------------------- common scaffold

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
    Scaffold(
        topBar = {
            TopAppBar(
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
                items(vm.translations, key = { it.id }) { t ->
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
                        Text(annotated, fontFamily = FontFamily.Serif)
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
    SubScreen(vm, stringResource(R.string.settings)) { padding ->
        Column(Modifier.padding(padding).testTag("settings-list").verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleMedium)
            Slider(value = vm.textSize, onValueChange = { vm.changeTextSize(it) }, valueRange = 14f..40f, steps = 12)
            Text(stringResource(R.string.sample_text), fontSize = vm.textSize.sp, lineHeight = (vm.textSize * 1.5f).sp, fontFamily = FontFamily.Serif)
            Spacer(Modifier.heightIn(min = 16.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            listOf(ThemeMode.System to R.string.theme_system, ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark).forEach { (m, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.changeTheme(m) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = vm.theme == m, onClick = { vm.changeTheme(m) })
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.choose_translation), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            vm.translations.forEach { t ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.setTranslation(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = vm.translationId == t.id, onClick = { vm.setTranslation(t.id) })
                    Column {
                        Text("${t.name} (${t.shortName})")
                        Text(
                            styleNote(t) + (translationNote(t)?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.translation_note_numbering),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(stringResource(R.string.interface_language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            mg.baiboly.app.UiLanguage.choices.forEach { lang ->
                val label = when (lang) {
                    "" -> stringResource(R.string.language_system)
                    "mg" -> "Malagasy"
                    "fr" -> "Français"
                    else -> "English"
                }
                val choose = { if (vm.uiLanguage != lang) vm.chooseUiLanguage(lang) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = choose).testTag("ui-lang:$lang"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.uiLanguage == lang, onClick = choose)
                    Text(label)
                }
            }
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
            Text(stringResource(R.string.version_x, version), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_text), modifier = Modifier.padding(vertical = 12.dp))
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
