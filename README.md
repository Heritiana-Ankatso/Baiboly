# Baiboly

A free, offline, silent Malagasy Bible for Android, built for two uses of the same text:

* **Church services.** Capture the verses the preacher announces as a short list, then move between them.
* **Reading and study.** Read chapter by chapter, keep notes and highlights, and look at the Greek and Hebrew words behind a verse (α mode).

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
| English | World English Bible | Modern, easy to read | Public domain |
| English | Bible in Basic English | Very simple vocabulary | Public domain |
| English | World English Bible, Catholic Edition ✝ | Modern, easy to read | Public domain |
| English | Douay-Rheims 1899 ✝ | Classic Catholic translation (Psalms numbered as in the Vulgate) | Public domain |

✝ Catholic Bibles (73 books): they include Tobit, Judith, 1–2 Maccabees, Wisdom, Sirach, Baruch and the Greek parts of Esther and Daniel, in Catholic order. A free Malagasy Catholic Bible could not be found: the 1938 text has no stated free licence, so it is not included.

**Design.** Vellum and ink by day, warm black and gilt by night; Gentium Book Plus for the text and Cormorant SC small capitals for titles (both SIL Open Font License); chapter openings with a rubricated initial and a gilt fleuron. Screenshots: [docs/screenshots](docs/screenshots) (regenerate with `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'`).

**Settings at hand.** Theme (light / dark / device) and language (MG / FR / EN) are one tap each, at the top of the side menu and of Settings.

**Study symbol.** The study button shows **א** (Aleph) in books written in Hebrew or Aramaic (the Old Testament) and **α** in books that come down to us in Greek (New Testament and the deuterocanonical books).

The **default Bible** (what the app opens with) is Malagasy 1865; it is changed with one tap on ☆ in the reading-text menu (tap the title) or in Settings. The **interface** opens in Malagasy and can be switched to French, English or the phone’s language in Settings.

The app has no INTERNET or VIBRATE permission, no accounts, no analytics and no ads. It makes no sound and no vibration.

## Building

Requirements: JDK 17 or newer, the Android SDK (platform 35, build-tools 35), and Python 3.9 or newer.

```bash
python3 tools/build_data.py          # downloads the sources once (cached in tools/.cache), builds the databases
./gradlew :app:assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

* The Gradle `bibleData` task runs `tools/build_data.py` automatically when the databases are missing.
* The generated databases are not committed (about 120 MB). They are rebuilt from the sources.
* **Release signing.** Pass `-Pbaiboly.storeFile=… -Pbaiboly.storePassword=… -Pbaiboly.keyAlias=… -Pbaiboly.keyPassword=…`. Without these, the release APK is signed with the debug key so it can be installed for testing.

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
| 1 | Project and silence | The manifest has no INTERNET or VIBRATE permission. `check_silence.sh` runs before every build and fails it on any audio, vibration, haptic or sound-effect API, or on those permissions, including in merged manifests. Compose haptics are replaced by a no-op. |
| 2 | Text pipeline | `DataAcceptanceTest.task2_textPipeline`: Jaona 3:16, Salamo 1, 66 books, every translation |
| 3 | Parser | `ReferenceParserTest` (15 tests) |
| 4–9 | Reader, list strip, Add (4 taps), Browse and Back to my place, persistence, five-verse dry run | `UiAcceptanceTest.serviceDryRun` |
| 10 | Menu (3 taps), History, search (`masina` → `masìna`) | `UiAcceptanceTest.menuNotesHighlights`, `DataAcceptanceTest.task10_searchIgnoresAccents` |
| 11 | Notes and highlights survive restart; tapping an entry opens it | `UiAcceptanceTest.menuNotesHighlights` |
| 12 | Backup and restore reproduce data exactly | `DataAcceptanceTest.task12_backupRoundTrip` |
| 13 | Study pipeline: 2 Tim 2:25 → *acknowledging* = G1922 ἐπίγνωσις; gap report | `DataAcceptanceTest.task13_studyData`, [docs/reports/lexicon_gaps.md](docs/reports/lexicon_gaps.md) |
| 14 | α panel, entry, Found N verses → Malagasy text with Back to my place | `UiAcceptanceTest.studyPanel` |
| — | Malagasy interface by default; switch, live change, kept after restart | `FreshInstallTest`, `UiAcceptanceTest.malagasyInterface` |
| — | Default Bible in one tap; each text's own chapter numbering (KJV Malachi 4) | `UiAcceptanceTest.defaultBibleAndOwnChapterNumbering` |
| 15 | Offline, dark mode, size report | No network permission at all; [docs/size-report.md](docs/size-report.md) |

**Not verified here:** no Android emulator could run in the build environment (no hardware virtualisation). The UI scenarios run under Robolectric on the JVM, not on a phone, and touch-target size and low-end-device performance still need a check on a real device.

## Decisions taken (the user can revisit them)

* **Browse drawer stays open after Add to list**, with a Done button (the assumption in section 7).
* **Add panel also stays open after Add**, showing "Added: …", so several verses can be captured in a row.
* **Swipe while away from the list.** When the reader is away from the current list item, the first swipe returns to it instead of skipping an item.
* **Selecting text.** Tapping a verse (text or number) selects it; adjacent verses merge. "Select words" picks a character range inside one verse. With α on, the verse number opens the study panel instead.
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
