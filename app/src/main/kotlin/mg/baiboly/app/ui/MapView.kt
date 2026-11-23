package mg.baiboly.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.text.TextNorm
import mg.baiboly.data.Place
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------- data

private class Line(val major: Boolean, val p: FloatArray)
private class Route(val names: List<String>, val p: FloatArray)
private class MapLabel(val en: String, val fr: String, val x: Float, val y: Float, val rank: Int)

/**
 * The outline of a region (OpenBible.info). [bands]: nested rings of
 * decreasing likelihood, outermost first, when the extent is uncertain;
 * otherwise a single rough boundary.
 */
private class Region(val rings: List<FloatArray>, val bands: Boolean) {
    private val outer = rings.first()
    val lo1 = outer.filterIndexed { i, _ -> i % 2 == 0 }.min()
    val lo2 = outer.filterIndexed { i, _ -> i % 2 == 0 }.max()
    val la1 = outer.filterIndexed { i, _ -> i % 2 == 1 }.min()
    val la2 = outer.filterIndexed { i, _ -> i % 2 == 1 }.max()

    /** The band of middle likelihood: what a tap inside the region hits. */
    private val core = rings[rings.size / 2]
    val area: Float = run {
        val xs = core.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = core.filterIndexed { i, _ -> i % 2 == 1 }
        (xs.max() - xs.min()) * (ys.max() - ys.min())
    }

    fun contains(lon: Float, lat: Float): Boolean {
        var inside = false
        var j = core.size - 2
        var i = 0
        while (i + 1 < core.size) {
            val xi = core[i]; val yi = core[i + 1]; val xj = core[j]; val yj = core[j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-9f) + xi) inside = !inside
            j = i
            i += 2
        }
        return inside
    }
}

/** A journey told in the Bible: its stops (place ids) in the order of the text. */
private class Journey(val id: String, val names: List<String>, val ref: Passage, val stops: List<String>, val p: FloatArray) {
    fun name(lang: String) = names.getOrElse(when (lang) { "fr" -> 1; "mg" -> 2; else -> 0 }) { names[0] }
    val km: Double get() = lineKm(p)
}

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
    val regions: Map<String, Region>,
    val journeys: List<Journey>,
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
    val regions = o.optJSONObject("regions")?.let { r ->
        r.keys().asSequence().associateWith { k -> r.getJSONObject(k).let { g -> Region(layer("r", g), g.optInt("b") == 1) } }
            .filterValues { it.rings.isNotEmpty() }
    }
    val journeys = o.optJSONArray("journeys")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val j = a.getJSONObject(i)
            val ref = j.getJSONArray("ref")
            runCatching {
                Journey(
                    j.getString("id"),
                    j.getJSONArray("name").let { n -> List(n.length()) { k -> n.getString(k) } },
                    Passage(ref.getInt(0), ref.getInt(1), ref.getInt(2), ref.getInt(2)),
                    j.getJSONArray("stops").let { n -> List(n.length()) { k -> n.getString(k) } },
                    floats(j.getJSONArray("p")),
                )
            }.getOrNull()
        }
    }
    return MapData(
        layer("land"), layer("lakes"), layer("rivers"), roads.orEmpty(), routes.orEmpty(), realms.orEmpty(),
        layer("countries"), labels("country_labels"), labels("cities"), regions.orEmpty(), journeys.orEmpty(),
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

private fun Place.inPeriod(period: Period?) = period == null || periods and (1 shl period.ordinal) != 0

// ---------------------------------------------------------------- camera

/** Equirectangular, true to scale around Jerusalem's latitude. */
private val KX = cos(31.5 * PI / 180).toFloat()

/** Where the map looks: its centre, and its scale in pixels per degree of latitude. */
@Stable
private class MapCamera {
    var lon by mutableFloatStateOf(35.4f)
    var lat by mutableFloatStateOf(31.9f)
    var ppd by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    /** Room taken over the map by the controls (top) and the panels (bottom), in pixels. */
    var top by mutableFloatStateOf(0f)
    var bottom by mutableFloatStateOf(0f)

    fun scale() = if (ppd > 0f) ppd else if (width > 0f) defaultScale() else 1f
    fun defaultScale() = width / (5.5f * KX)
    fun toScreen(x: Float, y: Float) = Offset(width / 2 + (x - lon) * KX * scale(), height / 2 - (y - lat) * scale())
    fun toLonLat(o: Offset) = Offset((o.x - width / 2) / (KX * scale()) + lon, lat - (o.y - height / 2) / scale())

    /** The centre and scale that show the box (lon1, lat1, lon2, lat2) between the controls and the panels. */
    fun framing(lo1: Float, la1: Float, lo2: Float, la2: Float, minSpan: Float = 0.5f): Triple<Float, Float, Float> {
        val pad = 0.08f * width
        val visH = (height - top - bottom).coerceAtLeast(height * 0.3f)
        val w = max((lo2 - lo1) * KX, minSpan * KX)
        val h = max(la2 - la1, minSpan)
        val s = min((width - 2 * pad) / w, (visH - pad) / h).coerceIn(8f, 4000f)
        val visCentre = top + visH / 2
        return Triple((lo1 + lo2) / 2, (la1 + la2) / 2 + (visCentre - height / 2) / s, s)
    }

    /** The centre that puts (x, y) in the middle of the visible part, at scale [s]. */
    fun centreOn(x: Float, y: Float, s: Float): Triple<Float, Float, Float> {
        val visH = (height - top - bottom).coerceAtLeast(height * 0.3f)
        return Triple(x, y + (top + visH / 2 - height / 2) / s, s)
    }

    suspend fun animateTo(target: Triple<Float, Float, Float>) {
        val (x1, y1, s1) = target
        val x0 = lon; val y0 = lat; val s0 = scale()
        animate(0f, 1f, animationSpec = tween(500)) { t, _ ->
            lon = x0 + (x1 - x0) * t
            lat = y0 + (y1 - y0) * t
            ppd = exp(ln(s0) + (ln(s1) - ln(s0)) * t) // zoom evenly
        }
    }

    suspend fun fit(lo1: Float, la1: Float, lo2: Float, la2: Float, minSpan: Float = 0.5f) = animateTo(framing(lo1, la1, lo2, la2, minSpan))

    suspend fun fitPoints(p: FloatArray, minSpan: Float = 0.5f) {
        if (p.size < 2) return
        var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < p.size) { a = min(a, p[i]); c = max(c, p[i]); b = min(b, p[i + 1]); d = max(d, p[i + 1]); i += 2 }
        fit(a, b, c, d, minSpan)
    }

    suspend fun zoom(factor: Float) = animateTo(Triple(lon, lat, (scale() * factor).coerceIn(8f, 4000f)))
}

