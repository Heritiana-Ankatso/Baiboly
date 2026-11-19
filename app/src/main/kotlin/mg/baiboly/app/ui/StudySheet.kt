package mg.baiboly.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import mg.baiboly.data.LexiconEntry
import mg.baiboly.data.VerseRef
import mg.baiboly.data.VerseStudy

private sealed interface StudyPage {
    data object Words : StudyPage
    data class Entry(val strongs: String) : StudyPage
    data class Found(val strongs: String) : StudyPage
}

/**
 * Brief section 9, α mode: the KJV words of a verse that carry a Strong's
 * number, their lexicon entries, and Found N verses. Verse numbers are
 * Malagasy 1865 numbers, mapped to the KJV numbering where they differ.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySheet(vm: AppViewModel, book: Int, chapter: Int, verse: Int, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf<StudyPage>(StudyPage.Words) }
    val ref = Passage(book, chapter, verse, verse)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != StudyPage.Words) {
                    IconButton(onClick = {
                        page = when (val p = page) {
                            is StudyPage.Found -> StudyPage.Entry(p.strongs)
                            else -> StudyPage.Words
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
                Text(
                    studySymbol(book) + "  " + stringResource(R.string.study_title, ref.longLabel()),
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalOrnament.current.rubric,
                )
            }
            when (val p = page) {
                StudyPage.Words -> WordsPage(vm, book, chapter, verse) { page = StudyPage.Entry(it) }
                is StudyPage.Entry -> EntryPage(vm, p.strongs) { page = StudyPage.Found(p.strongs) }
                is StudyPage.Found -> FoundPage(vm, p.strongs, onOpen = { r -> vm.openInMalagasy(r); onDismiss() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsPage(vm: AppViewModel, book: Int, chapter: Int, verse: Int, onWord: (String) -> Unit) {
    var data by remember { mutableStateOf<VerseStudy?>(null) }
    LaunchedEffect(book, chapter, verse) { data = vm.studyVerse(book, chapter, verse) }
    val d = data ?: return Text(stringResource(R.string.loading))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.study_kjv_note, "${d.kjv.chapter}:${d.kjv.verse}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (d.numberingUncertain) {
            Text(
                stringResource(R.string.study_numbering_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (d.words.isEmpty()) Text(stringResource(R.string.study_no_words))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.words.forEach { w ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onWord(w.strongs) }.heightIn(min = 48.dp).testTag("word:${w.strongs}"),
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
private fun EntryPage(vm: AppViewModel, strongs: String, onFound: () -> Unit) {
    var data by remember { mutableStateOf<Pair<LexiconEntry?, Int>?>(null) }
    LaunchedEffect(strongs) { data = vm.lexicon(strongs) }
    val (e, count) = data ?: return Text(stringResource(R.string.loading))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
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
private fun FoundPage(vm: AppViewModel, strongs: String, onOpen: (VerseRef) -> Unit) {
    var data by remember { mutableStateOf<List<Pair<VerseRef, String>>?>(null) }
    LaunchedEffect(strongs) { data = vm.versesWith(strongs) }
    val list = data ?: return Text(stringResource(R.string.loading))
    Text(
        stringResource(R.string.found_n_verses, list.size) + " · " + strongs,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    LazyColumn {
        items(list, key = { "${it.first.book}-${it.first.chapter}-${it.first.verse}" }) { (r, text) ->
            val p = Passage(r.book, r.chapter, r.verse, r.verse)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpen(r) }.testTag("found:${r.book}-${r.chapter}-${r.verse}").padding(vertical = 8.dp)) {
                    Text(p.shortLabel(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = TextSerif, fontStyle = FontStyle.Normal)
                }
                IconButton(onClick = { vm.addToList(listOf(p)); vm.message = vm.str(R.string.added_x, p.shortLabel()) }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_to_list))
                }
            }
            HorizontalDivider()
        }
    }
}
