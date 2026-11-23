package mg.baiboly.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs
import kotlin.test.fail

/**
 * Reported on a phone (1080 px wide): in paragraph layout the right margin of the text is about
 * twice the left one. Renders Matthew 1 at that size, in the reported themes and typesetting
 * settings, on the Android versions the app targets, and measures the ink on each side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-420dpi", sdk = [34, 35])
class ReaderMarginTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private val out = File(System.getProperty("user.dir")!!).parentFile!!.resolve("app/build/reports/tests/margins").apply { mkdirs() }

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeByFrame(); Thread.sleep(30)
        }
        rule.waitForIdle()
    }

    private val paragraph = SemanticsMatcher("paragraph text") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("paragraph:") == true
    }

    /** Left and right blank space (px) beside the ink of the paragraphs, and the paragraph box. */
    private fun measure(name: String): String {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out.resolve("$name-sdk${android.os.Build.VERSION.SDK_INT}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val boxes = rule.onAllNodes(paragraph, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val box = boxes.maxByOrNull { it.height } ?: return "$name: no paragraph on screen"
        val y0 = maxOf(box.top.toInt() + 4, (view.height * 0.35).toInt())
        val y1 = minOf(box.bottom.toInt() - 4, (view.height * 0.80).toInt())
        var left = view.width; var right = -1
        for (y in y0 until y1 step 2) {
            val bg = bmp.getPixel(1, y)
            for (x in 0 until view.width) {
                val p = bmp.getPixel(x, y)
                val d = abs(android.graphics.Color.red(p) - android.graphics.Color.red(bg)) +
                    abs(android.graphics.Color.green(p) - android.graphics.Color.green(bg)) +
                    abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(bg))
                if (d > 150) { if (x < left) left = x; if (x > right) right = x }
            }
        }
        val l = left; val r = view.width - 1 - right
        return "$name sdk=${android.os.Build.VERSION.SDK_INT} width=${view.width}: ink left=$l right=$r (diff ${r - l}); " +
            "box ${box.left.toInt()}..${box.right.toInt()}"
    }

    @Test fun marginsAreEven() {
        for (i in 0 until 300) { if (vm.ready && vm.chapter != null) break; pump(30) }
        vm.chooseUiLanguage("mg")
        vm.changeVerseLayout(VerseLayout.Paragraphs)
        vm.openChapter(40, 1)
        pump(1500)
        val report = mutableListOf<String>()
        val bad = mutableListOf<String>()
        fun check(name: String) {
            pump(1200)
            val line = measure(name)
            report += line
            val m = Regex("left=(\\d+) right=(\\d+)").find(line)
            if (m == null || abs(m.groupValues[1].toInt() - m.groupValues[2].toInt()) > 8) bad += line
        }
        for (style in listOf(Style.Page, Style.Glass, Style.Manuscript)) {
            vm.changeStyle(style)
            vm.changeJustify(true); vm.changeHyphenate(true); vm.changeParagraphBreak(ParagraphBreak.Indent); vm.changePageMargin(PageMargin.Normal)
            check("${style.name}-default")
            if (style == Style.Glass) { vm.changeTextFont(TextFont.Sans); check("Glass-sans") }
            vm.changeParagraphBreak(ParagraphBreak.Space); check("${style.name}-no-indent")
            vm.changeParagraphBreak(ParagraphBreak.Indent); vm.changeJustify(false); check("${style.name}-left-aligned")
            vm.changeJustify(true); vm.changeHyphenate(false); check("${style.name}-no-word-breaks")
            vm.changeHyphenate(true)
        }
        vm.changeUiDensity(UiDensity.Normal); check("Manuscript-normal-size")
        println("READER MARGINS\n" + report.joinToString("\n"))
        if (bad.isNotEmpty()) fail("Uneven margins:\n" + bad.joinToString("\n") + "\n\nAll:\n" + report.joinToString("\n"))
    }

    /** Reported: Settings › Appearance › Interface size ran to the screen edges; every choice is inset 20 dp. */
    @Test fun settingsChoicesAreInset() {
        for (i in 0 until 300) { if (vm.ready) break; pump(30) }
        vm.screen = Screen.Settings
        pump(1000)
        val view = rule.activity.window.decorView
        val minInset = 16 * rule.activity.resources.displayMetrics.density * vm.uiDensity.scale
        val bad = mutableListOf<String>()
        // first and last option of each row
        for ((first, last) in listOf("settings-density:Compact" to "settings-density:Normal",
            "settings-spacing:Compact" to "settings-spacing:Airy", "settings-layout:Lines" to "settings-layout:Paragraphs")) {
            rule.onNode(androidx.compose.ui.test.hasTestTag("settings-list")).performScrollToNode(androidx.compose.ui.test.hasTestTag(first))
            pump(300)
            val l = rule.onNode(androidx.compose.ui.test.hasTestTag(first), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
            val r = view.width - rule.onNode(androidx.compose.ui.test.hasTestTag(last), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right
            val line = "${first.substringBefore(':')}: left=${l.toInt()} right=${r.toInt()} (min ${minInset.toInt()})"
            println(line)
            if (l < minInset || r < minInset) bad += line
        }
        if (bad.isNotEmpty()) fail("Settings choices touching the screen edge:\n" + bad.joinToString("\n"))
    }
}
