package mg.baiboly.data

/** A KJV word carrying a Strong's number, with its lexicon headword. */
data class TaggedWord(
    val position: Int,
    val word: String,
    val strongs: String,
    val lemma: String?,
    val translit: String?,
)

data class LexiconEntry(
    val strongs: String,
    val lemma: String?,
    val translit: String?,
    val pron: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val kjvUsage: String?,
)

/** A verse in Malagasy numbering. */
data class VerseRef(val book: Int, val chapter: Int, val verse: Int)

data class VerseStudy(
    /** The KJV verse the words come from. */
    val kjv: VerseRef,
    val words: List<TaggedWord>,
    /** True when this chapter's numbering differs and no evidence-based mapping exists. */
    val numberingUncertain: Boolean,
)

/**
 * Original-language study data (KJV with Strong's numbers + Strong's
 * dictionaries), read-only, from study.db. Verse numbers given and returned
 * here are Malagasy 1865 numbers; the KJV numbering is handled via verse_map.
 */
class StudyRepository(private val dbs: AssetDatabases) {
    private val db get() = dbs.get("study.db")

    /** verse_map is small (one row per verse of a remapped chapter): keep it in memory. */
    private val forward: Map<VerseRef, VerseRef> by lazy {
        db.rawQuery("SELECT book, chapter, verse, kjv_chapter, kjv_verse FROM verse_map WHERE translation='mg1865'", null)
            .use { c ->
                buildMap { while (c.moveToNext()) put(VerseRef(c.getInt(0), c.getInt(1), c.getInt(2)), VerseRef(c.getInt(0), c.getInt(3), c.getInt(4))) }
            }
    }
    private val inverse: Map<VerseRef, VerseRef> by lazy {
        // Several Malagasy verses can map to KJV verse 1 (title + first verse): keep the later one.
        forward.entries.sortedBy { it.key.verse }.associate { it.value to it.key }
    }
    private val unresolvedChapters: Set<Pair<Int, Int>> by lazy {
        db.rawQuery("SELECT book, chapter FROM verse_map_chapter WHERE translation='mg1865' AND method='unresolved'", null)
            .use { c -> buildSet { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) } }
    }

    private fun toKjv(r: VerseRef): Pair<VerseRef, Boolean> =
        forward[r]?.let { it to false } ?: (r to ((r.book to r.chapter) in unresolvedChapters))

    private fun fromKjv(k: VerseRef): VerseRef = inverse[k] ?: k

    /** A KJV-numbered reference (dictionaries use KJV numbering) in Malagasy 1865 numbering. */
    fun kjvToMg(k: VerseRef): VerseRef = fromKjv(k)

    /** A Malagasy 1865 reference in KJV numbering (the numbering of the other texts, Psalms of the Douay-Rheims aside). */
    fun mgToKjv(mg: VerseRef): VerseRef = toKjv(mg).first

    fun verse(mg: VerseRef): VerseStudy {
        val (kjv, uncertain) = toKjv(mg)
        val words = db.rawQuery(
            "SELECT w.position, w.word_en, w.strongs, l.lemma, l.translit FROM kjv_word w " +
                "LEFT JOIN lexicon l ON l.strongs = w.strongs WHERE w.book=? AND w.chapter=? AND w.verse=? ORDER BY w.position",
            arrayOf(kjv.book.toString(), kjv.chapter.toString(), kjv.verse.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(TaggedWord(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
            }
        }
        return VerseStudy(kjv, words, uncertain)
    }

    fun entry(strongs: String): LexiconEntry? = db.rawQuery(
        "SELECT strongs, lemma, translit, pron, part_of_speech, definition_en, kjv_usage FROM lexicon WHERE strongs=?",
        arrayOf(strongs),
    ).use { c ->
        if (c.moveToFirst()) LexiconEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))
        else null
    }

    fun verseCount(strongs: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM strongs_verse WHERE strongs=?", arrayOf(strongs)).use { c -> c.moveToFirst(); c.getInt(0) }

    /** Every verse carrying [strongs], in Malagasy numbering, canonical order. */
    fun versesWith(strongs: String): List<VerseRef> = db.rawQuery(
        "SELECT book, chapter, verse FROM strongs_verse WHERE strongs=? ORDER BY book, chapter, verse",
        arrayOf(strongs),
    ).use { c -> buildList { while (c.moveToNext()) add(fromKjv(VerseRef(c.getInt(0), c.getInt(1), c.getInt(2)))) } }
}
