package mg.baiboly.data

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * An optional download (tools/packs.py): a reading text other than Malagasy
 * 1865, the Hebrew and Greek text or a commentary. The release APK does not
 * carry it; the user downloads it with a tap. Every field comes from
 * packs.json inside the signed APK, so the checksums cannot be changed on the
 * server.
 */
data class Pack(
    val file: String,
    /** "text", "original" or "commentary". */
    val kind: String,
    /** Translation id, commentary id, or "original". */
    val id: String,
    val title: String,
    val licence: String,
    val version: Int,
    /** Size once unpacked on the phone, in bytes. */
    val size: Long,
    val sha256: String,
    /** Size of the download (gzip), in bytes. */
    val gzSize: Long,
    val gzSha256: String,
    val url: String,
)

/** The packs this version of the app knows of, from assets/databases/packs.json. */
class PackCatalogue(val set: String, val version: Int, val minVersion: Int, val packs: List<Pack>) {
    private val byFile = packs.associateBy { it.file }

    fun pack(file: String): Pack? = byFile[file]

    companion object {
        val EMPTY = PackCatalogue("", 0, 0, emptyList())

        fun parse(json: String): PackCatalogue {
            val o = JSONObject(json)
            val a = o.getJSONArray("packs")
            val packs = (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                Pack(
                    p.getString("file"), p.getString("kind"), p.getString("id"), p.optString("title"), p.optString("licence"),
                    p.getInt("version"), p.getLong("size"), p.getString("sha256").lowercase(), p.getLong("gz_size"),
                    p.getString("gz_sha256").lowercase(), p.getString("url"),
                )
            }
            return PackCatalogue(o.optString("set"), o.getInt("version"), o.getInt("min_version"), packs)
        }
    }
}

enum class PackState {
    /** On the phone and current. */
    Installed,
    /** On the phone and still readable; a newer version can be downloaded. */
    UpdateAvailable,
    /** Not on the phone (or too old to read): it must be downloaded. */
    NotInstalled,
}

/** A pack that is needed but not on the phone: the caller offers the download. */
class PackMissingException(val file: String) : IllegalStateException("$file is not downloaded")

/** Why a download failed; each has its own message in the app. */
enum class PackFailure { Network, Checksum, Space, Insecure, Cancelled }

class PackDownloadException(val failure: PackFailure, message: String? = null) : IOException(message ?: failure.name)

/** An open download: the response body and its length (-1 when unknown). */
class Download(val stream: InputStream, val length: Long, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() { runCatching { stream.close() }; onClose() }
}

/** Opens an HTTPS download. The app's implementation is the only network code outside the update check. */
fun interface PackSource {
    /** Throws [IOException] when the server cannot be reached or does not answer 200. */
    fun open(url: String): Download
}

/**
 * Downloads and installs a pack, safely:
 *  * HTTPS only;
 *  * enough free space first (the .gz and the unpacked database side by side);
 *  * the .gz must match the SHA-256 in packs.json, and so must the unpacked database;
 *  * its version must be one this app reads;
 *  * only then is it moved into place, so a failed or cancelled download never
 *    replaces a working file.
 * Blocking: call from a background thread.
 */
class PackInstaller(private val dbs: AssetDatabases, private val cacheDir: File) {
    fun install(pack: Pack, source: PackSource, isCancelled: () -> Boolean = { false }, onProgress: (Float) -> Unit = {}) {
        if (!pack.url.startsWith("https://")) throw PackDownloadException(PackFailure.Insecure)
        val dir = File(cacheDir, "packs").apply { mkdirs() }
        val target = dbs.pathOf(pack.file)
        target.parentFile?.mkdirs()
        val margin = 2L shl 20
        if (dir.usableSpace in 1 until pack.gzSize + pack.size + margin) throw PackDownloadException(PackFailure.Space)
        val gz = File(dir, pack.file + ".gz.part")
        val tmp = File(target.path + ".download")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val d = try { source.open(pack.url) } catch (e: PackDownloadException) { throw e } catch (e: IOException) {
                throw PackDownloadException(PackFailure.Network, e.message)
            }
            d.use {
                val total = d.length.takeIf { it > 0 } ?: pack.gzSize
                var done = 0L
                var last = -1
                gz.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled()) throw PackDownloadException(PackFailure.Cancelled)
                        val n = try { d.stream.read(buf) } catch (e: IOException) { throw PackDownloadException(PackFailure.Network, e.message) }
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done > pack.gzSize + margin) throw PackDownloadException(PackFailure.Checksum, "larger than expected")
                        val pct = (done * 100 / total).toInt()
                        if (pct != last) { last = pct; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            if (hex(digest.digest()) != pack.gzSha256) throw PackDownloadException(PackFailure.Checksum)
            val dbDigest = MessageDigest.getInstance("SHA-256")
            try {
                GZIPInputStream(gz.inputStream(), 1 shl 16).use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) throw PackDownloadException(PackFailure.Cancelled)
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            dbDigest.update(buf, 0, n)
                        }
                    }
                }
            } catch (e: PackDownloadException) { throw e } catch (e: IOException) {
                // out of space while unpacking, or a broken gzip that still matched (cannot happen with a good checksum)
                val free = target.parentFile?.usableSpace ?: 0L
                throw PackDownloadException(if (free in 1 until pack.size) PackFailure.Space else PackFailure.Checksum, e.message)
            }
            if (hex(dbDigest.digest()) != pack.sha256) throw PackDownloadException(PackFailure.Checksum)
            dbs.install(pack.file, tmp)
        } finally {
            gz.delete()
            tmp.delete()
        }
    }

    /** Leftovers of a download that was interrupted (the app was closed). */
    fun clean() { File(cacheDir, "packs").deleteRecursively() }

    companion object {
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}
