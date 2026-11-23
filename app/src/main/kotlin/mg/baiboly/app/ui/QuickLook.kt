package mg.baiboly.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mg.baiboly.app.AppViewModel
import mg.baiboly.app.R
import mg.baiboly.core.ref.Passage
import mg.baiboly.data.BibleEvent
import mg.baiboly.data.CreedItem
import mg.baiboly.data.Person
import mg.baiboly.data.Place

/** The items the quick look can show, in its order; the settings list them the same way. */
val QUICK_LOOK_ITEMS = listOf("translation", "units", "people", "places", "confessions")

@Composable
fun quickLookItemLabel(item: String): String = stringResource(
    when (item) {
        "translation" -> R.string.ql_translation
        "units" -> R.string.units
        "people" -> R.string.tab_people
        "places" -> R.string.tab_places
        else -> R.string.tool_confessions
    },
)

/**
 * A small card shown with selected verses (any mode): what the user chose in Settings, Advanced,
 * Quick look; by default the passage in another language and the units it names. A section with
 * nothing for this passage is left out, and the card with it when every section is empty. A tap
 * opens the full Study panel at that tab (or the converter for a unit).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickLookCard(vm: AppViewModel, passage: Passage, onOpen: (PassageTab) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val items = vm.quickLookItems
    if (items.isEmpty()) return
    val target = remember(vm.translationId, vm.quickLookTranslation, vm.tradition) { vm.quickLookTarget() }
    var other by remember(passage, target) { mutableStateOf<AppViewModel.Compared?>(null) }
    var units by remember(passage) { mutableStateOf<List<BibleUnit>>(emptyList()) }
    var people by remember(passage) { mutableStateOf<List<Person>>(emptyList()) }
    var events by remember(passage) { mutableStateOf<List<BibleEvent>>(emptyList()) }
    var places by remember(passage) { mutableStateOf<List<Place>>(emptyList()) }
    var creeds by remember(passage) { mutableStateOf<List<CreedItem>>(emptyList()) }
    var loaded by remember(passage) { mutableStateOf(false) }
    LaunchedEffect(passage, target, items) {
        if ("translation" in items && target != null) other = vm.passageIn(passage, target)
        if ("units" in items) units = BibleUnits.namedIn(vm.passageTexts(passage), passage.book)
        if ("people" in items) { people = vm.peopleInPassage(passage); events = vm.eventsInPassage(passage) }
        if ("places" in items) places = vm.placesInPassage(passage)
        if ("confessions" in items) {
            val own = vm.ownFamilies()
            val docs = vm.onIo { vm.chronicle.creedDocs }.associateBy { it.id }
            creeds = vm.creedsCiting(passage).filter { docs[it.doc]?.family in own }
        }
        loaded = true
    }
    val o = other
    val hasAny = (o != null && "translation" in items) || units.isNotEmpty() || people.isNotEmpty() || events.isNotEmpty() ||
        places.isNotEmpty() || creeds.isNotEmpty()
    if (!loaded || !hasAny) return
    val lang = vm.uiLanguage.ifEmpty { "en" }
    val locale = LocalConfiguration.current.locales[0]
    val ornament = LocalOrnament.current
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.38f).dp
    Surface(
        shape = RoundedCornerShape(18.dp), tonalElevation = 3.dp, shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).heightIn(max = maxH).testTag("quick-look"),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ql_title), style = MaterialTheme.typography.labelMedium, color = ornament.gold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp).testTag("quick-look-close")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(18.dp))
                }
            }
            // the sections that have something for this passage, with a hairline between them
            val shown = QUICK_LOOK_ITEMS.filter { it in items }.filter {
                when (it) {
                    "translation" -> o != null
                    "units" -> units.isNotEmpty()
                    "people" -> people.isNotEmpty() || events.isNotEmpty()
                    "places" -> places.isNotEmpty()
                    else -> creeds.isNotEmpty()
                }
            }
            shown.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                when (item) {
                "translation" -> {
                    val o = o!!
                    Column(Modifier.fillMaxWidth().clickable { onOpen(PassageTab.Translations) }.padding(end = 10.dp).testTag("quick-look-translation")) {
                        Text(o.translation.shortName + " · " + o.passage.label(), style = MaterialTheme.typography.labelLarge, color = ornament.rubric)
                        Text(o.text, style = MaterialTheme.typography.bodyMedium, fontFamily = TextSerif, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }
                "units" -> {
                    Text(stringResource(R.string.units), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        units.forEach { u ->
                            AssistChip(onClick = { vm.openUnit(u.id) }, modifier = Modifier.testTag("quick-look-unit:${u.id}"),
                                label = { Text(listOfNotNull(u.name(lang), unitSummary(u, locale)).joinToString(" "), maxLines = 1) })
                        }
                    }
                }
                "people" -> {
                    Column(Modifier.fillMaxWidth().clickable { onOpen(PassageTab.People) }.padding(end = 10.dp).testTag("quick-look-people")) {
                        Text(stringResource(R.string.tab_people), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text((people.map { it.name } + events.map { it.title }).distinct().joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                "places" -> {
                    Column(Modifier.fillMaxWidth().clickable { onOpen(PassageTab.Places) }.padding(end = 10.dp).testTag("quick-look-places")) {
                        Text(stringResource(R.string.tab_places), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(places.joinToString(" · ") { it.displayName }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                else -> {
                    Column(Modifier.fillMaxWidth().clickable { onOpen(PassageTab.Confessions) }.padding(end = 10.dp).testTag("quick-look-confessions")) {
                        Text(stringResource(R.string.tool_confessions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.ql_cited, creeds.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
                    }
                }
                }
            }
        }
    }
}
