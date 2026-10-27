# Prompt für Claude Code: Branch `feature/ueberhitzungsschutz` testen

Voraussetzung: PR `feature/messing-luefter` ist getestet und nach main gemergt (dieser Branch baut darauf auf).

```
Lies CLAUDE.md und docs/UEBERHITZUNGSSCHUTZ_SPEZIFIKATION.md. Teste den Branch feature/ueberhitzungsschutz (PR nach main), bevor Tom merged.

Inhalt: Messing-Lüfter bekommt eine 3-polige Steckdose (Teil 1, unten rechts: Servo +48 V, Servo −, Steuersignal),
Überhitzungsschutz (Auslösen ≥155 °C ⇒ Steuerspannung ±2,5 V ⇒ Servo dreht 180° und öffnet einen drehbetätigten Schalter,
Zurücksetzen ≤100 °C), Servo-Richtung per Schraubenschlüssel auf die Steckdose, Item pghv:data_cable (Kupfer+Eisen+Golddraht)
spannt drei sichtbare Power-Grid-Drähte Steckdose→Servo. GameTests: OverheatProtectionTests.

Vorgehen:
1. git fetch && git checkout feature/ueberhitzungsschutz && git pull; falls main inzwischen weiter ist: git merge origin/main (Konflikte lösen).
2. .\gradlew.bat build --stacktrace – Fehler mit kleinster Korrektur beheben (eigener Commit, Zeile in docs/BUGLOG.md).
3. .\gradlew.bat runGameTestServer – alle Tests grün (der Ende-zu-Ende-Test darf bis 3000 Ticks laufen).
4. runServer: Log auf pghv-Fehler.
5. runClient, Testaufbau: Medium-Trafo + Messing-Lüfter + Servo + Welle + Power-Grid-HV-Schalter im Trafo-Stromkreis.
   Datenkabel Steckdose→Servo (drei Drähte sichtbar: Kupfer, Eisen, Gold), Trafo überlasten bis ≥155 °C ⇒ Servo dreht, Schalter öffnet,
   Trafo kühlt ⇒ bei ≤100 °C dreht Servo zurück, Schalter schließt. Richtung per Schraubenschlüssel prüfen. Servo-Tempo bewerten
   (öffnet der Schalter rechtzeitig vor 175 °C?). Goggles-Zeilen prüfen. Screenshots nach docs/screenshots/ueberhitzung_*.png.
6. Commit + Push des Branches (nicht nach main mergen). Bericht an Tom (Deutsch): Teststatus, Bugs, Screenshots, Empfehlung mergen ja/nein.
```
