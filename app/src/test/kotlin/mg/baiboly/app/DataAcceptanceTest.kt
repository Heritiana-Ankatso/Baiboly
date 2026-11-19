package mg.baiboly.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.list.ServiceList
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.StudyRepository
import mg.baiboly.data.UserStore
import mg.baiboly.data.VerseRef
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Brief tasks 2, 10, 12 and 13 against the real bundled databases. */
@RunWith(AndroidJUnit4::class)
class DataAcceptanceTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dbs = AssetDatabases(ctx)
    private val bible = BibleRepository(dbs)

    @Test fun task2_textPipeline() {
        assertEquals(73, Canon.books.size) // 66 + 7 deuterocanonical
        val jn = bible.verses("mg1865", 43, 3, 16, 16).single()
        assertTrue(jn.text.startsWith("Fa toy izao no nitiavan’ Andriamanitra izao tontolo izao"), jn.text)
        val ps1 = bible.chapter("mg1865", 19, 1)
        assertEquals(6, ps1.verses.size)
        assertTrue(ps1.verses[0].text.startsWith("Sambatra ny olona"))
        // every translation answers John 3:16
        for (t in bible.translations) {
            assertTrue(bible.verses(t.id, 43, 3, 16, 16).single().text.isNotBlank(), t.id)
        }
        assertEquals(13, bible.translations.size)
        assertTrue(bible.verses("lsv", 43, 3, 16, 16).single().text.contains("God so loved the world"))
        // Catholic Bibles: 73 books, deuterocanonical books readable
        assertEquals(setOf("ncl_fr", "crampon", "webc", "dra"), bible.translations.filter { it.catholic }.map { it.id }.toSet())
        for (id in listOf("ncl_fr", "crampon", "webc", "dra")) {
            assertEquals(73, bible.chapterCounts(id).size, id)
            assertTrue(bible.verses(id, 67, 1, 1, 1).single().text.isNotBlank(), "$id Tobit 1:1")
        }
        assertEquals(66, bible.chapterCounts("kjv").size)
        assertTrue(bible.verses("kjv", 43, 3, 16, 16).single().text.startsWith("For God so loved"), "no ¶ marks")
        assertEquals(listOf("mg1865", "tdx"), bible.translations.filter { it.lang == "mg" }.map { it.id })
        assertTrue(bible.translations.none { it.id == "sbl_fr" })
        assertTrue(bible.verses("ncl_fr", 43, 3, 16, 16).single().text.contains("Dieu a tellement aimé le monde"))
        // Each text keeps its own chapter numbering: Malachi has 4 chapters in the KJV, 3 in Malagasy 1865.
        assertEquals(4, bible.chapterCounts("kjv")[39])
        assertEquals(3, bible.chapterCounts("mg1865")[39])
        assertEquals(6, bible.chapter("kjv", 39, 4).verses.size)
        // Catholic Esther keeps the Greek additions (16 chapters); 66-book Bibles have 10
        assertEquals(16, bible.chapterCounts("ncl_fr")[17])
        assertEquals(10, bible.chapterCounts("lsg1910")[17])
        // no soft hyphens left in the Tandroy text
        assertTrue(bible.verses("tdx", 43, 3, 16, 16).single().text.none { it == '\u00ad' })
        assertEquals(setOf("mg", "fr", "en"), bible.translations.map { it.lang }.toSet())
    }

    @Test fun task10_searchIgnoresAccents() {
        val hits = bible.search("mg1865", "masina")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.text.contains("masìna") }, "masina should find masìna")
        assertTrue(bible.search("lsg1910", "eternel").any { it.text.contains("Éternel") })
    }

    @Test fun task13_studyData() {
        val study = StudyRepository(dbs)
        val v = study.verse(VerseRef(55, 2, 25))
        val ack = v.words.first { it.word == "acknowledging" }
        assertEquals("G1922", ack.strongs)
        assertEquals("ἐπίγνωσις", ack.lemma)
        val e = study.entry("G1922")!!
        assertEquals("epignosis", e.translit)
        assertTrue(e.definition!!.contains("recognition"))
        assertTrue(study.verseCount("G1922") > 10)
        assertTrue(study.versesWith("G1922").contains(VerseRef(55, 2, 25)))
        // Open Scriptures (approved CC-BY-SA): Hebrew script and the Greek gaps
        assertEquals("אֱלֹהִים", study.entry("H430")!!.lemma)
        assertEquals("ἑαυτοῦ", study.entry("G1438")!!.lemma)
        // Psalm 3: Malagasy verse 2 is KJV verse 1 (the title is Malagasy verse 1)
        assertEquals(VerseRef(19, 3, 1), study.verse(VerseRef(19, 3, 2)).kjv)
    }

    @Test fun task12_backupRoundTrip() {
        val a = UserStore(ctx)
        var l = a.createList("Fanompoana", "2026-11-22")
        l = l.add(listOf(Passage(43, 3, 16, 16), Passage(49, 2, 8, 9))).toggleSide(1)
        a.saveList(l)
        a.addNote(Passage(43, 3, 16, 16), "Fitiavana")
        a.addHighlight(Passage(19, 23, 1, 1), 3, 12, 0xFFFFE066.toInt())
        a.addHighlight(Passage(19, 23, 2, 4), null, null, 0xFF9BE59B.toInt())
        a.touchRecent(Passage(45, 5, 8, 8), 1000)
        val json = a.exportJson(JSONObject().put("list_id", l.id))
        val before = json.toString()

        // "fresh install": wipe and restore
        a.close()
        ctx.deleteDatabase("user.db")
        val b = UserStore(ctx)
        assertTrue(b.notes().isEmpty())
        val state = b.importJson(JSONObject(before))
        assertEquals(l.id, state.getLong("list_id"))
        val after = b.exportJson(JSONObject().put("list_id", l.id))
        for (t in UserStore.TABLES) assertEquals(json.getJSONArray(t).toString(), after.getJSONArray(t).toString(), t)
        val restored: ServiceList = b.loadList(l.id)!!
        assertEquals(l.items, restored.items)
        assertEquals(listOf(3 to 12), b.highlights().filter { it.charStart != null }.map { it.charStart to it.charEnd })
    }

    @Test fun library_crossRefs_dictionaries_places() {
        val lib = mg.baiboly.data.LibraryRepository(dbs, StudyRepository(dbs))
        // TSK cross-references for John 3:16
        val x = lib.crossRefs(43, 3, 16)
        assertTrue(x.isNotEmpty() && x.all { it.refs.isNotEmpty() }, x.toString())
        // Psalm numbering: KJV Psalm 3:1 is Malagasy Salamo 3:2 (the title is verse 1 there)
        assertEquals(Passage(19, 3, 2, 2), lib.osisToPassage("Ps.3.1"))
        assertEquals(Passage(19, 23, 1, 1), lib.osisToPassage("Ps.23.1")) // same numbering in Psalm 23
        assertEquals(Passage(1, 1, 1, 3), lib.osisToPassage("Gen.1.1-Gen.1.3"))
        // dictionaries
        assertEquals(setOf("easton", "nave", "martin"), lib.sources.map { it.id }.toSet())
        val jerusalem = lib.entry("easton", "Jerusalem")!!
        val parts = lib.markup(jerusalem.body)
        assertTrue(parts.any { it is mg.baiboly.data.Markup.Ref }, "Easton entries link to verses")
        assertTrue(lib.search("easton", "jerus").first().startsWith("JERUSALEM"))
        assertTrue(lib.search("martin", "bouffi").isNotEmpty(), "French glossary")
        assertTrue(lib.search("nave", "prayer").isNotEmpty())
        // places
        assertTrue(lib.places.size > 1000)
        val exodus14 = lib.placesIn(2, 14).map { it.name }
        assertTrue(exodus14.any { it.contains("Red Sea") || it.contains("Pi-hahiroth") || it.contains("Baal-zephon") }, exodus14.toString())
        val jer = lib.places.first { it.name == "Jerusalem" }
        assertTrue(jer.lat in 31.7..31.8 && jer.lon in 35.2..35.3)
        assertTrue(lib.versesOf(jer.id).size > 500)
    }
}
