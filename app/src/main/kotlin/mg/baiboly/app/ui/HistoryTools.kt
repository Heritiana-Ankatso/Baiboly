package mg.baiboly.app.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.BibleEvent
import mg.baiboly.data.CreedDoc
import mg.baiboly.data.CreedItem
import mg.baiboly.data.HistoryRepository
import mg.baiboly.data.Person
import mg.baiboly.data.Relative

// Family tree, timeline, confessions and councils (docs/ideas.md A1-A3, B1-B2).
// Shown on the Dictionary page (chips People, Timeline, Confessions) and in the
// Study panel of a passage.

@Composable
internal fun yearLabel(year: Int): String =
    if (year <= 0) stringResource(R.string.year_bc, 1 - year) else stringResource(R.string.year_ad, year)

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = LocalOrnament.current.gold,
        modifier = modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Verses as a wrapping row of references; a tap opens the verse. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseChips(vm: AppViewModel, verses: List<Passage>, tag: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag(tag)) {
        verses.forEach { p ->
            TextButton(onClick = { vm.openPassage(p) }, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(p.label(), color = LocalOrnament.current.rubric)
            }
        }
    }
}

// ---------------------------------------------------------------- people

private val RELATION_ORDER = listOf("father", "legal_father", "mother", "partner", "sibling", "half_sibling", "child", "legal_child")

/** A note for a person's page, where a family link would say more (or other) than the text. */
@Composable
private fun personNote(key: String): String? = when (key) {
    "son_of_god" -> stringResource(R.string.note_son_of_god)
    "virgin_birth" -> stringResource(R.string.note_virgin_birth)
    "no_family" -> stringResource(R.string.note_no_family)
    else -> null
}

@Composable
private fun relationTitle(kind: String): String = when (kind) {
    "father" -> stringResource(R.string.rel_father)
    "mother" -> stringResource(R.string.rel_mother)
    "partner" -> stringResource(R.string.rel_partners)
    "sibling" -> stringResource(R.string.rel_siblings)
    "half_sibling" -> stringResource(R.string.rel_half_siblings)
    "legal_father" -> stringResource(R.string.rel_legal_father)
    "legal_child" -> stringResource(R.string.rel_legal_child)
    else -> stringResource(R.string.rel_children)
}

/** "Abraham" + what tells apart people of the same name: father, else first verse. */
@Composable
private fun personSubtitle(p: Person, father: Person? = null): String = listOfNotNull(
    father?.let { stringResource(R.string.child_of, it.name) },
    p.first?.label(),
    stringResource(R.string.place_mentions, p.mentions),
).joinToString(" · ")

@Composable
fun PeopleView(vm: AppViewModel, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Person>>(emptyList()) }
    var open by remember { mutableStateOf(vm.personRequest) }
    var trail by remember { mutableStateOf(listOf<Int>()) }
    LaunchedEffect(vm.personRequest) { vm.personRequest?.let { open = it; vm.personRequest = null } }
    LaunchedEffect(query) { found = vm.onIo { vm.chronicle.searchPeople(query) } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = trail.lastOrNull(); trail = trail.dropLast(1) }
    val id = open
    if (id != null) {
        PersonView(vm, id, modifier) { next -> trail = trail + id; open = next }
        return
    }
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.people_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("people-search"),
        )
        LazyColumn(Modifier.weight(1f).testTag("people-list")) {
            items(found, key = { it.id }) { p ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = p.id }
                        .testTag("person:${p.id}").padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(p.name + if (p.alsoCalled.isNotEmpty()) " (${p.alsoCalled})" else "", style = MaterialTheme.typography.bodyLarge)
                    Note(personSubtitle(p))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Note(stringResource(R.string.theographic_credit), Modifier.padding(16.dp))
    }
}

