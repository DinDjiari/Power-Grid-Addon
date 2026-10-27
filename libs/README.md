# libs/

Hier muss das Jar von **Create: Power Grid** liegen (wird nicht mitgeliefert und nicht eingecheckt):

    libs/powergrid-mc1.21.1-0.6.2.jar

Quelle: CurseForge (Projekt-ID 1321420) oder Modrinth (Projekt `eWiBLJ9R`), Version 0.6.2 fuer NeoForge 1.21.1.
Der Dateiname muss exakt `powergrid-mc1.21.1-0.6.2.jar` lauten, sonst `build.gradle` anpassen
(`compileOnly files(...)` und `localRuntime files(...)`).

Alternative ohne lokales Jar: in `build.gradle` die auskommentierten Zeilen
`curse.maven:power-grid-1321420:<fileId>` aktivieren und die CurseForge-Datei-ID eintragen.
