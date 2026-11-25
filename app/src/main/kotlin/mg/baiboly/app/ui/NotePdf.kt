package mg.baiboly.app.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import mg.baiboly.core.notes.Block
import mg.baiboly.core.notes.Inline
import mg.baiboly.core.notes.NoteMarkup
import mg.baiboly.core.notes.NoteSheet
import java.io.OutputStream

/** A note as a PDF: A4 pages, serif type, headings, points and quotations as on screen. */
object NotePdf {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 56f

    private fun spans(xs: List<Inline>): CharSequence {
        val sb = SpannableStringBuilder()
        fun walk(ys: List<Inline>, bold: Boolean, italic: Boolean) {
            for (x in ys) when (x) {
                is Inline.Text -> {
                    val start = sb.length
                    sb.append(x.text)
                    val style = when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> null }
                    if (style != null) sb.setSpan(StyleSpan(style), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Inline.Bold -> walk(x.children, true, italic)
                is Inline.Italic -> walk(x.children, bold, true)
                is Inline.Link -> {
                    val start = sb.length
                    sb.append(x.label)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(Color.rgb(0x8B, 0x2B, 0x1C)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        walk(xs, false, false)
        return sb
    }

    private fun paint(size: Float, style: Int = Typeface.NORMAL, colour: Int = Color.rgb(0x2B, 0x21, 0x18)) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = Typeface.create(Typeface.SERIF, style); color = colour
    }

    fun write(sheet: NoteSheet, out: OutputStream) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = MARGIN
            if (pageNo > 1) page!!.canvas.drawText("$pageNo", W / 2f, H - 28f, paint(9f).apply { textAlign = Paint.Align.CENTER })
        }
        fun draw(text: CharSequence, p: TextPaint, indent: Float = 0f, before: Float = 0f, after: Float = 6f, prefix: String? = null) {
            val width = (W - 2 * MARGIN - indent).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f).build()
            if (page == null || y + before + layout.height > H - MARGIN) newPage() else y += before
            val c = page!!.canvas
            if (prefix != null) c.drawText(prefix, MARGIN + indent - 16f, y - p.ascent(), p)
            c.save(); c.translate(MARGIN + indent, y); layout.draw(c); c.restore()
            y += layout.height + after
        }
        newPage()
        draw(sheet.title, paint(22f, Typeface.BOLD), after = 4f)
        sheet.meta.forEach { draw(it, paint(10f, Typeface.ITALIC, Color.rgb(0x7A, 0x6A, 0x55)), after = 1f) }
        y += 10f
        val body = paint(11.5f)
        for (b in NoteMarkup.parse(sheet.body)) when (b) {
            is Block.Heading -> draw(spans(b.inlines), paint(when (b.level) { 1 -> 17f; 2 -> 14f; else -> 12.5f }, Typeface.BOLD), before = 10f, after = 4f)
            is Block.Paragraph -> draw(spans(b.inlines), body)
            is Block.Item -> draw(spans(b.inlines), body, indent = 22f + 18f * b.indent, after = 2f, prefix = if (b.number != null) "${b.number}." else "•")
            is Block.Quote -> draw(spans(b.inlines), paint(11.5f, Typeface.ITALIC, Color.rgb(0x4A, 0x3B, 0x2A)), indent = 24f, before = 4f, after = 8f)
            Block.Rule -> { y += 8f; page?.canvas?.drawLine(W / 2f - 40f, y, W / 2f + 40f, y, paint(1f)); y += 12f }
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }
}
