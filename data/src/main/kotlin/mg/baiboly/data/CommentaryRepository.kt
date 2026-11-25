package mg.baiboly.data

/** A commentary shipped with the app (tools/build_commentaries.py). */
data class Commentary(val id: String, val title: String, val author: String, val year: String, val tradition: String)

/** A note of a commentary on verses [v1]..[v2] of a chapter (Malagasy 1865 numbering); [body] in library markup. */
data class CommentNote(val commentary: Commentary, val book: Int, val chapter: Int, val v1: Int, val v2: Int, val body: String)

/**
 * Commentaries in the public domain: the Geneva Bible notes (1599), Calvin's commentaries and Matthew
 * Henry's Concise Commentary, all of the Reformed tradition. Each is its own database
 * (commentary-<id>.db), unpacked on the phone only when first opened.
 */
class CommentaryRepository(private val dbs: AssetDatabases) {
    val available: List<Commentary> by lazy { KNOWN.filter { runCatching { dbs.isBundled("commentary-${it.id}.db") }.getOrDefault(false) } }

    /** The notes of [c] on verses [from]..[to] of a chapter, in order. */
    fun notes(c: Commentary, book: Int, chapter: Int, from: Int, to: Int): List<CommentNote> {
        val db = dbs.get("commentary-${c.id}.db")
        return db.rawQuery(
            "SELECT book, chapter, v1, v2, body FROM comment WHERE book=? AND chapter=? AND v1 <= ? AND v2 >= ? ORDER BY v1, v2",
            arrayOf(book.toString(), chapter.toString(), to.toString(), from.toString()),
        ).use { cur -> buildList { while (cur.moveToNext()) add(CommentNote(c, cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3), cur.getString(4))) } }
    }

    companion object {
        /** In the order they are offered: the Reformers first, then the Puritan devotional commentary. */
        val KNOWN = listOf(
            Commentary("calvin", "Calvin’s Commentaries", "John Calvin", "1540–1564", "reformed"),
            Commentary("geneva", "Geneva Bible notes", "Translators of the Geneva Bible", "1560–1599", "reformed"),
            Commentary("mhcc", "Matthew Henry’s Concise Commentary", "Matthew Henry", "1706–1721", "reformed"),
        )
    }
}
