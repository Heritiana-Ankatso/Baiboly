package mg.baiboly.app.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.data.BibleEvent
import mg.baiboly.data.Era
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The timeline as a chart: time runs down a true scale (years BC to AD), with
 *  - the eras as bands behind it, their names pinned at the top while they last;
 *  - spans of a year or more (lifetimes, reigns, judgeships, other periods) as thin bars in lanes, so
 *    overlaps show (Shem outlived Abraham; Israel and Judah reign side by side);
 *  - the other events as dots on the axis;
 *  - labels placed by importance (spans first, then the events told in most verses): a label that
 *    would collide is left out, and zooming in brings it back, as the map does with place names;
 *  - the events of the chapter being read picked out, one tap away.
 * Pinch or double-tap to zoom, drag to scroll; a tap opens the event. Colours: the four first slots of
 * the validated categorical palette (dataviz skill, light and dark), with a legend and a label in ink.
 */

/** Kinds of span, in legend order; their colours in light and dark. */
private enum class SpanKind(val key: String, val label: Int, val light: Color, val dark: Color) {
    Life("life", R.string.tl_lives, Color(0xFF2A78D6), Color(0xFF3987E5)),
    Reign("reign", R.string.tl_reigns, Color(0xFFEB6834), Color(0xFFD95926)),
    Judge("judge", R.string.tl_judges, Color(0xFF1BAF7A), Color(0xFF199E70)),
    Period("period", R.string.tl_periods, Color(0xFFEDA100), Color(0xFFC98500)),
}

@Composable
internal fun eraName(key: String): String = stringResource(
    when (key) {
        "creation" -> R.string.era_creation
        "flood" -> R.string.era_flood
        "patriarchs" -> R.string.period_patriarchs
        "exodus" -> R.string.period_exodus
        "conquest" -> R.string.period_conquest
        "kingdom" -> R.string.period_kingdom
        "divided" -> R.string.period_divided
        "exile" -> R.string.era_exile
        "return" -> R.string.era_return
        "between" -> R.string.period_between
        "jesus" -> R.string.period_jesus
        else -> R.string.period_church
    },
)

/** A span placed in its lane. */
private class Span(val event: BibleEvent, val kind: SpanKind, val lane: Int)

/** A label that may be drawn, in the order of importance (ranked once, not on every frame). */
private class Candidate(val event: BibleEvent, val span: Boolean, val chapter: Boolean, val score: Double)

/**
 * One pool of lanes for every kind (colour tells the kind): a span goes to the first lane free at its
 * start. Ten lanes hold all the spans, where a pool per kind needed 22 and left most of them empty.
 */
private fun lanes(events: List<BibleEvent>): Pair<List<Span>, Int> {
    val out = mutableListOf<Span>()
    val ends = mutableListOf<Double>()
    for (e in events.filter { it.kind != "point" }.sortedWith(compareBy({ it.year }, { -it.endYear }))) {
        val k = SpanKind.entries.firstOrNull { it.key == e.kind } ?: SpanKind.Period
        var lane = ends.indexOfFirst { it <= e.year }
        if (lane < 0) { ends += e.endYear; lane = ends.lastIndex } else ends[lane] = e.endYear
        out += Span(e, k, lane)
    }
    return out to ends.size
}

