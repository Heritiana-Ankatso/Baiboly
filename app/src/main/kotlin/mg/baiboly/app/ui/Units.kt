package mg.baiboly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

// ---------------------------------------------------------------- data

enum class UnitKind(val label: Int) {
    Length(R.string.units_length), Weight(R.string.units_weight), Dry(R.string.units_dry),
    Liquid(R.string.units_liquid), Money(R.string.units_money), Time(R.string.units_time),
}

/**
 * A unit of the Bible. [value] is in the kind's base: metres, grams, litres, or
 * denarii (a day's wage, Mt 20:2). Names: Malagasy 1865, Louis Segond 1910 and
 * the KJV at the verse given ([ref]); [original] is the Hebrew or Greek word.
 * [rule] is a relation the cited verse itself states ("20 gerahs = 1 shekel",
 * Ex 30:13): (n of this unit, = m of that unit). Other relations used for the
 * values (16 asses to the denarius, 2000 cubits for a sabbath's journey…) are
 * conventional estimates and are not shown as rules.
 */
data class BibleUnit(
    val id: String,
    val kind: UnitKind,
    val mg: String,
    val fr: String,
    val en: String,
    val original: String,
    val value: Double,
    val ref: Passage? = null,
    val rule: Triple<Int, Int, String>? = null,
    /** A modern unit (metre, gram, litre…), for converting from today's measures. */
    val modern: Boolean = false,
) {
    fun name(lang: String) = when (lang) { "mg" -> mg; "fr" -> fr; else -> en }
}

private fun p(b: Int, c: Int, v: Int) = Passage(b, c, v, v)

/**
 * Modern values are the usual estimates (common cubit ≈ 44.4 cm, shekel ≈ 11.4 g,
 * ephah = bath ≈ 22 L); ancient standards varied, and scholars differ. The
 * relations between units come from the verses cited.
 */
object BibleUnits {
    private const val CUBIT = 0.444
    private const val LONG_CUBIT = CUBIT + CUBIT / 6 // "a cubit and a handbreadth", Ezek 40:5
    private const val SHEKEL = 11.4
    private const val EPHAH = 22.0

