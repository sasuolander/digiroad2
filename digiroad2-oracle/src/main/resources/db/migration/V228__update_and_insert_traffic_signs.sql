-- Create temporary table as auxiliary table
CREATE GLOBAL TEMPORARY TABLE tmp_signs (
    othId NUMBER,
	name  VARCHAR2(200)
)
ON COMMIT PRESERVE ROWS;


DECLARE
    v_property_id NUMBER;

BEGIN

    -- Insert the values into the temporary table
    INSERT INTO tmp_signs (othId, name) VALUES (1,'Nopeusrajoitus');
    INSERT INTO tmp_signs (othId, name) VALUES (2,'Nopeusrajoitus päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (3,'Nopeusrajoitusalue');
    INSERT INTO tmp_signs (othId, name) VALUES (4,'Nopeusrajoitusalue päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (5,'Taajama');
    INSERT INTO tmp_signs (othId, name) VALUES (6,'Taajama päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (7,'Suojatie');
    INSERT INTO tmp_signs (othId, name) VALUES (8,'Ajoneuvon tai ajoneuvoyhdistelmän suurin sallittu pituus');
    INSERT INTO tmp_signs (othId, name) VALUES (9,'Muu vaara');
    INSERT INTO tmp_signs (othId, name) VALUES (10,'Vasemmalle kääntyminen kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (11,'Oikealle kääntyminen kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (12,'U-käännös kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (13,'Ajoneuvolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (14,'Moottorikäyttöisellä ajoneuvolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (15,'Kuorma- ja pakettiautolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (16,'Ajoneuvoyhdistelmällä ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (17,'Traktorilla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (18,'Moottoripyörällä ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (19,'Moottorikelkalla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (20,'Vaarallisten aineiden kuljetus kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (21,'Linja-autolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (22,'Mopolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (23,'Polkupyörällä ja mopolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (24,'Jalankulku kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (25,'Jalankulku ja polkupyörällä ja mopolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (26,'Ratsastus kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (27,'Kielletty ajosuunta');
    INSERT INTO tmp_signs (othId, name) VALUES (28,'Ohituskielto');
    INSERT INTO tmp_signs (othId, name) VALUES (29,'Ohituskielto päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (30,'Ajoneuvon suurin sallittu leveys');
    INSERT INTO tmp_signs (othId, name) VALUES (31,'Ajoneuvon suurin sallittu korkeus');
    INSERT INTO tmp_signs (othId, name) VALUES (32,'Ajoneuvon suurin sallittu massa');
    INSERT INTO tmp_signs (othId, name) VALUES (33,'Ajoneuvoyhdistelmän suurin sallittu massa');
    INSERT INTO tmp_signs (othId, name) VALUES (34,'Ajoneuvon suurin sallittu akselille kohdistuva massa');
    INSERT INTO tmp_signs (othId, name) VALUES (35,'Ajoneuvon suurin sallittu telille kohdistuva massa');
    INSERT INTO tmp_signs (othId, name) VALUES (36,'Mutka oikealle');
    INSERT INTO tmp_signs (othId, name) VALUES (37,'Mutka vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (38,'Mutkia, joista ensimmäinen oikealle');
    INSERT INTO tmp_signs (othId, name) VALUES (39,'Mutkia, joista ensimmäinen vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (40,'Jyrkkä alamäki');
    INSERT INTO tmp_signs (othId, name) VALUES (41,'Jyrkkä ylämäki');
    INSERT INTO tmp_signs (othId, name) VALUES (42,'Epätasainen tie');
    INSERT INTO tmp_signs (othId, name) VALUES (43,'Lapsia');
    INSERT INTO tmp_signs (othId, name) VALUES (45,'Vapaa leveys');
    INSERT INTO tmp_signs (othId, name) VALUES (46,'Vapaa korkeus');
    INSERT INTO tmp_signs (othId, name) VALUES (47,'Kielto ryhmän A vaarallisten aineiden kuljetukselle');
    INSERT INTO tmp_signs (othId, name) VALUES (48,'Kielto ryhmän B vaarallisten aineiden kuljetukselle');
    INSERT INTO tmp_signs (othId, name) VALUES (49,'Voimassaoloaika arkisin ma-pe');
    INSERT INTO tmp_signs (othId, name) VALUES (50,'Voimassaoloaika arkilauantaisin');
    INSERT INTO tmp_signs (othId, name) VALUES (51,'Aikarajoitus');
    INSERT INTO tmp_signs (othId, name) VALUES (52,'Henkilöauto');
    INSERT INTO tmp_signs (othId, name) VALUES (53,'Linja-auto');
    INSERT INTO tmp_signs (othId, name) VALUES (54,'Kuormaauto');
    INSERT INTO tmp_signs (othId, name) VALUES (55,'Pakettiauto');
    INSERT INTO tmp_signs (othId, name) VALUES (56,'Invalidin ajoneuvo');
    INSERT INTO tmp_signs (othId, name) VALUES (57,'Moottoripyörä');
    INSERT INTO tmp_signs (othId, name) VALUES (58,'Polkupyörä');
    INSERT INTO tmp_signs (othId, name) VALUES (60,'Pysäköintiajan alkamisen osoittamisvelvollisuus (keltainen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (61,'Tekstillinen lisäkilpi');
    INSERT INTO tmp_signs (othId, name) VALUES (62,'Huoltoajo sallittu');
    INSERT INTO tmp_signs (othId, name) VALUES (63,'Linja-autokaista');
    INSERT INTO tmp_signs (othId, name) VALUES (64,'Linja-autokaista päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (65,'Raitiovaunukaista');
    INSERT INTO tmp_signs (othId, name) VALUES (68,'Raitiovaunupysäkki');
    INSERT INTO tmp_signs (othId, name) VALUES (69,'Taksiasema');
    INSERT INTO tmp_signs (othId, name) VALUES (70,'Jalkakäytävä');
    INSERT INTO tmp_signs (othId, name) VALUES (71,'Pyörätie');
    INSERT INTO tmp_signs (othId, name) VALUES (72,'Yhdistetty pyörätie ja jalkakäytävä');
    INSERT INTO tmp_signs (othId, name) VALUES (74,'Pakollinen ajosuunta kääntyminen oikealle');
    INSERT INTO tmp_signs (othId, name) VALUES (77,'Pakollinen kiertosuunta');
    INSERT INTO tmp_signs (othId, name) VALUES (78,'Liikenteenjakaja oikea');
    INSERT INTO tmp_signs (othId, name) VALUES (80,'Taksiasema-alue');
    INSERT INTO tmp_signs (othId, name) VALUES (81,'Taksin pysäyttämispaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (82,'Kapeneva tie');
    INSERT INTO tmp_signs (othId, name) VALUES (83,'Kaksisuuntainen liikenne');
    INSERT INTO tmp_signs (othId, name) VALUES (84,'Avattava silta');
    INSERT INTO tmp_signs (othId, name) VALUES (85,'Tietyö');
    INSERT INTO tmp_signs (othId, name) VALUES (86,'Liukas ajorata');
    INSERT INTO tmp_signs (othId, name) VALUES (87,'Suojatien ennakkovaroitus');
    INSERT INTO tmp_signs (othId, name) VALUES (88,'Pyöräilijöitä');
    INSERT INTO tmp_signs (othId, name) VALUES (89,'Tienristeys');
    INSERT INTO tmp_signs (othId, name) VALUES (90,'Liikennevalot');
    INSERT INTO tmp_signs (othId, name) VALUES (91,'Raitiovaunu');
    INSERT INTO tmp_signs (othId, name) VALUES (92,'Putoavia kiviä');
    INSERT INTO tmp_signs (othId, name) VALUES (93,'Sivutuuli');
    INSERT INTO tmp_signs (othId, name) VALUES (94,'Etuajo-oikeutettu tie');
    INSERT INTO tmp_signs (othId, name) VALUES (95,'Etuajo-oikeuden päättyminen');
    INSERT INTO tmp_signs (othId, name) VALUES (96,'Etuajo-oikeus kohdattaessa');
    INSERT INTO tmp_signs (othId, name) VALUES (97,'Väistämisvelvollisuus kohdattaessa');
    INSERT INTO tmp_signs (othId, name) VALUES (98,'Väistämisvelvollisuus risteyksessä');
    INSERT INTO tmp_signs (othId, name) VALUES (99,'Pakollinen pysäyttäminen');
    INSERT INTO tmp_signs (othId, name) VALUES (100,'Pysäyttäminen kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (101,'Pysäköinti kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (102,'Pysäköintikieltoalue');
    INSERT INTO tmp_signs (othId, name) VALUES (103,'Pysäköintikieltoalue päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (104,'Vuoropysäköinti (kielletty parittomina päivinä)');
    INSERT INTO tmp_signs (othId, name) VALUES (105,'Pysäköintipaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (106,'Yksisuuntainen tie suoraan');
    INSERT INTO tmp_signs (othId, name) VALUES (107,'Moottoritie');
    INSERT INTO tmp_signs (othId, name) VALUES (108,'Moottoritie päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (109,'Pihakatu');
    INSERT INTO tmp_signs (othId, name) VALUES (110,'Pihakatu päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (111,'Kävelykatu');
    INSERT INTO tmp_signs (othId, name) VALUES (112,'Kävelykatu päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (113,'Umpitie edessä');
    INSERT INTO tmp_signs (othId, name) VALUES (114,'Umpitie oikealla/vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (115,'Moottoritien tunnus');
    INSERT INTO tmp_signs (othId, name) VALUES (116,'Pysäköinti');
    INSERT INTO tmp_signs (othId, name) VALUES (117,'Tietylle ajoneuvolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (118,'Jalankulkijalle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (119,'Vammaiselle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (120,'Palvelukohteen osoiteviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (121,'Ensiapu');
    INSERT INTO tmp_signs (othId, name) VALUES (122,'Polttoaineen jakelu bensiini tai etanoli');
    INSERT INTO tmp_signs (othId, name) VALUES (123,'Ruokailupaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (124,'Käymälä');
    INSERT INTO tmp_signs (othId, name) VALUES (125,'Hirvi');
    INSERT INTO tmp_signs (othId, name) VALUES (126,'Poro');
    INSERT INTO tmp_signs (othId, name) VALUES (127,'Sivutien risteys molemmin puolin');
    INSERT INTO tmp_signs (othId, name) VALUES (128,'Sivutien risteys oikealla/vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (129,'Sivutien risteys oikealla/vasemmalla viistoon');
    INSERT INTO tmp_signs (othId, name) VALUES (130,'Rautatien tasoristeys ilman puomeja');
    INSERT INTO tmp_signs (othId, name) VALUES (131,'Rautatien tasoristeys, jossa on puomit');
    INSERT INTO tmp_signs (othId, name) VALUES (132,'Yksiraiteisen rautatien tasoristeys');
    INSERT INTO tmp_signs (othId, name) VALUES (133,'Kaksi tai useampiraiteisen rautatien tasoristeys');
    INSERT INTO tmp_signs (othId, name) VALUES (134,'Vuoropysäköinti (kielletty parillisina päivinä)');
    INSERT INTO tmp_signs (othId, name) VALUES (135,'Moottorikelkkailureitti');
    INSERT INTO tmp_signs (othId, name) VALUES (136,'Ratsastustie');
    INSERT INTO tmp_signs (othId, name) VALUES (137,'Liityntäpysäköintipaikka juna');
    INSERT INTO tmp_signs (othId, name) VALUES (138,'Etäisyys pakolliseen pysäyttämiseen');
    INSERT INTO tmp_signs (othId, name) VALUES (139,'Sähköjohdon korkeus');
    INSERT INTO tmp_signs (othId, name) VALUES (140,'Vaikutusalue molempiin suuntiin oikealle ja vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (141,'Vaikutusalue molempiin suuntiin eteen- ja taaksepäin');
    INSERT INTO tmp_signs (othId, name) VALUES (144,'Vaikutusalue päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (145,'Voimassaoloaika sunnuntaisin ja pyhinä');
    INSERT INTO tmp_signs (othId, name) VALUES (148,'Vaikutusalueen pituus');
    INSERT INTO tmp_signs (othId, name) VALUES (149,'Etäisyys kohteeseen');
    INSERT INTO tmp_signs (othId, name) VALUES (150,'Matkailuperävaunu');
    INSERT INTO tmp_signs (othId, name) VALUES (151,'Mopo');
    INSERT INTO tmp_signs (othId, name) VALUES (152,'Kiertotien suunnistustaulu (sininen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (153,'Kiertotieopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (154,'Ajoreittiopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (155,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (156,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (157,'Ajokaistan päättyminen');
    INSERT INTO tmp_signs (othId, name) VALUES (158,'Ajokaistan yläpuolinen viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (159,'Ajokaistan yläpuolinen erkanemisviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (161,'Erkanemisviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (163,'Osoiteviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (164,'Jalankulun viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (169,'Liityntäpysäköintiviitta juna');
    INSERT INTO tmp_signs (othId, name) VALUES (170,'Enimmäisnopeussuositus');
    INSERT INTO tmp_signs (othId, name) VALUES (171,'Etäisyystaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (173,'Kansainvälisen pääliikenneväylän numero');
    INSERT INTO tmp_signs (othId, name) VALUES (174,'Opastus numeron tarkoittamalle tielle');
    INSERT INTO tmp_signs (othId, name) VALUES (175,'Valtatien numero');
    INSERT INTO tmp_signs (othId, name) VALUES (176,'Kantatien numero');
    INSERT INTO tmp_signs (othId, name) VALUES (177,'Muun maantien numero');
    INSERT INTO tmp_signs (othId, name) VALUES (178,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (179,'Moottoriliikennetien tunnus');
    INSERT INTO tmp_signs (othId, name) VALUES (180,'Lentoasema');
    INSERT INTO tmp_signs (othId, name) VALUES (181,'Autolautta');
    INSERT INTO tmp_signs (othId, name) VALUES (182,'Tavarasatama');
    INSERT INTO tmp_signs (othId, name) VALUES (183,'Teollisuusalue tai yritysalue');
    INSERT INTO tmp_signs (othId, name) VALUES (184,'Rautatieasema');
    INSERT INTO tmp_signs (othId, name) VALUES (185,'Linja-autoasema');
    INSERT INTO tmp_signs (othId, name) VALUES (186,'Vaarallisten aineiden kuljetukselle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (187,'Reitti, jolla on portaat alas');
    INSERT INTO tmp_signs (othId, name) VALUES (188,'Reitti ilman portaita alas');
    INSERT INTO tmp_signs (othId, name) VALUES (189,'Hätäuloskäynti vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (190,'Poistumisreitti (yksi)');
    INSERT INTO tmp_signs (othId, name) VALUES (191,'Ajokaistan yläpuolinen viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (192,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (193,'Kiertotien suunnistustaulu (keltainen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (200,'Lautta, laituri tai ranta');
    INSERT INTO tmp_signs (othId, name) VALUES (201,'Liikenneruuhka');
    INSERT INTO tmp_signs (othId, name) VALUES (202,'Töyssyjä');
    INSERT INTO tmp_signs (othId, name) VALUES (203,'Irtokiviä');
    INSERT INTO tmp_signs (othId, name) VALUES (204,'Vaarallinen tien reuna');
    INSERT INTO tmp_signs (othId, name) VALUES (205,'Jalankulkijoita');
    INSERT INTO tmp_signs (othId, name) VALUES (206,'Hiihtolatu');
    INSERT INTO tmp_signs (othId, name) VALUES (207,'Kauriseläin');
    INSERT INTO tmp_signs (othId, name) VALUES (208,'Sivutien risteys molemmin puolin porrastetusti');
    INSERT INTO tmp_signs (othId, name) VALUES (209,'Liikenneympyrä');
    INSERT INTO tmp_signs (othId, name) VALUES (210,'Rautatien tasoristeyksen lähestymismerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (211,'Rautatien tasoristeyksen lähestymismerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (212,'Rautatien tasoristeyksen lähestymismerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (213,'Matalalla lentäviä lentokoneita');
    INSERT INTO tmp_signs (othId, name) VALUES (214,'Väistämisvelvollisuus pyöräilijän tienylityspaikassa');
    INSERT INTO tmp_signs (othId, name) VALUES (215,'Polkupyörällä ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (216,'Jalankulku ja polkupyörällä ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (217,'Ohituskielto kuorma-autolla');
    INSERT INTO tmp_signs (othId, name) VALUES (218,'Ohituskielto kuorma-autolla päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (219,'Ajokaistakohtainen kielto, rajoitus tai määräys');
    INSERT INTO tmp_signs (othId, name) VALUES (220,'Kuormauspaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (221,'Pakollinen pysäyttäminen tullitarkastusta varten');
    INSERT INTO tmp_signs (othId, name) VALUES (222,'Pakollinen pysäyttäminen tarkastusta varten');
    INSERT INTO tmp_signs (othId, name) VALUES (223,'Moottorikäyttöisten ajoneuvojen vähimmäisetäisyys');
    INSERT INTO tmp_signs (othId, name) VALUES (224,'Nastarenkailla varustetulla moottorikäyttöisellä ajoneuvolla ajo kielletty');
    INSERT INTO tmp_signs (othId, name) VALUES (225,'Pakollinen ajosuunta oikealle');
    INSERT INTO tmp_signs (othId, name) VALUES (226,'Pakollinen ajosuunta vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (227,'Pakollinen ajosuunta suoraan');
    INSERT INTO tmp_signs (othId, name) VALUES (228,'Pakollinen ajosuunta kääntyminen vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (229,'Pakollinen ajosuunta suoraan tai kääntyminen oikealle');
    INSERT INTO tmp_signs (othId, name) VALUES (230,'Pakollinen ajosuunta suoraan tai kääntyminen vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (231,'Pakollinen ajosuunta kääntyminen oikealle tai vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (232,'Pakollinen ajosuunta suoraan tai kääntyminen oikealle tai vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (233,'Liikenteenjakaja vasen');
    INSERT INTO tmp_signs (othId, name) VALUES (234,'Liikenteenjakaja molemmin puolin');
    INSERT INTO tmp_signs (othId, name) VALUES (235,'Pyörätie ja jalkakäytävä rinnakkain, pyörätie vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (236,'Pyörätie ja jalkakäytävä rinnakkain, pyörätie oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (237,'Vähimmäisnopeus');
    INSERT INTO tmp_signs (othId, name) VALUES (238,'Vähimmäisnopeus päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (239,'Liityntäpysäköintipaikka bussi');
    INSERT INTO tmp_signs (othId, name) VALUES (240,'Liityntäpysäköintipaikka tram');
    INSERT INTO tmp_signs (othId, name) VALUES (241,'Liityntäpysäköintipaikka metro');
    INSERT INTO tmp_signs (othId, name) VALUES (242,'Liityntäpysäköintipaikka useita joukkoliikennevälineitä');
    INSERT INTO tmp_signs (othId, name) VALUES (243,'Ajoneuvojen sijoitus pysäköintipaikalla suoraan');
    INSERT INTO tmp_signs (othId, name) VALUES (244,'Ajoneuvojen sijoitus pysäköintipaikalla vastakkain');
    INSERT INTO tmp_signs (othId, name) VALUES (245,'Ajoneuvojen sijoitus pysäköintipaikalla vinoon');
    INSERT INTO tmp_signs (othId, name) VALUES (246,'Kohtaamispaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (248,'Linja-auto ja taksikaista');
    INSERT INTO tmp_signs (othId, name) VALUES (249,'Linja-auto ja taksikaista päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (250,'Raitiovaunu- ja taksikaista');
    INSERT INTO tmp_signs (othId, name) VALUES (251,'Raitiovaunukaista päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (252,'Raitiovaunu- ja taksikaista päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (253,'Pyöräkaista oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (254,'Pyöräkaista keskellä');
    INSERT INTO tmp_signs (othId, name) VALUES (255,'Yksisuuntainen tie oikealle/vasemmalle');
    INSERT INTO tmp_signs (othId, name) VALUES (256,'Moottoriliikennetie');
    INSERT INTO tmp_signs (othId, name) VALUES (257,'Moottoriliikennetie päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (258,'Tunneli');
    INSERT INTO tmp_signs (othId, name) VALUES (259,'Tunneli päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (260,'Hätäpysäyttämispaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (261,'Pyöräkatu');
    INSERT INTO tmp_signs (othId, name) VALUES (262,'Pyöräkatu päättyy');
    INSERT INTO tmp_signs (othId, name) VALUES (263,'Ajokaistojen yhdistymien');
    INSERT INTO tmp_signs (othId, name) VALUES (264,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (265,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (266,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (267,'Suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (268,'Ajokaistakohtainen suunnistustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (269,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (270,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (271,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (272,'Ajokaistaopastus');
    INSERT INTO tmp_signs (othId, name) VALUES (273,'Viitoituksen koontimerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (275,'Osoiteviitan ennakkomerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (276,'Liityntäpysäköintiviitta bussi');
    INSERT INTO tmp_signs (othId, name) VALUES (277,'Liityntäpysäköintiviitta raitiovaunu');
    INSERT INTO tmp_signs (othId, name) VALUES (278,'Liityntäpysäköintiviitta metro');
    INSERT INTO tmp_signs (othId, name) VALUES (279,'Liityntäpysäköintiviitta useita joukkoliikennevälineitä');
    INSERT INTO tmp_signs (othId, name) VALUES (280,'Pyöräilyn viitta ilman etäisyyksiä');
    INSERT INTO tmp_signs (othId, name) VALUES (281,'Pyöräilyn viitta etäisyyslukemilla');
    INSERT INTO tmp_signs (othId, name) VALUES (282,'Pyöräilyn suunnistustaulu etäisyyslukemilla');
    INSERT INTO tmp_signs (othId, name) VALUES (283,'Pyöräilyn suunnistustaulu ilman etäisyyksiä');
    INSERT INTO tmp_signs (othId, name) VALUES (284,'Pyöräilyn etäisyystaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (285,'Pyöräilyn paikannimi');
    INSERT INTO tmp_signs (othId, name) VALUES (286,'Umpitie');
    INSERT INTO tmp_signs (othId, name) VALUES (289,'Vesistön nimi');
    INSERT INTO tmp_signs (othId, name) VALUES (290,'Kehätien numero');
    INSERT INTO tmp_signs (othId, name) VALUES (291,'Eritasoliittymän numero');
    INSERT INTO tmp_signs (othId, name) VALUES (292,'Varareitti');
    INSERT INTO tmp_signs (othId, name) VALUES (293,'Matkustajasatama');
    INSERT INTO tmp_signs (othId, name) VALUES (294,'Tavaraterminaali');
    INSERT INTO tmp_signs (othId, name) VALUES (295,'Vähittäiskaupan suuryksikkö');
    INSERT INTO tmp_signs (othId, name) VALUES (296,'Katettu pysäköinti');
    INSERT INTO tmp_signs (othId, name) VALUES (297,'Keskusta');
    INSERT INTO tmp_signs (othId, name) VALUES (298,'Reitti, jolla on portaat ylös');
    INSERT INTO tmp_signs (othId, name) VALUES (299,'Reitti ilman portaita ylös');
    INSERT INTO tmp_signs (othId, name) VALUES (300,'Pyörätuoliramppi alas');
    INSERT INTO tmp_signs (othId, name) VALUES (301,'Pyörätuoliramppi ylös');
    INSERT INTO tmp_signs (othId, name) VALUES (302,'Hätäuloskäynti oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (303,'Poistumisreitti (useita)');
    INSERT INTO tmp_signs (othId, name) VALUES (304,'Palvelukohteen opastustaulu');
    INSERT INTO tmp_signs (othId, name) VALUES (305,'Palvelukohteen opastustaulu nuolella');
    INSERT INTO tmp_signs (othId, name) VALUES (306,'Palvelukohteen erkanemisviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (307,'Palvelukohteen osoiteviitan ennakkomerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (308,'Radioaseman taajuus');
    INSERT INTO tmp_signs (othId, name) VALUES (309,'Opastuspiste');
    INSERT INTO tmp_signs (othId, name) VALUES (310,'Opastustoimisto');
    INSERT INTO tmp_signs (othId, name) VALUES (311,'Autokorjaamo');
    INSERT INTO tmp_signs (othId, name) VALUES (312,'Polttoaineen jakelu paineistettu maakaasu');
    INSERT INTO tmp_signs (othId, name) VALUES (313,'Polttoaineen jakelu sähkö');
    INSERT INTO tmp_signs (othId, name) VALUES (314,'Polttoaineen jakelu vety');
    INSERT INTO tmp_signs (othId, name) VALUES (315,'Hotelli tai motelli');
    INSERT INTO tmp_signs (othId, name) VALUES (316,'Kahvila tai pikaruokapaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (317,'Retkeilymaja');
    INSERT INTO tmp_signs (othId, name) VALUES (318,'Leirintäalue');
    INSERT INTO tmp_signs (othId, name) VALUES (319,'Matkailuajoneuvoalue');
    INSERT INTO tmp_signs (othId, name) VALUES (320,'Levähdysalue');
    INSERT INTO tmp_signs (othId, name) VALUES (321,'Ulkoilualue');
    INSERT INTO tmp_signs (othId, name) VALUES (322,'Hätäpuhelin');
    INSERT INTO tmp_signs (othId, name) VALUES (323,'Sammutin');
    INSERT INTO tmp_signs (othId, name) VALUES (324,'Museo tai historiallinen rakennus');
    INSERT INTO tmp_signs (othId, name) VALUES (325,'Maailmanperintökohde');
    INSERT INTO tmp_signs (othId, name) VALUES (326,'Luontokohde');
    INSERT INTO tmp_signs (othId, name) VALUES (327,'Näköalapaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (328,'Eläintarha tai -puisto');
    INSERT INTO tmp_signs (othId, name) VALUES (329,'Muu nähtävyys');
    INSERT INTO tmp_signs (othId, name) VALUES (330,'Uintipaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (331,'Kalastuspaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (332,'Hiihtohissi');
    INSERT INTO tmp_signs (othId, name) VALUES (333,'Maastohiihtokeskus');
    INSERT INTO tmp_signs (othId, name) VALUES (334,'Golfkenttä');
    INSERT INTO tmp_signs (othId, name) VALUES (335,'Huvi- ja teemapuisto');
    INSERT INTO tmp_signs (othId, name) VALUES (336,'Mökkimajoitus');
    INSERT INTO tmp_signs (othId, name) VALUES (337,'Aamiaismajoitus');
    INSERT INTO tmp_signs (othId, name) VALUES (338,'Suoramyyntipaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (339,'Käsityöpaja');
    INSERT INTO tmp_signs (othId, name) VALUES (340,'Kotieläinpiha');
    INSERT INTO tmp_signs (othId, name) VALUES (341,'Ratsastuspaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (342,'Matkailutie (pelkkä teksti)');
    INSERT INTO tmp_signs (othId, name) VALUES (343,'Matkailutie (kuva ja teksti)');
    INSERT INTO tmp_signs (othId, name) VALUES (344,'Tilapäinen opastusmerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (345,'Kohde risteävässä suunnassa');
    INSERT INTO tmp_signs (othId, name) VALUES (346,'Kohde nuolen suunnassa');
    INSERT INTO tmp_signs (othId, name) VALUES (347,'Kohde nuolen suunnassa ja etäisyys');
    INSERT INTO tmp_signs (othId, name) VALUES (348,'Kohde edessä ja etäisyys');
    INSERT INTO tmp_signs (othId, name) VALUES (349,'Matkailuauto');
    INSERT INTO tmp_signs (othId, name) VALUES (350,'Moottorikelkka');
    INSERT INTO tmp_signs (othId, name) VALUES (351,'Traktori');
    INSERT INTO tmp_signs (othId, name) VALUES (352,'Vähäpäästöinen ajoneuvo');
    INSERT INTO tmp_signs (othId, name) VALUES (353,'Pysäköintitapa reunakiven päälle');
    INSERT INTO tmp_signs (othId, name) VALUES (354,'Pysäköintitapa reunakiven laitaan');
    INSERT INTO tmp_signs (othId, name) VALUES (355,'Tunneliluokka');
    INSERT INTO tmp_signs (othId, name) VALUES (356,'Pysäköintiajan alkamisen osoittamisvelvollisuus (sininen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (358,'Latauspaikka');
    INSERT INTO tmp_signs (othId, name) VALUES (360,'Etuajooikeutetun liikenteen suunta kääntyville');
    INSERT INTO tmp_signs (othId, name) VALUES (361,'Kaksisuuntainen pyörätie (keltainen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (362,'Kaksisuuntainen pyörätie (sininen pohja)');
    INSERT INTO tmp_signs (othId, name) VALUES (363,'Hätäpuhelin ja sammutin');
    INSERT INTO tmp_signs (othId, name) VALUES (364,'Sulkupuomi');
    INSERT INTO tmp_signs (othId, name) VALUES (365,'Sulkuaita');
    INSERT INTO tmp_signs (othId, name) VALUES (366,'Sulkuaita nuolilla');
    INSERT INTO tmp_signs (othId, name) VALUES (367,'Sulkupylväs vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (368,'Sulkupylväs oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (369,'Sulkupylväs');
    INSERT INTO tmp_signs (othId, name) VALUES (370,'Sulkukartio');
    INSERT INTO tmp_signs (othId, name) VALUES (371,'Taustamerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (372,'Kaarteen suuntamerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (373,'Reunamerkki vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (374,'Reunamerkki oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (375,'Korkeusmerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (376,'Alikulun korkeusmitta');
    INSERT INTO tmp_signs (othId, name) VALUES (377,'Liikennemerkkipylvään tehostamismerkki (sinivalkoinen)');
    INSERT INTO tmp_signs (othId, name) VALUES (378,'Liikennemerkkipylvään tehostamismerkki (keltamusta)');
    INSERT INTO tmp_signs (othId, name) VALUES (379,'Erkanemismerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (380,'Reunapaalu vasemmalla');
    INSERT INTO tmp_signs (othId, name) VALUES (381,'Reunapaalu oikealla');
    INSERT INTO tmp_signs (othId, name) VALUES (382,'Siirtokehotus');
    INSERT INTO tmp_signs (othId, name) VALUES (383,'Paikannusmerkki');
    INSERT INTO tmp_signs (othId, name) VALUES (384,'Automaattinen liikennevalvonta');
    INSERT INTO tmp_signs (othId, name) VALUES (385,'Tekninen valvonta');
    INSERT INTO tmp_signs (othId, name) VALUES (386,'Poronhoitoalue tekstillinen');
    INSERT INTO tmp_signs (othId, name) VALUES (387,'Poronhoitoalue ilman tekstiä');
    INSERT INTO tmp_signs (othId, name) VALUES (388,'Yleinen nopeusrajoitus rajalla');
    INSERT INTO tmp_signs (othId, name) VALUES (389,'Valtion raja');
    INSERT INTO tmp_signs (othId, name) VALUES (390,'Kuorma-autolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (391,'Henkilöautolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (392,'Linja-autolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (393,'Pakettiautolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (394,'Moottoripyörälle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (395,'Mopolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (396,'Traktorille tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (397,'Matkailuajoneuvolle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (398,'Polkupyörälle tarkoitettu reitti');
    INSERT INTO tmp_signs (othId, name) VALUES (399,'Ajokaistan päättyminen');
    INSERT INTO tmp_signs (othId, name) VALUES (400,'Seututien numero');
    INSERT INTO tmp_signs (othId, name) VALUES (287,'Paikannimi');
    INSERT INTO tmp_signs (othId, name) VALUES (288,'Paikannimi');
    INSERT INTO tmp_signs (othId, name) VALUES (172,'Paikannimi');
    INSERT INTO tmp_signs (othId, name) VALUES (66,'Linja-autopysäkki');
    INSERT INTO tmp_signs (othId, name) VALUES (247,'Linja-autopysäkki');
    INSERT INTO tmp_signs (othId, name) VALUES (160,'Tienviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (162,'Tienviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (167,'Tienviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (168,'Tienviitta');
    INSERT INTO tmp_signs (othId, name) VALUES (165,'Kiertotien viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (166,'Kiertotien viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (274,'Kiertotien viitta');
    INSERT INTO tmp_signs (othId, name) VALUES (142,'Vaikutusalue nuolen suuntaan');
    INSERT INTO tmp_signs (othId, name) VALUES (143,'Vaikutusalue nuolen suuntaan');
    INSERT INTO tmp_signs (othId, name) VALUES (59,'Maksullinen pysäköinti');
    INSERT INTO tmp_signs (othId, name) VALUES (357,'Maksullinen pysäköinti');
    INSERT INTO tmp_signs (othId, name) VALUES (146,'Etuajooikeutetun liikenteen suunta');
    INSERT INTO tmp_signs (othId, name) VALUES (359,'Etuajooikeutetun liikenteen suunta');

    COMMIT;

    -- Save the property Id for PUBLIC_ID ='trafficSigns_type' of the traffic signs (asset_type_id = 300)
	SELECT ID INTO v_property_id
	FROM PROPERTY
	WHERE ASSET_TYPE_ID  = 300
	AND PUBLIC_ID ='trafficSigns_type';

    -- Update the name of the current traffic signs
	MERGE INTO ENUMERATED_VALUE ev
    	USING (SELECT * FROM TMP_SIGNS) ts
    	ON (ev.PROPERTY_ID  = v_property_id AND ts.othId = ev.value)
  	WHEN MATCHED THEN
    	UPDATE SET ev.NAME_FI = ts.name, ev.MODIFIED_DATE = SYSDATE, ev.MODIFIED_BY = 'db_migration_v228'
	    WHERE ev.NAME_FI <> ts.name;

    -- INSERT the new traffic signs
	INSERT INTO ENUMERATED_VALUE (id, PROPERTY_ID , VALUE , NAME_FI , CREATED_BY , CREATED_DATE )
	SELECT primary_key_seq.nextval, v_property_id, ts.othId, ts.name, 'db_migration_v228', SYSDATE
	FROM tmp_signs ts
	WHERE othId NOT IN ( SELECT ev.value FROM ENUMERATED_VALUE ev  WHERE ev.PROPERTY_ID  = v_property_id  );

    COMMIT;
END;

-- We no longer need the temporary table
DELETE FROM tmp_signs;
DROP TABLE tmp_signs;