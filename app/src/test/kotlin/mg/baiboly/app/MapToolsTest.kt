package mg.baiboly.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.app.ui.BibleUnits
import mg.baiboly.app.ui.RoadGraph
import mg.baiboly.app.ui.UnitKind
import mg.baiboly.app.ui.formatAmount
import mg.baiboly.app.ui.kmBetween
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.LibraryRepository
import mg.baiboly.data.StudyRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Map data (regions, journeys, road routing) and the units converter, on the real bundled data. */
@RunWith(AndroidJUnit4::class)
class MapToolsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dbs = AssetDatabases(ctx)
    private val lib = LibraryRepository(dbs, StudyRepository(dbs))
    private val map = JSONObject(ctx.assets.open("map.json").use { it.readBytes().decodeToString() })

    private fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
    private fun place(name: String) = lib.places.first { it.name == name }

    private val graph by lazy {
        val lines = mutableListOf<FloatArray>()
        map.getJSONArray("roads").let { a -> for (i in 0 until a.length()) lines += floats(a.getJSONObject(i).getJSONArray("p")) }
        map.getJSONArray("routes").let { a -> for (i in 0 until a.length()) lines += floats(a.getJSONObject(i).getJSONArray("p")) }
        RoadGraph(lines)
    }

    @Test fun regionsHaveOutlines() {
        val regions = map.getJSONObject("regions")
        assertTrue(regions.length() > 100, "region outlines: ${regions.length()}")
        // kingdoms, territories and provinces of both Testaments
        for (name in listOf("Edom", "Moab 1", "Philistia", "Galilee 1", "Samaria 2", "Judea 1", "Decapolis", "Macedonia", "Galatia", "Asia", "Egypt", "Assyria")) {
            val id = place(name).id
            assertTrue(regions.has(id), "outline of $name")
            val rings = regions.getJSONObject(id).getJSONArray("r")
            val outer = floats(rings.getJSONArray(0))
            assertTrue(outer.size >= 8, "$name has a polygon")
            // the region's point lies within its outer outline's box
            val p = place(name)
            val xs = outer.filterIndexed { i, _ -> i % 2 == 0 }
            val ys = outer.filterIndexed { i, _ -> i % 2 == 1 }
            assertTrue(p.lon in xs.min() - 0.5..xs.max() + 0.5.toDouble() && p.lat in ys.min() - 0.5..ys.max() + 0.5.toDouble(), name)
        }
    }

    @Test fun journeysFollowTheirStops() {
        val journeys = map.getJSONArray("journeys")
        assertTrue(journeys.length() >= 15)
        val ids = lib.places.associateBy { it.id }
        for (i in 0 until journeys.length()) {
            val j = journeys.getJSONObject(i)
            val stops = j.getJSONArray("stops")
            val p = floats(j.getJSONArray("p"))
            assertEquals(stops.length() * 2, p.size, j.getString("id"))
            for (k in 0 until stops.length()) {
                val place = ids[stops.getString(k)]
                assertTrue(place != null, "stop ${stops.getString(k)} of ${j.getString("id")} is a known place")
                assertTrue(abs(place.lon - p[2 * k]) < 0.01 && abs(place.lat - p[2 * k + 1]) < 0.01)
            }
            val ref = j.getJSONArray("ref")
            assertEquals(1, BibleRepository(dbs).verses("mg1865", ref.getInt(0), ref.getInt(1), ref.getInt(2), ref.getInt(2)).size, "start verse of ${j.getString("id")}")
        }
        // Paul's first journey starts and ends at Antioch of Syria, by Cyprus (Acts 13–14)
        val paul1 = (0 until journeys.length()).map { journeys.getJSONObject(it) }.first { it.getString("id") == "paul1" }
        val stops = paul1.getJSONArray("stops").let { s -> List(s.length()) { ids[s.getString(it)]!!.name } }
        assertEquals("Antioch 1", stops.first()); assertEquals("Antioch 1", stops.last())
        assertTrue("Salamis" in stops && "Paphos" in stops && "Lystra" in stops)
    }

    @Test fun routesFollowTheRoads() {
        val jer = place("Jerusalem"); val dam = place("Damascus")
        val r = graph.route(jer.lon, jer.lat, dam.lon, dam.lat)
        val direct = kmBetween(jer.lon, jer.lat, dam.lon, dam.lat)
        assertTrue(r.byRoad, "Jerusalem to Damascus by road")
        assertTrue(r.km >= direct && r.km < direct * 1.4, "${r.km} km by road, $direct km direct")
        assertTrue(r.points.size > 4, "a road has bends")
        // Ephesus to Rome: no land road joins them on the map; a straight line, by sea
        val eph = lib.places.first { it.displayName == "Ephesus" }; val rome = place("Rome")
        val sea = graph.route(eph.lon, eph.lat, rome.lon, rome.lat)
        assertFalse(sea.byRoad)
        assertEquals(kmBetween(eph.lon, eph.lat, rome.lon, rome.lat), sea.km, 0.01)
        // Nazareth to Jerusalem: about 100-150 km, 3 to 5 days on foot
        val naz = place("Nazareth")
        val n = graph.route(naz.lon, naz.lat, jer.lon, jer.lat)
        assertTrue(n.km in 100.0..160.0, "${n.km}")
        assertTrue(n.km / RoadGraph.KM_PER_DAY in 3.0..5.5)
    }

    @Test fun unitsFollowTheText() {
        fun u(id: String) = BibleUnits.byId(id)
        fun conv(a: Double, from: String, to: String) = BibleUnits.convert(a, u(from), u(to))
        assertEquals(20.0, conv(1.0, "shekel", "gerah"), 1e-9)       // Ex 30:13
        assertEquals(0.5, conv(1.0, "beka", "shekel"), 1e-9)         // Ex 38:26
        assertEquals(3000.0, conv(1.0, "talent", "shekel"), 1e-9)    // Ex 38:25-26: 603,550 half-shekels = 100 talents + 1,775 shekels
        assertEquals(603_550 / 2.0, 100 * 3000 + 1775.0)
        assertEquals(10.0, conv(1.0, "ephah", "omer"), 1e-9)         // Ex 16:36
        assertEquals(10.0, conv(1.0, "homer", "ephah"), 1e-9)        // Ezek 45:11
        assertEquals(10.0, conv(1.0, "cor", "bath"), 1e-9)           // Ezek 45:14
        assertEquals(u("ephah").value, u("bath").value, 1e-9)        // Ezek 45:11
        assertEquals(6.0, conv(1.0, "reed", "long_cubit"), 1e-9)     // Ezek 40:5
        assertEquals(2.0, conv(1.0, "quadrans", "lepton"), 1e-9)     // Mk 12:42
        // Noah's ark: 300 cubits ≈ 133 m (Gen 6:15)
        assertEquals(133.2, conv(300.0, "cubit", "m"), 0.1)
        // every kind but time has units, every unit of the Bible has a verse that exists
        val bible = BibleRepository(dbs)
        for (k in UnitKind.entries.filter { it != UnitKind.Time }) assertTrue(BibleUnits.of(k).any { !it.modern }, k.name)
        for (unit in BibleUnits.all.filter { !it.modern }) {
            val r = unit.ref!!
            assertEquals(1, bible.verses("mg1865", r.book, r.chapter, r.verseStart, r.verseStart).size, "${unit.id} ${r.shortLabel()}")
        }
        // the Malagasy names are the 1865 Bible's own words at the verse cited
        for (id in listOf("cubit", "gerah", "shekel", "beka", "talent", "omer", "ephah", "bath", "homer", "hin", "log", "denarius", "stadion", "mile")) {
            val unit = u(id)
            val text = bible.verses("mg1865", unit.ref!!.book, unit.ref!!.chapter, unit.ref!!.verseStart, unit.ref!!.verseStart).single().text.lowercase()
            assertTrue(text.contains(unit.mg.substringBefore(" ").lowercase()), "$id: '${unit.mg}' in ${unit.ref!!.shortLabel()}: $text")
        }
    }

    @Test fun amountsReadWell() {
        assertEquals("133", formatAmount(133.2, Locale.ENGLISH))
        assertEquals("0.444", formatAmount(0.444, Locale.ENGLISH))
        assertEquals("34,200", formatAmount(34_200.0, Locale.ENGLISH))
        assertEquals("1,5", formatAmount(1.5, Locale.FRENCH))
    }

    @Test fun unitsNamedInAPassage() {
        val bible = BibleRepository(dbs)
        fun texts(mg: Triple<Int, Int, Int>, kjv: Triple<Int, Int, Int> = mg): Map<String, String> = mapOf(
            "mg" to bible.verses("mg1865", mg.first, mg.second, mg.third, mg.third).single().text,
            "fr" to bible.verses("lsg1910", kjv.first, kjv.second, kjv.third, kjv.third).single().text,
            "en" to bible.verses("kjv", kjv.first, kjv.second, kjv.third, kjv.third).single().text,
        )
        assertEquals(listOf("cubit"), BibleUnits.namedIn(texts(Triple(1, 6, 15)), 1).map { it.id })          // the ark
        assertTrue("talent_nt" in BibleUnits.namedIn(texts(Triple(40, 18, 24)), 40).map { it.id })            // money in the Gospel
        assertFalse("talent" in BibleUnits.namedIn(texts(Triple(40, 18, 24)), 40).map { it.id })
        assertTrue("shekel" in BibleUnits.namedIn(texts(Triple(2, 30, 13)), 2).map { it.id } && "gerah" in BibleUnits.namedIn(texts(Triple(2, 30, 13)), 2).map { it.id })
        assertTrue(BibleUnits.namedIn(texts(Triple(43, 3, 16)), 43).isEmpty(), "no unit in John 3:16")
    }

    @Test fun placesAndEntriesOfAPassage() {
        // Acts 13:4: Seleucia and Cyprus
        val names = lib.placesInVerses(44, 13, 4, 4).map { it.displayName }
        assertTrue("Seleucia" in names && "Cyprus" in names, names.toString())
        // dictionary titles named by words of a verse (KJV Gen 1:1)
        val keys = lib.entriesNamed("easton", listOf("beginning", "God", "created", "heaven", "earth")).map { it.uppercase() }
        assertTrue("GOD" in keys && "EARTH" in keys, keys.toString())
    }
}
