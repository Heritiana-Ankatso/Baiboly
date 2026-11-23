package mg.baiboly.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression: in Study mode, "Back to my place" stayed on every page once a service list had a
 * current passage. It is meant for Church mode, and in Study mode only just after a jump.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class BackToMyPlaceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun settle(until: () -> Boolean = { true }) {
        for (i in 0 until 300) {
            if (vm.ready && until()) break
            ShadowLooper.idleMainLooper(); rule.mainClock.advanceTimeByFrame(); Thread.sleep(20)
        }
        rule.waitForIdle()
    }

    private fun buttonShown() = rule.onAllNodesWithTag("back-to-place").fetchSemanticsNodes().isNotEmpty()

    @Test fun studyModeShowsItOnlyAfterAJump() {
        settle { vm.chapter != null }
        val item = Passage(60, 2, 4, 7) // 1 Pet 2:4-7, the list passage of a service
        vm.addToList(listOf(item))
        settle { vm.location.focus == item }

        // Study mode, reading elsewhere by choice (Books, chapter buttons): no button
        vm.changeMode(AppMode.Study)
        vm.openChapter(40, 1)
        settle { vm.chapter?.book == 40 }
        assertTrue(vm.isAway)
        assertFalse(vm.showBackToMyPlace); assertFalse(buttonShown())
        vm.nextChapter()
        settle { vm.chapter?.chapter == 2 }
        assertFalse(vm.showBackToMyPlace)

        // a jump (reference, search, Found N verses): the button is offered ...
        vm.openPassage(Passage(45, 1, 28, 28))
        settle { vm.chapter?.book == 45 }
        assertTrue(vm.showBackToMyPlace); assertTrue(buttonShown())
        // ... and goes once the reader turns to the next chapter
        vm.nextChapter()
        settle { vm.chapter?.chapter == 2 }
        assertFalse(vm.showBackToMyPlace); assertFalse(buttonShown())

        // Church mode: always while away from the list passage
        vm.changeMode(AppMode.Church)
        settle()
        assertTrue(vm.showBackToMyPlace)
        vm.backToMyPlace()
        settle { vm.location.focus == item }
        assertFalse(vm.showBackToMyPlace)
    }
}