    val all: List<BibleUnit> = listOf(
        // length (metres)
        BibleUnit("finger", UnitKind.Length, "voantondro", "doigt", "finger", "’etsba‘", CUBIT / 24, p(24, 52, 21)),
        BibleUnit("handbreadth", UnitKind.Length, "vodivoam-pelatanana", "palme", "handbreadth", "tefach", CUBIT / 6, p(11, 7, 26)),
        BibleUnit("span", UnitKind.Length, "zehy", "empan", "span", "zeret", CUBIT / 2, p(2, 28, 16)),
        BibleUnit("cubit", UnitKind.Length, "hakiho", "coudée", "cubit", "’ammah · pēchys", CUBIT, p(1, 6, 15)),
        BibleUnit("long_cubit", UnitKind.Length, "hakiho lava (Ezek. 40)", "coudée longue (Éz 40)", "long cubit (Ezek 40)", "’ammah", LONG_CUBIT, p(26, 40, 5)),
        BibleUnit("reed", UnitKind.Length, "volotara fandrefesana", "canne", "reed", "qaneh · kalamos", 6 * LONG_CUBIT, p(26, 40, 5), Triple(1, 6, "long_cubit")),
        BibleUnit("fathom", UnitKind.Length, "refy", "brasse", "fathom", "orgyia", 1.85, p(44, 27, 28)),
        BibleUnit("stadion", UnitKind.Length, "stadio", "stade", "furlong", "stadion", 185.0, p(42, 24, 13)),
        BibleUnit("sabbath", UnitKind.Length, "dia azo aleha amin’ny Sabata", "chemin de sabbat", "sabbath day’s journey", "sabbatou hodos", 2000 * CUBIT, p(44, 1, 12)),
        BibleUnit("mile", UnitKind.Length, "maily", "mille", "mile", "milion", 1480.0, p(40, 5, 41)),
        BibleUnit("m", UnitKind.Length, "metatra", "mètre", "metre", "", 1.0, modern = true),
        BibleUnit("km", UnitKind.Length, "kilometatra", "kilomètre", "kilometre", "", 1000.0, modern = true),
        // weight (grams)
        BibleUnit("gerah", UnitKind.Weight, "gera", "guéra", "gerah", "gerah", SHEKEL / 20, p(2, 30, 13), Triple(20, 1, "shekel")),
        BibleUnit("beka", UnitKind.Weight, "beka", "béka", "bekah", "beqa‘", SHEKEL / 2, p(2, 38, 26), Triple(2, 1, "shekel")),
        BibleUnit("shekel", UnitKind.Weight, "sekely", "sicle", "shekel", "sheqel", SHEKEL, p(2, 30, 13)),
        BibleUnit("mina", UnitKind.Weight, "mane", "mine", "maneh", "maneh · mna", 50 * SHEKEL, p(26, 45, 12)),
        BibleUnit("talent", UnitKind.Weight, "talenta", "talent", "talent", "kikkar · talanton", 3000 * SHEKEL, p(2, 38, 25), Triple(1, 3000, "shekel")),
        BibleUnit("litra", UnitKind.Weight, "livatra", "livre", "pound", "litra", 327.0, p(43, 12, 3)),
        BibleUnit("g", UnitKind.Weight, "grama", "gramme", "gram", "", 1.0, modern = true),
        BibleUnit("kg", UnitKind.Weight, "kilao", "kilogramme", "kilogram", "", 1000.0, modern = true),
        // dry measures (litres)
        BibleUnit("cab", UnitKind.Dry, "kaba", "kab", "cab", "qav", EPHAH / 18, p(12, 6, 25)),
        BibleUnit("omer", UnitKind.Dry, "omera", "omer", "omer", "‘omer", EPHAH / 10, p(2, 16, 36), Triple(10, 1, "ephah")),
        BibleUnit("seah", UnitKind.Dry, "famarana (sea)", "mesure (séa)", "measure (seah)", "se’ah", EPHAH / 3, p(12, 7, 1)),
        BibleUnit("ephah", UnitKind.Dry, "efaha", "épha", "ephah", "’efah", EPHAH, p(26, 45, 11), Triple(10, 1, "homer")),
        BibleUnit("lethech", UnitKind.Dry, "tapany homera", "léthec", "half homer", "letekh", 5 * EPHAH, p(28, 3, 2)),
        BibleUnit("homer", UnitKind.Dry, "homera", "homer", "homer", "chomer", 10 * EPHAH, p(26, 45, 11)),
        BibleUnit("choinix", UnitKind.Dry, "fatam-bary kely", "mesure (chénice)", "measure (choinix)", "choinix", 1.1, p(66, 6, 6)),
        BibleUnit("modius", UnitKind.Dry, "vata famarana", "boisseau", "bushel", "modios", 8.75, p(40, 5, 15)),
        BibleUnit("l_dry", UnitKind.Dry, "litatra", "litre", "litre", "", 1.0, modern = true),
        // liquid measures (litres)
        BibleUnit("log", UnitKind.Liquid, "loga", "log", "log", "log", EPHAH / 72, p(3, 14, 10)),
        BibleUnit("hin", UnitKind.Liquid, "hina", "hin", "hin", "hin", EPHAH / 6, p(2, 29, 40)),
        BibleUnit("bath", UnitKind.Liquid, "bata", "bath", "bath", "bat · batos", EPHAH, p(26, 45, 14), Triple(10, 1, "cor")),
        BibleUnit("cor", UnitKind.Liquid, "kora", "cor", "cor", "kor · koros", 10 * EPHAH, p(26, 45, 14)),
        BibleUnit("metretes", UnitKind.Liquid, "vata (metreta)", "mesure (métrète)", "firkin (metretes)", "metrētēs", 39.0, p(43, 2, 6)),
        BibleUnit("l", UnitKind.Liquid, "litatra", "litre", "litre", "", 1.0, modern = true),
        // money (denarii: a day's wage)
        BibleUnit("lepton", UnitKind.Money, "farantsakely (lepta)", "petite pièce (lepte)", "mite (lepton)", "lepton", 1.0 / 128, p(41, 12, 42), Triple(2, 1, "quadrans")),
        BibleUnit("quadrans", UnitKind.Money, "variroaventy (kodranta)", "quart de sou (quadrant)", "farthing (quadrans)", "kodrantēs", 1.0 / 64, p(41, 12, 42)),
        BibleUnit("assarion", UnitKind.Money, "varidimiventy (asarion)", "sou (as)", "farthing (assarion)", "assarion", 1.0 / 16, p(40, 10, 29)),
        BibleUnit("denarius", UnitKind.Money, "denaria", "denier", "penny (denarius)", "dēnarion", 1.0, p(40, 20, 2)),
        BibleUnit("drachma", UnitKind.Money, "farantsakely (drakma)", "drachme", "piece of silver (drachma)", "drachmē", 1.0, p(42, 15, 8)),
        BibleUnit("didrachma", UnitKind.Money, "antsasaky ny sekely (didrakma)", "deux drachmes", "tribute (didrachma)", "didrachmon", 2.0, p(40, 17, 24)),
        BibleUnit("stater", UnitKind.Money, "vola sekely (statera)", "statère", "piece of money (stater)", "statēr", 4.0, p(40, 17, 27)),
        BibleUnit("mina_nt", UnitKind.Money, "farantsa (mina)", "mine", "pound (mina)", "mna", 100.0, p(42, 19, 13)),
        BibleUnit("talent_nt", UnitKind.Money, "talenta", "talent", "talent", "talanton", 6000.0, p(40, 18, 24)),
    )

