package mg.baiboly.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mg.baiboly.core.ref.Passage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Reformed commentaries: Calvin, the Geneva notes, Matthew Henry (Concise and Complete), and the book introductions, in the Study panel. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class CommentaryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun waitFor(timeout: Long = 15_000, what: () -> Boolean) = rule.waitUntil(timeout) { what() }
    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction {
        rule.waitForIdle(); performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle(); return this
    }

    @Test fun notesOnAPassage() {
        waitFor { vm.ready && vm.chapter != null }
        val repo = vm.commentaries
        val model = vm
        assertEquals(listOf("calvin", "geneva", "mhcc", "mhc"), repo.available.map { it.id })
        val calvin = repo.available.first()
        val rom828 = runBlocking { model.onIo { repo.notes(calvin, 45, 8, 28, 28) } }
        assertTrue(rom828.isNotEmpty())
        assertContains(rom828.first().body, "And we know")
        // Matthew Henry: a note that runs over a passage (John 3:16 lies in the note on 3:1-21)
        val mh = repo.available.first { it.id == "mhcc" }
        val j316 = runBlocking { model.onIo { repo.notes(mh, 43, 3, 16, 16) } }
        assertEquals(1, j316.size); assertEquals(1, j316[0].v1); assertEquals(21, j316[0].v2)
        // the complete Henry also has a note on Romans 8:30 (that part was completed by other ministers)
        val full = repo.available.first { it.id == "mhc" }
        val rom830 = runBlocking { model.onIo { repo.notes(full, 45, 8, 30, 30) } }
        assertTrue(rom830.isNotEmpty())
        assertContains(rom830.first().body, "predestinate")
        // in the Study panel
        if (vm.uiLanguage != "en") vm.chooseUiLanguage("en") // the English labels below
        vm.setTranslation("mg1865")
        vm.openPassage(Passage(45, 8, 28, 28))
        waitFor { vm.chapter?.book == 45 && vm.chapter?.chapter == 8 }
        rule.waitForIdle()
        tag("reader").performScrollToKey("45:8:v28")
        tag("verse:28").tap()
        tag("action:Study").tap()
        tag("tab-commentary").tap()
        waitFor { runCatching { tag("comment:0").assertExists(); true }.getOrDefault(false) }
        tag("commentary:geneva").tap()
        waitFor { runCatching { tag("comment:0").assertExists(); true }.getOrDefault(false) }
    }

    @Test fun bookIntroductionAndStoryline() {
        waitFor { vm.ready && vm.chapter != null }
        if (vm.uiLanguage != "en") vm.chooseUiLanguage("en")
        vm.setTranslation("mg1865")
        vm.openPassage(Passage(43, 3, 16, 16))
        waitFor { vm.chapter?.book == 43 && vm.chapter?.chapter == 3 }
        rule.waitForIdle()
        tag("reader").performScrollToKey("43:3:v16")
        tag("verse:16").tap()
        tag("action:Study").tap()
        tag("tab-introduction").tap()
        waitFor { runCatching { tag("book-intro").assertExists(); true }.getOrDefault(false) }
        // John belongs to the stage of Christ, whose section opens; the storyline lists the ten stages
        tag("stage:8").assertExists()
        tag("stage:10").tap()
    }
}
