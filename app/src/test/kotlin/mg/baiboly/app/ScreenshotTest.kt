package mg.baiboly.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders the main screens to PNG files in docs/screenshots so the design can
 * be reviewed without a phone. Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private val out = File(System.getProperty("user.dir")!!).parentFile!!.resolve("docs/screenshots").apply { mkdirs() }

    private fun settle(cond: () -> Boolean = { true }) {
        for (i in 0 until 300) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            if (cond()) break
            Thread.sleep(30)
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun open(p: Passage) {
        vm.openPassage(p)
        settle { vm.chapter?.book == p.book && vm.chapter?.chapter == p.chapter }
    }

    @Test fun screens() {
        settle { vm.ready && vm.chapter != null }
        // a small service list, reading the current item
        vm.addToList(listOf(Passage(43, 3, 16, 16), Passage(49, 2, 8, 9), Passage(19, 23)))
        settle { vm.chapter?.book == 43 }
        vm.toggleSide(1)
        settle()
        shot("01-reader-light-mg")

        vm.changeTheme(ThemeMode.Dark)
        settle()
        shot("02-reader-dark-mg")

        vm.changeTheme(ThemeMode.Light)
        open(Passage(19, 23))
        shot("03-psalm-23-light")

        vm.screen = Screen.Settings
        settle()
        shot("04-settings")
        vm.screen = Screen.Main
        settle()

        // Catholic Bible: Tobit in the World English Bible, Catholic Edition
        vm.chooseUiLanguage("en")
        vm.setTranslation("webc")
        open(Passage(67, 1))
        shot("05-tobit-catholic-en")

        // α / א: an Old Testament chapter with study mode on
        vm.chooseUiLanguage("fr")
        vm.setTranslation("lsg1910")
        open(Passage(1, 1))
        if (!vm.studyMode) vm.toggleStudyMode()
        settle()
        shot("06-genesis-aleph-fr")
    }
}
