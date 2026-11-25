package mg.baiboly.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import mg.baiboly.core.list.ListItem
import mg.baiboly.core.list.ServiceList
import mg.baiboly.core.ref.Passage
import org.json.JSONArray
import org.json.JSONObject

data class Note(val id: Long, val passage: Passage, val text: String, val createdAt: Long)

data class Highlight(
    val id: Long,
    val passage: Passage,
    /** Character range inside verse [Passage.verseStart]; null when whole verses are highlighted. */
    val charStart: Int?,
    val charEnd: Int?,
    val colour: Int,
    val createdAt: Long,
)

data class SavedListSummary(val id: Long, val title: String, val date: String, val createdAt: Long, val itemCount: Int)

data class RecentPassage(val passage: Passage, val openedAt: Long)

/**
 * A sermon note or a study. [passages]: the references as written ("Jaona 3: 16; Rom 8: 28"), kept in
 * Malagasy 1865 numbering by the app; [body]: the notes' Markdown (core.notes.NoteMarkup).
 */
data class NoteDocument(
    val id: Long,
    val kind: String,
    val title: String,
    val date: Long,
    val passages: String,
    val speaker: String,
    val place: String,
    val body: String,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * The user's own data (brief section 3), in plain SQLite. All verse numbers
 * are Malagasy 1865 numbers. Call from a background thread.
 */
class UserStore(context: Context) : SQLiteOpenHelper(context, "user.db", null, SCHEMA_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE service_list(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, date TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE list_item(list_id INTEGER NOT NULL REFERENCES service_list(id) ON DELETE CASCADE, position INTEGER NOT NULL, " +
                "book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse_start INTEGER NOT NULL, verse_end INTEGER NOT NULL, " +
                "is_side INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(list_id, position))"
        )
        db.execSQL(
            "CREATE TABLE recent_passage(book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse_start INTEGER NOT NULL, " +
                "verse_end INTEGER NOT NULL, opened_at INTEGER NOT NULL, PRIMARY KEY(book, chapter, verse_start, verse_end))"
        )
        db.execSQL("CREATE INDEX recent_by_time ON recent_passage(opened_at DESC)")
        db.execSQL(
            "CREATE TABLE note(id INTEGER PRIMARY KEY AUTOINCREMENT, book INTEGER NOT NULL, chapter INTEGER NOT NULL, " +
                "verse_start INTEGER NOT NULL, verse_end INTEGER NOT NULL, text TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE highlight(id INTEGER PRIMARY KEY AUTOINCREMENT, book INTEGER NOT NULL, chapter INTEGER NOT NULL, " +
                "verse_start INTEGER NOT NULL, verse_end INTEGER NOT NULL, char_start INTEGER, char_end INTEGER, " +
                "colour INTEGER NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX note_by_chapter ON note(book, chapter)")
        db.execSQL("CREATE INDEX highlight_by_chapter ON highlight(book, chapter)")
        createDocuments(db)
    }

    /**
     * App updates keep the user's data. Each schema change gets one numbered
     * step here, applied in order inside one transaction, and SCHEMA_VERSION
     * goes up by one. Steps only add or transform; they never drop user data.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (v in oldVersion + 1..newVersion) MIGRATIONS[v]?.invoke(db)
    }

    /** A newer app's database opened by an older app (downgrade): keep the data, refuse to guess. */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("user.db is from a newer version of Baiboly ($oldVersion > $newVersion); install the newer version")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    private val db: SQLiteDatabase get() = writableDatabase

    private fun Cursor.passage(i: Int) = Passage(getInt(i), getInt(i + 1), getInt(i + 2), getInt(i + 3))

    // ------------------------------------------------------------ service lists

    fun createList(title: String, date: String): ServiceList {
        val id = db.insert("service_list", null, ContentValues().apply {
            put("title", title); put("date", date); put("created_at", System.currentTimeMillis())
        })
        return ServiceList(id = id, title = title, date = date)
    }

    fun loadList(id: Long, current: Int = 0): ServiceList? {
        val head = db.rawQuery("SELECT title, date FROM service_list WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        } ?: return null
        val items = db.rawQuery(
            "SELECT book, chapter, verse_start, verse_end, is_side FROM list_item WHERE list_id=? ORDER BY position",
            arrayOf(id.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(ListItem(c.passage(0), c.getInt(4) != 0)) } }
        val cur = if (items.isEmpty()) -1 else current.coerceIn(0, items.lastIndex)
        return ServiceList(id, head.first, head.second, items, cur)
    }

    /** Autosave: replaces the stored items of the list with the current state. */
    fun saveList(list: ServiceList) {
        db.beginTransaction()
        try {
            db.update("service_list", ContentValues().apply { put("title", list.title); put("date", list.date) },
                "id=?", arrayOf(list.id.toString()))
            db.delete("list_item", "list_id=?", arrayOf(list.id.toString()))
            list.items.forEachIndexed { i, it ->
                db.insert("list_item", null, ContentValues().apply {
                    put("list_id", list.id); put("position", i)
                    put("book", it.passage.book); put("chapter", it.passage.chapter)
                    put("verse_start", it.passage.verseStart); put("verse_end", it.passage.verseEnd)
                    put("is_side", if (it.isSide) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun savedLists(): List<SavedListSummary> = db.rawQuery(
        "SELECT s.id, s.title, s.date, s.created_at, (SELECT COUNT(*) FROM list_item i WHERE i.list_id = s.id) " +
            "FROM service_list s ORDER BY s.created_at DESC",
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(SavedListSummary(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4))) } }

    fun deleteList(id: Long) {
        // SQLite foreign keys are off, so ON DELETE CASCADE does not run: remove the items as well
        db.beginTransaction()
        try {
            db.delete("list_item", "list_id=?", arrayOf(id.toString()))
            db.delete("service_list", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun duplicateList(id: Long, title: String, date: String): ServiceList? {
        val src = loadList(id) ?: return null
        val copy = createList(title, date).copy(items = src.items, current = if (src.items.isEmpty()) -1 else 0)
        saveList(copy)
        return copy
    }

    // ------------------------------------------------------------ recent passages / history

    fun touchRecent(p: Passage, at: Long = System.currentTimeMillis()) {
        db.insertWithOnConflict("recent_passage", null, ContentValues().apply {
            put("book", p.book); put("chapter", p.chapter); put("verse_start", p.verseStart); put("verse_end", p.verseEnd)
            put("opened_at", at)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        // keep the table small
        db.execSQL("DELETE FROM recent_passage WHERE opened_at < (SELECT opened_at FROM recent_passage ORDER BY opened_at DESC LIMIT 1 OFFSET 499)")
    }

    fun recent(limit: Int = 200): List<RecentPassage> = db.rawQuery(
        "SELECT book, chapter, verse_start, verse_end, opened_at FROM recent_passage ORDER BY opened_at DESC LIMIT ?",
        arrayOf(limit.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(RecentPassage(c.passage(0), c.getLong(4))) } }

    fun clearHistory() {
        db.delete("recent_passage", null, null)
    }

    /** How often each book was opened: drives the keypad book order. */
    fun bookUsage(): Map<Int, Int> = db.rawQuery("SELECT book, COUNT(*) FROM recent_passage GROUP BY book", null)
        .use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) } }

    // ------------------------------------------------------------ notes

    fun addNote(p: Passage, text: String): Long = db.insert("note", null, ContentValues().apply {
        put("book", p.book); put("chapter", p.chapter); put("verse_start", p.verseStart); put("verse_end", p.verseEnd)
        put("text", text); put("created_at", System.currentTimeMillis())
    })

    fun updateNote(id: Long, text: String) {
        db.update("note", ContentValues().apply { put("text", text) }, "id=?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long) {
        db.delete("note", "id=?", arrayOf(id.toString()))
    }

    fun notes(): List<Note> = db.rawQuery(
        "SELECT id, book, chapter, verse_start, verse_end, text, created_at FROM note ORDER BY created_at DESC", null,
    ).use { c -> buildList { while (c.moveToNext()) add(Note(c.getLong(0), c.passage(1), c.getString(5), c.getLong(6))) } }

    fun notesIn(book: Int, chapter: Int): List<Note> = db.rawQuery(
        "SELECT id, book, chapter, verse_start, verse_end, text, created_at FROM note WHERE book=? AND chapter=?",
        arrayOf(book.toString(), chapter.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(Note(c.getLong(0), c.passage(1), c.getString(5), c.getLong(6))) } }

    // ------------------------------------------------------------ highlights

    fun addHighlight(p: Passage, charStart: Int?, charEnd: Int?, colour: Int): Long =
        db.insert("highlight", null, ContentValues().apply {
            put("book", p.book); put("chapter", p.chapter); put("verse_start", p.verseStart); put("verse_end", p.verseEnd)
            if (charStart != null) put("char_start", charStart) else putNull("char_start")
            if (charEnd != null) put("char_end", charEnd) else putNull("char_end")
            put("colour", colour); put("created_at", System.currentTimeMillis())
        })

    fun deleteHighlight(id: Long) {
        db.delete("highlight", "id=?", arrayOf(id.toString()))
    }

    private fun Cursor.highlight() = Highlight(
        getLong(0), passage(1), if (isNull(5)) null else getInt(5), if (isNull(6)) null else getInt(6), getInt(7), getLong(8),
    )

    private val hlCols = "id, book, chapter, verse_start, verse_end, char_start, char_end, colour, created_at"

    fun highlights(): List<Highlight> = db.rawQuery("SELECT $hlCols FROM highlight ORDER BY created_at DESC", null)
        .use { c -> buildList { while (c.moveToNext()) add(c.highlight()) } }

    fun highlightsIn(book: Int, chapter: Int): List<Highlight> = db.rawQuery(
        "SELECT $hlCols FROM highlight WHERE book=? AND chapter=? ORDER BY created_at", arrayOf(book.toString(), chapter.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(c.highlight()) } }

    // ------------------------------------------------------------ backup (brief task 12)

    /** Every user table, row for row, as one JSON document. */
    fun exportJson(appState: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("format", "baiboly-backup")
        out.put("version", 1)
        out.put("exported_at", System.currentTimeMillis())
        out.put("app_state", appState)
        for (table in TABLES) {
            val rows = JSONArray()
            db.rawQuery("SELECT * FROM $table", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(c.getColumnName(i), JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(c.getColumnName(i), c.getLong(i))
                            else -> o.put(c.getColumnName(i), c.getString(i))
                        }
                    }
                    rows.put(o)
                }
            }
            out.put(table, rows)
        }
        return out
    }

    /** Replaces all user data with the backup's content, exactly. Returns the saved app state. */
    fun importJson(json: JSONObject): JSONObject {
        require(json.optString("format") == "baiboly-backup") { "Not a Baiboly backup file" }
        db.beginTransaction()
        try {
            for (table in TABLES.reversed()) db.delete(table, null, null)
            db.execSQL("DELETE FROM sqlite_sequence")
            for (table in TABLES) {
                val rows = json.optJSONArray(table) ?: continue
                for (i in 0 until rows.length()) {
                    val o = rows.getJSONObject(i)
                    val cv = ContentValues()
                    for (k in o.keys()) {
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Number -> cv.put(k, v.toLong())
                            else -> cv.put(k, v.toString())
                        }
                    }
                    db.insertOrThrow(table, null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return json.optJSONObject("app_state") ?: JSONObject()
    }

    // ------------------------------------------------------------ sermon notes and studies

    private fun Cursor.document() = NoteDocument(
        getLong(0), getString(1), getString(2), getLong(3), getString(4), getString(5), getString(6), getString(7), getString(8), getLong(9), getLong(10),
    )

    private val docCols = "id, kind, title, date, passages, speaker, place, body, tags, created_at, updated_at"

    fun documents(kind: String? = null): List<NoteDocument> = db.rawQuery(
        "SELECT $docCols FROM document" + (if (kind != null) " WHERE kind=?" else "") + " ORDER BY date DESC, updated_at DESC",
        if (kind != null) arrayOf(kind) else null,
    ).use { c -> buildList { while (c.moveToNext()) add(c.document()) } }

    fun document(id: Long): NoteDocument? = db.rawQuery("SELECT $docCols FROM document WHERE id=?", arrayOf(id.toString()))
        .use { c -> if (c.moveToFirst()) c.document() else null }

    /** Inserts ([NoteDocument.id] == 0) or updates; returns the id. */
    fun saveDocument(d: NoteDocument): Long {
        val now = System.currentTimeMillis()
        val v = ContentValues().apply {
            put("kind", d.kind); put("title", d.title); put("date", d.date); put("passages", d.passages); put("speaker", d.speaker)
            put("place", d.place); put("body", d.body); put("tags", d.tags); put("updated_at", now)
        }
        return if (d.id == 0L) {
            v.put("created_at", now)
            db.insert("document", null, v)
        } else {
            db.update("document", v, "id=?", arrayOf(d.id.toString()))
            d.id
        }
    }

    fun deleteDocument(id: Long) {
        db.delete("document", "id=?", arrayOf(id.toString()))
    }

    companion object {
        const val SCHEMA_VERSION = 2

        /** Sermon notes (kind "sermon", Church mode) and studies (kind "study", Study mode). */
        private fun createDocuments(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE document(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, date INTEGER NOT NULL, " +
                    "passages TEXT NOT NULL, speaker TEXT NOT NULL, place TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX document_by_date ON document(kind, date DESC)")
        }

        /** schema version -> step that brings the previous version up to it. */
        private val MIGRATIONS: Map<Int, (SQLiteDatabase) -> Unit> = mapOf(
            2 to ::createDocuments,
        )

        /** Parent tables first. */
        val TABLES = listOf("service_list", "list_item", "recent_passage", "note", "highlight", "document")
    }
}
