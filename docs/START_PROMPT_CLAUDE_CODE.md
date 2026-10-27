# Start-Prompt für Claude Code (in den Projektordner wechseln, `claude` starten, diesen Text einfügen)

```
Du arbeitest im Projekt "Power Grid: High Voltage" (NeoForge 1.21.1 Addon für Create: Power Grid 0.6.2) auf meinem Windows-PC. Lies zuerst CLAUDE.md, TESTPLAN.md, docs/ERDKABEL_SPEZIFIKATION.md und docs/API_ANNAHMEN.md vollständig.

Der Code wurde bisher NIE kompiliert oder gestartet. Deine Aufgabe: alles zum Laufen bringen, gründlich testen und Bugs finden und beheben – ohne Design, IDs oder Spielmechanik zu ändern.

Vorgehen:
1. Umgebung prüfen (Java 21, Power-Grid-Jar 0.6.2 für NeoForge 1.21.1 nach libs\powergrid-mc1.21.1-0.6.2.jar – lade es selbst von Modrinth, falls es fehlt).
2. .\gradlew.bat build --stacktrace so lange iterieren, bis es fehlerfrei baut. Falsche API-Namen anhand der echten Klassen (Gradle-Cache, javap) korrigieren, nicht raten.
3. Dedicated Server (runServer) starten, Log auf Fehler zu pghv prüfen (Registries, Rezepte, Loot-Tables, Tags, Modelle).
4. NeoForge-GameTests für alle Punkte in CLAUDE.md Abschnitt 4 schreiben und mit runGameTestServer ausführen, bis alle grün sind. Besonders: Item-Dupes beim Kabel-Trennen, Multiblock-Abbau des Lüfters, Neuaufbau der Kabel-Verbindungen nach Chunk-Neuladen, Konstruktor-Reihenfolge in SmartBlockEntity (Felder ohne Initializer).
5. runClient starten und prüfen, dass das Spiel ohne Modell-/Texturfehler lädt. Gib mir dann eine kurze Liste, was ich im Spiel optisch prüfen soll.
6. Jeden Fix in docs/BUGLOG.md eintragen.

Frag mich nur bei Designentscheidungen. Am Ende: Bericht auf Deutsch (behobene Bugs, Teststatus je Feature, offene Punkte) und Pfad zum fertigen Jar.
```
