package mg.baiboly.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.Note
import kotlin.math.abs

/** Dialog state for adding or editing a note. */
private data class NoteDraft(val passage: Passage, val initial: String, val noteId: Long? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, openMenu: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var showBrowse by remember { mutableStateOf(false) }
    var studyVerse by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var wordsVerse by remember { mutableStateOf<Int?>(null) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }
    var xrefVerse by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Keep the screen awake while the main screen is open (brief section 5).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(vm.location.book, vm.location.chapter, vm.translationId) { selected = emptySet() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    val loc = vm.location
    val translation = vm.translations.firstOrNull { it.id == vm.translationId }

    fun selectedPassages(): List<Passage> =
        ReferenceParser.mergeRanges(selected.sorted().map { it..it }).map { Passage(loc.book, loc.chapter, it.first, it.last) }

    Scaffold(
        topBar = { ReaderTopBar(vm, openMenu) },
        bottomBar = {
            if (selected.isNotEmpty()) {
                SelectionBar(
                    vm = vm,
                    count = selected.size,
                    hasHighlight = vm.chapterHighlights.any { h -> selected.any { h.passage.contains(it) } },
                    onCopy = {
                        val ps = selectedPassages()
                        scope.launch {
                            val body = ps.map { vm.versesText(it) }.joinToString(" ")
                            val ref = Passage.describe(ps, translation?.lang ?: "mg")
                            clipboard.setText(AnnotatedString("$body\n— $ref (${translation?.shortName ?: ""})"))
                            vm.message = ctx.getString(R.string.copied)
                            selected = emptySet()
                        }
                    },
                    onNote = {
                        val ps = selectedPassages()
                        val p = Passage(loc.book, loc.chapter, ps.first().verseStart, ps.last().verseEnd)
                        noteDraft = NoteDraft(p, "")
                    },
                    onHighlight = { colour -> vm.addHighlight(selectedPassages(), colour); selected = emptySet() },
                    onRemoveHighlight = { vm.removeHighlights(selected); selected = emptySet() },
                    onWords = if (selected.size == 1) ({ wordsVerse = selected.first() }) else null,
                    onCrossRefs = if (selected.size == 1) ({ xrefVerse = selected.first(); selected = emptySet() }) else null,
                    onListen = if (vm.readAloudEnabled) ({ vm.startReading(selected.min()); selected = emptySet() }) else null,
                    onAddToList = { vm.addToList(selectedPassages()); selected = emptySet() },
                    onClear = { selected = emptySet() },
                )
            } else {
                Column {
                    ReadingBar(vm)
                    ActionBar(onAdd = { showAdd = true }, onBrowse = { showBrowse = true })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListStrip(vm, onStartList = { showAdd = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Reader(
                    vm = vm,
                    selected = selected,
                    onVerseTap = { v -> selected = if (v in selected) selected - v else selected + v },
                    onNumberTap = { v ->
                        if (vm.studyMode) studyVerse = v
                        else selected = if (v in selected) selected - v else selected + v
                    },
                    onNoteTap = { n -> noteDraft = NoteDraft(n.passage, n.text, n.id) },
                )
                ChapterButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_chapter),
                    Modifier.align(Alignment.BottomStart).padding(14.dp).testTag("prev-chapter")) { vm.previousChapter() }
                ChapterButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_chapter),
                    Modifier.align(Alignment.BottomEnd).padding(14.dp).testTag("next-chapter")) { vm.nextChapter() }
                if (vm.isAway) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.backToMyPlace() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 74.dp).heightIn(min = 48.dp).testTag("back-to-place"),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            stringResource(R.string.back_to_my_place) + "  ·  " + (vm.list.currentItem?.passage?.shortLabel() ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) AddPanel(vm, onDismiss = { showAdd = false })
    if (showBrowse) BrowseSheet(vm, onDismiss = { showBrowse = false })
    studyVerse?.let { v -> StudySheet(vm, loc.book, loc.chapter, v, onDismiss = { studyVerse = null }) }
    xrefVerse?.let { v -> CrossRefSheet(vm, v, onDismiss = { xrefVerse = null }) }
    noteDraft?.let { d ->
        NoteDialog(
            title = d.passage.longLabel(translation?.lang ?: "mg"),
            initial = d.initial,
            onDelete = d.noteId?.let { id -> { vm.deleteNote(id); noteDraft = null } },
            onSave = { text ->
                if (d.noteId != null) vm.updateNote(d.noteId, text) else vm.addNote(d.passage, text)
                noteDraft = null
                selected = emptySet()
            },
            onDismiss = { noteDraft = null },
        )
    }
    wordsVerse?.let { v ->
        val text = vm.chapter?.verses?.firstOrNull { it.verse == v }?.text ?: ""
        WordSelectDialog(
            reference = Passage(loc.book, loc.chapter, v, v).longLabel(translation?.lang ?: "mg"),
            text = text,
            allowCharHighlight = vm.translationId == BibleRepository.PRIMARY,
            onCopy = { s ->
                clipboard.setText(AnnotatedString("$s\n— ${Passage(loc.book, loc.chapter, v, v).longLabel(translation?.lang ?: "mg")} (${translation?.shortName})"))
                vm.message = ctx.getString(R.string.copied)
                wordsVerse = null; selected = emptySet()
            },
            onHighlight = { range, colour ->
                vm.addHighlight(listOf(Passage(loc.book, loc.chapter, v, v)), colour, range)
                wordsVerse = null; selected = emptySet()
            },
            onNote = { s ->
                wordsVerse = null
                noteDraft = NoteDraft(Passage(loc.book, loc.chapter, v, v), "“$s” ")
            },
            onDismiss = { wordsVerse = null },
        )
    }
}

// ---------------------------------------------------------------- top bar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(vm: AppViewModel, openMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val t = vm.translations.firstOrNull { it.id == vm.translationId }
    val lang = t?.lang ?: "mg"
    val focus = vm.location.focus
    val title = if (focus != null && !focus.isWholeChapter && focus.book == vm.location.book && focus.chapter == vm.location.chapter)
        focus.longLabel(lang) else Canon.book(vm.location.book).name(lang) + " " + vm.location.chapter
    val gold = LocalOrnament.current.gold
    Column {
        CenterAlignedTopAppBar(
            navigationIcon = { IconButton(onClick = openMenu) { Icon(Icons.Filled.Menu, stringResource(R.string.menu)) } },
            title = {
                Box {
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { menu = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        Text(
                            (t?.shortName ?: "") + "  ▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = gold,
                            letterSpacing = 2.sp,
                        )
                    }
                    TranslationMenu(vm, menu, onDismiss = { menu = false })
                }
            },
            actions = {
                StudyButton(vm)
                IconButton(onClick = { vm.search(vm.searchQuery, vm.translationId); vm.screen = Screen.Search }) {
                    Icon(Icons.Filled.Search, stringResource(R.string.search))
                }
                ReaderMoreMenu(vm)
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        GoldRule()
    }
}

/** Listen, places of this chapter, dictionary. */
@Composable
private fun ReaderMoreMenu(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("more")) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (vm.readAloudEnabled) DropdownMenuItem(
                text = { Text(stringResource(R.string.listen)) },
                leadingIcon = { Text("🕮", color = LocalOrnament.current.gold) },
                onClick = { open = false; vm.startReading() },
                modifier = Modifier.testTag("menu-listen"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.places_this_chapter)) },
                leadingIcon = { Text("⌖", color = LocalOrnament.current.gold, fontSize = 18.sp) },
                onClick = { open = false; vm.mapForChapter = true; vm.screen = Screen.Map },
                modifier = Modifier.testTag("menu-map"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dictionary)) },
                leadingIcon = { Text("❦", color = LocalOrnament.current.gold) },
                onClick = { open = false; vm.screen = Screen.Dictionary },
            )
        }
    }
}

