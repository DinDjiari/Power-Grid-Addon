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

## Nachtrag Wertefeld oben (Kühllüfter)
- `ScrollValueBehaviour.setValue(int)` überschreibbar (nicht final); öffentliches Feld `value`.
- `ValueBoxTransform.testHit(LevelAccessor, BlockPos, BlockState, Vec3)` überschreibbar (wie Power Grids CustomValueBoxTransformer).
- Wertefeld auf der Oberseite: Drehung `rotateYDegrees(facing+180).rotateXDegrees(90)` wie `ValueBoxTransform.Sided` für UP – Lage/Leserichtung im Spiel prüfen.
- Zweiter BlockEntityType `pghv:cooling_fan_top` für denselben Block (Teile 2/3), eigener Ticker im Block (`getTicker` überschrieben).

## Nachtrag Messing-Trafolüfter (`brass_cooling_fan`)
- Variante wird in `addBehaviours` (Super-Konstruktor von `SmartBlockEntity`) über `getBlockState().getBlock()` bestimmt – setzt voraus, dass `BlockEntity` den Zustand vor `addBehaviours` setzt (Vanilla: ja). `isBrass()` liest den Block jedes Mal neu, kein gespeichertes Feld.
- Eigene BlockEntityTypes `pghv:brass_cooling_fan` und `pghv:brass_cooling_fan_top` mit denselben Klassen (`HvCoolingFanBlockEntity`, `CoolingFanTopBlockEntity`); `IBE.getBlockEntityType()` liefert je nach Block-Variante den passenden Typ.
- `AllConfigs.server().kinetics.maxRotationSpeed.get()` (ConfigInt, Standard 256; geprüft gegen Create-Quelle 6.0.11) als Obergrenze für die Luftstrom-Stärke; Zugriff auf dem Client in try/catch (Fallback 256), falls die Server-Config dort noch nicht geladen ist. Da der Andesit-Lüfter bei 100 % schon 256 liefert, wirkt „Luftstrom ×2“ nur unterhalb 50 % Drehzahl.
- `ValueSettingsBoard(title, maxValue, milestoneInterval, rows, formatter)` (Record, geprüft) für das °C-Board (max 24, Marken alle 4 = 20 °C).
- Übersetzungen mit `%1$s … %%` (Vanilla-`TranslatableContents` setzt `%%` als Prozentzeichen um).
- Partial `pghv:block/brass_cooling_fan/rotor` setzt `models/block/brass_cooling_fan/rotor.json` voraus (Asset-Worker); fehlt die Datei, zeigt der Rotor das Missing-Model.
- `ThermalBehaviour.getTemperature()` liefert auch auf dem Client den synchronisierten Wert (für die Goggles-Zeile „Automatik …“, wie bisher beim Andesit-Lüfter angenommen).
- Rotor-Animation ×2 = bis 120 °/Tick: bei 4-zähligem Rotor kann der Stroboskop-Effekt die Drehung rückwärts/langsam wirken lassen – optisch prüfen.

