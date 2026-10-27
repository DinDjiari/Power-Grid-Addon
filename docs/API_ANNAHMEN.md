# API-Annahmen

## Status nach erstem Build (2026-10-27)
Gebaut gegen NeoForge 21.1.231, Create 6.0.10-280, Power Grid 0.6.2 (Modrinth-Jar). Alle Annahmen unten sind
damit **geprüft**: Der Code kompiliert, Server und Client starten ohne `pghv`-Fehler, 34 GameTests sind grün
(`./gradlew runGameTestServer`). Abweichungen, die korrigiert werden mussten, stehen in `docs/BUGLOG.md`:
- `ValueBoxTransform.rotate(...)` ist in Create 6 abstrakt (SpeedBox des Lüfters).
- Registrate `MC1.21-1.3.0+67` liegt nur in `https://mvn.devos.one/snapshots`.
- Gradle 8.12: `idea.module.downloadSources` statt `downloadSourceJars`; Konfiguration `localRuntime` muss angelegt werden.
- ejml kommt per Jar-in-Jar mit dem Power-Grid-Jar, kein eigener `localRuntime`-Eintrag nötig.
- Laufzeit bestätigt: `makeSimpleConnection`-Drähte werden nicht gespeichert; die Kabelbox baut sie nach Entladen/Laden neu auf (GameTest `cablereloadrebuildswires`).

## Ursprüngliche Liste (vor dem ersten Build geschrieben)

Der Code wurde ohne Zugriff auf Maven geschrieben. Power-Grid-APIs sind gegen dessen Quellcode
(github.com/patryk3211/PowerGrid, Branch architectury-1.21.1/dev) geprüft. Create-, Flywheel-, NeoForge- und
Vanilla-Signaturen wurden NUR aus der Verwendung in Power Grid abgeleitet. Diese Stellen sind die
wahrscheinlichsten Compile-/Laufzeitfehler:

## Build
- `net.neoforged.moddev` Plugin-Version 2.0.78 (build.gradle) – ggf. auf aktuelle 2.0.x anheben.
- Maven-Koordinaten Create 6.0.10-280, Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67, Architectury 13.0.8.
- Power Grid kommt als lokales Jar `libs/powergrid-mc1.21.1-0.6.2.jar` (siehe libs/README.md); ejml 0.44.0 eventuell als localRuntime nötig.

## Create / Catnip / Flywheel
- `SmartBlockEntity`: `addBehaviours` und Power Grids `buildCircuit` laufen im Super-Konstruktor → Felder ohne Initializer (bewusst so).
- `invalidate()` / `remove()` / `sendData()` / `notifyUpdate()` / `write|read(tag, registries, clientPacket)`.
- `ScrollValueBehaviour` (Feld `value`, `between`, `withFormatter`, `createBoard`, `formatSettings`), `ValueBoxTransform`(`getLocalOffset(LevelAccessor, BlockPos, BlockState)`, `rotateHorizontally`), `ValueSettingsBoard`.
- `AirCurrent` / `IAirCurrentSource` (`rebuild`, `findEntities`, `tick`), `AllConfigs.server().kinetics.fanBlockCheckRate`, `isVirtual()`.
- `IWrenchable.onWrenched` / `onSneakWrenched` / `playRemoveSound`; `IBE`; `IHaveGoggleInformation` im Paket `com.simibubi.create.api.equipment.goggles`.
- `CachedBuffers.partial(...)`, `SuperByteBuffer.rotateCentered/translate/rotate/light/renderInto`, `PartialModel.of(ResourceLocation)` (Init-Zeitpunkt: statischer Block in PghvClient), `AllPartialModels.GAUGE_DIAL`.
- `PlayerUtilities.cancelBreak` (Power Grid).

## NeoForge 21.1 / Vanilla 1.21.1
- `BlockEntity#getRenderBoundingBox()` mit `@Override` (IBlockEntityExtension).
- `IBlockExtension#getPistonPushReaction(BlockState)`.
- `RegisterColorHandlersEvent.Block/Item#register(...)`; ItemColor liefert Farben mit Alpha `0xFF......`.
- DataComponents: `DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID)`, `DataComponentType.builder().persistent(..).networkSynchronized(..)`.
- Rezepte: `CraftingRecipe`/`CustomRecipe`, `CraftingInput.width/height/size/getItem`, `Ingredient.CODEC_NONEMPTY`, `Ingredient.CONTENTS_STREAM_CODEC`, `CraftingBookCategory.CODEC/STREAM_CODEC`, `ByteBufCodecs.list(int)`, `ResourceKey.streamCodec`, `HolderLookup.Provider.lookup(...)`, `Item.TooltipContext.registries()`.
- `BlockState#canBeReplaced()` ohne Parameter, `SoundType.COPPER`, `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE`.
- Modell-Feld `"render_type": "minecraft:cutout"` (Kühllüfter-Gitter).
- Item-IDs in Rezepten: `create:copper_casing`, `create:copper_sheet`, `create:iron_sheet`, `create:brass_sheet`, `create:electron_tube`, `powergrid:electric_fan`, `powergrid:wire_connector`.

## Power Grid (geprüft, aber Laufzeitverhalten offen)
- `GlobalElectricNetworks.makeSimpleConnection` / `WorldNetworks.makeSimpleWire` werden NICHT gespeichert → Erdkabel baut Verbindungen nach dem Laden selbst neu auf; Entfernen über `ElectricWire.remove()`.
- `ThermalBehaviour.applyTickPower` für die Lüfterkühlung; Überhitzungstemperatur 175 °C angenommen (nicht auslesbar).
- `ResistanceValues`/`ThermalValues` werfen bei fremden Mod-IDs → im Addon bewusst nicht verwendet.

## Nachtrag Erdkabel-Fixes
- `Block.playerWillDestroy(Level, BlockPos, BlockState, Player)` gibt in 1.21.1 `BlockState` zurück.
- `com.mojang.logging.LogUtils` verfügbar.
- Erzwungenes synchrones Chunk-Laden beim Trennen (max. 256 Blöcke) – vertretbar, beobachten.
