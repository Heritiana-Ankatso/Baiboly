"""
Bible commentaries for the Study panel (tools/build_data.py calls build_commentaries()).

Reformed commentaries in the public domain, from CrossWire SWORD modules (zCom):
  * Geneva Bible Translation Notes (1599): the notes of the Genevan exiles' Bible.
  * Calvin's Collected Commentaries (Calvin Translation Society, 1840s-1850s).
  * Matthew Henry's Concise Commentary (1706-1721; abridged edition).
  * Matthew Henry's Complete Commentary on the Whole Bible (1706-1721; Romans to Revelation completed by
    other nonconformist ministers after his death in 1714).

Each becomes its own database, commentary-<id>.db, unpacked on the phone only when first opened:
    comment(book, chapter, v1, v2, body)   -- verses in Malagasy 1865 numbering
Bodies use the library markup: paragraphs separated by a blank line, Bible references as
⟦Osis.Ref|label⟧ (KJV numbering, as LibraryRepository.osisToPassage reads them).
"""
import html
import os
import re
import sqlite3
import struct
import zipfile
import zlib

# id, CrossWire module, title, author, year, tradition ("reformed" for all three)
COMMENTARIES = [
    ("geneva", "Geneva", "Geneva Bible notes", "Translators of the Geneva Bible", "1599", "reformed"),
    ("calvin", "CalvinCommentaries", "Calvin’s Commentaries", "John Calvin", "1540–1564", "reformed"),
    ("mhcc", "MHCC", "Matthew Henry’s Concise Commentary", "Matthew Henry", "1706–1721", "reformed"),
    ("mhc", "MHC", "Matthew Henry’s Complete Commentary", "Matthew Henry (Romans to Revelation completed by other ministers)", "1706–1721", "reformed"),
]


def _entries(zf, data_path, canon, osis_ids, wide=False):
    """(book, chapter, verse, key, raw): key identifies the stored text, shared by linked verses."""
    data_path = data_path.lstrip("./").rstrip("/") + "/"
    for t in ("ot", "nt"):
        try:
            bzs, bzv, bzz = (zf.read(data_path + t + ext) for ext in (".bzs", ".bzv", ".bzz"))
        except KeyError:
            continue
        cache = {}

        def entry(i):
            # zCom: 4+4+2 bytes per verse; zCom4 (the complete Henry): 4+4+4
            b, o, s = struct.unpack("<III", bzv[i * 12:i * 12 + 12]) if wide else struct.unpack("<IIH", bzv[i * 10:i * 10 + 10])
            if s == 0:
                return None, ""
            if b not in cache:
                bo, bs, _ = struct.unpack("<III", bzs[b * 12:b * 12 + 12])
                cache[b] = zlib.decompress(bzz[bo:bo + bs])
            return (b, o, s), cache[b][o:o + s].decode("utf-8", "replace")

        i = 2
        for (_name, osis, counts) in canon[t]:
            i += 1
            for ch, n in enumerate(counts, start=1):
                i += 1
                for v in range(1, n + 1):
                    key, raw = entry(i)
                    if osis in osis_ids:
                        yield osis_ids[osis], ch, v, key, raw
                    i += 1


def _ref(osis_attr, label):
    refs = [r.split(":", 1)[-1] for r in osis_attr.split() if r]
    if not refs:
        return label
    first, last = refs[0].split("-")[0], refs[-1].split("-")[-1]
    target = first if first == last else f"{first}-{last}"
    return f"⟦{target}|{label}⟧"


