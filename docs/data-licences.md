# Data sources and licences

Every source below passed the automatic licence check in `tools/build_data.py` (the build stops otherwise).

| Source | Title | Licence | Download | Origin |
|---|---|---|---|---|
| CrossWire SWORD module Mg1865 | Baiboly Malagasy (1865) | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/Mg1865.zip | https://nybaiboly.net/BibleFrancoMalgache.htm |
| eBible.org tdx | Saontsin’ Añahare | CC BY-SA 4.0, © 2023 Steve Lellelid (licence text checked on the publisher's copyright page) | https://ebible.org/Scriptures/tdx_vpl.zip | http://eBible.org/tdx/ |
| eBible.org fraLSG | Louis Segond 1910 | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/fraLSG_vpl.zip | http://ebible.org/fraLSG/ |
| eBible.org frajnd | Bible J.N. Darby | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/frajnd_vpl.zip | http://ebible.org/frajnd/ |
| eBible.org francl | Sainte Bible néo-Crampon Libre | CC BY-SA 4.0, © 2022 Fraternité de Tibériade (licence text checked on the publisher's copyright page) | https://ebible.org/Scriptures/francl_vpl.zip | http://eBible.org/francl/ |
| CrossWire SWORD module FreCrampon | La Bible Augustin Crampon 1923 | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/FreCrampon.zip | https://fr.wikisource.org/wiki/Bible_Crampon_1923 |
| eBible.org eng-kjv2006 | King James (Authorized) Version | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/eng-kjv2006_vpl.zip | http://ebible.org/eng-kjv2006/ |
| eBible.org engbsb | Berean Standard Bible | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/engbsb_vpl.zip | http://ebible.org/engbsb/ |
| eBible.org englsv | Literal Standard Version | CC BY-SA 4.0, © 2020 Covenant Press (licence text checked on the publisher's copyright page) | https://ebible.org/Scriptures/englsv_vpl.zip | http://eBible.org/englsv/ |
| eBible.org eng-web | World English Bible Classic | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/eng-web_vpl.zip | http://ebible.org/eng-web/ |
| eBible.org engBBE | Bible in Basic English | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/engBBE_vpl.zip | http://ebible.org/engBBE/ |
| eBible.org eng-web-c | World English Bible (Catholic) | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/eng-web-c_vpl.zip | http://ebible.org/eng-web-c/ |
| eBible.org engDRA | Douay-Rheims 1899 | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/engDRA_vpl.zip | http://ebible.org/engDRA/ |
| eBible.org eng-kjv2006 (USFM) | King James Version with Strong's numbers | Public Domain (eBible.org catalogue + about page) | https://ebible.org/Scriptures/eng-kjv2006_usfm.zip |  |
| CrossWire SWORD module StrongsGreek | Strong's Greek Bible Dictionary | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/StrongsGreek.zip | http://www.bf.org/ |
| CrossWire SWORD module StrongsHebrew | Strong's Hebrew Bible Dictionary | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/StrongsHebrew.zip | http://www.bf.org/ |
| Open Scriptures strongs | Strong's Hebrew and Greek dictionaries (JSON) | CC-BY-SA (Open Scriptures, as stated in the file header); attribution: Open Scriptures, derived from Strong's 1890/1894 dictionaries | https://raw.githubusercontent.com/openscriptures/strongs/master/{hebrew,greek}/strongs-{hebrew,greek}-dictionary.js | https://github.com/openscriptures/strongs |
| CrossWire SWORD module TSK | Treasury of Scripture Knowledge | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/TSK.zip |  |
| CrossWire SWORD module Easton | Easton's Bible Dictionary | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/Easton.zip | CCEL |
| CrossWire SWORD module Nave | Naveߴs Topical Bible | Public Domain (DistributionLicense in module .conf) | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/Nave.zip | https://ccel.org/ccel/n/nave/bible.xml |
| CrossWire SWORD module FreGBM | Glossaire de la Bible David Martin (1744) | Free distribution with mention of www.biblemartin.com | https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/FreGBM.zip | www.biblemartin.com |
| OpenBible.info | Bible places and region outlines (OpenBible.info Bible Geocoding Data) | CC BY 4.0, © OpenBible.info | https://github.com/openbibleinfo/Bible-Geocoding-Data | https://www.openbible.info/geo/ |
| Ancient World Mapping Center, UNC Chapel Hill | Roman roads and period borders (Ancient World Mapping Center geodata) | ODbL 1.0 (Open Database License); the map data derived from it (assets/map.json) is offered under the same licence | https://github.com/AWMC/geodata | https://opendatacommons.org/licenses/odbl/1-0/ |
| Natural Earth | Map outlines (1:10m land, lakes, rivers), today's borders and cities (1:50m), shaded relief (SR_LR raster) | Public Domain | https://www.naturalearthdata.com | https://github.com/nvkelso/natural-earth-vector, https://github.com/nvkelso/natural-earth-raster |
| Ancient World Mapping Center | Roman roads; borders of the Persian empire, the Hasmonean kingdom, Herod's kingdom and the Roman provinces (c. AD 200) | ODbL 1.0. The map data derived from it (`assets/map.json`, rebuilt by `tools/build_data.py`) is offered under the same licence | https://github.com/AWMC/geodata | https://opendatacommons.org/licenses/odbl/1-0/ |

Tools: versification tables from PySword 0.2.8 (MIT licence), `tools/vendor/sword_canons.py`.
