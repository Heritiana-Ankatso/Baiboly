package mg.baiboly.app

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.app.update.Release
import mg.baiboly.app.update.UpdateCheck
import mg.baiboly.app.update.UpdateInstaller
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The update check, without going online: version order, GitHub's answer, the opt-in switch. */
@RunWith(AndroidJUnit4::class)
class UpdateCheckTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun jobs() = (ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).allPendingJobs

    @Test fun versionOrder() {
        assertTrue(UpdateCheck.isNewer("v1.3.0", "1.2.0"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.9"))       // numbers, not text
        assertTrue(UpdateCheck.isNewer("v2", "1.9"))
        assertTrue(UpdateCheck.isNewer("1.2.1", "1.2"))
        assertFalse(UpdateCheck.isNewer("v1.2.0", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("v1.1.9", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("1.2.0-beta", "1.2.0"))
    }

    @Test fun readsGitHubLatestRelease() {
        val json = """{"tag_name":"v1.3.0","name":"Baiboly v1.3.0","html_url":"https://github.com/Heritiana-Ankatso/Baiboly/releases/tag/v1.3.0","draft":false}"""
        assertEquals(
            Release("1.3.0", "https://github.com/Heritiana-Ankatso/Baiboly/releases/tag/v1.3.0"),
            UpdateCheck.parseLatest(json),
        )
        assertEquals(UpdateCheck.RELEASES_PAGE, UpdateCheck.parseLatest("""{"tag_name":"v1.3.0"}""")?.url)
        assertNull(UpdateCheck.parseLatest("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parseLatest("not json"))
    }

    @Test fun findsTheApkAndItsChecksum() {
        val json = """{"tag_name":"v1.5.0","html_url":"https://github.com/Heritiana-Ankatso/Baiboly/releases/tag/v1.5.0","assets":[
            {"name":"Baiboly-v1.5.0.apk.sha256","size":90,"browser_download_url":"https://github.com/Heritiana-Ankatso/Baiboly/releases/download/v1.5.0/Baiboly-v1.5.0.apk.sha256"},
            {"name":"Baiboly-v1.5.0.apk","size":52428800,"browser_download_url":"https://github.com/Heritiana-Ankatso/Baiboly/releases/download/v1.5.0/Baiboly-v1.5.0.apk"}]}"""
        val r = UpdateCheck.parseLatest(json)!!
        assertEquals("1.5.0", r.version)
        assertEquals("https://github.com/Heritiana-Ankatso/Baiboly/releases/download/v1.5.0/Baiboly-v1.5.0.apk", r.apkUrl)
        assertEquals("https://github.com/Heritiana-Ankatso/Baiboly/releases/download/v1.5.0/Baiboly-v1.5.0.apk.sha256", r.sha256Url)
        assertEquals(52428800L, r.apkSize)
        // only https links are followed
        assertNull(UpdateCheck.parseLatest("""{"tag_name":"v1.5.0","assets":[{"name":"x.apk","browser_download_url":"http://example.com/x.apk"}]}""")!!.apkUrl)
    }

    @Test fun readsTheChecksumFile() {
        val sum = "3f".repeat(32)
        assertEquals(sum, UpdateInstaller.parseSha256("$sum  Baiboly-v1.5.0.apk\n"))
        assertEquals(sum, UpdateInstaller.parseSha256(sum.uppercase()))
        assertNull(UpdateInstaller.parseSha256("Not Found"))
    }

    @Test fun updateSettingsOfferInstallInsideTheApp() {
        // the notification opens Settings with Advanced › Updates unfolded
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.openUpdates()
        assertEquals(Screen.Settings, vm.screen)
        assertTrue(vm.openUpdateSettings)
    }

    @Test fun automaticCheckIsOptIn() {
        assertFalse(UpdateCheck.isAuto(ctx), "off by default")
        assertTrue(jobs().none(), "nothing scheduled by default")
        UpdateCheck.setAuto(ctx, true)
        assertTrue(UpdateCheck.isAuto(ctx))
        assertEquals(1, jobs().size, "a daily job while the switch is on")
        UpdateCheck.setAuto(ctx, false)
        assertTrue(jobs().none(), "switching off cancels it")
    }
}
