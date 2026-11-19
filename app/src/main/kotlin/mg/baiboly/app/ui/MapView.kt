package mg.baiboly.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.Place
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- data

private class Line(val major: Boolean, val p: FloatArray)
private class Route(val names: List<String>, val p: FloatArray)
private class MapLabel(val en: String, val fr: String, val x: Float, val y: Float, val rank: Int)

private class MapData(
    val land: List<FloatArray>,
    val lakes: List<FloatArray>,
    val rivers: List<FloatArray>,
    val roads: List<Line>,
    val routes: List<Route>,
    val realms: Map<String, List<FloatArray>>,
    val countries: List<FloatArray>,
    val countryLabels: List<MapLabel>,
    val cities: List<MapLabel>,
)

private fun loadMap(context: android.content.Context): MapData {
    val o = JSONObject(context.assets.open("map.json").use { it.readBytes().decodeToString() })
    fun floats(a: org.json.JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
    fun layer(name: String, from: JSONObject = o): List<FloatArray> {
        val a = from.optJSONArray(name) ?: return emptyList()
        return (0 until a.length()).map { floats(a.getJSONArray(it)) }
    }
    fun labels(name: String): List<MapLabel> {
        val a = o.optJSONArray(name) ?: return emptyList()
        return (0 until a.length()).map { i ->
            val l = a.getJSONObject(i)
            MapLabel(l.getString("en"), l.getString("fr"), l.getDouble("x").toFloat(), l.getDouble("y").toFloat(), l.optInt("r", 5))
        }
    }
    val roads = o.optJSONArray("roads")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { r -> Line(r.getInt("m") == 1, floats(r.getJSONArray("p"))) } } }
    val routes = o.optJSONArray("routes")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { r -> Route(r.getJSONArray("name").let { n -> List(n.length()) { k -> n.getString(k) } }, floats(r.getJSONArray("p"))) } }
    }
    val realms = o.optJSONObject("realms")?.let { r -> r.keys().asSequence().associateWith { k -> layer(k, r) } }
    return MapData(
        layer("land"), layer("lakes"), layer("rivers"), roads.orEmpty(), routes.orEmpty(), realms.orEmpty(),
        layer("countries"), labels("country_labels"), labels("cities"),
    )
}

// ---------------------------------------------------------------- timeline

/** Periods of the timeline; their order matches the bits of Place.periods. */
private enum class Period(val title: Int, val dates: Int, val realm: String?, val realmName: Int?, val romanRoads: Boolean) {
    Patriarchs(R.string.period_patriarchs, R.string.dates_patriarchs, null, null, false),
    Exodus(R.string.period_exodus, R.string.dates_exodus, null, null, false),
    Conquest(R.string.period_conquest, R.string.dates_conquest, null, null, false),
    Kingdom(R.string.period_kingdom, R.string.dates_kingdom, null, null, false),
    Divided(R.string.period_divided, R.string.dates_divided, null, null, false),
    Exile(R.string.period_exile, R.string.dates_exile, "persia", R.string.realm_persia, false),
    Between(R.string.period_between, R.string.dates_between, "hasmonean", R.string.realm_hasmonean, false),
    Jesus(R.string.period_jesus, R.string.dates_jesus, "herod", R.string.realm_herod, true),
    Church(R.string.period_church, R.string.dates_church, "rome", R.string.realm_rome, true),
}

private enum class BaseMap(val label: Int) { Ancient(R.string.map_ancient), Terrain(R.string.map_terrain), Today(R.string.map_today) }

/**
 * How many of a place's mentions put it on the map at a given zoom: the
 * most-named places first, villages only when zoomed in.
 */
private fun tier(p: Place, period: Period? = null): Int {
    // within one period, weigh the place by that period's verses only (fewer, so lower steps)
    val n = if (period != null) p.periodMentions.getOrElse(period.ordinal) { 0 } else p.mentions
    val steps = if (period != null) intArrayOf(12, 4, 2) else intArrayOf(40, 12, 4)
    return when {
        n >= steps[0] -> 0
        n >= steps[1] -> 1
        n >= steps[2] -> 2
        else -> 3
    }
}

private fun maxTier(dpPerDegree: Float): Int = when {
    dpPerDegree < 110f -> 0
    dpPerDegree < 230f -> 1
    dpPerDegree < 460f -> 2
    else -> 3
}

