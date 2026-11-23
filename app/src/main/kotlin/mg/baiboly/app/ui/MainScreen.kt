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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import mg.baiboly.app.AppMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import kotlinx.coroutines.launch
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.Style
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
fun MainScreen(vm: AppViewModel) {
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

    // Church mode keeps the screen awake for the whole service.
    val view = LocalView.current
    val church = vm.mode == AppMode.Church
    DisposableEffect(church) {
        view.keepScreenOn = church
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(vm.location.book, vm.location.chapter, vm.translationId) { selected = emptySet() }
    // the selection actions take the place of the navigation bar
    LaunchedEffect(selected.isNotEmpty()) { vm.selectionActive = selected.isNotEmpty() }
    DisposableEffect(Unit) { onDispose { vm.selectionActive = false } }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    val loc = vm.location
    val translation = vm.translations.firstOrNull { it.id == vm.translationId }

    fun selectedPassages(): List<Passage> =
        ReferenceParser.mergeRanges(selected.sorted().map { it..it }).map { Passage(loc.book, loc.chapter, it.first, it.last) }

    val style = LocalStyle.current
    val pageStyle = style == Style.Page
    // Page shows the verse actions as its top bar; the other themes at the foot
    val selectionBar: @Composable () -> Unit = {
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
                        onCrossRefs = if (selected.size == 1 && vm.mode == AppMode.Study) ({ xrefVerse = selected.first(); selected = emptySet() }) else null,
                        onListen = if (vm.readAloudEnabled && !church) ({ vm.startReading(selected.min()); selected = emptySet() }) else null,
                        onAddToList = if (church) ({ vm.addToList(selectedPassages()); selected = emptySet() }) else null,
                        onClear = { selected = emptySet() },
                    )
    }

    Scaffold(
        topBar = { if (pageStyle && selected.isNotEmpty()) selectionBar() else ReaderTopBar(vm) },
        bottomBar = {
            if (selected.isNotEmpty()) {
                if (!pageStyle) selectionBar()
            } else {
                Column {
                    if (!church) ReadingBar(vm)
                    if (church) ServiceBar(vm, onAdd = { showAdd = true })
                    if (style == Style.Glass && !church) ChapterBottomBar(vm, readerTitle(vm))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (church) ListStrip(vm, onStartList = { showAdd = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Reader(
                    vm = vm,
                    selected = selected,
                    onVerseTap = { v -> selected = if (v in selected) selected - v else selected + v },
                    onNumberTap = { v ->
                        if (vm.mode == AppMode.Study) studyVerse = v
                        else selected = if (v in selected) selected - v else selected + v
                    },
                    onNoteTap = { n -> noteDraft = NoteDraft(n.passage, n.text, n.id) },
                )
                ChapterOverlay(vm)
                // Church, and Study after a jump (Found N verses): one tap back to the list passage.
                if ((church || vm.mode == AppMode.Study) && vm.isAway) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.backToMyPlace() },
                        icon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
                        text = {
                            Text(
                                stringResource(R.string.back_to_my_place) + "  " + (vm.list.currentItem?.passage?.shortLabel() ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 74.dp).heightIn(min = 48.dp).testTag("back-to-place"),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    if (showAdd) AddPanel(vm, onDismiss = { showAdd = false })
    if (showBrowse) BrowseSheet(vm, onDismiss = { showBrowse = false })
    studyVerse?.let { v -> VerseToolsSheet(vm, loc.book, loc.chapter, v, onDismiss = { studyVerse = null }) }
    xrefVerse?.let { v -> VerseToolsSheet(vm, loc.book, loc.chapter, v, startOnCrossRefs = true, onDismiss = { xrefVerse = null }) }
    if (vm.showWhatsNew) WhatsNewSheet(vm.versionName, onDismiss = { vm.dismissWhatsNew() })
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

/**
 * Reader top bar: mode badge; the chapter as title (tap: Books); the reading
 * text (tap: choose, ☆ default); Listen when reading aloud is on; Settings.
 */
/** The chapter (or the passage being read) as the reader's title. */
@Composable
fun readerTitle(vm: AppViewModel): String {
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val focus = vm.location.focus
    return if (focus != null && !focus.isWholeChapter && focus.book == vm.location.book && focus.chapter == vm.location.chapter)
        focus.longLabel(lang) else Canon.book(vm.location.book).name(lang) + " " + vm.location.chapter
}

@Composable
private fun ReaderTopBar(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    val t = vm.translations.firstOrNull { it.id == vm.translationId }
    StyledTopBar(
        vm, readerTitle(vm), titleTag = "chapter-title", onTitle = { vm.screen = Screen.Books },
        actions = {
            Box {
                TranslationChip(t?.shortName ?: "") { menu = true }
                TranslationMenu(vm, menu, onDismiss = { menu = false })
            }
            if (vm.readAloudEnabled && vm.mode != AppMode.Church) {
                IconButton(onClick = { vm.startReading() }, modifier = Modifier.testTag("listen")) {
                    Icon(Icons.Outlined.Headphones, stringResource(R.string.listen))
                }
            }
        },
    )
}

/**
 * Church mode: move between the announced passages with two large buttons
 * (previous / next) and add a passage in the middle.
 */
@Composable
private fun ServiceBar(vm: AppViewModel, onAdd: () -> Unit) {
    val list = vm.list
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            GoldRule()
            if (list.isEmpty) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 56.dp).testTag("service-add"),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_passages), style = MaterialTheme.typography.titleMedium)
                }
                return@Column
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val prev = list.items.getOrNull(list.current - 1)
                val next = list.items.getOrNull(list.current + 1)
                ServiceStep(prev?.passage?.shortLabel(), forward = false, modifier = Modifier.weight(1f).testTag("service-prev")) {
                    vm.selectItem(list.current - 1)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                    FilledIconButton(
                        onClick = onAdd,
                        modifier = Modifier.size(52.dp).testTag("service-add"),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                    Text("${list.current + 1} / ${list.items.size}", style = MaterialTheme.typography.labelMedium, color = LocalOrnament.current.gold)
                }
                ServiceStep(next?.passage?.shortLabel(), forward = true, modifier = Modifier.weight(1f).testTag("service-next")) {
                    vm.selectItem(list.current + 1)
                }
            }
        }
    }
}

@Composable
private fun ServiceStep(label: String?, forward: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val enabled = label != null
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalOrnament.current.gold.copy(alpha = if (enabled) 0.8f else 0.3f)),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        if (!forward) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_item))
        Text(label ?: "—", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
        if (forward) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_item))
    }
}

