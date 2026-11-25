package mg.baiboly.app

import mg.baiboly.core.ref.Passage
import mg.baiboly.data.Highlight
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A tap on a highlight colour over text already highlighted in it takes the highlight off; one verse included. */
class HighlightToggleTest {
    private val yellow = 1
    private val green = 2
    private fun hl(id: Long, v1: Int, v2: Int = v1, colour: Int = yellow, chars: IntRange? = null) =
        Highlight(id, Passage(43, 3, v1, v2), chars?.first, chars?.let { it.last + 1 }, colour, 0)

    @Test fun sameColourOnOneVerseRemovesIt() {
        val (remove, add) = planHighlight(listOf(hl(1, 16)), listOf(Passage(43, 3, 16, 16)), yellow, null)
        assertEquals(listOf(1L), remove); assertFalse(add)
    }

    @Test fun sameColourOnSeveralVersesRemovesThem() {
        val (remove, add) = planHighlight(listOf(hl(1, 16, 17), hl(2, 18)), listOf(Passage(43, 3, 16, 18)), yellow, null)
        assertEquals(setOf(1L, 2L), remove.toSet()); assertFalse(add)
    }

    @Test fun anotherColourReplaces() {
        val (remove, add) = planHighlight(listOf(hl(1, 16)), listOf(Passage(43, 3, 16, 16)), green, null)
        assertEquals(listOf(1L), remove); assertTrue(add)
    }

    @Test fun partlyHighlightedGetsTheColour() {
        // verse 17 is not yellow yet: the whole choice becomes yellow; the highlight wholly inside gives way
        val (remove, add) = planHighlight(listOf(hl(1, 16)), listOf(Passage(43, 3, 16, 17)), yellow, null)
        assertEquals(listOf(1L), remove); assertTrue(add)
    }

    @Test fun charactersToggleToo() {
        val (remove, add) = planHighlight(listOf(hl(1, 16, chars = 0..9)), listOf(Passage(43, 3, 16, 16)), yellow, 2..5)
        assertEquals(listOf(1L), remove); assertFalse(add)
        val (_, add2) = planHighlight(emptyList(), listOf(Passage(43, 3, 16, 16)), yellow, 2..5)
        assertTrue(add2)
    }

    @Test fun otherChaptersAreLeftAlone() {
        val other = Highlight(9, Passage(43, 4, 16, 16), null, null, yellow, 0)
        val (remove, add) = planHighlight(listOf(other), listOf(Passage(43, 3, 16, 16)), yellow, null)
        assertTrue(remove.isEmpty()); assertTrue(add)
    }
}