/** One person: dates, family (parents, spouses, brothers and sisters, children), father line, events, verses. */
@Composable
private fun PersonView(vm: AppViewModel, id: Int, modifier: Modifier, onPerson: (Int) -> Unit) {
    var person by remember(id) { mutableStateOf<Person?>(null) }
    var relatives by remember(id) { mutableStateOf<List<Relative>>(emptyList()) }
    var line by remember(id) { mutableStateOf<List<Person>>(emptyList()) }
    var events by remember(id) { mutableStateOf<List<BibleEvent>>(emptyList()) }
    var verses by remember(id) { mutableStateOf<List<Passage>>(emptyList()) }
    LaunchedEffect(id) {
        vm.onIo {
            person = vm.chronicle.person(id)
            relatives = vm.chronicle.relatives(id)
            line = vm.chronicle.fatherLine(id)
            events = vm.chronicle.eventsOf(id)
            verses = vm.chronicle.versesOf(id)
        }
    }
    val p = person ?: return Text(stringResource(R.string.loading), modifier.padding(20.dp))
    val ornament = LocalOrnament.current
    // folded[k] = true when the reader folded section k, false when they unfolded it
    var folded by remember(id) { mutableStateOf(mapOf<String, Boolean>()) }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("person-view")) {
        item {
            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp))
            if (p.alsoCalled.isNotEmpty()) Note(stringResource(R.string.also_called, p.alsoCalled))
            personNote(p.note)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 6.dp).testTag("person-note"))
            }
            val born = p.born?.let { yearLabel(it) }
            val died = p.died?.let { yearLabel(it) }
            if (born != null || died != null) {
                Text(
                    listOfNotNull(born?.let { stringResource(R.string.born_x, it) }, died?.let { stringResource(R.string.died_x, it) }).joinToString(" · ") +
                        " " + stringResource(R.string.after_jones),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Fleuron()
        }
        // each part folds away (the father line and the verses start folded: they are long)
        val shut = mutableSetOf<String>().also { if (line.size > 6) it += "line"; if (verses.size > 12) it += "verses" }
        fun isOpen(k: String) = folded[k]?.let { !it } ?: (k !in shut)
        fun toggle(k: String) { folded = folded + (k to isOpen(k)) }
        // Father line, from the farthest ancestor down to the person (a column, not a count of generations)
        if (line.isNotEmpty()) {
            collapsibleSection("line", { stringResource(R.string.father_line) }, isOpen("line"), { toggle("line") },
                rows = line.reversed(), rowKey = { "line${it.id}" }, detail = { "${line.size}" }) { a ->
                Text(
                    "↓ " + a.name, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onPerson(a.id) }.testTag("line:${a.id}").padding(vertical = 8.dp),
                )
            }
            if (isOpen("line")) item {
                Text("✠ " + p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ornament.rubric, modifier = Modifier.padding(vertical = 6.dp))
                // the first ancestor's note (Adam: "the son of God" by creation, Luke 3:38)
                line.last().note.let { k -> personNote(k)?.let { Note(line.last().name + ": " + it, Modifier.padding(bottom = 4.dp)) } }
                Note(stringResource(R.string.genealogy_note))
            }
        }
        RELATION_ORDER.forEach { kind ->
            val group = relatives.filter { it.kind == kind }.map { it.person }.distinctBy { it.id }
            if (group.isNotEmpty()) {
                collapsibleSection("rel-$kind", { relationTitle(kind) + " (${group.size})" }, isOpen("rel-$kind"), { toggle("rel-$kind") },
                    rows = group, rowKey = { "$kind${it.id}" }) { r ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPerson(r.id) }.testTag("relative:${r.id}").padding(vertical = 6.dp),
                    ) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Note(personSubtitle(r))
                    }
                }
            }
        }
        if (relatives.isEmpty() && line.isEmpty()) item { Note(stringResource(R.string.no_family), Modifier.padding(top = 8.dp)) }
        if (events.isNotEmpty()) {
            collapsibleSection("events", { stringResource(R.string.events) + " (${events.size})" }, isOpen("events"), { toggle("events") },
                rows = events, rowKey = { "ev${it.id}" }) { e -> EventRow(vm, e) }
        }
        if (verses.isNotEmpty()) {
            collapsibleSection("verses", { stringResource(R.string.verses_x, p.mentions) }, isOpen("verses"), { toggle("verses") },
                rows = listOf(verses), rowKey = { "verses" }) { vs -> VerseChips(vm, vs, "person-verses") }
        }
        item {
            Note(stringResource(R.string.names_note), Modifier.padding(top = 16.dp))
            Note(stringResource(R.string.theographic_credit), Modifier.padding(vertical = 16.dp))
        }
    }
}