/** Study symbol: Aleph for the Old Testament (Hebrew and Aramaic), alpha otherwise (Greek). */
fun studySymbol(book: Int): String = if (Canon.book(book).isHebrewOriginal) "א" else "α"

@Composable
private fun StudyButton(vm: AppViewModel) {
    val on = vm.studyMode
    val symbol = studySymbol(vm.location.book)
    val gold = LocalOrnament.current.gold
    Box(
        Modifier.size(48.dp).testTag("alpha").padding(6.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else gold, CircleShape)
            .clickable {
                vm.toggleStudyMode()
                vm.message = if (!on) vm.str(R.string.study_mode_on, symbol) else vm.str(R.string.study_mode_off, symbol)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontSize = 20.sp,
            fontFamily = if (symbol == "α") TextSerif else FontFamily.Default,
            color = if (on) MaterialTheme.colorScheme.onPrimary else gold,
        )
    }
}

@Composable
fun TranslationMenu(vm: AppViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val langName = mapOf("mg" to "Malagasy", "fr" to "Français", "en" to "English")
        vm.translations.groupBy { it.lang }.forEach { (lang, ts) ->
            Text(
                langName[lang] ?: lang,
                style = MaterialTheme.typography.labelLarge,
                color = LocalOrnament.current.gold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ts.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                t.name,
                                fontWeight = if (t.id == vm.translationId) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                styleNote(t) + if (t.catholic) " · " + stringResource(R.string.catholic_tag) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Text(t.shortName, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp)) },
                    // One tap on the star makes this the Bible the app opens with.
                    trailingIcon = {
                        val isDefault = t.id == vm.defaultTranslationId
                        val setDefault = stringResource(R.string.set_default)
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .clickable(onClickLabel = setDefault) {
                                    vm.setDefaultTranslation(t.id)
                                    vm.message = vm.str(R.string.default_set, t.name)
                                    onDismiss()
                                }
                                .testTag("default:${t.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (isDefault) "★" else "☆",
                                fontSize = 22.sp,
                                color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { vm.setTranslation(t.id); onDismiss() },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- list strip

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListStrip(vm: AppViewModel, onStartList: () -> Unit) {
    val list = vm.list
    if (list.isEmpty) {
        // Collapsed to a small handle so reading stays uncluttered.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onStartList, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    "✚  " + stringResource(R.string.start_list),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(list.current) { if (list.current >= 0) state.animateScrollToItem(maxOf(0, list.current - 1)) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = state,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(list.items) { i, item ->
                val current = i == list.current
                Box {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .then(if (current) Modifier else Modifier.border(1.dp, LocalOrnament.current.gold.copy(alpha = 0.7f), RoundedCornerShape(24.dp)))
                            .clip(RoundedCornerShape(24.dp))
                            .combinedClickable(onClick = { vm.selectItem(i) }, onLongClick = { menuFor = i })
                            .testTag("chip:$i"),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.passage.shortLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                            if (item.isSide) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.side_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .background(
                                            if (current) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (item.isSide) R.string.unmark_side else R.string.mark_side)) },
                            onClick = { vm.toggleSide(i); menuFor = null },
                        )
                        if (i > 0) DropdownMenuItem(text = { Text(stringResource(R.string.move_earlier)) }, onClick = { vm.moveEarlier(i); menuFor = null })
                        if (i < list.items.lastIndex) DropdownMenuItem(text = { Text(stringResource(R.string.move_later)) }, onClick = { vm.moveLater(i); menuFor = null })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { vm.deleteItem(i); menuFor = null })
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { listMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.list_menu)) }
            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                Text(
                    list.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.rename_list)) }, onClick = { rename = true; listMenu = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.new_list)) }, onClick = { vm.newList(); listMenu = false })
                DropdownMenuItem(text = { Text(stringResource(R.string.saved_lists)) }, onClick = {
                    vm.refreshSavedLists(); vm.screen = Screen.Lists; listMenu = false
                })
            }
        }
    }
    GoldRule(Modifier.padding(horizontal = 24.dp))
    if (rename) {
        var text by remember { mutableStateOf(list.title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename_list)) },
            text = { OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.list_title)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameList(text.trim().ifEmpty { list.title }); rename = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- reader

