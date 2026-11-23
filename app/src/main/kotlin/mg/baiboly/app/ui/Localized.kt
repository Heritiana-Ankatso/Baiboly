package mg.baiboly.app.ui

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog

/*
 * Why this file exists
 *
 * The interface language is applied by BaibolyApp, which provides a localized
 * LocalContext / LocalConfiguration to the composition. Sheets, dialogs and
 * menus, however, are drawn in a window of their own, and Compose gives that
 * window's composition a fresh LocalContext / LocalConfiguration taken from the
 * activity, i.e. in the PHONE's language. Without help, a sheet opened from a
 * French interface on an English phone would be in English.
 *
 * Custom composition locals do cross into those windows, so BaibolyApp also
 * provides the localized context as LocalUiContext, and the wrappers below
 * re-apply it inside every window. Use them instead of ModalBottomSheet,
 * DropdownMenu, AlertDialog and Dialog.
 */

/** The context whose resources use the chosen interface language (null outside BaibolyApp, e.g. previews). */
val LocalUiContext = staticCompositionLocalOf<Context?> { null }

/** Re-applies the interface language to [content]; needed inside any separate window. */
@Composable
fun Localized(content: @Composable () -> Unit) {
    val ui = LocalUiContext.current ?: return content()
    CompositionLocalProvider(
        LocalContext provides ui,
        LocalConfiguration provides ui.resources.configuration,
        content = content,
    )
}

/** [ModalBottomSheet] in the interface language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalizedSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = dragHandle?.let { handle -> { Localized(handle) } },
    ) {
        val scope = this
        Localized { scope.content() }
    }
}

/** [DropdownMenu] in the interface language. */
@Composable
fun LocalizedMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        val scope = this
        Localized { scope.content() }
    }
}

/** [AlertDialog] in the interface language (each slot is composed in the dialog's window). */
@Composable
fun LocalizedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { Localized(confirmButton) },
        dismissButton = dismissButton?.let { slot -> { Localized(slot) } },
        title = title?.let { slot -> { Localized(slot) } },
        text = text?.let { slot -> { Localized(slot) } },
    )
}

/** [Dialog] in the interface language. */
@Composable
fun LocalizedDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) { Localized(content) }
}
