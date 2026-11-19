package mg.baiboly.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}
