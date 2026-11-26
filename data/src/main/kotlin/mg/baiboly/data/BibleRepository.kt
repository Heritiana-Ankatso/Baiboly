package mg.baiboly.data

import android.database.sqlite.SQLiteDatabase
import mg.baiboly.core.text.TextNorm

data class Translation(
    val id: String,
    val num: Int,
    val lang: String,
    val shortName: String,
    val name: String,
    val style: String,
    val styleNote: String,
    val licence: String,
    val source: String,
    val notes: String?,
    val file: String,
    /** Catholic Bible: has the deuterocanonical books (Tobit, Judith, 1-2 Maccabees, Wisdom, Sirach, Baruch). */
    val catholic: Boolean,
)

data class VerseText(val verse: Int, val text: String)

data class ChapterText(
    val translation: String,
    val book: Int,
    val chapter: Int,
    val verses: List<VerseText>,
    /** Section headings shown before a verse. */
    val headings: Map<Int, List<String>>,
)

data class SearchHit(val book: Int, val chapter: Int, val verse: Int, val text: String)

/** Read access to the reading texts (bundled or downloaded). Call from a background thread. */
class BibleRepository(private val dbs: AssetDatabases) {

    companion object {
        const val PRIMARY = "mg1865"

        /** Languages in the order the menus show them. */
        val LANGUAGES = listOf("mg", "fr", "en")

        /**
         * Year each text was first published complete, so every list runs from the
         * oldest to the most recent within its language. Douay-Rheims: the 1899
         * edition of Challoner's revision; BSB: the whole Bible as Berean Study
         * Bible (renamed Berean Standard Bible in 2022); WEB: declared complete in 2000.
         */
        val YEAR = mapOf(
            "mg1865" to 1865, "tdx" to 2023,
            "darby_fr" to 1885, "lsg1910" to 1910, "crampon" to 1923, "ncl_fr" to 2022,
            "kjv" to 1611, "dra" to 1899, "bbe" to 1949, "web" to 2000, "webc" to 2000, "bsb" to 2018, "lsv" to 2020,
        )

        /** By language (Malagasy, French, English), then from the oldest to the most recent. */
        fun ordered(ts: List<Translation>): List<Translation> = ts.withIndex().sortedWith(
            compareBy<IndexedValue<Translation>>(
                { LANGUAGES.indexOf(it.value.lang).let { i -> if (i < 0) LANGUAGES.size else i } },
                { YEAR[it.value.id] ?: Int.MAX_VALUE },
                { it.index },
            ),
        ).map { it.value }
        private const val CATALOGUE = "bible.db"
        fun verseId(num: Int, book: Int, chapter: Int, verse: Int): Long =
            num * 100_000_000L + book * 1_000_000L + chapter * 1_000L + verse
    }

    val translations: List<Translation> by lazy {
        dbs.get(CATALOGUE).rawQuery(
            "SELECT id,num,lang,short_name,name,style,style_note,licence,source,notes,file,catholic FROM translation ORDER BY sort",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Translation(
                        c.getString(0), c.getInt(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5),
                        c.getString(6) ?: "", c.getString(7), c.getString(8), c.getString(9), c.getString(10),
                        c.getInt(11) != 0,
                    )
                )
            }
        }
    }

    fun translation(id: String): Translation = translations.firstOrNull { it.id == id } ?: translations.first()

    /**
     * On the phone now, so it can be read, searched and compared. A text that is
     * not downloaded yet is still listed in [translations] (the catalogue is in
     * bible.db), and offered for download where it would appear. Reading it
     * before then throws [PackMissingException].
     */
    fun isAvailable(id: String): Boolean {
        val t = translations.firstOrNull { it.id == id } ?: return false
        return t.id == PRIMARY || dbs.isUsable(t.file)
    }

    /** The download of a text, or null when it is bundled (Malagasy 1865) or unknown. */
    fun pack(id: String): Pack? = translations.firstOrNull { it.id == id }?.let { dbs.catalogue.pack(it.file) }

    private fun db(t: Translation): SQLiteDatabase = dbs.get(t.file)

    fun chapter(translationId: String, book: Int, chapter: Int): ChapterText {
        val t = translation(translationId)
        val db = db(t)
        val lo = verseId(t.num, book, chapter, 0)
        val verses = db.rawQuery(
            "SELECT verse, text FROM verse WHERE id BETWEEN ? AND ? ORDER BY id",
            arrayOf(lo.toString(), (lo + 999).toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(VerseText(c.getInt(0), c.getString(1))) } }
        val headings = HashMap<Int, MutableList<String>>()
        db.rawQuery(
            "SELECT verse, text FROM heading WHERE translation=? AND book=? AND chapter=? ORDER BY verse, seq",
            arrayOf(t.id, book.toString(), chapter.toString()),
        ).use { c -> while (c.moveToNext()) headings.getOrPut(c.getInt(0)) { mutableListOf() } += c.getString(1) }
        return ChapterText(t.id, book, chapter, verses, headings)
    }

    /** Verse text for a range, used by Copy, the notes list and Found N verses. */
    fun verses(translationId: String, book: Int, chapter: Int, from: Int, to: Int): List<VerseText> {
        val t = translation(translationId)
        val lo = verseId(t.num, book, chapter, if (from == 0) 0 else from)
        val hi = verseId(t.num, book, chapter, if (from == 0) 999 else to)
        return db(t).rawQuery(
            "SELECT verse, text FROM verse WHERE id BETWEEN ? AND ? ORDER BY id",
            arrayOf(lo.toString(), hi.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(VerseText(c.getInt(0), c.getString(1))) } }
    }

    private val chapterCountCache = HashMap<String, Map<Int, Int>>()

    /** Chapters per book in this translation's own numbering (Malachi has 3 or 4, Joel 3 or 4...). */
    @Synchronized
    fun chapterCounts(translationId: String): Map<Int, Int> = chapterCountCache.getOrPut(translationId) {
        val t = translation(translationId)
        db(t).rawQuery(
            "SELECT book, MAX(chapter) FROM chapter_info WHERE translation=? GROUP BY book", arrayOf(t.id),
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) } }
    }

    fun verseCount(translationId: String, book: Int, chapter: Int): Int {
        val t = translation(translationId)
        return db(t).rawQuery(
            "SELECT verse_count FROM chapter_info WHERE translation=? AND book=? AND chapter=?",
            arrayOf(t.id, book.toString(), chapter.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /**
     * Accent-insensitive full-text search over text_norm (typing "masina"
     * finds "masìna"). Every word must occur; the last word also matches as a
     * prefix so results appear while typing.
     */
    fun search(translationId: String, query: String, limit: Int = 500): List<SearchHit> {
        val words = TextNorm.norm(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val match = words.mapIndexed { i, w -> if (i == words.lastIndex && w.length >= 2) "$w*" else w }
            .joinToString(" ")
        val t = translation(translationId)
        val db = db(t)
        val lo = verseId(t.num, 0, 0, 0)
        val hi = verseId(t.num + 1, 0, 0, 0)
        return db.rawQuery(
            "SELECT v.book, v.chapter, v.verse, v.text FROM verse_fts f JOIN verse v ON v.id = f.docid " +
                "WHERE verse_fts MATCH ? AND f.docid >= ? AND f.docid < ? ORDER BY f.docid LIMIT ?",
            arrayOf(match, lo.toString(), hi.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(SearchHit(c.getInt(0), c.getInt(1), c.getInt(2), c.getString(3))) } }
    }
}