private val STEPS = intArrayOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TimelineChart(vm: AppViewModel, events: List<BibleEvent>, eras: List<Era>, modifier: Modifier = Modifier, extraNames: Map<String, String> = emptyMap()) {
    if (events.isEmpty() || eras.isEmpty()) return
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.5f
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val band = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.06f else 0.035f)
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val accent = LocalOrnament.current.rubric
    val measurer = rememberTextMeasurer()
    val first = eras.first().start.toDouble()
    val last = eras.last().end.toDouble()
    val (spans, laneCount) = remember(events) { lanes(events) }
    // events of the chapter being read
    var chapterIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(vm.location.book, vm.location.chapter) {
        chapterIds = vm.onIo { vm.chronicle.eventsInVerses(vm.location.book, vm.location.chapter, 1, 999).map { it.id }.toSet() }
    }
    var heightPx by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pxPerYear by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableDoubleStateOf(first) }
    fun minScale() = if (heightPx > 0) (heightPx / (last - first)).toFloat() else 0.1f
    fun clampTop() { top = top.coerceIn(first - 20 / max(pxPerYear, 0.01f), max(first, last - heightPx / pxPerYear + 20 / pxPerYear)) }
    fun fit(a: Double, b: Double) {
        if (heightPx <= 0) return
        pxPerYear = (heightPx * 0.92 / max(b - a, 1.0)).toFloat().coerceIn(minScale(), 400f)
        top = a - (heightPx * 0.04) / pxPerYear
        clampTop()
    }
    // a new range of years (church history switched on or off) is framed whole
    LaunchedEffect(first, last) { if (heightPx > 0) fit(first, last) }
    val lifeEra = remember(chapterIds) { events.filter { it.id in chapterIds } }
    val points = remember(events) { events.filter { it.kind == "point" } }
    // one ranking: the chapter's events first, then by the verses that tell them; a span gains a little for its length
    val ranked = remember(spans, points, chapterIds) {
        buildList {
            spans.forEach { s -> add(Candidate(s.event, true, s.event.id in chapterIds, s.event.weight + min(20.0, (s.event.endYear - s.event.year) * 0.2))) }
            points.forEach { e -> add(Candidate(e, false, e.id in chapterIds, e.weight.toDouble())) }
        }.sortedWith(compareBy({ !it.chapter }, { -it.score }))
    }
    // Text is laid out once and kept: laying out every label again on each frame made scrolling stutter.
    val textCache = remember(measurer, ink, accent, muted, density) { HashMap<String, TextLayoutResult>() }
    // momentum after a drag (fling), stopped by the next touch
    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }
    var swallowTap by remember { mutableStateOf(false) }

    // Church history outside the Bible shown too: the two never mix. Events the Bible tells stay on the right
    // of the axis; the others go to its left (their labels ending at the axis), and the axis moves to the middle.
    val outside = remember(events) { events.any { it.id < 0 } }
    fun isOutside(e: BibleEvent) = outside && e.id < 0

    // layout: year gutter | span lanes | [outside the Bible] axis | labels of the Bible's events
    val gutter = with(density) { 56.dp.toPx() }
    val laneW = with(density) { 6.dp.toPx() }
    val laneGap = with(density) { 2.dp.toPx() }
    val lanesX = gutter + with(density) { 6.dp.toPx() }
    val lanesEnd = lanesX + laneCount * (laneW + laneGap) + with(density) { 6.dp.toPx() }
    val axisX = if (outside && widthPx > 0f) lanesEnd + (widthPx - lanesEnd) / 2 else lanesEnd
    val labelX = axisX + with(density) { 10.dp.toPx() }
    val leftEnd = axisX - with(density) { 10.dp.toPx() }
    val leftStart = lanesEnd + with(density) { 2.dp.toPx() }
    val rowH = with(density) { 18.dp.toPx() }

    fun yOf(year: Double) = ((year - top) * pxPerYear).toFloat()

    /** Era names that fit, pinned at the top of their band (or of the view): (era, y). */
    fun placedEras(): List<Pair<Era, Float>> {
        val out = mutableListOf<Pair<Era, Float>>()
        for (era in eras) {
            val y0 = yOf(era.start.toDouble()); val y1 = yOf(era.end.toDouble())
            if (y1 < 0 || y0 > heightPx) continue
            val y = min(max(y0, 0f) + rowH / 2 + 2f, y1 - rowH / 2)
            // a short era whose name would cover the next one waits for the zoom
            if (out.none { abs(it.second - y) < rowH }) out += era to y
        }
        return out
    }

    /** An era of church history after the apostles: its name goes in the left column with the events outside the Bible. */
    fun eraOnLeft(era: Era) = outside && era.key in extraNames

    /**
     * Labels that fit, most important first: (event, y, is span). A span's label is pinned to the top of the view while
     * it lasts. Each column (the Bible's on the right, outside the Bible on the left) places its own labels.
     */
    fun placedLabels(eraRows: List<Pair<Era, Float>> = placedEras()): List<Triple<BibleEvent, Float, Boolean>> {
        val right = ArrayList<ClosedFloatingPointRange<Float>>(64)
        val left = ArrayList<ClosedFloatingPointRange<Float>>(64)
        eraRows.forEach { (era, y) -> (if (eraOnLeft(era)) left else right) += (y - rowH / 2)..(y + rowH / 2) }
        val out = mutableListOf<Triple<BibleEvent, Float, Boolean>>()
        val room = (heightPx / rowH).toInt() + 1 // no more labels than lines on the screen
        for (c in ranked) {
            if (right.size >= room && (!outside || left.size >= room)) break
            val taken = if (isOutside(c.event)) left else right
            if (taken.size >= room) continue
            val y = if (c.span) {
                val y0 = yOf(c.event.year.toDouble()); val y1 = yOf(c.event.endYear)
                if (y1 <= 0 || y0 >= heightPx) continue
                max(y0, 0f)
            } else {
                val y = yOf(c.event.year + 0.5)
                if (y < 0f || y > heightPx) continue
                y
            }
            val lo = y - rowH / 2; val hi = y + rowH / 2
            if (taken.none { it.start < hi && lo < it.endInclusive }) {
                taken += lo..hi; out += Triple(c.event, y, c.span)
            }
        }
        return out
    }

    val legendStyle = MaterialTheme.typography.labelMedium
    Column(modifier) {
        // eras: one tap frames the era; "Whole Bible" frames everything; "This chapter" goes to the events being read
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lifeEra.isNotEmpty()) item {
                FilterChip(selected = false, onClick = {
                    val a = lifeEra.minOf { it.year }.toDouble(); val b = lifeEra.maxOf { it.endYear }
                    fit(a - max(5.0, (b - a) * 0.6), b + max(5.0, (b - a) * 0.6))
                }, label = { Text(stringResource(R.string.tl_this_chapter)) }, modifier = Modifier.testTag("tl-chapter"))
            }
            item {
                FilterChip(selected = false, onClick = { fit(first, last) }, label = { Text(stringResource(R.string.tl_whole)) },
                    modifier = Modifier.testTag("tl-whole"))
            }
            items(eras, key = { it.key }) { era ->
                FilterChip(selected = false, onClick = { fit(era.start.toDouble(), era.end.toDouble()) },
                    label = { Text(extraNames[era.key] ?: eraName(era.key)) }, modifier = Modifier.testTag("tl-era:${era.key}"))
            }
        }
        // legend: identity never by colour alone
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpanKind.entries.forEach { k ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(width = 6.dp, height = 12.dp)
                        .background(if (dark) k.dark else k.light, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(k.label), style = legendStyle, color = muted, maxLines = 1)
                }
            }
            // events of church history (negative ids): a hollow ring, never a filled dot like the Bible's own
            if (outside) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.testTag("tl-legend-outside")) {
                Box(Modifier.size(10.dp).border(1.5.dp, muted, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tl_outside_bible), style = legendStyle, color = muted, maxLines = 1)
            }
        }
        // the two columns, named over the chart: outside the Bible on the left of the axis, the Bible on the right
        if (outside) Row(Modifier.fillMaxWidth().padding(top = 2.dp).testTag("tl-columns"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Spacer(Modifier.width(with(density) { lanesEnd.toDp() }))
            Text("← " + stringResource(R.string.tl_outside_bible), Modifier.weight(1f), style = legendStyle, color = muted,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2)
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.tl_in_bible) + " →", Modifier.weight(1f), style = legendStyle, color = ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        val eraNames = eras.associate { it.key to (extraNames[it.key] ?: eraName(it.key)) }
        // year labels as the interface writes them ("1491 BC", "AD 30"), from the same strings
        val bcPattern = stringResource(R.string.year_bc, 999999)
        val adPattern = stringResource(R.string.year_ad, 999999)
        val yearOne = androidx.compose.ui.res.pluralStringResource(R.plurals.work_years, 1, 1)
        val yearsPattern = androidx.compose.ui.res.pluralStringResource(R.plurals.work_years, 999999, 999999)
        Canvas(
            Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("timeline-chart")
                .onSizeChanged {
                    val firstLayout = heightPx == 0f
                    heightPx = it.height.toFloat(); widthPx = it.width.toFloat()
                    if (firstLayout || pxPerYear < minScale()) fit(first, last)
                }
                .pointerInput(Unit) {
                    // drag and pinch as before, plus momentum: after a quick drag the chart glides on and slows
                    // down (the platform's fling curve); the next touch stops it.
                    val decay = splineBasedDecay<Float>(this)
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // set on every touch: this touch only stops the fling if one was running
                        swallowTap = fling?.isActive == true
                        fling?.cancel()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = 0f
                        var dragging = false
                        var pinched = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            if (event.changes.count { it.pressed } > 1) pinched = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (!dragging) {
                                moved += pan.getDistance()
                                if (moved > slop || zoom != 1f) dragging = true
                            }
                            if (dragging && (zoom != 1f || pan != Offset.Zero)) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val anchor = top + centroid.y / pxPerYear
                                pxPerYear = (pxPerYear * zoom).coerceIn(minScale(), 400f)
                                top = anchor - centroid.y / pxPerYear - pan.y / pxPerYear
                                clampTop()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                        }
                        if (dragging && !pinched) {
                            val v = tracker.calculateVelocity().y
                            if (abs(v) > 300f) fling = scope.launch {
                                var last = 0f
                                AnimationState(0f, v).animateDecay(decay) {
                                    val before = top
                                    top -= (value - last) / pxPerYear
                                    last = value
                                    clampTop()
                                    if (top == before) cancelAnimation() // reached either end
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { p ->
                            val anchor = top + p.y / pxPerYear
                            pxPerYear = (pxPerYear * 2.5f).coerceIn(minScale(), 400f)
                            top = anchor - p.y / pxPerYear
                            clampTop()
                        },
                        onTap = { p ->
                            // a tap that stops a fling only stops it, as in a list
                            if (swallowTap) { swallowTap = false; return@detectTapGestures }
                            // a span bar, then a label or dot on the same line
                            val hitSpan = spans.firstOrNull { s ->
                                val sx = lanesX + s.lane * (laneW + laneGap)
                                p.x in (sx - 8f)..(sx + laneW + 8f) && p.y in yOf(s.event.year.toDouble())..max(yOf(s.event.endYear), yOf(s.event.year.toDouble()) + 12f)
                            }
                            // the label column the tap is in: outside the Bible on the left of the axis, the Bible on the right
                            val onLeft = outside && p.x < axisX
                            val hitLabel = placedLabels().filter { isOutside(it.first) == onLeft }.minByOrNull { abs(it.second - p.y) }
                                ?.takeIf { abs(it.second - p.y) < rowH && (if (onLeft) p.x > leftStart - 8f else p.x > axisX - 16f) }
                            (hitSpan?.event ?: hitLabel?.first)?.let { vm.openEvent(it.id) }
                        },
                    )
                },
        ) {
            if (pxPerYear <= 0f) return@Canvas
            val yearTop = top; val yearBottom = top + size.height / pxPerYear
            // era bands, alternating; their names (those that fit) pinned in the label column
            eras.forEachIndexed { i, era ->
                val y0 = yOf(era.start.toDouble()); val y1 = yOf(era.end.toDouble())
                if (y1 < 0 || y0 > size.height) return@forEachIndexed
                if (i % 2 == 0) drawRect(band, Offset(0f, max(0f, y0)), Size(size.width, min(size.height, y1) - max(0f, y0)))
                drawLine(hairline, Offset(0f, y0), Offset(size.width, y0), 1f)
            }
            val eraRows = placedEras()
            val eraW = (size.width - labelX - 8f).toInt().coerceAtLeast(1)
            val eraLeftW = (leftEnd - leftStart).toInt().coerceAtLeast(1)
            for ((era, y) in eraRows) {
                val onLeft = eraOnLeft(era)
                val width = if (onLeft) eraLeftW else eraW
                val name = textCache.getOrPut("E|${era.key}|$width") {
                    measurer.measure(eraNames.getValue(era.key).uppercase(),
                        TextStyle(fontSize = 10.sp, letterSpacing = 1.2.sp, color = accent, fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = width))
                }
                drawText(name, topLeft = Offset(if (onLeft) leftEnd - name.size.width else labelX, y - name.size.height / 2))
            }
            // year ticks: the smallest round step at least 56dp apart
            val minGap = 56.dp.toPx()
            val step = STEPS.firstOrNull { it * pxPerYear >= minGap } ?: 1000
            // ticks on round years as printed: 1500 BC is astronomical -1499, AD 30 is 30
            val ticks = buildList {
                var b = ceil(max(1.0, 1 - yearBottom) / step).toInt() * step
                while (b <= 1 - yearTop) { add(1 - b to bcPattern.replace("999999", "$b")); b += step }
                var a = ceil(max(1.0, yearTop) / step).toInt() * step
                while (a <= yearBottom) { add(a to adPattern.replace("999999", "$a")); a += step }
            }
            for ((t, label) in ticks) {
                val y = yOf(t.toDouble())
                drawLine(hairline, Offset(gutter, y), Offset(axisX, y), 1f)
                val m = textCache.getOrPut("T|$label") { measurer.measure(label, TextStyle(fontSize = 11.sp, color = muted)) }
                drawText(m, topLeft = Offset(gutter - m.size.width - 6f, y - m.size.height / 2))
            }
            // axis
            drawLine(ink.copy(alpha = 0.55f), Offset(axisX, 0f), Offset(axisX, size.height), 2f)
            // spans: 6dp bars with 4dp rounded ends, a 2px surface gap between lanes (the lane gap)
            for (s in spans) {
                val y0 = yOf(s.event.year.toDouble()); val y1 = max(yOf(s.event.endYear), y0 + 3f)
                if (y1 < 0 || y0 > size.height) continue
                val sx = lanesX + s.lane * (laneW + laneGap)
                val c = if (dark) s.kind.dark else s.kind.light
                val strong = s.event.id in chapterIds
                val corner = CornerRadius(min(4.dp.toPx(), laneW / 2))
                // a span outside the Bible (a Father's life, a council's years) is an outline, the Bible's are filled
                if (s.event.id < 0) drawRoundRect(c, Offset(sx + 0.75f, y0), Size(laneW - 1.5f, y1 - y0), corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                else drawRoundRect(if (strong) c else c.copy(alpha = 0.85f), Offset(sx, y0), Size(laneW, y1 - y0), corner)
            }
            // point events: dots on the axis with a surface ring; the chapter's events larger, in the accent
            for (e in points) {
                val y = yOf(e.year + 0.5)
                if (y < -8 || y > size.height + 8) continue
                val strong = e.id in chapterIds
                val r = if (strong) 5.dp.toPx() else 3.dp.toPx()
                drawCircle(surface, r + 2f, Offset(axisX, y))
                if (e.id < 0) drawCircle(muted, r, Offset(axisX, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                else drawCircle(if (strong) accent else ink.copy(alpha = 0.7f), r, Offset(axisX, y))
            }
            // labels in ink, a swatch for spans
            val wRight = (size.width - labelX - 4f).toInt().coerceAtLeast(1)
            val wLeft = (leftEnd - leftStart - 16f).toInt().coerceAtLeast(1)
            for ((e, y, span) in placedLabels(eraRows)) {
                val strong = e.id in chapterIds
                val onLeft = isOutside(e)
                val w = if (onLeft) wLeft else wRight
                val m = textCache.getOrPut("L|${e.id}|$strong|$span|$w") {
                    val years = if (span) yearsOf(e)?.let { " · " + if (it == 1) yearOne else yearsPattern.replace("999999", "$it") } ?: "" else ""
                    // church history outside the Bible: muted and in italics, so it is never read as Scripture's own
                    measurer.measure(e.title + years, TextStyle(fontSize = 12.sp, color = if (strong) accent else if (e.id < 0) muted else ink,
                        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                        fontStyle = if (e.id < 0) androidx.compose.ui.text.font.FontStyle.Italic else null), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = w))
                }
                val k = if (span) SpanKind.entries.first { it.key == e.kind } else null
                if (onLeft) {
                    // left column: the label ends at the axis; a span's swatch (an outline) sits between them
                    var rx = leftEnd
                    if (k != null) {
                        drawRoundRect(if (dark) k.dark else k.light, Offset(leftEnd - 10f, y - 5f), Size(10f, 10f), CornerRadius(3f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                        rx -= 16f
                    } else {
                        drawLine(hairline, Offset(leftEnd + 2f, y), Offset(axisX - 5f, y), 1f)
                    }
                    drawText(m, topLeft = Offset(rx - m.size.width, y - m.size.height / 2))
                } else {
                    var lx = labelX
                    if (k != null) {
                        drawRoundRect(if (dark) k.dark else k.light, Offset(labelX, y - 5f), Size(10f, 10f), CornerRadius(3f))
                        lx += 16f
                    } else {
                        drawLine(hairline, Offset(axisX + 5f, y), Offset(labelX - 2f, y), 1f)
                    }
                    drawText(m, topLeft = Offset(lx, y - m.size.height / 2))
                }
            }
        }
    }
}

/** Whole years of a span ("40Y", "7Y6M" -> 40, 7), or null. */
private fun yearsOf(e: BibleEvent): Int? = Regex("(\\d+)Y").find(e.duration)?.groupValues?.get(1)?.toInt()
