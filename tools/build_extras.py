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
                       float(f.get("sortKey") or y), part_of, first[0], first[1], first[2],
                       event_kind(f["title"], f.get("duration") or ""), len(vs)))
        for rec in set((f.get("participants") or []) + (f.get("people (from verses)") or [])):
            if rec in rec_to_pid:
                eppl.append((eid, rec_to_pid[rec]))
        for (b, c, v), _y in vs:
            everses.append((eid, b, c, v))
    return people, sorted(set(rels)), pverses, events, sorted(set(eppl)), everses


def event_kind(title, duration):
    """life | reign | judge | period (a span of a year or more) | point."""
    if not re.match(r"^\d+(Y|Y\d+M)$", duration or ""):
        return "point"
    t = title.lower()
    if t.startswith("lifetime of"):
        return "life"
    if t.startswith("reign of"):
        return "reign"
    if t.startswith("judgeship of"):
        return "judge"
    return "period"


# Eras of the timeline, each opened by a dated event of the source (F. N. Jones's chronology) and
# closed by the next one. key (names in the app's strings), title of the event that opens it.
ERAS = [
    ("creation", "Creation of all things"),
    ("flood", "The Great Flood begins"),
    ("patriarchs", "Abraham enters Canaan"),
    ("exodus", "Exodus from Egypt"),
    ("conquest", "Crossing the Jordan into Canaan"),
    ("kingdom", "Reign of Saul"),
    ("divided", "Death of Solomon"),
    ("exile", "Fall of Jerusalem"),
    ("return", "Exiles return with Zerubbabel"),
    ("between", "Prophecies of Malachi"),
    ("jesus", "Birth of Jesus"),
    ("church", "Peter preaches at Pentecost"),
]


