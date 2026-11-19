package mg.baiboly.app.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback // silence-check: allow (no-op implementation below)
import androidx.compose.ui.hapticfeedback.HapticFeedbackType // silence-check: allow (type for the no-op below)
import androidx.compose.ui.platform.LocalHapticFeedback // silence-check: allow (replaced by the no-op below)

/**
 * The app makes no sound and no vibration (brief section 2). Compose triggers
 * haptics on long-press and text selection through a composition local, so the
 * whole UI gets a no-op implementation; the window's views have system click
 * sounds and haptics switched off.
 */
object Silent {
    private val none = object : HapticFeedback { // silence-check: allow (does nothing)
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit // silence-check: allow (does nothing)
    }

    fun applyTo(root: View) {
        root.isSoundEffectsEnabled = false // silence-check: allow (turns sounds off)
        root.isHapticFeedbackEnabled = false // silence-check: allow (turns haptics off)
    }

    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalHapticFeedback provides none, content = content) // silence-check: allow (no-op)
    }
}
