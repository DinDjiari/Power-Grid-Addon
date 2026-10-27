# Prompt für Claude Code: Branch `feature/ponder` testen

Enthält auch den Überhitzungsschutz (Branch baut darauf auf) und den messing-eingefärbten Messing-Lüfter.

```
Lies CLAUDE.md. Teste den Branch feature/ponder (PR nach main), bevor Tom merged. Er enthält:
1. Überhitzungsschutz (docs/UEBERHITZUNGSSCHUTZ_SPEZIFIKATION.md, Tests OverheatProtectionTests) – falls noch nicht getestet.
2. Messing-Lüfter sieht jetzt aus wie der Andesit-Lüfter: gleiche Create-Texturen (fan_casing, fan_blades), per tintindex/BlockColor
   messing eingefärbt (PghvColors.BRASS_TINT), Rotor per SuperByteBuffer.color, Seiten create:block/brass_casing.
3. Ponder-Szenen (client/ponder/*, assets/pghv/ponder/*.nbt, Texte EN/DE in den Lang-Dateien): Andesit-Lüfter, Messing-Lüfter
   (Regelung + Überhitzungsschutz), Erdkabel. TESTPLAN.md Abschnitt 3d.

Vorgehen:
1. git fetch && git checkout feature/ponder && git pull; falls main weiter ist: git merge origin/main.
2. .\gradlew.bat build --stacktrace – Fehler mit kleinster Korrektur beheben (Ponder-API laut docs/API_ANNAHMEN.md prüfen; eigener Commit + BUGLOG).
3. .\gradlew.bat runGameTestServer – alle Tests grün.
4. runClient: Jede Ponder-Szene über W im Inventar öffnen, komplett durchlaufen lassen, einmal mit Sprache Englisch und einmal Deutsch.
   Prüfen: Struktur lädt (keine fehlenden Blöcke), Texte richtig (kein Key-Text), Markierungen/Linien an den richtigen Stellen,
   HV-Schalter öffnet/schließt in Szene 2b sinnvoll, Items fliegen in Szene 1 nicht doppelt weit. Messing-Farbton des Lüfters bewerten.
   Screenshots nach docs/screenshots/ponder_*.png und messing_tint_*.png.
5. Commit + Push des Branches (nicht nach main mergen). Bericht an Tom (Deutsch): Teststatus, Bugs, Screenshots, Empfehlung mergen ja/nein.
```
