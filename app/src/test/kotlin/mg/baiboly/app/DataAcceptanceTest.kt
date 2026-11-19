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
        assertEquals(66, Canon.books.size)
        val jn = bible.verses("mg1865", 43, 3, 16, 16).single()
        assertTrue(jn.text.startsWith("Fa toy izao no nitiavan’ Andriamanitra izao tontolo izao"), jn.text)
        val ps1 = bible.chapter("mg1865", 19, 1)
        assertEquals(6, ps1.verses.size)
        assertTrue(ps1.verses[0].text.startsWith("Sambatra ny olona"))
        // every translation answers John 3:16
        for (t in bible.translations) {
            assertTrue(bible.verses(t.id, 43, 3, 16, 16).single().text.isNotBlank(), t.id)
        }
        assertEquals(8, bible.translations.size)
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
}
