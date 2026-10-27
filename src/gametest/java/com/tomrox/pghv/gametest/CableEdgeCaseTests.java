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
package com.tomrox.pghv.gametest;

import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.CableSegmentBlock;
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.block.entity.CableSegmentBlockEntity;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvDataComponents;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class CableEdgeCaseTests {
    private static final BlockPos A = new BlockPos(2, 1, 2);

    private static ItemStack cable(CableCores cores, int count) {
        ItemStack stack = new ItemStack(PghvItems.UNDERGROUND_CABLE.get(), count);
        stack.set(PghvDataComponents.CABLE_CORES.get(), cores);
        return stack;
    }

    private static Object field(CableBoxBlockEntity box, String name) {
        try {
            Field f = CableBoxBlockEntity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(box);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertConnections(GameTestHelper helper, BlockPos pos, Direction... dirs) {
        BlockState state = helper.getBlockState(pos);
        if (!state.is(PghvBlocks.CABLE_SEGMENT.get())) {
            helper.fail("segment missing", pos);
            return;
        }
        Set<Direction> expected = dirs.length == 0 ? EnumSet.noneOf(Direction.class) : EnumSet.of(dirs[0], dirs);
        for (Direction d : Direction.values())
            helper.assertValueEqual(state.getValue(CableSegmentBlock.PROPERTY_BY_DIRECTION.get(d)), expected.contains(d),
                    "connection " + d + " at " + pos);
    }

    /** Straight cable A (2,1,2) -> B (8,1,2), length 5, laid in survival. */
    private static BlockPos straight(GameTestHelper helper, CableCores cores) {
        PghvTestUtil.floor(helper);
        BlockPos b = new BlockPos(8, 1, 2);
        helper.setBlock(A, PghvBlocks.CABLE_BOX.get());
        helper.setBlock(b, PghvBlocks.CABLE_BOX.get());
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(5, 1, 5));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(cores, 64));
        PghvTestUtil.use(helper, player, A, Direction.UP);
        PghvTestUtil.use(helper, player, b, Direction.UP);
        CableBoxBlockEntity box = helper.getBlockEntity(A);
        helper.assertTrue(box.isOwner(), "straight cable connected");
        helper.assertValueEqual(64 - player.getMainHandItem().getCount(), 5, "straight cable length");
        return b;
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableRiserAndUndergroundCells(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos b = new BlockPos(10, 2, 2);
        helper.setBlock(A, PghvBlocks.CABLE_BOX.get());
        helper.setBlock(b, PghvBlocks.CABLE_BOX.get());
        BlockPos anchorBlock = new BlockPos(5, 2, 2);
        helper.setBlock(anchorBlock, Blocks.STONE);
        BlockPos solid = new BlockPos(8, 2, 2);
        helper.setBlock(solid, Blocks.STONE);

        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(CableCores.DEFAULT, 64));
        PghvTestUtil.use(helper, player, A, Direction.UP);
        PghvTestUtil.use(helper, player, anchorBlock, Direction.EAST); // anchor (6,2,2)
        PghvTestUtil.use(helper, player, b, Direction.UP);
        // cells: (3..6,1,2), (6,2,2), (7..9,2,2) -> 8
        helper.assertValueEqual(64 - player.getMainHandItem().getCount(), 8, "length with riser");

        assertConnections(helper, new BlockPos(3, 1, 2), Direction.WEST, Direction.EAST);
        assertConnections(helper, new BlockPos(6, 1, 2), Direction.WEST, Direction.UP);
        assertConnections(helper, new BlockPos(6, 2, 2), Direction.DOWN, Direction.EAST);
        assertConnections(helper, new BlockPos(7, 2, 2), Direction.WEST, Direction.EAST);
        helper.assertBlockPresent(Blocks.STONE, solid); // solid cell skipped, cable runs inside
        assertConnections(helper, new BlockPos(9, 2, 2), Direction.WEST, Direction.EAST);

        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(6, 1, 6));
        PghvTestUtil.breakBlock(helper, breaker, new BlockPos(6, 2, 2));
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 8, "drops with riser");
        helper.assertBlockPresent(Blocks.STONE, solid);
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableThroughWaterAndLava(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos b = new BlockPos(8, 1, 2);
        helper.setBlock(A, PghvBlocks.CABLE_BOX.get());
        helper.setBlock(b, PghvBlocks.CABLE_BOX.get());
        BlockPos water = new BlockPos(4, 1, 2);
        BlockPos lava = new BlockPos(6, 1, 2);
        // walls so the fluids stay put
        for (int x = 3; x <= 7; ++x) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.GLASS);
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.GLASS);
        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(lava, Blocks.LAVA);

        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(5, 1, 6));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(CableCores.DEFAULT, 64));
        PghvTestUtil.use(helper, player, A, Direction.UP);
        PghvTestUtil.use(helper, player, b, Direction.UP);
        helper.assertValueEqual(64 - player.getMainHandItem().getCount(), 5, "length through fluids");

        BlockState inWater = helper.getBlockState(water);
        helper.assertTrue(inWater.is(PghvBlocks.CABLE_SEGMENT.get()) && inWater.getValue(CableSegmentBlock.WATERLOGGED),
                "waterlogged segment in water, got " + inWater);
        helper.assertBlockPresent(Blocks.LAVA, lava); // lava cell skipped

        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(5, 1, 6));
        PghvTestUtil.breakBlock(helper, breaker, new BlockPos(3, 1, 2));
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(water)).is(FluidTags.WATER)
                && helper.getLevel().getFluidState(helper.absolutePos(water)).isSource(), "water restored after cut");
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 5, "drops through fluids");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableOrphanSegmentDoesNotCut(GameTestHelper helper) {
        straight(helper, CableCores.DEFAULT);
        BlockPos orphan = new BlockPos(12, 1, 12);
        helper.setBlock(orphan, PghvBlocks.CABLE_SEGMENT.get());
        CableSegmentBlockEntity segment = helper.getBlockEntity(orphan);
        segment.setOwner(helper.absolutePos(A)); // e.g. copied with a structure
        FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(11, 1, 11));
        PghvTestUtil.breakBlock(helper, breaker, orphan);
        CableBoxBlockEntity box = helper.getBlockEntity(A);
        helper.assertTrue(box.isOwner(), "real cable must survive an orphan segment");
        helper.assertBlockPresent(PghvBlocks.CABLE_SEGMENT.get(), new BlockPos(5, 1, 2));
        helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.UNDERGROUND_CABLE.get()), 0, "orphan drops");
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void cableOverloadNeverBurns(GameTestHelper helper) {
        BlockPos b = straight(helper, CableCores.DEFAULT);
        BlockPos source = new BlockPos(2, 1, 6);
        helper.runAfterDelay(30, () -> {
            PghvTestUtil.voltageSource(helper, source, 100f);
            PghvTestUtil.wire(helper, source, 0, A, 0, 0.01f);
            PghvTestUtil.wire(helper, source, 1, A, 1, 0.01f);
            PghvTestUtil.wire(helper, b, 0, b, 1, 0.05f); // ~1300 A, far above the copper core rating
        });
        helper.runAfterDelay(120, () -> {
            CableBoxBlockEntity box = helper.getBlockEntity(A);
            ElectricWire[] wires = (ElectricWire[]) field(box, "wires");
            helper.assertTrue(wires[0] != null && wires[1] != null && wires[2] != null, "all cores stay closed under overload");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void cableCancelLaying(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        helper.setBlock(A, PghvBlocks.CABLE_BOX.get());
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(5, 1, 5));
        player.setItemInHand(InteractionHand.MAIN_HAND, cable(CableCores.DEFAULT, 10));
        PghvTestUtil.use(helper, player, A, Direction.UP);
        helper.assertTrue(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "laying started");
        // clicking the start box again cancels
        PghvTestUtil.use(helper, player, A, Direction.UP);
        helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "cancelled via start box");
        // sneak-right-click into the air cancels
        PghvTestUtil.use(helper, player, A, Direction.UP);
        player.setShiftKeyDown(true);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.getMainHandItem().has(PghvDataComponents.CABLE_LAYING.get()), "cancelled via sneak-use");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 10, "cancel uses nothing");
        helper.succeed();
    }
}