    fun byId(id: String) = all.first { it.id == id }
    fun of(kind: UnitKind) = all.filter { it.kind == kind }

    /**
     * Words that name a unit in each language, from its names ("famarana (sea)"
     * gives "sea"). Words with an everyday sense are left out (measure, finger,
     * reed, piece…), so a unit is found only where the text means it.
     */
    private val GENERIC = setOf(
        "measure", "mesure", "famarana", "vata", "piece", "pièce", "money", "silver", "finger", "doigt", "voantondro",
        "reed", "canne", "volotara", "fandrefesana", "half", "petite", "small", "quart", "sou", "deux", "of", "tribute",
        "antsasaky", "ny", "sekely", "vola", "farantsakely", "farantsa", "dia", "azo", "aleha", "amin’ny", "sabata", "day’s", "journey",
        "chemin", "de", "sabbath", "long", "lava", "longue", "tapany", "homera", "kely", "fatam-bary", "penny", "ezek", "éz",
    )

    private fun words(name: String) = Regex("[\\p{L}’'-]+").findAll(name).map { it.value.lowercase() }.filter { it !in GENERIC && it.length >= 3 }.toSet()

    private val keywords: Map<String, Map<String, Set<String>>> by lazy {
        all.filter { !it.modern && it.id != "long_cubit" }.associate { u ->
            u.id to mapOf("mg" to words(u.mg) + (if (u.id == "shekel") setOf("sekely") else emptySet()), "fr" to words(u.fr), "en" to words(u.en))
        }
    }

    /**
     * Units of the Bible named in a passage of [book], given its text in
     * Malagasy, French and English. A talent or a mina is the Old Testament
     * weight or the New Testament money, by the book.
     */
    fun namedIn(texts: Map<String, String>, book: Int): List<BibleUnit> {
        // plurals too: cubits, coudées, shekels
        val tokens = texts.mapValues { (_, t) -> Regex("[\\p{L}’'-]+").findAll(t.lowercase()).flatMap { sequenceOf(it.value, it.value.removeSuffix("s")) }.toSet() }
        val nt = book in 40..66
        return all.filter { u ->
            !u.modern && keywords[u.id].orEmpty().any { (lang, ws) -> ws.any { it in tokens[lang].orEmpty() } }
        }.filterNot { (nt && it.id in setOf("talent", "mina")) || (!nt && it.id in setOf("talent_nt", "mina_nt")) }
    }

    /** [amount] of [from] expressed in [to]. */
    fun convert(amount: Double, from: BibleUnit, to: BibleUnit): Double {
        require(from.kind == to.kind)
        return amount * from.value / to.value
    }
}

// ---------------------------------------------------------------- formatting