// ---------------------------------------------------------------- screen

/** What the panel at the foot of the map shows. */
private sealed interface Panel {
    data object None : Panel
    data object Regions : Panel
    data object Routes : Panel
}

/** A way between two places chosen by the user. */
private class Planned(val from: Place, val to: Place, val route: RoadRoute)

/**
 * Bible places on an offline map drawn by the app (no tiles, no network).
 * It fills the screen; the controls float over it: search, layers, full
 * screen, zoom. A timeline shows the places of one period of biblical history
 * with the borders of that time; regions are drawn as areas; the journeys of
 * the Bible and the way between any two places are traced on the ancient roads.
 */
@Composable
fun MapScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val map = remember { runCatching { loadMap(ctx) }.getOrNull() }
    val graph by produceState<RoadGraph?>(null, map) {
        value = map?.let { m -> withContext(Dispatchers.Default) { RoadGraph(m.roads.map { it.p } + m.routes.map { it.p }) } }
    }
    var all by remember { mutableStateOf<List<Place>>(emptyList()) }
    var chapterPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var verses by remember { mutableStateOf<List<Pair<Passage, String>>>(emptyList()) }
    var period by rememberSaveable { mutableStateOf<Int?>(null) }
    var base by rememberSaveable { mutableStateOf(BaseMap.Terrain) }
    var roads by rememberSaveable { mutableStateOf(true) }
    var realms by rememberSaveable { mutableStateOf(true) }
    var outlines by rememberSaveable { mutableStateOf(false) }
    var layersMenu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel>(Panel.None) }
    var journey by remember { mutableStateOf<Journey?>(null) }
    var planFrom by remember { mutableStateOf<Place?>(null) }
    var planTo by remember { mutableStateOf<Place?>(null) }
    var planned by remember { mutableStateOf<Planned?>(null) }
    /** "from" or "to" while the search picks a place for the route. */
    var picking by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val camera = remember { MapCamera() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val lang = LocalConfiguration.current.locales[0].language
    val fullscreen = vm.mapFullscreen

    LaunchedEffect(Unit) {
        all = vm.places()
        chapterPlaces = vm.placesInChapter()
    }
    LaunchedEffect(selected) {
        verses = selected?.let { p -> vm.placeVerses(p.id).take(60).map { it to vm.verseLine(it) } } ?: emptyList()
    }
    // the way between the two chosen places
    LaunchedEffect(planFrom, planTo, graph) {
        val a = planFrom; val b = planTo; val g = graph
        planned = if (a != null && b != null && g != null && a != b) {
            Planned(a, b, withContext(Dispatchers.Default) { g.route(a.lon, a.lat, b.lon, b.lat) })
        } else null
        planned?.let { journey = null; camera.fitPoints(it.route.points, 0.3f) }
    }

    val uiLang = vm.uiLanguage
    val chapterMode = vm.mapForChapter && chapterPlaces.isNotEmpty()
    val p = period?.let { Period.entries[it] }
    val byId = remember(all) { all.associateBy { it.id } }
    val region = selected?.let { map?.regions?.get(it.id) }

    fun select(place: Place?) {
        selected = place
        if (place != null) {
            panel = Panel.None
            val r = map?.regions?.get(place.id)
            scope.launch {
                // a region is framed whole; a town is zoomed to, if the map is far out
                if (r != null) camera.fit(r.lo1, r.la1, r.lo2, r.la2)
                else camera.animateTo(camera.centreOn(place.lon.toFloat(), place.lat.toFloat(), max(camera.scale(), 260f * density.density)))
            }
        }
    }
    fun showJourney(j: Journey) {
        journey = j; planned = null; planFrom = null; planTo = null; selected = null
        scope.launch { camera.fitPoints(j.p) }
    }
    fun pick(place: Place) {
        when (picking) {
            "from" -> { planFrom = place; panel = Panel.Routes }
            "to" -> { planTo = place; panel = Panel.Routes }
            else -> select(place)
        }
        picking = null; query = ""; searching = false; focus.clearFocus()
    }
    // opened from a passage: show that place
    LaunchedEffect(vm.mapFocus, all) {
        val id = vm.mapFocus ?: return@LaunchedEffect
        val place = all.firstOrNull { it.id == id } ?: return@LaunchedEffect
        vm.mapFocus = null
        kotlinx.coroutines.delay(300) // the map has its size
        select(place)
    }
    // Back closes what is open on the map before leaving it
    BackHandler(enabled = searching || picking != null || panel != Panel.None || selected != null || journey != null || planned != null) {
        when {
            searching || picking != null -> { searching = false; picking = null; query = ""; focus.clearFocus() }
            selected != null -> selected = null
            panel != Panel.None -> panel = Panel.None
            else -> { journey = null; planned = null; planFrom = null; planTo = null }
        }
    }

    Scaffold(
        topBar = { if (!fullscreen) MainTopBar(vm, stringResource(R.string.map)) },
        contentWindowInsets = if (fullscreen) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().clipToBounds()) {
            if (map != null) {
                val marked = buildSet {
                    if (vm.mapForChapter) chapterPlaces.forEach { add(it.id) }
                    journey?.stops?.let { addAll(it) }
                    planned?.let { add(it.from.id); add(it.to.id) }
                }
                MapCanvas(
                    map = map, places = all, marked = marked, fitToMarked = chapterMode && journey == null && planned == null,
                    selected = selected, period = p, base = base, roads = roads, realms = realms, outlines = outlines, uiLang = uiLang,
                    camera = camera, region = region, journey = journey, planned = planned?.route,
                    onTap = { place, at ->
                        if (searching) { searching = false; focus.clearFocus() }
                        if (place != null) select(place)
                        else {
                            // a tap inside a region shows it (the smallest that contains the point)
                            val hit = map.regions.entries
                                .filter { (id, r) -> byId[id]?.inPeriod(p) == true && r.contains(at.x, at.y) }
                                .minByOrNull { it.value.area }
                            selected = hit?.let { byId[it.key] }
                            if (hit != null) panel = Panel.None
                        }
                    },
                )
            }

            // ---- controls over the map, top
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .onSizeChanged { camera.top = it.height.toFloat() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchPill(
                        query = query,
                        hint = stringResource(when (picking) { "from" -> R.string.route_pick_from; "to" -> R.string.route_pick_to; else -> R.string.map_search_hint }),
                        active = searching || picking != null,
                        onQuery = { query = it; searching = true },
                        onFocus = { if (it) searching = true },
                        onClose = { searching = false; picking = null; query = ""; focus.clearFocus() },
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        MapButton(Icons.Outlined.Layers, stringResource(R.string.map_layers), Modifier.testTag("map-layers")) { layersMenu = true }
                        LocalizedMenu(expanded = layersMenu, onDismissRequest = { layersMenu = false }) {
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.map_region_outlines)) },
                                leadingIcon = { Checkbox(checked = outlines, onCheckedChange = null) },
                                onClick = { outlines = !outlines },
                                modifier = Modifier.testTag("map-outlines"),
                            )
                        }
                    }
                    MapButton(
                        if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        stringResource(if (fullscreen) R.string.map_exit_fullscreen else R.string.map_fullscreen),
                        Modifier.testTag("map-fullscreen"),
                    ) { vm.changeMapFullscreen(!fullscreen) }
                }
                if (searching && query.isNotBlank()) {
                    SearchResults(all, query, map?.regions?.keys.orEmpty()) { pick(it) }
                } else if (picking == null) {
                    // what the map shows, and the tools
                    LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            MapChip(mg.baiboly.core.bible.Canon.book(vm.location.book).name(vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg") + " " + vm.location.chapter,
                                vm.mapForChapter, Modifier.testTag("map-chapter")) { vm.mapForChapter = true }
                        }
                        item { MapChip(stringResource(R.string.all_places), !vm.mapForChapter, Modifier.testTag("map-all")) { vm.mapForChapter = false } }
                        item {
                            MapChip(stringResource(R.string.map_regions), panel == Panel.Regions, Modifier.testTag("map-regions")) {
                                panel = if (panel == Panel.Regions) Panel.None else Panel.Regions; selected = null
                            }
                        }
                        item {
                            MapChip(stringResource(R.string.map_routes), panel == Panel.Routes, Modifier.testTag("map-routes")) {
                                panel = if (panel == Panel.Routes) Panel.None else Panel.Routes; selected = null
                            }
                        }
                    }
                }
            }

            // ---- zoom, right side
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapButton(Icons.Filled.Add, stringResource(R.string.map_zoom_in), Modifier.testTag("map-zoom-in")) { scope.launch { camera.zoom(2f) } }
                MapButton(Icons.Filled.Remove, stringResource(R.string.map_zoom_out), Modifier.testTag("map-zoom-out")) { scope.launch { camera.zoom(0.5f) } }
                MapButton(Icons.Outlined.ZoomOutMap, stringResource(R.string.map_recenter), Modifier.testTag("map-recenter")) {
                    scope.launch {
                        val pts = chapterPlaces.takeIf { vm.mapForChapter && it.isNotEmpty() }
                        when {
                            journey != null -> camera.fitPoints(journey!!.p)
                            planned != null -> camera.fitPoints(planned!!.route.points, 0.3f)
                            pts != null -> camera.fit(pts.minOf { it.lon }.toFloat(), pts.minOf { it.lat }.toFloat(), pts.maxOf { it.lon }.toFloat(), pts.maxOf { it.lat }.toFloat(), 0.6f)
                            else -> camera.animateTo(Triple(35.4f, 31.9f, camera.defaultScale()))
                        }
                    }
                }
            }

            // ---- foot: what is shaded, the timeline, the open panel
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .onSizeChanged { camera.bottom = it.height.toFloat() },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        if (realms && p?.realmName != null) {
                            Text(
                                stringResource(p.realmName),
                                style = MaterialTheme.typography.labelMedium, color = LocalOrnament.current.rubric,
                                modifier = Modifier.padding(bottom = 4.dp).testTag("map-realm")
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            "© OpenBible.info (CC BY 4.0) · AWMC (ODbL) · Natural Earth",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                        )
                    }
                }
                if (vm.mapForChapter && chapterPlaces.isEmpty() && all.isNotEmpty() && journey == null && planned == null && selected == null && panel == Panel.None) {
                    Text(
                        stringResource(R.string.no_places_in_chapter),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(10.dp),
                    )
                }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)) {
                        val sel = selected
                        val j = journey
                        when {
                            sel != null -> PlaceCard(vm, sel, verses, region != null, onClose = { selected = null },
                                onFrom = { planFrom = sel; selected = null; panel = Panel.Routes },
                                onTo = { planTo = sel; selected = null; panel = Panel.Routes })
                            panel == Panel.Regions && map != null -> RegionsPanel(all, map.regions.keys, p, onClose = { panel = Panel.None }) { select(it) }
                            panel == Panel.Routes && map != null -> RoutesPanel(
                                vm, map.journeys, j, planFrom, planTo, planned, graph != null, lang,
                                onJourney = { showJourney(it) },
                                onPick = { which -> picking = which; searching = true; query = "" },
                                onSwap = { val a = planFrom; planFrom = planTo; planTo = a },
                                onClear = { planFrom = null; planTo = null; planned = null; journey = null },
                                onClose = { panel = Panel.None },
                            )
                            j != null -> JourneyBar(vm, j, lang, onOpen = { panel = Panel.Routes }, onClose = { journey = null })
                            planned != null -> RouteBar(planned!!, onOpen = { panel = Panel.Routes }, onClose = { planned = null; planFrom = null; planTo = null })
                        }
                        Timeline(period) { period = it; selected = null }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- controls

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 3.dp,
        modifier = Modifier.padding(start = 6.dp).size(48.dp),
    ) {
        IconButton(onClick = onClick, modifier = modifier) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun MapChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        modifier = modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The search field over the map. */
@Composable
private fun SearchPill(
    query: String, hint: String, active: Boolean,
    onQuery: (String) -> Unit, onFocus: (Boolean) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) runCatching { focusRequester.requestFocus() } }
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = modifier.heightIn(min = 48.dp)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) }.testTag("map-search"),
                )
            }
            if (active || query.isNotEmpty()) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
        }
    }
}

