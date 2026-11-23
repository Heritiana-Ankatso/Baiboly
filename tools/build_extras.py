"""
People, events, confessions and councils for library.db (docs/ideas.md B1, B2, A1, A2).

Called by tools/build_data.py (build_study_library). Every verse is stored in
Malagasy 1865 numbering, like the rest of library.db.

Sources (each passes a licence check in build_data.py):
  * Theographic Bible Metadata (CC BY-SA 4.0): people, family relations,
    events with dates after F. N. Jones (and the Ussher-based marginal year of
    each verse, from the Treasury of Scripture Knowledge, as a second date).
  * Reformed Standards compendium (Apache 2.0): historic English texts of the
    Reformed confessions and catechisms, with their proof texts.
  * Creeds.json (Unlicense for the texts used here): the French Confession of
    1559 (Schaff's translation) and the place/year metadata of the documents.

Texts are included only where the English translation is historic (public
domain) or the original is English. Left out, with the reason recorded in
docs/ideas.md: modern translations under copyright (Second Helvetic,
Chalcedonian Definition, Council of Orange, the ELLC Apostles' Creed), and
documents for which no public-domain source could be reached.
"""
import json
import re

THEO = "https://raw.githubusercontent.com/robertrouse/theographic-bible-metadata/master/{}"
RS = "https://raw.githubusercontent.com/reformed-standards/compendium/master/{}"
CREEDS = "https://raw.githubusercontent.com/NonlinearFruit/Creeds.json/master/{}"

# ---------------------------------------------------------------- people and events

REL_KINDS = ("father", "mother", "children", "siblings", "partners", "halfSiblingsSameFather", "halfSiblingsSameMother")


def _osis_to_mg(osis, osis_ids, numbering):
    m = re.match(r"^(\w+)\.(\d+)\.(\d+)$", osis or "")
    if not m or m.group(1) not in osis_ids:
        return None
    b = osis_ids[m.group(1)]
    c, v = numbering.to_mg(b, int(m.group(2)), int(m.group(3)))
    return b, c, v


def load_people_events(people_path, events_path, verses_path, osis_ids, numbering):
    """Returns (people, relations, person_verses, events, event_people, event_verses)."""
    with open(verses_path, encoding="utf-8") as f:
        verses = {r["id"]: r["fields"] for r in json.load(f)}
    with open(people_path, encoding="utf-8") as f:
        raw_people = json.load(f)
    with open(events_path, encoding="utf-8") as f:
        raw_events = json.load(f)
    rec_to_pid = {r["id"]: r["fields"]["personID"] for r in raw_people}
    rec_to_eid = {r["id"]: r["fields"]["eventID"] for r in raw_events}

    def year(s):
        try:
            return int(str(s)[:5].rstrip("-")) if str(s).startswith("-") else int(str(s)[:4])
        except (TypeError, ValueError):
            return None

    def verse_rows(recs):
        out, seen = [], set()
        for rec in recs or []:
            v = verses.get(rec)
            if not v:
                continue
            mg = _osis_to_mg(v.get("osisRef"), osis_ids, numbering)
            if mg and mg not in seen:
                seen.add(mg)
                out.append((mg, v.get("yearNum")))
        return sorted(out)

    people, rels, pverses = [], [], []
    for r in raw_people:
        f = r["fields"]
        pid = f["personID"]
        vs = verse_rows(f.get("verses"))
        first = vs[0][0] if vs else (0, 0, 0)
        people.append((pid, f.get("displayTitle") or f["name"], clean_also(f.get("alsoCalled")), (f.get("gender") or "")[:1],
                       year(f.get("birthYear")), year(f.get("deathYear")), len(vs), first[0], first[1], first[2]))
        for (b, c, v), _y in vs:
            pverses.append((pid, b, c, v))
        for kind in REL_KINDS:
            for other in f.get(kind) or []:
                if other in rec_to_pid:
                    k = {"children": "child", "siblings": "sibling", "partners": "partner",
                         "halfSiblingsSameFather": "half_sibling", "halfSiblingsSameMother": "half_sibling"}.get(kind, kind)
                    rels.append((pid, k, rec_to_pid[other]))
    people, rels = apply_curation(people, rels)
    events, eppl, everses = [], [], []
    for r in raw_events:
        f = r["fields"]
        eid = f["eventID"]
        start = f.get("startDate") or ""
        y = year(start)
        if y is None:
            continue
        vs = verse_rows(f.get("verses"))
        first = vs[0][0] if vs else (0, 0, 0)
        # second date: the Ussher-based year printed beside the event's first verse (TSK margin)
        ussher = next((yy for _v, yy in vs if isinstance(yy, int)), None)
        part_of = rec_to_eid.get((f.get("partOf") or [None])[0])
        events.append((eid, f["title"], y, start if "-" in start[1:] else "", f.get("duration") or "",
                       1 if f.get("rangeFlag") else 0, (f.get("notes") or "").strip(), ussher,
                       float(f.get("sortKey") or y), part_of, first[0], first[1], first[2]))
        for rec in set((f.get("participants") or []) + (f.get("people (from verses)") or [])):
            if rec in rec_to_pid:
                eppl.append((eid, rec_to_pid[rec]))
        for (b, c, v), _y in vs:
            everses.append((eid, b, c, v))
    return people, sorted(set(rels)), pverses, events, sorted(set(eppl)), everses


