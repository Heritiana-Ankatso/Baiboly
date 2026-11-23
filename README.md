# Baiboly

A free, silent Malagasy Bible for Android that works fully offline, built for two uses of the same text:

* **Church services.** Capture the verses the preacher announces as a short list, then move between them.
* **Reading and study.** Read chapter by chapter, keep notes and highlights, and look at the Greek and Hebrew words behind a verse.

**Three modes** (tap the badge at the top left of every main page):

| Mode | For | Bottom tabs | What changes |
|---|---|---|---|
| **Casual** (*Tsotra*) | Everyday reading | Read · Search · Library | Plain reading; Listen button if reading aloud is on |
| **Church** (*Fiangonana*) | The service | Service · Books · Lists | Silent (no Listen), screen kept on; service list strip, large *previous / next passage* bar, *Back to my place*; selected verses go *To list* |
| **Study** (*Fianarana*) | Study | Read · Search · Dictionary · Map · Library | Tapping a verse number opens the original words (א / α) and the cross-references; *References* in the selection bar |

**Navigation.** Standard Android patterns: a bottom navigation bar for the main pages, the chapter title opens the books (Old / New Testament tabs, then a chapter grid), the translation chip switches text, the gear opens Settings. *Library* gathers Notes, Highlights, History and Lists in tabs. Selecting verses replaces the tabs with an icon action bar (Copy, Note, Highlight, …).

**Reading texts:**

| Language | Translation | Style | Licence |
|---|---|---|---|
| Malagasy | Baiboly Malagasy 1865 | Classic church translation (primary text, default) | Public domain |
| Malagasy | Saontsin’ Añahare (Tandroy) | Regional dialect of southern Madagascar, draft | CC BY-SA 4.0 |
| French | Louis Segond 1910 | Classic church translation | Public domain |
| French | Bible J.N. Darby | Word-for-word, close to the original | Public domain |
| French | Sainte Bible néo-Crampon Libre ✝ | Modern French, close to the original | CC BY-SA 4.0 |
| French | Bible Crampon 1923 ✝ | Classic Catholic translation | Public domain |
| English | King James Version | Word-for-word, classic | Public domain |
| English | Berean Standard Bible | Modern, close to the original | Public domain |
| English | Literal Standard Version | Modern, very literal | CC BY-SA 4.0 |
| English | World English Bible | Modern, easy to read | Public domain |
| English | Bible in Basic English | Very simple vocabulary | Public domain |
| English | World English Bible, Catholic Edition ✝ | Modern, easy to read | Public domain |
| English | Douay-Rheims 1899 ✝ | Classic Catholic translation (Psalms numbered as in the Vulgate) | Public domain |

✝ Catholic Bibles (73 books): they include Tobit, Judith, 1–2 Maccabees, Wisdom, Sirach, Baruch and the Greek parts of Esther and Daniel, in Catholic order. A free Malagasy Catholic Bible could not be found: the 1938 text has no stated free licence, so it is not included.

**Design.** Vellum and ink by day, warm black and gilt by night; Gentium Book Plus for the text and Cormorant SC small capitals for titles (both SIL Open Font License); chapter openings with a rubricated initial and a gilt fleuron. Screenshots: [docs/screenshots](docs/screenshots) (01 Casual, 03 Church, 07 Study, 04 Settings, 06 Books, 10 Library; regenerate with `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'`).

**Themes.** Four complete designs, chosen in Settings › Appearance › Theme; each has its own colours, type, navigation, controls and layout of actions and settings:

| | Manuscript (default) | Modern | Page | Stained glass |
|---|---|---|---|---|
| Colours | Vellum · Purple · Olive | Slate · Paper | Sepia · Paper (true black at night) | Lapis · Purple · Olive |
| Type | Gentium, engraved small capitals | Phone's sans-serif, plain titles | Phone's serif, plain | Gentium, engraved |
| Navigation | Bottom tab bar | Floating capsule | No bars: ☰ opens one grouped menu (Bible · Study tools · My library · App) | Coloured header with tabs |
| Chapters | Gilt discs over the text | Floating ‹ › pill | Links after the last verse | Bottom bar ‹ chapter › |
| Verse actions | Bottom bar, icon + label | Floating dark toolbar | Top action bar | Panel of large tiles |
| Settings (groups, each on its own page) | Rows with gilt icons | Rounded cards | Flat list with hairlines | Coloured tiles |

