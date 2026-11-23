package mg.baiboly.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.test.fail

/** Which text option leaves a gap on the right of justified text on Android 15 (temporary probe). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-420dpi", sdk = [34, 35])
class JustifyProbeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val text = "Ary ny taranaka rehetra hatramin’ i Abrahama ka hatramin’ i Davida dia taranaka efatra ambin’ ny folo; " +
        "ary hatramin’ i Davida ka hatramin’ ny nifindrana tany Babylona dia taranaka efatra ambin’ ny folo; ary hatramin’ ny " +
        "nifindrana tany Babylona ka hatramin’ i Kristy dia taranaka efatra ambin’ ny folo."

    private data class Case(val name: String, val text: androidx.compose.ui.text.AnnotatedString, val style: TextStyle)

    /** The reader's paragraph, one ingredient at a time. */
    private fun verses(superscript: Boolean, nbsp: Boolean, shift: Boolean, soft: Boolean) =
        androidx.compose.ui.text.buildAnnotatedString {
            val parts = text.split(". ")
            parts.forEachIndexed { i, p ->
                if (i > 0) append(' ')
                if (superscript) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        baselineShift = if (shift) androidx.compose.ui.text.style.BaselineShift.Superscript else null)); append("${i + 16}"); pop()
                    append(if (nbsp) '\u00A0' else ' ')
                }
                append(if (soft) mg.baiboly.app.ui.malagasySoftHyphens(p).first else p)
                if (i < parts.lastIndex) append('.')
            }
        }

    @Test fun probe() {
        val base = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Justify, color = Color.Black)
        val plain = androidx.compose.ui.text.AnnotatedString(text)
        val mgHyph = base.copy(lineBreak = LineBreak.Paragraph, localeList = LocaleList("mg"), hyphens = Hyphens.Auto)
        val cases = listOf(
            Case("plain", plain, base),
            Case("paragraph-mg-hyph", plain, mgHyph),
            Case("soft-hyphens", verses(false, false, false, true), mgHyph),
            Case("superscript+nbsp", verses(true, true, true, false), base),
            Case("superscript+space", verses(true, false, true, false), base),
            Case("small-number+nbsp", verses(true, true, false, false), base),
            Case("indent", plain, base.copy(textIndent = androidx.compose.ui.text.style.TextIndent(firstLine = 22.sp))),
            Case("reader-like", verses(true, true, true, true), mgHyph.copy(textIndent = androidx.compose.ui.text.style.TextIndent(firstLine = 22.sp))),
        )
        rule.setContent {
            Column(Modifier.background(Color.White)) {
                cases.forEach { c ->
                    Text(c.text, style = c.style, modifier = Modifier.width(300.dp).background(Color.White).testTag(c.name))
                }
            }
        }
        repeat(20) { ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val report = cases.map { c ->
            val b = rule.onNodeWithTag(c.name).fetchSemanticsNode().boundsInWindow
            val x0 = b.left.toInt(); val x1 = b.right.toInt() - 1
            var left = Int.MAX_VALUE; var right = -1
            for (y in b.top.toInt() until b.bottom.toInt() step 2) for (x in x0..x1) {
                if (android.graphics.Color.red(bmp.getPixel(x, y)) < 128) { if (x < left) left = x; if (x > right) right = x }
            }
            "${c.name} sdk=${android.os.Build.VERSION.SDK_INT}: box=${x1 - x0 + 1} left gap=${left - x0} right gap=${x1 - right}"
        }
        fail("JUSTIFY PROBE\n" + report.joinToString("\n"))
    }
}
