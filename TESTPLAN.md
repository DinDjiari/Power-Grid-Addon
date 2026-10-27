# Testplan Test-PC (Minecraft 1.21.1, NeoForge 21.1.231)

Stand 2026-10-27: Build, Dedicated Server, Client (bis in eine Testwelt) und 34 automatische GameTests laufen
fehlerfrei (`./gradlew runGameTestServer`, Tests in `src/gametest`). Abschnitt 0 ist damit erledigt; offen ist
vor allem die **optische** Prüfung (Modelle, Texturen, Animation, Goggles-Texte) und das Verhalten mit echten Spielern.
Bitte die Punkte der Reihe nach abhaken. Fehlerausgaben (Gradle oder `logs/latest.log`) an Claude schicken.

## 0. Build
- [ ] Power-Grid-Jar (0.6.2, 1.21.1) nach `libs/powergrid-mc1.21.1-0.6.2.jar` kopieren
- [ ] `./gradlew build` läuft ohne Fehler (erwartete Problemstellen: ModDevGradle-Version, Create/Flywheel-API-Namen, Event-Annotationen)
- [ ] `./gradlew runClient` startet bis ins Hauptmenü, in der Modliste erscheint "Power Grid: High Voltage"
- [ ] Im Log keine Fehler zu `pghv` (fehlende Modelle, Texturen, Registry)

## 1. Creative-Tab und Items
- [ ] Tab "Power Grid: High Voltage" vorhanden, enthält Voltmeter, Kühllüfter, Kabelbox und Kabel
- [ ] Beide Items haben Namen (kein Key-Text), Item-Icons sichtbar (nicht lila-schwarz)

## 2. Hochspannungs-Voltmeter (Messing)
- [ ] Platzieren in allen vier Himmelsrichtungen, Modell ist Messing statt Andesit
- [ ] Anzeigefläche mit Skala sichtbar, Zeiger sichtbar und beweglich
- [ ] Zwei Anschlüsse (rot/blau) lassen sich mit Draht verbinden
- [ ] Bereich per Scroll/Klick umschaltbar: 20 kV, 200 kV, 2 MV
- [ ] Mit Netz von 150 kV: Zeiger steht im Bereich 200 kV nicht am Anschlag
- [ ] Brille (Goggles): Anzeige in kV/MV, kein Absturz
- [ ] Welt speichern/laden: Bereich bleibt erhalten

## 3. Trafo-Kühllüfter (2x2, 1/3 Block dick)
- [ ] Mit dem Lüfter auf eine große Seite des Medium-Trafos klicken (egal welches der vier Felder): Lüfter rastet bündig auf die 2x2-Fläche ein; geht vorne und hinten
- [ ] Freies Platzieren ohne Trafo: vier Teile (2 breit x 2 hoch) erscheinen; bei blockiertem Platz wird nicht platziert
- [ ] Standardposition hinten (Slot 2); Sneak + leere Hand (oder Schraubenschlüssel) schaltet hinten -> vorne -> mittig durch, alle vier Teile wechseln gemeinsam
- [ ] Alle vier Himmelsrichtungen: Modell passt, Teile schließen bündig, Platte ragt in keiner Position aus der Zelle
- [ ] Abbauen eines Teils entfernt die anderen drei, genau ein Item droppt (Kreativmodus: keins)
- [ ] Zwei Pins unten (rot +, blau -) mit Draht verbindbar; 240 V Netz anschließen
- [ ] Rotor dreht sich mit Spannung, steht ohne Spannung; Drehzahl ändert sich ohne Ruckeln
- [ ] Wertebox/Menü wie Creates Rotation Speed Controller, aber in %: 0..100 % in 1-%-Schritten (Board mit 10er-Marken), Standard 100 %, Wertefeld oben mittig auf dem Lüfter (beide oberen Hälften anklickbar); weniger Prozent = weniger Leistung (R = 192 Ω / s²)
- [ ] Goggles: Drehzahl, Spannung/Leistung, "Kühlt: Trafo, NN °C" bzw. "Kein Trafo"
- [ ] Am Standard-Medium-Trafo (original Textur) vor/hinter die große Fläche setzen: Temperatur sinkt bei Last schneller als ohne Lüfter; zwei Lüfter (vorne+hinten) kühlen additiv
- [ ] Luftstrom: Spieler/Mobs/Items vor dem Lüfter werden in Blickrichtung weggeblasen, Stärke folgt der Drehzahl; ohne Strom kein Wind
- [ ] Welt speichern/laden: Drehzahl und Position (Slot) bleiben erhalten

