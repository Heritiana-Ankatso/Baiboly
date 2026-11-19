# Book abbreviations: review needed

The parser accepts, for every book, its Malagasy abbreviation, its full Malagasy name or any unambiguous beginning of that name (accents and case ignored). French and English names are accepted as a fallback.

**Confirmed** abbreviations come from your own examples. **Unconfirmed** ones were taken from the abbreviation field (`toc3`) of the Malagasy 1865 source file. Please check them. To change one, edit `tools/abbreviations.tsv`, set `status` to `confirmed`, and rebuild with `python3 tools/build_data.py`. The `aliases` column takes extra spellings, separated by `|`.

Books 1 and 2 Samoela, 1 and 2 Mpanjaka and so on share an abbreviation: the number typed before it (for example `2 Sam`) picks the book.

| # | Book | Abbreviation | Status | Source |
|---|---|---|---|---|
| 1 | Genesisy | Gen | unconfirmed | mg1865 toc3 |
| 2 | Eksodosy | Eks | unconfirmed | mg1865 toc3 |
| 3 | Levitikosy | Lev | unconfirmed | mg1865 toc3 |
| 4 | Nomery | Nom | unconfirmed | mg1865 toc3 |
| 5 | Deotoronomia | Deo | unconfirmed | mg1865 toc3 |
| 6 | Josoa | Jos | unconfirmed | mg1865 toc3 |
| 7 | Mpitsara | Mpits | unconfirmed | mg1865 toc3 |
| 8 | Rota | Rota | unconfirmed | mg1865 toc3 |
| 9 | 1 Samoela | Sam | unconfirmed | mg1865 toc3 |
| 10 | 2 Samoela | Sam | unconfirmed | mg1865 toc3 |
| 11 | 1 Mpanjaka | Mpan | unconfirmed | mg1865 toc3 |
| 12 | 2 Mpanjaka | Mpan | unconfirmed | mg1865 toc3 |
| 13 | 1 Tantara | Tan | unconfirmed | mg1865 toc3 |
| 14 | 2 Tantara | Tan | unconfirmed | mg1865 toc3 |
| 15 | Ezra | Ezra | unconfirmed | mg1865 toc3 |
| 16 | Nehemia | Neh | unconfirmed | mg1865 toc3 |
| 17 | Estera | Est | unconfirmed | mg1865 toc3 |
| 18 | Joba | Joba | unconfirmed | mg1865 toc3 |
| 19 | Salamo | Sal | ✅ confirmed | user example |
| 20 | Ohabolana | Oha | unconfirmed | mg1865 toc3 |
| 21 | Mpitoriteny | Mpito | unconfirmed | mg1865 toc3 |
| 22 | Tonon-kiran’ i Solomona | Ton | unconfirmed | mg1865 toc3 |
| 23 | Isaia | Isa | unconfirmed | mg1865 toc3 |
| 24 | Jeremia | Jer | unconfirmed | mg1865 toc3 |
| 25 | Fitomaniana | Fit | unconfirmed | mg1865 toc3 |
| 26 | Ezekiela | Ezek | unconfirmed | mg1865 toc3 |
| 27 | Daniela | Dan | unconfirmed | mg1865 toc3 |
| 28 | Hosea | Hos | unconfirmed | mg1865 toc3 |
| 29 | Joela | Joe | unconfirmed | mg1865 toc3 |
| 30 | Amosa | Amo | unconfirmed | mg1865 toc3 |
| 31 | Obadia | Oba | unconfirmed | mg1865 toc3 |
| 32 | Jona | Jona | unconfirmed | mg1865 toc3 |
| 33 | Mika | Mik | unconfirmed | mg1865 toc3 |
| 34 | Nahoma | Nah | unconfirmed | mg1865 toc3 |
| 35 | Habakoka | Hab | unconfirmed | mg1865 toc3 |
| 36 | Zefania | Zef | unconfirmed | mg1865 toc3 |
| 37 | Hagay | Hag | unconfirmed | mg1865 toc3 |
| 38 | Zakaria | Zak | unconfirmed | mg1865 toc3 |
| 39 | Malakia | Mal | unconfirmed | mg1865 toc3 |
| 40 | Matio | Mat | unconfirmed | mg1865 toc3 |
| 41 | Marka | Mar | unconfirmed | mg1865 toc3 |
| 42 | Lioka | Lio | unconfirmed | mg1865 toc3 |
| 43 | Jaona | Jao | ✅ confirmed | user example |
| 44 | Asan’ ny Apostoly | Asan | unconfirmed | mg1865 toc3 |
| 45 | Romana | Rom | unconfirmed | mg1865 toc3 |
| 46 | 1 Korintiana | Kor | unconfirmed | mg1865 toc3 |
| 47 | 2 Korintiana | Kor | unconfirmed | mg1865 toc3 |
| 48 | Galatiana | Gal | unconfirmed | mg1865 toc3 |
| 49 | Efesiana | Efe | ✅ confirmed | user example |
| 50 | Filipiana | Flp | unconfirmed | mg1865 toc3 |
| 51 | Kolosiana | Kol | unconfirmed | mg1865 toc3 |
| 52 | 1 Tesaloniana | Tes | unconfirmed | mg1865 toc3 |
| 53 | 2 Tesaloniana | Tes | unconfirmed | mg1865 toc3 |
| 54 | 1 Timoty | Tim | ✅ confirmed | user example |
| 55 | 2 Timoty | Tim | ✅ confirmed | user example |
| 56 | Titosy | Tit | unconfirmed | mg1865 toc3 |
| 57 | Filemona | Flm | unconfirmed | mg1865 toc3 |
| 58 | Hebreo | Heb | unconfirmed | mg1865 toc3 |
| 59 | Jakoba | Jak | unconfirmed | mg1865 toc3 |
| 60 | 1 Petera | Pet | unconfirmed | mg1865 toc3 |
| 61 | 2 Petera | Pet | unconfirmed | mg1865 toc3 |
| 62 | 1 Jaona | Jao | unconfirmed | mg1865 toc3 |
| 63 | 2 Jaona | Jao | unconfirmed | mg1865 toc3 |
| 64 | 3 Jaona | Jao | unconfirmed | mg1865 toc3 |
| 65 | Joda | Joda | unconfirmed | mg1865 toc3 |
| 66 | Apokalypsy | Apok | unconfirmed | mg1865 toc3 |
