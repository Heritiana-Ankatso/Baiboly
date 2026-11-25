package mg.baiboly.core.ref

import mg.baiboly.core.bible.BookInfo
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.text.TextNorm

/** Why an entry could not be turned into list items. The UI turns each into a localised message. */
sealed interface ParseError {
    /** Nothing typed yet. */
    data object Empty : ParseError

    /** The entry ends in the middle of a reference ("Jao 3:", "Efe 2: 4,"): not an error yet, just unfinished. */
    data object Incomplete : ParseError

    data class UnknownBook(val text: String) : ParseError
    data class AmbiguousBook(val text: String, val candidates: List<Int>) : ParseError
    data object MissingBook : ParseError
    data class ChapterOutOfRange(val book: Int, val chapter: Int, val max: Int) : ParseError
    data class VerseOutOfRange(val book: Int, val chapter: Int, val verse: Int, val max: Int) : ParseError
    data class ReversedRange(val book: Int, val chapter: Int, val from: Int, val to: Int) : ParseError
    data class Syntax(val near: String) : ParseError
}

data class ParseResult(val passages: List<Passage>, val error: ParseError?) {
    val isValid: Boolean get() = error == null && passages.isNotEmpty()

    /** Live preview such as "Efesiana 2: 4, 8-9". */
    fun preview(lang: String = "mg"): String = Passage.describe(passages, lang)
}

/** Result of looking up a typed book name. */
sealed interface BookMatch {
    data class Found(val book: BookInfo) : BookMatch
    data class Ambiguous(val candidates: List<BookInfo>) : BookMatch
    data object NotFound : BookMatch
}

/**
 * Turns the user's typed references into passages (brief section 6).
 *
 * Grammar (spaces optional everywhere):
 * ```
 * entry    := segment (';' segment)*
 * segment  := book? chapter ((':' | '.') verses)?     -- book carries over from the previous segment
 * verses   := range (',' range)*
 * range    := n ('-' n)?
 * ```
 * One passage per contiguous verse range; a chapter alone means the whole
 * chapter; reversed ranges and numbers beyond the real counts are rejected,
 * never guessed. Book matching is accent- and case-insensitive and accepts the
 * Malagasy abbreviation, any unambiguous prefix of the Malagasy name, and the
 * French or English names as a fallback.
 */
object ReferenceParser {

    private val bookPart = Regex("^((?:[1-3]\\s*)?\\p{L}[\\p{L}'’\\s-]*?)\\.?\\s*(?=\\d|$)")
    private val numberedBook = Regex("^([1-3])\\s*(\\p{L}.*)$")
    private val chapterSpec = Regex("^(\\d+)\\s*(?:[:.]\\s*(.*))?$")
    private val rangeSpec = Regex("^(\\d+)(?:\\s*[-–—]\\s*(\\d+))?$")

    fun parse(input: String): ParseResult {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult(emptyList(), ParseError.Empty)
        val out = mutableListOf<Passage>()
        var book: BookInfo? = null
        val segments = text.split(';')
        for ((i, rawSeg) in segments.withIndex()) {
            val seg = rawSeg.trim()
            if (seg.isEmpty()) {
                return ParseResult(out, if (i == segments.lastIndex) ParseError.Incomplete else ParseError.Syntax(";"))
            }
            var rest = seg
            val bm = bookPart.find(seg)
            if (bm != null && bm.groupValues[1].any { it.isLetter() }) {
                val name = bm.groupValues[1].trim()
                when (val m = matchBook(name)) {
                    is BookMatch.Found -> book = m.book
                    is BookMatch.Ambiguous -> return ParseResult(out, ParseError.AmbiguousBook(name, m.candidates.map { it.id }))
                    BookMatch.NotFound -> return ParseResult(out, ParseError.UnknownBook(name))
                }
                rest = seg.substring(bm.range.last + 1).trim()
            }
            val b = book ?: return ParseResult(out, ParseError.MissingBook)
            if (rest.isEmpty()) return ParseResult(out, ParseError.Incomplete)
            val err = parseChapterSpec(b, rest, out)
            if (err != null) return ParseResult(out, err)
        }
        return ParseResult(out, null)
    }

