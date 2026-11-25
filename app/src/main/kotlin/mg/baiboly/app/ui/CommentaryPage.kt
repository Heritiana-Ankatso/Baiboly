package mg.baiboly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.app.Tradition
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.CommentNote
import mg.baiboly.data.Markup

/**
 * What the commentaries say on the passage: Calvin, the Geneva Bible notes, Matthew Henry (Concise).
 * All three are Reformed: with the Catholic tradition chosen they are marked as another tradition's.
 */
@Composable
fun CommentaryPage(vm: AppViewModel, passage: Passage, onDismiss: () -> Unit) {
    val all = vm.commentaries.available
    if (all.isEmpty()) return
    var chosen by rememberSaveable { mutableStateOf(all.first().id) }
    val c = all.firstOrNull { it.id == chosen } ?: all.first()
    var notes by remember(passage, c.id) { mutableStateOf<List<CommentNote>?>(null) }
    LaunchedEffect(passage, c.id) {
        val m = vm.toMalagasy(passage)
        notes = vm.onIo { vm.commentaries.notes(c, m.book, m.chapter, m.verseStart.coerceAtLeast(1), if (m.isWholeChapter) 999 else m.verseEnd) }
    }
    val ornament = LocalOrnament.current
    val linkStyle = TextLinkStyles(SpanStyle(color = ornament.rubric, fontWeight = FontWeight.Bold))
    val otherTradition = vm.tradition == Tradition.Catholic
    Column(Modifier.fillMaxSize().testTag("commentary")) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(all, key = { it.id }) { k ->
                FilterChip(selected = k.id == c.id, onClick = { chosen = k.id }, label = { Text(k.author.substringAfterLast(' ').let { if (k.id == "geneva") "Geneva" else it }) },
                    modifier = Modifier.testTag("commentary:${k.id}"))
            }
        }
        Text(c.title + " · " + c.year, style = MaterialTheme.typography.titleSmall, color = ornament.rubric, modifier = Modifier.padding(top = 6.dp))
        if (otherTradition) Text(stringResource(R.string.other_tradition_note, stringResource(R.string.tradition_reformed)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val list = notes
        when {
            list == null -> Text(stringResource(R.string.loading), Modifier.padding(top = 12.dp))
            list.isEmpty() -> Text(stringResource(R.string.commentary_none, c.title), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp).testTag("commentary-none"))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(list.size) { i ->
                    val n = list[i]
                    val text = remember(n.body, ornament) {
                        buildAnnotatedString {
                            for (p in vm.library.markup(n.body)) when (p) {
                                is Markup.Plain -> append(p.text)
                                is Markup.Ref -> withLink(LinkAnnotation.Clickable("ref", linkStyle) { vm.openPassage(p.passage); onDismiss() }) { append(p.label) }
                                is Markup.Link -> append(p.label)
                            }
                        }
                    }
                    Column(Modifier.padding(vertical = 8.dp).testTag("comment:$i")) {
                        Text(Passage(n.book, n.chapter, n.v1, n.v2).label(), style = MaterialTheme.typography.labelLarge, color = ornament.gold)
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                item {
                    Text(stringResource(R.string.commentary_credit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}
