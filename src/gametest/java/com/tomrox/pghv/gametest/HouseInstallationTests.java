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
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.wiring.BoardModule;
import com.tomrox.pghv.wiring.BoardStructure;
import com.tomrox.pghv.wiring.DistributionBoardBlock;
import com.tomrox.pghv.wiring.DistributionBoardBlockEntity;
import com.tomrox.pghv.wiring.HouseConnectionBoxBlock;
import com.tomrox.pghv.wiring.InWallLaying;
import com.tomrox.pghv.wiring.WallBoxBlockEntity;
import com.tomrox.pghv.wiring.WallDeviceBlock;
import com.tomrox.pghv.wiring.WallDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * House installation: house connection box (main switch) -> in-wall -> distribution board (fuses, RCD, main switch,
 * energy meter) -> in-wall circuit -> load (wall box with a 100 ohm wire between L and N).
 * Layout: stone wall z = 5 (x 0..13, y 1..4), devices at z = 4 facing north.
 */
@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class HouseInstallationTests {
    private static final Direction OUT = Direction.NORTH;
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos HAK = new BlockPos(1, 2, 4);
    private static final BlockPos BOARD = new BlockPos(5, 3, 4);
    private static final BlockPos BOARD_LOW = new BlockPos(5, 2, 4);
    private static final BlockPos LOAD = new BlockPos(10, 2, 4);

    private static void wall(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        for (int x = 0; x <= 13; ++x)
            for (int y = 1; y <= 4; ++y)
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
    }

    private static void device(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(WallDeviceBlock.HORIZONTAL_FACING, OUT));
    }

    private static FakePlayer wirePlayer(GameTestHelper helper) {
        FakePlayer p = PghvTestUtil.player(helper, GameType.CREATIVE, new BlockPos(6, 1, 1));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(WallDeviceBlockEntity.WIRE_ID), 1));
        return p;
    }

    /** Block-local hit on rail place 0 of a north-facing board. */
    private static final Vec3 PLACE0 = new Vec3(14 / 16.0, 8 / 16.0, 12.5 / 16.0);

    private static DistributionBoardBlockEntity board(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    /** Source -> HAK, HAK -> board feed, board circuit 1 (fuse at place 0) -> load box with 100 ohm. */
    private static void install(GameTestHelper helper, boolean twoBlocks, double loadOhm) {
        wall(helper);
        PghvTestUtil.voltageSource(helper, SOURCE, 230f);
        device(helper, HAK, PghvBlocks.HOUSE_CONNECTION_BOX.get());
        device(helper, BOARD, PghvBlocks.DISTRIBUTION_BOARD.get());
        if (twoBlocks)
            device(helper, BOARD_LOW, PghvBlocks.DISTRIBUTION_BOARD.get());
        device(helper, LOAD, PghvBlocks.WALL_BOX.get());
        helper.runAfterDelay(3, () -> {
            PghvTestUtil.wire(helper, SOURCE, 0, HAK, 0, 0.01f);
            PghvTestUtil.wire(helper, SOURCE, 1, HAK, 1, 0.01f);
            PghvTestUtil.wire(helper, LOAD, 0, LOAD, 1, (float) loadOhm);
            DistributionBoardBlockEntity b = board(helper, BOARD);
            b.insert(0, BoardModule.FUSE, null, new ItemStack(BoardModule.FUSE.item()));
            BoardStructure.setOpen(helper.getLevel(), helper.absolutePos(BOARD), true);
            FakePlayer p = wirePlayer(helper);
            ItemStack w = p.getMainHandItem();
            InWallLaying.begin(helper.getLevel(), p, w, helper.absolutePos(HAK));
            helper.assertTrue(InWallLaying.finish(helper.getLevel(), p, w, helper.absolutePos(BOARD), List.of(), null),
                    "HAK -> board feed");
            InWallLaying.begin(helper.getLevel(), p, w, helper.absolutePos(BOARD), PLACE0);
            helper.assertTrue(InWallLaying.finish(helper.getLevel(), p, w, helper.absolutePos(LOAD), List.of(), null),
                    "board circuit 1 -> load");
            helper.assertValueEqual(board(helper, BOARD).getOwned().get(0).portA(), 1, "cable on circuit 1 (fuse at place 0)");
        });
    }

    private static double loadVoltage(GameTestHelper helper) {
        WallBoxBlockEntity l = helper.getBlockEntity(LOAD);
        return Math.abs(l.portVoltage(0));
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void boardFormsUpToEightByEight(GameTestHelper helper) {
        wall(helper);
        device(helper, BOARD, PghvBlocks.DISTRIBUTION_BOARD.get());
        device(helper, BOARD_LOW, PghvBlocks.DISTRIBUTION_BOARD.get());
        device(helper, BOARD.west(), PghvBlocks.DISTRIBUTION_BOARD.get());
        device(helper, BOARD_LOW.west(), PghvBlocks.DISTRIBUTION_BOARD.get());
        helper.runAfterDelay(2, () -> {
            var top = helper.getBlockState(BOARD);
            helper.assertTrue(top.getValue(DistributionBoardBlock.DOWN), "top joined downwards");
            helper.assertFalse(top.getValue(DistributionBoardBlock.UP), "nothing above");
            // facing north: left as seen from the front = east (+x); BOARD.west() is to the right
            helper.assertTrue(top.getValue(DistributionBoardBlock.RIGHT), "joined to the right");
            helper.assertTrue(board(helper, BOARD).isController(), "top-left block is the controller");
            helper.assertFalse(board(helper, BOARD_LOW.west()).isController(), "others are not");
            List<BlockPos> nine = new java.util.ArrayList<>();
            for (int i = 0; i < 9; ++i)
                nine.add(new BlockPos(i, 0, 0));
            helper.assertFalse(BoardStructure.fits(nine, OUT), "9 wide does not fit");
            helper.assertTrue(BoardStructure.fits(nine.subList(0, 8), OUT), "8 wide fits");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void hakMainSwitchFeedsCircuit(GameTestHelper helper) {
        install(helper, false, 100);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(loadVoltage(helper) > 200, "230 V at the load, is " + loadVoltage(helper));
            BlockPos abs = helper.absolutePos(HAK);
            var st = helper.getBlockState(HAK).setValue(HouseConnectionBoxBlock.OPEN, true);
            helper.getLevel().setBlock(abs, st, Block.UPDATE_ALL);
            FakePlayer hand = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(1, 1, 2));
            // click on the red lever (north-local x 8, y 9 -> block-local 0.5, 0.56, front of the box)
            hand.gameMode.useItemOn(hand, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atLowerCornerOf(abs).add(0.5, 9 / 16.0, 11 / 16.0), OUT, abs, false));
            helper.assertFalse(helper.getBlockState(HAK).getValue(HouseConnectionBoxBlock.POWERED), "main switch off");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(loadVoltage(helper) < 1, "main switch off: no voltage, is " + loadVoltage(helper));
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void fuseBlowsAndResets(GameTestHelper helper) {
        install(helper, false, 5); // 46 A > 16 A
        helper.runAfterDelay(40, () -> {
            DistributionBoardBlockEntity b = board(helper, BOARD);
            helper.assertValueEqual(b.state(0), 2, "fuse blown");
            helper.assertTrue(loadVoltage(helper) < 1, "blown fuse: no voltage");
            for (int i = 0; i < 4; ++i)
                b.cycleRating(0, null); // 16 -> 20 -> 25 -> 32 -> 63 A
            helper.assertValueEqual(b.rating(0), 63, "rating 63 A");
            FakePlayer p = PghvTestUtil.player(helper, GameType.SURVIVAL, new BlockPos(5, 1, 2));
            ItemStack iron = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "iron_wire")), 2);
            helper.assertTrue(b.interact(0, p, iron), "iron wire resets the fuse");
            helper.assertValueEqual(iron.getCount(), 1, "one iron wire used");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(board(helper, BOARD).state(0), 1, "fuse holds at 63 A");
            helper.assertTrue(loadVoltage(helper) > 200, "voltage back, is " + loadVoltage(helper));
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void mainSwitchAndRcd(GameTestHelper helper) {
        install(helper, false, 100);
        helper.runAfterDelay(10, () -> {
            DistributionBoardBlockEntity b = board(helper, BOARD);
            b.insert(1, BoardModule.MAIN_SWITCH, null, new ItemStack(BoardModule.MAIN_SWITCH.item()));
            b.insert(2, BoardModule.RCD, null, new ItemStack(BoardModule.RCD.item()));
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(loadVoltage(helper) < 1, "new main switch and RCD are off");
            DistributionBoardBlockEntity b = board(helper, BOARD);
            b.toggleLever(1);
            b.toggleLever(2);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(loadVoltage(helper) > 200, "both on: 230 V, is " + loadVoltage(helper));
            // residual current: circuit L straight to the grid N (past the board's N)
            PghvTestUtil.wire(helper, BOARD, 4, HAK, 1, 50f);
        });
        helper.runAfterDelay(100, () -> {
            DistributionBoardBlockEntity b = board(helper, BOARD);
            helper.assertValueEqual(b.state(2), 3, "RCD tripped");
            helper.assertTrue(loadVoltage(helper) < 1, "RCD off: no voltage, is " + loadVoltage(helper));
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void energyMeterCounts(GameTestHelper helper) {
        install(helper, true, 100); // 529 W
        helper.runAfterDelay(10, () -> {
            DistributionBoardBlockEntity low = board(helper, BOARD_LOW);
            helper.assertTrue(low.insert(0, BoardModule.METER, null, new ItemStack(BoardModule.METER.item())), "meter inserted");
            for (int s = 0; s < 4; ++s)
                helper.assertValueEqual(low.module(s), BoardModule.METER, "meter fills place " + s);
        });
        helper.runAfterDelay(210, () -> {
            DistributionBoardBlockEntity c = board(helper, BOARD);
            helper.assertTrue(c.isController(), "top block controls");
            double expected = 529.0 * 200 / 20.0 / 3600.0; // ~1.47 Wh in 200 ticks
            helper.assertTrue(c.energyWh() > expected * 0.6 && c.energyWh() < expected * 1.3,
                    "energy about " + expected + " Wh, is " + c.energyWh());
            helper.assertTrue(c.monthWh() > 0, "month counter runs");
            helper.succeed();
        });
    }
}
