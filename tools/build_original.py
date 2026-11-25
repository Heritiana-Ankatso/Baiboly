#!/usr/bin/env python3
"""
Original-language text for the study panel (original.db) and the Hebrew
versification table used to number the Malagasy 1865 chapters that the
KJV does not match.

Sources (STEPBible-Data, Tyndale House Cambridge, CC BY 4.0):

    TAHOT  Translators Amalgamated Hebrew OT   (Leningrad codex, Strong's, ETCBC morphology)
    TAGNT  Translators Amalgamated Greek NT    (NA28 / SBLGNT / TR words, Strong's, Robinson morphology)
    TBESH  Translators Brief lexicon of Extended Strongs, Hebrew (abridged BDB)
    TBESG  Translators Brief lexicon of Extended Strongs, Greek (Abbott-Smith and others)
    TEHMC  Translators Expansion of Hebrew Morphology Codes
    TVTMS  Translators Versification Traditions with Methodology for Standardisation

Greek parsing codes are expanded here (Robinson's scheme) because the
TEGMC file could not be found at a stable address.

Imported by tools/build_data.py; has no command line of its own.
"""
import os
import re
import sqlite3
import sys
import unicodedata

BASE = "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/master/"
STEP_LICENCE_URL = "https://github.com/STEPBible/STEPBible-Data"

_AMAL = "Translators Amalgamated OT+NT/"
SOURCES = {
    "tahot1": _AMAL + "TAHOT Gen-Deu - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
    "tahot2": _AMAL + "TAHOT Jos-Est - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
    "tahot3": _AMAL + "TAHOT Job-Sng - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
    "tahot4": _AMAL + "TAHOT Isa-Mal - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
    "tagnt1": _AMAL + "TAGNT Mat-Jhn - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt",
    "tagnt2": _AMAL + "TAGNT Act-Rev - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt",
    "tbesh": "Lexicons/TBESH - Translators Brief lexicon of Extended Strongs for Hebrew - STEPBible.org CC BY.txt",
    "tbesg": "Lexicons/TBESG - Translators Brief lexicon of Extended Strongs for Greek - STEPBible.org CC BY.txt",
    "tehmc": "Morphology codes/TEHMC - Translators Expansion of Hebrew Morphology Codes - STEPBible.org CC BY.txt",
    "tvtms": "Versification/TVTMS - Translators Versification Traditions with Methodology for Standardisation for Eng+Heb+Lat+Grk+Others - STEPBible.org CC BY.txt",
}

# STEPBible book codes, in canonical order (book number = index + 1)
BOOKS = ("Gen Exo Lev Num Deu Jos Jdg Rut 1Sa 2Sa 1Ki 2Ki 1Ch 2Ch Ezr Neh Est Job Psa Pro Ecc Sng Isa "
         "Jer Lam Ezk Dan Hos Jol Amo Oba Jon Mic Nam Hab Zep Hag Zec Mal Mat Mrk Luk Jhn Act Rom 1Co "
         "2Co Gal Eph Php Col 1Th 2Th 1Ti 2Ti Tit Phm Heb Jas 1Pe 2Pe 1Jn 2Jn 3Jn Jud Rev").split()
BOOK_NO = {c: i + 1 for i, c in enumerate(BOOKS)}

REF_RE = re.compile(r"^(\w{3})\.(\d+)\.(\d+)(?:\([\d.]+\))?#(\d+)=(\S+)$")
CANTILLATION = re.compile("[֑-֯]")  # accents: the vowels stay


def _collapse(s):
    return re.sub(r"\s+", " ", s).strip()


def base_strongs(code):
    """H0430G -> H430, G0026 -> G26 (the form used by lexicon and kjv_word)."""
    m = re.match(r"([GH])0*(\d+)", code or "")
    return m.group(1) + m.group(2) if m else None


