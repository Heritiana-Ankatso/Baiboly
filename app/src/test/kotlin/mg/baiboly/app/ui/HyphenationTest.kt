package mg.baiboly.app.ui

import org.junit.Test
import kotlin.test.assertEquals

class HyphenationTest {
    private fun shown(t: String) = malagasySoftHyphens(t).first.replace('­', '·')

    @Test fun openSyllables() {
        // consonant groups go with the vowel after them; no break leaves fewer than 2 or 3 letters
        assertEquals("Andria·ma·ni·tra", shown("Andriamanitra"))
        assertEquals("va·vo·lo·mbe·lona", shown("vavolombelona"))
        assertEquals("ha·na·mbara", shown("hanambara"))
        assertEquals("ny Azy tsy", shown("ny Azy tsy"))
        // an apostrophe ends a word
        assertEquals("za·nak’ Andria·ma·ni·tra,", shown("zanak’ Andriamanitra,"))
    }

    @Test fun mapKeepsOriginalOffsets() {
        val text = "Teo amin’ izao tontolo izao"
        val (out, map) = malagasySoftHyphens(text)
        for (i in text.indices) assertEquals(text[i], out[map[i]])
        assertEquals(out.length, map[text.length])
    }
}
