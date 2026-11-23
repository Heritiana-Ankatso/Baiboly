package mg.baiboly.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Screen
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.CrossRefGroup
import mg.baiboly.data.DictSource
import mg.baiboly.data.Markup
import mg.baiboly.data.Place
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- cross-references

/** Treasury of Scripture Knowledge references for one verse, grouped by keyword. */
@Composable
fun CrossRefContent(vm: AppViewModel, verse: Int, onDismiss: () -> Unit) {
    var groups by remember(verse) { mutableStateOf<List<CrossRefGroup>?>(null) }
    var lines by remember(verse) { mutableStateOf<Map<Passage, String>>(emptyMap()) }
    LaunchedEffect(verse) {
        val g = vm.crossRefs(verse)
        groups = g
        lines = g.flatMap { it.refs }.distinct().take(120).associateWith { vm.verseLine(it) }
    }
    val ornament = LocalOrnament.current
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tsk_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val g = groups
        when {
            g == null -> Text(stringResource(R.string.loading))
            g.isEmpty() -> Text(stringResource(R.string.no_cross_references), modifier = Modifier.padding(top = 12.dp))
            else -> LazyColumn(Modifier.testTag("xrefs")) {
                g.forEach { group ->
                    item {
                        Text(
                            group.keyword.ifBlank { "—" },
                            style = MaterialTheme.typography.titleSmall, color = ornament.gold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    }
                    items(group.refs) { p ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.openPassage(p); onDismiss() }
                                .testTag("xref:${p.book}-${p.chapter}-${p.verseStart}").padding(vertical = 6.dp),
                        ) {
                            Text(p.shortLabel(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ornament.rubric)
                            lines[p]?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dictionary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(vm: AppViewModel) {
    var sources by remember { mutableStateOf<List<DictSource>>(emptyList()) }
    var source by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var keys by remember { mutableStateOf<List<String>>(emptyList()) }
    var open by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        sources = vm.dictSources()
        // French glossary first when the interface is French
        source = (sources.firstOrNull { it.lang == vm.uiLanguage } ?: sources.firstOrNull())?.id
    }
    LaunchedEffect(source, query) { source?.let { keys = vm.dictSearch(it, query) } }
    androidx.activity.compose.BackHandler(enabled = open != null) {
        open = history.lastOrNull(); history = history.dropLast(1)
    }
    Scaffold(
        topBar = {
            if (open == null && isTopLevel(vm.style, vm.mode, vm.screen)) MainTopBar(vm, stringResource(R.string.dictionary))
            else TopAppBar(
                title = { Text(open?.lowercase()?.replaceFirstChar { it.titlecase() } ?: stringResource(R.string.dictionary), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (open != null) { open = history.lastOrNull(); history = history.dropLast(1) } else vm.screen = Screen.Main
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val src = sources.firstOrNull { it.id == source }
            val key = open
            if (key != null && src != null) {
                DictEntryView(vm, src, key, onLink = { history = history + key; open = it })
                return@Column
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.id }) { s ->
                    FilterChip(
                        selected = s.id == source,
                        onClick = { source = s.id },
                        label = { Text(dictLabel(s)) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("dict-source:${s.id}"),
                    )
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.dictionary_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.testTag("dict-keys")) {
                items(keys, key = { it }) { k ->
                    Text(
                        k.lowercase().replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = k }
                            .testTag("dict-key:$k").padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            src?.let {
                Text(
                    it.name + " · " + it.licence,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun dictLabel(s: DictSource): String = when (s.id) {
    "easton" -> stringResource(R.string.dict_easton)
    "nave" -> stringResource(R.string.dict_nave)
    "martin" -> stringResource(R.string.dict_martin)
    else -> s.name
}

/** An entry: Bible references open the passage, "see" links open another entry. */
@Composable
private fun DictEntryView(vm: AppViewModel, source: DictSource, key: String, onLink: (String) -> Unit) {
    var parts by remember(source.id, key) { mutableStateOf<List<Markup>?>(null) }
    LaunchedEffect(source.id, key) {
        parts = vm.dictEntry(source.id, key)?.let { vm.library.markup(it.body) } ?: emptyList()
    }
    val ornament = LocalOrnament.current
    val linkStyle = TextLinkStyles(SpanStyle(color = ornament.rubric, fontWeight = FontWeight.Bold))
    val text = remember(parts, ornament) {
        buildAnnotatedString {
            for (p in parts.orEmpty()) when (p) {
                is Markup.Plain -> append(p.text)
                is Markup.Ref -> withLink(LinkAnnotation.Clickable("ref", linkStyle) { vm.openPassage(p.passage) }) { append(p.label) }
                is Markup.Link -> withLink(LinkAnnotation.Clickable("link", linkStyle) { onLink(p.key) }) { append(p.label) }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(key.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric)
        Fleuron()
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp), modifier = Modifier.testTag("dict-entry"))
        Text(
            source.name + " · " + source.licence,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

// ---------------------------------------------------------------- map

/** Land, lakes and rivers (assets/map.json, Natural Earth). */
// ---------------------------------------------------------------- reading aloud bar

/** Shown while the Bible is being read aloud: what is read, and the controls. */
@Composable
fun ReadingBar(vm: AppViewModel) {
    val s = vm.readState
    if (!s.active) return
    val lang = vm.translations.firstOrNull { it.id == s.translation }?.lang ?: "mg"
    val ornament = LocalOrnament.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            GoldRule()
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🕮", color = ornament.gold, modifier = Modifier.padding(horizontal = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.book > 0) Passage(s.book, s.chapter, s.verse.coerceAtLeast(1), s.verse.coerceAtLeast(1)).longLabel(lang) else "",
                        style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    )
                    Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.labelSmall, color = ornament.gold)
                }
                IconButton(onClick = { vm.previousVerseReading() }) { Text("⏮", fontSize = 18.sp, color = ornament.gold) }
                IconButton(onClick = { if (s.playing) vm.pauseReading() else vm.resumeReading() }, modifier = Modifier.testTag("read-toggle")) {
                    if (s.playing) Text("⏸", fontSize = 20.sp, color = ornament.rubric)
                    else Icon(Icons.Filled.PlayArrow, stringResource(R.string.resume), tint = ornament.rubric)
                }
                IconButton(onClick = { vm.nextVerseReading() }) { Text("⏭", fontSize = 18.sp, color = ornament.gold) }
                IconButton(onClick = { vm.stopReading() }, modifier = Modifier.testTag("read-stop")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.stop), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


// ---------------------------------------------------------------- what's new

/** Release notes, shown once after an update (and from About). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(version: String, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ornament = LocalOrnament.current
    val items = androidx.compose.ui.res.stringArrayResource(R.array.whats_new_items)
    LocalizedSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp).testTag("whats-new"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✠", color = ornament.gold, fontSize = 22.sp)
            Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.headlineSmall, color = ornament.rubric)
            Text(stringResource(R.string.version_x, version), style = MaterialTheme.typography.labelMedium, color = ornament.gold, letterSpacing = 2.sp)
            Fleuron()
            // The list takes the room left between the title and the button, and
            // scrolls within it; the button stays visible on any screen.
            // (Order matters: the size limit must come BEFORE verticalScroll.
            // The other way round, the scroll area grew with the list, then the
            // list was cut off and the button pushed out of the sheet.)
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("whats-new-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    Row {
                        Text("❧", color = ornament.gold, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                        Text(item, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            androidx.compose.material3.Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(27.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.whats_new_ok), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