# ---------------------------------------------------------------------------
# Hebrew (TAHOT)
# ---------------------------------------------------------------------------
def hebrew_root(dstrongs):
    """The word's own Strong's number: the one in {braces}, else the first that is not an affix (H9xxx)."""
    parts = dstrongs.split("/")
    for p in parts:
        if p.startswith("{"):
            return p.strip("{}")
    for p in parts:
        p = p.strip("{}").split("\\")[0]
        if re.match(r"H\d+", p) and not p.startswith("H9"):
            return p
    return parts[0].strip("{}")


def load_tahot(paths):
    """-> list of (book, chapter, verse, position, word, translit, gloss, strongs, dstrong, morph, flag)."""
    rows, seen, dup = [], set(), 0
    for path in paths:
        for line in open(path, encoding="utf-8-sig"):
            p = line.rstrip("\n").split("\t")
            if len(p) < 6:
                continue
            m = REF_RE.match(p[0])
            if not m or m.group(1) not in BOOK_NO:
                continue
            code, ch, v, pos, typ = m.groups()
            key = (BOOK_NO[code], int(ch), int(v), int(pos))
            if key in seen:
                dup += 1
                continue
            seen.add(key)
            word = CANTILLATION.sub("", p[1]).replace("/", "").replace("\\", "")
            word = unicodedata.normalize("NFC", word)
            if not word.strip():  # a Qere that is absent from the text: "[ ]"
                continue
            translit = p[2].replace("/", "").replace("\\", "").replace(".", "")
            gloss = _collapse(re.sub(r"\s*[/\\]\s*", " ", p[3]))
            ds = p[4]
            root = hebrew_root(ds)
            rows.append(key + (word, translit, gloss, base_strongs(root), re.sub(r"[{}]", "", root), p[5], typ[0]))
    if dup:
        print(f"  TAHOT: {dup} repeated word positions ignored")
    return rows


# ---------------------------------------------------------------------------
# Greek (TAGNT)
# ---------------------------------------------------------------------------
def load_tagnt(paths):
    rows, seen = [], set()
    for path in paths:
        for line in open(path, encoding="utf-8-sig"):
            p = line.rstrip("\n").split("\t")
            if len(p) < 5:
                continue
            m = re.match(r"^(\w{3})\.(\d+)\.(\d+)#(\d+)=(\S+)$", p[0])
            if not m or m.group(1) not in BOOK_NO:
                continue
            code, ch, v, pos, typ = m.groups()
            # N = in the Nestle-Aland text; K = in the Textus Receptus used by the KJV.
            # Lower-case letters and O (other editions) are variants that are not shown.
            if "N" in typ:
                flag = "N"
            elif "K" in typ:
                flag = "K"
            else:
                continue
            key = (BOOK_NO[code], int(ch), int(v), int(pos))
            if key in seen:
                continue
            seen.add(key)
            wm = re.match(r"^(.*?)\s*\(([^()]*)\)\s*$", p[1])
            word, translit = (wm.group(1), wm.group(2)) if wm else (p[1], "")
            ds, _, morph = p[3].partition("=")  # "G0976=N-NSF", or "G1161=CONJ + G1565=D" for merged words
            rows.append(key + (unicodedata.normalize("NFC", word), translit, _collapse(p[2]),
                               base_strongs(ds), ds, morph, flag))
    return rows


# --- Robinson morphology codes, in words --------------------------------------
_CASE = {"N": "nominative", "G": "genitive", "D": "dative", "A": "accusative", "V": "vocative"}
_NUM = {"S": "singular", "P": "plural"}
_GEN = {"M": "masculine", "F": "feminine", "N": "neuter"}
_TENSE = {"P": "present", "I": "imperfect", "F": "future", "A": "aorist", "R": "perfect", "L": "pluperfect", "X": "no tense stated"}
_VOICE = {"A": "active", "M": "middle", "P": "passive", "E": "middle or passive", "D": "middle (deponent)",
          "O": "passive (deponent)", "N": "middle or passive (deponent)", "Q": "impersonal active", "X": "no voice stated"}