## Nachtrag Überhitzungsschutz (Messing-Lüfter, Datenkabel)
- `IElectric.terminalCount()` ist pro Block-Instanz (nicht pro Zustand): Messing (eigene Instanz) liefert 5, Andesit 2. `terminal(state, i)` gibt für Teile ohne Terminal `null` zurück (`IElectric.terminalIndexAt` überspringt `null`, wie bisher bei Teil 1–3).
- Drahtenden an der Steckdose sind `BlockWireEndpoint(Teil-1-Position, 2..4)`; Behaviour über `HvCoolingFanBlock.getBehaviour` → Hauptteil (Muster: Power Grids `HvSwitchBlock` Teil 1). Liegen Hauptteil und Teil 1 in verschiedenen Chunks und ist nur Teil 1 geladen, findet `BlockWireEndpoint.getNode` keinen Knoten (die globale Knotenliste ist mit der Hauptteil-Position verschlüsselt) – gleiches Verhalten wie beim HV-Schalter, beobachten.
- `ProvidedVoltageSourceCoupling(positive, negative, float resistance, float voltage)` + `setVoltageProvider(Supplier<? extends Number>)` (preSolve-Hook, wie CreativeSourceBlockEntity) – Provider überlebt `rebuildCircuit`. Annahme: Ein Netz-Inselchen aus nur Spannungsquellen (Steckdose ohne Kabel) ist für den Solver unkritisch (wie eine einzelne Creative-Spannungsquelle).
- Servo-Steuerspannung = U(Servo T2) − U(Servo T1); 2,5 V an 10 Ω gegen 1000 Ω Steuereingang ⇒ ≈ 2,475 V ⇒ Ziel 178° statt 180° (Servo rundet auf ganze Grad). Für den HV-Schalter unerheblich.
- Servo-Drehzahl kommt aus der Spulenleistung: 24 V an 64 Ω (Ruhe) ≈ 9 W ⇒ ≈ 1 U/min bei der ersten Bewegung (`generatedSpeed` wird auf int gerundet; < 1 ergäbe 0 und eine endlose Bewegung im Servo-Code). Danach bleibt die Spule auf 12,8 Ω ⇒ ≈ 7 U/min.
- `HangingWireEntity.create(level, a, b, ItemStack, Float resistance)`: Widerstands-Override wird von Power Grid NICHT gespeichert. Datenkabel-Drähte halten je 1 Draht-Item (kein Dupe beim Trennen); nach dem Neuladen gilt daher `resistancePerItem × 1` statt `× Länge·itemsPerMeter` (Kupfer 0,0015 Ω statt bis 0,012 Ω bei 16 m) – für den 24-V-Servokreis bedeutungslos.
- Länge = gerade Distanz der Terminal-Mittelpunkte (`getExactPosition`), Power Grid nutzt `SableUtils.projectedDistance` (ohne Sable gleich). Power Grids eigenes Limit für Golddraht ist 12 m; das Datenkabel erlaubt laut Spezifikation 16 Blöcke (Power Grid prüft die Länge nur beim Verlegen).
- `GlobalPos.CODEC` / `GlobalPos.STREAM_CODEC` / `GlobalPos.of` (1.21.1) für die Datenkomponente `pghv:data_cable_start`.
- `UseOnContext.getClickLocation()` für die Trefferprüfung des Schraubenschlüssels; Create-`WrenchItem.useOn` ruft `onWrenched` mit dem Original-Kontext.
- `ServerLevel.tryAddFreshEntityWithPassengers`, `ServerLevel.sendParticles`, `SoundEvents.LEVER_CLICK` (SoundEvent, kein Holder).
- GameTest Ende-zu-Ende: `HvSwitchBlockEntity` startet mit Stange 0 = offen (`isClosed()` = Stange > 0,9); Test ist deshalb richtungsneutral. `HorizontalKineticBlock.HORIZONTAL_FACING`, `HvSwitchBlock.PART`, `ServoBlock.FACING`, `ModdedBlocks.SERVO/HV_SWITCH` aus den Quellen.

## Nachtrag Messing-Tint
- Messing-Lüfter: Create-Texturen fan_casing/fan_blades per `tintindex` 0 + BlockColor/ItemColor (PghvColors.BRASS_TINT) eingefärbt;
  Rotor im BER per `SuperByteBuffer.color(r, g, b, a)` (wie Power Grids LightFixtureRenderer). Farbton im Spiel prüfen.

