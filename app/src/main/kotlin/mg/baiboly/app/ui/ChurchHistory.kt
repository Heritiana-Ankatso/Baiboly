package mg.baiboly.app.ui

import android.content.Context
import mg.baiboly.data.BibleEvent
import mg.baiboly.data.Era

/**
 * Church history outside the Bible (assets/church_history.tsv), for the timeline's switch "Church history
 * (outside the Bible)": events between the Testaments and in the apostolic age that Scripture does not
 * tell, then the eras from the Church Fathers to today and their events, lives and periods. Events get
 * negative ids so they never meet the Bible's own (the chart draws them as hollow rings); they name no verses.
 */
class ChurchHistory(val eras: List<Era>, val eraNames: Map<String, String>, val events: List<BibleEvent>, val family: Map<Int, String>) {
    companion object {
        /** Titles in [lang]; Malagasy falls back to French where no Malagasy title is given. */
        fun load(context: Context, lang: String): ChurchHistory {
            val eras = mutableListOf<Era>()
            val names = mutableMapOf<String, String>()
            val events = mutableListOf<BibleEvent>()
            val family = mutableMapOf<Int, String>()
            context.assets.open("church_history.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEachIndexed { i, line ->
                    val f = line.split('\t')
                    if (f.size < 9) return@forEachIndexed
                    val kind = f[0]; val year = f[1].toInt(); val end = f[2].toIntOrNull() ?: 0
                    val approx = f[3] == "1"; val fam = f[4]; val weight = f[5].toIntOrNull() ?: 0
                    val title = when (lang) {
                        "fr" -> f[8]
                        "mg" -> f.getOrNull(9)?.takeIf { it.isNotBlank() } ?: f[8]
                        else -> f[7]
                    }
                    if (kind == "era") {
                        eras += Era(f[6], year, end)
                        names[f[6]] = title
                        return@forEachIndexed
                    }
                    val id = -(i + 1)
                    val duration = if (end > year) "${end - year}Y" else ""
                    events += BibleEvent(
                        id = id, title = title, year = year, date = "", duration = duration, approximate = approx,
                        notes = "", ussher = null, partOf = null, first = null,
                        kind = if (kind == "point") "point" else kind, weight = weight,
                    )
                    family[id] = fam
                }
            }
            return ChurchHistory(eras, names, events.sortedBy { it.year }, family)
        }
    }
}
