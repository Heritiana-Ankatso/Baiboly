package mg.baiboly.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mg.baiboly.app.R

/**
 * The show / hide header used across the app: a title, an optional count or value, and a chevron
 * that turns when the section is open. A tap folds or unfolds what follows.
 */
@Composable
fun CollapsibleHeader(
    title: String,
    open: Boolean,
    tag: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = LocalOrnament.current.rubric,
    horizontal: Dp = 0.dp,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val state = stringResource(if (open) R.string.shown else R.string.hidden)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle)
            .semantics { heading(); stateDescription = state }
            .testTag(tag).padding(horizontal = horizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = style, color = color)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
    }
}

/** A section that can be folded away; it remembers its state across recompositions and rotation. */
@Composable
fun Collapsible(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    detail: String? = null,
    horizontal: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(tag) { mutableStateOf(initiallyOpen) }
    Column(modifier) {
        CollapsibleHeader(title, open, tag, { open = !open }, detail = detail, horizontal = horizontal)
        AnimatedVisibility(open) { Column { content() } }
    }
}

/**
 * The same in a lazy list: the header as one item, the [rows] as items only while open.
 * [open] and [onToggle] come from the caller, which keeps the state (one per section).
 */
fun <T> LazyListScope.collapsibleSection(
    key: String,
    title: @Composable () -> String,
    open: Boolean,
    onToggle: () -> Unit,
    rows: List<T>,
    rowKey: (T) -> Any,
    detail: @Composable () -> String? = { null },
    row: @Composable (T) -> Unit,
) {
    item(key = "hdr-$key") { CollapsibleHeader(title(), open, "section:$key", onToggle, detail = detail()) }
    if (open) items(rows.size, key = { i -> rowKey(rows[i]) }) { i -> row(rows[i]) }
}

/** Marks a choice as the chosen one for screen readers. */
fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.setSelectedState(on: Boolean) {
    androidx.compose.ui.semantics.SemanticsProperties.Selected.let { this[it] = on }
}
