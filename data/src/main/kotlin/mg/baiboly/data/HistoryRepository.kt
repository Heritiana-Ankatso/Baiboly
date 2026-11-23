package mg.baiboly.data

import mg.baiboly.core.ref.Passage
import mg.baiboly.core.text.TextNorm

/**
 * A person of the Bible (Theographic Bible Metadata, CC BY-SA 4.0). Years are
 * astronomical (1 BC = 0) and follow F. N. Jones's chronology; most people have none.
 */
data class Person(
    val id: Int,
    val name: String,
    val alsoCalled: String,
    /** "M", "F" or "". */
    val gender: String,
    val born: Int?,
    val died: Int?,
    /** Number of verses that name the person. */
    val mentions: Int,
    /** First verse naming the person (Malagasy numbering), if any. */
    val first: Passage?,
    /** Key of a note for the person's page ("son_of_god", "virgin_birth", "no_family"), or "". */
    val note: String = "",
)

/** father | mother | child | sibling | half_sibling | partner | legal_father | legal_child, seen from the person. */
data class Relative(val kind: String, val person: Person)

/** An event of the timeline. [year] after Jones; [ussher] = the TSK marginal year of its first verse. */
data class BibleEvent(
    val id: Int,
    val title: String,
    val year: Int,
    /** ISO calendar date where the text supports a day ("0030-04-04"), else "". */
    val date: String,
    /** "7D", "40Y"… */
    val duration: String,
    val approximate: Boolean,
    val notes: String,
    val ussher: Int?,
    val partOf: Int?,
    val first: Passage?,
)

/** A confession, catechism or creed. family: "creed" (shared by the traditions) or "reformed". */
data class CreedDoc(
    val id: String, val short: String, val title: String, val family: String,
    val year: String, val place: String, val language: String, val items: Int,
)

/** An article, a section of a chapter, or a question and answer. [proofs]: one line per footnote, library markup. */
data class CreedItem(val doc: String, val seq: Int, val label: String, val group: String, val question: String, val text: String, val proofs: String)

/** A council, synod or assembly that issued documents of the library. */
data class Council(val id: String, val name: String, val place: String, val year: String, val docs: List<String>)

/**
 * Family tree, timeline, confessions and councils (library.db, tools/build_extras.py).
 * Verses are in Malagasy 1865 numbering.
 */
class HistoryRepository(private val dbs: AssetDatabases) {
    private val db get() = dbs.get("library.db")

    private val personCols = "id, name, also, gender, born, died, mentions, book, chapter, verse, note"

    private fun android.database.Cursor.passageAt(i: Int): Passage? =
        if (getInt(i) == 0) null else runCatching { Passage(getInt(i), getInt(i + 1), getInt(i + 2), getInt(i + 2)) }.getOrNull()

    private fun android.database.Cursor.person(o: Int = 0) = Person(
        getInt(o), getString(o + 1), getString(o + 2), getString(o + 3),
        if (isNull(o + 4)) null else getInt(o + 4), if (isNull(o + 5)) null else getInt(o + 5),
        getInt(o + 6), passageAt(o + 7), getString(o + 10),
    )

