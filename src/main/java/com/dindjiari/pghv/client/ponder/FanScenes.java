/*
 * Copyright 2026 DinDjiari
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Scene style and helpers follow Create: Power Grid's ponder scenes by patryk3211, Apache-2.0.
 */
package com.dindjiari.pghv.client.ponder;

import com.simibubi.create.AllItems;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import com.dindjiari.pghv.registry.PghvItems;
import com.dindjiari.pghv.registry.PghvBlocks;
import com.dindjiari.pghv.block.entity.DataSocketBoxBlockEntity;
import com.dindjiari.pghv.block.DataSocketBoxBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.element.ElementLinkImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.patryk3211.powergrid.ponder.base.CreateWireInstruction;
import org.patryk3211.powergrid.ponder.base.PowerGridSceneBuilder;
import org.patryk3211.powergrid.ponder.base.WireElement;

import java.util.function.Supplier;

/**
 * Ponder scenes of the transformer cooling fans (structures cooling_fan.nbt / brass_cooling_fan.nbt).
 *
 * Common layout (7x7 base plate): Power Grid medium transformer (axis x) in x 3..4, y 1..2, z 3;
 * fan facing north on the north (large) face: part 0 (main) (4,1,2), part 1 (3,1,2), part 2 (4,2,2), part 3 (3,2,2),
 * slot 2 (back, against the transformer). 240 V supply connectors (6,1,1) and (6,1,3).
 * Brass structure additionally: servo (0,1,1) facing south, shaft (0,1,2), HV switch part 0 (0,1,3) / part 1 (1,1,3)
 * facing east (shaft axis z, closed at the start), supply connector (0,1,5).
 *
 * In the Ponder level the fan block entity only runs its client part: the rotor follows the synchronised
 * "FanSteps" value (0..100 = 0..100 %), which the scenes set via NBT; cooling and regulation are shown,
 * not simulated.
 */
public class FanScenes {
    // ---- layout ----
    private static final BlockPos MAIN = new BlockPos(4, 1, 2);
    private static final BlockPos SOCKET = new BlockPos(3, 1, 2);
    private static final BlockPos TRAFO = new BlockPos(3, 1, 3);
    private static final BlockPos TRAFO_TOP = new BlockPos(3, 2, 3);
    private static final BlockPos SUPPLY_PLUS = new BlockPos(6, 1, 1);
    private static final BlockPos SUPPLY_MINUS = new BlockPos(6, 1, 3);
    private static final BlockPos SERVO = new BlockPos(0, 1, 1);
    private static final BlockPos SHAFT = new BlockPos(0, 1, 2);
    private static final BlockPos SWITCH = new BlockPos(0, 1, 3);
    private static final BlockPos SWITCH_2 = new BlockPos(1, 1, 3);
    private static final BlockPos GRID = new BlockPos(0, 1, 5);
    /** Data socket box (placed during the overheat scene), socket facing east towards the fan. */
    private static final BlockPos BOX = new BlockPos(1, 1, 1);

    private static final double PX = 1.0 / 16.0;
    /** Front face of pins and socket connectors for slot 2 (z0 = 32/3 px, pin depth from z0 - 3 px). */
    private static final double PIN_FRONT_Z = 2 + (32.0 / 3.0 - 3.0) * PX;
    private static final double PIN_BACK_Z = 2 + (32.0 / 3.0 + 2.0) * PX;

    private static Selection fan(SceneBuildingUtil util) {
        return util.select().fromTo(3, 1, 2, 4, 2, 2);
    }

    private static Selection trafo(SceneBuildingUtil util) {
        return util.select().fromTo(3, 1, 3, 4, 2, 3);
    }

    /** Box of a pin/socket connector on the bottom row of a fan cell (north-local pixel x range, slot 2). */
    private static AABB pinBox(BlockPos cell, double x0, double x1) {
        return new AABB(cell.getX() + x0 * PX, cell.getY() + PX, PIN_FRONT_Z,
                cell.getX() + x1 * PX, cell.getY() + 4 * PX, PIN_BACK_Z);
    }