/** A readable number: 3 significant digits, no exponent, the locale's separators. */
fun formatAmount(x: Double, locale: Locale): String {
    if (x == 0.0 || !x.isFinite()) return "0"
    val digits = 3
    val mag = floor(log10(abs(x))).toInt()
    val decimals = (digits - 1 - mag).coerceIn(0, 6)
    val rounded = if (mag >= digits) (x / 10.0.pow(mag - digits + 1)).roundToLong() * 10.0.pow(mag - digits + 1) else x
    return NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = decimals
        minimumFractionDigits = 0
    }.format(rounded)
}

/** Metric (and, in English, imperial) value of [base] in the kind's base unit. */
private fun modernText(kind: UnitKind, base: Double, lang: String, locale: Locale): String? {
    fun f(x: Double) = formatAmount(x, locale)
    val metric = when (kind) {
        UnitKind.Length -> if (base >= 1000) "${f(base / 1000)} km" else if (base >= 1) "${f(base)} m" else "${f(base * 100)} cm"
        UnitKind.Weight -> if (base >= 1000) "${f(base / 1000)} kg" else "${f(base)} g"
        UnitKind.Dry, UnitKind.Liquid -> if (base >= 1) "${f(base)} L" else "${f(base * 1000)} mL"
        else -> return null
    }
    if (lang != "en") return metric
    val imperial = when (kind) {
        UnitKind.Length -> if (base >= 1609) "${f(base / 1609.344)} mi" else if (base >= 0.9144) "${f(base / 0.3048)} ft" else "${f(base / 0.0254)} in"
        UnitKind.Weight -> if (base >= 453.6) "${f(base / 453.59237)} lb" else "${f(base / 28.349523)} oz"
        UnitKind.Dry, UnitKind.Liquid -> if (base >= 3.785) "${f(base / 3.785411784)} US gal" else "${f(base / 0.946352946)} US qt"
        else -> return metric
    }
    return "$metric · $imperial"
}

/** One line for a unit: its modern value, or for money its value in denarii (days' wages). */
fun unitSummary(u: BibleUnit, locale: Locale): String? = when (u.kind) {
    UnitKind.Money -> "= ${formatAmount(u.value, locale)} × " + BibleUnits.byId("denarius").name(locale.language)
    else -> modernText(u.kind, u.value, locale.language, locale)?.let { "≈ $it" }
}

// ---------------------------------------------------------------- screen

/**
 * Units of the Bible (Study mode, Dictionary page, "Units" chip): type an
 * amount, choose its unit, and read it in every other unit of the same kind and
 * in today's measures. Money is also shown in days' wages; Time gives the hours
 * of the day and the watches of the night.
 */