_MOOD = {"I": "indicative", "S": "subjunctive", "O": "optative", "M": "imperative", "N": "infinitive", "P": "participle"}
_PERSON = {"1": "first", "2": "second", "3": "third"}
_POS = {"N": "noun", "V": "verb", "T": "article", "P": "personal pronoun", "A": "adjective", "D": "demonstrative pronoun",
        "R": "relative pronoun", "I": "interrogative pronoun", "X": "indefinite pronoun", "F": "reflexive pronoun",
        "S": "possessive adjective", "K": "correlative pronoun", "C": "reciprocal pronoun", "Q": "correlative or interrogative pronoun",
        "CONJ": "conjunction", "PREP": "preposition", "PRT": "particle", "ADV": "adverb", "COND": "conditional",
        "INJ": "interjection", "HEB": "Hebrew word", "ARAM": "Aramaic word", "ARAM-": "Aramaic word"}
_SUFFIX = {"P": "proper name", "PG": "proper name (a people)", "L": "place name", "T": "title", "C": "comparative",
           "S": "superlative", "N": "negative", "I": "interrogative", "K": "contracted", "ATT": "Attic form",
           "ARAM": "Aramaic", "M": "middle", "O": "indeclinable"}


def _case_num_gen(s):
    out = []
    if len(s) >= 1 and s[0] in _CASE:
        out.append(_CASE[s[0]])
    if len(s) >= 2 and s[1] in _NUM:
        out.append(_NUM[s[1]])
    if len(s) >= 3 and s[2] in _GEN:
        out.append(_GEN[s[2]])
    return " ".join(out)


def expand_greek(code):
    """Robinson's code (N-NSF, V-2AAI-3S, P-1GS, S-1SGSN, CONJ + G1565=D ...) in words, or '' if unreadable."""
    if not code:
        return ""
    if " + " in code:  # merged words: "CONJ + G1565=D"
        return " + ".join(expand_greek(c.split("=")[-1].strip()) or c for c in code.split(" + "))
    parts = code.split("-")
    pos = _POS.get(parts[0])
    if pos is None:
        return ""
    head, rest = parts[0], parts[1:]
    if head == "V" and rest:
        t = rest[0]
        second = ""
        if t[:1] == "2" and len(t) > 1:
            second, t = "second ", t[1:]
        tense = _TENSE.get(t[0], "") if t else ""
        voice = _VOICE.get(t[1], "") if len(t) > 1 else ""
        mood = _MOOD.get(t[2], "") if len(t) > 2 else ""
        bits = ["verb", (second + tense + " " + voice + " " + mood).strip()]
        if len(rest) > 1:
            tail = rest[1]
            if mood == "participle":
                bits.append(_case_num_gen(tail))
            elif tail[:1] in _PERSON:
                bits.append(f"{_PERSON[tail[0]]} person {_NUM.get(tail[1:2], '')}".strip())
            rest = rest[2:]
        else:
            rest = []
        return ", ".join([b for b in bits if b] + [_SUFFIX.get(x, x.lower()) for x in rest])
    bits = [pos]
    if rest and rest[0][:1] in _PERSON:
        tail = rest[0]
        if head == "S":  # S-1SGSN: first person singular owner, genitive singular neuter thing owned
            bits.append(f"{_PERSON[tail[0]]} person {_NUM.get(tail[1:2], '')} owner".replace("  ", " "))
            bits.append(_case_num_gen(tail[2:]))
        else:  # P-1GS, F-3ASM
            bits.append(f"{_PERSON[tail[0]]} person")
            bits.append(_case_num_gen(tail[1:]))
        rest = rest[1:]
    elif rest:
        bits.append(_case_num_gen(rest[0]))
        rest = rest[1:]
    bits += [_SUFFIX.get(x, x.lower()) for x in rest]
    return ", ".join(b for b in bits if b)


