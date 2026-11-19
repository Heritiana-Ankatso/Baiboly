# APK size report

Release build (`./gradlew :app:assembleRelease`, R8 minified, resources shrunk), measured on 2026-11-19.

| Part | In the APK (compressed) | On the device once unpacked |
|---|---|---|
| **Whole release APK** | **45.1 MB** | |
| App code (`classes.dex`) | 1.1 MB | |
| `bible.db`: catalogue + Malagasy 1865 + search index | 4.2 MB | 13.7 MB |
| 8 other reading texts (`text-*.db`), about 4–4.6 MB each | 34.0 MB | 12–13.5 MB each |
| **Study data (`study.db`)** | **5.5 MB** | **15.5 MB** |

What the study data adds: **5.5 MB to the APK** and 15.5 MB on the device, once the
α panel is first used. Inside `study.db`: `kjv_word` (348,884 tagged words),
`strongs_verse` (310,076 rows), `lexicon` (14,375 entries: public-domain CrossWire base plus Open Scriptures Hebrew lemmas and missing entries) and `verse_map`.

Notes

* The 25 MB soft target was lifted by the user. Without the 8 extra translations the APK
  would be about 11 MB.
* Device storage: `bible.db` is unpacked on first launch. Each other translation is
  unpacked only when it is first opened, and `study.db` only when α is first used. On a
  phone that reads only Malagasy, storage use is about 14 MB.
* Ways to shrink later, if needed: drop the prebuilt search index of the extra
  translations (about 1.8 MB each, compressed) and build it on the device; drop
  `strongs_verse` in favour of an index on `kjv_word`; ship rarely used translations as
  optional downloads (this needs the INTERNET permission, which is the user's decision).
