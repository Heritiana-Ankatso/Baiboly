# Book abbreviations

The parser accepts, for every book, its abbreviation, any extra spelling listed below, its full Malagasy name or any unambiguous beginning of that name (accents, case and a trailing dot ignored). So *Ohab*, *Asan*, *Mpanj* or *Filip* work as well as *Oha*, *Asa*, *Mpan* and *Flp*. French and English names work as a fallback. A number before the abbreviation picks the book (`2 Sam`, `1 Mpan`, `3 Jao`).

**Sources.** Your own examples; the Malagasy Bible dictionary's abbreviation list (malagasyword.org / mondemalgache.org: *Gen.*, *Eks.*, *Deot.*, *Tant.*, *Mpit.*, read through search-result excerpts because the site refuses connections from the build machine); otherwise the abbreviation field of the Malagasy 1865 source file. No other publisher's material is used.

**Ambiguous on purpose:** *Fil* could be Filipiana or Filemona, so the app asks which one (use *Flp* or *Flm*). *Mpit* is Mpitoriteny and *Mpits* Mpitsara, as in the dictionary list.

Books 67–73 are the deuterocanonical books of Catholic Bibles; their Malagasy names follow the Malagasy Catholic Bible's book list, and their abbreviations are proposals for you to review.

To change one: edit `tools/abbreviations.tsv` and rebuild with `python3 tools/build_data.py`.

**By Bible language (1.9.0).** The book grids and references now follow the Bible being read: Malagasy abbreviations below for the Malagasy Bibles, the usual French ones (*Gn, Ex, … Jn, Ac, Rm, 1 Co … Ap*) for the French Bibles and English ones from the OSIS names (*Gen, Exod, … John, Acts, Rom, 1 Cor … Rev*) for the English Bibles. The parser accepts the French and English abbreviations as well (*Jn 3:16*, *1 Co 13:4*).

**Further research (November 2026).** A wider search for current Malagasy usage found little new: the Jehovah's Witnesses' Malagasy index writes *Le* (Levitikosy) and *Mpto* (Mpitoriteny), forms of that one publisher, not adopted here; the Malagasy dictionary sites (malagasyword.org, tenymalagasy.org) and the Malagasy Wikipedia could not be reached from the build environment. The list below is unchanged; it still awaits your review where it says so.

| # | Book | Abbreviation | Also accepted | Status | Source |
|---|---|---|---|---|---|
| 1 | Genesisy | Gen |  | Malagasy Bible dictionary | Malagasy Bible dictionary (malagasyword.org) list, quoted in search results |
| 2 | Eksodosy | Eks |  | Malagasy Bible dictionary | Malagasy Bible dictionary (malagasyword.org) list, quoted in search results |
| 3 | Levitikosy | Lev |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 4 | Nomery | Nom |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 5 | Deotoronomia | Deot | Deo, Deoteronomia | Malagasy Bible dictionary | Malagasy Bible dictionary list (malagasyword.org / mondemalgache.org), via search-result excerpt |
| 6 | Josoa | Jos |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 7 | Mpitsara | Mpits |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage; distinct from Mpit (Mpitoriteny) |
| 8 | Rota | Rota |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 9 | 1 Samoela | Sam |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 10 | 2 Samoela | Sam |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 11 | 1 Mpanjaka | Mpan | Mpanj | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 12 | 2 Mpanjaka | Mpan | Mpanj | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 13 | 1 Tantara | Tant | Tan | Malagasy Bible dictionary | Malagasy Bible dictionary list (malagasyword.org / mondemalgache.org), via search-result excerpt |
| 14 | 2 Tantara | Tant | Tan | Malagasy Bible dictionary | Malagasy Bible dictionary list (malagasyword.org / mondemalgache.org), via search-result excerpt |
| 15 | Ezra | Ezra |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 16 | Nehemia | Neh |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 17 | Estera | Est |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 18 | Joba | Joba | Job | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 19 | Salamo | Sal |  | ✅ yours | user example |
| 20 | Ohabolana | Oha |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 21 | Mpitoriteny | Mpit | Mpito | Malagasy Bible dictionary | Malagasy Bible dictionary list (malagasyword.org / mondemalgache.org), via search-result excerpt; distinct from Mpits (Mpitsara) |
| 22 | Tonon-kiran’ i Solomona | Ton | Tonon-kira, Tononkira, Tonon-kiran’i Solomona | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 23 | Isaia | Isa |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 24 | Jeremia | Jer |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 25 | Fitomaniana | Fit |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 26 | Ezekiela | Ezek |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 27 | Daniela | Dan |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 28 | Hosea | Hos |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 29 | Joela | Joe | Joela | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 30 | Amosa | Amo | Amosa, Am | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 31 | Obadia | Oba | Obad | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 32 | Jona | Jona |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 33 | Mika | Mik |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 34 | Nahoma | Nah |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 35 | Habakoka | Hab |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 36 | Zefania | Zef |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 37 | Hagay | Hag |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 38 | Zakaria | Zak |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 39 | Malakia | Mal |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 40 | Matio | Mat |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 41 | Marka | Mar |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 42 | Lioka | Lio |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 43 | Jaona | Jao |  | ✅ yours | user example |
| 44 | Asan’ ny Apostoly | Asa | Asan’ny Apostoly, Asan | confirmed | the user's own usage; Asan (1865 source, toc3) kept as an alias |
| 45 | Romana | Rom |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 46 | 1 Korintiana | Kor |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 47 | 2 Korintiana | Kor |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 48 | Galatiana | Gal |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 49 | Efesiana | Efe |  | ✅ yours | user example |
| 50 | Filipiana | Flp |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 51 | Kolosiana | Kol |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 52 | 1 Tesaloniana | Tes |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 53 | 2 Tesaloniana | Tes |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 54 | 1 Timoty | Tim |  | ✅ yours | user example |
| 55 | 2 Timoty | Tim |  | ✅ yours | user example |
| 56 | Titosy | Tit | Titosy | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 57 | Filemona | Flm | Filem, Filemona | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 58 | Hebreo | Heb | Hebreo | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 59 | Jakoba | Jak |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 60 | 1 Petera | Pet |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 61 | 2 Petera | Pet |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 62 | 1 Jaona | Jao |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 63 | 2 Jaona | Jao |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 64 | 3 Jaona | Jao |  | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 65 | Joda | Joda | Jod, Jud | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 66 | Apokalypsy | Apok | Apo, Apokalipsy | 1865 source file | Malagasy 1865 source (toc3), matches common usage |
| 67 | Tobia | Tob | Tôbia, Tobita | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 68 | Jodita | Jdt | Jiodita | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 69 | 1 Makabeo | Mak |  | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 70 | 2 Makabeo | Mak |  | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 71 | Fahendrena | Fah |  | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 72 | Eklesiastika | Ekl | Sir, Sirasida, Ekleziastika | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
| 73 | Baroka | Bar |  | proposed, to review | Malagasy Catholic book name (baiboly.katolika.org list, via search); abbreviation proposed, to be reviewed |
