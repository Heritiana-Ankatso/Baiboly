package mg.baiboly.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mg.baiboly.app.AppMode
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.core.notes.Block
import mg.baiboly.core.notes.ExportFormat
import mg.baiboly.core.notes.Inline
import mg.baiboly.core.notes.LinkTarget
import mg.baiboly.core.notes.NoteExport
import mg.baiboly.core.notes.NoteMarkup
import mg.baiboly.core.notes.NoteSheet
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.data.NoteDocument
import java.text.DateFormat
import java.util.Date

// Sermon notes (Church mode: quick, dated, with the service's passages) and studies (Study mode:
// structured documents with an outline, templates and sections that move). Both link parts of the
// text to verses, people and events, and export to Markdown, text, HTML, Word and PDF.

private fun kindIcon(kind: String): ImageVector = if (kind == "sermon") Icons.Outlined.Church else Icons.Outlined.School

@Composable
private fun formatDay(ms: Long): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))
}

/** The list: sermon notes and studies, newest first, with a filter and the two ways to start one. */
@Composable
fun DocumentsScreen(vm: AppViewModel, embedded: Boolean = false) {
    LaunchedEffect(Unit) { vm.refreshDocuments() }
    var filter by rememberSaveable { mutableStateOf(if (vm.mode == AppMode.Church) "sermon" else if (vm.mode == AppMode.Study) "study" else "") }
    val shown = vm.documents.filter { filter.isEmpty() || it.kind == filter }
    val body: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter.isEmpty(), { filter = "" }, label = { Text(stringResource(R.string.all)) }, modifier = Modifier.testTag("docs-filter:all"))
                    FilterChip(filter == "sermon", { filter = "sermon" }, label = { Text(stringResource(R.string.sermon_notes)) }, modifier = Modifier.testTag("docs-filter:sermon"))
                    FilterChip(filter == "study", { filter = "study" }, label = { Text(stringResource(R.string.studies)) }, modifier = Modifier.testTag("docs-filter:study"))
                }
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.docs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp).testTag("docs-empty"))
                }
                LazyColumn(Modifier.weight(1f).testTag("docs-list")) {
                    items(shown, key = { it.id }) { d ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { vm.openDocument(d) }.testTag("doc:${d.id}")
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(kindIcon(d.kind), null, tint = LocalOrnament.current.gold)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(d.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(listOf(formatDay(d.date), d.passages).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    item { Spacer(Modifier.heightIn(min = 96.dp)) }
                }
            }
            Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { vm.newDocument("sermon") },
                    icon = { Icon(Icons.Outlined.Church, null) }, text = { Text(stringResource(R.string.new_sermon_note)) },
                    modifier = Modifier.testTag("new-sermon"),
                )
                ExtendedFloatingActionButton(
                    onClick = { vm.newDocument("study") },
                    icon = { Icon(Icons.Outlined.School, null) }, text = { Text(stringResource(R.string.new_study)) },
                    modifier = Modifier.testTag("new-study"),
                )
            }
        }
    }
    if (embedded) { body(Modifier); return }
    Scaffold(topBar = {
        if (isTopLevel(vm.style, vm.mode, vm.screen)) MainTopBar(vm, stringResource(R.string.my_notes))
        else StyledSubBar(stringResource(R.string.my_notes), onBack = { vm.screen = backTarget(vm) })
    }) { padding -> body(Modifier.padding(padding)) }
}

/** A tool on the editor's bar. */
private class Tool(val icon: ImageVector?, val text: String?, val label: String, val tag: String, val onClick: () -> Unit)