def clean(raw):
    """OSIS or ThML -> library markup."""
    s = raw
    s = re.sub(r"<note\b.*?</note>", "", s, flags=re.S)                      # editors' footnotes
    s = re.sub(r'<reference[^>]*osisRef="([^"]+)"[^>]*>(.*?)</reference>',
               lambda m: _ref(m.group(1), re.sub(r"<[^>]+>", "", m.group(2))), s, flags=re.S)
    s = re.sub(r'<scripRef[^>]*osisRef="([^"]+)"[^>]*>(.*?)</scripRef>',
               lambda m: _ref(m.group(1), re.sub(r"<[^>]+>", "", m.group(2))), s, flags=re.S)
    s = re.sub(r"<br\s*/?>", "\n\n", s)
    s = re.sub(r'<div[^>]*type="x-p"[^>]*/>', "\n\n", s)
    s = re.sub(r"</?p\b[^>]*>", "\n\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = html.unescape(s)
    s = re.sub(r"[ \t\r\f\v]+", " ", s)
    s = re.sub(r" *\n *", "\n", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


_VERSES = re.compile(r"^Verses? (\d+)(?:\s*[-–]\s*(\d+))?\s*$", re.M)


def _split_verse_headings(rows):
    """
    Matthew Henry's Concise files some notes under the wrong verses: the entry for John 3:1-8 also holds
    the note headed "Verses 9-21", and 9-21 are empty. A note with several "Verses N-M" headings is
    split at them, each part on the verses its heading names.
    """
    out = []
    for book, ch, v1, v2, body in rows:
        heads = list(_VERSES.finditer(body))
        if len(heads) < 2:
            out.append([book, ch, v1, v2, body])
            continue
        if heads[0].start() > 0 and body[:heads[0].start()].strip():
            out.append([book, ch, v1, v1, body[:heads[0].start()].strip()])
        for i, m in enumerate(heads):
            end = heads[i + 1].start() if i + 1 < len(heads) else len(body)
            a = int(m.group(1)); b = int(m.group(2) or a)
            out.append([book, ch, a, max(a, b), body[m.start():end].strip()])
    return out


def build_commentaries(fetch, crosswire_url, conf_of, check_crosswire, canons, osis_ids, numbering, assets, data_version,
                       licences, offline):
    """Builds commentary-<id>.db for each commentary; returns {file name: size}."""
    sizes = {}
    for cid, mod, title, author, year, tradition in COMMENTARIES:
        path = fetch(crosswire_url.format(mod), mod + ".zip", offline)
        zf = zipfile.ZipFile(path)
        conf = conf_of(zf)
        if mod == "Geneva" and not conf.get("DistributionLicense"):
            # The 1599 notes are long out of copyright; the module's .conf names no licence, so say so plainly.
            if "Geneva Bible Translation Notes" not in conf.get("Description", ""):
                raise SystemExit("LICENCE CHECK FAILED: Geneva module changed")
            licences.append((mod, conf["Description"], "CrossWire SWORD module " + mod, crosswire_url.format(mod),
                             "Public domain by age (notes first printed 1560-1599); the module's .conf states no licence",
                             "CrossWire Bible Society"))
        else:
            check_crosswire(mod, conf)
        v11n = conf.get("Versification", "KJV").lower()
        rows = []
        last_key, start = None, None
        for book, ch, v, key, raw in _entries(zf, conf["DataPath"], canons[v11n], osis_ids, conf.get("ModDrv") == "zCom4"):
            if key is not None and key == last_key and start and start[0] == book and start[1] == ch:
                rows[-1][3] = v          # linked verse: the same note runs on
                continue
            last_key = key
            if not raw.strip():
                # Matthew Henry's Concise comments on whole passages: the empty verses after a note belong to it
                # (John 3:9-21 are empty; the note filed at 3:1-8 runs on to verse 21).
                if cid == "mhcc" and start and start[0] == book and start[1] == ch:
                    rows[-1][3] = v
                    continue
                start = None
                continue
            body = clean(raw)
            if not body:
                start = None
                continue
            start = (book, ch)
            rows.append([book, ch, v, v, body])
        rows = _split_verse_headings(rows)
        out = os.path.join(assets, f"commentary-{cid}.db")
        if os.path.exists(out):
            os.remove(out)
        db = sqlite3.connect(out)
        db.executescript("""
            CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
            -- one note on verses v1..v2 of a chapter (Malagasy 1865 numbering)
            CREATE TABLE comment(book INTEGER NOT NULL, chapter INTEGER NOT NULL, v1 INTEGER NOT NULL, v2 INTEGER NOT NULL, body TEXT NOT NULL);
            CREATE INDEX comment_by_chapter ON comment(book, chapter);
        """)
        for book, ch, v1, v2, body in rows:
            c1, m1 = numbering.to_mg(book, ch, v1)
            _, m2 = numbering.to_mg(book, ch, v2)
            db.execute("INSERT INTO comment VALUES(?,?,?,?,?)", (book, c1, m1, max(m1, m2), body))
        for k, val in (("data_version", str(data_version)), ("id", cid), ("title", title), ("author", author),
                       ("year", year), ("tradition", tradition)):
            db.execute("INSERT INTO meta VALUES(?,?)", (k, val))
        db.commit()
        db.execute("VACUUM")
        db.close()
        sizes[f"commentary-{cid}.db"] = os.path.getsize(out)
        print(f"  commentary {cid}: {len(rows)} notes, {sizes[f'commentary-{cid}.db'] / 1e6:.1f} MB")
    return sizes