    /** Round data socket on part 1 (x 5..11, y 0.5..6.5 px, slot 2: z0 = 32/3 px, sticks out 2 px). */
    private static AABB socketBox() {
        double z0 = 32.0 / 3.0;
        return new AABB(SOCKET.getX() + 5 * PX, SOCKET.getY() + 0.5 * PX, 2 + (z0 - 2) * PX,
                SOCKET.getX() + 11 * PX, SOCKET.getY() + 6.5 * PX, 2 + (z0 + 1) * PX);
    }

    private static Vec3 socketFront() {
        return new Vec3(SOCKET.getX() + 8 * PX, SOCKET.getY() + 3.5 * PX, 2 + (32.0 / 3.0 - 2) * PX);
    }

    private static Vec3 pinFront(BlockPos cell, double x0, double x1) {
        return new Vec3(cell.getX() + (x0 + x1) / 2 * PX, cell.getY() + 2.5 * PX, PIN_FRONT_Z);
    }

    /** Centre of the value box on top of the fan (edge between part 2 and 3, middle of the slot-2 panel). */
    private static Vec3 valueBox() {
        return new Vec3(4.0, 3.0, 2 + (32.0 / 3.0 + 8.0 / 3.0) * PX);
    }

    private static Vec3 fanFront() {
        return new Vec3(4.0, 2.0, 2.0 + 32.0 / 3.0 * PX);
    }

    private static void setFanSteps(SceneBuilder scene, SceneBuildingUtil util, int steps) {
        scene.world().modifyBlockEntityNBT(util.select().position(MAIN),
                HvCoolingFanBlockEntity.class, tag -> tag.putInt("FanSteps", steps));
    }

    /** Visible Power Grid wire between two terminals, drawn with the given wire item. */
    private static void wire(SceneBuilder scene, BlockPos a, int ta, BlockPos b, int tb, Supplier<ItemStack> item) {
        var link = new ElementLinkImpl<>(WireElement.class);
        var element = new WireElement(level -> {
            var wire = HangingWireEntity.create(level, new BlockWireEndpoint(a, ta), new BlockWireEndpoint(b, tb),
                    item.get(), 0.005f);
            wire.updateCurveParams();
            return wire;
        });
        scene.addInstruction(new CreateWireInstruction(15, Direction.DOWN, element));
        scene.addInstruction(ponder -> ponder.linkElement(element, link));
    }

    /** Shows base plate, transformer and the mounted fan with its 240 V supply. */
    private static void showMountedFan(PowerGridSceneBuilder scene, SceneBuildingUtil util) {
        scene.showBasePlate();
        scene.idle(5);
        scene.world().showSection(trafo(util), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(fan(util), Direction.SOUTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(SUPPLY_PLUS).add(util.select().position(SUPPLY_MINUS)), Direction.DOWN);
        scene.idle(5);
        scene.electric().connect(SUPPLY_PLUS, 0, MAIN, 0);
        scene.electric().connect(SUPPLY_MINUS, 0, MAIN, 1);
        scene.idle(15);
    }

    // =====================================================================================
    // Scene 1: andesite cooling fan
    // =====================================================================================

    public static void coolingFan(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new PowerGridSceneBuilder(builder);
        scene.title("cooling_fan", "Cooling Transformers");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        // --- mounting ---
        scene.world().showSection(trafo(util), Direction.DOWN);
        scene.idle(15);
        Vec3 trafoFace = new Vec3(4.0, 2.0, 3.0);
        scene.overlay().showText(70)
                .text("The Cooling Fan is mounted on a large side of a Power Grid Medium Transformer")
                .pointAt(trafoFace)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(TRAFO_TOP, Direction.NORTH), Pointing.DOWN, 30)
                .rightClick()
                .withItem(new ItemStack(PghvItems.COOLING_FAN.get()));
        scene.idle(30);
        scene.world().showSection(fan(util), Direction.SOUTH);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "fan", fan(util), 70);
        scene.overlay().showText(70)
                .text("No matter which cell you click, it snaps onto the whole 2x2 face")
                .pointAt(fanFront())
                .placeNearTarget();
        scene.idle(80);

