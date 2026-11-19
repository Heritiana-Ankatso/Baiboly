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
import androidx.compose.material3.ModalBottomSheet
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossRefSheet(vm: AppViewModel, verse: Int, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var groups by remember { mutableStateOf<List<CrossRefGroup>?>(null) }
    var lines by remember { mutableStateOf<Map<Passage, String>>(emptyMap()) }
    LaunchedEffect(verse) {
        val g = vm.crossRefs(verse)
        groups = g
        lines = g.flatMap { it.refs }.distinct().take(120).associateWith { vm.verseLine(it) }
    }
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val ornament = LocalOrnament.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.cross_references) + " · " + Passage(vm.location.book, vm.location.chapter, verse, verse).longLabel(lang),
                style = MaterialTheme.typography.titleMedium, color = ornament.rubric,
            )
            Text(stringResource(R.string.tsk_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GoldRule(Modifier.padding(vertical = 8.dp))
            val g = groups
            when {
                g == null -> Text(stringResource(R.string.loading))
                g.isEmpty() -> Text(stringResource(R.string.no_cross_references))
                else -> LazyColumn(Modifier.testTag("xrefs")) {
                    g.forEach { group ->
                        item {
                            Text(
                                group.keyword.ifBlank { "—" },
                                style = MaterialTheme.typography.labelLarge, color = ornament.gold, letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
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
            TopAppBar(
                title = { Text(open ?: stringResource(R.string.dictionary), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
private class MapData(val bbox: FloatArray, val land: List<FloatArray>, val lakes: List<FloatArray>, val rivers: List<FloatArray>)

private fun loadMap(context: android.content.Context): MapData {
    val o = JSONObject(context.assets.open("map.json").use { it.readBytes().decodeToString() })
    fun layer(name: String): List<FloatArray> {
        val a = o.getJSONArray(name)
        return (0 until a.length()).map { i -> val r = a.getJSONArray(i); FloatArray(r.length()) { r.getDouble(it).toFloat() } }
    }
    val b = o.getJSONArray("bbox")
    return MapData(FloatArray(4) { b.getDouble(it).toFloat() }, layer("land"), layer("lakes"), layer("rivers"))
}

/**
 * Bible places on an offline map drawn by the app (no map tiles, no network).
 * Opened from the reader it marks the places of the chapter being read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val map = remember { runCatching { loadMap(ctx) }.getOrNull() }
    var all by remember { mutableStateOf<List<Place>>(emptyList()) }
    var chapterPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var verses by remember { mutableStateOf<List<Pair<Passage, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        all = vm.places()
        chapterPlaces = vm.placesInChapter()
    }
    LaunchedEffect(selected) {
        verses = selected?.let { p -> vm.placeVerses(p.id).take(60).map { it to vm.verseLine(it) } } ?: emptyList()
    }
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val chapterMode = vm.mapForChapter && chapterPlaces.isNotEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.mapForChapter) stringResource(R.string.places_in, mg.baiboly.core.bible.Canon.book(vm.location.book).name(lang) + " " + vm.location.chapter)
                        else stringResource(R.string.map),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { IconButton(onClick = { vm.screen = Screen.Main }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (map != null) MapCanvas(map, all, chapterPlaces.map { it.id }.toSet(), chapterMode, selected) { selected = it }
                Text(
                    "© OpenBible.info (CC BY 4.0) · Natural Earth",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                )
                if (vm.mapForChapter && chapterPlaces.isEmpty() && all.isNotEmpty()) {
                    Text(
                        stringResource(R.string.no_places_in_chapter),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(10.dp),
                    )
                }
            }
            val sel = selected
            if (sel != null) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.42f).navigationBarsPadding()) {
                        GoldRule()
                        Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sel.name, style = MaterialTheme.typography.titleLarge, color = LocalOrnament.current.rubric)
                                Text(
                                    sel.type + " · " + stringResource(
                                        when (sel.confidence) { 3 -> R.string.place_certain; 2 -> R.string.place_likely; else -> R.string.place_uncertain }
                                    ),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { selected = null }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                        }
                        LazyColumn(Modifier.testTag("place-verses")) {
                            items(verses, key = { it.first.toString() }) { (p, line) ->
                                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.openPassage(p) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Text(p.shortLabel(), fontWeight = FontWeight.Bold, color = LocalOrnament.current.rubric)
                                    Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapCanvas(
    map: MapData,
    places: List<Place>,
    marked: Set<String>,
    fitToMarked: Boolean,
    selected: Place?,
    onSelect: (Place?) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    val sea = if (dark) Color(0xFF16222A) else Color(0xFFD9E4E4)
    val land = if (dark) Color(0xFF2A241D) else Color(0xFFEDE3CC)
    val coast = if (dark) Color(0xFF5E5240) else Color(0xFFB9A57E)
    val river = if (dark) Color(0xFF3F6F86) else Color(0xFF7FA6B8)
    val ornament = LocalOrnament.current
    val ink = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val kx = cos(31.5 * PI / 180).toFloat() // equirectangular, true to scale around Jerusalem's latitude

    // view: centre (lon, lat) and pixels per degree
    var cLon by remember { mutableFloatStateOf(35.4f) }
    var cLat by remember { mutableFloatStateOf(31.9f) }
    var ppd by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    LaunchedEffect(size, fitToMarked, marked) {
        if (size.width == 0f) return@LaunchedEffect
        val pts = places.filter { it.id in marked }
        if (fitToMarked && pts.isNotEmpty()) {
            val lo1 = pts.minOf { it.lon }; val lo2 = pts.maxOf { it.lon }
            val la1 = pts.minOf { it.lat }; val la2 = pts.maxOf { it.lat }
            cLon = ((lo1 + lo2) / 2).toFloat(); cLat = ((la1 + la2) / 2).toFloat()
            val w = max(((lo2 - lo1) * kx).toFloat(), 0.6f); val h = max((la2 - la1).toFloat(), 0.6f)
            ppd = min(size.width / (w * 1.9f), size.height / (h * 1.5f)).coerceIn(20f, 2000f)
        } else if (ppd == 0f) {
            ppd = size.width / (5.5f * kx)
        }
    }
    // until a view is fitted, show about 5.5 degrees of longitude around Jerusalem
    fun scale() = if (ppd > 0f) ppd else if (size.width > 0f) size.width / (5.5f * kx) else 1f
    fun toScreen(lon: Float, lat: Float) = Offset(size.width / 2 + (lon - cLon) * kx * scale(), size.height / 2 - (lat - cLat) * scale())

    Canvas(
        Modifier.fillMaxSize().testTag("map")
            .onSizeChanged { size = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // zoom around the fingers, then pan
                    val before = Offset((centroid.x - size.width / 2) / (kx * scale()) + cLon, cLat - (centroid.y - size.height / 2) / scale())
                    ppd = (scale() * zoom).coerceIn(8f, 4000f)
                    cLon = before.x - (centroid.x - size.width / 2) / (kx * ppd)
                    cLat = before.y + (centroid.y - size.height / 2) / ppd
                    cLon -= pan.x / (kx * ppd)
                    cLat += pan.y / ppd
                }
            }
            .pointerInput(places) {
                detectTapGestures { tap ->
                    val radius = 28.dp.toPx()
                    val hit = places.minByOrNull { p -> toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } }
                    onSelect(hit?.takeIf { p -> toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } <= radius })
                }
            },
    ) {
        if (size.width == 0f) return@Canvas
        val ppd = scale()
        drawRect(sea)
        fun ring(coords: FloatArray, close: Boolean): Path = Path().apply {
            var i = 0
            while (i + 1 < coords.size) {
                val p = toScreen(coords[i], coords[i + 1])
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                i += 2
            }
            if (close) close()
        }
        for (r in map.land) { val path = ring(r, true); drawPath(path, land); drawPath(path, coast, style = Stroke(1f)) }
        for (r in map.lakes) { val path = ring(r, true); drawPath(path, sea); drawPath(path, river, style = Stroke(1f)) }
        for (r in map.rivers) drawPath(ring(r, false), river, style = Stroke(with(density) { 1.4.dp.toPx() }))

        // places: all as small gilt dots; the chapter's places and the selected one marked in red with names
        val occupied = mutableListOf<androidx.compose.ui.geometry.Rect>()
        val small = with(density) { 2.5.dp.toPx() }
        val big = with(density) { 5.dp.toPx() }
        val visible = places.filter { p ->
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            o.x > -50 && o.y > -50 && o.x < size.width + 50 && o.y < size.height + 50
        }
        fun important(p: Place) = p.id in marked || p == selected
        // dots: ordinary places first, marked ones on top
        for (p in visible.sortedBy { if (important(it)) 1 else 0 }) {
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            if (important(p)) {
                drawCircle(ornament.rubric, big, o)
                drawCircle(Color.White.copy(alpha = 0.85f), big * 0.38f, o)
            } else drawCircle(ornament.gold, small, o)
        }
        // names: marked places first (stacked when they share a spot), then others where there is room
        for (p in visible.sortedBy { (if (important(it)) 0 else 1) * 10 + (3 - it.confidence) }) {
            val imp = important(p)
            if (!(imp || (ppd > 120f && p.confidence >= 3) || ppd > 400f)) continue
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            val label = measurer.measure(
                p.name,
                TextStyle(fontFamily = TitleSerif, fontWeight = if (imp) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (imp) 15.sp else 12.sp, color = if (imp) ornament.rubric else ink),
            )
            var rect = androidx.compose.ui.geometry.Rect(o.x + big + 3, o.y - label.size.height / 2f, o.x + big + 3 + label.size.width, o.y + label.size.height / 2f)
            if (imp) {
                var tries = 0
                while (occupied.any { it.overlaps(rect) } && tries++ < 6) rect = rect.translate(0f, label.size.height.toFloat())
            } else if (occupied.any { it.overlaps(rect) }) continue
            occupied += rect
            drawText(label, topLeft = Offset(rect.left, rect.top))
        }
    }
}

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

