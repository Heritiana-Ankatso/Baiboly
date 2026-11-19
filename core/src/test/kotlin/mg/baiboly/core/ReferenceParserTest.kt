package mg.baiboly.core

import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.BookMatch
import mg.baiboly.core.ref.ParseError
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.core.text.TextNorm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceParserTest {
    private fun ok(s: String): List<Passage> {
        val r = ReferenceParser.parse(s)
        assertNull(r.error, "unexpected error for '$s': ${r.error}")
        return r.passages
    }

    // ---- the four examples from the brief -------------------------------

    @Test fun jao316() {
        assertEquals(listOf(Passage(43, 3, 16, 16)), ok("Jao 3: 16"))
        assertEquals(listOf(Passage(43, 3, 16, 16)), ok("Jao 3:16"))
        assertEquals(listOf(Passage(43, 3, 16, 16)), ok("jao3 :16"))
    }

    @Test fun twoTim222() {
        assertEquals(listOf(Passage(55, 2, 22, 22)), ok("2 Tim 2: 22"))
        assertEquals(listOf(Passage(55, 2, 22, 22)), ok("2Tim 2:22"))
        assertEquals(listOf(Passage(54, 2, 1, 1)), ok("1 Tim 2: 1"))
    }

    @Test fun efe2_4_8to9() {
        val r = ReferenceParser.parse("Efe 2: 4, 8-9")
        assertEquals(listOf(Passage(49, 2, 4, 4), Passage(49, 2, 8, 9)), r.passages)
        assertEquals("Efesiana 2: 4, 8-9", r.preview())
    }

    @Test fun salSemicolonCarriesBook() {
        // The brief's example "Sal 1: 3-4; 3: 8-12" is syntactically parsed with the
        // book carried over, but Salamo 3 has only 9 verses in this text, so 3:8-12
        // must be rejected rather than guessed.
        val bad = ReferenceParser.parse("Sal 1: 3-4; 3: 8-12")
        assertEquals(listOf(Passage(19, 1, 3, 4)), bad.passages)
        val e = assertIs<ParseError.VerseOutOfRange>(bad.error)
        assertEquals(19, e.book); assertEquals(3, e.chapter); assertEquals(12, e.verse); assertEquals(9, e.max)
        // Same structure with an in-range second part:
        assertEquals(listOf(Passage(19, 1, 3, 4), Passage(19, 3, 8, 9)), ok("Sal 1: 3-4; 3: 8-9"))
        assertEquals("Salamo 1: 3-4; 3: 8-9", ReferenceParser.parse("Sal 1: 3-4; 3: 8-9").preview())
    }

    // ---- rules ------------------------------------------------------------

    @Test fun wholeChapter() {
        assertEquals(listOf(Passage(19, 23)), ok("Sal 23"))
        assertTrue(ok("Sal 23")[0].isWholeChapter)
    }

    @Test fun reversedRangeRejected() {
        val e = assertIs<ParseError.ReversedRange>(ReferenceParser.parse("Efe 2: 8-2").error)
        assertEquals(8, e.from); assertEquals(2, e.to)
    }

    @Test fun outOfRangeRejected() {
        assertIs<ParseError.ChapterOutOfRange>(ReferenceParser.parse("Jao 22: 1").error)
        assertIs<ParseError.VerseOutOfRange>(ReferenceParser.parse("Jao 3: 37").error)
        assertIs<ParseError.VerseOutOfRange>(ReferenceParser.parse("Jao 3: 0").error)
        assertIs<ParseError.ChapterOutOfRange>(ReferenceParser.parse("Jao 0").error)
    }

    @Test fun accentInsensitiveAndFullNames() {
        assertEquals(43, (ReferenceParser.matchBook("Jaona") as BookMatch.Found).book.id)
        assertEquals(43, (ReferenceParser.matchBook("JAONA") as BookMatch.Found).book.id)
        assertEquals(49, (ReferenceParser.matchBook("Efesiana") as BookMatch.Found).book.id)
        assertEquals(49, (ReferenceParser.matchBook("Éfésiana") as BookMatch.Found).book.id)
        assertEquals(44, (ReferenceParser.matchBook("Asan’ ny Apostoly") as BookMatch.Found).book.id)
        assertEquals(44, (ReferenceParser.matchBook("asan'ny apostoly") as BookMatch.Found).book.id)
        assertEquals(listOf(Passage(49, 2, 4, 4)), ok("Éfé 2:4"))
        assertEquals(listOf(Passage(19, 1, 1, 1)), ok("Salamo 1:1"))
    }

    @Test fun frenchAndEnglishFallback() {
        assertEquals(listOf(Passage(43, 3, 16, 16)), ok("Jean 3:16"))
        assertEquals(listOf(Passage(43, 3, 16, 16)), ok("John 3:16"))
        assertEquals(listOf(Passage(23, 53, 5, 5)), ok("Ésaïe 53:5"))
        assertEquals(listOf(Passage(62, 4, 8, 8)), ok("1 Jaona 4:8"))
    }

    @Test fun ambiguousAndUnknownBooks() {
        assertIs<ParseError.AmbiguousBook>(ReferenceParser.parse("Jo 1:1").error)
        assertIs<ParseError.AmbiguousBook>(ReferenceParser.parse("Tim 1:1").error) // needs 1 or 2
        assertIs<ParseError.UnknownBook>(ReferenceParser.parse("Xyz 1:1").error)
        assertIs<ParseError.MissingBook>(ReferenceParser.parse("3:16").error)
    }

    @Test fun adjacentVersesMerge() {
        assertEquals(listOf(Passage(49, 2, 4, 5)), ok("Efe 2: 4, 5"))
        assertEquals(listOf(Passage(49, 2, 4, 9)), ok("Efe 2: 8-9, 4-7"))
    }

    @Test fun newBookAfterSemicolon() {
        assertEquals(listOf(Passage(43, 3, 16, 16), Passage(45, 5, 8, 8)), ok("Jao 3:16; Rom 5:8"))
    }

    @Test fun incompleteIsNotAnError() {
        assertEquals(ParseError.Incomplete, ReferenceParser.parse("Jao 3:").error)
        assertEquals(ParseError.Incomplete, ReferenceParser.parse("Efe 2: 4,").error)
        assertEquals(ParseError.Incomplete, ReferenceParser.parse("Efe 2: 8-").error)
        assertEquals(ParseError.Incomplete, ReferenceParser.parse("Jao").error)
    }

    @Test fun textNorm() {
        assertEquals("masina", TextNorm.norm("masìna"))
        assertEquals("ny lalan'i jehovah", TextNorm.norm("Ny lalàn’i Jehovah"))
        assertEquals("y", TextNorm.norm("ỳ"))
    }

    @Test fun canon() {
        assertEquals(66, Canon.books.size)
        assertEquals(21, Canon.chapterCount(43))
        assertEquals(36, Canon.verseCount(43, 3))
        assertEquals(1 to 2, Canon.nextChapter(1, 1))
        assertEquals(2 to 1, Canon.nextChapter(1, 50))
        assertNull(Canon.nextChapter(66, 22))
        assertEquals(1 to 50, Canon.previousChapter(2, 1))
        assertNull(Canon.previousChapter(1, 1))
    }

    /** Abbreviations from the 1865 source and the Malagasy Bible dictionary; longer prefixes also work. */
    @Test fun malagasyAbbreviations() {
        val expect = mapOf(
            "Gen 1:1" to 1, "Eks 3:14" to 2, "Deot 6:4" to 5, "Deo 6:4" to 5, "Deoteronomia 6:4" to 5,
            "Mpits 6:12" to 7, "Mpit 3:1" to 21, "1 Mpanj 18:21" to 11, "2 Tant 7:14" to 14,
            "Oha 3:5" to 20, "Ohab 3:5" to 20, "Ton 2:4" to 22, "Fit 3:22" to 25, "Ezek 37:4" to 26,
            "Asan 2:38" to 44, "Asa 2:38" to 44, "Flp 4:13" to 50, "Filip 4:13" to 50, "Flm 1:6" to 57,
            "Filem 1:6" to 57, "Jak 1:5" to 59, "1 Pet 5:7" to 60, "Apok 21:4" to 66, "Joda 1:24" to 65,
        )
        for ((ref, book) in expect) assertEquals(book, ok(ref).single().book, ref)
        // "Fil" could be Filipiana or Filemona: the parser asks instead of guessing
        val e = assertIs<ParseError.AmbiguousBook>(ReferenceParser.parse("Fil 4:13").error)
        assertEquals(setOf(50, 57), e.candidates.toSet())
    }
}