        // --- slot ---
        scene.overlay().showText(90)
                .text("Right-click it with a Wrench to move the panel: front, middle or back")
                .pointAt(fanFront())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        for (int slot : new int[] { 0, 1, 2 }) {
            scene.overlay().showControls(fanFront(), Pointing.DOWN, 15).rightClick().withItem(AllItems.WRENCH.asStack());
            scene.idle(7);
            scene.world().modifyBlocks(fan(util), s -> s.setValue(HvCoolingFanBlock.SLOT, slot), false);
            scene.idle(23);
        }
        scene.idle(10);

        // --- power ---
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, "pin_plus", pinBox(MAIN, 11, 14), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "pin_minus", pinBox(MAIN, 6, 9), 80);
        scene.overlay().showText(80)
                .text("Power is connected to the two ceramic pins at the bottom left")
                .pointAt(pinFront(MAIN, 6, 14))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        scene.world().showSection(util.select().position(SUPPLY_PLUS).add(util.select().position(SUPPLY_MINUS)), Direction.DOWN);
        scene.idle(10);
        scene.electric().connect(SUPPLY_PLUS, 0, MAIN, 0);
        scene.electric().connect(SUPPLY_MINUS, 0, MAIN, 1);
        scene.idle(50);

        setFanSteps(scene, util, 100);
        scene.overlay().showText(70)
                .text("At 240 V it runs at full speed and draws its rated 300 W")
                .pointAt(fanFront())
                .placeNearTarget();
        scene.idle(80);

        // --- value box ---
        scene.overlay().showScrollInput(valueBox(), Direction.UP, 90);
        scene.overlay().showControls(valueBox(), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(90)
                .text("The value field on top sets the speed from 0 to 100 %, just like a Rotation Speed Controller")
                .pointAt(valueBox())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);
        setFanSteps(scene, util, 50);
        scene.idle(40);
        scene.overlay().showText(80)
                .text("The slower it turns, the less power it draws: half speed needs only a quarter")
                .pointAt(fanFront())
                .placeNearTarget();
        scene.idle(90);
        setFanSteps(scene, util, 100);
        scene.idle(10);

        // --- cooling ---
        scene.overlay().showOutline(PonderPalette.BLUE, "trafo", trafo(util), 90);
        scene.overlay().showText(90)
                .text("The air flow cools the transformer behind it, so it can carry more power continuously without overheating")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // --- air flow ---
        scene.overlay().showText(80)
                .text("All four cells blow air forwards and push entities away")
                .pointAt(fanFront())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(10);
        ElementLink<EntityElement>[] items = blowItems(scene, util);
        scene.idle(70);
        for (ElementLink<EntityElement> item : items)
            scene.world().modifyEntity(item, e -> e.discard());
        scene.idle(10);
        scene.markAsFinished();
    }

    /** Item entities in front of all four cells, blown away from the fan (north). */
    @SuppressWarnings("unchecked")
    private static ElementLink<EntityElement>[] blowItems(SceneBuilder scene, SceneBuildingUtil util) {
        ItemStack[] stacks = { new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.REDSTONE) };
        double[][] at = { { 4.5, 1.1 }, { 3.5, 1.1 }, { 4.5, 2.1 }, { 3.5, 2.1 } };
        ElementLink<EntityElement>[] links = new ElementLink[4];
        for (int i = 0; i < 4; ++i) {
            links[i] = scene.world().createItemEntity(util.vector().of(at[i][0], at[i][1], 1.6),
                    util.vector().of(0, 0, 0), stacks[i]);
        }
        scene.idle(10);
        for (ElementLink<EntityElement> link : links)
            scene.world().modifyEntity(link, e -> e.setDeltaMovement(0, 0.12, -0.35));
        return links;
    }

    // =====================================================================================
    // Scene 2a: brass transformer fan - automatic regulation
    // =====================================================================================

    public static void brassCoolingFan(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new PowerGridSceneBuilder(builder);
        scene.title("brass_cooling_fan", "Automatic Transformer Cooling");
        scene.configureBasePlate(0, 0, 7);
        scene.setNextUpEnabled(true);
        showMountedFan(scene, util);

        scene.overlay().showText(80)
                .text("The Brass Transformer Fan is mounted and powered just like the Andesite one, but controls itself")
                .pointAt(fanFront())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showScrollInput(valueBox(), Direction.UP, 90);
        scene.overlay().showControls(valueBox(), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(90)
                .text("Its value field on top sets the target temperature of the transformer instead of a speed")
                .pointAt(valueBox())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.BLUE, "trafo", trafo(util), 80);
        scene.overlay().showText(80)
                .text("While the transformer is cold, the fan stands still and saves power")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.RED, "trafo", trafo(util), 120);
        scene.overlay().showText(110)
                .text("When the transformer gets warmer than the target, the fan speeds up on its own - at full speed 25 °C above it")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        setFanSteps(scene, util, 30);
        scene.idle(30);
        setFanSteps(scene, util, 65);
        scene.idle(30);
        setFanSteps(scene, util, 100);
        scene.idle(40);

        scene.overlay().showText(80)
                .text("It turns twice as fast and cools twice as strongly as the Andesite fan")
                .pointAt(fanFront())
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.BLUE, "trafo", trafo(util), 90);
        scene.overlay().showText(80)
                .text("Once the transformer has cooled down, it slows down and stops again")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        setFanSteps(scene, util, 40);
        scene.idle(30);
        setFanSteps(scene, util, 0);
        scene.idle(50);
        scene.markAsFinished();
    }

    // =====================================================================================
    // Scene 2b: overheat protection (data cable, servo, HV switch)
    // =====================================================================================

    public static void overheatProtection(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new PowerGridSceneBuilder(builder);
        scene.title("brass_cooling_fan_overheat", "Overheat Protection");
        scene.configureBasePlate(0, 0, 7);
        showMountedFan(scene, util);
        setFanSteps(scene, util, 100);

        scene.world().showSection(util.select().fromTo(SWITCH, SWITCH_2).add(util.select().position(GRID)), Direction.DOWN);
        scene.idle(10);
        scene.electric().connect(GRID, 0, SWITCH, 0);
        scene.electric().connect(SWITCH_2, 1, TRAFO_TOP, 0);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("If the transformer still gets too hot, the Brass fan can disconnect it using a Servo and a High Voltage Switch")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // --- socket ---
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "socket", socketBox(), 90);
        scene.overlay().showText(90)
                .text("The round socket at the bottom right carries servo supply +, servo - and the control signal")
                .pointAt(socketFront())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // --- box + data cable ---
        scene.world().showSection(util.select().fromTo(SERVO, SHAFT), Direction.DOWN);
        scene.idle(10);
        scene.world().setBlock(BOX, PghvBlocks.DATA_SOCKET_BOX.get().defaultBlockState()
                .setValue(DataSocketBoxBlock.HORIZONTAL_FACING, Direction.EAST), false);
        scene.world().showSection(util.select().position(BOX), Direction.DOWN);
        scene.idle(15);
        ItemStack dataCable = new ItemStack(PghvItems.DATA_CABLE.get());
        scene.overlay().showText(90)
                .text("Right-click the fan socket with a Data Cable, then plug it into a Data Socket Box")
                .pointAt(util.vector().blockSurface(BOX, Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(socketFront(), Pointing.DOWN, 30).rightClick().withItem(dataCable);
        scene.idle(40);
        scene.overlay().showControls(util.vector().blockSurface(BOX, Direction.EAST), Pointing.DOWN, 30)
                .rightClick().withItem(dataCable);
        scene.idle(25);
        scene.world().modifyBlockEntityNBT(util.select().position(BOX), DataSocketBoxBlockEntity.class, tag -> {
            tag.putLong("FanSocket", SOCKET.asLong());
            tag.put("Points", new net.minecraft.nbt.ListTag());
        });
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Right-click blocks in between to lay the cable on the floor or a wall; otherwise it hangs")
                .pointAt(util.vector().topOf(2, 0, 1))
                .placeNearTarget();
        scene.idle(90);

        wire(scene, BOX, DataSocketBoxBlock.TERMINAL_POSITIVE, SERVO, 0, ModdedItems.WIRE::asStack);
        scene.idle(3);
        wire(scene, BOX, DataSocketBoxBlock.TERMINAL_NEGATIVE, SERVO, 1, ModdedItems.IRON_WIRE::asStack);
        scene.idle(3);
        wire(scene, BOX, DataSocketBoxBlock.TERMINAL_CONTROL, SERVO, 2, ModdedItems.GOLDEN_WIRE::asStack);
        scene.idle(20);
        scene.overlay().showText(80)
                .text("Wire the connectors on top of the box to the Servo: + to coil +, - to coil -, control to control")
                .pointAt(util.vector().topOf(BOX))
                .placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(80)
                .text("After each wire, Power Grid starts the next one at the clicked terminal: sneak + right-click into the air before starting the next wire")
                .pointAt(util.vector().topOf(SERVO))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.WHITE, "switch", util.select().fromTo(SWITCH, SWITCH_2), 80);
        scene.overlay().showText(80)
                .text("The Servo turns the shaft of a High Voltage Switch in the transformer's supply line")
                .pointAt(util.vector().topOf(SWITCH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // --- trip ---
        scene.overlay().showOutline(PonderPalette.RED, "trafo", trafo(util), 100);
        scene.overlay().showText(100)
                .text("At 155 °C the protection trips: the Servo turns and opens the switch")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(util.select().position(MAIN), HvCoolingFanBlockEntity.class,
                tag -> tag.putBoolean("Tripped", true));
        scene.effects().emitParticles(socketFront().add(0, 0.3, -0.2),
                scene.effects().simpleParticleEmitter(ParticleTypes.SMOKE, new Vec3(0, 0.03, 0)), 2, 12);
        turnServo(scene, util, 32);
        scene.idle(80);

        // --- reset ---
        scene.overlay().showOutline(PonderPalette.BLUE, "trafo", trafo(util), 100);
        scene.overlay().showText(100)
                .text("Once it has cooled below 100 °C, the Servo turns back and the switch closes again")
                .pointAt(util.vector().topOf(TRAFO_TOP))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().modifyBlockEntityNBT(util.select().position(MAIN), HvCoolingFanBlockEntity.class,
                tag -> tag.putBoolean("Tripped", false));
        turnServo(scene, util, -32);
        scene.idle(80);

        // --- direction ---
        scene.overlay().showText(90)
                .text("If the switch moves the wrong way, right-click the socket with a Wrench to reverse the servo direction")
                .pointAt(socketFront())
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.overlay().showControls(socketFront(), Pointing.DOWN, 40).rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(80);
        scene.markAsFinished();
    }

    /**
     * Servo half turn (~180 deg at 32 RPM in 20 ticks) driving the HV switch. The switch faces east, so
     * Power Grid inverts the speed: positive speed opens (rod -> 0), negative closes (rod -> 1).
     */
    private static void turnServo(PowerGridSceneBuilder scene, SceneBuildingUtil util, float rpm) {
        Selection drive = util.select().fromTo(SERVO, SHAFT);
        scene.world().setKineticSpeed(drive, rpm);
        scene.world().modifyBlockEntity(SWITCH, HvSwitchBlockEntity.class, be -> {
            be.setSpeed(rpm);
            be.onSpeedChanged(0);
        });
        scene.effects().rotationSpeedIndicator(SERVO);
        scene.idle(20);
        scene.world().setKineticSpeed(drive, 0);
        // switch: keep chasing until the rod has reached its end, then stop the shaft
        scene.idle(5);
        scene.world().modifyBlockEntity(SWITCH, HvSwitchBlockEntity.class, be -> be.setSpeed(0));
    }
}