def eras(events):
    """(key, start year, end year, sort) from the events; the last era runs to the last event."""
    by_title = {e[1]: e[2] for e in events}
    missing = [t for _, t in ERAS if t not in by_title]
    if missing:
        raise SystemExit(f"timeline eras: events not found in the source: {missing}")
    starts = [(k, by_title[t]) for k, t in ERAS]
    last = max(e[2] for e in events) + 1
    return [(k, y, starts[i + 1][1] if i + 1 < len(starts) else last, i) for i, (k, y) in enumerate(starts)]


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
        -- kind: life | reign | judge | period (spans of a year or more) | point; weight: verses telling it
        CREATE TABLE event(id INTEGER PRIMARY KEY, title TEXT NOT NULL, year INTEGER NOT NULL, date TEXT NOT NULL,
            duration TEXT NOT NULL, approx INTEGER NOT NULL, notes TEXT NOT NULL, ussher INTEGER, sort REAL NOT NULL,
            part_of INTEGER, book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            kind TEXT NOT NULL, weight INTEGER NOT NULL);
        -- eras of the timeline (tools/build_extras.py ERAS): [start, end) in astronomical years
        CREATE TABLE era(key TEXT PRIMARY KEY, start INTEGER NOT NULL, end INTEGER NOT NULL, sort INTEGER NOT NULL) WITHOUT ROWID;
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
        -- families: the traditions that receive the council (creed = all; reformed, catholic, orthodox)
        CREATE TABLE council(id TEXT PRIMARY KEY, name TEXT NOT NULL, place TEXT NOT NULL, year TEXT NOT NULL,
            docs TEXT NOT NULL, families TEXT NOT NULL, summary TEXT NOT NULL, sort INTEGER NOT NULL) WITHOUT ROWID;
    """)
    db.executemany("INSERT INTO person VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", [p + (norm(p[1]),) for p in people])
    db.executemany("INSERT OR IGNORE INTO person_rel VALUES(?,?,?)", rels)
    db.executemany("INSERT OR IGNORE INTO person_verse VALUES(?,?,?,?)", pverses)
    db.executemany("INSERT INTO event VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", events)
    db.executemany("INSERT INTO era VALUES(?,?,?,?)", eras(events))
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
    sort = 0
    # the ecumenical councils, Trent and the Vatican Council (Schaff), then the Reformed assemblies
    for cid, name, place, year, ids, families, summary in SCHAFF_COUNCILS:
        db.execute("INSERT INTO council VALUES(?,?,?,?,?,?,?,?)",
                   (cid, name, place, year, ",".join(i for i in ids if i in meta), families, summary, sort))
        sort += 1
    for cid, name, ids in COUNCILS:
        if cid in {c[0] for c in SCHAFF_COUNCILS}:
            continue
        first = meta[ids[0]]
        years = sorted({meta[i][4] for i in ids if meta[i][4]})
        db.execute("INSERT INTO council VALUES(?,?,?,?,?,?,?,?)",
                   (cid, name, first[5], years[0] if len(years) == 1 else f"{years[0]}–{years[-1]}" if years else "",
                    ",".join(ids), "reformed", "", sort))
        sort += 1
    return refs


# ---------------------------------------------------------------- Schaff, Creeds of Christendom II (1877)

SCHAFF = "https://www.ccel.org/ccel/s/schaff/creeds2.xml"

# Documents of the Catholic and Orthodox traditions (and the Definition of Chalcedon, shared), in the
# English of Schaff's edition (public domain): id, short, title, family, start heading, end heading,
# layout ("table": Latin/Greek and English side by side; "catechism": numbered questions), year, place,
# numbered (items start with "I." / "1." in the text).
SCHAFF_DOCS = [
    ("chalcedon", "Chal", "The Definition of Chalcedon", "creed",
     "III. SYMBOLUM CHALCEDONENSE", "IV. SYMBOLUM QUICUNQUE", "table", "451", "Chalcedon", False),
    ("trent", "Trent", "The Canons and Decrees of the Council of Trent", "catholic",
     "I. CANONES ET DECRETA DOGMATICA CONCILII TRIDENTINI", "II. PROFESSIO FIDEI TRIDENTIN", "table", "1546–1563", "Trent", False),
    ("ptf", "PTF", "The Profession of the Tridentine Faith", "catholic",
     "II. PROFESSIO FIDEI TRIDENTIN", "III. DECRETUM PII IX", "table", "1564", "Rome", True),
    ("ineffabilis", "ID", "The Decree on the Immaculate Conception (Ineffabilis Deus)", "catholic",
     "III. DECRETUM PII IX", "IV. SYLLABUS ERRORUM", "table", "1854", "Rome", False),
    ("vatican1", "Vat. I", "The Dogmatic Decrees of the Vatican Council", "catholic",
     "V. DECRETA DOGMATICA CONCILII VATICANI", "SYMBOLA GRÆCA ET RUSSICA", "table", "1870", "Rome", True),
    ("philaret", "LCat", "The Longer Catechism of the Orthodox Church", "orthodox",
     "III. THE LONGER CATECHISM OF THE ORTHODOX", "THE OLD CATHOLIC UNION CREEDS", "catechism", "1830", "Moscow", False),
]

# The seven ecumenical councils, Trent and the Vatican Council, in date order: id, name, place, year,
# documents of the library they issued, traditions that receive them, a neutral summary drawn from
# Schaff, Creeds II pp. 12-73 (the creeds) and his "Review of the Dogmatic Legislation of the Seven
# Œcumenical Councils" (pp. 72-73). The Reformed assemblies follow (COUNCILS).
SCHAFF_COUNCILS = [
    ("nicaea1", "First Council of Nicaea", "Nicaea", "325", [], "creed",
     "First ecumenical council. Its creed confessed the Son “of one substance with the Father” against Arius; "
     "the Nicene Creed in its received form was completed at Constantinople in 381."),
    ("constantinople381", "First Council of Constantinople", "Constantinople", "381", ["nicene"], "creed",
     "Second ecumenical council. The received text of the Nicene Creed (Niceno-Constantinopolitan). "
     "The early texts have no “and the Son” (Filioque), a later Western addition."),
    ("ephesus", "Council of Ephesus", "Ephesus", "431", [], "creed",
     "Third ecumenical council. Issued no new creed: it reaffirmed the previous creeds and condemned the teaching of Nestorius."),
    ("chalcedon", "Council of Chalcedon", "Chalcedon", "451", ["chalcedon"], "creed",
     "Fourth ecumenical council. Defined one Christ in two natures, divine and human, in one person, against Nestorius and Eutyches."),
    ("constantinople553", "Second Council of Constantinople", "Constantinople", "553", [], "creed",
     "Fifth ecumenical council. Confessed the Nicene Creed as explained at Constantinople, Ephesus and Chalcedon, "
     "and condemned the “Three Chapters”, writings of Theodore of Mopsuestia, Theodoret of Cyrus and Ibas of Edessa."),
    ("constantinople680", "Third Council of Constantinople", "Constantinople", "680", [], "creed",
     "Sixth ecumenical council. Taught two natural wills and two natural operations in Christ, the human subject to the divine, "
     "against the Monothelites."),
    ("nicaea2", "Second Council of Nicaea", "Nicaea", "787", [], "catholic,orthodox",
     "Seventh ecumenical council for the Orthodox and Catholic Churches. Condemned the Iconoclasts and sanctioned the use "
     "and veneration of images; Protestants reject this decision (the Second Commandment)."),
    ("trent", "Council of Trent", "Trent", "1546–1563", ["trent", "ptf"], "catholic",
     "Catholic council of the Counter-Reformation: decrees on Scripture and tradition, original sin, justification "
     "and the sacraments, with canons against Protestant teaching. The Profession of Faith of 1564 summarises them."),
    ("vatican1", "First Vatican Council", "Rome", "1870", ["vatican1"], "catholic",
     "Catholic council: dogmatic constitutions on the Catholic faith (God, revelation, faith and reason) "
     "and on the Church (the primacy and infallible teaching office of the Pope)."),
]

_EN = {"the", "and", "of", "to", "that", "which", "is", "be", "he", "his", "by", "with", "shall", "this", "who", "as", "are", "it", "for", "any", "all"}
_LA = {"et", "est", "quod", "ad", "non", "cum", "ut", "qui", "quæ", "sunt", "esse", "vel", "atque", "sed", "enim", "eius", "ejus",
       "autem", "nos", "quam", "per", "de", "si", "quis", "dixerit", "sit", "ac", "eorum", "in", "ab", "sanctæ", "ecclesiæ"}


def _plain(fragment):
    import html as _h
    # block tags separate words; inline ones (small capitals split as <span>D</span>ogmatic) do not
    t = re.sub(r"<(?:/?(?:p|td|tr|br|div\d?|h\d)\b)[^>]*>", " ", fragment)
    t = re.sub(r"<[^>]+>", "", t)
    return re.sub(r"\s+", " ", _h.unescape(t)).strip()


def _english(text):
    words = re.findall(r"[a-zæœ]+", text.lower())
    if not words or len(re.findall(r"[Ͱ-Ͽἀ-῿]", text)) > len(text) * 0.2:
        return False
    return sum(w in _EN for w in words) > sum(w in _LA for w in words)


def _cell(fragment, refs):
    """English cell -> text with [n] where a footnote cites Scripture; refs gets (n, [osis]) for those notes."""
    out, pos = [], 0
    for m in re.finditer(r"<note\b.*?</note>", fragment, re.S):
        out.append(fragment[pos:m.start()])
        osis = [o for r in re.findall(r'osisRef="([^"]+)"', m.group(0)) for o in r.split()]
        osis = [o.replace("Bible:", "") for o in osis if o.startswith("Bible:")]
        if osis:
            n = len(refs) + 1
            refs.append((str(n), osis))
            out.append(f"[{n}]")
        pos = m.end()
    out.append(fragment[pos:])
    text = "".join(out)
    # scripture cited in the text itself
    inline = [o.replace("Bible:", "") for r in re.findall(r'osisRef="([^"]+)"', re.sub(r"<note\b.*?</note>", "", fragment, flags=re.S))
              for o in r.split() if o.startswith("Bible:")]
    return _plain(text).replace(" [", "[").replace(" ,", ",").replace(" .", "."), inline


def _heading_positions(xml):
    return [(m.start(), _plain(m.group(2))) for m in re.finditer(r"<h([1-5])[^>]*>(.*?)</h\1>", xml, re.S)]


def _slice(xml, heads, start, end):
    a = next(p for p, t in heads if t.upper().startswith(start.upper()))
    b = next(p for p, t in heads if p > a and t.upper().startswith(end.upper()))
    return xml[a:b]


def _cap(h):
    h = h.strip().rstrip(",")
    return h[:1].upper() + h[1:].lower() if h.isupper() else h


def _table_items(seg, numbered):
    items, ctx, cur = [], [], None
    item_head = re.compile(r"^(chapter|canon)\s+[ivxlc\d]+\.?$|^[ivxlc]+\.$", re.I)
    for row in re.findall(r"<tr\b[^>]*>(.*?)</tr>", seg, re.S):
        cells = re.findall(r"<td\b([^>]*)>(.*?)</td>", row, re.S)
        refs = []
        english = [(a, c) for a, c in cells if _english(_plain(re.sub(r"<note\b.*?</note>", "", c, flags=re.S)))]
        if not english:
            # a short centred heading ("Chapter I.", "I.") has too few words to tell: take the right-hand cell
            if len(cells) >= 2 and "center" in cells[-1][0] and len(_plain(cells[-1][1])) < 80 and not re.search(
                    r"[Ͱ-Ͽ]", _plain(cells[-1][1])) and not _plain(cells[-1][1]).lower().startswith(("caput", "sessio", "de ")):
                english = [cells[-1]]
            else:
                continue
        attrs, frag = english[-1]
        text, inline = _cell(frag, refs)
        if not text:
            continue
        if "center" in attrs:
            if item_head.match(text):
                cur = {"label": text.rstrip("."), "title": None, "text": [], "refs": [], "grp": " · ".join(ctx)}
                items.append(cur)
            elif cur is not None and cur["title"] is None and not cur["text"]:
                cur["title"] = _cap(text)
            else:
                if re.search(r"session|constitution", text, re.I) and not re.match(r"(held|published)\b", text, re.I):
                    ctx, top = [], _cap(text).rstrip(",. ")
                    ctx = [top]
                elif ctx and re.match(r"(held|published|celebrated)\b", text, re.I):
                    # "Third Session," + "held February 4, 1546." -> one heading
                    ctx[0] = ctx[0].rstrip(",. ") + ", " + text.rstrip(".")
                else:
                    # the session (or constitution) first, then the two latest headings under it
                    ctx = ctx[:1] + (ctx[1:] + [_cap(text).rstrip(".")])[-2:]
                cur = None
            continue
        m = re.match(r"^CANON\s+([IVXLC]+)\.\s*[—-]?\s*", text, re.I) or (re.match(r"^([IVXLC]+|\d+)\.\s+", text) if numbered else None)
        if m:
            cur = {"label": ("Canon " if text.upper().startswith("CANON") else "") + m.group(1), "title": None,
                   "text": [], "refs": [], "grp": " · ".join(ctx)}
            items.append(cur)
            text = text[m.end():]
        elif cur is None:
            cur = {"label": "", "title": None, "text": [], "refs": [], "grp": " · ".join(ctx)}
            items.append(cur)
        # renumber this cell's footnotes within the item
        base = len(cur["refs"])
        for n, osis in refs:
            text = text.replace(f"[{n}]", f"[{base + int(n)}]", 1)
            cur["refs"].append((str(base + int(n)), osis))
        if inline:
            cur["refs"].append(("", inline))
        if cur["text"] and not re.search(r"[.;:!?»”’)\]]$", cur["text"][-1]):
            cur["text"][-1] += " " + text
        else:
            cur["text"].append(text)
    return [i for i in items if i["text"]]


def _catechism_items(seg):
    items, grp, cur = [], "", None
    # footnotes hold paragraphs of their own: keep only the Scripture they cite, as inline references
    seg = re.sub(r"<note\b.*?</note>", lambda m: "".join(f' <scripRef osisRef="{o}"/>' for o in re.findall(r'osisRef="([^"]+)"', m.group(0))),
                 seg, flags=re.S)
    def blocks(fragment):
        for m in re.finditer(r"<(h[2-5]|p)\b([^>]*)>(.*?)</\1>", fragment, re.S):
            tag, attrs, body = m.groups()
            # one heading of the source is never closed and runs over many questions: read inside it
            if tag.startswith("h") and len(_plain(body)) > 150:
                yield from blocks(body)
            else:
                yield tag, attrs, body

    for tag, attrs, body in blocks(seg):
        if tag.startswith("h") or ("text-align:center" in attrs.replace(" ", "") and len(_plain(body)) < 90):
            grp = _cap(_plain(body))
            continue
        if "footnote" in attrs:
            continue
        q = re.match(r"\s*<b>\s*(\d+)\.\s*</b>(.*)", body, re.S)
        if q:
            cur = {"label": q.group(1), "title": _plain(q.group(2)), "text": [], "refs": [], "grp": grp}
            items.append(cur)
            continue
        if cur is None:
            continue
        refs = []
        text, inline = _cell(body, refs)
        if not text:
            continue
        for n, osis in refs:
            cur["refs"].append(("", osis))
        if inline:
            cur["refs"].append(("", inline))
        cur["text"].append(text)
    return [i for i in items if i["text"]]


def load_schaff(path):
    """Schaff II -> (docs, items, councils) in the shapes of load_documents/add_meta and COUNCILS."""
    xml = open(path, encoding="utf-8").read()
    heads = _heading_positions(xml)
    docs, items = [], []
    for sort, (did, short, title, family, start, end, layout, year, place, numbered) in enumerate(SCHAFF_DOCS, start=100):
        seg = _slice(xml, heads, start, end)
        parsed = _catechism_items(seg) if layout == "catechism" else _table_items(seg, numbered)
        for seq, it in enumerate(parsed):
            proofs = []
            for letter, osis in it["refs"]:
                proofs.append((letter, osis))
            text = "\n\n".join(it["text"])
            if layout == "catechism":
                items.append((did, seq, it["label"], it["grp"], it["title"] or "", text, proofs))
            else:
                items.append((did, seq, it["label"], it["grp"], it["title"] or "", text, proofs))
        language = "Russian" if layout == "catechism" else ("Greek" if did == "chalcedon" else "Latin")
        docs.append((did, short, title, family, year, place, language, sort, len(parsed)))
    return docs, items


def schaff_licence_ok(path):
    head = open(path, encoding="utf-8").read(20000)
    return "<DC.Rights>Public Domain</DC.Rights>" in head.replace(" ", "") or re.search(r"<DC\.Rights[^>]*>\s*Public Domain", head) is not None