## 3b. Erdkabel (Mehrleiter)
- [ ] Rezept: 3 beliebige Drähte (z.B. 2× Kupfer + 1× Eisen) formlos → 2× blankes Kabel; Tooltip zeigt die 3 Adern, Ω/Block und max. A; Item-Litzen in Aderfarben
- [ ] Blankes Kabel lässt sich nicht platzieren
- [ ] Blankes Kabel mittig + 8× getrockneter Seetang ringsum → Erdkabel mit gleichen Adern
- [ ] Rezept Kabelbox (3× Wire Connector, Kupferblech, Kupfergehäuse, Eisenblech) funktioniert, Rezeptbuch zeigt alle drei Rezepte
- [ ] Kabelbox platzieren: Buchse zeigt vom Spieler weg (Schleichen: zum Spieler)
- [ ] Rechtsklick mit Erdkabel auf Box A → Meldung „Kabel begonnen“; Rechtsklick auf Boden setzt Ankerpunkte (auch höher/tiefer); Rechtsklick auf Box B verbindet
- [ ] Segmente liegen sichtbar am Boden, Ecken und Steigungen korrekt verbunden; in festen Blöcken unsichtbar; Boxen drehen die Buchse zum Kabel
- [ ] Verbrauch = Länge in Blöcken (aus Hand + Inventar), Kreativ ohne Verbrauch; zu wenig Kabel → Meldung
- [ ] Anschlusskappen der Boxen in Aderfarben (Kupfer/Eisen/Gold)
- [ ] Je Ader leitet Terminal i von Box A zu Terminal i von Box B (mit Multimeter/Voltmeter prüfen); Widerstand steigt mit Länge, Eisenader höher
- [ ] Goggles an der Box: Adern, Widerstand, Länge, verbunden
- [ ] Welt speichern/laden und weit weggehen (Chunks entladen) → Verbindung danach wieder aktiv
- [ ] Segment abbauen → ganzes Kabel weg, Länge × Erdkabel droppt einmal (kein Dupe, auch bei Explosion)
- [ ] Überlast: Adern brennen NICHT durch (entfernt auf Toms Wunsch); Goggles zeigen Strom rot über Max.

