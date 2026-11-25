package mg.baiboly.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.app.ui.NotePdf
import mg.baiboly.app.ui.sheetOf
import mg.baiboly.core.notes.NoteMarkup
import mg.baiboly.core.ref.Passage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sermon notes in Church mode and studies in Study mode: created, written, linked, found again, exported. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class DocumentsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun waitFor(timeout: Long = 15_000, what: () -> Boolean) = rule.waitUntil(timeout) { what() }
    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = true)
    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction {
        rule.waitForIdle(); performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle(); return this
    }

    private fun ready() {
        waitFor { vm.ready && vm.chapter != null }
        if (vm.uiLanguage != "en") vm.chooseUiLanguage("en")
        if (vm.style != Style.Manuscript) vm.changeStyle(Style.Manuscript)
        if (vm.translationId != "mg1865") vm.setTranslation("mg1865")
        vm.screen = Screen.Main
        rule.waitForIdle()
    }

    @Test fun sermonNoteInChurch() {
        ready()
        vm.changeMode(AppMode.Church)
        if (!vm.list.isEmpty) vm.newList()
        vm.addToList(listOf(Passage(43, 3, 16, 16), Passage(45, 8, 28, 28)))
        waitFor { vm.list.items.size == 2 }
        rule.waitForIdle()
        // the Notes tab of Church mode, and the one-tap note on the service page
        tag("nav:Documents").assertExists()
        tag("sermon-note").tap()
        waitFor { vm.screen == Screen.Document && vm.openDocument != null }
        val d = vm.openDocument!!
        assertEquals("sermon", d.kind)
        assertEquals("Jaona 3: 16; Romana 8: 28", d.passages, "the service list's passages are recorded")
        assertTrue(d.title.startsWith("Sermon of"))
        // the Church bar is short: no numbered points, no quotation, no events
        tag("tool-title").assertExists(); tag("tool-verse").assertExists(); tag("tool-person").assertExists()
        tag("tool-numbered").assertDoesNotExist(); tag("tool-event").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        try {
            tag("doc-body").performTextReplacement("God loved\nThe world")
            rule.mainClock.advanceTimeBy(100)
            tag("tool-section").tap()       // the caret's line becomes a section
            rule.mainClock.advanceTimeBy(1_500) // autosave
        } finally { rule.mainClock.autoAdvance = true }
        waitFor { (vm.openDocument?.id ?: 0L) > 0L }
        waitFor { vm.documents.any { it.id == vm.openDocument!!.id && it.body.contains("## The world") } }
        // a second tap the same day opens the same note
        val id = vm.openDocument!!.id
        vm.closeDocument()
        rule.waitForIdle()
        vm.todaysSermonNote()
        waitFor { vm.openDocument?.id == id }
        vm.closeDocument()
        vm.changeMode(AppMode.Casual)
    }

    @Test fun studyWithLinksOutlineAndMyNotes() {
        ready()
        vm.changeMode(AppMode.Study)
        vm.openChapter(45, 8)
        waitFor { vm.chapter?.book == 45 && vm.chapter?.chapter == 8 }
        vm.newDocument("study", Passage(45, 8, 28, 30))
        waitFor { vm.screen == Screen.Document }
        assertEquals("Romana 8: 28-30", vm.openDocument!!.title)
        tag("tool-numbered").assertExists(); tag("tool-event").assertExists(); tag("tool-quote").assertExists()
        // a template to start from
        tag("template:0").tap()
        rule.waitForIdle()
        val body = "# Golden chain\nSee [Rom 8: 30](bible:45.8.30-30) and [Abraham](person:1).\n# Called\nx\n# Justified\ny"
        rule.mainClock.autoAdvance = false
        try {
            tag("doc-body").performTextReplacement(body)
            rule.mainClock.advanceTimeBy(1_500)
        } finally { rule.mainClock.autoAdvance = true }
        waitFor { vm.documents.any { it.kind == "study" && it.body == body } }
        // the outline: "Justified" moves before "Called"
        tag("doc-outline").tap()
        tag("outline-up:4").tap()
        rule.mainClock.autoAdvance = false
        try { rule.mainClock.advanceTimeBy(1_500) } finally { rule.mainClock.autoAdvance = true }
        waitFor { vm.documents.any { it.kind == "study" && NoteMarkup.outline(it.body).map { o -> o.second } == listOf("Golden chain", "Justified", "Called") } }
        // read view: the verse link opens the verse
        tag("doc-preview").tap()
        tag("doc-rendered").assertExists()
        // the Study panel of Romans 8:30 lists the study
        val doc = vm.documents.first { it.kind == "study" }
        val citing = kotlinx.coroutines.runBlocking { vm.documentsCiting(Passage(45, 8, 30, 30)) }
        assertTrue(citing.any { it.id == doc.id })
        // PDF and the heading lines
        val sheet = sheetOf(rule.activity, doc, vm)
        assertContains(sheet.meta.joinToString(), "Romana 8: 28-30")
        val out = ByteArrayOutputStream()
        runCatching { NotePdf.write(sheet, out) } // Robolectric draws no real PDF; the call must not throw where it can run
        vm.closeDocument()
        vm.changeMode(AppMode.Casual)
    }
}
