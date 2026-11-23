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
@Config(qualifiers = "w411dp-h914dp-420dpi", sdk = [34, 35])
class JustifyProbeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val text = "Ary ny taranaka rehetra hatramin’ i Abrahama ka hatramin’ i Davida dia taranaka efatra ambin’ ny folo; " +
        "ary hatramin’ i Davida ka hatramin’ ny nifindrana tany Babylona dia taranaka efatra ambin’ ny folo; ary hatramin’ ny " +
        "nifindrana tany Babylona ka hatramin’ i Kristy dia taranaka efatra ambin’ ny folo."

    private data class Case(val name: String, val style: TextStyle?)

    @Test fun probe() {
        val base = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Justify, color = Color.Black)
        val cases = listOf(
            Case("compose-justify", base),
            Case("compose-simple", base.copy(lineBreak = LineBreak.Simple)),
            Case("compose-heading", base.copy(lineBreak = LineBreak.Heading)),
            Case("compose-paragraph", base.copy(lineBreak = LineBreak.Paragraph)),
            Case("compose-paragraph-mg-hyph", base.copy(lineBreak = LineBreak.Paragraph, localeList = LocaleList("mg"), hyphens = Hyphens.Auto)),
            Case("compose-simple-mg-hyph", base.copy(lineBreak = LineBreak.Simple, localeList = LocaleList("mg"), hyphens = Hyphens.Auto)),
            Case("compose-start", base.copy(textAlign = TextAlign.Start)),
            Case("textview-justify", null),
        )
        rule.setContent {
            Column(Modifier.background(Color.White)) {
                cases.forEach { c ->
                    val m = Modifier.width(300.dp).background(Color.White).testTag(c.name)
                    if (c.style != null) Text(text, style = c.style, modifier = m)
                    else AndroidView(factory = { ctx ->
                        android.widget.TextView(ctx).apply {
                            setText(text); textSize = 18f; setTextColor(android.graphics.Color.BLACK)
                            justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                        }
                    }, modifier = m)
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