/**
 * The editor. Church: the date and the service's passages are filled in, a short bar (title, section,
 * point, bold, verse, person) and nothing else in the way. Study: the details, an outline whose sections
 * move up and down, templates for a new study, numbered points, quotations, the text of a verse, events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(vm: AppViewModel) {
    val start = vm.openDocument ?: run { LaunchedEffect(Unit) { vm.closeDocument() }; return }
    val study = start.kind == "study"
    var doc by remember(start.kind, start.createdAt) { mutableStateOf(start) }
    var body by remember(start.kind, start.createdAt) { mutableStateOf(TextFieldValue(start.body, TextRange(start.body.length))) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(start.id == 0L && study) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // the id arrives after the first save
    LaunchedEffect(vm.openDocument?.id) { vm.openDocument?.id?.let { if (it != doc.id && doc.id == 0L) doc = doc.copy(id = it) } }
    val current by rememberUpdatedState(doc.copy(body = body.text))
    // autosave a moment after typing stops, and on leaving
    LaunchedEffect(body.text, doc.title, doc.date, doc.passages, doc.speaker, doc.place) {
        delay(700)
        if (current.copy(id = 0) != vm.openDocument?.copy(id = 0)) vm.saveDocument(current)
    }
    DisposableEffect(Unit) { onDispose { vm.saveDocument(current, keepOpen = false) } }
    BackHandler { vm.saveDocument(current, keepOpen = false); vm.closeDocument() }

    fun insert(text: String, block: Boolean = false) {
        val t = body.text
        val sel = body.selection
        val before = t.substring(0, sel.min)
        val prefix = if (block && before.isNotEmpty() && !before.endsWith("\n")) "\n" else ""
        val new = before + prefix + text + t.substring(sel.max)
        val caret = before.length + prefix.length + text.length
        body = TextFieldValue(new, TextRange(caret))
    }

    /** Starts the current line with [mark] ("# ", "- ", "1. ", "> "), or takes it off when it is there. */
    fun lineMark(mark: String) {
        val t = body.text
        val pos = body.selection.min
        val lineStart = t.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (pos == 0) 0 else it + 1 }
        val line = t.substring(lineStart).substringBefore('\n')
        val stripped = line.replace(Regex("^(#{1,3} |[-*•] |\\d{1,3}[.)] |> )"), "")
        val newLine = if (line.startsWith(mark)) stripped else mark + stripped
        val new = t.substring(0, lineStart) + newLine + t.substring(lineStart + line.length)
        body = TextFieldValue(new, TextRange((pos + newLine.length - line.length).coerceIn(lineStart, lineStart + newLine.length)))
    }

    fun wrap(mark: String) {
        val sel = body.selection
        val t = body.text
        if (sel.collapsed) { insert(mark + mark); body = body.copy(selection = TextRange(sel.min + mark.length)); return }
        val new = t.substring(0, sel.min) + mark + t.substring(sel.min, sel.max) + mark + t.substring(sel.max)
        body = TextFieldValue(new, TextRange(sel.max + 2 * mark.length))
    }

    val tools = buildList {
        add(Tool(Icons.Filled.Title, null, stringResource(R.string.tool_title), "tool-title") { lineMark("# ") })
        add(Tool(null, "§", stringResource(R.string.tool_section), "tool-section") { lineMark("## ") })
        if (study) add(Tool(null, "§§", stringResource(R.string.tool_subsection), "tool-subsection") { lineMark("### ") })
        add(Tool(Icons.AutoMirrored.Filled.FormatListBulleted, null, stringResource(R.string.tool_point), "tool-point") { lineMark("- ") })
        if (study) add(Tool(Icons.Filled.FormatListNumbered, null, stringResource(R.string.tool_numbered), "tool-numbered") { lineMark("1. ") })
        add(Tool(Icons.Filled.FormatBold, null, stringResource(R.string.tool_bold), "tool-bold") { wrap("**") })
        add(Tool(Icons.Filled.FormatItalic, null, stringResource(R.string.tool_italic), "tool-italic") { wrap("*") })
        if (study) add(Tool(Icons.Filled.FormatQuote, null, stringResource(R.string.tool_quote), "tool-quote") { lineMark("> ") })
        add(Tool(Icons.AutoMirrored.Outlined.MenuBook, null, stringResource(R.string.tool_verse), "tool-verse") { dialog = "verse" })
        add(Tool(Icons.Outlined.Person, null, stringResource(R.string.tool_person), "tool-person") { dialog = "person" })
        if (study) add(Tool(Icons.Outlined.Event, null, stringResource(R.string.tool_event), "tool-event") { dialog = "event" })
        if (study) add(Tool(Icons.Filled.HorizontalRule, null, stringResource(R.string.tool_rule), "tool-rule") { insert("---\n", block = true) })
    }

    // export: the system's "save as" picker, then the bytes
    var pendingExport by remember { mutableStateOf<ExportFormat?>(null) }
    val saver = rememberLauncherForActivityResult(CreateFile()) { uri ->
        val f = pendingExport ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = vm.onIo { runCatching { writeExport(ctx, uri, sheetOf(ctx, current, vm), f) }.isSuccess }
            vm.message = vm.str(if (ok) R.string.export_done else R.string.export_failed)
        }
        pendingExport = null
    }

    Scaffold(topBar = {
        StyledSubBar(
            stringResource(if (study) R.string.study else R.string.sermon_note),
            onBack = { vm.saveDocument(current, keepOpen = false); vm.closeDocument() },
            actions = {
                IconButton(onClick = { preview = !preview }, modifier = Modifier.testTag("doc-preview")) {
                    Icon(if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility, stringResource(if (preview) R.string.edit else R.string.preview))
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("doc-menu")) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                    LocalizedMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_text)) }, onClick = {
                            menu = false
                            val text = NoteExport.text(sheetOf(ctx, current, vm))
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, current.title).putExtra(Intent.EXTRA_TEXT, text), null))
                        }, modifier = Modifier.testTag("doc-share"))
                        ExportFormat.entries.forEach { f ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_as, exportName(f))) }, onClick = {
                                menu = false
                                pendingExport = f
                                saver.launch(NoteExport.fileName(current.title, f) to f.mime)
                            }, modifier = Modifier.testTag("doc-export:${f.name}"))
                        }
                        if (doc.id != 0L) DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirmDelete = true },
                            modifier = Modifier.testTag("doc-delete"))
                    }
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = doc.title, onValueChange = { doc = doc.copy(title = it) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.doc_title_hint)) },
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("doc-title"),
            )
            // date and passages: recorded by default, changed with a tap
            CollapsibleHeader(
                title = stringResource(R.string.doc_details),
                detail = listOf(formatDay(doc.date), doc.passages, doc.speaker).filter { it.isNotBlank() }.joinToString(" · "),
                open = details, tag = "doc-details", onToggle = { details = !details }, horizontal = 16.dp,
                style = MaterialTheme.typography.labelLarge,
            )
            if (details) DocumentDetails(vm, doc, study) { doc = it }
            if (preview) {
                NotePreview(vm, current, Modifier.weight(1f))
                return@Column
            }
            // the bar of tools: scrolls sideways when it does not fit
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp).testTag("doc-tools"),
                verticalAlignment = Alignment.CenterVertically) {
                tools.forEach { t ->
                    IconButton(onClick = t.onClick, modifier = Modifier.testTag(t.tag)) {
                        if (t.icon != null) Icon(t.icon, t.label) else Text(t.text ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (study) StudyOutline(body.text) { line, up -> body = TextFieldValue(NoteMarkup.moveSection(body.text, line, up), body.selection) }
            if (study && body.text.isBlank()) StudyTemplates { body = TextFieldValue(it, TextRange(it.length)) }
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                placeholder = { Text(stringResource(if (study) R.string.study_hint else R.string.sermon_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp).testTag("doc-body"),
            )
            if (study) Text(
                stringResource(R.string.word_count, Regex("\\S+").findAll(NoteMarkup.parse(body.text).joinToString(" ") { plainOf(it) }).count()),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }
    }
    when (dialog) {
        "verse" -> VerseLinkDialog(vm, study, onDismiss = { dialog = null }) { insert(it); dialog = null }
        "person" -> PickDialog(vm, "person", onDismiss = { dialog = null }) { insert(it); dialog = null }
        "event" -> PickDialog(vm, "event", onDismiss = { dialog = null }) { insert(it); dialog = null }
    }
    if (confirmDelete) LocalizedAlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_doc_q)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDocument(doc.id) }, modifier = Modifier.testTag("doc-delete-yes")) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun plainOf(b: Block): String = when (b) {
    is Block.Heading -> NoteMarkup.plain(b.inlines)
    is Block.Paragraph -> NoteMarkup.plain(b.inlines)
    is Block.Item -> NoteMarkup.plain(b.inlines)
    is Block.Quote -> NoteMarkup.plain(b.inlines)
    Block.Rule -> ""
}

@Composable
private fun exportName(f: ExportFormat): String = when (f) {
    ExportFormat.Markdown -> "Markdown (.md)"
    ExportFormat.Text -> stringResource(R.string.format_text) + " (.txt)"
    ExportFormat.Html -> "HTML (.html)"
    ExportFormat.Word -> "Word (.docx)"
    ExportFormat.Pdf -> "PDF (.pdf)"
}

/** Date, passages (from the service list in Church), preacher or author, place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetails(vm: AppViewModel, doc: NoteDocument, study: Boolean, onChange: (NoteDocument) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picking = true }, modifier = Modifier.testTag("doc-date")) { Text("📅  " + formatDay(doc.date)) }
            if (!study && !vm.list.isEmpty) TextButton(onClick = { onChange(doc.copy(passages = vm.servicePassagesText())) }, modifier = Modifier.testTag("doc-from-list")) {
                Text("↻ " + stringResource(R.string.from_service_list))
            }
        }
        OutlinedTextField(doc.passages, { onChange(doc.copy(passages = it)) }, singleLine = true,
            label = { Text(stringResource(R.string.doc_passages)) }, modifier = Modifier.fillMaxWidth().testTag("doc-passages"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(doc.speaker, { onChange(doc.copy(speaker = it)) }, singleLine = true,
                label = { Text(stringResource(if (study) R.string.doc_author else R.string.doc_preacher)) }, modifier = Modifier.weight(1f).testTag("doc-speaker"))
            OutlinedTextField(doc.place, { onChange(doc.copy(place = it)) }, singleLine = true,
                label = { Text(stringResource(if (study) R.string.doc_tags else R.string.doc_church)) }, modifier = Modifier.weight(1f).testTag("doc-place"))
        }
    }
    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = doc.date)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(doc.copy(date = it)) }; picking = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

/** The study's headings, each section movable up or down (with what it contains). */
@Composable
private fun StudyOutline(body: String, onMove: (Int, Boolean) -> Unit) {
    val outline = remember(body) { NoteMarkup.outline(body) }
    if (outline.size < 2) return
    Collapsible(stringResource(R.string.outline) + " (${outline.size})", "doc-outline", horizontal = 16.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            outline.forEach { (level, text, line) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("outline:$line")) {
                    Text(text, style = if (level == 1) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (level == 1) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f).padding(start = ((level - 1) * 16).dp), maxLines = 1)
                    IconButton(onClick = { onMove(line, true) }, modifier = Modifier.testTag("outline-up:$line")) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_earlier)) }
                    IconButton(onClick = { onMove(line, false) }, modifier = Modifier.testTag("outline-down:$line")) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_later)) }
                }
            }
        }
    }
}

