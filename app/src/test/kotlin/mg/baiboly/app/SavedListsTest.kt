package mg.baiboly.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression: the delete button of Saved lists seemed to do nothing. Deleting the
 * list in use made a new empty list with the same name in its place, and
 * "New list" added one more identical empty list each time.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class SavedListsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun settle(vm: AppViewModel, until: () -> Boolean = { true }) {
        for (i in 0 until 300) {
            if (vm.ready && until()) break
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(20)
        }
        rule.waitForIdle()
    }

    @Test fun deletingRemovesTheRow() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        settle(vm) { vm.chapter != null }
        vm.refreshSavedLists(); settle(vm) { vm.savedLists.isNotEmpty() }
        val start = vm.savedLists.size

        // "New list" while the list in use is empty: no second empty list
        vm.newList(); vm.refreshSavedLists(); settle(vm)
        assertEquals(start, vm.savedLists.size, "an empty list was duplicated")

        // a list with a verse, then a new one: two lists
        vm.addToList(listOf(Passage(43, 3, 16, 16)))
        settle(vm)
        val first = vm.list.id
        vm.newList(); settle(vm) { vm.list.id != first && vm.savedLists.size == start + 1 }
        val second = vm.list.id
        assertEquals(start + 1, vm.savedLists.size)

        // delete the list in use: it goes, and the remaining list is used; no new list appears
        vm.deleteSavedList(second); settle(vm) { vm.savedLists.none { it.id == second } }
        assertFalse(vm.savedLists.any { it.id == second }, "deleted list still listed")
        assertEquals(start, vm.savedLists.size)
        assertEquals(first, vm.list.id)

        // delete the last one: an empty list replaces it, so there is always a list in use
        vm.deleteSavedList(first); settle(vm) { vm.savedLists.none { it.id == first } }
        assertTrue(vm.savedLists.none { it.id == first })
        assertTrue(vm.savedLists.any { it.id == vm.list.id })
    }
}