# ---------------------------------------------------------------- curation of family links

# The source models God as a person whose "children" are Adam and Eve (from Luke 3:38), and gives
# Joseph as the father of Jesus without qualification. Shown as family links, both say more than
# the text: Adam is "the son of God" by creation (Gen 2:7), not by begetting, and Jesus was "(as was
# supposed) the son of Joseph" (Luke 3:23), conceived of the Holy Spirit (Matt 1:18-25; Luke 1:35).
DIVINE = {"God", "Holy Spirit"}           # no family links to or from these
NOTES = {                                 # person name -> note key (texts in the app's strings)
    "Adam": "son_of_god",
    "Jesus Christ": "virgin_birth",
    "God": "no_family",
}
LEGAL = ("Jesus Christ", "Joseph (Mary's Husband)")  # (child, legal father)


def apply_curation(people, rels):
    """Drops family links of God and the Holy Spirit, marks Joseph as legal father; adds note keys."""
    by_name = {}
    for p in people:
        by_name.setdefault(p[1], p[0])
    divine = {by_name[n] for n in DIVINE if n in by_name}
    child, father = by_name.get(LEGAL[0]), by_name.get(LEGAL[1])
    out = []
    for a, kind, b in rels:
        if a in divine or b in divine:
            continue
        if a == child and b == father and kind == "father":
            kind = "legal_father"
        elif a == father and b == child and kind == "child":
            kind = "legal_child"
        out.append((a, kind, b))
    notes = {by_name[n]: k for n, k in NOTES.items() if n in by_name}
    return [p + (notes.get(p[0], ""),) for p in people], sorted(set(out))


def clean_also(also):
    """'LORD,Lord,Father' -> 'LORD, Lord, Father' (no repeats, case kept)."""
    seen, out = set(), []
    for a in (also or "").split(","):
        a = a.strip()
        if a and a not in seen:
            seen.add(a)
            out.append(a)
    return ", ".join(out)


# ---------------------------------------------------------------- confessions and councils

# The documents, their tradition and family, and where they come from.
# family: "creed" (shared by the traditions) or "reformed" (the app's standpoint).
DOCUMENTS = [
    # id, short (as cited), title, family, source kind, path
    ("apostles", "AC", "The Apostles' Creed", "creed", "hc23", "data/three-forms-of-unity/heidelberg-catechism.yaml"),
    ("nicene", "NC", "The Nicene Creed", "creed", "rs", "data/ancient-church/nicene-creed.yaml"),
    ("athanasian", "Ath", "The Athanasian Creed", "creed", "rs", "data/ancient-church/athanasian-creed.yaml"),
    ("belgic", "BC", "The Belgic Confession", "reformed", "rs", "data/three-forms-of-unity/belgic-confession.yaml"),
    ("heidelberg", "HC", "The Heidelberg Catechism", "reformed", "rs", "data/three-forms-of-unity/heidelberg-catechism.yaml"),
    ("dort", "CD", "The Canons of Dort", "reformed", "rs", "data/three-forms-of-unity/canons-of-dort.yaml"),
    ("wcf", "WCF", "The Westminster Confession of Faith", "reformed", "rs", "data/westminster/wcf.yaml"),
    ("wlc", "WLC", "The Westminster Larger Catechism", "reformed", "rs", "data/westminster/wlc.yaml"),
    ("wsc", "WSC", "The Westminster Shorter Catechism", "reformed", "rs", "data/westminster/wsc.yaml"),
    ("gallic", "FC", "The French (Gallic) Confession", "reformed", "creeds", "creeds/french_confession_of_faith.json"),
    ("savoy", "SD", "The Savoy Declaration", "reformed", "rs", "data/british/savoy.yaml"),
    ("lbc1689", "LBC", "The Second London Baptist Confession", "reformed", "rs", "data/second-london/1689-confession.yaml"),
]

