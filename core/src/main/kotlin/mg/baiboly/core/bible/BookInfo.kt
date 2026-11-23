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

    /**
     * Abbreviation in a language: Malagasy from tools/abbreviations.tsv ([shortLabel]); English from
     * the OSIS id ("1Cor" -> "1 Cor"); French the usual one (Gn, Mt, 1 Co…).
     */
    fun abbr(lang: String): String = when (lang) {
        "en" -> osis.replace(Regex("^([1-3])(\\D)"), "$1 $2")
        "fr" -> FR_ABBR.getOrNull(id - 1) ?: nameFr
        else -> shortLabel
    }

    fun name(lang: String): String = when (lang) {
        "fr" -> nameFr
        "en" -> nameEn
        else -> nameMg
    }

    internal fun baseKey(name: String): String = TextNorm.key(name.replace(Regex("^[1-3]\\s*"), ""))
}

/** French abbreviations, in book order (66 books, then Tobit … Baruch). */
private val FR_ABBR = listOf(
    "Gn", "Ex", "Lv", "Nb", "Dt", "Jos", "Jg", "Rt", "1 S", "2 S", "1 R", "2 R", "1 Ch", "2 Ch", "Esd", "Né", "Est", "Jb",
    "Ps", "Pr", "Ec", "Ct", "Es", "Jr", "Lm", "Ez", "Dn", "Os", "Jl", "Am", "Ab", "Jon", "Mi", "Na", "Ha", "So", "Ag", "Za", "Ml",
    "Mt", "Mc", "Lc", "Jn", "Ac", "Rm", "1 Co", "2 Co", "Ga", "Ep", "Ph", "Col", "1 Th", "2 Th", "1 Tm", "2 Tm", "Tt", "Phm",
    "Hé", "Jc", "1 P", "2 P", "1 Jn", "2 Jn", "3 Jn", "Jd", "Ap",
    "Tb", "Jdt", "1 M", "2 M", "Sg", "Si", "Ba",
)