// ---------------------------------------------------------------- timeline

/** "1491 BC · Ussher 1491 BC · about · 40 years" */
@Composable
private fun eventDates(e: BibleEvent): String = listOfNotNull(
    (if (e.approximate) stringResource(R.string.approx) + " " else "") + yearLabel(e.year),
    e.ussher?.takeIf { it != e.year }?.let { stringResource(R.string.ussher_x, yearLabel(it)) },
    durationLabel(e.duration),
).joinToString(" · ")

@Composable
private fun durationLabel(d: String): String? {
    val m = Regex("^(\\d+)([DWMY])$").find(d) ?: return null
    val n = m.groupValues[1].toInt()
    if (n <= 1 && m.groupValues[2] == "D") return null
    return when (m.groupValues[2]) {
        "D" -> stringResource(R.string.dur_days, n)
        "W" -> stringResource(R.string.dur_weeks, n)
        "M" -> stringResource(R.string.dur_months, n)
        else -> stringResource(R.string.dur_years, n)
    }
}

@Composable
private fun EventRow(vm: AppViewModel, e: BibleEvent, family: String? = null) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.openEvent(e.id) }.testTag("event:${e.id}").padding(vertical = 6.dp)) {
        Text(e.title, style = MaterialTheme.typography.bodyLarge)
        Note(listOfNotNull(eventDates(e), e.first?.label(), churchFamilyLabel(family)).joinToString(" · "))
    }
}