# Creeds.json file holding each document's year and place (its metadata, not its text).
META = {
    "apostles": "apostles_creed", "nicene": "nicene_creed", "athanasian": "athanasian_creed",
    "belgic": "belgic_confession_of_faith", "heidelberg": "heidelberg_catechism", "dort": "canons_of_dort",
    "wcf": "westminster_confession_of_faith", "wlc": "westminster_larger_catechism", "wsc": "westminster_shorter_catechism",
    "gallic": "french_confession_of_faith", "savoy": "savoy_declaration", "lbc1689": "london_baptist_1689",
}

# Councils, synods and assemblies that issued a document in the library. Name, place and
# year are those the sources give for the document (Creeds.json metadata).
COUNCILS = [
    # id, name, documents issued
    ("constantinople381", "Council of Constantinople", ["nicene"]),
    ("dort", "Synod of Dort", ["dort"]),
    ("westminster", "Westminster Assembly", ["wcf", "wlc", "wsc"]),
    ("savoy", "Savoy Assembly", ["savoy"]),
]

_EN_BOOKS = None


def _english_books(canon_books):
    """'Romans' -> 'Rom', '1 John' -> '1John', plus a few older spellings."""
    global _EN_BOOKS
    if _EN_BOOKS is None:
        m = {name: osis for osis, name in canon_books}
        m.update({"Psalm": "Ps", "Revelation": "Rev", "Song of Solomon": "Song", "Canticles": "Song"})
        _EN_BOOKS = m
    return _EN_BOOKS


