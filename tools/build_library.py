"""
Study library for Baiboly (called from build_data.py).

    library.db
        xref        Treasury of Scripture Knowledge cross-references (public domain),
                    converted to Malagasy 1865 verse numbers
        dict_source / dict_entry
                    Easton's Bible Dictionary, Nave's Topical Bible (public domain, English)
                    and the Glossaire de la Bible Martin (French, free with attribution)
        place / place_verse
                    Bible places from OpenBible.info (CC BY 4.0)
    map.json        land, lakes and rivers of the Bible lands (Natural Earth, public domain)

Bible references inside dictionary entries are stored as ⟦OSIS|label⟧ and
links to other entries of the same source as ⟦@KEY|label⟧; the app turns
them into links.
"""
import html
import json
import os
import re
import sqlite3
import struct
import zipfile
import zlib

GEO_URL = "https://raw.githubusercontent.com/openbibleinfo/Bible-Geocoding-Data/main/data/ancient.jsonl"
GEO_LICENCE_URL = "https://raw.githubusercontent.com/openbibleinfo/Bible-Geocoding-Data/main/license.txt"
# outlines of regions (Edom, Galilee, the Roman provinces…), same dataset and licence
GEO_GEOMETRY_URL = "https://raw.githubusercontent.com/openbibleinfo/Bible-Geocoding-Data/main/data/geometry.jsonl"
GEO_GEOMETRY_FILE = "https://raw.githubusercontent.com/openbibleinfo/Bible-Geocoding-Data/main/geometry/{}"
NE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/{}.geojson"
# Ancient World Mapping Center geodata (ODbL): Roman roads and period borders
AWMC = "https://raw.githubusercontent.com/AWMC/geodata/master/{}"
AWMC_LICENCE = AWMC.format("LICENSE.txt")
# Natural Earth shaded relief, 10m "low resolution" (1.3 arc-minutes per pixel), public domain
NE_RELIEF = "https://raw.githubusercontent.com/nvkelso/natural-earth-raster/master/10m_rasters/SR_LR/SR_LR.tif"
AWMC_ROADS = "Cultural-Data/roads/roads.geojson"
AWMC_REALMS = {  # key -> file; drawn for the period that uses it (see PERIODS)
    "persia": "Cultural-Data/political_shading/persian_extent/extent_of_the_persian_empire.geojson",
    "hasmonean": "Cultural-Data/political_shading/hasmonean/hasmonean_kingdom.geojson",
    "herod": "Cultural-Data/political_shading/herod/herods_kingdom.geojson",
    "rome": "Cultural-Data/political_shading/roman_empire_ce_200_provinces/roman_empire_ce_200_provinces.geojson",
}

# ---------------------------------------------------------------- timeline

# Periods of biblical history, by the books (and chapters) whose events fall in them.
# Bit i of a place's `periods` is set when a verse of period i names it. Poetry and
# wisdom books (Job, Psalms, Proverbs, Ecclesiastes, Song) belong to no period.
PERIODS = [
    ("patriarchs", lambda b, c: b == 1),
    ("exodus", lambda b, c: 2 <= b <= 5),
    ("conquest", lambda b, c: 6 <= b <= 8),
    ("kingdom", lambda b, c: b in (9, 10, 13) or (b == 11 and c <= 11) or (b == 14 and c <= 9)),
    ("divided", lambda b, c: (b == 11 and c >= 12) or b == 12 or (b == 14 and c >= 10) or b in (23, 24, 25) or 28 <= b <= 36),
    ("exile", lambda b, c: b in (15, 16, 17, 26, 27, 37, 38, 39)),
    ("between", lambda b, c: 67 <= b <= 73),
    ("jesus", lambda b, c: 40 <= b <= 43),
    ("church", lambda b, c: 44 <= b <= 66),
]


def place_stats(links):
    """place id -> (verses that name it, bit mask of periods, verses per period "n0,n1,...")."""
    counts = {}
    for pid, b, c, _v in links:
        n, per = counts.get(pid, (0, [0] * len(PERIODS)))
        for i, (_k, test) in enumerate(PERIODS):
            if test(b, c):
                per[i] += 1
        counts[pid] = (n + 1, per)
    return {pid: (n, sum(1 << i for i, k in enumerate(per) if k), ",".join(map(str, per))) for pid, (n, per) in counts.items()}


