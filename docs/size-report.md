# APK size report

## Release APK

Release build (`./gradlew :app:assembleRelease`, R8 minified, resources shrunk).

| | v1.11.0 (everything bundled) | Now (optional downloads) |
|---|---|---|
| **Whole release APK** | **117.9 MB** | **≈ 20.5 MB (estimate; to be measured)** |

What the release APK carries now (compressed, as measured in the v1.11.0 APK):

| Part | In the APK | On the device once unpacked |
|---|---|---|
| `bible.db`: catalogue, Malagasy 1865, search index | 4.2 MB | 13.7 MB |
| `study.db`: KJV Strong's tagging, lexicon, verse mapping | 5.5 MB | 15.5 MB |
| `library.db`: cross-references, Easton/Nave/Martin, places, people, timeline, confessions | 6.0 MB | 19.6 MB |
| App code, resources and fonts (Gentium Book Plus, Cormorant SC), map | ≈ 4.8 MB | |

## Optional downloads

Data release `data-13`. Each download is a gzipped database; the size is what the phone downloads.

| Download | Download size | On the phone |
|---|---|---|
| Each of the 12 other reading texts | 3.9–5.5 MB | 12–16 MB |
| All 12 texts | 53.2 MB | 166 MB |
| Hebrew and Greek text (`original.db`, STEPBible) | 14.4 MB | 45.5 MB |
| Calvin's Commentaries | 12.8 MB | 42.9 MB |
| Matthew Henry, Complete | 13.6 MB | 42.1 MB |
| Geneva Bible notes | 1.8 MB | 5.1 MB |
| Matthew Henry, Concise | 1.5 MB | 4.9 MB |
| **All downloads** | **97.1 MB** | |

## Notes

* **Updates are small now.** The in-app updater downloads the whole APK, so an update now costs about 20 MB instead of 118 MB. Downloaded packs are kept across updates and are downloaded again only when their `PACK_VERSION` changes (see [RELEASING.md](RELEASING.md)).
* **Device storage.** A phone that reads only Malagasy stores about 49 MB once every bundled database is unpacked: `bible.db` at first launch, `study.db` and `library.db` when first used.
* **Why `study.db` and `library.db` stay in the APK.** They hold the verse mapping (Quick look and comparisons), the cross-references, the map, the dictionaries, people, timeline and confessions. Together they cost 11.5 MB of the APK. The smallest possible APK (bible.db only) would be about 9 MB, but every study tool would then need a download first.
* **Ways to shrink further, if needed:**
  * drop the prebuilt search index of the downloaded texts (about 1.8 MB each, compressed) and build it on the device;
  * drop `strongs_verse` in favour of an index on `kjv_word`.