private sealed interface ReaderRow {
    data class Title(val book: String, val chapter: Int) : ReaderRow
    data class Heading(val text: String) : ReaderRow
    data class Verse(val verse: Int, val text: String) : ReaderRow
}

@Composable
private fun Reader(
    vm: AppViewModel,
    selected: Set<Int>,
    onVerseTap: (Int) -> Unit,
    onNumberTap: (Int) -> Unit,
    onNoteTap: (Note) -> Unit,
) {
    val ch = vm.chapter
    val state = rememberLazyListState()
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val rows = remember(ch, lang) {
        if (ch == null) emptyList() else buildList {
            add(ReaderRow.Title(Canon.book(ch.book).name(lang), ch.chapter))
            for (v in ch.verses) {
                ch.headings[v.verse]?.forEach { add(ReaderRow.Heading(it)) }
                add(ReaderRow.Verse(v.verse, v.text))
            }
        }
    }
    val focus = vm.location.focus?.takeIf { it.book == vm.location.book && it.chapter == vm.location.chapter && !it.isWholeChapter }
    LaunchedEffect(vm.scrollRequest, rows) {
        if (rows.isEmpty()) return@LaunchedEffect
        val target = focus?.let { f ->
            // scroll to the heading above the verse when there is one
            val idx = rows.indexOfFirst { it is ReaderRow.Verse && it.verse == f.verseStart }
            var i = idx
            while (i > 0 && rows[i - 1] is ReaderRow.Heading) i--
            i
        } ?: 0
        state.scrollToItem(maxOf(0, target))
    }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val onSwipe by rememberUpdatedState<(Boolean) -> Unit> { forward -> vm.swipe(forward) }
    val highlights = vm.chapterHighlights
    val notes = vm.chapterNotes
    val charHighlights = vm.translationId == BibleRepository.PRIMARY
    val textSize = vm.textSize
    // verse being read aloud in this chapter (highlighted and kept in view)
    val rs = vm.readState
    val reading = if (rs.active && rs.book == ch?.book && rs.chapter == ch.chapter && rs.translation == vm.translationId) rs.verse else null
    LaunchedEffect(reading, rows) {
        if (reading != null) {
            val idx = rows.indexOfFirst { it is ReaderRow.Verse && it.verse == reading }
            if (idx >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == idx }) state.animateScrollToItem(idx)
        }
    }

    if (ch == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.loading)) }
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader")
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipeThreshold) onSwipe(total < 0) },
                    onHorizontalDrag = { change, dx -> total += dx; change.consume() },
                )
            },
        contentPadding = PaddingValues(start = 4.dp, end = 18.dp, top = 4.dp, bottom = 104.dp),
    ) {
        items(rows.size, key = { i -> rows[i].let { r -> if (r is ReaderRow.Verse) "v${r.verse}" else "r$i" } }) { i ->
            when (val r = rows[i]) {
                is ReaderRow.Title -> ChapterHeader(r.book, r.chapter)
                is ReaderRow.Heading -> Text(
                    r.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = LocalOrnament.current.rubric,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 28.dp, top = 18.dp, bottom = 6.dp),
                )
                is ReaderRow.Verse -> VerseRow(
                    verse = r.verse,
                    text = r.text,
                    textSize = textSize,
                    focused = focus?.contains(r.verse) == true || reading == r.verse,
                    selected = r.verse in selected,
                    highlights = highlights.filter { it.passage.contains(r.verse) }.map { h ->
                        Triple(if (charHighlights) h.charStart else null, if (charHighlights) h.charEnd else null, h.colour)
                    },
                    note = notes.firstOrNull { it.passage.firstVerse == r.verse },
                    studyMode = vm.studyMode,
                    onTap = { onVerseTap(r.verse) },
                    onNumberTap = { onNumberTap(r.verse) },
                    onNoteTap = onNoteTap,
                )
            }
        }
    }
}

