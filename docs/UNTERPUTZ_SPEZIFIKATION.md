# Unterputz-Installation – Spezifikation

Wunsch von Tom (2026-10-28): Lichtschalter wie normale EU-Schalter an der Wand, Steckdosen EU und US,
Unterputzdose, Leitungen in der Wand verlegen (Röntgenansicht beim Verlegen, Wegpunkte oder Autorouting per Taste),
damit sich Lichter sauber über Schalter verdrahten lassen.

Antworten von Tom: Wippschalter **und** Taster; Steckdosen mit Spannungsangabe (230 V EU / 120 V US, Warnung);
Verlegen mit Wegpunkten **und** Autorouting per Taste beim Anschauen des Ziels; Röntgenansicht nur beim Verlegen.

## Blöcke (alle an der Wand, HORIZONTAL_FACING = Richtung aus der Wand heraus)
Platzierung: geklickte Seitenfläche ⇒ zeigt aus der Wand; sonst zum Spieler. Modell north-lokal: Front nach Norden,
Wand dahinter (Süden, z = 16). Verdeckte Terminals sitzen hinten (z 15,5..16, nicht anklickbar).

| Block | Terminals | Ports (Unterputz) | Funktion |
|---|---|---|---|
| `wall_box` Unterputzdose | 0 = L, 1 = N, sichtbar vorn (Mini-Connectoren) | 0: (0,1) | Einspeisung mit normalen Power-Grid-Drähten, beliebig viele Leitungen |
| `wall_switch` EU-Wippschalter | 0 = L ein, 1 = N ein, 2 = L aus, 3 = N aus (verdeckt) | 0: (0,1), 1: (2,3) | Rechtsklick schaltet L zwischen 0 und 2 (N 1–3 immer durch), Zustand `powered` |
| `wall_button` EU-Taster | wie Schalter | wie Schalter | Rechtsklick schließt 20 Ticks |
| `outlet_eu` Schuko | 0 = L, 1 = N (verdeckt) | 0: (0,1) | Power-Grid-Stecker passen (ISocketElectric); Goggles: Spannung, Nenn 230 V |
| `outlet_us` US | wie EU | wie EU | Nenn 120 V |

Goggles Steckdose: „Spannung: NNN V (Nenn 230 V)“; Status: keine Spannung (< 5 V), OK (±10 %), Unterspannung, Überspannung.
Goggles Schalter: „Ein/Aus“. Dose/Schalter/Steckdose: Anzahl Leitungen.

## Unterputz-Leitung
- Material: `powergrid:insulated_copper_wire`. 2 Adern (L, N). Widerstand je Ader = Ω/Block der Drahtart × Länge.
- Eine Leitung verbindet Port A eines eigenen Wandgeräts (Besitzer, speichert die Leitung) mit Port B eines eigenen
  Wandgeräts **oder** eines beliebigen Power-Grid-Elektroblocks (z. B. Lampe; Terminals 0/1).
- Port-Wahl automatisch: beim Schalter der Port mit weniger Leitungen (erste = Eingang, zweite = Ausgang).
- Verlegen: Rechtsklick mit dem Draht auf ein Wandgerät ⇒ Start (Verlegemodus, Röntgenansicht);
  Rechtsklick auf andere Blöcke ⇒ Wegpunkt (Blockmitte, max. 32); Rechtsklick auf Ziel ⇒ fertig.
  **Taste R** (änderbar, „Unterputz: Autoroute“) beim Anschauen eines Ziels ⇒ Weg automatisch durch feste Blöcke
  (A*, 6 Nachbarn, max. 4096 Knoten), Wegpunkte davor bleiben erhalten. Schleichen + Rechtsklick in die Luft bricht ab.
- Max. 64 Blöcke. Verbrauch ⌈Länge × 0,5⌉ Drähte (Power-Grid-Wert itemsPerMeter), Kreativ nichts.
- Elektrik wie Kabelbox: einfache Power-Grid-Verbindungen, nicht gespeichert, Neuaufbau nach dem Laden.
- Abbau eines Endes ⇒ Leitung weg, Drähte droppen dort; fremdes Ziel verschwindet ⇒ Besitzer merkt es (alle 20 Ticks).
- Darstellung: unsichtbar; nur solange der Spieler isolierten Kupferdraht hält, werden alle Leitungen in der Nähe
  durch die Wände gezeichnet (Linien ohne Tiefentest), dazu die Vorschau der gerade verlegten Leitung.

## 230-V-Lampen (Entscheidung Tom, 2026-10-28)
Power-Grid-Glühbirnen sind 120 V / 30 W und brennen an 230 V durch. Daher eigene Lampen:
- `ceiling_lamp` Deckenleuchte (hängt unter der Decke, Terminals verdeckt oben) und `wall_light` Wandleuchte (Wandgerät).
- Glühfaden als fester Widerstand R = 230² / 60 W ≈ 882 Ω zwischen L (0) und N (1); Licht 0..15 nach Leistung
  (15 ab Nennleistung, darunter anteilig; 120 V ≈ Stufe 4). Kein Durchbrennen; Goggles: Spannung, Status, Leistung.
- Ziel für Unterputz-Leitungen; Wandleuchte kann auch Start sein (z. B. weiter zur Deckenleuchte).
- Rezepte formlos: Deckenleuchte Quarz + Glasscheibe + Glowstonestaub + Kupferbarren; Wandleuchte ohne Kupferbarren.
