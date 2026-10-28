# Hausinstallation – Spezifikation

Wunsch von Tom (2026-10-28): Hausanschlusskasten mit rotem Hauptschalter außen, Unterputzleitung zum Zähler-/Sicherungskasten
im Haus, Kasten aus mehreren Blöcken (1×2, 2×2 … max. 8×8), Tür per Klick, Module reinklicken; Stromzähler = Power-Grid-
Energiezähler (4 Plätze, Anzeige gesamt/Monat), Sicherung = Power-Grid-Sicherung (1 Platz), dazu FI und Hauptschalter.

## Hausanschlusskasten `house_connection_box` (auf der Wand)
- North-lokal: Kasten x 2..14, y 3..15, z 11..16; unten zwei kleine Power-Grid-Connectoren (L rot, N blau) für die
  Netz-Drähte (Terminals 0/1); verdeckt hinten L/N Haus (2/3) = Unterputz-Port 0.
- Klick: Tür auf. Offen: Klick auf den roten Hebel (x 5..11, y 5..13) schaltet `powered`, Klick daneben schließt.
- Elektrik: Schalter 0 → 2, N 1 → 3 durchgehend.

## Zähler-/Sicherungskasten `distribution_board`
- **Unterputz** (Wunsch Tom): der Block sitzt IN der Wand (Vollblock, statt eines Wandblocks setzen), Tür bündig mit der
  Wandfläche (north-lokal z 0), Innenraum 7 px tief (Rückwand z 7..8, Gehäuse z 8..16), Hutschiene z 6..7, Module z ~1..6.
- Blöcke gleicher Ausrichtung, die sich in der Wandebene berühren, bilden einen Kasten (max. 8 breit, 8 hoch; sonst
  lässt sich der Block nicht setzen). Rahmen nur an den Außenkanten (Blockstate up/down/left/right), Tür für den ganzen
  Kasten (`open`), Griff am rechten Rand. Steuerblock = oben links.
- Pro Block Hutschiene mit 4 Plätzen (Platz s bei x 16−4(s+1) … 16−4s, y 3..13 north-lokal, Platz 0 links).
- Bedienung bei offener Tür: Modul-Item auf freien Platz = einsetzen; leere Hand: Hebel (FI/Hauptschalter) umschalten,
  durchgebrannte Sicherung herausnehmen; Eisendraht (Power-Grid-Tag fuse_resetting) auf Sicherung = neu einsetzen;
  Schraubenschlüssel = Sicherungswert 6/10/13/16/20/25/32/63 A; Schleichen + Schraubenschlüssel = Modul heraus;
  Schleichen + leere Hand = Tür zu. Abbau droppt die Module.
- Module: Power-Grid-Sicherungshalter (`powergrid:fuse_holder`, 1 Platz, Standard 16 A, brennt bei I > Nennwert durch,
  Aussehen = Power-Grid-Modell), Power-Grid-Energiezähler (`powergrid:energy_meter`, alle 4 Plätze eines Blocks, Aussehen
  = Power-Grid-Modell verkleinert, Anzeige kWh), FI-Schutzschalter `pghv:rcd` (30 mA), Hauptschalter `pghv:main_switch`.
- Elektrik je Block (fest, kein Umbau): Terminals 0 L_in, 1 N_in, 2 L, 3 N, 4..7 L_out Platz 0..3. Nachbarblöcke
  verbinden L_in, N_in, L, N. Einspeiseverbindung L_in→L und N_in→N nur im Steuerblock, geschlossen solange alle
  Hauptschalter an und kein FI aus/ausgelöst. Sicherung = Schalter L → L_out.
- Zähler: misst die Einspeiseverbindung (Leistung = |I_L| × U_LN), Gesamt-Wh und Monat (30 Spieltage); Goggles am
  Zählerblock: Leistung kW, gesamt kWh, Monat kWh.
- FI: ||I_L| − |I_N|| > 30 mA an der Einspeiseverbindung ⇒ alle FI des Kastens ausgelöst; Hebel wieder ein = Reset.
- Unterputz-Ports: 0 = Einspeisung (L_in, N_in); 1..4 = Stromkreis Platz 0..3 (L_out, N). Klick mit Draht auf eine
  Sicherung (Tür offen) wählt ihren Stromkreis, sonst Einspeisung; Autoroute: Einspeisung, wenn frei, sonst erster
  freier Sicherungskreis.
