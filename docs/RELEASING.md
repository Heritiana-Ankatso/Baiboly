# Releasing Baiboly

Releases are built and signed by GitHub Actions and published as **GitHub Releases** (the APK is attached to the release; it is not committed to the repository).

## One-time setup: the signing key

Android installs an update only if it is signed with the **same key** as the installed app. Baiboly has one permanent release key (`baiboly-release.p12`, alias `baiboly`), kept by the project owner and **never committed** (`.gitignore` blocks `*.p12`, `*.jks`, `*.keystore`, `keystore.properties`). Losing it means phones would have to uninstall to update; keep two private copies.

In the repository: *Settings › Secrets and variables › Actions › New repository secret*, add:

| Secret | Value |
|---|---|
| `BAIBOLY_KEYSTORE_BASE64` | content of `baiboly-release.p12.base64` |
| `BAIBOLY_KEYSTORE_PASSWORD` | from `baiboly-release-passwords.txt` |
| `BAIBOLY_KEY_ALIAS` | `baiboly` |
| `BAIBOLY_KEY_PASSWORD` | from `baiboly-release-passwords.txt` |

Secrets stay private when the repository is public, and are not given to pull requests from forks.

## Each release

1. Set the version in `gradle.properties`: `baiboly.versionName=1.3.0` (the versionCode is the commit count, so it always rises).
2. Update the "What's new" texts: `app/src/main/res/values*/strings.xml` (`whats_new_items`) and `docs/release-notes.md`.
3. Merge to `main`, then either press **Run workflow** under *Actions › Release* (it releases the version in `gradle.properties` from `main` and creates the tag), or tag and push yourself: `git tag v1.3.0 && git push origin v1.3.0`.
4. The *Release* workflow builds, runs every test, signs, refuses a debug-signed APK, and publishes *Baiboly v1.3.0* with the APK and its SHA-256. If the data release for the current `PACK_VERSION` does not exist yet, it publishes it first (see below).

## Optional downloads (the data release)

The release APK carries only `bible.db` (catalogue and Malagasy 1865), `study.db`, `library.db`, the map and the fonts (about 20 MB). The other Bibles, the Hebrew and Greek text (`original.db`) and the commentaries are **packs** that the user downloads in the app, on a tap.

* **Where they are.** Release assets of a GitHub **pre-release** tagged `data-<PACK_VERSION>` (for example `data-13`): one gzipped database per pack, plus `packs.json`. A pre-release is never GitHub's "latest release", so the app's update check never takes it for an app update.
* **How the app finds them.** `tools/packs.py` writes `packs.json` (file, size, SHA-256 of the `.gz` and of the database, version, URL) into `app/src/main/assets/databases/`, so every APK carries the list and the checksums of the data it can download. A file that does not match is refused.
* **The Release workflow:**
  * If `data-<PACK_VERSION>` already exists, it downloads it, checks it (`tools/packs.py adopt`) and builds the APK and runs the tests on exactly those files.
  * Otherwise it builds the packs, runs the tests, and publishes the data release before the app release.
  * It refuses a release APK that still contains a pack.
* **Changing a pack** (new source text, schema change): bump `PACK_VERSION` in `tools/packs.py`. The next release then publishes `data-<new>`. Phones keep their files and are offered *Update* until they download the new one. A file older than `MIN_PACK_VERSION` is deleted and must be downloaded again, so raise `MIN_PACK_VERSION` only when the app can no longer read the old schema.
* **Changing only `bible.db`, `study.db` or `library.db`:** bump `DATA_VERSION` as before. Packs are not affected and nobody downloads anything again.
* **Never delete a data release.** Older versions of the app download from it.
* **Debug builds** (and the tests) still carry every pack inside the APK (`app/src/debug/assets/databases/`), so nothing needs downloading while developing.

## What an update keeps

* `user.db` (lists, notes, highlights, history) is never replaced; schema changes go through numbered steps in `UserStore.onUpgrade`.
* Settings (SharedPreferences) are kept.
* Bundled databases (`bible.db`, `study.db`, `library.db`) are replaced when their `DATA_VERSION` changes; files no longer shipped or offered are deleted.
* Downloaded Bibles, the Hebrew and Greek text and commentaries are kept. Updating from 1.11.0 or earlier, when everything was bundled, keeps every text the phone had already opened; a text never opened before has to be downloaded once.
* After an update the app shows "What's new" once.

## How phones learn of a new release

The app's update check (Settings › Updates, off by default) reads GitHub's *latest release* for this repository and compares its tag with the installed `versionName`. So:

* Tag every release `vX.Y.Z` with the same number as `baiboly.versionName` (the Release workflow does this).
* A draft or a pre-release is not "latest": phones are told of a version only once it is published as a full release.

## Local signed build

Create `keystore.properties` at the repository root (ignored by git):

```
BAIBOLY_KEYSTORE=/path/to/baiboly-release.p12
BAIBOLY_KEYSTORE_PASSWORD=...
BAIBOLY_KEY_ALIAS=baiboly
BAIBOLY_KEY_PASSWORD=...
```