@Composable
fun TimelineView(vm: AppViewModel, modifier: Modifier = Modifier) {
    var bible by remember { mutableStateOf<List<BibleEvent>>(emptyList()) }
    var bibleEras by remember { mutableStateOf<List<mg.baiboly.data.Era>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(vm.eventRequest) }
    // the chart by default; the list is its table view (and the place to search)
    var asList by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Go deeper: on past the apostles to today (the Fathers, the councils, the Reformation, missions, Madagascar)
    var toToday by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lang = refLang()
    var church by remember { mutableStateOf<ChurchHistory?>(null) }
    LaunchedEffect(Unit) { vm.onIo { bible = vm.chronicle.timeline; bibleEras = vm.chronicle.eras } }
    LaunchedEffect(lang) { church = vm.onIo { ChurchHistory.load(ctx, lang) } }
    LaunchedEffect(vm.eventRequest) { vm.eventRequest?.let { open = it; vm.eventRequest = null } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    val ch = church
    val all = if (toToday && ch != null) bible + ch.events else bible
    val eras = remember(bibleEras, toToday, ch) {
        if (!toToday || ch == null || bibleEras.isEmpty()) bibleEras
        else {
            // the Bible's last era (the early church) ends where church history takes over
            val handover = ch.eras.first().start
            bibleEras.dropLast(1) + bibleEras.last().let { mg.baiboly.data.Era(it.key, it.start, maxOf(it.start + 1, minOf(it.end, handover))) } + ch.eras
        }
    }
    val extraNames = ch?.eraNames.orEmpty()
    val id = open
    if (id != null) {
        (all.firstOrNull { it.id == id } ?: ch?.events?.firstOrNull { it.id == id })
            ?.let { EventView(vm, it, modifier, ch?.family?.get(it.id)) } ?: Text(stringResource(R.string.loading), modifier.padding(20.dp))
        return
    }
    val q = mg.baiboly.core.text.TextNorm.norm(query.trim())
    val shown = remember(all, q) { if (q.isEmpty()) all else all.filter { mg.baiboly.core.text.TextNorm.norm(it.title).contains(q) } }
    Column(modifier) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.FilterChip(selected = !asList, onClick = { asList = false },
                label = { Text(stringResource(R.string.tl_chart)) }, modifier = Modifier.testTag("tl-mode-chart"))
            androidx.compose.material3.FilterChip(selected = asList, onClick = { asList = true },
                label = { Text(stringResource(R.string.tl_list)) }, modifier = Modifier.testTag("tl-mode-list"))
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.FilterChip(selected = toToday, onClick = { toToday = !toToday },
                label = { Text(stringResource(R.string.tl_to_today)) }, modifier = Modifier.testTag("tl-to-today"))
        }
        if (!asList) {
            Note(stringResource(R.string.tl_hint), Modifier.padding(horizontal = 16.dp))
            TimelineChart(vm, all, eras, Modifier.weight(1f), extraNames)
            Note(stringResource(if (toToday) R.string.church_history_credit else R.string.theographic_credit),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            return@Column
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.timeline_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("timeline-search"),
        )
        // condensed: one line per era with its count; a tap unfolds it (a search unfolds them all)
        var openEras by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
        val byEra = remember(shown, eras) {
            shown.groupBy { e -> eras.lastOrNull { e.year >= it.start }?.key ?: eras.firstOrNull()?.key ?: "" }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp).testTag("timeline")) {
            item { Note(stringResource(R.string.timeline_note), Modifier.padding(vertical = 6.dp)) }
            eras.filter { byEra[it.key]?.isNotEmpty() == true }.forEach { era ->
                val rows = byEra.getValue(era.key)
                val isOpen = q.isNotEmpty() || era.key in openEras
                collapsibleSection(
                    key = "era-${era.key}",
                    title = { extraNames[era.key] ?: eraName(era.key) },
                    detail = { yearLabel(era.start) + " – " + yearLabel(era.end - 1) + " · " + pluralStringResource(R.plurals.n_events, rows.size, rows.size) },
                    open = isOpen,
                    onToggle = { openEras = if (era.key in openEras) openEras - era.key else openEras + era.key },
                    rows = rows,
                    rowKey = { it.id },
                ) { e -> EventRow(vm, e, ch?.family?.get(e.id)); HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            }
            item {
                Note(stringResource(R.string.theographic_credit), Modifier.padding(top = 16.dp))
                if (toToday) Note(stringResource(R.string.church_history_credit), Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/** The tradition an event of church history belongs to, when it is not common to all. */
@Composable
private fun churchFamilyLabel(f: String?): String? = when (f) {
    "reformed" -> stringResource(R.string.tradition_reformed)
    "catholic" -> stringResource(R.string.catholic)
    "orthodox" -> stringResource(R.string.tradition_orthodox)
    "madagascar" -> stringResource(R.string.madagascar)
    else -> null
}

@Composable
private fun EventView(vm: AppViewModel, e: BibleEvent, modifier: Modifier, family: String? = null) {
    var people by remember(e.id) { mutableStateOf<List<Person>>(emptyList()) }
    var verses by remember(e.id) { mutableStateOf<List<Passage>>(emptyList()) }
    LaunchedEffect(e.id) { vm.onIo { people = vm.chronicle.eventPeople(e.id); verses = vm.chronicle.eventVerses(e.id) } }
    val ornament = LocalOrnament.current
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("event-view")) {
        item {
            Text(e.title, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp))
            Fleuron()
            val churchEvent = e.id < 0
            Text(
                (if (e.approximate) stringResource(R.string.approx) + " " else "") + yearLabel(e.year) +
                    (if (churchEvent) (if (e.endYear > e.year) " – " + yearLabel(e.endYear.toInt()) else "") else " " + stringResource(R.string.after_jones)),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (churchEvent) churchFamilyLabel(family)?.let { Note(it, Modifier.padding(top = 4.dp)) }
            e.ussher?.let { Text(stringResource(R.string.ussher_x, yearLabel(it)) + " " + stringResource(R.string.after_tsk), style = MaterialTheme.typography.bodyMedium) }
            durationLabel(e.duration)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (e.notes.isNotEmpty()) Text(e.notes, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 8.dp))
            if (!churchEvent) Note(stringResource(R.string.dates_reconstructed), Modifier.padding(top = 8.dp))
        }
        if (verses.isNotEmpty()) item { SectionTitle(stringResource(R.string.verses_x, verses.size)); VerseChips(vm, verses, "event-verses") }
        if (people.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.tool_people)) }
            items(people, key = { it.id }) { p ->
                Text(
                    p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { vm.openPerson(p.id) }.padding(vertical = 8.dp),
                )
            }
        }
        item { Note(stringResource(if (e.id < 0) R.string.church_history_credit else R.string.theographic_credit), Modifier.padding(vertical = 16.dp)) }
    }
}

