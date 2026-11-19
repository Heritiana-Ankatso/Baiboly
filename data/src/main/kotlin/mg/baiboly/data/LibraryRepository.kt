package mg.baiboly.data

import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.text.TextNorm

/** One TSK keyword group for a verse: "beginning" -> Pr 8:22-24, Joh 1:1-3 ... */
data class CrossRefGroup(val keyword: String, val refs: List<Passage>)

data class DictSource(val id: String, val name: String, val lang: String, val kind: String, val licence: String, val url: String)

data class DictEntry(val source: String, val key: String, val body: String)

data class Place(
    val id: String,
    val name: String,
    val type: String,
    val lon: Double,
    val lat: Double,
    /** 3 = well established, 2 = likely, 1 = uncertain (OpenBible.info votes). */
    val confidence: Int,
)

/** A piece of dictionary text: plain, a Bible reference, or a link to another entry. */
sealed interface Markup {
    data class Plain(val text: String) : Markup
    data class Ref(val passage: Passage, val label: String) : Markup
    data class Link(val key: String, val label: String) : Markup
}

/**
 * Study library (library.db): TSK cross-references, Easton / Nave / Martin
 * dictionaries and OpenBible.info places. Verse numbers are Malagasy 1865.
 */
class LibraryRepository(private val dbs: AssetDatabases, private val study: StudyRepository) {
    private val db get() = dbs.get("library.db")

    fun crossRefs(book: Int, chapter: Int, verse: Int): List<CrossRefGroup> = db.rawQuery(
        "SELECT keyword, refs FROM xref WHERE book=? AND chapter=? AND verse=? ORDER BY grp",
        arrayOf(book.toString(), chapter.toString(), verse.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val refs = c.getString(1).split(' ').mapNotNull { parseRef(it) }
                if (refs.isNotEmpty()) add(CrossRefGroup(c.getString(0), refs))
            }
        }
    }

    private fun parseRef(s: String): Passage? {
        val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(\\d+))?$").find(s) ?: return null
        val b = m.groupValues[1].toInt()
        val v1 = m.groupValues[3].toInt()
        val v2 = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: v1
        return runCatching { Passage(b, m.groupValues[2].toInt(), v1, maxOf(v1, v2)) }.getOrNull()
    }

    val sources: List<DictSource> by lazy {
        db.rawQuery("SELECT id, name, lang, kind, licence, url FROM dict_source ORDER BY sort", null).use { c ->
            buildList { while (c.moveToNext()) add(DictSource(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))) }
        }
    }

    /** Entries whose title starts with (then contains) the query, accent- and case-insensitive. */
    fun search(source: String, query: String, limit: Int = 200): List<String> {
        val q = TextNorm.norm(query.trim())
        if (q.isEmpty()) {
            return db.rawQuery("SELECT key FROM dict_entry WHERE source=? ORDER BY key_norm LIMIT ?", arrayOf(source, limit.toString()))
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        }
        val like = q.replace("%", "").replace("_", "")
        return db.rawQuery(
            "SELECT key FROM dict_entry WHERE source=? AND key_norm LIKE ? ORDER BY (key_norm LIKE ?) DESC, key_norm LIMIT ?",
            arrayOf(source, "%$like%", "$like%", limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    fun entry(source: String, key: String): DictEntry? = db.rawQuery(
        "SELECT key, body FROM dict_entry WHERE source=? AND (key=? OR key_norm=?) LIMIT 1",
        arrayOf(source, key, TextNorm.norm(key)),
    ).use { c -> if (c.moveToFirst()) DictEntry(source, c.getString(0), c.getString(1)) else null }

    /** Splits stored entry text into plain text, Bible references and entry links. */
    fun markup(body: String): List<Markup> {
        val out = mutableListOf<Markup>()
        var pos = 0
        for (m in Regex("⟦([^|⟧]+)\\|([^⟧]*)⟧").findAll(body)) {
            if (m.range.first > pos) out += Markup.Plain(body.substring(pos, m.range.first))
            val target = m.groupValues[1]
            val label = m.groupValues[2]
            out += if (target.startsWith("@")) Markup.Link(target.substring(1), label)
            else osisToPassage(target)?.let { Markup.Ref(it, label) } ?: Markup.Plain(label)
            pos = m.range.last + 1
        }
        if (pos < body.length) out += Markup.Plain(body.substring(pos))
        return out
    }

    /** "Gen.1.1", "Prov.25.8-Prov.25.10", "Ps.23" (KJV numbering) -> Passage in Malagasy numbering. */
    fun osisToPassage(osis: String): Passage? {
        val first = osis.substringBefore('-')
        val last = osis.substringAfter('-', first)
        val p = first.split('.')
        val book = Canon.books.firstOrNull { it.osis == p[0] }?.id ?: return null
        val chapter = p.getOrNull(1)?.toIntOrNull() ?: return null
        val verse = p.getOrNull(2)?.toIntOrNull() ?: return runCatching { Passage(book, chapter) }.getOrNull()
        val endVerse = last.split('.').let { if (it.size >= 3 && it[1].toIntOrNull() == chapter) it[2].toIntOrNull() else null } ?: verse
        val a = study.kjvToMg(VerseRef(book, chapter, verse))
        val b = study.kjvToMg(VerseRef(book, chapter, endVerse))
        return runCatching { Passage(book, a.chapter, a.verse, maxOf(a.verse, b.verse)) }.getOrNull()
    }

    val places: List<Place> by lazy {
        db.rawQuery("SELECT id, name, type, lon, lat, confidence FROM place", null).use { c ->
            buildList { while (c.moveToNext()) add(Place(c.getString(0), c.getString(1), c.getString(2), c.getDouble(3), c.getDouble(4), c.getInt(5))) }
        }
    }

    /** Places mentioned in a chapter. */
    fun placesIn(book: Int, chapter: Int): List<Place> {
        val ids = db.rawQuery("SELECT DISTINCT place FROM place_verse WHERE book=? AND chapter=?", arrayOf(book.toString(), chapter.toString()))
            .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        return places.filter { it.id in ids }
    }

    /** Verses that mention a place, canonical order. */
    fun versesOf(placeId: String): List<Passage> = db.rawQuery(
        "SELECT book, chapter, verse FROM place_verse WHERE place=? ORDER BY book, chapter, verse", arrayOf(placeId),
    ).use { c -> buildList { while (c.moveToNext()) runCatching { add(Passage(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(2))) } } }
}
