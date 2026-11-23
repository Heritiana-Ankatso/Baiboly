# Ideas for future development

Brainstorm notes. Unprioritised, not committed to any release.
Dates and details marked [VERIFY] come from memory and must be checked against a source before any data is entered.

## A. Study library (study mode, "go deeper" section)

Common to A1-A3: each text or event is linked to the Bible passages it cites or discusses; every source and translation gets its licence checked (see `data-licences.md`); the Reformed standpoint of the app is stated openly, while other traditions are described on their own terms. [EXTRA-CONFESSIONAL]

### A1. Confessions and catechisms

- Many traditions, with **Malagasy** texts as a priority.
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
