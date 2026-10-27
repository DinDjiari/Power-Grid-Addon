# Prompt für Claude Code: Branch `feature/luefter-v3-luftstrom` testen

Im Projektordner auf dem Windows-PC `claude` starten und diesen Text einfügen:

```
Lies CLAUDE.md. Teste den Branch feature/luefter-v3-luftstrom (PR nach main) vollständig, bevor Tom ihn merged.

Änderungen auf dem Branch gegenüber main:
1. Kühllüfter: Luftstrom aus allen 4 Zellen (PartAirSource in HvCoolingFanBlockEntity), neuer GameTest fanBlowsFromAllFourCells.
2. Kühllüfter: beide Pins auf Teil 0 (unten links): plus x11..14, minus x6..9 (HvCoolingFanBlock.TERMINALS/terminal()), Tests angepasst.
3. Kühllüfter-Design: Create-Encased-Fan-Look auf 2x2 hochskaliert. Modelle verweisen auf Create-Texturen
   (create:block/fan_casing, fan_side, fan_blades, axis, axis_top) – NICHT kopieren, nur referenzieren.
   render_type cutout, Gitter vorn und hinten, Rotor-Partial pghv:block/cooling_fan/rotor.
4. Erdkabel: Durchbrennen der Adern entfernt (CableBoxBlockEntity), Test cableOverloadNeverBurns.

Vorgehen:
1. git fetch && git checkout feature/luefter-v3-luftstrom && git pull
2. .\gradlew.bat build --stacktrace – Fehler beheben (kleinste Korrektur, eigener Commit, Zeile in docs/BUGLOG.md).
3. .\gradlew.bat runGameTestServer – alle Tests grün. Wenn fanBlowsFromAllFourCells scheitert: prüfen, ob Creates AirCurrent
   mit einer Nicht-BlockEntity-Quelle (PartAirSource) funktioniert; ggf. robust lösen, ohne Verhalten zu ändern.
4. runServer: Log auf pghv-Fehler prüfen (Modelle, fehlende Texturen „missing texture“ für create:block/... beachten).
5. runClient, Testwelt: Lüfter frei und am Medium-Trafo in allen 4 Richtungen und 3 Slots, Rotor dreht sich,
   Texturen korrekt (kein lila-schwarz), Gitter durchsichtig (Cutout), Pins unten links, Highlight beim Drüberfahren
   zeigt +/−; Kabel und Voltmeter kurz gegenprüfen. Screenshots nach docs/screenshots/ (luefter_v4_*.png).
6. Alles committen und den Branch pushen (NICHT nach main mergen).
7. Bericht an Tom auf Deutsch: Teststatus je Punkt 1–4, behobene Bugs, Screenshots, Empfehlung „mergen ja/nein“.
```
