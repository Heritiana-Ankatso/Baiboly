// network-check: allow-file (in-app update: downloads the release APK from GitHub, only on the user's tap)
package mg.baiboly.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app update: downloads the APK of a newer release and hands it to Android's
 * package installer, without leaving the app. Runs only when the user taps
 * "Download and install".
 *
 * Safety: the download must match the SHA-256 published with the release, must
 * be this app (same package name) and a newer version. Android itself then
 * refuses an APK not signed with the same key as the installed app, and always
 * asks the user to confirm the update.
 */
object UpdateInstaller {
    /** What the installer reports back (from [UpdateInstallReceiver]). */
    sealed interface Event {
        /** Android's confirmation dialog is open. */
        data object Confirming : Event
        /** Android declined or the user cancelled; [message] is the system's reason, if any. */
        data class Failed(val message: String?) : Event
        data object Installed : Event
    }

    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events

    internal fun report(e: Event?) { _events.value = e }

    private fun dir(c: Context) = File(c.cacheDir, "updates")

    /** Removes downloaded APKs (after an update, or when a download is abandoned). */
    fun clean(c: Context) { dir(c).deleteRecursively() }

    /** True when Android lets this app install packages (Android 8+: "Install unknown apps", per app). */
    fun canInstall(c: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    /** The system page where the user allows this app to install updates. */
    fun permissionIntent(c: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.packageName))

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub sends release files from another host
            setRequestProperty("User-Agent", "Baiboly-Android")
        }

    /** First word of a "sha256sum" line: the 64 hex digits. */
    fun parseSha256(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /**
     * Downloads the APK of [r] (blocking: call it off the main thread), reporting
     * progress from 0 to 1, and checks it. Throws [IOException] with a short
     * reason when the download fails or the file is not a valid update.
     */
    fun download(c: Context, r: Release, onProgress: (Float) -> Unit): File {
        val apkUrl = r.apkUrl ?: throw IOException("no APK in this release")
        val expected = r.sha256Url?.let { u ->
            val conn = open(u)
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                parseSha256(conn.inputStream.use { it.readBytes().decodeToString() })
            } finally { conn.disconnect() }
        }
        val d = dir(c)
        d.deleteRecursively()
        d.mkdirs()
        val file = File(d, "Baiboly-${r.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(apkUrl)
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            file.delete(); throw e
        } finally { conn.disconnect() }
        if (expected != null && hex(digest.digest()) != expected) {
            file.delete(); throw IOException("checksum")
        }
        @Suppress("DEPRECATION")
        val info = c.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != c.packageName) {
            file.delete(); throw IOException("not this app")
        }
        if (!UpdateCheck.isNewer(info.versionName ?: "0", UpdateCheck.installedVersion(c))) {
            file.delete(); throw IOException("not newer")
        }
        return file
    }

    /**
     * Hands [apk] to Android's package installer. Android shows its confirmation
     * over the app; when the update is installed, the app restarts on its own.
     */
    fun install(c: Context, apk: File) {
        report(null)
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.packageName)
            setSize(apk.length())
            // Android 12+: no second prompt once this app installed the previous update itself
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(c, id, Intent(c, UpdateInstallReceiver::class.java).setPackage(c.packageName), flags)
            s.commit(result.intentSender)
        }
    }
}

/** Receives the package installer's answer: opens its confirmation, or reports the outcome. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) { UpdateInstaller.report(UpdateInstaller.Event.Failed(null)); return }
                UpdateInstaller.report(UpdateInstaller.Event.Confirming)
                runCatching { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { UpdateInstaller.report(UpdateInstaller.Event.Failed(it.message)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateInstaller.clean(c)
                UpdateInstaller.report(UpdateInstaller.Event.Installed)
            }
            else -> UpdateInstaller.report(UpdateInstaller.Event.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }
}
