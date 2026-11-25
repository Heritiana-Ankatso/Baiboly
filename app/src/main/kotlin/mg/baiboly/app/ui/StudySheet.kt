package mg.baiboly.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.ExtendedEntry
import mg.baiboly.data.LexiconEntry
import mg.baiboly.data.OriginalVerse
import mg.baiboly.data.OriginalWord
import mg.baiboly.data.VerseRef
import mg.baiboly.data.VerseStudy
import androidx.compose.material.icons.outlined.Map

private sealed interface StudyPage {
    data object Words : StudyPage
    /** [word] is set when the entry was opened from a word of the Hebrew or Greek text (it adds the parsing). */
    data class Entry(val strongs: String, val word: OriginalWord? = null) : StudyPage
    data class Found(val strongs: String, val word: OriginalWord? = null) : StudyPage
}

/**
 * Brief section 9, α mode: the KJV words of a verse that carry a Strong's
 * number, their lexicon entries, and Found N verses. Verse numbers are
 * Malagasy 1865 numbers, mapped to the KJV numbering where they differ.
 */
/** Original words of a verse (KJV Strong's tagging), lexicon entries and Found N verses. */
@Composable
fun StudyContent(vm: AppViewModel, book: Int, chapter: Int, verse: Int, onDismiss: () -> Unit) {
    var page by remember(book, chapter, verse) { mutableStateOf<StudyPage>(StudyPage.Words) }
    Column(Modifier.fillMaxWidth()) {
        if (page != StudyPage.Words) {
            IconButton(onClick = {
                page = when (val p = page) {
                    is StudyPage.Found -> StudyPage.Entry(p.strongs, p.word)
                    else -> StudyPage.Words
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        }
        when (val p = page) {
            StudyPage.Words -> WordsPage(vm, book, chapter, verse) { strongs, word -> page = StudyPage.Entry(strongs, word) }
            is StudyPage.Entry -> EntryPage(vm, p.strongs, p.word) { page = StudyPage.Found(p.strongs, p.word) }
            is StudyPage.Found -> FoundPage(vm, p.strongs, p.word != null, onOpen = { r -> vm.openInMalagasy(r); onDismiss() })
        }
    }
}

/** Kept for callers of the one-verse panel: the passage panel on that verse. */
@Composable
fun VerseToolsSheet(vm: AppViewModel, book: Int, chapter: Int, verse: Int, startOnCrossRefs: Boolean = false, onDismiss: () -> Unit) =
    PassageToolsSheet(vm, Passage(book, chapter, verse, verse), if (startOnCrossRefs) PassageTab.References else PassageTab.Original, onDismiss)

enum class PassageTab { Translations, Original, Commentary, References, People, Places, Units, Dictionary, Confessions, MyNotes }

/**
 * Everything about the selected verses, in one panel (any mode: Study on the
 * selection bar, or a tap on a verse number in Study mode):
 * the passage in the other Bibles; the original words (א Hebrew/Aramaic,
 * α Greek) with their lexicon entries; cross-references; places named, to
 * open on the map; units named, to open in the converter; dictionary entries
 * named by its words. Tabs with nothing for this passage are left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassageToolsSheet(vm: AppViewModel, passage: Passage, start: PassageTab = PassageTab.Translations, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    var mg by remember(passage) { mutableStateOf<Passage?>(null) }
    var places by remember(passage) { mutableStateOf<List<mg.baiboly.data.Place>?>(null) }
    var units by remember(passage) { mutableStateOf<List<BibleUnit>?>(null) }
    var dict by remember(passage) { mutableStateOf<List<AppViewModel.DictMatch>?>(null) }
    var people by remember(passage) { mutableStateOf<List<mg.baiboly.data.Person>?>(null) }
    var events by remember(passage) { mutableStateOf<List<mg.baiboly.data.BibleEvent>?>(null) }
    var creeds by remember(passage) { mutableStateOf<List<mg.baiboly.data.CreedItem>?>(null) }
    var myNotes by remember(passage) { mutableStateOf<List<mg.baiboly.data.NoteDocument>?>(null) }
    LaunchedEffect(passage) { myNotes = vm.documentsCiting(vm.toMalagasy(passage)) }
    LaunchedEffect(passage) {
        people = vm.peopleInPassage(passage)
        events = vm.eventsInPassage(passage)
        creeds = vm.creedsCiting(passage)
    }
    LaunchedEffect(passage) {
        val m = vm.toMalagasy(passage)
        mg = m
        places = vm.placesInPassage(passage)
        units = BibleUnits.namedIn(vm.passageTexts(passage), passage.book)
        dict = vm.passageDictionary(passage)
    }
    val tabs = PassageTab.entries.filter {
        when (it) {
            PassageTab.Places -> !places.isNullOrEmpty()
            PassageTab.Units -> !units.isNullOrEmpty()
            PassageTab.Dictionary -> !dict.isNullOrEmpty()
            PassageTab.People -> !people.isNullOrEmpty() || !events.isNullOrEmpty()
            PassageTab.Confessions -> !creeds.isNullOrEmpty()
            PassageTab.Commentary -> vm.commentaries.available.isNotEmpty()
            PassageTab.MyNotes -> !myNotes.isNullOrEmpty()
            else -> true
        }
    }
    var tab by remember(passage) { mutableStateOf(start) }
    val shown = if (tab in tabs) tab else PassageTab.Translations
    LocalizedSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).navigationBarsPadding().testTag("passage-tools")) {
            Text(
                passage.longLabel(lang),
                style = MaterialTheme.typography.titleLarge,
                color = LocalOrnament.current.rubric,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            androidx.compose.material3.ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(shown).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                tabs.forEach { t ->
                    androidx.compose.material3.Tab(
                        selected = t == shown, onClick = { tab = t },
                        text = {
                            Text(
                                when (t) {
                                    PassageTab.Translations -> stringResource(R.string.tab_translations)
                                    PassageTab.Original -> studySymbol(passage.book) + " " + stringResource(R.string.original_words)
                                    PassageTab.References -> stringResource(R.string.cross_references)
                                    PassageTab.Places -> stringResource(R.string.tab_places) + " (${places?.size ?: 0})"
                                    PassageTab.Units -> stringResource(R.string.units) + " (${units?.size ?: 0})"
                                    PassageTab.Dictionary -> stringResource(R.string.dictionary)
                                    PassageTab.People -> stringResource(R.string.tab_people) + " (${(people?.size ?: 0) + (events?.size ?: 0)})"
                                    PassageTab.Confessions -> stringResource(R.string.tool_confessions) + " (${creeds?.size ?: 0})"
                                    PassageTab.Commentary -> stringResource(R.string.commentary)
                                    PassageTab.MyNotes -> stringResource(R.string.my_notes) + " (${myNotes?.size ?: 0})"
                                },
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.testTag(
                            when (t) {
                                PassageTab.Original -> "tab-original"; PassageTab.References -> "tab-xrefs"
                                else -> "tab-" + t.name.lowercase()
                            },
                        ),
                    )
                }
            }
            Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                val m = mg
                when (shown) {
                    PassageTab.Translations -> TranslationsPage(vm, passage, onDismiss)
                    PassageTab.Original -> if (m == null) Text(stringResource(R.string.loading)) else OriginalPage(vm, m, onDismiss)
                    PassageTab.References -> CrossRefContent(vm, passage.verseStart.coerceAtLeast(1), onDismiss)
                    PassageTab.Places -> PlacesPage(vm, places.orEmpty(), onDismiss)
                    PassageTab.Units -> UnitsPage(vm, units.orEmpty(), lang, onDismiss)
                    PassageTab.Dictionary -> DictionaryPage(vm, dict.orEmpty())
                    PassageTab.People -> PassagePeoplePage(vm, people.orEmpty(), events.orEmpty(), onDismiss)
                    PassageTab.Confessions -> PassageConfessionsPage(vm, creeds.orEmpty(), onDismiss)
                    PassageTab.Commentary -> CommentaryPage(vm, passage, onDismiss)
                    PassageTab.MyNotes -> androidx.compose.foundation.lazy.LazyColumn(Modifier.testTag("my-notes")) {
                        items(myNotes.orEmpty().size) { i ->
                            val d = myNotes.orEmpty()[i]
                            Column(Modifier.fillMaxWidth().clickable { onDismiss(); vm.openDocument(d) }.padding(vertical = 10.dp)) {
                                Text(d.title, style = MaterialTheme.typography.bodyLarge)
                                Text(d.passages, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The passage in each Bible offered; a tap reads it there. */
@Composable
private fun TranslationsPage(vm: AppViewModel, passage: Passage, onDismiss: () -> Unit) {
    var list by remember(passage) { mutableStateOf<List<AppViewModel.Compared>?>(null) }
    LaunchedEffect(passage, vm.translationId) { list = vm.compareTranslations(passage) }
    val items = list ?: return Text(stringResource(R.string.loading))
    val ornament = LocalOrnament.current
    LazyColumn(Modifier.testTag("compare-list")) {
        items(items, key = { it.translation.id }) { c ->
            val current = c.translation.id == vm.translationId
            Column(
                Modifier.fillMaxWidth().clickable { vm.readIn(c); onDismiss() }
                    .testTag("compare:${c.translation.id}").padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.translation.shortName, style = MaterialTheme.typography.labelLarge, color = if (current) ornament.rubric else ornament.gold,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "  " + c.translation.name + (if (c.passage != passage) " · " + c.passage.numbers() else "") + (if (c.numberingMayDiffer) " ⚠" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(c.text, fontFamily = TextSerif, fontSize = 16.sp, lineHeight = 22.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Text(stringResource(R.string.compare_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

/** Original words verse by verse: a chip per verse when several are selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginalPage(vm: AppViewModel, mg: Passage, onDismiss: () -> Unit) {
    val verses = (mg.verseStart.coerceAtLeast(1)..mg.verseEnd.coerceAtLeast(mg.verseStart.coerceAtLeast(1))).toList()
    var verse by remember(mg) { mutableStateOf(verses.first()) }
    Column {
        if (verses.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(verses) { v ->
                    androidx.compose.material3.FilterChip(selected = v == verse, onClick = { verse = v }, label = { Text("${mg.chapter}:$v") })
                }
            }
        }
        Box(Modifier.weight(1f)) { StudyContent(vm, mg.book, mg.chapter, verse, onDismiss) }
    }
}

@Composable
private fun PlacesPage(vm: AppViewModel, places: List<mg.baiboly.data.Place>, onDismiss: () -> Unit) {
    LazyColumn(Modifier.testTag("passage-places")) {
        items(places, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.showOnMap(p.id); onDismiss() }
                    .testTag("passage-place:${p.id}").padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Map, null, tint = LocalOrnament.current.gold, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(listOf(p.type, stringResource(R.string.place_mentions, p.mentions)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.show_on_map), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun UnitsPage(vm: AppViewModel, units: List<BibleUnit>, lang: String, onDismiss: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.testTag("passage-units")) {
        items(units, key = { it.id }) { u ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { vm.openUnit(u.id); onDismiss() }
                    .testTag("passage-unit:${u.id}").padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(u.name(lang), style = MaterialTheme.typography.bodyLarge)
                    Text(listOfNotNull(u.original.takeIf { it.isNotEmpty() }, unitSummary(u, locale)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.open_converter), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item {
            Text(stringResource(R.string.units_approx), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
        }
    }
}

/** Dictionary entries named by words of the passage; an entry opens here, its links too. */
@Composable
private fun DictionaryPage(vm: AppViewModel, matches: List<AppViewModel.DictMatch>) {
    var open by remember { mutableStateOf<Pair<mg.baiboly.data.DictSource, String>?>(null) }
    val o = open
    if (o != null) {
        Column {
            IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Box(Modifier.weight(1f)) { DictEntryView(vm, o.first, o.second, onLink = { open = o.first to it }) }
        }
        return
    }
    LazyColumn(Modifier.testTag("passage-dictionary")) {
        matches.forEach { m ->
            item(key = "h:" + m.source.id) {
                Text(dictLabel(m.source), style = MaterialTheme.typography.titleSmall, color = LocalOrnament.current.gold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            item(key = "k:" + m.source.id) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.keys.forEach { k ->
                        Surface(
                            shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = m.source to k }
                                .heightIn(min = 40.dp).testTag("passage-entry:${m.source.id}:$k"),
                        ) {
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Text(k.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsPage(vm: AppViewModel, book: Int, chapter: Int, verse: Int, onWord: (String, OriginalWord?) -> Unit) {
    var data by remember { mutableStateOf<VerseStudy?>(null) }
    var original by remember { mutableStateOf<OriginalVerse?>(null) }
    LaunchedEffect(book, chapter, verse) {
        data = vm.studyVerse(book, chapter, verse)
        original = runCatching { vm.studyOriginal(book, chapter, verse) }.getOrNull()
    }
    val d = data ?: return Text(stringResource(R.string.loading))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (d.numberingUncertain) {
            Text(
                stringResource(R.string.study_numbering_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        original?.takeIf { it.words.isNotEmpty() }?.let { o ->
            Text(
                stringResource(if (o.hebrew) R.string.original_text_hebrew else R.string.original_text_greek),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.original_text_note, "${o.kjv.chapter}:${o.kjv.verse}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Hebrew is read from right to left: the first word is on the right
            CompositionLocalProvider(LocalLayoutDirection provides if (o.hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    o.words.forEach { w ->
                        val tint = if (w.onlyInReceivedText) 0.6f else 1f
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = w.strongs != null) { w.strongs?.let { onWord(it, w) } }
                                .heightIn(min = 48.dp).testTag("orig:${w.strongs}"),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(w.word, fontFamily = TextSerif, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = tint))
                                w.translit?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                w.gloss?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                w.strongs?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
            if (!o.hebrew && o.words.any { it.onlyInReceivedText }) {
                Text(
                    stringResource(R.string.original_text_tr_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }
        Text(
            stringResource(R.string.study_kjv_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.study_kjv_note, "${d.kjv.chapter}:${d.kjv.verse}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (d.words.isEmpty()) Text(stringResource(R.string.study_no_words))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.words.forEach { w ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onWord(w.strongs, null) }.heightIn(min = 48.dp).testTag("word:${w.strongs}"),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(w.word, fontWeight = FontWeight.SemiBold)
                            Text(
                                " " + w.strongs,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val head = listOfNotNull(w.lemma, w.translit?.let { if (w.lemma != null) "($it)" else it }).joinToString(" ")
                        if (head.isNotEmpty()) Text(head, fontFamily = TextSerif, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryPage(vm: AppViewModel, strongs: String, word: OriginalWord?, onFound: () -> Unit) {
    var data by remember { mutableStateOf<Pair<LexiconEntry?, Int>?>(null) }
    var fuller by remember { mutableStateOf<ExtendedEntry?>(null) }
    LaunchedEffect(strongs, word) {
        data = vm.lexicon(strongs, word != null)
        fuller = runCatching { vm.extendedEntry(strongs, word?.dstrong) }.getOrNull()
    }
    val (e, count) = data ?: return Text(stringResource(R.string.loading))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        if (word != null) {
            Text(word.word, fontFamily = TextSerif, fontSize = 34.sp)
            Text(
                listOfNotNull(word.translit?.takeIf { it.isNotBlank() }, word.gloss?.takeIf { it.isNotBlank() }?.let { "“$it”" }).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            word.parsing?.let { Field(stringResource(R.string.parsing), it) }
            HorizontalDivider(Modifier.padding(top = 12.dp))
        }
        if (e == null) {
            Text(stringResource(R.string.no_entry, strongs))
        } else {
            Text(e.lemma ?: e.translit ?: strongs, fontFamily = TextSerif, fontSize = 30.sp)
            Text(
                listOfNotNull(e.translit.takeIf { e.lemma != null }, e.pron?.let { "[$it]" }, strongs).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            e.partOfSpeech?.let { Field(stringResource(R.string.part_of_speech), it) }
            e.definition?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.definition), it) }
            e.kjvUsage?.takeIf { it.isNotBlank() }?.let { Field(stringResource(R.string.kjv_usage), it) }
        }
        fuller?.let { f ->
            val text = listOfNotNull(f.gloss?.takeIf { it.isNotBlank() }, f.definition?.takeIf { it.isNotBlank() }).joinToString("\n\n")
            if (text.isNotBlank()) {
                Field(stringResource(if (strongs.startsWith("H")) R.string.fuller_definition_hebrew else R.string.fuller_definition_greek), text)
                Text(
                    stringResource(R.string.fuller_definition_credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Button(onClick = onFound, enabled = count > 0, modifier = Modifier.padding(vertical = 12.dp).heightIn(min = 48.dp).testTag("found")) {
            Text(stringResource(R.string.found_n_verses, count))
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun FoundPage(vm: AppViewModel, strongs: String, inOriginal: Boolean, onOpen: (VerseRef) -> Unit) {
    var data by remember { mutableStateOf<List<Pair<VerseRef, String>>?>(null) }
    LaunchedEffect(strongs, inOriginal) { data = vm.versesWith(strongs, inOriginal) }
    val list = data ?: return Text(stringResource(R.string.loading))
    Text(
        stringResource(R.string.found_n_verses, list.size) + " · " + strongs,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    LazyColumn {
        items(list, key = { "${it.first.book}-${it.first.chapter}-${it.first.verse}" }) { (r, text) ->
            val p = Passage(r.book, r.chapter, r.verse, r.verse)
            val label = p.label()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpen(r) }.testTag("found:${r.book}-${r.chapter}-${r.verse}").padding(vertical = 8.dp)) {
                    Text(p.label(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = TextSerif, fontStyle = FontStyle.Normal)
                }
                IconButton(onClick = { vm.addToList(listOf(p)); vm.message = vm.str(R.string.added_x, label) }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_to_list))
                }
            }
            HorizontalDivider()
        }
    }
}
