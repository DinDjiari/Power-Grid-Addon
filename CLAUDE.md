# Power Grid: High Voltage (pghv) – Arbeitsanweisung für Claude Code

Auftraggeber: Tom (Tomrox GmbH). Mit ihm auf **Deutsch** kommunizieren, ihn mit „Tom“ ansprechen.
Umgebung: Windows (PowerShell). Gradle über `.\gradlew.bat`. Java 21 nötig.

## Was das ist
NeoForge-1.21.1-Addon (Mod-ID `pghv`) für **Create: Power Grid 0.6.2** (Mod-ID `powergrid`) mit Create 6.0.10.
Inhalt:
1. **HV-Voltmeter** (`hv_voltmeter`): Power-Grid-Voltmeter, nur Andesit→Messing umgefärbt; Messbereiche 20 kV / 200 kV / 2 MV.
2. **Trafo-Kühllüfter** (`cooling_fan`): runder Industrieventilator, 2×2 Blöcke, ⅓ Block dick, 3 Tiefenpositionen (slot 0/1/2), rastet beim Draufklicken auf die große Seite des Power-Grid-Medium-Trafos ein, 240 V / 300 W, Drehzahlmenü (Create-Wertepanel), kühlt den Trafo, bläst Entities weg (Create-Luftstrom).
3. **Mehrleiter-Erdkabel**: 3 beliebige Power-Grid-Drähte → `bare_cable`; + 8 getrockneter Seetang → `underground_cable`; Verlegen zwischen zwei `cable_box` mit Ankerpunkten; jede Ader ein eigener Leiter. Spezifikation: `docs/ERDKABEL_SPEZIFIKATION.md`.

Der Code wurde **ohne Build** geschrieben (kein Maven-Zugang). Liste der ungeprüften Annahmen: `docs/API_ANNAHMEN.md`. Testcheckliste: `TESTPLAN.md`.

## Regeln
- Designs, Texturen, Modell-Geometrie, IDs, Blockstate-Properties und Spielmechanik laut Spezifikation **nicht eigenmächtig ändern**. Nur Fehler beheben. Designfragen an Tom.
- Kleinste mögliche Korrekturen; nach jeder Änderungsrunde erneut bauen.
- Keine Assets von Create/Power Grid ins Repo kopieren (Lizenz). Power-Grid-Jar liegt nur lokal in `libs/` und wird nicht eingecheckt.
- Jeder Fix bekommt eine Zeile in `docs/BUGLOG.md` (Datum, Datei, Symptom, Ursache, Fix) und einen eigenen Git-Commit mit aussagekräftiger Nachricht (Deutsch). Arbeiten auf einem Branch `fix/erster-build`, am Ende pushen; `main` nur nach Toms OK mergen.

## Ablauf
1. **Umgebung**: `java -version` (21?). Sonst `winget install Microsoft.OpenJDK.21`. Power-Grid-Jar 0.6.2 (NeoForge 1.21.1) nach `libs\powergrid-mc1.21.1-0.6.2.jar` (Modrinth-Projekt `eWiBLJ9R`, z.B. über `https://api.modrinth.com/v2/project/eWiBLJ9R/version?game_versions=["1.21.1"]&loaders=["neoforge"]`).
2. **Build**: `.\gradlew.bat build --stacktrace`. Compile-Fehler anhand von `docs/API_ANNAHMEN.md` und den echten Klassen in den Gradle-Caches beheben (Quellen/Signaturen in `~/.gradle/caches` bzw. per IDE/`javap` prüfen). Ggf. ModDevGradle-Version anheben.
3. **Datenprüfung**: `.\gradlew.bat runData` ist nicht eingerichtet – stattdessen `runServer` (eula akzeptieren in `run\eula.txt`) starten, bis „Done“ erscheint, dann `stop`. `run\logs\latest.log` auf `pghv`-Fehler prüfen (Rezepte, Loot-Tables, Tags, Registries, Datapack-Registry `powergrid:wire_types`).
4. **Automatische Tests**: NeoForge-GameTests unter `src/test` oder `src/main/java/.../gametest` anlegen (`@GameTestHolder("pghv")`, `@PrefixGameTestTemplate(false)`, leere Strukturvorlage) und mit `.\gradlew.bat runGameTestServer` ausführen. Mindestens:
   - Voltmeter: Platzieren, BlockEntity vorhanden, Bereichswechsel.
   - Kühllüfter: Platzieren frei und auf Medium-Trafo (Einrasten, alle 4 Richtungen), Abbau entfernt alle 4 Teile und droppt genau 1 Item, Slot-Umschalten, Kühlwirkung (Temperatur sinkt schneller mit Lüfter).
   - Erdkabel: Rezepte (Adern-Komponente korrekt, frei kombinierbar), Verlegen per simuliertem Item-Use (Start, Anker, Ziel), Segmente/Verbindungs-Properties, Verbrauch, Trennen ohne Item-Dupe (Segment, Box, Explosion), Widerstand je Ader = R/Block × Länge, Speichern/Laden (Chunk entladen/neu laden) baut Verbindungen wieder auf.
5. **Client**: `.\gradlew.bat runClient` starten und prüfen, ob das Spiel ohne Fehler bis ins Hauptmenü/eine Testwelt lädt (Modellfehler „missing model“, Texturfehler im Log). Optische Prüfung macht Tom anhand `TESTPLAN.md`; ihm eine kurze Liste geben, was er im Spiel ansehen soll.
6. **Bericht an Tom** (Deutsch, knapp): gefundene/behobene Bugs, Teststatus je Feature, offene Punkte, nächste Schritte. Fertiges Jar: `build\libs\pghv-0.1.0.jar`.
