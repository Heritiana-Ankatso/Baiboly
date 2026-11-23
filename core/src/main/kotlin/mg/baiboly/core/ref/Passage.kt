package mg.baiboly.core.ref

import mg.baiboly.core.bible.Canon

/**
 * A contiguous range of verses in one chapter. [verseStart] == 0 means the
 * whole chapter (the user typed a chapter number alone).
 */
data class Passage(val book: Int, val chapter: Int, val verseStart: Int = 0, val verseEnd: Int = verseStart) {
    init {
        require(book in 1..Canon.BOOK_COUNT) { "book $book" }
        require(verseStart >= 0 && verseEnd >= verseStart) { "range $verseStart-$verseEnd" }
    }

    val isWholeChapter: Boolean get() = verseStart == 0

    fun contains(verse: Int): Boolean = isWholeChapter || verse in verseStart..verseEnd

    /** First verse to scroll to. */
    val firstVerse: Int get() = if (isWholeChapter) 1 else verseStart

    /** "Jao 3: 16", "Efe 2: 8-9", "Sal 23" (chip label, the user's own style). */
    fun shortLabel(): String = Canon.book(book).shortLabel + " " + numbers()

    /** In the interface language: "Jao 3: 16" (mg), "Jn 3:16" (fr), "John 3:16" (en). */
    fun shortLabel(lang: String): String =
        if (lang == "mg" || lang.isEmpty()) shortLabel() else Canon.book(book).abbr(lang) + " " + numbers().replace(": ", ":")

    /** "Jaona 3: 16" */
    fun longLabel(lang: String = "mg"): String = Canon.book(book).name(lang) + " " + numbers()

    fun numbers(): String = when {
        isWholeChapter -> "$chapter"
        verseStart == verseEnd -> "$chapter: $verseStart"
        else -> "$chapter: $verseStart-$verseEnd"
    }

    companion object {
        /** Formats several passages the way the user writes them: "Efesiana 2: 4, 8-9; 3: 1". */
        fun describe(passages: List<Passage>, lang: String = "mg"): String {
            val sb = StringBuilder()
            var lastBook = -1
            var lastChapter = -1
            for (p in passages) {
                when {
                    p.book != lastBook -> {
                        if (sb.isNotEmpty()) sb.append("; ")
                        sb.append(Canon.book(p.book).name(lang)).append(' ').append(p.numbers())
                    }
                    p.chapter != lastChapter || p.isWholeChapter -> sb.append("; ").append(p.numbers())
                    else -> sb.append(", ").append(if (p.verseStart == p.verseEnd) "${p.verseStart}" else "${p.verseStart}-${p.verseEnd}")
                }
                lastBook = p.book
                lastChapter = p.chapter
            }
            return sb.toString()
        }
    }
}
