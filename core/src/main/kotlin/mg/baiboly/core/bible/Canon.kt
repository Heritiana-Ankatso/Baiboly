package mg.baiboly.core.bible

/**
 * Book table and verse counts in the Malagasy 1865 numbering. Generated at
 * build time from the bundled text, so the parser and the suggestions never
 * need the database.
 */
object Canon {
    val books: List<BookInfo> = CanonData.books
    private val counts: List<IntArray> = CanonData.verseCounts

    /** 66 books of every Bible + 7 deuterocanonical books (ids 67-73) of Catholic Bibles. */
    const val BOOK_COUNT = 73

    /**
     * Reading order. Catholic Bibles place Tobit and Judith after Nehemiah,
     * 1-2 Maccabees after Esther, Wisdom and Sirach after the Song of Songs and
     * Baruch after Lamentations; for a 66-book Bible this is simply 1..66.
     */
    val ORDER: List<Int> = (1..16) + listOf(67, 68, 17, 69, 70) + (18..22) + listOf(71, 72) +
        (23..25) + listOf(73) + (26..66)

    /** Reading order restricted to the books a text contains. */
    fun order(available: Set<Int>?): List<Int> =
        if (available.isNullOrEmpty()) ORDER.filter { it <= 66 } else ORDER.filter { it in available }

    fun book(id: Int): BookInfo = books[id - 1]

    fun chapterCount(book: Int): Int = counts[book - 1].size

    /** Verses in a chapter, or 0 if the chapter does not exist. */
    fun verseCount(book: Int, chapter: Int): Int =
        counts.getOrNull(book - 1)?.getOrNull(chapter - 1) ?: 0

    /**
     * Next chapter through the whole Bible in reading order, or null at the end.
     * [chapters] gives each available book's chapter count in the text being read.
     */
    fun nextChapter(book: Int, chapter: Int, chapters: Map<Int, Int>? = null): Pair<Int, Int>? {
        val count = chapters?.get(book) ?: chapterCount(book)
        if (chapter < count) return book to chapter + 1
        val order = order(chapters?.keys)
        val i = order.indexOf(book)
        return if (i >= 0 && i < order.lastIndex) order[i + 1] to 1 else null
    }

    /** Previous chapter through the whole Bible in reading order, or null at Genesis 1. */
    fun previousChapter(book: Int, chapter: Int, chapters: Map<Int, Int>? = null): Pair<Int, Int>? {
        if (chapter > 1) return book to chapter - 1
        val order = order(chapters?.keys)
        val i = order.indexOf(book)
        if (i <= 0) return null
        val prev = order[i - 1]
        return prev to (chapters?.get(prev) ?: chapterCount(prev))
    }
}
