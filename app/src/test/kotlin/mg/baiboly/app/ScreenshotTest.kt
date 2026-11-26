package mg.baiboly.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performScrollToNode
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

    /** Lets background loading (databases on the io thread) finish, frame by frame. */
    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        rule.waitForIdle()
    }

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

    /** Verses as running paragraphs (Settings, Appearance, Verses). */
    @Test fun paragraphs() {
        settle { vm.ready && vm.chapter != null }
        vm.changeVerseLayout(VerseLayout.Paragraphs)
        open(Passage(43, 1, 10, 14))
        shot("reader-paragraphs-mg")
        // Advanced: left-aligned, no word breaks, a space between paragraphs, wide margins
        vm.changeJustify(false); vm.changeHyphenate(false)
        vm.changeParagraphBreak(ParagraphBreak.Space); vm.changePageMargin(PageMargin.Wide)
        settle()
        shot("reader-paragraphs-plain-mg")
        vm.resetAppearance()
    }

    @Test fun screens() {
        settle { vm.ready && vm.chapter != null }
        // Casual mode: reading, with the bottom navigation
        open(Passage(43, 3))
        shot("01-reader-casual-mg")

        vm.changeTheme(ThemeMode.Dark)
        settle()
        shot("02-reader-dark-mg")
        vm.changeTheme(ThemeMode.Light)

        // Church mode: a small service list, previous/next passage bar
        vm.changeMode(AppMode.Church)
        vm.addToList(listOf(Passage(43, 3, 16, 16), Passage(49, 2, 8, 9), Passage(19, 23)))
        settle { vm.chapter?.book == 43 }
        vm.toggleSide(1)
        settle()
        shot("03-church-service-mg")

        vm.screen = Screen.Settings
        settle()
        shot("04-settings")
        vm.screen = Screen.Main
        settle()

        // Catholic Bible: Tobit in the World English Bible, Catholic Edition
        vm.changeMode(AppMode.Casual)
        vm.changeTradition(Tradition.Catholic)
        vm.chooseUiLanguage("en")
        vm.setTranslation("webc")
        open(Passage(67, 1))
        shot("05-tobit-catholic-en")

        vm.screen = Screen.Books
        settle()
        shot("06-books-en")
        vm.screen = Screen.Main
        settle()

        // Study mode: an Old Testament chapter (א), map, dictionary
        vm.chooseUiLanguage("fr")
        vm.setTranslation("lsg1910")
        vm.changeMode(AppMode.Study)
        open(Passage(1, 1))
        shot("07-study-genesis-fr")

        vm.chooseUiLanguage("mg")
        vm.setTranslation("mg1865")
        open(Passage(2, 14))
        vm.mapForChapter = true
        vm.screen = Screen.Map
        pump(4000)
        shot("08-map-exodus-14")
        vm.screen = Screen.Dictionary
        pump(2500)
        shot("09-dictionary")
        vm.screen = Screen.Library
        settle()
        shot("10-library")
        vm.screen = Screen.Main
        settle()
    }

    /** One reader, one selection and one Settings page per theme (light), and the reader at night. */
    @Test fun themes() {
        settle { vm.ready && vm.chapter != null }
        vm.chooseUiLanguage("en")
        for ((i, st) in Style.entries.withIndex()) {
            vm.changeStyle(st)
            vm.screen = Screen.Main
            open(Passage(23, 40))
            shot("t${i + 1}a-${st.name.lowercase()}-reader")
            vm.screen = Screen.Settings
            settle()
            shot("t${i + 1}b-${st.name.lowercase()}-settings")
            vm.screen = if (st == Style.Page) Screen.Menu else Screen.Library
            settle()
            shot("t${i + 1}c-${st.name.lowercase()}-${if (st == Style.Page) "menu" else "library"}")
            vm.changeTheme(ThemeMode.Dark)
            vm.screen = Screen.Main
            settle()
            shot("t${i + 1}d-${st.name.lowercase()}-night")
            vm.changeTheme(ThemeMode.Light)
            if (st == Style.Navy) {
                // the side menu, the Old Testament unfolded
                vm.drawerOpen = true
                pump(1200)
                rule.onNodeWithTag("drawer-testament:0", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
                pump(600)
                shot("t${i + 1}e-navy-menu")
                vm.drawerOpen = false
                pump(800)
            }
        }
        vm.changeStyle(Style.Manuscript)
        settle()
    }

    private fun click(tag: String) {
        if (tag.startsWith("period:")) rule.onNodeWithTag("timeline").performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
        rule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(1500)
    }

    /** The map: decluttered at the opening zoom, then a few periods of the timeline and the other base maps. */
    @Test fun maps() {
        settle { vm.ready && vm.chapter != null }
        vm.chooseUiLanguage("en")
        vm.changeMode(AppMode.Study)
        vm.mapForChapter = false
        vm.screen = Screen.Map
        pump(4000)
        shot("m1-all-terrain")
        click("period:Divided")
        shot("m2-israel-and-judah")
        click("period:Jesus")
        shot("m3-life-of-jesus")
        // zoom out for Paul's world
        rule.onNodeWithTag("map").performTouchInput {
            pinch(center - androidx.compose.ui.geometry.Offset(300f, 0f), center - androidx.compose.ui.geometry.Offset(60f, 0f),
                center + androidx.compose.ui.geometry.Offset(300f, 0f), center + androidx.compose.ui.geometry.Offset(60f, 0f))
        }
        click("period:Church")
        shot("m4-early-church")
        click("period:all")
        click("map-layers")
        click("map-base:Today")
        shot("m5-today")
        click("map-layers")
        click("map-base:Ancient")
        shot("m6-ancient")
        // regions drawn whole, journeys of the Bible
        click("map-layers")
        click("map-base:Terrain")
        click("map-regions")
        shot("m7-regions")
        val edom = "region:" + vm.library.places.first { it.name == "Edom" }.id
        rule.onNodeWithTag("regions-list").performScrollToNode(androidx.compose.ui.test.hasTestTag(edom))
        click(edom)
        pump(1500)
        shot("m8-region-edom")
        click("map-routes")
        rule.onNodeWithTag("journeys-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("journey:paul2"))
        click("journey:paul2")
        pump(1500)
        shot("m9-journey-paul")
        // the units converter (Study mode, Dictionary page)
        vm.dictionaryUnits = true
        vm.screen = Screen.Dictionary
        pump(1500)
        shot("u1-units")
    }

    /** The compact reader, and a selected verse with its actions (Study first). */
    @Test fun passageTools() {
        settle { vm.ready && vm.chapter != null }
        vm.chooseUiLanguage("en")
        vm.changeMode(AppMode.Casual)
        vm.setTranslation("mg1865")
        vm.openPassage(mg.baiboly.core.ref.Passage(1, 6, 15, 15))
        settle { vm.chapter?.book == 1 && vm.chapter?.chapter == 6 }
        pump(1500)
        shot("p0-reader-compact")
        rule.onNodeWithTag("verse:15", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(800)
        shot("p1-selection-bar")
        // the Study panel opens in a sheet (its own window, not in these screenshots): see UiAcceptanceTest.passageStudyTools
    }

    /** Sermon notes and studies, the church-history timeline, the text formatting group, units both ways. */
    @Test fun newTools() {
        settle { vm.ready && vm.chapter != null }
        vm.chooseUiLanguage("en")
        vm.setTranslation("mg1865")
        // a sermon note in Church mode: date and the service's passages recorded
        vm.changeMode(AppMode.Church)
        vm.addToList(listOf(Passage(45, 8, 28, 30), Passage(43, 6, 37, 40)))
        settle { vm.list.items.size >= 2 }
        vm.newDocument("sermon")
        settle { vm.screen == Screen.Document }
        val draft = vm.openDocument!!
        vm.closeDocument(); settle()
        vm.saveDocument(draft.copy(body = "# The golden chain\n- Foreknown, predestined, called\n- Justified, glorified: [Rom 8: 30](bible:45.8.30-30)\n## None is lost\n- [Jao 6: 39](bible:43.6.39-39): *I should lose nothing*"))
        settle { vm.documents.any { it.body.contains("golden chain") } }
        vm.closeDocument(); settle()
        vm.openDocument(vm.documents.first { it.body.contains("golden chain") })
        settle()
        shot("n1-sermon-note")
        rule.onNodeWithTag("doc-preview", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(800)
        shot("n2-sermon-note-read")
        vm.closeDocument()
        // a study in Study mode, its outline and tools
        vm.changeMode(AppMode.Study)
        vm.newDocument("study", Passage(45, 8, 28, 30))
        settle { vm.screen == Screen.Document }
        pump(800)
        shot("n3-study-new")
        vm.closeDocument()
        vm.screen = Screen.Documents
        settle()
        shot("n4-my-notes")
        // the timeline on to today
        vm.dictionaryTool = StudyTool.Timeline
        vm.screen = Screen.Dictionary
        pump(2000)
        rule.onNodeWithTag("tl-to-today", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(2500)
        shot("h6-timeline-to-today")
        rule.onNodeWithTag("tl-mode-list", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(1500)
        shot("h7-timeline-list-by-era")
        // page layout, in its own group with the usual icons
        vm.screen = Screen.Settings
        vm.settingsPage = "page"
        settle()
        pump(800)
        rule.onNodeWithTag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("settings-justify:true"))
        pump(400)
        shot("s1-text-formatting")
        vm.settingsPage = null
        // units, from today's measures to the Bible's
        vm.dictionaryTool = StudyTool.Units
        vm.screen = Screen.Dictionary
        pump(1500)
        rule.onNodeWithTag("units-swap", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(800)
        shot("u2-units-from-metres")
        vm.screen = Screen.Main
        vm.changeMode(AppMode.Casual)
        settle()
    }
}
