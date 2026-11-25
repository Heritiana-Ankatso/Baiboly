package mg.baiboly.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R

/** An introduction to one book of the Bible (assets/book_intros.tsv), in English. */
data class BookIntro(
    val book: Int,
    val authorAndDate: String,
    val setting: String,
    val structure: String,
    val message: String,
    /** The stages of the storyline (ids in storyline.tsv) this book belongs to. */
    val stages: List<String>,
)

/** One stage of the story of redemption (assets/storyline.tsv). */
data class StorylineStage(val id: String, val title: String, val span: String, val text: String, val keyTexts: String)

/**
 * Book introductions and the covenant storyline of Scripture, written for the app from the Reformed
 * confessions' account of the one covenant of grace (WCF VII, Heidelberg Q&A 19). Where Reformed
 * Christians differ, the text says so.
 */
class BookIntros(val intros: Map<Int, BookIntro>, val stages: List<StorylineStage>) {
    companion object {
        private fun rows(context: Context, name: String): List<List<String>> =
            context.assets.open(name).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()
            }

        fun load(context: Context): BookIntros {
            val intros = rows(context, "book_intros.tsv").filter { it.size >= 6 }.associate { f ->
                f[0].toInt() to BookIntro(f[0].toInt(), f[1], f[2], f[3], f[4], f[5].split(',').map { it.trim() })
            }
            val stages = rows(context, "storyline.tsv").filter { it.size >= 5 }.map { StorylineStage(it[0], it[1], it[2], it[3], it[4]) }
            return BookIntros(intros, stages)
        }
    }
}

/** The Study panel's *Introduction* tab: the book's author and date, setting, structure and message, and where it sits in the storyline. */
@Composable
fun BookIntroPage(vm: AppViewModel, book: Int) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<BookIntros?>(null) }
    LaunchedEffect(Unit) { data = vm.onIo { BookIntros.load(ctx) } }
    val d = data ?: return Text(stringResource(R.string.loading))
    val intro = d.intros[book]
    Column(Modifier.verticalScroll(rememberScrollState()).testTag("book-intro")) {
        Text(
            stringResource(R.string.intro_english_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (intro != null) {
            IntroField(R.string.intro_author, intro.authorAndDate)
            IntroField(R.string.intro_setting, intro.setting)
            IntroField(R.string.intro_structure, intro.structure)
            IntroField(R.string.intro_message, intro.message)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.intro_storyline), style = MaterialTheme.typography.titleMedium, color = LocalOrnament.current.rubric)
        Text(
            stringResource(R.string.intro_storyline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        d.stages.forEach { s ->
            val here = intro != null && s.id in intro.stages
            Collapsible(title = (if (here) "▸ " else "") + s.title, tag = "stage:${s.id}", initiallyOpen = here, detail = s.span) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(s.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        s.keyTexts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroField(label: Int, value: String) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
}