# ---------------------------------------------------------------------------
# Hebrew parsing codes (TEHMC)
# ---------------------------------------------------------------------------
def load_tehmc(path):
    """code -> short expansion, e.g. HVqp3ms -> 'Verb : Qal (Simple, Active) Perfect ... Third Singular Masculine'."""
    out = {}
    for rec in open(path, encoding="utf-8-sig").read().split("\n$\n"):
        lines = rec.split("\n")
        m = re.match(r"^([HA][A-Za-z0-9+._\-]*)\tFunction=", lines[0])
        if not m:
            continue
        label = ""
        for ln in lines[1:]:
            if ln.startswith("\t") and ln.strip():
                label = ln.strip().strip('"')
                break
        if label:
            out[m.group(1)] = label
    return out


def expand_hebrew(raw, table):
    """'HR/Ncfsa' -> 'Preposition + Noun (Singular Feminine, Absolute)'. The language letter of the first part
    (H Hebrew, A Aramaic) is shared by the parts that follow it."""
    lang = "H"
    labels = []
    for i, part in enumerate(raw.split("/")):
        if not part:
            continue
        if i == 0 and part[0] in "HA":
            lang = part[0]
        found = None
        for cand in (part, lang + part, "A" + part, "H" + part):
            if cand in table:
                found = table[cand]
                break
        if found is None:
            return ""
        labels.append(found)
    return " + ".join(labels)


# ---------------------------------------------------------------------------
# Lexicons (TBESH, TBESG)
# ---------------------------------------------------------------------------
def _plain(html):
    s = re.sub(r"<\s*BR\s*/?\s*>", "\n", html, flags=re.I)
    s = re.sub(r"<ref='[^']*'>", "", s)
    s = re.sub(r"</?[A-Za-z][^>]*>", "", s)
    s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    s = re.sub(r"[ \t]+", " ", s)
    s = re.sub(r"\s*\n\s*", "\n", s)
    return s.strip()


def load_tbes(path, lang):
    """-> list of (dstrong, strongs, lemma, translit, pos, gloss, definition)."""
    out, seen = [], set()
    for line in open(path, encoding="utf-8-sig"):
        p = line.rstrip("\n").split("\t")
        if len(p) < 8 or not re.match(rf"^{lang}\d{{4}}", p[0]):
            continue
        ds = p[1].split("=")[0].strip().split()[0] if p[1].strip() else p[0]
        if ds in seen:
            continue
        seen.add(ds)
        out.append((ds, base_strongs(ds), p[3].strip(), p[4].strip(), p[5].strip(), p[6].strip(), _plain(p[7])))
    return out


# ---------------------------------------------------------------------------
# Versification (TVTMS): Hebrew numbering -> English (KJV) numbering
# ---------------------------------------------------------------------------
_RANGE = re.compile(r"^(\w{3})\.(\d+):(\d+)(?:-(\d+))?$")


def load_tvtms_hebrew(path):
    """{(book, ch, v) in Hebrew numbering: (ch, v) in English numbering}, only the verse-for-verse rows
    inside one chapter. Merged and split verses are left out (they cannot be mapped one to one)."""
    mapping = {}
    for line in open(path, encoding="utf-8-sig"):
        if not line.startswith("OneToOne\t"):
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) < 3:
            continue
        e, h = _RANGE.match(p[1].strip()), _RANGE.match(p[2].strip())
        if not e or not h or e.group(1) != h.group(1) or e.group(1) not in BOOK_NO:
            continue
        ev1, hv1 = int(e.group(3)), int(h.group(3))
        ev2, hv2 = int(e.group(4) or ev1), int(h.group(4) or hv1)
        if ev2 - ev1 != hv2 - hv1:
            continue
        b = BOOK_NO[e.group(1)]
        for k in range(ev2 - ev1 + 1):
            mapping[(b, int(h.group(2)), hv1 + k)] = (int(e.group(2)), ev1 + k)
    return mapping


