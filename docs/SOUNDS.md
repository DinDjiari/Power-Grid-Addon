# Sounds

Wunsch von Tom (2026-10-28): echte Geräusche statt Minecraft-Platzhaltern.
Erzeugt mit `python3 tools/sounds/make_sounds.py` (numpy, scipy, ffmpeg/libvorbis) nach `assets/pghv/sounds/`.

| Sound-Event `pghv:` | Wo | Quelle |
|---|---|---|
| `block.wall_switch.on/off` (je 2 Varianten) | EU-Wippschalter | echte Schalteraufnahme (Kenney CC0), tiefer, Resonanz Unterputzdose |
| `block.wall_button.press/release` | EU-Taster (Loslassen nach 20 Ticks) | echte Aufnahme |
| `block.breaker.on/off` (je 2) | Hebel von Hauptschalter/FI im Zählerkasten | echte Aufnahme, Schnappmechanik + Metallkasten |
| `block.breaker.trip` | FI löst aus | zwei Aufnahmen übereinander, tiefer, lauter |
| `block.distribution_board.module_insert` | Modul auf Hutschiene einrasten | echte Aufnahme |
| `block.distribution_board.fuse_insert` | Sicherung mit Eisendraht wieder einsetzen | echte Aufnahme |
| `block.distribution_board.fuse_blow` (2) | Sicherung brennt durch | modelliert: Lichtbogen-Knall, Druckstoß, Funken |
| `block.house_connection_box.on/off` | roter Hauptschalter Hausanschlusskasten | echte Aufnahme, stark tiefer + Wumms |
| `block.cooling_fan.run` | Laufgeräusch Kühllüfter (Schleife 2 s) | modelliert: Luftrauschen, Blattfrequenz 100 Hz, Motor |
| `block.cooling_fan.trip` | Überhitzungsschutz löst aus | wie FI |

Lüfter: `client/CoolingFanSound` (nur Client) folgt der Rotordrehzahl rel (1 = Andesit 100 %, Messing bis 2):
Tonhöhe 0,55 + 0,45·rel, Lautstärke 0,2 + 0,5·rel, unter rel 0,25 ein-/ausgeblendet; Mitte des 2×2-Lüfters.
Türen von Kasten und Hausanschlusskasten bleiben Eisenfalltür (Minecraft).
Untertitel: `pghv.subtitle.*` (DE/EN).
