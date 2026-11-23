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
import mg.baiboly.data.DictSource
import mg.baiboly.data.Highlight
import mg.baiboly.data.LibraryRepository
import mg.baiboly.app.audio.ReadAloud
import mg.baiboly.app.audio.ReadAloudState
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

enum class Screen { Main, History, Notes, Highlights, Search, Lists, Settings, About, Backup, Dictionary, Map, Books, Library, Menu }

/**
 * How the app is used right now. Each mode has its own navigation:
 * Casual reads and searches simply; Church is silent and moves between the
 * announced passages; Study adds original words, cross-references,
 * dictionaries and maps.
 */
enum class AppMode { Casual, Church, Study }

/** Size of the interface (bars, buttons, lists), not of the Bible text: Compact fits more on the screen. */
enum class UiDensity(val scale: Float) { Compact(0.88f), Normal(1f) }

/** Smallest Bible text size, in sp. */
const val MIN_TEXT = 10f

/** The tools of the Dictionary page besides the dictionaries. */
enum class StudyTool { Units, People, Timeline, Confessions }

/** Which Bibles the menus offer: Protestant (66 books) or also the Catholic Bibles (73). */
enum class Tradition { Protestant, Catholic }

enum class ThemeMode { System, Light, Dark }

/** Colour themes, each with a day and a night version. Stored by name, so the order may change. */
enum class Palette { Vellum, Lapis, Olive, Purple, Sepia, Paper, Slate }

/**
 * Themes: each is a whole design of its own (layout, navigation, controls,
 * how actions and settings are grouped), with the colours that suit it.
 */
enum class Style(val palettes: List<Palette>, val font: TextFont, val engraved: Boolean) {
    Manuscript(listOf(Palette.Vellum, Palette.Purple, Palette.Olive), TextFont.Classic, true),
    Modern(listOf(Palette.Slate, Palette.Paper), TextFont.Sans, false),
    Page(listOf(Palette.Sepia, Palette.Paper), TextFont.Modern, false),
    Glass(listOf(Palette.Lapis, Palette.Purple, Palette.Olive), TextFont.Classic, true),
}

/** Typeface for the Bible text: Gentium (bundled), the phone's serif, or the phone's sans-serif. */
enum class TextFont { Classic, Modern, Sans }

enum class LineSpacing { Compact, Normal, Airy }

/** How verses are set on the page: one per line, or as running paragraphs. */
enum class VerseLayout { Lines, Paragraphs }

/** What separates paragraphs in running text: a first-line indent, or a blank space. */
enum class ParagraphBreak { Indent, Space }

/** Side margins of running text. */
enum class PageMargin { Narrow, Normal, Wide }

/**
 * App state. The service list, the reader position and the settings are
 * restored on start (the app opens where the user left off) and saved on
 * every change. All database work runs on one background thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** Common words left out when looking for dictionary entries named in a verse. */
