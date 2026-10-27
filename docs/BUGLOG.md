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
