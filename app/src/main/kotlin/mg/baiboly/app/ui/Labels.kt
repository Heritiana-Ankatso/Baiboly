package mg.baiboly.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mg.baiboly.app.R
import mg.baiboly.data.Translation

/** Translation style in the interface language (the database holds English). */
@Composable
fun styleNote(t: Translation): String = when (t.style) {
    "classic" -> stringResource(R.string.style_classic)
    "literal" -> stringResource(R.string.style_literal)
    "faithful-modern" -> stringResource(R.string.style_faithful_modern)
    "accessible" -> stringResource(R.string.style_accessible)
    "simplified" -> stringResource(R.string.style_simplified)
    "regional" -> stringResource(R.string.style_regional)
    else -> t.styleNote
}

/** Remarks about a translation, in the interface language. */
@Composable
fun translationNote(t: Translation): String? = when (t.id) {
    "mg1865" -> stringResource(R.string.note_mg1865)
    "darby_fr" -> stringResource(R.string.note_darby_fr)
    "ncl_fr" -> stringResource(R.string.note_ncl_fr)
    "tdx" -> stringResource(R.string.note_tdx)
    else -> t.notes
}
