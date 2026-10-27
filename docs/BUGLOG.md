# Bug-Log

| Datum | Datei | Symptom | Ursache | Fix |
|---|---|---|---|---|
| 2026-10-27 | CableBoxBlockEntity.disconnect | Kabel-Dupe bei kopierter Box (NBT) | Drop ohne Rückverweis-Prüfung | Drop nur bei konsistentem Partner |
| 2026-10-27 | CableSegmentBlock / disconnectAt | Verwaistes Segment trennt fremdes Kabel | Owner-Pos blind vertraut | Segment muss im Pfad liegen |
| 2026-10-27 | CableBoxBlockEntity.tryBuildWires | Ader bleibt nach Fehler dauerhaft getrennt | null/Exception nicht behandelt | try/catch, erneuter Versuch |
| 2026-10-27 | CableBoxBlockEntity.tick | Verbindung zu ersetzter fremder Box | nur Blocktyp geprüft | owner-Rückverweis prüfen |
| 2026-10-27 | CableBoxBlockEntity.releaseWires | Box B zeigt nach Entladen von A noch „verbunden“ | Status nicht zurückgesetzt | Partner linked=false, Ströme 0 |
| 2026-10-27 | Kabel Kreativmodus | Kabel aus dem Nichts | Verlegen gratis, Abbau droppt | Flag creativeLaid, Kreativ-Abbau ohne Drop |
| 2026-10-27 | CableBoxBlockEntity.pushStatus | Unnötige Netzwerkpakete | sendData alle 10 Ticks | nur bei Änderung |
| 2026-10-27 | Kabel-Platzierung | Segment ersetzte Lava | canBeReplaced erlaubt Lava | Nicht-Wasser-Flüssigkeiten überspringen |
| 2026-10-27 | HvCoolingFanBlock.getStateForPlacement | Lüfter hing beim Klick aufs obere/rechte Trafofeld schief | kein Einrasten | snapToTransformer |
| 2026-10-27 | cooling_fan part*_slot2.json | Platte ragte 2 px in den Trafo | Generator-Offset | auf Slot-Grenzen korrigiert |
| 2026-10-27 | build.gradle | Gradle bricht beim Konfigurieren ab | idea-Property downloadSourceJars existiert in Gradle 8.12 nicht | downloadSources |
| 2026-10-27 | build.gradle | localRuntime() unbekannt | Konfiguration localRuntime nicht angelegt (MDK-Konvention) | configurations.localRuntime + runtimeClasspath.extendsFrom |
| 2026-10-27 | build.gradle | Registrate MC1.21-1.3.0+67 nicht gefunden | liegt nur im Snapshot-Repo von devos | Repo mvn.devos.one/snapshots ergänzt |
| 2026-10-27 | HvCoolingFanBlockEntity.SpeedBox | Compile-Fehler: rotate() fehlt | ValueBoxTransform.rotate ist in Create 6 abstrakt | rotate wie Sided (yRot = facing + 180°) |
| 2026-10-27 | HvCoolingFanBlockEntity | Lüfter bläst nur aus 1 von 4 Zellen | nur ein AirCurrent (Hauptzelle) | je Zelle ein AirCurrent (PartAirSource), Test fanBlowsFromAllFourCells |
| 2026-10-27 | CableBoxBlockEntity | Adern brennen bei Überlast durch | Toms Entscheidung: Mechanik entfernen | Durchbrennen entfernt, Test cableOverloadNeverBurns |
| 2026-10-27 | HvCoolingFanBlock | Pins sollen beide unten links sitzen (Toms Skizze) | Pins auf Teil 0 und 1 verteilt | beide Terminals auf Teil 0 (plus x11..14, minus x6..9), Tests angepasst |
| 2026-10-27 | cooling_fan Modelle/Textur | Lüfter wirkte wie Steuerrad, Messing khaki, Gitter unruhig | Blätter aus gedrehten Quadern | Neues Design nach Toms Skizze: grauer Box-Fan, 4 Flügel als gezeichnete Rotorscheibe, kein Gitter |
