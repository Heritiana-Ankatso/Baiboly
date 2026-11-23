package mg.baiboly.core

import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reported: references used the Malagasy abbreviations (Jao, Eks, Apok) in the English interface. */
class PassageLabelTest {
    @Test fun referencesInTheInterfaceLanguage() {
        val john = Passage(43, 3, 16, 16)
        assertEquals("Jao 3: 16", john.shortLabel("mg"))
        assertEquals("John 3:16", john.shortLabel("en"))
        assertEquals("Jn 3:16", john.shortLabel("fr"))
        assertEquals("1 Cor 15:3-4", Passage(46, 15, 3, 4).shortLabel("en"))
        assertEquals("1 Co 15:3-4", Passage(46, 15, 3, 4).shortLabel("fr"))
        assertEquals("Rev 1:8", Passage(66, 1, 8, 8).shortLabel("en"))
        assertEquals("Ap 1:8", Passage(66, 1, 8, 8).shortLabel("fr"))
        assertEquals("Ps 23", Passage(19, 23).shortLabel("en"))
        assertEquals("Sir 1:1", Passage(72, 1, 1, 1).shortLabel("en"))
    }

    @Test fun everyBookHasAnAbbreviationInEachLanguage() {
        for (b in Canon.books) for (lang in listOf("mg", "fr", "en")) {
            val a = b.abbr(lang)
            assertTrue(a.isNotBlank() && a.length <= 8, "${b.osis} $lang: '$a'")
        }
        // French abbreviations are distinct (no two books share one)
        val fr = Canon.books.map { it.abbr("fr") }
        assertEquals(fr.size, fr.toSet().size, fr.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }
}
