# Ideas for future development

Brainstorm notes. Unprioritised, not committed to any release.
Dates and details marked [VERIFY] come from memory and must be checked against a source before any data is entered.

## A. Study library (study mode, "go deeper" section)

Common to A1-A2: each text or event is linked to the Bible passages it cites or discusses; every source and translation gets its licence checked (see `data-licences.md`); the Reformed standpoint of the app is stated openly, while other traditions are described on their own terms. [EXTRA-CONFESSIONAL]

### A1. Confessions and catechisms

- Confessions of each tradition (Protestant, Catholic, Orthodox), shown according to the tradition setting (A3). **Malagasy** texts are a priority.
- Reformed and Catholic lists are filled in; the Orthodox list is still to do.
- Open points:
  - Which Malagasy confessions and catechisms exist, which church owns them, under what licence. Unknown. [VERIFY]
  - Original-language text vs. translations (Malagasy, French, English) of the same document.
  - Cross-linking: article or question ↔ proof texts already in the app.

**Reformed confessions to include** [VERIFY all dates]

| Group | Documents |
|---|---|
| Three Forms of Unity (Continental) | Belgic Confession (1561), Heidelberg Catechism (1563), Canons of Dort (1618-19) |
| Westminster Standards (Presbyterian) | Westminster Confession of Faith (1646), Larger Catechism and Shorter Catechism (1647) |
| Other Reformation confessions | Second Helvetic Confession (1566), Scots Confession (1560), French (Gallic) Confession (1559), Geneva Catechism (Calvin, 1545) |
| Related Reformed families | Savoy Declaration (1658, Congregationalist), Second London Baptist Confession (1689, Reformed Baptist) |

- Debated whether to include: the Thirty-Nine Articles (Anglican, Reformed in content, but a different tradition). [INTRA-REFORMED DEBATE]
- Present each as a document with structure (articles, chapters, questions and answers), so it can be cited the way the user cites them: `[WCF X.Y]`, `[HC Q&A N]`.

**Catholic doctrinal documents to include** [VERIFY all dates]

Catholic teaching is not centred on "confessions" in the Reformed sense. Its standards are mainly conciliar decrees and catechisms, so this list overlaps with A2 (councils); the same document should be one entry linked from both.

| Group | Documents |
|---|---|
| Creeds shared with other traditions | Apostles' Creed, Nicene-Constantinopolitan Creed |
| Council of Trent and after | Decrees and canons of the Council of Trent (1545-63), Profession of the Tridentine Faith (Creed of Pius IV, 1564), Roman Catechism (Catechism of the Council of Trent, 1566) |
| First Vatican Council | Dei Filius and Pastor Aeternus (1870) |
| Second Vatican Council | Dei Verbum, Lumen Gentium, Sacrosanctum Concilium, Gaudium et Spes (1962-65) |
| Modern catechisms | Catechism of the Catholic Church (1992; typical edition 1997), Compendium of the Catechism (2005) |

- Malagasy translations (e.g. of the Catechism of the Catholic Church) and who holds their rights: unknown. [VERIFY]
- Official Latin text vs. vernacular translations; check the licence of each translation before use.

### A2. Church councils

- For each council: date, place, what it dealt with, who attended, what it decided (creeds, canons), why it matters.
- Scope: the ecumenical councils (Nicaea 325 to Nicaea II 787) are the core. Regional and Reformation-era synods (e.g. Dort) are an open choice. [VERIFY list and dates]
- Present decisions as history, separate from how each tradition rates the council's authority. The Reformed view puts councils below Scripture (cf. WCF XXXI.3). [INTRA-REFORMED DEBATE] [VERIFY]
- Sources: primary texts (canons, creeds) and reputable scholarly summaries. Malagasy translations of creeds or canons: unknown. [VERIFY]

### A3. Tradition as the master setting

Decided by the user:
- The setting has three values: **Protestant / Catholic / Orthodox**. Today the app has Protestant and Catholic only (Advanced settings); Orthodox is to be added.
- The **tradition setting governs every part of the app**: Bible canon and offered translations, confessions, councils, and so on.
- **The councils and confessions of the selected tradition show in normal study mode.**
- **Other traditions' councils and confessions appear only in the "go deeper" section of study mode**, and each item is **clearly marked** as belonging to another tradition.

