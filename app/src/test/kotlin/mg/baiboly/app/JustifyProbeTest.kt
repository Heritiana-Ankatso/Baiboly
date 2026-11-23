package mg.baiboly.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.fail

/** Which text option leaves a gap on the right of justified text on Android 15 (temporary probe). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi", sdk = [34, 35])
class JustifyProbeTest {
    @get:Rule val rule = createComposeRule()

    private val text = "Ary ny taranaka rehetra hatramin’ i Abrahama ka hatramin’ i Davida dia taranaka efatra ambin’ ny folo; " +
        "ary hatramin’ i Davida ka hatramin’ ny nifindrana tany Babylona dia taranaka efatra ambin’ ny folo; ary hatramin’ ny " +
        "nifindrana tany Babylona ka hatramin’ i Kristy dia taranaka efatra ambin’ ny folo. Ary ny nahaterahan’ i Jesosy Kristy dia toy izao."

    private data class Case(val name: String, val style: TextStyle)

    @Test fun probe() {
        val base = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, textAlign = TextAlign.Justify)
        val cases = listOf(
            Case("justify-only", base),
            Case("simple", base.copy(lineBreak = LineBreak.Simple)),
            Case("heading", base.copy(lineBreak = LineBreak.Heading)),
            Case("paragraph", base.copy(lineBreak = LineBreak.Paragraph)),
            Case("paragraph+mg", base.copy(lineBreak = LineBreak.Paragraph, localeList = LocaleList("mg"))),
            Case("paragraph+mg+hyph", base.copy(lineBreak = LineBreak.Paragraph, localeList = LocaleList("mg"), hyphens = Hyphens.Auto)),
            Case("simple+mg+hyph", base.copy(lineBreak = LineBreak.Simple, localeList = LocaleList("mg"), hyphens = Hyphens.Auto)),
            Case("paragraph+en", base.copy(lineBreak = LineBreak.Paragraph, localeList = LocaleList("en"))),
            Case("start", base.copy(textAlign = TextAlign.Start)),
        )
        rule.setContent {
            Column {
                cases.forEach { c ->
                    Text(text, style = c.style, color = Color.Black,
                        modifier = Modifier.width(300.dp).background(Color.White).testTag(c.name))
                }
            }
        }
        val report = cases.map { c ->
            val bmp = rule.onNodeWithTag(c.name).captureToImage().asAndroidBitmap()
            var left = bmp.width; var right = -1
            for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width) {
                if (android.graphics.Color.red(bmp.getPixel(x, y)) < 128) { if (x < left) left = x; if (x > right) right = x }
            }
            "${c.name} sdk=${android.os.Build.VERSION.SDK_INT}: box=${bmp.width} ink left=$left right gap=${bmp.width - 1 - right}"
        }
        fail("JUSTIFY PROBE\n" + report.joinToString("\n"))
    }
}
