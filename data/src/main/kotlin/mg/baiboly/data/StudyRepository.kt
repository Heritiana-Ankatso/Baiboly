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

/** A word of the Hebrew (OT) or Greek (NT) text of a verse, with its parsing in words. */
data class OriginalWord(
    val word: String,
    val translit: String?,
    val gloss: String?,
    /** Strong's number (H7225, G976): the lexicon key. */
    val strongs: String?,
    /** Extended Strong's number (H7225G): the key of the fuller STEPBible lexicon. */
    val dstrong: String?,
    /** The parsing, e.g. "Verb : Qal (Simple, Active) Perfect ... Third Singular Masculine"; null if unknown. */
    val parsing: String?,
    /** Greek only: the word is in the Textus Receptus but not in the Nestle-Aland text. */
    val onlyInReceivedText: Boolean,
)

data class OriginalVerse(
    val kjv: VerseRef,
    val hebrew: Boolean,
    val words: List<OriginalWord>,
)

/** The fuller lexicon of STEPBible: abridged BDB (Hebrew) or Abbott-Smith and others (Greek). */
data class ExtendedEntry(
    val dstrong: String,
    val lemma: String?,
    val translit: String?,
    val partOfSpeech: String?,
    val gloss: String?,
    val definition: String?,
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
    companion object {
        const val ORIGINAL = "original.db"
    }

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

    private val original get() = dbs.get(ORIGINAL)

    /** The Hebrew and Greek text is an optional download: true once it is on the phone. */
    val hasOriginal: Boolean get() = dbs.isUsable(ORIGINAL)

    val originalPack: Pack? get() = dbs.catalogue.pack(ORIGINAL)

    /**
     * The English words of the KJV for a passage (KJV numbering), from its Strong's tagging in the
     * bundled study.db: used to find dictionary entries and units without the KJV text being downloaded.
     */
    fun kjvWords(book: Int, chapter: Int, from: Int, to: Int): String = db.rawQuery(
        "SELECT word_en FROM kjv_word WHERE book=? AND chapter=? AND verse BETWEEN ? AND ? ORDER BY verse, position",
        arrayOf(book.toString(), chapter.toString(), from.toString(), to.toString()),
    ).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } }.joinToString(" ") }

    /** The Hebrew or Greek words of a verse (Malagasy numbering in; the text itself is numbered as the KJV). */
    fun originalText(mg: VerseRef): OriginalVerse {
        val kjv = toKjv(mg).first
        // a psalm's title is verse 0 of the original text and verse 1 of the KJV numbering used here
        val first = if (kjv.book == 19 && kjv.verse == 1) 0 else kjv.verse
        val words = original.rawQuery(
            "SELECT w.word, w.translit, w.gloss, w.strongs, w.dstrong, m.label, w.flag FROM orig_word w " +
                "LEFT JOIN morph_label m ON m.code = w.morph WHERE w.book=? AND w.chapter=? AND w.verse BETWEEN ? AND ? " +
                "ORDER BY w.verse, w.position",
            arrayOf(kjv.book.toString(), kjv.chapter.toString(), first.toString(), kjv.verse.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    OriginalWord(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6) == "K"),
                )
            }
        }
        return OriginalVerse(kjv, kjv.book <= 39, words)
    }

    /** The STEPBible lexicon entry of an extended Strong's number, else the first entry of the plain number. */
    fun extendedEntry(strongs: String, dstrong: String?): ExtendedEntry? {
        fun read(sql: String, arg: String): ExtendedEntry? = original.rawQuery(sql, arrayOf(arg)).use { c ->
            if (c.moveToFirst()) ExtendedEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)) else null
        }
        val cols = "SELECT dstrong, lemma, translit, pos, gloss, definition FROM lexicon_ext"
        return dstrong?.let { read("$cols WHERE dstrong=?", it) } ?: read("$cols WHERE strongs=? ORDER BY dstrong LIMIT 1", strongs)
    }

    fun originalVerseCount(strongs: String): Int =
        original.rawQuery("SELECT COUNT(*) FROM orig_strongs_verse WHERE strongs=?", arrayOf(strongs)).use { c -> c.moveToFirst(); c.getInt(0) }

    /** Every verse whose Hebrew or Greek text has a word with [strongs], in Malagasy numbering, canonical order. */
    fun originalVersesWith(strongs: String): List<VerseRef> = original.rawQuery(
        "SELECT book, chapter, verse FROM orig_strongs_verse WHERE strongs=? AND verse>0 ORDER BY book, chapter, verse",
        arrayOf(strongs),
    ).use { c -> buildList { while (c.moveToNext()) add(fromKjv(VerseRef(c.getInt(0), c.getInt(1), c.getInt(2)))) } }

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
