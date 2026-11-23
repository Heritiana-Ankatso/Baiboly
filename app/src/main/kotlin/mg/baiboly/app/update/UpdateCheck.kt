// network-check: allow-file (update check: run by the user's tap, or daily when they switch it on)
package mg.baiboly.app.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import mg.baiboly.app.R
import mg.baiboly.app.UiLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * A published release: its version ("1.3.0"), its page, and the APK attached to
 * it (with its SHA-256 file), which the app downloads and installs itself.
 */
data class Release(
    val version: String,
    val url: String,
    val apkUrl: String? = null,
    val sha256Url: String? = null,
    /** Size of the APK in bytes (0 when unknown). */
    val apkSize: Long = 0,
)

/**
 * Update check: one of the two places the app goes online (the other is
 * [UpdateInstaller], on the user's tap). It asks GitHub for the latest release
 * and compares it with the installed version. Nothing about the reader is sent.
 *
 * Off by default. "Check now" runs on the user's tap; "Check automatically"
 * runs about once a day and shows a silent notification, once per new version;
 * the notification opens the app's update settings, where it installs.
 */
object UpdateCheck {
    const val RELEASES_PAGE = "https://github.com/Heritiana-Ankatso/Baiboly/releases"
    private const val LATEST_API = "https://api.github.com/repos/Heritiana-Ankatso/Baiboly/releases/latest"
    private const val PREFS = "updates" // its own file: not part of the backup
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST = "last_check"
    private const val KEY_NOTIFIED = "notified_version"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 7
    private const val JOB_ID = 7001
    private val DAY = TimeUnit.DAYS.toMillis(1)

    /** Intent extra: what to open when the app is started from a notification. */
    const val EXTRA_OPEN = "mg.baiboly.app.open"
    const val OPEN_UPDATES = "updates"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAuto(c: Context) = prefs(c).getBoolean(KEY_AUTO, false)
    fun lastCheck(c: Context) = prefs(c).getLong(KEY_LAST, 0L)

    fun setAuto(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_AUTO, on).apply()
        schedule(c, on)
    }

    fun installedVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** True when [candidate] ("v1.10.0" or "1.10.0") is a later version than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads GitHub's "latest release" answer, with the APK and its checksum among the assets. */
    fun parseLatest(json: String): Release? = runCatching {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        var apk: String? = null
        var sha: String? = null
        var size = 0L
        val assets = o.optJSONArray("assets")
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val link = a.optString("browser_download_url").takeIf { it.startsWith("https://") } ?: continue
            when {
                name.endsWith(".apk.sha256") -> sha = link
                name.endsWith(".apk") && apk == null -> { apk = link; size = a.optLong("size") }
            }
        }
        Release(tag.removePrefix("v").removePrefix("V"), o.optString("html_url").ifBlank { RELEASES_PAGE }, apk, sha, size)
    }.getOrNull()

    /** Asks GitHub for the latest release (blocking: call it off the main thread). Null when offline or on error. */
    fun fetchLatest(c: Context): Release? {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Baiboly-Android")
        }
        val release = try {
            if (conn.responseCode != 200) null
            else parseLatest(conn.inputStream.use { it.readBytes().decodeToString() })
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
        if (release != null) prefs(c).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        return release
    }

    /** The newer release, or null when the installed one is the latest. Throws nothing; [Result.failure] when offline. */
    fun check(c: Context): Result<Release?> {
        val latest = fetchLatest(c) ?: return Result.failure(IllegalStateException("offline"))
        return Result.success(latest.takeIf { isNewer(it.version, installedVersion(c)) })
    }

    /** Automatic check (blocking): at most once a day, a notification once per new version. */
    fun autoCheck(c: Context) {
        if (!isAuto(c)) return
        if (System.currentTimeMillis() - lastCheck(c) < DAY - TimeUnit.HOURS.toMillis(2)) return
        val newer = check(c).getOrNull() ?: return
        if (prefs(c).getString(KEY_NOTIFIED, null) == newer.version) return
        notify(c, newer)
        prefs(c).edit().putString(KEY_NOTIFIED, newer.version).apply()
    }

    /** Daily background check while the switch is on (re-armed whenever the app opens). */
    fun schedule(c: Context, on: Boolean) {
        val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (!on) { js.cancel(JOB_ID); return }
        if (js.allPendingJobs.any { it.id == JOB_ID }) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(c, UpdateJob::class.java))
                .setPeriodic(DAY)
                .build()
        )
    }

    /** A silent notification (low importance: no sound, no vibration); a tap opens the app's update settings. */
    private fun notify(c: Context, r: Release) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = UiLanguage.wrap(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, text.getString(R.string.updates), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            c, 1,
            Intent(c, mg.baiboly.app.MainActivity::class.java)
                .putExtra(EXTRA_OPEN, OPEN_UPDATES)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CHANNEL) else Notification.Builder(c).setPriority(Notification.PRIORITY_LOW)
        b.setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(text.getString(R.string.update_available, r.version))
            .setContentText(text.getString(R.string.update_tap_to_open))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        nm.notify(NOTIFICATION_ID, b.build())
    }
}

/** Runs the automatic check from JobScheduler, on its own thread. */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            runCatching { UpdateCheck.autoCheck(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
