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
3. Commit, then tag and push: `git tag v1.3.0 && git push origin v1.3.0`.
4. The *Release* workflow builds, runs every test, signs, refuses a debug-signed APK, and publishes *Baiboly v1.3.0* with the APK and its SHA-256.

## What an update keeps

* `user.db` (lists, notes, highlights, history) is never replaced; schema changes go through numbered steps in `UserStore.onUpgrade`.
* Settings (SharedPreferences) are kept.
* Bundled Bible/study databases are replaced when their `DATA_VERSION` changes; files no longer shipped are deleted.
* After an update the app shows "What's new" once.

## Local signed build

Create `keystore.properties` at the repository root (ignored by git):

```
BAIBOLY_KEYSTORE=/path/to/baiboly-release.p12
BAIBOLY_KEYSTORE_PASSWORD=...
BAIBOLY_KEY_ALIAS=baiboly
BAIBOLY_KEY_PASSWORD=...
```
