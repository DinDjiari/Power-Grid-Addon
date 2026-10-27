# Prompt für Claude Code: Branch `feature/messing-luefter` testen

```
Lies CLAUDE.md und docs/MESSING_LUEFTER_SPEZIFIKATION.md. Teste den Branch feature/messing-luefter (PR nach main), bevor Tom merged.

Änderungen gegenüber main:
1. Andesit-Lüfter: Drehzahlmenü wie Creates Rotation Speed Controller in % (0..100, 1er-Schritte, Board mit 10er-Marken).
2. Andesit-Lüfter: Wertefeld sitzt jetzt OBEN MITTIG auf dem Lüfter (neue CoolingFanTopBlockEntity auf Teil 2/3, leitet an den
   Hauptteil weiter; Feld im Hauptteil ausgeblendet). Test fanTopValueBoxForwardsSpeed; CoolingFanTests.assertFan angepasst.
3. Neuer Block brass_cooling_fan (Messing-Trafolüfter): Rezept Electron Tube / Andesit-Lüfter / Messingbarren (Mittelspalte),
   automatische Regelung nach Trafo-Temperatur (Zieltemperatur 40..160 °C im Wertefeld oben), Kühlung/Rotor/Luftstrom x2,
   eigene BE-Typen brass_cooling_fan(_top), Rotor-Partial pghv:block/brass_cooling_fan/rotor, GameTests BrassCoolingFanTests.

Vorgehen:
1. git fetch && git checkout feature/messing-luefter && git pull
2. .\gradlew.bat build --stacktrace – Fehler mit kleinster Korrektur beheben (eigener Commit, Zeile in docs/BUGLOG.md).
3. .\gradlew.bat runGameTestServer – alle Tests grün (Zeitfenster in BrassCoolingFanTests sind geschätzt; nur anpassen, wenn die
   Logik stimmt und nur das Timing knapp ist – im Bericht begründen).
4. runServer: Log auf pghv-Fehler (Modelle, Texturen create:block/brass_casing usw.).
5. runClient: Wertefeld oben mittig bei beiden Lüftern – Lage, Leserichtung, beide oberen Hälften anklickbar, Board 0–100 % bzw.
   40–160 °C; Messing-Lüfter am heißen Medium-Trafo regelt hoch, am kalten steht er; Rotor dreht beim Messing-Lüfter doppelt so schnell
   (sichtbar doppelt so schnell wie Andesit, kein Stroboskop-Effekt); Texturen ok. Screenshots nach docs/screenshots/messing_*.png.
6. Optik Messing-Lüfter (Toms Wunsch): Lade die Mod „Create Encased“ für NeoForge 1.21.1 (Modrinth/CurseForge, MIT-Lizenz)
   und prüfe, ob sie einen Brass Encased Fan hat. Wenn ja: dessen Gitter- und Flügeltextur (und ggf. Seiten) als Basis für
   brass_cooling_fan verwenden (nach src/main/resources/assets/pghv/textures/block/ kopieren, MIT-Lizenzhinweis + Quelle in
   ASSET-NOTICE.txt), Modelle models/block/brass_cooling_fan/* darauf umstellen, 2×2 vergrößert wie beim Andesit-Lüfter,
   unsere Keramik-Pins bleiben. Wenn nein: Tom fragen. Screenshot vorher/nachher.
7. Commit + Push des Branches (nicht nach main mergen). Bericht an Tom (Deutsch): Teststatus je Punkt, Bugs, Screenshots, Empfehlung mergen ja/nein.
```
