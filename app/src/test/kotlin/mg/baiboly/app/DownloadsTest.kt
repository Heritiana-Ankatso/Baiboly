package mg.baiboly.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.Download
import mg.baiboly.data.Pack
import mg.baiboly.data.PackInstaller
import mg.baiboly.data.PackSource
import mg.baiboly.data.PackState
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app as a release build behaves: only Malagasy 1865 (and the study and library
 * data) on a new phone, every other Bible offered for download, never downloaded
 * without a tap and a confirmation, and every place that names a translation coping
 * with one that is not on the phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class DownloadsTest {
    companion object {
        @JvmStatic @BeforeClass fun releaseLike() { AssetDatabases.useApkPacks = false }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm: AppViewModel get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    private fun waitFor(timeout: Long = 20_000, what: () -> Boolean) = rule.waitUntil(timeout) { what() }

    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction {
        rule.waitForIdle()
        performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        return this
    }

    private fun ready() {
        waitFor { vm.ready && vm.chapter != null }
        if (vm.uiLanguage != "en") vm.chooseUiLanguage("en")
        rule.waitForIdle()
    }

    private fun sha(b: ByteArray) = PackInstaller.hex(MessageDigest.getInstance("SHA-256").digest(b))

    /**
     * The file as the data release serves it: the .gz that packs.json was made from
     * (build/packs, or build/published in a release build that adopted the published files).
     */
    private fun publishedGz(p: Pack): ByteArray {
        val root = generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) { it.parentFile }
            .first { File(it, "tools/packs.py").exists() }
        return listOf("build/published", "build/packs").map { File(root, "$it/${p.file}.gz") }
            .firstOrNull { it.exists() && sha(it.readBytes()) == p.gzSha256 }?.readBytes()
            ?: error("no .gz matching packs.json for ${p.file}: run tools/build_data.py (or tools/packs.py make)")
    }

    /** A test server: counts what is asked for, so nothing can be downloaded behind the user's back. */
    private val asked = mutableListOf<String>()

    private fun serve(bytes: (String) -> ByteArray) = PackSource { url ->
        synchronized(asked) { asked += url }
        val b = bytes(url)
        Download(ByteArrayInputStream(b), b.size.toLong())
    }

    private fun pack(id: String): Pack = vm.packOf(vm.translations.first { it.id == id })!!

    @Test fun newPhoneReadsMalagasyAndDownloadsNothingByItself() {
        ready()
        vm.packSource = serve { error("nothing may be downloaded without a tap") }
        assertEquals("mg1865", vm.translationId)
        assertTrue(vm.isAvailable("mg1865"))
        assertFalse(vm.isAvailable("kjv"))
        assertTrue(vm.downloads.isEmpty())
        // the translation menu offers it, with its size
        tag("translation").tap()
        tag("lang:en").tap()
        tag("not-downloaded:kjv").assertExists()
        tag("bible:kjv").tap()
        // a tap asks first: nothing downloads, the text read does not change
        val (p, then) = assertNotNull(vm.confirmDownload)
        assertEquals("text-kjv.db", p.file)
        assertEquals(AppViewModel.AfterDownload.Read, then)
        tag("pack-confirm").assertExists()
        vm.confirmDownload = null
        rule.waitForIdle()
        assertEquals("mg1865", vm.translationId)
        // the star of a text not on the phone: asks to download it, the default stays
        vm.setDefaultTranslation("kjv")
        assertEquals(AppViewModel.AfterDownload.MakeDefault, vm.confirmDownload?.second)
        assertEquals("mg1865", vm.defaultTranslationId)
        vm.confirmDownload = null
        // search in a text not on the phone: asks to download it, searches Malagasy meanwhile
        vm.search("fitiavana", "kjv")
        assertEquals(AppViewModel.AfterDownload.Search, vm.confirmDownload?.second)
        assertEquals("mg1865", vm.searchTranslation)
        vm.confirmDownload = null
        // the Study panel's other Bibles: only those on the phone, and a hint
        val compared = kotlinx.coroutines.runBlocking { vm.compareTranslations(Passage(43, 3, 16, 16)) }
        assertEquals(listOf("mg1865"), compared.map { it.translation.id })
        // the quick look chooses only among Bibles on the phone
        vm.changeQuickLookTranslation("kjv")
        assertNull(vm.quickLookTarget(), "no other Bible on the phone yet")
        assertTrue(vm.originalAvailable.not())
        assertTrue(asked.isEmpty())
    }

    /** Every list of Bibles: by language (Malagasy, French, English), each from the oldest to the most recent. */
    @Test fun biblesByLanguageOldestFirst() {
        ready()
        vm.changeTradition(Tradition.Catholic)
        assertEquals(
            listOf("mg1865", "tdx", "darby_fr", "lsg1910", "crampon", "ncl_fr", "kjv", "dra", "bbe", "web", "webc", "bsb", "lsv"),
            vm.visibleTranslations.map { it.id },
        )
        vm.changeTradition(Tradition.Protestant)
        assertEquals(listOf("mg1865", "tdx", "darby_fr", "lsg1910", "kjv", "bbe", "web", "bsb", "lsv"), vm.visibleTranslations.map { it.id })
        // Settings › Reading aids › Quick look: a heading per language over its Bibles
        vm.screen = Screen.Settings
        vm.settingsPage = "aids"
        rule.waitForIdle()
        listOf("mg", "fr", "en").forEach { tag("settings-ql-lang:$it").assertExists() }
    }

    @Test fun tradition() {
        ready()
        if (vm.tradition != Tradition.Protestant) vm.changeTradition(Tradition.Protestant)
        val catholic = vm.translations.filter { it.catholic }.map { it.id }.toSet()
        assertTrue(catholic.isNotEmpty())
        assertTrue(vm.visiblePacks.none { it.kind == "text" && it.id in catholic }, "Protestant: no Catholic Bible offered")
        assertTrue(vm.visiblePacks.any { it.kind == "original" } && vm.visiblePacks.any { it.kind == "commentary" })
        vm.changeTradition(Tradition.Catholic)
        assertTrue(catholic.all { id -> vm.visiblePacks.any { it.id == id } }, "Catholic: the Catholic Bibles are offered too")
        vm.changeTradition(Tradition.Protestant)
    }

    @Test fun downloadReadRemove() {
        ready()
        val gz = publishedGz(pack("bsb"))
        // a failed check: refused, nothing changes, a clear message
        vm.packSource = serve { gz.copyOf().also { it[it.size / 3] = (it[it.size / 3] + 1).toByte() } }
        vm.startDownload(pack("bsb"), AppViewModel.AfterDownload.Read)
        waitFor { vm.downloads.isEmpty() && vm.message != null }
        assertTrue(vm.message!!.contains("SHA-256"), vm.message)
        assertEquals(PackState.NotInstalled, vm.packState(pack("bsb")))
        assertEquals("mg1865", vm.translationId)
        // the real file: installed and opened
        vm.message = null
        synchronized(asked) { asked.clear() }
        vm.packSource = serve { gz }
        vm.requestDownload(pack("bsb"), AppViewModel.AfterDownload.Read)
        tag("pack-confirm").tap()
        waitFor { vm.translationId == "bsb" && vm.chapter?.translation == "bsb" }
        assertEquals(PackState.Installed, vm.packState(pack("bsb")))
        assertTrue(asked.single().startsWith("https://"))
        // now offered everywhere a Bible is chosen
        val compared = kotlinx.coroutines.runBlocking { vm.compareTranslations(Passage(43, 3, 16, 16)) }
        assertEquals(setOf("mg1865", "bsb"), compared.map { it.translation.id }.toSet())
        vm.setDefaultTranslation("bsb")
        assertEquals("bsb", vm.defaultTranslationId)
        // a note made while reading it does not depend on it
        vm.openPassage(Passage(43, 3, 16, 16))
        vm.addNote(Passage(43, 3, 16, 16), "kept after the text is removed")
        // Settings › Bible › Downloads: remove it
        vm.screen = Screen.Settings
        vm.settingsPage = "downloads"
        rule.waitForIdle()
        tag("pack-remove:text-bsb.db").tap()
        tag("pack-remove-confirm").tap()
        waitFor { vm.packState(pack("bsb")) == PackState.NotInstalled }
        assertEquals("mg1865", vm.translationId)
        assertEquals("mg1865", vm.defaultTranslationId)
        assertFalse(File(rule.activity.getDatabasePath("text-bsb.db").path).exists())
        waitFor { vm.notes.any { it.text == "kept after the text is removed" } }
    }

    /** A default Bible that is not on the phone (restored from a backup made on another phone, say). */
    @Test fun defaultBibleNotOnThePhone() {
        ready()
        rule.activity.getSharedPreferences("app_state", android.content.Context.MODE_PRIVATE).edit()
            .putString("default_translation", "lsg1910").commit()
        val fresh = AppViewModel(rule.activity.application)
        for (i in 0 until 300) {
            if (fresh.ready && fresh.chapter != null) break
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }
        assertTrue(fresh.ready && fresh.chapter != null, "the second start did not finish loading")
        assertEquals("lsg1910", fresh.defaultTranslationId, "the setting is kept")
        assertEquals("mg1865", fresh.translationId, "the reader opens Malagasy 1865 meanwhile")
        assertEquals("mg1865", fresh.chapter?.translation)
        assertEquals("mg1865", fresh.searchTranslation)
    }
}
