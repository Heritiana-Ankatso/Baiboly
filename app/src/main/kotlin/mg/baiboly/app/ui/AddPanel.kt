package mg.baiboly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.bible.Canon
import mg.baiboly.core.ref.ParseError
import mg.baiboly.core.ref.ReferenceParser
import mg.baiboly.core.suggest.Suggester

private val suggester = Suggester()

/** Localised inline message for a parse error, or null when there is nothing to report yet. */
@Composable
fun parseErrorText(e: ParseError?): String? {
    val ctx = LocalContext.current
    return when (e) {
        null, ParseError.Empty, ParseError.Incomplete -> null
        is ParseError.UnknownBook -> ctx.getString(R.string.err_unknown_book, e.text)
        is ParseError.AmbiguousBook -> ctx.getString(R.string.err_ambiguous_book, e.text, e.candidates.joinToString(", ") { Canon.book(it).nameMg })
        ParseError.MissingBook -> ctx.getString(R.string.err_missing_book)
        is ParseError.ChapterOutOfRange -> ctx.getString(R.string.err_chapter_range, Canon.book(e.book).nameMg, e.chapter, e.max)
        is ParseError.VerseOutOfRange -> ctx.getString(R.string.err_verse_range, Canon.book(e.book).nameMg, e.chapter, e.verse, e.max)
        is ParseError.ReversedRange -> ctx.getString(R.string.err_reversed, e.from, e.to)
        is ParseError.Syntax -> ctx.getString(R.string.err_syntax, e.near)
    }
}

/**
 * Brief section 6: two input modes (silent custom keypad, system keyboard),
 * a live preview and staged suggestions with recent passages first.
 * The panel stays open after Add so several verses can be captured in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPanel(vm: AppViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }
    var keypad by remember { mutableStateOf(true) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    val result = remember(input) { ReferenceParser.parse(input) }
    val suggestions = remember(input, vm.recent, vm.bookUsage) { suggester.suggest(input, vm.recent, vm.bookUsage) }
    val catholic = vm.tradition == mg.baiboly.app.Tradition.Catholic
    // Protestant: the deuterocanonical books stay out of the keypad.
    val books = remember(vm.recent, vm.bookUsage, catholic) {
        suggester.rankBooks(vm.recent, vm.bookUsage).filter { catholic || !it.isDeuterocanonical }
    }
    val error = parseErrorText(result.error)
    val bookLang = refLang()

    fun submit() {
        if (result.isValid) {
            vm.addToList(result.passages)
            lastAdded = result.preview(bookLang)
            input = ""
        }
    }

    fun tapBook(label: String) {
        input = when (val st = ReferenceParser.analyze(input)) {
            is ReferenceParser.Stage.Book -> st.head + label + " "
            else -> if (input.isBlank()) "$label " else input.trimEnd().trimEnd(';') + "; " + label + " "
        }
    }

    LocalizedSheet(onDismissRequest = onDismiss, sheetState = sheet, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 8.dp)) {
            // header
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.add_verses), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(selected = keypad, onClick = { keypad = true }, label = { Text(stringResource(R.string.keypad)) }, modifier = Modifier.heightIn(min = 48.dp))
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = !keypad, onClick = { keypad = false }, label = { Text(stringResource(R.string.keyboard)) }, modifier = Modifier.heightIn(min = 48.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            // entry
            if (keypad) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 56.dp),
                ) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (input.isEmpty()) Text(stringResource(R.string.entry_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Text(input + "▏", style = MaterialTheme.typography.titleLarge)
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                var tfv by remember { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
                if (tfv.text != input) tfv = TextFieldValue(input, TextRange(input.length))
                OutlinedTextField(
                    value = tfv,
                    onValueChange = { tfv = it; input = it.text },
                    placeholder = { Text(stringResource(R.string.entry_hint)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            // live preview / error / confirmation
            val line = when {
                error != null -> error
                result.passages.isNotEmpty() -> stringResource(R.string.preview_x, result.preview(bookLang))
                lastAdded != null -> stringResource(R.string.added_x, lastAdded!!)
                else -> ""
            }
            Text(
                line,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp).heightIn(min = 20.dp),
            )
            // suggestions
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("suggestions"),
            ) {
                items(suggestions, key = { it.newInput }) { s ->
                    SuggestionChip(
                        onClick = { input = s.newInput },
                        label = { Text(s.label, fontWeight = if (s.isRecent) FontWeight.Bold else FontWeight.Normal) },
                        colors = if (s.isRecent) SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else SuggestionChipDefaults.suggestionChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("suggestion:${s.newInput}"),
                    )
                }
            }
            if (keypad) {
                // book grid: recent and most-used books first
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(164.dp).testTag("book-grid"),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(books, key = { it.id }) { b ->
                        // shown in the Bible's language; typed as a name the parser cannot mistake ("Ésaïe", not "Es")
                        Key(b.abbr(bookLang), Modifier.width(72.dp).testTag("book:${b.shortLabel}"), tonal = true) {
                            tapBook(if (bookLang == "mg") b.shortLabel else b.name(bookLang))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                NumberPad(
                    onKey = { k ->
                        input = when (k) {
                            ":" -> input.trimEnd() + ": "
                            "," -> input.trimEnd() + ", "
                            ";" -> input.trimEnd() + "; "
                            "-" -> input.trimEnd() + "-"
                            else -> input + k
                        }
                    },
                    onBackspace = { input = input.trimEnd().dropLast(1) },
                    onClear = { input = "" },
                    onAdd = { submit() },
                    canAdd = result.isValid,
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                    Key(stringResource(R.string.add), Modifier.width(140.dp).testTag("key:ADD"), primary = true, enabled = result.isValid) { submit() }
                }
            }
        }
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onAdd: () -> Unit, canAdd: Boolean) {
    val rows = listOf(
        listOf("1", "2", "3", ":"),
        listOf("4", "5", "6", "-"),
        listOf("7", "8", "9", ","),
        listOf("⌫", "0", ";", "ADD"),
    )
    val backspace = stringResource(R.string.backspace)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    when (k) {
                        "ADD" -> Key(stringResource(R.string.add), Modifier.weight(1f).testTag("key:ADD"), primary = true, enabled = canAdd, onClick = onAdd)
                        "⌫" -> Key("⌫", Modifier.weight(1f), contentDescription = backspace, onLongClick = onClear, onClick = onBackspace)
                        ":", "-", ",", ";" -> Key(k, Modifier.weight(1f).testTag("key:$k"), tonal = true) { onKey(k) }
                        else -> Key(k, Modifier.weight(1f).testTag("key:$k")) { onKey(k) }
                    }
                }
            }
        }
    }
}

/** A silent key: a plain clickable surface (no sound, no vibration), at least 48 dp tall. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Key(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val gold = LocalOrnament.current.gold
    val bg = when {
        primary && enabled -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.surfaceVariant
        tonal -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val fg = when {
        primary && enabled -> MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        tonal -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (primary) Color.Transparent else gold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .then(
                if (enabled) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = contentDescription)
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontFamily = TitleSerif, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