def resolve_by_hebrew(unresolved, mg_counts, kjv_counts, heb2eng, fixed):
    """Chapters where the Malagasy text follows the Hebrew verse numbers: give each verse its KJV place.
    `fixed` holds the verses already mapped from evidence (the psalm titles). A Malagasy chapter is accepted
    only if every KJV chapter its verses land in is then complete: each of its verses is the image of exactly
    one Malagasy verse (or of several where `fixed` says so, as a psalm title) and no verse is missing or
    extra. Anything else stays unresolved. Returns ({(book, ch, v): (kjv_ch, kjv_v)}, {(book, ch) settled})."""
    def target(b, c, v):
        return fixed.get((b, c, v)) or heb2eng.get((b, c, v), (c, v))

    result, settled = {}, set()
    for book in sorted({b for b, _ in unresolved}):
        image = {}
        for c, n in mg_counts.get(book, {}).items():
            for v in range(1, n + 1):
                image.setdefault(target(book, c, v), []).append((c, v))
        complete = {}
        for ec, n in kjv_counts.get(book, {}).items():
            ok = all(
                (ec, ev) in image and (len(image[(ec, ev)]) == 1 or all((book, *src) in fixed for src in image[(ec, ev)]))
                for ev in range(1, n + 1)
            ) and not any(k[0] == ec and k[1] > n for k in image)
            complete[ec] = ok
        for c in sorted({c for b, c in unresolved if b == book}):
            n = mg_counts.get(book, {}).get(c, 0)
            targets = [target(book, c, v) for v in range(1, n + 1)]
            landing = {ec for ec, _ in targets}
            # a chapter only the KJV has (Malachi 4) is settled when the Malagasy verses that land there are
            landing_all = landing | ({c} if n == 0 else set())
            if all(complete.get(ec, False) for ec in landing_all) and (n or any(k[0] == c for k in image)):
                for v, t in enumerate(targets, start=1):
                    result[(book, c, v)] = t
                settled.add((book, c))
    return result, settled


# ---------------------------------------------------------------------------
# original.db
# ---------------------------------------------------------------------------
def check_licence(path, name):
    head = open(path, encoding="utf-8-sig").read(4000)
    if "CC BY 4.0" not in head:
        sys.exit(f"LICENCE CHECK FAILED: {name} no longer states CC BY 4.0 in its header")


def fetch_all(fetch, offline):
    paths = {}
    for k, rel in SOURCES.items():
        url = BASE + rel.replace(" ", "%20").replace("+", "%2B")
        paths[k] = fetch(url, "stepbible-" + k + ".txt", offline)
        check_licence(paths[k], rel.split(" - ")[0])
    return paths


def licence_entries():
    lic = "CC BY 4.0, © STEPBible.org and Tyndale House, Cambridge (credit: STEP Bible, www.STEPBible.org)"
    return [
        ("stepbible-tahot", "Hebrew Old Testament with Strong's numbers and morphology (TAHOT: Leningrad codex via Open Scriptures, "
                            "corrected by Tyndale House; ETCBC morphology)", "STEPBible-Data (Tyndale House Cambridge)", STEP_LICENCE_URL, lic, "https://www.stepbible.org"),
        ("stepbible-tagnt", "Greek New Testament with Strong's numbers and morphology (TAGNT: NA28, SBLGNT, Textus Receptus and other editions; "
                            "Robinson morphology)", "STEPBible-Data (Tyndale House Cambridge)", STEP_LICENCE_URL, lic, "https://www.stepbible.org"),
        ("stepbible-tbes", "Brief Hebrew and Greek lexicons (TBESH: abridged BDB; TBESG: Abbott-Smith and others) and Hebrew parsing codes (TEHMC)",
         "STEPBible-Data (Tyndale House Cambridge)", STEP_LICENCE_URL, lic, "https://www.stepbible.org"),
        ("stepbible-tvtms", "Versification traditions (TVTMS), used to number the Malagasy chapters that follow the Hebrew verse numbers",
         "STEPBible-Data (Tyndale House Cambridge)", STEP_LICENCE_URL, lic, "https://www.stepbible.org"),
    ]