    private fun parseChapterSpec(book: BookInfo, spec: String, out: MutableList<Passage>): ParseError? {
        val m = chapterSpec.find(spec) ?: return ParseError.Syntax(spec)
        val chapter = m.groupValues[1].toIntOrNull() ?: return ParseError.Syntax(spec)
        val maxChapter = Canon.chapterCount(book.id)
        if (chapter < 1 || chapter > maxChapter) return ParseError.ChapterOutOfRange(book.id, chapter, maxChapter)
        val hasColon = m.groups[2] != null
        val verses = m.groupValues[2].trim()
        if (!hasColon) {
            out += Passage(book.id, chapter)
            return null
        }
        if (verses.isEmpty()) return ParseError.Incomplete
        val maxVerse = Canon.verseCount(book.id, chapter)
        val ranges = mutableListOf<IntRange>()
        val parts = verses.split(',')
        for ((i, raw) in parts.withIndex()) {
            val p = raw.trim()
            if (p.isEmpty() || p.endsWith("-") || p.endsWith("–")) {
                return if (i == parts.lastIndex) ParseError.Incomplete else ParseError.Syntax(",")
            }
            val r = rangeSpec.find(p) ?: return ParseError.Syntax(p)
            val from = r.groupValues[1].toInt()
            val to = r.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: from
            if (from < 1 || from > maxVerse) return ParseError.VerseOutOfRange(book.id, chapter, from, maxVerse)
            if (to > maxVerse) return ParseError.VerseOutOfRange(book.id, chapter, to, maxVerse)
            if (to < from) return ParseError.ReversedRange(book.id, chapter, from, to)
            ranges += from..to
        }
        out += mergeRanges(ranges).map { Passage(book.id, chapter, it.first, it.last) }
        return null
    }

