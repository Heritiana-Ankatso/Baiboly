#!/usr/bin/env python3
"""
Optional downloads ("packs"): the reading texts other than Malagasy 1865, the
Hebrew and Greek text (original.db) and the commentaries are not shipped in
the release APK. They are published once per data set as release assets of a
GitHub pre-release tagged data-<PACK_VERSION>, gzipped, and the app downloads
one only when the user taps it.

The APK carries packs.json (assets/databases/packs.json): for every pack its
file, size, SHA-256 of the .gz and of the unpacked database, version and URL.
The checksums are pinned inside the signed APK, so a changed or damaged file
on the server is refused by the app.

    python3 tools/packs.py make            # called at the end of build_data.py
    python3 tools/packs.py tag             # prints the data release tag (data-13)
    python3 tools/packs.py adopt DIR       # release: use the files already published in DIR

make
    gzips every pack into build/packs/ (with packs.json), moves the unpacked
    databases from app/src/main/assets/databases/ to app/src/debug/assets/databases/
    (debug builds and the Robolectric tests keep every database inside the APK;
    release builds do not), writes packs.json next to manifest.json and removes
    the packs from manifest.json's list of bundled files.
adopt DIR
    DIR holds the assets of the published data release (gh release download).
    Checks every .gz against the published packs.json, unpacks them into the
    debug assets (so the tests run on exactly the bytes users will download)
    and bundles the published packs.json in the APK.

Versioning
    PACK_VERSION is written into each pack's meta.data_version. It is
    independent of DATA_VERSION (bible.db, study.db, library.db), so a change to
    the bundled databases never makes phones download their texts again.
    Bump PACK_VERSION when the content or schema of a pack changes: the next
    release then publishes data-<new version>. MIN_PACK_VERSION is the oldest
    pack this app can still read; a phone with an older file is asked to
    download it again, a phone with a file between the two keeps using it and
    is offered the update.
    Never delete a data release: older versions of the app download from it.
"""
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "databases")
DEBUG_ASSETS = os.path.join(ROOT, "app", "src", "debug", "assets", "databases")
OUT = os.path.join(ROOT, "build", "packs")
REPO = "Heritiana-Ankatso/Baiboly"

PACK_VERSION = 13
MIN_PACK_VERSION = 13
TAG = f"data-{PACK_VERSION}"
URL = f"https://github.com/{REPO}/releases/download/{TAG}/{{}}"

COMMENTARY_MODULES = {"calvin": "CalvinCommentaries", "geneva": "Geneva", "mhcc": "MHCC", "mhc": "MHC"}


def is_pack(name):
    return name.endswith(".db") and (name.startswith("text-") or name.startswith("commentary-") or name == "original.db")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def meta(path):
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as db:
        return dict(db.execute("SELECT key, value FROM meta"))


def describe(name, path):
    """Kind, id, title and licence of a pack, for packs.json and the Downloads page."""
    m = meta(path)
    licences = []
    lic_path = os.path.join(os.path.dirname(ASSETS), "licences.json")
    if os.path.exists(lic_path):
        with open(lic_path, encoding="utf-8") as f:
            licences = json.load(f)

    def licence_of(source_part):
        for l in licences:
            if source_part in l.get("source", ""):
                return l.get("licence", "")
        return ""

    if name.startswith("text-"):
        tid = m.get("translation") or name[5:-3]
        with sqlite3.connect(f"file:{os.path.join(ASSETS, 'bible.db')}?mode=ro", uri=True) as db:
            row = db.execute("SELECT name, licence FROM translation WHERE id=?", (tid,)).fetchone()
        return "text", tid, row[0] if row else tid, row[1] if row else ""
    if name.startswith("commentary-"):
        cid = m.get("id") or name[11:-3]
        return "commentary", cid, m.get("title", cid), licence_of("module " + COMMENTARY_MODULES.get(cid, cid))
    return "original", "original", "Hebrew and Greek text (STEPBible)", licence_of("STEPBible")


def read_manifest():
    with open(os.path.join(ASSETS, "manifest.json")) as f:
        return json.load(f)