/** Places whose name matches: names starting with the query first, then the most named. */
@Composable
private fun SearchResults(places: List<Place>, query: String, regionIds: Set<String>, onPick: (Place) -> Unit) {
    val q = remember(query) { TextNorm.norm(query.trim()) }
    val hits = remember(places, q) {
        places.asSequence()
            .map { it to TextNorm.norm(it.displayName) }
            .filter { (_, n) -> n.contains(q) }
            .sortedWith(compareBy<Pair<Place, String>>({ if (it.second.startsWith(q)) 0 else 1 }, { -it.first.mentions }))
            .take(40).map { it.first }.toList()
    }
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 320.dp),
    ) {
        if (hits.isEmpty()) {
            Text(stringResource(R.string.map_no_results), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.testTag("map-search-results")) {
            items(hits, key = { it.id }) { pl ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(pl) }.testTag("map-hit:${pl.id}").padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(pl.type, if (pl.id in regionIds) "▧" else null, stringResource(R.string.place_mentions, pl.mentions)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
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
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().testTag("timeline"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
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

@Composable
private fun TimelineStop(
    title: String, dates: String, on: Boolean, first: Boolean, last: Boolean, accent: Color, line: Color,
    modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the line of time with a stop on it
        Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).height(2.dp).background(if (first) Color.Transparent else line))
                Box(Modifier.weight(1f).height(2.dp).background(if (last) Color.Transparent else line))
            }
            Box(
                Modifier.size(if (on) 14.dp else 10.dp).clip(CircleShape)
                    .background(if (on) accent else MaterialTheme.colorScheme.surface),
            ) {
                if (!on) Box(Modifier.fillMaxSize().padding(2.dp).clip(CircleShape).background(line))
            }
        }
        Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
            color = if (on) accent else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- panels

@Composable
private fun PanelHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    GoldRule()
    Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = LocalOrnament.current.rubric, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
    }
}