Within a theme, the reader can also change the colour variant, the Bible typeface (classic Gentium / modern serif / sans-serif), engraved or plain titles, line spacing and text size; *Restore the original look* undoes it all.

**Settings** (gear icon): theme and language at the top, then one row per group, each opening its own page: Appearance (style, colours, typeface, line spacing, verses, text size, interface size); Default Bible; Reading aloud; Backup; About. **Advanced** holds the **Tradition** choice: *Protestant* (default: 66 books, Catholic translations hidden) or *Catholic* (adds the Catholic translations and the deuterocanonical books); and **Updates**: *Check now*, *Download and install* (in the app), and *Check automatically* (off by default).

**Study a passage** (any mode): select verses, then *Study* on the selection bar: the passage in every Bible offered (numbering matched), its original Hebrew/Greek words with lexicon entries, cross-references, the places it names (open on the map), the units it names (open in the converter), and the Easton / Nave / Martin entries named by its words. In Study mode a tap on a verse number opens the same panel.

**Screen space.** Settings › Appearance › *Interface size*: *Compact* (default, 88 %) or *Normal*; it scales bars, buttons and lists, not the Bible text. Text size 10 to 40. *Keep the screen on* (default on; always in Church mode).

**Study tools (all offline).**
* **Cross-references** for any verse (Study mode: tap the verse number, or select the verse and tap *References*): the Treasury of Scripture Knowledge, public domain, in Malagasy 1865 numbering.
* **Dictionary and topics** (Study mode, *Dictionary* tab): Easton's Bible Dictionary and Nave's Topical Bible (English, public domain) and the Glossaire de la Bible Martin (French, ~1,000 older Bible words, free with credit to biblemartin.com). Bible references in entries open the verse. No free Malagasy Bible dictionary was found; the French Westphal dictionary is licensed only for CrossWire's own format.
* **Units** (Study mode, *Dictionary* tab, *Units* chip; in the Page theme, its own menu entry): length, weight, dry and liquid measures, money and time. Type an amount and tap a unit to read it in every other unit of the same kind and in metric units (metres, grams, litres, with the prefix that reads best: km, cm, kg, mL); money also in days' wages (Mt 20:2). Each unit carries its name in the Malagasy 1865, Segond 1910 and KJV texts, its Hebrew or Greek word and a verse that opens the text. Relations the text states are marked "=" (Ex 30:13, 38:25-26, 16:36; Ezek 40:5, 45:11, 45:14; Mk 12:42); the modern values are the usual estimates (cubit ≈ 44.4 cm, shekel ≈ 11.4 g, ephah = bath ≈ 22 L) and say so. *Time* gives the hours of the day from sunrise and the four watches of the night.
* **People** (Study mode, *Dictionary* tab, *People* chip): 3,068 people of the Bible with their father, mother, spouses, brothers and sisters and children, the father line upwards (genealogies can skip generations, and the page says so), the events they take part in and every verse that names them. Names in English (KJV). Theographic Bible Metadata, CC BY-SA 4.0.
* **Timeline** (*Timeline* chip): a chart to scale, from Creation (4004 BC) to the early church, after F. N. Jones's chronology. Twelve eras shade the background (Creation to the Flood, the patriarchs, the Exodus, the judges, the kingdom, Israel and Judah, the exile, the return, between the Testaments, the life of Jesus, the early church). Lifetimes, reigns, judgeships and other periods are bars side by side, so overlaps show (Noah's life overlaps Abraham's; the kings of Israel and Judah reign in parallel), and 371 other events are dots on the axis. Labels appear as you zoom (pinch or double-tap), the most-told first; *This chapter* goes to the events of the chapter being read, picked out in red; an era chip frames that era. A tap opens the event. *List* shows the same events as a searchable list, with Ussher's year where it differs; every date is labelled as a reconstruction.
* **Confessions** (*Confessions* chip): the Apostles', Nicene and Athanasian Creeds and the Reformed confessions and catechisms (Belgic, Heidelberg, Dort, Westminster Confession, Larger and Shorter Catechisms, French Confession, Savoy, 1689 Baptist) in historic public-domain English, with their proof texts opening the verse, cited as `WCF 1.2`, `HC Q&A 23`. They are listed with the councils and assemblies that issued them. The **Tradition** setting decides what shows: Protestant shows creeds and Reformed documents; Catholic shows the creeds, with the Reformed documents under *Go deeper: other traditions*, each marked as such. Catholic and Orthodox documents are not included yet (no freely licensed source).
* The **Study panel** of a passage adds *People and events* and *Confessions* (the articles and answers that cite it).
* **Map** (Study mode, *Map* tab; *This chapter* / *All places*): 1,300 Bible places from OpenBible.info (CC BY 4.0), drawn offline by the app; tap a place for its verses and the periods in which it appears.
  * **Full screen** by default: search, layers, zoom (+ / −, pinch, double tap) and *Fit the map* float over the map; *Show the bars* brings back the top bar and the navigation.
  * **Search** a place by name (accent-insensitive); the map flies to it.
  * **Regions:** 192 kingdoms, peoples, provinces and natural regions drawn whole from OpenBible.info's outlines (Edom, Moab, Philistia, Galilee, Samaria, Judea, Decapolis, Assyria, Egypt, Macedonia, Asia…). Uncertain extents are drawn as bands of likelihood. Tap inside a region or pick it under *Regions* (find it by name, list it A–Z or by most named, or show only lands and peoples, mountains and valleys, or seas, rivers and islands); *Layers › Region outlines* draws them all.
  * **Routes:** 15 journeys of the Bible through the stops the text names (Abraham, Jacob, Joseph, the Exodus, the entry into Canaan, Elijah, Jesus, Emmaus, Philip, Saul, Paul's journeys and voyage to Rome), and *From… to…*: the shortest way between two places over the Roman roads and the Old Testament highways, with its length and days on foot (about 30 km a day), or a straight line where no road joins them.
  * **Readable at every zoom:** places are weighted by how many verses name them; at the opening view only the main ones show, smaller towns and villages appear as you zoom in, and a name that would overlap another is left out. Regions (Moab, Galilee, …) are written across the land, without a dot.
  * **Timeline:** *All periods* or one of nine (Patriarchs · Exodus and wilderness · Conquest and judges · United kingdom · Israel and Judah · Exile and return · Between the Testaments · Life of Jesus · Early church), each with approximate dates. A period shows the places its books name, weighted within that period, and the borders of the time where open data exists (Persian empire, Hasmonean kingdom, Herod's kingdom, Roman provinces). No free dataset of the borders of Israel, Judah and their neighbours was found, so the Old Testament periods have no shaded borders.
  * **Base maps (layers button):** *Terrain* (shaded relief, default), *Ancient world* (plain), *Today's borders* (modern countries and main cities, to relate places to today). **Roads:** the Old Testament highways (Way of the Sea, King's Highway, Ridge Route, traced through their known stations) before the Roman age, and the Roman roads (Ancient World Mapping Center, ODbL) in the time of Jesus and the early church.
  * Place names are in English (OpenBible.info); modern names in English or French.
* **Reading aloud** (off by default; switch on in Settings): the headphones button in the top bar (Casual and Study modes) reads with the phone's text-to-speech voice, verse by verse and on into the next chapter, also with the screen off, with controls in the notification and on the lock screen. Each text is read only with a voice in its own language; phones have no Malagasy voice yet, so Malagasy text is not read aloud. This is the only part of the app allowed to use audio (a reviewed exception in `tools/check_silence.sh`).

**Study symbol.** The study tab shows **א** (Aleph) in books written in Hebrew or Aramaic (the Old Testament) and **α** in books that come down to us in Greek (New Testament and the deuterocanonical books).

The **default Bible** (what the app opens with) is Malagasy 1865; it is changed with one tap on ☆ in the translation menu (the chip in the top bar) or in Settings. The **interface** opens in Malagasy and can be switched to French, English or the phone’s language in Settings.

The app works fully offline. Its only internet use is **updates** (Settings › Advanced › Updates): *Check now* asks GitHub for the number of the latest version, on a tap; *Check automatically* (off by default) does the same about once a day and shows a silent notification when a new version is out, which opens these settings. *Download and install* downloads the release's APK inside the app, on a tap, checks its SHA-256, package and version, and hands it to Android's installer (REQUEST_INSTALL_PACKAGES), which asks the user to confirm; Android itself refuses an APK not signed with the app's key. Nothing about the reader is sent. No VIBRATE permission, no accounts, no analytics and no ads. It makes no sound and no vibration.

## Installing

Download the APK from the repository's **Releases** page (each version has its own release, built and signed by GitHub Actions). Updates install over the app and keep lists, notes, highlights and settings; after an update the app shows "What's new" once. See [docs/RELEASING.md](docs/RELEASING.md).

## Building

Requirements: JDK 17 or newer, the Android SDK (platform 35, build-tools 35), and Python 3.9 or newer with Pillow (`pip install -r tools/requirements.txt`).

```bash
python3 tools/build_data.py          # downloads the sources once (cached in tools/.cache), builds the databases
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

* The Gradle `bibleData` task runs `tools/build_data.py` automatically when the databases are missing.
* The generated databases are not committed (about 120 MB). They are rebuilt from the sources.
* **Release signing.** The permanent key is never in the repository: GitHub Actions uses repository secrets, local builds a git-ignored `keystore.properties` (see [docs/RELEASING.md](docs/RELEASING.md)). Without a key, release builds are signed with the debug key and cannot update an installed release.

**Tests:**

```bash
./gradlew :core:test                 # parser, list logic, suggestions (pure Kotlin)
./gradlew :app:testDebugUnitTest     # acceptance scenarios on the real databases and UI (Robolectric)
bash tools/check_silence.sh          # also runs before every build
```

## Layout

| Module | Depends on Android? | Contents |
|---|---|---|
| `core` | No (pure Kotlin) | Book table and verse counts (generated), reference parser, service-list logic, suggestions, text normalisation |
| `data` | Yes, no UI | Bundled databases (copied out of the APK on first use), user data (`user.db`), study queries, backup |
| `app` | Yes | Jetpack Compose UI |
| `tools` | | `build_data.py` (data pipeline and licence checks), `abbreviations.tsv`, `check_silence.sh` |
| `docs` | | Licences, size report, verse-numbering and lexicon-gap reports, abbreviation review |

## Data

* **`bible.db`**: catalogue (`translation`, `book`) plus the Malagasy text.
  * `verse(id, translation, book, chapter, verse, text, text_norm)`. The id encodes translation, book, chapter and verse, so a chapter is a single id range.
  * `chapter_info` gives the verse counts; `heading` holds section titles.
  * `verse_fts` is a contentless FTS4 index over `text_norm`, the accent-free form, so `masina` finds `masìna`.
* **`text-<id>.db`**: one file per other translation, with the same schema and its own prebuilt search index. A file is unpacked only when that translation is first opened. Adding a translation is one line in `TRANSLATIONS` in `build_data.py`.
* **`study.db`**: `kjv_word`, `lexicon`, `strongs_verse`, `verse_map` and `verse_map_chapter`. See [docs/reports](docs/reports).
* **`user.db`**: `service_list`, `list_item`, `recent_passage`, `note` and `highlight`, as in the brief.

Every source passes an automatic licence check before it is used; see [docs/data-licences.md](docs/data-licences.md). The About screen lists them.

## Acceptance status (brief section 10)

| # | Task | How it is verified |
|---|---|---|
| 1 | Project and silence | The manifest has no VIBRATE or network-state permission; INTERNET is declared only for the opt-in update check. `check_silence.sh` runs before every build and fails it on any audio, vibration, haptic or sound-effect API, on those permissions (including in merged manifests), on INTERNET from any other source, and on network code outside the reviewed files of `update/` (the check and the in-app installer). Compose haptics are replaced by a no-op. |
| 2 | Text pipeline | `DataAcceptanceTest.task2_textPipeline`: Jaona 3:16, Salamo 1, 66 books, every translation |
| 3 | Parser | `ReferenceParserTest` (15 tests) |
| 4–9 | Reader, list strip, Add (4 taps), Browse and Back to my place, persistence, five-verse dry run | `UiAcceptanceTest.serviceDryRun` |
| 10 | Chapter in 3 taps (title › book › chapter), History, search (`masina` → `masìna`) | `UiAcceptanceTest.menuNotesHighlights`, `DataAcceptanceTest.task10_searchIgnoresAccents` |
| 11 | Notes and highlights survive restart; tapping an entry opens it | `UiAcceptanceTest.menuNotesHighlights` |
| 12 | Backup and restore reproduce data exactly | `DataAcceptanceTest.task12_backupRoundTrip` |
| 13 | Study pipeline: 2 Tim 2:25 → *acknowledging* = G1922 ἐπίγνωσις; gap report | `DataAcceptanceTest.task13_studyData`, [docs/reports/lexicon_gaps.md](docs/reports/lexicon_gaps.md) |
| — | Modes and Tradition: Church mode is silent; Catholic/Protestant under Settings › Advanced | `UiAcceptanceTest.churchModeIsSilent`, `UiAcceptanceTest.traditionInAdvancedSettings` |
| 14 | α panel, entry, Found N verses → Malagasy text with Back to my place | `UiAcceptanceTest.studyPanel` |
| — | Malagasy interface by default; switch, live change, kept after restart | `FreshInstallTest`, `UiAcceptanceTest.malagasyInterface` |
| — | Default Bible in one tap; each text's own chapter numbering (KJV Malachi 4) | `UiAcceptanceTest.defaultBibleAndOwnChapterNumbering` |
| 15 | Offline, dark mode, size report | No network permission at all; [docs/size-report.md](docs/size-report.md) |

**Not verified here:** no Android emulator could run in the build environment (no hardware virtualisation). The UI scenarios run under Robolectric on the JVM, not on a phone, and touch-target size and low-end-device performance still need a check on a real device.

## Decisions taken (the user can revisit them)

* **Browsing to add stays open after Add to list** (Church mode, *Books* tab), so several passages can be added in a row.
* **Add panel also stays open after Add**, showing "Added: …", so several verses can be captured in a row.
* **Swipe while away from the list.** When the reader is away from the current list item, the first swipe returns to it instead of skipping an item.
* **Selecting text.** Tapping a verse (text or number) selects it; adjacent verses merge. "Select words" picks a character range inside one verse. In Study mode the verse number opens the study panel instead.
* **Interface language.** Malagasy by default; French, English or "same as the phone" in Settings → *Fiteny ampiasaina*, applied immediately and remembered; few phones offer Malagasy as a system language, so the choice lives in the app. The Malagasy wording (`res/values-mg/strings.xml`) is a draft to be reviewed by a native speaker; for example *History* is *Novakiana* to avoid a clash with the book *Tantara*.
* **Psalm numbering (kept, as decided by the user).** Malagasy 1865 numbers psalm titles as verse 1, so Salamo 3 has 9 verses and Salamo 3:2 is KJV Psalm 3:1. The study panel maps this automatically.
* **Default Bible vs. the text being read.** The default Bible is what the app opens with. Switching text from the title menu lasts until the app is closed; ☆/★ in that menu (or Settings) changes the default in one tap.
* **Reading in other texts.** The service list and notes use Malagasy 1865 verse numbers. Each text keeps its own chapter and verse numbering when read (for example Malachi has 4 chapters in the KJV, Segond and BSB, 3 in Malagasy 1865 and néo-Crampon). A chapter that exists only in another text's numbering can be opened but not added to the list. Character-level highlights apply to the Malagasy text only.
* **Abbreviations** come from your examples, the Malagasy Bible dictionary's list and the 1865 source file (see [docs/abbreviations-review.md](docs/abbreviations-review.md)). *Mpit* is Mpitoriteny and *Mpits* Mpitsara; *Fil* is ambiguous (Filipiana or Filemona), so the app asks. Any unambiguous beginning of a book name also works (*Ohab*, *Asa*, *Filip*…).
* **néo-Crampon Libre** (CC BY-SA 4.0) is a Catholic translation: its deuterocanonical books and the Greek additions to Esther and Daniel are left out to match the 66-book canon, and it writes the divine name *Yahweh*. Attribution is on the About screen; the build re-checks both CC BY-SA licence texts on the publisher's pages.
* **Lexicon sources.** Base: the public-domain CrossWire Strong's dictionaries. On top: the Open Scriptures dictionaries (CC-BY-SA, approved by the user), which add Hebrew-script lemmas (אֱלֹהִים) and the 31 Greek entries missing from the base. Every tagged Strong's number now has an entry. The attribution is shown on the About screen; the build checks that the source still states CC-BY-SA. `--no-openscriptures` builds with public-domain data only.
* **Part of speech.** Strong's dictionaries have no part-of-speech field. It is filled only where Strong's own wording states it (about 13% of entries), never guessed.
* **Licence note.** Because part of the lexicon is CC-BY-SA, a redistributed adaptation of that lexicon data must keep the same licence and attribution. This concerns the data only, not the app's code or the public-domain Bible texts.

## Open points for the user

1. **Abbreviations still from the 1865 source file only** (status *source-text* in [docs/abbreviations-review.md](docs/abbreviations-review.md)) are consistent with common usage but not confirmed from a published list.
2. **Malagasy interface wording**: please review `app/src/main/res/values-mg/strings.xml` (for example *Baiboly fampiasa matetika* for "default Bible").
3. **Verse numbering.** 85 chapters differ between the Malagasy text and the KJV with no evidence-based mapping. In those chapters the study panel shows a warning. See [docs/reports/verse_map.md](docs/reports/verse_map.md).
