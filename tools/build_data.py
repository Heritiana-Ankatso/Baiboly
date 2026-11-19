#!/usr/bin/env python3
"""
Baiboly data pipeline (brief tasks 2 and 13).

Converts the public-domain sources into the two read-only SQLite databases
bundled in the APK, plus a generated Kotlin canon table and written reports.

    python3 tools/build_data.py            # download (cached) + build everything
    python3 tools/build_data.py --offline  # use tools/.cache only

Outputs
    app/src/main/assets/databases/bible.db   catalogue + Malagasy 1865 (+ search index)
    app/src/main/assets/databases/text-*.db  one file per other reading translation
    app/src/main/assets/databases/study.db   KJV Strong's tagging + lexicon
    core/src/main/kotlin/mg/baiboly/core/bible/CanonData.kt
    docs/data-licences.md, docs/reports/*.md

Every source passes a licence check before it is used: the build stops if a
source is not explicitly marked public domain by its publisher.
"""
import argparse
import csv
import html
import io
import json
import os
import re
import sqlite3
import struct
import sys
import unicodedata
import urllib.request
import zipfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "vendor"))
from sword_canons import CANONS  # noqa: E402  (MIT, extracted from PySword)

CACHE = os.path.join(ROOT, "tools", ".cache")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "databases")
DOCS = os.path.join(ROOT, "docs")
REPORTS = os.path.join(DOCS, "reports")
ABBR_TSV = os.path.join(ROOT, "tools", "abbreviations.tsv")
CANON_KT = os.path.join(ROOT, "core", "src", "main", "kotlin", "mg", "baiboly", "core", "bible", "CanonData.kt")

CROSSWIRE = "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/{}.zip"
EBIBLE = "https://ebible.org/Scriptures/{}"

# Bump when the schema or content of bible.db / study.db changes, so the app
# replaces its installed copy.
DATA_VERSION = 8

# ---------------------------------------------------------------------------
# Translations. `num` is a stable small integer used to build verse ids:
#   id = num * 100_000_000 + book * 1_000_000 + chapter * 1_000 + verse
# ---------------------------------------------------------------------------
TRANSLATIONS = [
    # id, num, lang, short, name, style, source kind, source id
    ("mg1865", 1, "mg", "MG1865", "Baiboly Malagasy 1865", "classic", "crosswire", "Mg1865"),
    ("tdx", 10, "mg", "TDX", "Saontsin’ Añahare (Tandroy)", "regional", "ebible", "tdx"),
    ("lsg1910", 2, "fr", "LSG", "Louis Segond 1910", "classic", "ebible", "fraLSG"),
    ("darby_fr", 3, "fr", "JND", "Bible J.N. Darby (français)", "literal", "ebible", "frajnd"),
    ("ncl_fr", 9, "fr", "NCL", "Sainte Bible néo-Crampon Libre", "faithful-modern", "ebible", "francl"),
    ("crampon", 11, "fr", "CRA", "Bible Crampon 1923", "classic", "crosswire", "FreCrampon"),
    ("kjv", 5, "en", "KJV", "King James Version", "literal", "ebible", "eng-kjv2006"),
    ("bsb", 6, "en", "BSB", "Berean Standard Bible", "faithful-modern", "ebible", "engbsb"),
    ("lsv", 14, "en", "LSV", "Literal Standard Version", "literal", "ebible", "englsv"),
    ("web", 7, "en", "WEB", "World English Bible", "accessible", "ebible", "eng-web"),
    ("bbe", 8, "en", "BBE", "Bible in Basic English", "simplified", "ebible", "engBBE"),
    ("webc", 12, "en", "WEBC", "World English Bible, Catholic Edition", "accessible", "ebible", "eng-web-c"),
    ("dra", 13, "en", "DRA", "Douay-Rheims 1899", "classic", "ebible", "engDRA"),
]

# Catholic Bibles: they keep the deuterocanonical books (ids 67-73) and the
# Greek additions to Esther and Daniel. All other texts keep the 66 books.
CATHOLIC = {"ncl_fr", "crampon", "webc", "dra"}

# Deuterocanonical books, numbered after the 66: 67 Tobit, 68 Judith,
# 69 1 Maccabees, 70 2 Maccabees, 71 Wisdom, 72 Sirach, 73 Baruch.
DEUTERO = [  # (id, osis, Malagasy (Catholic usage), French, English, vpl code)
    (67, "Tob", "Tobia", "Tobie", "Tobit", "TOB"),
    (68, "Jdt", "Jodita", "Judith", "Judith", "JDT"),
    (69, "1Macc", "1 Makabeo", "1 Maccabées", "1 Maccabees", "1MA"),
    (70, "2Macc", "2 Makabeo", "2 Maccabées", "2 Maccabees", "2MA"),
    (71, "Wis", "Fahendrena", "Sagesse", "Wisdom", "WIS"),
    (72, "Sir", "Eklesiastika", "Siracide", "Sirach", "SIR"),
    (73, "Bar", "Baroka", "Baruch", "Baruch", "BAR"),
]
BOOK_COUNT = 73

# eBible texts that are not public domain but whose open licence the user has
# accepted. The licence text on the publisher's copyright page is checked at
# every build.
OPEN_LICENCES = {
    "francl": ("Creative Commons Attribution Share-Alike license 4.0", "CC BY-SA 4.0, © 2022 Fraternité de Tibériade"),
    "tdx": ("Creative Commons Attribution Share-Alike license 4.0", "CC BY-SA 4.0, © 2023 Steve Lellelid"),
    "englsv": ("Creative Commons Attribution Share-Alike license 4.0", "CC BY-SA 4.0, © 2020 Covenant Press"),
}
STYLE_NOTE = {
    "classic": "Classic church translation",
    "literal": "Word-for-word, close to the original",
    "faithful-modern": "Modern language, close to the original",
    "regional": "Regional Malagasy (Tandroy-Mahafaly), draft",
    "accessible": "Modern, easy to read",
    "simplified": "Very simple vocabulary (850 basic words)",
}

VPL_CODES = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SOL ISA "
             "JER LAM EZE DAN HOS JOE AMO OBA JON MIC NAH HAB ZEP HAG ZEC MAL MAT MAR LUK JOH ACT ROM 1CO "
             "2CO GAL EPH PHI COL 1TH 2TH 1TI 2TI TIT PHM HEB JAM 1PE 2PE 1JO 2JO 3JO JUD REV").split()
USFM_CODES = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA "
              "JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO "
              "2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split()

