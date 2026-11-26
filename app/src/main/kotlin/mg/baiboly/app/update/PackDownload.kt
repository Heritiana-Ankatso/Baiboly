// network-check: allow-file (optional downloads: a Bible, the Hebrew and Greek text or a commentary from the project's GitHub release, only on the user's tap)
package mg.baiboly.app.update

import mg.baiboly.data.Download
import mg.baiboly.data.PackDownloadException
import mg.baiboly.data.PackFailure
import mg.baiboly.data.PackSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opens the download of a pack (a release asset of the data-<N> pre-release on
 * GitHub). HTTPS only: HttpURLConnection never follows a redirect from HTTPS to
 * HTTP, and the final address is checked again. The data is checked against the
 * SHA-256 in the APK's packs.json by [mg.baiboly.data.PackInstaller] before use.
 * Nothing about the reader is sent: a plain GET with the app's name as user agent.
 */
object PackDownload : PackSource {
    override fun open(url: String): Download {
        if (!url.startsWith("https://")) throw PackDownloadException(PackFailure.Insecure)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub sends release files from another host
            setRequestProperty("User-Agent", "Baiboly-Android")
        }
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            if (conn.url.protocol != "https") throw PackDownloadException(PackFailure.Insecure)
            return Download(conn.inputStream, conn.contentLengthLong) { conn.disconnect() }
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
    }
}
