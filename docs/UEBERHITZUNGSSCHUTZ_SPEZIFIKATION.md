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

## Steckdose am Messing-Lüfter
- Sitzt auf **Teil 1** (von vorn gesehen unten **rechts**), 3 kleine Keramik-Connectoren im Power-Grid-Stil
  auf einer dunklen Steckdosenplatte. Klickboxen (Teil-1-lokal, facing=north, wie die Strom-Pins in der Tiefe
  z (z0−p)..(z0+2), p = min(3, z0)):
  - Terminal 2 = POSITIVE „Servo +24 V“: x 2..5, y 1..4
  - Terminal 3 = NEGATIVE „Servo −“:      x 6.5..9.5, y 1..4
  - Terminal 4 = CONTROL „Steuersignal“:  x 11..14, y 1..4 (Farbe grün wie beim Servo)
- Messing-Lüfter hat damit 5 Terminals (0/1 = Strom wie bisher auf Teil 0, 2–4 = Steckdose auf Teil 1). Andesit: 2.
- Schaltkreis (nur Messing, in buildCircuit – läuft im Super-Konstruktor, Variante über getBlockState()):
  - 24-V-Quelle zwischen Terminal 2 (+) und 3 (−), Innenwiderstand 0,5 Ω.
  - Steuerquelle zwischen Terminal 4 und 3, Spannung 0 V (bereit) bzw. ±2,5 V (ausgelöst ⇒ Servo 180°), Innenwiderstand 10 Ω.
  - Galvanisch getrennt vom 240-V-Eingang.

## Schutzlogik
- T = höchste Temperatur der gekühlten Trafos (wie Regelung). Kein Trafo ⇒ nicht auslösen.
- **Auslösen** bei T ≥ 155 °C ⇒ `tripped = true`, Steuerspannung = Richtung × 2,5 V.
- **Zurücksetzen** bei T ≤ 100 °C ⇒ `tripped = false`, Steuerspannung 0 V (Servo dreht zurück ⇒ Schalter schließt).
- **Richtung** (+1 / −1, Standard +1): Rechtsklick mit Schraubenschlüssel **auf die Steckdose** (Treffer in deren Bereich)
  wechselt sie; sonstige Schraubenschlüssel-Klicks behalten ihr Verhalten (Slot wechseln, Sneak = abbauen).
- `tripped` und Richtung werden gespeichert und synchronisiert.
- Goggles (Messing): „Überhitzungsschutz: bereit / AUSGELÖST“, „Servo-Richtung: rechts / links“,
  „Aus bei 155 °C, wieder an bei 100 °C“.
- Optional Rauch/Klick-Sound beim Auslösen.

## Datenkabel `pghv:data_cable`
- Rezept formlos: `powergrid:wire` + `powergrid:iron_wire` + `powergrid:golden_wire` ⇒ 1 Datenkabel.
- Benutzung: Rechtsklick auf die Steckdose eines Messing-Lüfters (Teil 1 oder generell Messing-Lüfter) ⇒ Start gespeichert
  (Item-Komponente, Meldung in der Aktionsleiste); Rechtsklick auf einen `powergrid:servo` ⇒ drei sichtbare Hängedrähte:
  - Steckdose + (T2) ↔ Servo T0 mit Kupferdraht (`powergrid:wire`)
  - Steckdose − (T3) ↔ Servo T1 mit Eisendraht (`powergrid:iron_wire`)
  - Steuer (T4) ↔ Servo T2 mit Golddraht (`powergrid:golden_wire`)
  Widerstand wie Power Grid für die jeweilige Drahtart und Länge; max. 16 Blöcke; verbraucht 1 Datenkabel (Kreativ: nicht).
  Sneak-Rechtsklick in die Luft bricht ab. Die Drähte sind normale Power-Grid-Drähte (Drahtschere entfernt sie).

## Assets
- Messing-Lüfter Teil 1 (alle 3 Slots): Steckdosenplatte + 3 Mini-Keramik-Connectoren (Power-Grid-Stil, Textur
  `powergrid:block/wire_connector` referenzierbar), passend zu den Klickboxen oben.
- Item `data_cable`: 16×16, drei verdrillte Adern Kupfer/Eisen/Gold mit kleinem Stecker, Minecraft-Auflösung.
- Lang de/en: Datenkabel, Meldungen, Goggles-Zeilen, Terminal-Namen.

## Tests (GameTests)
- Auslösen bei 155 °C und Zurücksetzen bei 100 °C (Hysterese), Steuerspannung 0 / ±2,5 V, Richtung per Schraubenschlüssel.
- Datenkabel: drei Drähte zwischen den richtigen Terminals, Verbrauch.
- Ende-zu-Ende (wenn machbar): Servo dreht, HV-Schalter öffnet bei Auslösen und schließt nach Abkühlung.
