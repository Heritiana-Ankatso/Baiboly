package mg.baiboly.core.notes

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A note ready to leave the app: its title, the lines under it (date, passages, preacher…, already
 * written in the reader's language) and its body in the notes' Markdown ([NoteMarkup]).
 */
data class NoteSheet(val title: String, val meta: List<String>, val body: String)

/** Export formats. PDF is drawn by the app itself (it needs Android's PdfDocument). */
enum class ExportFormat(val extension: String, val mime: String) {
    Markdown("md", "text/markdown"),
    Text("txt", "text/plain"),
    Html("html", "text/html"),
    Word("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Pdf("pdf", "application/pdf"),
}

object NoteExport {
    /** A file name from the title: letters, digits, spaces and dashes only. */
    fun fileName(title: String, format: ExportFormat): String {
        val base = title.replace(Regex("[^\\p{L}\\p{N} _-]"), " ").trim().replace(Regex("\\s+"), " ").take(60).ifEmpty { "Baiboly" }
        return "$base.${format.extension}"
    }

    fun bytes(sheet: NoteSheet, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.Markdown -> markdown(sheet).toByteArray()
        ExportFormat.Text -> text(sheet).toByteArray()
        ExportFormat.Html -> html(sheet).toByteArray()
        ExportFormat.Word -> docx(sheet)
        ExportFormat.Pdf -> throw IllegalArgumentException("PDF is drawn by the app")
    }

    // ------------------------------------------------------------ Markdown

    /** Markdown as other apps read it: links to the app's own targets become their words. */
    fun markdown(sheet: NoteSheet): String = buildString {
        append("# ").append(sheet.title).append("\n\n")
        sheet.meta.forEach { append("*").append(it).append("*  \n") }
        if (sheet.meta.isNotEmpty()) append("\n")
        for (b in NoteMarkup.parse(sheet.body)) {
            when (b) {
                is Block.Heading -> append("#".repeat((b.level + 1).coerceAtMost(4))).append(' ').append(md(b.inlines)).append("\n\n")
                is Block.Paragraph -> append(md(b.inlines)).append("\n\n")
                is Block.Item -> append("  ".repeat(b.indent)).append(if (b.number != null) "${b.number}. " else "- ").append(md(b.inlines)).append("\n")
                is Block.Quote -> append("> ").append(md(b.inlines)).append("\n\n")
                Block.Rule -> append("---\n\n")
            }
        }
    }.trimEnd() + "\n"

    private fun md(xs: List<Inline>): String = buildString {
        for (x in xs) when (x) {
            is Inline.Text -> append(x.text)
            is Inline.Bold -> append("**").append(md(x.children)).append("**")
            is Inline.Italic -> append("*").append(md(x.children)).append("*")
            is Inline.Link -> append(x.label)
        }
    }

    // ------------------------------------------------------------ plain text

    fun text(sheet: NoteSheet): String = buildString {
        append(sheet.title).append('\n').append("=".repeat(sheet.title.length.coerceIn(3, 60))).append("\n")
        sheet.meta.forEach { append(it).append('\n') }
        append('\n')
        for (b in NoteMarkup.parse(sheet.body)) {
            when (b) {
                is Block.Heading -> {
                    val t = NoteMarkup.plain(b.inlines)
                    append(if (b.level == 1) t.uppercase() else t).append('\n')
                    if (b.level <= 2) append((if (b.level == 1) "-" else "·").repeat(t.length.coerceIn(3, 60))).append('\n')
                    append('\n')
                }
                is Block.Paragraph -> append(NoteMarkup.plain(b.inlines)).append("\n\n")
                is Block.Item -> append("   ".repeat(b.indent)).append(if (b.number != null) "${b.number}. " else "• ").append(NoteMarkup.plain(b.inlines)).append('\n')
                is Block.Quote -> append("    « ").append(NoteMarkup.plain(b.inlines)).append(" »\n\n")
                Block.Rule -> append("* * *\n\n")
            }
        }
    }.trimEnd() + "\n"

    // ------------------------------------------------------------ HTML

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun htmlInline(xs: List<Inline>): String = buildString {
        for (x in xs) when (x) {
            is Inline.Text -> append(esc(x.text))
            is Inline.Bold -> append("<strong>").append(htmlInline(x.children)).append("</strong>")
            is Inline.Italic -> append("<em>").append(htmlInline(x.children)).append("</em>")
            is Inline.Link -> append("<span class=\"ref\">").append(esc(x.label)).append("</span>")
        }
    }

    /** A page that prints well: serif type, a narrow measure, the meta lines in small capitals. */
    fun html(sheet: NoteSheet): String = buildString {
        append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>").append(esc(sheet.title)).append("</title><style>")
        append("body{font-family:Georgia,'Gentium Book Plus',serif;max-width:40em;margin:2em auto;padding:0 1em;line-height:1.55;color:#2b2118;background:#fffdf8}")
        append("h1{font-size:1.7em;margin-bottom:.2em}h2{font-size:1.3em;margin-top:1.4em;border-bottom:1px solid #d8c9a8}h3{font-size:1.1em}h4{font-size:1em}")
        append(".meta{color:#7a6a55;font-variant:small-caps;margin:0}blockquote{border-left:3px solid #b08d57;margin:1em 0;padding:.2em 1em;color:#4a3b2a;font-style:italic}")
        append(".ref{color:#8b2b1c;font-weight:bold}hr{border:0;border-top:1px solid #d8c9a8;margin:1.5em 0}")
        append("</style></head><body>\n<h1>").append(esc(sheet.title)).append("</h1>\n")
        sheet.meta.forEach { append("<p class=\"meta\">").append(esc(it)).append("</p>\n") }
        var list: String? = null
        fun closeList() { list?.let { append("</").append(it).append(">\n") }; list = null }
        for (b in NoteMarkup.parse(sheet.body)) {
            if (b !is Block.Item) closeList()
            when (b) {
                is Block.Heading -> append("<h${b.level + 1}>").append(htmlInline(b.inlines)).append("</h${b.level + 1}>\n")
                is Block.Paragraph -> append("<p>").append(htmlInline(b.inlines)).append("</p>\n")
                is Block.Item -> {
                    val tag = if (b.number != null) "ol" else "ul"
                    if (list != tag) { closeList(); append("<").append(tag).append(">\n"); list = tag }
                    append("<li").append(if (b.indent > 0) " style=\"margin-left:1.5em\"" else "").append(">").append(htmlInline(b.inlines)).append("</li>\n")
                }
                is Block.Quote -> append("<blockquote>").append(htmlInline(b.inlines)).append("</blockquote>\n")
                Block.Rule -> append("<hr>\n")
            }
        }
        closeList()
        append("</body></html>\n")
    }

    // ------------------------------------------------------------ Word (.docx)

    private fun xml(s: String) = esc(s).replace("'", "&apos;")

    private fun runs(xs: List<Inline>, bold: Boolean = false, italic: Boolean = false, ref: Boolean = false): String = buildString {
        for (x in xs) when (x) {
            is Inline.Text -> if (x.text.isNotEmpty()) {
                append("<w:r>")
                if (bold || italic || ref) {
                    append("<w:rPr>")
                    if (bold || ref) append("<w:b/>")
                    if (italic) append("<w:i/>")
                    if (ref) append("<w:color w:val=\"8B2B1C\"/>")
                    append("</w:rPr>")
                }
                append("<w:t xml:space=\"preserve\">").append(xml(x.text)).append("</w:t></w:r>")
            }
            is Inline.Bold -> append(runs(x.children, true, italic, ref))
            is Inline.Italic -> append(runs(x.children, bold, true, ref))
            is Inline.Link -> append(runs(listOf(Inline.Text(x.label)), bold, italic, true))
        }
    }

    private fun para(style: String?, body: String, indentTwips: Int = 0): String = buildString {
        append("<w:p>")
        if (style != null || indentTwips > 0) {
            append("<w:pPr>")
            if (style != null) append("<w:pStyle w:val=\"").append(style).append("\"/>")
            if (indentTwips > 0) append("<w:ind w:left=\"").append(indentTwips).append("\" w:hanging=\"283\"/>")
            append("</w:pPr>")
        }
        append(body).append("</w:p>")
    }

    /** A Word document: Title, Heading 1–3, Quote and list paragraphs; bullets and numbers written in the text. */
    fun docx(sheet: NoteSheet): ByteArray {
        val body = buildString {
            append(para("Title", runs(listOf(Inline.Text(sheet.title)))))
            sheet.meta.forEach { append(para("Subtitle", runs(listOf(Inline.Text(it)))) ) }
            for (b in NoteMarkup.parse(sheet.body)) {
                append(
                    when (b) {
                        is Block.Heading -> para("Heading${b.level}", runs(b.inlines))
                        is Block.Paragraph -> para(null, runs(b.inlines))
                        is Block.Item -> para("ListParagraph", runs(listOf(Inline.Text(if (b.number != null) "${b.number}.\t" else "•\t")) + b.inlines), 567 * (b.indent + 1))
                        is Block.Quote -> para("Quote", runs(b.inlines))
                        Block.Rule -> para(null, "<w:r><w:t>* * *</w:t></w:r>")
                    },
                )
            }
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\" w:header=\"709\" w:footer=\"709\" w:gutter=\"0\"/></w:sectPr>" +
            "</w:body></w:document>"
        val styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Georgia\" w:hAnsi=\"Georgia\" w:cs=\"Georgia\"/><w:sz w:val=\"23\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            style("Normal", "Normal", "", "") +
            style("Title", "Title", "<w:spacing w:after=\"60\"/>", "<w:b/><w:sz w:val=\"40\"/>") +
            style("Subtitle", "Subtitle", "<w:spacing w:after=\"0\"/>", "<w:smallCaps/><w:color w:val=\"7A6A55\"/>") +
            style("Heading1", "heading 1", "<w:keepNext/><w:spacing w:before=\"360\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/>", "<w:b/><w:sz w:val=\"32\"/>") +
            style("Heading2", "heading 2", "<w:keepNext/><w:spacing w:before=\"240\" w:after=\"80\"/><w:outlineLvl w:val=\"1\"/>", "<w:b/><w:sz w:val=\"27\"/>") +
            style("Heading3", "heading 3", "<w:keepNext/><w:spacing w:before=\"200\" w:after=\"60\"/><w:outlineLvl w:val=\"2\"/>", "<w:b/><w:i/><w:sz w:val=\"24\"/>") +
            style("Quote", "Quote", "<w:ind w:left=\"567\" w:right=\"567\"/>", "<w:i/><w:color w:val=\"4A3B2A\"/>") +
            style("ListParagraph", "List Paragraph", "<w:spacing w:after=\"40\"/>", "") +
            "</w:styles>"
        val types = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        val docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, content) in listOf(
                "[Content_Types].xml" to types, "_rels/.rels" to rels, "word/document.xml" to document,
                "word/styles.xml" to styles, "word/_rels/document.xml.rels" to docRels,
            )) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun style(id: String, name: String, pPr: String, rPr: String) =
        "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$name\"/>" +
            (if (id != "Normal") "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" else "<w:qFormat/>") +
            (if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else "") + (if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else "") + "</w:style>"
}