private val STOP_WORDS = setOf(
    "the", "and", "that", "this", "these", "those", "with", "unto", "upon", "into", "from", "for", "not", "but", "his", "her", "him",
    "them", "they", "their", "thee", "thou", "thy", "thine", "you", "your", "shall", "will", "was", "were", "are", "have", "had", "hath",
    "been", "which", "what", "when", "then", "there", "here", "all", "also", "said", "saith", "say", "came", "come", "went", "out", "one",
    "les", "des", "une", "est", "que", "qui", "dans", "pour", "par", "sur", "avec", "ses", "son", "leur", "leurs", "vous", "nous", "ils",
    "elle", "elles", "mais", "comme", "tout", "tous", "fut", "été", "sont", "était", "dit", "aux", "pas", "plus",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    private val dbs = AssetDatabases(app)
    val bible = BibleRepository(dbs)
    val study = StudyRepository(dbs)
    val library = LibraryRepository(dbs, study)
    val chronicle = mg.baiboly.data.HistoryRepository(dbs)
    private val user = UserStore(app)
    private val io = Dispatchers.IO.limitedParallelism(1)

    // ------------------------------------------------------------ settings
    /** The Bible the app opens with; changed in one tap from the reading-text menu or Settings. */
    var defaultTranslationId by mutableStateOf(prefs.getString("default_translation", BibleRepository.PRIMARY)!!)
        private set

    /** The text being read now: starts as the default, can be switched for a while without changing the default. */
    var translationId by mutableStateOf(defaultTranslationId)
        private set

    /** Chapters per book in the current text's own numbering. */
    private var chapterCounts by mutableStateOf<Map<Int, Int>>(emptyMap())

    fun chapterCount(book: Int): Int = chapterCounts[book] ?: Canon.chapterCount(book)
    var textSize by mutableStateOf(prefs.getFloat("text_size", 20f))
        private set
    var theme by mutableStateOf(ThemeMode.entries[prefs.getInt("theme", 0)])
        private set
    var style by mutableStateOf(enumPref("style", Style.Manuscript))
        private set
    var palette by mutableStateOf(enumPref("palette", Palette.Vellum))
        private set
    var textFont by mutableStateOf(enumPref("text_font", TextFont.Classic))
        private set
    var engravedTitles by mutableStateOf(prefs.getBoolean("engraved_titles", true))
        private set
    var lineSpacing by mutableStateOf(enumPref("line_spacing", LineSpacing.Normal))
        private set
    var verseLayout by mutableStateOf(enumPref("verse_layout", VerseLayout.Lines))
        private set

    // typesetting details (Settings, Advanced)
    var justify by mutableStateOf(prefs.getBoolean("text_justify", true))
        private set
    var hyphenate by mutableStateOf(prefs.getBoolean("text_hyphenate", true))
        private set
    var paragraphBreak by mutableStateOf(enumPref("paragraph_break", ParagraphBreak.Indent))
        private set
    var pageMargin by mutableStateOf(enumPref("page_margin", PageMargin.Normal))
        private set

    /** Interface size (Settings › Appearance); compact by default. */
    var uiDensity by mutableStateOf(enumPref("ui_density", UiDensity.Compact))
        private set

    fun changeUiDensity(d: UiDensity) {
        uiDensity = d
        prefs.edit().putString("ui_density", d.name).apply()
    }

    /** The screen stays awake while the app is open (on by default; always in Church mode). */
    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_screen_on", true))
        private set

    fun changeKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefs.edit().putBoolean("keep_screen_on", on).apply()
    }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    /** A new theme brings its own colours and type; the reader can then adjust them. */
    fun changeStyle(st: Style) {
        style = st
        prefs.edit().putString("style", st.name).apply()
        changePalette(st.palettes.first()); changeTextFont(st.font); changeEngravedTitles(st.engraved)
    }

    fun changePalette(p: Palette) { palette = p; prefs.edit().putString("palette", p.name).apply() }
    fun changeTextFont(f: TextFont) { textFont = f; prefs.edit().putString("text_font", f.name).apply() }
    fun changeEngravedTitles(on: Boolean) { engravedTitles = on; prefs.edit().putBoolean("engraved_titles", on).apply() }
    fun changeLineSpacing(l: LineSpacing) { lineSpacing = l; prefs.edit().putString("line_spacing", l.name).apply() }
    fun changeVerseLayout(l: VerseLayout) { verseLayout = l; prefs.edit().putString("verse_layout", l.name).apply() }
    fun changeJustify(on: Boolean) { justify = on; prefs.edit().putBoolean("text_justify", on).apply() }
    fun changeHyphenate(on: Boolean) { hyphenate = on; prefs.edit().putBoolean("text_hyphenate", on).apply() }
    fun changeParagraphBreak(b: ParagraphBreak) { paragraphBreak = b; prefs.edit().putString("paragraph_break", b.name).apply() }
    fun changePageMargin(m: PageMargin) { pageMargin = m; prefs.edit().putString("page_margin", m.name).apply() }

    /** Back to the look the app ships with (text size included). */
    fun resetAppearance() {
        changeStyle(Style.Manuscript); changeLineSpacing(LineSpacing.Normal); changeVerseLayout(VerseLayout.Lines); changeTextSize(20f)
        changeJustify(true); changeHyphenate(true); changeParagraphBreak(ParagraphBreak.Indent); changePageMargin(PageMargin.Normal)
    }
    var mode by mutableStateOf(AppMode.entries[prefs.getInt("mode", 0).coerceIn(0, 2)])
        private set

    var tradition by mutableStateOf(Tradition.entries[prefs.getInt("tradition", 0).coerceIn(0, 1)])
        private set

    /** Bibles offered in the menus for the chosen tradition. */
    val visibleTranslations: List<Translation>
        get() = if (tradition == Tradition.Catholic) translations else translations.filter { !it.catholic }

    fun changeTradition(t: Tradition) {
        tradition = t
        prefs.edit().putInt("tradition", t.ordinal).apply()
        if (t == Tradition.Protestant) {
            val catholic = translations.filter { it.catholic }.map { it.id }.toSet()
            if (defaultTranslationId in catholic) setDefaultTranslation(BibleRepository.PRIMARY)
            if (translationId in catholic) setTranslation(defaultTranslationId)
            if (searchTranslation in catholic) searchTranslation = defaultTranslationId
        }
    }

    /** Original-language tools (verse number -> study panel) belong to Study mode. */
    val studyMode: Boolean get() = mode == AppMode.Study

    fun changeMode(m: AppMode) {
        mode = m
        prefs.edit().putInt("mode", m.ordinal).apply()
        // Church mode is silent: no reading aloud.
        if (m == AppMode.Church) stopReading()
        screen = Screen.Main
    }
    var translations by mutableStateOf<List<Translation>>(emptyList())
        private set

    /** Reading aloud is off until the user switches it on in Settings (the app is silent by default). */
    var readAloudEnabled by mutableStateOf(prefs.getBoolean("read_aloud", false))
        private set
    var readRate by mutableStateOf(prefs.getFloat("read_rate", 1f))
        private set
    var readState by mutableStateOf(ReadAloudState())
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
        // Reading-aloud problems (e.g. no Malagasy voice on the phone) are shown as messages.
        viewModelScope.launch {
            var seen = ReadAloud.errors.value?.first
            ReadAloud.errors.collect { e -> if (e != null && e.first != seen) { seen = e.first; message = e.second } }
        }
        // Follow the reading: highlight the verse, turn to the next chapter with it.
        viewModelScope.launch {
            ReadAloud.state.collect { s ->
                readState = s
                if (s.active && s.book > 0 && (s.book != location.book || s.chapter != location.chapter)) {
                    if (s.translation != translationId) translationId = s.translation
                    go(ReaderLocation(s.book, s.chapter, null), null)
                }
            }
        }
        viewModelScope.launch {
            // Resource strings are read on the main thread, never inside the io blocks.
            val newTitle = defaultTitle()
            withContext(io) {
                val ts = bible.translations
                val listId = prefs.getLong("list_id", -1)
                val restored = if (listId > 0) user.loadList(listId, prefs.getInt("list_current", 0)) else null
                val l = restored ?: user.createList(newTitle, today())
                val rec = user.recent()
                val usage = user.bookUsage()
                withContext(Dispatchers.Main) {
                    translations = ts
                    if (ts.none { it.id == defaultTranslationId }) defaultTranslationId = BibleRepository.PRIMARY
                    if (ts.none { it.id == translationId }) translationId = defaultTranslationId
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

    var uiLanguage by mutableStateOf(prefs.getString(UiLanguage.KEY, UiLanguage.DEFAULT) ?: UiLanguage.DEFAULT)
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
            .putString("default_translation", defaultTranslationId)
            .putFloat("text_size", textSize)
            .putInt("theme", theme.ordinal)
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
        var tid = translationId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var counts = withContext(io) { bible.chapterCounts(tid) }
            if (counts.isNotEmpty() && location.book !in counts) {
                // e.g. Tobit while reading a 66-book Bible: show it in a Catholic Bible,
                // in the same language when there is one, and say so.
                val current = bible.translation(tid)
                val other = translations.filter { it.catholic }.let { cs -> cs.firstOrNull { it.lang == current.lang } ?: cs.firstOrNull() }
                if (other != null) {
                    tid = other.id
                    translationId = other.id
                    counts = withContext(io) { bible.chapterCounts(other.id) }
                    message = str(R.string.shown_in, other.name)
                }
            }
            chapterCounts = counts
            // e.g. Malachi 4 exists in the KJV numbering but not in the Malagasy one
            val max = counts[location.book] ?: Canon.chapterCount(location.book)
            if (location.chapter > max) {
                location = ReaderLocation(location.book, max, null)
                persistState()
            }
            val loc = location
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
        // a jump (a reference, a search result, Found N verses…): Back to my place is offered
        jumpedFromList = true
        go(ReaderLocation(p.book, p.chapter, p), p)
        screen = Screen.Main
    }

    /** A chapter chosen in Books: reading elsewhere on purpose, so Study mode drops Back to my place. */
    fun openChapter(book: Int, chapter: Int) {
        openPassage(Passage(book, chapter))
        jumpedFromList = false
    }

    /** Next chapter in reading order (Catholic order when the text has the deuterocanonical books). */
    fun nextChapter() {
        jumpedFromList = false
        Canon.nextChapter(location.book, location.chapter, chapterCounts.ifEmpty { null })
            ?.let { (b, c) -> go(ReaderLocation(b, c, null), null) }
    }

    fun previousChapter() {
        jumpedFromList = false
        Canon.previousChapter(location.book, location.chapter, chapterCounts.ifEmpty { null })
            ?.let { (b, c) -> go(ReaderLocation(b, c, null), null) }
    }

    /** Books in the text being read, in reading order (66, or 73 for a Catholic Bible). */
    val availableBooks: List<Int>
        get() = Canon.order(chapterCounts.keys.ifEmpty { null })
            .filter { tradition == Tradition.Catholic || !Canon.book(it).isDeuterocanonical }

    /** True when the reader is not showing the current list item: show Back to my place. */
    val isAway: Boolean
        get() {
            val item = list.currentItem ?: return false
            return location.focus != item.passage || location.book != item.passage.book || location.chapter != item.passage.chapter
        }

    /**
     * Set when the reader jumps away from the list passage; cleared when they go back, or read on
     * elsewhere by choice (Books, previous / next chapter). Not saved: a restart clears it.
     */
    var jumpedFromList by mutableStateOf(false)
        private set

    /**
     * Back to my place: in Church mode whenever the reader is away from the list passage; in Study
     * mode only just after a jump from it. (Study mode used to show it whenever a list had a current
     * passage, so after a service it stayed on every page.)
     */
    val showBackToMyPlace: Boolean
        get() = isAway && (mode == AppMode.Church || (mode == AppMode.Study && jumpedFromList))

    fun backToMyPlace() {
        jumpedFromList = false
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

    /** Makes [id] the Bible the app opens with, and switches to it now. */
    fun setDefaultTranslation(id: String) {
        defaultTranslationId = id
        searchTranslation = id
        setTranslation(id)
    }

    fun changeTextSize(sp: Float) {
        textSize = sp.coerceIn(MIN_TEXT, 40f)
        persistState()
    }

    fun changeTheme(t: ThemeMode) {
        theme = t
        persistState()
    }

    fun enableReadAloud(on: Boolean) {
        readAloudEnabled = on
        prefs.edit().putBoolean("read_aloud", on).apply()
        if (!on) stopReading()
    }

    // ------------------------------------------------------------ updates (the only network use)

    /** Where the update stands, shown in Settings › Advanced › Updates. */
    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data object UpToDate : UpdateStatus
        data object Failed : UpdateStatus
        data class Available(val release: mg.baiboly.app.update.Release) : UpdateStatus
        /** Downloading the new version; [progress] from 0 to 1 (negative while the size is unknown). */
        data class Downloading(val release: mg.baiboly.app.update.Release, val progress: Float) : UpdateStatus
        /** Android must first allow this app to install updates (once, in a system page). */
        data class NeedsPermission(val release: mg.baiboly.app.update.Release, val apk: java.io.File) : UpdateStatus
        /** Handed to Android's installer; its confirmation is shown over the app. */
        data class Installing(val release: mg.baiboly.app.update.Release, val apk: java.io.File) : UpdateStatus
        /** The download or the installation did not succeed; [reason] is a short technical cause. */
        data class InstallFailed(val release: mg.baiboly.app.update.Release, val reason: String?, val apk: java.io.File? = null) : UpdateStatus
    }

    var updateStatus by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
    var updateAuto by mutableStateOf(mg.baiboly.app.update.UpdateCheck.isAuto(app))
        private set
    var updateLastCheck by mutableStateOf(mg.baiboly.app.update.UpdateCheck.lastCheck(app))
        private set

    fun changeUpdateAuto(on: Boolean) {
        updateAuto = on
        mg.baiboly.app.update.UpdateCheck.setAuto(getApplication(), on)
        if (on) autoCheckUpdates()
    }

    /** "Check now": only ever on the user's tap. */
    fun checkForUpdates() {
        if (updateStatus == UpdateStatus.Checking || updateStatus is UpdateStatus.Downloading) return
        updateStatus = UpdateStatus.Checking
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { mg.baiboly.app.update.UpdateCheck.check(getApplication()) }
            updateLastCheck = mg.baiboly.app.update.UpdateCheck.lastCheck(getApplication())
            updateStatus = r.fold(
                onSuccess = { rel -> if (rel == null) UpdateStatus.UpToDate else UpdateStatus.Available(rel) },
                onFailure = { UpdateStatus.Failed },
            )
        }
    }

    private var downloadJob: Job? = null

    /**
     * "Download and install": downloads the new version inside the app, checks
     * it, and hands it to Android's installer. Only ever on the user's tap.
     */
    fun downloadAndInstallUpdate(release: mg.baiboly.app.update.Release) {
        if (downloadJob?.isActive == true) return
        val app = getApplication<Application>()
        updateStatus = UpdateStatus.Downloading(release, -1f)
        downloadJob = viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) {
                runCatching {
                    mg.baiboly.app.update.UpdateInstaller.download(app, release) { p ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (updateStatus is UpdateStatus.Downloading) updateStatus = UpdateStatus.Downloading(release, p)
                        }
                    }
                }
            }
            apk.fold(
                onSuccess = { installUpdate(release, it) },
                onFailure = { updateStatus = UpdateStatus.InstallFailed(release, it.message) },
            )
        }
    }

    /** Hands the downloaded file to Android, first asking for the install permission if needed. */
    fun installUpdate(release: mg.baiboly.app.update.Release, apk: java.io.File) {
        val app = getApplication<Application>()
        if (!apk.exists()) { updateStatus = UpdateStatus.Available(release); return }
        if (!mg.baiboly.app.update.UpdateInstaller.canInstall(app)) {
            updateStatus = UpdateStatus.NeedsPermission(release, apk); return
        }
        updateStatus = UpdateStatus.Installing(release, apk)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { mg.baiboly.app.update.UpdateInstaller.install(app, apk) } }
            r.onFailure { updateStatus = UpdateStatus.InstallFailed(release, it.message, apk) }
        }
    }

    /** "Try again" after a failure: installs the file already downloaded, or downloads it again. */
    fun retryUpdate(s: UpdateStatus.InstallFailed) {
        val apk = s.apk
        if (apk != null && apk.exists()) installUpdate(s.release, apk) else downloadAndInstallUpdate(s.release)
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        mg.baiboly.app.update.UpdateInstaller.clean(getApplication())
        (updateStatus as? UpdateStatus.Downloading)?.let { updateStatus = UpdateStatus.Available(it.release) }
    }

    /** Back from the system page that allows installing: continue if it was allowed. */
    fun onInstallPermissionResult() {
        val s = updateStatus as? UpdateStatus.NeedsPermission ?: return
        if (mg.baiboly.app.update.UpdateInstaller.canInstall(getApplication())) installUpdate(s.release, s.apk)
    }

    /** Started from the "new version" notification: open the update settings and look for it. */
    fun openUpdates() {
        screen = Screen.Settings
        openUpdateSettings = true
        if (updateStatus == UpdateStatus.Idle || updateStatus == UpdateStatus.UpToDate || updateStatus == UpdateStatus.Failed) checkForUpdates()
    }

    /** Settings shows Advanced › Updates unfolded (set when opened from the notification). */
    var openUpdateSettings by mutableStateOf(false)

    /** The Settings group shown ("look", "bible", "audio", "advanced"), or null for the list of groups. */
    var settingsPage by mutableStateOf<String?>(null)

    /** When the app opens, with the switch on: re-arms the daily check and checks if a day has passed. */
    private fun autoCheckUpdates() {
        if (!updateAuto) return
        val app = getApplication<Application>()
        mg.baiboly.app.update.UpdateCheck.schedule(app, true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { mg.baiboly.app.update.UpdateCheck.autoCheck(app) }
            withContext(Dispatchers.Main) { updateLastCheck = mg.baiboly.app.update.UpdateCheck.lastCheck(app) }
        }
    }

    init {
        // a downloaded APK is not kept from one run of the app to the next
        mg.baiboly.app.update.UpdateInstaller.clean(app)
        autoCheckUpdates()
        // the installer's answer (confirmation shown, refused, cancelled)
        viewModelScope.launch {
            mg.baiboly.app.update.UpdateInstaller.events.collect { e ->
                val s = updateStatus as? UpdateStatus.Installing ?: return@collect
                when (e) {
                    // the downloaded file is kept, so trying again does not download it again
                    is mg.baiboly.app.update.UpdateInstaller.Event.Failed -> updateStatus = UpdateStatus.InstallFailed(s.release, e.message, s.apk)
                    else -> Unit
                }
            }
        }
    }

    fun changeReadRate(rate: Float) {
        readRate = rate.coerceIn(0.6f, 1.6f)
        prefs.edit().putFloat("read_rate", readRate).apply()
    }

    /** Starts reading the shown chapter aloud from [verse] (only ever on the user's tap). */
    fun startReading(verse: Int = location.focus?.takeIf { !it.isWholeChapter && it.book == location.book && it.chapter == location.chapter }?.verseStart ?: 1) {
        if (!readAloudEnabled || mode == AppMode.Church) return
        ReadAloud.start(getApplication(), translationId, location.book, location.chapter, verse, readRate)
    }

    fun pauseReading() = ReadAloud.send(getApplication(), ReadAloud.ACTION_PAUSE)
    fun resumeReading() = ReadAloud.send(getApplication(), ReadAloud.ACTION_RESUME)
    fun stopReading() = ReadAloud.send(getApplication(), ReadAloud.ACTION_STOP)
    fun nextVerseReading() = ReadAloud.send(getApplication(), ReadAloud.ACTION_NEXT)
    fun previousVerseReading() = ReadAloud.send(getApplication(), ReadAloud.ACTION_PREVIOUS)

    // ------------------------------------------------------------ library (cross-references, dictionaries, places)

    /** Cross-references of a verse of the current text (the Treasury is in Malagasy 1865 numbering). */
    suspend fun crossRefs(verse: Int) = withContext(io) {
        val mg = toMalagasyNow(Passage(location.book, location.chapter, verse, verse))
        library.crossRefs(mg.book, mg.chapter, mg.verseStart)
    }

    // ------------------------------------------------------------ passage tools (selected verses)

    /** A passage of the current text in Malagasy 1865 numbering, which the study data uses. */
    private fun toMalagasyNow(p: Passage): Passage {
        if (translationId == BibleRepository.PRIMARY || p.isWholeChapter) return p
        val a = study.kjvToMg(VerseRef(p.book, p.chapter, p.verseStart))
        val b = study.kjvToMg(VerseRef(p.book, p.chapter, p.verseEnd))
        return if (a.chapter == b.chapter) Passage(p.book, a.chapter, a.verse, maxOf(a.verse, b.verse)) else Passage(p.book, a.chapter, a.verse, a.verse)
    }

    suspend fun toMalagasy(p: Passage): Passage = withContext(io) { toMalagasyNow(p) }

    /** The same passage in one translation. */
    data class Compared(val translation: Translation, val passage: Passage, val text: String, val numberingMayDiffer: Boolean)

    /**
     * The passage in every Bible offered (for the chosen tradition): from the
     * current text's numbering through the KJV numbering to each text's own.
     */
    suspend fun compareTranslations(p: Passage): List<Compared> = withContext(io) {
        val fromMg = translationId == BibleRepository.PRIMARY
        fun kjv(v: Int) = if (fromMg) study.mgToKjv(VerseRef(p.book, p.chapter, v)) else VerseRef(p.book, p.chapter, v)
        val a = kjv(p.verseStart)
        val b = kjv(p.verseEnd)
        visibleTranslations.mapNotNull { t ->
            val q = if (t.id == translationId) p else {
                val (s, e) = if (t.id == BibleRepository.PRIMARY) study.kjvToMg(a) to study.kjvToMg(b) else a to b
                runCatching { Passage(p.book, s.chapter, s.verse, if (e.chapter == s.chapter) maxOf(s.verse, e.verse) else s.verse) }.getOrNull()
            } ?: return@mapNotNull null
            val text = runCatching { bible.verses(t.id, q.book, q.chapter, q.verseStart, q.verseEnd) }.getOrDefault(emptyList())
                .joinToString(" ") { if (q.verseStart != q.verseEnd) "${it.verse} ${it.text}" else it.text }
            // the Douay-Rheims numbers the Psalms as the Vulgate does
            if (text.isBlank()) null else Compared(t, q, text, t.id == "dra" && p.book == 19)
        }
    }

    // ------------------------------------------------------------ quick look (selected verses)

    /**
     * What the small card shown with selected verses offers (Settings, Advanced, Quick look):
     * "translation" (the passage in another Bible), "units", "people", "places", "confessions".
     * By default the passage in another language and the units it names.
     */
    var quickLookItems by mutableStateOf(prefs.getStringSet("quick_look", null)?.toSet() ?: setOf("translation", "units"))
        private set

    fun changeQuickLookItem(item: String, on: Boolean) {
        quickLookItems = if (on) quickLookItems + item else quickLookItems - item
        prefs.edit().putStringSet("quick_look", quickLookItems).apply()
    }

    /** The Bible the quick look shows the passage in ("" = chosen by the app). */
    var quickLookTranslation by mutableStateOf(prefs.getString("quick_look_translation", "") ?: "")
        private set

    fun changeQuickLookTranslation(id: String) {
        quickLookTranslation = id
        prefs.edit().putString("quick_look_translation", id).apply()
    }

    /**
     * The other Bible for the quick look: the one chosen, unless it is the one being read; else one
     * in the interface language (or French, then English, when reading in it), never the text being read.
     */
    fun quickLookTarget(): Translation? {
        val current = translations.firstOrNull { it.id == translationId }
        val offered = visibleTranslations.filter { it.id != translationId }
        offered.firstOrNull { it.id == quickLookTranslation }?.let { return it }
        val ui = uiLanguage.ifEmpty { java.util.Locale.getDefault().language }
        val preferred = when (ui) { "fr" -> "lsg1910"; "en" -> "kjv"; else -> BibleRepository.PRIMARY }
        return offered.firstOrNull { it.id == preferred && it.lang != current?.lang }
            ?: offered.firstOrNull { it.lang != current?.lang }
            ?: offered.firstOrNull()
    }

    /** The passage in one other Bible only (each Bible's file is copied on first use), numbering matched. */
    suspend fun passageIn(p: Passage, t: Translation): Compared? = withContext(io) {
        val fromMg = translationId == BibleRepository.PRIMARY
        fun kjv(v: Int) = if (fromMg) study.mgToKjv(VerseRef(p.book, p.chapter, v)) else VerseRef(p.book, p.chapter, v)
        val a = kjv(p.verseStart)
        val b = kjv(p.verseEnd)
        val (s, e) = if (t.id == BibleRepository.PRIMARY) study.kjvToMg(a) to study.kjvToMg(b) else a to b
        val q = runCatching { Passage(p.book, s.chapter, s.verse, if (e.chapter == s.chapter) maxOf(s.verse, e.verse) else s.verse) }.getOrNull()
            ?: return@withContext null
        val text = runCatching { bible.verses(t.id, q.book, q.chapter, q.verseStart, q.verseEnd) }.getOrDefault(emptyList())
            .joinToString(" ") { if (q.verseStart != q.verseEnd) "${it.verse} ${it.text}" else it.text }
        if (text.isBlank()) null else Compared(t, q, text, t.id == "dra" && p.book == 19)
    }

    /** Opens the passage in another Bible. */
    fun readIn(c: Compared) {
        if (c.translation.id != translationId) { translationId = c.translation.id; chapter = null; persistState() }
        openPassage(c.passage)
    }

    /** Places named in the passage. */
    suspend fun placesInPassage(p: Passage) = withContext(io) {
        val mg = toMalagasyNow(p)
        library.placesInVerses(mg.book, mg.chapter, mg.verseStart, mg.verseEnd)
    }

    /** Dictionary entries named by words of the passage: Easton and Nave from the KJV, Martin from the Segond text. */
    data class DictMatch(val source: DictSource, val keys: List<String>)

    suspend fun passageDictionary(p: Passage): List<DictMatch> = withContext(io) {
        val mg = toMalagasyNow(p)
        val k1 = study.mgToKjv(VerseRef(mg.book, mg.chapter, mg.verseStart))
        val k2 = study.mgToKjv(VerseRef(mg.book, mg.chapter, mg.verseEnd))
        fun text(t: String) = runCatching {
            bible.verses(t, k1.book, k1.chapter, k1.verse, if (k2.chapter == k1.chapter) maxOf(k1.verse, k2.verse) else k1.verse).joinToString(" ") { it.text }
        }.getOrDefault("")
        fun words(s: String) = Regex("[\\p{L}][\\p{L}’'-]*").findAll(s).map { it.value.trim('’', '\'', '-') }
            .filter { it.length >= 3 && it.lowercase() !in STOP_WORDS }
            .flatMap { w -> sequenceOf(w, w.removeSuffix("s"), w.removeSuffix("es")) }.toList()
        val en = words(text("kjv"))
        val fr = words(text("lsg1910"))
        library.sources.mapNotNull { s ->
            val keys = library.entriesNamed(s.id, if (s.lang == "fr") fr else en).take(40)
            if (keys.isEmpty()) null else DictMatch(s, keys)
        }
    }

    /** Texts of the passage in Malagasy, French and English, for finding the units it names. */
    suspend fun passageTexts(p: Passage): Map<String, String> = withContext(io) {
        val mg = toMalagasyNow(p)
        val k1 = study.mgToKjv(VerseRef(mg.book, mg.chapter, mg.verseStart))
        val k2 = study.mgToKjv(VerseRef(mg.book, mg.chapter, mg.verseEnd))
        val end = if (k2.chapter == k1.chapter) maxOf(k1.verse, k2.verse) else k1.verse
        fun t(id: String, b: Int, c: Int, v1: Int, v2: Int) = runCatching { bible.verses(id, b, c, v1, v2).joinToString(" ") { it.text } }.getOrDefault("")
        mapOf(
            "mg" to t(BibleRepository.PRIMARY, mg.book, mg.chapter, mg.verseStart, mg.verseEnd),
            "fr" to t("lsg1910", k1.book, k1.chapter, k1.verse, end),
            "en" to t("kjv", k1.book, k1.chapter, k1.verse, end),
        )
    }

    /** A place to show when the map opens (from a passage). */
    var mapFocus by mutableStateOf<String?>(null)

    fun showOnMap(placeId: String) {
        mapFocus = placeId
        mapForChapter = false
        screen = Screen.Map
    }

    /** A unit to start the converter from (from a passage). */
    var unitsRequest by mutableStateOf<String?>(null)

    fun openUnit(id: String) {
        unitsRequest = id
        dictionaryUnits = true
        screen = Screen.Dictionary
    }

    suspend fun verseLine(p: Passage): String = withContext(io) {
        bible.verses(translationId, p.book, p.chapter, p.verseStart, p.verseEnd).joinToString(" ") { it.text }
    }

    suspend fun dictSources() = withContext(io) { library.sources }
    suspend fun dictSearch(source: String, q: String) = withContext(io) { library.search(source, q) }
    suspend fun dictEntry(source: String, key: String) = withContext(io) { library.entry(source, key) }
    suspend fun places() = withContext(io) { library.places }
    suspend fun placesInChapter() = withContext(io) { library.placesIn(location.book, location.chapter) }
    suspend fun placeVerses(id: String) = withContext(io) { library.versesOf(id) }

    /** Installed version, for About and the "What's new" page. */
    val versionName: String = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: ""
    private val versionCode: Long = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    /**
     * "What's new" after an update: shown once when the app was already in use
     * and the installed version is newer than the last one seen; never on a
     * first install.
     */
    var showWhatsNew by mutableStateOf(
        prefs.contains("list_id") && prefs.getLong("seen_version_code", -1L) < versionCode
    )

    fun dismissWhatsNew() {
        showWhatsNew = false
        prefs.edit().putLong("seen_version_code", versionCode).apply()
    }

    init {
        if (!prefs.contains("list_id")) prefs.edit().putLong("seen_version_code", versionCode).apply()
    }

    /** Verses are selected in the reader: the selection actions replace the navigation bar. */
    var selectionActive by mutableStateOf(false)

    /** Map opened from the reader shows this chapter's places; from the menu, all places. */
    var mapForChapter by mutableStateOf(false)

    /** The map fills the screen: no top bar, no navigation bar (on by default; remembered). */
    var mapFullscreen by mutableStateOf(prefs.getBoolean("map_fullscreen", true))
        private set

    fun changeMapFullscreen(on: Boolean) {
        mapFullscreen = on
        prefs.edit().putBoolean("map_fullscreen", on).apply()
    }

    /** The Dictionary page shows one of the study tools instead of a dictionary (null: a dictionary). */
    var dictionaryTool by mutableStateOf<StudyTool?>(null)

    /** The Dictionary page shows the units converter instead of a dictionary. */
    var dictionaryUnits: Boolean
        get() = dictionaryTool == StudyTool.Units
        set(on) { dictionaryTool = if (on) StudyTool.Units else null }

    /** A person, event or document to open when the tool page shows (from a passage). */
    var personRequest by mutableStateOf<Int?>(null)
    var eventRequest by mutableStateOf<Int?>(null)
    var creedRequest by mutableStateOf<Pair<String, Int>?>(null)

    fun openPerson(id: Int) { personRequest = id; dictionaryTool = StudyTool.People; screen = Screen.Dictionary }
    fun openEvent(id: Int) { eventRequest = id; dictionaryTool = StudyTool.Timeline; screen = Screen.Dictionary }
    fun openCreed(doc: String, seq: Int) { creedRequest = doc to seq; dictionaryTool = StudyTool.Confessions; screen = Screen.Dictionary }

    /** People, events and confession articles of the selected passage. */
    suspend fun peopleInPassage(p: Passage) = withContext(io) {
        val mg = toMalagasyNow(p)
        chronicle.peopleInVerses(mg.book, mg.chapter, mg.verseStart, mg.verseEnd)
    }

    suspend fun eventsInPassage(p: Passage) = withContext(io) {
        val mg = toMalagasyNow(p)
        chronicle.eventsInVerses(mg.book, mg.chapter, mg.verseStart, mg.verseEnd)
    }

    suspend fun creedsCiting(p: Passage) = withContext(io) {
        val mg = toMalagasyNow(p)
        chronicle.creedCitations(mg.book, mg.chapter, mg.verseStart, mg.verseEnd)
    }

    /**
     * Families of documents shown in normal study for the chosen tradition (docs/ideas.md A3):
     * the creeds belong to every tradition; the Reformed documents to the Protestant one.
     * Documents of other traditions appear only under "Go deeper", marked with their tradition.
     */
    fun ownFamilies(): Set<String> = if (tradition == Tradition.Protestant) setOf("creed", "reformed") else setOf("creed", "catholic")

    suspend fun <T> onIo(block: () -> T): T = withContext(io) { block() }

    fun toggleStudyMode() {
        changeMode(if (studyMode) AppMode.Casual else AppMode.Study)
    }

    // ------------------------------------------------------------ service list

    private fun updateList(l: ServiceList) {
        list = l
        persistState()
        viewModelScope.launch(io) { user.saveList(l) }
    }

    fun selectItem(index: Int) {
        if (index !in list.items.indices) return
        jumpedFromList = false
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
        // the list in use is still empty: use it rather than adding a second empty list of the same name
        if (list.items.isEmpty()) return
        viewModelScope.launch {
            val newTitle = defaultTitle()
            val l = withContext(io) { user.createList(newTitle, today()) }
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
            val copyTitle = str(R.string.copy_suffix, title)
            withContext(io) { user.duplicateList(id, copyTitle, today()) }
            refreshSavedLists()
        }
    }

    fun deleteSavedList(id: Long) {
        viewModelScope.launch {
            withContext(io) { user.deleteList(id) }
            if (id == list.id) {
                // The list in use was deleted: continue with the most recent remaining list. A new empty
                // list is made only when none is left (it used to be made every time, with the same name,
                // so the deleted row seemed to stay on screen).
                val l = withContext(io) {
                    user.savedLists().firstOrNull()?.let { user.loadList(it.id, 0) } ?: user.createList(defaultTitle(), today())
                }
                list = l
                persistState()
            }
            refreshSavedLists()
        }
    }

    var savedLists by mutableStateOf<List<SavedListSummary>>(emptyList())
        private set

    fun refreshSavedLists() {
        val current = list.id
        viewModelScope.launch {
            savedLists = withContext(io) {
                // empty lists other than the one in use are left over (e.g. made and never filled): drop them
                user.savedLists().filter { it.itemCount == 0 && it.id != current }.forEach { user.deleteList(it.id) }
                user.savedLists()
            }
        }
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
    var searchTranslation by mutableStateOf(defaultTranslationId)
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
        defaultTranslationId = prefs.getString("default_translation", BibleRepository.PRIMARY)!!
        translationId = defaultTranslationId
        uiLanguage = prefs.getString(UiLanguage.KEY, UiLanguage.DEFAULT) ?: UiLanguage.DEFAULT
        textSize = prefs.getFloat("text_size", 20f)
        theme = ThemeMode.entries[prefs.getInt("theme", 0).coerceIn(0, 2)]
        style = enumPref("style", Style.Manuscript)
        palette = enumPref("palette", Palette.Vellum)
        textFont = enumPref("text_font", TextFont.Classic)
        engravedTitles = prefs.getBoolean("engraved_titles", true)
        lineSpacing = enumPref("line_spacing", LineSpacing.Normal)
        verseLayout = enumPref("verse_layout", VerseLayout.Lines)
        justify = prefs.getBoolean("text_justify", true)
        hyphenate = prefs.getBoolean("text_hyphenate", true)
        paragraphBreak = enumPref("paragraph_break", ParagraphBreak.Indent)
        pageMargin = enumPref("page_margin", PageMargin.Normal)
        uiDensity = enumPref("ui_density", UiDensity.Compact)
        keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        mode = AppMode.entries[prefs.getInt("mode", 0).coerceIn(0, 2)]
        val newTitle = defaultTitle()
        val (l, rec, usage) = withContext(io) {
            val id = prefs.getLong("list_id", -1)
            val l = user.loadList(id, prefs.getInt("list_current", 0)) ?: user.createList(newTitle, today())
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
