package mg.baiboly.app.ui

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
                Text(p.shortLabel(), color = LocalOrnament.current.rubric)
            }
        }
    }
}

// ---------------------------------------------------------------- people

private val RELATION_ORDER = listOf("father", "mother", "partner", "sibling", "half_sibling", "child")

@Composable
private fun relationTitle(kind: String): String = when (kind) {
    "father" -> stringResource(R.string.rel_father)
    "mother" -> stringResource(R.string.rel_mother)
    "partner" -> stringResource(R.string.rel_partners)
    "sibling" -> stringResource(R.string.rel_siblings)
    "half_sibling" -> stringResource(R.string.rel_half_siblings)
    else -> stringResource(R.string.rel_children)
}

/** "Abraham" + what tells apart people of the same name: father, else first verse. */
@Composable
private fun personSubtitle(p: Person, father: Person? = null): String = listOfNotNull(
    father?.let { stringResource(R.string.child_of, it.name) },
    p.first?.shortLabel(),
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
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("person-view")) {
        item {
            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp))
            if (p.alsoCalled.isNotEmpty()) Note(stringResource(R.string.also_called, p.alsoCalled))
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
        // Father line, from the farthest ancestor down to the person (a column, not a count of generations)
        if (line.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.father_line)) }
            items(line.reversed(), key = { "line${it.id}" }) { a ->
                Text(
                    "↓ " + a.name, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onPerson(a.id) }.testTag("line:${a.id}").padding(vertical = 8.dp),
                )
            }
            item {
                Text("✠ " + p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ornament.rubric, modifier = Modifier.padding(vertical = 6.dp))
                Note(stringResource(R.string.genealogy_note))
            }
        }
        val father = relatives.firstOrNull { it.kind == "father" }?.person
        RELATION_ORDER.forEach { kind ->
            val group = relatives.filter { it.kind == kind }.map { it.person }.distinctBy { it.id }
            if (group.isNotEmpty()) {
                item(key = "rel-$kind") { SectionTitle(relationTitle(kind) + " (${group.size})") }
                items(group, key = { "$kind${it.id}" }) { r ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPerson(r.id) }.testTag("relative:${r.id}").padding(vertical = 6.dp),
                    ) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Note(personSubtitle(r, if (kind == "child" || kind == "sibling") null else null))
                    }
                }
            }
        }
        if (relatives.isEmpty() && line.isEmpty()) item { Note(stringResource(R.string.no_family), Modifier.padding(top = 8.dp)) }
        if (events.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.events)) }
            items(events, key = { "ev${it.id}" }) { e -> EventRow(vm, e) }
        }
        if (verses.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.verses_x, p.mentions))
                VerseChips(vm, verses, "person-verses")
            }
        }
        item {
            Note(stringResource(R.string.names_note), Modifier.padding(top = 16.dp))
            Note(stringResource(R.string.theographic_credit), Modifier.padding(vertical = 16.dp))
        }
        @Suppress("UNUSED_EXPRESSION") father
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
private fun EventRow(vm: AppViewModel, e: BibleEvent) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.openEvent(e.id) }.testTag("event:${e.id}").padding(vertical = 6.dp)) {
        Text(e.title, style = MaterialTheme.typography.bodyLarge)
        Note(listOfNotNull(eventDates(e), e.first?.shortLabel()).joinToString(" · "))
    }
}

