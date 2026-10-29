/*
 * Copyright 2026 Tomrox GmbH
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
 */
package com.tomrox.pghv.client.ponder;

import com.simibubi.create.AllItems;
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.ponder.base.PowerGridSceneBuilder;

/**
 * Ponder scene of the multi-core underground cable (structure underground_cable.nbt, 8x8 base plate).
 *
 * Layout: grass terrace x 3..7, z 5..7, y 1. Box A (1,1,1), box B (5,2,7) on the terrace.
 * Path as CablePath builds it: A -> anchor 1 (5,1,1) [corner, X leg] -> anchor 2 (5,2,5) [on the terrace, Z then Y leg]
 * -> B [Z leg]. Cells (2..5,1,1), (5,1,2..4), (5,2,5), (5,2,6) carry segments; (5,1,5) is the solid grass block
 * (cable runs underground, no segment). Length 10. Cores: copper, iron, gold.
 */
public class CableScenes {
    private static final BlockPos BOX_A = new BlockPos(1, 1, 1);
    private static final BlockPos BOX_B = new BlockPos(5, 2, 7);
    private static final BlockPos ANCHOR_1_GROUND = new BlockPos(5, 0, 1);
    private static final BlockPos ANCHOR_2_GROUND = new BlockPos(5, 1, 5);
    private static final BlockPos UNDERGROUND = new BlockPos(5, 1, 5);
    private static final BlockPos[] PATH = {
            new BlockPos(2, 1, 1), new BlockPos(3, 1, 1), new BlockPos(4, 1, 1), new BlockPos(5, 1, 1),
            new BlockPos(5, 1, 2), new BlockPos(5, 1, 3), new BlockPos(5, 1, 4),
            new BlockPos(5, 2, 5), new BlockPos(5, 2, 6) };
    private static final int LENGTH = 10;

    private static final CableCores CORES = CableCores.of(CableCores.COPPER, CableCores.IRON, CableCores.GOLD);
    /** Line colours for core 1..3 (copper, iron, gold). */
    private static final PonderPalette[] CORE_COLORS = { PonderPalette.RED, PonderPalette.WHITE, PonderPalette.OUTPUT };

    private static ItemStack cableStack() {
        ItemStack stack = new ItemStack(PghvItems.UNDERGROUND_CABLE.get());
        stack.set(PghvDataComponents.CABLE_CORES.get(), CORES);
        return stack;
    }

    /** Top of terminal i of box A (socket east: terminals run along z) and box B (socket north: along x). */
    private static Vec3 terminalA(int i) {
        return new Vec3(BOX_A.getX() + 0.5, BOX_A.getY() + 1.0, BOX_A.getZ() + (3.5 + 4.5 * i) / 16.0);
    }

    private static Vec3 terminalB(int i) {
        return new Vec3(BOX_B.getX() + (3.5 + 4.5 * i) / 16.0, BOX_B.getY() + 1.0, BOX_B.getZ() + 0.5);
    }