/** Study symbol: Aleph for the Old Testament (Hebrew and Aramaic), alpha otherwise (Greek). */
fun studySymbol(book: Int): String = if (Canon.book(book).isHebrewOriginal) "א" else "α"

@Composable
fun TranslationMenu(vm: AppViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    LocalizedMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val langName = mapOf("mg" to "Malagasy", "fr" to "Français", "en" to "English")
        // The menu opens on the languages only; each one unfolds its translations.
        // The state lives inside the menu, so it starts folded every time it opens.
        var open by remember { mutableStateOf(setOf<String>()) }
        vm.visibleTranslations.groupBy { it.lang }.forEach { (lang, ts) ->
            val isOpen = lang in open
            val holdsCurrent = ts.any { it.id == vm.translationId }
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            langName[lang] ?: lang,
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalOrnament.current.gold,
                            letterSpacing = 2.sp,
                            fontWeight = if (holdsCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                        // What the language holds, so the right one is found without opening it.
                        Text(
                            ts.joinToString(" · ") { it.shortName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (isOpen) 180f else 0f),
                    )
                },
                onClick = { open = if (isOpen) open - lang else open + lang },
                modifier = Modifier.heightIn(min = 56.dp).testTag("lang:$lang"),
            )
            if (isOpen) ts.forEach { t ->
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
                    LocalizedMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
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
            LocalizedMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
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
        LocalizedAlertDialog(
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
    val pageStyle = LocalStyle.current == Style.Page
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
        contentPadding = PaddingValues(start = 4.dp, end = 18.dp, top = 4.dp, bottom = if (pageStyle) 24.dp else 104.dp),
    ) {
        items(rows.size, key = { i -> rows[i].let { r -> if (r is ReaderRow.Verse) "v${r.verse}" else "r$i" } }) { i ->
            when (val r = rows[i]) {
                is ReaderRow.Title -> StyledChapterHeader(r.book, r.chapter)
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
        if (pageStyle) item(key = "footer") { ChapterFooterLinks(vm) }
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
    val titleFamily = TitleSerif
    val initial = dropCaps()
    val annotated = remember(text, highlights, dark, verse, textSize, ornament, titleFamily, initial) {
        buildAnnotatedString {
            append(text)
            for ((s, e, colour) in highlights) {
                val c = Color(colour).copy(alpha = if (dark) 0.34f else 0.55f)
                val start = (s ?: 0).coerceIn(0, text.length)
                val end = (e ?: text.length).coerceIn(start, text.length)
                addStyle(SpanStyle(background = c), start, end)
            }
            // A rubricated initial opens each chapter, as in old Bibles.
            if (initial && verse == 1 && text.isNotEmpty() && text[0].isLetter()) {
                addStyle(SpanStyle(fontFamily = titleFamily, fontWeight = FontWeight.Bold, fontSize = (textSize * 1.9f).sp, color = ornament.rubric), 0, 1)
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
            lineHeight = (textSize * LocalReading.current.lineSpacing).sp,
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

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------- bottom bars

/** Actions on the selected verses: icon and short label each, one row, no scrolling. */
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
    onAddToList: (() -> Unit)?,
    onClear: () -> Unit,
) {
    var colours by remember { mutableStateOf(false) }
    val actions = buildList {
        add(VerseAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy), onCopy))
        add(VerseAction(Icons.Outlined.EditNote, stringResource(R.string.note_action), onNote))
        add(VerseAction(Icons.Outlined.BorderColor, stringResource(R.string.highlight)) { colours = !colours })
        if (onCrossRefs != null) add(VerseAction(Icons.Outlined.Link, stringResource(R.string.references_action), onCrossRefs))
        if (onAddToList != null) add(VerseAction(Icons.AutoMirrored.Outlined.PlaylistAdd, stringResource(R.string.add_to_list_short), onAddToList))
        if (onListen != null) add(VerseAction(Icons.Outlined.Headphones, stringResource(R.string.listen), onListen))
        if (onWords != null) add(VerseAction(Icons.Outlined.FormatQuote, stringResource(R.string.words_action), onWords))
    }
    StyledSelectionActions(
        count = count,
        actions = actions,
        colours = colours,
        swatches = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColours.forEachIndexed { i, c ->
                    Box(Modifier.size(44.dp).testTag("swatch:$i").clip(CircleShape).background(Color(c)).clickable { colours = false; onHighlight(c) })
                }
                if (hasHighlight) TextButton(onClick = { colours = false; onRemoveHighlight() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.remove_highlight), color = androidx.compose.material3.LocalContentColor.current)
                }
            }
        },
        onClear = onClear,
    )
}


// ---------------------------------------------------------------- dialogs

@Composable
fun NoteDialog(title: String, initial: String, onDelete: (() -> Unit)?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    LocalizedAlertDialog(
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
    LocalizedDialog(onDismissRequest = onDismiss) {
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
