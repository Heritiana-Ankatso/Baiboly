package mg.baiboly.app.ui

/**
 * Break points for Malagasy, which Android has no hyphenation patterns for.
 *
 * Malagasy syllables are open: every consonant group (b, mb, ndr, ts, ntr…)
 * belongs to the vowel after it, so a word may break before any consonant
 * group that stands between two vowels: An·dria·ma·ni·tra. Soft hyphens
 * (U+00AD) are invisible unless the line breaks there.
 *
 * Returns the text with soft hyphens and, for each index of the original text,
 * its index in the new one (one entry more than the text, for the end), so
 * highlight ranges and taps stay on the right characters.
 */
internal fun malagasySoftHyphens(text: String, minBefore: Int = 2, minAfter: Int = 3): Pair<String, IntArray> {
    val map = IntArray(text.length + 1)
    val out = StringBuilder(text.length + text.length / 4)
    var i = 0
    while (i < text.length) {
        if (!text[i].isLetter()) { map[i] = out.length; out.append(text[i]); i++; continue }
        // one word: a run of letters (an apostrophe or hyphen ends it)
        var end = i
        while (end < text.length && text[end].isLetter()) end++
        val breaks = HashSet<Int>()
        var k = i + 1
        while (k < end) {
            if (isVowel(text[k - 1]) && !isVowel(text[k])) {
                var c = k
                while (c < end && !isVowel(text[c])) c++
                // consonants then a vowel: break before the consonant group
                if (c < end && k - i >= minBefore && end - k >= minAfter) breaks += k
                k = c
            } else k++
        }
        for (j in i until end) {
            if (j in breaks) out.append('­')
            map[j] = out.length
            out.append(text[j])
        }
        i = end
    }
    map[text.length] = out.length
    return out.toString() to map
}

private fun isVowel(c: Char): Boolean = c.lowercaseChar() in "aeiouyàâäéèêëìîïòôöùûüỳ"
