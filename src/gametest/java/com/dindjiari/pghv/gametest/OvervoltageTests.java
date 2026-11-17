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

import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import com.dindjiari.pghv.registry.PghvBlocks;
import com.dindjiari.pghv.registry.PghvItems;
import com.dindjiari.pghv.wiring.WallDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

/** Overvoltage explosions (Discord request): normal voltage is fine, above the limit the device explodes. */
@GameTestHolder(Pghv.MOD_ID)
@PrefixGameTestTemplate(false)
public class OvervoltageTests {
    private static final BlockPos SOURCE = new BlockPos(2, 1, 1);

    private static void wallDevice(GameTestHelper helper, BlockPos pos, Block block) {
        for (int x = 0; x <= 8; ++x)
            for (int y = 1; y <= 3; ++y)
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
        helper.setBlock(pos, block.defaultBlockState().setValue(WallDeviceBlock.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void outletTest(GameTestHelper helper, Block outlet, float ok, float over) {
        PghvTestUtil.floor(helper);
        BlockPos pos = new BlockPos(5, 2, 4);
        wallDevice(helper, pos, outlet);
        helper.runAfterDelay(2, () -> {
            PghvTestUtil.voltageSource(helper, SOURCE, ok);
            PghvTestUtil.wire(helper, SOURCE, 0, pos, 0, 0.01f);
            PghvTestUtil.wire(helper, SOURCE, 1, pos, 1, 0.01f);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(outlet, pos);
            CreativeSourceBlockEntity src = helper.getBlockEntity(SOURCE);
            src.setValue(over);
        });
        helper.runAfterDelay(160, () -> {
            helper.assertBlockNotPresent(outlet, pos);
            helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> s.is(outlet.asItem())), 0, "fried outlet drops nothing");
            helper.succeed();
        });
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void outletEuExplodesAboveTwiceNominal(GameTestHelper helper) {
        outletTest(helper, PghvBlocks.OUTLET_EU.get(), 230f, 500f);
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 200)
    public static void outletUsSurvives230ButExplodesAt300(GameTestHelper helper) {
        outletTest(helper, PghvBlocks.OUTLET_US.get(), 230f, 300f);
    }

    @GameTest(template = PghvTestUtil.TEMPLATE, timeoutTicks = 250)
    public static void coolingFanExplodesAboveTwiceRated(GameTestHelper helper) {
        PghvTestUtil.floor(helper);
        BlockPos main = new BlockPos(7, 1, 3);
        FakePlayer player = PghvTestUtil.player(helper, GameType.SURVIVAL, main.relative(Direction.SOUTH, 3));
        player.setYRot(Direction.NORTH.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PghvItems.COOLING_FAN.get()));
        PghvTestUtil.use(helper, player, main.below(), Direction.UP);
        helper.runAfterDelay(2, () -> {
            PghvTestUtil.voltageSource(helper, SOURCE, HvCoolingFanBlockEntity.RATED_VOLTAGE);
            PghvTestUtil.wire(helper, SOURCE, 0, main, 0, 0.01f);
            PghvTestUtil.wire(helper, SOURCE, 1, main, 1, 0.01f);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(PghvBlocks.COOLING_FAN.get(), main);
            CreativeSourceBlockEntity src = helper.getBlockEntity(SOURCE);
            src.setValue(600f);
        });
        helper.runAfterDelay(170, () -> {
            helper.assertBlockNotPresent(PghvBlocks.COOLING_FAN.get(), main);
            helper.assertValueEqual(PghvTestUtil.countDrops(helper, s -> s.is(PghvItems.COOLING_FAN.get())), 0, "fried fan drops nothing");
            helper.succeed();
        });
    }
}
