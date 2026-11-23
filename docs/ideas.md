# Ideas for future development

Unprioritised. Not committed to any release.

## 1. Confessions and catechisms

- Support many traditions, not only Reformed (WCF, Heidelberg, Belgic, Canons of Dort, etc.).
- Include **Malagasy** confessions/catechisms as a priority.
- Open points:
  - Which Malagasy texts exist, which church owns them, and under what licence (see `data-licences.md`). [VERIFY]
  - Original-language text vs. translation; Malagasy/French/English versions of the same document.
  - Cross-linking: confession/catechism article ↔ Bible verses (proof texts) already in the app.

## 2. Hymnals

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