    private fun people(sql: String, args: Array<String>): List<Person> =
        db.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.person()) } }

    /** People whose name starts with (then contains) the query; the most named first. */
    fun searchPeople(query: String, limit: Int = 200): List<Person> {
        val q = TextNorm.norm(query.trim()).replace("%", "").replace("_", "")
        return if (q.isEmpty()) people("SELECT $personCols FROM person ORDER BY mentions DESC LIMIT ?", arrayOf(limit.toString()))
        else people(
            "SELECT $personCols FROM person WHERE name_norm LIKE ? ORDER BY (name_norm LIKE ?) DESC, mentions DESC LIMIT ?",
            arrayOf("%$q%", "$q%", limit.toString()),
        )
    }

    fun person(id: Int): Person? = people("SELECT $personCols FROM person WHERE id=?", arrayOf(id.toString())).firstOrNull()

    fun relatives(id: Int): List<Relative> = db.rawQuery(
        "SELECT r.kind, p.id, p.name, p.also, p.gender, p.born, p.died, p.mentions, p.book, p.chapter, p.verse, p.note " +
            "FROM person_rel r JOIN person p ON p.id = r.other WHERE r.person=? ORDER BY p.born IS NULL, p.born, p.book, p.chapter, p.verse",
        arrayOf(id.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(Relative(c.getString(0), c.person(1))) } }

    /** Father line upwards, nearest first, as far as the data goes (at most [max]). */
    fun fatherLine(id: Int, max: Int = 80): List<Person> {
        val out = mutableListOf<Person>()
        var cur = id
        val seen = mutableSetOf(id)
        while (out.size < max) {
            // the legal father too: the genealogies of Jesus (Matt 1, Luke 3) run through Joseph
            val f = relatives(cur).firstOrNull { it.kind == "father" || it.kind == "legal_father" }?.person ?: break
            if (!seen.add(f.id)) break
            out += f
            cur = f.id
        }
        return out
    }

    fun versesOf(personId: Int, limit: Int = 400): List<Passage> = db.rawQuery(
        "SELECT book, chapter, verse FROM person_verse WHERE person=? ORDER BY book, chapter, verse LIMIT ?",
        arrayOf(personId.toString(), limit.toString()),
    ).use { c -> buildList { while (c.moveToNext()) c.passageAt(0)?.let { add(it) } } }

    /** People named in verses [from]..[to] of a chapter, the most named first. */
    fun peopleInVerses(book: Int, chapter: Int, from: Int, to: Int): List<Person> = people(
        "SELECT DISTINCT p.id, p.name, p.also, p.gender, p.born, p.died, p.mentions, p.book, p.chapter, p.verse, p.note " +
            "FROM person_verse v JOIN person p ON p.id = v.person WHERE v.book=? AND v.chapter=? AND v.verse BETWEEN ? AND ? ORDER BY p.mentions DESC",
        arrayOf(book.toString(), chapter.toString(), from.toString(), to.toString()),
    )

    private val eventCols = "e.id, e.title, e.year, e.date, e.duration, e.approx, e.notes, e.ussher, e.part_of, e.book, e.chapter, e.verse"

    private fun events(sql: String, args: Array<String>): List<BibleEvent> = db.rawQuery(sql, args).use { c ->
        buildList {
            while (c.moveToNext()) add(
                BibleEvent(
                    c.getInt(0), c.getString(1), c.getInt(2), c.getString(3), c.getString(4), c.getInt(5) != 0, c.getString(6),
                    if (c.isNull(7)) null else c.getInt(7), if (c.isNull(8)) null else c.getInt(8), c.passageAt(9),
                ),
            )
        }
    }

    /** The whole timeline, in order. */
    val timeline: List<BibleEvent> by lazy { events("SELECT $eventCols FROM event e ORDER BY e.sort", emptyArray()) }

    fun eventsOf(personId: Int): List<BibleEvent> = events(
        "SELECT $eventCols FROM event e JOIN event_person p ON p.event = e.id WHERE p.person=? ORDER BY e.sort", arrayOf(personId.toString()),
    )

    fun eventsInVerses(book: Int, chapter: Int, from: Int, to: Int): List<BibleEvent> = events(
        "SELECT DISTINCT $eventCols FROM event_verse v JOIN event e ON e.id = v.event " +
            "WHERE v.book=? AND v.chapter=? AND v.verse BETWEEN ? AND ? ORDER BY e.sort",
        arrayOf(book.toString(), chapter.toString(), from.toString(), to.toString()),
    )

    fun eventPeople(eventId: Int): List<Person> = people(
        "SELECT p.id, p.name, p.also, p.gender, p.born, p.died, p.mentions, p.book, p.chapter, p.verse, p.note " +
            "FROM event_person e JOIN person p ON p.id = e.person WHERE e.event=? ORDER BY p.mentions DESC",
        arrayOf(eventId.toString()),
    )

    fun eventVerses(eventId: Int): List<Passage> = db.rawQuery(
        "SELECT book, chapter, verse FROM event_verse WHERE event=? ORDER BY book, chapter, verse", arrayOf(eventId.toString()),
    ).use { c -> buildList { while (c.moveToNext()) c.passageAt(0)?.let { add(it) } } }

    // ------------------------------------------------------------ confessions and councils

    val creedDocs: List<CreedDoc> by lazy {
        db.rawQuery("SELECT id, short, title, family, year, place, language, items FROM creed_doc ORDER BY sort", null).use { c ->
            buildList { while (c.moveToNext()) add(CreedDoc(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getInt(7))) }
        }
    }

    fun creedItems(doc: String): List<CreedItem> = db.rawQuery(
        "SELECT doc, seq, label, grp, question, text, proofs FROM creed_item WHERE doc=? ORDER BY seq", arrayOf(doc),
    ).use { c -> buildList { while (c.moveToNext()) add(CreedItem(c.getString(0), c.getInt(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))) } }

    fun creedItem(doc: String, seq: Int): CreedItem? = db.rawQuery(
        "SELECT doc, seq, label, grp, question, text, proofs FROM creed_item WHERE doc=? AND seq=?", arrayOf(doc, seq.toString()),
    ).use { c -> if (c.moveToFirst()) CreedItem(c.getString(0), c.getInt(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6)) else null }

    /** Articles and answers that cite verses [from]..[to] of a chapter as proof texts. */
    fun creedCitations(book: Int, chapter: Int, from: Int, to: Int): List<CreedItem> = db.rawQuery(
        "SELECT DISTINCT i.doc, i.seq, i.label, i.grp, i.question, i.text, i.proofs FROM creed_ref r " +
            "JOIN creed_item i ON i.doc = r.doc AND i.seq = r.seq JOIN creed_doc d ON d.id = r.doc " +
            "WHERE r.book=? AND r.chapter=? AND r.v1 <= ? AND r.v2 >= ? ORDER BY d.sort, i.seq",
        arrayOf(book.toString(), chapter.toString(), to.toString(), from.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(CreedItem(c.getString(0), c.getInt(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))) } }

    val councils: List<Council> by lazy {
        db.rawQuery("SELECT id, name, place, year, docs FROM council ORDER BY sort", null).use { c ->
            buildList { while (c.moveToNext()) add(Council(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4).split(','))) }
        }
    }

    companion object {
        /** How a document item is cited: "WCF 1.2", "HC Q&A 2", "BC 3". */
        fun citation(doc: CreedDoc?, item: CreedItem): String {
            val short = doc?.short ?: item.doc
            return when {
                item.label.isEmpty() -> short
                item.question.isNotEmpty() -> "$short Q&A ${item.label}"
                else -> "$short ${item.label}"
            }
        }
    }
}
