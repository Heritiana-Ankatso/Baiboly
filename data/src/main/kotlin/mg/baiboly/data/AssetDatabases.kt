package mg.baiboly.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * The read-only databases built by tools/build_data.py.
 *
 * Bundled (in every APK, listed in assets/databases/manifest.json): bible.db,
 * study.db and library.db. A file is copied to app storage the first time it is
 * needed and replaced when the bundled DATA_VERSION changes.
 *
 * Optional downloads ("packs", listed in assets/databases/packs.json): the
 * other reading texts, original.db and the commentaries. A release APK does
 * not carry them: the user downloads one with a tap ([PackInstaller]). A
 * pack on the phone is used while its meta.data_version lies between the
 * catalogue's min_version and the pack's version. Debug builds (and the
 * tests) also carry the packs inside the APK and copy them out as before,
 * unless [useApkPacks] is off.
 *
 * Migration from the versions that bundled everything: a pack the phone had
 * already unpacked has the same file name and a readable version, so it is
 * simply kept as installed; nothing is downloaded again. user.db is never touched.
 */
class AssetDatabases(private val context: Context, private val apkPacks: Boolean = useApkPacks) {
    companion object {
        /** Off in tests that check how a release build behaves without its packs. */
        @Volatile
        var useApkPacks = true
    }

    private fun manifest(): JSONObject = context.assets.open("databases/manifest.json").use { JSONObject(it.readBytes().decodeToString()) }

    private val dataVersion: Int by lazy { manifest().getInt("data_version") }
    private val open = HashMap<String, SQLiteDatabase>()
    private val versions = HashMap<String, Int>()

    private val bundled: Set<String> by lazy {
        val files = manifest().getJSONArray("files")
        (0 until files.length()).map { i -> files.getString(i) }.toSet()
    }

    /** What can be downloaded with this version of the app. */
    val catalogue: PackCatalogue by lazy {
        runCatching { context.assets.open("databases/packs.json").use { PackCatalogue.parse(it.readBytes().decodeToString()) } }
            .getOrDefault(PackCatalogue.EMPTY)
    }

    private val inApk = HashMap<String, Boolean>()

    init {
        removeObsolete()
    }

    /**
     * After an app update, files of the previous data version are replaced on
     * first use; files no longer shipped or offered at all (e.g. a translation
     * that was removed) and packs too old to read are deleted here so they do
     * not take space. Packs the user has are kept. user.db is never touched.
     */
    private fun removeObsolete() {
        val prefs = context.getSharedPreferences("asset_databases", Context.MODE_PRIVATE)
        val key = "$dataVersion/${catalogue.minVersion}"
        if (prefs.getString("cleaned_for_key", null) == key) return
        val dir = context.getDatabasePath("bible.db").parentFile ?: return
        dir.listFiles()?.forEach { f ->
            val name = f.name.removeSuffix("-journal").removeSuffix(".tmp").removeSuffix(".download")
            val ours = name == "bible.db" || name == "study.db" || name == "original.db" || name == "library.db" || name.startsWith("text-") || name.startsWith("commentary-")
            if (!ours || name in bundled) return@forEach
            val pack = catalogue.pack(name)
            when {
                pack == null -> f.delete()
                f.name != name -> f.delete() // an interrupted copy or download
                readVersion(f) < catalogue.minVersion -> f.delete()
            }
        }
        prefs.edit().putString("cleaned_for_key", key).putInt("cleaned_for", dataVersion).apply()
    }

    fun pathOf(name: String): File = context.getDatabasePath(name)

    @Synchronized
    fun get(name: String): SQLiteDatabase {
        open[name]?.let { return it }
        val file = pathOf(name)
        val pack = catalogue.pack(name)
        if (pack == null) {
            if (!file.exists() || version(name) != dataVersion) copy(name, file)
        } else {
            val v = version(name)
            if (v != pack.version && hasInApk(name)) copy(name, file)
            else if (v !in catalogue.minVersion..pack.version) throw PackMissingException(name)
        }
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        open[name] = db
        return db
    }

    fun isInstalled(name: String): Boolean = pathOf(name).exists()

    /** Shipped in every APK (listed in the manifest). */
    fun isBundled(name: String): Boolean = name in bundled

    /** Bundled, or offered as a download. */
    fun isOffered(name: String): Boolean = name in bundled || catalogue.pack(name) != null

    @Synchronized
    fun state(name: String): PackState {
        val pack = catalogue.pack(name) ?: return if (name in bundled) PackState.Installed else PackState.NotInstalled
        val v = version(name)
        return when {
            v == pack.version || hasInApk(name) -> PackState.Installed
            v in catalogue.minVersion until pack.version -> PackState.UpdateAvailable
            else -> PackState.NotInstalled
        }
    }

    /** Can be opened now, without a download. */
    fun isUsable(name: String): Boolean = state(name) != PackState.NotInstalled

    /** Moves a downloaded, checked database into place (closing the old copy first). */
    @Synchronized
    fun install(name: String, downloaded: File) {
        open.remove(name)?.close()
        val target = pathOf(name)
        File(target.path + "-journal").delete()
        if (!downloaded.renameTo(target)) {
            target.delete()
            if (!downloaded.renameTo(target)) throw java.io.IOException("cannot move $name into place")
        }
        versions.remove(name)
    }

    /** Deletes a downloaded pack from the phone (Settings › Bible › Downloads). Bundled files cannot be removed. */
    @Synchronized
    fun remove(name: String) {
        if (catalogue.pack(name) == null) return
        open.remove(name)?.close()
        pathOf(name).delete()
        File(pathOf(name).path + "-journal").delete()
        versions.remove(name)
    }

    private fun hasInApk(name: String): Boolean = apkPacks && inApk.getOrPut(name) {
        runCatching { context.assets.open("databases/$name").close(); true }.getOrDefault(false)
    }

    private fun version(name: String): Int = versions.getOrPut(name) { readVersion(pathOf(name)) }

    private fun readVersion(file: File): Int = if (!file.exists()) -1 else try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT value FROM meta WHERE key='data_version'", null).use { c ->
                if (c.moveToFirst()) c.getString(0).toInt() else -1
            }
        }
    } catch (e: Exception) {
        -1
    }

    private fun copy(name: String, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        context.assets.open("databases/$name").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
        File(target.path + "-journal").delete()
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        versions.remove(name)
    }
}