## Nachtrag Ponder-Szenen (Ponder 1.0.82, ungeprüft ohne Build)
- Plugin `PghvPonderPlugin implements PonderPlugin` (getModId/registerScenes/registerTags), angemeldet mit `PonderIndex.addPlugin(...)` in `PghvClient.onClientSetup` per `event.enqueueWork` (Dist.CLIENT). Ponder ruft `PonderIndex.registerAll()` erst bei `FMLLoadCompleteEvent` (PonderClient.modLoadCompleted) – Client-Setup liegt davor. Power Grid meldet sich im Konstruktor an.
- Registrierung direkt mit `ResourceLocation` (Item-IDs über `DeferredItem.getId()`): `helper.addStoryBoard(id, "pfad", board, tags...)`, `helper.forComponents(ids...).addStoryBoard(...)`; Tag `registerTag(rl).title().description().item(ItemLike, true, false).addToIndex().register()` + `addToTag(rl).add(id)`.
- Strukturen `assets/pghv/ponder/<pfad>.nbt` (Namespace = Plugin-Mod-ID). Erzeugt mit `gen_ponder_nbt.py` (Scratchpad), Format wie Power Grid, aber `DataVersion 3955` (1.21.1) statt 3465 der PG-Dateien. BlockEntities ohne NBT legt Ponders `SchematicLevel.getBlockEntity` bei Bedarf an.
- Lang: Ponder liest im Spiel `I18n.get("pghv.ponder.<szene>.header" / ".text_N")`, N = Reihenfolge der `text(...)`-Aufrufe ab 1 (geprüft in Ponder-Quelle `PonderLocalization`/`PonderScene.registerText`); die englischen Code-Texte sind nur Defaults. `I18n.get` formatiert immer → `%` im Lang als `%%`. Tag-Keys `pghv.ponder.tag.high_voltage[.description]`.
- Power-Grid-Ponderklassen (aus dem PG-Jar) werden mitbenutzt: `PowerGridSceneBuilder` (extends Create `CreateSceneBuilder`, `electric().connect(...)`), `WireElement`, `CreateWireInstruction`, `HangingWireEntity.create(level, BlockWireEndpoint, BlockWireEndpoint, ItemStack, Float)` + `updateCurveParams()` für Kupfer/Eisen/Gold-Drähte des Datenkabels; `ModdedItems.WIRE/IRON_WIRE/GOLDEN_WIRE.asStack()`.
- Im Ponder-Level ist `level.isClientSide` true: Lüfter-BE läuft nur clientseitig, der Rotor folgt `FanSteps` (per `modifyBlockEntityNBT` gesetzt, `read(..., clientPacket=false)`), Messing-„Tripped“ ebenso. Kühlung/Regelung werden gezeigt, nicht simuliert. Luftstrom: echte Create-`AirCurrent` (Partikel) + Item-Entities per `setDeltaMovement` weggeschoben.
- HV-Schalter (facing east): `setSpeed(rpm)` + `onSpeedChanged(0)` wie in Power Grids hvSwitch-Szene; bei facing east/north invertiert PG das Vorzeichen ⇒ +32 öffnet, −32 schließt; Stange startet per Struktur-NBT `Rod {Value 1, Target 1}` geschlossen (LerpedFloat-NBT-Format angenommen). Servo/Welle per `setKineticSpeed`.
- Kabelbox: Facing per `BlockStateProperties.HORIZONTAL_FACING` (= PG `HorizontalElectricBlock.HORIZONTAL_FACING`), Adern per NBT `Cores` (Liste von Strings) mit Redraw ⇒ BlockColor färbt die Kappen um. Terminal-Positionen für die Linien (Box Ost: Terminals entlang z 3,5/8/12,5 px) aus der Rotation geschätzt – Lage im Spiel prüfen.
- Pin-/Steckdosen-/Wertefeld-Positionen in den Szenen aus den Klickboxen in `HvCoolingFanBlock`/`CoolingFanTopBlockEntity` für facing north, slot 2 gerechnet.

## Datenstecker-Box / Stecker-Kabel (feature/feinschliff)
- `HorizontalElectricBlock.horizontalNorthTerminals`, `getStateForPlacement` überschrieben (Buchse aus der Wand).
- BER: `Sheets.cutoutBlockSheet()`, `TextureAtlasSprite.getU/getV` mit Anteil 0..1 (1.21), `VertexConsumer.addVertex(Pose, …)
  .setColor/.setUv/.setOverlay/.setLight/.setNormal(Pose, …)`, `LevelRenderer.getLightColor(Level, BlockPos)`,
  `AABB.minmax`, `BlockEntityRenderer.getViewDistance/shouldRenderOffScreen`.
- Texturen unter textures/block landen automatisch im Block-Atlas (Atlas-Quelle „directory block“).
- Ponder: `scene.world().setBlock(pos, state, false)` + `modifyBlockEntityNBT` für die Box.

## Unterputz-Installation (feature/unterputz)
- `ElectricBlock.use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` (Power Grid) für Schalter/Taster;
  `IElectric.terminalAt(BlockState, Vec3)`; `ISocketElectric.socket(BlockState)` (Power-Grid-Stecker in Steckdosen).
- `CircuitBuilder.connect(float, node, node)` und `connectSwitch(float, node, node, boolean)` → `SwitchedWire.setState`.
- `PlayerInteractEvent.RightClickBlock/RightClickItem` mit `EventPriority.HIGHEST`, `setCanceled` + `setCancellationResult`
  (vor Power Grids eigener Draht-Behandlung über Architectury).
- Netzwerk: `RegisterPayloadHandlersEvent.registrar("1").playToServer(TYPE, BlockPos.STREAM_CODEC.map(...), handler)`,
  `IPayloadContext.enqueueWork/player`, `PacketDistributor.sendToServer`.
- Client: `RegisterKeyMappingsEvent`, `ClientTickEvent.Post`, `RenderLevelStageEvent` (Stage AFTER_TRANSLUCENT_BLOCKS),
  eigener `RenderType` über `RenderType.create(name, POSITION_COLOR_NORMAL, LINES, size, CompositeState)` in einer
  RenderType-Unterklasse (Zugriff auf `RENDERTYPE_LINES_SHADER`, `NO_DEPTH_TEST`, `VIEW_OFFSET_Z_LAYERING`, …).
- `SmartBlockEntity.initialize()` läuft auch clientseitig (Registrierung für die Röntgenansicht).