@Composable
private fun VerseRow(
    verse: Int,
    text: String,
    textSize: Float,
    focused: Boolean,
    selected: Boolean,
    highlights: List<Triple<Int?, Int?, Int>>,
    note: Note?,
    studyMode: Boolean,
    onTap: () -> Unit,
    onNumberTap: () -> Unit,
    onNoteTap: (Note) -> Unit,
) {
    val ornament = LocalOrnament.current
    val bg = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        focused -> ornament.focusWash
        else -> Color.Transparent
    }
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val annotated = remember(text, highlights, dark, verse, textSize, ornament) {
        buildAnnotatedString {
            append(text)
            for ((s, e, colour) in highlights) {
                val c = Color(colour).copy(alpha = if (dark) 0.34f else 0.55f)
                val start = (s ?: 0).coerceIn(0, text.length)
                val end = (e ?: text.length).coerceIn(start, text.length)
                addStyle(SpanStyle(background = c), start, end)
            }
            // A rubricated initial opens each chapter, as in old Bibles.
            if (verse == 1 && text.isNotEmpty() && text[0].isLetter()) {
                addStyle(SpanStyle(fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = (textSize * 1.9f).sp, color = ornament.rubric), 0, 1)
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 48.dp)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .drawBehind {
                // gilt rule in the margin beside the passage being read
                if (focused) drawRect(ornament.gold, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            },
    ) {
        Box(
            Modifier
                .widthIn(min = 48.dp)
                .fillMaxHeight()
                .clickable(onClick = onNumberTap)
                .testTag("vnum:$verse")
                .padding(top = (textSize * 0.42f).dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                "$verse",
                fontSize = (textSize * 0.72f).sp,
                fontFamily = TitleSerif,
                fontWeight = FontWeight.Bold,
                color = ornament.gold,
                modifier = if (studyMode) Modifier
                    .border(1.dp, ornament.gold, RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp) else Modifier,
            )
        }
        Text(
            annotated,
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.55f).sp,
            fontFamily = TextSerif,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTap)
                .testTag("verse:$verse")
                .padding(start = 4.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        )
        if (note != null) {
            Box(
                Modifier.size(48.dp).clickable { onNoteTap(note) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Create, stringResource(R.string.notes), tint = ornament.gold, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Chapter opening: book name in spaced small capitals, a large numeral, a gilt fleuron. */
@Composable
private fun ChapterHeader(book: String, chapter: Int) {
    val ornament = LocalOrnament.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp, start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(book.uppercase(), style = MaterialTheme.typography.titleMedium, color = ornament.gold, letterSpacing = 4.sp, textAlign = TextAlign.Center)
        Text("$chapter", fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 56.sp, color = ornament.rubric)
        Fleuron()
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------- bottom bars

/** Round chapter button: vellum disc with a gilt edge. */
@Composable
private fun ChapterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val gold = LocalOrnament.current.gold
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = gold,
        border = androidx.compose.foundation.BorderStroke(1.dp, gold),
        shadowElevation = 3.dp,
        modifier = modifier.size(48.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label) } }
}

@Composable
private fun ActionBar(onAdd: () -> Unit, onBrowse: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            GoldRule()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(27.dp)) {
                    Text("✚  " + stringResource(R.string.add), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onBrowse,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LocalOrnament.current.gold),
                ) {
                    Text(stringResource(R.string.browse), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    vm: AppViewModel,
    count: Int,
    hasHighlight: Boolean,
    onCopy: () -> Unit,
    onNote: () -> Unit,
    onHighlight: (Int) -> Unit,
    onRemoveHighlight: () -> Unit,
    onWords: (() -> Unit)?,
    onCrossRefs: (() -> Unit)?,
    onListen: (() -> Unit)?,
    onAddToList: () -> Unit,
    onClear: () -> Unit,
) {
    var colours by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (colours) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HighlightColours.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(48.dp).testTag("swatch:$i").clip(CircleShape).background(Color(c)).clickable { colours = false; onHighlight(c) },
                        )
                    }
                    if (hasHighlight) TextButton(onClick = { colours = false; onRemoveHighlight() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.remove_highlight))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                Text(
                    stringResource(R.string.n_selected, "$count"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 4.dp),
                )
                BarButton(stringResource(R.string.copy), onCopy)
                BarButton(stringResource(R.string.add_note), onNote)
                BarButton(stringResource(R.string.highlight)) { colours = !colours }
                if (onCrossRefs != null) BarButton(stringResource(R.string.cross_references), onCrossRefs)
                if (onListen != null) BarButton(stringResource(R.string.listen_from_here), onListen)
                if (onWords != null) BarButton(stringResource(R.string.select_words), onWords)
                BarButton(stringResource(R.string.add_to_list), onAddToList)
            }
        }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun NoteDialog(title: String, initial: String, onDelete: (() -> Unit)?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Choosing words inside one verse: a read-only selectable field gives the character range. */
@Composable
private fun WordSelectDialog(
    reference: String,
    text: String,
    allowCharHighlight: Boolean,
    onCopy: (String) -> Unit,
    onHighlight: (IntRange, Int) -> Unit,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(0, text.length))) }
    val sel = value.selection
    val range = if (sel.collapsed) 0 until text.length else minOf(sel.start, sel.end) until maxOf(sel.start, sel.end)
    val chosen = text.substring(range.first, range.last + 1)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(reference, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.select_words_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = { value = it.copy(text = text) },
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface, fontFamily = TextSerif, fontSize = 20.sp, lineHeight = 30.sp,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (allowCharHighlight) HighlightColours.forEach { c ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c)).clickable { onHighlight(range, c) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onNote(chosen) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_note)) }
                    TextButton(onClick = { onCopy(chosen) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy)) }
                }
            }
        }
    }
}
