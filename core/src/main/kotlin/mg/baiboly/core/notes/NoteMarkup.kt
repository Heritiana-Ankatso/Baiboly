package mg.baiboly.core.notes

import mg.baiboly.core.ref.Passage

/**
 * Sermon notes and study documents are written in a small Markdown:
 *
 *     # Title            ## Section          ### Subsection
 *     - point            1. numbered point   > quotation
 *     **bold**  *italic*  ---  (a line across)
 *     [Jao 3: 16](bible:43.3.16-16)   [Abraham](person:42)   [The Exodus](event:7)
 *     [Jerusalem](place:jerusalem)    [WCF 1.2](creed:wcf:12)
 *
 * The links tie a part of the note to a verse, a person, an event, a place or a confession: the
 * app opens them, and the Study panel of a passage lists the notes that cite it.
 */
sealed interface LinkTarget {
    /** The link as written in the note ("bible:43.3.16-16"). */
    val uri: String

    data class Verse(val passage: Passage) : LinkTarget {
        override val uri: String get() = "bible:${passage.book}.${passage.chapter}.${passage.verseStart}-${passage.verseEnd}"
    }
    data class Person(val id: Int) : LinkTarget { override val uri: String get() = "person:$id" }
    data class Event(val id: Int) : LinkTarget { override val uri: String get() = "event:$id" }
    data class Place(val id: String) : LinkTarget { override val uri: String get() = "place:$id" }
    data class Creed(val doc: String, val seq: Int) : LinkTarget { override val uri: String get() = "creed:$doc:$seq" }

    companion object {
        fun parse(uri: String): LinkTarget? {
            val scheme = uri.substringBefore(':', "")
            val rest = uri.substringAfter(':', "")
            return runCatching {
                when (scheme) {
                    "bible" -> {
                        val (b, c, vs) = rest.split('.').let { Triple(it[0].toInt(), it[1].toInt(), it.getOrElse(2) { "0" }) }
                        val v1 = vs.substringBefore('-').toInt()
                        val v2 = vs.substringAfter('-', vs).toInt()
                        Verse(Passage(b, c, v1, maxOf(v1, v2)))
                    }
                    "person" -> Person(rest.toInt())
                    "event" -> Event(rest.toInt())
                    "place" -> Place(rest).takeIf { rest.isNotEmpty() }
                    "creed" -> Creed(rest.substringBefore(':'), rest.substringAfter(':').toInt())
                    else -> null
                }
            }.getOrNull()
        }
    }
}

sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Bold(val children: List<Inline>) : Inline
    data class Italic(val children: List<Inline>) : Inline
    /** [target] is null when the link is not one the app knows (it then reads as plain text). */
    data class Link(val label: String, val target: LinkTarget?, val uri: String) : Inline
}

sealed interface Block {
    data class Heading(val level: Int, val inlines: List<Inline>) : Block
    data class Paragraph(val inlines: List<Inline>) : Block
    /** A list item; [number] for a numbered one ("3."), else null. [indent]: 0 or 1. */
    data class Item(val inlines: List<Inline>, val number: Int?, val indent: Int) : Block
    data class Quote(val inlines: List<Inline>) : Block
    data object Rule : Block
}

object NoteMarkup {
    private val heading = Regex("^(#{1,3})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*•]\\s+(.*)$")
    private val numbered = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")

    /** The body as blocks: consecutive plain lines join into one paragraph; a blank line ends it. */
    fun parse(body: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += Block.Paragraph(inline(para.toString().trim()))
            para.clear()
        }
        for (raw in body.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            val h = heading.find(line)
            val b = bullet.find(line)
            val n = numbered.find(line)
            when {
                line.isBlank() -> flush()
                line.trim() == "---" -> { flush(); out += Block.Rule }
                h != null -> { flush(); out += Block.Heading(h.groupValues[1].length, inline(h.groupValues[2].trim())) }
                b != null -> { flush(); out += Block.Item(inline(b.groupValues[2]), null, if (b.groupValues[1].length >= 2) 1 else 0) }
                n != null -> { flush(); out += Block.Item(inline(n.groupValues[3]), n.groupValues[2].toInt(), if (n.groupValues[1].length >= 2) 1 else 0) }
                line.startsWith(">") -> { flush(); out += Block.Quote(inline(line.removePrefix(">").trim())) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
            }
        }
        flush()
        // consecutive quotation lines read as one quotation
        return out.fold(mutableListOf()) { acc, blk ->
            val last = acc.lastOrNull()
            if (blk is Block.Quote && last is Block.Quote) acc[acc.lastIndex] = Block.Quote(last.inlines + Inline.Text(" ") + blk.inlines)
            else acc += blk
            acc
        }
    }