/** The tapped place: what it is, when it appears, the verses that name it; a way from or to it. */
@Composable
private fun PlaceCard(
    vm: AppViewModel, sel: Place, verses: List<Pair<Passage, String>>, hasOutline: Boolean,
    onClose: () -> Unit, onFrom: () -> Unit, onTo: () -> Unit,
) {
    val periods = Period.entries.filterIndexed { i, _ -> sel.periods and (1 shl i) != 0 }.map { stringResource(it.title) }
    val height = LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxWidth().heightIn(max = (height * 0.42f).dp).testTag("place-card")) {
        PanelHeader(
            sel.displayName,
            listOf(
                sel.type,
                stringResource(when (sel.confidence) { 3 -> R.string.place_certain; 2 -> R.string.place_likely; else -> R.string.place_uncertain }),
                stringResource(R.string.place_mentions, sel.mentions),
            ).joinToString(" · "),
            onClose,
        )
        if (periods.isNotEmpty()) Text(periods.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
        if (hasOutline) Text(stringResource(R.string.region_outline_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).testTag("region-note"))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onFrom, modifier = Modifier.heightIn(min = 48.dp).testTag("route-from-here")) {
                Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.route_from_here))
            }
            TextButton(onClick = onTo, modifier = Modifier.heightIn(min = 48.dp).testTag("route-to-here")) {
                Text(stringResource(R.string.route_to_here))
            }
        }
        LazyColumn(Modifier.testTag("place-verses")) {
            items(verses, key = { it.first.toString() }) { (ps, line) ->
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.openPassage(ps) }.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(ps.shortLabel(), fontWeight = FontWeight.Bold, color = LocalOrnament.current.rubric)
                    Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Kinds of region, from the type OpenBible.info gives each one. */
private enum class RegionKind(val label: Int, val types: Set<String>) {
    Lands(R.string.region_kind_lands, setOf("region", "people group", "natural area")),
    Relief(R.string.region_kind_relief, setOf("valley", "mountain range")),
    Waters(R.string.region_kind_waters, setOf("body of water", "island")),
}

/**
 * Regions drawn as areas (kingdoms, peoples, provinces…), those of the period when one is chosen.
 * A filter by name (accents ignored, names starting with it first), the order (most named or
 * A–Z, with letter headings) and the kind narrow the list: scrolling 190 names was tedious.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RegionsPanel(places: List<Place>, outlined: Set<String>, period: Period?, onClose: () -> Unit, onPick: (Place) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetical by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember(places, outlined, period) { places.filter { it.id in outlined && it.inPeriod(period) } }
    val q = remember(query) { TextNorm.norm(query.trim()) }
    val list = remember(all, q, alphabetical, kind, period) {
        val types = kind?.let { k -> RegionKind.valueOf(k).types }
        all.asSequence()
            .filter { types == null || it.type in types }
            .map { it to TextNorm.norm(it.displayName) }
            .filter { (_, n) -> q.isEmpty() || n.contains(q) }
            .sortedWith(
                if (alphabetical) compareBy({ if (q.isNotEmpty() && !it.second.startsWith(q)) 1 else 0 }, { it.second })
                else compareBy<Pair<Place, String>>({ if (q.isNotEmpty() && !it.second.startsWith(q)) 1 else 0 },
                    { -(if (period != null) it.first.periodMentions.getOrElse(period.ordinal) { 0 } else it.first.mentions) }),
            )
            .map { it.first }.toList()
    }
    val height = LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxWidth().heightIn(max = (height * 0.5f).dp).testTag("regions-panel")) {
        PanelHeader(stringResource(R.string.map_regions), stringResource(R.string.regions_hint), onClose)
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.regions_filter_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) } }) else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).testTag("regions-filter"),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                androidx.compose.material3.FilterChip(selected = !alphabetical, onClick = { alphabetical = false },
                    label = { Text(stringResource(R.string.regions_sort_named)) }, modifier = Modifier.testTag("regions-sort-named"))
            }
            item {
                androidx.compose.material3.FilterChip(selected = alphabetical, onClick = { alphabetical = true },
                    label = { Text(stringResource(R.string.regions_sort_az)) }, modifier = Modifier.testTag("regions-sort-az"))
            }
            item { androidx.compose.material3.VerticalDivider(Modifier.height(32.dp).padding(horizontal = 2.dp)) }
            item {
                androidx.compose.material3.FilterChip(selected = kind == null, onClick = { kind = null },
                    label = { Text(stringResource(R.string.region_kind_all)) }, modifier = Modifier.testTag("regions-kind:All"))
            }
            items(RegionKind.entries, key = { it.name }) { k ->
                androidx.compose.material3.FilterChip(selected = kind == k.name, onClick = { kind = if (kind == k.name) null else k.name },
                    label = { Text(stringResource(k.label)) }, modifier = Modifier.testTag("regions-kind:${k.name}"))
            }
        }
        Text(
            stringResource(R.string.regions_count, list.size, all.size), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).testTag("regions-count"),
        )
        if (list.isEmpty()) {
            Text(stringResource(R.string.map_no_results), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.testTag("regions-list")) {
            var letter = ""
            list.forEach { r ->
                // A–Z: a heading for each first letter
                val first = TextNorm.norm(r.displayName).take(1).uppercase()
                if (alphabetical && q.isEmpty() && first != letter) {
                    letter = first
                    stickyHeader(key = "letter-$first") {
                        Text(first, style = MaterialTheme.typography.titleSmall, color = LocalOrnament.current.gold,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
                item(key = r.id) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(r) }.testTag("region:${r.id}").padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("▧", color = LocalOrnament.current.rubric, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(listOf(r.type, stringResource(R.string.place_mentions, r.mentions)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

private fun kmText(km: Double) = if (km < 20) "%.1f".format(km) else (km.roundToInt()).toString()

/** Journeys of the Bible, and the way between two places of your choice. */
@Composable
private fun RoutesPanel(
    vm: AppViewModel, journeys: List<Journey>, current: Journey?, from: Place?, to: Place?, planned: Planned?, ready: Boolean, lang: String,
    onJourney: (Journey) -> Unit, onPick: (String) -> Unit, onSwap: () -> Unit, onClear: () -> Unit, onClose: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(if (from != null || to != null) 1 else 0) }
    LaunchedEffect(from, to) { if (from != null || to != null) tab = 1 }
    val height = LocalConfiguration.current.screenHeightDp
    Column(Modifier.fillMaxWidth().heightIn(max = (height * 0.46f).dp).testTag("routes-panel")) {
        PanelHeader(stringResource(R.string.map_routes), onClose = onClose)
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.map_journeys)) }, modifier = Modifier.testTag("routes-tab:journeys"))
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.map_plan)) }, modifier = Modifier.testTag("routes-tab:plan"))
        }
        if (tab == 0) {
            LazyColumn(Modifier.testTag("journeys-list")) {
                items(journeys, key = { it.id }) { j ->
                    val on = j == current
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .background(if (on) LocalOrnament.current.gold.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onJourney(j) }.testTag("journey:${j.id}").padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(j.name(lang), style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            Text(stringResource(R.string.journey_length, j.stops.size, kmText(j.km)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            j.ref.shortLabel(), style = MaterialTheme.typography.labelMedium, color = LocalOrnament.current.rubric,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openPassage(j.ref) }.padding(8.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                item {
                    Text(stringResource(R.string.journey_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceField(stringResource(R.string.route_from), from, Modifier.testTag("route-from")) { onPick("from") }
                        PlaceField(stringResource(R.string.route_to), to, Modifier.testTag("route-to")) { onPick("to") }
                    }
                    IconButton(onClick = onSwap, modifier = Modifier.testTag("route-swap")) { Icon(Icons.Filled.SwapVert, stringResource(R.string.route_swap)) }
                }
                when {
                    planned != null -> {
                        val r = planned.route
                        Text(
                            stringResource(if (r.byRoad) R.string.route_by_road else R.string.route_straight, kmText(r.km)),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("route-result"),
                        )
                        Text(stringResource(R.string.route_days, kmText(r.km / RoadGraph.KM_PER_DAY)),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.route_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_clear)) }
                    }
                    from != null && to != null && !ready -> Text(stringResource(R.string.loading))
                    else -> Text(stringResource(R.string.route_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaceField(label: String, place: Place?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
            Text(place?.displayName ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The journey on the map: its name and length; a tap opens the list. */
@Composable
private fun JourneyBar(vm: AppViewModel, j: Journey, lang: String, onOpen: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.testTag("journey-bar")) {
        GoldRule()
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(j.name(lang), style = MaterialTheme.typography.titleMedium, color = LocalOrnament.current.rubric)
                Text(stringResource(R.string.journey_length, j.stops.size, kmText(j.km)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(j.ref.shortLabel(), style = MaterialTheme.typography.labelMedium, color = LocalOrnament.current.rubric,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openPassage(j.ref) }.padding(8.dp))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun RouteBar(planned: Planned, onOpen: () -> Unit, onClose: () -> Unit) {
    val r = planned.route
    Column(Modifier.testTag("route-bar")) {
        GoldRule()
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${planned.from.displayName} → ${planned.to.displayName}", style = MaterialTheme.typography.titleMedium,
                    color = LocalOrnament.current.rubric, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (r.byRoad) R.string.route_by_road else R.string.route_straight, kmText(r.km)) + " · " +
                    stringResource(R.string.route_days, kmText(r.km / RoadGraph.KM_PER_DAY)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
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
    outlines: Boolean,
    uiLang: String,
    camera: MapCamera,
    region: Region?,
    journey: Journey?,
    planned: RoadRoute?,
    onTap: (Place?, Offset) -> Unit,
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
    val way = if (dark) Color(0xFF7FC4FF) else Color(0xFF1F5FA8)
    val ink = scheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(camera.width > 0f, fitToMarked, marked) {
        if (camera.width == 0f) return@LaunchedEffect
        val pts = places.filter { it.id in marked }
        if (fitToMarked && pts.isNotEmpty()) {
            val (x, y, s) = camera.framing(pts.minOf { it.lon }.toFloat(), pts.minOf { it.lat }.toFloat(), pts.maxOf { it.lon }.toFloat(), pts.maxOf { it.lat }.toFloat(), 0.6f)
            camera.lon = x; camera.lat = y; camera.ppd = s
        } else if (camera.ppd == 0f) {
            camera.ppd = camera.defaultScale()
        }
    }

    // which places are on the map: the period's, then by weight for the zoom; the marked and the chosen one always
    val dpPerDegree = camera.scale() / density.density
    val limit = maxTier(dpPerDegree)
    val shown = remember(places, period, limit, marked, selected) {
        places.filter { p -> p.id in marked || p == selected || (p.inPeriod(period) && tier(p, period) <= limit) }
    }
    val stopIndex = remember(journey) { journey?.stops?.withIndex()?.groupBy({ it.value }, { it.index + 1 }) ?: emptyMap() }

    Canvas(
        Modifier.fillMaxSize().clipToBounds().testTag("map")
            .onSizeChanged { camera.width = it.width.toFloat(); camera.height = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val before = camera.toLonLat(centroid)
                    camera.ppd = (camera.scale() * zoom).coerceIn(8f, 4000f)
                    camera.lon = before.x - (centroid.x - camera.width / 2) / (KX * camera.ppd)
                    camera.lat = before.y + (centroid.y - camera.height / 2) / camera.ppd
                    camera.lon -= pan.x / (KX * camera.ppd)
                    camera.lat += pan.y / camera.ppd
                }
            }
            .pointerInput(shown) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        // zoom in where tapped
                        val ll = camera.toLonLat(at)
                        camera.ppd = (camera.scale() * 2f).coerceIn(8f, 4000f)
                        camera.lon = ll.x - (at.x - camera.width / 2) / (KX * camera.ppd)
                        camera.lat = ll.y + (at.y - camera.height / 2) / camera.ppd
                    },
                ) { tap ->
                    val radius = 28.dp.toPx()
                    val hit = shown.minByOrNull { p -> camera.toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } }
                    onTap(hit?.takeIf { p -> camera.toScreen(p.lon.toFloat(), p.lat.toFloat()).let { hypot(it.x - tap.x, it.y - tap.y) } <= radius }, camera.toLonLat(tap))
                }
            },
    ) {
        if (camera.width == 0f) return@Canvas
        val dp = density.density
        val w = size.width
        val h = size.height
        fun toScreen(x: Float, y: Float) = camera.toScreen(x, y)
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
                if (q.x > -200 && q.y > -200 && q.x < w + 200 && q.y < h + 200) return true
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

        // outlines of the regions on the map (layer), faint
        if (outlines) {
            val ids = shown.filter { it.type == "region" }.map { it.id }.toSet()
            for ((id, r) in map.regions) {
                if (id !in ids || r === region) continue
                val ring = r.rings[r.rings.size / 2]
                if (!onScreen(ring)) continue
                drawPath(path(ring, true), ink.copy(alpha = 0.35f), style = Stroke(1f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * dp, 3 * dp))))
            }
        }
        // the chosen region: nested bands of likelihood (darker where surely inside), or its boundary
        if (region != null) {
            if (region.bands) {
                val step = 0.40f / region.rings.size
                for (ring in region.rings) drawPath(path(ring, true), ornament.rubric.copy(alpha = step))
                drawPath(path(region.rings.first(), true), ornament.rubric.copy(alpha = 0.45f),
                    style = Stroke(1f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * dp, 4 * dp))))
                drawPath(path(region.rings[region.rings.size / 2], true), ornament.rubric.copy(alpha = 0.8f), style = Stroke(1.6f * dp))
            } else {
                for (ring in region.rings) {
                    val pa = path(ring, true)
                    drawPath(pa, ornament.rubric.copy(alpha = 0.16f))
                    drawPath(pa, ornament.rubric.copy(alpha = 0.85f), style = Stroke(1.8f * dp))
                }
            }
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

        // a journey of the Bible or a planned way: a cased line over everything but the names
        fun wayLine(p: FloatArray, straight: Boolean) {
            val pa = path(p, false)
            drawPath(pa, Color.White.copy(alpha = if (dark) 0.25f else 0.85f), style = Stroke(6.5f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(pa, way, style = Stroke(3.5f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = if (straight) PathEffect.dashPathEffect(floatArrayOf(9 * dp, 6 * dp)) else null))
        }
        if (journey != null) wayLine(journey.p, true)
        if (planned != null) wayLine(planned.points, !planned.byRoad)

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
            o.x > -60 && o.y > -60 && o.x < w + 60 && o.y < h + 60
        }
        for (p in visible.sortedBy { if (important(it)) 1 else 0 }) {
            if (p.type == "region" && !important(p)) continue
            val o = toScreen(p.lon.toFloat(), p.lat.toFloat())
            val stops = stopIndex[p.id]
            if (stops != null) {
                // a numbered stop of the journey
                val label = measurer.measure(stops.joinToString(","), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White))
                val rad = max(8f * dp, label.size.width / 2f + 3 * dp)
                drawCircle(way, rad, o)
                drawCircle(Color.White, rad, o, style = Stroke(1.5f * dp))
                drawText(label, topLeft = Offset(o.x - label.size.width / 2f, o.y - label.size.height / 2f))
                occupied += Rect(o.x - rad, o.y - rad, o.x + rad, o.y + rad)
            } else if (important(p)) {
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
            val gap = if (stopIndex[p.id] != null) 10 * dp else big + 3
            var rect = if (region && !imp) Rect(o.x - label.size.width / 2f, o.y - label.size.height / 2f, o.x + label.size.width / 2f, o.y + label.size.height / 2f)
            else Rect(o.x + gap, o.y - label.size.height / 2f, o.x + gap + label.size.width, o.y + label.size.height / 2f)
            if (imp) {
                var tries = 0
                while (occupied.any { it.overlaps(rect) } && tries++ < 6) rect = rect.translate(0f, label.size.height.toFloat())
            } else if (occupied.any { it.overlaps(rect) }) continue
            occupied += rect
            if (imp) drawRect(Color.White.copy(alpha = if (dark) 0.1f else 0.55f), rect.topLeft, rect.size)
            drawText(label, topLeft = Offset(rect.left, rect.top))
        }
        // route names last, where there is still room
        if (roads && (period == null || !period.romanRoads)) {
            val nameIndex = when (uiLang) { "fr" -> 1; "mg" -> 2; else -> 0 }
            for (rt in map.routes) {
                // beside a stop of the route where nothing else is written, trying the middle first
                val t = measurer.measure(rt.names.getOrElse(nameIndex) { rt.names[0] },
                    TextStyle(fontSize = 11.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, color = routeColour))
                val n = rt.p.size / 2
                val order = (0 until n).sortedBy { kotlin.math.abs(it - n / 2) }
                for (k in order) {
                    val at = toScreen(rt.p[2 * k], rt.p[2 * k + 1])
                    if (at.x < 0 || at.y < 0 || at.x > w - t.size.width || at.y > h) continue
                    val rect = Rect(at.x + 6 * dp, at.y - t.size.height / 2f, at.x + 6 * dp + t.size.width, at.y + t.size.height / 2f)
                    if (occupied.none { it.overlaps(rect) }) { occupied += rect; drawText(t, topLeft = rect.topLeft); break }
                }
            }
        }
    }
}
