package mg.baiboly.app.ui

import java.util.PriorityQueue
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance in kilometres. */
fun kmBetween(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
    val r = Math.PI / 180
    val dLat = (lat2 - lat1) * r
    val dLon = (lon2 - lon1) * r
    val h = sin(dLat / 2).let { it * it } + cos(lat1 * r) * cos(lat2 * r) * sin(dLon / 2).let { it * it }
    return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Length in km of a line of lon, lat pairs. */
fun lineKm(p: FloatArray): Double {
    var d = 0.0
    var i = 2
    while (i + 1 < p.size) {
        d += kmBetween(p[i - 2].toDouble(), p[i - 1].toDouble(), p[i].toDouble(), p[i + 1].toDouble())
        i += 2
    }
    return d
}

/** A way from one place to another: lon, lat pairs; [byRoad] false when it is a straight line. */
class RoadRoute(val points: FloatArray, val km: Double, val byRoad: Boolean)

/**
 * The ancient roads as a graph (Roman roads of the AWMC and the Old Testament
 * highways), for the shortest way between two places. Lines that end close to
 * another road (within [JOIN_KM]) are joined to it. Leaving or reaching the
 * road network costs [OFF_ROAD] times the straight distance; when no road way
 * is shorter than the straight line so weighted (across the sea, or far from
 * any road), the answer is the straight line.
 */
class RoadGraph(lines: List<FloatArray>) {
    private val lon = ArrayList<Float>()
    private val lat = ArrayList<Float>()
    private val adj = ArrayList<MutableList<Pair<Int, Double>>>()
    private val index = HashMap<Long, Int>()

    val size get() = lon.size

    private fun node(x: Float, y: Float): Int {
        val key = ((x * 1000).roundToLong() shl 32) or ((y * 1000).roundToLong() and 0xffffffffL)
        return index.getOrPut(key) {
            lon.add(x); lat.add(y); adj.add(mutableListOf()); lon.size - 1
        }
    }

    private fun km(a: Int, b: Int) = kmBetween(lon[a].toDouble(), lat[a].toDouble(), lon[b].toDouble(), lat[b].toDouble())

    private fun edge(a: Int, b: Int, w: Double) {
        if (a == b || adj[a].any { it.first == b }) return
        adj[a] += b to w
        adj[b] += a to w
    }

    init {
        val ends = LinkedHashSet<Int>()
        for (p in lines) {
            if (p.size < 4) continue
            var prev = -1
            var i = 0
            while (i + 1 < p.size) {
                val n = node(p[i], p[i + 1])
                if (prev >= 0) edge(prev, n, km(prev, n))
                prev = n
                i += 2
            }
            ends += node(p[0], p[1])
            ends += node(p[p.size - 2], p[p.size - 1])
        }
        // join each loose end to the nearest point of another road nearby
        val grid = HashMap<Long, MutableList<Int>>()
        fun cell(x: Float, y: Float) = (floor(x * 10.0).toLong() shl 32) or (floor(y * 10.0).toLong() and 0xffffffffL)
        for (i in 0 until size) grid.getOrPut(cell(lon[i], lat[i])) { mutableListOf() } += i
        for (e in ends) {
            val cx = floor(lon[e] * 10.0).toLong()
            val cy = floor(lat[e] * 10.0).toLong()
            var best = -1
            var bestD = JOIN_KM
            for (dx in -1L..1L) for (dy in -1L..1L) {
                for (j in grid[((cx + dx) shl 32) or ((cy + dy) and 0xffffffffL)].orEmpty()) {
                    if (j == e || adj[e].any { it.first == j }) continue
                    val d = km(e, j)
                    if (d < bestD) { bestD = d; best = j }
                }
            }
            if (best >= 0) edge(e, best, bestD)
        }
    }

    private fun nearest(x: Double, y: Double, k: Int): List<Int> =
        (0 until size).sortedBy { kmBetween(x, y, lon[it].toDouble(), lat[it].toDouble()) }.take(k)

    /** The shortest way from (aLon, aLat) to (bLon, bLat). */
    fun route(aLon: Double, aLat: Double, bLon: Double, bLat: Double): RoadRoute {
        val direct = kmBetween(aLon, aLat, bLon, bLat)
        val straight = RoadRoute(floatArrayOf(aLon.toFloat(), aLat.toFloat(), bLon.toFloat(), bLat.toFloat()), direct, false)
        if (size == 0 || direct < 0.5) return straight
        val dist = DoubleArray(size) { Double.MAX_VALUE }
        val prev = IntArray(size) { -2 } // -1: reached from the start point
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        for (n in nearest(aLon, aLat, 6)) {
            val d = OFF_ROAD * kmBetween(aLon, aLat, lon[n].toDouble(), lat[n].toDouble())
            if (d < dist[n]) { dist[n] = d; prev[n] = -1; queue += d to n }
        }
        val exits = nearest(bLon, bLat, 6).associateWith { OFF_ROAD * kmBetween(bLon, bLat, lon[it].toDouble(), lat[it].toDouble()) }
        var bestCost = OFF_ROAD * direct
        var bestEnd = -1
        while (queue.isNotEmpty()) {
            val (d, u) = queue.poll()!!
            if (d > dist[u] || d >= bestCost) continue
            exits[u]?.let { if (d + it < bestCost) { bestCost = d + it; bestEnd = u } }
            for ((v, w) in adj[u]) {
                val nd = d + w
                if (nd < dist[v]) { dist[v] = nd; prev[v] = u; queue += nd to v }
            }
        }
        if (bestEnd < 0) return straight
        val path = ArrayList<Int>()
        var u = bestEnd
        while (u >= 0) { path += u; u = prev[u] }
        path.reverse()
        val pts = FloatArray((path.size + 2) * 2)
        pts[0] = aLon.toFloat(); pts[1] = aLat.toFloat()
        path.forEachIndexed { i, n -> pts[2 * i + 2] = lon[n]; pts[2 * i + 3] = lat[n] }
        pts[pts.size - 2] = bLon.toFloat(); pts[pts.size - 1] = bLat.toFloat()
        return RoadRoute(pts, lineKm(pts), true)
    }

    companion object {
        const val JOIN_KM = 6.0
        const val OFF_ROAD = 1.3
        /** A day's walk assumed for travel times. */
        const val KM_PER_DAY = 30.0
    }
}
