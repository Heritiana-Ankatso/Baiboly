package mg.baiboly.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.UserStore
import org.junit.Rule
import org.robolectric.shadows.ShadowLooper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The acceptance scenarios of the brief, driven through the real Compose UI
 * on the JVM (Robolectric) with the real bundled databases.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class UiAcceptanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun waitFor(timeout: Long = 15_000, what: () -> Boolean) {
        try {
            rule.waitUntil(timeout) { what() }
        } catch (e: Throwable) {
            throw AssertionError(
                "timeout; list=${vm.list.items.map { it.passage }} current=${vm.list.current} loc=${vm.location} " +
                    "chapter=${vm.chapter?.book}:${vm.chapter?.chapter} tr=${vm.translationId} away=${vm.isAway} screen=${vm.screen}", e,
            )
        }
    }

    /**
     * A focused text field blinks its cursor forever, so Compose never reports
     * "idle". Text entry steps run with a manually advanced clock.
     */
    private fun withManualClock(block: () -> Unit) {
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            block()
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    private fun SemanticsNodeInteraction.press(): SemanticsNodeInteraction {
        performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        return this
    }

    private fun dumpTree(): String = rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().indices
        .joinToString("\n=====\n") { rule.onAllNodes(androidx.compose.ui.test.isRoot())[it].printToString(maxDepth = 40) }

    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = true)

    /** Invokes the node's click handler (the same code a tap runs) without depending on sheet animations. */
    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction {
        rule.waitForIdle()
        performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        return this
    }

    /** Waits for the first chapter, then starts from a new, empty list. */
    private fun ready() {
        waitFor { vm.ready && vm.chapter != null }
        if (vm.uiLanguage != "en") vm.chooseUiLanguage("en") // the English labels below
        // saved settings can outlive a test: start each one from the defaults
        if (vm.style != Style.Manuscript) vm.changeStyle(Style.Manuscript)
        if (vm.mode != AppMode.Casual) vm.changeMode(AppMode.Casual)
        if (vm.tradition != Tradition.Protestant) vm.changeTradition(Tradition.Protestant)
        vm.screen = Screen.Main
        if (!vm.list.isEmpty) {
            val old = vm.list.id
            vm.newList()
            waitFor { vm.list.id != old }
        }
        rule.waitForIdle()
    }

    private fun Passage.at() = vm.location.let { it.focus == this && it.book == book && it.chapter == chapter }

    /** Tasks 5–9: capture five verses, navigate, detour, return; list and position survive a restart. */
    @Test fun serviceDryRun() {
        ready()
        assertTrue(vm.list.isEmpty)
        // Church mode: the service list, its strip and the previous/next bar.
        tag("mode-badge").tap()
        tag("mode:Church").tap()
        waitFor { vm.mode == AppMode.Church }
        rule.onNodeWithText("Start a list", substring = true).tap()

        // Task 6: a verse in four taps in keypad mode: book, chapter chip, verse chip, Add.
        tag("book-grid").performScrollToKey(43) // scrolling the book grid is not a tap
        tag("book:Jao").tap()
        tag("suggestion:Jao 3: ").tap()                        // chapter chip "3"
        tag("suggestions").performScrollToKey("Jao 3: 16") // scrolling the chip row is not a tap
        tag("suggestion:Jao 3: 16").tap()                      // verse chip "16"
        tag("key:ADD").tap()
        waitFor { vm.list.items.size == 1 }
        assertEquals(Passage(43, 3, 16, 16), vm.list.items[0].passage)

        // Keypad typing in the user's own style: "Efe 2: 4, 8-9" gives two items.
        tag("book-grid").performScrollToKey(49) // scrolling the book grid is not a tap
        tag("book:Efe").tap()
        listOf("2", ":", "4", ",", "8", "-", "9").forEach { tag("key:$it").tap() }
        rule.onNodeWithText("→ Efesiana 2: 4, 8-9").assertExists()
        tag("key:ADD").tap()
        // Keyboard mode: "Sal 23" (whole chapter) and "2 Tim 2: 22".
        // "Sal 23" (whole chapter) and "2 Tim 2: 22" on the keypad.
        tag("book-grid").performScrollToKey(19)
        tag("book:Sal").tap()
        listOf("2", "3").forEach { tag("key:$it").tap() }
        tag("key:ADD").tap()
        tag("book-grid").performScrollToKey(55)
        tag("book:2 Tim").tap()
        listOf("2", ":", "2", "2").forEach { tag("key:$it").tap() }
        rule.onNodeWithText("→ 2 Timoty 2: 22").assertExists()
        tag("key:ADD").tap()
        rule.onNodeWithContentDescription("Close").tap()
        waitFor { vm.list.items.size == 5 }
        rule.waitForIdle()
        assertEquals(
            listOf(Passage(43, 3, 16, 16), Passage(49, 2, 4, 4), Passage(49, 2, 8, 9), Passage(19, 23), Passage(55, 2, 22, 22)),
            vm.list.items.map { it.passage },
        )
        assertEquals(0, vm.list.current)
        waitFor { Passage(43, 3, 16, 16).at() && vm.chapter?.book == 43 }

        // Task 5: tap a chip, swipe, side tag.
        tag("chip:2").tap()
        waitFor { vm.list.current == 2 && Passage(49, 2, 8, 9).at() }
        tag("reader").performTouchInput { swipeLeft() }
        waitFor { vm.list.current == 3 && vm.chapter?.book == 19 && vm.chapter?.chapter == 23 }
        tag("reader").performTouchInput { swipeRight() }
        waitFor { vm.list.current == 2 }
        vm.toggleSide(4)
        rule.waitForIdle()
        rule.onNodeWithText("side").assertExists()

        // Task 4: chapter buttons move through the Bible.
        tag("next-chapter").tap()
        waitFor { vm.chapter?.book == 49 && vm.chapter?.chapter == 3 }
        assertTrue(vm.isAway)
        tag("back-to-place").tap()
        waitFor { Passage(49, 2, 8, 9).at() && !vm.isAway }

        // Service bar: next passage in one tap.
        tag("service-next").tap()
        waitFor { vm.list.current == 3 }
        tag("service-prev").tap()
        waitFor { vm.list.current == 2 && Passage(49, 2, 8, 9).at() }

        // Task 7: Books tab -> Open without adding -> Back to my place in one tap.
        tag("nav:Books").tap()
        tag("browse-books").performScrollToKey(45)
        tag("browse-book:45").tap()
        tag("browse-chapter:5").tap()
        waitFor(20_000) { runCatching { tag("browse-verse:8").assertExists(); true }.getOrDefault(false) }
        tag("browse-verse:8").tap()
        rule.onNodeWithText("Open without adding").tap()
        waitFor { Passage(45, 5, 8, 8).at() && vm.chapter?.book == 45 }
        assertTrue(vm.isAway)
        assertEquals(5, vm.list.items.size, "Open without adding must not add")
        tag("back-to-place").tap()
        waitFor { Passage(49, 2, 8, 9).at() && vm.chapter?.book == 49 }
        assertFalse(vm.isAway)

        // Task 8: long-press menu actions (via the same list operations) and autosave.
        vm.moveLater(0)
        rule.waitForIdle()
        assertEquals(Passage(49, 2, 4, 4), vm.list.items[0].passage)

        // Task 8: "closing and reopening" restores the same list and position: a fresh
        // ViewModel reads everything back from storage.
        Thread.sleep(500) // let the background autosave finish
        val expectedItems = vm.list.items
        val expectedCurrent = vm.list.current
        val expectedLocation = vm.location
        val old = vm
        // Drop the ViewModel and recreate the activity: the new ViewModel reads
        // everything back from storage, as after closing and reopening the app.
        rule.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        rule.activityRule.scenario.recreate()
        waitFor { vm !== old && vm.ready }
        assertEquals(expectedItems, vm.list.items)
        assertEquals(expectedCurrent, vm.list.current)
        assertEquals(expectedLocation, vm.location)
    }

    /** Task 14: α on, tap a verse number, open a word, Found N verses, open in Malagasy with Back to my place. */
    @Test fun studyPanel() {
        ready()
        vm.addToList(listOf(Passage(43, 3, 16, 16)))
        waitFor { Passage(43, 3, 16, 16).at() }
        vm.setTranslation("lsg1910")
        vm.openChapter(55, 2)
        waitFor { vm.chapter?.book == 55 && vm.chapter?.chapter == 2 }
        rule.waitForIdle()
        tag("mode-badge").tap()
        tag("mode:Study").tap()
        waitFor { vm.studyMode }
        tag("reader").performScrollToKey("v25")
        tag("vnum:25").tap()
        waitFor { runCatching { tag("word:G1922").assertExists(); true }.getOrDefault(false) }
        rule.onNodeWithText("acknowledging").assertExists()
        tag("word:G1922").tap()
        waitFor { runCatching { rule.onNodeWithText("ἐπίγνωσις").assertExists(); true }.getOrDefault(false) }
        tag("found").tap()
        waitFor { runCatching { tag("found:45-1-28").assertExists(); true }.getOrDefault(false) }
        tag("found:45-1-28").tap()
        waitFor { Passage(45, 1, 28, 28).at() && vm.chapter?.book == 45 }
        assertEquals("mg1865", vm.translationId, "Found N verses opens the Malagasy text")
        assertTrue(vm.isAway)
        tag("back-to-place").assertExists()
    }

    /** Tasks 10–11: three taps to a chapter from the title; notes and highlights survive a restart. */
    @Test fun menuNotesHighlights() {
        ready()
        tag("chapter-title").tap()      // the chapter title opens the books page
        waitFor { vm.screen == Screen.Books }
        tag("testament:0").tap()        // Old Testament
        tag("books").performScrollToKey(19) // scrolling, not a tap
        tag("book-item:19").tap()       // Salamo
        tag("chapters").assertExists()
        tag("browse-chapter:23").tap()
        waitFor { vm.chapter?.book == 19 && vm.chapter?.chapter == 23 }

        // Selection action "Add note": the dialog's text entry is driven through the
        // ViewModel because a focused field never lets Compose report idle under Robolectric.
        tag("verse:1").tap()
        tag("action:Note").assertExists()
        tag("verse:1").tap() // unselect
        vm.addNote(Passage(19, 23, 1, 1), "Jehovah no Mpiandry ahy")
        tag("verse:2").tap()
        tag("verse:3").tap()
        tag("action:Highlight").tap()
        tag("swatch:0").tap()
        rule.waitForIdle()

        waitFor { vm.notes.isNotEmpty() && vm.chapterHighlights.isNotEmpty() }
        // closing and reopening the app: a new ViewModel reads them back from storage
        Thread.sleep(500)
        val old = vm
        rule.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        rule.activityRule.scenario.recreate()
        waitFor { vm !== old && vm.ready }
        vm.refreshNotes(); vm.refreshHighlights()
        waitFor { vm.notes.isNotEmpty() && vm.highlights.isNotEmpty() }
        assertEquals(Passage(19, 23, 1, 1), vm.notes.single().passage)
        assertEquals(Passage(19, 23, 2, 3), vm.highlights.single().passage)

        vm.openChapter(1, 1)
        waitFor { vm.chapter?.book == 1 }
        vm.refreshNotes()
        tag("nav:Library").tap()
        tag("library-tab:0").tap()       // Notes
        waitFor { vm.notes.isNotEmpty() }
        rule.onNodeWithText("Jehovah no Mpiandry ahy").tap()
        waitFor { Passage(19, 23, 1, 1).at() && vm.chapter?.book == 19 && vm.screen == Screen.Main }
    }

    /** Malagasy interface: chosen in Settings, applied at once and kept after a restart. */
    @Test fun malagasyInterface() {
        ready()
        vm.screen = Screen.Settings
        rule.waitForIdle()
        tag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("settings-ui-lang:mg"))
        tag("settings-ui-lang:mg").tap()
        waitFor { rule.onAllNodesWithText("Fiteny").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("mg", vm.uiLanguage)
        vm.screen = Screen.Main
        waitFor { rule.onAllNodesWithText("Vakio").fetchSemanticsNodes().isNotEmpty() } // Read tab
        rule.onNodeWithText("Tahiry").assertExists()          // Library tab
        // Regression: sheets, dialogs and menus open in their own window, which
        // used to fall back to the phone's language (English here).
        tag("mode-badge").tap()
        waitFor { rule.onAllNodesWithText("Safidio ny fomba").fetchSemanticsNodes().isNotEmpty() } // "Choose a mode"
        assertTrue(rule.onAllNodesWithText("Choose a mode").fetchSemanticsNodes().isEmpty(), "mode sheet in English")
        rule.onNodeWithText("Tsotra").assertExists()          // Casual
        tag("mode:Casual").tap()                              // closes the sheet
        assertTrue(vm.str(R.string.default_list_title, "x").startsWith("Fotoam-pivavahana"))
        // after closing and reopening the app
        rule.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        rule.activityRule.scenario.recreate()
        waitFor { vm.ready && vm.chapter != null }
        waitFor { rule.onAllNodesWithText("Vakio").fetchSemanticsNodes().isNotEmpty() }
    }

    /** The default Bible is set with one tap on its star; it is what the app opens with. */
    @Test fun defaultBibleAndOwnChapterNumbering() {
        ready()
        assertEquals("mg1865", vm.defaultTranslationId)
        tag("reader").assertExists()
        tag("translation").tap()                   // the reading-text chip in the top bar
        tag("lang:fr").tap()                       // the menu opens on the languages
        tag("default:lsg1910").tap()               // one tap: default Bible
        waitFor { vm.defaultTranslationId == "lsg1910" && vm.translationId == "lsg1910" && vm.chapter?.translation == "lsg1910" }
        // switching the text for a while does not change the default
        vm.setTranslation("kjv")
        vm.openChapter(39, 3)
        waitFor { vm.chapter?.translation == "kjv" && vm.chapter?.chapter == 3 }
        tag("next-chapter").tap()
        waitFor { vm.chapter?.book == 39 && vm.chapter?.chapter == 4 } // KJV Malachi 4
        assertEquals("lsg1910", vm.defaultTranslationId)
        // reopen: the app starts with the default Bible
        val old = vm
        rule.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        rule.activityRule.scenario.recreate()
        waitFor { vm !== old && vm.ready && vm.chapter != null }
        assertEquals("lsg1910", vm.translationId)
    }

    /** The Catholic/Protestant choice sits under Settings > Advanced and filters texts and books. */
    @Test fun traditionInAdvancedSettings() {
        ready()
        assertEquals(Tradition.Protestant, vm.tradition)
        assertTrue(vm.visibleTranslations.none { it.catholic })
        assertTrue(vm.availableBooks.none { it > 66 })
        tag("settings").tap()
        waitFor { vm.screen == Screen.Settings }
        tag("settings-tradition:Catholic").assertDoesNotExist() // hidden until Advanced is opened
        tag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("settings-advanced"))
        tag("settings-advanced").tap()
        tag("settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("settings-tradition:Catholic"))
        tag("settings-tradition:Catholic").tap()
        waitFor { vm.tradition == Tradition.Catholic }
        assertTrue(vm.visibleTranslations.any { it.id == "crampon" })
        vm.setTranslation("crampon")
        waitFor { vm.chapter?.translation == "crampon" }
        assertTrue(vm.availableBooks.any { it > 66 }, "Catholic text shows the deuterocanonical books")
        // back to Protestant: the Catholic text is left for a Protestant one
        vm.changeTradition(Tradition.Protestant)
        waitFor { vm.chapter?.translation != "crampon" && vm.chapter != null }
        assertFalse(vm.translations.first { it.id == vm.translationId }.catholic)
    }

    /** Church mode is silent: no Listen button, and the selection offers "To list". */
    @Test fun churchModeIsSilent() {
        ready()
        vm.enableReadAloud(true)
        rule.waitForIdle()
        tag("listen").assertExists()
        vm.changeMode(AppMode.Church)
        rule.waitForIdle()
        tag("listen").assertDoesNotExist()
        tag("nav:Lists").assertExists()
        vm.openChapter(19, 23)
        waitFor { vm.chapter?.book == 19 && runCatching { tag("verse:1").assertExists(); true }.getOrDefault(false) }
        tag("verse:1").tap()
        tag("action:To list").assertExists()
    }

    /** Every theme is a whole design: each can still reach a chapter, the verse actions and Settings. */
    @Test fun everyThemeWorks() {
        ready()
        for (st in Style.entries) {
            vm.changeStyle(st)
            vm.screen = Screen.Main
            vm.openChapter(19, 23)
            waitFor { vm.chapter?.book == 19 && vm.chapter?.chapter == 23 }
            rule.waitForIdle()
            assertEquals(st.palettes.first(), vm.palette)
            tag("chapter-title").assertExists()
            // next chapter: Page puts the link after the last verse
            if (st == Style.Page) tag("reader").performScrollToKey("footer")
            tag("next-chapter").tap()
            waitFor { vm.chapter?.chapter == 24 }
            // verse actions
            tag("reader").performScrollToKey("v1")
            tag("verse:1").tap()
            tag("action:Note").assertExists()
            tag("verse:1").tap()
            rule.waitForIdle()
            // settings: the gear, or in Page the menu
            if (st == Style.Page) {
                tag("menu").tap()
                waitFor { vm.screen == Screen.Menu }
                tag("menu-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("nav:Settings"))
                tag("nav:Settings").tap()
            } else tag("settings").tap()
            waitFor { vm.screen == Screen.Settings }
            tag("settings-list").assertExists()
            tag("settings-advanced").assertExists()
            vm.screen = Screen.Main
            rule.waitForIdle()
        }
    }

    /** Map: the timeline shows a period with the borders of its time; layers switch the base map. */
    @Test fun mapTimelineAndLayers() {
        ready()
        vm.changeMode(AppMode.Study)
        tag("nav:Map").tap()
        waitFor { vm.screen == Screen.Map }
        tag("map-all").tap()
        tag("timeline").performScrollToNode(androidx.compose.ui.test.hasTestTag("period:Jesus"))
        tag("period:Jesus").tap()
        waitFor { runCatching { tag("map-realm").assertExists(); true }.getOrDefault(false) }
        rule.onNodeWithText("Kingdom of Herod the Great").assertExists()
        tag("timeline").performScrollToNode(androidx.compose.ui.test.hasTestTag("period:all"))
        tag("period:all").tap()
        tag("map-realm").assertDoesNotExist()
        tag("map-layers").tap()
        tag("map-base:Today").assertExists()
        tag("map-base:Today").tap()
        tag("map").assertExists()
    }
}