NAMES_FR = ["Genèse", "Exode", "Lévitique", "Nombres", "Deutéronome", "Josué", "Juges", "Ruth", "1 Samuel",
            "2 Samuel", "1 Rois", "2 Rois", "1 Chroniques", "2 Chroniques", "Esdras", "Néhémie", "Esther", "Job",
            "Psaumes", "Proverbes", "Ecclésiaste", "Cantique des cantiques", "Ésaïe", "Jérémie", "Lamentations",
            "Ézéchiel", "Daniel", "Osée", "Joël", "Amos", "Abdias", "Jonas", "Michée", "Nahum", "Habacuc",
            "Sophonie", "Aggée", "Zacharie", "Malachie", "Matthieu", "Marc", "Luc", "Jean", "Actes", "Romains",
            "1 Corinthiens", "2 Corinthiens", "Galates", "Éphésiens", "Philippiens", "Colossiens",
            "1 Thessaloniciens", "2 Thessaloniciens", "1 Timothée", "2 Timothée", "Tite", "Philémon", "Hébreux",
            "Jacques", "1 Pierre", "2 Pierre", "1 Jean", "2 Jean", "3 Jean", "Jude", "Apocalypse"]
NAMES_EN = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel",
            "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job",
            "Psalms", "Proverbs", "Ecclesiastes", "Song of Songs", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel",
            "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah",
            "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
            "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
            "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter",
            "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"]

# Abbreviations taken from the user's own examples (brief section 3).
USER_ABBR = {43: "Jao", 49: "Efe", 19: "Sal", 54: "Tim", 55: "Tim"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def norm(s: str) -> str:
    """text_norm: lowercase, diacritics stripped, typographic apostrophes -> '.
    Must stay identical to mg.baiboly.core.text.TextNorm in Kotlin."""
    s = s.replace("\u2019", "'").replace("\u2018", "'").replace("\u02bc", "'")
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return unicodedata.normalize("NFC", s)


def fetch(url: str, name: str, offline: bool) -> str:
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        if offline:
            sys.exit(f"missing cached source {name} (run without --offline)")
        print(f"  downloading {url}")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; bible-data-build)"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path + ".part", "wb") as f:
            f.write(r.read())
        os.replace(path + ".part", path)
    return path


