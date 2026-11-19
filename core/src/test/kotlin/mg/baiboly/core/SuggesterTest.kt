package mg.baiboly.core

import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.core.suggest.Suggester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggesterTest {
    private val s = Suggester()
    private val recent = listOf(Passage(49, 2, 8, 9), Passage(43, 3, 16, 16))

    @Test fun recentFirstOnEmptyInput() {
        val out = s.suggest("", recent)
        assertEquals("Efe 2: 8-9", out[0].label)
        assertTrue(out[0].isRecent)
        assertEquals("Jao 3: 16", out[1].label)
    }

    @Test fun stagedBookChapterVerse() {
        val books = s.suggest("Ja", recent)
        assertTrue(books.any { it.newInput == "Jao " }, books.toString())
        val chapters = s.suggest("Jao ", recent)
        assertEquals("Jao 3: 16", chapters[0].label) // recent first
        assertTrue(chapters.any { it.newInput == "Jao 3: " })
        val verses = s.suggest("Jao 3: ", recent)
        assertTrue(verses.any { it.newInput == "Jao 3: 16" })
        val verses2 = s.suggest("Efe 2: 4, ", recent)
        assertTrue(verses2.any { it.newInput == "Efe 2: 4, 8" }, verses2.take(5).toString())
    }

    /** Brief task 6: a verse in four taps (book, chapter chip, verse chip, Add). */
    @Test fun fourTaps() {
        var input = ""
        input = s.suggest(input, emptyList()).first { it.label == "Jao" }.newInput          // tap 1
        input = s.suggest(input, emptyList()).first { it.label == "3" }.newInput            // tap 2
        input = s.suggest(input, emptyList()).first { it.label == "16" }.newInput           // tap 3
        val r = ReferenceParser.parse(input)                                                 // tap 4: Add
        assertEquals(listOf(Passage(43, 3, 16, 16)), r.passages)
        assertTrue(r.isValid)
    }

    @Test fun fastEnough() {
        val t0 = System.nanoTime()
        repeat(1000) { s.suggest("Sal 119: 1", recent) }
        val perCallMs = (System.nanoTime() - t0) / 1e6 / 1000
        assertTrue(perCallMs < 5.0, "suggest took $perCallMs ms")
    }
}
