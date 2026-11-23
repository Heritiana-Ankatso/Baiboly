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
    "crampon" -> stringResource(R.string.note_crampon)
    "webc" -> stringResource(R.string.note_webc)
    "dra" -> stringResource(R.string.note_dra)
    else -> t.notes
}

/** Language of Bible references: that of the interface (book abbreviations "Jao", "Jn", "John"). */
@Composable
fun refLang(): String = stringResource(R.string.ref_lang)

/** A reference in the interface language: "Jao 3: 16", "Jn 3:16", "John 3:16". */
@Composable
fun mg.baiboly.core.ref.Passage.label(): String = shortLabel(refLang())