def collapse(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def verse_id(num, book, chapter, verse):
    return num * 100_000_000 + book * 1_000_000 + chapter * 1_000 + verse


# ---------------------------------------------------------------------------
# SWORD readers (zText bible, zLD and rawLD lexicons)
# ---------------------------------------------------------------------------
class ZText:
    def __init__(self, zf: zipfile.ZipFile, data_path: str, canon: dict):
        self.zf, self.canon = zf, canon
        self.data_path = data_path.lstrip("./").rstrip("/") + "/"
        self.tst = {}
        for t in ("ot", "nt"):
            self.tst[t] = dict(bzs=zf.read(self.data_path + t + ".bzs"), bzv=zf.read(self.data_path + t + ".bzv"),
                               bzz=zf.read(self.data_path + t + ".bzz"), cache={})

    def _entry(self, t, i):
        d = self.tst[t]
        b, o, s = struct.unpack("<IIH", d["bzv"][i * 10:i * 10 + 10])
        if s == 0:
            return ""
        if b not in d["cache"]:
            bo, bs, _ = struct.unpack("<III", d["bzs"][b * 12:b * 12 + 12])
            d["cache"][b] = zlib.decompress(d["bzz"][bo:bo + bs])
        return d["cache"][b][o:o + s].decode("utf-8")

    def iterate(self):
        """Yields (book 1..66, chapter, verse, raw) where verse 0 is a heading
        entry (book or chapter intro)."""
        for t in ("ot", "nt"):
            i = 2
            for (_name, osis, counts) in self.canon[t]:
                book_no = OSIS_IDS[osis]
                yield book_no, 0, 0, self._entry(t, i)
                i += 1
                for ch, n in enumerate(counts, start=1):
                    yield book_no, ch, 0, self._entry(t, i)
                    i += 1
                    for v in range(1, n + 1):
                        yield book_no, ch, v, self._entry(t, i)
                        i += 1


# OSIS book id -> app book id (1..66 in Protestant order, then 67..73)
OSIS_IDS = {b[1]: i + 1 for i, b in enumerate(CANONS["german"]["ot"] + CANONS["german"]["nt"])}
OSIS_IDS.update({d[1]: d[0] for d in DEUTERO})


def read_zld(zf, base):
    idx, dat = zf.read(base + ".idx"), zf.read(base + ".dat")
    zdx, zdt = zf.read(base + ".zdx"), zf.read(base + ".zdt")
    blocks = {}
    for i in range(len(idx) // 8):
        off, sz = struct.unpack("<II", idx[i * 8:i * 8 + 8])
        rec = dat[off:off + sz]
        k = rec.index(b"\n")
        key = rec[:k].strip().decode()
        blk, ent = struct.unpack("<II", rec[k + 1:k + 9])
        if blk not in blocks:
            bo, bs = struct.unpack("<II", zdx[blk * 8:blk * 8 + 8])
            blocks[blk] = zlib.decompress(zdt[bo:bo + bs])
        raw = blocks[blk]
        eo, es = struct.unpack("<II", raw[4 + ent * 8:12 + ent * 8])
        yield key, raw[eo:eo + es].decode("utf-8", "replace").rstrip("\x00")


def conf_of(zf):
    name = [n for n in zf.namelist() if n.startswith("mods.d/") and n.endswith(".conf")][0]
    conf = {}
    for line in zf.read(name).decode("utf-8", "replace").splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            conf.setdefault(k.strip(), v.strip())
    return conf


# ---------------------------------------------------------------------------
# Licence checks
# ---------------------------------------------------------------------------
LICENCES = []
OFFLINE = False
VMAP = {}  # Malagasy verse -> KJV (chapter, verse), where the numbering differs


def check_crosswire(mod, conf):
    lic = conf.get("DistributionLicense", "")
    if lic.lower() != "public domain":
        sys.exit(f"LICENCE CHECK FAILED: {mod} DistributionLicense={lic!r}")
    LICENCES.append((mod, conf.get("Description", mod), "CrossWire SWORD module " + mod,
                     CROSSWIRE.format(mod), "Public Domain (DistributionLicense in module .conf)",
                     conf.get("TextSource", "")))


def check_ebible(tid, catalogue):
    row = catalogue.get(tid)
    if not row:
        sys.exit(f"LICENCE CHECK FAILED: {tid} not in eBible catalogue")
    if row["Redistributable"] != "True":
        sys.exit(f"LICENCE CHECK FAILED: {tid} is not redistributable")
    if row["Copyright"].strip().lower() == "public domain":
        licence = "Public Domain (eBible.org catalogue + about page)"
    elif tid in OPEN_LICENCES:
        expected, licence = OPEN_LICENCES[tid]
        page = fetch(f"https://ebible.org/{tid}/copyright.htm", f"{tid}_copyright.htm", OFFLINE)
        text = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", open(page, encoding="utf-8-sig").read())))
        if expected not in text:
            sys.exit(f"LICENCE CHECK FAILED: {tid} copyright page no longer states {expected!r}")
        licence += " (licence text checked on the publisher's copyright page)"
    else:
        sys.exit(f"LICENCE CHECK FAILED: {tid} copyright={row['Copyright']!r}")
    LICENCES.append((tid, row["title"] or row["shortTitle"], "eBible.org " + tid,
                     EBIBLE.format(tid + "_vpl.zip"), licence, row.get("publicationURL", "")))
    return row


# ---------------------------------------------------------------------------
# Malagasy 1865 (CrossWire OSIS)
# ---------------------------------------------------------------------------
TITLE_RE = re.compile(r"<title([^>]*)>(.*?)</title>", re.S)


def strip_tags(s):
    return re.sub(r"<[^>]+>", "", s)


def clean_osis(raw):
    """Returns (verse_text, headings, has_canonical_title)."""
    raw = re.sub(r"<note\b[^>]*/>", "", raw)
    raw = re.sub(r"<note\b.*?</note>", "", raw, flags=re.S)
    headings, canonical = [], False

    def title(m):
        nonlocal canonical
        attrs, inner = m.group(1), m.group(2)
        if 'canonical="true"' in attrs:
            canonical = True
            return inner + " "
        t = collapse(html.unescape(strip_tags(inner)))
        if t:
            headings.append(t)
        return " "

    raw = TITLE_RE.sub(title, raw)
    text = collapse(html.unescape(strip_tags(raw)))
    return text, headings, canonical


def load_mg1865(offline):
    path = fetch(CROSSWIRE.format("Mg1865"), "Mg1865.zip", offline)
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    check_crosswire("Mg1865", conf)
    assert conf.get("Versification") == "German", conf.get("Versification")
    zt = ZText(zf, conf["DataPath"], CANONS["german"])
    verses, headings, book_meta, canonical_titles = {}, [], {}, set()
    pending = []
    for book, ch, v, raw in zt.iterate():
        if ch == 0:  # book header: names come from the source (toc1/toc2/toc3)
            meta = dict(re.findall(r'<milestone n="([^"]*)" type="x-usfm-(toc\d|h)"/>', raw))
            meta = {t: n for n, t in re.findall(r'<milestone n="([^"]*)" type="x-usfm-(toc\d|h)"/>', raw)}
            m = re.search(r'<title type="main">(.*?)</title>', raw, re.S)
            meta["main"] = collapse(strip_tags(m.group(1))) if m else ""
            book_meta[book] = meta
            continue
        if v == 0:
            text, hs, _ = clean_osis(raw)
            pending.extend(hs)
            continue
        text, hs, canonical = clean_osis(raw)
        for h in pending + hs:
            headings.append((book, ch, v, h))
        pending = []
        if canonical:
            canonical_titles.add((book, ch, v))
        if text:
            verses[(book, ch, v)] = text
    return verses, headings, book_meta, canonical_titles


def load_crosswire_text(mod, offline):
    """A CrossWire zText Bible other than the Malagasy one (e.g. Crampon 1923)."""
    path = fetch(CROSSWIRE.format(mod), mod + ".zip", offline)
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    check_crosswire(mod, conf)
    v11n = conf.get("Versification", "KJV").lower()
    zt = ZText(zf, conf["DataPath"], CANONS[v11n])
    verses, headings, pending = {}, [], []
    for book, ch, v, raw in zt.iterate():
        if v == 0:
            if ch > 0:
                pending.extend(clean_osis(raw)[1])
            continue
        text, hs, _ = clean_osis(raw)
        for h in pending + hs:
            headings.append((book, ch, v, h))
        pending = []
        if text:
            verses[(book, ch, v)] = text
    return verses, headings


# ---------------------------------------------------------------------------
# eBible VPL texts
# ---------------------------------------------------------------------------
def load_vpl(tid, offline, catholic=False):
    path = fetch(EBIBLE.format(tid + "_vpl.zip"), tid + "_vpl.zip", offline)
    zf = zipfile.ZipFile(path)
    name = [n for n in zf.namelist() if n.endswith("_vpl.txt")][0]
    codes = {c: i + 1 for i, c in enumerate(VPL_CODES)}
    if catholic:
        codes.update({d[5]: d[0] for d in DEUTERO})
        # editions that give Esther and Daniel in their Greek form (with the additions)
        codes.update({"ESG": 17, "DAG": 27, "DNG": 27})
    verses = {}
    for line in zf.read(name).decode("utf-8-sig").splitlines():
        m = re.match(r"^(\S+) (\d+):(\d+) ?(.*)$", line)
        if not m or m.group(1) not in codes:
            continue
        text = m.group(4)
        text = re.sub(r"[\[\]]", "", text)  # KJV italics brackets
        text = text.replace("\u00ad", "")  # soft hyphens break word search
        text = text.replace("¶", "")  # KJV paragraph marks
        text = collapse(text)
        if text:
            verses[(codes[m.group(1)], int(m.group(2)), int(m.group(3)))] = text
    return verses


# ---------------------------------------------------------------------------
# KJV Strong's tagging (eBible USFM)
# ---------------------------------------------------------------------------
W_RE = re.compile(r'\\\+?w ([^|\\]*)\|strong="([GH])0*(\d+)"\\\+?w\*')


def load_kjv_tagging(offline):
    path = fetch(EBIBLE.format("eng-kjv2006_usfm.zip"), "eng-kjv2006_usfm.zip", offline)
    zf = zipfile.ZipFile(path)
    codes = {c: i + 1 for i, c in enumerate(USFM_CODES)}
    words = {}  # (b,c,v) -> list of (word, strongs|None)
    for name in sorted(zf.namelist()):
        if not name.endswith(".usfm"):
            continue
        data = zf.read(name).decode("utf-8-sig")
        m = re.search(r"\\id (\w+)", data)
        if not m or m.group(1) not in codes:
            continue
        book = codes[m.group(1)]
        data = re.sub(r"\\f .*?\\f\*", "", data, flags=re.S)
        ch = 0
        for part in re.split(r"(\\c \d+|\\v \d+)", data):
            if part.startswith("\\c "):
                ch = int(part[3:])
                continue
            if part.startswith("\\v "):
                cur = (book, ch, int(part[3:]))
                words[cur] = []
                continue
            if ch == 0 or not words or cur[1] != ch:
                continue
            seq = words[cur]
            pos = 0
            for m in W_RE.finditer(part):
                for w in re.findall(r"[A-Za-z’'-]+", re.sub(r"\\\+?\w+\*?", " ", part[pos:m.start()])):
                    seq.append((w, None))
                seq.append((collapse(m.group(1)), m.group(2) + m.group(3)))
                pos = m.end()
            for w in re.findall(r"[A-Za-z’'-]+", re.sub(r"\\\+?\w+\*?", " ", part[pos:])):
                seq.append((w, None))
    return words


# ---------------------------------------------------------------------------
# Strong's lexicons (CrossWire, public domain)
# ---------------------------------------------------------------------------
POS_PATTERNS = [
    (r"\bprimitive root\b", "verb (primitive root)"),
    (r"\bprimary verb\b|\bprolonged form of a primary verb\b|\ba primitive verb\b", "verb"),
    (r"\bverb\b", "verb"),
    (r"\badverb\b|\badv\.", "adverb"),
    (r"\bpreposition\b|\bprep\.", "preposition"),
    (r"\bconjunction\b|\bconj\.", "conjunction"),
    (r"\binterjection\b", "interjection"),
    (r"\bparticle\b", "particle"),
    (r"\bpronoun\b|\bpron\.", "pronoun"),
    (r"\badjective\b|\badj\.", "adjective"),
    (r"\bmasculine noun\b|\bfeminine noun\b|\bneuter noun\b|\bnoun\b", "noun"),
    (r"\bproper name\b|\ba place\b|\ban Israelite\b|\ba city\b|\bthe name of\b", "proper noun"),
]


def guess_pos(definition):
    """Strong's dictionaries have no part-of-speech field. Only an explicit
    wording in the entry is used; otherwise the field stays empty."""
    head = definition.split(";")[0].lower() if definition else ""
    for pat, pos in POS_PATTERNS:
        if re.search(pat, head):
            return pos
    return None


def split_def(text):
    text = collapse(text)
    usage = None
    if ":--" in text:
        text, usage = text.split(":--", 1)
    elif ": --" in text:
        text, usage = text.split(": --", 1)
    return text.strip(" ;:"), (usage.strip(" .") if usage else None)


def load_lexicon(offline):
    out = {}
    path = fetch(CROSSWIRE.format("StrongsGreek"), "StrongsGreek.zip", offline)
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    check_crosswire("StrongsGreek", conf)
    base = conf["DataPath"].lstrip("./")
    for key, body in read_zld(zf, base):
        if not key.isdigit() or int(key) == 0:
            continue
        orths = re.findall(r"<orth([^>]*)>(.*?)</orth>", body)
        lemma = next((collapse(t) for a, t in orths if "type" not in a), None)
        translit = next((collapse(t) for a, t in orths if 'type="trans"' in a), None)
        pron = re.search(r"<pron[^>]*>\{?(.*?)\}?</pron>", body)
        d = re.search(r"<def>(.*?)</def>", body, re.S)
        dtext = d.group(1) if d else ""
        dtext = re.sub(r"see (GREEK|HEBREW) for \d+", "", strip_tags(dtext))
        definition, usage = split_def(html.unescape(dtext))
        if not lemma and not definition:
            continue
        out["G" + str(int(key))] = dict(lemma=lemma, translit=translit,
                                        pron=pron.group(1) if pron else None,
                                        definition=definition, usage=usage, pos=guess_pos(definition))
    path = fetch(CROSSWIRE.format("StrongsHebrew"), "StrongsHebrew.zip", offline)
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    check_crosswire("StrongsHebrew", conf)
    base = conf["DataPath"].lstrip("./")
    # The rawLD index is unreliable for this module (records pack many entries),
    # so the whole .dat is split on its "\\NNNNN\\" entry markers.
    dat = zf.read(base + ".dat").decode("utf-8", "replace").replace("\r", "")
    for m in re.finditer(r"\\(\d{5})\\\s*\n(.*?)(?=\$\$T\d+|\Z)", dat, re.S):
        k, b = m.group(1), m.group(2)
        if int(k) == 0:
            continue
        lines = b.strip("\n").split("\n")
        head = lines[0].strip()
        hm = re.match(r"^\d+\s+(.*?)\s{2,}(.*)$", head)
        translit, pron = (hm.group(1).strip(), hm.group(2).strip()) if hm else (head, None)
        rest = "\n".join(lines[1:])
        rest = re.sub(r"see (GREEK|HEBREW) for \d+", "", rest)
        definition, usage = split_def(rest)
        sid = "H" + str(int(k))
        if sid in out or not (translit or definition):
            continue
        out[sid] = dict(lemma=None, translit=translit, pron=pron, definition=definition, usage=usage,
                        pos=guess_pos(definition))
    return out


OPENSCRIPTURES = "https://raw.githubusercontent.com/openscriptures/strongs/master/{0}/strongs-{0}-dictionary.js"


def fill_from_openscriptures(lexicon, offline):
    """Open Scriptures' JSON Strong's dictionaries (CC-BY-SA, approved by the
    user on 2026-11-19). The public-domain CrossWire entries stay the base;
    Open Scriptures adds Hebrew-script lemmas and the Greek entries missing
    from the CrossWire module. Disable with --no-openscriptures."""
    added = 0
    for lang in ("hebrew", "greek"):
        path = fetch(OPENSCRIPTURES.format(lang), f"openscriptures-{lang}.js", offline)
        s = open(path, encoding="utf-8").read()
        if "CC-BY-SA" not in s[:2000]:
            sys.exit(f"LICENCE CHECK FAILED: Open Scriptures {lang} header no longer states CC-BY-SA")
        data = json.loads(s[s.index("{"):s.rindex("}") + 1])
        for sid, e in data.items():
            sid = sid[0] + str(int(sid[1:]))
            cur = lexicon.get(sid)
            if cur is None:
                definition = collapse(e.get("strongs_def") or "")
                deriv = collapse(e.get("derivation") or "")
                if deriv:
                    definition = (deriv + " " + definition).strip()
                lexicon[sid] = dict(lemma=e.get("lemma"), translit=e.get("translit") or e.get("xlit"),
                                    pron=e.get("pron"), definition=definition, usage=e.get("kjv_def"),
                                    pos=guess_pos(definition))
                added += 1
            elif not cur["lemma"] and e.get("lemma"):
                cur["lemma"] = e["lemma"]
                added += 1
    LICENCES.append(("openscriptures-strongs", "Strong's Hebrew and Greek dictionaries (JSON)",
                     "Open Scriptures strongs", OPENSCRIPTURES.format("{hebrew,greek}"),
                     "CC-BY-SA (Open Scriptures, as stated in the file header); attribution: Open Scriptures, derived from Strong's 1890/1894 dictionaries", "https://github.com/openscriptures/strongs"))
    return added


# ---------------------------------------------------------------------------
# Abbreviation table (user-reviewable)
# ---------------------------------------------------------------------------
def load_or_create_abbreviations(book_meta):
    if os.path.exists(ABBR_TSV):
        rows = {}
        with open(ABBR_TSV, encoding="utf-8") as f:
            for r in csv.DictReader((l for l in f if not l.startswith("#")), delimiter="\t"):
                rows[int(r["book"])] = r
        return rows
    rows = {}
    for b in range(1, 67):
        meta = book_meta[b]
        name = meta.get("toc2") or meta.get("main")
        if b in USER_ABBR:
            abbr, status, src = USER_ABBR[b], "confirmed", "user example"
        else:
            abbr = meta.get("toc3") or ""
            status, src = "unconfirmed", "mg1865 toc3"
            # strip the leading number: the parser handles "1 Sam" itself
            abbr = re.sub(r"^\d\s*", "", abbr)
        rows[b] = {"book": str(b), "name_mg": name, "abbr_mg": abbr, "status": status, "source": src,
                   "aliases": ""}
    with open(ABBR_TSV, "w", encoding="utf-8") as f:
        f.write("# Malagasy book abbreviations. Edit abbr_mg / aliases, then set status=confirmed.\n")
        f.write("# aliases: extra spellings separated by '|' (accents and case are ignored by the parser).\n")
        w = csv.DictWriter(f, ["book", "name_mg", "abbr_mg", "status", "source", "aliases"], delimiter="\t",
                           lineterminator="\n")
        w.writeheader()
        for b in range(1, 67):
            w.writerow(rows[b])
    return rows


# ---------------------------------------------------------------------------
# Kotlin canon generation (consumed by the pure-Kotlin core module)
# ---------------------------------------------------------------------------
def kq(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def write_canon_kt(books, mg_counts):
    os.makedirs(os.path.dirname(CANON_KT), exist_ok=True)
    lines = [
        "// GENERATED by tools/build_data.py. Do not edit: change tools/abbreviations.tsv and rebuild.",
        "package mg.baiboly.core.bible",
        "",
        "internal object CanonData {",
        "    /** id, osis, Malagasy name, Malagasy abbreviation, confirmed, aliases, French name, English name */",
        "    val books: List<BookInfo> = listOf(",
    ]
    for b in books:
        aliases = "listOf(" + ", ".join(kq(a) for a in b["aliases"]) + ")"
        lines.append(f"        BookInfo({b['id']}, {kq(b['osis'])}, {kq(b['name_mg'])}, {kq(b['abbr_mg'])}, "
                     f"{'true' if b['confirmed'] else 'false'}, {aliases}, {kq(b['name_fr'])}, {kq(b['name_en'])}),")
    lines.append("    )")
    lines.append("")
    lines.append("    /** Verses per chapter in the Malagasy 1865 numbering, one string per book. */")
    lines.append("    val verseCounts: List<IntArray> = listOf(")
    for b in range(1, BOOK_COUNT + 1):
        lines.append("        intArrayOf(" + ", ".join(str(n) for n in mg_counts[b]) + "),")
    lines.append("    )")
    lines.append("}")
    with open(CANON_KT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
def chapter_counts(verses):
    counts = {}
    for (b, c, v) in verses:
        counts.setdefault(b, {}).setdefault(c, 0)
        counts[b][c] = max(counts[b][c], v)
    return counts


def build(offline, openscriptures=True):
    global OFFLINE
    OFFLINE = offline
    os.makedirs(ASSETS, exist_ok=True)
    os.makedirs(REPORTS, exist_ok=True)

    print("eBible catalogue")
    cat_path = fetch(EBIBLE.format("translations.csv"), "translations.csv", offline)
    with open(cat_path, encoding="utf-8-sig") as f:
        catalogue = {r["translationId"]: r for r in csv.DictReader(f)}

    print("Malagasy 1865")
    mg, mg_headings, book_meta, canonical_titles = load_mg1865(offline)
    texts = {"mg1865": mg}
    headings_of = {"mg1865": mg_headings}
    for tid, num, lang, short, name, style, kind, src in TRANSLATIONS:
        if kind == "ebible":
            print(tid)
            check_ebible(src, catalogue)
            texts[tid] = load_vpl(src, offline, catholic=tid in CATHOLIC)
        elif kind == "crosswire" and tid != "mg1865":
            print(tid)
            texts[tid], headings_of[tid] = load_crosswire_text(src, offline)

    abbr = load_or_create_abbreviations(book_meta)
    mg_counts_map = chapter_counts(mg)
    # Only the Catholic texts keep books 67-73; chapter-count differences elsewhere
    # (Malachi 4, Joel 4, Greek Esther 11-16, Daniel 13-14) are each text's own numbering.
    for tid in texts:
        if tid not in CATHOLIC:
            texts[tid] = {k: t for k, t in texts[tid].items() if k[0] <= 66}

    # Reference verse counts for the parser: Malagasy 1865 for the 66 books, the
    # World English Bible Catholic Edition for the deuterocanonical books.
    webc_counts = chapter_counts(texts["webc"])
    for d in DEUTERO:
        mg_counts_map[d[0]] = webc_counts[d[0]]
    mg_counts = {b: [mg_counts_map[b][c] for c in sorted(mg_counts_map[b])] for b in range(1, BOOK_COUNT + 1)}
    deutero = {d[0]: d for d in DEUTERO}
    books = []
    for b in range(1, BOOK_COUNT + 1):
        if b in deutero:
            d = deutero[b]
            a = abbr[b]
            aliases = [x for x in (a.get("aliases") or "").split("|") if x]
            books.append(dict(id=b, osis=d[1], name_mg=a["name_mg"], abbr_mg=a["abbr_mg"],
                              confirmed=a["status"] == "confirmed", aliases=aliases,
                              name_fr=d[3], name_en=d[4], chapters=len(mg_counts[b])))
            continue
        meta = book_meta[b]
        osis = CANONS["german"]["ot" if b <= 39 else "nt"][(b - 1) if b <= 39 else (b - 40)][1]
        a = abbr[b]
        aliases = [x for x in (a.get("aliases") or "").split("|") if x]
        long_name = meta.get("toc1") or meta.get("main")
        if long_name and norm(long_name) != norm(a["name_mg"]):
            aliases.append(long_name)
        books.append(dict(id=b, osis=osis, name_mg=a["name_mg"], abbr_mg=a["abbr_mg"],
                          confirmed=a["status"] == "confirmed", aliases=aliases,
                          name_fr=NAMES_FR[b - 1], name_en=NAMES_EN[b - 1], chapters=len(mg_counts[b])))
    write_canon_kt(books, mg_counts)

    # ---------------- bible.db + text-<id>.db ----------------
    # bible.db holds the catalogue (translations, books) and the Malagasy text
    # with its full-text index. Every other translation lives in its own
    # text-<id>.db with the same schema (text_norm + prebuilt FTS index): the
    # app copies a file out of the APK only when that translation is first
    # opened, so device storage holds only the texts actually used.
    for f in os.listdir(ASSETS):
        if f.endswith(".db"):
            os.remove(os.path.join(ASSETS, f))
    text_schema = """
        PRAGMA page_size = 4096;
        CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
        -- id = num*1e8 + book*1e6 + chapter*1e3 + verse, so a chapter is one id range.
        CREATE TABLE verse(id INTEGER PRIMARY KEY, translation TEXT NOT NULL, book INTEGER NOT NULL,
            chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL, text_norm TEXT NOT NULL);
        CREATE TABLE chapter_info(translation TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse_count INTEGER NOT NULL, PRIMARY KEY(translation, book, chapter)) WITHOUT ROWID;
        CREATE TABLE heading(translation TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, seq INTEGER NOT NULL, text TEXT NOT NULL,
            PRIMARY KEY(translation, book, chapter, verse, seq)) WITHOUT ROWID;
        -- Contentless FTS4 over text_norm; docid = verse.id.
        CREATE VIRTUAL TABLE verse_fts USING fts4(content="", text_norm);
    """
    db_path = os.path.join(ASSETS, "bible.db")
    db = sqlite3.connect(db_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
        CREATE TABLE translation(id TEXT PRIMARY KEY, num INTEGER NOT NULL UNIQUE, lang TEXT NOT NULL,
            short_name TEXT NOT NULL, name TEXT NOT NULL, style TEXT NOT NULL, style_note TEXT,
            licence TEXT NOT NULL, source TEXT NOT NULL, notes TEXT, sort INTEGER NOT NULL,
            file TEXT NOT NULL, fts_prebuilt INTEGER NOT NULL, catholic INTEGER NOT NULL);
        CREATE TABLE book(id INTEGER PRIMARY KEY, osis TEXT NOT NULL, name_mg TEXT NOT NULL, abbr_mg TEXT NOT NULL,
            abbr_confirmed INTEGER NOT NULL, name_fr TEXT NOT NULL, name_en TEXT NOT NULL, chapters INTEGER NOT NULL);
    """)
    db.executescript(text_schema)
    db.execute("INSERT INTO meta VALUES('data_version', ?)", (str(DATA_VERSION),))
    for b in books:
        db.execute("INSERT INTO book VALUES(?,?,?,?,?,?,?,?)",
                   (b["id"], b["osis"], b["name_mg"], b["abbr_mg"], int(b["confirmed"]), b["name_fr"], b["name_en"],
                    b["chapters"]))
    lic_by_src = {l[0]: l for l in LICENCES}
    sizes = {}
    for sort, (tid, num, lang, short, name, style, kind, src) in enumerate(TRANSLATIONS):
        lic = lic_by_src[src]
        notes = None
        if tid == "ncl_fr":
            notes = "Catholic Bible (73 books). Adapted under CC BY-SA 4.0: notes and formatting left out; the text itself is unchanged."
        if tid == "crampon":
            notes = "Catholic Bible (73 books), canon Augustin Crampon, 1923 edition."
        if tid == "webc":
            notes = "Catholic Bible (73 books); Esther and Daniel in their Greek form."
        if tid == "dra":
            notes = "Catholic Bible (73 books). Psalms numbered as in the Vulgate: Psalm 22 here is Psalm 23 elsewhere."
        if tid == "tdx":
            notes = "Tandroy dialect of southern Madagascar. Draft version published by its translator."
        if tid == "darby_fr":
            notes = "Old Testament verse numbers follow the Hebrew Bible in places."
        if tid == "mg1865":
            notes = "Psalm titles are numbered as verse 1 in this source (German versification)."
        primary = tid == "mg1865"
        fname = "bible.db" if primary else f"text-{tid}.db"
        db.execute("INSERT INTO translation VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                   (tid, num, lang, short, name, style, STYLE_NOTE[style], lic[4], lic[3], notes, sort, fname, 1,
                    int(tid in CATHOLIC)))
        if primary:
            tdb = db
        else:
            tdb = sqlite3.connect(os.path.join(ASSETS, fname))
            tdb.executescript(text_schema)
            tdb.execute("INSERT INTO meta VALUES('data_version', ?)", (str(DATA_VERSION),))
            tdb.execute("INSERT INTO meta VALUES('translation', ?)", (tid,))
        rows = []
        for (b, c, v), t in sorted(texts[tid].items()):
            rows.append((verse_id(num, b, c, v), tid, b, c, v, t, norm(t)))
        tdb.executemany("INSERT INTO verse VALUES(?,?,?,?,?,?,?)", rows)
        tdb.executemany("INSERT INTO verse_fts(docid, text_norm) VALUES(?,?)", [(r[0], r[6]) for r in rows])
        tdb.execute("INSERT INTO verse_fts(verse_fts) VALUES('optimize')")
        seq = {}
        for (b, c, v, h) in headings_of.get(tid, []):
            if (b, c, v) not in texts[tid]:
                continue
            k = (b, c, v)
            seq[k] = seq.get(k, 0) + 1
            tdb.execute("INSERT INTO heading VALUES(?,?,?,?,?,?)", (tid, b, c, v, seq[k], h))
        cc = chapter_counts(texts[tid])
        tdb.executemany("INSERT INTO chapter_info VALUES(?,?,?,?)",
                        [(tid, b, c, n) for b in cc for c, n in cc[b].items()])
        if not primary:
            tdb.commit()
            tdb.execute("VACUUM")
            tdb.close()
            sizes[fname] = os.path.getsize(os.path.join(ASSETS, fname))
    db.commit()
    db.execute("VACUUM")
    db.close()
    sizes["bible.db"] = os.path.getsize(db_path)

    # ---------------- study.db ----------------
    print("KJV Strong's tagging + lexicon")
    LICENCES.append(("eng-kjv2006-usfm", "King James Version with Strong's numbers", "eBible.org eng-kjv2006 (USFM)",
                     EBIBLE.format("eng-kjv2006_usfm.zip"), "Public Domain (eBible.org catalogue + about page)",
                     ""))
    check_ebible("eng-kjv2006", catalogue)
    LICENCES.pop()  # check_ebible appended a duplicate VPL row
    tagging = load_kjv_tagging(offline)
    lexicon = load_lexicon(offline)
    if openscriptures:
        print("  filled from Open Scriptures:", fill_from_openscriptures(lexicon, offline))
    kjv_counts = chapter_counts(texts["kjv"])

    sp = os.path.join(ASSETS, "study.db")
    if os.path.exists(sp):
        os.remove(sp)
    sdb = sqlite3.connect(sp)
    sdb.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
        -- Only words carrying a Strong's number are stored; `position` is the word's
        -- index among all words of the KJV verse, so chips keep KJV word order.
        CREATE TABLE kjv_word(book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            position INTEGER NOT NULL, word_en TEXT NOT NULL, strongs TEXT NOT NULL,
            PRIMARY KEY(book, chapter, verse, position)) WITHOUT ROWID;
        CREATE TABLE lexicon(strongs TEXT PRIMARY KEY, lemma TEXT, translit TEXT, pron TEXT,
            part_of_speech TEXT, definition_en TEXT, kjv_usage TEXT) WITHOUT ROWID;
        CREATE TABLE strongs_verse(strongs TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, PRIMARY KEY(strongs, book, chapter, verse)) WITHOUT ROWID;
        -- Malagasy (mg1865) verse -> KJV verse, only for chapters whose counts differ
        -- and where the mapping is evidenced by the source. Other mismatched
        -- chapters are listed in verse_map_chapter with method 'unresolved'.
        CREATE TABLE verse_map(translation TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL,
            PRIMARY KEY(translation, book, chapter, verse)) WITHOUT ROWID;
        CREATE TABLE verse_map_chapter(translation TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse_count INTEGER NOT NULL, kjv_verse_count INTEGER NOT NULL, method TEXT NOT NULL,
            PRIMARY KEY(translation, book, chapter)) WITHOUT ROWID;
    """)
    sdb.execute("INSERT INTO meta VALUES('data_version', ?)", (str(DATA_VERSION),))
    kw, sv = [], set()
    tagged_ids = set()
    for (b, c, v), seqw in tagging.items():
        for pos, (w, s) in enumerate(seqw):
            if s:
                kw.append((b, c, v, pos, w, s))
                sv.add((s, b, c, v))
                tagged_ids.add(s)
    sdb.executemany("INSERT INTO kjv_word VALUES(?,?,?,?,?,?)", kw)
    sdb.executemany("INSERT INTO strongs_verse VALUES(?,?,?,?)", sorted(sv))
    sdb.executemany("INSERT INTO lexicon VALUES(?,?,?,?,?,?,?)",
                    [(k, e["lemma"], e["translit"], e["pron"], e["pos"], e["definition"], e["usage"])
                     for k, e in sorted(lexicon.items())])

    # verse_map
    mism = []
    for b in range(1, 67):
        chs = sorted(set(mg_counts_map.get(b, {})) | set(kjv_counts.get(b, {})))
        for c in chs:
            m, k = mg_counts_map.get(b, {}).get(c, 0), kjv_counts.get(b, {}).get(c, 0)
            if m == k:
                continue
            method = "unresolved"
            if m == k + 1 and (b, c, 1) in canonical_titles and b == 19:
                method = "psalm_title_offset"
                for v in range(1, m + 1):
                    sdb.execute("INSERT INTO verse_map VALUES('mg1865',?,?,?,?,?)", (b, c, v, c, max(1, v - 1)))
                    VMAP[(b, c, v)] = (c, max(1, v - 1))
            elif m == k + 2 and (b, c, 1) in canonical_titles and (b, c, 2) in canonical_titles and b == 19:
                method = "psalm_title_offset_2"
                for v in range(1, m + 1):
                    sdb.execute("INSERT INTO verse_map VALUES('mg1865',?,?,?,?,?)", (b, c, v, c, max(1, v - 2)))
                    VMAP[(b, c, v)] = (c, max(1, v - 2))
            sdb.execute("INSERT INTO verse_map_chapter VALUES('mg1865',?,?,?,?,?)", (b, c, m, k, method))
            mism.append((b, c, m, k, method))
    sdb.commit()
    sdb.execute("VACUUM")
    sdb.close()

    # ---------------- reports ----------------
    build_study_library(offline)
    write_reports(books, mism, lexicon, tagged_ids, kw, texts, mg_counts_map)
    sizes["study.db"] = os.path.getsize(sp)
    sizes["library.db"] = os.path.getsize(os.path.join(ASSETS, "library.db"))
    with open(os.path.join(ASSETS, "manifest.json"), "w") as f:
        json.dump({"data_version": DATA_VERSION, "files": sorted(sizes)}, f)
    for k, v in sorted(sizes.items()):
        print(f"  {k:24s} {v / 1e6:6.2f} MB")


def build_study_library(offline):
    """Cross-references, dictionaries, places and map outlines (tools/build_library.py)."""
    import build_library as L
    print("Study library")
    numbering = L.Numbering(VMAP)

    tsk = fetch(CROSSWIRE.format("TSK"), "TSK.zip", offline)
    check_crosswire("TSK", conf_of(zipfile.ZipFile(tsk)))
    xrefs = L.load_tsk(tsk, CANONS["kjv"], OSIS_IDS, numbering)

    dicts = []
    for mod, sid, kind in (("Easton", "easton", "dictionary"), ("Nave", "nave", "topics")):
        path = fetch(CROSSWIRE.format(mod), mod + ".zip", offline)
        conf, entries = L.load_zld_dict(path, read_zld, conf_of)
        check_crosswire(mod, conf)
        dicts.append((sid, conf.get("Description", mod), "en", kind, "Public Domain", CROSSWIRE.format(mod), entries, norm))
    path = fetch(CROSSWIRE.format("FreGBM"), "FreGBM.zip", offline)
    conf, entries = L.load_rawld4_or_rawld(path, conf_of)
    # "Any distribution authorized, with the mention of the site: www.biblemartin.com"
    if "biblemartin.com" not in conf.get("Copyright", "") or "distribution authorized" not in conf.get("Copyright", ""):
        sys.exit("LICENCE CHECK FAILED: FreGBM copyright statement changed")
    martin_licence = "Free distribution with mention of www.biblemartin.com"
    dicts.append(("martin", "Glossaire de la Bible David Martin (1744)", "fr", "glossary", martin_licence,
                  "http://www.biblemartin.com/bible/glossaire_txt.htm", entries, norm))
    LICENCES.append(("FreGBM", "Glossaire de la Bible David Martin (1744)", "CrossWire SWORD module FreGBM",
                     CROSSWIRE.format("FreGBM"), martin_licence, "www.biblemartin.com"))

    geo = fetch(L.GEO_URL, "openbible-ancient.jsonl", offline)
    geo_lic = fetch(L.GEO_LICENCE_URL, "openbible-geo-license.txt", offline)
    if "Attribution 4.0 International" not in open(geo_lic, encoding="utf-8").read(2000):
        sys.exit("LICENCE CHECK FAILED: OpenBible.info geocoding data is no longer CC BY 4.0")
    LICENCES.append(("openbible-geo", "Bible places (OpenBible.info Bible Geocoding Data)", "OpenBible.info",
                     "https://github.com/openbibleinfo/Bible-Geocoding-Data", "CC BY 4.0, © OpenBible.info", "https://www.openbible.info/geo/"))
    places, links = L.load_places(geo, OSIS_IDS, numbering)

    land = fetch(L.NE.format("ne_10m_land"), "ne_10m_land.geojson", offline)
    lakes = fetch(L.NE.format("ne_10m_lakes"), "ne_10m_lakes.geojson", offline)
    rivers = fetch(L.NE.format("ne_10m_rivers_lake_centerlines"), "ne_10m_rivers.geojson", offline)
    # roads and borders of the ancient world (AWMC, ODbL), today's borders and cities (Natural Earth)
    awmc_lic = fetch(L.AWMC_LICENCE, "awmc-license.txt", offline)
    with open(awmc_lic, encoding="utf-8-sig") as f:
        if "Open Database License" not in f.read(2000):
            raise SystemExit("AWMC licence is no longer ODbL: review before shipping")
    LICENCES.append(("awmc", "Roman roads and period borders (Ancient World Mapping Center geodata)", "Ancient World Mapping Center, UNC Chapel Hill",
                     "https://github.com/AWMC/geodata", "ODbL 1.0 (Open Database License); the map data derived from it (assets/map.json) is offered under the same licence",
                     "https://opendatacommons.org/licenses/odbl/1-0/"))
    roads = fetch(L.AWMC.format(L.AWMC_ROADS), "awmc-roads.geojson", offline)
    realms = {k: fetch(L.AWMC.format(v), f"awmc-{k}.geojson", offline) for k, v in L.AWMC_REALMS.items()}
    countries = fetch(L.NE.format("ne_50m_admin_0_countries"), "ne_50m_admin_0_countries.geojson", offline)
    cities = fetch(L.NE.format("ne_50m_populated_places"), "ne_50m_populated_places.geojson", offline)
    relief = fetch(L.NE_RELIEF, "ne-sr-lr.tif", offline)
    rs = L.build_relief(relief, os.path.join(ROOT, "app", "src", "main", "assets", "map_relief.jpg"))
    print(f"  relief {rs[0]}x{rs[1]}")
    m = L.build_map(land, lakes, rivers, os.path.join(ROOT, "app", "src", "main", "assets", "map.json"),
                    roads_path=roads, realms=realms, countries_path=countries, cities_path=cities, routes=L.build_routes(geo))
    LICENCES.append(("naturalearth", "Map outlines (Natural Earth 1:10m land, lakes, rivers)", "Natural Earth",
                     "https://www.naturalearthdata.com", "Public Domain", "https://github.com/nvkelso/natural-earth-vector"))

    lib = os.path.join(ASSETS, "library.db")
    L.build_library(lib, xrefs, dicts, places, links)
    sqlite3.connect(lib).execute("INSERT INTO meta VALUES('data_version', ?)", (str(DATA_VERSION),)).connection.commit()
    print(f"  cross-references {len(xrefs)}, dictionaries {[len(d[6]) for d in dicts]}, places {len(places)} "
          f"({len(links)} verse links), map rings land {len(m['land'])} lakes {len(m['lakes'])} rivers {len(m['rivers'])}, "
          f"library.db {os.path.getsize(lib) / 1e6:.2f} MB, map.json {os.path.getsize(os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'map.json')) / 1e6:.2f} MB")


def write_reports(books, mism, lexicon, tagged_ids, kw, texts, mg_counts_map):
    name = {b["id"]: b["name_mg"] for b in books}
    with open(os.path.join(REPORTS, "verse_map.md"), "w", encoding="utf-8") as f:
        f.write("# Verse numbering: Malagasy 1865 vs KJV\n\n")
        f.write("Generated by `tools/build_data.py`. Chapters whose verse counts differ between the Malagasy "
                "source (German versification) and the KJV used by the study panel.\n\n")
        f.write("* `psalm_title_offset`: the source marks verse 1 as the canonical psalm title, so Malagasy "
                "verse *n* is mapped to KJV verse *n-1* (title -> KJV 1).\n")
        f.write("* `unresolved`: no evidence-based mapping; the study panel uses the same verse number "
                "and shows a warning.\n\n")
        counts = {}
        for r in mism:
            counts[r[4]] = counts.get(r[4], 0) + 1
        f.write("Summary: " + ", ".join(f"{k}: {v}" for k, v in sorted(counts.items())) + "\n\n")
        f.write("| Book | Chapter | Malagasy verses | KJV verses | Method |\n|---|---|---|---|---|\n")
        for b, c, m, k, method in mism:
            f.write(f"| {name[b]} | {c} | {m} | {k} | {method} |\n")
    missing = sorted(s for s in tagged_ids if s not in lexicon)
    usage = {}
    for (_b, _c, _v, _p, _w, s) in kw:
        usage[s] = usage.get(s, 0) + 1
    no_pos = sum(1 for e in lexicon.values() if not e["pos"])
    no_lemma_h = sum(1 for k, e in lexicon.items() if k.startswith("H") and not e["lemma"])
    with open(os.path.join(REPORTS, "lexicon_gaps.md"), "w", encoding="utf-8") as f:
        f.write("# Strong's coverage report\n\n")
        f.write(f"* Distinct Strong's numbers in the tagged KJV: {len(tagged_ids)}\n")
        f.write(f"* Lexicon entries: {len(lexicon)}\n")
        f.write(f"* Tagged numbers without a lexicon entry: {len(missing)}\n")
        f.write(f"* Entries without an explicit part of speech in Strong's wording: {no_pos} "
                "(the field is left empty rather than guessed)\n")
        f.write(f"* Hebrew entries without Hebrew-script lemma: {no_lemma_h}\n")
        f.write("* Base: public-domain CrossWire Strong's dictionaries; Hebrew-script lemmas and missing entries "
                "from Open Scriptures (CC-BY-SA), see docs/data-licences.md\n\n")
        if missing:
            f.write("| Strong's | Occurrences in KJV |\n|---|---|\n")
            for s in missing:
                f.write(f"| {s} | {usage.get(s, 0)} |\n")
    with open(os.path.join(REPORTS, "translations.md"), "w", encoding="utf-8") as f:
        f.write("# Bundled translations\n\n| Id | Name | Language | Style | Verses | Chapters |\n"
                "|---|---|---|---|---|---|\n")
        for tid, num, lang, short, nm, style, kind, src in TRANSLATIONS:
            cc = chapter_counts(texts[tid])
            f.write(f"| {tid} | {nm} | {lang} | {STYLE_NOTE[style]} | {len(texts[tid])} | "
                    f"{sum(len(v) for v in cc.values())} |\n")
    with open(os.path.join(DOCS, "data-licences.md"), "w", encoding="utf-8") as f:
        f.write("# Data sources and licences\n\nEvery source below passed the automatic licence check in "
                "`tools/build_data.py` (the build stops otherwise).\n\n")
        f.write("| Source | Title | Licence | Download | Origin |\n|---|---|---|---|---|\n")
        for sid, title, desc, url, lic, origin in LICENCES:
            f.write(f"| {desc} | {title} | {lic} | {url} | {origin} |\n")
        f.write("\nTools: versification tables from PySword 0.2.8 (MIT licence), `tools/vendor/sword_canons.py`.\n")
    with open(os.path.join(ROOT, "app", "src", "main", "assets", "licences.json"), "w", encoding="utf-8") as f:
        json.dump([dict(id=s, title=t, source=d, url=u, licence=l, origin=o) for s, t, d, u, l, o in LICENCES], f,
                  ensure_ascii=False, indent=1)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--no-openscriptures", action="store_true",
                    help="public-domain sources only (skip the CC-BY-SA Open Scriptures dictionaries)")
    a = ap.parse_args()
    build(a.offline, not a.no_openscriptures)