    public static void undergroundCable(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new PowerGridSceneBuilder(builder);
        scene.title("underground_cable", "Laying Underground Cable");
        scene.configureBasePlate(0, 0, 8);

        // Boxes start with their sockets turned away; they turn towards the cable when it is connected.
        scene.world().modifyBlock(BOX_A, s -> s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), false);
        scene.world().modifyBlock(BOX_B, s -> s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), false);

        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 5, 7, 1, 7), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(BOX_A), Direction.DOWN);
        scene.world().showSection(util.select().position(BOX_B), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("Cable Boxes form the two ends of an underground cable: three terminals on top, a cable socket on the side")
                .pointAt(util.vector().topOf(BOX_A))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        ItemStack cable = cableStack();
        scene.overlay().showControls(util.vector().topOf(BOX_A).add(0, 0.3, 0), Pointing.DOWN, 80).withItem(cable);
        scene.overlay().showText(90)
                .text("Lay it with Underground Cable. Its three cores determine the terminal colours and the resistance of each conductor")
                .pointAt(util.vector().topOf(BOX_A))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // --- start ---
        scene.overlay().showControls(util.vector().topOf(BOX_A), Pointing.DOWN, 30).rightClick().withItem(cable);
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Right-click the first box to start")
                .pointAt(util.vector().topOf(BOX_A))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(10);
        scene.effects().indicateSuccess(BOX_A);
        scene.idle(60);

        // --- anchor 1: corner ---
        scene.overlay().showControls(util.vector().topOf(ANCHOR_1_GROUND), Pointing.DOWN, 30).rightClick().withItem(cable);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "leg1", util.select().fromTo(2, 1, 1, 5, 1, 1), 70);
        scene.overlay().showText(70)
                .text("Right-click the ground to set anchor points: for corners...")
                .pointAt(util.vector().topOf(ANCHOR_1_GROUND))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // --- anchor 2: change of height ---
        scene.overlay().showControls(util.vector().topOf(ANCHOR_2_GROUND), Pointing.DOWN, 30).rightClick().withItem(cable);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "leg2",
                util.select().fromTo(5, 1, 2, 5, 1, 5).add(util.select().position(5, 2, 5)), 70);
        scene.overlay().showText(70)
                .text("...and for changes in height")
                .pointAt(util.vector().topOf(ANCHOR_2_GROUND))
                .placeNearTarget();
        scene.idle(80);

        // --- connect ---
        scene.overlay().showControls(util.vector().topOf(BOX_B), Pointing.DOWN, 30).rightClick().withItem(cable);
        scene.idle(15);
        for (BlockPos cell : PATH) {
            scene.world().showSection(util.select().position(cell), Direction.DOWN);
            scene.idle(3);
        }
        scene.world().modifyBlock(BOX_A, s -> s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), false);
        scene.world().modifyBlock(BOX_B, s -> s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), false);
        scene.world().modifyBlockEntityNBT(util.select().position(BOX_A), CableBoxBlockEntity.class, tag -> {
            tag.put("Cores", CORES.toTag());
            tag.putInt("Length", LENGTH);
            tag.putLong("Partner", BOX_B.asLong());
        }, true);
        scene.world().modifyBlockEntityNBT(util.select().position(BOX_B), CableBoxBlockEntity.class, tag -> {
            tag.put("Cores", CORES.toTag());
            tag.putInt("Length", LENGTH);
            tag.putLong("Owner", BOX_A.asLong());
        }, true);
        scene.effects().indicateSuccess(BOX_A);
        scene.effects().indicateSuccess(BOX_B);
        scene.overlay().showText(90)
                .text("Right-click the second box to connect. The boxes turn their sockets towards the cable; one cable item is used per block")
                .pointAt(util.vector().topOf(BOX_B))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // --- underground ---
        scene.overlay().showOutline(PonderPalette.RED, "underground", util.select().position(UNDERGROUND), 80);
        scene.overlay().showText(80)
                .text("The cable lies on the ground. Inside solid blocks it runs underground and stays invisible")
                .pointAt(util.vector().blockSurface(UNDERGROUND, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // --- cores ---
        for (int i = 0; i < CableCores.COUNT; ++i)
            scene.overlay().showLine(CORE_COLORS[i], terminalA(i), terminalB(i), 100);
        scene.overlay().showText(100)
                .text("Each core is a separate conductor: terminal 1 to terminal 1, 2 to 2 and 3 to 3. The terminal caps show the core colour")
                .pointAt(terminalA(1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        // --- goggles ---
        scene.overlay().showControls(util.vector().topOf(BOX_A), Pointing.DOWN, 80).withItem(AllItems.GOGGLES.asStack());
        scene.overlay().showText(80)
                .text("With Engineer's Goggles a box shows the cores, the resistance of each core, the length and whether it is connected")
                .pointAt(util.vector().topOf(BOX_A))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }
}