@Composable
fun TimelineView(vm: AppViewModel, modifier: Modifier = Modifier) {
    var all by remember { mutableStateOf<List<BibleEvent>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(vm.eventRequest) }
    LaunchedEffect(Unit) { all = vm.onIo { vm.chronicle.timeline } }
    LaunchedEffect(vm.eventRequest) { vm.eventRequest?.let { open = it; vm.eventRequest = null } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    val id = open
    if (id != null) {
        all.firstOrNull { it.id == id }?.let { EventView(vm, it, modifier) } ?: Text(stringResource(R.string.loading), modifier.padding(20.dp))
        return
    }
    val q = mg.baiboly.core.text.TextNorm.norm(query.trim())
    val shown = remember(all, q) { if (q.isEmpty()) all else all.filter { mg.baiboly.core.text.TextNorm.norm(it.title).contains(q) } }
    val ornament = LocalOrnament.current
    Column(modifier) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.timeline_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("timeline-search"),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp).testTag("timeline")) {
            item { Note(stringResource(R.string.timeline_note), Modifier.padding(vertical = 6.dp)) }
            var lastBook = -1
            shown.forEach { e ->
                val testament = if ((e.first?.book ?: 1) >= 40) 2 else 1
                if (testament != lastBook) {
                    lastBook = testament
                    item(key = "t$testament") {
                        Text(
                            stringResource(if (testament == 1) R.string.old_testament else R.string.new_testament),
                            style = MaterialTheme.typography.titleMedium, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                item(key = e.id) { EventRow(vm, e); HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
            }
            item { Note(stringResource(R.string.theographic_credit), Modifier.padding(vertical = 16.dp)) }
        }
    }
}

@Composable
private fun EventView(vm: AppViewModel, e: BibleEvent, modifier: Modifier) {
    var people by remember(e.id) { mutableStateOf<List<Person>>(emptyList()) }
    var verses by remember(e.id) { mutableStateOf<List<Passage>>(emptyList()) }
    LaunchedEffect(e.id) { vm.onIo { people = vm.chronicle.eventPeople(e.id); verses = vm.chronicle.eventVerses(e.id) } }
    val ornament = LocalOrnament.current
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("event-view")) {
        item {
            Text(e.title, style = MaterialTheme.typography.headlineSmall, color = ornament.rubric, modifier = Modifier.padding(top = 12.dp))
            Fleuron()
            Text(
                (if (e.approximate) stringResource(R.string.approx) + " " else "") + yearLabel(e.year) + " " + stringResource(R.string.after_jones),
                style = MaterialTheme.typography.bodyLarge,
            )
            e.ussher?.let { Text(stringResource(R.string.ussher_x, yearLabel(it)) + " " + stringResource(R.string.after_tsk), style = MaterialTheme.typography.bodyMedium) }
            durationLabel(e.duration)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (e.notes.isNotEmpty()) Text(e.notes, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 8.dp))
            Note(stringResource(R.string.dates_reconstructed), Modifier.padding(top = 8.dp))
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
        item { Note(stringResource(R.string.theographic_credit), Modifier.padding(vertical = 16.dp)) }
    }
}

// ---------------------------------------------------------------- confessions and councils

@Composable
internal fun familyLabel(family: String): String =
    if (family == "reformed") stringResource(R.string.tradition_reformed) else stringResource(R.string.family_creeds)

@Composable
fun ConfessionsView(vm: AppViewModel, modifier: Modifier = Modifier) {
    var docs by remember { mutableStateOf<List<CreedDoc>>(emptyList()) }
    var councils by remember { mutableStateOf<List<mg.baiboly.data.Council>>(emptyList()) }
    var open by remember { mutableStateOf(vm.creedRequest) }
    var deeper by remember { mutableStateOf(false) }
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
        listOf("creed", "reformed").filter { it in own }.forEach { fam ->
            item(key = "fam-$fam") { SectionTitle(familyLabel(fam)) }
            items(docs.filter { it.family == fam }, key = { it.id }) { d -> DocRow(d, marked = false) { open = d.id to -1 } }
        }
        val councilsShown = councils.filter { c -> c.docs.any { id -> docs.firstOrNull { it.id == id }?.family in own } }
        if (councilsShown.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.councils)) }
            items(councilsShown, key = { "c-" + it.id }) { c -> CouncilRow(c, docs) { open = it to -1 } }
            item { Note(stringResource(R.string.councils_note), Modifier.padding(top = 4.dp)) }
        }
        // Go deeper: documents of the other traditions, each marked with its tradition
        item {
            TextButton(onClick = { deeper = !deeper }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp).testTag("go-deeper")) {
                Text((if (deeper) "▾ " else "▸ ") + stringResource(R.string.go_deeper), style = MaterialTheme.typography.titleSmall)
            }
        }
        if (deeper) {
            val others = docs.filter { it.family !in own }
            items(others, key = { "o-" + it.id }) { d -> DocRow(d, marked = true) { open = d.id to -1 } }
            items(councils.filter { it !in councilsShown }, key = { "oc-" + it.id }) { c -> CouncilRow(c, docs, marked = true) { open = it to -1 } }
            item { Note(stringResource(R.string.no_other_traditions), Modifier.padding(vertical = 6.dp)) }
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
        val fam = docs.firstOrNull { it.id == c.docs.first() }?.family ?: "creed"
        Text((if (marked) familyLabel(fam) + ": " else "") + c.name, style = MaterialTheme.typography.bodyLarge)
        Note(listOf(c.place, c.year).filter { it.isNotEmpty() }.joinToString(" · "))
        Row {
            Text(stringResource(R.string.council_issued) + " ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            c.docs.forEach { id ->
                val d = docs.firstOrNull { it.id == id } ?: return@forEach
                TextButton(onClick = { onDoc(id) }, modifier = Modifier.heightIn(min = 40.dp)) { Text(d.short, color = LocalOrnament.current.rubric) }
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
    val proofs = remember(it.proofs, ornament) {
        it.proofs.lines().filter { l -> l.isNotBlank() }.map { line ->
            val letter = line.substringBefore('⟦')
            buildAnnotatedString {
                if (letter.isNotEmpty()) withStyle(SpanStyle(color = ornament.gold, fontWeight = FontWeight.Bold)) { append("$letter  ") }
                Regex("⟦([^|⟧]+)\\|[^⟧]*⟧").findAll(line).forEachIndexed { i, m ->
                    if (i > 0) append(", ")
                    val p = vm.library.osisToPassage(m.groupValues[1])
                    if (p == null) append(m.groupValues[1])
                    else withLink(LinkAnnotation.Clickable("ref", link) { vm.openPassage(p) }) { append(p.shortLabel()) }
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