## 3c. Überhitzungsschutz (Messing-Lüfter + Servo + Datenkabel)
Spezifikation: `docs/UEBERHITZUNGSSCHUTZ_SPEZIFIKATION.md`. Automatisch: `OverheatProtectionTests` (Hysterese, Spannungen, Schraubenschlüssel, Datenkabel, Ende-zu-Ende Servo + HV-Schalter).
- [ ] Rezept: Kupferdraht + Eisendraht + Golddraht formlos → 1 Datenkabel; Item im Creative-Tab, Icon sichtbar
- [ ] Messing-Lüfter Teil 1 (von vorn unten rechts): Steckdose mit 3 Connectoren in allen 3 Slots und 4 Richtungen; Highlight/Name beim Anvisieren: „Servo +48 V“ (rot), „Servo −“ (blau), „Steuersignal“ (grün); Andesit-Lüfter ohne Steckdose
- [ ] Datenkabel: Rechtsklick auf Messing-Lüfter → Meldung „Steckdose gewählt“, Item glänzt; Rechtsklick auf Servo → 3 Drähte (Kupfer/Eisen/Gold) hängen zwischen den richtigen Connectoren; 1 Datenkabel weg (Kreativ: nicht)
- [ ] Über 16 Blöcke → Meldung „zu lang“, nichts verbraucht; Schleichen + Rechtsklick in die Luft bricht ab; Andesit-Lüfter → „nur Messing-Lüfter hat eine Steckdose“
- [ ] Drähte mit der Drahtschere trennen: je 1 Kupfer-/Eisen-/Golddraht droppt (kein Dupe)
- [ ] Slot des Lüfters wechseln: Drahtenden wandern mit
- [ ] Servo an Power Grids HV-Schalter (Welle) koppeln; Trafo über 155 °C heizen → Goggles „Überhitzungsschutz: AUSGELÖST“, Klick + Rauch, Servo dreht 180°, Schalter öffnet; unter 100 °C → „bereit“, Servo dreht zurück, Schalter schließt
- [ ] Öffnet der Schalter in die falsche Richtung: Schraubenschlüssel-Rechtsklick auf die Steckdose → Meldung „Servo-Richtung: links/rechts“, Goggles zeigen die Richtung; Schraubenschlüssel woanders auf den Lüfter wechselt weiter den Slot
- [ ] Goggles (Messing): „Aus bei 155 °C, wieder an bei 100 °C“
- [ ] Welt speichern/laden im ausgelösten Zustand: bleibt ausgelöst, Richtung bleibt
- [ ] Servo-Tempo: bei 48 V erste Bewegung ca. 7 U/min (180° ≈ 4 s), danach bis 32 U/min; prüfen, dass der Schalter rechtzeitig vor 175 °C öffnet

## 3d. Ponder-Szenen (Taste W über dem Item im Inventar)
- [ ] Spiel auf **Englisch**: über Trafo-Kühllüfter, Messing-Trafolüfter, Datenkabel, Kabelbox, Erdkabel und blankem Kabel erscheint „Hold [W] to Ponder“; Szenen öffnen ohne Absturz, keine Roh-Keys (`pghv.ponder...`) sichtbar
- [ ] Spiel auf **Deutsch**: dieselben Szenen zeigen deutsche Titel und Texte (Umlaute, °C, „100 %“ korrekt, kein „Format error“)
- [ ] Ponder-Index: Kategorie „Power Grid: High Voltage“ mit Lüfter-Icon, enthält alle sechs Items
- [ ] Szene „Transformatoren kühlen“: Trafo + Lüfter rasten sichtbar ein, Slot-Wechsel vorne/mittig/hinten, Pins rot/blau umrandet und Drähte hängen an den Pins, Rotor dreht (100 % → 50 % → 100 %), Wertefeld-Markierung oben mittig, Gegenstände werden nach vorne weggeblasen
- [ ] Szene „Automatische Trafokühlung“: Rotor steht bei „kalt“, dreht hoch (30/65/100 %), doppelt so schnell, stoppt wieder
- [ ] Szene „Überhitzungsschutz“: Steckdosen-Markierungen rot/blau/grün auf der Steckdose, 3 Drähte Kupfer/Eisen/Gold Steckdose → Servo, Servo dreht, HV-Schalter öffnet bei „155 °C“ und schließt bei „100 °C“ (Richtung stimmt?), Rauch an der Steckdose
- [ ] Szene „Erdkabel verlegen“: Boxen drehen die Buchse zum Kabel, Segmente erscheinen der Reihe nach, (5,1,5) unterirdisch markiert, Kappen werden Kupfer/Eisen/Gold, die drei Farblinien enden auf den richtigen Anschlüssen
- [ ] Log: keine Fehler zu `pghv:ponder/*.nbt` oder fehlenden Lang-Keys

## 4. Texturen und Optik
- [ ] Lüfter: Creates Lüftertextur auf 2×2 vergrößert (Create-Texturen laden, kein lila-schwarz), 2 kleine Keramik-Connectoren unten links (Polarität per Highlight), durchsichtig; Standard-Trafo behält seine Originaltextur
- [ ] Lüfter bläst aus allen 4 Zellen (Entity vor jeder Zelle wird weggeschoben)
- [ ] Gauge-Skala lesbar
- [ ] Lichtwert des Rotors passt zur Umgebung
