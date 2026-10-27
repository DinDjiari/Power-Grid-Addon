# Messing-Trafolüfter (`brass_cooling_fan`) – Spezifikation

Wunsch von Tom (2026-10-27): Der bestehende Andesit-Lüfter (`cooling_fan`) behält sein Menü für eine **feste Drehzahl**
und den drehzahlabhängigen Verbrauch. Zusätzlich gibt es eine **bessere Version aus Messing**.

## Rezept
Formgebunden 3×3, Mittelspalte:
```
 T      T = create:electron_tube
 F      F = pghv:cooling_fan (Andesit-Lüfter)
 B      B = create:brass_ingot
```
Ergebnis: 1× `pghv:brass_cooling_fan`.

## Verhalten (Unterschiede zum Andesit-Lüfter)
- **Gleich:** Geometrie (2×2, ⅓ Block dick, slot 0/1/2), Multiblock-Logik, Einrasten am Medium-Trafo, Pins (beide unten links),
  240 V / 300 W Nennleistung, Luftstrom aus allen 4 Zellen, Kühlformel, Goggles-Grundaufbau.
- **Intelligente Regelung:** Die Drehzahl wird automatisch aus der Temperatur der gekühlten Trafos geregelt
  (höchste Temperatur aller Trafos hinter dem Lüfter):
  - Menü (Create-Wertepanel) stellt statt Drehzahl die **Zieltemperatur** ein: 40 … 160 °C in 5-°C-Schritten, Standard 80 °C.
  - Regler: s = clamp((T − T_ziel + 5 °C) / 30 °C, 0, 1) ⇒ unter T_ziel − 5 °C steht der Lüfter (spart Strom),
    bei T_ziel + 25 °C volle Drehzahl. Kein Trafo dahinter ⇒ s = 0.
  - Elektrisch wie beim Andesit-Lüfter R(s) = R0/s² (R_OFF bei s < 0.05); der Widerstand wird höchstens alle 20 Ticks
    und nur in 5-%-Schritten nachgeführt (keine Solver-Unruhe).
- **Doppelt so schnell:** Rotor-Drehgeschwindigkeit (Anzeige) ×2, Kühlwirkung ×2 (COOLING_GAIN 2.0),
  Luftstrom-Stärke ×2 (bei Create-Obergrenze kappen).
- **Goggles:** zusätzlich „Automatik: Ziel NN °C, Trafo NN °C, Drehzahl NN %“.

## Umsetzung (Code)
- Bevorzugt dieselben Klassen mit Variante: `HvCoolingFanBlock(Properties, boolean brass)` bzw. Variante aus dem Block ableiten;
  dieselbe BlockEntity-Klasse, entweder eigener BlockEntityType `brass_cooling_fan` oder der vorhandene Typ für beide Blöcke.
  `addBehaviours` läuft im Super-Konstruktor – Variante dort über `getBlockState().getBlock()` bestimmen, Felder ohne Initializer.
- Renderer für beide registrieren; Rotor-Partial bleibt `pghv:block/cooling_fan/rotor` (Aussehen per Assets, siehe unten) –
  oder eigener Partial `pghv:block/brass_cooling_fan/rotor`, falls der Asset-Worker einen liefert.
- Registrierung: Block, Item, Creative-Tab, Loot-Table (nur part=0 droppt), Mineable-Tag, Lang (de/en), Rezept.
- GameTests: Rezept, Regelung (kalter Trafo ⇒ Drehzahl ≈ 0; heißer Trafo ⇒ Drehzahl steigt; Verbrauch niedriger als Andesit
  bei kaltem Trafo), Rotor-Animation ×2, Kühlwirkung ×2 gegenüber Andesit.

## Aussehen (Assets)
- Wie der Andesit-Lüfter (Create-Encased-Fan auf 2×2 vergrößert), aber in **Messing**:
  Seiten/Rahmen über Create-Texturen referenzieren (z. B. `create:block/brass_casing`), Gitter und ggf. Flügel als
  **eigene** Pixel-Art im Create-Messing-Stil (Create-Assets sind All Rights Reserved – nichts kopieren, nur referenzieren
  oder selbst zeichnen). Pins: dieselben kleinen Power-Grid-Keramik-Connectoren unten links.
- Dateien: `blockstates/brass_cooling_fan.json` (48 Varianten wie cooling_fan), `models/block/brass_cooling_fan/part{p}_slot{s}.json`,
  optional `models/block/brass_cooling_fan/rotor.json`, `models/item/brass_cooling_fan.json`, Texturen unter `textures/block/`.
