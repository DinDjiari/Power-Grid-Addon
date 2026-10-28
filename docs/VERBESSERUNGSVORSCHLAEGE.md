# Verbesserungsvorschläge nach dem ersten Build (2026-10-27)

Grundlage: erster Build, Serverstart, Clientstart bis in eine Testwelt, 34 GameTests (alle grün) und Durchsicht
des Quellcodes. **Nichts davon ist umgesetzt.** Punkte, die Design oder Spielmechanik betreffen, brauchen Toms
Entscheidung. Die Dringlichkeit ist in Klammern angegeben.

(Die Screenshots vom ersten Start mit den alten Lüfter-Designs wurden beim Aufräumen am 2026-10-28 entfernt.)

## Build und Projekt

1. **Automatischer Build auf GitHub (CI)** *(hoch)*
   Ein GitHub-Actions-Workflow sollte bei jedem Push `build` und `runGameTestServer` ausführen. Bisher hängt
   alles daran, dass jemand lokal testet.
2. **Power-Grid-Abhängigkeit reproduzierbar machen** *(hoch, Voraussetzung für 1.)*
   Das Jar muss heute von Hand nach `libs/` kopiert werden. Besser ist das Modrinth-Maven-Repo, das schon
   in `build.gradle` eingetragen ist, z. B. `compileOnly "maven.modrinth:<projekt>:0.6.2"`. Dann braucht
   auch die CI keine manuelle Datei.
3. **Deprecation-Warnung beheben** *(niedrig)*
   `@EventBusSubscriber(bus = ...)` in `PghvClient` ist in NeoForge als „for removal“ markiert. Das sollte
   vor einem NeoForge-Update auf die neue Registrierung umgestellt werden.
4. **Datengenerierung** *(niedrig)*
   Rezepte, Loot-Tables, Tags und Blockstates sind von Hand geschrieben. Ein `runData`-Setup verhindert
   Tippfehler bei späteren Änderungen. Aktuell sind alle Dateien fehlerfrei; Server-Log und Tests haben das
   bestätigt.

## Kühllüfter

5. **Luftstrom nur aus einer der vier Zellen** *(UMGESETZT: Tom wünscht volle 2×2-Fläche)*
   Create bläst nur aus der Hauptzelle (unten links), nicht aus der ganzen 2×2-Fläche. Ein Lüfter dieser
   Größe würde realistischer auf der vollen Fläche blasen. Dazu bräuchte es vier Luftströme oder eine
   eigene Entity-Schiebelogik.
6. **Überhitzungstemperatur fest auf 175 °C** *(mittel)*
   Die Kühlleistung wird aus `maxPower / (175 °C − Umgebung)` berechnet, weil Power Grid den Wert nicht
   ausliest. Ändert Power Grid den Wert per Config, rechnet der Lüfter falsch. Lösung: per Reflection
   auslesen oder Power Grid um einen Getter bitten.
7. **Explosion kann 0 Lüfter droppen** *(Designfrage)*
   Die Loot-Table hat `survives_explosion`, eine Explosion kann den Lüfter also komplett zerstören. Das ist
   vanilla-üblich, aber eine bewusste Entscheidung wert.

## Erdkabel

8. **Kabelvorschau beim Verlegen** *(mittel, Bedienkomfort)*
   Beim Verlegen sieht man den künftigen Kabelweg nicht, nur Chat-Meldungen. Eine Linie oder Umrisse der
   Pfadzellen auf dem Client wie bei Power Grids Draht-Vorschau würden Fehlplatzierungen vermeiden.
9. **Durchgebrannte Adern nur durch Neuverlegen reparierbar** *(ERLEDIGT: Durchbrennen auf Toms Wunsch entfernt)*
   Nach einer Überlast muss das ganze Kabel getrennt und neu verlegt werden. Möglich wäre eine Reparatur
   per Rechtsklick mit einem passenden Draht an der Box.
10. **Unterirdische Kabelabschnitte** *(Designfrage)*
    In festen Blöcken liegt kein Segmentblock. Gräbt man dort, wird das Kabel nicht getrennt; es ist nur an
    sichtbaren Segmenten und an den Boxen trennbar. Das ist laut Spezifikation so gewollt, kann Spieler
    aber überraschen.
11. **Kabel-Items stapeln während des Verlegens nicht** *(niedrig)*
    Solange der Verlege-Zustand am gehaltenen Stack hängt, stapeln aufgesammelte Kabel nicht mit ihm. Der
    Verbrauch zählt das Inventar trotzdem korrekt mit (getestet). Alternative: den Zustand am Spieler
    speichern statt am Item.
12. **Globale Variable für Kreativ-Abbau** *(niedrig, Robustheit)*
    `CableBoxBlockEntity.creativeBreakPos` ist statisch und gilt für alle Welten und Dimensionen. In der
    Praxis unkritisch, weil sie sofort verbraucht wird. Sauberer wäre ein Wert pro Level oder die Prüfung
    über den abbauenden Spieler.
13. **Erzwungenes Chunk-Laden beim Trennen** *(niedrig, beobachten)*
    Wird Box B abgebaut, während Box A in einem ungeladenen Chunk liegt, lädt der Code diesen Chunk
    synchron (bis 256 Blöcke entfernt). Auf großen Servern kann das kurz ruckeln.
14. **Registry-Abfrage jeden Tick** *(niedrig, Performance)*
    `checkCurrents` fragt pro Ader und Tick den Maximalstrom aus der Datapack-Registry ab. Der Wert kann
    beim Aufbau der Verbindung zwischengespeichert werden.

## Tests

15. **Echtes Chunk-Entladen** *(mittel)*
    Der GameTest bildet das Entladen nach: Block-Entity speichern, entfernen und neu laden. Ein Test mit
    wirklich entladenen Chunks (Box 200 Blöcke entfernt, Spieler fliegt weg) steht noch aus, im Spiel oder
    als aufwendigerer Test.
16. **Client-Seite ungetestet** *(mittel)*
    Renderer (Rotor-Animation, Zeiger), Goggles-Texte, Aderfarben (BlockColor/ItemColor) und Netzwerk-Sync
    sind nur per Screenshot geprüft. Diese Punkte bleiben für Toms optische Prüfung nach `TESTPLAN.md`.
17. **Mehrspieler** *(mittel)*
    Zwei Spieler verlegen gleichzeitig, Server mit echten Clients: nicht getestet.

## Bereits geprüft und in Ordnung

- Sprachdateien `de_de` / `en_us`: identische Schlüssel, alle im Code verwendeten Schlüssel vorhanden.
- Rezepte, Loot-Tables, Tags und die Datapack-Registry `powergrid:wire_types` laden ohne Fehler.
- Kein Item-Dupe beim Trennen über Segment, Box, Schraubenschlüssel, Explosion oder im Kreativmodus.
- Der Multiblock-Abbau des Lüfters entfernt alle 4 Teile und droppt genau 1 Item.
- Konstruktor-Reihenfolge der SmartBlockEntities ist korrekt (keine Null-Felder).
