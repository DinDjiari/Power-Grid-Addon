# Überspannung – Spezifikation

Discord-Wunsch (EpicGamer13082, weitergeleitet von Tom 2026-11-17): „make it that when power go too high explode“.

## Verhalten
- Liegt an einem pghv-Gerät dauerhaft eine Spannung über seiner Grenze an, raucht und knistert es (Rauch, Funken,
  Zischen) und explodiert nach `delayTicks` (Standard 40 Ticks = 2 s) am Stück. Sinkt die Spannung vorher, beginnt der
  Countdown von vorn.
- Das Gerät wird ohne Drop zerstört („durchgebrannt“). Unterputz-Leitungen geben ihren Draht wie beim Abbau zurück,
  Module im Zählerkasten fallen heraus.
- Die Explosion verletzt Spieler und Mobs; Blöcke werden nur zerstört, wenn `breakBlocks = true`.
- Goggles zeigen „Explodiert über … V“.

## Grenzen
| Gerät | Grenze | Gemessen | Explosionsstärke |
|---|---|---|---|
| Kühllüfter Andesit/Messing | 480 V (2 × 240 V) | Pins 0/1 | 2,0 |
| Steckdose EU / US | 460 V / 240 V (2 × Nennspannung) | L–N | 1,0 |
| Unterputzdose, Schalter, Taster | 500 V | L–N jedes Ports | 1,5 |
| Hausanschlusskasten | 1000 V | Netz- und Hausseite | 2,0 |
| Zähler-/Sicherungskasten | 500 V | nur Steuerblock (Einspeisung) | 2,0 |

US-Steckdose an 230 V bleibt also bei der Warnung „Überspannung“ und explodiert erst über 240 V.

## Config (`config/pghv-server.toml`, Abschnitt `overvoltage`)
- `explosions` (true): Explosionen an/aus.
- `breakBlocks` (false): Explosion zerstört auch umliegende Blöcke.
- `delayTicks` (40): Ticks über der Grenze bis zur Explosion.
