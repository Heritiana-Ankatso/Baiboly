package mg.baiboly.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/** The Reformed commentaries: Calvin, the Geneva notes, Matthew Henry (Concise), in the Study panel. */
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
        assertEquals(listOf("calvin", "geneva", "mhcc"), vm.commentaries.available.map { it.id })
        val calvin = vm.commentaries.available.first()
        val rom828 = runBlocking { vm.onIo { vm.commentaries.notes(calvin, 45, 8, 28, 28) } }
        assertTrue(rom828.isNotEmpty())
        assertContains(rom828.first().body, "And we know")
        // Matthew Henry: a note that runs over a passage (John 3:16 lies in the note on 3:1-21)
        val mh = vm.commentaries.available.first { it.id == "mhcc" }
        val j316 = runBlocking { vm.onIo { vm.commentaries.notes(mh, 43, 3, 16, 16) } }
        assertEquals(1, j316.size); assertEquals(1, j316[0].v1); assertEquals(21, j316[0].v2)
        // in the Study panel
        vm.setTranslation("mg1865")
        vm.openPassage(Passage(45, 8, 28, 28))
        waitFor { vm.chapter?.book == 45 && vm.chapter?.chapter == 8 }
        rule.waitForIdle()
        tag("verse:28").tap()
        tag("action:Study").tap()
        tag("tab-commentary").tap()
        waitFor { runCatching { tag("comment:0").assertExists(); true }.getOrDefault(false) }
        tag("commentary:geneva").tap()
        waitFor { runCatching { tag("comment:0").assertExists(); true }.getOrDefault(false) }
    }
}
