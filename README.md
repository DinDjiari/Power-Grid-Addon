# Power Grid: High Voltage (`pghv`)

Addon fuer **Create: Power Grid 0.6.2** (NeoForge 1.21.1). Fuegt hinzu:

- `hv_voltmeter` – Messing-Voltmeter mit Messbereichen bis 2 MV
- `cooling_fan` – Trafo-Kuehllueffter: flache 2x2-Platte, 1/3 Block dick, vorne/mittig/hinten platzierbar, 240 V / 300 W, Drehzahlmenue, Luftstrom (schiebt Entities weg). Mit dem Luefter auf den Standard-Medium-Trafo klicken, er rastet auf die grosse 2x2-Seite ein (vorne und hinten moeglich) und kuehlt ihn (Original-Textur bleibt)

- `underground_cable` / `bare_cable` / `cable_box` – Mehrleiter-Erdkabel: 3 beliebige Power-Grid-Draehte → blankes Kabel, mit 8 getrocknetem Seetang isoliert → Erdkabel (1 Item = 1 Block). Von Kabelbox zu Kabelbox verlegen wie Schienen (Rechtsklick Box = Start, Rechtsklick Boden = Ankerpunkt, Rechtsklick zweite Box = verbinden). Jede Ader ist ein eigener Leiter zwischen den 3 Anschluessen der Boxen.
Autor: Tomrox GmbH · Lizenz: Apache-2.0 (siehe `LICENSE`, `NOTICE`)

## Voraussetzungen

- JDK 21 (Gradle-Toolchain; `foojay-resolver` kann es bei Bedarf nachladen)
- Gradle 8.12.1 (ueber den Wrapper `./gradlew`)
- Internetzugang zu den Maven-Repos (createmod, tterrag, neoforged, parchmentmc, modmaven, devos, modrinth, cursemaven, architectury)
- Abhaengigkeiten: NeoForge 21.1.231, Create 6.0.10-280, Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67, Architectury API 13.0.8 (werden von Gradle geladen)

## Power-Grid-Jar bereitstellen

1. Create: Power Grid 0.6.2 (NeoForge, MC 1.21.1) herunterladen.
2. Als `libs/powergrid-mc1.21.1-0.6.2.jar` ablegen (Details: `libs/README.md`).
3. Laufzeit-Hinweis: Power Grid braucht **ejml 0.44.0** (ejml-core, -simple, -ddense, -dsparse).
   Normalerweise ist es im Power-Grid-Jar eingebettet (Jar-in-Jar). Fehlt es beim Start
   (`NoClassDefFoundError: org/ejml/...`), die auskommentierten `localRuntime "org.ejml:..."`-Zeilen in `build.gradle` aktivieren.

## Bauen und Starten

```
./gradlew build        # erzeugt build/libs/pghv-<version>.jar
./gradlew runClient    # Testclient mit Create, Power Grid und diesem Addon
./gradlew runServer    # Dedicated Server (optional)
```

Unter Windows: `gradlew.bat build` bzw. `gradlew.bat runClient`.

## Testen

Test-Checkliste: siehe `TESTPLAN.md`.

## Projektstruktur

- `src/main/java/com/tomrox/pghv/` – Mod-Code (Registries, Bloecke, BlockEntities, Client-Renderer)
- `src/main/resources/` – `neoforge.mods.toml`, Assets und Daten
- `libs/` – lokale Abhaengigkeiten (Power-Grid-Jar)