// ---------------------------------------------------------------- screen

/**
 * Bible places on an offline map drawn by the app (no tiles, no network).
 * A timeline shows the places of one period of biblical history with the
 * borders of that time; layers add roads and today's borders.
 */
@Composable
fun MapScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val map = remember { runCatching { loadMap(ctx) }.getOrNull() }
    var all by remember { mutableStateOf<List<Place>>(emptyList()) }
    var chapterPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var verses by remember { mutableStateOf<List<Pair<Passage, String>>>(emptyList()) }
    var period by rememberSaveable { mutableStateOf<Int?>(null) }
    var base by rememberSaveable { mutableStateOf(BaseMap.Terrain) }
    var roads by rememberSaveable { mutableStateOf(true) }
    var realms by rememberSaveable { mutableStateOf(true) }
    var layersMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        all = vm.places()
        chapterPlaces = vm.placesInChapter()
    }
    LaunchedEffect(selected) {
        verses = selected?.let { p -> vm.placeVerses(p.id).take(60).map { it to vm.verseLine(it) } } ?: emptyList()
    }
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val uiLang = vm.uiLanguage
    val chapterMode = vm.mapForChapter && chapterPlaces.isNotEmpty()
    val p = period?.let { Period.entries[it] }
    Scaffold(
        topBar = {
            MainTopBar(vm, stringResource(R.string.map)) {
                Box {
                    IconButton(onClick = { layersMenu = true }, modifier = Modifier.testTag("map-layers")) {
                        Icon(Icons.Outlined.Layers, stringResource(R.string.map_layers))
                    }
                    DropdownMenu(expanded = layersMenu, onDismissRequest = { layersMenu = false }) {
                        Text(stringResource(R.string.map_layers), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        BaseMap.entries.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(stringResource(b.label)) },
                                leadingIcon = { RadioButton(selected = base == b, onClick = null) },
                                onClick = { base = b; layersMenu = false },
                                modifier = Modifier.testTag("map-base:${b.name}"),
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.map_roads)) },
                            leadingIcon = { Checkbox(checked = roads, onCheckedChange = null) },
                            onClick = { roads = !roads },
                            modifier = Modifier.testTag("map-roads"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.map_realms)) },
                            leadingIcon = { Checkbox(checked = realms, onCheckedChange = null) },
                            onClick = { realms = !realms },
                            modifier = Modifier.testTag("map-realms"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // places of the chapter being read, or every place
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.mapForChapter,
                    onClick = { vm.mapForChapter = true },
                    label = { Text(mg.baiboly.core.bible.Canon.book(vm.location.book).name(lang) + " " + vm.location.chapter) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("map-chapter"),
                )
                FilterChip(
                    selected = !vm.mapForChapter,
                    onClick = { vm.mapForChapter = false },
                    label = { Text(stringResource(R.string.all_places)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("map-all"),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (map != null) {
                    MapCanvas(
                        map = map, places = all, marked = if (vm.mapForChapter) chapterPlaces.map { it.id }.toSet() else emptySet(), fitToMarked = chapterMode,
                        selected = selected, period = p, base = base, roads = roads, realms = realms, uiLang = uiLang,
                    ) { selected = it }
                }
                // what the shading is
                if (realms && p?.realmName != null) {
                    Text(
                        stringResource(p.realmName),
                        style = MaterialTheme.typography.labelMedium, color = LocalOrnament.current.rubric,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("map-realm")
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    "© OpenBible.info (CC BY 4.0) · AWMC (ODbL) · Natural Earth",
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
            Timeline(period) { period = it; selected = null }
            val sel = selected
            if (sel != null) PlaceCard(vm, sel, verses) { selected = null }
        }
    }
}

/** A line of periods, oldest first; the chosen one is filled. "All" shows every place. */
@Composable
private fun Timeline(period: Int?, onPick: (Int?) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { if (period != null) state.scrollToItem(period) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        LazyRow(state = state, modifier = Modifier.fillMaxWidth().testTag("timeline"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
            item {
                TimelineStop(stringResource(R.string.period_all), "", period == null, first = true, last = false, accent, line,
                    Modifier.testTag("period:all")) { onPick(null) }
            }
            itemsIndexed(Period.entries) { i, pe ->
                TimelineStop(stringResource(pe.title), stringResource(pe.dates), period == i, first = false, last = i == Period.entries.lastIndex,
                    accent, line, Modifier.testTag("period:${pe.name}")) { onPick(i) }
            }
        }
    }
}

@Composable
private fun TimelineStop(
    title: String, dates: String, on: Boolean, first: Boolean, last: Boolean, accent: Color, line: Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier.width(118.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the line of time with a stop on it
        Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).height(2.dp).background(if (first) Color.Transparent else line))
                Box(Modifier.weight(1f).height(2.dp).background(if (last) Color.Transparent else line))
            }
            Box(
                Modifier.size(if (on) 14.dp else 10.dp).clip(CircleShape)
                    .background(if (on) accent else MaterialTheme.colorScheme.surface)
                    .then(if (on) Modifier else Modifier.background(line.copy(alpha = 0.0f))),
            ) {
                if (!on) Box(Modifier.fillMaxSize().padding(2.dp).clip(CircleShape).background(line))
            }
        }
        Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
            color = if (on) accent else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The tapped place: what it is, when it appears, and the verses that name it. */
@Composable
private fun PlaceCard(vm: AppViewModel, sel: Place, verses: List<Pair<Passage, String>>, onClose: () -> Unit) {
    val periods = Period.entries.filterIndexed { i, _ -> sel.periods and (1 shl i) != 0 }.map { stringResource(it.title) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.42f).navigationBarsPadding()) {
            GoldRule()
            Row(Modifier.padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sel.displayName, style = MaterialTheme.typography.titleLarge, color = LocalOrnament.current.rubric)
                    Text(
                        listOf(
                            sel.type,
                            stringResource(when (sel.confidence) { 3 -> R.string.place_certain; 2 -> R.string.place_likely; else -> R.string.place_uncertain }),
                            stringResource(R.string.place_mentions, sel.mentions),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (periods.isNotEmpty()) Text(periods.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
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

// ---------------------------------------------------------------- drawing

@Composable
private fun MapCanvas(
    map: MapData,
    places: List<Place>,
    marked: Set<String>,
    fitToMarked: Boolean,
    selected: Place?,
    period: Period?,
    base: BaseMap,
    roads: Boolean,
    realms: Boolean,
    uiLang: String,
    onSelect: (Place?) -> Unit,
) {
    val labelFamily = TitleSerif
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.3f
    val today = base == BaseMap.Today
    val ctx = LocalContext.current
    // shaded relief (Natural Earth), loaded once when first shown
    val relief = remember(base == BaseMap.Terrain) {
        if (base != BaseMap.Terrain) null
        else runCatching { ctx.assets.open("map_relief.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }
    // Today: a cooler, plainer land so the borders read; Ancient: parchment land
    val sea = if (dark) Color(0xFF16222A) else if (today) Color(0xFFD6E3EA) else Color(0xFFD9E4E4)
    val land = if (dark) Color(0xFF2A241D) else if (today) Color(0xFFF2F0EA) else Color(0xFFEDE3CC)
    val coast = if (dark) Color(0xFF5E5240) else Color(0xFFB9A57E)
    val river = if (dark) Color(0xFF3F6F86) else Color(0xFF7FA6B8)
    val road = if (dark) Color(0xFFB59468) else Color(0xFF8A6238)
    val routeColour = scheme.tertiary
    val border = if (dark) Color(0xFF9A9A9A) else Color(0xFF6E6E6E)
    val ornament = LocalOrnament.current
    val ink = scheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val kx = cos(31.5 * PI / 180).toFloat() // equirectangular, true to scale around Jerusalem's latitude

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
    fun scale() = if (ppd > 0f) ppd else if (size.width > 0f) size.width / (5.5f * kx) else 1f
    fun toScreen(lon: Float, lat: Float) = Offset(size.width / 2 + (lon - cLon) * kx * scale(), size.height / 2 - (lat - cLat) * scale())

    // which places are on the map: the period's, then by weight for the zoom; the chapter's and the chosen one always
    val dpPerDegree = scale() / density.density
    val limit = maxTier(dpPerDegree)
    val shown = remember(places, period, limit, marked, selected) {
        places.filter { p ->
            p.id in marked || p == selected ||
                ((period == null || p.periods and (1 shl period.ordinal) != 0) && tier(p, period) <= limit)
        }
    }

    Canvas(
        Modifier.fillMaxSize().clipToBounds().testTag("map")
            .onSizeChanged { size = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val before = Offset((centroid.x - size.width / 2) / (kx * scale()) + cLon, cLat - (centroid.y - size.height / 2) / scale())
                    ppd = (scale() * zoom).coerceIn(8f, 4000f)
                    cLon = before.x - (centroid.x - size.width / 2) / (kx * ppd)
                    cLat = before.y + (centroid.y - size.height / 2) / ppd
                    cLon -= pan.x / (kx * ppd)
                    cLat += pan.y / ppd
                }
            }
            .pointerInput(shown) {
                detectTapGestures { tap ->
                    val radius = 28.dp.toPx()
                    val hit = shown.minByOrNull { p -> toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } }
                    onSelect(hit?.takeIf { p -> toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } <= radius })
                }
            },
    ) {
        if (size.width == 0f) return@Canvas
        val dp = density.density
        fun path(coords: FloatArray, close: Boolean): Path = Path().apply {
            var i = 0
            while (i + 1 < coords.size) {
                val q = toScreen(coords[i], coords[i + 1])
                if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                i += 2
            }
            if (close) close()
        }
        fun onScreen(coords: FloatArray): Boolean {
            var i = 0
            while (i + 1 < coords.size) {
                val q = toScreen(coords[i], coords[i + 1])
                if (q.x > -200 && q.y > -200 && q.x < size.width + 200 && q.y < size.height + 200) return true
                i += 2
            }
            return false
        }

        drawRect(sea)
        for (r in map.land) drawPath(path(r, true), land)
        if (relief != null) {
            // the relief image spans the map's extent (8°E 44°N to 56°E 22°N); multiplied into the land colour
            val tl = toScreen(8f, 44f)
            val br = toScreen(56f, 22f)
            drawImage(
                relief,
                dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.toInt(), tl.y.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize((br.x - tl.x).toInt(), (br.y - tl.y).toInt()),
                alpha = if (dark) 0.55f else 0.9f,
                blendMode = androidx.compose.ui.graphics.BlendMode.Multiply,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
            )
        }
        for (r in map.land) drawPath(path(r, true), coast, style = Stroke(1f))
        for (r in map.lakes) { val pa = path(r, true); drawPath(pa, sea); drawPath(pa, river, style = Stroke(1f)) }
        for (r in map.rivers) drawPath(path(r, false), river, style = Stroke(1.4f * dp))

        // borders of the period (shaded), or today's borders (dashed)
        val realm = period?.realm?.let { map.realms[it] }
        if (realms && realm != null && !today) {
            for (r in realm) {
                val pa = path(r, true)
                drawPath(pa, ornament.rubric.copy(alpha = 0.10f))
                drawPath(pa, ornament.rubric.copy(alpha = 0.55f), style = Stroke(1.2f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 4 * dp))))
            }
        }
        if (today) {
            for (r in map.countries) drawPath(path(r, true), border, style = Stroke(1f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * dp, 3 * dp))))
        }

        // roads: Roman roads in the Roman age (and on the "all" map), the old highways before it
        val occupied = mutableListOf<Rect>()
        if (roads) {
            if (period == null || period.romanRoads) {
                for (l in map.roads) {
                    if (!l.major && dpPerDegree < 90f) continue
                    if (!onScreen(l.p)) continue
                    drawPath(path(l.p, false), road.copy(alpha = if (l.major) 0.65f else 0.4f), style = Stroke((if (l.major) 1.6f else 0.9f) * dp))
                }
            }
            if (period == null || !period.romanRoads) {
                for (rt in map.routes) {
                    drawPath(path(rt.p, false), routeColour.copy(alpha = 0.85f),
                        style = Stroke(2.6f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * dp, 5 * dp))))
                }
            }
        }

        // today: country names and the main cities, in a plain face, under the Bible places
        if (today) {
            for (c in map.countryLabels.sortedBy { it.rank }) {
                if (c.rank > 3 && dpPerDegree < 60f) continue
                val at = toScreen(c.x, c.y)
                val t = measurer.measure((if (uiLang == "en") c.en else c.fr).uppercase(),
                    TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, letterSpacing = 2.sp, color = border.copy(alpha = 0.75f)))
                val rect = Rect(at.x - t.size.width / 2f, at.y - t.size.height / 2f, at.x + t.size.width / 2f, at.y + t.size.height / 2f)
                if (occupied.none { it.overlaps(rect) }) { occupied += rect; drawText(t, topLeft = rect.topLeft) }
            }
            for (c in map.cities.sortedBy { it.rank }) {
                if (c.rank > 2 && dpPerDegree < 120f) continue
                val at = toScreen(c.x, c.y)
                drawRect(border, Offset(at.x - 2.5f * dp, at.y - 2.5f * dp), androidx.compose.ui.geometry.Size(5 * dp, 5 * dp))
                val t = measurer.measure(if (uiLang == "en") c.en else c.fr, TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = border))
                val rect = Rect(at.x + 5 * dp, at.y - t.size.height - 1 * dp, at.x + 5 * dp + t.size.width, at.y - 1 * dp)
                if (occupied.none { it.overlaps(rect) }) { occupied += rect; drawText(t, topLeft = rect.topLeft) }
            }
        }

        // Bible places. Regions are names only (no dot); settlements and the rest have a dot.
        val small = 2.5f * dp
        val big = 5f * dp
        fun important(p: Place) = p.id in marked || p == selected
        val visible = shown.filter { p ->
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            o.x > -60 && o.y > -60 && o.x < size.width + 60 && o.y < size.height + 60
        }
        for (p in visible.sortedBy { if (important(it)) 1 else 0 }) {
            if (p.type == "region" && !important(p)) continue
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            if (important(p)) {
                drawCircle(ornament.rubric, big, o)
                drawCircle(Color.White.copy(alpha = 0.85f), big * 0.38f, o)
            } else drawCircle(ornament.gold, if (tier(p, period) == 0) small * 1.4f else small, o)
        }
        // names by priority: marked, then by weight; a name that would overlap another is left out
        for (p in visible.sortedWith(compareBy<Place>({ if (important(it)) 0 else 1 }, { tier(it, period) }, { -it.mentions }))) {
            val imp = important(p)
            val region = p.type == "region"
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            val style = when {
                imp -> TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ornament.rubric)
                region -> TextStyle(fontFamily = labelFamily, fontStyle = FontStyle.Italic, fontSize = if (tier(p, period) == 0) 15.sp else 12.sp,
                    letterSpacing = 1.5.sp, color = ink.copy(alpha = 0.5f))
                else -> TextStyle(fontFamily = labelFamily, fontWeight = if (tier(p, period) == 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (tier(p, period) == 0) 14.sp else 12.sp, color = ink)
            }
            val label = measurer.measure(p.displayName, style)
            var rect = if (region && !imp) Rect(o.x - label.size.width / 2f, o.y - label.size.height / 2f, o.x + label.size.width / 2f, o.y + label.size.height / 2f)
            else Rect(o.x + big + 3, o.y - label.size.height / 2f, o.x + big + 3 + label.size.width, o.y + label.size.height / 2f)
            if (imp) {
                var tries = 0
                while (occupied.any { it.overlaps(rect) } && tries++ < 6) rect = rect.translate(0f, label.size.height.toFloat())
            } else if (occupied.any { it.overlaps(rect) }) continue
            occupied += rect
            drawText(label, topLeft = Offset(rect.left, rect.top))
        }
        // route names last, where there is still room
        if (roads && (period == null || !period.romanRoads)) {
            val nameIndex = when (uiLang) { "fr" -> 1; "mg" -> 2; else -> 0 }
            for (rt in map.routes) {
                // its name beside the middle of the route
                // beside a stop of the route where nothing else is written, trying the middle first
                val t = measurer.measure(rt.names.getOrElse(nameIndex) { rt.names[0] },
                    TextStyle(fontSize = 11.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, color = routeColour))
                val n = rt.p.size / 2
                val order = (0 until n).sortedBy { kotlin.math.abs(it - n / 2) }
                for (k in order) {
                    val at = toScreen(rt.p[2 * k], rt.p[2 * k + 1])
                    if (at.x < 0 || at.y < 0 || at.x > size.width - t.size.width || at.y > size.height) continue
                    val rect = Rect(at.x + 6 * dp, at.y - t.size.height / 2f, at.x + 6 * dp + t.size.width, at.y + t.size.height / 2f)
                    if (occupied.none { it.overlaps(rect) }) { occupied += rect; drawText(t, topLeft = rect.topLeft); break }
                }
            }
        }
    }
}