@Composable
fun UnitsView(vm: AppViewModel, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val lang = locale.language.let { if (it in listOf("mg", "fr", "en")) it else "en" }
    var kind by rememberSaveable { mutableStateOf(UnitKind.Length) }
    var fromId by rememberSaveable { mutableStateOf("cubit") }
    var text by rememberSaveable { mutableStateOf("1") }
    // opened from a passage: start from the unit it names
    androidx.compose.runtime.LaunchedEffect(vm.unitsRequest) {
        vm.unitsRequest?.let { id ->
            BibleUnits.all.firstOrNull { it.id == id }?.let { kind = it.kind; fromId = it.id; text = "1" }
            vm.unitsRequest = null
        }
    }
    val from = BibleUnits.all.firstOrNull { it.id == fromId && it.kind == kind } ?: BibleUnits.of(kind).firstOrNull { !it.modern }
    val amount = text.replace(',', '.').replace(" ", "").replace(" ", "").toDoubleOrNull()
    val ornament = LocalOrnament.current

    Column(modifier.fillMaxSize().testTag("units")) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(UnitKind.entries) { k ->
                FilterChip(
                    selected = k == kind,
                    onClick = {
                        kind = k
                        fromId = when (k) {
                            UnitKind.Length -> "cubit"; UnitKind.Weight -> "shekel"; UnitKind.Dry -> "ephah"
                            UnitKind.Liquid -> "bath"; UnitKind.Money -> "denarius"; UnitKind.Time -> ""
                        }
                        text = "1"
                    },
                    label = { Text(stringResource(k.label)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("units-kind:${k.name}"),
                )
            }
        }
        if (kind == UnitKind.Time || from == null) {
            TimeOfDay(vm, lang)
            return@Column
        }
        // the amount and its unit
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(14) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp).testTag("units-amount"),
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(from.name(lang), style = MaterialTheme.typography.titleMedium, color = ornament.rubric)
                val base = amount?.let { it * from.value }
                val modern = base?.let { modernText(kind, it, lang, locale) }
                if (kind == UnitKind.Money && base != null) {
                    Text(stringResource(R.string.units_days_wages, formatAmount(base, locale)), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("units-modern"))
                } else if (modern != null && !from.modern) {
                    Text("≈ $modern", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("units-modern"))
                }
            }
        }
        Text(stringResource(R.string.units_pick_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f).testTag("units-list"), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(BibleUnits.of(kind), key = { it.id }) { u ->
                val on = u.id == from.id
                val value = amount?.let { BibleUnits.convert(it, from, u) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .background(if (on) ornament.gold.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { if (value != null && !on) text = formatAmount(value, Locale.ROOT).replace(",", ""); fromId = u.id }
                        .testTag("unit:${u.id}").padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name(lang), style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        val details = buildList {
                            if (u.original.isNotEmpty()) add(u.original)
                            u.rule?.let { (n, m, other) ->
                                val o = BibleUnits.byId(other).name(lang)
                                add(if (n == 1) "= $m $o" else if (m == 1) "1/$n $o" else "$n = $m $o")
                            }
                            if (!u.modern) modernText(kind, u.value, lang, locale)?.let { add("≈ $it") }
                        }
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = if (u.modern) FontStyle.Italic else FontStyle.Normal)
                        u.ref?.let { r ->
                            Text(
                                r.shortLabel(), style = MaterialTheme.typography.labelMedium, color = ornament.rubric,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openPassage(r) }
                                    .heightIn(min = 32.dp).padding(vertical = 6.dp).testTag("unit-ref:${u.id}"),
                            )
                        }
                    }
                    Text(
                        value?.let { formatAmount(it, locale) } ?: "—",
                        style = MaterialTheme.typography.titleMedium, color = if (on) ornament.rubric else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp).testTag("unit-value:${u.id}"),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            item {
                Text(stringResource(R.string.units_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/**
 * Hours of the day, counted from sunrise (about 6 a.m.), as in the Gospels
 * ("the third hour", Mk 15:25), and the four watches of the night (Mk 6:48; 13:35).
 */
@Composable
private fun TimeOfDay(vm: AppViewModel, lang: String) {
    val ornament = LocalOrnament.current
    LazyColumn(Modifier.fillMaxSize().testTag("units-time"), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(stringResource(R.string.units_hours_title), style = MaterialTheme.typography.titleMedium, color = ornament.rubric,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            Text(stringResource(R.string.units_hours_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        // hours named in the text, with a verse for each
        val hours = listOf(
            3 to Passage(41, 15, 25, 25), 6 to Passage(43, 4, 6, 6), 7 to Passage(43, 4, 52, 52),
            9 to Passage(44, 3, 1, 1), 10 to Passage(43, 1, 39, 39), 11 to Passage(40, 20, 6, 6),
        ).toMap()
        items((1..12).toList()) { h ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp).testTag("hour:$h"),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.units_hour_n, h), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                hours[h]?.let { r ->
                    Text(r.shortLabel(), style = MaterialTheme.typography.labelMedium, color = ornament.rubric,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openPassage(r) }.padding(8.dp))
                }
                Text("≈ %d:00".format(6 + h), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Text(stringResource(R.string.units_watches_title), style = MaterialTheme.typography.titleMedium, color = ornament.rubric,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Text(stringResource(R.string.units_watches_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        val watches = listOf("18:00 – 21:00", "21:00 – 0:00", "0:00 – 3:00", "3:00 – 6:00")
        items(watches.indices.toList()) { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp).testTag("watch:${i + 1}"),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.units_watch_n, i + 1), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (i == 3) Text(Passage(41, 6, 48, 48).shortLabel(), style = MaterialTheme.typography.labelMedium, color = ornament.rubric,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openPassage(Passage(41, 6, 48, 48)) }.padding(8.dp))
                Text(watches[i], style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