    /** Bold, italic and links inside a line. Unclosed markers stay as written. */
    fun inline(s: String): List<Inline> {
        val out = mutableListOf<Inline>()
        val text = StringBuilder()
        fun flush() { if (text.isNotEmpty()) { out += Inline.Text(text.toString()); text.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { text.append(s[i + 1]); i += 2; continue }
            if (c == '[') {
                val close = findClose(s, i + 1, ']')
                if (close > 0 && close + 1 < s.length && s[close + 1] == '(') {
                    val end = s.indexOf(')', close + 2)
                    if (end > 0) {
                        flush()
                        val label = s.substring(i + 1, close)
                        val uri = s.substring(close + 2, end).trim()
                        out += Inline.Link(label, LinkTarget.parse(uri), uri)
                        i = end + 1
                        continue
                    }
                }
            }
            if (s.startsWith("**", i)) {
                val end = s.indexOf("**", i + 2)
                if (end > i + 2) { flush(); out += Inline.Bold(inline(s.substring(i + 2, end))); i = end + 2; continue }
            }
            if (c == '*' || c == '_') {
                val end = s.indexOf(c, i + 1)
                // a lone star between words (2 * 3) stays a star
                if (end > i + 1 && !s[i + 1].isWhitespace() && !s[end - 1].isWhitespace()) {
                    flush(); out += Inline.Italic(inline(s.substring(i + 1, end))); i = end + 1; continue
                }
            }
            text.append(c); i++
        }
        flush()
        return out
    }

    private fun findClose(s: String, from: Int, ch: Char): Int {
        var depth = 0
        for (j in from until s.length) {
            when (s[j]) {
                '[' -> depth++
                ch -> if (depth == 0) return j else depth--
            }
        }
        return -1
    }

    /** A link as written in a note: label and target, with the label's brackets escaped. */
    fun link(label: String, target: LinkTarget): String =
        "[" + label.replace("[", "(").replace("]", ")") + "](" + target.uri + ")"

    /** The plain words of some inlines (links read as their labels). */
    fun plain(inlines: List<Inline>): String = buildString {
        for (x in inlines) when (x) {
            is Inline.Text -> append(x.text)
            is Inline.Bold -> append(plain(x.children))
            is Inline.Italic -> append(plain(x.children))
            is Inline.Link -> append(x.label)
        }
    }

    /** Every link target in a body, in order. */
    fun targets(body: String): List<LinkTarget> {
        val out = mutableListOf<LinkTarget>()
        fun walk(xs: List<Inline>) {
            for (x in xs) when (x) {
                is Inline.Link -> x.target?.let { out += it }
                is Inline.Bold -> walk(x.children)
                is Inline.Italic -> walk(x.children)
                is Inline.Text -> Unit
            }
        }
        for (b in parse(body)) when (b) {
            is Block.Heading -> walk(b.inlines)
            is Block.Paragraph -> walk(b.inlines)
            is Block.Item -> walk(b.inlines)
            is Block.Quote -> walk(b.inlines)
            Block.Rule -> Unit
        }
        return out
    }

    /** The headings (level, text, line index), for the outline of a study. */
    fun outline(body: String): List<Triple<Int, String, Int>> =
        body.split('\n').mapIndexedNotNull { i, line ->
            heading.find(line.trimEnd())?.let { Triple(it.groupValues[1].length, plain(inline(it.groupValues[2].trim())), i) }
        }

    /**
     * Moves the section that starts at heading line [line] (up to the next heading of the same or a
     * higher level) before the previous section of that level, or after the next one.
     */
    fun moveSection(body: String, line: Int, up: Boolean): String {
        val lines = body.split('\n').toMutableList()
        val level = heading.find(lines.getOrNull(line) ?: return body)?.groupValues?.get(1)?.length ?: return body
        fun levelAt(i: Int) = heading.find(lines[i])?.groupValues?.get(1)?.length
        fun endOf(start: Int): Int {
            var j = start + 1
            while (j < lines.size && (levelAt(j)?.let { it > level } != false)) j++
            return j
        }
        val end = endOf(line)
        if (up) {
            var prev = line - 1
            while (prev >= 0 && levelAt(prev)?.let { it == level } != true) {
                if (levelAt(prev)?.let { it < level } == true) return body
                prev--
            }
            if (prev < 0) return body
            val block = lines.subList(line, end).toList()
            repeat(end - line) { lines.removeAt(line) }
            lines.addAll(prev, block)
        } else {
            if (end >= lines.size || levelAt(end) != level) return body
            val nextEnd = endOf(end)
            val block = lines.subList(end, nextEnd).toList()
            repeat(nextEnd - end) { lines.removeAt(end) }
            lines.addAll(line, block)
        }
        return lines.joinToString("\n")
    }
}
