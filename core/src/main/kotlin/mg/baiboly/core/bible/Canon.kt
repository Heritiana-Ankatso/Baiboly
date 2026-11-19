package mg.baiboly.core.bible

/**
 * Book table and verse counts in the Malagasy 1865 numbering. Generated at
 * build time from the bundled text, so the parser and the suggestions never
 * need the database.
 */
object Canon {
    val books: List<BookInfo> = CanonData.books
    private val counts: List<IntArray> = CanonData.verseCounts

    const val BOOK_COUNT = 66

    fun book(id: Int): BookInfo = books[id - 1]

    fun chapterCount(book: Int): Int = counts[book - 1].size

    /** Verses in a chapter, or 0 if the chapter does not exist. */
    fun verseCount(book: Int, chapter: Int): Int =
        counts.getOrNull(book - 1)?.getOrNull(chapter - 1) ?: 0

    /** Next chapter through the whole Bible, or null at Revelation 22. */
    fun nextChapter(book: Int, chapter: Int): Pair<Int, Int>? = when {
        chapter < chapterCount(book) -> book to chapter + 1
        book < BOOK_COUNT -> book + 1 to 1
        else -> null
    }

    /** Previous chapter through the whole Bible, or null at Genesis 1. */
    fun previousChapter(book: Int, chapter: Int): Pair<Int, Int>? = when {
        chapter > 1 -> book to chapter - 1
        book > 1 -> book - 1 to chapterCount(book - 1)
        else -> null
    }
}