# Main highways of the Old Testament world, traced through places whose site is known.
# Names: English, French, Malagasy.
ROUTES = [
    (("Way of the Sea", "Chemin de la mer", "Lalan’ny ranomasina"),
     ["Sin", "Gaza", "Ashkelon", "Ashdod", "Joppa", "Aphek", "Megiddo", "Hazor", "Dan", "Damascus"]),
    (("King’s Highway", "Route royale", "Lalam-panjakana"),
     ["Ezion-geber", "Bozrah", "Kir-hareseth", "Dibon", "Heshbon", "Rabbah", "Ramoth-gilead", "Damascus"]),
    (("Ridge Route", "Route des crêtes", "Lalana an-tampon-tendrombohitra"),
     ["Beersheba", "Hebron", "Bethlehem", "Jerusalem", "Bethel", "Shechem", "Dothan", "Megiddo"]),
]


def build_routes(geo_path):
    """Resolve each route's places to coordinates (several places share a name: take the nearest)."""
    by_name = {}
    with open(geo_path, encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            ident = (d.get("identifications") or [None])[0]
            res = ((ident or {}).get("resolutions") or [None])[0]
            if not res or not res.get("lonlat"):
                continue
            lon, lat = (float(x) for x in res["lonlat"].split(","))
            base = re.sub(r" \d+$", "", d["friendly_id"])
            by_name.setdefault(base, []).append((len(d.get("verses", [])), lon, lat))
    out = []
    for names, stops in ROUTES:
        pts, prev = [], None
        for stop in stops:
            cands = by_name.get(stop)
            if not cands:
                raise SystemExit(f"route place not found: {stop}")
            if prev is None:
                best = max(cands)
            else:
                best = min(cands, key=lambda c: (c[1] - prev[0]) ** 2 + (c[2] - prev[1]) ** 2)
            prev = (best[1], best[2])
            pts.append(prev)
        out.append({"name": list(names), "p": [round(v, 3) for p in pts for v in p]})
    return out

# Journeys told in the Bible, through their stops in the order of the text
# (OpenBible.info names; a stop is resolved by its exact name). The line joins
# the stops: it is not the exact way taken. Names: English, French, Malagasy.
# ref: (book, chapter, verse) where the journey begins in the text (Malagasy 1865 numbering).
JOURNEYS = [
    ("abraham", ("Abraham: from Ur to Canaan", "Abraham : d’Ur à Canaan", "Abrahama: avy any Ora ho any Kanana"), (1, 11, 31),
     ["Ur 2", "Haran", "Shechem", "Bethel 1", "Egypt", "Bethel 1", "Hebron"]),
    ("jacob", ("Jacob: to Haran and back", "Jacob : vers Charan et retour", "Jakoba: nankany Harana sy niverina"), (1, 28, 10),
     ["Beersheba 1", "Bethel 1", "Haran", "Mahanaim", "Penuel", "Succoth 1", "Shechem", "Bethel 1", "Bethlehem 1", "Hebron"]),
    ("joseph", ("Joseph sold into Egypt", "Joseph vendu en Égypte", "Josefa namidy ho any Egypta"), (1, 37, 14),
     ["Hebron", "Shechem", "Dothan", "Egypt"]),
    ("exodus", ("The Exodus (traditional route)", "L’Exode (itinéraire traditionnel)", "Ny Fivoahana (lalana nentim-paharazana)"), (2, 12, 37),
     ["Rameses", "Succoth 2", "Etham", "Pi-hahiroth", "Marah", "Elim", "Rephidim", "Mount Sinai", "Ezion-geber", "Kadesh-barnea",
      "Mount Hor 1", "Dibon 1", "Mount Nebo", "Shittim"]),
    ("conquest", ("Into the Promised Land", "L’entrée en Canaan", "Ny fidirana tany Kanana"), (6, 3, 1),
     ["Shittim", "Gilgal 1", "Jericho 1", "Ai 1", "Shechem"]),
    ("elijah", ("Elijah: from Carmel to Horeb", "Élie : du Carmel à l’Horeb", "Elia: avy any Karmela ho any Horeba"), (11, 18, 19),
     ["Mount Carmel", "Jezreel 2", "Beersheba 1", "Mount Sinai"]),
    ("nativity", ("Jesus: birth and childhood", "Jésus : naissance et enfance", "Jesosy: fahaterahana sy fahazazana"), (42, 2, 4),
     ["Nazareth", "Bethlehem 1", "Egypt", "Nazareth"]),
    ("last_journey", ("Jesus goes up to Jerusalem", "Jésus monte à Jérusalem", "Jesosy niakatra ho any Jerosalema"), (40, 19, 1),
     ["Capernaum", "Jericho 1", "Bethany 1", "Bethphage", "Jerusalem"]),
    ("emmaus", ("The road to Emmaus", "Le chemin d’Emmaüs", "Ny lalana ho any Emaosy"), (42, 24, 13),
     ["Jerusalem", "Emmaus"]),
    ("philip", ("Philip the evangelist", "Philippe l’évangéliste", "Filipo mpitory filazantsara"), (44, 8, 5),
     ["Jerusalem", "Samaria 1", "Gaza", "Ashdod", "Caesarea"]),
    ("damascus", ("Saul on the road to Damascus", "Saul sur le chemin de Damas", "Saoly teny an-dalana ho any Damaskosy"), (44, 9, 1),
     ["Jerusalem", "Damascus"]),
    ("paul1", ("Paul’s first journey", "Premier voyage de Paul", "Dia voalohany nataon’i Paoly"), (44, 13, 4),
     ["Antioch 1", "Seleucia", "Salamis", "Paphos", "Perga", "Antioch 2", "Iconium", "Lystra", "Derbe", "Lystra", "Iconium", "Antioch 2",
      "Perga", "Attalia", "Antioch 1"]),
    ("paul2", ("Paul’s second journey", "Deuxième voyage de Paul", "Dia faharoa nataon’i Paoly"), (44, 15, 36),
     ["Antioch 1", "Derbe", "Lystra", "Troas", "Samothrace", "Neapolis", "Philippi", "Amphipolis", "Apollonia", "Thessalonica",
      "Berea", "Athens", "Corinth", "Cenchreae", "Ephesus", "Caesarea", "Antioch 1"]),
    ("paul3", ("Paul’s third journey", "Troisième voyage de Paul", "Dia fahatelo nataon’i Paoly"), (44, 18, 23),
     ["Antioch 1", "Ephesus", "Troas", "Philippi", "Corinth", "Philippi", "Troas", "Assos", "Mitylene", "Chios", "Samos", "Miletus", "Cos",
      "Rhodes 1", "Patara", "Tyre", "Ptolemais", "Caesarea", "Jerusalem"]),
    ("rome", ("Paul sails to Rome", "Paul en route vers Rome", "Paoly nankany Roma"), (44, 27, 1),
     ["Caesarea", "Sidon", "Myra", "Cnidus", "Fair Havens", "Cauda", "Malta", "Syracuse", "Rhegium", "Puteoli", "Forum of Appius",
      "Three Taverns", "Rome"]),
]

# Kinds of place drawn as an area when OpenBible.info has an outline for them.
REGION_TYPES = {"region", "valley", "mountain range", "people group", "natural area", "island", "body of water"}


def _geo_rows(geo_path):
    with open(geo_path, encoding="utf-8") as f:
        for line in f:
            yield json.loads(line)


def build_journeys(geo_path):
    """Resolve each journey's stops (exact OpenBible names) to place ids and coordinates."""
    by_name = {}
    for d in _geo_rows(geo_path):
        ident = (d.get("identifications") or [None])[0]
        res = ((ident or {}).get("resolutions") or [None])[0]
        if res and res.get("lonlat"):
            lon, lat = (float(x) for x in res["lonlat"].split(","))
            by_name[d["friendly_id"]] = (d["id"], lon, lat)
    out = []
    for jid, names, ref, stops in JOURNEYS:
        ids, pts = [], []
        for stop in stops:
            if stop not in by_name:
                raise SystemExit(f"journey {jid}: place not found: {stop}")
            pid, lon, lat = by_name[stop]
            ids.append(pid)
            pts += [round(lon, 3), round(lat, 3)]
        out.append({"id": jid, "name": list(names), "ref": list(ref), "stops": ids, "p": pts})
    return out


def build_regions(geo_path, geometry_index_path, fetch_geometry):
    """
    place id -> outline, for regions that OpenBible.info draws as an area.
    "b": 1 when the outline is a set of nested bands of decreasing likelihood
    (isobands: the region's extent is uncertain), 0 for a single rough boundary.
    "r": the rings (outermost band first).
    """
    index = {g["id"]: g for g in _geo_rows(geometry_index_path)}
    out = {}
    for d in _geo_rows(geo_path):
        ptype = (d.get("types") or [""])[0]
        if ptype not in REGION_TYPES:
            continue
        ident = (d.get("identifications") or [None])[0]
        res = ((ident or {}).get("resolutions") or [None])[0] or {}
        geom = None
        for key in ("geometry_id", "precise_geometry_id"):
            g = index.get(res.get(key) or "")
            if g and g.get("geometry") in ("isobands", "rough_boundary"):
                geom = g
                break
        if not geom:
            continue
        bands = geom["geometry"] == "isobands"
        name = geom.get("isobands_geojson_file") if bands else geom.get("geojson_file")
        rings = []
        if name:
            with open(fetch_geometry(name), encoding="utf-8") as f:
                data = json.load(f)
            feats = data["features"] if data.get("type") == "FeatureCollection" else [data]
            for feat in feats:
                for ring in _rings(feat["geometry"]):
                    pts = [(float(x), float(y)) for x, y, *_ in ring]
                    if len(pts) >= 4:
                        rings.append(pts)
        elif (geom.get("suggested") or {}).get("rough_boundary"):
            rings.append([tuple(float(v) for v in q.split(",")) for q in geom["suggested"]["rough_boundary"]])
        if not rings:
            continue
        # outermost first (largest extent), so inner bands draw over outer ones
        rings.sort(key=lambda r: -(max(p[0] for p in r) - min(p[0] for p in r)) * (max(p[1] for p in r) - min(p[1] for p in r)))
        simple = []
        for r in rings:
            span = max(max(p[0] for p in r) - min(p[0] for p in r), max(p[1] for p in r) - min(p[1] for p in r))
            r2 = _simplify_ring(r, max(0.002, span / 150))
            if len(r2) >= 4:
                simple.append([round(v, 3) for p in r2 for v in p])
        if simple:
            out[d["id"]] = {"b": 1 if bands and len(simple) > 1 else 0, "r": simple}
    return out


# Bible lands, from Rome to Persia and from the Black Sea to Upper Egypt.
BBOX = (8.0, 22.0, 56.0, 44.0)  # lon_min, lat_min, lon_max, lat_max

TSK_BOOKS = ("Ge Ex Le Nu De Jos Jud Ru 1Sa 2Sa 1Ki 2Ki 1Ch 2Ch Ezr Ne Es Job Ps Pr Ec So Isa Jer La Eze Da Ho "
             "Joe Am Ob Jon Mic Na Hab Zep Hag Zec Mal Mt Mr Lu Joh Ac Ro 1Co 2Co Ga Eph Php Col 1Th 2Th 1Ti "
             "2Ti Tit Phm Heb Jas 1Pe 2Pe 1Jo 2Jo 3Jo Jude Re").split()
TSK_IDS = {a: i + 1 for i, a in enumerate(TSK_BOOKS)}


def _collapse(s):
    return re.sub(r"\s+", " ", s).strip()


# ---------------------------------------------------------------- KJV <-> Malagasy numbering

class Numbering:
    """KJV verse -> Malagasy 1865 verse, from the verse_map rows built for study.db."""

    def __init__(self, forward):
        # forward: {(b, c, v_mg): (c_kjv, v_kjv)}
        self.inverse = {}
        for (b, c, v), (kc, kv) in sorted(forward.items()):
            self.inverse[(b, kc, kv)] = (c, v)  # the later Malagasy verse wins (title + v1 -> KJV 1)

    def to_mg(self, b, c, v):
        return self.inverse.get((b, c, v), (c, v))


# ---------------------------------------------------------------- TSK

def _ztext_entries(zf, data_path, canon, osis_ids, encoding="utf-8"):
    """(book, chapter, verse, raw) for a SWORD zText/zCom module."""
    data_path = data_path.lstrip("./").rstrip("/") + "/"
    for t in ("ot", "nt"):
        bzs, bzv, bzz = (zf.read(data_path + t + ext) for ext in (".bzs", ".bzv", ".bzz"))
        cache = {}

        def entry(i):
            b, o, s = struct.unpack("<IIH", bzv[i * 10:i * 10 + 10])
            if s == 0:
                return ""
            if b not in cache:
                bo, bs, _ = struct.unpack("<III", bzs[b * 12:b * 12 + 12])
                cache[b] = zlib.decompress(bzz[bo:bo + bs])
            return cache[b][o:o + s].decode(encoding, "replace")

        i = 2
        for (_name, osis, counts) in canon[t]:
            i += 1
            for ch, n in enumerate(counts, start=1):
                i += 1
                for v in range(1, n + 1):
                    yield osis_ids[osis], ch, v, entry(i)
                    i += 1


def parse_tsk_refs(text, book=None):
    """'Pr 8:22-24; 16:4; Mr 13:19; Joh 1:1-3' -> [(book, chapter, v1, v2)]"""
    out = []
    chapter = None
    for part in text.split(";"):
        part = part.strip()
        if not part:
            continue
        m = re.match(r"^((?:[123] ?)?[A-Za-z]+)\s+(.*)$", part)
        if m and m.group(1).replace(" ", "") in TSK_IDS:
            book = TSK_IDS[m.group(1).replace(" ", "")]
            part = m.group(2)
            chapter = None
        if book is None:
            continue
        for item in part.split(","):
            item = item.strip()
            mm = re.match(r"^(\d+):(\d+)(?:-(\d+))?$", item)
            if mm:
                chapter = int(mm.group(1))
                v1 = int(mm.group(2))
                out.append((book, chapter, v1, int(mm.group(3) or v1)))
                continue
            mm = re.match(r"^(\d+)(?:-(\d+))?$", item)
            if mm and chapter is not None:
                v1 = int(mm.group(1))
                out.append((book, chapter, v1, int(mm.group(2) or v1)))
    return out


def load_tsk(path, canon, osis_ids, numbering):
    zf = zipfile.ZipFile(path)
    rows = []
    for b, c, v, raw in _ztext_entries(zf, "modules/comments/zcom/tsk/", canon, osis_ids):
        if not raw or b > 66:
            continue
        mc, mv = numbering.to_mg(b, c, v)
        # keyword lines: "beginning.<br /><scripRef>Pr 8:22-24; ...</scripRef>"
        body = raw.split("<br />\n<br />\n<br />", 1)[-1]
        grp = 0
        for m in re.finditer(r"(?:<br />)?([^<]*?)<br /><scripRef>(.*?)</scripRef>", body, re.S):
            keyword = _collapse(html.unescape(m.group(1))).rstrip(".")
            refs = parse_tsk_refs(html.unescape(m.group(2)))
            if not refs:
                continue
            grp += 1
            for (tb, tc, t1, t2) in refs:
                tc1, tv1 = numbering.to_mg(tb, tc, t1)
                _, tv2 = numbering.to_mg(tb, tc, t2)
                rows.append((b, mc, mv, grp, keyword, tb, tc1, tv1, max(tv1, tv2)))
    return rows


# ---------------------------------------------------------------- dictionaries

def _tei_to_markup(body):
    body = re.sub(r"<title>.*?</title>", "", body, flags=re.S)
    body = re.sub(r'<ref\s+osisRef="(?:Bible:)?([^"]+)"\s*>(.*?)</ref>',
                  lambda m: "⟦" + m.group(1) + "|" + _collapse(re.sub("<[^>]+>", "", m.group(2))) + "⟧", body, flags=re.S)
    body = re.sub(r'<ref\s+target="[^":]*:([^"]+)"\s*>(.*?)</ref>',
                  lambda m: "⟦@" + m.group(1) + "|" + _collapse(re.sub("<[^>]+>", "", m.group(2))) + "⟧", body, flags=re.S)
    body = re.sub(r"<lb\s*/>", "\n", body)
    body = re.sub(r"</p>\s*<p>", "\n\n", body)
    body = re.sub(r"<[^>]+>", "", body)
    body = html.unescape(body)
    body = re.sub(r"[ \t]+", " ", body)
    body = re.sub(r"\n[ \t]+", "\n", body)
    body = re.sub(r"\n{3,}", "\n\n", body)
    return body.strip()


def load_zld_dict(path, read_zld, conf_of):
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    base = conf["DataPath"].lstrip("./")
    entries = []
    for key, body in read_zld(zf, base):
        text = _tei_to_markup(body)
        if text:
            entries.append((key.strip(), text))
    return conf, entries


def load_rawld4_or_rawld(path, conf_of):
    """RawLD: .idx = (u32 offset, u16 size) records; .dat = 'KEY\\r\\n' + body."""
    zf = zipfile.ZipFile(path)
    conf = conf_of(zf)
    base = conf["DataPath"].lstrip("./")
    idx, dat = zf.read(base + ".idx"), zf.read(base + ".dat")
    entries = []
    for i in range(len(idx) // 6):
        off, size = struct.unpack("<IH", idx[i * 6:i * 6 + 6])
        rec = dat[off:off + size].decode("utf-8", "replace")
        if "\n" not in rec:
            continue
        key, body = rec.split("\n", 1)
        text = _tei_to_markup(body)
        if key.strip() and text:
            entries.append((key.strip(), text))
    return conf, entries


# ---------------------------------------------------------------- places

def load_places(path, osis_ids, numbering):
    places, links = [], []
    with open(path, encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            ident = (d.get("identifications") or [None])[0]
            if not ident:
                continue
            res = (ident.get("resolutions") or [None])[0]
            if not res or not res.get("lonlat"):
                continue
            lon, lat = (float(x) for x in res["lonlat"].split(","))
            score = (ident.get("score") or {}).get("vote_total", 0) or 0
            confidence = 3 if score >= 500 else 2 if score >= 200 else 1
            ptype = (d.get("types") or [""])[0]
            places.append((d["id"], d["friendly_id"], ptype, round(lon, 5), round(lat, 5), confidence))
            seen = set()
            for v in d.get("verses", []):
                m = re.match(r"^(\w+)\.(\d+)\.(\d+)$", v.get("osis", ""))
                if not m or m.group(1) not in osis_ids:
                    continue
                b = osis_ids[m.group(1)]
                c, vv = numbering.to_mg(b, int(m.group(2)), int(m.group(3)))
                if (b, c, vv) not in seen:
                    seen.add((b, c, vv))
                    links.append((d["id"], b, c, vv))
    return places, links


# ---------------------------------------------------------------- map outlines

def _simplify(points, tol):
    """Douglas-Peucker on a list of (lon, lat)."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (x1, y1), (x2, y2) = points[a], points[b]
        dx, dy = x2 - x1, y2 - y1
        norm = (dx * dx + dy * dy) ** 0.5 or 1e-12
        best, idx = 0.0, -1
        for i in range(a + 1, b):
            x, y = points[i]
            d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / norm
            if d > best:
                best, idx = d, i
        if best > tol and idx > 0:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(points, keep) if k]


def _simplify_ring(points, tol):
    """Closed ring: split at the point farthest from the start, simplify both halves."""
    if len(points) < 4:
        return points
    x0, y0 = points[0]
    far = max(range(len(points)), key=lambda i: (points[i][0] - x0) ** 2 + (points[i][1] - y0) ** 2)
    a = _simplify(points[:far + 1], tol)
    b = _simplify(points[far:], tol)
    return a[:-1] + b


def _clip_polygon(points, box):
    """Sutherland-Hodgman clipping of a polygon to a rectangle."""
    x1, y1, x2, y2 = box

    def clip(pts, inside, cross):
        out = []
        for i, cur in enumerate(pts):
            prev = pts[i - 1]
            if inside(cur):
                if not inside(prev):
                    out.append(cross(prev, cur))
                out.append(cur)
            elif inside(prev):
                out.append(cross(prev, cur))
        return out

    def at_x(x):
        return lambda p, q: (x, p[1] + (q[1] - p[1]) * (x - p[0]) / ((q[0] - p[0]) or 1e-12))

    def at_y(y):
        return lambda p, q: (p[0] + (q[0] - p[0]) * (y - p[1]) / ((q[1] - p[1]) or 1e-12), y)

    pts = points
    for inside, cross in ((lambda p: p[0] >= x1, at_x(x1)), (lambda p: p[0] <= x2, at_x(x2)),
                          (lambda p: p[1] >= y1, at_y(y1)), (lambda p: p[1] <= y2, at_y(y2))):
        if not pts:
            break
        pts = clip(pts, inside, cross)
    return pts


def _in_bbox(points, margin=2.0):
    lo1, la1, lo2, la2 = BBOX
    return any(lo1 - margin <= x <= lo2 + margin and la1 - margin <= y <= la2 + margin for x, y in points)


def _rings(geom):
    if geom["type"] == "Polygon":
        yield from geom["coordinates"]
    elif geom["type"] == "MultiPolygon":
        for poly in geom["coordinates"]:
            yield from poly
    elif geom["type"] == "LineString":
        yield geom["coordinates"]
    elif geom["type"] == "MultiLineString":
        yield from geom["coordinates"]


def build_map(land_path, lakes_path, rivers_path, out_path, roads_path=None, realms=None, countries_path=None, cities_path=None, routes=None,
              regions=None, journeys=None):
    def layer(path, tol, keep=lambda props: True, min_points=3, polygon=True):
        out = []
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for feat in data["features"]:
            if not keep(feat.get("properties") or {}):
                continue
            for ring in _rings(feat["geometry"]):
                pts = [(float(x), float(y)) for x, y, *_ in ring]
                if not _in_bbox(pts):
                    continue
                if polygon:
                    lo1, la1, lo2, la2 = BBOX
                    pts = _clip_polygon(pts[:-1] if pts[0] == pts[-1] else pts, (lo1 - 1, la1 - 1, lo2 + 1, la2 + 1))
                    if len(pts) < 3:
                        continue
                    pts = _simplify_ring(pts + [pts[0]], tol)
                else:
                    pts = _simplify(pts, tol)
                if len(pts) >= min_points:
                    out.append([round(v, 3) for p in pts for v in p])
        return out

    big_rivers = {"Jordan", "Nile", "Euphrates", "Tigris", "Orontes", "Litani", "Yarmouk", "Arnon", "Jabbok"}
    result = {
        "bbox": BBOX,
        "land": layer(land_path, 0.01),
        "lakes": layer(lakes_path, 0.004, keep=lambda p: (p.get("scalerank") or 9) <= 9),
        "rivers": layer(rivers_path, 0.006, min_points=2, polygon=False,
                        keep=lambda p: any(r in (p.get("name") or "") for r in big_rivers)),
        "credit": "Natural Earth (public domain)",
    }
    if roads_path:
        # Roman roads (AWMC): major roads kept apart so they can be drawn bolder
        roads = []
        with open(roads_path, encoding="utf-8") as f:
            for feat in json.load(f)["features"]:
                major = 1 if (feat.get("properties") or {}).get("Major_or_M") == "1" else 0
                for line in _rings(feat["geometry"]):
                    pts = [(float(x), float(y)) for x, y, *_ in line]
                    if not _in_bbox(pts, margin=0):
                        continue
                    pts = _simplify(pts, 0.01)
                    if len(pts) >= 2:
                        roads.append({"m": major, "p": [round(v, 3) for q in pts for v in q]})
        result["roads"] = roads
    if routes:
        result["routes"] = routes
    if regions:
        result["regions"] = regions
    if journeys:
        result["journeys"] = journeys
    if realms:
        result["realms"] = {k: layer(path, 0.02) for k, path in realms.items()}
    if countries_path:
        result["countries"] = layer(countries_path, 0.02)
        labels = []
        lo1, la1, lo2, la2 = BBOX
        with open(countries_path, encoding="utf-8") as f:
            for feat in json.load(f)["features"]:
                p = feat["properties"]
                x, y = p.get("LABEL_X"), p.get("LABEL_Y")
                if x is None or not (lo1 <= x <= lo2 and la1 <= y <= la2):
                    continue
                labels.append({"en": p.get("NAME_EN") or p["NAME"], "fr": p.get("NAME_FR") or p["NAME"], "x": round(x, 2), "y": round(y, 2),
                               "r": p.get("LABELRANK") or 5})
        result["country_labels"] = labels
    if cities_path:
        cities = []
        lo1, la1, lo2, la2 = BBOX
        with open(cities_path, encoding="utf-8") as f:
            for feat in json.load(f)["features"]:
                p = feat["properties"]
                x, y = feat["geometry"]["coordinates"][:2]
                if not (lo1 <= x <= lo2 and la1 <= y <= la2) or (p.get("SCALERANK") or 10) > 4:
                    continue
                cities.append({"en": p.get("NAME_EN") or p["NAME"], "fr": p.get("NAME_FR") or p["NAME"], "x": round(x, 3), "y": round(y, 3),
                               "r": p.get("SCALERANK") or 4})
        result["cities"] = cities
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, separators=(",", ":"))
    return result


def build_relief(tif_path, out_path):
    """Natural Earth shaded relief cut to the map's extent, as a greyscale JPEG.

    Flat ground and water (value 206 in SR_LR) become white, so the app can
    lay the image over its own land colour with a multiply blend."""
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
    im = Image.open(tif_path)
    w, h = im.size
    lo1, la1, lo2, la2 = BBOX
    box = (round((lo1 + 180) * w / 360), round((90 - la2) * h / 180), round((lo2 + 180) * w / 360), round((90 - la1) * h / 180))
    crop = im.crop(box).convert("L")
    lut = [255 if v >= 206 else round(255 * (v / 206) ** 1.8) for v in range(256)]
    crop.point(lut).save(out_path, quality=88)
    return crop.size


# ---------------------------------------------------------------- library.db

def build_library(db_path, tsk_rows, dicts, places, place_links):
    if os.path.exists(db_path):
        os.remove(db_path)
    db = sqlite3.connect(db_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
        -- One row per TSK keyword group; refs = space-separated "book.chapter.v1[-v2]"
        -- in Malagasy 1865 numbering.
        CREATE TABLE xref(book INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            grp INTEGER NOT NULL, keyword TEXT NOT NULL, refs TEXT NOT NULL,
            PRIMARY KEY(book, chapter, verse, grp)) WITHOUT ROWID;
        CREATE TABLE dict_source(id TEXT PRIMARY KEY, name TEXT NOT NULL, lang TEXT NOT NULL, kind TEXT NOT NULL,
            licence TEXT NOT NULL, url TEXT NOT NULL, sort INTEGER NOT NULL) WITHOUT ROWID;
        CREATE TABLE dict_entry(source TEXT NOT NULL, key TEXT NOT NULL, key_norm TEXT NOT NULL, body TEXT NOT NULL,
            PRIMARY KEY(source, key)) WITHOUT ROWID;
        CREATE INDEX dict_by_norm ON dict_entry(source, key_norm);
        CREATE TABLE place(id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, lon REAL NOT NULL,
            lat REAL NOT NULL, confidence INTEGER NOT NULL,
            mentions INTEGER NOT NULL, periods INTEGER NOT NULL, period_mentions TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE place_verse(place TEXT NOT NULL, book INTEGER NOT NULL, chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL, PRIMARY KEY(place, book, chapter, verse)) WITHOUT ROWID;
        CREATE INDEX place_by_chapter ON place_verse(book, chapter);
    """)
    groups = {}
    for (b, c, v, grp, keyword, tb, tc, t1, t2) in tsk_rows:
        g = groups.setdefault((b, c, v, grp), [keyword, []])
        g[1].append(f"{tb}.{tc}.{t1}" + (f"-{t2}" if t2 != t1 else ""))
    db.executemany("INSERT INTO xref VALUES(?,?,?,?,?,?)",
                   [(b, c, v, grp, kw, " ".join(refs)) for (b, c, v, grp), (kw, refs) in groups.items()])
    for sort, (sid, name, lang, kind, licence, url, entries, norm) in enumerate(dicts):
        db.execute("INSERT INTO dict_source VALUES(?,?,?,?,?,?,?)", (sid, name, lang, kind, licence, url, sort))
        seen = set()
        for key, body in entries:
            if key in seen:
                continue
            seen.add(key)
            db.execute("INSERT INTO dict_entry VALUES(?,?,?,?)", (sid, key, norm(key), body))
    # mentions: how many verses name the place (its weight on the map); periods: see PERIODS
    stats = place_stats(place_links)
    empty = (0, 0, ",".join("0" * len(PERIODS)))
    db.executemany("INSERT INTO place VALUES(?,?,?,?,?,?,?,?,?)", [p + stats.get(p[0], empty) for p in places])
    db.executemany("INSERT OR IGNORE INTO place_verse VALUES(?,?,?,?)", place_links)
    db.commit()
    db.execute("VACUUM")
    db.close()
