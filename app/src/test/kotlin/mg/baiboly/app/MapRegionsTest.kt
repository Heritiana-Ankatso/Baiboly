package mg.baiboly.app

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reported: finding a region meant scrolling ~190 names. The Regions panel filters, sorts A–Z and by kind. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class MapRegionsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun pump(ms: Long = 600) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeByFrame(); Thread.sleep(30) }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        pump()
    }

    /** Region rows shown in the list, in order (names from the place data). */
    private fun shown(): List<String> {
        val ids = rule.onAllNodes(SemanticsMatcher("region row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("region:") == true
        }, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix("region:") }
        val byId = vm.library.places.associateBy { it.id }
        return ids.mapNotNull { byId[it]?.displayName }
    }

    @Test fun findARegion() {
        for (i in 0 until 300) { if (vm.ready && vm.chapter != null) break; pump(30) }
        vm.chooseUiLanguage("en")
        vm.changeMode(AppMode.Study)
        vm.mapForChapter = false
        vm.screen = Screen.Map
        pump(2000)
        click("map-regions")
        // by name, accents and case ignored
        rule.onNodeWithTag("regions-filter").performTextInput("edo")
        pump()
        assertEquals("Edom", shown().first(), "a name starting with the text comes first")
        // A–Z
        rule.onNodeWithTag("regions-filter").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetText) { it(androidx.compose.ui.text.AnnotatedString("")) }
        pump()
        click("regions-sort-az")
        val az = shown()
        assertTrue(az.size >= 5)
        assertEquals(az, az.sortedBy { mg.baiboly.core.text.TextNorm.norm(it) }, "A–Z order")
        // by kind: only seas, rivers and islands
        // the kind chips follow the order chips in a row that scrolls sideways
        rule.onNodeWithTag("regions-chips").performScrollToNode(androidx.compose.ui.test.hasTestTag("regions-kind:Waters"))
        click("regions-kind:Waters")
        val waters = vm.library.places.filter { it.type == "body of water" || it.type == "island" }.map { it.displayName }.toSet()
        assertTrue(shown().isNotEmpty() && shown().all { it in waters }, "only waters: ${shown()}")
    }
}
