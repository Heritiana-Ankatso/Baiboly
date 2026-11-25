package mg.baiboly.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.intl.LocaleList
import mg.baiboly.app.VerseLayout
import mg.baiboly.app.PageMargin
import mg.baiboly.app.ParagraphBreak
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
import androidx.compose.runtime.snapshotFlow
import mg.baiboly.app.ChapterBlock
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
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
    var toolsPassage by remember { mutableStateOf<Passage?>(null) }
    var toolsTab by remember { mutableStateOf(PassageTab.Translations) }
    // the quick look closed by the user for this selection (it comes back with the next one)
    var quickLookClosed by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // the screen is kept awake for the whole app (BaibolyApp), always in Church mode
    val church = vm.mode == AppMode.Church
    // the selection belongs to one chapter: it goes when another becomes the chapter being read
    var selectedAt by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(vm.location.book, vm.location.chapter, vm.translationId) {
        if (selectedAt != (vm.location.book to vm.location.chapter)) selected = emptySet()
    }
    LaunchedEffect(vm.translationId) { selected = emptySet() }
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
                        onStudy = { toolsTab = PassageTab.Translations; toolsPassage = Passage(loc.book, loc.chapter, selected.min(), selected.max()) },
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
                    onVerseTap = { v ->
                        val at = vm.location.book to vm.location.chapter
                        val base = if (selectedAt == at) selected else emptySet()
                        selectedAt = at
                        selected = if (v in base) base - v else base + v
                    },
                    onNumberTap = { v ->
                        if (vm.mode == AppMode.Study) studyVerse = v
                        else {
                            val at = vm.location.book to vm.location.chapter
                            val base = if (selectedAt == at) selected else emptySet()
                            selectedAt = at
                            selected = if (v in base) base - v else base + v
                        }
                    },
                    onNoteTap = { n -> noteDraft = NoteDraft(n.passage, n.text, n.id) },
                )
                ChapterOverlay(vm)
                // quick look: the passage in another language, its units… (Settings, Advanced, Quick look)
                LaunchedEffect(selected) { quickLookClosed = false }
                if (selected.isNotEmpty() && !quickLookClosed && toolsPassage == null) {
                    val qp = Passage(loc.book, loc.chapter, selected.min(), selected.max())
                    QuickLookCard(
                        vm, qp,
                        onOpen = { tab -> toolsTab = tab; toolsPassage = qp },
                        onClose = { quickLookClosed = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                // Church, and Study after a jump (Found N verses): one tap back to the list passage.
                if (vm.showBackToMyPlace) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.backToMyPlace() },
                        icon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
                        text = {
                            Text(
                                stringResource(R.string.back_to_my_place) + "  " + (vm.list.currentItem?.passage?.label() ?: ""),
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
    toolsPassage?.let { p -> PassageToolsSheet(vm, p, toolsTab, onDismiss = { toolsPassage = null; selected = emptySet() }) }
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
            // Navy has no bar at the foot: search sits in the top bar, as the most used tool
            if (LocalStyle.current == Style.Navy) {
                IconButton(onClick = { navigateTo(vm, Screen.Search) }, modifier = Modifier.testTag("top-search")) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.primary)
                }
            }
            // Church: the day's sermon note, one tap from the service (date and passages already filled in)
            if (vm.mode == AppMode.Church) {
                IconButton(onClick = { vm.todaysSermonNote() }, modifier = Modifier.testTag("sermon-note")) {
                    Icon(Icons.Outlined.EditNote, stringResource(R.string.sermon_note))
                }
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
                ServiceStep(prev?.passage?.label(), forward = false, modifier = Modifier.weight(1f).testTag("service-prev")) {
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
                ServiceStep(next?.passage?.label(), forward = true, modifier = Modifier.weight(1f).testTag("service-next")) {
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
                            Text(item.passage.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
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
    /** Running text: several verses set as one paragraph. */
    data class Paragraph(val verses: List<Verse>) : ReaderRow
}

/** A row of the reader and the chapter it belongs to (continuous reading shows three). */
private data class Placed(val book: Int, val chapter: Int, val index: Int, val row: ReaderRow)

private fun ReaderRow.holds(verse: Int): Boolean = when (this) {
    is ReaderRow.Verse -> this.verse == verse
    is ReaderRow.Paragraph -> verses.any { it.verse == verse }
    else -> false
}

/**
 * The texts carry no paragraph marks, so paragraphs break at section headings
 * and, inside a long section, at the first sentence end after a few verses.
 */
private fun MutableList<ReaderRow>.addParagraphs(verses: List<ReaderRow.Verse>) {
    var run = mutableListOf<ReaderRow.Verse>()
    for (v in verses) {
        run += v
        val end = v.text.trimEnd().trimEnd('"', '”', '»', '’', '\'', ')').lastOrNull()
        if (run.size >= 12 || (run.size >= 5 && end != null && end in ".!?")) {
            add(ReaderRow.Paragraph(run)); run = mutableListOf()
        }
    }
    if (run.isNotEmpty()) add(ReaderRow.Paragraph(run))
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
    val paragraphs = vm.verseLayout == VerseLayout.Paragraphs
    val typeset = Typeset(
        justify = vm.justify,
        hyphenate = vm.hyphenate,
        indent = vm.paragraphBreak == ParagraphBreak.Indent,
        margin = when (vm.pageMargin) { PageMargin.Narrow -> 8.dp; PageMargin.Normal -> 14.dp; PageMargin.Wide -> 26.dp },
        lang = lang,
    )
    // continuous reading: the chapters before and after run on above and below the one being read
    val blocks = listOfNotNull(
        vm.previousBlock?.takeIf { vm.continuousReading && ch != null },
        ch?.let { ChapterBlock(it, vm.chapterHighlights, vm.chapterNotes) },
        vm.nextBlock?.takeIf { vm.continuousReading && ch != null },
    ).filter { it.text.translation == ch?.translation }.distinctBy { it.text.book to it.text.chapter }
    val rows: List<Placed> = remember(blocks.map { System.identityHashCode(it.text) }, lang, paragraphs) {
        buildList {
            for (b in blocks) {
                val c = b.text
                val out = mutableListOf<ReaderRow>()
                out.add(ReaderRow.Title(Canon.book(c.book).name(lang), c.chapter))
                val section = mutableListOf<ReaderRow.Verse>()
                for (v in c.verses) {
                    val heads = c.headings[v.verse]
                    if (paragraphs && heads != null && section.isNotEmpty()) { out.addParagraphs(section); section.clear() }
                    heads?.forEach { out.add(ReaderRow.Heading(it)) }
                    if (paragraphs) section += ReaderRow.Verse(v.verse, v.text) else out.add(ReaderRow.Verse(v.verse, v.text))
                }
                if (section.isNotEmpty()) out.addParagraphs(section)
                out.forEachIndexed { i, r -> add(Placed(c.book, c.chapter, i, r)) }
            }
        }
    }
    val annotations = blocks.associate { (it.text.book to it.text.chapter) to it }
    fun isCurrent(r: Placed) = ch != null && r.book == ch.book && r.chapter == ch.chapter
    val focus = vm.location.focus?.takeIf { it.book == vm.location.book && it.chapter == vm.location.chapter && !it.isWholeChapter }
    val latestRows by rememberUpdatedState(rows)
    val latestFocus by rememberUpdatedState(focus)
    LaunchedEffect(vm.scrollRequest) {
        val rs = latestRows
        if (rs.isEmpty()) return@LaunchedEffect
        val start = rs.indexOfFirst { isCurrent(it) }.coerceAtLeast(0)
        val target = latestFocus?.let { f ->
            // scroll to the heading above the verse when there is one
            val idx = rs.indexOfFirst { isCurrent(it) && it.row.holds(f.verseStart) }
            var i = idx
            while (i > start && rs[i - 1].row is ReaderRow.Heading) i--
            i
        } ?: start
        state.scrollToItem(maxOf(0, target))
    }
    // the chapter at the top of the screen is the one being read: title, selection, notes follow the scroll
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }.collect { (scrolling, first) ->
            if (!scrolling) return@collect
            val r = latestRows.getOrNull(first) ?: return@collect
            vm.scrolledInto(r.book, r.chapter)
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val onSwipe by rememberUpdatedState<(Boolean) -> Unit> { forward -> vm.swipe(forward) }
    val highlights = vm.chapterHighlights
    val notes = vm.chapterNotes
    val charHighlights = vm.translationId == BibleRepository.PRIMARY
    val textSize = vm.textSize * LocalTextScale.current
    // verse being read aloud in this chapter (highlighted and kept in view)
    val rs = vm.readState
    val reading = if (rs.active && rs.book == ch?.book && rs.chapter == ch.chapter && rs.translation == vm.translationId) rs.verse else null
    LaunchedEffect(reading, rows) {
        if (reading != null) {
            val idx = rows.indexOfFirst { isCurrent(it) && it.row.holds(reading) }
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
        contentPadding = PaddingValues(start = 0.dp, end = 6.dp, top = 2.dp, bottom = if (pageStyle) 24.dp else 88.dp),
    ) {
        items(rows.size, key = { i ->
            rows[i].let { pl ->
                val at = "${pl.book}:${pl.chapter}:"
                when (val r = pl.row) {
                    is ReaderRow.Verse -> at + "v${r.verse}"
                    is ReaderRow.Paragraph -> at + "p${r.verses.first().verse}"
                    else -> at + "r${pl.index}"
                }
            }
        }) { i ->
            val pl = rows[i]
            val current = isCurrent(pl)
            val block = annotations[pl.book to pl.chapter]
            val hls = if (current) highlights else block?.highlights.orEmpty()
            val nts = if (current) notes else block?.notes.orEmpty()
            val sel = if (current) selected else emptySet()
            val foc: (Int) -> Boolean = { v -> current && (focus?.contains(v) == true || reading == v) }
            // a tap in the chapter above or below makes it the chapter being read, then acts on it
            val tap: (Int) -> Unit = { v -> if (!current) vm.scrolledInto(pl.book, pl.chapter); onVerseTap(v) }
            val numberTap: (Int) -> Unit = { v -> if (!current) vm.scrolledInto(pl.book, pl.chapter); onNumberTap(v) }
            when (val r = pl.row) {
                is ReaderRow.Title -> StyledChapterHeader(r.book, r.chapter)
                is ReaderRow.Heading -> if (LocalStyle.current == Style.Navy) Text(
                    // Navy: headings as in a printed Bible, in capitals at the left
                    r.text.uppercase(),
                    style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                ) else Text(
                    r.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = LocalOrnament.current.rubric,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                )
                is ReaderRow.Verse -> VerseRow(
                    verse = r.verse,
                    text = r.text,
                    textSize = textSize,
                    // the typesetting details are for running text; one verse per line stays as it was
                    typeset = typeset.copy(justify = false, hyphenate = false),
                    focused = foc(r.verse),
                    selected = r.verse in sel,
                    highlights = hls.filter { it.passage.contains(r.verse) }.map { h ->
                        Triple(if (charHighlights) h.charStart else null, if (charHighlights) h.charEnd else null, h.colour)
                    },
                    note = nts.firstOrNull { it.passage.firstVerse == r.verse },
                    studyMode = vm.studyMode,
                    onTap = { tap(r.verse) },
                    onNumberTap = { numberTap(r.verse) },
                    onNoteTap = onNoteTap,
                    tagPrefix = if (current) "" else "${pl.book}:${pl.chapter}:",
                )
                is ReaderRow.Paragraph -> ParagraphBlock(
                    verses = r.verses,
                    typeset = typeset,
                    textSize = textSize,
                    focused = foc,
                    selected = sel,
                    highlights = { v ->
                        hls.filter { it.passage.contains(v) }.map { h ->
                            Triple(if (charHighlights) h.charStart else null, if (charHighlights) h.charEnd else null, h.colour)
                        }
                    },
                    notes = r.verses.mapNotNull { v -> nts.firstOrNull { it.passage.firstVerse == v.verse }?.let { v.verse to it } }.toMap(),
                    onTap = tap,
                    onNumberTap = numberTap,
                    onNoteTap = onNoteTap,
                    tagPrefix = if (current) "" else "${pl.book}:${pl.chapter}:",
                )
            }
        }
        if (pageStyle && !vm.continuousReading) item(key = "footer") { ChapterFooterLinks(vm) }
    }
}

@Composable
private fun VerseRow(
    verse: Int,
    text: String,
    textSize: Float,
    typeset: Typeset,
    focused: Boolean,
    selected: Boolean,
    highlights: List<Triple<Int?, Int?, Int>>,
    note: Note?,
    studyMode: Boolean,
    onTap: () -> Unit,
    onNumberTap: () -> Unit,
    onNoteTap: (Note) -> Unit,
    /** "" for the chapter being read; "book:chapter:" for the chapters around it (continuous reading). */
    tagPrefix: String = "",
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
    val annotated = remember(text, highlights, dark, verse, textSize, ornament, titleFamily, initial, typeset) {
        buildAnnotatedString {
            val (shown, at) = typeset.breakable(text)
            append(shown)
            for ((s, e, colour) in highlights) {
                val c = Color(colour).copy(alpha = if (dark) 0.34f else 0.55f)
                val start = (s ?: 0).coerceIn(0, text.length)
                val end = (e ?: text.length).coerceIn(start, text.length)
                addStyle(SpanStyle(background = c), at?.get(start) ?: start, at?.get(end) ?: end)
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
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .drawBehind {
                // gilt rule in the margin beside the passage being read
                if (focused) drawRect(ornament.gold, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            },
    ) {
        Box(
            Modifier
                .widthIn(min = 34.dp)
                .fillMaxHeight()
                .clickable(onClick = onNumberTap)
                .testTag("vnum:$tagPrefix$verse")
                .padding(top = (textSize * 0.36f).dp, end = 4.dp),
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
            style = typeset.style(MaterialTheme.typography.bodyLarge),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTap)
                .testTag("verse:$tagPrefix$verse")
                .padding(start = 2.dp, top = 3.dp, bottom = 3.dp, end = 2.dp),
        )
        if (note != null) {
            Box(
                Modifier.width(32.dp).fillMaxHeight().clickable { onNoteTap(note) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Create, stringResource(R.string.notes), tint = ornament.gold, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Typesetting details from Settings, Advanced. */
private data class Typeset(
    val justify: Boolean,
    val hyphenate: Boolean,
    val indent: Boolean,
    val margin: androidx.compose.ui.unit.Dp,
    val lang: String,
) {
    /** Lines break in the text's own language: system patterns for French and English. */
    fun style(base: androidx.compose.ui.text.TextStyle) = base.copy(
        // No letter spacing in the Bible text (Material's bodyLarge adds 0.5sp). Book text has none, and on
        // Android 15 justified lines with letter spacing stop short of the right edge by the spacing of the
        // whole line (~one character), so the right margin looked twice the left. Test: ReaderMarginTest.
        letterSpacing = 0.sp,
        textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
        hyphens = if (hyphenate) Hyphens.Auto else Hyphens.None,
        lineBreak = if (justify) LineBreak.Paragraph else LineBreak.Simple,
        localeList = LocaleList(lang),
    )

    /** Android has no Malagasy patterns: its break points come from its syllables. */
    fun breakable(text: String): Pair<String, IntArray?> =
        if (hyphenate && lang == "mg") malagasySoftHyphens(text) else text to null
}

/** Where each verse sits inside a paragraph's text. */
private class VerseSpan(val verse: Int, val number: IntRange, val text: IntRange, val note: IntRange?)

/**
 * Verses as running text, numbers in small gilt superscript, as in a printed
 * Bible. A tap on a number, on a verse's words or on its note mark acts as in
 * the one-verse-per-line layout.
 */
@Composable
private fun ParagraphBlock(
    verses: List<ReaderRow.Verse>,
    typeset: Typeset,
    textSize: Float,
    focused: (Int) -> Boolean,
    selected: Set<Int>,
    highlights: (Int) -> List<Triple<Int?, Int?, Int>>,
    notes: Map<Int, Note>,
    onTap: (Int) -> Unit,
    onNumberTap: (Int) -> Unit,
    onNoteTap: (Note) -> Unit,
    /** "" for the chapter being read; "book:chapter:" for the chapters around it (continuous reading). */
    tagPrefix: String = "",
) {
    val ornament = LocalOrnament.current
    val selectedWash = MaterialTheme.colorScheme.secondaryContainer
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val initial = dropCaps()
    val titleFamily = TitleSerif
    val marks = verses.map { v -> Triple(focused(v.verse), v.verse in selected, highlights(v.verse)) }
    val (annotated, spans) = remember(verses, typeset, marks, notes.keys, dark, textSize, ornament, initial, selectedWash, titleFamily) {
        val spans = mutableListOf<VerseSpan>()
        val text = buildAnnotatedString {
            verses.forEachIndexed { i, v ->
                val (isFocused, isSelected, hl) = marks[i]
                if (i > 0) append(' ')
                val start = length
                // chapter opening: a rubricated initial instead of the number 1
                val dropInitial = initial && v.verse == 1 && v.text.firstOrNull()?.isLetter() == true
                if (!dropInitial) {
                    withStyle(SpanStyle(
                        fontSize = (textSize * 0.6f).sp, fontFamily = titleFamily, fontWeight = FontWeight.Bold,
                        color = ornament.gold, baselineShift = BaselineShift.Superscript,
                    )) { append("${v.verse}") }
                    append('\u00A0')
                }
                val numberEnd = length - 1
                val textStart = length
                val (shown, at) = typeset.breakable(v.text)
                append(shown)
                if (dropInitial) addStyle(SpanStyle(fontFamily = titleFamily, fontWeight = FontWeight.Bold,
                    fontSize = (textSize * 1.9f).sp, color = ornament.rubric), textStart, textStart + 1)
                for ((s, e, colour) in hl) {
                    val c = Color(colour).copy(alpha = if (dark) 0.34f else 0.55f)
                    val os = (s ?: 0).coerceIn(0, v.text.length)
                    val oe = (e ?: v.text.length).coerceIn(os, v.text.length)
                    val hs = textStart + (at?.get(os) ?: os)
                    val he = textStart + (at?.get(oe) ?: oe)
                    addStyle(SpanStyle(background = c), hs, he)
                }
                val textEnd = length
                var note: IntRange? = null
                if (v.verse in notes) {
                    append('\u00A0')
                    val ns = length
                    withStyle(SpanStyle(color = ornament.gold, fontSize = (textSize * 0.8f).sp)) { append("✎") }
                    note = ns until length
                }
                // a marked run of verses is washed without gaps at the spaces between them
                val prev = marks.getOrNull(i - 1)
                val wash = when { isSelected -> 2; isFocused -> 1; else -> 0 }
                val prevWash = when { prev == null -> 0; prev.second -> 2; prev.first -> 1; else -> 0 }
                val from = if (i > 0 && wash == prevWash) start - 1 else start
                when (wash) {
                    2 -> addStyle(SpanStyle(background = selectedWash), from, length)
                    1 -> addStyle(SpanStyle(background = ornament.focusWash), from, length)
                }
                spans += VerseSpan(v.verse, start..maxOf(start, numberEnd), textStart until textEnd, note)
            }
        }
        text to spans
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val first = verses.first().verse
    Text(
        annotated,
        fontSize = textSize.sp,
        lineHeight = (textSize * LocalReading.current.lineSpacing).sp,
        fontFamily = TextSerif,
        color = MaterialTheme.colorScheme.onSurface,
        // paragraphs marked by a first-line indent, or by a blank space between them
        style = typeset.style(MaterialTheme.typography.bodyLarge).let {
            if (typeset.indent) it.copy(textIndent = TextIndent(firstLine = (textSize * 1.2f).sp)) else it
        },
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paragraph:$tagPrefix$first")
            // inside the padding, so tap positions match the text layout
            // the list adds 0 dp on the left and 6 on the right: equal margins on both sides
            .padding(
                start = typeset.margin, end = (typeset.margin - 6.dp).coerceAtLeast(0.dp),
                top = if (typeset.indent) 0.dp else 6.dp, bottom = if (typeset.indent) 0.dp else 6.dp,
            )
            .pointerInput(spans, notes) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val at = l.getOffsetForPosition(pos)
                    // the verse whose number, words or note mark lie under the finger
                    val span = spans.lastOrNull { it.number.first <= at } ?: return@detectTapGestures
                    when {
                        span.note != null && at in span.note -> notes[span.verse]?.let(onNoteTap)
                        at in span.number && at < span.text.first -> onNumberTap(span.verse)
                        else -> onTap(span.verse)
                    }
                }
            },
    )
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
    onStudy: () -> Unit,
    onWords: (() -> Unit)?,
    onCrossRefs: (() -> Unit)?,
    onListen: (() -> Unit)?,
    onAddToList: (() -> Unit)?,
    onClear: () -> Unit,
) {
    var colours by remember { mutableStateOf(false) }
    val actions = buildList {
        // the passage in other Bibles, its original words, places, units, dictionary
        add(VerseAction(Icons.Outlined.School, stringResource(R.string.study_action), onStudy))
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
