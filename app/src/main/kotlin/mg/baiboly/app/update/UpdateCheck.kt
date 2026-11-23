// network-check: allow-file (update check: the only network use; run by the user's tap, or daily when they switch it on)
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
import android.net.Uri
import android.os.Build
import mg.baiboly.app.R
import mg.baiboly.app.UiLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A published release: its version ("1.3.0") and the page to download it from. */
data class Release(val version: String, val url: String)

/**
 * Update check: the one place the app goes online. It asks GitHub for the
 * latest release and compares it with the installed version. Nothing else is
 * sent or received; no download happens in the app (the release page opens
 * in the browser).
 *
 * Off by default. "Check now" runs on the user's tap; "Check automatically"
 * runs about once a day and shows a silent notification, once per new version.
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

    /** Reads GitHub's "latest release" answer. */
    fun parseLatest(json: String): Release? = runCatching {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        Release(tag.removePrefix("v").removePrefix("V"), o.optString("html_url").ifBlank { RELEASES_PAGE })
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

    /** A silent notification (low importance: no sound, no vibration); a tap opens the release page. */
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
            c, 1, Intent(Intent.ACTION_VIEW, Uri.parse(r.url)),
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
