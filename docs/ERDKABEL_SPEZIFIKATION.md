# Erdkabel-System – Spezifikation (Vertrag zwischen Code- und Asset-Worker)

Projekt: /home/claude/pghv (NeoForge 1.21.1, Mod-ID `pghv`, Addon für Create: Power Grid 0.6.2).
Power-Grid-Quelle: /root/pg121 (Java: src/main/java/org/patryk3211/powergrid/..., Assets/Daten unter src/main/resources und fabric/src/main/generated).
Bestehender Code als Stilvorlage: src/main/java/com/tomrox/pghv/** (Voltmeter, Kühllüfter, Registries mit DeferredRegister).
Build im Sandbox NICHT möglich (Maven gesperrt) – nur statisch prüfen, API immer gegen /root/pg121 und dessen Nutzung von Create/NeoForge verifizieren.

## Spielablauf (Wunsch des Auftraggebers)
1. 3 beliebige Power-Grid-Drähte (frei kombinierbar, z.B. 2× Kupferdraht + 1× Eisendraht) formlos im Crafting → **blankes Kabel** (`pghv:bare_cable`). Kann nichts, nicht platzierbar.
2. Blankes Kabel mittig, 8× getrockneter Seetang (minecraft:dried_kelp) ringsum → **isoliertes Erdkabel** (`pghv:underground_cable`). 1 Item = 1 Block Kabellänge.
3. **Kabelbox** (`pghv:cable_box`): 1 Block, kupferfarben, oben 3 Anschlüsse (Terminal 0/1/2 = Ader 0/1/2), seitlich ein großer Eisen-Anschluss (Kabelbuchse). Box „passt sich an“: Buchse dreht sich zur Kabelrichtung, die 3 Anschluss-Kappen nehmen die Farbe ihrer Ader an.
4. Verlegen wie Schienen: Rechtsklick mit Erdkabel auf Kabelbox = Start (auf Höhe der Box). Rechtsklick auf Boden/Blockfläche = Ankerpunkt (neuer Start für Ecken; damit auch hoch/runter). Rechtsklick auf zweite freie Kabelbox = verbinden. Sneak-Rechtsklick in die Luft = abbrechen.
5. Das Kabel liegt als Blöcke (`pghv:cable_segment`) sichtbar am Boden der Zellen; Zellen, die fest sind, werden übersprungen (dort liegt es unsichtbar „unterirdisch“).
6. Elektrisch: 3 Adern, jede ein eigener Leiter zwischen Terminal i der Box A und Terminal i der Box B (Eisenader = dritter Leiter, höherer Widerstand).

## IDs
- Items: `pghv:bare_cable`, `pghv:underground_cable`, BlockItem `pghv:cable_box`. (`cable_segment` hat KEIN Item.)
- Blöcke: `pghv:cable_box` (BlockEntity `pghv:cable_box`), `pghv:cable_segment` (BlockEntity `pghv:cable_segment`, speichert nur Owner-Box-Position).
- Data Component `pghv:cable_cores`: Liste von genau 3 Item-IDs (ResourceLocation) der Adern, Reihenfolge = Ader 0,1,2. Auf bare_cable und underground_cable.
- Data Component `pghv:cable_laying` (nur während Verlegen, auf dem gehaltenen underground_cable): Dimension, Startbox-Pos, Liste Ankerpunkte (BlockPos).
- Recipe-Serializer/-Typen: `pghv:cable_assembly` (3 Drähte → bare_cable), `pghv:cable_insulation` (bare_cable + 8 dried_kelp → underground_cable, Adern übernehmen).

## Werte je Ader (aus Power Grids Datapack-Registry `powergrid:wire_types`, Klasse WireRegistry/WireItemEntry)
- Erlaubt als Ader: jedes Item mit WireItemEntry, das `cord == false` und `insulated == false` ist (Kupfer `powergrid:wire`, `powergrid:iron_wire`, `powergrid:golden_wire`, auch Drähte anderer Addons).
- Widerstand pro Block Kabellänge je Ader: R_block = resistancePerItem × itemsPerMeter (Formel bitte an Power Grids eigener Längen/Widerstands-Rechnung prüfen und ggf. angleichen; dokumentieren).
- Maximalstrom je Ader: maximumCurrent (in Goggles/Tooltip anzeigen; kein Durchbrennen (auf Wunsch des Auftraggebers entfernt); Goggles färben den Strom rot über maximumCurrent).
- Bare-Cable-Rezept-Ausgabe: 2 Stück (PG-Draht: itemsPerMeter 0.5 → 1 Draht = 2 m). Isolier-Rezept: 1 bare_cable + 8 dried_kelp → 1 underground_cable. Zahlen in den Rezept-JSONs einstellbar (Feld `count`).

## Kabelbox – Geometrie (north-lokal, Pixel 0..16, Buchse zeigt nach `facing`, facing=north → Buchse auf der −z-Seite)
- Körper: x 1..15, y 0..13, z 1..15 (Kupfergehäuse, Create-/Power-Grid-Stil).
- Terminals oben (Klickboxen im Java = diese Boxen): T0 x 2..5, T1 x 6.5..9.5, T2 x 11..14; jeweils y 13..16, z 6.5..9.5. Kappe (oberste 1–1.5 px) im Modell mit `tintindex` 0/1/2 (BlockColor liefert Aderfarbe; Default Kupfer).
- Buchse (Eisen, „groß“): Flansch x 3..13, y 0..9, z 0..1 plus Ring/Kragen; Kabel-Öffnung dunkel x 5.5..10.5, y 0..4 (dort kommt das Kabel heraus).
- Collision-Shape: Körper + Buchse + Terminals.
- Blockstate: `facing` (horizontal), Varianten y 0/90/180/270. Platzieren: Buchse zeigt vom Spieler weg (oder zum Spieler – Code entscheidet, dokumentieren); beim Verbinden dreht der Code die Box so, dass die Buchse zum ersten Kabelschritt zeigt (falls horizontal).

## Kabelsegment – Geometrie
- Querschnitt 5×4 px liegend am Zellenboden: x 5.5..10.5 (bzw. z), y 0..4.
- Multipart-Blockstate mit BooleanProperties `north,east,south,west,up,down` + `waterlogged`:
  - immer: Mittelstück x 5.5..10.5, y 0..4, z 5.5..10.5
  - north: z 0..5.5 (gleicher Querschnitt), south: z 10.5..16, west: x 0..5.5, east: x 10.5..16
  - up: Steigleitung x 5.5..10.5, y 4..16, z 5.5..10.5 (die Zelle darüber hat ihr Mittelstück bei y 0..4 → durchgehend)
  - down: keine eigene Geometrie nötig (darf leer sein).
- Shape (Kollision): nur so hoch wie Geometrie, begehbar (Spieler soll drüberlaufen; ggf. Kollision leer, Outline = Geometrie).
- Textur: schwarzes/dunkelgraues Gummikabel, 1 Texel pro Modellpixel (Minecraft-Auflösung), leichte Längsrillen, dezente Kupfer-Markierung erlaubt.
- Abbauen eines Segments = Kabel durchtrennen (ganze Verbindung entfernen, Items droppen).

## Verlegelogik (Code)
- Punkte: P0 = Startbox, P1..Pn = Ankerzellen (geklickte Pos + Fläche, d.h. die Zelle VOR der Fläche), Pn+1 = Zielbox.
- Pfad zwischen zwei Punkten: achsparallele Beine in der Reihenfolge X, dann Z, dann Y. Der erste Schritt aus einer Box sollte horizontal sein; Boxen selbst zählen nicht zur Länge.
- Länge = Anzahl Pfadzellen ohne die beiden Boxzellen (min. 1). Maximal 256. Verbrauch = Länge Items vom gehaltenen Stack (Kreativ: kein Verbrauch). Zu wenig → Meldung „Zu wenig Kabel: N benötigt“.
- Alle Adern müssen zum gehaltenen Kabel passen (Adern stehen im Item); Box A und B müssen frei sein (je Box max. 1 Kabel).
- Segmentblöcke in jede Pfadzelle setzen, deren Zustand `canBeReplaced` ist (Luft, Gras, Wasser→waterlogged). Feste Zellen überspringen. Verbindungs-Properties aus Vorgänger/Nachfolger-Richtung.
- Speichern: Owner = Box A: Partner-Pos, Pfadzellen (komprimiert als Punkte-Liste), Adern, Länge. Box B: Owner-Pos + Adern (für Farbe/Goggles). Segment-BE: Owner-Pos.
- Elektrik: je Ader i eine Verbindung Terminal i (A) ↔ Terminal i (B) mit R = R_block(Ader i) × Länge. Wie Power Grid zwei Terminals verschiedener BlockEntities verbindet: siehe GlobalElectricNetworks.makeSimpleConnection / WorldNetworks.makeSimpleWire, BlockWireEndpoint, WireEntity/BaseWireEntity. Lebenszyklus prüfen (Speichern? Entfernen? Chunk-Unload) und sauber wieder aufbauen nach Laden (beide Chunks geladen) und entfernen beim Trennen.
- Trennen (Segment oder Box abgebaut, Box per Schraubenschlüssel): alle Segmentblöcke des Pfads entfernen (ohne Kaskaden-Chaos), elektrische Verbindungen lösen, Länge × underground_cable mit gleichen Adern droppen (an der Abbaustelle), Partner zurücksetzen, Facing bleibt.
- Client: BlockColor für cable_box (tintindex 0..2 → Aderfarbe, Daten via BE-Sync, Re-Render bei Änderung), ItemColor für bare_cable/underground_cable (tintindex 1..3 → Aderfarbe).
- Aderfarben: powergrid:wire → Kupfer #c87533, powergrid:iron_wire → #c9ccd1, powergrid:golden_wire → #f2c94c, sonst #9a9a9a.
- Goggles (Box): Adern mit Material, Widerstand gesamt je Ader, Länge, Partner verbunden ja/nein; optional Strom je Ader.
- Tooltips (Items): Adern-Liste, Ω/Block je Ader, Max. A.

## Item-Modelle (Asset-Worker)
- bare_cable: layered item: layer0 = gemeinsame Basis (Endkappen/Bindungen), layer1..3 = drei verdrillte Adern-Stränge in Graustufen (werden per ItemColor tintindex 1..3 eingefärbt).
- underground_cable: layer0 = schwarzer Kabelring/Rolle, layer1..3 = drei kleine Aderenden (getintet).
- cable_box: Block-Item (parent Blockmodell).
- Alle Texturen 16×16 Item-Stil bzw. Block 1 Texel/Modellpixel. Kupfer-Gehäuse im Create-Stil (Kupfer-Ramp wie Create copper casing, aber eigene Pixel).

## Sprache (de_de + en_us)
- item.pghv.bare_cable „Blankes Mehrleiterkabel“ / „Bare Multi-Core Cable“
- item.pghv.underground_cable „Erdkabel (isoliert)“ / „Underground Cable (insulated)“
- block.pghv.cable_box „Kabelbox“ / „Cable Box“
- block.pghv.cable_segment „Erdkabel“ / „Underground Cable“
- Meldungen/Tooltips: Keys unter `pghv.cable.*` – der Code-Worker legt die Keys an und trägt sie in beide Lang-Dateien ein (Asset-Worker fügt nur Block/Item-Namen ein; nicht gegenseitig überschreiben: JSON zusammenführen, nicht ersetzen).
