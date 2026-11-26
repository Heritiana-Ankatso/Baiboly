package mg.baiboly.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.CommentaryRepository
import mg.baiboly.data.Download
import mg.baiboly.data.Pack
import mg.baiboly.data.PackDownloadException
import mg.baiboly.data.PackFailure
import mg.baiboly.data.PackInstaller
import mg.baiboly.data.PackMissingException
import mg.baiboly.data.PackSource
import mg.baiboly.data.PackState
import mg.baiboly.data.StudyRepository
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The optional downloads at the data level, as a release build sees them: the
 * APK carries bible.db, study.db and library.db; every other database must be
 * downloaded (here from a fake source), checked, and is then read like before.
 */
@RunWith(AndroidJUnit4::class)
class PacksTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Robolectric keeps database files between the tests of a class: each test starts from a new phone. */
    @org.junit.Before fun newPhone() {
        context.getDatabasePath("bible.db").parentFile?.listFiles()?.forEach { it.delete() }
        File(context.cacheDir, "packs").deleteRecursively()
        context.getSharedPreferences("asset_databases", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** As in a release APK: the packs are not inside it. */
    private fun releaseDbs() = AssetDatabases(context, apkPacks = false)

    private fun sha(b: ByteArray) = PackInstaller.hex(MessageDigest.getInstance("SHA-256").digest(b))

    /** The pack as a test server would serve it: gzipped here, with packs.json's checksum of the database itself. */
    private fun served(dbs: AssetDatabases, file: String): Pair<Pack, ByteArray> {
        val pack = dbs.catalogue.pack(file)!!
        val raw = context.assets.open("databases/$file").use { it.readBytes() }
        assertEquals(pack.sha256, sha(raw), "the debug assets hold exactly the database packs.json describes")
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(raw) } }.toByteArray()
        return pack.copy(gzSize = gz.size.toLong(), gzSha256 = sha(gz)) to gz
    }

    private fun source(bytes: ByteArray) = PackSource { url ->
        assertTrue(url.startsWith("https://"))
        Download(ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    @Test fun releaseApkBundlesOnlyTheDefaultExperience() {
        val dbs = releaseDbs()
        val manifest = context.assets.open("databases/manifest.json").use { org.json.JSONObject(it.readBytes().decodeToString()) }
        val bundled = (0 until manifest.getJSONArray("files").length()).map { manifest.getJSONArray("files").getString(it) }.toSet()
        assertEquals(setOf("bible.db", "study.db", "library.db"), bundled)
        val bible = BibleRepository(dbs)
        // every translation but Malagasy 1865 is a pack, and so are the Hebrew/Greek text and the commentaries
        val texts = bible.translations.filter { it.id != BibleRepository.PRIMARY }
        assertTrue(texts.isNotEmpty())
        texts.forEach { t ->
            val p = dbs.catalogue.pack(t.file)
            assertTrue(p != null && p.kind == "text" && p.id == t.id, "${t.id} is offered as a download")
            assertTrue(p.url.startsWith("https://github.com/") && p.url.contains("/releases/download/data-"), p.url)
            assertTrue(p.licence.isNotBlank(), "${t.id} carries its licence")
        }
        assertTrue(dbs.catalogue.pack(StudyRepository.ORIGINAL) != null)
        CommentaryRepository.KNOWN.forEach { assertTrue(dbs.catalogue.pack("commentary-${it.id}.db") != null, it.id) }
        assertEquals(CommentaryRepository.KNOWN.map { it.id }, CommentaryRepository(dbs).available.map { it.id })
        // nothing is on a new phone: the default experience still works
        assertTrue(bible.isAvailable(BibleRepository.PRIMARY))
        assertTrue(bible.chapter(BibleRepository.PRIMARY, 43, 3).verses.isNotEmpty())
        assertFalse(bible.isAvailable("kjv"))
        assertEquals(PackState.NotInstalled, dbs.state("text-kjv.db"))
        assertFailsWith<PackMissingException> { bible.chapter("kjv", 43, 3) }
        assertFalse(StudyRepository(dbs).hasOriginal)
        // the KJV words for dictionaries and units come from the bundled Strong's tagging
        assertTrue("loved" in StudyRepository(dbs).kjvWords(43, 3, 16, 16))
    }

    @Test fun downloadIsCheckedThenRead() {
        val dbs = releaseDbs()
        val (pack, gz) = served(dbs, "text-bsb.db")
        var progress = 0f
        PackInstaller(dbs, context.cacheDir).install(pack, source(gz)) { progress = it }
        assertEquals(1f, progress)
        assertEquals(PackState.Installed, dbs.state("text-bsb.db"))
        val bible = BibleRepository(dbs)
        assertTrue(bible.isAvailable("bsb"))
        assertTrue(bible.verses("bsb", 43, 3, 16, 16).single().text.contains("God"))
        assertTrue(bible.search("bsb", "only son").isNotEmpty() || bible.search("bsb", "God loved").isNotEmpty())
        assertFalse(File(context.cacheDir, "packs/text-bsb.db.gz.part").exists(), "the download is removed once unpacked")
    }

    @Test fun damagedDownloadIsRefused() {
        val dbs = releaseDbs()
        val (pack, gz) = served(dbs, "text-bbe.db")
        val damaged = gz.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val e = assertFailsWith<PackDownloadException> { PackInstaller(dbs, context.cacheDir).install(pack, source(damaged)) }
        assertEquals(PackFailure.Checksum, e.failure)
        assertEquals(PackState.NotInstalled, dbs.state("text-bbe.db"))
        assertFalse(dbs.pathOf("text-bbe.db").exists(), "a file that fails its check is never put in place")
        // the real packs.json checksum, with bytes that are not the published file: refused too
        val real = dbs.catalogue.pack("text-bbe.db")!!
        val e2 = assertFailsWith<PackDownloadException> {
            PackInstaller(dbs, context.cacheDir).install(real, source(gz.copyOf(gz.size - 10) + ByteArray(10)))
        }
        assertEquals(PackFailure.Checksum, e2.failure)
        assertFalse(BibleRepository(dbs).isAvailable("bbe"))
    }

    @Test fun networkFailureCancelAndInsecureAddress() {
        val dbs = releaseDbs()
        val (pack, gz) = served(dbs, "text-web.db")
        val installer = PackInstaller(dbs, context.cacheDir)
        val down = assertFailsWith<PackDownloadException> { installer.install(pack, PackSource { throw IOException("HTTP 503") }) }
        assertEquals(PackFailure.Network, down.failure)
        // the connection breaks half way
        val broken = PackSource {
            Download(object : java.io.InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 10_000) gz[n - 1].toInt() and 0xff else throw IOException("reset")
                override fun read(b: ByteArray, off: Int, len: Int): Int { b[off] = read().toByte(); return 1 }
            }, gz.size.toLong())
        }
        assertEquals(PackFailure.Network, assertFailsWith<PackDownloadException> { installer.install(pack, broken) }.failure)
        assertEquals(PackFailure.Cancelled, assertFailsWith<PackDownloadException> { installer.install(pack, source(gz), isCancelled = { true }) }.failure)
        assertEquals(PackFailure.Insecure, assertFailsWith<PackDownloadException> {
            installer.install(pack.copy(url = pack.url.replace("https://", "http://")), source(gz))
        }.failure)
        assertEquals(PackState.NotInstalled, dbs.state("text-web.db"))
        assertFalse(dbs.pathOf("text-web.db").exists())
    }

    @Test fun removeFreesTheSpace() {
        val dbs = releaseDbs()
        val (pack, gz) = served(dbs, "commentary-mhcc.db")
        PackInstaller(dbs, context.cacheDir).install(pack, source(gz))
        val repo = CommentaryRepository(dbs)
        val mhcc = repo.available.first { it.id == "mhcc" }
        assertTrue(repo.isInstalled(mhcc))
        assertTrue(repo.notes(mhcc, 43, 3, 16, 16).isNotEmpty())
        dbs.remove("commentary-mhcc.db")
        assertFalse(dbs.pathOf("commentary-mhcc.db").exists())
        assertFalse(repo.isInstalled(mhcc))
        assertFailsWith<PackMissingException> { repo.notes(mhcc, 43, 3, 16, 16) }
        // bundled databases cannot be removed
        dbs.get("bible.db")
        dbs.remove("bible.db")
        assertTrue(dbs.pathOf("bible.db").exists())
    }

    /**
     * Updating from a version that bundled every text (1.11.0 and before): the texts the
     * phone had already unpacked are kept and read without a download; user.db is never
     * touched; files no longer offered, and packs too old to read, are deleted.
     */
    @Test fun migrationKeepsTextsUnpackedByEarlierVersions() {
        val dir = context.getDatabasePath("bible.db").parentFile!!.apply { mkdirs() }
        fun unpackLikeTheOldApp(name: String) =
            context.assets.open("databases/$name").use { i -> File(dir, name).outputStream().use { i.copyTo(it) } }
        unpackLikeTheOldApp("bible.db")
        unpackLikeTheOldApp("text-kjv.db")
        unpackLikeTheOldApp("original.db")
        unpackLikeTheOldApp("text-lsv.db")
        // an older pack than this app reads
        SQLiteDatabase.openDatabase(File(dir, "text-lsv.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE meta SET value='1' WHERE key='data_version'")
        }
        File(dir, "text-gone.db").writeText("a translation no longer offered")
        File(dir, "user.db").writeText("lists, notes, highlights")
        // the old app had already cleaned up for this data version
        context.getSharedPreferences("asset_databases", Context.MODE_PRIVATE).edit().putInt("cleaned_for", 13).commit()

        val dbs = releaseDbs()
        assertEquals(PackState.Installed, dbs.state("text-kjv.db"), "kept: no download needed")
        assertEquals(PackState.Installed, dbs.state(StudyRepository.ORIGINAL))
        assertTrue(BibleRepository(dbs).verses("kjv", 43, 3, 16, 16).single().text.contains("only begotten Son"))
        assertTrue(StudyRepository(dbs).hasOriginal)
        assertFalse(File(dir, "text-lsv.db").exists(), "too old to read: removed, to be downloaded again")
        assertEquals(PackState.NotInstalled, dbs.state("text-lsv.db"))
        assertFalse(File(dir, "text-gone.db").exists())
        assertEquals("lists, notes, highlights", File(dir, "user.db").readText())
        assertFalse(BibleRepository(dbs).isAvailable("bsb"), "a text never opened before must be downloaded once")
    }

    /** DATA_VERSION still replaces the bundled databases when it changes. */
    @Test fun bundledDatabaseReplacedWhenItsVersionChanges() {
        val dbs = releaseDbs()
        val file = dbs.pathOf("bible.db")
        dbs.get("bible.db").close()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE meta SET value='1' WHERE key='data_version'")
        }
        val again = releaseDbs()
        val v = again.get("bible.db").rawQuery("SELECT value FROM meta WHERE key='data_version'", null).use { it.moveToFirst(); it.getString(0) }
        val bundled = context.assets.open("databases/manifest.json").use { org.json.JSONObject(it.readBytes().decodeToString()).getInt("data_version") }
        assertEquals(bundled.toString(), v)
    }

    /** Debug builds keep every database in the APK: everything is available, as before. */
    @Test fun debugApkCarriesThePacks() {
        val dbs = AssetDatabases(context, apkPacks = true)
        assertEquals(PackState.Installed, dbs.state("text-kjv.db"))
        assertTrue(BibleRepository(dbs).verses("kjv", 43, 3, 16, 16).isNotEmpty())
    }
}
