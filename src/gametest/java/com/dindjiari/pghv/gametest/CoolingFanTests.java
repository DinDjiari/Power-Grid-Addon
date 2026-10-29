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
import com.tomrox.pghv.block.HvCoolingFanBlock;
import com.tomrox.pghv.block.entity.HvCoolingFanBlockEntity;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.TransformerMediumBlock;

@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class CoolingFanTests {
    private static final BlockPos FREE = new BlockPos(7, 1, 7);

    // ---- helpers ----

    /** Places a fan like a player clicking the top of the floor below {@code pos}, fan front = {@code facing}. */
    private static FakePlayer placeOnFloor(GameTestHelper helper, BlockPos pos, Direction facing) {
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, pos.relative(facing, 3));
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(player.getYRot());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get(), 4));
        PghvTestUtil.use(helper, player, pos.below(), Direction.UP);
        return player;
    }

    /** Asserts a complete fan with main part at {@code main}; returns the main state. */
    private static BlockState assertFan(GameTestHelper helper, BlockPos main, Direction facing, int slot) {
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = HvCoolingFanBlock.cellOf(main, facing, part);
            BlockState state = helper.getBlockState(cell);
            if (!state.is(PghvBlocks.COOLING_FAN.get())) {
                helper.fail("fan part " + part + " missing (facing " + facing + ")", cell);
                return state;
            }
            helper.assertValueEqual(state.getValue(HvCoolingFanBlock.FACING), facing, "facing of part " + part);
            helper.assertValueEqual(state.getValue(HvCoolingFanBlock.PART), part, "part index at " + cell);
            helper.assertValueEqual(state.getValue(HvCoolingFanBlock.SLOT), slot, "slot of part " + part);
            boolean hasBe = helper.getLevel().getBlockEntity(helper.absolutePos(cell)) != null;
            helper.assertValueEqual(hasBe, part != 1, "block entity on part 0 (main) and parts 2/3 (value box on top), none on part 1 (part " + part + ")");
        }
        return helper.getBlockState(main);
    }

    private static void clearFan(GameTestHelper helper, BlockPos main, Direction facing) {
        for (int part = 3; part >= 0; --part)
            helper.setBlock(HvCoolingFanBlock.cellOf(main, facing, part), Blocks.AIR);
        helper.killAllEntitiesOfClass(ItemEntity.class);
    }

    /** Medium transformer with origin (part 0) at {@code origin}, parts along +axis and +y. */
    private static void placeTransformer(GameTestHelper helper, BlockPos origin, Direction.Axis axis) {
        BlockState base = ModdedBlocks.TRANSFORMER_MEDIUM.getDefaultState()
                .setValue(TransformerMediumBlock.HORIZONTAL_AXIS, axis);
        for (int part = 0; part < 4; ++part) {
            BlockPos cell = origin.relative(axis, part & 1).above((part & 2) >> 1);
            helper.setBlock(cell, base.setValue(TransformerMediumBlock.PART, part));
        }
    }

    // ---- placement ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanPlaceFreeAllDirections(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            placeOnFloor(helper, FREE, facing);
            assertFan(helper, FREE, facing, 2);
            clearFan(helper, FREE, facing);
        }
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanPlaceBlocked(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        Direction facing = Direction.NORTH;
        BlockPos blocker = HvCoolingFanBlock.cellOf(FREE, facing, 3);
        helper.setBlock(blocker, Blocks.STONE);
        FakePlayer player = placeOnFloor(helper, FREE, facing);
        for (int part = 0; part < 3; ++part)
            helper.assertBlockNotPresent(PghvBlocks.COOLING_FAN.get(), HvCoolingFanBlock.cellOf(FREE, facing, part));
        helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "fan items after blocked placement");
        helper.succeed();
    }

    /** Clicking any of the four transformer cells on a large face must snap the fan onto the 2x2 face. */
    private static void snapAllCells(GameTestHelper helper, Direction.Axis axis) {
        PghvTestUtil.floor(helper);
        BlockPos origin = new BlockPos(6, 1, 6);
        placeTransformer(helper, origin, axis);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side.getAxis() == axis)
                continue;
            for (int trafoPart = 0; trafoPart < 4; ++trafoPart) {
                BlockPos clicked = origin.relative(axis, trafoPart & 1).above((trafoPart & 2) >> 1);
                FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, origin.relative(side, 4));
                player.setYRot(side.getOpposite().toYRot());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get()));
                PghvTestUtil.use(helper, player, clicked, side);

                // Expected main cell: in front of the transformer column that lies "left" seen from the front.
                Direction right = side.getCounterClockWise();
                BlockPos main = (right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? origin : origin.relative(axis, 1))
                        .relative(side);
                assertFan(helper, main, side, 2);
                for (int part = 0; part < 4; ++part) {
                    BlockPos behind = HvCoolingFanBlock.cellOf(main, side, part).relative(side.getOpposite());
                    helper.assertBlockPresent(ModdedBlocks.TRANSFORMER_MEDIUM.get(), behind);
                }
                HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
                helper.assertValueEqual(fan.findTransformers().size(), 1, "transformers found behind fan");
                clearFan(helper, main, side);
            }
        }
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanSnapToTransformerAxisX(GameTestHelper helper) {
        snapAllCells(helper, Direction.Axis.X);
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanSnapToTransformerAxisZ(GameTestHelper helper) {
        snapAllCells(helper, Direction.Axis.Z);
    }

    // ---- breaking ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanBreakEachPartDropsOne(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        Direction facing = Direction.EAST;
        for (int part = 0; part < 4; ++part) {
            placeOnFloor(helper, FREE, facing);
            assertFan(helper, FREE, facing, 2);
            FakePlayer breaker = PghvTestUtil.player(helper, GameType.SURVIVAL, FREE.relative(facing, 2));
            breaker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            BlockPos cell = HvCoolingFanBlock.cellOf(FREE, facing, part);
            PghvTestUtil.breakBlock(helper, breaker, cell);
            for (int p = 0; p < 4; ++p)
                helper.assertBlockNotPresent(PghvBlocks.COOLING_FAN.get(), HvCoolingFanBlock.cellOf(FREE, facing, p));
            int drops = PghvTestUtil.countDrops(helper, PghvItems.COOLING_FAN.get());
            helper.assertValueEqual(drops, 1, "fan items dropped when breaking part " + part);
            helper.killAllEntitiesOfClass(ItemEntity.class);
        }
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanBreakCreativeNoDrop(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        Direction facing = Direction.SOUTH;
        for (int part = 0; part < 4; ++part) {
            placeOnFloor(helper, FREE, facing);
            FakePlayer breaker = PghvTestUtil.player(helper, GameType.CREATIVE, FREE.relative(facing, 2));
            PghvTestUtil.breakBlock(helper, breaker, HvCoolingFanBlock.cellOf(FREE, facing, part));
            for (int p = 0; p < 4; ++p)
                helper.assertBlockNotPresent(PghvBlocks.COOLING_FAN.get(), HvCoolingFanBlock.cellOf(FREE, facing, p));
            helper.assertValueEqual(PghvTestUtil.countDrops(helper, PghvItems.COOLING_FAN.get()), 0,
                    "creative drops for part " + part);
        }
        helper.succeed();
    }

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanExplosionDropsAtMostOne(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        Direction facing = Direction.WEST;
        placeOnFloor(helper, FREE, facing);
        BlockPos abs = helper.absolutePos(HvCoolingFanBlock.cellOf(FREE, facing, 3));
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 3.0f,
                net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        for (int p = 0; p < 4; ++p)
            helper.assertBlockNotPresent(PghvBlocks.COOLING_FAN.get(), HvCoolingFanBlock.cellOf(FREE, facing, p));
        int drops = PghvTestUtil.countDrops(helper, PghvItems.COOLING_FAN.get());
        helper.assertTrue(drops <= 1, "explosion dropped " + drops + " fans");
        helper.succeed();
    }

    // ---- slot switching ----

    @GameTest(template = PghvTestUtil.TEMPLATE)
    public static void fanSlotCycle(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        Direction facing = Direction.NORTH;
        placeOnFloor(helper, FREE, facing);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, FREE.relative(facing, 2));
        player.setShiftKeyDown(true);
        int[] expected = { 0, 1, 2, 0 };
        for (int i = 0; i < expected.length; ++i) {
            // click a different part each time
            PghvTestUtil.use(helper, player, HvCoolingFanBlock.cellOf(FREE, facing, i % 4), facing);
            assertFan(helper, FREE, facing, expected[i]);
        }
        HvCoolingFanBlockEntity reloaded = PghvTestUtil.reload(helper, FREE);
        helper.assertTrue(reloaded != null, "fan block entity after reload");
        helper.succeed();
    }

    // ---- cooling ----

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 400)
    public static void fanCoolsTransformer(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos cooled = new BlockPos(2, 1, 3);
        BlockPos reference = new BlockPos(10, 1, 3);
        placeTransformer(helper, cooled, Direction.Axis.X);
        placeTransformer(helper, reference, Direction.Axis.X);

        // Fan on the south face of the first transformer, powered with 240 V.
        Direction facing = Direction.SOUTH;
        BlockPos main = cooled.relative(facing);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(facing, 3));
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get()));
        PghvTestUtil.use(helper, player, cooled, facing);
        assertFan(helper, main, facing, 2);

        BlockPos source = new BlockPos(2, 1, 10);
        helper.runAfterDelay(2, () -> {
            PghvTestUtil.voltageSource(helper, source, HvCoolingFanBlockEntity.RATED_VOLTAGE);
            PghvTestUtil.wire(helper, source, 0, main, 0, 0.01f);
            PghvTestUtil.wire(helper, source, 1, main, 1, 0.01f);
        });

        float start = 150.0f;
        helper.runAfterDelay(80, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(main);
            helper.assertTrue(fan.getFanFraction() > 0.9f, "fan should run at rated speed, is " + fan.getFanFraction());
            thermal(helper, cooled).setTemperature(start);
            thermal(helper, reference).setTemperature(start);
        });
        // Compare at the moment the reference transformer has lost half of its excess temperature.
        boolean[] done = { false };
        for (int t = 81; t < 380; ++t) {
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                float ambient = ThermalBehaviour.getAmbientTemperature(helper.getLevel(), helper.absolutePos(cooled));
                float withFan = thermal(helper, cooled).getTemperature() - ambient;
                float without = thermal(helper, reference).getTemperature() - ambient;
                if (without > (start - ambient) * 0.5f)
                    return;
                done[0] = true;
                // One fan at full speed doubles the dissipation: the excess temperature decays about twice as fast.
                helper.assertTrue(withFan < without * 0.75f,
                        "fan does not cool: dT with fan " + withFan + " K, without " + without + " K");
                helper.succeed();
            });
        }
    }

    private static ThermalBehaviour thermal(GameTestHelper helper, BlockPos trafoOrigin) {
        TransformerBlockEntity be = helper.getBlockEntity(trafoOrigin);
        ThermalBehaviour thermal = be.getBehaviour(ThermalBehaviour.TYPE);
        if (thermal == null)
            throw new IllegalStateException("transformer without thermal behaviour");
        return thermal;
    }
}
