package mg.baiboly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.Passage
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.data.VerseText

/**
 * Brief section 7: Browse drawer. Book grid -> chapter grid -> verse list with
 * text; tapped verses are selected, adjacent ones merge into a range. Partially
 * expanded so the list strip stays visible above it. After Add to list the
 * drawer stays open (assumption to confirm) and Done closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LocalizedSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        BrowseContent(vm, Modifier.fillMaxWidth().fillMaxHeight(0.72f).navigationBarsPadding(), onDone = onDismiss)
    }
}

/**
 * Book grid -> chapter grid -> verse list with text. Tapped verses are
 * selected, adjacent ones merge into a range; Add to list keeps the view
 * open for the next passage, Open without adding goes to the reader.
 */
@Composable
fun BrowseContent(vm: AppViewModel, modifier: Modifier, onDone: (() -> Unit)?) {
    var book by remember { mutableStateOf<Int?>(null) }
    var chapter by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val ctx = LocalContext.current
    val lang = vm.translations.firstOrNull { it.id == vm.translationId }?.lang ?: "mg"
    val onDismiss = { onDone?.invoke() ?: Unit; vm.screen = mg.baiboly.app.Screen.Main }
    run {
        Column(modifier) {
            // breadcrumb
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Crumb(stringResource(R.string.books), book != null) { book = null; chapter = null; selected = emptySet() }
                book?.let { b ->
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Crumb(Canon.book(b).name(lang), chapter != null) { chapter = null; selected = emptySet() }
                }
                chapter?.let { c ->
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Crumb("$c", false) {}
                }
                Box(Modifier.weight(1f))
                if (onDone != null) TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
            }
            HorizontalDivider()
            val b = book
            val c = chapter
            when {
                b == null -> BookGrid(vm.availableBooks) { book = it }
                c == null -> ChapterGrid(vm.chapterCount(b)) { chapter = it; selected = emptySet() }
                else -> {
                    var verses by remember(b, c, vm.translationId) { mutableStateOf<List<VerseText>>(emptyList()) }
                    LaunchedEffect(b, c, vm.translationId) {
                        verses = withContext(Dispatchers.IO) { vm.bible.chapter(vm.translationId, b, c).verses }
                    }
                    Text(
                        stringResource(R.string.select_verses_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        items(verses, key = { it.verse }) { v ->
                            val on = v.verse in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable { selected = if (on) selected - v.verse else selected + v.verse }
                                    .testTag("browse-verse:${v.verse}")
                                    .padding(8.dp),
                            ) {
                                Text(
                                    "${v.verse}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(36.dp),
                                )
                                Text(v.text, fontFamily = TextSerif)
                            }
                        }
                    }
                    val passages = if (selected.isEmpty()) listOf(Passage(b, c))
                    else ReferenceParser.mergeRanges(selected.sorted().map { it..it }).map { Passage(b, c, it.first, it.last) }
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.whole_chapter) + " · " + Passage.describe(passages, lang)
                        else Passage.describe(passages, lang),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // The service list uses Malagasy numbering: a chapter that exists only in
                        // another text's numbering (e.g. KJV Malachi 4) can be opened, not added.
                        Button(enabled = c <= Canon.chapterCount(b), onClick = {
                            vm.addToList(passages)
                            vm.message = ctx.getString(R.string.added_x, Passage.describe(passages, lang))
                            selected = emptySet()
                        }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.add_to_list)) }
                        FilledTonalButton(onClick = {
                            vm.openPassage(passages.first())
                            onDismiss()
                        }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.open_without_adding)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Crumb(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, fontWeight = if (enabled) FontWeight.Normal else FontWeight.Bold)
    }
}

@Composable
fun BookGrid(available: List<Int>, onBook: (Int) -> Unit) {
    val books = available.map { Canon.book(it) }
    // abbreviations in the language of the Bible being read: "Jao" (Malagasy), "Jn" (French), "John" (English)
    val lang = refLang()
    LazyVerticalGrid(
        modifier = Modifier.testTag("browse-books"),
        columns = GridCells.Adaptive(76.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.old_testament)) }
        items(books.filter { it.isOldTestament }, key = { it.id }) { b ->
            Key(b.abbr(lang) + if (b.isDeuterocanonical) " ✦" else "", Modifier.testTag("browse-book:${b.id}"), tonal = false) { onBook(b.id) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.new_testament)) }
        items(books.filter { !it.isOldTestament }, key = { it.id }) { b -> Key(b.abbr(lang), Modifier.testTag("browse-book:${b.id}"), tonal = true) { onBook(b.id) } }
    }
}

@Composable
fun ChapterGrid(chapters: Int, onChapter: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items((1..chapters).toList()) { c -> Key("$c", Modifier.testTag("browse-chapter:$c")) { onChapter(c) } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}