// ---------------------------------------------------------------- confessions and councils

@Composable
internal fun familyLabel(family: String): String = when (family) {
    "reformed" -> stringResource(R.string.tradition_reformed)
    "catholic" -> stringResource(R.string.catholic)
    "orthodox" -> stringResource(R.string.tradition_orthodox)
    else -> stringResource(R.string.family_creeds)
}

/** The traditions that receive a council, as a label ("Catholic, Orthodox"). */
@Composable
private fun councilLabel(c: mg.baiboly.data.Council): String =
    c.families.filter { it != "creed" }.map { familyLabel(it) }.joinToString(", ")

@Composable
fun ConfessionsView(vm: AppViewModel, modifier: Modifier = Modifier) {
    var docs by remember { mutableStateOf<List<CreedDoc>>(emptyList()) }
    var councils by remember { mutableStateOf<List<mg.baiboly.data.Council>>(emptyList()) }
    var open by remember { mutableStateOf(vm.creedRequest) }
    var deeper by remember { mutableStateOf(false) }
    var shutParts by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { vm.onIo { docs = vm.chronicle.creedDocs; councils = vm.chronicle.councils } }
    LaunchedEffect(vm.creedRequest) { vm.creedRequest?.let { open = it; vm.creedRequest = null } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    val o = open
    if (o != null) {
        docs.firstOrNull { it.id == o.first }?.let { CreedDocView(vm, it, o.second, modifier) }
        return
    }
    val own = vm.ownFamilies()
    val ornament = LocalOrnament.current
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("confessions")) {
        item {
            Text(
                stringResource(R.string.your_tradition) + ": " +
                    stringResource(if (vm.tradition == mg.baiboly.app.Tradition.Protestant) R.string.protestant else R.string.catholic),
                style = MaterialTheme.typography.titleMedium, color = ornament.rubric, modifier = Modifier.padding(top = 8.dp),
            )
        }
        listOf("creed", "reformed", "catholic", "orthodox").filter { it in own }.forEach { fam ->
            val famDocs = docs.filter { it.family == fam }
            collapsibleSection("fam-$fam", { familyLabel(fam) + " (${famDocs.size})" }, "fam-$fam" !in shutParts,
                { shutParts = if ("fam-$fam" in shutParts) shutParts - "fam-$fam" else shutParts + "fam-$fam" },
                rows = famDocs, rowKey = { it.id }) { d -> DocRow(d, marked = false) { open = d.id to -1 } }
        }
        // councils received by the chosen tradition (the ecumenical ones by all); the others under Go deeper
        val councilsShown = councils.filter { c -> c.families.any { it in own } }
        if (councilsShown.isNotEmpty()) {
            collapsibleSection("councils", { stringResource(R.string.councils) + " (${councilsShown.size})" }, "councils" !in shutParts,
                { shutParts = if ("councils" in shutParts) shutParts - "councils" else shutParts + "councils" },
                rows = councilsShown, rowKey = { "c-" + it.id }) { c -> CouncilRow(c, docs) { open = it to -1 } }
            if ("councils" !in shutParts) item { Note(stringResource(R.string.councils_note), Modifier.padding(top = 4.dp)) }
        }
        // Go deeper: documents and councils of the other traditions, each marked with its tradition
        // (left out altogether when there is none)
        val others = docs.filter { it.family !in own }
        val otherCouncils = councils.filter { it !in councilsShown }
        if (others.isNotEmpty() || otherCouncils.isNotEmpty()) {
            item {
                TextButton(onClick = { deeper = !deeper }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp).testTag("go-deeper")) {
                    Text((if (deeper) "▾ " else "▸ ") + stringResource(R.string.go_deeper) + " (${others.size + otherCouncils.size})",
                        style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (deeper) {
            items(others, key = { "o-" + it.id }) { d -> DocRow(d, marked = true) { open = d.id to -1 } }
            items(otherCouncils, key = { "oc-" + it.id }) { c -> CouncilRow(c, docs, marked = true) { open = it to -1 } }
        }
        item {
            Note(stringResource(R.string.creeds_text_note), Modifier.padding(top = 16.dp))
            Note(stringResource(R.string.creeds_credit), Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun DocRow(d: CreedDoc, marked: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onOpen).testTag("creed:${d.id}").padding(vertical = 8.dp)) {
        Text((if (marked) familyLabel(d.family) + ": " else "") + d.title, style = MaterialTheme.typography.bodyLarge)
        Note(listOf(d.short, d.year, d.place).filter { it.isNotEmpty() }.joinToString(" · "))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun CouncilRow(c: mg.baiboly.data.Council, docs: List<CreedDoc>, marked: Boolean = false, onDoc: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("council:${c.id}")) {
        val label = councilLabel(c)
        Text((if (marked && label.isNotEmpty()) "$label: " else "") + c.name, style = MaterialTheme.typography.bodyLarge)
        Note(listOf(c.place, c.year).filter { it.isNotEmpty() }.joinToString(" · "))
        if (c.summary.isNotEmpty()) Text(c.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        val issued = c.docs.mapNotNull { id -> docs.firstOrNull { it.id == id } }
        if (issued.isNotEmpty()) Row {
            Text(stringResource(R.string.council_issued) + " ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            issued.forEach { d ->
                TextButton(onClick = { onDoc(d.id) }, modifier = Modifier.heightIn(min = 40.dp)) { Text(d.short, color = LocalOrnament.current.rubric) }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/** A document: its articles or questions in order, each with its proof texts. [focus]: item to scroll to, -1 for the start. */
@Composable
private fun CreedDocView(vm: AppViewModel, d: CreedDoc, focus: Int, modifier: Modifier) {
    var items by remember(d.id) { mutableStateOf<List<CreedItem>>(emptyList()) }
    LaunchedEffect(d.id) { items = vm.onIo { vm.chronicle.creedItems(d.id) } }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(items, focus) { if (focus >= 0 && items.isNotEmpty()) state.scrollToItem(1 + items.indexOfFirst { it.seq == focus }.coerceAtLeast(0)) }
    val ornament = LocalOrnament.current
    val own = d.family in vm.ownFamilies()
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("creed-view"), state = state) {
        item {
            Text(d.title, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp))
            Note(listOf(familyLabel(d.family), d.year, d.place).filter { it.isNotEmpty() }.joinToString(" · "))
            if (!own) Note(stringResource(R.string.other_tradition_note, familyLabel(d.family)), Modifier.padding(top = 4.dp))
            Fleuron()
        }
        var lastGroup = ""
        items.forEach { it ->
            if (it.group.isNotEmpty() && it.group != lastGroup) {
                lastGroup = it.group
                item(key = "g${it.seq}") { SectionTitle(it.group) }
            }
            item(key = it.seq) { CreedItemView(vm, d, it) }
        }
        item { Note(stringResource(R.string.creeds_credit), Modifier.padding(vertical = 16.dp)) }
    }
}

@Composable
internal fun CreedItemView(vm: AppViewModel, d: CreedDoc?, it: CreedItem, compact: Boolean = false) {
    val ornament = LocalOrnament.current
    val link = TextLinkStyles(SpanStyle(color = ornament.rubric, fontWeight = FontWeight.Bold))
    // footnote markers [a] / [1] become small raised letters
    val body = remember(it.text, ornament) {
        buildAnnotatedString {
            var pos = 0
            for (m in Regex("\\[([a-z]{1,2}|\\d{1,2})\\]").findAll(it.text)) {
                append(it.text.substring(pos, m.range.first))
                withStyle(SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Superscript, color = ornament.gold)) { append(m.groupValues[1]) }
                pos = m.range.last + 1
            }
            append(it.text.substring(pos))
        }
    }
    val lang = refLang()
    val proofs = remember(it.proofs, ornament, lang) {
        it.proofs.lines().filter { l -> l.isNotBlank() }.map { line ->
            val letter = line.substringBefore('⟦')
            buildAnnotatedString {
                if (letter.isNotEmpty()) withStyle(SpanStyle(color = ornament.gold, fontWeight = FontWeight.Bold)) { append("$letter  ") }
                Regex("⟦([^|⟧]+)\\|[^⟧]*⟧").findAll(line).forEachIndexed { i, m ->
                    if (i > 0) append(", ")
                    val p = vm.library.osisToPassage(m.groupValues[1])
                    if (p == null) append(m.groupValues[1])
                    else withLink(LinkAnnotation.Clickable("ref", link) { vm.openPassage(p) }) { append(p.shortLabel(lang)) }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("creed-item:${it.doc}:${it.seq}")) {
        val cite = HistoryRepository.citation(d, it)
        Text(cite, style = MaterialTheme.typography.labelLarge, color = ornament.rubric)
        if (it.question.isNotEmpty()) Text(it.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
            maxLines = if (compact) 6 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
        )
        if (!compact && proofs.isNotEmpty()) {
            Note(stringResource(R.string.proof_texts), Modifier.padding(top = 6.dp))
            proofs.forEach { p -> Text(p, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

// ---------------------------------------------------------------- study panel pages

@Composable
fun PassagePeoplePage(vm: AppViewModel, people: List<Person>, events: List<BibleEvent>, onDismiss: () -> Unit) {
    LazyColumn(Modifier.testTag("passage-people")) {
        if (people.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.tool_people)) }
            items(people, key = { "p" + it.id }) { p ->
                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.openPerson(p.id); onDismiss() }.testTag("passage-person:${p.id}").padding(vertical = 6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Note(personSubtitle(p))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (events.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.events)) }
            items(events, key = { "e" + it.id }) { e ->
                Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.openEvent(e.id); onDismiss() }.testTag("passage-event:${e.id}").padding(vertical = 6.dp)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Note(eventDates(e))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        item { Note(stringResource(R.string.theographic_credit), Modifier.padding(vertical = 12.dp)) }
    }
}

/** Articles and answers that cite the passage: own tradition first; others under "Go deeper", marked. */
@Composable
fun PassageConfessionsPage(vm: AppViewModel, items: List<CreedItem>, onDismiss: () -> Unit) {
    var docs by remember { mutableStateOf<List<CreedDoc>>(emptyList()) }
    var deeper by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { docs = vm.onIo { vm.chronicle.creedDocs } }
    val own = vm.ownFamilies()
    val byDoc = docs.associateBy { it.id }
    val mine = items.filter { byDoc[it.doc]?.family in own }
    val others = items.filter { byDoc[it.doc]?.family !in own }
    LazyColumn(Modifier.testTag("passage-confessions")) {
        items(mine, key = { "${it.doc}:${it.seq}" }) { c ->
            Column(Modifier.clickable { vm.openCreed(c.doc, c.seq); onDismiss() }) { CreedItemView(vm, byDoc[c.doc], c, compact = true) }
        }
        if (mine.isEmpty()) item { Note(stringResource(R.string.no_citations_own), Modifier.padding(vertical = 8.dp)) }
        if (others.isNotEmpty()) {
            item {
                TextButton(onClick = { deeper = !deeper }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text((if (deeper) "▾ " else "▸ ") + stringResource(R.string.go_deeper) + " (${others.size})")
                }
            }
            if (deeper) items(others, key = { "o${it.doc}:${it.seq}" }) { c ->
                Column(Modifier.clickable { vm.openCreed(c.doc, c.seq); onDismiss() }) {
                    Note(familyLabel(byDoc[c.doc]?.family ?: ""))
                    CreedItemView(vm, byDoc[c.doc], c, compact = true)
                }
            }
        }
    }
}

@Suppress("unused") private val keepAnnotated: AnnotatedString? = null
@Suppress("unused") @Composable private fun keepChip() { if (false) AssistChip(onClick = {}, label = {}) }
