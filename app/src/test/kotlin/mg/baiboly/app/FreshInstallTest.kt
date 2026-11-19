package mg.baiboly.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Its own class, so it runs in a fresh JVM with no data or settings left by other tests. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class FreshInstallTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun opensInMalagasyWithMalagasyBible() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        // First launch unpacks the database in the background; keep the main
        // looper and the Compose clock moving while it does.
        for (i in 0 until 300) {
            if (vm.ready && vm.chapter != null) break
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(50)
        }
        assertTrue(vm.ready && vm.chapter != null, "first launch did not finish loading")
        rule.waitForIdle()
        assertEquals("mg", vm.uiLanguage)
        assertEquals("mg1865", vm.defaultTranslationId)
        assertEquals("mg1865", vm.translationId)
        rule.onNodeWithText("Hijery").assertExists()            // Browse
        rule.onNodeWithText("Hanomboka lisitra").assertExists() // Start a list
    }

    /** Regression: opening Fitahirizana (Backup) in Malagasy crashed (file picker could not find the activity). */
    @Test fun backupScreenInMalagasy() {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        for (i in 0 until 300) {
            if (vm.ready && vm.chapter != null) break
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(50)
        }
        assertEquals("mg", vm.uiLanguage)
        vm.screen = Screen.Backup
        rule.waitForIdle()
        rule.onNodeWithText("Fitahirizana").assertExists()
        // both buttons start the system file picker
        rule.onNodeWithText("Hitahiry ao anaty rakitra").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Hamerina avy amin’ny rakitra").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        // the other screens that use the activity: Settings, About
        vm.screen = Screen.Settings; rule.waitForIdle()
        vm.screen = Screen.About; rule.waitForIdle()
        rule.onNodeWithText("Mombamomba").assertExists()
    }
}