def build_original_db(db_path, paths, data_version, kjv_counts):
    """Writes original.db; returns a dict of statistics for the report."""
    hebrew = load_tahot([paths[k] for k in ("tahot1", "tahot2", "tahot3", "tahot4")])
    greek = load_tagnt([paths[k] for k in ("tagnt1", "tagnt2")])
    heb_table = load_tehmc(paths["tehmc"])
    lex = load_tbes(paths["tbesh"], "H") + load_tbes(paths["tbesg"], "G")

    labels, unknown = {}, set()
    for r in hebrew:
        if r[9] and r[9] not in labels:
            lab = expand_hebrew(r[9], heb_table)
            labels[r[9]] = lab
            if not lab:
                unknown.add(r[9])
    for r in greek:
        if r[9] and r[9] not in labels:
            lab = expand_greek(r[9])
            labels[r[9]] = lab
            if not lab:
                unknown.add(r[9])

    if os.path.exists(db_path):
        os.remove(db_path)
    db = sqlite3.connect(db_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
        -- One row per word of the Hebrew (TAHOT) and Greek (TAGNT) text, in the English (KJV) verse numbering.
        -- Psalm titles are verse 0. flag: L/Q/R/X... = Hebrew reading; N = in the Nestle-Aland text,
        -- K = only in the Textus Receptus (Greek). dstrong is the extended Strong's number (H7225G).
        CREATE TABLE orig_word(book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            position INTEGER NOT NULL, word TEXT NOT NULL, translit TEXT, gloss TEXT, strongs TEXT, dstrong TEXT,
            morph TEXT, flag TEXT, PRIMARY KEY(book, chapter, verse, position)) WITHOUT ROWID;
        CREATE TABLE morph_label(code TEXT PRIMARY KEY, label TEXT NOT NULL) WITHOUT ROWID;
        -- Found N verses by the word of the original text (its Strong's number)
        CREATE TABLE orig_strongs_verse(strongs TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, PRIMARY KEY(strongs, book, chapter, verse)) WITHOUT ROWID;
        CREATE TABLE lexicon_ext(dstrong TEXT PRIMARY KEY, strongs TEXT, lemma TEXT, translit TEXT, pos TEXT,
            gloss TEXT, definition TEXT) WITHOUT ROWID;
        CREATE INDEX lexicon_ext_strongs ON lexicon_ext(strongs);
    """)
    db.execute("INSERT INTO meta VALUES('data_version', ?)", (str(data_version),))
    db.executemany("INSERT INTO orig_word VALUES(?,?,?,?,?,?,?,?,?,?,?)", hebrew + greek)
    db.executemany("INSERT INTO morph_label VALUES(?,?)", sorted((k, v) for k, v in labels.items() if v and k))
    sv = {(r[7], r[0], r[1], r[2]) for r in hebrew + greek if r[7]}
    db.executemany("INSERT INTO orig_strongs_verse VALUES(?,?,?,?)", sorted(sv))
    db.executemany("INSERT INTO lexicon_ext VALUES(?,?,?,?,?,?,?)", lex)
    db.commit()
    db.execute("VACUUM")
    db.close()

    # verses of the KJV that have no original-language words, and the reverse
    have = {(r[0], r[1], r[2]) for r in hebrew + greek}
    expected = {(b, c, v) for b, cs in kjv_counts.items() for c, n in cs.items() for v in range(1, n + 1)}
    return {
        "hebrew_words": len(hebrew), "greek_words": len(greek), "lexicon": len(lex),
        "unknown_morph": sorted(unknown), "missing": sorted(expected - have),
        "extra": sorted(v for v in have - expected if v[2] != 0),
    }