/** Ways to start a study: inductive (observe, interpret, apply), a sermon outline, a word, a person. */
@Composable
private fun StudyTemplates(onPick: (String) -> Unit) {
    val templates = listOf(
        stringResource(R.string.tpl_inductive) to stringResource(R.string.tpl_inductive_body),
        stringResource(R.string.tpl_outline) to stringResource(R.string.tpl_outline_body),
        stringResource(R.string.tpl_word) to stringResource(R.string.tpl_word_body),
        stringResource(R.string.tpl_person) to stringResource(R.string.tpl_person_body),
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.start_from_template), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            templates.forEachIndexed { i, (name, text) ->
                androidx.compose.material3.AssistChip(onClick = { onPick(text.replace("\\n", "\n")) }, label = { Text(name) }, modifier = Modifier.testTag("template:$i"))
            }
        }
    }
}

/** The note as it reads: headings, points, quotations; links open the verse, the person, the event. */
@Composable
fun NotePreview(vm: AppViewModel, d: NoteDocument, modifier: Modifier = Modifier) {
    val ornament = LocalOrnament.current
    val linkStyle = TextLinkStyles(SpanStyle(color = ornament.rubric, fontWeight = FontWeight.Bold))
    val blocks = remember(d.body) { NoteMarkup.parse(d.body) }
    fun open(t: LinkTarget) {
        when (t) {
            is LinkTarget.Verse -> vm.openPassage(t.passage)
            is LinkTarget.Person -> vm.openPerson(t.id)
            is LinkTarget.Event -> vm.openEvent(t.id)
            is LinkTarget.Place -> vm.showOnMap(t.id)
            is LinkTarget.Creed -> vm.openCreed(t.doc, t.seq)
        }
    }
    fun annotated(xs: List<Inline>): AnnotatedString = buildAnnotatedString {
        fun walk(ys: List<Inline>) {
            for (x in ys) when (x) {
                is Inline.Text -> append(x.text)
                is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { walk(x.children) }
                is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { walk(x.children) }
                is Inline.Link -> {
                    val t = x.target
                    if (t == null) append(x.label) else withLink(LinkAnnotation.Clickable(x.uri, linkStyle) { vm.saveDocument(d, keepOpen = false); open(t) }) { append(x.label) }
                }
            }
        }
        walk(xs)
    }
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("doc-rendered")) {
        item {
            Text(d.title, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 8.dp))
            Text(listOf(formatDay(d.date), d.passages, d.speaker, d.place).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Fleuron()
        }
        items(blocks.size) { i ->
            when (val b = blocks[i]) {
                is Block.Heading -> Text(annotated(b.inlines), style = when (b.level) {
                    1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall
                }, color = if (b.level == 1) ornament.rubric else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                is Block.Paragraph -> Text(annotated(b.inlines), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                is Block.Item -> Row(Modifier.padding(start = (8 + 20 * b.indent).dp, top = 2.dp, bottom = 2.dp)) {
                    Text(if (b.number != null) "${b.number}." else "•", style = MaterialTheme.typography.bodyLarge, color = ornament.gold, modifier = Modifier.width(24.dp))
                    Text(annotated(b.inlines), style = MaterialTheme.typography.bodyLarge)
                }
                is Block.Quote -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(annotated(b.inlines), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.padding(12.dp))
                }
                Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 12.dp), color = ornament.gold.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * A verse to link: typed as a reference ("Jao 3: 16"), the passage being read offered first. A study can
 * also take the verse's words as a quotation, with the link under it.
 */
@Composable
private fun VerseLinkDialog(vm: AppViewModel, study: Boolean, onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    val lang = refLang()
    val here = vm.location.focus?.takeIf { !it.isWholeChapter } ?: Passage(vm.location.book, vm.location.chapter)
    var text by remember { mutableStateOf(here.shortLabel(lang)) }
    val parsed = remember(text) { ReferenceParser.parse(text) }
    val scope = rememberCoroutineScope()
    fun link(): String = parsed.passages.joinToString("; ") { p -> NoteMarkup.link(p.shortLabel(lang), LinkTarget.Verse(p)) }
    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_verse)) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.reference)) },
                    isError = text.isNotBlank() && !parsed.isValid, modifier = Modifier.testTag("verse-ref"))
                if (parsed.isValid) Text("→ " + parsed.preview(lang), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row {
                if (study) TextButton(enabled = parsed.isValid, onClick = {
                    scope.launch {
                        val parts = mutableListOf<String>()
                        for (p in parsed.passages) parts += vm.versesText(p)
                        val words = parts.joinToString(" ")
                        onInsert("\n> " + words.replace("\n", " ") + "\n\n" + link() + "\n")
                    }
                }, modifier = Modifier.testTag("verse-quote")) { Text(stringResource(R.string.insert_words)) }
                TextButton(enabled = parsed.isValid, onClick = { onInsert(link() + " ") }, modifier = Modifier.testTag("verse-link")) {
                    Text(stringResource(R.string.insert_link))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A person or an event of the Bible to link, found by name. */
@Composable
private fun PickDialog(vm: AppViewModel, what: String, onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Pair<String, LinkTarget>>>(emptyList()) }
    LaunchedEffect(q) {
        found = vm.onIo {
            if (what == "person") vm.chronicle.searchPeople(q, 30).map { p -> (p.name + (p.first?.let { " · " + it.shortLabel() } ?: "")) to LinkTarget.Person(p.id) }
            else {
                val n = mg.baiboly.core.text.TextNorm.norm(q.trim())
                vm.chronicle.timeline.filter { n.isEmpty() || mg.baiboly.core.text.TextNorm.norm(it.title).contains(n) }
                    .sortedByDescending { it.weight }.take(30).map { it.title to LinkTarget.Event(it.id) }
            }
        }
    }
    LocalizedDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp).heightIn(max = 520.dp)) {
                Text(stringResource(if (what == "person") R.string.tool_person else R.string.tool_event), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("pick-search"))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(found.size) { i ->
                        val (label, target) = found[i]
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable { onInsert(NoteMarkup.link(label.substringBefore(" · "), target) + " ") }.padding(vertical = 12.dp).testTag("pick:$i"))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

// ---------------------------------------------------------------- export

/** "Save as" with the file's own name and type. */
private class CreateFile : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

/** The note with its heading lines in the interface's language. */
fun sheetOf(context: Context, d: NoteDocument, vm: AppViewModel): NoteSheet {
    val r = context.resources
    val locale = r.configuration.locales[0]
    val meta = listOfNotNull(
        DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(d.date)),
        d.passages.takeIf { it.isNotBlank() },
        d.speaker.takeIf { it.isNotBlank() }?.let { r.getString(if (d.kind == "study") R.string.doc_author else R.string.doc_preacher) + " : " + it },
        d.place.takeIf { it.isNotBlank() },
    )
    return NoteSheet(d.title.ifBlank { r.getString(R.string.untitled) }, meta, d.body)
}

private fun writeExport(context: Context, uri: Uri, sheet: NoteSheet, format: ExportFormat) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
        if (format == ExportFormat.Pdf) NotePdf.write(sheet, out) else out.write(NoteExport.bytes(sheet, format))
    }
}