def _clean(s):
    s = (s or "").replace("\r", "")
    s = re.sub(r"[ \t]+\n", "\n", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def _footnoted(text, verses):
    """Footnote letters [a] in the text and their proof texts -> (text, [(letter, [osis...])])."""
    if isinstance(verses, list):  # one list for the whole article, no footnote letters
        return _clean(text), [("", [r for r in verses if isinstance(r, str)])] if verses else []
    proofs = []
    for letter in re.findall(r"\[([a-z]{1,2}|\d{1,2})\]", text or ""):
        # keys are letters ("a") or, in the Larger Catechism, numbers (1)
        refs = (verses or {}).get(letter) or (verses or {}).get(int(letter) if letter.isdigit() else letter) or []
        proofs.append((letter, [r for r in refs if isinstance(r, str)]))
    return _clean(text), proofs


def _inline_refs(text, en_books):
    """Book chapter:verse references written in the text -> OSIS ranges."""
    names = sorted(en_books, key=len, reverse=True)
    pat = re.compile(r"\b(" + "|".join(re.escape(n) for n in names) + r")\.? (\d+):(\d+)(?:\s*[-–]\s*(\d+))?")
    out = []
    for m in pat.finditer(text or ""):
        osis = en_books[m.group(1)]
        a = f"{osis}.{m.group(2)}.{m.group(3)}"
        out.append(a + (f"-{osis}.{m.group(2)}.{m.group(4)}" if m.group(4) else ""))
    return out


def load_documents(paths, canon_books):
    """paths: id -> local file. Returns (docs, items). An item: (doc, seq, label, title, question, text, proofs)."""
    import yaml
    en = _english_books(canon_books)
    docs, items = [], []
    for sort, (did, short, title, family, kind, _p) in enumerate(DOCUMENTS):
        path = paths[did]
        rows = []
        if kind == "creeds":
            with open(path, encoding="utf-8") as f:
                d = json.load(f)
            for a in d["Data"]:
                label = a.get("Article", "")
                text = _clean(a.get("Content"))
                rows.append((label, a.get("Title") if a.get("Title") not in (None, "", label) else "", "", text,
                             [("", _inline_refs(text, en))] if _inline_refs(text, en) else []))
        else:
            with open(path, encoding="utf-8") as f:
                d = yaml.safe_load(f)
            if kind == "hc23":
                # The Apostles' Creed as the Heidelberg Catechism gives it (Q&A 23), in its historic English form.
                q = next(q for day in d["days"] for q in day["questions"] if q["number"] == 23)
                text = "\n".join(re.sub(r"^\d+\.\s*", "", line) for line in _clean(q["answer"]).splitlines())
                rows.append(("", "", "", text, []))
            elif "text" in d:  # a creed: one text with footnotes
                text, proofs = _footnoted(d["text"], d.get("verses"))
                rows.append(("", "", "", text, proofs))
            elif "questions" in d or "days" in d:  # a catechism
                qs = d.get("questions") or [q for day in d["days"] for q in day["questions"]]
                lord = {}
                for day in d.get("days", []):
                    for q in day["questions"]:
                        lord[q["number"]] = day["number"]
                for q in qs:
                    text, proofs = _footnoted(q.get("answer"), q.get("verses"))
                    group = f"Lord's Day {lord[q['number']]}" if q["number"] in lord else ""
                    rows.append((str(q["number"]), group, _clean(q.get("question")), text, proofs))
            else:  # a confession: chapters, with articles or one text each
                for ch in d["chapters"]:
                    name = _clean(ch.get("name"))
                    if ch.get("articles"):
                        for a in ch["articles"]:
                            text, proofs = _footnoted(a.get("text"), a.get("verses"))
                            if not proofs and _inline_refs(text, en):
                                proofs = [("", _inline_refs(text, en))]
                            rows.append((f"{ch['number']}.{a['number']}", name, "", text, proofs))
                    else:
                        text, proofs = _footnoted(ch.get("text"), ch.get("verses"))
                        if not proofs and _inline_refs(text, en):
                            proofs = [("", _inline_refs(text, en))]
                        rows.append((str(ch["number"]), name, "", text, proofs))
        for seq, (label, group, question, text, proofs) in enumerate(rows):
            items.append((did, seq, label, group, question, text, proofs))
        docs.append([did, short, title, family, sort, len(rows)])
    return docs, items


def add_meta(docs, meta_paths):
    """Year and place of each document, from Creeds.json metadata."""
    out = []
    for did, short, title, family, sort, n in docs:
        with open(meta_paths[did], encoding="utf-8") as f:
            m = json.load(f)["Metadata"]
        out.append((did, short, title, family, str(m.get("Year") or ""), m.get("Location") or "",
                    m.get("OriginalLanguage") or "", sort, n))
    return out


def osis_range_to_mg(osis, osis_ids, numbering):
    """'Rom.14.7', 'Ps.19.1-Ps.19.3', 'Rom.1.19-20' -> (book, chapter, v1, v2) in Malagasy numbering, or None."""
    a, _, b = osis.partition("-")
    pa = a.split(".")
    if len(pa) < 3 or pa[0] not in osis_ids:
        return None
    try:
        ch, v1 = int(pa[1]), int(pa[2])
        pb = b.split(".") if b else []
        if len(pb) == 3 and pb[1].isdigit() and int(pb[1]) == ch:
            v2 = int(pb[2])
        elif len(pb) == 1 and pb[0].isdigit():
            v2 = int(pb[0])
        else:
            v2 = v1
    except ValueError:
        return None
    book = osis_ids[pa[0]]
    c1, m1 = numbering.to_mg(book, ch, v1)
    c2, m2 = numbering.to_mg(book, ch, max(v1, v2))
    return book, c1, m1, (m2 if c2 == c1 else m1)


def write_tables(db, people, rels, pverses, events, eppl, everses, docs, items, osis_ids, numbering, norm):
    db.executescript("""
        -- Theographic Bible Metadata (CC BY-SA 4.0). Years are astronomical (1 BC = 0, 4004 BC = -4003).
        CREATE TABLE person(id INTEGER PRIMARY KEY, name TEXT NOT NULL, also TEXT NOT NULL, gender TEXT NOT NULL,
            born INTEGER, died INTEGER, mentions INTEGER NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, note TEXT NOT NULL, name_norm TEXT NOT NULL);
        CREATE INDEX person_by_name ON person(name_norm);
        -- note: key of a note shown on the person's page (tools/build_extras.py NOTES), or ''
        -- kind: father | mother | child | sibling | half_sibling | partner | legal_father | legal_child
        CREATE TABLE person_rel(person INTEGER NOT NULL, kind TEXT NOT NULL, other INTEGER NOT NULL,
            PRIMARY KEY(person, kind, other)) WITHOUT ROWID;
        CREATE TABLE person_verse(person INTEGER NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, PRIMARY KEY(person, book, chapter, verse)) WITHOUT ROWID;
        CREATE INDEX person_by_verse ON person_verse(book, chapter, verse);
        -- year: F. N. Jones's chronology; ussher: TSK marginal year of the first verse (after Ussher)
        CREATE TABLE event(id INTEGER PRIMARY KEY, title TEXT NOT NULL, year INTEGER NOT NULL, date TEXT NOT NULL,
            duration TEXT NOT NULL, approx INTEGER NOT NULL, notes TEXT NOT NULL, ussher INTEGER, sort REAL NOT NULL,
            part_of INTEGER, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL);
        CREATE TABLE event_person(event INTEGER NOT NULL, person INTEGER NOT NULL, PRIMARY KEY(event, person)) WITHOUT ROWID;
        CREATE INDEX event_by_person ON event_person(person);
        CREATE TABLE event_verse(event INTEGER NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, PRIMARY KEY(event, book, chapter, verse)) WITHOUT ROWID;
        CREATE INDEX event_by_verse ON event_verse(book, chapter, verse);
        -- Confessions, catechisms and creeds. family: creed (shared) | reformed
        CREATE TABLE creed_doc(id TEXT PRIMARY KEY, short TEXT NOT NULL, title TEXT NOT NULL, family TEXT NOT NULL,
            year TEXT NOT NULL, place TEXT NOT NULL, language TEXT NOT NULL, sort INTEGER NOT NULL, items INTEGER NOT NULL) WITHOUT ROWID;
        -- One article, chapter section or question. proofs: "a⟦Rom.14.7|Rom 14:7⟧ ⟦…⟧\\nb…" (letter, then references)
        CREATE TABLE creed_item(doc TEXT NOT NULL, seq INTEGER NOT NULL, label TEXT NOT NULL, grp TEXT NOT NULL,
            question TEXT NOT NULL, text TEXT NOT NULL, proofs TEXT NOT NULL, PRIMARY KEY(doc, seq)) WITHOUT ROWID;
        CREATE TABLE creed_ref(doc TEXT NOT NULL, seq INTEGER NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            v1 INTEGER NOT NULL, v2 INTEGER NOT NULL, PRIMARY KEY(doc, seq, book, chapter, v1, v2)) WITHOUT ROWID;
        CREATE INDEX creed_by_verse ON creed_ref(book, chapter);
        CREATE TABLE council(id TEXT PRIMARY KEY, name TEXT NOT NULL, place TEXT NOT NULL, year TEXT NOT NULL,
            docs TEXT NOT NULL, sort INTEGER NOT NULL) WITHOUT ROWID;
    """)
    db.executemany("INSERT INTO person VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", [p + (norm(p[1]),) for p in people])
    db.executemany("INSERT OR IGNORE INTO person_rel VALUES(?,?,?)", rels)
    db.executemany("INSERT OR IGNORE INTO person_verse VALUES(?,?,?,?)", pverses)
    db.executemany("INSERT INTO event VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", events)
    db.executemany("INSERT OR IGNORE INTO event_person VALUES(?,?)", eppl)
    db.executemany("INSERT OR IGNORE INTO event_verse VALUES(?,?,?,?)", everses)
    db.executemany("INSERT INTO creed_doc VALUES(?,?,?,?,?,?,?,?,?)", docs)
    refs = 0
    for did, seq, label, group, question, text, proofs in items:
        lines = []
        for letter, osis_list in proofs:
            parts = []
            for osis in osis_list:
                r = osis_range_to_mg(osis, osis_ids, numbering)
                if not r:
                    continue
                db.execute("INSERT OR IGNORE INTO creed_ref VALUES(?,?,?,?,?,?)", (did, seq) + r)
                refs += 1
                parts.append(f"⟦{osis}|{osis}⟧")
            if parts:
                lines.append(letter + " ".join(parts))
        db.execute("INSERT INTO creed_item VALUES(?,?,?,?,?,?,?)", (did, seq, label, group, question, text, "\n".join(lines)))
    meta = {d[0]: d for d in docs}
    for sort, (cid, name, ids) in enumerate(COUNCILS):
        first = meta[ids[0]]
        years = sorted({meta[i][4] for i in ids if meta[i][4]})
        db.execute("INSERT INTO council VALUES(?,?,?,?,?,?)",
                   (cid, name, first[5], years[0] if len(years) == 1 else f"{years[0]}–{years[-1]}" if years else "", ",".join(ids), sort))
    return refs