Open points:
- "Protestant" covers Reformed, Lutheran, Anglican and Baptist, which differ in confessions. Decide how confessions are shown inside Protestant (all together, grouped by family, with Reformed first as the app's standpoint). [VERIFY]
- How "marked as such" looks: a label naming the tradition, e.g. "Catholic: Council of Trent", plus a short neutral note on that tradition's view of its authority.
- Councils: the Orthodox count only the first seven as ecumenical; Catholics count more. Each tradition's own count drives its normal-mode list. [VERIFY]
- Oriental Orthodox (Coptic, Ethiopian, Armenian, Syriac) are a separate family from Eastern Orthodox, with different canons and a different view of Chalcedon. Decide whether they fall under "Orthodox" or get separate entries in go deeper. [VERIFY]
- Malagasy context: Orthodox presence in Madagascar is very small, if any, so this is for completeness and study. [VERIFY]

## B. Bible content

### B1. Biblical character family tree

- Genealogy graph linked to verses. Sources: Gen 5 and 11, 1 Chr 1-9, Matt 1, Luke 3, plus narrative relations (marriages, siblings).
- Data model: person, relation (parent, spouse), source verse(s), confidence.
- Genealogies can skip generations ("son of" can mean descendant), so the tree must not imply strict generation counts.
- Same name for different people, one person under several names; variants across Malagasy, French and English.
- Where manuscript traditions differ (e.g. Luke 3:36), record the variant rather than pick one silently. [VERIFY]

### B2. Bible chronology

- Timeline of events and people, linked to verses and to the family tree (B1).
- Dates are disputed: the Masoretic, Septuagint and Samaritan texts give different figures for the early genealogies, and the Exodus date is debated (early vs. late). [INTRA-REFORMED DEBATE]
- Show each date with its source and a range or alternatives, not a single "true" date; label what Scripture states and what is scholarly reconstruction.
- Choose which chronologies to include (Ussher is one historical option, not the only one). [VERIFY]

### B3. Canon (driven by the tradition setting, see A3)

- The canon follows the tradition setting; it is not a separate switch. The Protestant/Catholic version already exists (Bibles offered, deuterocanonical books shown or hidden). An Orthodox/Septuagint canon would be new and larger. [VERIFY]
- At least three Old Testament canons: Protestant 39, Catholic 46, Orthodox/LXX larger. No single closed Orthodox decree; the Synod of Jerusalem (1672) is the key statement. [VERIFY]
- The Septuagint also differs from the Hebrew text in wording and Psalm numbering. [VERIFY]
- Reformed position for reference: the extra books are not canonical (WCF I.3). [VERIFY]

## Status (release 1.6.0 branch)

Implemented: B1, B2, A1 and A2 in part. Not started: A3 Orthodox value, B3.

**B1 Family tree** (Dictionary page › *People*; Study panel › *People and events*)
- Source: Theographic Bible Metadata (CC BY-SA 4.0): 3,068 people, 8,926 family links (father, mother, spouses, brothers and sisters, half-siblings, children), every verse naming each person.
- Shows family groups, the father line upwards and a note that genealogies can skip generations.
- Names are English (KJV) only. Malagasy and French name variants are still open, and so is a recorded variant for Luke 3:36. [VERIFY]

**B2 Chronology** (Dictionary page › *Timeline*)
- 501 events. The year follows F. N. Jones. Where it differs, the Ussher year from the Treasury of Scripture Knowledge margin is shown as a second date.
- Each event is labelled as a reconstruction. Ranges and the Septuagint and Samaritan figures are not in the data. Open. [VERIFY]

**A1 Confessions and catechisms** (Dictionary page › *Confessions*; Study panel › *Confessions*: articles that cite the passage)
- Included (historic English texts, public domain): Apostles' Creed (as Heidelberg Q&A 23 gives it), Nicene (Western form) and Athanasian Creeds, Belgic Confession, Heidelberg Catechism, Canons of Dort, Westminster Confession, Larger and Shorter Catechisms, French Confession (Schaff), Savoy Declaration, 1689 Baptist Confession.
- Sources: Reformed Standards compendium (Apache 2.0) and Creeds.json. 9,096 proof-text links, cited as `WCF 1.2`, `HC Q&A 23`.
- Left out:
  - Copyright: the Second Helvetic Confession, Chalcedonian Definition and Council of Orange (modern translations in both sources), the ELLC Apostles' Creed, and the CRC/Canadian Reformed texts that Creeds.json labels "Public Domain".
  - Debated: the Thirty-Nine Articles.
- Catholic and Orthodox documents (in *Go deeper* for Protestants, in normal study for Catholics): the Canons and Decrees of Trent (Waterworth 1848), the Profession of the Tridentine Faith, *Ineffabilis Deus* (1854) and the Vatican Council's decrees (1870), and the Longer Catechism of the Orthodox Church (Philaret, tr. Blackmore 1845, all 610 questions); with the Definition of Chalcedon. Source: Schaff, *Creeds of Christendom* II (1877), CCEL, public domain. Still out: the Orthodox Confession (1643) and the Confession of Dositheus (1672), Greek and Latin only in Schaff; Vatican II and the Catechism of the Catholic Church (Vatican copyright).

**A2 Councils**
- The seven ecumenical councils (Nicaea 325 to Nicaea II 787), Trent and the Vatican Council, then Dort, Westminster and Savoy: place, year, a neutral summary after Schaff, and the documents of the library each issued.
- Each council records the traditions that receive it: the first six all; Nicaea II the Orthodox and Catholic Churches; Trent and Vatican I the Catholic; the Reformed assemblies the Reformed. Those of the chosen tradition show in normal study, the others under *Go deeper*.

**A3 Tradition**
- Protestant: creeds and Reformed documents show in normal study.
- Catholic: the creeds show in normal study. The Reformed documents appear only under *Go deeper: other traditions*, labelled "Protestant (Reformed)".
