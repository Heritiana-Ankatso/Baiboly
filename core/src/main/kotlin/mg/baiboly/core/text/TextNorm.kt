package mg.baiboly.core.text

import java.text.Normalizer

/**
 * The single text-normalisation rule used for every text match
 * (book names, search). Lowercase, diacritics stripped (ì -> i, ỳ -> y),
 * typographic apostrophes treated as '.
 *
 * Must stay identical to `norm()` in tools/build_data.py, which fills the
 * `text_norm` column and the full-text index at build time.
 */
object TextNorm {
    private val marks = Regex("\\p{Mn}+")

    fun norm(s: String): String {
        val a = s.replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'')
        val d = Normalizer.normalize(a.lowercase(), Normalizer.Form.NFD)
        return Normalizer.normalize(marks.replace(d, ""), Normalizer.Form.NFC)
    }

    /** Letters and digits only: used to compare book names ("Asan' ny Apostoly" -> "asannyapostoly"). */
    fun key(s: String): String = norm(s).filter { it.isLetterOrDigit() }
}
