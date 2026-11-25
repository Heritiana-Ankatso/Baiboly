package mg.baiboly.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * The read-only databases built by tools/build_data.py ship in
 * assets/databases/. A file is copied to app storage the first time it is
 * needed (only the translations actually opened take device space) and
 * replaced when the bundled data version changes.
 */
class AssetDatabases(private val context: Context) {
    private val dataVersion: Int by lazy {
        context.assets.open("databases/manifest.json").use {
            JSONObject(it.readBytes().decodeToString()).getInt("data_version")
        }
    }
    private val open = HashMap<String, SQLiteDatabase>()

    private val bundled: Set<String> by lazy {
        context.assets.open("databases/manifest.json").use {
            val files = JSONObject(it.readBytes().decodeToString()).getJSONArray("files")
            (0 until files.length()).map { i -> files.getString(i) }.toSet()
        }
    }

    init {
        removeObsolete()
    }

    /**
     * After an app update, files of the previous data version are replaced on
     * first use; files no longer shipped at all (e.g. a translation that was
     * removed) are deleted here so they do not take space. user.db is never touched.
     */
    private fun removeObsolete() {
        val prefs = context.getSharedPreferences("asset_databases", Context.MODE_PRIVATE)
        if (prefs.getInt("cleaned_for", -1) == dataVersion) return
        val dir = context.getDatabasePath("bible.db").parentFile ?: return
        dir.listFiles()?.forEach { f ->
            val name = f.name.removeSuffix("-journal").removeSuffix(".tmp")
            val ours = name == "bible.db" || name == "study.db" || name == "original.db" || name == "library.db" || name.startsWith("text-") || name.startsWith("commentary-")
            if (ours && name !in bundled) f.delete()
        }
        prefs.edit().putInt("cleaned_for", dataVersion).apply()
    }

    @Synchronized
    fun get(name: String): SQLiteDatabase {
        open[name]?.let { return it }
        val file = context.getDatabasePath(name)
        if (!file.exists() || installedVersion(file) != dataVersion) copy(name, file)
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        open[name] = db
        return db
    }

    fun isInstalled(name: String): Boolean = context.getDatabasePath(name).exists()

    /** Shipped with this version of the app (listed in the manifest). */
    fun isBundled(name: String): Boolean = name in bundled

    private fun installedVersion(file: File): Int = try {
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
    }
}
