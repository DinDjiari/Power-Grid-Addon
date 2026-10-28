# Überhitzungsschutz mit Servo (Messing-Lüfter) – Spezifikation

Wunsch von Tom (2026-10-27): Wenn der Trafo trotz Kühlung zu heiß wird, schaltet der **Messing-Lüfter** über einen
Power-Grid-**Servo** einen drehbetätigten Schalter (z. B. Power Grids HV-Schalter) auf, bevor der Trafo explodiert, und
schaltet nach dem Abkühlen wieder ein. Verbindung Lüfter → Servo über ein **Datenkabel** aus Kupfer-, Eisen- und Golddraht.
Nur der Messing-Lüfter (`brass_cooling_fan`); der Andesit-Lüfter bleibt unverändert.

## Power-Grid-Grundlagen (aus /root/pg121 geprüft)
- Servo `powergrid:servo` (ServoBlockEntity): 3 Terminals – 0 = POSITIVE (Spule +), 1 = NEGATIVE (Spule −),
  2 = CONTROL. Spule 12,8 Ω (dreht) / 64 Ω (Ruhe). Steuerspannung = U(2) − U(1), −5 … +5 V ↦ −360° … +360°.
- HV-Schalter (HvSwitchBlockEntity) öffnet/schließt je nach Drehrichtung der Welle; Stange folgt mit |RPM|/21/20 pro Tick.
- Spannungsquelle im Schaltkreis: `ProvidedVoltageSourceCoupling(positive, negative, resistance[, voltage])`,
  `setVoltage`/`setVoltageProvider`, per `builder.add(...)` (Muster: CreativeSourceBlockEntity).
- Sichtbare Drähte: `HangingWireEntity.create(level, BlockWireEndpoint a, BlockWireEndpoint b, ItemStack wireItem, Float resistance)`
  + `level.addFreshEntity` (Muster: ElectricInstructions in der Ponder, WireItem).

## Elektrik am Lüfter
- Messing-Lüfter: 5 Terminals (0/1 = 240 V-Eingang auf Teil 0, 2 = Servo +48 V, 3 = Servo −, 4 = Steuersignal in der
  runden Buchse auf Teil 1). Andesit: 2.
- 48-V-Quelle zwischen 2 (+) und 3 (−), Innenwiderstand 0,5 Ω; Steuerquelle zwischen 4 und 3: 0 V bereit, ±2,5 V
  ausgelöst (Servo 180°), Innenwiderstand 10 Ω; galvanisch getrennt vom 240-V-Eingang.
- Datenkabel-Rezept formlos: `powergrid:wire` + `powergrid:iron_wire` + `powergrid:golden_wire`.

## Schutzlogik
- T = höchste Temperatur der gekühlten Trafos (wie Regelung). Kein Trafo ⇒ nicht auslösen.
- **Auslösen** bei T ≥ 155 °C ⇒ `tripped = true`, Steuerspannung = Richtung × 2,5 V.
- **Zurücksetzen** bei T ≤ 100 °C ⇒ `tripped = false`, Steuerspannung 0 V (Servo dreht zurück ⇒ Schalter schließt).
- **Richtung** (+1 / −1, Standard +1): Rechtsklick mit Schraubenschlüssel **auf die Buchse** (Treffer in deren Bereich)
  wechselt sie; sonstige Schraubenschlüssel-Klicks behalten ihr Verhalten (Slot wechseln, Sneak = abbauen).
- `tripped` und Richtung werden gespeichert und synchronisiert.
- Goggles (Messing): „Überhitzungsschutz: bereit / AUSGELÖST“, „Servo-Richtung: rechts / links“,
  „Aus bei 155 °C, wieder an bei 100 °C“.
- Optional Rauch/Klick-Sound beim Auslösen.

## Buchse, Datenkabel, Datenstecker-Box (Skizze Tom, 2026-10-27)
- **Lüfter (Messing, Teil 1):** runde Datenbuchse x 5..11, y 0.5..6.5 (Pixel, north-lokal), Front bei z0 − p,
  p = max(0,5; min(2; z0)). Kontakte innen gelb/blau/rot. Terminals 2–4 bleiben elektrisch (Klickboxen in der Buchse).
- **Datenstecker-Box `pghv:data_socket_box`:** HORIZONTAL_FACING = Richtung der Buchse (an Wand: aus der Wand heraus,
  sonst zum Spieler). Körper x 4..12, y 1..13, z 10..16; runde Buchse vorn (Mitte x 8, y 7, Front z 9);
  oben drei kleine Power-Grid-Connectoren: Terminal 0 = Servo +, 1 = Servo −, 2 = Steuersignal.
  Rezept formlos: create:brass_sheet + powergrid:wire_connector + create:andesite_alloy.
- **Datenkabel:** ein Kabel mit rundem Stecker an beiden Enden. Rechtsklick Lüfter (Start) → optional Rechtsklicks auf
  Blöcke (Verlegepunkte auf der Oberfläche, Kabel liegt dann gerade von Punkt zu Punkt) → Rechtsklick Box (einstecken).
  Ohne Punkte hängt es durch. Max. 16 Blöcke, max. 16 Punkte, 1 Datenkabel. Eine Lüfter-Buchse nur einmal belegbar.
- **Elektrik:** Box baut je Ader eine einfache Power-Grid-Verbindung Lüfter-Terminal 2+i ↔ Box-Terminal i
  (Widerstand = Ω/Block der Power-Grid-Drahtart × Länge), wie die Kabelbox: nicht gespeichert, bei Laden neu aufgebaut.
- **Ausstecken:** Schraubenschlüssel auf die Box (Kabel droppt), Box abbauen (droppt), Lüfter abbauen (Stecker fällt
  raus, Kabel droppt an der Box). Kreativ verlegt: kein Drop.
- **Servo:** mit normalen Power-Grid-Drähten an die Connectoren oben auf der Box.
- **Darstellung:** DataSocketBoxRenderer (Stecker 4×4×3 px, Kabel 2 px) mit Texturen pghv:block/data_cable,
  pghv:block/data_plug; Buchse pghv:block/data_socket, Gehäuse pghv:block/data_socket_box.

## Tests (GameTests)
- Auslösen 155 °C / Zurücksetzen 100 °C, Steuerspannung 0 / ±2,5 V, Richtung per Schraubenschlüssel.
- Datenkabel: Einstecken in die Box, Wegpunkte, Grenzen, Abbau; Ende-zu-Ende Servo + HV-Schalter.
