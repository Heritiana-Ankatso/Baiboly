package mg.baiboly.core.suggest

import mg.baiboly.core.bible.BookInfo
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.core.ref.ReferenceParser.Stage

/** A tappable suggestion. Tapping replaces the input with [newInput]. */
data class Suggestion(val label: String, val newInput: String, val isRecent: Boolean = false)

/**
 * Staged suggestions (brief section 6): book chips, then chapter chips, then
 * verse chips. Recently opened passages always come first. Pure and
 * allocation-light: it runs on every keystroke.
 */
class Suggester(private val maxChips: Int = 80) {

    /** Book order for the keypad grid: books of recent passages, then most used, then canonical. */
    fun rankBooks(recent: List<Passage>, usage: Map<Int, Int>): List<BookInfo> {
        val order = LinkedHashSet<Int>()
        recent.forEach { order += it.book }
        usage.entries.sortedByDescending { it.value }.forEach { order += it.key }
        Canon.books.forEach { order += it.id }
        return order.map { Canon.book(it) }
    }

    fun suggest(input: String, recent: List<Passage>, usage: Map<Int, Int> = emptyMap()): List<Suggestion> {
        val out = ArrayList<Suggestion>()
        val seen = HashSet<String>()
        fun add(s: Suggestion) {
            if (out.size < maxChips && seen.add(s.newInput)) out += s
        }
        when (val st = ReferenceParser.analyze(input)) {
            is Stage.Book -> {
                val cands = ReferenceParser.bookCandidates(st.typed).map { it.id }.toSet()
                // whole recent passages whose book matches what is typed
                recent.filter { it.book in cands }.take(8).forEach {
                    add(Suggestion(it.shortLabel(), st.head + it.shortLabel(), isRecent = true))
                }
                rankBooks(recent, usage).filter { it.id in cands }.forEach {
                    add(Suggestion(it.shortLabel, st.head + it.shortLabel + " "))
                }
            }
            is Stage.Chapter -> {
                recent.filter { it.book == st.book.id && "${it.chapter}".startsWith(st.typed) }.take(6).forEach {
                    add(Suggestion(it.shortLabel(), st.head + it.numbers(), isRecent = true))
                }
                val n = Canon.chapterCount(st.book.id)
                val recentChapters = recent.filter { it.book == st.book.id }.map { it.chapter }.distinct()
                (recentChapters + (1..n)).distinct().filter { "$it".startsWith(st.typed) }.forEach {
                    add(Suggestion("$it", st.head + "$it: "))
                }
            }
            is Stage.Verse -> {
                recent.filter { it.book == st.book.id && it.chapter == st.chapter && !it.isWholeChapter }
                    .filter { "${it.verseStart}".startsWith(st.typed) }.take(6).forEach {
                        val v = if (it.verseStart == it.verseEnd) "${it.verseStart}" else "${it.verseStart}-${it.verseEnd}"
                        add(Suggestion(it.shortLabel(), st.head + v, isRecent = true))
                    }
                val n = Canon.verseCount(st.book.id, st.chapter)
                (1..n).filter { "$it".startsWith(st.typed) }.forEach { add(Suggestion("$it", st.head + "$it")) }
            }
            is Stage.None -> Unit
        }
        return out
    }
}
