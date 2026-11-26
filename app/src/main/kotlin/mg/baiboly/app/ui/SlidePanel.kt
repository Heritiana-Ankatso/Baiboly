package mg.baiboly.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mg.baiboly.app.R
import mg.baiboly.app.Style
import kotlin.math.abs

/*
 * A panel that comes up from the foot of the screen, like Navy's menu comes in from the side: the
 * movement shows where it came from and where it goes back to. It rests at half height (the text
 * being read stays visible above it) or at full height, and is dragged between the two or tucked
 * back down. It takes its room from the reader instead of covering it, so the passage can still be
 * scrolled while the notes are open.
 */

/** Height of the half-open panel, as a share of the room it shares with the text above it. */
const val PanelHalf = 0.5f

@Stable
class PanelState {
    /** 0 = tucked away, [PanelHalf], 1 = the whole area. */
    var fraction by mutableFloatStateOf(0f)
        private set

    /** Height in pixels of the area the reader and the panel share (measured by [panelSpan]). */
    var span by mutableIntStateOf(1)

    private var job: Job? = null

    val visible: Boolean get() = fraction > 0.001f

    /** Weight of whatever shares the column with the panel: the rest of the room. */
    val restWeight: Float get() = (1f - fraction).coerceAtLeast(0.0001f)

    /** Slides to [target]; [then] runs when the movement has finished (not when another one interrupts it). */
    fun animateTo(scope: CoroutineScope, target: Float, then: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            animate(fraction, target, animationSpec = tween(300, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
            then()
        }
    }

    fun jumpTo(value: Float) { job?.cancel(); fraction = value.coerceIn(0f, 1f) }

    /** A finger dragging the handle by [deltaPx] (downwards is positive). */
    fun drag(deltaPx: Float) {
        job?.cancel()
        fraction = (fraction - deltaPx / span).coerceIn(0f, 1f)
    }

    /** The finger lifted: rests at the nearest of tucked, half and full, a fling carrying it further. */
    fun settle(scope: CoroutineScope, velocityPxPerSec: Float, onTuck: () -> Unit) {
        val projected = fraction - velocityPxPerSec * 0.12f / span
        val target = listOf(0f, PanelHalf, 1f).minByOrNull { abs(it - projected) } ?: PanelHalf
        if (target == 0f) onTuck() else animateTo(scope, target)
    }

    fun toggle(scope: CoroutineScope) = animateTo(scope, if (fraction > 0.75f) PanelHalf else 1f)
}

/** Marks the column that holds the reader and the panel, so the drag knows how far a finger has gone. */
fun Modifier.panelSpan(state: PanelState): Modifier = onSizeChanged { state.span = it.height.coerceAtLeast(1) }

/**
 * The panel itself, to be placed under the reader in a column whose reader has the weight
 * [PanelState.restWeight]. [onTuck] puts it away (the owner saves what must be saved, then slides it down).
 */
@Composable
fun ColumnScope.SlidePanel(
    state: PanelState,
    title: String,
    onTuck: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.visible) return
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val gold = LocalOrnament.current.gold
    val drag = rememberDraggableState { state.drag(it) }
    Surface(
        modifier = modifier.weight(state.fraction.coerceAtLeast(0.0001f)).fillMaxWidth().testTag("panel"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = scheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 14.dp,
        border = if (LocalStyle.current == Style.Manuscript) BorderStroke(1.dp, gold.copy(alpha = 0.8f)) else null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // the grip: dragged up or down to resize, tapped to go between half and full
            Column(
                Modifier.fillMaxWidth()
                    .draggable(drag, Orientation.Vertical, onDragStopped = { v -> state.settle(scope, v, onTuck) })
                    .clickable { state.toggle(scope) }
                    .testTag("panel-grip"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 8.dp, bottom = 2.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.outlineVariant))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    actions()
                    val full = state.fraction > 0.75f
                    IconButton(onClick = { state.toggle(scope) }, modifier = Modifier.size(44.dp).testTag("panel-size")) {
                        Icon(if (full) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                            stringResource(if (full) R.string.panel_half else R.string.panel_full), tint = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onTuck, modifier = Modifier.size(44.dp).testTag("panel-tuck")) {
                        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.panel_tuck), tint = scheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}
