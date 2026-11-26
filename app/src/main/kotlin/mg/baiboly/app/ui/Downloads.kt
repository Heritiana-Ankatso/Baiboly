package mg.baiboly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.data.Pack
import mg.baiboly.data.PackState
import java.util.Locale

/** A size in megabytes, one decimal ("4.0 MB", "4,0 Mo"). */
@Composable
fun sizeText(bytes: Long): String {
    val ctx = LocalUiContext.current ?: androidx.compose.ui.platform.LocalContext.current
    val locale = ctx.resources.configuration.locales[0] ?: Locale.getDefault()
    return stringResource(R.string.size_mb, String.format(locale, "%.1f", bytes / 1_000_000.0))
}

/**
 * The confirmation before any download: what, how big, from where. Shown over the
 * whole app whichever menu asked for it; nothing is downloaded without this yes.
 */
@Composable
fun DownloadConfirmDialog(vm: AppViewModel) {
    val (pack, then) = vm.confirmDownload ?: return
    LocalizedAlertDialog(
        onDismissRequest = { vm.confirmDownload = null },
        title = { Text(stringResource(R.string.pack_confirm_title, vm.packTitle(pack))) },
        text = { Text(stringResource(R.string.pack_confirm_text, sizeText(pack.gzSize), sizeText(pack.size))) },
        confirmButton = {
            TextButton(onClick = { vm.startDownload(pack, then) }, modifier = Modifier.testTag("pack-confirm")) {
                Text(stringResource(R.string.pack_download))
            }
        },
        dismissButton = { TextButton(onClick = { vm.confirmDownload = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * One pack: its name, what it is, its sizes or progress, and the one action that
 * fits (Download, Update, Remove, Cancel). Used by Settings › Bible › Downloads and
 * wherever a feature needs a pack that is not on the phone (Study panel).
 */
@Composable
fun PackRow(vm: AppViewModel, pack: Pack, note: String? = null, removable: Boolean = true) {
    val state = vm.packState(pack)
    val progress = vm.downloads[pack.file]
    var confirmRemove by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("pack:${pack.file}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(vm.packTitle(pack), fontWeight = FontWeight.SemiBold)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    when {
                        progress != null -> stringResource(R.string.pack_downloading, (progress * 100).toInt())
                        state == PackState.Installed -> stringResource(R.string.pack_installed) + " · " + sizeText(pack.size)
                        state == PackState.UpdateAvailable -> stringResource(R.string.pack_update_available) + " · " + sizeText(pack.gzSize)
                        else -> stringResource(R.string.pack_sizes, sizeText(pack.gzSize), sizeText(pack.size))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state == PackState.NotInstalled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                if (pack.licence.isNotBlank()) Text(pack.licence, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            when {
                progress != null -> TextButton(onClick = { vm.cancelDownload(pack) }, modifier = Modifier.heightIn(min = 48.dp).testTag("pack-cancel:${pack.file}")) {
                    Text(stringResource(R.string.cancel))
                }
                state == PackState.NotInstalled -> OutlinedButton(onClick = { vm.requestDownload(pack) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("pack-download:${pack.file}")) { Text(stringResource(R.string.pack_download)) }
                state == PackState.UpdateAvailable -> OutlinedButton(onClick = { vm.requestDownload(pack) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("pack-download:${pack.file}")) { Text(stringResource(R.string.pack_update)) }
                removable -> TextButton(onClick = { confirmRemove = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("pack-remove:${pack.file}")) {
                    Text(stringResource(R.string.pack_remove))
                }
            }
        }
        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    if (confirmRemove) LocalizedAlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.pack_remove_title, vm.packTitle(pack))) },
        text = { Text(stringResource(R.string.pack_remove_text, sizeText(pack.size))) },
        confirmButton = {
            TextButton(onClick = { confirmRemove = false; vm.removePack(pack) }, modifier = Modifier.testTag("pack-remove-confirm")) {
                Text(stringResource(R.string.pack_remove))
            }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Settings › Bible › Downloads: every pack the tradition offers, by kind, with what is on the phone. */
@Composable
fun DownloadsSettings(vm: AppViewModel) {
    val langName = mapOf("mg" to "Malagasy", "fr" to "Français", "en" to "English")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("downloads")) {
        Text(stringResource(R.string.downloads_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val onPhone = vm.allPacks.filter { vm.packState(it) != PackState.NotInstalled }.sumOf { it.size }
        Text(stringResource(R.string.downloads_on_phone, sizeText(onPhone)), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp))
        val packs = vm.visiblePacks
        val texts = packs.filter { it.kind == "text" }
        if (texts.isNotEmpty()) {
            Text(stringResource(R.string.downloads_bibles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            // in the order of the translation menu (language, then the catalogue's order)
            vm.visibleTranslations.groupBy { it.lang }.forEach { (lang, ts) ->
                val inLang = ts.mapNotNull { t -> texts.firstOrNull { it.id == t.id }?.let { t to it } }
                if (inLang.isEmpty()) return@forEach
                Text(langName[lang] ?: lang, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 10.dp))
                inLang.forEach { (t, p) -> PackRow(vm, p, note = listOfNotNull(styleNote(t), if (t.catholic) stringResource(R.string.catholic_tag) else null).joinToString(" · ")) }
            }
        }
        val study = packs.filter { it.kind != "text" }
        if (study.isNotEmpty()) {
            Text(stringResource(R.string.downloads_study), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            study.sortedBy { if (it.kind == "original") 0 else 1 }.forEach { p ->
                val note = when (p.kind) {
                    "original" -> stringResource(R.string.pack_original_note)
                    else -> mg.baiboly.data.CommentaryRepository.KNOWN.firstOrNull { it.id == p.id }?.let { "${it.author} · ${it.year}" }
                }
                PackRow(vm, p, note = note)
            }
        }
    }
}
