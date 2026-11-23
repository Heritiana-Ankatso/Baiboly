# Ideas for future development

Unprioritised. Not committed to any release.

## 1. Confessions and catechisms

- Support many traditions, not only Reformed (WCF, Heidelberg, Belgic, Canons of Dort, etc.).
- Include **Malagasy** confessions/catechisms as a priority.
- Open points:
  - Which Malagasy texts exist, which church owns them, and under what licence (see `data-licences.md`). [VERIFY]
  - Original-language text vs. translation; Malagasy/French/English versions of the same document.
  - Cross-linking: confession/catechism article ↔ Bible verses (proof texts) already in the app.

## 2. Hymnals (on hold: set aside by the user for now)

- **Fihirana FFPM** and **Fihirana Fanampiny** (Malagasy).
- Open points:
  - Copyright/permission for each hymnal; older texts may be public domain, recent ones probably are not. [VERIFY]
  - Whether English/French equivalents are wanted at all, or whether this is Malagasy-only.
  - Data format: number, title, verses, refrain, tune/metre, author, source book.
  - Search by number, title, first line; link to Bible references.

## 3. Biblical character family tree

- Genealogy graph of people in the Bible, linked to verses and to the app's existing text.
- Main sources: Gen 5 and 11, 1 Chr 1-9, Matt 1, Luke 3, plus narrative relations (marriages, siblings).
- Open points:
  - Data model: person, relation (parent, spouse), source verse(s), confidence.
  - Genealogies can skip generations ("son of" can mean descendant), so the tree must not imply strict generation counts.
  - Same name for different people, and one person under several names; variants across Malagasy, French and English.
  - Where manuscript traditions differ (e.g. Luke 3:36), record the variant rather than pick one silently. [VERIFY]

## 4. Bible chronology

- Timeline of events and people, linked to verses and to the family tree.
- Open points:
  - Dates are disputed: the Masoretic, Septuagint and Samaritan texts give different figures for the early genealogies, and the date of the Exodus is debated (early vs. late). [INTRA-REFORMED DEBATE]
  - Show each date with its source and a range or alternatives, not a single "true" date; label what is stated in Scripture and what is scholarly reconstruction.
  - Choose which chronologies to include (e.g. Ussher is one historical option, not the only one). [VERIFY]

## 5. Church councils ("go deeper" section, study mode)

- A list of church councils, with for each: date, place, what it was about, who attended, what it decided (creeds, canons), and why it matters.
- Placed in study mode, under a "go deeper" section, linked to the confessions (item 1) and to the Bible passages the councils discussed.
- Open points:
  - Scope: the ecumenical councils (Nicaea 325 to Nicaea II 787) are the obvious core. Whether to add regional synods and Reformation-era ones (e.g. Dort 1618-19) is a choice to make. [VERIFY exact list and dates before entering data]
  - Perspective: present what each council decided as a historical fact, separately from how traditions (Reformed, Catholic, Orthodox) evaluate its authority. [INTRA-REFORMED DEBATE] on how binding the councils are; the Reformed view of conciliar authority is subordinate to Scripture (cf. WCF XXXI.3). [VERIFY]
  - Sources: primary texts (canons, creeds) and reputable scholarly summaries; check licences of any translation used (see `data-licences.md`).
  - Malagasy angle: whether Malagasy translations of creeds or canons exist. [VERIFY]

## 6. Traditions: Orthodox alongside Catholic and Protestant

- Present material by tradition (Orthodox, Catholic, Reformed, Lutheran, Anglican, ...) rather than three blocks; "Protestant" is not one tradition.
- Applies to: councils (item 5), confessions (item 1), and a possible **canon setting** (Protestant 66 / Catholic / Orthodox-Septuagint).
- Open points:
  - Councils: the Orthodox count only the first seven as ecumenical; Catholics count more. Record each tradition's own count. [VERIFY]
  - Canon: three Old Testament canons at least (Protestant 39, Catholic 46, Orthodox/LXX larger). No single closed Orthodox decree; Synod of Jerusalem (1672) is the key statement. [VERIFY]
  - Oriental Orthodox (Coptic, Ethiopian, Armenian, Syriac) are a separate family from Eastern Orthodox, with different canons and a different view of Chalcedon; they need their own entries. [VERIFY]
  - Framing: describe each tradition on its own terms, and mark the Reformed standpoint of the app explicitly. [EXTRA-CONFESSIONAL]
  - Malagasy context: Orthodox presence in Madagascar is very small, if any, so this is for completeness and study. [VERIFY]
