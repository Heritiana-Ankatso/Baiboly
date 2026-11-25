package mg.baiboly.core

import mg.baiboly.core.notes.Block
import mg.baiboly.core.notes.ExportFormat
import mg.baiboly.core.notes.Inline
import mg.baiboly.core.notes.LinkTarget
import mg.baiboly.core.notes.NoteExport
import mg.baiboly.core.notes.NoteMarkup
import mg.baiboly.core.notes.NoteSheet
import mg.baiboly.core.ref.Passage
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteMarkupTest {
    private val body = """
        # The love of God
        Read [Jao 3: 16](bible:43.3.16-16) with **care**.
        It goes on here.

        ## Who believes
        - [Abraham](person:42) believed *God*
        - the promise
        1. first
        2. second
        > For God so loved the world
        > that he gave
        ---
        ### Note
        A [lost link](nowhere:1) stays words.
    """.trimIndent()

    @Test fun blocks() {
        val b = NoteMarkup.parse(body)
        assertEquals(Block.Heading::class, b[0]::class)
        assertEquals(1, (b[0] as Block.Heading).level)
        val p = b[1] as Block.Paragraph
        assertEquals("Read Jao 3: 16 with care. It goes on here.", NoteMarkup.plain(p.inlines))
        assertEquals(2, (b[2] as Block.Heading).level)
        assertNull((b[3] as Block.Item).number)
        assertEquals(1, (b[5] as Block.Item).number)
        val q = b[7] as Block.Quote
        assertEquals("For God so loved the world that he gave", NoteMarkup.plain(q.inlines))
        assertIs<Block.Rule>(b[8])
    }

    @Test fun linksAndTargets() {
        val t = NoteMarkup.targets(body)
        assertEquals(listOf<LinkTarget>(LinkTarget.Verse(Passage(43, 3, 16, 16)), LinkTarget.Person(42)), t)
        val link = NoteMarkup.inline("[x](nowhere:1)").single() as Inline.Link
        assertNull(link.target)
        assertEquals("bible:45.8.28-30", LinkTarget.Verse(Passage(45, 8, 28, 30)).uri)
        assertEquals(LinkTarget.Creed("wcf", 12), LinkTarget.parse("creed:wcf:12"))
        assertEquals("[Rom 8: 28 (KJV)](bible:45.8.28-28)", NoteMarkup.link("Rom 8: 28 [KJV]", LinkTarget.Verse(Passage(45, 8, 28, 28))))
    }

    @Test fun loneStarsStay() {
        assertEquals("2 * 3 = 6", NoteMarkup.plain(NoteMarkup.inline("2 * 3 = 6")))
        assertEquals("**open", NoteMarkup.plain(NoteMarkup.inline("**open")))
    }

    @Test fun outlineAndMovingSections() {
        val doc = "# A\na\n## A1\nx\n# B\nb\n# C\nc"
        assertEquals(listOf(1 to "A", 2 to "A1", 1 to "B", 1 to "C"), NoteMarkup.outline(doc).map { it.first to it.second })
        // B moves up before A, with nothing of A left behind
        assertEquals("# B\nb\n# A\na\n## A1\nx\n# C\nc", NoteMarkup.moveSection(doc, 4, up = true))
        // A (with its subsection) moves down after B
        assertEquals("# B\nb\n# A\na\n## A1\nx\n# C\nc", NoteMarkup.moveSection(doc, 0, up = false))
        // the first section cannot go up, the last cannot go down
        assertEquals(doc, NoteMarkup.moveSection(doc, 0, up = true))
        assertEquals(doc, NoteMarkup.moveSection(doc, 6, up = false))
    }

    private val sheet = NoteSheet("Sunday 24 Nov", listOf("24 November 2026", "Jaona 3: 16"), body)

    @Test fun markdownAndText() {
        val md = NoteExport.markdown(sheet)
        assertTrue(md.startsWith("# Sunday 24 Nov\n"))
        assertContains(md, "## The love of God")
        assertContains(md, "Read Jao 3: 16 with **care**.")
        assertFalse(md.contains("bible:"))
        val txt = NoteExport.text(sheet)
        assertContains(txt, "THE LOVE OF GOD")
        assertContains(txt, "• Abraham believed God")
        assertFalse(txt.contains("**"))
    }

    @Test fun html() {
        val h = NoteExport.html(sheet.copy(title = "A <b> & c"))
        assertContains(h, "<title>A &lt;b&gt; &amp; c</title>")
        assertContains(h, "<h2>The love of God</h2>")
        assertContains(h, "<ul>")
        assertContains(h, "<ol>")
        assertContains(h, "<blockquote>For God so loved the world that he gave</blockquote>")
        assertContains(h, "<span class=\"ref\">Jao 3: 16</span>")
    }

    @Test fun word() {
        val bytes = NoteExport.bytes(sheet, ExportFormat.Word)
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; parts[e.name] = z.readBytes().decodeToString() }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/_rels/document.xml.rels"), parts.keys)
        val doc = parts.getValue("word/document.xml")
        assertContains(doc, "<w:pStyle w:val=\"Title\"/>")
        assertContains(doc, "<w:pStyle w:val=\"Heading1\"/>")
        assertContains(doc, "The love of God")
        assertContains(doc, "<w:b/>")
        // well-formed XML
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(doc.toByteArray()))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("word/styles.xml").toByteArray()))
    }

    @Test fun fileNames() {
        assertEquals("Sunday 24 Nov.docx", NoteExport.fileName("Sunday: 24/Nov", ExportFormat.Word))
        assertEquals("Baiboly.md", NoteExport.fileName("???", ExportFormat.Markdown))
    }
}
