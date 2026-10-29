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
 */
package com.dindjiari.pghv.gametest;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import com.dindjiari.pghv.registry.PghvItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class CoolingFanBehaviourTests {
    private static final BlockPos MAIN = new BlockPos(7, 1, 3);
    private static final Direction FACING = Direction.SOUTH;
    private static final BlockPos SOURCE = new BlockPos(2, 1, 2);

    /** Free-standing fan facing south at MAIN, wired to a 240 V source. */
    private static void poweredFan(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, MAIN.relative(FACING, 3));
        player.setYRot(FACING.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get()));
        PghvTestUtil.use(helper, player, MAIN.below(), Direction.UP);
        helper.runAfterDelay(2, () -> {
            PghvTestUtil.voltageSource(helper, SOURCE, HvCoolingFanBlockEntity.RATED_VOLTAGE);
            PghvTestUtil.wire(helper, SOURCE, 0, MAIN, 0, 0.01f);
            PghvTestUtil.wire(helper, SOURCE, 1, MAIN, 1, 0.01f);
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 300)
    public static void fanSpeedSettingAndPersistence(GameTestHelper helper) {
        poweredFan(helper);
        helper.runAfterDelay(80, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(MAIN);
            PghvTestUtil.assertNear(helper, fan.getFanFraction(), 1.0, 0.03, "full speed at 240 V");
            fan.getBehaviour(ScrollValueBehaviour.TYPE).setValue(HvCoolingFanBlockEntity.SETTING_MAX / 2); // 50 %
        });
        helper.runAfterDelay(160, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(MAIN);
            PghvTestUtil.assertNear(helper, fan.getFanFraction(), 0.5, 0.05, "half speed at 50 % setting");
            HvCoolingFanBlockEntity reloaded = PghvTestUtil.reload(helper, MAIN);
            helper.assertValueEqual(reloaded.getBehaviour(ScrollValueBehaviour.TYPE).getValue(),
                    HvCoolingFanBlockEntity.SETTING_MAX / 2, "speed setting after reload");
            reloaded.getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);
        });
        helper.runAfterDelay(260, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(MAIN);
            helper.assertTrue(fan.getFanFraction() < 0.05f, "fan stops at 0 %, is " + fan.getFanFraction());
            helper.assertValueEqual(fan.getSpeed(), 0f, "no air flow when stopped");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void fanBlowsEntitiesAway(GameTestHelper helper) {
        poweredFan(helper);
        BlockPos start = MAIN.relative(FACING, 2);
        double[] z0 = new double[1];
        helper.runAfterDelay(60, () -> {
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, start);
            z0[0] = pig.getZ();
        });
        helper.runAfterDelay(120, () -> {
            Pig pig = helper.findOneEntity(EntityType.PIG);
            double moved = pig.getZ() - z0[0];
            helper.assertTrue(moved > 1.0, "pig should be blown south, moved " + moved);
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void fanBlowsFromAllFourCells(GameTestHelper helper) {
        poweredFan(helper);
        // Cell of part 3 (one right, one up) - the original single current only covered part 0.
        BlockPos start = HvCoolingFanBlock.cellOf(MAIN, FACING, 3).relative(FACING, 2);
        double[] z0 = new double[1];
        helper.runAfterDelay(60, () -> {
            helper.setBlock(start.below(), net.minecraft.world.level.block.Blocks.STONE);
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, start);
            z0[0] = pig.getZ();
        });
        helper.runAfterDelay(120, () -> {
            Pig pig = helper.findOneEntity(EntityType.PIG);
            double moved = pig.getZ() - z0[0];
            helper.assertTrue(moved > 1.0, "pig in front of the upper right cell should be blown south, moved " + moved);
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void fanTopValueBoxForwardsSpeed(GameTestHelper helper) {
        poweredFan(helper);
        helper.runAfterDelay(20, () -> {
            BlockPos top2 = HvCoolingFanBlock.cellOf(MAIN, FACING, 2);
            BlockPos top3 = HvCoolingFanBlock.cellOf(MAIN, FACING, 3);
            com.dindjiari.pghv.block.entity.CoolingFanTopBlockEntity box = helper.getBlockEntity(top2);
            helper.assertTrue(helper.getBlockEntity(top3) instanceof com.dindjiari.pghv.block.entity.CoolingFanTopBlockEntity,
                    "upper right cell carries a value box too");
            box.getBehaviour(ScrollValueBehaviour.TYPE).setValue(40);
        });
        helper.runAfterDelay(40, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(MAIN);
            helper.assertValueEqual(fan.getSpeedValue(), 40, "value set on top is stored in the main part");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void fanWithoutPowerNoWind(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, MAIN.relative(FACING, 3));
        player.setYRot(FACING.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get()));
        PghvTestUtil.use(helper, player, MAIN.below(), Direction.UP);
        BlockPos start = MAIN.relative(FACING, 2);
        double[] z0 = new double[1];
        helper.runAfterDelay(20, () -> z0[0] = helper.spawnWithNoFreeWill(EntityType.PIG, start).getZ());
        helper.runAfterDelay(80, () -> {
            HvCoolingFanBlockEntity fan = helper.getBlockEntity(MAIN);
            helper.assertValueEqual(fan.getFanFraction(), 0f, "unpowered fan fraction");
            double moved = Math.abs(helper.findOneEntity(EntityType.PIG).getZ() - z0[0]);
            helper.assertTrue(moved < 0.2, "unpowered fan must not blow, moved " + moved);
            helper.succeed();
        });
    }
}
