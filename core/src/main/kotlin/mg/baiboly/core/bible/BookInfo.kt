package mg.baiboly.core.bible

import mg.baiboly.core.text.TextNorm

/**
 * One of the 66 books. Names come from the Malagasy 1865 source; the
 * abbreviation comes from tools/abbreviations.tsv (confirmed = taken from the
 * user's own usage, otherwise awaiting review).
 */
data class BookInfo(
    val id: Int,
    val osis: String,
    val nameMg: String,
    val abbrMg: String,
    val abbrConfirmed: Boolean,
    val aliases: List<String>,
    val nameFr: String,
    val nameEn: String,
) {
    /** Leading book number (1 Samoela -> 1), or null. */
    val number: Int? = Regex("^([1-3])\\s").find(nameMg)?.groupValues?.get(1)?.toInt()

    /** Tobit, Judith, 1-2 Maccabees, Wisdom, Sirach, Baruch: only in Catholic Bibles. */
    val isDeuterocanonical: Boolean get() = id >= 67

    /** Written in Hebrew or Aramaic (the deuterocanonical books come down to us in Greek). */
    val isOldTestament: Boolean get() = id <= 39 || isDeuterocanonical

    /** Hebrew/Aramaic original (study symbol א) rather than Greek (α). */
    val isHebrewOriginal: Boolean get() = id <= 39

    /** Short label used on chips: "Jao", "2 Tim". */
    val shortLabel: String
        get() {
            val a = abbrMg.ifBlank { nameMg }
            return if (number != null && !a.startsWith("$number")) "$number $a" else a
        }

    fun name(lang: String): String = when (lang) {
        "fr" -> nameFr
        "en" -> nameEn
        else -> nameMg
    }

    internal fun baseKey(name: String): String = TextNorm.key(name.replace(Regex("^[1-3]\\s*"), ""))
}