    /** Sorts ranges and merges overlapping or adjacent ones ("4, 5" -> 4-5). */
    fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (r in sorted) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            } else merged += r
        }
        return merged
    }

    // ------------------------------------------------------------------ books

    private data class Split(val number: Int?, val key: String)

    private fun split(raw: String): Split {
        val n = TextNorm.norm(raw.trim())
        val m = numberedBook.find(n)
        return if (m != null) Split(m.groupValues[1].toInt(), TextNorm.key(m.groupValues[2]))
        else Split(null, TextNorm.key(n))
    }

    private fun mgKeys(b: BookInfo): List<String> =
        (listOf(b.nameMg) + b.aliases).map { b.baseKey(it) } + TextNorm.key(b.abbrMg.replace(Regex("^[1-3]\\s*"), ""))

    private fun otherKeys(b: BookInfo): List<String> = listOf(b.baseKey(b.nameFr), b.baseKey(b.nameEn))

    /** Resolves a typed book name. */
    fun matchBook(raw: String): BookMatch {
        val (num, key) = split(raw)
        if (key.isEmpty()) return BookMatch.NotFound
        for (keys in listOf(::mgKeys, ::otherKeys)) {
            val sameNumber = Canon.books.filter { it.number == num }
            val exact = sameNumber.filter { b -> keys(b).any { it == key } }
            if (exact.size == 1) return BookMatch.Found(exact[0])
            val prefix = sameNumber.filter { b -> keys(b).any { it.startsWith(key) } }
            if (prefix.size == 1) return BookMatch.Found(prefix[0])
            if (prefix.size > 1) return BookMatch.Ambiguous(prefix)
            if (num == null) {
                // "Sam" alone: only numbered books match, so the number is missing.
                val numbered = Canon.books.filter { b -> b.number != null && keys(b).any { it.startsWith(key) } }
                if (numbered.isNotEmpty()) return BookMatch.Ambiguous(numbered)
            }
        }
        // last, the French and English abbreviations as written in those Bibles ("Jn", "1 Co", "Matt")
        val abbr = Canon.books.filter { b ->
            b.number == num && listOf("fr", "en").any { l -> b.baseKey(b.abbr(l)) == key }
        }
        if (abbr.size == 1) return BookMatch.Found(abbr[0])
        return BookMatch.NotFound
    }

    /** Books whose names start with what has been typed so far, for the suggestion row. */
    fun bookCandidates(raw: String): List<BookInfo> {
        val (num, key) = split(raw)
        if (key.isEmpty()) {
            return if (num == null) Canon.books else Canon.books.filter { it.number == num }
        }
        val all = Canon.books.filter { b ->
            (num == null || b.number == num) && (mgKeys(b) + otherKeys(b)).any { it.startsWith(key) }
        }
        // Malagasy matches first
        return all.sortedBy { b -> if (mgKeys(b).any { it.startsWith(key) }) 0 else 1 }
    }

    // ------------------------------------------------------------------ live analysis

    /** What is being typed right now, for staged suggestions. */
    sealed interface Stage {
        /** Text typed before the token currently being typed. A suggestion replaces the token. */
        val head: String

        data class Book(override val head: String, val typed: String) : Stage
        data class Chapter(override val head: String, val book: BookInfo, val typed: String) : Stage
        data class Verse(override val head: String, val book: BookInfo, val chapter: Int, val typed: String) : Stage
        data class None(override val head: String) : Stage
    }

    fun analyze(input: String): Stage {
        val segStart = input.lastIndexOf(';') + 1
        val seg = input.substring(segStart)
        val lead = seg.length - seg.trimStart().length
        val segBody = seg.trimStart()
        val bodyStart = segStart + lead
        // Book carried from earlier segments.
        var carried: BookInfo? = null
        if (segStart > 0) {
            val earlier = input.substring(0, segStart - 1)
            for (s in earlier.split(';')) {
                val bm = bookPart.find(s.trim())
                if (bm != null && bm.groupValues[1].any { it.isLetter() }) {
                    (matchBook(bm.groupValues[1].trim()) as? BookMatch.Found)?.let { carried = it.book }
                }
            }
        }
        val bm = bookPart.find(segBody)
        var book = carried
        var restStart = bodyStart
        if (bm != null && bm.groupValues[1].any { it.isLetter() }) {
            val name = bm.groupValues[1]
            val afterBook = segBody.substring(bm.range.last + 1)
            val typedBook = name.trim()
            // Still typing the book name: no digit after it yet and no trailing space.
            if (afterBook.isEmpty() && !segBody.endsWith(" ")) {
                return Stage.Book(input.substring(0, bodyStart), typedBook)
            }
            book = (matchBook(typedBook) as? BookMatch.Found)?.book
                ?: return Stage.Book(input.substring(0, bodyStart), typedBook)
            restStart = bodyStart + bm.range.last + 1
        } else if (segBody.isEmpty() && carried == null) {
            return Stage.Book(input.substring(0, bodyStart), "")
        } else if (segBody.matches(Regex("^[1-3]\\s*$")) && carried == null) {
            return Stage.Book(input.substring(0, bodyStart), segBody.trim())
        }
        val b = book ?: return Stage.Book(input.substring(0, bodyStart), segBody)
        val rest = input.substring(restStart)
        val restTrim = rest.trimStart()
        val restOffset = restStart + (rest.length - restTrim.length)
        val colon = restTrim.indexOfFirst { it == ':' || it == '.' }
        if (colon < 0) {
            if (!restTrim.all { it.isDigit() }) return Stage.None(input)
            return Stage.Chapter(input.substring(0, restOffset), b, restTrim)
        }
        val chapter = restTrim.substring(0, colon).trim().toIntOrNull() ?: return Stage.None(input)
        val versesPart = restTrim.substring(colon + 1)
        val tokenStart = versesPart.indexOfLast { it == ',' || it == '-' || it == '–' || it == ':' } + 1
        val token = versesPart.substring(tokenStart)
        val tokenTrim = token.trimStart()
        if (!tokenTrim.all { it.isDigit() }) return Stage.None(input)
        val headEnd = restOffset + colon + 1 + tokenStart + (token.length - tokenTrim.length)
        return Stage.Verse(input.substring(0, headEnd), b, chapter, tokenTrim)
    }
}