def write_manifest(m):
    with open(os.path.join(ASSETS, "manifest.json"), "w") as f:
        json.dump(m, f)


def write_packs_json(packs, *dirs):
    doc = {"set": TAG, "version": PACK_VERSION, "min_version": MIN_PACK_VERSION, "packs": packs}
    for d in dirs:
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "packs.json"), "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, indent=1, sort_keys=True)


def make():
    os.makedirs(DEBUG_ASSETS, exist_ok=True)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    # a pack built now lands in the main assets; an earlier run may already have moved it
    for name in sorted(os.listdir(ASSETS)):
        if is_pack(name):
            shutil.move(os.path.join(ASSETS, name), os.path.join(DEBUG_ASSETS, name))
    packs = []
    for name in sorted(os.listdir(DEBUG_ASSETS)):
        if not is_pack(name):
            continue
        path = os.path.join(DEBUG_ASSETS, name)
        version = int(meta(path).get("data_version", "-1"))
        if version != PACK_VERSION:
            sys.exit(f"{name}: meta.data_version is {version}, expected PACK_VERSION {PACK_VERSION}")
        gz = os.path.join(OUT, name + ".gz")
        with open(path, "rb") as src, open(gz, "wb") as raw:
            # mtime=0 and no file name: the same database always gives the same .gz
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, compresslevel=9, mtime=0) as out:
                shutil.copyfileobj(src, out, 1 << 20)
        kind, pid, title, licence = describe(name, path)
        packs.append({
            "file": name, "kind": kind, "id": pid, "title": title, "licence": licence,
            "version": version, "size": os.path.getsize(path), "sha256": sha256(path),
            "gz_size": os.path.getsize(gz), "gz_sha256": sha256(gz), "url": URL.format(name + ".gz"),
        })
    m = read_manifest()
    m["files"] = sorted(f for f in m["files"] if not is_pack(f))
    write_manifest(m)
    write_packs_json(packs, ASSETS, OUT)
    total = sum(p["gz_size"] for p in packs)
    print(f"  packs: {len(packs)} files, {total / 1e6:.1f} MB to download in all ({TAG})")
    for p in packs:
        print(f"    {p['file']:26s} {p['gz_size'] / 1e6:6.2f} MB download, {p['size'] / 1e6:6.2f} MB unpacked")


def adopt(directory):
    with open(os.path.join(directory, "packs.json"), encoding="utf-8") as f:
        doc = json.load(f)
    if doc.get("set") != TAG or doc.get("version") != PACK_VERSION:
        sys.exit(f"published packs.json is {doc.get('set')} v{doc.get('version')}, this build expects {TAG}")
    os.makedirs(DEBUG_ASSETS, exist_ok=True)
    for p in doc["packs"]:
        gz = os.path.join(directory, p["file"] + ".gz")
        if sha256(gz) != p["gz_sha256"]:
            sys.exit(f"{p['file']}.gz does not match the published SHA-256")
        target = os.path.join(DEBUG_ASSETS, p["file"])
        with gzip.open(gz, "rb") as src, open(target, "wb") as out:
            shutil.copyfileobj(src, out, 1 << 20)
        if sha256(target) != p["sha256"]:
            sys.exit(f"{p['file']}: unpacked database does not match the published SHA-256")
        if not p["url"].startswith("https://"):
            sys.exit(f"{p['file']}: URL is not HTTPS")
    # a pack built locally but not published must not stay in the debug assets
    published = {p["file"] for p in doc["packs"]}
    for name in os.listdir(DEBUG_ASSETS):
        if is_pack(name) and name not in published:
            os.remove(os.path.join(DEBUG_ASSETS, name))
    m = read_manifest()
    m["files"] = sorted(f for f in m["files"] if not is_pack(f))
    write_manifest(m)
    shutil.copyfile(os.path.join(directory, "packs.json"), os.path.join(ASSETS, "packs.json"))
    print(f"  packs: adopted {len(doc['packs'])} published files of {TAG}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "make":
        make()
    elif cmd == "tag":
        print(TAG)
    elif cmd == "adopt" and len(sys.argv) == 3:
        adopt(sys.argv[2])
    else:
        sys.exit(__doc__)
