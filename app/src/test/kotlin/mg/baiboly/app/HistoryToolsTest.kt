package mg.baiboly.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.HistoryRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Family tree, timeline, confessions and councils (docs/ideas.md A1-A3, B1-B2). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class HistoryToolsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private val out = File(System.getProperty("user.dir")!!).parentFile!!.resolve("docs/screenshots").apply { mkdirs() }

    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
        rule.waitForIdle()
    }

    private fun settle(cond: () -> Boolean) {
        for (i in 0 until 300) {
            ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeByFrame()
            if (cond()) break
            Thread.sleep(30)
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump(600)
    }

    @Test fun data() {
        settle { vm.ready }
        val h = vm.chronicle
        // B1: Abraham, his father Terah, Isaac among his children; the father line reaches Adam through Noah
        val abraham = h.searchPeople("Abraham").first { it.name == "Abraham" }
        val rel = h.relatives(abraham.id)
        assertTrue(rel.any { it.kind == "father" && it.person.name == "Terah" })
        assertTrue(rel.any { it.kind == "child" && it.person.name == "Isaac" })
        val line = h.fatherLine(abraham.id).map { it.name }
        assertTrue("Noah" in line && line.last() == "Adam", "father line: $line")
        // B2: Exodus year after Jones (1491 BC = astronomical -1490), in order
        assertEquals(-1490, h.timeline.first { it.title == "Exodus from Egypt" }.year)
        assertTrue(h.timeline.zipWithNext().all { (a, b) -> a.year <= b.year }, "timeline in order")
        assertTrue(h.timeline.first().year == -4003, "creation first")
        // A1: John 3:16 is a proof text of the Westminster Standards; citations are printed as cited
        val cites = h.creedCitations(43, 3, 16, 16)
        assertTrue(cites.any { it.doc == "wcf" } && cites.any { it.doc == "wsc" || it.doc == "wlc" }, "citations: ${cites.map { it.doc }}")
        val wsc1 = h.creedItems("wsc").first()
        assertEquals("WSC Q&A 1", HistoryRepository.citation(h.creedDocs.first { it.id == "wsc" }, wsc1))
        assertTrue(wsc1.text.startsWith("Man's chief end is to glorify God"))
        // the Apostles' Creed in its historic form
        assertTrue(h.creedItems("apostles").single().text.contains("descended into hell"))
        // A2: councils with the documents they issued
        assertEquals(listOf("wcf", "wlc", "wsc"), h.councils.first { it.id == "westminster" }.docs)
        // A3: the tradition decides what normal study shows
        vm.changeTradition(Tradition.Protestant)
        assertEquals(setOf("creed", "reformed"), vm.ownFamilies())
        vm.changeTradition(Tradition.Catholic)
        assertEquals(setOf("creed"), vm.ownFamilies())
        vm.changeTradition(Tradition.Protestant)
    }

    @Test fun screens() {
        settle { vm.ready && vm.chapter != null }
        vm.chooseUiLanguage("en")
        vm.changeMode(AppMode.Study)
        vm.screen = Screen.Dictionary
        pump(1000)
        click("dict-tool:People")
        shot("h1-people")
        vm.openPerson(vm.chronicle.searchPeople("Isaac").first { it.name == "Isaac" }.id)
        pump(1500)
        rule.onNodeWithTag("person-view").assertExists()
        shot("h2-person-isaac")
        click("dict-tool:Timeline")
        pump(800)
        rule.onNodeWithTag("timeline").assertExists()
        shot("h3-timeline")
        click("dict-tool:Confessions")
        pump(800)
        shot("h4-confessions")
        vm.openCreed("heidelberg", 0)
        pump(1500)
        rule.onNodeWithTag("creed-item:heidelberg:0").assertExists()
        shot("h5-heidelberg")
        // from a passage: the Study panel lists the people, events and confession articles
        val p = Passage(43, 3, 16, 16)
        kotlinx.coroutines.runBlocking {
            assertTrue(vm.creedsCiting(p).isNotEmpty())
            assertTrue(vm.peopleInPassage(Passage(1, 22, 1, 3)).any { it.name == "Abraham" })
            assertTrue(vm.eventsInPassage(Passage(1, 22, 1, 3)).isNotEmpty())
        }
    }
}
