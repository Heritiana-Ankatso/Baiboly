package mg.baiboly.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.list.ServiceList
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.ChapterText
import mg.baiboly.data.Highlight
import mg.baiboly.data.LexiconEntry
import mg.baiboly.data.Note
import mg.baiboly.data.RecentPassage
import mg.baiboly.data.SavedListSummary
import mg.baiboly.data.SearchHit
import mg.baiboly.data.StudyRepository
import mg.baiboly.data.Translation
import mg.baiboly.data.UserStore
import mg.baiboly.data.VerseRef
import mg.baiboly.data.VerseStudy
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the reader is: the chapter shown and the verses to scroll to and mark. */
data class ReaderLocation(val book: Int, val chapter: Int, val focus: Passage?)

enum class Screen { Main, History, Notes, Highlights, Search, Lists, Settings, About, Backup }

enum class ThemeMode { System, Light, Dark }

/**
 * App state. The service list, the reader position and the settings are
 * restored on start (the app opens where the user left off) and saved on
 * every change. All database work runs on one background thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    private val dbs = AssetDatabases(app)
    val bible = BibleRepository(dbs)
    val study = StudyRepository(dbs)
    private val user = UserStore(app)
    private val io = Dispatchers.IO.limitedParallelism(1)

    // ------------------------------------------------------------ settings
    var translationId by mutableStateOf(prefs.getString("translation", BibleRepository.PRIMARY)!!)
        private set
    var textSize by mutableStateOf(prefs.getFloat("text_size", 20f))
        private set
    var theme by mutableStateOf(ThemeMode.entries[prefs.getInt("theme", 0)])
        private set
    var studyMode by mutableStateOf(prefs.getBoolean("alpha", false))
        private set
    var translations by mutableStateOf<List<Translation>>(emptyList())
        private set

    // ------------------------------------------------------------ list + reader
    var list by mutableStateOf(ServiceList())
        private set
    var location by mutableStateOf(
        ReaderLocation(prefs.getInt("loc_book", 43), prefs.getInt("loc_chapter", 1), restoreFocus())
    )
        private set
    var chapter by mutableStateOf<ChapterText?>(null)
        private set
    var chapterHighlights by mutableStateOf<List<Highlight>>(emptyList())
        private set
    var chapterNotes by mutableStateOf<List<Note>>(emptyList())
        private set

    /** Incremented whenever the reader must scroll to [ReaderLocation.focus]. */
    var scrollRequest by mutableIntStateOf(0)
        private set
    var screen by mutableStateOf(Screen.Main)
    var ready by mutableStateOf(false)
        private set

    var recent by mutableStateOf<List<Passage>>(emptyList())
        private set
    var bookUsage by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set

    /** One-line feedback shown at the bottom of the screen. */
    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            withContext(io) {
                val ts = bible.translations
                val listId = prefs.getLong("list_id", -1)
                val restored = if (listId > 0) user.loadList(listId, prefs.getInt("list_current", 0)) else null
                val l = restored ?: user.createList(defaultTitle(), today())
                val rec = user.recent()
                val usage = user.bookUsage()
                withContext(Dispatchers.Main) {
                    translations = ts
                    if (ts.none { it.id == translationId }) translationId = BibleRepository.PRIMARY
                    list = l
                    recent = rec.map { it.passage }
                    bookUsage = usage
                }
            }
            persistState()
            loadChapter()
            ready = true
        }
    }

    private fun restoreFocus(): Passage? {
        val b = prefs.getInt("focus_book", 0)
        if (b == 0) return null
        return runCatching {
            Passage(b, prefs.getInt("focus_chapter", 1), prefs.getInt("focus_start", 0), prefs.getInt("focus_end", 0))
        }.getOrNull()
    }

    /** A string in the interface language chosen in the app (not only the phone's). */
    fun str(id: Int, vararg args: Any): String = UiLanguage.wrap(getApplication()).getString(id, *args)

    var uiLanguage by mutableStateOf(prefs.getString(UiLanguage.KEY, "") ?: "")
        private set

    /** Saves the interface language; the UI switches at once. */
    fun chooseUiLanguage(lang: String) {
        uiLanguage = lang
        prefs.edit().putString(UiLanguage.KEY, lang).commit()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private fun defaultTitle(): String =
        str(R.string.default_list_title, today())

    private fun persistState() {
        val f = location.focus
        prefs.edit()
            .putString("translation", translationId)
            .putFloat("text_size", textSize)
            .putInt("theme", theme.ordinal)
            .putBoolean("alpha", studyMode)
            .putLong("list_id", list.id)
            .putInt("list_current", list.current)
            .putInt("loc_book", location.book)
            .putInt("loc_chapter", location.chapter)
            .putInt("focus_book", f?.book ?: 0)
            .putInt("focus_chapter", f?.chapter ?: 0)
            .putInt("focus_start", f?.verseStart ?: 0)
            .putInt("focus_end", f?.verseEnd ?: 0)
            .apply()
    }

    private fun appStateJson(): JSONObject = JSONObject().apply {
        prefs.all.forEach { (k, v) -> put(k, v) }
    }

    private fun restoreAppState(o: JSONObject) {
        val e = prefs.edit().clear()
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is String -> e.putString(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is Float -> e.putFloat(k, v)
                is Int -> if (k == "list_id") e.putLong(k, v.toLong()) else e.putInt(k, v)
                is Long -> if (k == "list_id") e.putLong(k, v) else e.putInt(k, v.toInt())
            }
        }
        e.commit()
    }

    // ------------------------------------------------------------ reader

    private var loadJob: Job? = null

    private fun loadChapter() {
        val loc = location
        val tid = translationId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (text, hl, notes) = withContext(io) {
                Triple(bible.chapter(tid, loc.book, loc.chapter), user.highlightsIn(loc.book, loc.chapter), user.notesIn(loc.book, loc.chapter))
            }
            chapter = text
            chapterHighlights = hl
            chapterNotes = notes
            scrollRequest++
        }
    }

    private fun refreshAnnotations() {
        val loc = location
        viewModelScope.launch {
            val (hl, notes) = withContext(io) { user.highlightsIn(loc.book, loc.chapter) to user.notesIn(loc.book, loc.chapter) }
            chapterHighlights = hl
            chapterNotes = notes
        }
    }

    private fun go(loc: ReaderLocation, remember: Passage?) {
        val sameChapter = chapter?.let { it.book == loc.book && it.chapter == loc.chapter && it.translation == translationId } == true
        location = loc
        persistState()
        if (sameChapter) scrollRequest++ else loadChapter()
        if (remember != null) touchRecent(remember)
    }

    private fun touchRecent(p: Passage) {
        recent = (listOf(p) + recent.filter { it != p }).take(200)
        viewModelScope.launch(io) {
            user.touchRecent(p)
            val usage = user.bookUsage()
            withContext(Dispatchers.Main) { bookUsage = usage }
        }
    }

    /** Opens a passage that is not the current list item (Browse, search, history…): Back to my place appears. */
    fun openPassage(p: Passage) {
        go(ReaderLocation(p.book, p.chapter, p), p)
        screen = Screen.Main
    }

    fun openChapter(book: Int, chapter: Int) = openPassage(Passage(book, chapter))

    fun nextChapter() {
        Canon.nextChapter(location.book, location.chapter)?.let { (b, c) -> go(ReaderLocation(b, c, null), null) }
    }

    fun previousChapter() {
        Canon.previousChapter(location.book, location.chapter)?.let { (b, c) -> go(ReaderLocation(b, c, null), null) }
    }

    /** True when the reader is not showing the current list item: show Back to my place. */
    val isAway: Boolean
        get() {
            val item = list.currentItem ?: return false
            return location.focus != item.passage || location.book != item.passage.book || location.chapter != item.passage.chapter
        }

    fun backToMyPlace() {
        list.currentItem?.let { go(ReaderLocation(it.passage.book, it.passage.chapter, it.passage), null) }
    }

    /** Swipe: between list items when a list is active, otherwise between chapters. */
    fun swipe(forward: Boolean) {
        if (!list.isEmpty) {
            if (isAway) {
                // first swipe while away returns to the list rather than skipping an item
                backToMyPlace(); return
            }
            if (forward) selectItem(list.current + 1) else selectItem(list.current - 1)
        } else {
            if (forward) nextChapter() else previousChapter()
        }
    }

    fun setTranslation(id: String) {
        translationId = id
        persistState()
        loadChapter()
    }

    fun changeTextSize(sp: Float) {
        textSize = sp.coerceIn(14f, 40f)
        persistState()
    }

    fun changeTheme(t: ThemeMode) {
        theme = t
        persistState()
    }

    fun toggleStudyMode() {
        studyMode = !studyMode
        persistState()
    }

    // ------------------------------------------------------------ service list

    private fun updateList(l: ServiceList) {
        list = l
        persistState()
        viewModelScope.launch(io) { user.saveList(l) }
    }

    fun selectItem(index: Int) {
        if (index !in list.items.indices) return
        updateList(list.select(index))
        val p = list.items[index].passage
        go(ReaderLocation(p.book, p.chapter, p), p)
    }

    fun addToList(passages: List<Passage>, openFirst: Boolean = false) {
        if (passages.isEmpty()) return
        val wasEmpty = list.isEmpty
        val start = list.items.size
        updateList(list.add(passages))
        passages.forEach { touchRecent(it) }
        if (wasEmpty || openFirst) selectItem(if (wasEmpty) 0 else start)
    }

    fun toggleSide(i: Int) = updateList(list.toggleSide(i))
    fun moveEarlier(i: Int) = updateList(list.moveEarlier(i))
    fun moveLater(i: Int) = updateList(list.moveLater(i))
    fun moveItem(from: Int, to: Int) = updateList(list.move(from, to))
    fun deleteItem(i: Int) {
        val wasCurrent = i == list.current
        updateList(list.delete(i))
        if (wasCurrent) list.currentItem?.let { val p = it.passage; go(ReaderLocation(p.book, p.chapter, p), null) }
    }

    fun renameList(title: String) = updateList(list.copy(title = title))

    fun newList() {
        viewModelScope.launch {
            val l = withContext(io) { user.createList(defaultTitle(), today()) }
            list = l
            persistState()
            refreshSavedLists()
        }
    }

    fun openSavedList(id: Long) {
        viewModelScope.launch {
            val l = withContext(io) { user.loadList(id, 0) } ?: return@launch
            list = l
            persistState()
            screen = Screen.Main
            l.currentItem?.let { val p = it.passage; go(ReaderLocation(p.book, p.chapter, p), null) }
        }
    }

    fun duplicateSavedList(id: Long, title: String) {
        viewModelScope.launch {
            withContext(io) { user.duplicateList(id, str(R.string.copy_suffix, title), today()) }
            refreshSavedLists()
        }
    }

    fun deleteSavedList(id: Long) {
        viewModelScope.launch {
            withContext(io) { user.deleteList(id) }
            if (id == list.id) {
                val l = withContext(io) { user.createList(defaultTitle(), today()) }
                list = l
                persistState()
            }
            refreshSavedLists()
        }
    }

    var savedLists by mutableStateOf<List<SavedListSummary>>(emptyList())
        private set

    fun refreshSavedLists() {
        viewModelScope.launch { savedLists = withContext(io) { user.savedLists() } }
    }

    // ------------------------------------------------------------ history, notes, highlights

    var history by mutableStateOf<List<RecentPassage>>(emptyList())
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var highlights by mutableStateOf<List<Highlight>>(emptyList())
        private set

    fun refreshHistory() {
        viewModelScope.launch { history = withContext(io) { user.recent(500) } }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(io) { user.clearHistory() }
            recent = emptyList()
            bookUsage = emptyMap()
            refreshHistory()
        }
    }

    fun refreshNotes() {
        viewModelScope.launch { notes = withContext(io) { user.notes() } }
    }

    fun refreshHighlights() {
        viewModelScope.launch { highlights = withContext(io) { user.highlights() } }
    }

    fun addNote(p: Passage, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            withContext(io) { user.addNote(p, text.trim()) }
            refreshAnnotations(); refreshNotes()
        }
    }

    fun updateNote(id: Long, text: String) {
        viewModelScope.launch {
            withContext(io) { if (text.isBlank()) user.deleteNote(id) else user.updateNote(id, text.trim()) }
            refreshAnnotations(); refreshNotes()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            withContext(io) { user.deleteNote(id) }
            refreshAnnotations(); refreshNotes()
        }
    }

    /** Highlights whole verses (one row per contiguous range) or a character range of one verse. */
    fun addHighlight(passages: List<Passage>, colour: Int, charRange: IntRange? = null) {
        viewModelScope.launch {
            withContext(io) {
                for (p in passages) user.addHighlight(p, charRange?.first, charRange?.let { it.last + 1 }, colour)
            }
            refreshAnnotations(); refreshHighlights()
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch {
            withContext(io) { user.deleteHighlight(id) }
            refreshAnnotations(); refreshHighlights()
        }
    }

    /** Removes every highlight touching the given verses of the shown chapter. */
    fun removeHighlights(verses: Set<Int>) {
        val ids = chapterHighlights.filter { h -> verses.any { v -> h.passage.contains(v) } }.map { it.id }
        viewModelScope.launch {
            withContext(io) { ids.forEach { user.deleteHighlight(it) } }
            refreshAnnotations(); refreshHighlights()
        }
    }

    // ------------------------------------------------------------ search

    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searchTranslation by mutableStateOf(BibleRepository.PRIMARY)
    private var searchJob: Job? = null
    val searchLimit = 500

    fun search(q: String, translation: String = searchTranslation) {
        searchQuery = q
        searchTranslation = translation
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            searchResults = if (q.isBlank()) emptyList() else withContext(io) { bible.search(translation, q, searchLimit) }
        }
    }

    // ------------------------------------------------------------ study (α)

    suspend fun studyVerse(book: Int, chapter: Int, verse: Int): VerseStudy =
        withContext(io) { study.verse(VerseRef(book, chapter, verse)) }

    suspend fun lexicon(strongs: String): Pair<LexiconEntry?, Int> =
        withContext(io) { study.entry(strongs) to study.verseCount(strongs) }

    /** Found N verses: references with their Malagasy text. */
    suspend fun versesWith(strongs: String): List<Pair<VerseRef, String>> = withContext(io) {
        study.versesWith(strongs).map { r ->
            r to (bible.verses(BibleRepository.PRIMARY, r.book, r.chapter, r.verse, r.verse).firstOrNull()?.text ?: "")
        }
    }

    /** Opens a study result in the Malagasy text, with Back to my place. */
    fun openInMalagasy(r: VerseRef) {
        if (translationId != BibleRepository.PRIMARY) {
            translationId = BibleRepository.PRIMARY
            chapter = null
        }
        openPassage(Passage(r.book, r.chapter, r.verse, r.verse))
    }

    suspend fun versesText(p: Passage, translation: String = translationId): String = withContext(io) {
        bible.verses(translation, p.book, p.chapter, p.verseStart, p.verseEnd)
            .joinToString(" ") { if (p.verseStart != p.verseEnd || p.isWholeChapter) "${it.verse} ${it.text}" else it.text }
    }

    fun chapterTitle(): String {
        val lang = bible.translation(translationId).lang
        return Canon.book(location.book).name(lang) + " " + location.chapter
    }

    // ------------------------------------------------------------ backup

    fun exportBackup(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(io) {
                runCatching {
                    val json = user.exportJson(appStateJson())
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(1).toByteArray()) }
                }
            }
            message = ok.fold({ str(R.string.backup_saved) }, { str(R.string.backup_failed, it.message ?: "") })
        }
    }

    fun importBackup(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(io) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    user.importJson(JSONObject(text))
                }
            }
            result.onSuccess { state ->
                restoreAppState(state)
                reloadFromPrefs()
                message = str(R.string.backup_restored)
            }.onFailure { message = str(R.string.backup_failed, it.message ?: "") }
        }
    }

    private suspend fun reloadFromPrefs() {
        translationId = prefs.getString("translation", BibleRepository.PRIMARY)!!
        textSize = prefs.getFloat("text_size", 20f)
        theme = ThemeMode.entries[prefs.getInt("theme", 0).coerceIn(0, 2)]
        studyMode = prefs.getBoolean("alpha", false)
        val (l, rec, usage) = withContext(io) {
            val id = prefs.getLong("list_id", -1)
            val l = user.loadList(id, prefs.getInt("list_current", 0)) ?: user.createList(defaultTitle(), today())
            Triple(l, user.recent(), user.bookUsage())
        }
        list = l
        recent = rec.map { it.passage }
        bookUsage = usage
        location = ReaderLocation(prefs.getInt("loc_book", 43), prefs.getInt("loc_chapter", 1), restoreFocus())
        persistState()
        loadChapter()
        refreshHistory(); refreshNotes(); refreshHighlights(); refreshSavedLists()
    }
}
